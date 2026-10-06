package com.vidadomu.nexus.provider.runway

import com.vidadomu.nexus.core.model.*
import kotlinx.serialization.json.*

object ContractPayload {
    fun validate(model:NexusModel,request:GenerationRequest) {
        require(!model.promptRequired || request.prompt.isNotBlank()) { "Escribe las instrucciones." }
        model.parameters.forEach { p ->
            val value=(request.parameters[p.key] ?: p.defaultValue).trim()
            require(!p.isRequired || value.isNotEmpty()) { "Falta ${p.displayName}" }
            if(value.isNotEmpty()) {
                require(p.options.isEmpty() || value in p.options) { "Elige una opción válida para ${p.displayName}" }
                if(p.nativeType in setOf("integer","number")) {
                    val n=value.toDoubleOrNull()
                    require(n!=null && n.isFinite()) { "${p.displayName} debe ser un número válido" }
                    if(p.nativeType=="integer") require(value.toLongOrNull()!=null) { "${p.displayName} debe ser un entero" }
                    require(p.minValue==null || n>=p.minValue.toDouble()) { "${p.displayName}: mínimo ${p.minValue}" }
                    require(p.maxValue==null || n<=p.maxValue.toDouble()) { "${p.displayName}: máximo ${p.maxValue}" }
                }
                if(p.nativeType=="boolean") require(value in setOf("true","false")) { "Revisa ${p.displayName}" }
            }
        }
    }
    fun encode(model:NexusModel,request:GenerationRequest,includeModel:Boolean=true):JsonObject {
        validate(model,request)
        val result=mutableMapOf<String,JsonElement>()
        if(includeModel) result["model"]=JsonPrimitive(model.providerModelId)
        if(request.prompt.isNotBlank()) result[model.contractPromptField]=JsonPrimitive(request.prompt)
        val nested=mutableMapOf<String,MutableMap<String,JsonElement>>()
        fun put(key:String,value:JsonElement) {
            if('.' in key) nested.getOrPut(key.substringBefore('.')) { mutableMapOf() }[key.substringAfter('.')]=value
            else result[key]=value
        }
        model.contractDefaults.forEach { (key,value) -> put(key,JsonPrimitive(value)) }
        model.parameters.forEach { parameter ->
            val value=(request.parameters[parameter.key] ?: parameter.defaultValue).trim()
            if(value.isNotEmpty()) put(parameter.key,when(parameter.nativeType) {
                "integer" -> JsonPrimitive(value.toLong())
                "number" -> JsonPrimitive(value.toDouble().also { require(it.isFinite()) })
                "boolean" -> JsonPrimitive(value.toBooleanStrict())
                "object", "array" -> Json.parseToJsonElement(value)
                else -> JsonPrimitive(value)
            })
        }
        model.contractMedia.forEach { field ->
            val media=request.mediaInputs.filter { it.fieldKey==field.key }
            require(!field.required || media.isNotEmpty()) { "Falta ${field.label}" }
            require(media.size<=field.maximum) { "Demasiados archivos en ${field.label}" }
            val urls=media.mapNotNull { it.remoteUrl }
            if(urls.isNotEmpty()) put(field.key,when(field.encoding) {
                "array" -> JsonArray(urls.map(::JsonPrimitive))
                "object-array" -> JsonArray(urls.map { buildJsonObject { put("uri",it) } })
                "typed-object" -> buildJsonObject { put("type",field.fixedType ?: media.first().mimeType.substringBefore('/'));put("uri",urls.first()) }
                else -> JsonPrimitive(urls.first())
            })
        }
        nested.forEach { (key,value) -> result[key]=JsonObject(value) }
        return JsonObject(result)
    }
}
