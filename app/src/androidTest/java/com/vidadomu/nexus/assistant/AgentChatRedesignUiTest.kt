package com.vidadomu.nexus.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.agent.core.orders.FailureKind
import com.vidadomu.nexus.agent.core.orders.Order
import com.vidadomu.nexus.agent.core.orders.OrderFailure
import com.vidadomu.nexus.agent.core.orders.OrderStatus
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.PlanState
import com.vidadomu.nexus.agent.core.tools.AgentStep
import com.vidadomu.nexus.agent.core.tools.AgentStepKind
import com.vidadomu.nexus.agent.core.tools.AgentStepStatus
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The redesigned Agent screen, piece by piece, on a device. Synthetic local fixtures only: no model, no generation,
 * no conversation or setting of the installed app is read or written.
 */
class AgentChatRedesignUiTest {
    @get:Rule val compose = createComposeRule()

    private val steps = listOf(
        AgentStep("s1", AgentStepKind.THINK, "Pensando", AgentStepStatus.DONE, startedAt = 1L, durationMs = 1_800),
        AgentStep("s2", AgentStepKind.TOOL, "Buscando en tu Biblioteca", AgentStepStatus.ISSUE, tool = "search_assets", startedAt = 2L,
            durationMs = 200, command = "search_assets text=faro project=x", input = "{\n  \"text\": \"faro\"\n}", result = "No existe el proyecto x."),
        AgentStep("s3", AgentStepKind.TOOL, "Mostrando los archivos encontrados", AgentStepStatus.DONE, tool = "open_results_wall", startedAt = 3L,
            durationMs = 6_000, command = "open_results_wall limit=5", result = "{\n  \"count\": 3\n}"),
    )

    @Test fun headerLeadsWithTheTitleAndSaysWhatTheAgentDoes() {
        var menu = 0; var settings = 0; var more = 0; var title = 0
        var status by mutableStateOf(AgentHeaderStatus("Corto del faro · Gemini 3.8 Flash", false))
        compose.setContent { NexusTheme { AgentChatTheme {
            AgentChatHeader("Guion del faro", status, { menu++ }, { title++ }, { settings++ }, { more++ })
        } } }
        compose.onNodeWithText("Guion del faro").assertIsDisplayed().assertHasClickAction()
        compose.onNode(hasText("Corto del faro · Gemini 3.8 Flash") and hasStateDescription("En reposo")).assertIsDisplayed()
        compose.runOnIdle { status = AgentHeaderStatus("Buscando en tu Biblioteca", true) }
        compose.onNode(hasText("Buscando en tu Biblioteca") and hasStateDescription("Activo")).assertIsDisplayed()
        // The title leads: it sits above the status line and both stay between the menu and the settings.
        val titleBox = compose.onNodeWithText("Guion del faro").getUnclippedBoundsInRoot()
        val statusBox = compose.onNodeWithText("Buscando en tu Biblioteca").getUnclippedBoundsInRoot()
        assertTrue(statusBox.top >= titleBox.top + 12.dp)
        assertTrue(compose.onNodeWithContentDescription("Conversaciones").getUnclippedBoundsInRoot().right <= titleBox.left)
        assertTrue(compose.onNodeWithContentDescription("Ajustes").getUnclippedBoundsInRoot().left >= titleBox.right)
        compose.onNodeWithContentDescription("Conversaciones").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("Ajustes").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("Opciones de la conversación").assertHasClickAction().performClick()
        compose.onNodeWithText("Guion del faro").performClick()
        compose.runOnIdle { assertEquals(listOf(1, 1, 1, 1), listOf(menu, settings, more, title)) }
    }

    @Test fun savedActivityIsFoldedCountsItsIncidentAndOpensEachDetail() {
        compose.setContent { NexusTheme { AgentChatTheme { AgentActivitySaved("a1", steps) } } }
        val header = "Actividad del agente: 3 pasos · 8 s, 1 incidencia"
        compose.onNode(hasContentDescription(header) and hasStateDescription("Plegada")).assertIsDisplayed().assertHasClickAction()
            .assertHeightIsAtLeast(48.dp) // Dense to the eye, a full touch target to the finger.
        compose.onNodeWithText("Pensando").assertDoesNotExist()
        compose.onNodeWithContentDescription(header).performClick()
        compose.onNode(hasContentDescription(header) and hasStateDescription("Desplegada")).assertIsDisplayed()

        // Icon, description and duration in one row; the state is said, not only painted.
        compose.onNode(hasText("Pensando") and hasText("1 s") and hasStateDescription("Completado")).assertIsDisplayed().assertHasNoClickAction()
        val issue = hasText("Buscando en tu Biblioteca") and hasText("0,2 s")
        compose.onNode(issue and hasStateDescription("Con incidencia. Detalle plegado")).assertIsDisplayed().assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        // The row says what the step did, in words: the command behind it waits in the detail.
        compose.onNodeWithText("search_assets text=faro project=x").assertDoesNotExist()
        compose.onNode(hasText("Mostrando los archivos encontrados") and hasText("6 s") and hasStateDescription("Completado. Detalle plegado")).assertIsDisplayed()

        compose.onNodeWithText("No existe el proyecto x.").assertDoesNotExist()
        compose.onNode(issue).performClick()
        compose.onNode(issue and hasStateDescription("Con incidencia. Detalle desplegado")).assertIsDisplayed()
        compose.onNodeWithText("search_assets text=faro project=x").assertIsDisplayed()
        compose.onNodeWithText("Incidencia").assertIsDisplayed()
        compose.onNodeWithText("No existe el proyecto x.").assertIsDisplayed()
        compose.onNodeWithText("Entrada").assertIsDisplayed()
        compose.onNodeWithText("Copiar").assertIsDisplayed().assertHasClickAction()
        compose.onNode(issue).performClick()
        compose.onNodeWithText("No existe el proyecto x.").assertDoesNotExist()
        compose.onNodeWithText("search_assets text=faro project=x").assertDoesNotExist()
    }

