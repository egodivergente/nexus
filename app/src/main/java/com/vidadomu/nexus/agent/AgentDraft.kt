package com.vidadomu.nexus.agent

import com.vidadomu.nexus.core.model.*
import kotlinx.serialization.Serializable

@Serializable
data class AgentDraft(val id:String, val providerId:String, val modelId:String, val prompt:String,
    val parameters:Map<String,String>, val createdAt:Long=System.currentTimeMillis())

object AgentDraftContract {
    fun validate(model:NexusModel,prompt:String,parameters:Map<String,String>) {
        require(prompt.length<=16000) {"El prompt supera 16000 caracteres."}
        require(parameters.size<=64 && parameters.values.all {it.length<=16000}) {"Demasiados parámetros o valores demasiado largos."}
        val keys=FormContract.normalize(model.parameters).map {it.key}.toSet()
        require(parameters.keys.all {it in keys}) {"Hay parámetros que no pertenecen al esquema del modelo."}
        // Only validate supplied values here. A draft may intentionally omit required fields.
        FormContract.invalidValue(model.parameters.filter {it.key in parameters},parameters)?.let {throw IllegalArgumentException(it)}
    }
}
