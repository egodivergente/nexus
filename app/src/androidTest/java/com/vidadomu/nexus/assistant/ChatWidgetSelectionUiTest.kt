package com.vidadomu.nexus.assistant

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vidadomu.nexus.agent.core.chat.*
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ChatWidgetSelectionUiTest {
    @get:Rule val compose = createComposeRule()
    private val options = listOf(WidgetOption("k", "Kling"), WidgetOption("pro", "Kling Pro"))
    private fun state(label: String, value: String) = compose.onNode(hasText(label) and
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value))

    @Test fun singleChoiceSendsOnlyTheLongOptionOnceAndLocksThePanel() {
        val p = WidgetSpec.Poll("p", "Modelo", options, false)
        val replies = mutableListOf<WidgetReply>()
        val ctx = WidgetCtx(null, null, "a") { replies += it; true }
        compose.setContent { NexusTheme { MdWidget(p, ctx) } }
        compose.onNodeWithText("Kling Pro").performClick()
        state("Kling Pro", "Elegida").assertExists()
        state("Kling", "No elegida").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(listOf(WidgetReply("Kling Pro", WidgetAnswer("p", setOf("pro"), "a"))), replies)
            assertFalse(ctx.submit(p, setOf("k")))
            assertEquals(1, replies.size)
        }
    }

    @Test fun multiChoiceOnlySendsOnSubmitAndCanToggleAnOptionOff() {
        val p = WidgetSpec.Poll("p", "Modelo", options + WidgetOption("max", "Kling Pro Max"), true)
        val replies = mutableListOf<WidgetReply>()
        compose.setContent { NexusTheme { MdWidget(p, androidx.compose.runtime.remember { WidgetCtx(null, null, null) { replies += it; true } }) } }
        compose.onNodeWithText("Kling").performClick()
        compose.onNodeWithText("Kling Pro").performClick()
        compose.onNodeWithText("Kling").performClick()
        compose.onNodeWithText("Kling Pro Max").performClick()
        compose.runOnIdle { assertTrue(replies.isEmpty()) }
        compose.onNodeWithText("Enviar (2)").performClick()
        state("Kling", "No elegida").assertExists()
        state("Kling Pro", "Elegida").assertExists()
        state("Kling Pro Max", "Elegida").assertExists()
        compose.runOnIdle { assertEquals(setOf("pro", "max"), replies.single().answer.optionIds) }
    }

    @Test fun comparisonRestoresOnlyOneChosenCardAndDoesNotSendDuringRendering() {
        val c = WidgetSpec.Compare("c", "Comparar", options)
        val reply = ChatWidgets.reply(c, setOf("pro"))!!
        compose.setContent { NexusTheme { MdWidget(c, WidgetCtx(reply.text, reply.answer, null) { fail("Restoring must not send"); false }) } }
        compose.onAllNodesWithText("Elegida").assertCountEquals(1)
        compose.onAllNodesWithText("Elegir").assertCountEquals(1)
        compose.onNodeWithText("Elegir").assertIsNotEnabled()
    }

    @Test fun rejectedReplyIsRetryableAndStepsNeverSend() {
        val p = WidgetSpec.Poll("p", "Modelo", options, false)
        var calls = 0
        val ctx = WidgetCtx(null, null, null) { calls++; false }
        compose.setContent { NexusTheme { androidx.compose.foundation.layout.Column {
            MdWidget(p, ctx)
            MdWidget(WidgetSpec.Steps("steps", "Preparar", listOf("Referencias")), ctx)
        } } }
        compose.onNodeWithText("Referencias").performClick()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("Kling Pro").performClick()
        state("Kling Pro", "No elegida").assertIsEnabled()
        compose.onNodeWithText("Kling Pro").performClick()
        compose.runOnIdle { assertEquals(2, calls) }
    }
}