    @Test fun theRowThatClosesATurnArrivesAsTheLiveActivityAndSettlesIntoItsSummary() {
        var arrived by mutableStateOf(false)
        var motion = true
        compose.setContent { NexusTheme { AgentChatTheme {
            motion = com.vidadomu.nexus.ui.theme.rememberNexusMotionAllowed()
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.testTag("activity")) {
                AgentActivitySaved("a1", steps, arrived = arrived, liveGap = 12.dp)
            }
        } } }
        val header = "Actividad del agente: 3 pasos · 8 s, 1 incidencia"
        // Its first frame is what the live activity showed: the steps, and no summary line yet.
        compose.onNodeWithText("Pensando").assertIsDisplayed()
        compose.onNodeWithText("Mostrando los archivos encontrados").assertIsDisplayed()
        compose.onNodeWithContentDescription(header).assertDoesNotExist()
        val live = compose.onNodeWithTag("activity").getUnclippedBoundsInRoot().height

        // Halfway the line is opening while the steps are still closing: one movement, not one after the other.
        // (With animations off in the device or in the app there is no halfway: it is simply settled.)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { arrived = true }
        compose.mainClock.advanceTimeBy(TurnSettleMs / 2L)
        val halfway = compose.onNodeWithTag("activity").getUnclippedBoundsInRoot().height
        if (motion) {
            compose.onNodeWithContentDescription(header).assertExists()
            compose.onNodeWithText("Pensando").assertExists()
        }
        compose.mainClock.autoAdvance = true

