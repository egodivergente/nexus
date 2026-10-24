package com.vidadomu.nexus.provider.atlas

import com.vidadomu.nexus.core.model.*

/** Catalog comparison: five seconds from text/images, no source video, audio opt-in. */
object VideoPricePreview {
    const val SECONDS = 5
    private val durationKeys = setOf("duration", "video_duration")
    private val audioKeys = setOf("sound", "generate_audio", "audio")

    fun requiresVideo(model: NexusModel): Boolean = model.nativeRequiredFields.any {
        it == model.videoField || it == model.videosArrayField
    } || model.nativeRequiredFields.isEmpty() && model.inputMode == ModelInputMode.VIDEO_INPUT

    fun durationParameter(model: NexusModel) = model.parameters.firstOrNull { it.key in durationKeys }

    fun supportsFiveSeconds(model: NexusModel): Boolean {
        val p = durationParameter(model) ?: return false
        if(p.options.isNotEmpty()) return p.options.any { it.toDoubleOrNull() == SECONDS.toDouble() }
        return (p.nativeType in setOf("integer", "number") || p.defaultValue.toDoubleOrNull() == SECONDS.toDouble()) &&
            (p.minValue == null || p.minValue <= SECONDS) && (p.maxValue == null || p.maxValue >= SECONDS)
    }

    fun initialValues(model: NexusModel): Map<String, String> {
        val values = model.parameters.filter { it.defaultValue.isNotBlank() }.associate { it.key to it.defaultValue }.toMutableMap()
        if(Modality.VIDEO !in model.modalities || requiresVideo(model)) return values
        if(supportsFiveSeconds(model)) durationParameter(model)?.let { p ->
            values[p.key] = p.options.firstOrNull { it.toDoubleOrNull() == SECONDS.toDouble() } ?: SECONDS.toString()
        }
        model.parameters.filter { it.key in audioKeys && it.nativeType == "boolean" &&
            (it.options.isEmpty() || "false" in it.options) }.forEach { values[it.key] = "false" }
        return values
    }

    fun duration(model: NexusModel, values: Map<String, String>): Double? = durationParameter(model)?.let {
        (values[it.key] ?: it.defaultValue).toDoubleOrNull()?.takeIf { d -> d.isFinite() && d > 0 }
    }

    fun fallback(model: NexusModel, parameters: Map<String,String>? = null, videoSeconds: Double? = null): PriceQuote? {
        if(Modality.VIDEO !in model.modalities || requiresVideo(model)) return PublishedTariff.quote(model,videoSeconds)
        val rate = PublishedTariff.quote(model) ?: return null
        if(rate.unit != "s") return rate
        val seconds = if(parameters == null) SECONDS.toDouble() else duration(model,parameters) ?: return rate
        val total = rate.usd * seconds
        if(!total.isFinite()) return rate
        val note = if(parameters == null && !supportsFiveSeconds(model)) {
            val supported = durationParameter(model)?.options?.filter { (it.toDoubleOrNull() ?: -1.0) > 0 }.orEmpty()
            if(supported.isNotEmpty()) "Equivalente a 5 s; duraciones disponibles: " + supported.joinToString() + " s"
            else "Equivalente a 5 s; duración pendiente de confirmar"
        } else "Estimación según tarifa base; opciones pueden variar el coste"
        return rate.copy(usd=total,configuration=note,unit=null,unitUsd=rate.usd,clipSeconds=seconds)
    }
}
