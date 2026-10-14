package com.vidadomu.nexus.agent.core.spend

import com.vidadomu.nexus.agent.core.cost.CostEstimate
import com.vidadomu.nexus.agent.core.registry.ModelRef
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MediaRef(
    /** first_frame | last_frame | reference | video | audio */
    val role: String,
    val uri: String,
    val mimeType: String? = null,
    val assetId: String? = null,
    /** Campo del schema al que va (firstFrameField, imagesArrayField…), si se conoce. */
    val fieldKey: String? = null,
)

/** Generación preparada. Es lo que la UI muestra en la tarjeta: modelo, prompt, configuración, coste. */
@Serializable
data class GenerationPlan(
    val id: String,
    val ref: ModelRef,
    val modelName: String,
    val modality: String,
    val task: String? = null,
    val prompt: String,
    val negativePrompt: String? = null,
    /** Solo valores validados que se enviarán. */
    val params: Map<String, String>,
    /** Para mostrar: defaults del schema + elegidos (sin vacíos). */
    val effectiveParams: Map<String, String>,
    val media: List<MediaRef> = emptyList(),
    val estimate: CostEstimate? = null,
    val rationale: String? = null,
    val alternatives: List<ModelRef> = emptyList(),
    val projectId: String? = null,
    val shotId: String? = null,
    /** Códigos lógicos (escena 3, plano 12) y personajes: se registran en el índice al enviar. */
    val sceneCode: String? = null,
    val shotCode: String? = null,
    val characters: List<String> = emptyList(),
    val parentGenerationId: String? = null,
    /** Avisos para la tarjeta (duración ajustada, coste orientativo…). */
    val warnings: List<String> = emptyList(),
    val createdAt: Long,
    val originalPrompt: String? = null,
) {
    /** Huella de lo que se va a pagar. Cualquier cambio exige una confirmación nueva. */
    fun fingerprint(): String {
        val canonical = Json.encodeToString(
            listOf(ref.providerId, ref.modelId, prompt, negativePrompt ?: "",
                params.toSortedMap().toString(), media.joinToString("|") { "${it.role}=${it.uri}" })
        )
        val d = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }
}

enum class PlanState {
    PREPARED, AWAITING_CONFIRMATION, CONFIRMED, SUBMITTED, DISCARDED,
    /** El envío falló de forma ambigua: el proveedor pudo aceptarlo. No se puede volver a pulsar GENERAR. */
    UNCERTAIN,
}

/** Lo lanza el backend cuando no puede saber si el proveedor aceptó el envío (timeout, corte). */
class SubmissionUncertain(message: String, val jobId: String? = null) : Exception(message)

private val FINAL_FOR_EDIT = setOf(PlanState.SUBMITTED, PlanState.DISCARDED, PlanState.UNCERTAIN)

/** Ticket de un solo uso emitido SOLO por una acción explícita del usuario en la UI (botón GENERAR). */
class ExecutionTicket internal constructor(
    val id: String,
    val planId: String,
    val fingerprint: String,
    val issuedAt: Long,
    val expiresAt: Long,
)

class SpendViolation(message: String) : SecurityException(message)

/**
 * Guardián de gasto. Regla absoluta: ninguna generación de pago sin confirmación explícita para ESA
 * generación. Una confirmación no autoriza las siguientes (ticket de un uso, ligado a la huella del
 * plan, con caducidad). No existe herramienta del LLM que llame a [confirmByUser].
 */
