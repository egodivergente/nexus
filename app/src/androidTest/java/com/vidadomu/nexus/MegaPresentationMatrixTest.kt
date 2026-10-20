package com.vidadomu.nexus

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.agent.core.assets.ProjectMemory
import com.vidadomu.nexus.agent.core.assets.ProjectNote
import com.vidadomu.nexus.assistant.*
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.AtlasPublishedPrice
import com.vidadomu.nexus.sync.DriveConflictPresentation
import com.vidadomu.nexus.sync.DriveConflictReview
import com.vidadomu.nexus.ui.create.NativeParameterControl
import com.vidadomu.nexus.ui.models.*
import com.vidadomu.nexus.ui.theme.AppearancePreferences
import com.vidadomu.nexus.ui.theme.NexusAppearance
import com.vidadomu.nexus.ui.theme.NexusTheme
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Captures of real composables with in-memory fixtures and callback spies. Nothing reaches a provider,
 * repository, Drive, project store or generation. Widths are logical: the whole content gets a local
 * Density so that the window spans 411 or 448 dp. This is not hardware emulation (no S25, no other panel):
 * the physical pixels stay those of the device under test and are recorded in manifest.tsv.
 *
 * Dialogs and bottom sheets live in their own window and may not inherit the local Density; every scene
 * reads the Density its probe text was actually laid out with, and modal gaps are reported separately.
 * Instrumentation args: mega_matrix_themes, mega_matrix_scales, mega_matrix_widths (comma lists) and
 * mega_matrix_modal_scale=record to list modal gaps without failing.
 */
class MegaPresentationMatrixTest {
    @get:Rule val compose = createComposeRule()

    private data class Config(val theme: NexusAppearance, val fontScale: Float, val widthDp: Int) {
        val tag get() = "${theme.name}-${fontScale}x-${widthDp}dp"
    }

    private data class Applied(
        var density: Float = Float.NaN, var fontScale: Float = Float.NaN, var layoutWidthDp: Float = Float.NaN,
        var physicalWidthPx: Int = 0, var backgroundLuminance: Float = Float.NaN,
    )

    private class Scene(
        val name: String,
        val probe: String,
        val modal: Boolean = false,
        val prepare: () -> Unit = {},
        val verify: (density: Float) -> Unit = {},
    )

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as NexusApplication
    private val spanish = Locale("es", "ES")

    private fun guard() {
        check(BuildConfig.OFFLINE_QA) { "Requiere el build OFFLINE_QA" }
        check(app.packageName == MEGA_QA_PACKAGE) { "Solo se ejecuta en $MEGA_QA_PACKAGE, no en ${app.packageName}" }
    }

    private fun configs(): List<Config> {
        val args = InstrumentationRegistry.getArguments()
        val themes = args.getString("mega_matrix_themes")?.split(",")?.map { NexusAppearance.valueOf(it.trim()) }
            ?: listOf(NexusAppearance.LIGHT, NexusAppearance.AURORA)
        val scales = args.getString("mega_matrix_scales")?.split(",")?.map { it.trim().toFloat() } ?: listOf(1f, 1.3f, 2f)
        val widths = args.getString("mega_matrix_widths")?.split(",")?.map { it.trim().toInt() } ?: listOf(411, 448)
        return themes.flatMap { t -> scales.flatMap { s -> widths.map { w -> Config(t, s, w) } } }
    }

    private fun textLayoutDensity(text: String): Density? {
        val node = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() ?: return null
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.firstOrNull()?.layoutInput?.density
    }

    private fun hasVisualOverflow(text: String): Boolean {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.isEmpty() || layouts.first().hasVisualOverflow
    }

    private fun assertLogicalTouch(nodes: List<SemanticsNode>, density: Float, label: String) {
        assertTrue("Sin nodos para $label", nodes.isNotEmpty())
        nodes.forEach {
            val heightDp = it.size.height / density
            assertTrue("$label mide $heightDp dp lógicos (<48)", heightDp >= 47.5f)
        }
    }

    private fun touch(text: String, density: Float) =
        assertLogicalTouch(compose.onAllNodesWithText(text).fetchSemanticsNodes(), density, text)

    private fun capture(file: File) {
        SystemClock.sleep(1000)
        instrumentation.waitForIdleSync()
        // Throws instead of capturing when the QA package does not own the active window.
        compose.waitUntil(10_000) {
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == MEGA_QA_PACKAGE
        }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
    }

