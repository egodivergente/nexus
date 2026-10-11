package com.vidadomu.nexus

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.spend.*
import com.vidadomu.nexus.assistant.*
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.billing.BillingLedgerScreen
import com.vidadomu.nexus.ui.create.*
import com.vidadomu.nexus.ui.generations.GenerationsScreen
import com.vidadomu.nexus.ui.library.LibraryScreen
import com.vidadomu.nexus.ui.models.*
import com.vidadomu.nexus.ui.output.OutputViewerScreen
import com.vidadomu.nexus.ui.settings.*
import com.vidadomu.nexus.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real composables, synthetic local data. Never submits a plan or invokes the LLM. */
class UiAuditScreenshotsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureScreenMatrixWithoutGenerating() {
        check(BuildConfig.OFFLINE_QA)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as NexusApplication
        check(app.packageName.endsWith(".uiqa"))
        runBlocking { app.ready.await(); app.database.loading.first { !it } }
        app.providerRegistry.setMockMode(true)
        app.modelCatalogRepository.refreshModels()
        runBlocking { app.modelCatalogRepository.isLoading.first { !it } }
        val initialJobs = app.generationRepository.allJobs.value
        val model = LocalQaFixture.model(app, "references")
        val media = LocalQaFixture.media(app, model, 3)
        val id = "ui-audit-fixture"
        val output = GenerationOutput("ui-audit-output", id, Modality.IMAGE, media.first().uriString,
            media.first().uriString, "image/png")
        runBlocking { app.database.insertOrUpdateJob(GenerationJob(id, providerId="mock_provider",
            modelId=model.id, modality=Modality.IMAGE, prompt="Escultura de cerámica azul, luz suave",
            status=GenerationStatus.COMPLETED, outputs=listOf(output), actualCostUsd=.05)) }
        lateinit var canvas: CreateViewModel
        lateinit var assistant: AssistantViewModel
        lateinit var comparison: ComparisonController
        compose.runOnIdle {
            canvas = CreateViewModel(app)
            canvas.loadLocalPreview(model, media)
            canvas.updatePrompt("Escultura de cerámica azul, luz suave")
            assistant = AssistantViewModel(app)
            comparison = canvas.comparison
            comparison.open(canvas.uiState.value)
        }
        val plan = GenerationPlan("ui-audit-plan", ModelRef("mock_provider",model.id), "GPT Image · Edición", "IMAGE",
            prompt="Escultura de cerámica azul, luz suave", params=mapOf("size" to "1024x1024"),
            effectiveParams=mapOf("size" to "1024x1024"), createdAt=1)
        val project = Project("ui-audit-project", "Proyecto de ejemplo", createdAt=1)
        @Suppress("UNCHECKED_CAST")
        val assistantState = AssistantViewModel::class.java.getDeclaredField("_ui").apply { isAccessible=true }
            .get(assistant) as MutableStateFlow<AssistantUiState>
        compose.runOnIdle { assistantState.value = assistant.ui.value.copy(projects=listOf(project), activeProjectId=project.id,
            items=listOf(ChatItem.User("ui-audit-user", "Prepara una imagen con estas referencias", emptyList()),
                ChatItem.Assistant("ui-audit-assistant", "He preparado una propuesta. Revisa el prompt y el coste antes de generar.", emptyList(), ""),
                ChatItem.PlanCard("ui-audit-card",plan.id)), plans=mapOf(plan.id to PlanUi(plan,true,PlanState.PREPARED))) }
        val scene = mutableStateOf("lienzo")
        val scale = mutableFloatStateOf(1f)
        val preferences = AppearancePreferences(app)
        val originalTheme = preferences.theme()
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("audit_phase") ?: "before"
        val themes = if(phase=="after") NexusAppearance.entries
            else listOf(NexusAppearance.LIGHT)
        val scales = when(phase) { "before" -> listOf(1f,1.3f); "after" -> listOf(1f,1.3f,1.5f); else -> listOf(1.3f) }
        val scenes=args.getString("audit_scenes")?.split(",")
            ?: listOf("lienzo","historial","modelos","modelo-opciones","biblioteca","visor","asistente","proyecto","ajustes","flow","comparacion","costes")
        val folder = File(app.getExternalFilesDir(null),"ui-audit/$phase").apply { mkdirs() }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale.floatValue)) {
                NexusTheme {
                    com.vidadomu.nexus.ui.components.NexusChrome({}, {}, settingsVisible=scene.value!="ajustes") {
                    Box(Modifier.fillMaxSize()) {
                        when(scene.value) {
                            "lienzo" -> CreateScreen({}, {}, canvas)
                            "historial" -> GenerationsScreen({}, {})
                            "modelos" -> ModelsScreen({})
                            "modelo-opciones" -> ModelOptionsScreen(CatalogModelGroup("ui-audit", "GPT Image", listOf(model)), emptySet(), {}, {})
                            "biblioteca" -> LibraryScreen({})
                            "visor" -> OutputViewerScreen(id, {}, {})
                            "asistente", "proyecto" -> AssistantScreen({}, {}, {}, assistant)
                            "ajustes" -> SettingsScreen({})
                            "flow" -> FlowCreateScreen({}, {})
                            "comparacion" -> ComparisonScreen(comparison, {}, {})
                            "costes" -> BillingLedgerScreen(app.generationRepository.allJobs.value,null,app.currencyManager,{}, {},Modifier.fillMaxSize())
                        }
                    }
                    }
                }
            }
        }
        try {
            for(theme in themes) for(fontScale in scales) {
                compose.runOnIdle { preferences.setTheme(theme); scale.floatValue=fontScale }
                for(name in scenes) {
                    compose.runOnIdle { scene.value=name }
                    compose.waitForIdle()
                    if(name=="proyecto") {
                        val old = compose.onAllNodesWithText("Memoria (vacía)")
                        if(old.fetchSemanticsNodes().isNotEmpty()) old[0].performClick()
                        else compose.onNodeWithContentDescription("Abrir proyecto").performClick()
                        compose.waitForIdle()
                    }
                    // UiAutomation captures the presented frame, not Compose's next draw.
                    // Give the emulator compositor time to present the settled screen.
                    android.os.SystemClock.sleep(1000)
                    instrumentation.waitForIdleSync()
                    compose.waitUntil(10_000) {
                        instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()==app.packageName
                    }
                    // Capture the full display, including a dialog/sheet in a separate window.
                    val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                    File(folder,"${theme.name}-${fontScale}-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                    bitmap.recycle()
                }
            }
            // Captures cannot submit a generation. Existing jobs and provider ids survive unchanged.
            check(initialJobs.all { original -> app.generationRepository.allJobs.value.any { it.id==original.id && it.providerJobId==original.providerJobId } })
        } finally { preferences.setTheme(originalTheme) }
    }
}
