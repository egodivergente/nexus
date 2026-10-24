package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.core.model.*

/** Stable tasks shared by providers. Labels are never used as route identities. */
internal enum class CatalogWorkflow(val label:String, val summary:String) {
    CREATE_IMAGE("Desde texto", "Crea una imagen desde una descripción."),
    EDIT_IMAGE("Con referencias", "Transforma o combina tus imágenes con instrucciones."),
    LAYERS("Separar capas", "Separa los elementos de una imagen para editarlos por separado."),
    INPAINT("Retocar zona", "Edita una zona de la imagen con una máscara."),
    OUTPAINT("Ampliar encuadre", "Extiende la imagen más allá de sus bordes."),
    REMOVE_BACKGROUND("Quitar fondo", "Aísla el sujeto de una imagen."),
    UPSCALE("Ampliar resolución", "Aumenta la resolución de un archivo existente."),
    CREATE_VIDEO("Desde texto", "Crea un vídeo describiendo la escena y el movimiento."),
    ANIMATE("Animar imagen", "Da movimiento a una imagen de entrada."),
    FRAMES("Inicio y final", "Dirige la transición entre dos fotogramas."),
    REFERENCES("Con referencias", "Guía el vídeo con referencias visuales."),
    MOTION("Transferir movimiento", "Lleva los movimientos de un vídeo al personaje de una imagen."),
    EDIT_VIDEO("Editar vídeo", "Transforma un vídeo existente."),
    EXTEND("Extender vídeo", "Continúa un vídeo con una nueva parte de la escena."),
    LIPSYNC("Sincronizar labios", "Sincroniza el personaje con una pista de audio."),
    AUDIO("Audio", "Configura las entradas y opciones de audio."),
    CHAT("Conversar", "Escribe instrucciones para recibir una respuesta de texto."),
    OTHER("Herramienta", "Consulta las entradas y opciones admitidas por este modelo.")
}

internal fun catalogWorkflows(model:NexusModel):List<CatalogWorkflow> {
    val capabilities=model.capabilities.toSet()
    val id=(model.providerModelId ?: model.id).lowercase()
    if(Modality.CHAT in model.modalities) return listOf(CatalogWorkflow.CHAT)
    if(Modality.AUDIO in model.modalities && Modality.VIDEO !in model.modalities) return listOf(CatalogWorkflow.AUDIO)
    if(ModelCapability.UPSCALE in capabilities || "upscal" in id) return listOf(CatalogWorkflow.UPSCALE)
    if(Modality.IMAGE in model.modalities && Modality.VIDEO !in model.modalities) {
        val special=when {
            "layer-decomposition" in id || "layer_decomposition" in id -> CatalogWorkflow.LAYERS
            "inpaint" in id -> CatalogWorkflow.INPAINT
            "outpaint" in id -> CatalogWorkflow.OUTPAINT
            "background-remov" in id || "remove-background" in id -> CatalogWorkflow.REMOVE_BACKGROUND
            else -> null
        }
        if(special!=null) return listOf(special)
        val generates=ModelCapability.TEXT_TO_IMAGE in capabilities
        // A route named as an edit (…/edit, image-to-image, reference…) needs images even when the provider also tags it
        // text-to-image: it never goes under «Desde texto», or Lanzadera would ask for images on a text task.
        val editRoute=Regex("(^|[/_.-])(edit|editing|i2i|image-to-image|img2img|reference|references|remix)([/_.-]|$)").containsMatchIn(id)
        val edits=ModelCapability.IMAGE_TO_IMAGE in capabilities || ModelCapability.IMAGE_EDITING in capabilities || editRoute
        // Explicit capabilities win over old or speculative input-mode flags.
        if(generates && !edits) return listOf(CatalogWorkflow.CREATE_IMAGE)
        if(edits) return buildList {
            if(generates && !model.isMediaRequired && !editRoute) add(CatalogWorkflow.CREATE_IMAGE)
            add(CatalogWorkflow.EDIT_IMAGE)
        }
        return listOf(when {
            model.supportsImageInput || model.inputMode in setOf(ModelInputMode.SINGLE_IMAGE,ModelInputMode.MULTI_IMAGE,ModelInputMode.DUAL_FRAME) -> CatalogWorkflow.EDIT_IMAGE
            model.inputMode==ModelInputMode.TEXT_ONLY -> CatalogWorkflow.CREATE_IMAGE
            else -> CatalogWorkflow.OTHER
        })
    }
    if(Modality.VIDEO in model.modalities) return listOf(when {
        ModelCapability.MOTION_CONTROL in capabilities || "motion-control" in id -> CatalogWorkflow.MOTION
        "lip-sync" in id || "lipsync" in id -> CatalogWorkflow.LIPSYNC
        ModelCapability.VIDEO_EXTEND in capabilities || "extend" in id -> CatalogWorkflow.EXTEND
        ModelCapability.REFERENCE_TO_VIDEO in capabilities -> CatalogWorkflow.REFERENCES
        // The route's own name (v2v, i2v) wins over a generic text-to-video tag: those need a video or an image.
        ModelCapability.VIDEO_EDIT in capabilities || ModelCapability.VIDEO_TO_VIDEO in capabilities || model.inputMode==ModelInputMode.VIDEO_INPUT ||
            Regex("(^|[/_.-])(v2v|video-to-video)([/_.-]|$)").containsMatchIn(id) -> CatalogWorkflow.EDIT_VIDEO
        model.lastFrameField!=null && model.supportsLastFrame -> CatalogWorkflow.FRAMES
        ModelCapability.IMAGE_TO_VIDEO in capabilities || model.supportsImageInput ||
            Regex("(^|[/_.-])(i2v|image-to-video)([/_.-]|$)").containsMatchIn(id) -> CatalogWorkflow.ANIMATE
        ModelCapability.TEXT_TO_VIDEO in capabilities || model.inputMode==ModelInputMode.TEXT_ONLY -> CatalogWorkflow.CREATE_VIDEO
        else -> CatalogWorkflow.REFERENCES
    })
    return listOf(CatalogWorkflow.OTHER)
}

