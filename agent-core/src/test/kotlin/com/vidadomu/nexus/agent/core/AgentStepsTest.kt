package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.LlmException
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.text
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.tools
import com.vidadomu.nexus.agent.core.tools.AgentStep
import com.vidadomu.nexus.agent.core.tools.AgentStepKind
import com.vidadomu.nexus.agent.core.tools.AgentStepStatus
import com.vidadomu.nexus.agent.core.tools.AgentSteps
import com.vidadomu.nexus.agent.core.tools.NexusToolkit
import com.vidadomu.nexus.agent.core.tools.UiEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** La línea de actividad del chat se alimenta de estos pasos: tienen que salir de lo que el turno hace de verdad. */
class AgentStepsTest {
    private val config = AgentConfig(provider = "scripted", modelId = "test-model", region = "eu-west-1", endpoint = "http://proxy", maxToolRounds = 4)

    private fun orchestrator(h: Harness, llm: ScriptedLlmProvider) =
        AgentOrchestrator(llm, config, NexusToolkit.all(h.deps), h.skills, h.assets, today = { "2026-11-18" })

    /** Estado final de cada paso, en el orden en que empezaron (como lo acumula la pantalla). */
    private fun timeline(events: List<UiEvent>): List<AgentStep> =
        events.filterIsInstance<UiEvent.Step>().fold(emptyList()) { acc, e -> AgentSteps.upsert(acc, e.step) }

