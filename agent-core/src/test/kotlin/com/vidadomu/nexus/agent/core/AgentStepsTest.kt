package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.agent.toolActivity
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
        assertEquals(listOf("Pensando la respuesta", "Consultando tus proyectos", "Analizando los resultados"), steps.map { it.label })
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

    @Test fun `la actividad no guarda credenciales aunque el campo no se llame api_key`() {
        val input = Json.parseToJsonElement("""{"query":"faro","access_token":"ya29.a0AfH6SMBx","auth":{"password":"mi frase de paso","client_secret":"cs-123456"},
            "headers":["Authorization: Bearer abcdefghijklmnop123456"],"max_tokens":4096,"page_token":null,"nota":"usa sk-proj-ABCDEFGHIJKLMNOPQRST y nada más"}""")
        val detail = AgentSteps.detail(input)!!
        for (secret in listOf("ya29.a0AfH6SMBx", "mi frase de paso", "cs-123456", "abcdefghijklmnop123456", "sk-proj-ABCDEFGHIJKLMNOPQRST"))
            assertFalse(secret in detail, "$secret sigue en el detalle:\n$detail")
        // Lo que no es un secreto se sigue leyendo.
        assertTrue("\"query\": \"faro\"" in detail && "\"max_tokens\": 4096" in detail && "y nada más" in detail, detail)
        val line = AgentSteps.command("call_api", input)
        assertTrue("access_token=«oculto»" in line && "query=faro" in line && "max_tokens=4096" in line, line)
        assertFalse("ya29" in line || "sk-proj" in line, line)
        // Texto libre de un resultado o de un error: clave=valor, portador y formas conocidas de clave.
        val text = AgentSteps.clip("fallo: refresh_token=1//0gAbCdEfGh; Bearer zzzzzzzzzzzzzzzz1234 en https://x.test/v?token=abcdef123456&n=2 con ghp_abcdefghijklmnopqrstuvwxyz0123")
        for (secret in listOf("1//0gAbCdEfGh", "zzzzzzzzzzzzzzzz1234", "abcdef123456", "ghp_abcdefghijklmnopqrstuvwxyz0123")) assertFalse(secret in text, text)
        assertTrue(text.startsWith("fallo: refresh_token=«oculto»"), text)
        assertEquals(text, AgentSteps.scrub(text), "ocultar dos veces no cambia nada")
        // Un secreto largo no se salva por quedar partido al acortar el argumento.
        val cut = AgentSteps.command("x", buildJsonObject { put("nota", "la clave es " + "A1b2C3d4E5".repeat(4) + " fin") })
        assertFalse("A1b2C3d4E5A1b2" in cut, cut)
        // Un resultado enorme se recorta sin recorrerlo entero y dice su tamaño real.
        val huge = AgentSteps.clip("{\"data\":\"" + "QUJD".repeat(60_000) + "\"}")
        assertTrue(huge.length < AgentSteps.DETAIL_CHARS + 60 && huge.endsWith("(recortado: 240011 caracteres)"), huge.takeLast(60))
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

    @Test fun `las herramientas habituales se describen con palabras y una desconocida conserva su nombre`() {
        val activity = ::toolActivity
        assertEquals("Consultando tus proyectos", activity("list_projects"))
        assertEquals("Buscando en tu Biblioteca", activity("search_assets"))
        assertEquals("Preparando el encargo", activity("prepare_generation"))
        assertEquals("Recuperando el seguimiento del encargo", activity("recover_generation"))
        assertEquals("Editando el montaje", activity("cut_edit"))
        assertEquals("Leyendo el guion", activity("read_story_project"))
        assertEquals("Usando herramienta nueva", activity("herramienta_nueva"))
        // Lo que se lee en la fila es una frase corta, sin guiones bajos ni nombres internos.
        val known = listOf("list_projects", "create_project", "search_project_memory", "update_project_memory", "submit_generation",
            "estimate_generation_cost", "get_generation_status", "cancel_generation", "read_library_text", "transcribe_media_asset",
            "analyze_audio_asset", "propose_story_changes", "cut_read", "get_nexus_settings", "update_nexus_settings", "update_plan",
            "suggest_replies", "compare_model_families", "create_generation_prompt", "atlas_get_balance", "inspect_media_asset")
        for (tool in known) {
            val label = activity(tool)
            assertFalse(label.startsWith("Usando ") || '_' in label, "$tool sigue sin descripción: $label")
            assertTrue(label.length <= 42, "$tool: «$label» no cabe en una línea")
        }
    }
}
