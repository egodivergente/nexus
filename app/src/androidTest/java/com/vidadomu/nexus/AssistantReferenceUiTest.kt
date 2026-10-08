package com.vidadomu.nexus

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vidadomu.nexus.assistant.*
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.spend.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantReferenceUiTest {
    @get:Rule val compose = createComposeRule()
    private fun plan() = GenerationPlan("reference-fixture", ModelRef("fixture", "edit"), "Edit de prueba", "IMAGE",
        prompt = "Fixture", params = emptyMap(), effectiveParams = emptyMap(), createdAt = 1)

    @Test fun pendingCardExposesReferencesAndDiscardWithoutOpeningDetails() {
        check(BuildConfig.OFFLINE_QA)
        var discarded = false
        compose.setContent { MaterialTheme {
            CompactGeneration(PlanUi(plan(), true, PlanState.PREPARED), emptyList(), {}, {}, { discarded = true }, {}, false) {}
        } }
        compose.onNodeWithText("Adjuntar referencias").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Descartar").performClick()
        compose.runOnIdle { assertTrue(discarded) }
    }

    @Test fun referenceCountReflectsTheInputsAttachedToTheCard() {
        check(BuildConfig.OFFLINE_QA)
        val p = plan().copy(media = listOf(MediaRef("reference", "file:///fixture1.png", "image/png"), MediaRef("reference", "file:///fixture2.png", "image/png")))
        compose.setContent { MaterialTheme { CompactGeneration(PlanUi(p, true, PlanState.PREPARED), emptyList(), {}, {}, {}, {}, false) {} } }
        compose.onNodeWithText("Cambiar referencias · 2").assertIsDisplayed()
    }

    @Test fun updatingAReferenceBlocksTheBatchButton() {
        check(BuildConfig.OFFLINE_QA)
        val first = plan(); val second = plan().copy(id = "second")
        val ui = AssistantUiState(plans = mapOf(first.id to PlanUi(first, true, PlanState.PREPARED, busy = true),
            second.id to PlanUi(second, true, PlanState.PREPARED, includeInBatch = true)))
        compose.setContent { MaterialTheme { PendingGenerations(ui) { fail("Must not generate while attaching") } } }
        compose.onNodeWithText("Generar 1").assertIsNotEnabled()
    }

    @Test fun oldUnselectedCardsCannotEnterTheBatch() {
        check(BuildConfig.OFFLINE_QA)
        val p = plan()
        compose.setContent { MaterialTheme { PendingGenerations(AssistantUiState(plans = mapOf(p.id to PlanUi(p, true, PlanState.PREPARED)))) { fail("Not selected") } } }
        compose.onNodeWithText("Selecciona los encargos").assertIsDisplayed()
        compose.onNodeWithText("Generar 0").assertIsNotEnabled()
    }
}
