package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.core.model.*

enum class CreationUse(val label: String, val modality: Modality, val descriptionEs: String) {
    IMAGE_REFS("Con referencias", Modality.IMAGE, "Edita o combina imágenes aportadas con instrucciones. Revisa cuántas referencias admite al abrirlo."),
    IMAGE_EDIT("Imagen → imagen", Modality.IMAGE, "Transforma una imagen existente: cambia el fondo, el estilo o elementos mediante instrucciones."),
    IMAGE_TEXT("Texto → imagen", Modality.IMAGE, "Crea una imagen nueva a partir de una descripción, sin imagen de entrada."),
    VIDEO_IMAGE("Animar imagen", Modality.VIDEO, "Convierte una imagen en un clip. Describe el movimiento y comprueba los fotogramas admitidos."),
    VIDEO_REFS("Vídeo con referencias", Modality.VIDEO, "Crea un clip guiado por referencias. Consulta los tipos y límites de archivos al seleccionarlo."),
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
        "reference-to-video" in id || m.inputMode==ModelInputMode.MULTI_MODAL -> CreationUse.VIDEO_REFS
        m.inputMode==ModelInputMode.VIDEO_INPUT || listOf("video-edit","video-to-video","video-extend","upscal").any { it in id } -> CreationUse.VIDEO_EDIT
        m.inputMode in listOf(ModelInputMode.SINGLE_IMAGE,ModelInputMode.DUAL_FRAME) || "image-to-video" in id -> CreationUse.VIDEO_IMAGE
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
    "upscal" in m.id.lowercase() -> "Aumenta la resolución del archivo original. No es un generador desde texto."
    "background-remov" in m.id.lowercase() -> "Elimina el fondo de una imagen existente."
    "inpaint" in m.id.lowercase() -> "Modifica una zona de la imagen. Puede requerir una máscara; consulta la compatibilidad."
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
