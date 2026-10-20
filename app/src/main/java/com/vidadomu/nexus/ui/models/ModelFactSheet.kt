package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.PriceQuote
import com.vidadomu.nexus.provider.atlas.PublishedTariff

internal const val MISSING_FACT = "Sin dato"
internal const val UNDOCUMENTED_DIFFERENCE = "Diferencia no documentada"
internal const val EXAMPLE_SECONDS = 5.0

internal data class ModelFact(val label: String, val value: String?)

/** Hand-written provider contracts (Runway, Crun). Atlas routes need their schema loaded; catalog defaults never count. */
internal fun hasProviderContract(model: NexusModel): Boolean = model.contractEndpoint != null

private val audioParameterKeys = setOf("audio", "generate_audio", "enable_audio", "sound", "with_audio", "generate_sound")
private val durationParameterKeys = setOf("duration", "video_duration", "output_duration")

internal fun modelFactSheet(model: NexusModel, verified: NexusModel?, publishedUnit: String?): List<ModelFact> = buildList {
    add(ModelFact("Entrada", verified?.let(::inputFact)))
    add(ModelFact("Salida", outputFact(model)))
    if (Modality.VIDEO in model.modalities || Modality.AUDIO in model.modalities) add(ModelFact("Audio", verified?.let(::audioFact)))
    add(ModelFact("Límites", verified?.let(::limitsFact)))
    add(ModelFact("Coste por", costUnitFact(model, publishedUnit)))
}

private fun inputFact(model: NexusModel): String? {
    val required = model.nativeRequiredFields.toSet()
    fun mark(field: String?, label: String) = if (field != null && field in required) "$label (obligatorio)" else label
    val parts = mutableListOf<String>()
    if (model.promptAccepted) parts += if (model.promptRequired) "texto (obligatorio)" else "texto"
    val seen = mutableSetOf<String>()
    fun addField(field: String?, label: String) { if (field != null && seen.add(field)) parts += mark(field, label) }
    addField(model.firstFrameField ?: model.inputImageField, "fotograma o imagen inicial")
    addField(model.lastFrameField, "fotograma final")
    model.imagesArrayField?.let { addField(it, if (model.maxImages > 1) "hasta ${model.maxImages} imágenes" else "imágenes de referencia") }
    addField(model.videoField, "vídeo")
    model.videosArrayField?.let { addField(it, if (model.maxVideos > 1) "hasta ${model.maxVideos} vídeos" else "vídeos de referencia") }
    model.contractMedia.forEach { media ->
        val limit = if (media.maximum > 1) " (hasta ${media.maximum})" else ""
        addField(media.key, media.label.lowercase() + limit)
    }
    return parts.joinToString(", ").replaceFirstChar { it.uppercase() }.takeIf { it.isNotBlank() }
}

private fun outputFact(model: NexusModel): String? = model.modalities.map {
    when (it) { Modality.VIDEO -> "vídeo"; Modality.IMAGE -> "imagen"; Modality.AUDIO -> "audio"; Modality.CHAT -> "texto" }
}.distinct().joinToString(", ").replaceFirstChar { it.uppercase() }.takeIf { it.isNotBlank() }

private fun audioFact(model: NexusModel): String {
    val parameter = model.parameters.firstOrNull { it.key.lowercase() in audioParameterKeys }
    val audioInput = model.contractMedia.firstOrNull { it.mime.startsWith("audio") }
    val parts = buildList {
        if (Modality.AUDIO in model.modalities) add("Salida de audio")
        parameter?.let { add(if (it.defaultValue.equals("true", true)) "Sonido generado, activado por defecto" else "Sonido generado opcional") }
        audioInput?.let { add("Admite ${it.label.lowercase()}") }
    }
    return parts.joinToString(" · ").ifBlank { "No declarado en el esquema" }
}

private fun limitsFact(model: NexusModel): String? = buildList {
    durationFact(model)?.let { add(it) }
    model.parameters.firstOrNull { it.key.equals("resolution", true) }?.options
        ?.map { it.trim().lowercase() }?.filter { it.isNotBlank() }?.distinct()?.takeIf { it.isNotEmpty() }
        ?.let { add("Resolución ${it.joinToString(", ")}") }
    model.promptMaxLength?.takeIf { it > 0 }?.let { add("Instrucción hasta $it caracteres") }
}.joinToString(" · ").takeIf { it.isNotBlank() }

/** null when the contract publishes no duration limits. */
internal fun supportsDuration(model: NexusModel, seconds: Double): Boolean? {
    val parameter = model.parameters.firstOrNull { it.key.lowercase() in durationParameterKeys } ?: return null
    val options = parameter.options.mapNotNull { it.toDoubleOrNull() }.filter { it > 0 }
    if (options.isNotEmpty()) return options.any { it == seconds }
    val min = ParameterBounds.minimum(parameter) ?: return null
    val max = ParameterBounds.maximum(parameter) ?: return null
    return java.math.BigDecimal.valueOf(seconds) in min..max
}

