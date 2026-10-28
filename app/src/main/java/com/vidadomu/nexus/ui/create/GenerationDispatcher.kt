package com.vidadomu.nexus.ui.create

import android.net.Uri
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.core.work.GenerationPollingWorker
import com.vidadomu.nexus.provider.atlas.NativeGenerationRequest
import java.io.File
import java.util.UUID

/** Shared single/comparison pipeline; local metadata never enters a provider request. */
class GenerationDispatcher(private val app: NexusApplication) {
    suspend fun submit(state: CreateUiState, localMetadata: Map<String,String> = emptyMap(),
        jobId: String = "job_${UUID.randomUUID()}", maxCostUsd: Double? = null, onProgress: (Boolean,Float?) -> Unit = {_,_ ->}): String {
        check(!BuildConfig.OFFLINE_QA) { "NEXUS Lab: envío bloqueado. No se ha consumido saldo." }
        // Reject caller metadata before uploading any local attachment.
        com.vidadomu.nexus.data.repository.LocalJobMetadata.validate(localMetadata)
        val readiness = creationReadiness(state)
        require(readiness.canSubmit) { readiness.message ?: "Revisa la configuración." }
        val model = requireNotNull(state.selectedModel)
        val metadata=localMetadata+buildMap {
            val runtime=com.vidadomu.nexus.assistant.AssistantRuntime.get(app)
            runtime.settings.activeProjectId()?.let { project ->
                put("__project_id",project)
                app.getSharedPreferences("nexus_project_conversations",0).getString(project,null)?.let {put("__conversation_id",it)}
            }
        }
        val routePlan=if(maxCostUsd!=null && maxCostUsd.isFinite() && maxCostUsd>=0)
            com.vidadomu.nexus.routing.AutomaticGenerationRouting(app).prepare(state,maxCostUsd) else null
        if(routePlan!=null) {
            val localInputs=state.attachedMedia.map { media -> MediaInput(
                type=when {media.mimeType.startsWith("video/")->Modality.VIDEO; media.mimeType.startsWith("audio/")->Modality.AUDIO; else->Modality.IMAGE},
                uriString=media.uriString,mimeType=media.mimeType,fileName=media.fileName,slotType=media.slotType,fieldKey=media.fieldKey) }
            val logical=NativeGenerationRequest.build(model,state.selectedModality,state.prompt,
                ParameterDependencies.effective(model,state.parameters),localInputs,jobId)
            val routes=routePlan.routes.map { route ->
                val target=requireNotNull(route.state.selectedModel)
                com.vidadomu.nexus.routing.GenerationRoute(target.providerId,target.id,route.quote.totalUsd,route.quote.checkedAt+300_000) {
                    prepareRequest(route.state,jobId,onProgress)
                }
            }
            val job=app.generationRepository.submitRoutedGeneration(logical,routes,routePlan.ceilingUsd,routePlan.diagnostics,metadata).getOrThrow()
            return job.id
        }
        val request=prepareRequest(state,jobId,onProgress)
        val job=app.generationRepository.submitGeneration(request,metadata).getOrThrow()
        if(job.status!=GenerationStatus.COMPLETED) GenerationPollingWorker.enqueue(app,jobId)
        return jobId
    }

    private suspend fun prepareRequest(state:CreateUiState,jobId:String,onProgress:(Boolean,Float?)->Unit):GenerationRequest {
        val model=requireNotNull(state.selectedModel)
        val provider = app.providerRegistry.getProviderById(model.providerId)
            ?: error("Proveedor no disponible.")
        val inputs = state.attachedMedia.mapIndexed { index, media ->
            onProgress(true, index.toFloat() / state.attachedMedia.size)
            val uri = Uri.parse(media.uriString)
            val name = media.fileName ?: uri.lastPathSegment ?: "nexus-input"
            val remote = when {
                media.remoteUrl != null -> media.remoteUrl
                uri.scheme in setOf("http", "https") -> media.uriString
                else -> {
                    require(uri.scheme == "file") { "Vuelve a adjuntar el archivo para preparar una copia local." }
                    val file = File(uri.path ?: "")
                    require(file.isFile && file.length() > 0) { "El archivo adjunto ya no está disponible." }
                    provider.uploadFile(file, name, media.mimeType) { sent,total ->
                        onProgress(true, (index + if(total > 0) sent.toFloat()/total else 0f)/state.attachedMedia.size)
                    }.getOrThrow()
                }
            }
            MediaInput(type = when {
                media.mimeType.startsWith("video/") -> Modality.VIDEO
                media.mimeType.startsWith("audio/") -> Modality.AUDIO
                else -> Modality.IMAGE
            }, uriString = media.uriString, mimeType = media.mimeType, fileName = name,
                remoteUrl = remote, slotType = media.slotType, fieldKey = media.fieldKey)
        }
        onProgress(false, null)
        // Fields that do not apply to the chosen mode stay in the draft but never reach the provider.
        return NativeGenerationRequest.build(model, state.selectedModality, state.prompt,
            ParameterDependencies.effective(model, state.parameters), inputs, jobId)
    }
}