    @Test fun `cada llamada al modelo y cada herramienta es un paso que empieza y acaba`() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf(
            { tools(use("u1", "list_projects"), text = "Voy a mirar tus proyectos.") },
            { text("No hay proyectos todavía.") },
        ))
        val seen = mutableListOf<UiEvent>()
        orchestrator(h, llm).let { o -> o.send(o.newConversation(mode = AgentMode.AGENT), "¿qué proyectos tengo?") { seen += it } }

        val raw = seen.filterIsInstance<UiEvent.Step>().map { it.step }
        assertEquals(6, raw.size, "tres pasos, cada uno anunciado y cerrado")
        assertEquals(listOf(AgentStepStatus.RUNNING, AgentStepStatus.DONE), raw.filter { it.id == raw.first().id }.map { it.status })

        val steps = timeline(seen)
        assertEquals(listOf(AgentStepKind.THINK, AgentStepKind.TOOL, AgentStepKind.THINK), steps.map { it.kind })
        assertEquals(listOf("Pensando la respuesta", "Usando list projects", "Analizando los resultados"), steps.map { it.label })
        assertTrue(steps.all { it.status == AgentStepStatus.DONE && it.durationMs != null && it.startedAt > 0 })
        assertEquals(3, steps.map { it.id }.distinct().size)
        // Lo que dijo antes de usar la herramienta se puede desplegar; la respuesta final no se duplica en su paso.
        assertEquals("Voy a mirar tus proyectos.", steps[0].result)
        assertNull(steps[2].result)
        val tool = steps[1]
        assertEquals("list_projects", tool.tool)
        assertEquals("list_projects", tool.command)
        assertNotNull(tool.result, "el resultado real de la herramienta queda disponible")
        // «Pensando…» sigue recibiendo sus frases (lo usa también el chat de Cut).
        assertEquals(3, seen.filterIsInstance<UiEvent.Activity>().size)
    }

    @Test fun `una herramienta que falla queda como incidencia con su error y el turno sigue`() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf(
            { tools(use("bad", "no_existe", """{"asset_id":"ast_falso","nota":"prueba"}""")) },
            { r -> assertTrue(r.result("bad").isError); text("No he podido hacerlo.") },
        ))
        val seen = mutableListOf<UiEvent>()
        val turn = orchestrator(h, llm).let { o -> o.send(o.newConversation(mode = AgentMode.AGENT), "hazlo") { seen += it } }
        assertEquals("No he podido hacerlo.", turn.text)
        val issue = timeline(seen).single { it.kind == AgentStepKind.TOOL }
        assertEquals(AgentStepStatus.ISSUE, issue.status)
        assertEquals("no_existe asset_id=ast_falso nota=prueba", issue.command)
        assertTrue(issue.input!!.contains("\"asset_id\": \"ast_falso\""))
        assertTrue(issue.result!!.contains("no está disponible"), issue.result)
        assertEquals(1, AgentSteps.issues(timeline(seen)))
    }

    @Test fun `si el modelo falla el paso de pensar se cierra como incidencia antes de abortar`() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf({ throw LlmException(LlmException.Kind.THROTTLED, "Demasiadas peticiones", httpStatus = 429) }))
        val seen = mutableListOf<UiEvent>()
        assertFailsWith<LlmException> {
            orchestrator(h, llm).let { o -> o.send(o.newConversation(mode = AgentMode.AGENT), "hola") { seen += it } }
        }
        val step = timeline(seen).single()
        assertEquals(AgentStepStatus.ISSUE, step.status)
        assertEquals("THROTTLED · HTTP 429 · Demasiadas peticiones", step.result)
        assertNotNull(step.durationMs)
    }

    @Test fun `la linea de orden resume argumentos y nunca ensena secretos`() {
        val input = Json.parseToJsonElement("""{"query":"faro al amanecer con niebla espesa y gaviotas sobre el acantilado","limit":5,"media":[1,2,3],"options":{"a":1},"vacio":null,"api_key":"sk-abcdefghijklmnop"}""")
        val line = AgentSteps.command("search_assets", input)
        assertTrue(line.startsWith("search_assets query=faro al amanecer con niebla espesa y gaviotas s… "), line)
        assertTrue("limit=5" in line && "media=[3]" in line && "options={…}" in line, line)
        assertFalse("vacio" in line)
        assertFalse("sk-abcdefghijklmnop" in line, line)
        assertFalse("sk-abcdefghijklmnop" in AgentSteps.detail(input)!!, "el detalle también pasa por el redactor")
        assertTrue(AgentSteps.command("x", buildJsonObject { put("texto", "a".repeat(40) + " " + "b".repeat(400)) }).length <= AgentSteps.COMMAND_CHARS)
        assertNull(AgentSteps.detail(buildJsonObject { }))
    }

    @Test fun `el detalle se recorta y un error simple se ensena tal cual`() {
        val long = AgentSteps.clip("x".repeat(AgentSteps.DETAIL_CHARS + 500))
        assertTrue(long.startsWith("x".repeat(AgentSteps.DETAIL_CHARS)) && long.endsWith("(recortado: ${AgentSteps.DETAIL_CHARS + 500} caracteres)"))
        assertEquals("Tiempo agotado ejecutando web_search.", AgentSteps.outcome(buildJsonObject { put("error", "Tiempo agotado ejecutando web_search.") }, isError = true))
        val ok = AgentSteps.outcome(buildJsonObject { put("count", 2) }, isError = false)!!
        assertTrue(ok.contains("\"count\": 2"))
    }

    @Test fun `al cerrar el turno nada queda en curso y lo guardado tiene tope`() {
        val running = AgentStep("t:0", AgentStepKind.THINK, "Pensando la respuesta", startedAt = 1_000)
        val done = AgentStep("t:1", AgentStepKind.TOOL, "Buscando", AgentStepStatus.DONE, tool = "search_assets", startedAt = 900, durationMs = 40)
        val closed = AgentSteps.close(listOf(done, running), AgentStepStatus.CANCELLED, now = 3_500, reason = "Cancelado por el usuario.")
        assertEquals(done, closed[0])
        assertEquals(AgentStepStatus.CANCELLED, closed[1].status)
        assertEquals(2_500L, closed[1].durationMs)
        assertEquals("Cancelado por el usuario.", closed[1].result)
        assertEquals(2_540L, AgentSteps.totalMs(closed))
        assertEquals(listOf(done), AgentSteps.forStorage(listOf(done, running)))
        val many = (0 until 80).map { done.copy(id = "t:$it") }
        assertEquals(AgentSteps.KEPT_PER_TURN, AgentSteps.forStorage(many).size)
        assertEquals("t:79", AgentSteps.forStorage(many).last().id)
    }

    @Test fun `un paso guardado se lee igual y un lector antiguo ignora campos nuevos`() {
        val json = Json { ignoreUnknownKeys = true }
        val step = AgentStep("t:1", AgentStepKind.TOOL, "Buscando en tu Biblioteca", AgentStepStatus.ISSUE, "search_assets", 5, 120, "search_assets query=faro", "{}", "sin red")
        assertEquals(step, json.decodeFromString(AgentStep.serializer(), json.encodeToString(AgentStep.serializer(), step)))
        val future = """{"id":"t:2","kind":"THINK","label":"Pensando","status":"DONE","campo_futuro":{"x":1}}"""
        assertEquals(AgentStepStatus.DONE, json.decodeFromString(AgentStep.serializer(), future).status)
    }
}
