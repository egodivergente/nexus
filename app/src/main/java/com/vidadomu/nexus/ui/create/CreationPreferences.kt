package com.vidadomu.nexus.ui.create

import android.content.Context
import com.vidadomu.nexus.core.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class CreationDraft(val modelId:String,val modality:Modality,val prompt:String,
    val parameters:Map<String,String>,val media:List<AttachedMedia> = emptyList(),val negativePrompt:String = "")
@Serializable data class CreationPreset(val name:String,val values:Map<String,String>)

/** Private, bounded settings. Presets never include keys, prompts or source media. */
class CreationPreferences(private val context:Context) {
    private val prefs=context.getSharedPreferences("nexus_creation_v1",0)
    private val json=Json {ignoreUnknownKeys=true}
    fun rememberParameters()=prefs.getBoolean("remember_parameters",true)
    fun setRememberParameters(value:Boolean) {prefs.edit().putBoolean("remember_parameters",value).apply()}
    fun values(model:NexusModel):Map<String,String> {
        // Un preset marcado «al abrir» manda sobre los últimos valores recordados.
        defaultPreset(model)?.let {return FormContract.restoredValues(model,it.values)}
        val saved=if(rememberParameters()) runCatching {json.decodeFromString<Map<String,String>>(prefs.getString("last:${model.providerId}:${model.id}","{}")!!)}.getOrDefault(emptyMap()) else emptyMap()
        return FormContract.restoredValues(model,saved)
    }
    fun saveDraft(draft:CreationDraft, provider:String) {
        prefs.edit().putString("draft",json.encodeToString(draft)).apply()
        if(rememberParameters()) prefs.edit().putString("last:$provider:${draft.modelId}",json.encodeToString(draft.parameters)).apply()
    }
    fun draft():CreationDraft?=runCatching {json.decodeFromString<CreationDraft>(prefs.getString("draft",null) ?: return null)}.getOrNull()
    private fun key(model:NexusModel)="presets:${model.providerId}:${model.id}"
    fun presets(model:NexusModel):List<CreationPreset> = runCatching {json.decodeFromString<List<CreationPreset>>(prefs.getString(key(model),"[]")!!)}.getOrDefault(emptyList())
    fun savePreset(model:NexusModel,name:String,values:Map<String,String>) {
        val cleanName=name.trim(); if(cleanName.isEmpty()) return
        val entry=CreationPreset(cleanName,FormContract.restoredValues(model,values))
        prefs.edit().putString(key(model),json.encodeToString((listOf(entry)+presets(model).filterNot {it.name==cleanName}).let {entries->val max=com.vidadomu.nexus.settings.RuntimePreferences.get(context).int("generation.presetCount");if(max>0) entries.take(max) else entries})).apply()
    }
    fun removePreset(model:NexusModel,name:String) {
        prefs.edit().putString(key(model),json.encodeToString(presets(model).filterNot {it.name==name})).apply()
        if(defaultPresetName(model)==name) setDefaultPreset(model,null)
    }
    private fun defaultKey(model:NexusModel)="preset_default:${model.providerId}:${model.id}"
    fun defaultPresetName(model:NexusModel):String? = prefs.getString(defaultKey(model),null)
    fun defaultPreset(model:NexusModel):CreationPreset? = defaultPresetName(model)?.let {n->presets(model).firstOrNull {it.name==n}}
    /** [name] = null quita el preset de apertura. */
    fun setDefaultPreset(model:NexusModel,name:String?) {prefs.edit().apply {if(name==null) remove(defaultKey(model)) else putString(defaultKey(model),name)}.apply()}
}
