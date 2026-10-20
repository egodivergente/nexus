package com.vidadomu.nexus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.agent.core.assets.ProjectMemory
import com.vidadomu.nexus.agent.core.assets.ProjectNote
import com.vidadomu.nexus.assistant.*
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProjectAgentUiTest {
    @get:Rule val compose = createComposeRule()

    // NexusTheme fija su propia densidad (preferencias de apariencia): la escala de prueba va DENTRO del tema.
    private fun scaled(scale: Float, content: @Composable () -> Unit) {
        check(BuildConfig.OFFLINE_QA)
        compose.setContent {
            NexusTheme {
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) { content() }
            }
        }
    }

    @Test fun errorNoticeShowsCauseAndActionsBeforeOpeningDetails() {
        var retries = 0
        scaled(1.3f) {
            AgentErrorNotice("e1", "java.net.UnknownHostException: Unable to resolve host bedrock",
                onRetry = { retries++ }, onChangeModel = {}, onNewChat = null, onOpenHistory = null)
        }
        compose.onNodeWithText("No hay conexión. Comprueba la red y vuelve a intentarlo.").assertIsDisplayed()
        compose.onNodeWithText("Reintentar usa el mismo texto y los mismos adjuntos.").assertIsDisplayed()
        compose.onNodeWithText("UnknownHostException", substring = true).assertDoesNotExist()
        listOf("Reintentar", "Cambiar modelo", "Detalle técnico").forEach {
            compose.onNodeWithText(it).assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithText("Reintentar").performClick()
        compose.onNodeWithText("Detalle técnico").performClick()
        compose.onNodeWithText("UnknownHostException", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun composerControlsAreLabelledFullAndTouchSized() {
        var opened = 0
        scaled(1.3f) {
            Column {
                AgentControlButton("Proveedor", "Compatible OpenAI", Icons.Default.Psychology, enabled = true,
                    description = "Proveedor del agente: Compatible OpenAI. Cambiar", onClick = { opened++ })
                AgentControlButton("Razonamiento", "No disponible", Icons.Default.Psychology, enabled = false,
                    description = "Modo de razonamiento no disponible con este proveedor", onClick = { opened += 100 })
            }
        }
        val provider = compose.onNodeWithContentDescription("Proveedor del agente: Compatible OpenAI. Cambiar")
        provider.assertHeightIsAtLeast(48.dp).assertHasClickAction()
        compose.onNodeWithText("Compatible OpenAI", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Proveedor", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Modo de razonamiento no disponible con este proveedor")
            .assertHeightIsAtLeast(48.dp).assertIsNotEnabled()
        provider.performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun sixLongProjectsAreAllReachableWithFullNamesInOneTap() {
        val names = (1..6).map { "Proyecto de cuarenta caracteres número $it".padEnd(40, 'x') }
        val projects = names.mapIndexed { i, n -> Project("p$i", n, createdAt = i.toLong()) }
        var active by mutableStateOf<String?>("p0")
        scaled(1.3f) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProjectPickerList(projects, active, draftIds = setOf("p2"), onSelect = { active = it })
            }
        }
        names.forEach { name ->
            compose.onNodeWithText(name).performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(name, useUnmergedTree = true).fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            assertTrue("Nombre recortado: $name", layouts.isNotEmpty() && !layouts.first().hasVisualOverflow)
        }
        compose.onNodeWithText(PROJECT_DRAFT_BADGE).assertExists()
        compose.onNodeWithText(names[5]).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("p5", active) }
        compose.onNodeWithText("Sin proyecto").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(null, active) }
    }

    @Test fun memoryFormKeepsEveryFieldAccessibleAndEditsOnlyTheDraft() {
        val project = Project("p1", "Mateo", "Drama", createdAt = 0,
            memory = ProjectMemory(synopsis = "Base", notes = listOf(ProjectNote(7, "Nota fija")), updatedAt = 1))
        var draft by mutableStateOf(ProjectMemoryDraft.from(project))
        scaled(1f) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProjectMemoryForm(draft, { draft = it }, assetCount = 3, characters = emptyList(), notes = project.memory.notes)
            }
        }
        listOf("Descripción corta", "Sinopsis", "Tono / género", "Estilo visual", "Reglas fijas", "Modelos preferidos").forEach {
            compose.onNode(hasSetTextAction() and hasContentDescription(it)).performScrollTo().assertExists()
        }
        compose.onNode(hasSetTextAction() and hasContentDescription("Tono / género")).performScrollTo().performTextInput("sobrio")
        compose.onNodeWithContentDescription("Borrar nota").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("sobrio", draft.tone)
            assertEquals(listOf(7L), draft.removedNotes)
            assertTrue(draft.isDirty(project))
            assertEquals(null, project.memory.tone)
        }
    }

    @Test fun pastingBeyondTheLimitKeepsTheOriginalDraftAndShowsAnError() {
        var value by mutableStateOf("Una regla guardada")
        var warning by mutableStateOf<String?>(null)
        scaled(1f) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProjectMemoryField("Reglas fijas", value, { value = it }, "Una por línea",
                    maxLength = ProjectMemoryDraft.RULES_LIMIT, onValidationError = { warning = it })
            }
        }
        compose.onNode(hasSetTextAction() and hasContentDescription("Reglas fijas"))
            .performScrollTo().performTextInput("x".repeat(ProjectMemoryDraft.RULES_LIMIT + 1))
        compose.onNodeWithText("El texto supera ${ProjectMemoryDraft.RULES_LIMIT} caracteres; no se ha insertado.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("Una regla guardada", value)
            assertEquals("El texto supera ${ProjectMemoryDraft.RULES_LIMIT} caracteres; no se ha insertado.", warning)
        }
    }

    @Test fun invalidRuleStaysVisibleInMemoryFormAndDoesNotRequestSave() {
        val project = Project("qa-validation", "Memoria de prueba", createdAt = 0)
        var draft by mutableStateOf(ProjectMemoryDraft.from(project).copy(rules = "r".repeat(301)))
        var requestedSaves = 0
        scaled(1f) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProjectMemoryForm(draft, { draft = it }, assetCount = 0,
                    characters = emptyList(), notes = emptyList())
                androidx.compose.material3.Button(onClick = {
                    when (val decision = draft.prepareSave(project)) {
                        is ProjectMemorySaveDecision.Ready -> requestedSaves++
                        is ProjectMemorySaveDecision.Blocked -> draft = decision.draft
                    }
                }) { androidx.compose.material3.Text("Guardar memoria de prueba") }
            }
        }
        val warning = "Cada regla admite hasta 300 caracteres. Divide o acorta la regla larga antes de guardar."
        compose.onNodeWithText(warning).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Guardar memoria de prueba").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(0, requestedSaves)
            assertEquals("r".repeat(301), draft.rules)
            assertEquals(false, draft.saveRequested)
        }
    }

    @Test fun oversizeLegacyTextCanStillBeReducedOneCharacterAtATime() {
        val original = "x".repeat(ProjectMemoryDraft.RULES_LIMIT + 10)
        var value by mutableStateOf(original)
        scaled(1f) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProjectMemoryField("Reglas heredadas", value, { value = it }, "Una por línea",
                    maxLength = ProjectMemoryDraft.RULES_LIMIT, maxLines = 3)
            }
        }
        compose.onNode(hasSetTextAction() and hasContentDescription("Reglas heredadas"))
            .performScrollTo().performTextReplacement(original.dropLast(1))
        compose.runOnIdle { assertEquals(original.dropLast(1), value) }
        compose.onNodeWithText("no se ha insertado", substring = true).assertDoesNotExist()
    }

    @Test fun reviewingConcurrentMemoryDoesNotWriteAndKeepsTheWholeDraft() {
        val initial = Project("qa-conflict", "Proyecto de prueba", createdAt = 0,
            memory = ProjectMemory(synopsis = "Sinopsis anterior", updatedAt = 1))
        val newer = initial.copy(memory = initial.memory.copy(synopsis = "Sinopsis actual del agente", updatedAt = 2))
        var draft = ProjectMemoryDraft.from(initial).copy(synopsis = "Mi borrador sin perder", tone = "Épico")
        var accepted = 0
        scaled(1f) {
            ProjectMemoryConflictReview(newer, draft, onAcceptSnapshot = { draft = draft.afterReview(newer); accepted++ },
                onDismiss = {})
        }
        compose.onNodeWithText("Sinopsis actual del agente").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mi borrador sin perder").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, accepted); assertTrue(draft.isStale(newer)) }
        compose.onNodeWithText("Mantener mi borrador sobre esta versión").performClick()
        compose.runOnIdle {
            assertEquals(1, accepted)
            assertEquals("Mi borrador sin perder", draft.synopsis)
            assertEquals("Épico", draft.tone)
            assertEquals(false, draft.isStale(newer))
            assertEquals(false, draft.saveRequested)
            assertEquals("Sinopsis actual del agente", newer.memory.synopsis)
        }
    }

    @Test fun reviewKeepsAgentsUntouchedFieldAndShowsOnlyTheTrueOverwrite() {
        val base = Project("qa-merge", "Proyecto QA", createdAt = 0,
            memory = ProjectMemory(synopsis = "Sinopsis inicial", tone = "Anterior", updatedAt = 1))
        val current = base.copy(memory = base.memory.copy(synopsis = "Sinopsis nueva del agente", updatedAt = 2))
        var draft = ProjectMemoryDraft.from(base).copy(tone = "Épico")
        var accepted = 0
        scaled(1f) {
            ProjectMemoryConflictReview(current, draft, onAcceptSnapshot = {
                draft = draft.afterReview(current)
                accepted++
            }, onDismiss = {})
        }
        compose.onNodeWithText("No tocaste Sinopsis", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Sinopsis nueva del agente", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Épico").assertIsDisplayed()
        compose.onNodeWithText("Mantener mi borrador sobre esta versión").performClick()
        compose.runOnIdle {
            assertEquals(1, accepted)
            assertEquals("Sinopsis nueva del agente", draft.synopsis)
            assertEquals("Épico", draft.tone)
            assertEquals(false, draft.isStale(current))
            assertEquals(false, draft.saveRequested)
        }
    }

    @Test fun leavingWithChangesOffersSaveKeepAndDiscard() {
        var choice by mutableStateOf("")
        scaled(1.3f) {
            ProjectLeaveDialog("Mateo", onSave = { choice = "save" }, onKeep = { choice = "keep" },
                onDiscard = { choice = "discard" }, onStay = { choice = "stay" })
        }
        compose.onNodeWithText("Cambios sin guardar").assertIsDisplayed()
        listOf("Guardar", "Conservar borrador", "Descartar cambios").forEach { compose.onNodeWithText(it).assertHeightIsAtLeast(48.dp) }
        compose.onNodeWithText("Conservar borrador").performClick()
        compose.runOnIdle { assertEquals("keep", choice) }
    }
}
