package com.vidadomu.nexus

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.assistant.ChatCopyEntry
import com.vidadomu.nexus.assistant.ChatCopySheet
import com.vidadomu.nexus.cut.NexusCut
import com.vidadomu.nexus.cut.command.*
import com.vidadomu.nexus.cut.export.*
import com.vidadomu.nexus.cut.model.*
import com.vidadomu.nexus.ui.theme.NexusTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Real target APK, existing identity. No LLM calls, project edits, imports or Library writes. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@RunWith(AndroidJUnit4::class)
class CutFeedbackDeviceTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = inst.targetContext.applicationContext as NexusApplication
    private fun activity(): MainActivity {
        runBlocking { app.ready.await() }
        return inst.startActivitySync(Intent(inst.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    }
    private fun waitFor(predicate: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 30_000
        while (!predicate()) { assertTrue("UI condition was not reached", SystemClock.uptimeMillis() < until); SystemClock.sleep(100) }
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        fun add(n: AccessibilityNodeInfo?) { if (n == null) return; out += n; for (i in 0 until n.childCount) add(n.getChild(i)) }
        add(inst.uiAutomation.rootInActiveWindow); return out
    }
    private fun node(label: String) = nodes().firstOrNull { it.contentDescription?.toString() == label || it.text?.toString() == label }
    private fun click(label: String) {
        waitFor { node(label) != null }
        var n = requireNotNull(node(label))
        while (!n.isClickable && n.parent != null) n = n.parent
        assertTrue("Could not click $label", n.performAction(AccessibilityNodeInfo.ACTION_CLICK)); inst.waitForIdleSync()
    }
    private fun bounds(label: String) = Rect().also { requireNotNull(node(label)).getBoundsInScreen(it) }
    private fun drag(x: Float, y: Float, dx: Float, dy: Float) {
        val start = SystemClock.uptimeMillis()
        for (i in 0..12) {
            val action = when (i) { 0 -> MotionEvent.ACTION_DOWN; 12 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
            val e = MotionEvent.obtain(start, start + i * 30, action, x + dx * i / 12, y + dy * i / 12, 0)
            e.source = InputDevice.SOURCE_TOUCHSCREEN; assertTrue(inst.uiAutomation.injectInputEvent(e, true)); e.recycle()
        }
        inst.waitForIdleSync()
    }
    private fun pinch(r: Rect) {
        val start = SystemClock.uptimeMillis()
        val props = Array(2) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        fun send(action: Int, count: Int, span: Float, tick: Int) {
            val coordinates = Array(count) { i -> MotionEvent.PointerCoords().apply {
                x = r.exactCenterX() + (if (i == 0) -span else span); y = r.exactCenterY(); pressure = 1f; size = 1f
            } }
            val e = MotionEvent.obtain(start, start + tick * 30, action, count, props, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            assertTrue(inst.uiAutomation.injectInputEvent(e, true)); e.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 1, 30f, 0)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 30f, 1)
        for (i in 2..12) send(MotionEvent.ACTION_MOVE, 2, 30f + i * 8, i)
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 126f, 13)
        send(MotionEvent.ACTION_UP, 1, 126f, 14)
        inst.waitForIdleSync()
    }

    @Test fun copyAllAndMultipleMessagesWithoutTruncating() {
        val activity = activity()
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val old = clipboard.primaryClip
        val entries = listOf(ChatCopyEntry("1", "Tú", "Primero"), ChatCopyEntry("2", "Nexus", "Respuesta completa\n".repeat(200)), ChatCopyEntry("3", "Tú", "Último"))
        fun open() { inst.runOnMainSync { activity.setContent { NexusTheme { ChatCopySheet(entries, onDismiss = {}) } } }; inst.waitForIdleSync() }
        try {
            open(); click("Copiar todo")
            assertEquals(entries.joinToString("\n\n") { "${it.speaker}\n${it.text}" }, clipboard.primaryClip!!.getItemAt(0).text.toString())
            val boxes = nodes().filter { it.className?.toString() == "android.widget.CheckBox" }
            assertTrue(boxes.size >= 4)
            boxes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            inst.waitForIdleSync()
            for (i in 1..2) { nodes().filter { it.className?.toString() == "android.widget.CheckBox" }[i].performAction(AccessibilityNodeInfo.ACTION_CLICK); inst.waitForIdleSync() }
            click("Copiar selección")
            assertEquals(entries.take(2).joinToString("\n\n") { "${it.speaker}\n${it.text}" }, clipboard.primaryClip!!.getItemAt(0).text.toString())
        } finally { inst.runOnMainSync { if (old != null) clipboard.setPrimaryClip(old) else clipboard.clearPrimaryClip() }; activity.finish() }
    }

    @Test fun previewGesturesAndAgentMinimizeDoNotEditTheMontage() {
        val id = InstrumentationRegistry.getArguments().getString("cutProjectId") ?: throw AssertionError("Pass the current montage id explicitly")
        val activity = activity()
        val session = runBlocking { NexusCut.workspace(app).session(id) }
        val original = session.state.value.project
        inst.runOnMainSync { NexusCut.open(id) }
        waitFor { node("Previsualización del montaje") != null }
        try {
            val before = bounds("Previsualización del montaje")
            val handle = bounds("Tamaño de la previsualización y la línea de tiempo")
            drag(handle.exactCenterX(), handle.exactCenterY(), 0f, 100f)
            assertTrue(bounds("Previsualización del montaje").height() > before.height())
            pinch(bounds("Previsualización del montaje"))
            waitFor { node("Ajustar") != null }
            click("Ajustar")
            click("Reproducir")
            waitFor { node("Pausa") != null }
            val timeline = bounds("Línea de tiempo del montaje")
            drag(timeline.exactCenterX(), timeline.top + 10f, 150f, 0f)
            waitFor { node("Pausa") != null }
            click("Pausa")
            val visible = bounds("Previsualización del montaje").height()
            click("Minimizar agente")
            waitFor { node("Mostrar agente del montaje") != null }
            assertTrue(bounds("Previsualización del montaje").height() > visible)
            click("Mostrar agente del montaje")
            waitFor { node("Minimizar agente") != null }
            assertEquals(original, session.state.value.project)
        } finally { if (node("Pausa") != null) click("Pausa"); activity.finish() }
    }

    @Test fun sequentialRendersProduceTwoCompleteMp4FilesWithAudio() {
        assumeFalse("Do not interrupt a user's export", ExportManager.state.value is ExportState.Running)
        val activity = activity()
        val dir = File(app.cacheDir, "cut-feedback-${System.nanoTime()}").apply { mkdirs() }
        val source = File(dir, "source.mp4").apply { inst.context.assets.open("media-edit/red.mp4").use { input -> outputStream().use { input.copyTo(it) } } }
        val video = Clip("video", "m", ClipKind.VIDEO, 0, 3_000_000, linkId = "linked")
        val audio = video.copy(id = "audio", kind = ClipKind.AUDIO)
        val project = CutProjects.create("Prueba privada de exportación", 1, IdGenerator.sequential()).copy(
            media = listOf(MediaRef("m", source.toURI().toString(), "Fixture", MediaKind.VIDEO, 3_000_000, 320, 240, hasVideo = true, hasAudio = true)),
            timeline = Timeline(listOf(Track("v", TrackKind.VIDEO, "V1", TrackRole.PICTURE, listOf(video))),
                listOf(Track("a", TrackKind.AUDIO, "A1", TrackRole.DIALOGUE, listOf(audio)))))
        val original = project.copy()
        val jobs = (0..1).map { i -> ExportJob(project, ExportMath.plan(project.settings, project.export, 1_000_000), File(dir, "part-$i.mp4"), setOf("m"),
            fromUs = i * 1_000_000L, untilUs = (i + 1) * 1_000_000L) { "test-$i" } }
        inst.runOnMainSync { assertTrue(ExportManager.enqueueBatch(jobs)); ExportService.start(app) }
        val until = SystemClock.uptimeMillis() + 120_000
        while (ExportManager.state.value is ExportState.Running && SystemClock.uptimeMillis() < until) SystemClock.sleep(200)
        val done = ExportManager.state.value
        assertTrue("Export did not finish: $done", done is ExportState.Done)
        assertEquals(listOf("test-0", "test-1"), (done as ExportState.Done).assetIds)
        for (j in jobs) {
            val ex = MediaExtractor()
            try { ex.setDataSource(j.destination.path)
                val formats = (0 until ex.trackCount).map { ex.getTrackFormat(it) }
                assertTrue(formats.any { it.getString(MediaFormat.KEY_MIME)!!.startsWith("video/") })
                assertTrue(formats.any { it.getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") })
            } finally { ex.release() }
        }
        assertEquals(original, project)
        File(app.cacheDir, "cut-feedback-exports.txt").writeText(jobs.joinToString("\n") { it.destination.path })
        ExportManager.acknowledge(); activity.finish()
    }
}
