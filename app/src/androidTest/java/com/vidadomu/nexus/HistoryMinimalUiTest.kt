package com.vidadomu.nexus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.components.NexusChrome
import com.vidadomu.nexus.ui.generations.GenerationsScreen
import com.vidadomu.nexus.ui.theme.NexusTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HistoryMinimalUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun prepareAndDiagnosisRemainTwoTapsWithoutSubmitting() {
        check(BuildConfig.OFFLINE_QA)
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
        runBlocking {app.ready.await();app.database.loading.first {!it}}
        val fixture=GenerationJob("ui-history-fixture",providerId="mock_provider",modelId="modelo-prueba",
            modality=Modality.IMAGE,prompt="Prueba de historial",status=GenerationStatus.COMPLETED)
        runBlocking {app.database.insertOrUpdateJob(fixture)}
        var prepared:GenerationJob?=null
        compose.setContent {NexusTheme {NexusChrome({},{fail("No result selected")}) {
            GenerationsScreen({fail("No result selected")},{prepared=it})
        }}}
        compose.waitUntil(10_000) {app.generationRepository.allJobs.value.any {it.id==fixture.id}}
        val initial=app.generationRepository.allJobs.value.map {it.id}
        compose.onNodeWithContentDescription("Buscar en Historial").performClick()
        compose.onNodeWithText("Buscar modelo o descripción").performTextInput("Prueba de historial")
        compose.onNodeWithContentDescription("Opciones de «Prueba de historial»",substring=true).performClick()
        compose.onNodeWithText("Preparar de nuevo").performClick()
        compose.runOnIdle {assertEquals(fixture.id,prepared?.id)}
        compose.onNodeWithContentDescription("Opciones de «Prueba de historial»",substring=true).performClick()
        compose.onNodeWithText("Diagnóstico").performClick()
        compose.onNodeWithText("Diagnóstico de la tarea").assertIsDisplayed()
        compose.onNodeWithText("ID Tarea: ${fixture.id}").assertExists()
        compose.onNodeWithText("Cerrar").performClick()
        compose.runOnIdle {assertEquals(initial,app.generationRepository.allJobs.value.map {it.id})}
    }
}
