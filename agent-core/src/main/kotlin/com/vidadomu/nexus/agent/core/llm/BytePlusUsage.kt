package com.vidadomu.nexus.agent.core.llm

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable data class BytePlusUsageEntry(
    val id: String, val date: String, val model: String, val turn: String,
    val state: String = "pending", val reservedUsd: Double? = null,
    val input: Long? = null, val output: Long? = null, val estimatedUsd: Double? = null,
)
@Serializable data class BytePlusUsageLimits(
    val enabled: Boolean = false, val turnUsd: Double? = null, val dayUsd: Double? = null,
    val monthUsd: Double? = null, val dayCalls: Int? = null,
    val alertUsd: Double? = null, val alertTokens: Long? = null,
)
@Serializable data class BytePlusUsageData(
    val entries: List<BytePlusUsageEntry> = emptyList(), val limits: BytePlusUsageLimits = BytePlusUsageLimits(),
    val since: String = LocalDate.now().toString(), val zone: String = ZoneId.systemDefault().id,
)
data class BytePlusUsageTotals(val calls: Int, val input: Long?, val output: Long?, val usd: Double?, val uncertain: Int)

/** Nexus-only ledger. Official account snapshots never enter this ledger or its limits. */
class BytePlusUsageLedger(private val file: File, private val today: () -> String = { LocalDate.now().toString() }) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun read(): BytePlusUsageData = try {
        if (file.exists()) {
            val payload = file.readText()
            val document = json.parseToJsonElement(payload) as? JsonObject ?: error("Invalid ledger")
            require(document.keys.containsAll(setOf("entries", "limits", "since", "zone")))
            json.decodeFromString(payload)
        } else BytePlusUsageData(since = today())
    } catch (_: Exception) { throw LlmException(LlmException.Kind.BUDGET, "No se pudo leer el consumo BytePlus. Se conserva el registro; no se envían nuevas llamadas BytePlus.") }
    private fun write(data: BytePlusUsageData) {
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        try {
            FileOutputStream(temp).use { it.write(json.encodeToString(data).toByteArray()); it.fd.sync() }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) { throw LlmException(LlmException.Kind.BUDGET, "No se pudo guardar el consumo BytePlus; no se inicia otra llamada.") }
    }
    @Synchronized fun snapshot() = read()
    @Synchronized fun settings(value: BytePlusUsageLimits) {
        listOf(value.turnUsd, value.dayUsd, value.monthUsd, value.alertUsd).forEach { require(it == null || it.isFinite() && it > 0) }
        require(value.dayCalls == null || value.dayCalls > 0)
        require(value.alertTokens == null || value.alertTokens > 0)
        write(read().copy(limits = value))
    }
    @Synchronized fun reserve(model: String, turn: String, amount: Double?): String {
        require(amount == null || amount.isFinite() && amount >= 0)
        val data = read(); val limit = data.limits; val day = today()
        val rows = data.entries.filter { it.state != "rejected" }
        val daily = rows.filter { it.date == day }; val monthly = rows.filter { it.date.take(7) == day.take(7) }
        fun blocked(message: String): Nothing = throw LlmException(LlmException.Kind.BUDGET, message + " Solo cubre llamadas BytePlus desde Nexus en este dispositivo; no otras aplicaciones.")
        fun check(bound: Double?, subset: List<BytePlusUsageEntry>, label: String) {
            if (bound == null) return
            if (amount == null || subset.any { it.estimatedUsd == null && it.reservedUsd == null })
                blocked("No hay tarifa o uso suficiente para aplicar el límite $label. Configura las tarifas del cerebro BytePlus.")
            if (subset.sumOf { it.estimatedUsd ?: it.reservedUsd ?: 0.0 } + amount!! > bound)
                blocked("Límite local BytePlus $label alcanzado. No se ha enviado la llamada.")
        }
        if (limit.enabled) {
            if (limit.dayCalls != null && daily.size >= limit.dayCalls) blocked("Límite local de llamadas BytePlus alcanzado.")
            check(limit.turnUsd, rows.filter { it.turn == turn }, "por turno")
            check(limit.dayUsd, daily, "diario"); check(limit.monthUsd, monthly, "mensual")
        }
        val id = UUID.randomUUID().toString()
        write(data.copy(entries = data.entries + BytePlusUsageEntry(id, day, model, turn, reservedUsd = amount)))
        return id
    }
    @Synchronized fun settle(id: String, usage: Usage, rates: Pair<Double, Double>?) {
        require(usage.inputTokens >= 0 && usage.outputTokens >= 0)
        val data = read()
        // A repeated completion for the same reservation replaces nothing and never doubles usage.
        val rows = data.entries.map { row ->
            if (row.id != id || row.state != "pending") row else {
                val reported = usage.inputTokens > 0 || usage.outputTokens > 0
                row.copy(state = "settled", input = usage.inputTokens.toLong().takeIf { reported },
                    output = usage.outputTokens.toLong().takeIf { reported }, estimatedUsd =
                    rates?.takeIf { reported }?.let { (usage.inputTokens * it.first + usage.outputTokens * it.second) / 1_000_000 })
            }
        }
        write(data.copy(entries = rows))
    }
    @Synchronized fun failed(id: String, definitelyRejected: Boolean) {
        val data = read()
        write(data.copy(entries = data.entries.map { if (it.id == id && it.state == "pending") it.copy(state = if (definitelyRejected) "rejected" else "uncertain") else it }))
    }
    fun totals(entries: List<BytePlusUsageEntry>): BytePlusUsageTotals {
        val rows = entries.filter { it.state != "rejected" }; val settled = rows.filter { it.state == "settled" }
        return BytePlusUsageTotals(rows.size,
            settled.takeIf { it.isNotEmpty() && it.all { r -> r.input != null } }?.sumOf { it.input!! },
            settled.takeIf { it.isNotEmpty() && it.all { r -> r.output != null } }?.sumOf { it.output!! },
            settled.takeIf { it.isNotEmpty() && it.all { r -> r.estimatedUsd != null } }?.sumOf { it.estimatedUsd!! },
            rows.count { it.state == "pending" || it.state == "uncertain" })
    }
}

