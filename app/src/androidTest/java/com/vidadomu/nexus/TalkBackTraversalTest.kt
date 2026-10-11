package com.vidadomu.nexus

import android.app.UiAutomation
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.io.File

/** Focus actions are obtained from Android's accessibility tree, without guessed coordinates. */
class TalkBackTraversalTest {
    @Test fun enabledTalkBackCanFocusAndOpenTheFiveTabs() {
        check(BuildConfig.OFFLINE_QA)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as NexusApplication
        check(app.packageName.endsWith(".uiqa"))
        val enabled=Settings.Secure.getString(app.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        check(enabled.contains("com.google.android.marvin.talkback")) {"TalkBack must be enabled on the isolated emulator"}
        val automation=instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo=automation.serviceInfo.apply {flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
        val report=StringBuilder("TalkBack service enabled; actual Android accessibility focus. Audible speech is not tested.\n")
        fun nodes(root:AccessibilityNodeInfo):List<AccessibilityNodeInfo> {
            val all=mutableListOf<AccessibilityNodeInfo>();val queue=java.util.ArrayDeque<AccessibilityNodeInfo>();queue.add(root)
            while(queue.isNotEmpty()) {val n=queue.removeFirst();all.add(n);for(i in 0 until n.childCount) n.getChild(i)?.let(queue::add)}
            return all
        }
        ActivityScenario.launch<MainActivity>(Intent(app,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use {
            android.os.SystemClock.sleep(2500)
            for(label in listOf("Asistente","Lienzo","Modelos","Historial","Biblioteca")) {
                var root=automation.rootInActiveWindow
                repeat(30) {if(root?.packageName?.toString()!=app.packageName) {
                    android.os.SystemClock.sleep(100);root=automation.rootInActiveWindow
                }}
                val visible=checkNotNull(root)
                val available=nodes(visible)
                File(app.getExternalFilesDir(null),"ui-audit/talkback-tree.txt").apply {parentFile?.mkdirs()}.writeText(
                    "Root: ${visible.packageName}\n"+available.joinToString("\n") {
                        "${it.className}: text=${it.text}; description=${it.contentDescription}; clickable=${it.isClickable}; actions=${it.actionList}"
                    })
                check(visible.packageName?.toString()==app.packageName) {"Another window blocks TalkBack: ${visible.packageName}"}
                // Compose exposes a label child beneath the actual selectable Tab node.
                val named=available.firstOrNull {node->node.contentDescription?.toString()==label}
                    ?: error("Accessible label missing: $label; see talkback-tree.txt")
                val tab=generateSequence(named) {it.parent}.firstOrNull {it.isClickable}
                    ?: error("Accessible tab has no clickable ancestor: $label")
                check(tab.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)) {"Cannot focus $label"}
                var focus=automation.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                repeat(20) {if(focus!=tab) {
                    android.os.SystemClock.sleep(100)
                    focus=automation.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                }}
                check(focus==tab) {"Focus did not reach $label"}
                val bounds=android.graphics.Rect();tab.getBoundsInScreen(bounds)
                report.append("$label: focus OK, bounds=$bounds, role=${tab.className}\n")
                check(tab.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {"Cannot open $label"}
                android.os.SystemClock.sleep(600)
            }
        }
        File(app.getExternalFilesDir(null),"ui-audit/talkback.txt").writeText(report.toString())
    }
}
