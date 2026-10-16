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
 * podía aparecer dos o tres veces con varios botones GENERAR. No envía ni borra archivos.
 */
internal object ChatRestore {
    data class Result(val items: List<ChatItem>, val plans: Map<String, PlanUi>, val orders: OrderBookState, val discardedPlanIds: Set<String>)

    private val OPEN = setOf(PlanState.PREPARED, PlanState.AWAITING_CONFIRMATION, PlanState.CONFIRMED)

    fun clean(saved: SavedAssistantChat, now: Long = System.currentTimeMillis()): Result {
        // 1. La misma tarjeta (mismo plan) repetida: se conserva la primera.
        val seenPlans = mutableSetOf<String>()
        var items = saved.items.filter { it !is ChatItem.PlanCard || seenPlans.add(it.planId) }

        // 2. Encargos abiertos duplicados (mismo modelo y mismos archivos): se conserva el más reciente.
        val cards = items.filterIsInstance<ChatItem.PlanCard>()
        val open = cards.filter { saved.plans[it.planId]?.state in OPEN }
        val discarded = mutableSetOf<String>()
        open.groupBy { card -> saved.plans.getValue(card.planId).plan.let { p -> Triple(p.ref, p.media.map { it.uri }.sorted(), if (p.media.isEmpty()) p.prompt else "") } }
            .values.filter { it.size > 1 }.forEach { group -> group.dropLast(1).forEach { discarded += it.planId } }
        items = items.filterNot { it is ChatItem.PlanCard && it.planId in discarded }

        // 3. Cada tarjeta con su encargo: los guardados se respetan; las antiguas se migran.
        val known = saved.orders?.orders.orEmpty().associateBy { it.id }
        val migrated = mutableListOf<Order>()
        items = items.map { item ->
            if (item !is ChatItem.PlanCard) return@map item
            val existing = item.orderId?.let { known[it] } ?: known.values.firstOrNull { it.planId == item.planId }
            if (existing != null) return@map item.copy(orderId = existing.id)
            val p = saved.plans[item.planId] ?: return@map item
            val order = legacyOrder(p, now)
            migrated += order
            item.copy(orderId = order.id)
        }
        val referenced = items.filterIsInstance<ChatItem.PlanCard>().mapNotNull { it.orderId }.toSet()
        val base = saved.orders ?: OrderBookState()
        val orders = base.copy(orders = base.orders.filter { it.id in referenced } + migrated)
        val plans = saved.plans.filterKeys { id -> items.any { it is ChatItem.PlanCard && it.planId == id } }
        return Result(items, plans, orders, discarded)
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
        // Solo un envío rechazado («No se envió: …») es un error del encargo; otros avisos no.
        val rejected = p.error?.startsWith("No se envió") == true
        val failure = if (status == OrderStatus.FAILED || (status == OrderStatus.AWAITING_CONFIRMATION && rejected))
            listOf(OrderFailure(if (p.state == PlanState.SUBMITTED) FailureKind.JOB_FAILED else FailureKind.PROVIDER_REJECTED,
                p.error ?: "El proveedor devolvió un error sin detalle.", now, p.plan.id, 1)) else emptyList()
        return Order(id = OrderBook.newId(), planId = p.plan.id, status = if (failure.isNotEmpty()) OrderStatus.FAILED else status,
            modelName = p.plan.modelName, modelRef = p.plan.ref.toString(), modality = p.plan.modality, fingerprint = p.plan.fingerprint(),
            jobId = p.jobId, issues = p.issues, failures = failure, createdAt = p.plan.createdAt, updatedAt = now)
    }
}
