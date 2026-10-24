package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.core.model.*

enum class CreationUse(val label: String, val modality: Modality, val descriptionEs: String) {
    IMAGE_REFS("Con referencias", Modality.IMAGE, "Edita o combina imágenes aportadas con instrucciones. Revisa cuántas referencias admite al abrirlo."),
    IMAGE_EDIT("Imagen → imagen", Modality.IMAGE, "Transforma una imagen existente: cambia el fondo, el estilo o elementos mediante instrucciones."),
    IMAGE_TEXT("Texto → imagen", Modality.IMAGE, "Crea una imagen nueva a partir de una descripción, sin imagen de entrada."),
    VIDEO_IMAGE("Animar imagen", Modality.VIDEO, "Convierte una imagen en un clip. Describe el movimiento y comprueba los fotogramas admitidos."),
    VIDEO_REFS("Vídeo con referencias", Modality.VIDEO, "Crea un clip guiado por referencias. Consulta los tipos y límites de archivos al seleccionarlo."),
    MOTION("Motion Control", Modality.VIDEO, "Transfiere el movimiento de un vídeo a la persona de una imagen de referencia."),
    VIDEO_EDIT("Editar vídeo", Modality.VIDEO, "Trabaja sobre un clip existente. La operación concreta depende del modelo seleccionado."),
    VIDEO_TEXT("Texto → vídeo", Modality.VIDEO, "Crea un clip desde una descripción, sin fotograma inicial."),
    AUDIO("Audio", Modality.AUDIO, "Herramienta de audio. Revisa las entradas admitidas antes de generar."),
    TEXT("Texto / LLM", Modality.CHAT, "Escribe una consulta para redactar, resumir o resolver una tarea. Este flujo devuelve texto."),
    OTHER("Otras herramientas", Modality.IMAGE, "Herramienta especializada. Consulta sus opciones y requisitos antes de usarla.")
}
internal fun modelUse(m: NexusModel): CreationUse {
    val id=m.id.lowercase()
    if(Modality.CHAT in m.modalities) return CreationUse.TEXT
    if(Modality.VIDEO in m.modalities) return when {
        ModelCapability.MOTION_CONTROL in m.capabilities || "motion-control" in id -> CreationUse.MOTION
        "reference-to-video" in id || "r2v" in id || ModelCapability.REFERENCE_TO_VIDEO in m.capabilities || m.inputMode==ModelInputMode.MULTI_MODAL -> CreationUse.VIDEO_REFS
        m.inputMode==ModelInputMode.VIDEO_INPUT || listOf("video-edit","video-to-video","video-extend","upscal").any { it in id } -> CreationUse.VIDEO_EDIT
        m.inputMode in listOf(ModelInputMode.SINGLE_IMAGE,ModelInputMode.DUAL_FRAME,ModelInputMode.MULTI_IMAGE) || "image-to-video" in id || "i2v" in id || ModelCapability.IMAGE_TO_VIDEO in m.capabilities -> CreationUse.VIDEO_IMAGE
        "text-to-video" in id || ModelCapability.TEXT_TO_VIDEO in m.capabilities -> CreationUse.VIDEO_TEXT
        else -> CreationUse.VIDEO_REFS
    }
    if(Modality.AUDIO in m.modalities) return CreationUse.AUDIO
    return when {
        m.inputMode==ModelInputMode.MULTI_IMAGE || "reference" in id -> CreationUse.IMAGE_REFS
        m.inputMode==ModelInputMode.SINGLE_IMAGE || "/edit" in id || "image-to-image" in id -> CreationUse.IMAGE_EDIT
        "text-to-image" in id || ModelCapability.TEXT_TO_IMAGE in m.capabilities -> CreationUse.IMAGE_TEXT
        else -> CreationUse.OTHER
    }
}
internal fun usefulDescription(m:NexusModel):String = when {
    "motion-control" in m.id.lowercase() -> "Anima un personaje de una foto con los movimientos de un vídeo de referencia. Útil para gestos, baile y actuación."
    "lip-sync" in m.id.lowercase() -> "Sincroniza los labios de un personaje con una pista de audio."
    "extend" in m.id.lowercase() && Modality.VIDEO in m.modalities -> "Prolonga un vídeo existente para continuar la escena."
    "upscal" in m.id.lowercase() -> "Aumenta la resolución del archivo original. No es un generador desde texto."
    "background-remov" in m.id.lowercase() -> "Elimina el fondo de una imagen existente."
    "inpaint" in m.id.lowercase() -> "Modifica una zona de la imagen. Puede requerir una máscara; consulta la compatibilidad."
    Modality.VIDEO in m.modalities && (m.supportsLastFrame || m.inputMode == ModelInputMode.DUAL_FRAME) -> "Anima una escena entre un fotograma inicial y uno final. Útil para dirigir cómo empieza y termina el plano."
    m.inputMode == ModelInputMode.MULTI_MODAL -> "Combina referencias visuales para guiar una escena. Revisa las imágenes y vídeos admitidos en el formulario."
    m.inputMode == ModelInputMode.MULTI_IMAGE -> "Edita o combina varias imágenes: personas, productos o escenarios. Las referencias guían el resultado; no garantizan identidad exacta."
    Modality.VIDEO in m.modalities && ModelCapability.TEXT_TO_VIDEO in m.capabilities && ModelCapability.IMAGE_TO_VIDEO in m.capabilities -> "Crea un clip desde texto o anima una imagen. " + if(m.supportsAudio) "Admite audio generado." else "Describe la acción y el movimiento de cámara."
    Modality.VIDEO in m.modalities && m.supportsAudio -> modelUse(m).descriptionEs + " Admite audio generado."
    else -> modelUse(m).descriptionEs
}
internal fun creator(m:NexusModel):String = when {
    "kling" in m.id -> "Kling"
    "qwen" in m.id || "/wan" in m.id -> "Alibaba"
    "seedream" in m.id || "seedance" in m.id -> "ByteDance"
    "flux" in m.id -> "Black Forest Labs"
    "minimax" in m.id || "hailuo" in m.id -> "MiniMax"
    m.organization.equals("OTHER",true) || m.organization.isBlank() -> m.id.substringBefore('/').replace('-',' ')
    else -> m.organization
}
internal fun savingPercent(m:NexusModel):Int? {
    val before=m.originPriceUsd ?: return null
    val now=m.basePriceUsd ?: return null
    return if(before>0 && now>=0 && now<before) ((1-now/before)*100).toInt().takeIf { it>0 } else null
}
internal fun billingUnit(m:NexusModel):String = if(!m.priceUnitVerified) "sin unidad confirmada" else when(m.priceUnit?.lowercase()?.trim()) {
    "second","seconds","s","seg" -> "segundo"
    "image","img" -> "imagen"
    "generation","request","gen" -> "generación"
    "minute","min" -> "minuto"
    else -> m.priceUnit ?: "sin unidad confirmada"
}

