package com.vidadomu.nexus.ui.create

import android.content.Context
import com.vidadomu.nexus.core.model.AttachedMedia
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.NexusModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class ModelWorkDraft(val modelId:String,val prompt:String,val negativePrompt:String = "",
    val parameters:Map<String,String> = emptyMap(),val media:List<AttachedMedia> = emptyList(),
    val providerId:String? = null,val endpoint:String? = null,val modality:Modality? = null,
    val parameterAdaptations:List<ParameterTransferAdaptation> = emptyList())

/** Empty [parameters] means "use the target model defaults"; media still has to pass compatibleModelMedia. */
internal data class ModelSwitchPlan(val prompt:String,val negativePrompt:String,val parameters:Map<String,String>,
    val media:List<AttachedMedia>,val missingMedia:Int,val restored:Boolean,
    val parameterAdaptations:List<ParameterTransferAdaptation> = emptyList())

internal object ModelWorkDrafts {
    const val MAX_MODELS = 30
    private val json=Json {ignoreUnknownKeys=true}

    fun find(drafts:List<ModelWorkDraft>,modelId:String):ModelWorkDraft? = drafts.firstOrNull {it.modelId==modelId}

    fun find(drafts:List<ModelWorkDraft>,model:NexusModel,modality:Modality):ModelWorkDraft? = drafts.firstOrNull {
        it.modelId==model.id && it.providerId==model.providerId && it.endpoint==(model.contractEndpoint ?: model.atlasEndpoint) && it.modality==modality
    } ?: drafts.firstOrNull {it.modelId==model.id && it.providerId==null}

    private fun key(draft:ModelWorkDraft)=listOf(draft.providerId,draft.modelId,draft.endpoint,draft.modality?.name)
    /** Most recent first; the oldest parameter snapshots fall off once [max] configurations are stored. */
    fun remember(drafts:List<ModelWorkDraft>,draft:ModelWorkDraft,max:Int=MAX_MODELS):List<ModelWorkDraft> =
        (listOf(draft)+drafts.filterNot {key(it)==key(draft)}).take(max)

    fun plan(leaving:ModelWorkDraft,target:ModelWorkDraft?,isAvailable:(AttachedMedia)->Boolean):ModelSwitchPlan =
        plan(leaving,target,null,isAvailable)

    fun plan(leaving:ModelWorkDraft,target:ModelWorkDraft?,targetModel:NexusModel?,isAvailable:(AttachedMedia)->Boolean):ModelSwitchPlan {
        if(target==null) return ModelSwitchPlan(leaving.prompt,leaving.negativePrompt,emptyMap(),leaving.media,0,false)
        val missing=target.media.count { !isAvailable(it) && leaving.media.none { source -> source.id==it.id } }
        // Parameters belong to the target configuration. Text and resources belong to this work session.
        // Keep unavailable references too: the user may recover them, and their absence must stay explicit.
        val targetById=target.media.filter {item -> targetModel==null ||
            compatibleModelMedia(targetModel,listOf(item)).any {it.id==item.id}}.associateBy {it.id}
        val retained=(leaving.media.map {targetById[it.id] ?: it}+target.media).distinctBy {it.id}
        return ModelSwitchPlan(leaving.prompt,leaving.negativePrompt,target.parameters,retained,missing,true,target.parameterAdaptations)
    }

    fun encode(drafts:List<ModelWorkDraft>):String = json.encodeToString(drafts.take(MAX_MODELS))
    fun decode(raw:String?):List<ModelWorkDraft> = if(raw==null) emptyList() else
        runCatching {json.decodeFromString<List<ModelWorkDraft>>(raw)}.getOrDefault(emptyList()).distinctBy(::key).take(MAX_MODELS)
}

/** Exact state before a model switch, so "Volver" can put it back untouched. */
internal data class ModelSwitchSnapshot(val model:NexusModel,val modality:Modality,val availableModels:List<NexusModel>,
    val prompt:String,val negativePrompt:String,val parameters:Map<String,String>,val media:List<AttachedMedia>,
    val retainedMedia:List<AttachedMedia> = emptyList(),val parameterAdaptations:List<ParameterTransferAdaptation> = emptyList())

data class ModelSwitchNotice(val id:Long,val modelName:String,val parkedMedia:Int,val adaptedSettings:Int=0) {
    val message:String get() = "Modelo cambiado"+when(parkedMedia) {
        0 -> ""
        1 -> " · 1 archivo guardado"
        else -> " · $parkedMedia archivos guardados"
    }+when(adaptedSettings) {
        0 -> ""
        1 -> " · 1 ajuste adaptado"
        else -> " · $adaptedSettings ajustes adaptados"
    }
}

internal object ModelSwitchUndo {
    fun snapshot(state:CreateUiState):ModelSwitchSnapshot? = state.selectedModel?.let {
        ModelSwitchSnapshot(it,state.selectedModality,state.availableModels,state.prompt,state.negativePrompt,state.parameters,state.attachedMedia,state.retainedMedia,state.parameterAdaptations)
    }

    /** Media carried to the new model that it cannot use; they stay in the previous model's draft. */
    fun parked(carried:List<AttachedMedia>,compatible:List<AttachedMedia>):List<AttachedMedia> {
        val kept=compatible.map {it.id}.toSet()
        return carried.filterNot {it.id in kept}
    }

    fun restore(state:CreateUiState,snapshot:ModelSwitchSnapshot):CreateUiState = state.copy(selectedModel=snapshot.model,
        selectedModality=snapshot.modality,availableModels=snapshot.availableModels,prompt=snapshot.prompt,
        negativePrompt=snapshot.negativePrompt,parameters=snapshot.parameters,attachedMedia=snapshot.media,retainedMedia=snapshot.retainedMedia,
        parameterAdaptations=snapshot.parameterAdaptations,
        showAdvanced=false,errorMessage=null)
}

/** Stores only references to private media copies; it never deletes files. */
class ModelWorkDraftStore(context:Context) {
    private val prefs=context.getSharedPreferences("nexus_model_drafts_v1",Context.MODE_PRIVATE)
    fun load():List<ModelWorkDraft> = ModelWorkDrafts.decode(prefs.getString(KEY,null))
    fun save(drafts:List<ModelWorkDraft>) {prefs.edit().putString(KEY,ModelWorkDrafts.encode(drafts)).apply()}
    private companion object { const val KEY = "drafts" }
}
