package com.vidadomu.nexus.agent.core.orders

import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * Estado visible de un encargo. Un encargo es UNA tarjeta del chat con id persistente: los
 * reintentos, correcciones y recargas actualizan el mismo encargo, nunca crean otro.
 *
 * borrador → pendiente de confirmación → enviando → procesando → completado / error.
 */
@Serializable
enum class OrderStatus {
    /** Faltan datos o hay parámetros inválidos: no se puede enviar. */
    DRAFT,
    /** Válido, con coste mostrado: espera a que el usuario pulse el botón del encargo. */
    AWAITING_CONFIRMATION,
    /** El usuario confirmó; Nexus está subiendo/enviando. Botón bloqueado. */
    SUBMITTING,
    /** El proveedor aceptó el trabajo y lo está procesando. */
    PROCESSING,
    COMPLETED,
    /** Error definitivo (validación, rechazo del proveedor o fallo del trabajo). Corregible en el mismo encargo. */
    FAILED,
    /** No se sabe si el proveedor lo aceptó: podría haberse cobrado. Nunca se reenvía solo. */
    UNCERTAIN,
    DISCARDED,
    ;

    val terminal: Boolean get() = this == COMPLETED || this == DISCARDED
    val editable: Boolean get() = this == DRAFT || this == AWAITING_CONFIRMATION || this == FAILED
}

@Serializable
enum class FailureKind {
    /** Nexus lo bloqueó antes de enviar: nada salió del teléfono, nada se cobró. */
    VALIDATION,
    /** El proveedor rechazó la petición al recibirla (4xx): no se creó trabajo. */
    PROVIDER_REJECTED,
    /** El trabajo se creó y terminó en error. El proveedor decide si cobra. */
    JOB_FAILED,
    CANCELLED,
}

/** Corrección concreta y aplicable sobre el mismo encargo (no crea uno nuevo ni envía). */
@Serializable
data class SuggestedFix(val description: String, val params: Map<String, String> = emptyMap())

@Serializable
data class OrderFailure(
    val kind: FailureKind,
    val message: String,
    val at: Long,
    val planId: String,
    val attempt: Int,
    val fix: SuggestedFix? = null,
    /** Causa explicada con el schema real (no inventada); null si no se pudo diagnosticar. */
    val explanation: String? = null,
)

@Serializable
enum class OrderEventKind {
    CREATED, PLAN_UPDATED, CARD_RENDERED, USER_EDITED, USER_CONFIRMED, SUBMIT_STARTED,
    SUBMIT_REJECTED, SUBMIT_UNCERTAIN, ACCEPTED, COMPLETED, FAILED, DISCARDED, DUPLICATE_BLOCKED,
}

@Serializable
enum class Actor { USER, AGENT, SYSTEM, PROVIDER }

@Serializable
data class OrderEvent(
    val seq: Long,
    val orderId: String,
    val at: Long,
    val kind: OrderEventKind,
    val actor: Actor,
    val detail: String = "",
)

@Serializable
data class Order(
    val id: String,
    /** Plan vigente (el que se enviaría al pulsar el botón). */
    val planId: String,
    val status: OrderStatus,
    val modelName: String,
    val modelRef: String,
    val modality: String,
    /** 1 = primer intento; cada corrección tras un error suma uno. */
    val attempt: Int = 1,
    val fingerprint: String = "",
    val jobId: String? = null,
    val progress: Float? = null,
    val outputs: List<String> = emptyList(),
    val issues: List<String> = emptyList(),
    val failures: List<OrderFailure> = emptyList(),
    /** Planes anteriores del mismo encargo (para auditoría; ya no se pueden enviar). */
    val previousPlanIds: List<String> = emptyList(),
    /** Lo que la UI declara haber pintado: etiqueta de estado y botón activo. */
    val rendered: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val lastFailure: OrderFailure? get() = failures.lastOrNull()
}

@Serializable
data class OrderBookState(val orders: List<Order> = emptyList(), val events: List<OrderEvent> = emptyList(), val seq: Long = 0, val agentCursor: Long = 0)

/** Se lanza cuando una pulsación repetida intentaría enviar un encargo que ya está en curso. */
class DuplicateSubmission(message: String) : com.vidadomu.nexus.agent.core.spend.SpendViolation(message)

