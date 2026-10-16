package com.vidadomu.nexus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.components.NexusChrome
import com.vidadomu.nexus.ui.create.*
import com.vidadomu.nexus.ui.theme.NexusTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CanvasMinimalUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun changingModelAcrossModalitiesKeepsThePromptAndAttachments() {
        check(BuildConfig.OFFLINE_QA)
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
        runBlocking {app.ready.await();app.database.loading.first {!it}}
        val image=LocalQaFixture.model(app,"references")
        val video=LocalQaFixture.model(app,"motion")
        val media=LocalQaFixture.media(app,image,1)
        compose.runOnIdle {
            val vm=CreateViewModel(app)
            vm.loadLocalPreview(image,media)
            vm.updatePrompt("Conservar este encuadre")
            vm.selectModel(video)
            assertEquals(Modality.VIDEO,vm.uiState.value.selectedModality)
            assertEquals(video.id,vm.uiState.value.selectedModel?.id)
            assertEquals("Conservar este encuadre",vm.uiState.value.prompt)
            assertEquals(media,vm.uiState.value.attachedMedia)
            vm.selectModel(image)
            assertEquals(Modality.IMAGE,vm.uiState.value.selectedModality)
        }
    }

    @Test fun parametersRestoreMediaAndCatalogRemainReachableWithoutGeneration() {
        check(BuildConfig.OFFLINE_QA)
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
        runBlocking { app.ready.await();app.database.loading.first { !it } }
        val initial=app.generationRepository.allJobs.value.map {it.id}
        val model=LocalQaFixture.model(app,"references").copy(parameters=listOf(
            ModelParameter("seed","Semilla",ParameterType.INTEGER,"42"),
            ModelParameter("test_advanced","Opción avanzada",ParameterType.BOOLEAN,"false",isAdvanced=true)))
        val media=LocalQaFixture.media(app,model,3)
        lateinit var vm:CreateViewModel
        var initialMedia:List<AttachedMedia> = emptyList()
        compose.runOnIdle {
            vm=CreateViewModel(app);vm.loadLocalPreview(model,media);vm.updateParameter("seed","99")
            initialMedia=vm.uiState.value.attachedMedia
        }
        var catalog=false
        compose.setContent { NexusTheme { NexusChrome({},{fail("No generation requested")}) {
            CreateScreen({fail("No generation requested")},{catalog=true},vm)
        } } }
        compose.onNodeWithContentDescription("Prompt de creación").assertIsDisplayed()
        compose.onNodeWithContentDescription("Parámetros del modelo").performClick()
        compose.onNodeWithContentDescription("Restaurar Semilla").performClick()
        compose.runOnIdle { assertEquals("42",vm.uiState.value.parameters["seed"]) }
        compose.onNodeWithText("Opción avanzada").assertExists()
        capture("parametros")
        compose.onNodeWithContentDescription("Cerrar Parámetros").performClick()
        compose.onNodeWithText("Añadir").performClick()
        compose.onNodeWithText("Medios de entrada").assertIsDisplayed()
        capture("medios")
        compose.onNodeWithContentDescription("Cerrar Medios de entrada").performClick()
        compose.onNodeWithContentDescription("Más opciones del Lienzo").performClick()
        compose.onNodeWithText("Todos los modelos").performClick()
        compose.runOnIdle {
            assertTrue(catalog)
            assertEquals(initialMedia,vm.uiState.value.attachedMedia)
            assertEquals(initial,app.generationRepository.allJobs.value.map {it.id})
        }
    }

    private fun capture(name:String) {
        compose.waitForIdle()
        Thread.sleep(1000)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val directory=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"ui-audit/phase3-controls").apply {mkdirs()}
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        java.io.File(directory,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
}
