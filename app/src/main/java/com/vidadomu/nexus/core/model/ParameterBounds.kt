package com.vidadomu.nexus.core.model

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Exact numeric limits taken verbatim from a schema. [ModelParameter.minValue]/[ModelParameter.maxValue]
 * stay Float for existing consumers, rounded outwards so they never reject a value the exact limit allows;
 * a side Float cannot represent is left null there and enforced only through the exact value.
 */
object ParameterBounds {
    val spanish: Locale = Locale("es", "ES")

    private const val MAX_LITERAL = 128
    private const val MAX_DIGITS = 96

    /** Rejects literals whose plain form would need huge allocations (e.g. 1e999999999). */
    private fun safeDecimal(literal: String?): BigDecimal? {
        val text = literal?.trim()?.takeIf { it.length <= MAX_LITERAL } ?: return null
        val decimal = text.toBigDecimalOrNull() ?: return null
        val integerDigits = decimal.precision().toLong() - decimal.scale()
        return decimal.takeIf { integerDigits <= MAX_DIGITS && decimal.scale() <= MAX_DIGITS }
    }

    fun exact(literal: String?): String? = safeDecimal(literal)?.toPlainString()

    fun legacyFloat(exact: String?, upper: Boolean): Float? {
        val decimal = safeDecimal(exact) ?: return null
        val rounded = decimal.toFloat()
        if (!rounded.isFinite()) return null
        val comparison = BigDecimal(rounded.toDouble()).compareTo(decimal)
        // Rounding to Float.MAX_VALUE below the exact limit makes nextUp overflow to Infinity.
        return when {
            upper && comparison < 0 -> Math.nextUp(rounded)
            !upper && comparison > 0 -> Math.nextDown(rounded)
            else -> rounded
        }.takeIf { it.isFinite() }
    }

    fun minimum(parameter: ModelParameter): BigDecimal? =
        safeDecimal(parameter.minExact) ?: parameter.minValue?.let(::fromFloat)
    fun maximum(parameter: ModelParameter): BigDecimal? =
        safeDecimal(parameter.maxExact) ?: parameter.maxValue?.let(::fromFloat)

    private fun fromFloat(value: Float): BigDecimal? = value.takeIf { it.isFinite() }?.toString()?.toBigDecimalOrNull()

    fun format(value: BigDecimal, locale: Locale = spanish): String {
        val plain = value.stripTrailingZeros()
        return DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(locale)).apply {
            maximumFractionDigits = plain.scale().coerceAtLeast(0)
        }.format(plain)
    }

    fun range(parameter: ModelParameter, locale: Locale = spanish): String? {
        val min = minimum(parameter)
        val max = maximum(parameter)
        if (min == null && max == null) return null
        return "${min?.let { format(it, locale) } ?: "—"} – ${max?.let { format(it, locale) } ?: "—"}"
    }

    fun outOfRange(value: String, minimum: BigDecimal?, maximum: BigDecimal?): Boolean {
        val text = value.trim()
        if (text.length > MAX_LITERAL) return minimum != null || maximum != null
        val number = text.toBigDecimalOrNull() ?: return false
        return (minimum != null && number < minimum) || (maximum != null && number > maximum)
    }
}
