using System;
using System.IO;
using System.Text;
using System.Linq;
using System.Drawing;
using System.Collections.Generic;
using System.Net;
using System.Net.Http;
using System.Security.Cryptography;
using System.Runtime.InteropServices;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace NexusDesktop {
static class Program {
    public static readonly JavaScriptSerializer Json = new JavaScriptSerializer { MaxJsonLength = 50000000, RecursionLimit = 100 };
    public static bool Qa;
    public static bool QaRun;
    public static bool VerifyAgent;
    public static string Data;
    public static Dictionary<string, object> Obj(object x) { return x as Dictionary<string, object> ?? new Dictionary<string, object>(); }
    public static string S(Dictionary<string, object> d, string k) { return d.ContainsKey(k) && d[k]!=null ? Convert.ToString(d[k]) : ""; }
    public static void Atomic(string name, string text) { var p=Path.Combine(Data,name); File.WriteAllText(p+".tmp",text,Encoding.UTF8); if(File.Exists(p)) File.Replace(p+".tmp",p,p+".bak"); else File.Move(p+".tmp",p); }
    public static Dictionary<string, object> Keys() {
        var file=Path.Combine(Data,"credentials.dpapi");
        if(!File.Exists(file)) return new Dictionary<string,object>();
        return Obj(Json.DeserializeObject(Encoding.UTF8.GetString(ProtectedData.Unprotect(File.ReadAllBytes(file),null,DataProtectionScope.CurrentUser))));
    }
    [STAThread] static void Main(string[] args) {
        Qa=args.Contains("--qa");
        QaRun=Qa && args.Contains("--qa-run");
        VerifyAgent=args.Contains("--verify-agent");
        Data=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),Qa?"NexusDesktop-QA":"NexusDesktop");
        Directory.CreateDirectory(Data);Directory.CreateDirectory(Path.Combine(Data,"media"));
        ServicePointManager.SecurityProtocol=SecurityProtocolType.Tls12;
        if(args.Contains("--import-keys")) {
            var values=Obj(Json.DeserializeObject(Console.In.ReadToEnd()));
            var keys=Keys();
            foreach(var name in new[]{"atlas_cloud","runway","crun"}) if(values.ContainsKey(name) && !String.IsNullOrWhiteSpace(S(values,name))) keys[name]=S(values,name);
            var encrypted=ProtectedData.Protect(Encoding.UTF8.GetBytes(Json.Serialize(keys)),null,DataProtectionScope.CurrentUser);
            File.WriteAllBytes(Path.Combine(Data,"credentials.dpapi"),encrypted);
            File.WriteAllText(Path.Combine(Data,"migration-status.json"),Json.Serialize(new { imported=keys.Keys.ToArray(), protection="Windows DPAPI CurrentUser" }));return;
        }
        if(args.Contains("--status")) { Console.WriteLine(Json.Serialize(new { configured=Keys().Keys.ToArray(), data=Data }));return; }
        Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);
        try { Application.Run(new MainWindow()); }
        catch(Exception e) { File.WriteAllText(Path.Combine(Data,"startup-error.txt"),e.GetType().Name+": "+e.Message);MessageBox.Show("Nexus no pudo iniciarse. Consulta startup-error.txt en la carpeta de datos.","Nexus"); }
    }
}
class MainWindow : Form {
    [DllImport("dwmapi.dll")] static extern int DwmSetWindowAttribute(IntPtr hwnd,int attribute,ref int value,int size);
    WebView2 web=new WebView2();
    HttpClient client=new HttpClient(new HttpClientHandler { AllowAutoRedirect=false }) { Timeout=TimeSpan.FromSeconds(120) };
    Dictionary<string,string> roots=new Dictionary<string,string> { {"atlas_cloud","https://api.atlascloud.ai"},{"runway","https://api.dev.runwayml.com"},{"crun","https://api.crun.ai"} };
    Dictionary<string,object> keys;
    public MainWindow() {
        Text="NEXUS Studio";BackColor=Color.FromArgb(12,16,20);Size=new Size(1240,820);MinimumSize=new Size(1024,620);StartPosition=FormStartPosition.CenterScreen;
        AutoScaleMode=AutoScaleMode.Dpi;web.Dock=DockStyle.Fill;
        var background=web.GetType().GetProperty("DefaultBackgroundColor");
        if(background!=null) background.SetValue(web,Color.FromArgb(12,16,20),null);
        Controls.Add(web);
        var icon=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"nexus.ico");if(File.Exists(icon)) Icon=new Icon(icon);
        Shown+=async delegate { await Initialize(); };
    }
    protected override void OnHandleCreated(EventArgs e) {
        base.OnHandleCreated(e);
        // Keep native resizing and snap layouts while matching the dark canvas.
        try { int dark=1; if(DwmSetWindowAttribute(Handle,20,ref dark,4)!=0) DwmSetWindowAttribute(Handle,19,ref dark,4); }
        catch(DllNotFoundException) { } catch(EntryPointNotFoundException) { }
    }
    async Task Initialize() {
        try {
            keys=Program.Keys();
            var environment=await CoreWebView2Environment.CreateAsync(null,Path.Combine(Program.Data,"WebView"));
            await web.EnsureCoreWebView2Async(environment);
            web.CoreWebView2.SetVirtualHostNameToFolderMapping("nexus.local",Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"ui"),CoreWebView2HostResourceAccessKind.DenyCors);
            web.CoreWebView2.SetVirtualHostNameToFolderMapping("media.nexus.local",Path.Combine(Program.Data,"media"),CoreWebView2HostResourceAccessKind.Allow);
            web.CoreWebView2.Settings.AreDevToolsEnabled=Program.Qa;
            web.CoreWebView2.Settings.AreDefaultContextMenusEnabled=false;
            var accelerator=web.CoreWebView2.Settings.GetType().GetProperty("AreBrowserAcceleratorKeysEnabled");
            if(accelerator!=null) accelerator.SetValue(web.CoreWebView2.Settings,false,null);
            web.CoreWebView2.Settings.IsStatusBarEnabled=false;
            web.CoreWebView2.Settings.IsPasswordAutosaveEnabled=false;
            web.CoreWebView2.Settings.IsGeneralAutofillEnabled=false;
            web.CoreWebView2.NavigationStarting+=(s,e)=> { if(!e.Uri.StartsWith("https://nexus.local/",StringComparison.Ordinal)) e.Cancel=true; };
            web.CoreWebView2.NewWindowRequested+=(s,e)=> { e.Handled=true; };
            web.CoreWebView2.PermissionRequested+=(s,e)=> { e.State=CoreWebView2PermissionState.Deny; };
            web.CoreWebView2.WebMessageReceived+=Receive;
            if(Program.QaRun) web.CoreWebView2.NavigationCompleted+=async (s,e)=> { await RunQa(); };
            if(Program.VerifyAgent) web.CoreWebView2.NavigationCompleted+=async (s,e)=> { await VerifyAgentUi(); };
            web.CoreWebView2.Navigate("https://nexus.local/index.html");
        } catch(Exception e) { File.WriteAllText(Path.Combine(Program.Data,"startup-error.txt"),e.ToString());MessageBox.Show("No se pudo iniciar la interfaz de Nexus. "+e.GetType().Name,"Nexus"); }
    }
    async Task VerifyAgentUi() {
        try {
            bool ready=false;
            for(int i=0;i<120;i++) {
                if(await web.CoreWebView2.ExecuteScriptAsync("typeof Agent!=='undefined' && Boolean(Agent.snapshot) && !Agent.busy")=="true") { ready=true;break; }
                await Task.Delay(250);
            }
            var result=await web.CoreWebView2.ExecuteScriptAsync("JSON.stringify({ready:typeof Agent!=='undefined'&&Boolean(Agent.snapshot),title:document.title,skills:typeof Agent!=='undefined'?Agent.snapshot?.skills.length:0,offline:typeof Agent!=='undefined'?Agent.snapshot?.offline:null,models:typeof models!=='undefined'?models.length:0,jobs:typeof state!=='undefined'?state.jobs.length:0,overflow:document.documentElement.scrollWidth>innerWidth,toast:document.querySelector('#toast')?.textContent})");
            File.WriteAllText(Path.Combine(Program.Data,"native-agent-verification.json"),result);
            using(var file=File.Create(Path.Combine(Program.Data,"native-agent.png"))) await web.CoreWebView2.CapturePreviewAsync(CoreWebView2CapturePreviewImageFormat.Png,file);
            if(!ready) File.WriteAllText(Path.Combine(Program.Data,"native-agent-pending.txt"),"El asistente no estuvo listo antes del limite de verificacion.");
        } catch(Exception e) { File.WriteAllText(Path.Combine(Program.Data,"native-agent-error.txt"),e.GetType().Name+": "+e.Message); }
    }
    async Task RunQa() {
        try {
            for(int i=0;i<80;i++) { if(await web.CoreWebView2.ExecuteScriptAsync("Boolean(window.nexusQA)")=="true") break;await Task.Delay(250); }
            using(var f=File.Create(Path.Combine(Program.Data,"qa-catalog.png"))) await web.CoreWebView2.CapturePreviewAsync(CoreWebView2CapturePreviewImageFormat.Png,f);
            await web.CoreWebView2.ExecuteScriptAsync("window.nexusQA.openModel('runway/seedream5_pro/image')");
            await Task.Delay(900);
            await web.CoreWebView2.ExecuteScriptAsync("window.nexusQA.state.draft.prompt='Una escultura de cerámica azul, luz lateral suave, fondo carbón';window.nexusQA.render();window.nexusQA.getQuote();");
            await Task.Delay(900);
            using(var f=File.Create(Path.Combine(Program.Data,"qa-canvas.png"))) await web.CoreWebView2.CapturePreviewAsync(CoreWebView2CapturePreviewImageFormat.Png,f);
            var result=await web.CoreWebView2.ExecuteScriptAsync("JSON.stringify({models:window.nexusQA.models.length,generateVisible:!!document.querySelector('#generate'),generateEnabled:!document.querySelector('#generate').disabled,fields:document.querySelectorAll('[data-param]').length,overflow:document.documentElement.scrollWidth>innerWidth})");
            File.WriteAllText(Path.Combine(Program.Data,"qa-ui.json"),result);
            await web.CoreWebView2.ExecuteScriptAsync("window.nexusQA.rpc('generate',{provider:'atlas_cloud',path:'/api/v1/model/generateImage',body:{},cost:.05}).then(()=>window.qaBlocked=false).catch(()=>window.qaBlocked=true)");
            await Task.Delay(500);
            File.WriteAllText(Path.Combine(Program.Data,"qa-paid-blocked.json"),await web.CoreWebView2.ExecuteScriptAsync("window.qaBlocked===true"));
        } catch(Exception e) { File.WriteAllText(Path.Combine(Program.Data,"qa-failure.txt"),e.GetType().Name+": "+e.Message); }
    }
    async void Receive(object sender,CoreWebView2WebMessageReceivedEventArgs e) {
        if(!e.Source.StartsWith("https://nexus.local/",StringComparison.Ordinal)) return;
        string id="";
        try {
            var message=Program.Obj(Program.Json.DeserializeObject(e.WebMessageAsJson));id=Program.S(message,"id");
            var value=await Dispatch(Program.S(message,"op"),Program.Obj(message.ContainsKey("args")?message["args"]:null));
            web.CoreWebView2.PostWebMessageAsJson(Program.Json.Serialize(new { id=id,ok=true,value=value }));
        } catch(Exception error) {
            string reason=error is InvalidOperationException || error is ArgumentException ? error.Message : "No se pudo completar la operación ("+error.GetType().Name+").";
            if(keys!=null) foreach(var value in keys.Values) if(value!=null && value.ToString().Length>4) reason=reason.Replace(value.ToString(),"[oculto]");
            web.CoreWebView2.PostWebMessageAsJson(Program.Json.Serialize(new { id=id,ok=false,error=reason }));
        }
    }
    Task<string> agentHost;
    readonly HttpClient agentClient=new HttpClient(new HttpClientHandler { AllowAutoRedirect=false }) { Timeout=TimeSpan.FromSeconds(180) };
    async Task<string> StartAgentHost() {
        if(Program.Qa) throw new InvalidOperationException("Prueba el asistente con el laboratorio local aislado.");
        var sessionFile=Path.Combine(Program.Data,"local-session.json");
        for(int attempt=0;attempt<60;attempt++) {
            try {
                var session=Program.Obj(Program.Json.DeserializeObject(File.ReadAllText(sessionFile)));
                int port=Convert.ToInt32(session["port"]);
                if(port<1 || port>65535) throw new InvalidOperationException("Puerto local no valido.");
                var origin="http://127.0.0.1:"+port;
                using(var probe=new HttpClient { Timeout=TimeSpan.FromSeconds(1) }) {
                    var health=await probe.GetStringAsync(origin+"/health");
                    if(health=="{\"app\":\"Nexus Studio\"}") return origin;
                }
            } catch(Exception) { }
            if(attempt==0) {
                var root=AppDomain.CurrentDomain.BaseDirectory;
                var script=Path.Combine(root,"local_host.py");
                if(!File.Exists(script)) script=Path.GetFullPath(Path.Combine(root,"..","local_host.py"));
                var python=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Programs","Python","Python312","pythonw.exe");
                if(!File.Exists(script) || !File.Exists(python)) throw new InvalidOperationException("Falta el motor local de Nexus o Python 3.12.");
                System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(python,"\""+script+"\" --headless") { UseShellExecute=false,CreateNoWindow=true,WindowStyle=System.Diagnostics.ProcessWindowStyle.Hidden });
            }
            await Task.Delay(250);
        }
        throw new InvalidOperationException("El motor local del asistente no responde.");
    }
    async Task<object> AgentRpc(string op,Dictionary<string,object> args) {
        if(agentHost==null) agentHost=StartAgentHost();
        string origin;
        try { origin=await agentHost; } catch { agentHost=null;throw; }
        var token=Program.S(Program.Obj(Program.Json.DeserializeObject(await agentClient.GetStringAsync(origin+"/session"))),"token");
        using(var request=new HttpRequestMessage(HttpMethod.Post,origin+"/rpc")) {
            request.Headers.TryAddWithoutValidation("Origin",origin);
            request.Headers.TryAddWithoutValidation("X-Nexus-Session",token);
            request.Content=new StringContent(Program.Json.Serialize(new { id="native-agent",op=op,args=args }),Encoding.UTF8,"application/json");
            using(var response=await agentClient.SendAsync(request)) {
                response.EnsureSuccessStatusCode();
                var value=Program.Obj(Program.Json.DeserializeObject(await response.Content.ReadAsStringAsync()));
                if(!value.ContainsKey("ok") || !Convert.ToBoolean(value["ok"])) throw new InvalidOperationException(Program.S(value,"error"));
                return value.ContainsKey("value")?value["value"]:null;
            }
        }
    }
    async Task<object> Dispatch(string op,Dictionary<string,object> a) {
        if(op=="agent" || op=="agentEvents" || op=="agentReply") return await AgentRpc(op,a);
        switch(op) {
            case "init":
                var p=Path.Combine(Program.Data,"state.json");
                return new { qa=Program.Qa,configured=keys.Keys.ToArray(),state=File.Exists(p)?Program.Json.DeserializeObject(File.ReadAllText(p)):null, version="1.7.0-agent-desktop",platform="HP EliteBook · Windows x64" };
            case "save": Program.Atomic("state.json",Program.Json.Serialize(a));return true;
            case "pick":
                using(var dialog=new OpenFileDialog { Multiselect=true,Filter="Multimedia|*.png;*.jpg;*.jpeg;*.webp;*.mp4;*.mov;*.webm;*.mp3;*.wav;*.m4a|Todos los archivos|*.*" }) {
                    if(dialog.ShowDialog(this)!=DialogResult.OK) return new object[0];
                    var files=new List<object>();
                    foreach(var name in dialog.FileNames) {
                        var f=new FileInfo(name);if(f.Length>512L*1024*1024) throw new InvalidOperationException("El archivo supera los 512 MB.");
                        var id=Guid.NewGuid().ToString("N")+f.Extension.ToLowerInvariant();var destination=Path.Combine(Program.Data,"media",id);
                        await Task.Run(()=>File.Copy(name,destination));
                        files.Add(new { id=id,name=f.Name,size=f.Length,mime=Mime(f.Extension),url="https://media.nexus.local/"+id });
                    } return files;
                }
            case "schema":
                if(Program.Qa) throw new InvalidOperationException("Laboratorio: red bloqueada.");
                var uri=new Uri(Program.S(a,"url"));if(uri.Scheme!="https" || uri.Host!="static.atlascloud.ai") throw new InvalidOperationException("El esquema no procede de Atlas.");
                return await Read(new HttpRequestMessage(HttpMethod.Get,uri));
            case "request": return await Api(Program.S(a,"provider"),Program.S(a,"path"),Program.S(a,"method"),a.ContainsKey("body")?a["body"]:null,false);
            case "generate":
                double cost;
                if(!double.TryParse(Program.S(a,"cost"),System.Globalization.NumberStyles.Any,System.Globalization.CultureInfo.InvariantCulture,out cost) || cost<0 || Double.IsNaN(cost) || Double.IsInfinity(cost)) throw new InvalidOperationException("Primero confirma un coste válido.");
                return await Api(Program.S(a,"provider"),Program.S(a,"path"),"POST",a["body"],true);
            case "upload": return await Upload(Program.S(a,"provider"),Program.S(a,"id"));
            case "download":
                var url=new Uri(Program.S(a,"url"));if(url.Scheme!="https") throw new InvalidOperationException("URL no admitida.");
                using(var dialog=new SaveFileDialog { FileName=Path.GetFileName(url.LocalPath),Filter="Archivo|*.*" }) {
                    if(dialog.ShowDialog(this)!=DialogResult.OK) return false;
                    using(var response=await client.GetAsync(url,HttpCompletionOption.ResponseHeadersRead)) { response.EnsureSuccessStatusCode();using(var stream=File.Create(dialog.FileName)) await response.Content.CopyToAsync(stream); }return true;
                }
            case "openData": System.Diagnostics.Process.Start("explorer.exe",Program.Data);return true;
            default: throw new InvalidOperationException("Operación no admitida.");
        }
    }
    bool Allowed(string provider,string path,string method,bool paid) {
        if(path.Contains(":") || path.Contains("..") || !path.StartsWith("/") || path.StartsWith("//")) return false;
        if(paid) return method=="POST" && (provider=="atlas_cloud" && new[]{"/api/v1/model/generateImage","/api/v1/model/generateVideo","/api/v1/model/generateAudio","/v1/chat/completions"}.Contains(path)
            || provider=="crun" && new[]{"/api/v1/client/job/CreateTask","/api/v1/chat/completions"}.Contains(path)
            || provider=="runway" && System.Text.RegularExpressions.Regex.IsMatch(path,@"^/v1/(text_to_image|image_to_image|text_to_video|image_to_video|video_to_video|video_to_hdr|image_upscale|video_upscale|character_performance|text_to_speech|speech_to_speech|voice_isolation|sound_effect|voice_dubbing)$"));
        if(method=="GET") return provider=="atlas_cloud" && (path=="/api/v1/models" || path=="/public/v1/balance" || path.StartsWith("/api/v1/model/prediction/"))
            || provider=="runway" && (path=="/v1/organization" || path.StartsWith("/v1/tasks/"))
            || provider=="crun" && (path.StartsWith("/api/v1/client/job/TaskInfo?") || path.StartsWith("/api/v1/client/files/upload-url?") || path=="/api/v1/client/account/balance");
        return method=="POST" && (provider=="atlas_cloud" && path=="/api/v1/model/calculate" || provider=="crun" && path=="/api/v1/client/job/estimate-credits" || provider=="runway" && path=="/v1/uploads");
    }
    HttpRequestMessage Request(string provider,string path,string method,object body) {
        if(!roots.ContainsKey(provider)) throw new InvalidOperationException("Proveedor desconocido.");
        if(!keys.ContainsKey(provider) || String.IsNullOrWhiteSpace(Program.S(keys,provider))) throw new InvalidOperationException("No hay una clave configurada para "+provider+".");
        var request=new HttpRequestMessage(new HttpMethod(method),roots[provider]+path);
        request.Headers.TryAddWithoutValidation(provider=="crun"?"x-api-key":"Authorization",(provider=="crun"?"":"Bearer ")+Program.S(keys,provider));
        if(provider=="runway") request.Headers.TryAddWithoutValidation("X-Runway-Version","2024-11-06");
        if(body!=null) request.Content=new StringContent(Program.Json.Serialize(body),Encoding.UTF8,"application/json");return request;
    }
    async Task<object> Api(string provider,string path,string method,object body,bool paid) {
        if(Program.Qa) throw new InvalidOperationException("Laboratorio: todas las peticiones externas están bloqueadas.");
        if(!Allowed(provider,path,method,paid)) throw new InvalidOperationException("Ruta no admitida por Nexus.");
        using(var request=Request(provider,path,method,body)) return await Read(request);
    }
    async Task<object> Read(HttpRequestMessage request) {
        using(var response=await client.SendAsync(request)) {
            if(!response.IsSuccessStatusCode) throw new InvalidOperationException("El proveedor respondió HTTP "+(int)response.StatusCode+". Comprueba los requisitos y la configuración.");
            var text=await response.Content.ReadAsStringAsync();
            if(String.IsNullOrWhiteSpace(text)) throw new InvalidOperationException("El proveedor devolvió una respuesta vacía.");
            return Program.Json.DeserializeObject(text);
        }
    }
    async Task<object> Upload(string provider,string id) {
        if(Program.Qa) throw new InvalidOperationException("Laboratorio: subida bloqueada.");
        if(Path.GetFileName(id)!=id || !System.Text.RegularExpressions.Regex.IsMatch(id,@"^[a-f0-9]{32}\.[a-z0-9]+$")) throw new InvalidOperationException("Archivo no válido.");
        var path=Path.Combine(Program.Data,"media",id);if(!File.Exists(path)) throw new InvalidOperationException("El archivo ya no está disponible.");
        var mime=Mime(Path.GetExtension(path));
        if(provider=="atlas_cloud") {
            using(var request=Request(provider,"/api/v1/model/uploadMedia","POST",null)) {
                var form=new MultipartFormDataContent();var stream=new StreamContent(File.OpenRead(path));stream.Headers.ContentType=new System.Net.Http.Headers.MediaTypeHeaderValue(mime);form.Add(stream,"file",id);request.Content=form;
                var response=Program.Obj(await Read(request));var data=response.ContainsKey("data")?response["data"]:null;
                if(data is string) return data;
                var map=Program.Obj(data);foreach(var name in new[]{"url","download_url","file_url","image_url"}) if(map.ContainsKey(name)) return map[name];
                throw new InvalidOperationException("Atlas no devolvió una URL de archivo.");
            }
        }
        var ticket=Program.Obj(await Api(provider,provider=="runway"?"/v1/uploads":"/api/v1/client/files/upload-url?content_type="+Uri.EscapeDataString(mime)+"&ext="+Uri.EscapeDataString(Path.GetExtension(id)),provider=="runway"?"POST":"GET",provider=="runway"?new {filename=id,type="ephemeral"}:null,false));
        if(provider=="crun") ticket=Program.Obj(ticket.ContainsKey("data")?ticket["data"]:null);
        var destination=new Uri(Program.S(ticket,provider=="runway"?"uploadUrl":"presigned_url"));if(destination.Scheme!="https") throw new InvalidOperationException("Destino de subida inseguro.");
        using(var request=new HttpRequestMessage(provider=="runway"?HttpMethod.Post:HttpMethod.Put,destination)) {
            var stream=new StreamContent(File.OpenRead(path));stream.Headers.ContentType=new System.Net.Http.Headers.MediaTypeHeaderValue(mime);
            if(provider=="runway") { var form=new MultipartFormDataContent();foreach(var field in Program.Obj(ticket.ContainsKey("fields")?ticket["fields"]:null)) form.Add(new StringContent(Convert.ToString(field.Value)),field.Key);form.Add(stream,"file",id);request.Content=form; } else request.Content=stream;
            using(var response=await client.SendAsync(request)) { if(!response.IsSuccessStatusCode) throw new InvalidOperationException("La subida no se completó."); }
        }
        return Program.S(ticket,provider=="runway"?"runwayUri":"file_url");
    }
    static string Mime(string ext) { switch(ext.ToLowerInvariant()) { case ".png":return "image/png";case ".jpg":case ".jpeg":return "image/jpeg";case ".webp":return "image/webp";case ".mp4":return "video/mp4";case ".mov":return "video/quicktime";case ".webm":return "video/webm";case ".mp3":return "audio/mpeg";case ".wav":return "audio/wav";case ".m4a":return "audio/mp4";default:return "application/octet-stream"; } }
}
}