private fun durationFact(model: NexusModel): String? {
    val parameter = model.parameters.firstOrNull { it.key.lowercase() in durationParameterKeys } ?: return null
    val options = parameter.options.mapNotNull { it.toDoubleOrNull() }.filter { it > 0 }.distinct().sorted()
    if (options.isNotEmpty()) return "Duración ${options.joinToString(", ") { plainNumber(it) }} s"
    val min = ParameterBounds.minimum(parameter) ?: return null
    val max = ParameterBounds.maximum(parameter) ?: return null
    return "Duración de ${min.stripTrailingZeros().toPlainString()} a ${max.stripTrailingZeros().toPlainString()} s"
}

private fun costUnitFact(model: NexusModel, publishedUnit: String?): String? {
    val unit = publishedUnit ?: PublishedTariff.quote(model)?.unit ?: return null
    return when (unit) {
        "s" -> "segundo"
        "min" -> "minuto"
        else -> unit
    }
}

internal fun plainNumber(value: Double): String =
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

internal data class VariantEvidence(val model: NexusModel, val verified: NexusModel?, val tariff: PriceQuote?)

/** A difference is shown only when it is backed by the same kind of evidence for every variant. */
internal fun variantDifferences(variants: List<VariantEvidence>): Map<String, String> {
    if (variants.size < 2) return emptyMap()
    val contractFacts = if (variants.all { it.verified != null }) variants.map { comparableContractFacts(it.verified!!) } else null
    val profileFacts = if (variants.all { it.model.description.isNotBlank() }) variants.map { profileStrengths(it.model).toSet() } else null
    return variants.mapIndexed { index, variant ->
        val unique = listOfNotNull(contractFacts, profileFacts).flatMap { facts ->
            val shared = facts.reduce { acc, set -> acc intersect set }
            (facts[index] - shared).toList()
        }
        modelBookmark(variant.model) to (unique.distinct().joinToString(" · ").ifBlank { UNDOCUMENTED_DIFFERENCE })
    }.toMap()
}

private fun comparableContractFacts(model: NexusModel): Set<String> = buildSet {
    durationFact(model)?.let { add(it) }
    model.parameters.firstOrNull { it.key.equals("resolution", true) }?.options
        ?.map { it.trim().lowercase() }?.distinct()?.takeIf { it.isNotEmpty() }?.let { add("Resolución ${it.joinToString(", ")}") }
    if (model.parameters.any { it.key.lowercase() in audioParameterKeys }) add("Sonido generado")
    if (model.lastFrameField != null) add("Fotograma final")
    if (model.imagesArrayField != null && model.maxImages > 1) add("Hasta ${model.maxImages} imágenes")
}

internal data class ExampleCost(val usd: Double?, val note: String)

private const val NOT_A_QUOTE = "No es una cotización"

/**
 * Arithmetic example, not a quote: 5 s × base rate for video, one image for images. Other units are
 * not comparable, and a duration the schema rejects yields no amount.
 */
internal fun exampleCost(model: NexusModel, tariff: PriceQuote?, verified: NexusModel? = null): ExampleCost {
    val quote = tariff ?: PublishedTariff.quote(model) ?: return ExampleCost(null, "Sin tarifa con unidad confirmada")
    val rate = (quote.unitUsd ?: quote.usd).takeIf { it.isFinite() && it >= 0 } ?: return ExampleCost(null, "Sin tarifa con unidad confirmada")
    val seconds = plainNumber(EXAMPLE_SECONDS)
    return when {
        Modality.VIDEO in model.modalities && quote.unit in setOf("s", "min") -> {
            val support = verified?.let { supportsDuration(it, EXAMPLE_SECONDS) }
            if (support == false) return ExampleCost(null, "Sin ejemplo: el esquema de esta variante no admite $seconds s")
            val perUnit = if (quote.unit == "s") "por segundo" else "por minuto"
            val usd = if (quote.unit == "s") rate * EXAMPLE_SECONDS else rate * EXAMPLE_SECONDS / 60.0
            val note = "Ejemplo aritmético: $seconds s × tarifa base $perUnit. $NOT_A_QUOTE" +
                if (support == null) " · duración de $seconds s sin verificar en esta variante" else ""
            ExampleCost(usd, note)
        }
        Modality.IMAGE in model.modalities && Modality.VIDEO !in model.modalities && quote.unit == "imagen" ->
            ExampleCost(rate, "Ejemplo aritmético: 1 imagen × tarifa base por imagen. $NOT_A_QUOTE")
        else -> ExampleCost(null, "Sin ejemplo comparable: tarifa por ${quote.unit ?: "unidad no indicada"}")
    }
}
