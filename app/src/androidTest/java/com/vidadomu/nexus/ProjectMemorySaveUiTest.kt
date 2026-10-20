package com.vidadomu.nexus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.agent.core.assets.ProjectMemory
import com.vidadomu.nexus.agent.core.assets.ProjectNote
import com.vidadomu.nexus.assistant.*
import com.vidadomu.nexus.ui.components.NexusSheet
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProjectMemorySaveUiTest {
    @get:Rule val compose = createComposeRule()

    private val project = Project("qa-sintetico", "QA sintético", "Drama", createdAt = 0,
        memory = ProjectMemory(synopsis = "Base", notes = listOf(ProjectNote(7, "Nota fija")), updatedAt = 1))

    private fun show(content: @Composable () -> Unit) {
        check(BuildConfig.OFFLINE_QA)
        compose.setContent { NexusTheme { content() } }
    }

    @Test fun whileSavingNoFieldNoteOrLegacyConfirmationCanChangeTheDraft() {
        val legacy = ProjectMemoryDraft.from(project).copy(models = "Seedance 2.0, Kling", legacyModelsNeedReview = true)
        var draft by mutableStateOf(legacy)
        var changes = 0
        show {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProjectMemoryForm(draft, { draft = it; changes++ }, assetCount = 0, characters = emptyList(),
                    notes = project.memory.notes, enabled = false)
            }
        }
        listOf("Descripción corta", "Sinopsis", "Tono / género", "Estilo visual", "Reglas fijas", "Modelos preferidos").forEach {
            compose.onNode(hasSetTextAction() and hasContentDescription(it)).performScrollTo().assertIsNotEnabled()
        }
        compose.onNodeWithContentDescription("Borrar nota").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(CONFIRM_LEGACY_MODELS).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, changes); assertEquals(legacy, draft) }
    }

    @Test fun legacyConfirmationKeepsCommasAndDoesNotSave() {
        var draft by mutableStateOf(ProjectMemoryDraft.from(project).copy(models = "Seedance 2.0, Kling", legacyModelsNeedReview = true))
        show {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProjectMemoryForm(draft, { draft = it }, assetCount = 0, characters = emptyList(), notes = project.memory.notes)
            }
        }
        compose.runOnIdle { assertTrue(draft.prepareSave(project) is ProjectMemorySaveDecision.Blocked) }
        compose.onNodeWithText(CONFIRM_LEGACY_MODELS).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText(CONFIRM_LEGACY_MODELS).assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(draft.legacyModelsNeedReview)
            assertFalse("Confirmar no guarda", draft.saveRequested)
            assertEquals("Seedance 2.0, Kling", draft.models)
            assertEquals(null, project.memory.preferredModels.firstOrNull())
            assertTrue(draft.prepareSave(project) is ProjectMemorySaveDecision.Ready)
        }
    }

    @Test fun leaveDialogStaysLockedWhileSaving() {
        var saving by mutableStateOf(true)
        var choice by mutableStateOf("")
        show {
            ProjectLeaveDialog("QA sintético", onSave = { choice = "save" }, onKeep = { choice = "keep" },
                onDiscard = { choice = "discard" }, onStay = { choice = "stay" }, saving = saving)
        }
        compose.onNodeWithText("Guardando…").assertIsNotEnabled()
        listOf("Conservar borrador", "Descartar cambios").forEach { compose.onNodeWithText(it).assertIsNotEnabled() }
        compose.runOnIdle { assertEquals("", choice) }
        saving = false
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("save", choice) }
    }

    @Test fun sheetCloseButtonIsDisabledWhileDismissIsBlocked() {
        var dismissed = 0
        show {
            NexusSheet("Memoria de QA sintético", { dismissed++ }, dismissEnabled = false) { }
        }
        compose.onNodeWithContentDescription("Cerrar Memoria de QA sintético").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, dismissed) }
    }
}
