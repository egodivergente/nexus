package com.vidadomu.nexus.core.model

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Exact numeric limits taken verbatim from a schema. [ModelParameter.minValue]/[ModelParameter.maxValue]
 * stay Float for existing consumers, rounded outwards so they never reject a value the exact limit allows.
 */
object ParameterBounds {
    val spanish: Locale = Locale("es", "ES")

    fun exact(literal: String?): String? = literal?.trim()?.toBigDecimalOrNull()?.toPlainString()

    fun legacyFloat(exact: String?, upper: Boolean): Float? {
        val decimal = exact?.toBigDecimalOrNull() ?: return null
        val rounded = decimal.toFloat()
        if (!rounded.isFinite()) return if (upper) Float.MAX_VALUE else -Float.MAX_VALUE
        val comparison = BigDecimal(rounded.toDouble()).compareTo(decimal)
        return when {
            upper && comparison < 0 -> Math.nextUp(rounded)
            !upper && comparison > 0 -> Math.nextDown(rounded)
            else -> rounded
        }
    }

    fun minimum(parameter: ModelParameter): BigDecimal? =
        parameter.minExact?.toBigDecimalOrNull() ?: parameter.minValue?.let(::fromFloat)
    fun maximum(parameter: ModelParameter): BigDecimal? =
        parameter.maxExact?.toBigDecimalOrNull() ?: parameter.maxValue?.let(::fromFloat)

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
        val number = value.trim().toBigDecimalOrNull() ?: return false
        return (minimum != null && number < minimum) || (maximum != null && number > maximum)
    }
}
