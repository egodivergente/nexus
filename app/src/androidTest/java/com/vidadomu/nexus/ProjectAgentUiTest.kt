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

    private fun scaled(scale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) { NexusTheme { content() } }
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
