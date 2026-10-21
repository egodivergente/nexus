package com.vidadomu.nexus.assistant

import android.net.Uri
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.tools.ToolInputException
import com.vidadomu.nexus.provider.flow.FlowBridgeClient
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * El agente de Nexus conversa con el agente de Google Flow a través del puente privado (AXELPC o ASUS):
 * le encarga generaciones, colecciones o descargas, lee su respuesta y negocia si pregunta algo.
 * Lo que genere se copia a la Biblioteca de Nexus y se devuelve como asset_ids. Con la cuenta Ultra no cuesta dinero.
 */
internal class FlowAgentOperations(private val app: NexusApplication) {
    private val bridge = FlowBridgeClient(com.vidadomu.nexus.core.network.OkHttpClientFactory.create(app.secureStorage).newBuilder()
        .retryOnConnectionFailure(true).build(), app.secureStorage)

    /** Leer el estado no cambia nada en Flow: si una conexión reutilizada se corta, se reintenta. */
    private suspend fun status(id: String): FlowBridgeClient.AgentTurn {
        var last: Throwable? = null
        repeat(3) { attempt ->
            bridge.agentStatus(id).onSuccess { return it }.onFailure { last = it }
            delay(1_000L * (attempt + 1))
        }
        throw ToolInputException(last?.message ?: "Se perdió la conexión con Flow.")
    }

    suspend fun run(operation: String, input: JsonObject): JsonObject {
        val limits=AssistantRuntime.get(app).settings.limits()
        val requested=(input["wait_seconds"] as? JsonPrimitive)?.longOrNull ?: 90L
        require(requested>=0) {"La espera no puede ser negativa."}
        val wait=if(limits.maxFlowWaitSeconds==0) requested else minOf(requested,limits.maxFlowWaitSeconds.toLong())
        val turn = when (operation) {
            "flow_agent_send" -> {
                val message = (input["message"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (message.isEmpty()) throw ToolInputException("Indica message: lo que quieres pedir al agente de Flow.")
                bridge.agentSend(message).getOrElse { throw ToolInputException(it.message ?: "No se pudo hablar con el agente de Flow.") }
            }
            "flow_agent_read" -> {
                val id = (input["conversation_id"] as? JsonPrimitive)?.contentOrNull
                    ?: throw ToolInputException("Indica conversation_id (lo devolvió flow_agent_send).")
                status(id)
            }
            else -> throw ToolInputException("Operación de Flow desconocida.")
        }
        var current = turn
        val now=System.currentTimeMillis()
        val deadline=now+minOf(wait,(Long.MAX_VALUE-now)/1000)*1000
        while (current.state != "completed" && System.currentTimeMillis() < deadline) {
            delay(4_000)
            current = status(current.id)
        }
        val saved = if (current.state == "completed") saveMedia(current) else emptyList()
        return buildJsonObject {
            put("conversation_id", current.id)
            put("state", current.state)
            put("reply", current.reply.text)
            putJsonArray("options") { current.reply.options.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("assets") { saved.forEach { (assetId, media) -> addJsonObject {
                put("asset_id", assetId); put("kind", media.kind); put("label", media.label)
                if (media.width > 0) { put("width", media.width); put("height", media.height) }
            } } }
            put("media_in_reply", current.reply.media.size)
            put("hint", when {
                current.state != "completed" -> "El agente de Flow sigue trabajando. Llama a flow_agent_read con este conversation_id."
                current.reply.options.isNotEmpty() -> "Flow propone opciones: elige una (o decide con el usuario) y respóndele con flow_agent_send."
                saved.isNotEmpty() -> "Los archivos ya están en la Biblioteca de Nexus (asset_ids)."
                else -> "Respuesta solo de texto."
            })
        }
    }

    /** Copia a la Biblioteca cada archivo de la respuesta una sola vez (aunque se lea varias veces). */
    private suspend fun saveMedia(turn: FlowBridgeClient.AgentTurn): List<Pair<String, FlowBridgeClient.AgentMedia>> {
        val known = SAVED.getOrPut(turn.id) { ConcurrentHashMap() }
        val temp = File(app.cacheDir, "flow_agent").apply { mkdirs() }
        return turn.reply.media.take(AssistantRuntime.get(app).settings.limits().let {it.maximum(it.maxFlowMedia)}).mapNotNull { media ->
            val key = "${media.row}/${media.index}"
            known[key]?.let { return@mapNotNull it to media }
            val file = bridge.agentMedia(turn.id, media, temp).getOrNull() ?: return@mapNotNull null
            try {
                val attachment = AgentMediaStore.persist(app, Uri.fromFile(file.file), file.mimeType)
                val id = attachment.assetId ?: attachment.id
                known[key] = id
                id to media
            } finally { file.file.delete() }
        }
    }

    companion object {
        private val SAVED = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()
    }
}