/** Comparable default cost for provider routing. Unknown or unverified prices never win. */
internal fun fiveSecondPriceUsd(model: NexusModel): Double? {
    val price = model.basePriceUsd?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    if (!model.priceUnitVerified) return null
    val unit = model.priceUnit?.lowercase()?.trim().orEmpty()
    return when {
        unit in setOf("second", "seconds", "s", "seg", "segundo") -> price * 5.0
        unit.contains("/s") || unit.contains("por segundo") -> price * 5.0
        unit.contains("5 s") || unit.contains("5s") -> price
        Modality.IMAGE in model.modalities && unit in setOf("image", "img", "imagen") -> price
        else -> null
    }
}

internal fun providerName(providerId: String): String = when (providerId.lowercase()) {
    "atlas", "atlas_cloud", "atlas-cloud" -> "Atlas Cloud"
    "crun" -> "Crun"
    "runway" -> "Runway directo"
    "fal" -> "fal.ai"
    else -> providerId.replaceFirstChar { it.uppercase() }
}

private fun routeFeature(model: NexusModel): String {
    val id = model.id.lowercase()
    return when {
        "motion-control" in id -> "motion-control"
        "omni" in id -> "omni"
        "reference-to-video" in id || "r2v" in id -> "reference"
        "first-last" in id || "f2v" in id -> "first-last"
        "video-edit" in id -> "video-edit"
        "extend" in id -> "extend"
        "lip-sync" in id -> "lip-sync"
        "layer-decomposition" in id || "layer_decomposition" in id -> "layer-decomposition"
        "upscal" in id -> "upscale"
        "background-remov" in id -> "background-remove"
        "inpaint" in id -> "inpaint"
        else -> "base"
    }
}

/** Provider-neutral model identity. Provider catalogs use different family names for the same engine. */
internal fun canonicalModelIdentity(model: NexusModel): String {
    val text = listOfNotNull(model.providerModelId, model.id, model.displayName, model.modelFamily)
        .joinToString(" ").lowercase()
    val tier = when {
        Regex("\\b(prime|pro|max|master)\\b").containsMatchIn(text) ->
            Regex("\\b(prime|pro|max|master)\\b").find(text)!!.value
        Regex("\\b(fast|turbo|lite|mini|dev)\\b").containsMatchIn(text) ->
            Regex("\\b(fast|turbo|lite|mini|dev)\\b").find(text)!!.value
        else -> "standard"
    }
    fun versionAfter(token:String):String? = Regex("$token[^0-9]{0,8}(\\d+)(?:[._-](\\d+))?",RegexOption.IGNORE_CASE)
        .find(text)?.let { (it.groupValues[1]+if(it.groupValues[2].isNotBlank()) "."+it.groupValues[2] else "").toDoubleOrNull()?.toString() }
    return when {
        "wan" in text -> "wan-${versionAfter("wan") ?: model.versionNumeric}-$tier"
        "seedance" in text -> "seedance-${versionAfter("seedance") ?: model.versionNumeric}-$tier"
        "seedream" in text -> "seedream-${versionAfter("seedream") ?: model.versionNumeric}-$tier"
        "hailuo" in text -> "hailuo-${versionAfter("hailuo") ?: model.versionNumeric}-$tier"
        "minimax" in text && Regex("\\bh3\\b").containsMatchIn(text) -> "minimax-h3-$tier"
        "kling" in text -> "kling-${versionAfter("kling") ?: model.versionNumeric}-$tier"
        "veo" in text -> "veo-${versionAfter("veo") ?: model.versionNumeric}-$tier"
        else -> model.displayName.lowercase()
            .replace(Regex("(?i)\\b(crun|api|runway dev|runway directo|atlas cloud)\\b"), " ")
            .replace(Regex("(?i)\\b(image|imagen|text|texto|video|vídeo|reference|referencias|to|a|editar|con)\\b"), " ")
            .replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }
}

