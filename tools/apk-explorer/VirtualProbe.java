import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Base64;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.*;
import java.lang.reflect.Constructor;
import org.xmlpull.v1.XmlSerializer;

/** Shell-only probe. Never falls back to the physical display. No installed app. */
public final class VirtualProbe {
    private static void attr(XmlSerializer x, String k, Object v) throws Exception {
        x.attribute("", k, v == null ? "" : v.toString());
    }
    private static void node(XmlSerializer x, AccessibilityNodeInfo n, int depth) throws Exception {
        if (n == null || depth > 80) return;
        x.startTag("", "node");
        attr(x,"text",n.getText()); attr(x,"content-desc",n.getContentDescription());
        attr(x,"resource-id",n.getViewIdResourceName()); attr(x,"class",n.getClassName());
        attr(x,"package",n.getPackageName()); attr(x,"clickable",n.isClickable());
        attr(x,"enabled",n.isEnabled()); attr(x,"scrollable",n.isScrollable());
        attr(x,"password",n.isPassword()); attr(x,"selected",n.isSelected());
        attr(x,"checked",n.isChecked()); attr(x,"focusable",n.isFocusable());
        Rect r = new Rect(); n.getBoundsInScreen(r);
        attr(x,"bounds","["+r.left+","+r.top+"]["+r.right+","+r.bottom+"]");
        for(int i=0;i<n.getChildCount();i++) node(x,n.getChild(i),depth+1);
        x.endTag("", "node");
    }
    public static void main(String[] args) throws Exception {
        int display = Integer.parseInt(args[0]);
        if (display <= 0) throw new IllegalArgumentException("Virtual display required");
        HandlerThread thread = new HandlerThread("apk-explorer-probe"); thread.start();
        UiAutomation ui = null;
        try {
            Class<?> connection = Class.forName("android.app.IUiAutomationConnection");
            Object service = Class.forName("android.app.UiAutomationConnection").getConstructor().newInstance();
            Constructor<UiAutomation> ctor = UiAutomation.class.getDeclaredConstructor(int.class, Looper.class, connection);
            ctor.setAccessible(true);
            ui = ctor.newInstance(display, thread.getLooper(), service);
            UiAutomation.class.getMethod("connect", int.class).invoke(ui, 1);
            AccessibilityServiceInfo info = ui.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            ui.setServiceInfo(info);
            System.out.println("READY"); System.out.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while ((line = reader.readLine()) != null && !line.equals("quit")) {
                try {
                    byte[] result;
                    if (line.equals("xml")) {
                        StringWriter out = new StringWriter(); XmlSerializer x = Xml.newSerializer();
                        x.setOutput(out); x.startDocument("UTF-8",true); x.startTag("","hierarchy");
                        attr(x,"display-id",display);
                        java.util.List<AccessibilityWindowInfo> windows = ui.getWindowsOnAllDisplays().get(display);
                        if (windows == null || windows.isEmpty()) throw new IOException("No virtual windows");
                        for (AccessibilityWindowInfo w : windows) node(x,w.getRoot(),0);
                        x.endTag("","hierarchy"); x.endDocument();
                        result = out.toString().getBytes("UTF-8");
                    } else if (line.equals("png")) {
                        Bitmap b = ui.takeScreenshot();
                        if(b == null) throw new IOException("Virtual screenshot unavailable");
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        b.compress(Bitmap.CompressFormat.PNG,100,out); b.recycle(); result = out.toByteArray();
                    } else throw new IllegalArgumentException("Unknown operation");
                    System.out.println("OK " + Base64.encodeToString(result,Base64.NO_WRAP));
                } catch(Exception e) { System.out.println("ERROR " + e); }
                System.out.flush();
            }
        } finally {
            if(ui != null) UiAutomation.class.getMethod("disconnect").invoke(ui);
            thread.quitSafely();
        }
        System.exit(0);
    }
}
