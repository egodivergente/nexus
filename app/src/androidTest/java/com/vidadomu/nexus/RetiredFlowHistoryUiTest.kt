package com.vidadomu.nexus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.registry.RetiredProviders
import com.vidadomu.nexus.ui.components.NexusChrome
import com.vidadomu.nexus.ui.create.LocalQaFixture
import com.vidadomu.nexus.ui.generations.GenerationsScreen
import com.vidadomu.nexus.ui.output.OutputViewerScreen
import com.vidadomu.nexus.ui.theme.NexusTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Flow retirado: su histórico local se ve y se descarga sin consultar, reenviar ni pagar nada. */
class RetiredFlowHistoryUiTest {
    @get:Rule val compose=createComposeRule()

    private fun app():NexusApplication {
        check(BuildConfig.OFFLINE_QA)
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
        runBlocking {app.ready.await();app.database.loading.first {!it}}
        return app
    }

    private fun insertFlowJob(app:NexusApplication,id:String,prompt:String):GenerationJob {
        val model=LocalQaFixture.model(app,"references")
        val media=LocalQaFixture.media(app,model,1).single()
        val job=GenerationJob(id,providerId=RetiredProviders.GOOGLE_FLOW,modelId="current_project",modality=Modality.IMAGE,
            prompt=prompt,status=GenerationStatus.COMPLETED,providerJobId="flow-history-$id",
            outputs=listOf(GenerationOutput("$id-out",id,Modality.IMAGE,media.uriString,media.uriString,"image/png")))
        runBlocking {app.database.insertOrUpdateJob(job)}
        compose.waitUntil(10_000) {app.generationRepository.allJobs.value.any {it.id==id}}
        return job
    }

    @Test fun oldFlowOutputOpensAndOffersDownloadWithoutFlowActions() {
        val app=app()
        val job=insertFlowJob(app,"ui-retired-flow-viewer","Histórico Flow visor")
        val before=app.generationRepository.allJobs.value
        compose.setContent {NexusTheme {OutputViewerScreen(job.id,{}, {_,_->})}}
        compose.onNodeWithText("No hay un resultado disponible.").assertDoesNotExist()
        compose.onNodeWithText("Descargar archivo").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Conexión de Flow").assertDoesNotExist()
        compose.onNodeWithText("Consultar Flow").assertDoesNotExist()
        compose.runOnIdle {assertEquals(before,app.generationRepository.allJobs.value)}
    }

    @Test fun oldFlowJobInHistoryHasNoRerunNorCostQuery() {
        val app=app()
        val job=insertFlowJob(app,"ui-retired-flow-history","Histórico Flow lista")
        val before=app.generationRepository.allJobs.value
        var prepared:GenerationJob?=null
        compose.setContent {NexusTheme {NexusChrome({},{}) {GenerationsScreen({},{prepared=it})}}}
        compose.onNodeWithContentDescription("Buscar en Historial").performClick()
        compose.onNodeWithText("Buscar modelo o descripción").performTextInput(job.prompt)
        compose.onNodeWithContentDescription("Opciones de «${job.prompt}»",substring=true).performClick()
        compose.onNodeWithText("Diagnóstico").assertIsDisplayed()
        compose.onNodeWithText("Preparar de nuevo").assertDoesNotExist()
        compose.onNodeWithText("Consultar coste").assertDoesNotExist()
        compose.onNodeWithText("Actualizar estado").assertDoesNotExist()
        compose.runOnIdle {
            assertNull(prepared)
            assertEquals(before,app.generationRepository.allJobs.value)
        }
    }

    @Test fun pausedOrImportedActiveFlowJobsOfferNoProviderActions() {
        val app=app()
        val jobs=listOf(
            GenerationJob("ui-retired-flow-paused",providerId=RetiredProviders.GOOGLE_FLOW,modelId="current_project",modality=Modality.IMAGE,
                prompt="Histórico Flow pausado",status=GenerationStatus.FAILED,providerJobId="flow-paused",error=RetiredProviders.TRACKING_MESSAGE,
                parameters=mapOf("__tracking_paused" to "true",RetiredProviders.PREVIOUS_STATUS_KEY to GenerationStatus.GENERATING.name)),
            GenerationJob("ui-retired-flow-active",providerId=RetiredProviders.GOOGLE_FLOW,modelId="current_project",modality=Modality.IMAGE,
                prompt="Histórico Flow importado activo",status=GenerationStatus.GENERATING,providerJobId="flow-active"))
        runBlocking {jobs.forEach {app.database.insertOrUpdateJob(it)}}
        compose.waitUntil(10_000) {jobs.all {j -> app.generationRepository.allJobs.value.any {it.id==j.id}}}
        val before=app.generationRepository.allJobs.value
        compose.setContent {NexusTheme {NexusChrome({},{}) {GenerationsScreen({},{fail("No se prepara un proveedor retirado")})}}}
        compose.onNodeWithContentDescription("Buscar en Historial").performClick()
        compose.onNodeWithText("Buscar modelo o descripción").performTextInput("Histórico Flow")
        jobs.forEach {job ->
            compose.onNodeWithContentDescription("Opciones de «${job.prompt}»",substring=true).performClick()
            compose.onNodeWithText("Diagnóstico").assertIsDisplayed()
            listOf("Recuperar seguimiento","Actualizar estado","Consultar coste","Detener seguimiento","Cancelar generación","Preparar de nuevo")
                .forEach {compose.onNodeWithText(it).assertDoesNotExist()}
            compose.onNodeWithText("Diagnóstico").performClick()
            compose.onNodeWithText("Cerrar").performClick()
        }
        compose.runOnIdle {assertEquals(before,app.generationRepository.allJobs.value)}
    }
}
