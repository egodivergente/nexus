package com.vidadomu.nexus.agent.core.llm

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Local standard-tier budget, not an account-wide AWS billing cap. Reservations survive process death. */
class LlmTurnContext(val turnId: String) : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<LlmTurnContext>
}

data class LlmLimits(val perTurnUsd: Double = 1.0, val dailyUsd: Double = 5.0,
    val perTurnTokens: Long = Long.MAX_VALUE, val dailyTokens: Long = Long.MAX_VALUE, val perTurnCalls: Int = 8)

@Serializable
internal data class LlmCharge(val id: String, val day: String, val turn: String, val usd: Double, val tokens: Long)

class LlmBudgetLedger(private val file: File, private val today: () -> String = { LocalDate.now(ZoneOffset.UTC).toString() }) {
    private fun read(): MutableList<LlmCharge> = try {
        if (!file.exists()) mutableListOf() else Json.decodeFromString<List<LlmCharge>>(file.readText()).toMutableList()
    } catch (error: Exception) { throw LlmException(LlmException.Kind.BUDGET, "No se pudo leer el registro de consumo. Se conserva para recuperarlo sin reiniciar el presupuesto.") }
    private fun write(rows: List<LlmCharge>) {
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        try {
            FileOutputStream(temp).use { it.write(Json.encodeToString(rows).toByteArray()); it.fd.sync() }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Exception) { throw LlmException(LlmException.Kind.BUDGET, "No se pudo guardar el registro de consumo. Se conserva la ultima reserva y se detienen nuevas llamadas.") }
    }
    @Synchronized fun spentToday(): Double = read().filter { it.day == today() }.sumOf { it.usd }
    @Synchronized fun reserve(turn: String, usd: Double, tokens: Long, limits: LlmLimits): String {
        require(usd.isFinite() && usd >= 0 && tokens >= 0 && limits.dailyUsd.isFinite() && limits.perTurnUsd.isFinite())
        val rows = read()
        val day = today()
        val daily = rows.filter { it.day == day }
        val current = rows.filter { it.turn == turn }
        if (current.size >= limits.perTurnCalls || current.sumOf { it.usd } + usd > limits.perTurnUsd || current.sumOf { it.tokens } + tokens > limits.perTurnTokens)
            throw LlmException(LlmException.Kind.BUDGET, "Limite de consumo de este mensaje alcanzado. El trabajo se conserva; revisa los limites en Ajustes del agente.")
        if (daily.sumOf { it.usd } + usd > limits.dailyUsd || daily.sumOf { it.tokens } + tokens > limits.dailyTokens)
            throw LlmException(LlmException.Kind.BUDGET, "Limite diario del agente alcanzado. Revisa el consumo y los limites en Ajustes; no se envio otra peticion.")
        val id = UUID.randomUUID().toString()
        rows += LlmCharge(id, day, turn, usd, tokens)
        // Keep today's usage and all entries for this turn, including a turn crossing midnight.
        write(rows.filter { it.day >= LocalDate.parse(day).minusDays(7).toString() || it.turn == turn })
        return id
    }
    @Synchronized fun settle(id: String, usd: Double, tokens: Long) {
        val rows = read().map { if (it.id == id) it.copy(usd = usd, tokens = tokens) else it }
        write(rows)
    }
}

class BudgetedLlmProvider(private val delegate: LlmProvider, private val ledger: LlmBudgetLedger,
    /** Tarifa (USD por millón de tokens entrada/salida) que el usuario fija para modelos que no son Claude. */
    private val customRates: () -> Pair<Double, Double>? = { null },
    private val limits: () -> LlmLimits,
) : LlmProvider {
    override val id = delegate.id
    // Standard geo/in-region prices with a 20% margin over global list price; no cache discount assumed.
    private fun rates(model: String): Pair<Double, Double> {
        val m = model.replace('.', '-')
        return when {
            "claude-sonnet-5-5" in m -> 2.4 to 12.0
            "claude-opus-5-5" in m -> 4.8 to 24.0
            "claude-sonnet-4-6" in m -> 3.6 to 18.0
            "claude-opus-4-6" in m -> 6.0 to 30.0
            "claude-haiku-4-5" in m -> 1.2 to 6.0
            // Sin tarifa conocida: la del usuario o, si no la indicó, solo los límites de llamadas por mensaje.
            else -> customRates() ?: (0.0 to 0.0)
        }
    }
    override suspend fun complete(request: LlmRequest): LlmResponse {
        val (inputRate, outputRate) = rates(request.modelId)
        val counted = delegate.countInputTokens(request)
        val hasDocument = request.messages.any { m -> m.content.any { it is ContentBlock.Document } }
        // Solo Bedrock cuenta tokens antes de enviar; en los demás proveedores se estima por tamaño.
        if (counted == null && hasDocument && delegate.id.startsWith("bedrock")) throw LlmException(LlmException.Kind.BUDGET,
            "Bedrock no pudo contar el documento antes de enviarlo. Revisa el permiso bedrock:CountTokens y la compatibilidad del modelo; el archivo sigue guardado.")
        val input = counted?.toLong() ?: (ConverseCodec.encodeRequest(request).toString().toByteArray().size.toLong() + 1024)
        val reserved = input * inputRate / 1_000_000 + request.maxTokens * outputRate / 1_000_000
        val ticket = ledger.reserve(request.turnId ?: kotlin.coroutines.coroutineContext[LlmTurnContext]?.turnId ?: UUID.randomUUID().toString(), reserved, input + request.maxTokens, limits())
        try {
            val response = delegate.complete(request)
            if(response.usage.inputTokens + response.usage.outputTokens > 0) ledger.settle(ticket, response.usage.inputTokens * inputRate / 1_000_000 + response.usage.outputTokens * outputRate / 1_000_000,
                response.usage.inputTokens.toLong() + response.usage.outputTokens)
            return response
        } catch (error: LlmException) {
            // Unknown network/upstream outcomes retain the reservation, including after a process restart.
            if (error.kind in setOf(LlmException.Kind.AUTH, LlmException.Kind.BAD_REQUEST, LlmException.Kind.MODEL_NOT_ALLOWED, LlmException.Kind.THROTTLED)) ledger.settle(ticket, 0.0, 0)
            throw error
        }
    }
}
