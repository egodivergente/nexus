package com.vidadomu.nexus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.components.NexusChrome
import com.vidadomu.nexus.ui.models.*
import com.vidadomu.nexus.ui.theme.NexusTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CatalogMinimalUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun catalogSearchPersonalizationAndSelectionRemainReachable() {
        check(BuildConfig.OFFLINE_QA)
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
        runBlocking {app.ready.await();app.database.loading.first {!it}}
        app.providerRegistry.setMockMode(true)
        app.modelCatalogRepository.refreshModels()
        runBlocking {app.modelCatalogRepository.isLoading.first {!it}}
        val prefs=app.getSharedPreferences("catalog_grid_v2",0)
        val originalModality=prefs.getString("browser_modality",null)
        val originalSort=prefs.getString("sort",null)
        prefs.edit().putString("browser_modality","VIDEO").putString("sort","PERSONAL").commit()
        val initial=app.generationRepository.allJobs.value.map {it.id}
        var selected:NexusModel?=null
        try {
            compose.setContent {NexusTheme {NexusChrome({},{fail("No generation requested")}) {ModelsScreen(onSelectModelAndCreate = {selected=it})}}}
            compose.onNodeWithText("Favoritos").assertExists()
            compose.onNodeWithContentDescription("Opciones del catálogo").performClick()
            compose.onNodeWithText("Modelos ocultos",substring=true).performClick()
            compose.onNodeWithText("Modelos ocultos").assertIsDisplayed()
            compose.onNodeWithText("Cerrar").performClick()
            compose.onNodeWithContentDescription("Filtros y orden").performClick()
            compose.onNodeWithText("Nombre").performClick()
            compose.onNodeWithContentDescription("Cerrar filtros").performClick()
            compose.onNodeWithText("Buscar modelo o función").assertIsDisplayed()
            compose.onNodeWithText("Buscar modelo o función").performTextInput("Cinema")
            val group=catalogModelGroups(app.modelCatalogRepository.models.value.filter {Modality.VIDEO in it.modalities},emptySet()).first()
            compose.onNodeWithText(group.title).performClick()
            compose.onNodeWithText("Usar ${group.title}").performClick()
            compose.runOnIdle {
                assertNotNull(selected)
                assertTrue(group.routes.any {it.id==selected?.id})
                assertEquals(initial,app.generationRepository.allJobs.value.map {it.id})
            }
        } finally {
            prefs.edit().apply {
                if(originalModality==null) remove("browser_modality") else putString("browser_modality",originalModality)
                if(originalSort==null) remove("sort") else putString("sort",originalSort)
            }.commit()
        }
    }
}
