package com.vidadomu.nexus

import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.components.NexusChrome
import com.vidadomu.nexus.ui.library.LibraryScreen
import com.vidadomu.nexus.ui.output.OutputViewerScreen
import com.vidadomu.nexus.ui.theme.NexusTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Small generated test patterns, no network media and no generation calls. */
class MediaPreviewUiTest {
    @get:Rule val compose=createComposeRule()
    private fun fixture(type:Modality,asset:String,title:String):Pair<NexusApplication,String> {
        check(BuildConfig.OFFLINE_QA)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as NexusApplication
        runBlocking {app.ready.await();app.database.loading.first {!it}}
        val id="ui-media-${type.name.lowercase()}-fixture"
        val file=File(app.cacheDir,"$id-${asset.substringAfterLast('/')}")
        instrumentation.context.assets.open(asset).use {input->file.outputStream().use {input.copyTo(it)}}
        val output=GenerationOutput("$id-output",id,type,file.toURI().toString(),file.toURI().toString(),
            if(type==Modality.VIDEO) "video/mp4" else "audio/wav",durationSeconds=2f)
        runBlocking {app.database.insertOrUpdateJob(GenerationJob(id,providerId="mock_provider",modelId="fixture-${type.name.lowercase()}",
            modality=type,prompt=title,status=GenerationStatus.COMPLETED,outputs=listOf(output)))}
        compose.waitUntil(10_000) {app.generationRepository.allJobs.value.any {it.id==id}}
        return app to id
    }

    @Test fun videoTileRendersARealFrameAndDuration() {
        val (app,id)=fixture(Modality.VIDEO,"ui-audit-video.mp4","Vídeo de prueba local")
        val initial=app.generationRepository.allJobs.value.map {it.id}
        compose.setContent {NexusTheme {NexusChrome({},{}) {LibraryScreen({})}}}
        compose.onNodeWithContentDescription("Buscar en Biblioteca").performClick()
        compose.onNodeWithText("Buscar por modelo o descripción").performTextInput("Vídeo de prueba local")
        val tile=compose.onNodeWithContentDescription("Vídeo · Vídeo de prueba local · 0:02")
        tile.assertIsDisplayed().assertHasClickAction()
        val bounds=tile.fetchSemanticsNode().boundsInWindow
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        // Only colourful pixels well inside the tile count: neutral placeholder icons cannot pass.
        compose.waitUntil(15_000) {
            val bitmap=automation.takeScreenshot() ?: return@waitUntil false
            try {
                var coloured=0
                for(ix in 3..7) for(iy in 3..7) {
                    val x=(bounds.left+bounds.width*ix/10).toInt().coerceIn(0,bitmap.width-1)
                    val y=(bounds.top+bounds.height*iy/10).toInt().coerceIn(0,bitmap.height-1)
                    val pixel=bitmap.getPixel(x,y)
                    val channels=listOf(Color.red(pixel),Color.green(pixel),Color.blue(pixel))
                    if(channels.max()-channels.min()>80) coloured++
                }
                coloured>8
            } finally {bitmap.recycle()}
        }
        val folder=File(app.getExternalFilesDir(null),"ui-audit/media").apply {mkdirs()}
        val bitmap=checkNotNull(automation.takeScreenshot())
        File(folder,"video-frame.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
        compose.runOnIdle {assertTrue(initial.contains(id));assertEquals(initial,app.generationRepository.allJobs.value.map {it.id})}
    }

    @Test fun localAudioShowsItsDurationAndAnAccessiblePlayAction() {
        val (app,id)=fixture(Modality.AUDIO,"ui-audit-audio.wav","Audio de prueba local")
        val initial=app.generationRepository.allJobs.value.map {it.id}
        compose.setContent {NexusTheme {NexusChrome({},{}) {OutputViewerScreen(id,{}, { _, _ -> })}}}
        compose.waitUntil(10_000) {compose.onAllNodesWithText("0:02").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Reproducir audio").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("0:00").assertIsDisplayed()
        compose.runOnIdle {assertEquals(initial,app.generationRepository.allJobs.value.map {it.id})}
    }
}
