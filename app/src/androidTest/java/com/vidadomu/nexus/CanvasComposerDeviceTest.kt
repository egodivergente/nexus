package com.vidadomu.nexus

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.create.*
import com.vidadomu.nexus.ui.flow.*
import com.vidadomu.nexus.ui.theme.NexusTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real device, isolated sandbox and local media. OFFLINE_QA blocks all provider submissions. */
class CanvasComposerDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
    @Before fun ready() {
        check(BuildConfig.OFFLINE_QA);runBlocking {app.ready.await()}
        compose.runOnUiThread {compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
    }
    private fun media(): List<AttachedMedia> = listOf(0xff465ecf.toInt(),0xffcb8063.toInt()).mapIndexed {index,color ->
        val target=File(app.cacheDir,"composer-$index.png")
        val bitmap=Bitmap.createBitmap(160,120,Bitmap.Config.ARGB_8888).apply {eraseColor(color)}
        target.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        AttachedMedia("fixture-$index",android.net.Uri.fromFile(target).toString(),"image/png",MediaSlotType.REFERENCE_IMAGE,fieldKey="images")
    }
    private fun capture(name: String) {
        val dir=File(app.getExternalFilesDir(null),"canvas-qa").apply {mkdirs()}
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun clearUndoAndPasteKeepVisualAttachmentsAndComfortableTouchTargets() {
        val images=media()
        val field=CanvasInputField("images","Imágenes de referencia","image/*",false,4,MediaSlotType.REFERENCE_IMAGE,ContractMediaField("images","Imágenes de referencia","image/*",maximum=4))
        compose.setContent {NexusTheme {Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            var prompt by remember {mutableStateOf("Una escena de cine al atardecer")}
            StudioPromptField(prompt,{prompt=it},"Describe…",attachments={CanvasAttachmentEditor(field,images,true,{}, {},compact=true)})
        }}}
        compose.onNodeWithContentDescription("Borrar prompt").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        assertEquals("",compose.onNodeWithContentDescription("Prompt de creación").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        compose.onNodeWithContentDescription("Revisar Imágenes de referencia 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Restaurar prompt").performClick()
        compose.onNodeWithContentDescription("Prompt de creación").assertTextEquals("Una escena de cine al atardecer")
        val clipboard=app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        var previous: ClipData?=null
        compose.runOnIdle {previous=clipboard.primaryClip;clipboard.setPrimaryClip(ClipData.newPlainText("fixture","Travelling suave entre dunas"))}
        try {
            compose.onNodeWithContentDescription("Pegar prompt").performClick()
            compose.onNodeWithContentDescription("Prompt de creación").assertTextEquals("Travelling suave entre dunas")
            capture("composer-adjuntos.png")
        } finally {compose.runOnIdle {previous?.let(clipboard::setPrimaryClip) ?: clipboard.clearPrimaryClip()}}
    }
    @Test fun fullCanvasRendersOneComposerWithImagesAndCompactModelSettings() {
        val field=ContractMediaField("images","Imágenes de referencia","image/*",maximum=4,encoding="array")
        val model=NexusModel("composer-image","Seedream 5.0 Pro","fixture",organization="ByteDance",
            modalities=listOf(Modality.IMAGE),capabilities=listOf(ModelCapability.IMAGE_TO_IMAGE),contractEndpoint="/fixture",
            contractMedia=listOf(field),parameters=listOf(ModelParameter("ratio","Formato",ParameterType.ENUM,"16:9",options=listOf("16:9","1:1"))))
        lateinit var vm: CreateViewModel
        compose.runOnUiThread {vm=CreateViewModel(app);vm.loadLocalPreview(model,media());vm.updatePrompt("Mantén la composición y transforma la luz del atardecer")}
        compose.setContent {NexusTheme {CreateScreen({}, {},vm)}}
        compose.onNodeWithContentDescription("Revisar Imágenes de referencia 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Revisar Imágenes de referencia 2").assertIsDisplayed()
        capture("composer-lienzo-completo.png")
        compose.onNodeWithContentDescription("Borrar prompt").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(2,vm.uiState.value.attachedMedia.size);assertEquals("",vm.uiState.value.prompt)}
        compose.onNodeWithContentDescription("Restaurar prompt").performClick()
    }
    @Test fun videoHasQuantityAndSeveralModelsWithoutSubmitting() {
        val model=NexusModel("minimax/h3-max-turbo/text-to-video","MiniMax Hailuo catalog long name","fixture",organization="MiniMax",
            modalities=listOf(Modality.VIDEO),capabilities=listOf(ModelCapability.TEXT_TO_VIDEO),parameters=emptyList())
        lateinit var vm: CreateViewModel
        compose.runOnUiThread {vm=CreateViewModel(app);vm.selectModel(model);vm.updatePrompt("Una escena de cine")}
        compose.setContent {NexusTheme {CreateScreen({}, {},vm)}}
        compose.onNodeWithText("H3 Max Turbo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Identidad exacta del modelo").performClick()
        compose.onNodeWithText(model.id).assertIsDisplayed()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("×1 · Resultados ▾").performClick()
        compose.onNodeWithText("×4").performClick()
        compose.onNodeWithText("×4 · Resultados ▾").assertIsDisplayed()
        capture("composer-video.png")
        compose.onNodeWithText("Varios modelos").performClick()
        compose.onAllNodesWithText("Varios modelos").onLast().assertIsDisplayed()
        compose.onAllNodesWithText("H3 Max Turbo").onLast().assertExists()
        assertEquals(Modality.VIDEO,vm.comparison.state.value.source.selectedModality)
        assertTrue(vm.comparison.state.value.catalog.all {Modality.VIDEO in it.modalities})
    }
    @Test fun flowUsesSameComposerWithSharedThumbnailsAndPersistsCount() {
        val store=FlowWorkspaceStore(app,AssistantRuntime.get(app).settings.activeProjectId())
        store.write(listOf(FlowStudioDraft(prompt="Luz de tarde sobre las dunas",modality=Modality.VIDEO)),media().map {FlowStudioReference(it.uriString,it.mimeType,"Referencia")})
        compose.setContent {NexusTheme {FlowStudioScreen({}, {})}}
        compose.onNodeWithContentDescription("Borrar prompt").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Revisar Referencia 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Restaurar prompt").performClick()
        compose.onNodeWithText("×1 ▾").performScrollTo().performClick()
        compose.onNodeWithText("×3").performClick()
        compose.onNodeWithText("×3 ▾").assertIsDisplayed()
        capture("composer-flow.png")
        compose.runOnIdle {
            val saved=FlowWorkspaceStore(app,AssistantRuntime.get(app).settings.activeProjectId()).read()
            assertEquals(3,saved.drafts.single().count);assertEquals(2,saved.references.size)
            assertEquals("Luz de tarde sobre las dunas",saved.drafts.single().prompt)
        }
    }
    @Test fun byteplusHelpShowsOwnBillingRouteAndAccessibleActionsWithLargeText() {
        var chosen: Modality?=null;var page: String?=null
        val model=NexusModel("seedance","Seedance","usr_byteplus-media",organization="ByteDance",modalities=listOf(Modality.VIDEO),capabilities=emptyList(),parameters=emptyList())
        compose.setContent {NexusTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                BytePlusCanvasSheet(app,listOf(model),{}, {chosen=it},{page=it})
            }
        }}
        compose.onNodeWithText("Tu cuenta BytePlus").assertIsDisplayed()
        compose.onNodeWithText("Crear vídeo").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(Modality.VIDEO,chosen)}
        compose.onNodeWithText("Consumo y cuotas").performScrollTo().performClick()
        compose.runOnIdle {assertEquals("BYTEPLUS",page)}
        compose.onNodeWithText("Configurar BytePlus").performScrollTo().assertIsDisplayed()
        capture("composer-byteplus-grande.png")
    }
}
