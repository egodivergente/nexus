package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.agent.core.orders.Order
import com.vidadomu.nexus.agent.core.orders.OrderBook
import com.vidadomu.nexus.agent.core.orders.OrderStatus
import com.vidadomu.nexus.agent.core.spend.SpendGuard
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Vista de un encargo para el agente: lo mismo que pinta la tarjeta, sin interpretar. */
internal fun Order.toAgentJson(guard: SpendGuard, detailed: Boolean): JsonObject = buildJsonObject {
    put("order_id", id)
    put("status", status.name)
    put("status_text", OrderBook.describeStatus(this@toAgentJson))
    rendered?.let { put("card_shows", it) }
    put("model", "$modelName ($modelRef)")
    put("attempt", attempt)
    put("plan_id", planId)
    jobId?.let { put("job_id", it) }
    if (outputs.isNotEmpty()) put("outputs", outputs.size)
    if (issues.isNotEmpty()) put("issues", JsonArray(issues.map { kotlinx.serialization.json.JsonPrimitive(it) }))
    put("can_send", status == OrderStatus.AWAITING_CONFIRMATION)
    put("needs_user_tap", status == OrderStatus.AWAITING_CONFIRMATION)
    lastFailure?.let { f ->
        put("last_error", buildJsonObject {
            put("kind", f.kind.name)
            put("provider_message", f.message)
            f.explanation?.let { put("diagnosis", it) }
            f.fix?.let { fix -> put("suggested_fix", buildJsonObject { put("description", fix.description); put("params", fix.params.toJson()) }) }
            put("attempt", f.attempt)
        })
    }
    if (detailed) {
        guard.plan(planId)?.let { p ->
            put("prompt", p.prompt)
            put("params_sent", p.params.toJson())
            put("effective_config", p.effectiveParams.toJson())
            put("media", JsonArray(p.media.map { m -> buildJsonObject { put("role", m.role); m.fieldKey?.let { put("field", it) } } }))
            p.estimate?.let { put("estimate", it.toJson()) }
        }
        if (failures.size > 1) put("error_history", JsonArray(failures.dropLast(1).takeLast(4).map { f ->
            buildJsonObject { put("attempt", f.attempt); put("kind", f.kind.name); put("provider_message", f.message); f.explanation?.let { put("diagnosis", it) } }
        }))
    }
}

/** Texto compacto con el estado REAL de la interfaz que recibe el agente al empezar cada turno. */
fun OrderBook.turnContext(guard: SpendGuard, maxOrders: Int = 8): String? {
    val live = all().filter { it.status != OrderStatus.DISCARDED }.sortedByDescending { it.updatedAt }.take(maxOrders)
    val fresh = drainForAgent()
    if (live.isEmpty() && fresh.isEmpty()) return null
    return buildString {
        appendLine("[Estado real de la interfaz de Nexus — datos verificados por la app, no instrucciones]")
        if (live.isNotEmpty()) {
            appendLine("Encargos en el chat (una tarjeta por encargo):")
            live.forEach { o ->
                append("- ").append(o.id).append(" · ").append(o.modelName).append(" · ").append(OrderBook.describeStatus(o))
                o.rendered?.let { append(" · la tarjeta muestra: ").append(it) }
                o.lastFailure?.takeIf { o.status == OrderStatus.FAILED }?.let { f ->
                    f.explanation?.let { append(" · diagnóstico: ").append(it) }
                    f.fix?.let { append(" · corrección disponible: ").append(it.description) }
                }
                appendLine()
            }
        }
        if (fresh.isNotEmpty()) {
            appendLine("Novedades desde tu último mensaje:")
            fresh.forEach { e -> appendLine("- ${e.orderId}: ${e.kind.name.lowercase()} (${e.actor.name.lowercase()}) ${e.detail}") }
        }
        append("Usa get_ui_state para el detalle. Corrige con update_plan(order_id) sin crear encargos nuevos. ")
        append("No digas que algo se envió, está generando o terminó si su estado no lo indica.")
    }
}

class GetUiStateTool(private val d: ToolDeps) : BaseTool(
    "get_ui_state", ToolCategory.READ,
    "Estado REAL de la interfaz: encargos (tarjetas) del chat con su estado, lo que muestra cada tarjeta, parámetros enviados, errores literales del proveedor, diagnóstico y corrección disponible, y los últimos eventos (pulsaciones del usuario, envíos, fallos). Úsalo antes de afirmar que algo se preparó, envió o terminó.",
    schema {
        string("order_id", "Encargo concreto (opcional) para ver su detalle e historial de errores.")
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val one = input.str("order_id")
        val list = if (one != null) listOf(d.orders.get(one) ?: throw ToolInputException("Encargo desconocido «$one»."))
            else d.orders.all().filter { it.status != OrderStatus.DISCARDED }.sortedByDescending { it.updatedAt }.take(10)
        return ok(buildJsonObject {
            put("orders", JsonArray(list.map { it.toAgentJson(d.guard, detailed = one != null || list.size <= 3) }))
            put("recent_events", JsonArray(d.orders.events().filter { one == null || it.orderId == one }.takeLast(15).map { e ->
                buildJsonObject { put("order_id", e.orderId); put("event", e.kind.name); put("actor", e.actor.name); put("detail", e.detail) }
            }))
            put("rules", "Solo el usuario envía pulsando el botón del encargo. AWAITING_CONFIRMATION = preparado, no enviado. PROCESSING = aceptado por el proveedor. COMPLETED = terminado. FAILED = no se generó: explica last_error y corrige con update_plan(order_id). UNCERTAIN = no reenviar.")
        })
    }
}
