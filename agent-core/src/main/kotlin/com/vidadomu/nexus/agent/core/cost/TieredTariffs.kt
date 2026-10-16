package com.vidadomu.nexus.agent.core.cost

/**
 * Tarifas publicadas que dependen de una opción (no del precio base del catálogo). Sin esta tabla
 * Nexus mostraba la tarifa 720P (0,004 $/s) para cualquier plantilla, también 4K (≈9× más).
 *
 * Fuente: README oficial del modelo en Atlas Cloud, consultado el 2026-10-16:
 * https://static.atlascloud.ai/model/readme/tencent-video-upscaler.md
 * price = segundos × tarifa del nivel (sufijo de template) × factor de fps.
 */
object TieredTariffs {
    data class Tier(val label: String, val usdPerSecond: Double)
    data class Quote(val usd: Double?, val usdPerSecond: Double, val tier: String, val fpsFactor: Int, val seconds: Double?, val basis: String, val source: String)

    private data class Table(val tierField: String, val tiers: Map<String, Tier>, val source: String, val checkedAt: String)

    private val TABLES = mapOf(
        "tencent/video/upscaler" to Table(
            tierField = "template",
            tiers = mapOf(
                "720p" to Tier("720P", 0.004110),
                "1080p" to Tier("1080P", 0.009092),
                "2k" to Tier("2K", 0.016507),
                "4k" to Tier("4K", 0.036542),
            ),
            source = "https://static.atlascloud.ai/model/readme/tencent-video-upscaler.md",
            checkedAt = "2026-10-16",
        ),
    )

    fun covers(modelId: String): Boolean = modelId in TABLES

    /** ≤30 fps = 1, ≤60 = 2, ≤120 = 4; sin fps se factura como ≤30. Null si fps no es válido. */
    fun fpsFactor(fps: String?): Int? {
        val value = fps?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: return 1
        return when {
            value <= 0 -> null
            value <= 30 -> 1
            value <= 60 -> 2
            value <= 120 -> 4
            else -> null
        }
    }

    /** Cotización local con la tarifa publicada; null si el modelo no tiene tabla o falta el nivel. */
    fun quote(modelId: String, params: Map<String, String>, seconds: Double?): Quote? {
        val table = TABLES[modelId] ?: return null
        val selected = params[table.tierField] ?: return null
        val suffix = table.tiers.keys.firstOrNull { selected.lowercase().endsWith("-$it") } ?: return null
        val tier = table.tiers.getValue(suffix)
        val factor = fpsFactor(params["fps"]) ?: return null
        val secs = seconds?.takeIf { it.isFinite() && it > 0 }
        val rate = tier.usdPerSecond * factor
        val basis = buildString {
            append("%.6f $/s (%s".format(java.util.Locale.ROOT, tier.usdPerSecond, tier.label))
            if (factor > 1) append(" × fps ×$factor")
            append(")")
            if (secs != null) append(" × ${"%.1f".format(java.util.Locale.ROOT, secs)} s de vídeo") else append(" × duración del vídeo de entrada")
        }
        return Quote(secs?.let { it * rate }, rate, tier.label, factor, secs, basis, table.source)
    }

    fun checkedAt(modelId: String): String? = TABLES[modelId]?.checkedAt

    /** Resumen legible de la tabla, para listados (el agente no debe usar la tarifa base). */
    fun summary(modelId: String): String? = TABLES[modelId]?.let { t ->
        "Tarifa por segundo de vídeo según el nivel de ${t.tierField}: " +
            t.tiers.values.joinToString(", ") { "${it.label} ${"%.6f".format(java.util.Locale.ROOT, it.usdPerSecond)} $/s" } +
            "; ×2 con más de 30 fps y ×4 con más de 60. Fuente: ${t.source}"
    }
}