class SpendGuard(
    private val clock: () -> Long = System::currentTimeMillis,
    private val ticketTtlMs: Long = 10 * 60_000L,
) {
    private val plans = ConcurrentHashMap<String, GenerationPlan>()
    private val states = ConcurrentHashMap<String, PlanState>()
    private val tickets = ConcurrentHashMap<String, ExecutionTicket>()
    private val used = ConcurrentHashMap.newKeySet<String>()

    @Synchronized
    fun register(plan: GenerationPlan): GenerationPlan {
        plans[plan.id] = plan
        states[plan.id] = PlanState.PREPARED
        return plan
    }

    fun plan(id: String): GenerationPlan? = plans[id]
    fun state(id: String): PlanState? = states[id]
    fun plans(): List<GenerationPlan> = plans.values.sortedByDescending { it.createdAt }

    /** El agente pide ejecutar: queda a la espera del usuario. No ejecuta nada. */
    @Synchronized
    fun requestConfirmation(planId: String): PlanState {
        plans[planId] ?: throw IllegalArgumentException("Plan desconocido: $planId")
        val s = states[planId]
        if (s == PlanState.SUBMITTED || s == PlanState.DISCARDED || s == PlanState.UNCERTAIN || s == PlanState.CONFIRMED) return s
        states[planId] = PlanState.AWAITING_CONFIRMATION
        return PlanState.AWAITING_CONFIRMATION
    }

    /** Sustituye un plan (edición del usuario en la tarjeta o update_plan). Invalida tickets previos. */
    @Synchronized
    fun replace(plan: GenerationPlan) {
        require(plans.containsKey(plan.id)) { "Plan desconocido: ${plan.id}" }
        val s = states[plan.id]
        if (s in FINAL_FOR_EDIT) throw SpendViolation(when (s) {
            PlanState.DISCARDED -> "El usuario descartó ese plan; prepara uno nuevo."
            PlanState.UNCERTAIN -> "Ese plan pudo enviarse; compruébalo en Historial antes de preparar otro."
            else -> "Ese plan ya se envió; prepara uno nuevo."
        })
        plans[plan.id] = plan
        states[plan.id] = PlanState.PREPARED
        tickets.values.removeIf { it.planId == plan.id }
    }

    /**
     * El envío falló SIN llegar al proveedor: el plan vuelve a PREPARED y hace falta pulsar
     * GENERAR otra vez (nunca se reintenta solo).
     */
    @Synchronized
    fun markSubmitFailed(planId: String) { if (plans.containsKey(planId)) states[planId] = PlanState.PREPARED }

    /** El envío pudo llegar al proveedor: se bloquea el plan para no pagar dos veces. */
    @Synchronized
    fun markUncertain(planId: String) { if (plans.containsKey(planId)) states[planId] = PlanState.UNCERTAIN }

    @Synchronized
    fun discard(planId: String) {
        if (states[planId] == PlanState.SUBMITTED || states[planId] == PlanState.UNCERTAIN) return
        states[planId] = PlanState.DISCARDED; tickets.values.removeIf { it.planId == planId }
    }

    /**
     * SOLO desde la UI, en respuesta al toque del usuario sobre GENERAR para este plan.
     * [expectedFingerprint] = huella del plan que el usuario TENÍA DELANTE en el diálogo: si el plan
     * cambió mientras confirmaba (p. ej. update_plan del LLM), no se emite ticket.
     */
    @Synchronized
    fun confirmByUser(planId: String, expectedFingerprint: String? = null): ExecutionTicket {
        val plan = plans[planId] ?: throw IllegalArgumentException("Plan desconocido: $planId")
        val s = states[planId]
        if (s == PlanState.SUBMITTED) throw SpendViolation("Este plan ya se envió; prepara uno nuevo para volver a generar.")
        if (s == PlanState.CONFIRMED) throw SpendViolation("Este plan ya está confirmado y en proceso de envío.")
        if (s == PlanState.DISCARDED) throw SpendViolation("Plan descartado.")
        if (s == PlanState.UNCERTAIN) throw SpendViolation("Este plan pudo enviarse ya. Compruébalo en Historial antes de generar otra vez.")
        val fp = plan.fingerprint()
        if (expectedFingerprint != null && expectedFingerprint != fp) {
            throw SpendViolation("El plan cambió mientras confirmabas. Revísalo y vuelve a pulsar GENERAR.")
        }
        val now = clock()
        val t = ExecutionTicket(UUID.randomUUID().toString(), planId, fp, now, now + ticketTtlMs)
        tickets[t.id] = t
        states[planId] = PlanState.CONFIRMED
        return t
    }

    /** Lo llama el ejecutor justo antes de enviar. Valida todo y, al final, consume el ticket. */
    @Synchronized
    fun consume(ticket: ExecutionTicket, plan: GenerationPlan) {
        val known = tickets[ticket.id] ?: throw SpendViolation("Ticket no emitido por esta sesión o ya invalidado.")
        if (known !== ticket) throw SpendViolation("Ticket falsificado.")
        if (ticket.id in used) throw SpendViolation("Ticket ya usado: cada generación necesita su propia confirmación.")
        if (clock() > ticket.expiresAt) { tickets.remove(ticket.id); states[plan.id] = PlanState.PREPARED; throw SpendViolation("La confirmación ha caducado; vuelve a pulsar GENERAR.") }
        if (ticket.planId != plan.id || ticket.fingerprint != plan.fingerprint()) {
            tickets.remove(ticket.id); states[plan.id] = PlanState.PREPARED
            throw SpendViolation("El plan cambió después de confirmar; revisa y confirma de nuevo.")
        }
        if (states[plan.id] != PlanState.CONFIRMED) {
            tickets.remove(ticket.id)
            throw SpendViolation("El plan no está en estado confirmado para envío.")
        }
        if (!used.add(ticket.id)) throw SpendViolation("Ticket ya usado: cada generación necesita su propia confirmación.")
        tickets.remove(ticket.id)
        states[plan.id] = PlanState.SUBMITTED
    }
}

@Serializable
data class SubmitResult(val jobId: String, val providerJobId: String? = null, val note: String? = null)

@Serializable
data class JobStatus(
    val jobId: String,
    /** PENDING | PROCESSING | COMPLETED | FAILED | CANCELLED | AWAITING_USER */
    val status: String,
    val progress: Float? = null,
    val outputs: List<String> = emptyList(),
    val error: String? = null,
    val actualCostUsd: Double? = null,
)

/** Puente hacia Nexus (GenerationRepository) o, en el arnés de PC, hacia los borradores del puente ADB. */
interface GenerationBackend {
    suspend fun submit(plan: GenerationPlan): SubmitResult
    suspend fun status(jobId: String): JobStatus
    suspend fun cancel(jobId: String): Boolean
}

/** Único camino hacia un envío de pago: exige ticket válido. */
class GuardedExecutor(private val backend: GenerationBackend, private val guard: SpendGuard) {
    suspend fun submit(planId: String, ticket: ExecutionTicket): SubmitResult {
        val plan = guard.plan(planId) ?: throw IllegalArgumentException("Plan desconocido: $planId")
        guard.consume(ticket, plan)
        return backend.submit(plan)
    }
    suspend fun status(jobId: String): JobStatus = backend.status(jobId)
    suspend fun cancel(jobId: String): Boolean = backend.cancel(jobId)
}
