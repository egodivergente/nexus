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
import java.util.*;
import java.util.zip.*;
import org.json.*;
import org.xmlpull.v1.XmlSerializer;

/**
 * Device side of the explorer. It owns the virtual display and the accessibility connection,
 * waits for the UI to settle, captures only screens that bring new information and writes
 * every step to disk so an interrupted audit can be resumed. All decisions come from
 * {@link ExplorationModel}.
 */
public final class Engine implements Runnable {
    public volatile boolean running=true, stop=false;
    public volatile String status="Preparando pantalla virtual…";
    static final int W=720, H=1600;
    static final long SESSION_MS=8*60*1000L, SETTLE_MAX_MS=7000, SETTLE_LOADING_MS=6500;
    static final int MAX_ACTIONS=160;
    static final long DISK_LIMIT=60L*1024*1024;

    private final Context context;
    private final String pkg;
    private final int limit;
    private final boolean resume;
    private File dir;
    private final JSONObject report=new JSONObject();
    private JSONArray route=new JSONArray();
    private final Map<String,Integer> captureStep=new LinkedHashMap<>();
    private ExplorationModel model=new ExplorationModel();
    private JSONObject replay;
    private final List<JSONObject> replayStates=new ArrayList<>();
    private UiAutomation ui;
    private VirtualDisplay display;
    private HandlerThread thread;
    private ImageReader reader;
    private int displayId=-1;
    private long diskBytes, startedAt, elapsedBefore;
    private double settleTotal, settleMax; private int settleCount;
    private String component="", resultReason="";
    private final Map<String,byte[]> replayFiles=new LinkedHashMap<>();

    private static final class Obs {
        List<UiNode> nodes=new ArrayList<>(), top=new ArrayList<>();
        List<AccessibilityNodeInfo> topLive=new ArrayList<>();
        List<Candidate> cands=new ArrayList<>();
        ScreenSignature sig; int[] thumb; long[] hash; Bitmap bitmap; String xml="", foreignPkg="", title="", section="";
        boolean owned; int windows; String heading=""; StabilityTracker.Verdict verdict;
    }

    public Engine(Context ctx,String packageName,int max){this(ctx,packageName,max,false);}
    public Engine(Context ctx,String packageName,int max,boolean resumeSession){
        context=ctx;pkg=packageName;limit=max;resume=resumeSession;
        dir=new File("/data/local/tmp/apk-explorer/"+packageName+"/session");
    }
    public Engine(Context ctx,String packageName,int max,String plan)throws Exception {
        this(ctx,packageName,max,false);
        dir=new File("/data/local/tmp/apk-explorer/"+packageName+"/replay");
        replay=new JSONObject(plan);
        if(!pkg.equals(replay.getString("package")) || replay.getInt("schema")!=1 || replay.getJSONArray("steps").length()>40)
            throw new IOException("Recorrido incompatible");
        for(int i=0;i<replay.getJSONArray("steps").length();i++){
            JSONObject step=replay.getJSONArray("steps").getJSONObject(i);
            String kind=step.getString("kind");
            if(!kind.equals("back") && !kind.equals("click") && !kind.equals("restart") && !kind.equals("scroll"))throw new IOException("Acción desconocida");
            if(kind.equals("click")){step.getString("label");step.getString("resource_id");step.getString("class");}
        }
    }