private open class UsageProvider(protected val provider: LlmProvider, private val ledger: BytePlusUsageLedger,
    private val rates: Pair<Double, Double>?, private val onUpdate: () -> Unit) : LlmProvider {
    override val id get() = provider.id
    override suspend fun countInputTokens(request: LlmRequest) = provider.countInputTokens(request)
    protected suspend fun tracked(request: LlmRequest, call: suspend () -> LlmResponse): LlmResponse {
        // Deliberately conservative reservation; actual tokens replace it only after a response.
        val bytes = ConverseCodec.encodeRequest(request).toString().toByteArray().size.toLong() + 1024
        val amount = rates?.let { (bytes * it.first + request.maxTokens * it.second) / 1_000_000 }
        val turn = request.turnId ?: kotlin.coroutines.coroutineContext[LlmTurnContext]?.turnId ?: UUID.randomUUID().toString()
        val ticket = ledger.reserve(request.modelId, turn, amount)
        try {
            return call().also { ledger.settle(ticket, it.usage, rates); runCatching { onUpdate() } }
        } catch (e: Exception) {
            ledger.failed(ticket, e is LlmException && e.kind in setOf(LlmException.Kind.AUTH, LlmException.Kind.BAD_REQUEST,
                LlmException.Kind.MODEL_NOT_ALLOWED, LlmException.Kind.THROTTLED, LlmException.Kind.BUDGET))
            throw e
        }
    }
    override suspend fun complete(request: LlmRequest) = tracked(request) { provider.complete(request) }
}
private class StreamingUsageProvider(private val stream: StreamingLlmProvider, ledger: BytePlusUsageLedger,
    rates: Pair<Double, Double>?, onUpdate: () -> Unit) : UsageProvider(stream, ledger, rates, onUpdate), StreamingLlmProvider {
    override suspend fun completeStreaming(request: LlmRequest, onTextDelta: (String) -> Unit) = tracked(request) { stream.completeStreaming(request, onTextDelta) }
}
fun trackedBytePlus(provider: LlmProvider, ledger: BytePlusUsageLedger, rates: Pair<Double, Double>?, onUpdate: () -> Unit = {}): LlmProvider {
    require(rates == null || listOf(rates.first, rates.second).all { it.isFinite() && it >= 0 })
    return if (provider is StreamingLlmProvider) StreamingUsageProvider(provider, ledger, rates, onUpdate) else UsageProvider(provider, ledger, rates, onUpdate)
}