/** Se lanza ante una transición no permitida (p. ej. reenviar un encargo incierto). */
class IllegalOrderTransition(message: String) : com.vidadomu.nexus.agent.core.spend.SpendViolation(message)

/**
 * Fuente única de verdad compartida por UI, agente y motor de ejecución. La UI pinta lo que hay
 * aquí, el agente lo consulta (y recibe sus eventos al empezar cada turno) y el ciclo de envío
 * lo actualiza. Toda transición pasa por [move], que rechaza las ilegales.
 */
class OrderBook(private val clock: () -> Long = System::currentTimeMillis) {
    private val orders = linkedMapOf<String, Order>()
    private val events = ArrayDeque<OrderEvent>()
    private var seq = 0L
    private var agentCursor = 0L
    private val listeners = mutableListOf<() -> Unit>()

    @Synchronized fun all(): List<Order> = orders.values.toList()
    @Synchronized fun get(id: String): Order? = orders[id]
    @Synchronized fun byPlan(planId: String): Order? = orders.values.firstOrNull { it.planId == planId || planId in it.previousPlanIds }
    @Synchronized fun events(): List<OrderEvent> = events.toList()

    /** Devuelve la función que retira el oyente (p. ej. al destruir el ViewModel). */
    fun onChange(listener: () -> Unit): () -> Unit {
        synchronized(this) { listeners += listener }
        return { synchronized(this) { listeners -= listener } }
    }
    private fun changed() { val copy = synchronized(this) { listeners.toList() }; copy.forEach { runCatching { it() } } }

    /** Encargo abierto que corresponde a [plan] (mismo modelo y mismos archivos de entrada). */
    @Synchronized
    fun findReusable(plan: GenerationPlan): Order? = orders.values.lastOrNull { o ->
        o.status.editable && o.modelRef == plan.ref.toString() && sameInputs(o, plan)
    }

    private val planInputs = mutableMapOf<String, Set<String>>()
    private val planPrompts = mutableMapOf<String, String>()
    private fun sameInputs(o: Order, plan: GenerationPlan): Boolean {
        val inputs = planInputs[o.planId] ?: return false
        val candidate = plan.media.map { it.uri }.toSet()
        // Sin archivos, solo el mismo prompt identifica el mismo encargo.
        return if (candidate.isEmpty()) inputs.isEmpty() && planPrompts[o.planId] == plan.prompt else inputs == candidate
    }

    /**
     * Crea un encargo o actualiza [orderId] en su sitio con un plan nuevo o corregido. Tras un
     * error, cuenta como nuevo intento del MISMO encargo. Nunca reabre un encargo incierto.
     */
    fun upsertPlan(plan: GenerationPlan, orderId: String? = null, issues: List<String> = emptyList(), actor: Actor = Actor.AGENT): Order {
        val result = synchronized(this) {
            val now = clock()
            planInputs[plan.id] = plan.media.map { it.uri }.toSet()
            planPrompts[plan.id] = plan.prompt
            val status = if (issues.isEmpty()) OrderStatus.AWAITING_CONFIRMATION else OrderStatus.DRAFT
            val existing = orderId?.let { orders[it] ?: throw IllegalArgumentException("Encargo desconocido «$it».") }
            if (existing == null) {
                val created = Order(id = newId(), planId = plan.id, status = status, modelName = plan.modelName,
                    modelRef = plan.ref.toString(), modality = plan.modality, fingerprint = plan.fingerprint(),
                    issues = issues, createdAt = now, updatedAt = now)
                orders[created.id] = created
                log(created.id, OrderEventKind.CREATED, actor, "${plan.modelName}: ${describeStatus(created)}")
                created
            } else {
                if (!existing.status.editable) throw IllegalOrderTransition(when (existing.status) {
                    OrderStatus.UNCERTAIN -> "El encargo pudo enviarse ya. Comprueba Historial antes de cambiarlo."
                    OrderStatus.SUBMITTING, OrderStatus.PROCESSING -> "El encargo se está enviando o procesando; espera al resultado."
                    OrderStatus.COMPLETED -> "El encargo ya terminó. Para otra versión crea un encargo nuevo."
                    else -> "El encargo se descartó."
                })
                val retry = existing.status == OrderStatus.FAILED
                val updated = existing.copy(
                    planId = plan.id, status = status, modelName = plan.modelName, modelRef = plan.ref.toString(),
                    modality = plan.modality, fingerprint = plan.fingerprint(), issues = issues,
                    attempt = if (retry) existing.attempt + 1 else existing.attempt,
                    jobId = if (retry) null else existing.jobId, progress = null, outputs = if (retry) emptyList() else existing.outputs,
                    previousPlanIds = if (existing.planId != plan.id) (existing.previousPlanIds + existing.planId).distinct() else existing.previousPlanIds,
                    updatedAt = now,
                )
                orders[updated.id] = updated
                log(updated.id, if (actor == Actor.USER) OrderEventKind.USER_EDITED else OrderEventKind.PLAN_UPDATED, actor,
                    (if (retry) "Corrección, intento ${updated.attempt}. " else "") + describeStatus(updated))
                updated
            }
        }
        changed(); return result
    }