private fun workflowOrderKey(workflow: CatalogWorkflow) = when(workflow) {
    CatalogWorkflow.REFERENCES -> "reference_to_video"
    CatalogWorkflow.ANIMATE -> "image_to_video"
    CatalogWorkflow.FRAMES -> "start_end_frame_video"
    CatalogWorkflow.CREATE_VIDEO -> "text_to_video"
    CatalogWorkflow.EDIT_IMAGE -> "reference_edit"
    CatalogWorkflow.CREATE_IMAGE -> "text_to_image"
    else -> workflow.name.lowercase()
}
internal val defaultWorkflowOrder = listOf("reference_to_video","image_to_video","start_end_frame_video","text_to_video","reference_edit","compose_multi_reference","image_to_image","text_to_image")

internal fun workflowRoutes(group:CatalogModelGroup, order: List<String> = defaultWorkflowOrder):Map<CatalogWorkflow,List<NexusModel>> =
    group.routes.flatMap {model -> catalogWorkflows(model).map {it to model}}
        .groupBy({it.first},{it.second}).toSortedMap(compareBy<CatalogWorkflow> {order.indexOf(workflowOrderKey(it)).takeIf {i->i>=0} ?: Int.MAX_VALUE}.thenBy {it.ordinal})

internal fun restoredWorkflow(group:CatalogModelGroup,savedKey:String?,legacyLabel:String?,order:List<String> = defaultWorkflowOrder):CatalogWorkflow {
    val available=workflowRoutes(group,order).keys
    available.firstOrNull {it.name==savedKey}?.let {return it}
    val image=com.vidadomu.nexus.core.model.Modality.IMAGE in group.routes.first().modalities
    val migrated=when(legacyLabel) {
        "Texto → imagen","Imagen desde texto" -> CatalogWorkflow.CREATE_IMAGE
        "Imagen → imagen","Editar una imagen" -> CatalogWorkflow.EDIT_IMAGE
        "Con referencias" -> if(image) CatalogWorkflow.EDIT_IMAGE else CatalogWorkflow.REFERENCES
        "Texto → vídeo","Vídeo desde texto" -> CatalogWorkflow.CREATE_VIDEO
        "Animar imagen" -> CatalogWorkflow.ANIMATE
        "Transferir movimiento","Motion Control" -> CatalogWorkflow.MOTION
        "Fotograma inicial y final" -> CatalogWorkflow.FRAMES
        "Vídeo con referencias" -> CatalogWorkflow.REFERENCES
        "Extender un vídeo" -> CatalogWorkflow.EXTEND
        "Editar vídeo" -> CatalogWorkflow.EDIT_VIDEO
        "Sincronizar labios" -> CatalogWorkflow.LIPSYNC
        else -> null
    }
    return migrated?.takeIf {it in available} ?: available.first()
}

internal fun optionVariantLabel(model:NexusModel, groupTitle:String):String {
    val text=model.displayName.replace(groupTitle,"",ignoreCase=true)
        .replace(Regex("(?i)^"+Regex.escape(catalogFamily(model))+"\\s*[- ]?v?[oh]?\\d+(?:[._]\\d+)*\\s*"),"")
        .replace(Regex("(?i)\\s*[·—]\\s*(?:crear y editar|editar|crear) (?:imagen|vídeo)\\s*$"),"")
        .replace(Regex("(?i)\\b(?:text[- ]to[- ](?:image|video)|image[- ]to[- ](?:image|video)|reference[- ]to[- ](?:image|video)|layer[- ]decomposition|motion[- ]control|edit|sequential)\\b")," ")
        .trim(' ','-','·','/').replace(Regex("\\s+")," ")
    val batch="sequential" in (model.providerModelId ?: model.id).lowercase()
    return listOfNotNull(text.takeIf {it.isNotBlank()},"Serie de imágenes".takeIf {batch}).joinToString(" · ").ifBlank {"Estándar"}
}
