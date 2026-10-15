package com.axel.apkexplorer;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    private Spinner apps,limits;
    private final List<String> packages=new ArrayList<>();
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private File archive;
    private boolean destroyed=false;
    private final Shizuku.UserServiceArgs serviceArgs=new Shizuku.UserServiceArgs(new ComponentName("com.axel.apkexplorer",ExplorerService.class.getName())).daemon(false).processNameSuffix("engine").version(BuildConfig.VERSION_CODE);
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName n,IBinder b){service=IExplorer.Stub.asInterface(b);message("Conectado · listo para explorar");}
        public void onServiceDisconnected(ComponentName n){service=null;message("Conexión cerrada. Vuelve a conectar Shizuku.");}
    };
    private final Shizuku.OnRequestPermissionResultListener permission=(code,grant)->{if(grant==PackageManager.PERMISSION_GRANTED)connect();else message("Permiso denegado. Puedes concederlo desde Shizuku.");};
    private final Runnable poll=new Runnable(){public void run(){if(destroyed)return;if(service!=null)io.execute(()->{try{message(service.status());}catch(Exception e){message("Conexión perdida");service=null;}});handler.postDelayed(this,1600);}};
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n);}
    private void message(String s){handler.post(()->{if(!destroyed)state.setText(s);});}
    private TextView text(LinearLayout parent,String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(8),0,dp(8));parent.addView(t);return t;}
    private void button(LinearLayout parent,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(Color.parseColor("#10352E"));b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#8EE5CD")));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56));p.topMargin=dp(9);parent.addView(b,p);b.setOnClickListener(v->action.run());}
    public void onCreate(Bundle bundle){super.onCreate(bundle);Shizuku.addRequestPermissionResultListener(permission);archive=new File(getFilesDir(),"inspection.zip");home();handler.post(poll);}
    private void home(){
        getWindow().setStatusBarColor(Color.parseColor("#10232D"));getWindow().setNavigationBarColor(Color.parseColor("#10232D"));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.parseColor("#10232D"));
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(24),dp(32),dp(24),dp(28));scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(24),bars.top+dp(20),dp(24),bars.bottom+dp(20));return insets;});
        text(root,"APK EXPLORER",13,Color.parseColor("#8EE5CD")).setLetterSpacing(.18f);
        text(root,"Explora en\nsegundo plano",32,Color.WHITE).setTypeface(null,Typeface.BOLD);
        text(root,"Una pantalla virtual para tus apps. Capturas y mapa visual listos para un agente.",16,Color.parseColor("#B8CDD4"));
        state=text(root,service==null?"Conecta Shizuku para comenzar.":"Conectado",15,Color.parseColor("#8EE5CD"));
        button(root,"Conectar Shizuku",this::connect);
        text(root,"APLICACIÓN INSTALADA",12,Color.parseColor("#B8CDD4"));
        apps=new Spinner(this);root.addView(apps,new LinearLayout.LayoutParams(-1,dp(56)));refreshApps();
        apps.setBackgroundColor(Color.parseColor("#E8F0F2"));
        limits=new Spinner(this);ArrayAdapter<String> values=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"6 capturas","12 capturas","20 capturas"});limits.setAdapter(values);root.addView(limits);limits.setSelection(1);
        limits.setBackgroundColor(Color.parseColor("#E8F0F2"));
        button(root,"Explorar en pantalla virtual",()->{
            if(service==null){message("Primero conecta y autoriza Shizuku.");return;}
            if(packages.isEmpty()){message("No hay apps disponibles.");return;}
            String pkg=packages.get(apps.getSelectedItemPosition());getPreferences(0).edit().putString("package",pkg).apply();
            int count=new int[]{6,12,20}[limits.getSelectedItemPosition()];
            io.execute(()->{try{message(service.start(pkg,count));}catch(Exception e){message(e.toString());}});
        });
        button(root,"Detener",()->io.execute(()->{try{if(service!=null)service.stop();}catch(Exception e){message(e.toString());}}));
        button(root,"Recuperar y ver capturas",this::retrieve);
        button(root,"Exportar ZIP para un agente",()->{if(!archive.exists()){message("Recupera las capturas primero.");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"apk-explorer-"+System.currentTimeMillis()+".zip");startActivityForResult(i,20);});
        button(root,"Releases de GitHub",()->{EditText repo=new EditText(this);repo.setHint("propietario/repositorio");repo.setText(getPreferences(0).getString("repo",""));new AlertDialog.Builder(this).setTitle("Abrir releases en GitHub").setMessage("Usa tu sesión de GitHub en el navegador para descargar e instalar tu APK; después selecciónala aquí.").setView(repo).setPositiveButton("Abrir",(d,w)->{String r=repo.getText().toString().trim();if(!r.matches("[\\w.-]+/[\\w.-]+")){message("Indica propietario/repositorio");return;}getPreferences(0).edit().putString("repo",r).apply();startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/"+r+"/releases")));}).setNegativeButton("Cancelar",null).show();});
        button(root,"Actualizar lista de apps",this::refreshApps);
        text(root,"Android 14+ · "+BuildConfig.VERSION_NAME+"\nUsa otra app mientras se explora. La cobertura es parcial y los datos de la app son compartidos. No está comprobada la escritura simultánea.",12,Color.parseColor("#B8CDD4"));
    }
    private void refreshApps(){
        packages.clear();List<String> labels=new ArrayList<>();List<ResolveInfo> found=getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
        found.sort(Comparator.comparing(r->r.loadLabel(getPackageManager()).toString(),String.CASE_INSENSITIVE_ORDER));
        for(ResolveInfo r:found){String p=r.activityInfo.packageName;if(p.equals(getPackageName())||packages.contains(p))continue;packages.add(p);labels.add(r.loadLabel(getPackageManager())+" · "+p);}
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels);apps.setAdapter(adapter);
        int last=packages.indexOf(getPreferences(0).getString("package",""));if(last>=0)apps.setSelection(last);
    }
    private void connect(){
        try{
            if(!Shizuku.pingBinder()){new AlertDialog.Builder(this).setTitle("Inicia Shizuku").setMessage("Shizuku debe estar instalado e iniciado mediante depuración inalámbrica. Después vuelve aquí y pulsa Conectar.").setPositiveButton("Abrir Shizuku",(d,w)->{Intent launch=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");startActivity(launch!=null?launch:new Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/download/")));}).setNegativeButton("Cerrar",null).show();return;}
            if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){Shizuku.requestPermission(1);return;}
            Shizuku.bindUserService(serviceArgs,connection);message("Conectando motor local…");
        }catch(Exception e){message(e.toString());}
    }
    private void retrieve(){
        if(service==null){if(archive.exists())gallery();else message("No hay capturas guardadas.");return;}
        io.execute(()->{try{
            ParcelFileDescriptor p=service.exportZip();if(p==null){message("La exploración sigue activa o no hay sesión.");return;}
            File tmp=new File(getFilesDir(),"inspection.tmp");
            try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(p);OutputStream out=new FileOutputStream(tmp)){in.transferTo(out);}
            if(!tmp.renameTo(archive))throw new IOException("No se pudo guardar el ZIP");
            handler.post(this::gallery);
        }catch(Exception e){message(e.toString());}});
    }
    private void gallery(){
        io.execute(()->{try{
            File folder=new File(getCacheDir(),"gallery-"+System.currentTimeMillis());if(!folder.mkdirs())throw new IOException("No se pudo abrir galería");
            try(ZipInputStream z=new ZipInputStream(new FileInputStream(archive))){ZipEntry e;long bytes=0;while((e=z.getNextEntry())!=null){if(!e.getName().matches("(screen-[0-9]+\\.(png|xml)|index\\.html|agent\\.json)"))continue;try(OutputStream out=new FileOutputStream(new File(folder,e.getName()))){byte[] b=new byte[8192];int n;while((n=z.read(b))!=-1){bytes+=n;if(bytes>50*1024*1024)throw new IOException("Archivo demasiado grande");out.write(b,0,n);}}}}
            handler.post(()->{WebView web=new WebView(this);web.getSettings().setJavaScriptEnabled(false);web.getSettings().setAllowFileAccess(true);web.getSettings().setAllowFileAccessFromFileURLs(false);web.loadUrl(Uri.fromFile(new File(folder,"index.html")).toString());new AlertDialog.Builder(this).setView(web).setPositiveButton("Cerrar",(d,w)->web.destroy()).show();});
        }catch(Exception e){message(e.toString());}});
    }
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==20&&result==RESULT_OK&&data!=null){Uri uri=data.getData();io.execute(()->{try(InputStream in=new FileInputStream(archive);OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Destino no disponible");in.transferTo(out);message("ZIP exportado");}catch(Exception e){message(e.toString());}});}}
    protected void onDestroy(){destroyed=true;handler.removeCallbacks(poll);Shizuku.removeRequestPermissionResultListener(permission);io.shutdown();super.onDestroy();}
}