/** Groups equivalent model variants while preserving different input workflows and quality tiers. */
internal fun canonicalRouteKey(model: NexusModel): String = listOf(
    creator(model).lowercase(),
    canonicalModelIdentity(model),
    modelUse(model).name,
    routeFeature(model)
).joinToString("|")

internal fun modelFacts(model:NexusModel):String {
    val facts=mutableListOf<String>()
    val quality=model.parameters.firstOrNull { it.key in setOf("resolution","quality") }?.defaultValue
        ?.takeIf { it.isNotBlank() } ?: model.supportedResolutions.lastOrNull()
    quality?.let { facts += it.uppercase() }
    val durations=(model.supportedDurations + model.parameters.firstOrNull { it.key=="duration" }?.options.orEmpty())
        .mapNotNull { it.toDoubleOrNull() }
    durations.maxOrNull()?.let { facts += "hasta ${it.toInt()} s" }
    if(model.supportsAudio || model.parameters.any { it.key in setOf("audio","generate_audio") && it.defaultValue=="true" }) facts += "audio"
    when {
        model.maxImages>1 && modelUse(model)==CreationUse.VIDEO_IMAGE -> facts += "hasta ${model.maxImages} fotogramas"
        model.maxImages>1 -> facts += "hasta ${model.maxImages} imágenes"
        model.maxImages==1 -> facts += "1 imagen"
    }
    if(model.maxVideos>0) facts += if(model.maxVideos==1) "1 vídeo" else "hasta ${model.maxVideos} vídeos"
    return facts.distinct().take(4).joinToString(" · ").ifBlank { usefulDescription(model) }
}

internal fun catalogPreviewSeconds(model:NexusModel):Double {
    val supported=(model.supportedDurations + model.parameters.firstOrNull { it.key=="duration" }?.options.orEmpty())
        .mapNotNull { it.toDoubleOrNull() }.filter { it>0 }.distinct().sorted()
    return when {
        supported.isEmpty() -> 5.0
        supported.any { it==5.0 } -> 5.0
        else -> supported.first()
    }
}

internal fun defaultQualityScore(model:NexusModel):Int {
    val value=model.parameters.firstOrNull { it.key in setOf("resolution","quality") }?.defaultValue
        ?.uppercase()?.replace(" ","").orEmpty()
    return when {
        "4K" in value -> 4000
        "2K" in value -> 2000
        "1080" in value -> 1080
        "1K" in value -> 1000
        "768" in value -> 768
        "720" in value -> 720
        "480" in value -> 480
        else -> 0
    }
}

internal fun cheapestRoute(routes: List<NexusModel>): NexusModel = routes.minWithOrNull(
    compareByDescending<NexusModel> { defaultQualityScore(it) }
        .thenBy { fiveSecondPriceUsd(it) ?: Double.MAX_VALUE }
        .thenByDescending { it.priceUnitVerified }
        .thenByDescending { it.providerPriority }
        .thenBy { providerName(it.providerId) }
) ?: routes.first()
private fun versionParts(m:NexusModel):List<Int> {
    val match=Regex("""(?i)\b(?:v|h)?([0-9]{1,2})(?:\.([0-9]{1,2}))?(?:\.([0-9]{1,2}))?\b""").find(m.displayName)
        ?: Regex("""(?i)(?:[-/])(?:v|h)?([0-9]{1,2})(?:\.([0-9]{1,2}))?(?:\.([0-9]{1,2}))?\b""").find(m.id)
    return match?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: 0 } ?: listOf(m.versionNumeric.toInt(),0,0)
}
internal fun recentModels(models:List<NexusModel>):List<NexusModel> = models.sortedWith { a,b ->
    val av=versionParts(a);val bv=versionParts(b)
    val version=(0..2).firstNotNullOfOrNull { i -> bv.getOrElse(i){0}.compareTo(av.getOrElse(i){0}).takeIf { it!=0 } } ?: 0
    if(version!=0) version else compareValuesBy(a,b,{-it.releaseDateMillis},{-it.variantRank},{it.displayName})
}
