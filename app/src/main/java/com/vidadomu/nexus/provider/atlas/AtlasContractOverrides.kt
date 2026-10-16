package com.vidadomu.nexus.provider.atlas

import com.vidadomu.nexus.agent.core.registry.DerivedParam
import com.vidadomu.nexus.core.model.NexusModel

/**
 * Campos que el backend de Atlas exige aunque el schema publicado no los declare. Cada entrada
 * procede de un rechazo real del proveedor y se deduce de otra opción ya elegida, nunca se
 * inventa: si el schema empieza a publicar el campo, se usa el del schema y esta regla se apaga.
 *
 * tencent/video/upscaler (2026-10-16, envíos reales desde el S25):
 * - «resolution is required (720p, 1080p, 1440p or 4k)» aunque el schema solo publica template.
 *   El nivel va en el sufijo de la plantilla (2k = 1440p).
 * - «fps must be an integer between 1 and 100» aunque el schema dice que fps se puede omitir.
 *   Nexus envía los fps medidos del vídeo original (conservarlo es lo que promete omitirlo).
 */
object AtlasContractOverrides {
    private const val TENCENT_VIDEO = "tencent/video/upscaler"

    /** Límites que el backend aplica y el schema no publica. */
    fun adjustParameters(modelId: String, parameters: List<com.vidadomu.nexus.core.model.ModelParameter>) =
        if (modelId != TENCENT_VIDEO) parameters
        else parameters.map { p -> if (p.key == "fps") p.copy(minValue = p.minValue ?: 1f, maxValue = p.maxValue ?: 100f, isAdvanced = false) else p }

    /** Parámetros exigidos que, si nadie los elige, se toman del vídeo de entrada. */
    fun sourceParams(model: NexusModel): Map<String, String> =
        if (model.id == TENCENT_VIDEO && model.parameters.any { it.key == "fps" }) mapOf("fps" to "video_fps") else emptyMap()
    private val TIER_TO_RESOLUTION = linkedMapOf("720p" to "720p", "1080p" to "1080p", "2k" to "1440p", "4k" to "4k")

    fun tierOf(template: String): String? = TIER_TO_RESOLUTION.entries.firstOrNull { template.lowercase().endsWith("-" + it.key) }?.value

    fun derived(model: NexusModel): List<DerivedParam> = when (model.id) {
        "tencent/video/upscaler" -> {
            val templates = model.parameters.firstOrNull { it.key == "template" }?.options.orEmpty()
            val mapping = templates.mapNotNull { t -> tierOf(t)?.let { t to it } }.toMap()
            if (mapping.isEmpty() || model.parameters.any { it.key == "resolution" }) emptyList()
            else listOf(DerivedParam("resolution", "template", mapping,
                "Atlas exige «resolution» (720p, 1080p, 1440p o 4k); Nexus la deduce del nivel de la plantilla."))
        }
        else -> emptyList()
    }

    /** Valores derivados para el payload, a partir de los valores efectivos del formulario. */
    fun derivedValues(model: NexusModel, values: Map<String, String>): Map<String, String> = derived(model).mapNotNull { d ->
        val source = values[d.from]?.takeIf { it.isNotBlank() } ?: model.parameters.firstOrNull { it.key == d.from }?.defaultValue
        d.valueFor(source)?.let { d.key to it }
    }.toMap()

    /** Mensaje si un campo exigido no se puede deducir con la opción elegida. */
    fun problem(model: NexusModel, values: Map<String, String>): String? {
        val missing = derived(model).filter { it.key !in derivedValues(model, values) }
        missing.firstOrNull()?.let { return "Elige una plantilla válida: Atlas exige «${it.key}» y se deduce de «${it.from}»." }
        // En el Lienzo no se mide el vídeo: sin fps, el envío no sería válido.
        return sourceParams(model).keys.firstOrNull { values[it].isNullOrBlank() }?.let {
            "Atlas exige «$it» (1–100): indica los fotogramas por segundo del vídeo original (normalmente 24, 25, 30 o 60)."
        }
    }
}
