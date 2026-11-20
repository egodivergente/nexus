package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.orders.Order
import com.vidadomu.nexus.agent.core.orders.OrderStatus
import com.vidadomu.nexus.agent.core.tools.AgentStep
import com.vidadomu.nexus.agent.core.tools.AgentStepKind
import com.vidadomu.nexus.agent.core.tools.AgentStepStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words of the activity timeline and of the header's status line: what the user reads, without a device. */
class AgentActivityTextTest {
    private fun step(id: String, status: AgentStepStatus, ms: Long? = null, kind: AgentStepKind = AgentStepKind.TOOL,
        label: String = "Buscando en tu Biblioteca", command: String? = null, input: String? = null, result: String? = null) =
        AgentStep(id, kind, label, status, tool = if (kind == AgentStepKind.TOOL) "search_assets" else null, startedAt = 1_000L,
            durationMs = ms, command = command, input = input, result = result)

    private fun order(id: String, status: OrderStatus) = Order(id = id, planId = "p_$id", status = status, modelName = "Modelo",
        modelRef = "atlas_cloud:modelo", modality = "VIDEO", createdAt = 0, updatedAt = 0)

    private fun status(thinking: Boolean = false, steps: List<AgentStep> = emptyList(), queued: Boolean = false, elsewhere: Boolean = false,
        orders: List<Order> = emptyList(), attaching: Boolean = false, carrying: Boolean = false, offline: Boolean = false,
        project: String = "Corto del faro", model: String = "Gemini 3.8 Flash") =
        agentHeaderStatus(thinking, steps, queued, elsewhere, orders, attaching, carrying, offline, project, model)

    @Test fun durationsUseTenthsBelowOneSecondAndWholeSecondsAfter() {
        assertEquals("0,0 s", AgentActivityText.duration(0))
        assertEquals("0,2 s", AgentActivityText.duration(249))
        assertEquals("0,9 s", AgentActivityText.duration(999))
        assertEquals("1 s", AgentActivityText.duration(1_000))
        assertEquals("8 s", AgentActivityText.duration(8_900))
        assertEquals("59 s", AgentActivityText.duration(59_999))
        assertEquals("1 min 0 s", AgentActivityText.duration(60_000))
        assertEquals("1 min 4 s", AgentActivityText.duration(64_300))
        assertEquals("un reloj que retrocede no enseña tiempos negativos", "0,0 s", AgentActivityText.duration(-5))
    }

    @Test fun everyStatusHasItsOwnName() {
        assertEquals(listOf("En curso", "Completado", "Con incidencia", "Cancelado"), AgentStepStatus.values().map(AgentActivityText::state))
        assertEquals("los cuatro estados se distinguen", 4, AgentStepStatus.values().map(AgentActivityText::state).toSet().size)
    }

    @Test fun foldedHeaderCountsStepsAddsTheirTimeAndCallsOutIncidents() {
        val steps = listOf(step("a", AgentStepStatus.DONE, 200), step("b", AgentStepStatus.ISSUE, 8_000), step("c", AgentStepStatus.DONE, 34))
        assertEquals("3 pasos · 8 s", AgentActivityText.summary(steps))
        assertEquals("1 incidencia", AgentActivityText.issues(steps))
        assertEquals("2 incidencias", AgentActivityText.issues(steps + step("d", AgentStepStatus.ISSUE, 1)))
        assertNull("sin incidencias no se anuncia ninguna", AgentActivityText.issues(steps.filterNot { it.status == AgentStepStatus.ISSUE }))
        assertEquals("1 paso · 0,2 s", AgentActivityText.summary(steps.take(1)))
        assertEquals("un cancelado no cuenta como incidencia", null, AgentActivityText.issues(listOf(step("x", AgentStepStatus.CANCELLED, 10))))
    }

    @Test fun messagesSavedBeforeTheTimelineListTheirToolsWithoutInventingTimes() {
        val legacy = AgentActivityText.legacy("a1", listOf("search_assets", "prepare_orders", "herramienta_nueva"))
        assertEquals(listOf("a1:tool:0", "a1:tool:1", "a1:tool:2"), legacy.map { it.id })
        assertEquals(listOf("Buscando en tu Biblioteca", "Preparando los encargos", "Usando herramienta nueva"), legacy.map { it.label })
        assertTrue(legacy.all { it.status == AgentStepStatus.DONE && it.kind == AgentStepKind.TOOL && it.durationMs == null })
        assertEquals("sin duración medida solo se dice cuántos fueron", "3 pasos", AgentActivityText.summary(legacy))
        assertEquals("el detalle de la fila enseña la herramienta como orden", "prepare_orders", legacy[1].command)
        assertTrue("y por eso la fila se puede abrir", legacy.all { it.hasDetail })
        assertTrue(AgentActivityText.legacy("a2", emptyList()).isEmpty())
    }

    @Test fun copiedDetailCarriesCommandInputAndOutcomeUnderTheRightTitle() {
        val issue = step("i", AgentStepStatus.ISSUE, 300, command = "search_assets text=faro project=x", input = "{\n  \"text\": \"faro\"\n}",
            result = "No existe el proyecto x.")
        assertEquals("search_assets text=faro project=x\n\nEntrada\n{\n  \"text\": \"faro\"\n}\n\nIncidencia\nNo existe el proyecto x.", AgentActivityText.copyText(issue))
        assertEquals("Resultado\n3 archivos", AgentActivityText.copyText(step("d", AgentStepStatus.DONE, 300, result = "3 archivos")))
        assertEquals("Cancelado\nDetenido por ti.", AgentActivityText.copyText(step("c", AgentStepStatus.CANCELLED, 300, result = "Detenido por ti.")))
        assertEquals("los campos vacíos no dejan títulos sueltos", "", AgentActivityText.copyText(step("e", AgentStepStatus.DONE, 300, command = " ", input = "", result = null)))
    }

