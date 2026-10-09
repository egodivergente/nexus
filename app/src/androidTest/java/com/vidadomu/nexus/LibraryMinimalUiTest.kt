package com.vidadomu.nexus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.components.NexusChrome
import com.vidadomu.nexus.ui.create.LocalQaFixture
import com.vidadomu.nexus.ui.library.LibraryScreen
import com.vidadomu.nexus.ui.output.OutputViewerScreen
import com.vidadomu.nexus.ui.theme.NexusTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryMinimalUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun everyOutputOpensItsSelectedResultAndDetailsStayReachable() {
        check(BuildConfig.OFFLINE_QA)
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
        runBlocking {app.ready.await();app.database.loading.first {!it}}
        val model=LocalQaFixture.model(app,"references")
        val media=LocalQaFixture.media(app,model,2)
        val id="ui-library-fixture"
        val outputs=media.mapIndexed {index,item->GenerationOutput("ui-library-result-$index",id,Modality.IMAGE,item.uriString,item.uriString,"image/png")}
        runBlocking {app.database.insertOrUpdateJob(GenerationJob(id,providerId="mock_provider",modelId=model.id,
            modality=Modality.IMAGE,prompt="Prueba dos resultados",status=GenerationStatus.COMPLETED,outputs=outputs))}
        compose.waitUntil(10_000) {app.generationRepository.allJobs.value.any {it.id==id}}
        val initial=app.generationRepository.allJobs.value.map {it.id}
        var opened:Pair<String,Int>?=null
        val selected=androidx.compose.runtime.mutableStateOf<Pair<String,Int>?>(null)
        compose.setContent {NexusTheme {NexusChrome({},{}) {
            selected.value?.let {OutputViewerScreen(it.first,{}, {},initialOutputIndex=it.second)} ?: LibraryScreen({}, {job,index->opened=job to index;selected.value=opened})
        }}}
        compose.onNodeWithContentDescription("Buscar en Biblioteca").performClick()
        compose.onNodeWithText("Buscar por modelo o descripción").performTextInput("Prueba dos resultados")
        compose.onAllNodesWithContentDescription("Imagen · Prueba dos resultados").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Imagen · Prueba dos resultados")[1].performClick()
        compose.onNodeWithText("Resultado 2").assertIsSelected()
        compose.onNodeWithText("Detalles").performScrollTo().performClick()
        compose.onNodeWithText(id).assertExists()
        compose.runOnIdle {assertEquals(id to 1,opened);assertEquals(initial,app.generationRepository.allJobs.value.map {it.id})}
    }
}