    /** Marca datos pendientes sin perder el último plan válido del encargo. */
    fun setIssues(orderId: String, issues: List<String>, actor: Actor = Actor.AGENT) = update(orderId) { o ->
        if (!o.status.editable) return@update o
        log(o.id, OrderEventKind.PLAN_UPDATED, actor, if (issues.isEmpty()) "Sin problemas pendientes." else "Faltan datos: ${issues.joinToString("; ")}")
        o.copy(issues = issues, status = if (issues.isEmpty() && o.status == OrderStatus.DRAFT) OrderStatus.AWAITING_CONFIRMATION else if (issues.isNotEmpty() && o.status == OrderStatus.AWAITING_CONFIRMATION) OrderStatus.DRAFT else o.status)
    }

    private val usedKeys = mutableSetOf<String>()

    /**
     * Pulsación del usuario sobre el botón del encargo. Devuelve la clave de idempotencia del
     * intento. Una segunda pulsación (mismo intento) o un encargo en curso/incierto se rechaza.
     */
    fun beginSubmit(orderId: String, fingerprint: String): String {
        val key = synchronized(this) {
            val current = orders[orderId] ?: throw IllegalArgumentException("Encargo desconocido «$orderId».")
            // Rechazo definitivo (no se creó trabajo): pulsar de nuevo es un intento nuevo del MISMO encargo.
            val o = if (current.status == OrderStatus.FAILED && current.lastFailure?.kind in RETRYABLE && current.lastFailure?.planId == current.planId)
                current.copy(status = OrderStatus.AWAITING_CONFIRMATION, attempt = current.attempt + 1) else current
            val key = "$orderId:${o.attempt}:${fingerprint.take(16)}"
            when {
                o.status == OrderStatus.UNCERTAIN -> throw IllegalOrderTransition("Este encargo pudo enviarse ya. Compruébalo en Historial antes de repetir.")
                o.status == OrderStatus.SUBMITTING || o.status == OrderStatus.PROCESSING || key in usedKeys -> {
                    log(o.id, OrderEventKind.DUPLICATE_BLOCKED, Actor.USER, "Pulsación repetida ignorada: el encargo ya está en curso.")
                    throw DuplicateSubmission("Este encargo ya se está enviando. No se ha repetido el envío.")
                }
                o.status != OrderStatus.AWAITING_CONFIRMATION -> throw IllegalOrderTransition(
                    if (o.status == OrderStatus.DRAFT) "Faltan datos: ${o.issues.joinToString("; ").ifBlank { "revisa el encargo" }}."
                    else "El encargo no está pendiente de confirmación (${o.status}).")
                o.fingerprint != fingerprint -> throw IllegalOrderTransition("El encargo cambió mientras confirmabas. Revísalo y vuelve a pulsar.")
            }
            usedKeys += key
            orders[orderId] = o.copy(status = OrderStatus.SUBMITTING, updatedAt = clock())
            log(o.id, OrderEventKind.USER_CONFIRMED, Actor.USER, "Confirmado por el usuario (intento ${o.attempt}).")
            key
        }
        changed(); return key
    }

