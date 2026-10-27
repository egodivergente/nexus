package com.axel.apkexplorer;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private IExplorer service;
    private TextView state;
    private ExplorerUi screen;
    private String selectedPackage="", page="explore";
    private int captureLimit=12;
    private boolean active=false, collecting=false;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private File archive;
    private boolean destroyed=false;
    private String resumeInfo="";
    private final Shizuku.UserServiceArgs serviceArgs=new Shizuku.UserServiceArgs(new ComponentName("com.axel.apkexplorer",ExplorerService.class.getName())).daemon(false).processNameSuffix("engine").version(BuildConfig.VERSION_CODE);
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName n,IBinder b){service=IExplorer.Stub.asInterface(b);home();refreshResume();}
        public void onServiceDisconnected(ComponentName n){service=null;active=false;home();message("Conexión cerrada. Pulsa Preparar conexión.");}
    };
    private final Shizuku.OnRequestPermissionResultListener permission=(code,grant)->{if(grant==PackageManager.PERMISSION_GRANTED)connect();else message("Permiso denegado. Puedes concederlo desde Shizuku.");};
    private final Runnable poll=new Runnable(){public void run(){if(destroyed)return;if(service!=null)io.execute(()->{try{String value=service.status();handler.post(()->updateStatus(value));}catch(Exception e){handler.post(()->{service=null;active=false;home();message("Conexión perdida");});}});handler.postDelayed(this,1600);}};
    private void message(String s){handler.post(()->{if(!destroyed){Toast.makeText(this,s,Toast.LENGTH_LONG).show();if(state!=null)state.setText(s);}});}
    public void onCreate(Bundle bundle){super.onCreate(bundle);Shizuku.addRequestPermissionResultListener(permission);archive=new File(getFilesDir(),"inspection.zip");selectedPackage=getPreferences(0).getString("package","");captureLimit=getPreferences(0).getInt("limit",12);if(bundle!=null)page=bundle.getString("page","explore");home();handler.post(poll);}
    protected void onSaveInstanceState(Bundle out){out.putString("page",page);super.onSaveInstanceState(out);}
    private void home(){if(destroyed)return;screen=new ExplorerUi(this,page,selectedPackage,captureLimit,active,service!=null,archive,getPreferences(0).getString("route",""),resumeInfo,new ExplorerUi.Events(){
        public void resume(){startResume();}
        public void navigate(String target){page=target;home();}
        public void choose(String pkg){selectedPackage=pkg;getPreferences(0).edit().putString("package",pkg).apply();home();}
        public void limit(int count){captureLimit=count;getPreferences(0).edit().putInt("limit",count).apply();home();}
        public void connect(){MainActivity.this.connect();}
        public void start(){startRun(false);}
        public void repeat(){startRun(true);}
        public void stop(){io.execute(()->{try{if(service!=null)service.stop();}catch(Exception e){message(e.toString());}});}
        public void gallery(){MainActivity.this.gallery();}
        public void retrieve(){MainActivity.this.retrieve();}
        public void export(boolean before){File f=before?new File(getFilesDir(),"baseline.zip"):archive;if(!f.exists()){message("No hay capturas guardadas.");return;}startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,before?"apk-explorer-antes.zip":"apk-explorer-"+System.currentTimeMillis()+".zip"),before?21:20);}
        public void save(){io.execute(()->{try{String plan=readRoute(archive);java.nio.file.Files.copy(archive.toPath(),new File(getFilesDir(),"baseline.zip").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);getPreferences(0).edit().putString("route",plan).apply();handler.post(()->{page="compare";home();});}catch(Exception e){message(e.getMessage());}});}
        public void github(){EditText repo=new EditText(MainActivity.this);repo.setHint("propietario/repositorio");repo.setText(getPreferences(0).getString("repo",""));new AlertDialog.Builder(MainActivity.this).setTitle("Repositorio de GitHub").setMessage("Abre las versiones publicadas para descargar tu APK con tu sesión del navegador.").setView(repo).setPositiveButton("Abrir",(d,w)->{String r=repo.getText().toString().trim();if(!r.matches("[\\w.-]+/[\\w.-]+")){message("Indica propietario/repositorio");return;}getPreferences(0).edit().putString("repo",r).apply();startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/"+r+"/releases")));}).setNegativeButton("Cancelar",null).show();}
    });state=screen.render();}
    private void startRun(boolean repeat){
        if(active)return;if(service==null){connect();return;}
        String pkg=selectedPackage, plan=getPreferences(0).getString("route","");int count=captureLimit;
        if(!repeat&&pkg.isEmpty())return;
        active=true;page="explore";home();
        io.execute(()->{try{String result=repeat?service.replay(plan):service.start(pkg,count);if(!result.startsWith("Explorando")&&!result.startsWith("Repitiendo"))handler.post(()->{active=false;home();message(result);});}catch(Exception e){handler.post(()->{active=false;home();message(e.toString());});}});
    }
    /** Asks the engine process whether an interrupted audit can be resumed. */
    private void refreshResume(){
        if(service==null)return;
        io.execute(()->{try{String r=service.resumable();handler.post(()->{if(!destroyed&&!r.equals(resumeInfo)){resumeInfo=r;home();}});}catch(Exception ignored){}});
    }
    private void startResume(){
        if(active||service==null||resumeInfo.isEmpty())return;
        String pkg=resumeInfo.split("\\|")[0];int count=captureLimit;
        selectedPackage=pkg;getPreferences(0).edit().putString("package",pkg).apply();
        active=true;page="explore";home();
        io.execute(()->{try{String result=service.resume(pkg,count);if(!result.startsWith("Explorando"))handler.post(()->{active=false;home();message(result);});}catch(Exception e){handler.post(()->{active=false;home();message(e.toString());});}});
    }
    private void updateStatus(String value){
        if(destroyed)return;
        if(active&&value.equals("Sin sesión"))return;
        boolean running=value.startsWith("Preparando")||value.startsWith("Explorando");
        if(running&&!active){active=true;home();}
        if(active&&!running){active=false;home();refreshResume();if(value.startsWith("Terminado")||value.startsWith("Recorrido"))retrieve();else message(value);}
        else if(active&&state!=null)state.setText(value);
    }
    private void connect(){
        try{
            if(!Shizuku.pingBinder()){new AlertDialog.Builder(this).setTitle("Activa la captura en segundo plano").setMessage("Abre Shizuku e inícialo mediante depuración inalámbrica. Después vuelve y pulsa Preparar conexión.").setPositiveButton("Abrir Shizuku",(d,w)->{Intent launch=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");startActivity(launch!=null?launch:new Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/download/")));}).setNegativeButton("Ahora no",null).show();return;}
            if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){Shizuku.requestPermission(1);return;}
            Shizuku.bindUserService(serviceArgs,connection);message("Conectando motor local…");
        }catch(Exception e){message(e.toString());}
    }
    private void retrieve(){
        if(service==null){if(archive.exists())gallery();else message("No hay capturas guardadas.");return;}
        if(collecting)return;collecting=true;
        io.execute(()->{try{
            ParcelFileDescriptor p=service.exportZip();if(p==null){message("La exploración sigue activa o no hay sesión.");return;}
            File tmp=new File(getFilesDir(),"inspection.tmp");
            try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(p);OutputStream out=new FileOutputStream(tmp)){in.transferTo(out);}
            if(!tmp.renameTo(archive))throw new IOException("No se pudo guardar el ZIP");
            handler.post(()->{page="results";home();});
        }catch(Exception e){message(e.toString());}finally{collecting=false;}});
    }
    private void gallery(){
        io.execute(()->{try{
            File folder=new File(getCacheDir(),"gallery-"+System.currentTimeMillis());if(!folder.mkdirs())throw new IOException("No se pudo abrir galería");
            try(ZipInputStream z=new ZipInputStream(new FileInputStream(archive))){ZipEntry e;long bytes=0;while((e=z.getNextEntry())!=null){if(e.getName().contains("..")||!e.getName().matches("(screens/[\\w-]+/[\\w.-]+\\.(png|xml)|screen-[0-9]+\\.(png|xml)|index\\.html|agent\\.json|REPORT\\.md)"))continue;File target=new File(folder,e.getName());File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("No se pudo abrir galería");try(OutputStream out=new FileOutputStream(target)){byte[] b=new byte[8192];int n;while((n=z.read(b))!=-1){bytes+=n;if(bytes>50*1024*1024)throw new IOException("Archivo demasiado grande");out.write(b,0,n);}}}}
            handler.post(()->{WebView web=new WebView(this);web.getSettings().setJavaScriptEnabled(false);web.getSettings().setAllowFileAccess(true);web.getSettings().setAllowFileAccessFromFileURLs(false);web.loadUrl(Uri.fromFile(new File(folder,"index.html")).toString());new AlertDialog.Builder(this).setView(web).setPositiveButton("Cerrar",(d,w)->web.destroy()).show();});
        }catch(Exception e){message(e.toString());}});
    }
    private String readRoute(File source)throws Exception {
        try(ZipFile zip=new ZipFile(source)){
            ZipEntry metadata=zip.getEntry("agent.json");
            if(metadata==null)throw new IOException("Falta el informe de la captura.");
            try(InputStream in=zip.getInputStream(metadata)){
                org.json.JSONObject report=new org.json.JSONObject(new String(in.readNBytes(1000000),java.nio.charset.StandardCharsets.UTF_8));
                if(report.getJSONArray("states").length()==0 || report.optString("status").equals("error"))throw new IOException("La captura falló; explora de nuevo antes de guardar una referencia.");
            }
            ZipEntry entry=zip.getEntry("route.json");
            if(entry==null)throw new IOException("Esta captura es antigua. Haz una nueva exploración.");
            try(InputStream in=zip.getInputStream(entry)){byte[] bytes=in.readNBytes(100001);if(bytes.length>100000)throw new IOException("Recorrido demasiado grande");return new String(bytes,java.nio.charset.StandardCharsets.UTF_8);}
        }
    }
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if((request==20||request==21)&&result==RESULT_OK&&data!=null){Uri uri=data.getData();File source=request==21?new File(getFilesDir(),"baseline.zip"):archive;io.execute(()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Destino no disponible");in.transferTo(out);message("ZIP exportado");}catch(Exception e){message(e.toString());}});}}
    protected void onDestroy(){destroyed=true;handler.removeCallbacks(poll);Shizuku.removeRequestPermissionResultListener(permission);io.shutdown();super.onDestroy();}
}
