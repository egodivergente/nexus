package com.vidadomu.nexus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.ui.components.NexusChrome
import com.vidadomu.nexus.ui.settings.*
import com.vidadomu.nexus.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsMinimalUiTest {
    @get:Rule val compose=createComposeRule()
    private fun app():NexusApplication {
        check(BuildConfig.OFFLINE_QA)
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
        check(app.packageName.endsWith(".uiqa"));runBlocking {app.ready.await()};return app
    }
    @Test fun appearanceCurrencyAndDiagnosticsStayReachable() {
        val app=app();val jobs=app.generationRepository.allJobs.value.map {it.id}
        val original=AppearancePreferences(app).theme()
        compose.setContent {NexusTheme {NexusChrome({}, {},settingsVisible=false) {SettingsScreen()}}}
        try {
            compose.onNode(hasText("Apariencia") and hasClickAction()).performClick()
            compose.onNodeWithText("Negro OLED").assertExists()
            compose.onNodeWithText("Cuarzo negro").assertExists()
            compose.onNodeWithText("Restaurar predeterminado").performScrollTo().assertHasClickAction()
            compose.onNodeWithText("Cerrar apariencia").performScrollTo().performClick()
            compose.onNode(hasText("Moneda") and hasClickAction()).performClick()
            compose.onNode(hasText("Euros (€)") and hasText("Importes en euros con dos decimales") and hasClickAction()).assertExists()
            compose.onNodeWithText("Dólares ($ USD)").assertExists()
            compose.onNodeWithText("Actualizar tasa").performScrollTo().assertHasClickAction()
            compose.onNodeWithText("Cerrar moneda").performScrollTo().performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Diagnóstico del sistema") and hasClickAction())
            compose.onNode(hasText("Diagnóstico del sistema") and hasClickAction()).performClick()
            compose.onNodeWithText("Nexus ${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}").assertExists()
            compose.onNodeWithText("Cerrar diagnóstico").performScrollTo().performClick()
            compose.runOnIdle {assertEquals(jobs,app.generationRepository.allJobs.value.map {it.id})}
        } finally {AppearancePreferences(app).setTheme(original)}
    }
    @Test fun flowKeepsPromptOutputAndCostWithoutSubmitting() {
        val app=app();val jobs=app.generationRepository.allJobs.value.map {it.id}
        compose.setContent {NexusTheme {NexusChrome({}, {}) {FlowCreateScreen({}, {})}}}
        compose.onNodeWithText("Generar en Flow").assertIsNotEnabled()
        compose.onNodeWithText("Describe lo que quieres crear").performTextInput("Escena de prueba local")
        compose.onNodeWithText("Imagen").assertExists()
        compose.onNodeWithText("Vídeo").performScrollTo().performClick()
        compose.onNodeWithText("Ajustes y coste · Detalles").performScrollTo().performClick()
        compose.onNodeWithText("Aquí no hay una estimación de coste verificable.",substring=true).performScrollTo().assertExists()
        compose.onNodeWithText("Generar en Flow").assertIsNotEnabled()
        compose.runOnIdle {assertEquals(jobs,app.generationRepository.allJobs.value.map {it.id})}
    }
}