    fun accepted(orderId: String, jobId: String) = update(orderId) { o ->
        // El seguimiento puede haber visto el trabajo antes de que vuelva el envío: idempotente.
        if (o.status in setOf(OrderStatus.PROCESSING, OrderStatus.COMPLETED, OrderStatus.FAILED) && o.jobId == jobId) return@update o
        requireFrom(o, OrderStatus.SUBMITTING, OrderStatus.UNCERTAIN, OrderStatus.PROCESSING)
        log(o.id, OrderEventKind.ACCEPTED, Actor.PROVIDER, "Aceptado por el proveedor (job $jobId).")
        o.copy(status = OrderStatus.PROCESSING, jobId = jobId)
    }

    /** Rechazo definitivo antes de crear el trabajo: corregible en el mismo encargo. */
    fun rejected(orderId: String, kind: FailureKind, message: String, fix: SuggestedFix? = null, explanation: String? = null) = update(orderId) { o ->
        if (o.status == OrderStatus.FAILED && o.lastFailure?.attempt == o.attempt) {
            // Ya registrado por el seguimiento: se completa con el diagnóstico, sin duplicar el error.
            val last = o.lastFailure!!
            return@update o.copy(failures = o.failures.dropLast(1) + last.copy(kind = kind, message = message, fix = fix ?: last.fix, explanation = explanation ?: last.explanation))
        }
        requireFrom(o, OrderStatus.SUBMITTING)
        log(o.id, OrderEventKind.SUBMIT_REJECTED, if (kind == FailureKind.VALIDATION) Actor.SYSTEM else Actor.PROVIDER, message)
        o.copy(status = OrderStatus.FAILED, failures = o.failures + OrderFailure(kind, message, clock(), o.planId, o.attempt, fix, explanation))
    }

    /**
     * Nada salió del teléfono (confirmación caducada, plan cambiado): vuelve a pendiente de
     * confirmación y libera la clave para que el usuario pueda pulsar otra vez.
     */
    fun backToReview(orderId: String, reason: String) = update(orderId) { o ->
        requireFrom(o, OrderStatus.SUBMITTING)
        usedKeys.removeAll { it.startsWith("${o.id}:${o.attempt}:") }
        log(o.id, OrderEventKind.SUBMIT_REJECTED, Actor.SYSTEM, "No enviado: $reason")
        o.copy(status = OrderStatus.AWAITING_CONFIRMATION, issues = emptyList())
    }

    /** Envío sin respuesta verificable: bloqueado hasta que el historial lo resuelva. */
    fun uncertain(orderId: String, message: String, jobId: String? = null) = update(orderId) { o ->
        requireFrom(o, OrderStatus.SUBMITTING, OrderStatus.PROCESSING)
        log(o.id, OrderEventKind.SUBMIT_UNCERTAIN, Actor.SYSTEM, message)
        o.copy(status = OrderStatus.UNCERTAIN, jobId = jobId ?: o.jobId)
    }

    /** Estado verificado del trabajo (polling o historial). Es la única vía para salir de UNCERTAIN. */
    fun jobUpdate(orderId: String, jobStatus: String, progress: Float? = null, outputs: List<String> = emptyList(), error: String? = null, fix: SuggestedFix? = null, explanation: String? = null) = update(orderId) { o ->
        if (o.status !in setOf(OrderStatus.SUBMITTING, OrderStatus.PROCESSING, OrderStatus.UNCERTAIN)) return@update o
        when (jobStatus) {
            "COMPLETED" -> { log(o.id, OrderEventKind.COMPLETED, Actor.PROVIDER, "Completado: ${outputs.size} resultado(s)."); o.copy(status = OrderStatus.COMPLETED, progress = 1f, outputs = outputs) }
            "FAILED", "CANCELLED" -> {
                val kind = if (jobStatus == "CANCELLED") FailureKind.CANCELLED else FailureKind.JOB_FAILED
                val text = error?.takeIf { it.isNotBlank() } ?: if (kind == FailureKind.CANCELLED) "Cancelado." else "El proveedor devolvió un error sin detalle."
                log(o.id, OrderEventKind.FAILED, Actor.PROVIDER, text)
                o.copy(status = OrderStatus.FAILED, progress = null, failures = o.failures + OrderFailure(kind, text, clock(), o.planId, o.attempt, fix, explanation))
            }
            else -> if (o.status == OrderStatus.PROCESSING && o.progress == progress) o else o.copy(status = OrderStatus.PROCESSING, progress = progress)
        }
    }

