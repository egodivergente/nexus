package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.create.capability.*

enum class CreationPhase { CHOOSE_MODEL, CONFIGURING, PREPARING, INVALID, READY, UPLOADING, SUBMITTING }
enum class CreationFixTarget { MODEL, PROMPT, MEDIA, PARAMETERS, RETRY_CONFIGURATION }
data class CreationReadiness(val phase:CreationPhase,val message:String?=null,val fixTarget:CreationFixTarget?=null) {val canSubmit get()=phase==CreationPhase.READY}
fun creationReadiness(state:CreateUiState):CreationReadiness {
    fun result(phase:CreationPhase,message:String,target:CreationFixTarget?=null)=CreationReadiness(phase,message,target)
    if(state.isUploading) return result(CreationPhase.UPLOADING,"Subiendo archivos…")
    if(state.isSubmitting) return result(CreationPhase.SUBMITTING,"Enviando solicitud…")
    val model=state.selectedModel ?: return result(CreationPhase.CHOOSE_MODEL,"Elige un modelo.",CreationFixTarget.MODEL)
    if(state.isLoadingSchema) return result(CreationPhase.CONFIGURING,"Cargando opciones…")
    if(!state.schemaReady) return result(CreationPhase.CONFIGURING,state.schemaMessage ?: "Carga la configuración del modelo.",if(state.schemaUnsupported) CreationFixTarget.MODEL else CreationFixTarget.RETRY_CONFIGURATION)
    if(state.isPreparingMedia) return result(CreationPhase.PREPARING,"Preparando archivos…")
    val validation=requireNotNull(state.capabilityValidation())
    validation.issues.firstOrNull()?.let { issue ->
        val target=when(issue.kind) {
            CapabilityIssueKind.MODEL -> CreationFixTarget.MODEL
            CapabilityIssueKind.UNKNOWN -> if(issue.fieldKey==null) CreationFixTarget.MODEL else CreationFixTarget.PARAMETERS
            CapabilityIssueKind.PROMPT -> CreationFixTarget.PROMPT
            CapabilityIssueKind.MEDIA -> CreationFixTarget.MEDIA
            CapabilityIssueKind.PARAMETER,CapabilityIssueKind.DEPENDENCY -> CreationFixTarget.PARAMETERS
        }
        val missingInput=if(issue.kind==CapabilityIssueKind.MEDIA) ModelCapabilityRegistry.resolve(
            model,state.selectedModality,state.parameters,state.schemaReady,state.attachedMedia).inputs.firstOrNull {
                it.field.key==issue.fieldKey && state.attachedMedia.count(it.field::contains)<it.minimum
            } else null
        val message=if(missingInput?.minimum==1) when {
            missingInput.field.mime.startsWith("video/") -> "Añade un vídeo de entrada."
            missingInput.field.slot==MediaSlotType.PRIMARY_IMAGE && model.lastFrameField==null -> "Añade una imagen de entrada."
            else -> "Falta: ${missingInput.field.label}."
        } else issue.message
        return result(CreationPhase.INVALID,message,target)
    }
    // Specialized compatibility checks stay in the existing API adapters.
    if(model.contractEndpoint!=null) {
        val effective=validation.configuration
        try {
            val request=GenerationRequest("validation",model.providerId,model.id,state.selectedModality,
                effective.requestPrompt.orEmpty(),parameters=effective.parameters)
            com.vidadomu.nexus.provider.runway.ContractPayload.validate(model,request)
            com.vidadomu.nexus.provider.crun.CrunTextCatalog.validateSuno(model,request)
        } catch(error:IllegalArgumentException) {
            return result(CreationPhase.INVALID,error.message ?: "Revisa las opciones del modelo.",CreationFixTarget.PARAMETERS)
        }
    }
    return CreationReadiness(CreationPhase.READY)
}
