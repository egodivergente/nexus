package com.vidadomu.nexus

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.core.database.NexusDatabase
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.output.captureVideoFrameToLocalUri
import com.vidadomu.nexus.ui.output.SplitWipeCompare
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Synthetic media and a separate temporary DB; never opens the user's history. */
class StudioIntegrationDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @org.junit.Before fun keepTestScreenAwake() {
        compose.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    private val target get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun requireQa() {check(BuildConfig.OFFLINE_QA && target.packageName.endsWith(".mediaqa"))}
    @Test fun extractedFrameIsPngWithTheExpectedImageAndCancelledCaptureLeavesNoFile()=runBlocking<Unit> {
        requireQa()
        val source=File(target.cacheDir,"studio-test-${UUID.randomUUID()}.mp4")
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open("media-edit/red.mp4").use {input -> source.outputStream().use {input.copyTo(it)}}
            val result=captureVideoFrameToLocalUri(target,Uri.fromFile(source).toString(),1000)!!
            val image=File(result.path!!)
            try {
                assertEquals(listOf(137,80,78,71,13,10,26,10),image.inputStream().use {input -> ByteArray(8).also {input.read(it)} }.map {it.toInt() and 255})
                val bitmap=BitmapFactory.decodeFile(image.absolutePath)
                assertNotNull(bitmap)
                val pixel=bitmap.getPixel(bitmap.width/2,bitmap.height/2)
                assertTrue(Color.red(pixel)>Color.blue(pixel)+100)
                bitmap.recycle()
            } finally { image.delete() }
            val dir=File(target.filesDir,"draft_media")
            val before=dir.list().orEmpty().toSet()
            val job=async(start=CoroutineStart.UNDISPATCHED) { captureVideoFrameToLocalUri(target,Uri.fromFile(source).toString(),1000) }
            job.cancelAndJoin()
            assertEquals(before,dir.list().orEmpty().toSet())
        } finally { source.delete() }
    }
    @Test fun malformedSourceReturnsFailureWithoutCreatingADraft()=runBlocking<Unit> {
        requireQa()
        val before=File(target.filesDir,"draft_media").list().orEmpty().toSet()
        assertNull(captureVideoFrameToLocalUri(target,"file:///unavailable/studio-test.mp4",1000))
        assertEquals(before,File(target.filesDir,"draft_media").list().orEmpty().toSet())
    }
    @Test fun compareSliderSupportsAccessibilityAndActuallyRevealsBothImages() {
        requireQa()
        fun image(color:Int):File {
            val file=File(target.cacheDir,"studio-compare-${UUID.randomUUID()}.png")
            val bitmap=android.graphics.Bitmap.createBitmap(100,100,android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
            return file
        }
        val left=image(Color.RED);val right=image(Color.BLUE)
        try {
            compose.setContent {MaterialTheme {SplitWipeCompare(Uri.fromFile(left).toString(),Uri.fromFile(right).toString(),
                modifier=Modifier.size(200.dp).testTag("compare"))}}
            compose.onNodeWithTag("compare").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
                androidx.compose.ui.semantics.ProgressBarRangeInfo(.5f,.05f.. .95f)))
            compose.waitUntil(10_000) {
                val pixels=compose.onNodeWithTag("compare").captureToImage().toPixelMap()
                pixels[pixels.width/4,pixels.height/2].red>.8f && pixels[pixels.width*3/4,pixels.height/2].blue>.8f
            }
            compose.onNodeWithTag("compare").performSemanticsAction(SemanticsActions.SetProgress) {it(.8f)}
            compose.onNodeWithTag("compare").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
                androidx.compose.ui.semantics.ProgressBarRangeInfo(.8f,.05f.. .95f)))
            val pixels=compose.onNodeWithTag("compare").captureToImage().toPixelMap()
            assertTrue(pixels[pixels.width*7/10,pixels.height/2].red>.8f)
            assertTrue(pixels[pixels.width*9/10,pixels.height/2].blue>.8f)
        } finally {left.delete();right.delete()}
    }
    @Test fun excelImportsOnTheAndroidParserWithoutExternalEntities() {
        requireQa()
        val sheet="""<worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>prompt</t></is></c></row><row r="2"><c r="A2" t="inlineStr"><is><t>Escena de prueba</t></is></c></row></sheetData></worksheet>"""
        fun excel(xml:String)=java.io.ByteArrayOutputStream().also {out -> java.util.zip.ZipOutputStream(out).use {zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"));zip.write(xml.toByteArray());zip.closeEntry()
        }}.toByteArray()
        assertEquals(listOf("Escena de prueba"),com.vidadomu.nexus.ui.create.PromptBatch.parse("test.xlsx",excel(sheet)))
        assertTrue(runCatching {com.vidadomu.nexus.ui.create.PromptBatch.parse("test.xlsx",excel("<!DOCTYPE a SYSTEM \"file:///private\">"+sheet))}.isFailure)
    }
    @Test fun completedPillCanAttachTheRealOutputAndDismissIt()=runBlocking<Unit> {
        requireQa()
        val app=target.applicationContext as NexusApplication
        app.ready.await()
        app.database.loadHistory()
        val id="studio-pill-${UUID.randomUUID()}"
        val job=GenerationJob(id,providerId="mock",modelId="studio-qa-render",modality=Modality.IMAGE,prompt="test",
            status=GenerationStatus.COMPLETED,completedAt=System.currentTimeMillis(),
            outputs=listOf(GenerationOutput("$id-out",id,Modality.IMAGE,"file:///unavailable/test.png")))
        var attached:Pair<String,String>?=null
        try {
            app.database.insertOrUpdateJob(job)
            compose.setContent {MaterialTheme {
                com.vidadomu.nexus.ui.components.LiveRenderPill(onOpenOutput={},onSendToAgent={j,o -> attached=j to o})
            }}
            compose.onNodeWithText("Al chat").performClick()
            compose.runOnIdle {assertEquals(id to "$id-out",attached)}
            compose.waitForIdle()
            compose.onNodeWithText("Al chat").assertDoesNotExist()
        } finally {app.database.deleteJob(id)}
    }
    @Test fun videoCaptureButtonPassesAPngToTheCanvasCallback()=runBlocking<Unit> {
        requireQa()
        val app=target.applicationContext as NexusApplication
        app.ready.await();app.database.loadHistory()
        val id="studio-viewer-${UUID.randomUUID()}"
        val source=File(target.cacheDir,"$id.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("media-edit/red.mp4").use {input ->source.outputStream().use {input.copyTo(it)}}
        val uri=Uri.fromFile(source).toString()
        val job=GenerationJob(id,providerId="mock",modelId="test-video",modality=Modality.VIDEO,prompt="test",
            status=GenerationStatus.COMPLETED,outputs=listOf(GenerationOutput("$id-out",id,Modality.VIDEO,uri,uri,"video/mp4",320,180,3f)))
        val captured=java.util.concurrent.atomic.AtomicReference<Pair<String,String?>?>(null)
        try {
            app.database.insertOrUpdateJob(job)
            compose.setContent {MaterialTheme {com.vidadomu.nexus.ui.output.OutputViewerScreen(id,{}, {media,mime ->captured.set(media to mime)})}}
            compose.onNodeWithText("Fotograma → Lienzo").performClick()
            compose.waitUntil(20_000) {captured.get()!=null}
            assertEquals("image/png",captured.get()!!.second)
            assertTrue(File(Uri.parse(captured.get()!!.first).path!!).length()>0)
        } finally {
            captured.get()?.first?.let {File(Uri.parse(it).path!!).delete()}
            app.database.deleteJob(id);source.delete()
        }
    }
    @Test fun whisperReusesModelForTwoIndependentTranscriptionsAndCanTrim()=runBlocking<Unit> {
        requireQa()
        val app=target.applicationContext as NexusApplication
        val store=com.vidadomu.nexus.assistant.WhisperModelStore(app)
        assertTrue("Preload the pinned public Whisper model",store.verifyExisting())
        val audio=File(target.cacheDir,"studio-speech-${UUID.randomUUID()}.wav")
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open("media-edit/jfk.wav").use {input -> audio.outputStream().use {input.copyTo(it)}}
            val samples=com.vidadomu.nexus.assistant.WhisperTranscription(app).decode(audio)
            repeat(2) {pass ->
                val started=android.os.SystemClock.elapsedRealtime()
                val text=withTimeout(180_000) {com.vidadomu.nexus.assistant.WhisperNative.run(store.model.path,samples)}.decodeToString()
                assertTrue(text.contains("country",ignoreCase=true))
                println("STUDIO_WHISPER_PASS_${pass+1}_MS="+(android.os.SystemClock.elapsedRealtime()-started))
            }
            com.vidadomu.nexus.assistant.WhisperNative.trimCache()?.get(10,java.util.concurrent.TimeUnit.SECONDS)
            val reloaded=withTimeout(180_000) {com.vidadomu.nexus.assistant.WhisperNative.run(store.model.path,samples)}.decodeToString()
            assertTrue(reloaded.contains("country",ignoreCase=true))
            com.vidadomu.nexus.assistant.WhisperNative.trimCache()?.get(10,java.util.concurrent.TimeUnit.SECONDS)
        } finally {audio.delete()}
    }
    @Test fun databaseUpdatesKeepOutputsAndOrderConsistentWithDisk()=runBlocking<Unit> {
        requireQa()
        val dir=File(target.cacheDir,"studio-db-${UUID.randomUUID()}").apply {mkdirs()}
        val isolated=object:ContextWrapper(target) {
            override fun getDatabasePath(name:String)=File(dir,name)
            override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory)
            override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?,handler:DatabaseErrorHandler?):SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath,factory,handler)
        }
        val db=NexusDatabase(isolated,loadOnOpen=false)
        try {
            db.loadHistory()
            val first=GenerationJob("a",providerId="mock",modelId="model",modality=Modality.IMAGE,prompt="test",startedAt=10)
            db.insertOrUpdateJob(first)
            db.insertOrUpdateJob(first.copy(id="b",startedAt=20))
            val outputs=mutableListOf(GenerationOutput("out","a",Modality.IMAGE,"https://example.invalid/result.png"))
            val complete=first.copy(status=GenerationStatus.COMPLETED,startedAt=30,completedAt=40,outputs=outputs)
            val expected=complete.copy(outputs=outputs.toList())
            db.insertOrUpdateJob(complete)
            outputs.clear()
            assertEquals(listOf("a","b"),db.jobsFlow.value.map {it.id})
            assertEquals(expected,db.getJobById("a"))
            assertEquals(expected,db.jobsFlow.value.first())
            db.deleteJob("a")
            assertEquals(listOf("b"),db.jobsFlow.value.map {it.id})
            db.loadHistory()
            assertEquals(listOf("b"),db.jobsFlow.value.map {it.id})
        } finally {db.close();dir.deleteRecursively()}
    }
}
