package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.llm.LlmException
import com.vidadomu.nexus.agent.core.llm.LlmProvider
import com.vidadomu.nexus.agent.core.llm.LlmRequest
import com.vidadomu.nexus.agent.core.llm.LlmResponse
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.agent.core.llm.StreamingLlmProvider
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Solo en depuración: el siguiente mensaje del chat del Agente lo responde un guion local en vez de un modelo.
 * No sale ninguna llamada externa ni se gasta nada. El guion pasa por el mismo bucle que usa el agente: las
 * herramientas son las reales (solo de lectura), y los pasos, duraciones e incidencias que se ven son los que
 * emite el orquestador. Sirve para comprobar en un móvil la actividad en curso, completada y con incidencia.
 */
internal object AgentChatQa {
    private data class Armed(val scenario: String, val holdMs: Long)
    @Volatile private var armed: Armed? = null

    fun isArmed(): Boolean = BuildConfig.DEBUG && armed != null

    /** The armed script is consumed by the next turn; arming never runs anything by itself. */
    fun take(): LlmProvider? {
        if (!BuildConfig.DEBUG) return null
        val next = armed ?: return null
        armed = null
        val inner = script(next.scenario)
        return if (next.scenario == "streaming") Streamed(inner, next.holdMs) else Paced(inner, next.holdMs)
    }

    fun handle(input: JsonObject): JsonObject {
        check(BuildConfig.DEBUG) { "Prueba de chat disponible solo en depuración." }
        require(input.keys.all { it in setOf("action", "scenario", "hold_ms") }) { "La prueba solo admite action, scenario y hold_ms." }
        when (input["action"]?.jsonPrimitive?.contentOrNull.orEmpty()) {
            "arm" -> {
                val scenario = input["scenario"]?.jsonPrimitive?.contentOrNull ?: "activity"
                require(scenario in SCENARIOS) { "Escenario desconocido: usa ${SCENARIOS.joinToString(", ")}." }
                val hold = input["hold_ms"]?.jsonPrimitive?.longOrNull ?: 2_500L
                require(hold in 0L..60_000L) { "hold_ms va de 0 a 60000." }
                armed = Armed(scenario, hold)
            }
            "status" -> Unit
            "clear" -> armed = null
            else -> throw IllegalArgumentException("Acción de prueba desconocida: usa arm, status o clear.")
        }
        val now = armed
        return buildJsonObject {
            put("ok", true); put("armed", now != null)
            now?.let { put("scenario", it.scenario); put("hold_ms", it.holdMs) }
        }
    }

    private val SCENARIOS = setOf("activity", "model_error", "streaming")

    /** Each model call waits [holdMs] first, so «en curso» can be looked at on a device. */
    private class Paced(private val inner: LlmProvider, private val holdMs: Long) : LlmProvider {
        override val id = "qa-local"
        override suspend fun complete(request: LlmRequest): LlmResponse { delay(holdMs); return inner.complete(request) }
    }

    /**
     * Like [Paced], and then the text arrives a few characters at a time, accumulated, as a real model streams it:
     * the live reply, its half-written formatting and the end of the turn can be looked at on a device.
     */
    private class Streamed(private val inner: LlmProvider, private val holdMs: Long) : StreamingLlmProvider {
        override val id = "qa-local"
        override suspend fun complete(request: LlmRequest): LlmResponse = completeStreaming(request) { }
        override suspend fun completeStreaming(request: LlmRequest, onTextDelta: (String) -> Unit): LlmResponse {
            delay(holdMs)
            val response = inner.complete(request)
            val text = response.message.text()
            var shown = 0
            while (shown < text.length) {
                // Seven characters a time also splits «**» in two, as the pieces of a real model do.
                shown = (shown + 7).coerceAtMost(text.length)
                onTextDelta(text.substring(0, shown))
                delay(90L)
            }
            return response
        }
    }

    private fun use(name: String, token: String, input: JsonObject) = ContentBlock.ToolUse("agent-qa-$name-$token", name, input)

    private fun script(scenario: String): LlmProvider {
        val token = UUID.randomUUID().toString().take(8)
        fun requireTools(request: LlmRequest, vararg names: String) = check(names.all { n -> request.tools.any { it.name == n } }) {
            "El chat no recibió las herramientas de lectura de la prueba."
        }
        val first: (LlmRequest) -> LlmResponse = { request ->
            requireTools(request, "list_projects", "search_assets")
            ScriptedLlmProvider.tools(use("list_projects", token, buildJsonObject { }), text = "Voy a mirar qué proyectos hay antes de buscar.")
        }
        val refused: (LlmRequest) -> LlmResponse = {
            throw LlmException(LlmException.Kind.THROTTLED, "Prueba local: el modelo ha rechazado la petición por exceso de solicitudes. Espera un momento y reintenta.", 429)
        }
        if (scenario == "model_error") return ScriptedLlmProvider(listOf(first, refused))
        // The turn of 19-11 in the Pixel: several short steps, more than 30 s in all, and an answer that streams.
        if (scenario == "streaming") return ScriptedLlmProvider(listOf<(LlmRequest) -> LlmResponse>(
            first,
            { _ -> ScriptedLlmProvider.tools(use("search_assets", "$token-a", buildJsonObject { put("text", "faro"); put("limit", 3) }),
                text = "Busco las imágenes más recientes.") },
            { _ -> ScriptedLlmProvider.tools(use("get_generation_history", "$token-b", buildJsonObject { put("limit", 5) })) },
            { _ -> ScriptedLlmProvider.text(
                "Prueba local sin modelo externo: esta respuesta llega **poco a poco, como la de un modelo real**, para ver el texto mientras se escribe.\n\n" +
                    "**Lo que he mirado**\n" +
                    "He listado tus proyectos, he buscado *«faro» en la Biblioteca* y he consultado el historial con `get_generation_history`.\n\n" +
                    "- **No he creado, movido ni borrado nada.**\n- No he preparado ni enviado ninguna generación.\n" +
                    "- La negrita, la *cursiva*, el ~~tachado~~ y el `código` llevan su estilo desde que empiezan, sin asteriscos sueltos.\n\n" +
                    "Al acabar, la conversación **no parpadea**: esta respuesta se queda donde está.") },
        ))
        return ScriptedLlmProvider(listOf<(LlmRequest) -> LlmResponse>(
            first,
            // A project that does not exist: the tool itself reports it, which is what an incident looks like.
            { _ -> ScriptedLlmProvider.tools(use("search_assets", "$token-a", buildJsonObject {
                put("text", "faro"); put("project", "proyecto-de-prueba-inexistente")
            })) },
            { _ -> ScriptedLlmProvider.tools(use("search_assets", "$token-b", buildJsonObject { put("text", "faro"); put("limit", 5) }),
                text = "Ese proyecto no existe. Busco en toda la Biblioteca.") },
            { _ -> ScriptedLlmProvider.text(
                "Prueba local sin modelo externo: he recorrido los pasos de un encargo de consulta con las herramientas reales de Nexus.\n\n" +
                    "He listado tus proyectos y he buscado «faro» en la Biblioteca. Una de las búsquedas apuntaba a un proyecto que no existe y la herramienta lo ha señalado; la he repetido sin ese filtro.\n\n" +
                    "- No he creado, movido ni borrado nada.\n- No he preparado ni enviado ninguna generación.\n- Cada paso de arriba se puede desplegar para ver su orden y su resultado.\n\n" +
                    "Si quieres, sigo desde aquí con una búsqueda distinta.") },
        ))
    }
}