    @Test fun headerSaysWhatRunsNowWhileTheAgentWorks() {
        val steps = listOf(step("a", AgentStepStatus.DONE, 200, label = "Buscando modelos en el catálogo"),
            step("b", AgentStepStatus.RUNNING, label = "Preparando los encargos"))
        assertEquals(AgentHeaderStatus("Preparando los encargos", true), status(thinking = true, steps = steps))
        assertEquals("antes del primer paso, o entre dos, sigue trabajando", AgentHeaderStatus("Trabajando", true),
            status(thinking = true, steps = steps.take(1)))
        assertEquals("Preparando los encargos · 1 mensaje en espera", status(thinking = true, steps = steps, queued = true).text)
        assertEquals("el turno manda sobre los encargos en curso", "Preparando los encargos",
            status(thinking = true, steps = steps, orders = listOf(order("o1", OrderStatus.PROCESSING))).text)
    }

    @Test fun headerAtRestNamesProjectAndModelAndKeepsTheDotOff() {
        assertEquals(AgentHeaderStatus("Corto del faro · Gemini 3.8 Flash", false), status())
        assertEquals(AgentHeaderStatus("Corto del faro", false), status(model = ""))
        assertEquals(AgentHeaderStatus("Listo", false), status(project = "", model = " "))
        assertEquals(AgentHeaderStatus("Corto del faro · Planificador local", false), status(offline = true))
        assertFalse("un mensaje en espera sin turno en curso no enciende nada", status(queued = true).active)
    }

    @Test fun headerReportsBackgroundWorkWithTheDotOn() {
        assertEquals(AgentHeaderStatus("Preparando adjuntos", true), status(attaching = true))
        assertEquals(AgentHeaderStatus("Resumiendo el contexto", true), status(carrying = true))
        assertEquals(AgentHeaderStatus("Trabajando en otra conversación", true), status(elsewhere = true))
        assertEquals(AgentHeaderStatus("1 encargo en curso", true), status(orders = listOf(order("o1", OrderStatus.SUBMITTING), order("o2", OrderStatus.COMPLETED))))
        assertEquals(AgentHeaderStatus("2 encargos en curso", true), status(orders = listOf(order("o1", OrderStatus.SUBMITTING), order("o2", OrderStatus.PROCESSING))))
        assertFalse("un encargo fallido o por confirmar no es trabajo en curso",
            status(orders = listOf(order("o1", OrderStatus.FAILED), order("o2", OrderStatus.AWAITING_CONFIRMATION))).active)
    }

    private fun timed(id: String, startedAt: Long, ms: Long? = null, kind: AgentStepKind = AgentStepKind.TOOL) =
        AgentStep(id, kind, "Buscando en tu Biblioteca", if (ms == null) AgentStepStatus.RUNNING else AgentStepStatus.DONE,
            startedAt = startedAt, durationMs = ms)

    @Test fun aTurnThatKeepsMovingIsNotSlowHoweverLongItRuns() {
        val t0 = 1_700_000_000_000L
        // The turn of 19-11: nine steps of a few seconds each, 35 s in all, and the note came up at second 30.
        val done = (0 until 8).map { timed("s$it", t0 + it * 4_000L, 3_900L) }
        val steps = done + timed("s8", t0 + 32_000L)
        assertFalse("35 s de turno con el paso en curso recién empezado", AgentActivityText.slow(t0 + 35_000L, steps, t0))
        assertFalse(AgentActivityText.slow(t0 + 61_999L, steps, t0))
        assertTrue("ese mismo paso, 30 s parado", AgentActivityText.slow(t0 + 62_000L, steps, t0))
        assertEquals(t0 + 32_000L, AgentActivityText.lastSignOfLife(steps, t0))
    }

    @Test fun slowCountsFromTheLastSignOfLife() {
        val t0 = 1_700_000_000_000L
        assertFalse("antes del primer paso cuenta desde el inicio del turno", AgentActivityText.slow(t0 + 29_999L, emptyList(), t0))
        assertTrue(AgentActivityText.slow(t0 + 30_000L, emptyList(), t0))
        // Between two steps the clock runs from the end of the last one.
        val ended = listOf(timed("a", t0 + 6_000L, 4_000L))
        assertEquals(t0 + 10_000L, AgentActivityText.lastSignOfLife(ended, t0))
        assertFalse(AgentActivityText.slow(t0 + 39_999L, ended, t0))
        assertTrue(AgentActivityText.slow(t0 + 40_000L, ended, t0))
        // One long step that is writing the reply: each piece of text is a sign of life.
        val writing = listOf(timed("t", t0 + 1_000L, kind = AgentStepKind.THINK))
        assertTrue("sin texto, el paso lleva 59 s", AgentActivityText.slow(t0 + 60_000L, writing, t0))
        assertFalse("con texto hace 10 s", AgentActivityText.slow(t0 + 60_000L, writing, t0, lastTextAt = t0 + 50_000L))
        assertTrue("el texto dejó de llegar hace 30 s", AgentActivityText.slow(t0 + 80_000L, writing, t0, lastTextAt = t0 + 50_000L))
    }

    @Test fun withoutAClockNothingIsCalledSlow() {
        val legacy = AgentActivityText.legacy("a1", listOf("search_assets"))
        assertEquals(0L, AgentActivityText.lastSignOfLife(legacy, 0L))
        assertFalse(AgentActivityText.slow(1_700_000_000_000L, legacy, 0L))
        assertFalse("un reloj que retrocede no dispara el aviso", AgentActivityText.slow(1_000L, emptyList(), 50_000L))
    }
}
