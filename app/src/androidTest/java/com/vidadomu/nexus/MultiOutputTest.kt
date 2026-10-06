package com.vidadomu.nexus

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.core.alexia.AlexiaBridge
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.output.ZoomableImageViewer
import com.vidadomu.nexus.ui.theme.NexusTheme
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Local files and intercepted HTTP only. Never touches the user's database or Alexia directory. */
class MultiOutputTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var folder: File
    @Before fun setup() {
        check(BuildConfig.OFFLINE_QA)
        folder = java.nio.file.Files.createTempDirectory(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.toPath(), "multi-output-").toFile()
    }
    @After fun cleanup() { folder.deleteRecursively() }
    private fun image(index: Int, color: Int): File = File(folder, "$index.png").also { file ->
        Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).let { bitmap ->
            bitmap.eraseColor(color)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun ready() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("output_loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("No se puede cargar la imagen").assertDoesNotExist()
    }

    @Test fun switchingThreeResultsShowsTheirOwnPixelsAndStopsLoading() {
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        val files = colors.mapIndexed { i, color -> image(i, color) }
        compose.setContent {
            NexusTheme {
                var selected by remember { mutableIntStateOf(0) }
                Column {
                    Row { files.indices.forEach { i -> Button(onClick = { selected = i }) { Text("Resultado ${i+1}") } } }
                    Box(Modifier.fillMaxWidth().height(240.dp)) {
                        ZoomableImageViewer("https://example.invalid/$selected.png", files[selected].toURI().toString())
                    }
                }
            }
        }
        listOf(0, 1, 2, 0, 2).forEach { i ->
            compose.onNodeWithText("Resultado ${i+1}").performClick()
            ready()
            val pixels = compose.onNodeWithTag("output_image").captureToImage().toPixelMap()
            val pixel = pixels[pixels.width/2, pixels.height/2]
            assertEquals(Color.red(colors[i])/255f, pixel.red, .03f)
            assertEquals(Color.green(colors[i])/255f, pixel.green, .03f)
            assertEquals(Color.blue(colors[i])/255f, pixel.blue, .03f)
        }
    }

    @Test fun changedResultWithSameLocalUriNeverLeavesOldSpinner() {
        val file = image(0, Color.BLUE)
        compose.setContent {
            NexusTheme {
                var selected by remember { mutableIntStateOf(0) }
                Column {
                    Button(onClick = { selected++ }) { Text("Siguiente") }
                    Box(Modifier.fillMaxWidth().height(240.dp)) {
                        ZoomableImageViewer("https://example.invalid/$selected.png", file.toURI().toString())
                    }
                }
            }
        }
        ready()
        repeat(3) { compose.onNodeWithText("Siguiente").performClick(); ready() }
    }

    @Test fun downloadsSaveEveryResultSeparatelyAndReuseOnlyItsOwnFile() {
        val bytes = listOf(image(0, Color.RED).readBytes(), image(1, Color.GREEN).readBytes(), image(2, Color.BLUE).readBytes())
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().method
            val index = chain.request().url.pathSegments.last().substringBefore('.').toInt()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(bytes[index].toResponseBody("image/png".toMediaType())).build()
        }.build()
        val job = GenerationJob("job", providerId="fixture", modelId="fixture", modality=Modality.IMAGE, prompt="same prompt")
        val outputs = bytes.indices.map { GenerationOutput("output-$it", job.id, Modality.IMAGE, "https://example.invalid/$it.png", mimeType="image/png") }
        val saved = outputs.map { AlexiaBridge.downloadMediaOutput(job, it, folder, client)!! }
        assertEquals(3, saved.distinct().size)
        saved.forEachIndexed { i, file -> assertArrayEquals(bytes[i], file.readBytes()) }
        outputs.forEachIndexed { i, output -> assertEquals(saved[i], AlexiaBridge.downloadMediaOutput(job, output, folder, client)) }
        assertEquals(listOf("GET", "GET", "GET"), requests)
    }
}
