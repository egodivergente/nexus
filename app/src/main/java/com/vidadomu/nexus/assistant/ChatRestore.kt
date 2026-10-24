package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.orders.FailureKind
import com.vidadomu.nexus.agent.core.orders.Order
import com.vidadomu.nexus.agent.core.orders.OrderBook
import com.vidadomu.nexus.agent.core.orders.OrderBookState
import com.vidadomu.nexus.agent.core.orders.OrderFailure
import com.vidadomu.nexus.agent.core.orders.OrderStatus
import com.vidadomu.nexus.agent.core.spend.PlanState

/**
 * Limpia un chat guardado antes de mostrarlo: una tarjeta por encargo. Las versiones anteriores
 * guardaban una tarjeta nueva en cada envío y en cada replanificación, así que un mismo encargo
 * podía aparecer dos o tres veces con varios botones GENERAR. No envía ni borra archivos: los
 * trabajos enviados siguen en el historial de Nexus.
 */
internal object ChatRestore {
    data class Result(val items: List<ChatItem>, val plans: Map<String, PlanUi>, val orders: OrderBookState, val discardedPlanIds: Set<String>)

    private val OPEN = setOf(PlanState.PREPARED, PlanState.AWAITING_CONFIRMATION, PlanState.CONFIRMED)
    /** Estados en los que dos tarjetas del mismo modelo y archivos son el mismo encargo. */
    private val MERGEABLE = setOf(OrderStatus.DRAFT, OrderStatus.AWAITING_CONFIRMATION, OrderStatus.FAILED)
    /** «Enviado a X. Lo verás en Historial…»: la tarjeta ya muestra el estado real. */
    private val STALE_SENT = Regex("^Enviado a .+\\. Lo verás en Historial")

    fun clean(saved: SavedAssistantChat, now: Long = System.currentTimeMillis()): Result {
        // 1. La misma tarjeta (mismo plan) repetida: se conserva la primera.
        val seenPlans = mutableSetOf<String>()
        val discardedOrders = saved.orders?.orders.orEmpty().filter { it.status == OrderStatus.DISCARDED }.map { it.id }.toSet()
        var items = saved.items.filter { it !is ChatItem.PlanCard ||
            (saved.plans[it.planId] != null && saved.plans[it.planId]?.state != PlanState.DISCARDED &&
                it.orderId !in discardedOrders && seenPlans.add(it.planId)) }
        // Avisos de versiones anteriores que contradicen el estado verificado de la tarjeta.
        items = items.filterNot { it is ChatItem.Notice && !it.error && STALE_SENT.containsMatchIn(it.text) }

        // 2. Cada tarjeta con su encargo: los guardados se respetan; las antiguas se migran.
        val known = saved.orders?.orders.orEmpty().associateBy { it.id }
        val orderOf = mutableMapOf<String, Order>()
        items = items.map { item ->
            if (item !is ChatItem.PlanCard) return@map item
            val p = saved.plans[item.planId] ?: return@map item
            val order = item.orderId?.let { known[it] } ?: known.values.firstOrNull { it.planId == item.planId } ?: legacyOrder(p, now)
            orderOf[item.planId] = order
            item.copy(orderId = order.id)
        }

        // 3. Mismo encargo repetido (mismo modelo y archivos) mientras está abierto o tras fallar:
        //    se conserva la tarjeta más reciente, con los intentos anteriores como historial.
        //    Completados, en curso e inciertos se conservan siempre (resultados o posible cobro).
        val discarded = mutableSetOf<String>()
        val removed = mutableSetOf<String>()
        val merged = mutableMapOf<String, Order>()
        items.filterIsInstance<ChatItem.PlanCard>().filter { orderOf[it.planId]?.status in MERGEABLE }
            .groupBy { card -> saved.plans.getValue(card.planId).plan.fingerprint() }
            .values.filter { it.size > 1 }.forEach { group ->
                val keep = orderOf.getValue(group.last().planId)
                val older = group.dropLast(1).map { orderOf.getValue(it.planId) }
                var attempt = 0
                val history = (older + keep).flatMap { it.failures }.map { f -> f.copy(attempt = ++attempt) }
                merged[keep.id] = keep.copy(
                    attempt = maxOf(older.size + 1, history.size),
                    failures = history,
                    previousPlanIds = (older.flatMap { it.previousPlanIds + it.planId } + keep.previousPlanIds).distinct(),
                    createdAt = older.minOf { it.createdAt }.coerceAtMost(keep.createdAt),
                )
                group.dropLast(1).forEach { card ->
                    removed += card.planId
                    // Solo los abiertos se anulan en el guard; los enviados conservan su job en Historial.
                    if (saved.plans[card.planId]?.state in OPEN) discarded += card.planId
                }
            }
        items = items.filterNot { it is ChatItem.PlanCard && it.planId in removed }

        val cards = items.filterIsInstance<ChatItem.PlanCard>()
        val referenced = cards.mapNotNull { it.orderId }.toSet()
        val finalOrders = cards.mapNotNull { c -> orderOf[c.planId]?.let { merged[it.id] ?: it } }.distinctBy { it.id }.filter { it.id in referenced }
        val base = saved.orders ?: OrderBookState()
        val plans = saved.plans.filterKeys { id -> cards.any { it.planId == id } }
        return Result(items, plans, base.copy(orders = finalOrders), discarded)
    }

    private fun failureOf(p: PlanUi, attempt: Int, now: Long): OrderFailure? {
        val rejected = p.error?.startsWith("No se envió") == true
        val failed = p.state == PlanState.SUBMITTED && p.jobStatus in setOf("FAILED", "CANCELLED")
        if (!failed && !(p.state in OPEN && rejected)) return null
        val kind = when { p.jobStatus == "CANCELLED" -> FailureKind.CANCELLED; failed -> FailureKind.JOB_FAILED; else -> FailureKind.PROVIDER_REJECTED }
        return OrderFailure(kind, p.error?.removePrefix("No se envió: ") ?: "El proveedor devolvió un error sin detalle.", now, p.plan.id, attempt)
    }

    private fun legacyOrder(p: PlanUi, now: Long): Order {
        val status = when (p.state) {
            PlanState.PREPARED, PlanState.AWAITING_CONFIRMATION, PlanState.CONFIRMED -> if (p.issues.isEmpty()) OrderStatus.AWAITING_CONFIRMATION else OrderStatus.DRAFT
            PlanState.SUBMITTED -> when (p.jobStatus) {
                "COMPLETED" -> OrderStatus.COMPLETED
                "FAILED", "CANCELLED" -> OrderStatus.FAILED
                else -> OrderStatus.PROCESSING
            }
            PlanState.UNCERTAIN -> OrderStatus.UNCERTAIN
            PlanState.DISCARDED -> OrderStatus.DISCARDED
        }
        // Solo un envío rechazado («No se envió: …») o un trabajo fallido es un error del encargo; otros avisos no.
        val own = failureOf(p, 1, now)
        return Order(id = OrderBook.newId(), planId = p.plan.id, status = if (own != null) OrderStatus.FAILED else status,
            modelName = p.plan.modelName, modelRef = p.plan.ref.toString(), modality = p.plan.modality, fingerprint = p.plan.fingerprint(),
            jobId = p.jobId, issues = p.issues, failures = listOfNotNull(own), createdAt = p.plan.createdAt, updatedAt = now)
    }
}
