package com.vidadomu.nexus

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.flow.FlowReferences
import com.vidadomu.nexus.ui.create.*
import com.vidadomu.nexus.ui.theme.NexusTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic local files, isolated offline QA package, no submission or user's database. */
class CanvasRedesignDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
    private val source = ContractMediaField("source", "Imagen de origen", "image/*", true, 1)
    private val references = ContractMediaField("references", "Imágenes de referencia", "image/*", true, 4, "array")
    private fun model(field: ContractMediaField, id: String = "fixture") = NexusModel(id,"Modelo de prueba","crun",
        organization="Fixture",modalities=listOf(Modality.IMAGE),capabilities=emptyList(),parameters=emptyList(),
        contractEndpoint="/fixture",contractMedia=listOf(field),promptRequired=false,promptAccepted=false)
    @Before fun ready() {
        check(BuildConfig.OFFLINE_QA)
        runBlocking { app.ready.await() }
        compose.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    private fun image(color: Int): Uri {
        val target=File(app.cacheDir,"canvas-test-${java.util.UUID.randomUUID()}.png")
        val bitmap=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888).apply {eraseColor(color)}
        target.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
        return Uri.fromFile(target)
    }
    private fun vm(model: NexusModel): CreateViewModel {
        lateinit var result: CreateViewModel
        compose.runOnUiThread {result=CreateViewModel(app);result.selectModel(model)}
        return result
    }
    private fun awaitMedia(vm: CreateViewModel, count: Int) {
        compose.waitUntil(15000) {!vm.uiState.value.isPreparingMedia && vm.uiState.value.attachedMedia.size==count}
    }

    @Test fun batchImagesAppearIndividuallyCanBeInspectedAndRemoved() {
        val model=model(references)
        val vm=vm(model)
        val uris=listOf(image(Color.RED),image(Color.BLUE))
        compose.runOnUiThread {vm.addCanvasMedia(references.key,"crun/fixture",uris.map {it to "image/png"})}
        awaitMedia(vm,2)
        val field=CanvasInputPlan.fields(model).single()
        compose.setContent {NexusTheme {
            val state by vm.uiState.collectAsState()
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                CanvasAttachmentEditor(field,state.attachedMedia,true,{},vm::removeMedia)
            }
        }}
        compose.onNodeWithContentDescription("Revisar Imágenes de referencia 1").assertIsDisplayed().performClick()
        compose.onNodeWithText("image/png · preparado en el dispositivo").assertIsDisplayed()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithContentDescription("Revisar Imágenes de referencia 2").assertIsDisplayed()
        capture("canvas-adjuntos.png")
        compose.runOnUiThread {vm.removeMedia(vm.uiState.value.attachedMedia.first().id)}
        awaitMedia(vm,1)
        assertEquals("references",vm.uiState.value.attachedMedia.single().fieldKey)
        assertEquals(uris.last().toString(),vm.uiState.value.attachedMedia.single().uriString)
    }

    @Test fun upscaleCanvasHasOneSourceAndNoPromptOrReferenceControls() {
        val vm=vm(model(source))
        compose.setContent {NexusTheme {CreateScreen({}, {}, vm, {})}}
        compose.onNodeWithText("Imagen de origen").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Galería").assertIsDisplayed()
        compose.onNodeWithText("Biblioteca").assertIsDisplayed()
        compose.onNodeWithContentDescription("Prompt de creación").assertDoesNotExist()
        compose.onNodeWithText("Imágenes de referencia").assertDoesNotExist()
        capture("canvas-upscale.png")
    }

    @Test fun galleryButtonGoesStraightToTheAndroidPhotoPicker() {
        val model=model(source)
        val field=CanvasInputPlan.fields(model).single()
        compose.setContent {NexusTheme {Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            CanvasAttachmentEditor(field,emptyList(),true,{}, {})
        }}}
        compose.onNodeWithText("Galería").assertIsDisplayed().performClick()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            compose.waitUntil(10000) {
                val owner=automation.rootInActiveWindow?.packageName?.toString().orEmpty()
                owner.contains("media") || owner.contains("photopicker")
            }
        } finally {
            automation.executeShellCommand("input keyevent 4").close()
        }
    }

    @Test fun resultFromPreviousModelsPickerCannotAttachToTheNewModel() {
        val vm=vm(model(source,"old"))
        val uri=image(Color.GREEN)
        compose.runOnUiThread {
            vm.selectModel(model(source,"new"))
            vm.addCanvasMedia(source.key,"crun/old",listOf(uri to "image/png"))
        }
        compose.waitForIdle()
        assertTrue(vm.uiState.value.attachedMedia.isEmpty())
        assertFalse(vm.uiState.value.isPreparingMedia)
    }

    @Test fun sourceReplacementKeepsExactlyOneFile() {
        val vm=vm(model(source))
        val first=image(Color.RED);val second=image(Color.BLUE)
        compose.runOnUiThread {vm.addCanvasMedia(source.key,"crun/fixture",listOf(first to "image/png"))}
        awaitMedia(vm,1)
        compose.runOnUiThread {vm.addCanvasMedia(source.key,"crun/fixture",listOf(second to "image/png"))}
        compose.waitUntil(15000) {!vm.uiState.value.isPreparingMedia && vm.uiState.value.attachedMedia.singleOrNull()?.uriString==second.toString()}
        assertEquals(1,vm.uiState.value.attachedMedia.size)
    }

    @Test fun flowModeSwitchClearsIncompatibleIngredients() {
        val flow=FlowReferences.model(Modality.VIDEO)
        val vm=vm(flow)
        val uri=image(Color.RED)
        compose.runOnUiThread {
            vm.updateParameter("mode","Referencias")
            vm.addCanvasMedia("reference_ids",flow.providerId+"/"+flow.id,listOf(uri to "image/png"))
        }
        awaitMedia(vm,1)
        compose.runOnUiThread {vm.updateParameter("mode","Fotogramas")}
        assertTrue(vm.uiState.value.attachedMedia.isEmpty())
        assertEquals(listOf("initial_frame","end_frame"),CanvasInputPlan.fields(flow,vm.uiState.value.parameters).map {it.key})
    }
    private fun capture(name: String) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        val dir=File(app.getExternalFilesDir(null),"canvas-qa").apply {mkdirs()}
        File(dir,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