    private fun runMatrix(test: String, scenes: List<Scene>, invariant: () -> Unit, content: @Composable (String) -> Unit) {
        guard()
        val strictModals = InstrumentationRegistry.getArguments().getString("mega_matrix_modal_scale") != "record"
        val matrix = configs()
        val folder = File(app.getExternalFilesDir(null), "mega-matrix/$test").apply { mkdirs() }
        val rows = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val modalGaps = mutableListOf<String>()
        val modalProbes = scenes.filter { it.modal }.map { it.probe }
        val config = mutableStateOf(matrix.first())
        val scene = mutableStateOf(scenes.first().name)
        val epoch = mutableIntStateOf(0)
        val applied = Applied()
        val preferences = AppearancePreferences(app)
        val raw = app.getSharedPreferences(APPEARANCE_PREFS, Context.MODE_PRIVATE)
        val hadTheme = raw.contains("theme")
        val savedTheme = raw.getString("theme", null)
        val themeBefore = preferences.theme()
        val systemDensity = app.resources.displayMetrics.density
        val systemFontScale = app.resources.configuration.fontScale
        try {
            compose.setContent {
                NexusTheme {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val c = config.value
                        val physicalPx = constraints.maxWidth
                        val logical = physicalPx.toFloat() / c.widthDp
                        val base = LocalConfiguration.current
                        val local = remember(base, c) {
                            Configuration(base).apply { setLocale(spanish); screenWidthDp = c.widthDp; fontScale = c.fontScale }
                        }
                        CompositionLocalProvider(LocalDensity provides Density(logical, c.fontScale), LocalConfiguration provides local) {
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val d = LocalDensity.current
                                val width = maxWidth.value
                                val background = MaterialTheme.colorScheme.background.luminance()
                                SideEffect {
                                    applied.density = d.density; applied.fontScale = d.fontScale; applied.layoutWidthDp = width
                                    applied.physicalWidthPx = physicalPx; applied.backgroundLuminance = background
                                }
                                key(scene.value, c, epoch.intValue) { content(scene.value) }
                            }
                        }
                    }
                }
            }
            for (c in matrix) {
                compose.runOnIdle { preferences.setTheme(c.theme); config.value = c }
                compose.waitForIdle()
                for (s in scenes) {
                    compose.runOnIdle { scene.value = s.name; epoch.intValue++ }
                    compose.waitForIdle()
                    val id = "${c.tag}-${s.name}"
                    modalProbes.filterNot { it == s.probe && s.modal }.forEach {
                        check(compose.onAllNodesWithText(it, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                            "$id: modal «$it» de la escena anterior sigue abierto"
                        }
                    }
                    s.prepare()
                    compose.waitForIdle()
                    val main = compose.runOnIdle { applied.copy() }
                    if (abs(main.fontScale - c.fontScale) > 0.001f) failures += "$id: fontScale ${main.fontScale}"
                    if (abs(main.layoutWidthDp - c.widthDp) > 0.5f) failures += "$id: ancho ${main.layoutWidthDp} dp"
                    val dark = main.backgroundLuminance < 0.5f
                    if (c.theme == NexusAppearance.LIGHT && dark || c.theme == NexusAppearance.AURORA && !dark)
                        failures += "$id: fondo con luminancia ${main.backgroundLuminance} no corresponde a ${c.theme}"
                    val probe = textLayoutDensity(s.probe)
                    if (probe == null) failures += "$id: sin texto sonda «${s.probe}»"
                    else if (abs(probe.density - main.density) > 0.001f || abs(probe.fontScale - c.fontScale) > 0.001f) {
                        val gap = "$id: «${s.probe}» con density=${probe.density} fontScale=${probe.fontScale}; " +
                            "esperado density=${main.density} fontScale=${c.fontScale}"
                        if (s.modal) modalGaps += gap else failures += gap
                    }
                    val file = File(folder, "$id.png")
                    capture(file)
                    try { s.verify(probe?.density ?: main.density) } catch (e: AssertionError) { failures += "$id: ${e.message}" }
                    invariant()
                    rows += listOf(test, s.name, c.theme.name, c.fontScale, c.widthDp, main.physicalWidthPx, systemDensity,
                        systemFontScale, main.density, main.fontScale, main.layoutWidthDp, s.modal, s.probe, probe?.density,
                        probe?.fontScale, probe?.let { main.physicalWidthPx / it.density }, Build.MODEL, file.name).joinToString("\t")
                }
            }
        } finally {
            raw.edit().apply { if (hadTheme) putString("theme", savedTheme) else remove("theme") }.commit()
            File(folder, "validation.txt").writeText(buildString {
                appendLine("strictModals=$strictModals")
                failures.forEach { appendLine("FAIL $it") }
                modalGaps.forEach { appendLine("MODAL_SCALE_GAP $it") }
                appendLine("capturedScenes=${rows.size}; expectedScenes=${matrix.size * scenes.size}")
            })
            File(folder, "manifest.tsv").writeText(buildString {
                appendLine("# Escala y ancho lógicos aplicados en Compose; no emula hardware de S25 ni de otro dispositivo.")
                appendLine("test\tscene\ttheme\trequestedFontScale\trequestedWidthDp\tphysicalWidthPx\tsystemDensity\t" +
                    "systemFontScale\tappliedDensity\tappliedFontScale\tlayoutWidthDp\tmodal\tprobe\tprobeDensity\t" +
                    "probeFontScale\tprobeWidthDp\tmodel\tfile")
                rows.forEach(::appendLine)
            })
        }
        assertEquals("Tema de .megaqa no restaurado", themeBefore, AppearancePreferences(app).theme())
        val reported = failures + if (strictModals) modalGaps.map { "MODAL_SCALE_GAP $it" } else emptyList()
        assertTrue(reported.joinToString("\n"), reported.isEmpty())
    }

    @Test fun agentErrorsAndComposerControls() {
        val errors = listOf(
            "java.net.UnknownHostException: Unable to resolve host bedrock" to setOf("retry", "model"),
            "java.net.SocketTimeoutException: timeout after 60000 ms" to setOf("retry", "chat", "history"),
            "HTTP 401 Unauthorized: invalid api key" to setOf("model"),
            "IllegalStateException: stream closed unexpectedly" to setOf("retry", "chat"),
        )
        val actions = mutableListOf<String>()
        val visibleButtons = listOf("Reintentar", "Continuar en chat nuevo", "Cambiar modelo", "Detalle técnico")
        val collapsedProbe = agentErrorNoticeContent(errors[0].first, true).cause
        runMatrix("agente-errores-controles", listOf(
            Scene("errores-controles", collapsedProbe, verify = { density ->
                errors.forEach { (raw, kinds) ->
                    val cause = agentErrorNoticeContent(raw, "retry" in kinds).cause
                    assertTrue("Sin causa: $cause", compose.onAllNodesWithText(cause).fetchSemanticsNodes().isNotEmpty())
                    assertTrue("Detalle visible sin abrir: $raw", compose.onAllNodesWithText(raw).fetchSemanticsNodes().isEmpty())
                }
                visibleButtons.forEach { touch(it, density) }
                listOf("Proveedor del agente: Compatible OpenAI. Cambiar", "Modo de razonamiento no disponible con este proveedor").forEach {
                    assertLogicalTouch(compose.onAllNodesWithContentDescription(it).fetchSemanticsNodes(), density, it)
                }
                compose.onNodeWithContentDescription("Modo de razonamiento no disponible con este proveedor").assertIsNotEnabled()
            }),
            Scene("errores-detalle", "Ocultar detalle técnico",
                prepare = { compose.onAllNodesWithText("Detalle técnico")[0].performScrollTo().performClick() },
                verify = { density ->
                    compose.onNodeWithText("UnknownHostException", substring = true).assertExists()
                    touch("Ocultar detalle técnico", density)
                }),
        ), invariant = { assertTrue("Acción real del agente: $actions", actions.isEmpty()) }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                errors.forEachIndexed { i, (raw, kinds) ->
                    AgentErrorNotice("mega-error-$i", raw,
                        onRetry = if ("retry" in kinds) ({ actions += "retry-$i" }) else null,
                        onChangeModel = if ("model" in kinds) ({ actions += "model-$i" }) else null,
                        onNewChat = if ("chat" in kinds) ({ actions += "chat-$i" }) else null,
                        onOpenHistory = if ("history" in kinds) ({ actions += "history-$i" }) else null)
                }
                AgentControlButton("Proveedor", "Compatible OpenAI", Icons.Default.Psychology, enabled = true,
                    description = "Proveedor del agente: Compatible OpenAI. Cambiar", onClick = { actions += "provider" })
                AgentControlButton("Razonamiento", "No disponible", Icons.Default.Psychology, enabled = false,
                    description = "Modo de razonamiento no disponible con este proveedor", onClick = { actions += "reasoning" })
            }
        }
    }

    @Test fun longProjectsMemoryAndLeaveDialog() {
        val names = (1..6).map { "Proyecto de cuarenta caracteres número $it".padEnd(40, 'x') }
        val projects = names.mapIndexed { i, n -> Project("mega-p$i", n, createdAt = i.toLong()) }
        val memoryProject = Project("mega-memory", "Mateo", "Drama", createdAt = 0,
            memory = ProjectMemory(synopsis = "Base", notes = listOf(ProjectNote(7, "Nota fija")), updatedAt = 1))
        val pristine = memoryProject.copy()
        val events = mutableListOf<String>()
        val fields = listOf("Descripción corta", "Sinopsis", "Tono / género", "Estilo visual", "Reglas fijas", "Modelos preferidos")
        runMatrix("proyectos-memoria", listOf(
            Scene("proyectos", names[0], verify = {
                names.forEach { name ->
                    compose.onNodeWithText(name).performScrollTo().assertIsDisplayed()
                    assertTrue("Nombre recortado: $name", !hasVisualOverflow(name))
                }
                compose.onNodeWithText(PROJECT_DRAFT_BADGE).assertExists()
                compose.onNodeWithText("Sin proyecto").performScrollTo().assertExists()
            }),
            Scene("proyectos-final", names[5], prepare = { compose.onNodeWithText(names[5]).performScrollTo() }, verify = {
                compose.onNodeWithText(names[5]).assertIsDisplayed()
            }),
            Scene("memoria", "Sinopsis", verify = {
                fields.forEach { compose.onNode(hasSetTextAction() and hasContentDescription(it)).performScrollTo().assertExists() }
                compose.onNodeWithContentDescription("Borrar nota").performScrollTo().assertExists()
            }),
            Scene("memoria-salir", "Cambios sin guardar", modal = true, verify = { density ->
                listOf("Guardar", "Conservar borrador", "Descartar cambios").forEach { touch(it, density) }
            }),
        ), invariant = {
            assertTrue("Callback real de proyecto: $events", events.isEmpty())
            assertEquals(pristine, memoryProject)
        }) { scene ->
            when (scene) {
                "proyectos", "proyectos-final" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ProjectPickerList(projects, "mega-p0", draftIds = setOf("mega-p2"), onSelect = { events += "select:$it" })
                }
                else -> {
                    var draft by remember { mutableStateOf(ProjectMemoryDraft.from(memoryProject)) }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        ProjectMemoryForm(draft, { draft = it; events += "draft" }, assetCount = 3, characters = emptyList(),
                            notes = memoryProject.memory.notes)
                    }
                    if (scene == "memoria-salir") ProjectLeaveDialog(memoryProject.name, onSave = { events += "save" },
                        onKeep = { events += "keep" }, onDiscard = { events += "discard" }, onStay = { events += "stay" })
                }
            }
        }
    }

    @Test fun seedInt32AndTriStateBoolean() {
        val seed = ModelParameter("seed", "Semilla", ParameterType.SEED, minValue = 0f, maxValue = 2.14748365E9f,
            isAdvanced = true, nativeType = "integer", minExact = "0", maxExact = "2147483647")
        val camera = ModelParameter("camera_fixed", "Cámara fija", ParameterType.BOOLEAN, nativeType = "boolean")
        val range = "Rango: 0 – 2.147.483.647"
        val changes = mutableListOf<Pair<String, String>>()
        val options = listOf("Automático", "Sí", "No").map { "Cámara fija: $it" }
        runMatrix("parametros-semilla-booleano", listOf(
            Scene("parametros", range, verify = { density ->
                compose.onNodeWithText(range).performScrollTo().assertIsDisplayed()
                assertTrue(compose.onAllNodesWithText("E9", substring = true).fetchSemanticsNodes().isEmpty())
                assertTrue(compose.onAllNodesWithText("2.147.483.648", substring = true).fetchSemanticsNodes().isEmpty())
                options.forEach { assertLogicalTouch(compose.onAllNodesWithContentDescription(it).fetchSemanticsNodes(), density, it) }
                compose.onNodeWithContentDescription(options[0]).assertIsSelected()
                compose.onNodeWithContentDescription(options[1]).assertIsNotSelected()
                compose.onNodeWithContentDescription(options[2]).assertIsNotSelected()
            }),
            Scene("parametros-semilla-detalle", "Clave técnica: seed", modal = true,
                prepare = { compose.onNodeWithContentDescription("Detalles de Semilla").performScrollTo().performClick() },
                verify = { assertEquals(2, compose.onAllNodesWithText(range).fetchSemanticsNodes().size) }),
            Scene("parametros-booleano-no", "Cámara fija",
                prepare = { compose.onNodeWithContentDescription(options[2]).performScrollTo().performClick() },
                verify = {
                    compose.onNodeWithContentDescription(options[2]).assertIsSelected()
                    compose.onNodeWithContentDescription(options[0]).assertIsNotSelected()
                    assertEquals("camera_fixed" to "false", changes.last())
                }),
        ), invariant = { assertTrue("Cambio inesperado: $changes", changes.all { it == ("camera_fixed" to "false") }) }) {
            var seedValue by remember { mutableStateOf("") }
            var cameraValue by remember { mutableStateOf("") }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NativeParameterControl(seed, seedValue) { seedValue = it; changes += "seed" to it }
                NativeParameterControl(camera, cameraValue) { cameraValue = it; changes += "camera_fixed" to it }
            }
        }
    }

    @Test fun modelSheetFactsAndVariantSheet() {
        val title = "Fixture Motion"
        fun route(name: String, rank: Int) = NexusModel("megaqa-fixture/motion-${name.lowercase()}", "$title $name", FIXTURE_PROVIDER,
            organization = "Estudio QA", modalities = listOf(Modality.VIDEO), capabilities = listOf(ModelCapability.IMAGE_TO_VIDEO),
            parameters = emptyList(), variantRank = rank)
        val pro = route("Pro", 4).copy(description = "Movimiento de cámara fluido y detalle alto en primeros planos.",
            basePriceUsd = 0.14, priceUnit = "s", priceUnitVerified = true, contractEndpoint = "image_to_video",
            nativeRequiredFields = listOf("prompt", "image"), promptRequired = true, firstFrameField = "image", inputImageField = "image",
            parameters = listOf(ModelParameter("duration", "Duración", ParameterType.ENUM, "5", options = listOf("5", "10")),
                ModelParameter("generate_audio", "Sonido", ParameterType.BOOLEAN, "true")))
        val lite = route("Lite", 1).copy(description = "Pruebas rápidas de composición con menos detalle.")
        val routes = listOf(pro, lite)
        val group = CatalogModelGroup("megaqa-matrix-fixture-motion", title, routes)
        val facts = modelFactSheet(pro, pro.takeIf(::hasProviderContract), AtlasPublishedPrice.cached(pro)?.unit)
        val evidence = routes.map { VariantEvidence(it, it.takeIf(::hasProviderContract), AtlasPublishedPrice.cached(it)) }
        val differences = variantDifferences(evidence)
        val choices = app.getSharedPreferences("catalog_choices_v1", Context.MODE_PRIVATE)
        fun groupChoices() = choices.all.filterKeys { group.key in it }
        val choicesBefore = groupChoices()
        val actions = mutableListOf<String>()
        fun exists(text: String, substring: Boolean = false) = assertTrue("Falta «$text»",
            compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        runMatrix("modelo-ficha-variantes", listOf(
            Scene("modelo-ficha", "Qué admite y cómo se cobra", verify = { density ->
                facts.forEach { exists(it.label); exists(it.value ?: MISSING_FACT) }
                assertTrue(compose.onAllNodesWithText("gratis", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
                touch("Usar $title", density)
            }),
            Scene("modelo-variantes", "Elige una variante", modal = true,
                prepare = { compose.onNodeWithContentDescription("${routes.size} opciones disponibles").performScrollTo().performClick() },
                verify = {
                    exists("No es una cotización", substring = true)
                    evidence.forEach { e ->
                        exists(optionVariantLabel(e.model, title))
                        exists(differences[modelBookmark(e.model)] ?: UNDOCUMENTED_DIFFERENCE)
                        exists(exampleCost(e.model, e.tariff, e.verified).note, substring = true)
                    }
                }),
        ), invariant = {
            assertTrue("Acción real sobre el modelo: $actions", actions.isEmpty())
            assertEquals(choicesBefore, groupChoices())
        }) {
            ModelOptionsScreen(group, emptySet(), toggleFavorite = { actions += "favorite:${it.id}" },
                onUse = { actions += "use:${it.id}" }, modifier = Modifier.fillMaxSize())
        }
    }

    @Test fun driveConflictListDetailAndConfirmation() {
        val assetId = "0d8c2f0e-7b0a-4c9e-9a51-2b7c51d7a001"
        val at = 1792491300000L
        fun version(id: String, device: String, value: JsonObject?) = buildJsonObject {
            put("id", id); put("device", device); put("at", at); put("value", value ?: JsonNull)
        }
        fun asset(name: String, uri: String, width: Int) = buildJsonObject {
            put("id", assetId); put("uri", uri); put("type", "IMAGE"); put("displayName", name)
            put("width", width); put("height", 768); put("createdAt", at)
        }
        fun conflict(index: Int) = buildJsonObject {
            put("key", "assets:$assetId-$index")
            put("versions", buildJsonArray {
                add(version("a-$index", "SM-S938B", asset("foto-$index.png", "nexus-sync:a$index.png", 1024)))
                add(version("b-$index", "Windows", asset("foto-$index.png", "file:///local/foto-$index.png", 2048)))
            })
        }
        val removal = buildJsonObject {
            put("key", "assets:$assetId-gone")
            put("versions", buildJsonArray {
                add(version("keep", "SM-S938B", asset("borrador.png", "nexus-sync:k.png", 1024)))
                add(version("drop", "Pixel 8 Pro", null))
            })
        }
        val summary = DriveConflictPresentation.summarize(buildJsonObject {
            put("state", "conflicts"); put("missingMedia", 2)
            put("conflicts", JsonArray((1..3).map(::conflict) + removal))
        }, zone = TimeZone.getTimeZone("UTC"))
        val resolved = mutableListOf<Pair<String, String>>()
        val row = "Archivo «foto-1.png»"
        runMatrix("conflicto-drive", listOf(
            Scene("conflictos", "Nexus no pudo leer 2 originales para copiarlos a Drive.", verify = { density ->
                assertEquals(4, compose.onAllNodesWithTag("drive-conflict-row").fetchSemanticsNodes().size)
                assertLogicalTouch(compose.onAllNodesWithTag("drive-conflict-row").fetchSemanticsNodes(), density, "fila de conflicto")
                assertTrue(compose.onAllNodesWithText(assetId, substring = true).fetchSemanticsNodes().isEmpty())
            }),
            Scene("conflicto-detalle", "Versión 1 de 2", modal = true,
                prepare = { compose.onNodeWithText(row).performScrollTo().performClick() },
                verify = { density ->
                    compose.onNodeWithTag("drive-conflict-detail").assertExists()
                    compose.onNodeWithText("Resolución: 2048 × 768 · distinto").assertExists()
                    compose.onNodeWithText("Original sin referencia en Drive").assertExists()
                    touch("Conservar esta versión", density)
                }),
            Scene("conflicto-confirmar", "¿Conservar la versión 2?", modal = true,
                prepare = {
                    compose.onNodeWithText(row).performScrollTo().performClick()
                    compose.waitForIdle()
                    compose.onAllNodesWithText("Conservar esta versión")[1].performScrollTo().performClick()
                },
                verify = { density ->
                    touch("Sí, conservar esta", density)
                    compose.onNodeWithText("Cancelar").assertExists()
                }),
        ), invariant = { assertTrue("Resolución real de conflicto: $resolved", resolved.isEmpty()) }) {
            DriveConflictReview(summary, null, onResolve = { key, version -> resolved += key to version },
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
        }
    }

    private companion object {
        const val MEGA_QA_PACKAGE = "com.vidadomu.nexus.megaqa"
        const val APPEARANCE_PREFS = "nexus_appearance_user_v1"
        const val FIXTURE_PROVIDER = "megaqa_fixture"
    }
}
