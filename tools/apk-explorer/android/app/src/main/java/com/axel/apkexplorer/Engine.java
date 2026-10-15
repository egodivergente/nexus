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
    private UiAutomation ui;
    private VirtualDisplay display;
    private HandlerThread thread;
    private ImageReader reader;
    private int displayId=-1;
    private long size;
    public Engine(Context ctx,String packageName,int max) {context=ctx;pkg=packageName;limit=max;}

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
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)s.append(label(c,depth+1));}
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
            report.put("package",pkg).put("coverage_complete",false).put("mode","native_virtual_display");
            open();Thread.sleep(2500);
            Set<String> visited=new HashSet<>(), attempted=new HashSet<>();
            long deadline=System.currentTimeMillis()+180000;
            int count=0;
            while(!stop && states.length()<limit && count<40 && System.currentTimeMillis()<deadline){
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
                String key=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
                if(visited.add(key)){
                    Bitmap bitmap=ui.takeScreenshot();if(bitmap==null)throw new IOException("Captura virtual no disponible");
                    ByteArrayOutputStream png=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,png);bitmap.recycle();
                    String stem=String.format(Locale.ROOT,"screen-%04d",states.length()+1);
                    store(stem+".png",png.toByteArray());store(stem+".xml",raw.getBytes(StandardCharsets.UTF_8));
                    states.put(new JSONObject().put("image",stem+".png").put("xml",stem+".xml").put("id",key));
                    status="Explorando · "+states.length()+" capturas · "+count+" acciones";
                }
                if(states.length()>=limit)break;
                AccessibilityNodeInfo next=null;String actionLabel="";
                for(AccessibilityNodeInfo a:actions){Rect b=new Rect();a.getBoundsInScreen(b);String l=label(a,0);String id=key+":"+b+":"+l;if(attempted.add(id)){next=a;actionLabel=l;break;}}
                if(next==null)break;
                guard();
                boolean ok=next.performAction(AccessibilityNodeInfo.ACTION_CLICK);count++;
                edges.put(new JSONObject().put("from",key).put("action",actionLabel).put("performed",ok));
                Thread.sleep(1400);
            }
            if(stop)result="stopped";
        } catch(Throwable e){result="error";try{report.put("error",e.toString());}catch(Exception ignored){} }
        finally {
            try{if(ui!=null)UiAutomation.class.getMethod("disconnect").invoke(ui);}catch(Exception ignored){}
            try{if(display!=null)display.release();report.put("display_released",true);}catch(Exception e){result="error";try{report.put("cleanup_error",e.toString());}catch(Exception ignored){}}
            if(reader!=null)reader.close();if(thread!=null)thread.quitSafely();
            try{report.put("status",result).put("states",states).put("edges",edges);files.put("agent.json",report.toString(2).getBytes(StandardCharsets.UTF_8));
                StringBuilder html=new StringBuilder("<!doctype html><meta name='viewport' content='width=device-width'><title>APK Explorer</title><style>body{background:#10232d;color:#e5f4f5;font:16px sans-serif;padding:20px}img{max-width:100%;max-height:75vh;border-radius:16px}article{display:inline-block;margin:12px}a{color:#84e4c8}</style><h1>APK Explorer</h1><p>").append(ActionPolicy.html(pkg)).append(" · ").append(ActionPolicy.html(result)).append("</p>");
                for(int i=0;i<states.length();i++){String image=states.getJSONObject(i).getString("image");html.append("<article><h2>").append(i+1).append("</h2><a href='").append(image).append("'><img src='").append(image).append("'></a></article>");}
                files.put("index.html",html.toString().getBytes(StandardCharsets.UTF_8));
            }catch(Exception ignored){}
            status=(result.equals("error")?"Error: "+report.optString("error",report.optString("cleanup_error")):"Terminado · "+states.length()+" capturas · cobertura parcial");
            running=false;
        }
    }
    public byte[] zip()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(out)){for(Map.Entry<String,byte[]> e:files.entrySet()){z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue());z.closeEntry();}}return out.toByteArray();}
    /** Explicit shell QA entrypoint; not an exported Android component. */
    public static void main(String[] args)throws Exception {
        Looper.prepareMainLooper();
        Object activityThread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
        Context ctx=(Context)activityThread.getClass().getMethod("getSystemContext").invoke(activityThread);
        Engine e=new Engine(ctx,args[0],Integer.parseInt(args[1]));
        new Thread(()->{e.run();try{java.nio.file.Files.write(java.nio.file.Path.of(args[2]),e.zip());System.out.println(e.status);}catch(Exception ex){ex.printStackTrace();}System.exit(e.report.optString("status").equals("error")?1:0);}).start();
        Looper.loop();
    }
}
