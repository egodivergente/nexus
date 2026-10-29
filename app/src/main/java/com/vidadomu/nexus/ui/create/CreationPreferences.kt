package com.vidadomu.nexus.ui.create

import android.content.Context
import com.vidadomu.nexus.core.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class CreationDraft(val modelId:String,val modality:Modality,val prompt:String,
    val parameters:Map<String,String>,val media:List<AttachedMedia> = emptyList(),val negativePrompt:String = "",
    val retainedMedia:List<AttachedMedia> = emptyList(),val providerId:String? = null,val endpoint:String? = null,
    val parameterAdaptations:List<ParameterTransferAdaptation> = emptyList())
@Serializable data class CreationPreset(val name:String,val values:Map<String,String>)

/** Private, bounded settings. Presets never include keys, prompts or source media. */
class CreationPreferences(private val context:Context) {
    private val prefs=context.getSharedPreferences("nexus_creation_v1",0)
    private val json=Json {ignoreUnknownKeys=true}
    fun rememberParameters()=prefs.getBoolean("remember_parameters",true)
    fun setRememberParameters(value:Boolean) {prefs.edit().putBoolean("remember_parameters",value).apply()}
    private fun configurationKey(provider:String,modelId:String,endpoint:String?,modality:Modality)=
        "last:v2:$provider:$modelId:${endpoint.orEmpty()}:${modality.name}"
    fun values(model:NexusModel,modality:Modality=model.modalities.first()):Map<String,String> {
        // Un preset marcado «al abrir» manda sobre los últimos valores recordados.
        defaultPreset(model,modality)?.let {return FormContract.restoredValues(model,it.values)}
        val saved=if(rememberParameters()) runCatching {json.decodeFromString<Map<String,String>>(
            prefs.getString(configurationKey(model.providerId,model.id,model.contractEndpoint ?: model.atlasEndpoint,modality),null)
                ?: prefs.getString("last:${model.providerId}:${model.id}","{}")!!)}.getOrDefault(emptyMap()) else emptyMap()
        return FormContract.restoredValues(model,saved)
    }
    fun saveDraft(draft:CreationDraft, provider:String) {
        prefs.edit().putString("draft",json.encodeToString(draft)).apply()
        if(rememberParameters()) prefs.edit().putString(configurationKey(provider,draft.modelId,draft.endpoint,draft.modality),json.encodeToString(draft.parameters)).apply()
    }
    fun draft():CreationDraft?=runCatching {json.decodeFromString<CreationDraft>(prefs.getString("draft",null) ?: return null)}.getOrNull()
    private fun key(model:NexusModel,modality:Modality)=
        "presets:v2:${model.providerId}:${model.id}:${(model.contractEndpoint ?: model.atlasEndpoint).orEmpty()}:${modality.name}"
    private fun legacyKey(model:NexusModel)="presets:${model.providerId}:${model.id}"
    fun presets(model:NexusModel,modality:Modality=model.modalities.first()):List<CreationPreset> = runCatching {
        // An empty v2 list is authoritative: removing the final preset must not revive legacy entries.
        val key=key(model,modality)
        val raw=if(prefs.contains(key)) prefs.getString(key,"[]") else prefs.getString(legacyKey(model),"[]")
        json.decodeFromString<List<CreationPreset>>(raw ?: "[]")
    }.getOrDefault(emptyList())
    fun savePreset(model:NexusModel,name:String,values:Map<String,String>,modality:Modality=model.modalities.first()) {
        val cleanName=name.trim(); if(cleanName.isEmpty()) return
        val entry=CreationPreset(cleanName,FormContract.restoredValues(model,values))
        prefs.edit().putString(key(model,modality),json.encodeToString((listOf(entry)+presets(model,modality).filterNot {it.name==cleanName}).let {entries->val max=com.vidadomu.nexus.settings.RuntimePreferences.get(context).int("generation.presetCount");if(max>0) entries.take(max) else entries})).apply()
    }
    fun removePreset(model:NexusModel,name:String,modality:Modality=model.modalities.first()) {
        val editor=prefs.edit().putString(key(model,modality),json.encodeToString(presets(model,modality).filterNot {it.name==name}))
        if(defaultPresetName(model,modality)==name) editor.putString(defaultKey(model,modality),"")
        editor.apply()
    }
    private fun defaultKey(model:NexusModel,modality:Modality)=
        "preset_default:v2:${model.providerId}:${model.id}:${(model.contractEndpoint ?: model.atlasEndpoint).orEmpty()}:${modality.name}"
    fun defaultPresetName(model:NexusModel,modality:Modality=model.modalities.first()):String? {
        val key=defaultKey(model,modality)
        return (if(prefs.contains(key)) prefs.getString(key,null)
            else prefs.getString("preset_default:${model.providerId}:${model.id}",null))?.takeIf {it.isNotBlank()}
    }
    fun defaultPreset(model:NexusModel,modality:Modality=model.modalities.first()):CreationPreset? =
        defaultPresetName(model,modality)?.let {n->presets(model,modality).firstOrNull {it.name==n}}
    /** [name] = null quita el preset de apertura. */
    fun setDefaultPreset(model:NexusModel,name:String?,modality:Modality=model.modalities.first()) {
        // An explicit empty value disables legacy fallback without deleting the original preferences.
        prefs.edit().putString(defaultKey(model,modality),name.orEmpty()).apply()
    }
}