    /** Package name of an interrupted session that can be resumed, or "". */
    public static String resumable(String packageName){
        try{
            File f=new File("/data/local/tmp/apk-explorer/"+packageName+"/session/session.json");
            if(!f.exists())return "";
            JSONObject o=new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));
            if(o.optBoolean("complete"))return "";
            ExplorationModel m=ExplorationModel.fromJson(o.getJSONObject("model"));
            return m.capturedCount()+" capturas · "+m.pendingCount()+" acciones pendientes";
        }catch(Exception e){return "";}
    }
    /** Package of the most recent unfinished session on disk, or "". */
    public static String lastResumablePackage(){
        File root=new File("/data/local/tmp/apk-explorer");File[] pkgs=root.listFiles();
        if(pkgs==null)return "";
        String best="";long time=0;
        for(File p:pkgs){File s=new File(p,"session/session.json");if(s.exists()&&s.lastModified()>time&&!resumable(p.getName()).isEmpty()){time=s.lastModified();best=p.getName();}}
        return best;
    }

    // ---------- device plumbing ----------
    private JSONObject selectorOf(UiNode n)throws Exception {
        return new JSONObject().put("label",ActionPolicy.identity(n.label)).put("resource_id",n.resId==null?"":n.resId).put("class",n.cls).put("kind","click");
    }
    private static boolean matches(UiNode n,JSONObject s)throws Exception {
        return ActionPolicy.identity(n.label).equals(s.getString("label")) && (n.resId==null?"":n.resId).equals(s.getString("resource_id")) && n.cls.equals(s.getString("class"));
    }
    private static String shell(String... args) throws Exception {
        Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        Thread reader=new Thread(()->{try{p.getInputStream().transferTo(output);}catch(IOException ignored){} });
        reader.start();
        if(!p.waitFor(8,java.util.concurrent.TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("Tiempo de espera agotado");}
        reader.join(2000);
        String s=output.toString("UTF-8");
        if(p.exitValue()!=0 || s.contains("Error:") || s.contains("Exception")) throw new IOException(s);
        return s;
    }
    private void open() throws Exception {
        Context shellContext=context.createPackageContext("com.android.shell",Context.CONTEXT_IGNORE_SECURITY);
        thread=new HandlerThread("explorer-display");thread.start();
        Handler handler=new Handler(thread.getLooper());
        reader=ImageReader.newInstance(W,H,PixelFormat.RGBA_8888,2);
        reader.setOnImageAvailableListener(r->{try(Image image=r.acquireLatestImage()){}catch(Exception ignored){}},handler);
        // Only this trusted display is unlocked; never dismiss the physical keyguard.
        // Own content, destroy activities on release and own focus.
        int flags=1|2|8|(1<<6)|(1<<8)|(1<<10)|(1<<11)|(1<<12)|(1<<13)|(1<<14)|(1<<15);
        display=shellContext.getSystemService(DisplayManager.class).createVirtualDisplay("APK Explorer",W,H,320,reader.getSurface(),flags);
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
        component=Arrays.stream(activity.split("\\R")).filter(s->s.matches("[\\w.]+/[\\w.$]+" )).findFirst().orElseThrow(()->new IOException("App sin actividad de inicio"));
        launch(true);
        report.put("display_id",displayId).put("ime_policy","local").put("force_stop",false);
    }
    /** Starts the launcher activity on the virtual display. Fresh = new task (no force stop, no data cleared). */
    private void launch(boolean fresh) throws Exception {
        guard();
        if(fresh)shell("am","start","--user","0","--display",String.valueOf(displayId),"--activity-clear-task","-n",component);
        else shell("am","start","--user","0","--display",String.valueOf(displayId),"-n",component);
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
    /** A hung shell command must not abort the audit: report it and let the caller recover. */
    private boolean tryBack(){try{back();return true;}catch(Exception e){return false;}}
    private boolean tryLaunchFresh(){
        try{launch(true);return true;}catch(Exception e){
            model.error("No se pudo reiniciar la app: "+e.getMessage(),model.current);resultReason="no se pudo reiniciar la app";return false;}
    }
    private void back() throws Exception { guard(); shell("input","-d",String.valueOf(displayId),"keyevent","KEYCODE_BACK"); }

    private static String label(AccessibilityNodeInfo n,int depth) {
        if(depth>12 || n.isPassword()) return "";
        StringBuilder s=new StringBuilder();
        if(n.getText()!=null) s.append(n.getText()).append(' ');
        if(n.getContentDescription()!=null) s.append(n.getContentDescription()).append(' ');
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)s.append(label(c,depth+1)).append(' ');}
        return s.toString().trim();
    }
    private static void scanState(AccessibilityNodeInfo n,UiNode u,int depth,boolean self) {
        if(n==null||depth>10)return;
        String c=String.valueOf(n.getClassName());
        if(n.isSelected()||n.isChecked())u.stateOn=true;
        if(n.isCheckable())u.checkableSub=true;
        if(c.endsWith("Switch")||c.endsWith("ToggleButton")||c.endsWith("RadioButton")||c.endsWith("SeekBar")||c.endsWith("Slider")||c.endsWith("Spinner"))u.toggleControl=true;
        if(self&&c.endsWith("CheckBox"))u.toggleControl=true;
        for(int i=0;i<n.getChildCount();i++)scanState(n.getChild(i),u,depth+1,false);
    }
    private static void attr(XmlSerializer x,String key,Object value)throws Exception{x.attribute("",key,value==null?"":value.toString());}
    private void walk(XmlSerializer x,AccessibilityNodeInfo n,Obs o,int depth,int windowRank,boolean inClickable) throws Exception {
        if(n==null || depth>60 || !pkg.contentEquals(n.getPackageName()==null?"":n.getPackageName()))return;
        UiNode u=new UiNode();
        Rect r=new Rect();n.getBoundsInScreen(r);
        u.cls=String.valueOf(n.getClassName());u.resId=n.getViewIdResourceName()==null?"":n.getViewIdResourceName();
        u.text=n.isPassword()||n.getText()==null?"":n.getText().toString();u.desc=n.getContentDescription()==null?"":n.getContentDescription().toString();
        u.left=r.left;u.top=r.top;u.right=r.right;u.bottom=r.bottom;u.depth=depth;u.window=windowRank;
        u.clickable=n.isClickable();u.scrollable=n.isScrollable();u.selected=n.isSelected();u.checked=n.isChecked();u.checkable=n.isCheckable();
        u.checkable=n.isCheckable();u.enabled=n.isEnabled();u.editable=n.isEditable();u.password=n.isPassword();u.visible=n.isVisibleToUser();
        u.progress=u.cls.contains("ProgressBar");u.inClickable=inClickable;
        if(u.clickable){u.label=label(n,0);scanState(n,u,0,true);}
        x.startTag("","node");
        attr(x,"text",u.text);attr(x,"content-desc",u.desc);attr(x,"package",n.getPackageName());
        attr(x,"class",u.cls);attr(x,"resource-id",u.resId);
        attr(x,"clickable",u.clickable);attr(x,"scrollable",u.scrollable);attr(x,"selected",u.selected);attr(x,"checked",u.checked);
        attr(x,"enabled",u.enabled);attr(x,"checkable",u.checkable);attr(x,"window",windowRank);
        attr(x,"bounds",r.toShortString());
        o.nodes.add(u);
        if(windowRank==0){o.top.add(u);o.topLive.add(n);}
        for(int i=0;i<n.getChildCount();i++)walk(x,n.getChild(i),o,depth+1,windowRank,inClickable||u.clickable);
        x.endTag("","node");
    }
    /** One look at the virtual display: hierarchy, screenshot, signature and perceptual hash. */
    private Obs snapshot() throws Exception {
        ui.clearCache();
        guard();
        List<AccessibilityWindowInfo> windows=ui.getWindowsOnAllDisplays().get(displayId);
        Obs o=new Obs();
        if(windows==null || windows.isEmpty()){o.owned=false;o.foreignPkg="sin ventanas";return o;}
        List<AccessibilityWindowInfo> ordered=new ArrayList<>(windows);
        ordered.sort((a,b)->Integer.compare(b.getLayer(),a.getLayer()));
        StringWriter out=new StringWriter();XmlSerializer xml=Xml.newSerializer();xml.setOutput(out);
        xml.startTag("","hierarchy");attr(xml,"display-id",displayId);
        boolean topSeen=false;int rank=0;
        for(AccessibilityWindowInfo w:ordered){
            AccessibilityNodeInfo root=w.getRoot();
            if(root==null)continue;
            String rp=String.valueOf(root.getPackageName());
            boolean counts=w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION||w.getType()==AccessibilityWindowInfo.TYPE_SYSTEM;
            if(pkg.equals(rp)){o.windows++;walk(xml,root,o,0,rank,false);rank++;topSeen=true;}
            else if(counts&&!topSeen){o.foreignPkg=rp;}
        }
        xml.endTag("","hierarchy");xml.flush();
        o.xml=out.toString();
        o.owned=o.windows>0&&o.foreignPkg.isEmpty();
        Bitmap bitmap=ui.takeScreenshot();if(bitmap==null)throw new IOException("Captura virtual no disponible");
        o.bitmap=bitmap;
        Bitmap small=Bitmap.createScaledBitmap(bitmap,64,128,true);
        o.thumb=new int[64*128];small.getPixels(o.thumb,0,64,0,0,64,128);
        if(small!=bitmap)small.recycle();
        o.hash=PerceptualHash.dhash(o.thumb,64,128);
        o.sig=ScreenSignature.of(o.nodes,Math.max(1,o.windows));
        o.cands=Candidate.extract(o.top,W,H);
        o.heading=headingOf(o);
        o.title=titleWithVariant(o);
        o.section=sectionOf(o);
        return o;
    }
    /** Heading of the top window: the biggest own text near the top that is not part of a control. */
    private static String headingOf(Obs o){
        UiNode best=null;
        for(UiNode n:o.top){
            if(n.clickable||n.inClickable||n.password||n.editable||!n.visible||n.text.isEmpty())continue;
            String t=n.text.trim();
            if(t.length()<2||t.length()>40||n.top>0.16*H)continue;
            if(best==null||n.height()>best.height())best=n;
        }
        if(best!=null)return best.text.trim();
        for(UiNode n:o.top)if(!n.clickable&&!n.inClickable&&n.visible&&!n.text.isEmpty()&&n.text.length()<=40)return n.text.trim();
        return "pantalla";
    }
    /** Heading plus the selected filter/tab, so captures of one screen in different states get distinct names. */
    private static String titleWithVariant(Obs o){
        for(Candidate c:o.cands)
            if(Candidate.TAB.equals(c.kind)&&c.stateOn&&c.top<0.84*H&&!c.label.isEmpty()){
                String v=c.label.replaceAll("[\\s·•]*[\\d][\\d.,]*\\s*$","").trim();
                if(!v.isEmpty()&&!v.equalsIgnoreCase(o.heading))return o.heading+" · "+(v.length()>24?v.substring(0,24):v);
            }
        return o.heading;
    }
    private static String sectionOf(Obs o){
        for(Candidate c:o.cands)if(Candidate.TAB.equals(c.kind)&&c.stateOn&&c.top>=0.84*H&&!c.label.isEmpty())return ExplorationModel.slug(c.label);
        return "";
    }

    /** Waits until the structure and the pixels stop changing (adaptive, replaces fixed sleeps). */
    private Obs settle(long minMs) throws Exception {
        long start=System.currentTimeMillis();
        StabilityTracker tracker=new StabilityTracker(start,minMs,SETTLE_MAX_MS,SETTLE_LOADING_MS);
        Obs o=null;int foreign=0;
        while(true){
            Obs n=snapshot();
            if(o!=null&&o.bitmap!=null&&o.bitmap!=n.bitmap)o.bitmap.recycle();
            o=n;
            if(!o.owned){if(++foreign>=3||stop){o.verdict=null;break;}Thread.sleep(200);continue;}
            foreign=0;
            StabilityTracker.Verdict v=tracker.add(System.currentTimeMillis(),o.sig.structureKey,o.thumb,o.sig.loading);
            if(v!=StabilityTracker.Verdict.WAIT||stop){o.verdict=v;break;}
            Thread.sleep(150);
        }
        double ms=System.currentTimeMillis()-start;settleTotal+=ms;settleCount++;settleMax=Math.max(settleMax,ms);
        if(o.verdict==StabilityTracker.Verdict.TIMEOUT_LOADING||o.verdict==StabilityTracker.Verdict.TIMEOUT_UNSTABLE)
            model.warn("La pantalla no se estabilizó ("+o.verdict+") tras "+(int)ms+" ms; se usa el último estado visto",model.current);
        return o;
    }
    /** Settle after an action; if nothing seems to have changed, wait once more for slow transitions. */
    private Obs settleAfter(Obs before,long minMs) throws Exception {
        Obs o=settle(minMs);
        if(o.owned&&before!=null&&o.sig.structureKey.equals(before.sig.structureKey)&&PerceptualHash.changedFraction(before.thumb,o.thumb)<0.004){
            Thread.sleep(700);
            Obs again=settle(0);
            if(o.bitmap!=null&&o.bitmap!=again.bitmap)o.bitmap.recycle();
            o=again;
        }
        return o;
    }

    // ---------- storage ----------
    private void write(String name,byte[] bytes)throws IOException{
        File f=new File(dir,name);File parent=f.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("No se pudo crear "+parent);
        if(diskBytes+bytes.length>DISK_LIMIT)throw new IOException("Límite de espacio de capturas alcanzado");
        try(FileOutputStream out=new FileOutputStream(f)){out.write(bytes);}
        diskBytes+=bytes.length;
    }
    private void capture(Obs o,ExplorationModel.Screen s)throws Exception{
        ByteArrayOutputStream png=new ByteArrayOutputStream();o.bitmap.compress(Bitmap.CompressFormat.PNG,100,png);
        String stem=String.format(Locale.ROOT,"screens/%s/%03d-%s",ExplorationModel.slug(s.section),model.capturedCount()+1,ExplorationModel.slug(s.title));
        write(stem+".png",png.toByteArray());write(stem+".xml",o.xml.getBytes(StandardCharsets.UTF_8));
        model.markCaptured(s,stem+".png",stem+".xml");
        captureStep.put(s.id,route.length());
    }
    private void save(boolean complete){
        try{
            JSONObject o=new JSONObject().put("package",pkg).put("model",model.toJson()).put("route",route).put("complete",complete)
                .put("elapsed_ms",elapsedBefore+System.currentTimeMillis()-startedAt).put("capture_step",new JSONObject(captureStep))
                .put("component",component);
            File tmp=new File(dir,"session.tmp");
            try(FileOutputStream out=new FileOutputStream(tmp)){out.write(o.toString().getBytes(StandardCharsets.UTF_8));}
            if(!tmp.renameTo(new File(dir,"session.json")))throw new IOException("rename");
        }catch(Exception e){model.warn("No se pudo guardar la sesión: "+e.getMessage(),model.current);}
    }
    private void prepareFresh()throws IOException{
        if(!dir.exists()){if(!dir.mkdirs())throw new IOException("No se pudo crear "+dir);return;}
        String[] existing=dir.list();
        if(existing!=null&&existing.length>0){
            // Never delete earlier audits: move them aside.
            File aside=new File(dir.getParentFile(),dir.getName()+"-"+System.currentTimeMillis());
            if(!dir.renameTo(aside))throw new IOException("No se pudo archivar la sesión anterior");
            if(!dir.mkdirs())throw new IOException("No se pudo crear "+dir);
        }
    }
    private void loadSession()throws Exception{
        File f=new File(dir,"session.json");
        if(!f.exists())throw new IOException("No hay sesión que reanudar para "+pkg);
        JSONObject o=new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));
        model=ExplorationModel.fromJson(o.getJSONObject("model"));
        route=o.optJSONArray("route")==null?new JSONArray():o.getJSONArray("route");
        elapsedBefore=o.optLong("elapsed_ms");
        JSONObject steps=o.optJSONObject("capture_step");
        if(steps!=null)for(Iterator<String> it=steps.keys();it.hasNext();){String k=it.next();captureStep.put(k,steps.getInt(k));}
        diskBytes=sizeOf(new File(dir,"screens"));
        report.put("resumed",true);
    }
    private static long sizeOf(File f){
        if(f.isFile())return f.length();
        File[] kids=f.listFiles();long n=0;if(kids!=null)for(File k:kids)n+=sizeOf(k);return n;
    }

    // ---------- exploration ----------
    public void run() {
        String result="finished_partial";
        startedAt=System.currentTimeMillis();
        try {
            report.put("package",pkg).put("coverage_complete",false).put("mode","native_virtual_display")
                .put("schema",2).put("replay",replay!=null).put("started_at",startedAt)
                .put("engine","0.4.0-smart")
                .put("environment",new JSONObject().put("width",W).put("height",H).put("density_dpi",320)
                    .put("sdk",android.os.Build.VERSION.SDK_INT).put("model",android.os.Build.MODEL)
                    .put("locale",Locale.getDefault().toLanguageTag())
                    .put("night_mode",context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    .put("font_scale",shell("settings","get","system","font_scale").trim()))
                .put("state_reset",false);
            android.content.pm.PackageInfo packageInfo=context.getPackageManager().getPackageInfo(pkg,0);
            report.put("app_version",packageInfo.versionName).put("app_version_code",packageInfo.getLongVersionCode());
            if(replay==null&&resume)loadSession();else prepareFresh();
            open();
            result=replay!=null?runReplay():runExplore();
        } catch(Throwable e){result="error";try{report.put("error",e.toString());}catch(Exception ignored){} }
        finally {
            try{if(ui!=null)UiAutomation.class.getMethod("disconnect").invoke(ui);}catch(Exception ignored){}
            try{if(display!=null)display.release();report.put("display_released",true);}catch(Exception e){result="error";try{report.put("cleanup_error",e.toString());}catch(Exception ignored){}}
            if(reader!=null)reader.close();if(thread!=null)thread.quitSafely();
            try{
                if(stop&&!result.equals("error"))result="stopped";
                report.put("status",result);
                if(replay==null)save(result.equals("finished_complete"));
            }catch(Exception ignored){}
            status=finalStatus(result);
            running=false;
        }
    }
    private String finalStatus(String result){
        if(result.equals("error"))return "Error: "+report.optString("error",report.optString("cleanup_error"));
        if(replay!=null)return (result.equals("replay_complete")?"Recorrido repetido":"Recorrido incompleto")+" · "+replayStates.size()+" capturas · cobertura parcial";
        String why=result.equals("stopped")?"detenido":result.equals("finished_complete")?"cobertura completa":"cobertura parcial";
        return "Terminado · "+model.capturedCount()+" capturas únicas · "+why;
    }

    private String runExplore()throws Exception{
        String result="finished_partial";
        long deadline=System.currentTimeMillis()+SESSION_MS;
        int ceiling=model.capturedCount()+limit;
        int actionsAtStart=model.actionsDone;
        Thread.sleep(1200);
        if(resume&&!model.screens.isEmpty()){
            launch(true);Thread.sleep(800);
            ExplorationModel.Decision d=ExplorationModel.Decision.of(ExplorationModel.Decision.Type.RESTART,"reanudar");
            model.performed(d);route.put(new JSONObject().put("kind","restart"));
        }
        Obs obs=settle(800);
        int recoveries=0;
        loop:
        while(!stop&&System.currentTimeMillis()<deadline){
            if(model.actionsDone-actionsAtStart>=MAX_ACTIONS){resultReason="límite de "+MAX_ACTIONS+" acciones";break;}
            if(!obs.owned){
                String who=obs.foreignPkg.isEmpty()?"sin ventana":obs.foreignPkg;
                if(++recoveries>8){result="external_boundary";resultReason="la app sale de sí misma de forma repetida";break;}
                model.externalExit(who);
                boolean noWindows=obs.windows==0&&obs.foreignPkg.equals("sin ventanas");
                if(!noWindows){route.put(new JSONObject().put("kind","back"));if(!tryBack())model.error("«Atrás» no respondió al salir de la app","");obs=settle(500);}
                if(!obs.owned){if(!tryLaunchFresh())break;route.put(new JSONObject().put("kind","restart"));obs=settle(1500);}
                if(!obs.owned){result="external_boundary";resultReason="no se pudo volver a la app";break;}
                continue;
            }
            ExplorationModel.Seen seen=model.observe(obs.sig,obs.hash,obs.cands,obs.title,obs.heading,obs.section);
            if(seen.isNew){
                try{capture(obs,seen.screen);}catch(IOException e){model.error("Captura no guardada: "+e.getMessage(),seen.screen.id);resultReason=e.getMessage();break;}
            }
            save(false);
            status="Explorando · "+model.capturedCount()+" capturas · "+model.actionsDone+" acciones";
            if(model.capturedCount()>=ceiling){resultReason="límite de capturas ("+limit+")";break;}
            ExplorationModel.Decision d=model.next(obs.cands);
            if(d.type==ExplorationModel.Decision.Type.STOP){
                resultReason=d.reason;
                result=d.reason.equals("completo")?"finished_complete":"finished_partial";
                break;
            }
            Obs before=obs;long min=300;
            switch(d.type){
                case CLICK:{
                    guard();
                    model.performed(d);
                    UiNode u=obs.top.get(d.cand.nodeIndex);
                    JSONObject selected=selectorOf(u);
                    boolean ok=obs.topLive.get(d.cand.nodeIndex).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    if(!ok){model.actionFailed(d,"el control rechazó el clic");Obs old=obs;obs=settle(100);if(old.bitmap!=null&&old.bitmap!=obs.bitmap&&!old.bitmap.isRecycled())old.bitmap.recycle();continue;}
                    route.put(selected);
                    break;}
                case BACK:
                    model.performed(d);route.put(new JSONObject().put("kind","back"));
                    if(!tryBack()){model.error("«Atrás» no respondió; se reinicia la app","");if(!tryLaunchFresh())break loop;min=1500;}
                    break;
                case RESTART:
                    model.performed(d);route.put(new JSONObject().put("kind","restart"));if(!tryLaunchFresh())break loop;min=1500;break;
                default:break;
            }
            obs=settleAfter(before,min);
            if(before.bitmap!=null&&before.bitmap!=obs.bitmap&&!before.bitmap.isRecycled())before.bitmap.recycle();
        }
        if(stop)result="stopped";
        if(resultReason.isEmpty()&&System.currentTimeMillis()>=deadline)resultReason="tiempo máximo de sesión";
        model.stopReason=resultReason;
        if(obs!=null&&obs.bitmap!=null&&!obs.bitmap.isRecycled())obs.bitmap.recycle();
        return result;
    }

    // ---------- replay of a saved route (design comparison) ----------
    private String runReplay()throws Exception{
        String result="finished_partial";
        JSONArray steps=replay.getJSONArray("steps");
        long deadline=System.currentTimeMillis()+SESSION_MS;
        Obs obs=settle(1200);
        int count=0;
        while(!stop&&count<=40&&System.currentTimeMillis()<deadline){
            if(!obs.owned){result="external_boundary";break;}
            String stem=String.format(Locale.ROOT,"screen-%04d",replayStates.size()+1);
            ByteArrayOutputStream png=new ByteArrayOutputStream();obs.bitmap.compress(Bitmap.CompressFormat.PNG,100,png);
            write(stem+".png",png.toByteArray());write(stem+".xml",obs.xml.getBytes(StandardCharsets.UTF_8));
            replayStates.add(new JSONObject().put("image",stem+".png").put("xml",stem+".xml").put("id",obs.sig.structureKey+PerceptualHash.toHex(obs.hash).substring(0,16)).put("route_step",count));
            status="Explorando · "+replayStates.size()+" capturas · "+count+" acciones";
            if(count>=steps.length()){result="replay_complete";break;}
            JSONObject step=steps.getJSONObject(count);
            guard();
            String kind=step.getString("kind");
            if(kind.equals("back")){back();}
            else if(kind.equals("restart")){launch(true);}
            else if(kind.equals("scroll")){guard();shell("input","-d",String.valueOf(displayId),"swipe","360","1250","360","450","350");}
            else{
                UiNode chosen=null;int found=0,index=-1;
                for(int i=0;i<obs.top.size();i++){UiNode u=obs.top.get(i);if(u.clickable&&u.enabled&&u.visible&&!u.editable&&!u.password&&ActionPolicy.allowed(u.label)&&matches(u,step)){chosen=u;found++;index=i;}}
                if(found!=1)throw new IOException("Recorrido interrumpido en paso "+count+": selector encontrado "+found+" veces");
                if(!obs.topLive.get(index).performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new IOException("No se pudo pulsar el paso "+count);
            }
            route.put(step);count++;
            Obs before=obs;
            obs=settleAfter(before,kind.equals("restart")?1500:300);
            if(before.bitmap!=null&&before.bitmap!=obs.bitmap&&!before.bitmap.isRecycled())before.bitmap.recycle();
        }
        if(obs!=null&&obs.bitmap!=null&&!obs.bitmap.isRecycled())obs.bitmap.recycle();
        return result;
    }

    // ---------- export ----------
    private AuditReport.Meta meta(){
        AuditReport.Meta m=new AuditReport.Meta();
        m.pkg=pkg;m.appVersion=report.optString("app_version");m.device=android.os.Build.MODEL+" · Android "+android.os.Build.VERSION.RELEASE+" (API "+android.os.Build.VERSION.SDK_INT+")";
        m.status=report.optString("status");m.stopReason=model.stopReason;m.durationMs=elapsedBefore+System.currentTimeMillis()-startedAt;
        m.captureLimit=limit;m.resumed=report.optBoolean("resumed");m.mode="explore";
        m.settleSamples=settleCount;m.avgSettleMs=settleCount==0?0:settleTotal/settleCount;m.maxSettleMs=settleMax;
        return m;
    }
    private static final String AUDIT="# Evidencia para auditoria visual\n\nLee REPORT.md y agent.json junto a las capturas (screens/<seccion>/NNN-titulo.png y su .xml).\nCada pantalla tiene id, seccion, ruta de acceso y nivel. Cada captura aporta informacion nueva: las repeticiones se descartaron por jerarquia y huella visual.\nComprueba jerarquia, espaciado, tipografia, contraste, consistencia y etiquetas accesibles.\nCita imagen y elemento en cada hallazgo, separa observaciones de propuestas y relaciona con codigo solo tras inspeccionarlo.\nLa cobertura es parcial: no se pulsan acciones sensibles ni campos de texto. No se restauran datos, scroll, sesion ni contenido remoto. Replay repite acciones, no garantiza un estado identico.\nPara comparar usa visual_audit.py con los ZIP antes/despues. Un cambio de pixeles no demuestra una mejora de diseno.\n";
    /** Everything except the screenshots, which are already on disk. */
    private Map<String,byte[]> generated()throws Exception{
        Map<String,byte[]> out=new LinkedHashMap<>();
        JSONObject plan=new JSONObject().put("schema",1).put("package",pkg).put("steps",route);
        out.put("route.json",plan.toString(2).getBytes(StandardCharsets.UTF_8));
        out.put("AUDIT.md",AUDIT.getBytes(StandardCharsets.UTF_8));
        JSONObject agent=new JSONObject(report.toString());
        agent.put("route",plan);
        if(replay!=null){
            agent.put("states",new JSONArray(replayStates)).put("edges",new JSONArray());
            StringBuilder html=new StringBuilder("<!doctype html><meta name='viewport' content='width=device-width'><title>APK Explorer</title><style>body{background:#101115;color:#e8e9f0;font:16px sans-serif;padding:20px}img{max-width:100%;max-height:75vh;border-radius:16px}article{display:inline-block;margin:12px}a{color:#c9bdff}</style><h1>APK Explorer · repetición</h1><p>").append(ActionPolicy.html(pkg)).append("</p>");
            for(int i=0;i<replayStates.size();i++){String image=replayStates.get(i).getString("image");html.append("<article><h2>").append(i+1).append("</h2><a href='").append(image).append("'><img src='").append(image).append("'></a></article>");}
            out.put("index.html",html.toString().getBytes(StandardCharsets.UTF_8));
        }else{
            JSONArray states=new JSONArray();
            for(ExplorationModel.Screen s:model.screens.values()){
                if(!s.captured)continue;
                states.put(new JSONObject().put("image",s.image).put("xml",s.xml).put("id",s.sig.structureKey+PerceptualHash.toHex(s.hash).substring(0,16))
                    .put("route_step",captureStep.containsKey(s.id)?captureStep.get(s.id):0).put("screen_id",s.id).put("title",s.title).put("section",s.section)
                    .put("depth",s.depth).put("visits",s.visits).put("route",new JSONArray(model.routeTo(s))));
            }
            agent.put("states",states).put("edges",model.toJson().getJSONArray("edges"));
            agent.put("duplicate_frames_skipped",model.duplicateObservations).put("unique_screens",model.capturedCount())
                .put("screens_found",model.screens.size()).put("actions",model.actionsDone).put("navigation_actions",model.navigationActions)
                .put("restarts",model.restarts).put("loops",model.loops).put("pending_actions",model.pendingCount()).put("stop_reason",model.stopReason)
                .put("coverage_complete",model.stopReason.equals("completo")&&model.pendingCount()==0)
                .put("duration_ms",meta().durationMs).put("avg_settle_ms",meta().avgSettleMs).put("max_settle_ms",meta().maxSettleMs);
            JSONArray errors=new JSONArray();
            for(ExplorationModel.Event e:model.events)errors.put(new JSONObject().put("type",e.type).put("message",e.message).put("screen",e.screen).put("step",e.step));
            agent.put("events",errors);
            out.put("exploration.json",model.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
            out.put("REPORT.md",AuditReport.markdown(model,meta()).getBytes(StandardCharsets.UTF_8));
            out.put("index.html",AuditReport.html(model,meta()).getBytes(StandardCharsets.UTF_8));
        }
        out.put("agent.json",agent.toString(2).getBytes(StandardCharsets.UTF_8));
        return out;
    }
    public byte[] zip()throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(out)){
            Map<String,byte[]> extra;
            try{extra=generated();}catch(Exception e){throw new IOException(e);}
            for(Map.Entry<String,byte[]> e:extra.entrySet()){z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue());z.closeEntry();}
            addDir(z,dir,"");
        }
        return out.toByteArray();
    }
    private void addDir(ZipOutputStream z,File folder,String prefix)throws IOException{
        File[] kids=folder.listFiles();if(kids==null)return;
        Arrays.sort(kids);
        for(File k:kids){
            String name=prefix+k.getName();
            if(k.isDirectory()){if(prefix.isEmpty()&&!k.getName().equals("screens"))continue;addDir(z,k,name+"/");}
            else if(name.matches("(screens/.+\\.(png|xml)|screen-[0-9]+\\.(png|xml))")){z.putNextEntry(new ZipEntry(name));z.write(java.nio.file.Files.readAllBytes(k.toPath()));z.closeEntry();}
        }
    }

    /** Explicit shell QA entrypoint; not an exported Android component. */
    public static void main(String[] args)throws Exception {
        Looper.prepareMainLooper();
        Object activityThread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
        Context ctx=(Context)activityThread.getClass().getMethod("getSystemContext").invoke(activityThread);
        Engine e;
        if(args.length>3&&args[3].equals("resume"))e=new Engine(ctx,args[0],Integer.parseInt(args[1]),true);
        else if(args.length>3)e=new Engine(ctx,args[0],Integer.parseInt(args[1]),new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[3])),StandardCharsets.UTF_8));
        else e=new Engine(ctx,args[0],Integer.parseInt(args[1]));
        new Thread(()->{e.run();try{java.nio.file.Files.write(java.nio.file.Path.of(args[2]),e.zip());System.out.println(e.status);}catch(Exception ex){ex.printStackTrace();}System.exit(e.report.optString("status").equals("error")?1:0);}).start();
        Looper.loop();
    }
}
