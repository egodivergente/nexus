package com.vidadomu.nexus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.components.NexusChrome
import com.vidadomu.nexus.ui.create.LocalQaFixture
import com.vidadomu.nexus.ui.generations.GenerationsScreen
import com.vidadomu.nexus.ui.library.LibraryScreen
import com.vidadomu.nexus.ui.theme.NexusTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** V17 y U10 con fixtures aislados de OFFLINE_QA: sin proveedores, sin borrar archivos ni tocar preferencias guardadas. */
class LibraryHistoryRecoveryUiTest {
    @get:Rule val compose=createComposeRule()

    private fun app():NexusApplication {
        check(BuildConfig.OFFLINE_QA)
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
        runBlocking {app.ready.await();app.database.loading.first {!it}}
        return app
    }

    @Test fun emptySearchRecoversTheLibraryInOneTapWithoutTouchingPreferences() {
        val app=app()
        val model=LocalQaFixture.model(app,"references")
        val media=LocalQaFixture.media(app,model,1).single()
        val id="ui-library-recovery-fixture"
        runBlocking {app.database.insertOrUpdateJob(GenerationJob(id,providerId="mock_provider",modelId=model.id,modality=Modality.IMAGE,
            prompt="Recuperar biblioteca fixture",status=GenerationStatus.COMPLETED,
            outputs=listOf(GenerationOutput("ui-library-recovery-out",id,Modality.IMAGE,media.uriString,media.uriString,"image/png"))))}
        compose.waitUntil(10_000) {app.generationRepository.allJobs.value.any {it.id==id}}
        val prefs=app.getSharedPreferences("library_actions_v1",0)
        val before=prefs.all.toMap()
        val jobsBefore=app.generationRepository.allJobs.value.map {it.id}
        compose.setContent {NexusTheme {NexusChrome({},{}) {LibraryScreen({})}}}
        compose.onNodeWithText("Biblioteca").assertIsDisplayed()
        compose.onNodeWithContentDescription("Importar archivos").assertIsDisplayed()
        compose.onNodeWithContentDescription("Buscar en Biblioteca").performClick()
        compose.onNodeWithText("Buscar por modelo o descripción").performTextInput("zzz sin coincidencias")
        compose.onNodeWithText("Vídeo").performClick()
        compose.onNodeWithText("No hay resultados para «zzz sin coincidencias» con estos filtros.").assertIsDisplayed()
        compose.onNodeWithText("Quitar filtros y mantener la búsqueda").assertIsDisplayed()
        compose.onNodeWithText("Ver toda la biblioteca").performClick()
        compose.onAllNodesWithContentDescription("Imagen · Recuperar biblioteca fixture").onFirst().assertExists()
        compose.runOnIdle {
            assertEquals(before,prefs.all.toMap())
            assertEquals(jobsBefore,app.generationRepository.allJobs.value.map {it.id})
        }
    }

    @Test fun repeatedAttemptsAreDistinguishableWithoutOpeningThem() {
        val app=app()
        val prompt="SUBJECT & SCENE\nPrueba intentos fixture"
        val base=1_700_000_000_000L
        val jobs=(1..3).map {GenerationJob("ui-attempt-$it",providerId="mock_provider",modelId="modelo-prueba",modality=Modality.IMAGE,
            prompt=prompt,status=GenerationStatus.FAILED,error="Fallo simulado $it",startedAt=base+it*60_000L)}
        runBlocking {jobs.forEach {app.database.insertOrUpdateJob(it)}}
        compose.waitUntil(10_000) {jobs.all {j -> app.generationRepository.allJobs.value.any {it.id==j.id}}}
        compose.setContent {NexusTheme {NexusChrome({},{}) {GenerationsScreen({},{})}}}
        compose.onNodeWithContentDescription("Buscar en Historial").performClick()
        compose.onNodeWithText("Buscar modelo o descripción").performTextInput("Prueba intentos fixture")
        // Tres fallos seguidos del mismo intento se pliegan: la cabecera ya lleva el título real.
        compose.onNodeWithText("3 intentos fallidos · Prueba intentos fixture").performClick()
        (1..3).forEach {compose.onNodeWithText("Intento $it de 3 · Fallo simulado $it").assertExists()}
        compose.onAllNodesWithText("SUBJECT & SCENE",substring=true).assertCountEquals(0)
    }
}
