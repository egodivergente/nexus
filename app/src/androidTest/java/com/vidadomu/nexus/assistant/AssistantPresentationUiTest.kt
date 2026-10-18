package com.vidadomu.nexus.assistant

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.agent.core.orders.Order
import com.vidadomu.nexus.agent.core.orders.OrderStatus
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.PlanState
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic local fixtures only. No LLM, generations, production data or credentials. */
class AssistantPresentationUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun guard() { check(BuildConfig.OFFLINE_QA) }
    private fun screenshot(name: String) {
        val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val dir = File(context.getExternalFilesDir(null), "studio-ux").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    @Test fun settingsPersistImmediatelyAndResetOnlyPresentation() {
        guard()
        val store = AssistantPresentationStore(context)
        val previous = store.load()
        try {
            store.reset()
            compose.setContent { NexusTheme { AssistantPresentationSettings() } }
            compose.onNodeWithText("Compactas").performClick().assertIsSelected()
            compose.onNodeWithContentDescription("Animación al generar").performClick().assertIsOff()
            compose.runOnIdle {
                assertEquals(AssistantCardSize.COMPACT, AssistantPresentationStore(context).load().cardSize)
                assertFalse(AssistantPresentationStore(context).load().animateGeneration)
            }
            screenshot("settings-compact")
            compose.onNodeWithText("Restablecer presentación").performClick()
            compose.onNodeWithText("Equilibradas").assertIsSelected()
            compose.onNodeWithContentDescription("Animación al generar").assertIsOn()
        } finally { store.setCardSize(previous.cardSize); store.setAnimateGeneration(previous.animateGeneration) }
    }
    @Test fun preferenceObservationStopsWhenUnregistered() {
        guard()
        val prefs = context.getSharedPreferences("test_presentation_isolated", Context.MODE_PRIVATE)
        val store = AssistantPresentationStore(prefs)
        compose.setContent { NexusTheme { Text("Observación de preferencias") } }
        prefs.edit().clear().commit()
        try {
            var calls = 0
            lateinit var remove: () -> Unit
            compose.runOnIdle { remove = store.observe { calls++ }; store.setCardSize(AssistantCardSize.LARGE) }
            compose.waitForIdle()
            compose.runOnIdle { assertTrue(calls > 0); remove() }
            val before = calls
            compose.runOnIdle { store.setAnimateGeneration(false) }
            compose.waitForIdle()
            assertEquals(before, calls)
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun agentImageOpensInForegroundAndReturnsWithoutLosingChat() {
        guard()
        val file = File(context.cacheDir, "studio-ux-fixture.png")
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(96, 132, 164))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        try {
            val media = Attachment("fixture", Uri.fromFile(file).toString(), "image/png", "Plano de prueba")
            compose.setContent { NexusTheme { Column { Text("Chat conservado"); AssistantMediaPreview(media) } } }
            compose.onNodeWithText("Ampliar imagen").performClick()
            compose.onNodeWithContentDescription("Cerrar imagen ampliada").assertIsDisplayed()
            screenshot("foreground-image")
            compose.onNodeWithText("Ajustar").performClick()
            compose.onNodeWithContentDescription("Cerrar imagen ampliada").performClick()
            compose.onNodeWithText("Chat conservado").assertIsDisplayed()
            compose.onNodeWithText("Ampliar imagen").assertIsDisplayed()
        } finally { file.delete() }
    }
    @Test fun completedCardsActuallyResizeWithoutLosingResultsOrLogos() {
        guard()
        val ref = ModelRef("atlas_cloud", "black-forest-labs/flux")
        val plan = GenerationPlan(id = "p-size", ref = ref, modelName = "Flux 2 Max", modality = "IMAGE", prompt = "Prueba",
            params = emptyMap(), effectiveParams = emptyMap(), createdAt = 1)
        val order = Order(id = "o-size", planId = plan.id, status = OrderStatus.COMPLETED, modelName = plan.modelName,
            modelRef = ref.toString(), modality = "IMAGE", createdAt = 1, updatedAt = 2)
        val image = File(context.cacheDir, "size-fixture.png")
        val bitmap = Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        var size by mutableStateOf(AssistantCardSize.COMPACT)
        var opens = 0
        try {
            compose.setContent { NexusTheme {
                OrderCard(order, PlanUi(plan, false, PlanState.SUBMITTED), listOf(Uri.fromFile(image).toString()), false,
                    {}, {}, {}, {}, {}, {}, { opens++ }, {}, presentation = AssistantPresentation(size, false))
            } }
            var previousWidth = 0f
            for (value in AssistantCardSize.entries) {
                compose.runOnIdle { size = value }
                compose.onNodeWithContentDescription("atlas cloud").assertIsDisplayed()
                val thumbnail = compose.onNodeWithContentDescription("Abrir imagen generada")
                thumbnail.assertIsDisplayed().performClick()
                val width = thumbnail.fetchSemanticsNode().boundsInRoot.width
                assertTrue("Each size grows the result", width > previousWidth)
                previousWidth = width
                screenshot("result-${value.name.lowercase()}")
            }
            compose.runOnIdle { assertEquals(3, opens) }
        } finally { image.delete() }
    }
    @Test fun unknownProgressHasNoPercentageAndStopsAtCompletion() {
        guard()
        var status by mutableStateOf(OrderStatus.PROCESSING)
        compose.setContent { NexusTheme {
            GenerationActivity(Order(id = "o-unknown", planId = "p-unknown", status = status,
                modelName = "Veo", modelRef = "google_flow:veo", modality = "VIDEO", progress = .5f,
                createdAt = 1, updatedAt = 2), animated = false, compact = false)
        } }
        compose.onNodeWithText("Generación en curso").assertIsDisplayed()
        compose.onNodeWithText("50", substring = true).assertDoesNotExist()
        screenshot("generation-indeterminate")
        compose.runOnIdle { status = OrderStatus.COMPLETED }
        compose.onNodeWithText("Generación en curso").assertDoesNotExist()
    }
    @Test fun cardSizesKeepLogosAndActionsAndMeasuredProgress() {
        guard()
        val ref = ModelRef("atlas_cloud", "black-forest-labs/flux")
        val plan = GenerationPlan(id = "p-test", ref = ref, modelName = "Flux 2 Max", modality = "IMAGE", prompt = "Plano local de prueba", params = emptyMap(), effectiveParams = emptyMap(), createdAt = 1)
        val order = Order(id = "o-test", planId = plan.id, status = OrderStatus.PROCESSING, modelName = plan.modelName,
            modelRef = ref.toString(), modality = "IMAGE", progress = .42f, createdAt = 1, updatedAt = 2)
        var size by mutableStateOf(AssistantCardSize.BALANCED)
        compose.setContent { NexusTheme {
            OrderCard(order, PlanUi(plan, false, PlanState.SUBMITTED), emptyList(), false, {}, {}, {}, {}, {}, {}, {}, {},
                presentation = AssistantPresentation(size, false))
        } }
        for (value in AssistantCardSize.entries) {
            compose.runOnIdle { size = value }
            compose.onNodeWithText("Flux 2 Max").assertIsDisplayed()
            compose.onNodeWithContentDescription("atlas cloud").assertIsDisplayed()
            compose.onNodeWithContentDescription("Detalles").assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithText("Procesando · 42 %").assertIsDisplayed()
            screenshot("generation-${value.name.lowercase()}")
        }
    }
}
