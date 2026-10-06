package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*

enum class CreationPhase { CHOOSE_MODEL, CONFIGURING, PREPARING, INVALID, READY, UPLOADING, SUBMITTING }
data class CreationReadiness(val phase:CreationPhase,val message:String?=null) {val canSubmit get()=phase==CreationPhase.READY}
fun creationReadiness(state:CreateUiState):CreationReadiness {
    fun result(phase:CreationPhase,message:String)=CreationReadiness(phase,message)
    if(state.isUploading) return result(CreationPhase.UPLOADING,"Subiendo archivos…")
    if(state.isSubmitting) return result(CreationPhase.SUBMITTING,"Enviando solicitud…")
    val model=state.selectedModel ?: return result(CreationPhase.CHOOSE_MODEL,"Elige un modelo.")
    if(state.isLoadingSchema) return result(CreationPhase.CONFIGURING,"Cargando opciones…")
    if(!state.schemaReady) return result(CreationPhase.CONFIGURING,state.schemaMessage ?: "Carga la configuración del modelo.")
    if(state.isPreparingMedia) return result(CreationPhase.PREPARING,"Preparando archivos…")
    state.missingRequiredFields.firstOrNull()?.let { missing ->
        val message=when(missing) {"Prompt","Instrucciones"->"Escribe un prompt.";"Vídeo de entrada"->"Añade un vídeo de entrada.";"Imagen de entrada"->"Añade una imagen de entrada.";else->"Completa: $missing."}
        return result(CreationPhase.INVALID,message)
    }
    FormContract.invalidValue(model.parameters,state.parameters)?.let {return result(CreationPhase.INVALID,it)}
    val media=state.attachedMedia
    if(media.count {it.slotType==MediaSlotType.REFERENCE_IMAGE && it.fieldKey==null}>model.maxImages ||
       media.count {it.slotType==MediaSlotType.VIDEO_REFERENCE && it.fieldKey==null}>model.maxVideos)
        return result(CreationPhase.INVALID,"Hay más archivos de los que admite este modelo.")
    val wrong=media.any {item ->
        val expected=item.fieldKey?.let {key->model.contractMedia.find {it.key==key}?.mime?.substringBefore('/')} ?: when(item.slotType) {
            MediaSlotType.VIDEO_INPUT,MediaSlotType.VIDEO_REFERENCE->"video"
            else->"image"
        }
        !item.mimeType.startsWith("$expected/")
    }
    if(wrong) return result(CreationPhase.INVALID,"Revisa el tipo de archivo de cada entrada.")
    if(model.providerId=="atlas_cloud") com.vidadomu.nexus.provider.atlas.NativeModelSchema.validate(model,(state.parameters+("prompt" to state.prompt.trim())).filterValues {it.isNotBlank()})?.let {return result(CreationPhase.INVALID,it)}
    return CreationReadiness(CreationPhase.READY)
}
