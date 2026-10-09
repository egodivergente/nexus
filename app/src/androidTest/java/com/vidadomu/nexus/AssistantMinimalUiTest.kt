package com.vidadomu.nexus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vidadomu.nexus.assistant.*
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.spend.*
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AssistantMinimalUiTest {
    @get:Rule val compose=createComposeRule()
    private fun plan()=GenerationPlan("ui-dirty-plan",ModelRef("fixture","edit"),"Edición de prueba","IMAGE",
        prompt="Prompt original",params=mapOf("seed" to "42"),effectiveParams=mapOf("seed" to "42"),createdAt=1)

    @Test fun promptDraftBlocksIndividualAndBatchUntilAppliedOrUndone() {
        check(BuildConfig.OFFLINE_QA)
        var current by mutableStateOf(PlanUi(plan(),true,PlanState.PREPARED,includeInBatch=true))
        var dirty by mutableStateOf(false)
        var edits=0
        compose.setContent {NexusTheme {Column(Modifier.verticalScroll(rememberScrollState())) {
            PlanCardView(current,onEdit={edit->edits++;current=current.copy(plan=current.plan.copy(prompt=edit.prompt ?: current.plan.prompt))},
                onDiscard={},onGenerate={fail("No generation allowed")},onOpenCanvas={},onOpenOutput={},onDirtyChanged={dirty=it})
            PendingGenerations(AssistantUiState(plans=mapOf(current.plan.id to current)),blockedByDraft=dirty) {fail("No batch allowed")}
        }}}
        compose.onNodeWithText("Generar").assertIsEnabled()
        compose.onNodeWithText("Prompt").performTextClearance()
        compose.onNodeWithText("Prompt").performTextInput("Borrador sin aplicar")
        compose.onNodeWithText("Generar").assertIsNotEnabled()
        compose.onNodeWithText("Generar 1").assertIsNotEnabled()
        compose.onNodeWithText("Deshacer").performScrollTo().performClick()
        compose.onNodeWithText("Generar").assertIsEnabled()
        compose.onNodeWithText("Prompt").performTextClearance()
        compose.onNodeWithText("Prompt").performTextInput("Prompt aplicado")
        compose.onNodeWithText("Aplicar cambios").performScrollTo().performClick()
        compose.onNodeWithText("Generar").assertIsEnabled()
        compose.runOnIdle {assertEquals(1,edits);assertEquals("Prompt aplicado",current.plan.prompt)}
    }

    @Test fun uncertainSubmissionCannotExposeAnotherGenerateAction() {
        check(BuildConfig.OFFLINE_QA)
        compose.setContent {NexusTheme {PlanCardView(PlanUi(plan(),true,PlanState.UNCERTAIN,jobId="fixture-job"),
            {},{}, {fail("No retry allowed")},{},{})}}
        compose.onAllNodesWithText("Generar").assertCountEquals(0)
        compose.onNodeWithText("Ver en Nexus").assertExists()
        compose.onNodeWithText("Envío sin confirmar. Revisa Historial antes de generar otra vez.").assertExists()
    }
}