    fun discard(orderId: String, actor: Actor = Actor.USER) = update(orderId) { o ->
        if (o.status in setOf(OrderStatus.SUBMITTING, OrderStatus.PROCESSING, OrderStatus.UNCERTAIN)) throw IllegalOrderTransition("No se puede descartar un encargo enviado o pendiente de comprobar.")
        if (o.status == OrderStatus.DISCARDED) return@update o
        log(o.id, OrderEventKind.DISCARDED, actor, "Descartado.")
        o.copy(status = OrderStatus.DISCARDED)
    }

    /** La UI declara lo que ha pintado (estado y botón). El agente lo ve tal cual. */
    fun rendered(orderId: String, shown: String) = update(orderId) { o ->
        if (o.rendered == shown) return@update o
        log(o.id, OrderEventKind.CARD_RENDERED, Actor.SYSTEM, shown)
        o.copy(rendered = shown)
    }

    /** Eventos que el agente aún no ha visto; avanza el cursor. */
    @Synchronized
    fun drainForAgent(limit: Int = 12): List<OrderEvent> {
        val fresh = events.filter { it.seq > agentCursor && it.kind != OrderEventKind.CARD_RENDERED }
        agentCursor = seq
        return fresh.takeLast(limit)
    }

    @Synchronized fun snapshot() = OrderBookState(orders.values.toList(), events.toList(), seq, agentCursor)

    /** Restaura tras reiniciar: un envío a medias pasa a incierto, nunca se reintenta. */
    fun restore(state: OrderBookState, plans: Collection<GenerationPlan> = emptyList()) {
        synchronized(this) {
            orders.clear(); events.clear()
            state.orders.forEach { o ->
                orders[o.id] = if (o.status == OrderStatus.SUBMITTING) o.copy(status = OrderStatus.UNCERTAIN) else o
            }
            events.addAll(state.events.takeLast(MAX_EVENTS))
            seq = state.seq; agentCursor = state.agentCursor
            plans.forEach { planInputs[it.id] = it.media.map { m -> m.uri }.toSet(); planPrompts[it.id] = it.prompt }
        }
        changed()
    }

    private fun requireFrom(o: Order, vararg allowed: OrderStatus) {
        if (o.status !in allowed) throw IllegalOrderTransition("Transición no permitida desde ${o.status} en el encargo ${o.id}.")
    }

    private fun update(orderId: String, f: (Order) -> Order): Order {
        val r = synchronized(this) {
            val o = orders[orderId] ?: throw IllegalArgumentException("Encargo desconocido «$orderId».")
            val n = f(o)
            if (n != o) orders[orderId] = n.copy(updatedAt = clock())
            orders.getValue(orderId)
        }
        changed(); return r
    }

    private fun log(orderId: String, kind: OrderEventKind, actor: Actor, detail: String) {
        events.addLast(OrderEvent(++seq, orderId, clock(), kind, actor, detail.take(400)))
        while (events.size > MAX_EVENTS) events.removeFirst()
    }

    companion object {
        const val MAX_EVENTS = 300
        /** Fallos en los que el proveedor no creó trabajo: repetir no puede cobrar dos veces. */
        val RETRYABLE = setOf(FailureKind.VALIDATION, FailureKind.PROVIDER_REJECTED)
        fun newId() = "ord_" + UUID.randomUUID().toString().replace("-", "").take(12)

        fun describeStatus(o: Order): String = when (o.status) {
            OrderStatus.DRAFT -> "Borrador: faltan datos (${o.issues.joinToString("; ")})"
            OrderStatus.AWAITING_CONFIRMATION -> "Pendiente de confirmación del usuario"
            OrderStatus.SUBMITTING -> "Enviando"
            OrderStatus.PROCESSING -> "Procesando" + (o.progress?.let { " ${(it * 100).toInt()} %" } ?: "")
            OrderStatus.COMPLETED -> "Completado (${o.outputs.size} resultado(s))"
            OrderStatus.FAILED -> "Error: ${o.lastFailure?.message ?: "sin detalle"}"
            OrderStatus.UNCERTAIN -> "Envío sin confirmar (podría haberse iniciado)"
            OrderStatus.DISCARDED -> "Descartado"
        }
    }
}
