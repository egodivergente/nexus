package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.ModelParameter
import com.vidadomu.nexus.core.model.ParameterBounds
import com.vidadomu.nexus.core.model.ParameterLabels
import com.vidadomu.nexus.core.model.ParameterType
import java.util.Locale
import kotlin.math.abs

/** Display-only labels. The dropdown always sends the original option to the provider. */
internal object ParameterPresentation {
    data class Label(val primary: String, val secondary: String? = null)
    private val formatKeys = setOf("aspect_ratio", "aspectRatio", "ratio", "resolution", "size", "image_size")
    private val common = listOf(1 to 1, 16 to 9, 9 to 16, 4 to 3, 3 to 4, 21 to 9, 9 to 21, 3 to 2, 2 to 3, 5 to 4, 4 to 5, 2 to 1, 1 to 2)
    private fun dimensions(value: String): Pair<Int,Int>? {
        val match = Regex("^([0-9]+)\\s*[:xX*×]\\s*([0-9]+)$").matchEntire(value.trim()) ?: return null
        val w = match.groupValues[1].toIntOrNull() ?: return null
        val h = match.groupValues[2].toIntOrNull() ?: return null
        return (w to h).takeIf { w > 0 && h > 0 }
    }
    fun options(parameter: ModelParameter): List<String> {
        if(parameter.options.isNotEmpty()) return parameter.options
        // Offer presets only when the native contract supplies a dimension syntax and edge limits.
        if(parameter.key !in setOf("size","image_size") || dimensions(parameter.defaultValue) == null ||
            parameter.minValue == null || parameter.maxValue == null) return emptyList()
        val separator = parameter.defaultValue.firstOrNull { it in ":xX*×" } ?: return emptyList()
        return (listOf(parameter.defaultValue) + listOf(1024 to 1024,1280 to 720,720 to 1280,1024 to 768,
            768 to 1024,1792 to 768,768 to 1792,1536 to 1024,1024 to 1536).map { (w,h) -> "$w$separator$h" })
            .distinct().filter { value -> dimensions(value)?.let { (w,h) ->
                w >= parameter.minValue && h >= parameter.minValue && w <= parameter.maxValue && h <= parameter.maxValue
            } == true }
    }
    fun title(parameter: ModelParameter): String = if(parameter.key in formatKeys && options(parameter).any { dimensions(it) != null })
        "Formato" else when (parameter.key.lowercase()) {
            "return_last_frame" -> "Guardar último fotograma"
            "generate_audio", "enable_audio", "sound", "audio" -> "Generar sonido"
            "keep_original_sound" -> "Conservar sonido original"
            "duration", "duration_seconds", "seconds" -> "Duración"
            "resolution" -> "Resolución"
            "seed" -> "Semilla"
            "output_format" -> "Tipo de archivo"
            "num_images", "number_of_images" -> "Número de imágenes"
            else -> if (ParameterLabels.isRawKey(parameter.key, parameter.displayName)) ParameterLabels.label(parameter.key)
                else parameter.displayName
        }

    fun help(parameter: ModelParameter): String = ParameterLabels.help(parameter.key) ?: ParameterLabels.UNKNOWN_HELP

    /** Text published by the provider, shown untranslated and attributed, only when it adds something. */
    fun providerNote(parameter: ModelParameter): String? =
        parameter.description.trim().takeIf { it.isNotEmpty() && it != ParameterLabels.help(parameter.key) }

    /** Native key for diagnostics only; never used as the visible title. */
    fun diagnosticKey(parameter: ModelParameter): String = "Clave técnica: " + parameter.key

    fun range(parameter: ModelParameter, locale: Locale = ParameterBounds.spanish): String? =
        ParameterBounds.range(parameter, locale)

    /** Boolean choices: Automático ("") is a distinct third state and is never folded into "false". */
    fun booleanChoices(parameter: ModelParameter): List<Pair<String, String>> =
        (if (parameter.isRequired) emptyList() else listOf("" to "Automático")) + listOf("true" to "Sí", "false" to "No")

    /** Spoken label; selection itself is announced through stateDescription. */
    fun chipDescription(parameter: ModelParameter, option: String, optionTitle: String? = null): String =
        title(parameter) + ": " + (optionTitle ?: describe(parameter, option))

    fun proportion(parameter: ModelParameter, value: String): Float? =
        if (parameter.key in formatKeys) dimensions(value)?.let { (w,h) -> w.toFloat() / h } else null

    fun label(parameter: ModelParameter, value: String): Label {
        if(value.isBlank()) return Label("Automático")
        if(parameter.type == ParameterType.BOOLEAN)
            return Label(when(value) { "true" -> "Sí"; "false" -> "No"; else -> value })
        if(parameter.key.lowercase() in setOf("duration", "duration_seconds", "seconds") && value.toDoubleOrNull() != null)
            return Label("$value s")
        if(parameter.key !in formatKeys) return Label(value)
        if(value.equals("auto", true)) return Label("Automático")
        if(value.lowercase() in setOf("match_input_image", "same_as_input", "original")) return Label("Como la referencia")
        val (w,h) = dimensions(value) ?: return Label(value)
        val ratio = w.toDouble()/h
        val exact = common.firstOrNull { (a,b) -> w.toLong()*b == h.toLong()*a }
        val nearby = exact ?: common.minBy { (a,b) -> abs(a.toDouble()/b/ratio-1) }
            .takeIf { (a,b) -> abs(a.toDouble()/b/ratio-1) <= .02 }
        fun gcd(a: Int, b: Int): Int = if(b == 0) a else gcd(b,a%b)
        val divisor = gcd(w,h)
        val proportion = nearby ?: (w/divisor to h/divisor)
        val approximate = nearby != null && exact == null
        val orientation = when { w == h -> "Cuadrado"; w > h -> "Horizontal"; else -> "Vertical" }
        val primary = (if(approximate) "≈ " else "") + "${proportion.first}:${proportion.second} · $orientation"
        val pixels = if(w > 100 || h > 100 || ':' !in value) "$w × $h px" else null
        return Label(primary, pixels?.let { if(approximate) "$it · proporción aproximada" else it })
    }
    fun describe(parameter: ModelParameter, value: String): String = label(parameter,value).let {
        listOfNotNull(it.primary,it.secondary).joinToString(" · ")
    }
}
