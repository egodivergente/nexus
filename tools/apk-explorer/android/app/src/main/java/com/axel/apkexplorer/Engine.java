package com.axel.apkexplorer;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import org.xmlpull.v1.XmlSerializer;

public final class Engine implements Runnable {
    public volatile boolean running=true, stop=false;
    public volatile String status="Preparando pantalla virtual…";
    private final Context context;
    private final String pkg;
    private final int limit;
    private final Map<String,byte[]> files=new LinkedHashMap<>();
    private final JSONArray states=new JSONArray(), edges=new JSONArray();
    private final JSONObject report=new JSONObject();
    private final JSONArray route=new JSONArray();
    private JSONObject replay;
    private UiAutomation ui;
    private VirtualDisplay display;
    private HandlerThread thread;
    private ImageReader reader;
    private int displayId=-1;
    private long size;
    public Engine(Context ctx,String packageName,int max) {context=ctx;pkg=packageName;limit=max;}
    public Engine(Context ctx,String packageName,int max,String plan)throws Exception {
        this(ctx,packageName,max);
        replay=new JSONObject(plan);
        if(!pkg.equals(replay.getString("package")) || replay.getInt("schema")!=1 || replay.getJSONArray("steps").length()>40)
            throw new IOException("Recorrido incompatible");
        for(int i=0;i<replay.getJSONArray("steps").length();i++){
            JSONObject step=replay.getJSONArray("steps").getJSONObject(i);
            String kind=step.getString("kind");
            if(!kind.equals("back") && !kind.equals("click"))throw new IOException("Acción desconocida");
            if(kind.equals("click")){step.getString("label");step.getString("resource_id");step.getString("class");}
        }
    }
    private JSONObject selector(AccessibilityNodeInfo n)throws Exception {
        return new JSONObject().put("label",ActionPolicy.identity(label(n,0)))
            .put("resource_id",n.getViewIdResourceName()==null?"":n.getViewIdResourceName())
            .put("class",String.valueOf(n.getClassName())).put("kind","click");
    }
    private boolean matches(AccessibilityNodeInfo n,JSONObject s)throws Exception {
        JSONObject actual=selector(n);
        return actual.getString("label").equals(s.getString("label")) && actual.getString("resource_id").equals(s.getString("resource_id")) && actual.getString("class").equals(s.getString("class"));
    }

