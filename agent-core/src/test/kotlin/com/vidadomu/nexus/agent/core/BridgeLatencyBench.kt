package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.AntigravityProvider
import com.vidadomu.nexus.agent.core.tools.AgentSession
import com.vidadomu.nexus.agent.core.tools.AgentTool
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.agent.core.tools.ToolOutcome
import com.vidadomu.nexus.agent.core.tools.NexusToolkit
import com.vidadomu.nexus.agent.core.tools.UiEvent
import com.vidadomu.nexus.agent.core.tools.turnContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test

/**
 * Banco de latencia del agente real de Nexus contra un puente AXEL (AGY / Codex). No se ejecuta en la suite
 * normal: solo con `-Dnexus.bench.endpoint=http://127.0.0.1:18788`. Usa el prompt de sistema, las herramientas
 * y las rondas reales de AgentOrchestrator sobre un catálogo de prueba; nunca envía una generación (no existe
 * herramienta que confirme gasto y el backend es falso). Mide el turno completo como lo vive el usuario.
 */
class BridgeLatencyBench {
    private data class Brain(val label: String, val engine: AntigravityProvider.BridgeEngine, val model: String, val effort: String? = null)
    private data class Case(val id: String, val prompt: String, val attach: Boolean)

    private val brains = listOf(
        Brain("GPT-6.1 Terra (medio)", AntigravityProvider.BridgeEngine.CODEX, "gpt-6.1-terra", "medium"),
        Brain("Gemini 4 Hydrogen (medio)", AntigravityProvider.BridgeEngine.ANTIGRAVITY, "gemini-4-hydrogen-medium"),
        Brain("Gemini 4 Argon (medio)", AntigravityProvider.BridgeEngine.ANTIGRAVITY, "gemini-4-argon-medium"),
    )
    private val cases = listOf(
        Case("encargo", "Haz un vídeo de 5 segundos de esta foto: Mateo caminando por el Retiro al atardecer. Prepáralo con Kling O3 Pro, Seedance 2.0 y Veo 3.1 para que yo elija; no lo envíes.", attach = true),
        Case("consulta", "¿Qué modelos de vídeo tengo para animar una foto con sonido? Dímelo en dos líneas.", attach = false),
    )

    @Test fun bench() {
        val endpoint = System.getProperty("nexus.bench.endpoint") ?: return
        val runs = System.getProperty("nexus.bench.runs")?.toIntOrNull() ?: 3
        val only = System.getProperty("nexus.bench.only")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        val out = File(System.getProperty("nexus.bench.out") ?: "build/latency-bench.jsonl").apply { parentFile?.mkdirs(); delete() }
        val active = brains.filter { b -> only == null || only.any { it in b.model } }
        for (run in 1..runs) {
            // Orden alterno entre rondas para que ningún modelo vaya siempre primero (cachés, calentamiento del puente).
            val order = if (run % 2 == 1) active else active.reversed()
            for (case in cases) for (brain in order) {
                val line = runCatching { runBlocking { withTimeout(6 * 60_000L) { turn(endpoint, brain, case, run) } } }
                    .getOrElse { e -> buildJsonObject { put("run", run); put("case", case.id); put("brain", brain.label); put("model", brain.model); put("error", (e.message ?: e.javaClass.simpleName).take(300)) }.toString() }
                out.appendText(line + "\n")
                println("BENCH $line")
            }
        }
    }

    private suspend fun turn(endpoint: String, brain: Brain, case: Case, run: Int): String {
        val h = Harness()
        val conversation = "bench-${brain.model}-${case.id}-$run-${System.currentTimeMillis()}"
        val provider = AntigravityProvider(endpoint, sessionId = { conversation }, engine = brain.engine, reasoningEffort = brain.effort)
        val config = AgentConfig(provider = brain.engine.providerId, modelId = brain.model, region = "-", endpoint = endpoint, mode = AgentMode.AGENT, sendImagesToLlm = false)
        // Cada llamada a herramienta queda anotada (nombre, error, resumen) para saber por qué se repite una.
        val trace = mutableListOf<kotlinx.serialization.json.JsonObject>()
        val tools = NexusToolkit.all(h.deps).map { t -> object : AgentTool by t {
            override suspend fun run(input: kotlinx.serialization.json.JsonObject, session: AgentSession): ToolOutcome = t.run(input, session).also { out ->
                trace += buildJsonObject {
                    put("tool", t.name); put("error", out.isError)
                    if (t.name == "prepare_orders") put("input_orders", (input["orders"] as? kotlinx.serialization.json.JsonArray)?.size ?: 0)
                    put("result", out.result.toString().replace('\n', ' ').take(if (t.name == "prepare_orders") 700 else 160))
                }
            }
        } }
        val o = AgentOrchestrator(provider, config, tools, h.skills, h.assets, uiContext = { h.deps.orders.turnContext(h.guard) })
        val attachments = if (case.attach) listOf(Attachment("foto1", "file:///bench/mateo.jpg", "image/jpeg", "mateo.jpg", 1080, 1920)) else emptyList()
        val started = System.nanoTime()
        val r = o.send(o.newConversation(conversation, AgentMode.AGENT), case.prompt, attachments)
        val wall = (System.nanoTime() - started) / 1_000_000
        return buildJsonObject {
            put("run", run); put("case", case.id); put("brain", brain.label); put("model", brain.model)
            put("wall_ms", wall); put("llm_ms", r.llmMillis); put("tool_ms", r.toolMillis); put("llm_calls", r.llmCalls)
            put("tools", buildJsonArray { r.toolCalls.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            put("cards", r.events.filterIsInstance<UiEvent.PlanReady>().size)
            put("submitted", h.backend.submitted.size)
            put("stop", r.stop)
            put("text", r.text.replace('\n', ' ').take(220))
            put("trace", kotlinx.serialization.json.JsonArray(trace))
        }.toString()
    }
}
