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
        assertEquals("la fila enseña la herramienta como orden", "prepare_orders", legacy[1].command)
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
}