        // Settled: the summary line, folded, and the steps one tap away.
        compose.onNode(hasContentDescription(header) and hasStateDescription("Plegada")).assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Pensando").assertDoesNotExist()
        val settled = compose.onNodeWithTag("activity").getUnclippedBoundsInRoot().height
        assertTrue("plegada ocupa menos: $live > $settled", live > settled)
        if (motion) assertTrue("la altura cambia de forma gradual: $live > $halfway > $settled", live > halfway && halfway > settled)
        compose.onNodeWithContentDescription(header).performClick()
        compose.onNodeWithText("Pensando").assertIsDisplayed()
    }

    @Test fun liveActivityShowsWhatRunsNowAndWhatAlreadyEnded() {
        var live by mutableStateOf(emptyList<AgentStep>())
        compose.setContent { NexusTheme { AgentChatTheme { AgentActivityLive(live, android.os.SystemClock.elapsedRealtime()) } } }
        compose.onNode(hasText("Preparando la respuesta") and hasStateDescription("En curso")).assertIsDisplayed()
        compose.runOnIdle {
            live = listOf(steps[0], AgentStep("s2", AgentStepKind.TOOL, "Buscando en tu Biblioteca", AgentStepStatus.RUNNING, tool = "search_assets",
                startedAt = System.currentTimeMillis(), command = "search_assets text=faro"))
        }
        compose.onNodeWithText("Preparando la respuesta").assertDoesNotExist()
        compose.onNode(hasText("Pensando") and hasStateDescription("Completado")).assertIsDisplayed()
        compose.onNode(hasText("Buscando en tu Biblioteca") and hasStateDescription("En curso. Detalle plegado")).assertIsDisplayed()
        compose.onNodeWithText("search_assets text=faro").assertDoesNotExist()
        // The same row, by its id, becomes the finished one: nothing is added or reordered.
        compose.runOnIdle { live = listOf(steps[0], steps[1].copy(command = "search_assets text=faro")) }
        compose.onNode(hasText("Buscando en tu Biblioteca") and hasStateDescription("Con incidencia. Detalle plegado")).assertIsDisplayed()
        compose.onAllNodes(hasText("Buscando en tu Biblioteca")).assertCountEquals(1)
    }

    @Test fun composerIsOneBlockWithTheFieldAboveItsControls() {
        var sent = 0
        compose.setContent {
            var draft by remember { mutableStateOf(TextFieldValue("")) }
            NexusTheme { AgentChatTheme {
                InputBar(AssistantUiState(), draft, { draft = it }, {}, { sent++ }, {}, {},
                    reasoningControl = { ReasoningIcon("Auto", true) {}; ModelMenuItem("Gemini 3.8 Flash", true) {} })
            } }
        }
        compose.onNodeWithText("Escribe a Nexus…", useUnmergedTree = true).assertExists()
        val field = compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot()
        for (control in listOf("Añadir", "Modo de razonamiento: Auto", "Modelo: Gemini 3.8 Flash", "Dictar mensaje", "Enviar mensaje")) {
            val box = compose.onNodeWithContentDescription(control).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("«$control» va debajo del campo", box.top >= field.bottom - 1.dp)
        }
        compose.onNodeWithContentDescription("Modo de razonamiento: Auto").assertHasClickAction()
        compose.onNodeWithContentDescription("Modelo: Gemini 3.8 Flash").assertHasClickAction()
        compose.onNodeWithContentDescription("Enviar mensaje").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Parar respuesta").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("Hola")
        compose.onNodeWithContentDescription("Enviar mensaje").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, sent) }
    }

    @Test fun whileTheAgentWorksTheFieldTakesMoreAndStopStaysInReach() {
        var queued = 0; var cancelled = 0; var stopped = 0; var sent = 0
        var state by mutableStateOf(AssistantUiState(thinking = true))
        compose.setContent {
            var draft by remember { mutableStateOf(TextFieldValue("")) }
            NexusTheme { AgentChatTheme {
                InputBar(state, draft, { draft = it }, {}, { sent++ }, { stopped++ }, {},
                    onQueue = { queued++; state = state.copy(queuedSend = true) },
                    onCancelQueue = { cancelled++; state = state.copy(queuedSend = false) })
            } }
        }
        compose.onNodeWithText("Añade algo mientras trabaja", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Parar respuesta").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithContentDescription("Enviar al terminar").assertDoesNotExist()
        compose.onNodeWithContentDescription("Enviar mensaje").assertDoesNotExist()

        compose.onNode(hasSetTextAction()).assertIsEnabled().performTextInput("Y ponle música")
        compose.onNodeWithContentDescription("Parar respuesta").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithContentDescription("Enviar al terminar").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, queued) }
        compose.onNodeWithText("Se enviará cuando termine").assertIsDisplayed()
        compose.onNodeWithContentDescription("Enviar al terminar").assertDoesNotExist()
        compose.onNodeWithText("Y ponle música").assertExists()

        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(1, cancelled) }
        compose.onNodeWithText("Se enviará cuando termine").assertDoesNotExist()
        compose.onNodeWithText("Y ponle música").assertExists()

        compose.onNodeWithContentDescription("Parar respuesta").performClick()
        compose.runOnIdle { assertEquals(1, stopped); assertEquals("nada se envía solo mientras trabaja", 0, sent) }
        // The answer ends: the same draft is still there and the main action is «Enviar» again.
        compose.runOnIdle { state = state.copy(thinking = false) }
        compose.onNodeWithText("Y ponle música").assertExists()
        compose.onNodeWithContentDescription("Parar respuesta").assertDoesNotExist()
        compose.onNodeWithContentDescription("Enviar mensaje").assertIsEnabled()
    }

    @Test fun aPausedOrderOffersRecoverOnItsOwnCard() {
        val ref = ModelRef("atlas_cloud", "black-forest-labs/flux")
        val plan = GenerationPlan(id = "p-rec", ref = ref, modelName = "Flux 2 Max", modality = "IMAGE", prompt = "Plano local de prueba",
            params = emptyMap(), effectiveParams = emptyMap(), createdAt = 1)
        val order = Order(id = "o-rec", planId = plan.id, status = OrderStatus.FAILED, modelName = plan.modelName, modelRef = ref.toString(),
            modality = "IMAGE", jobId = "job-rec", createdAt = 1, updatedAt = 2,
            failures = listOf(OrderFailure(FailureKind.JOB_FAILED, "Seguimiento pausado tras 6 h sin respuesta.", 2, plan.id, 1)))
        var recovers = 0; var resends = 0; var asks = 0
        var recoverable by mutableStateOf(true)
        var busy by mutableStateOf(false)
        compose.setContent { NexusTheme { AgentChatTheme {
            OrderCard(order, PlanUi(plan, false, PlanState.SUBMITTED, busy = busy), emptyList(), false,
                { resends++ }, {}, { asks++ }, {}, {}, {}, {}, {}, recoverable = recoverable, onRecover = { recovers++ })
        } } }
        compose.onNodeWithText("Flux 2 Max").assertIsDisplayed()
        compose.onNodeWithText("Seguimiento pausado").assertIsDisplayed()
        compose.onNodeWithText("Error").assertDoesNotExist()
        compose.onNodeWithText("Recuperar").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, recovers); assertEquals(0, resends); assertEquals(0, asks) }
        compose.runOnIdle { busy = true }
        compose.onNodeWithText("Consultando…").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Consultando al proveedor…").assertIsDisplayed()
        // The same order when its job is not paused: a plain failure, with no «Recuperar».
        compose.runOnIdle { busy = false; recoverable = false }
        compose.onNodeWithText("Recuperar").assertDoesNotExist()
        compose.onNodeWithText("Error").assertIsDisplayed()
        compose.onNodeWithText("Pedir corrección al agente").assertIsDisplayed()
    }
}
