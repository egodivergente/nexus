package com.vidadomu.nexus.assistant

import kotlinx.serialization.json.*
import java.util.concurrent.TimeUnit

/** A flat, recent quote for this exact model/version, never another model or currency. */
internal fun cachedBytePlusRates(model: String, quote: JsonObject?, now: Long): Pair<Double, Double>? {
    val updated = (quote?.get("updated_at") as? JsonPrimitive)?.longOrNull ?: return null
    if (updated <= 0 || updated > now + 60_000 || updated < now - TimeUnit.HOURS.toMillis(24)) return null
    for (element in (quote["rows"] as? JsonArray).orEmpty()) {
        val row = element as? JsonObject ?: continue
        val base = (row["model"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
        val suffix = model.removePrefix("$base-")
        if (model != base && !(model.startsWith("$base-") && suffix.matches(Regex("[0-9]{6}")))) continue
        if ((row["currency"] as? JsonPrimitive)?.contentOrNull != "USD") continue
        val input = (row["input_usd_per_million"] as? JsonPrimitive)?.doubleOrNull ?: continue
        val output = (row["output_usd_per_million"] as? JsonPrimitive)?.doubleOrNull ?: continue
        if (input.isFinite() && output.isFinite() && input >= 0 && output >= 0) return input to output
    }
    return null
}