    private static String shell(String... args) throws Exception {
        Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        Thread reader=new Thread(()->{try{p.getInputStream().transferTo(output);}catch(IOException ignored){} });
        reader.start();
        if(!p.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("Tiempo de espera agotado");}
        reader.join(2000);
        String s=output.toString("UTF-8");
        if(p.exitValue()!=0 || s.contains("Error:") || s.contains("Exception")) throw new IOException(s);
        return s;
    }
    private void open() throws Exception {
        Context shellContext=context.createPackageContext("com.android.shell",Context.CONTEXT_IGNORE_SECURITY);
        thread=new HandlerThread("explorer-display");thread.start();
        Handler handler=new Handler(thread.getLooper());
        reader=ImageReader.newInstance(720,1600,PixelFormat.RGBA_8888,2);
        reader.setOnImageAvailableListener(r->{try(Image image=r.acquireLatestImage()){}catch(Exception ignored){}},handler);
        // Only this trusted display is unlocked; never dismiss the physical keyguard.
        // Own content, destroy activities on release and own focus.
        int flags=1|2|8|(1<<6)|(1<<8)|(1<<10)|(1<<11)|(1<<12)|(1<<13)|(1<<14)|(1<<15);
        display=shellContext.getSystemService(DisplayManager.class).createVirtualDisplay("APK Explorer",720,1600,320,reader.getSurface(),flags);
        if(display==null || (displayId=display.getDisplay().getDisplayId())<=0) throw new IOException("No se pudo crear una pantalla virtual");
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"window");
        Object wm=Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        Class.forName("android.view.IWindowManager").getMethod("setDisplayImePolicy",int.class,int.class).invoke(wm,displayId,0);
        Class<?> connection=Class.forName("android.app.IUiAutomationConnection");
        Constructor<UiAutomation> ctor=UiAutomation.class.getDeclaredConstructor(int.class,Looper.class,connection);
        ctor.setAccessible(true);
        ui=ctor.newInstance(displayId,thread.getLooper(),Class.forName("android.app.UiAutomationConnection").getConstructor().newInstance());
        UiAutomation.class.getMethod("connect",int.class).invoke(ui,1);
        AccessibilityServiceInfo info=ui.getServiceInfo();
        info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        ui.setServiceInfo(info);
        guard();
        String activity=shell("cmd","package","resolve-activity","--brief","--user","0",pkg);
        String component=Arrays.stream(activity.split("\\R")).filter(s->s.matches("[\\w.]+/[\\w.$]+" )).findFirst().orElseThrow(()->new IOException("App sin actividad de inicio"));
        shell("am","start","--user","0","--display",String.valueOf(displayId),"-n",component);
        report.put("display_id",displayId).put("ime_policy","local").put("force_stop",false);
    }
    private void guard() throws Exception {
        if(displayId<=0 || display==null || !display.getDisplay().isValid()) throw new IOException("Pantalla virtual no disponible");
        List<AccessibilityWindowInfo> physical=ui.getWindowsOnAllDisplays().get(0);
        if(physical==null) throw new IOException("No se pudo comprobar la pantalla principal");
        for(AccessibilityWindowInfo w:physical){
            AccessibilityNodeInfo root=w.getRoot();
            if(root!=null && pkg.contentEquals(root.getPackageName()==null?"":root.getPackageName())) throw new IOException("La app objetivo está en tu pantalla principal. Usa otra app.");
        }
    }
    private static String label(AccessibilityNodeInfo n,int depth) {
        if(depth>12 || n.isPassword()) return "";
        StringBuilder s=new StringBuilder();
        if(n.getText()!=null) s.append(n.getText()).append(' ');
        if(n.getContentDescription()!=null) s.append(n.getContentDescription()).append(' ');
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)s.append(label(c,depth+1)).append(' ');}
        return s.toString().trim();
    }
    private static void attr(XmlSerializer x,String key,Object value)throws Exception{x.attribute("",key,value==null?"":value.toString());}
    private void tree(XmlSerializer x,AccessibilityNodeInfo n,List<AccessibilityNodeInfo> actions,int depth) throws Exception {
        if(n==null || depth>60 || !pkg.contentEquals(n.getPackageName()==null?"":n.getPackageName()))return;
        x.startTag("","node");
        attr(x,"text",n.isPassword()?"":n.getText());attr(x,"content-desc",n.getContentDescription());attr(x,"package",n.getPackageName());
        attr(x,"class",n.getClassName());attr(x,"resource-id",n.getViewIdResourceName());
        attr(x,"clickable",n.isClickable());attr(x,"scrollable",n.isScrollable());
        Rect r=new Rect();n.getBoundsInScreen(r);attr(x,"bounds",r.toShortString());
        String title=label(n,0);
        if(n.isEnabled() && n.isVisibleToUser() && !n.isEditable() && !n.isPassword() && n.isClickable() && ActionPolicy.allowed(title))actions.add(n);
        for(int i=0;i<n.getChildCount();i++)tree(x,n.getChild(i),actions,depth+1);
        x.endTag("","node");
    }
    private void store(String name,byte[] bytes)throws IOException {
        if(size+bytes.length>40*1024*1024)throw new IOException("Límite de memoria de capturas alcanzado");
        files.put(name,bytes);size+=bytes.length;
    }
    public void run() {
        String result="finished_partial";
        try {
            report.put("package",pkg).put("coverage_complete",false).put("mode","native_virtual_display")
                .put("schema",2).put("replay",replay!=null).put("started_at",System.currentTimeMillis())
                .put("environment",new JSONObject().put("width",720).put("height",1600).put("density_dpi",320)
                    .put("sdk",android.os.Build.VERSION.SDK_INT).put("model",android.os.Build.MODEL)
                    .put("locale",Locale.getDefault().toLanguageTag())
                    .put("night_mode",context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    .put("font_scale",shell("settings","get","system","font_scale").trim()))
                .put("state_reset",false);
            android.content.pm.PackageInfo packageInfo=context.getPackageManager().getPackageInfo(pkg,0);
            report.put("app_version",packageInfo.versionName).put("app_version_code",packageInfo.getLongVersionCode());
            open();Thread.sleep(2500);
            List<int[]> frames=new ArrayList<>();
            List<String> frameKeys=new ArrayList<>();
            Set<String> attempted=new HashSet<>();
            Set<String> backedOut=new HashSet<>();
            int duplicates=0;
            long deadline=System.currentTimeMillis()+180000;
            int count=0;
            while(!stop && (replay!=null || states.length()<limit) && count<=40 && System.currentTimeMillis()<deadline){
                ui.clearCache();
                guard();
                List<AccessibilityWindowInfo> windows=ui.getWindowsOnAllDisplays().get(displayId);
                if(windows==null || windows.isEmpty())throw new IOException("No hay ventanas en la pantalla virtual");
                List<AccessibilityNodeInfo> actions=new ArrayList<>();
                StringWriter out=new StringWriter();XmlSerializer xml=Xml.newSerializer();xml.setOutput(out);
                xml.startTag("","hierarchy");attr(xml,"display-id",displayId);
                boolean owned=false;
                for(AccessibilityWindowInfo window:windows){AccessibilityNodeInfo root=window.getRoot();if(root!=null && pkg.contentEquals(root.getPackageName()==null?"":root.getPackageName()))owned=true;tree(xml,root,actions,0);}
                xml.endTag("","hierarchy");xml.flush();
                if(!owned){result="external_boundary";break;}
                String raw=out.toString();
                Bitmap bitmap=ui.takeScreenshot();if(bitmap==null)throw new IOException("Captura virtual no disponible");
                Bitmap sampled=Bitmap.createScaledBitmap(bitmap,64,128,true);
                int[] pixels=new int[64*128];sampled.getPixels(pixels,0,64,0,0,64,128);
                if(sampled!=bitmap)sampled.recycle();
                int match=-1;
                for(int i=0;i<frames.size();i++)if(VisualFingerprint.same(frames.get(i),pixels)){match=i;break;}
                String key;
                if(match<0 || replay!=null){
                    ByteArrayOutputStream png=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,png);
                    key=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(png.toByteArray()));
                    frames.add(pixels);frameKeys.add(key);
                    String stem=String.format(Locale.ROOT,"screen-%04d",states.length()+1);
                    store(stem+".png",png.toByteArray());store(stem+".xml",raw.getBytes(StandardCharsets.UTF_8));
                    states.put(new JSONObject().put("image",stem+".png").put("xml",stem+".xml").put("id",key).put("route_step",count));
                    status="Explorando · "+states.length()+" capturas · "+count+" acciones";
                }else{key=frameKeys.get(match);duplicates++;}
                bitmap.recycle();
                report.put("duplicate_frames_skipped",duplicates);
                if(replay!=null){
                    JSONArray steps=replay.getJSONArray("steps");
                    if(count>=steps.length()){result="replay_complete";break;}
                    JSONObject step=steps.getJSONObject(count);
                    guard();
                    if(step.getString("kind").equals("back"))shell("input","-d",String.valueOf(displayId),"keyevent","KEYCODE_BACK");
                    else {
                        AccessibilityNodeInfo chosen=null;int found=0;
                        for(AccessibilityNodeInfo a:actions)if(matches(a,step)){chosen=a;found++;}
                        if(found!=1)throw new IOException("Recorrido interrumpido en paso "+count+": selector encontrado "+found+" veces");
                        if(!chosen.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new IOException("No se pudo pulsar el paso "+count);
                    }
                    route.put(step);edges.put(new JSONObject().put("from",key).put("action",step).put("performed",true));count++;
                    Thread.sleep(1400);continue;
                }
                if(states.length()>=limit || count>=40)break;
                AccessibilityNodeInfo next=null;String actionLabel="";
                actions.sort(Comparator.comparingInt(a->{Rect b=new Rect();a.getBoundsInScreen(b);return ActionPolicy.priority(label(a,0),b.top>=1440 && b.bottom-b.top<=160 && b.right-b.left<=360);}));
                // Semantic action budget is session-wide: layout shifts and accordion
                // combinations must not make the same action look unexplored again.
                for(AccessibilityNodeInfo a:actions){String l=label(a,0);String id=ActionPolicy.identity(l);if(!a.isSelected() && attempted.add(id)){next=a;actionLabel=l;break;}}
                if(next==null){
                    if(!backedOut.add(key) || count>=39)break;
                    guard();shell("input","-d",String.valueOf(displayId),"keyevent","KEYCODE_BACK");count++;
                    route.put(new JSONObject().put("kind","back"));
                    edges.put(new JSONObject().put("from",key).put("action","Atrás (pantalla virtual)").put("performed",true));
                    Thread.sleep(1400);
                    List<AccessibilityWindowInfo> remaining=ui.getWindowsOnAllDisplays().get(displayId);
                    if(remaining==null || remaining.isEmpty())break;
                    continue;
                }
                guard();
                JSONObject selected=selector(next);
                boolean ok=next.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if(ok){route.put(selected);count++;}else{result="action_failed";break;}
                edges.put(new JSONObject().put("from",key).put("action",actionLabel).put("performed",ok));
                Thread.sleep(1400);
            }
            if(stop)result="stopped";
        } catch(Throwable e){result="error";try{report.put("error",e.toString());}catch(Exception ignored){} }
        finally {
            try{if(ui!=null)UiAutomation.class.getMethod("disconnect").invoke(ui);}catch(Exception ignored){}
            try{if(display!=null)display.release();report.put("display_released",true);}catch(Exception e){result="error";try{report.put("cleanup_error",e.toString());}catch(Exception ignored){}}
            if(reader!=null)reader.close();if(thread!=null)thread.quitSafely();
            try{report.put("status",result).put("states",states).put("edges",edges);
                JSONObject plan=new JSONObject().put("schema",1).put("package",pkg).put("steps",route);
                report.put("route",plan);
                files.put("route.json",plan.toString(2).getBytes(StandardCharsets.UTF_8));
                files.put("AUDIT.md",("# Evidencia para auditoria visual\n\nLee agent.json y las capturas junto al XML. Cada route_step identifica el punto del recorrido.\nComprueba jerarquia, espaciado, tipografia, contraste, consistencia y etiquetas accesibles.\nCita imagen y elemento en cada hallazgo, separa observaciones de propuestas y relaciona con codigo solo tras inspeccionarlo.\nLa cobertura es parcial. No se restauran datos, scroll, sesion ni contenido remoto. Replay repite acciones, no garantiza un estado identico.\nPara comparar usa visual_audit.py con los ZIP antes/despues. Un cambio de pixeles no demuestra una mejora de diseno.\n").getBytes(StandardCharsets.UTF_8));
                files.put("agent.json",report.toString(2).getBytes(StandardCharsets.UTF_8));
                StringBuilder html=new StringBuilder("<!doctype html><meta name='viewport' content='width=device-width'><title>APK Explorer</title><style>body{background:#10232d;color:#e5f4f5;font:16px sans-serif;padding:20px}img{max-width:100%;max-height:75vh;border-radius:16px}article{display:inline-block;margin:12px}a{color:#84e4c8}</style><h1>APK Explorer</h1><p>").append(ActionPolicy.html(pkg)).append(" · ").append(ActionPolicy.html(result)).append("</p>");
                for(int i=0;i<states.length();i++){String image=states.getJSONObject(i).getString("image");html.append("<article><h2>").append(i+1).append("</h2><a href='").append(image).append("'><img src='").append(image).append("'></a></article>");}
                files.put("index.html",html.toString().getBytes(StandardCharsets.UTF_8));
            }catch(Exception ignored){}
            status=(result.equals("error")?"Error: "+report.optString("error",report.optString("cleanup_error")):
                (result.equals("replay_complete")?"Recorrido repetido":replay!=null?"Recorrido incompleto":"Terminado")+" · "+states.length()+" capturas · cobertura parcial");
            running=false;
        }
    }
    public byte[] zip()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(out)){for(Map.Entry<String,byte[]> e:files.entrySet()){z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue());z.closeEntry();}}return out.toByteArray();}
    /** Explicit shell QA entrypoint; not an exported Android component. */
    public static void main(String[] args)throws Exception {
        Looper.prepareMainLooper();
        Object activityThread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
        Context ctx=(Context)activityThread.getClass().getMethod("getSystemContext").invoke(activityThread);
        Engine e=args.length>3?new Engine(ctx,args[0],Integer.parseInt(args[1]),new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[3])),StandardCharsets.UTF_8)):new Engine(ctx,args[0],Integer.parseInt(args[1]));
        new Thread(()->{e.run();try{java.nio.file.Files.write(java.nio.file.Path.of(args[2]),e.zip());System.out.println(e.status);}catch(Exception ex){ex.printStackTrace();}System.exit(e.report.optString("status").equals("error")?1:0);}).start();
        Looper.loop();
    }
}
