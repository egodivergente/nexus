package com.vidadomu.nexus.ui.components

import com.vidadomu.nexus.core.model.*

internal data class CatalogPrice(val usd: Double, val unit: String)

/** Unknown billing units must never be interpreted as a five-second clip. */
internal fun catalogPrice(model: NexusModel, seconds: Double, rate: Double? = model.basePriceUsd): CatalogPrice? {
    if (!model.priceUnitVerified || rate == null || !rate.isFinite() || rate < 0) return null
    val unit = model.priceUnit?.lowercase()?.trim()
    val duration = seconds.takeIf { it.isFinite() && it > 0 } ?: return null
    val durationLabel = java.math.BigDecimal.valueOf(duration).stripTrailingZeros().toPlainString() + " s"
    val result = when (unit) {
        "image", "img", "imagen" -> CatalogPrice(rate, "imagen")
        "second", "seconds", "s", "seg", "segundo" -> if (Modality.VIDEO in model.modalities)
            CatalogPrice(rate * duration, durationLabel) else CatalogPrice(rate, "s")
        "5 s", "5s" -> CatalogPrice(rate * duration / 5.0, durationLabel)
        "minute", "min" -> CatalogPrice(rate, "min")
        "generation", "gen", "request" -> CatalogPrice(rate, "generación")
        "clip" -> CatalogPrice(rate, "clip")
        else -> null
    }
    return result?.takeIf { it.usd.isFinite() }
}
