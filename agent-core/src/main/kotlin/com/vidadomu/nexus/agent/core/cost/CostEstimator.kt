package com.vidadomu.nexus.agent.core.cost

import com.vidadomu.nexus.agent.core.registry.ModelSummary
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class CostConfidence {
    /** Cotización del motor de precios de Nexus para esta configuración exacta. */
    NEXUS_QUOTE,
    /** Tarifa publicada × unidades (segundos, imágenes). Puede variar por resolución/audio. */
    PUBLISHED_RATE,
    /** Solo hay tarifa base sin unidad fiable: orientativo. */
    BASE_ONLY,
    UNKNOWN,
}

@Serializable
data class CostEstimate(
    val amountUsd: Double?,
    val confidence: CostConfidence,
    val basis: String,
    val unitPriceUsd: Double? = null,
    val unit: String? = null,
    val source: String? = null,
    val checkedAt: String? = null,
    val notes: List<String> = emptyList(),
)

interface CostEstimator {
    /** [params] = valores efectivos (defaults del schema + elegidos). */
    suspend fun estimate(model: ModelSummary, params: Map<String, String>): CostEstimate
}

@Serializable
data class PublishedPrice(
    val id: String,
    val usd: Double,
    val unit: String,
    val checkedAt: Long? = null,
    val source: String? = null,
    val status: String? = null,
)

/**
 * Usa la misma tabla de precios publicada que empaqueta Nexus (assets/atlas_published_prices.json)
 * y, si no hay entrada, la tarifa base del registro. Nunca inventa una unidad.
 */
class PublishedRateEstimator(prices: List<PublishedPrice>) : CostEstimator {

    private val byId = prices.filter { it.status == null || it.status == "ok" }.associateBy { it.id }

    override suspend fun estimate(model: ModelSummary, params: Map<String, String>): CostEstimate {
        val p = if (model.ref.providerId == ATLAS) byId[model.ref.modelId] else null
        if (p != null) {
            val checked = p.checkedAt?.let { DATE.format(Instant.ofEpochMilli(it)) }
            val common = listOf(
                "Tarifa publicada${checked?.let { " (consultada $it)" } ?: ""}; puede variar con resolución, audio u otras opciones.",
                "Nexus muestra el coste definitivo antes de enviar.",
            )
            return when (p.unit) {
                "second" -> {
                    val secs = durationSeconds(params)
                    if (secs == null) CostEstimate(null, CostConfidence.BASE_ONLY, "${usd(p.usd)}/s; falta la duración", p.usd, "second", p.source, checked, common)
                    else CostEstimate(p.usd * secs, CostConfidence.PUBLISHED_RATE, "${usd(p.usd)}/s × ${fmt(secs)} s", p.usd, "second", p.source, checked, common)
                }
                "image" -> {
                    val n = imageCount(params)
                    CostEstimate(p.usd * n, CostConfidence.PUBLISHED_RATE, "${usd(p.usd)}/imagen × $n", p.usd, "image", p.source, checked, common)
                }
                else -> CostEstimate(null, CostConfidence.BASE_ONLY, "${usd(p.usd)} por ${p.unit}", p.usd, p.unit, p.source, checked, common)
            }
        }
        val base = model.basePriceUsd ?: return CostEstimate(
            null, CostConfidence.UNKNOWN, "Nexus no dispone de precio para este modelo.",
            notes = listOf("El proveedor descontará créditos; consulta el saldo antes de generar."),
        )
        val unit = model.priceUnit?.takeIf { model.priceUnitVerified }
        return when (unit?.lowercase()) {
            "second", "seg" -> durationSeconds(params)?.let {
                CostEstimate(base * it, CostConfidence.PUBLISHED_RATE, "${usd(base)}/s × ${fmt(it)} s", base, "second")
            } ?: CostEstimate(null, CostConfidence.BASE_ONLY, "${usd(base)}/s; falta la duración", base, "second")
            "img", "image" -> imageCount(params).let {
                CostEstimate(base * it, CostConfidence.PUBLISHED_RATE, "${usd(base)}/imagen × $it", base, "image")
            }
            "gen" -> CostEstimate(base, CostConfidence.PUBLISHED_RATE, "${usd(base)} por generación", base, "gen")
            else -> CostEstimate(
                null, CostConfidence.BASE_ONLY, "Tarifa base ${usd(base)} por unidad sin especificar",
                base, model.priceUnit,
                notes = listOf("Nexus no conoce la unidad de cobro de este modelo; el total puede ser varias veces la tarifa base."),
            )
        }
    }

    companion object {
        const val ATLAS = "atlas_cloud"
        private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
        private val JSON = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): PublishedRateEstimator = PublishedRateEstimator(JSON.decodeFromString(text))

        private val DURATION_KEYS = listOf("duration", "duration_seconds", "seconds", "video_length", "length")
        private val COUNT_KEYS = listOf("n", "num_images", "num_outputs", "number_of_images", "batch_size", "image_num")

        fun durationSeconds(params: Map<String, String>): Double? =
            DURATION_KEYS.firstNotNullOfOrNull { k -> params[k]?.trim()?.removeSuffix("s")?.toDoubleOrNull() }

        fun imageCount(params: Map<String, String>): Int =
            COUNT_KEYS.firstNotNullOfOrNull { k -> params[k]?.toIntOrNull() }?.coerceAtLeast(1) ?: 1

        fun usd(v: Double): String = "%.3f $".format(java.util.Locale.ROOT, v)
        private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
    }
}

/** Para Nexus: cotización propia primero (AtlasPriceQuote/Crun/Runway) y tabla publicada si falla. */
class ChainedCostEstimator(private val chain: List<CostEstimator>) : CostEstimator {
    override suspend fun estimate(model: ModelSummary, params: Map<String, String>): CostEstimate {
        var last: CostEstimate? = null
        for (e in chain) {
            val r = runCatching { e.estimate(model, params) }.getOrNull() ?: continue
            if (r.amountUsd != null) return r
            if (last == null || r.confidence.ordinal < last.confidence.ordinal) last = r
        }
        return last ?: CostEstimate(null, CostConfidence.UNKNOWN, "Sin estimación disponible.")
    }
}

