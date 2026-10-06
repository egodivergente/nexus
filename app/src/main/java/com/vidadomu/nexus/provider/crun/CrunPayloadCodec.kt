package com.vidadomu.nexus.provider.crun

import com.vidadomu.nexus.core.model.*
import kotlinx.serialization.json.*

data class CrunUploadTicket(val fileUrl: String, val presignedUrl: String)

object CrunPayloadCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(model: NexusModel, request: GenerationRequest): JsonObject {
        if(model.contractEndpoint != null) {
            val full=com.vidadomu.nexus.provider.runway.ContractPayload.encode(model,request,includeModel=false)
            return buildJsonObject {
                put("model",model.providerModelId ?: error("Modelo nativo desconocido"))
                put("input",full)
            }
        }
        val images = request.mediaInputs.filter { it.type == Modality.IMAGE }.mapNotNull { it.remoteUrl }
        val videos = request.mediaInputs.filter { it.type == Modality.VIDEO }.mapNotNull { it.remoteUrl }
        val nativeModel = model.providerModelId ?: nativeModelId(model.id, images.isNotEmpty())
        val values = model.parameters.associate { it.key to it.defaultValue } + request.parameters
        val input = buildJsonObject {
            if (request.prompt.isNotBlank()) put("prompt", request.prompt.trim())
            model.parameters.forEach { parameter ->
                val value = values[parameter.key]?.trim().orEmpty()
                if (value.isBlank() || parameter.key == "prompt") return@forEach
                put(parameter.key, toNativeValue(parameter, value))
            }
            if (images.isNotEmpty()) {
                when {
                    !model.imagesArrayField.isNullOrBlank() -> put(model.imagesArrayField, JsonArray(images.map(::JsonPrimitive)))
                    model.firstFrameField?.endsWith("urls") == true -> put(model.firstFrameField, JsonArray(images.map(::JsonPrimitive)))
                    !model.firstFrameField.isNullOrBlank() -> {
                        put(model.firstFrameField, images.first())
                        if (!model.lastFrameField.isNullOrBlank() && images.size > 1) put(model.lastFrameField, images[1])
                    }
                    else -> put("img_urls", JsonArray(images.map(::JsonPrimitive)))
                }
            }
            if (videos.isNotEmpty()) {
                when {
                    !model.videosArrayField.isNullOrBlank() -> put(model.videosArrayField, JsonArray(videos.map(::JsonPrimitive)))
                    model.videoField?.endsWith("urls") == true -> put(model.videoField, JsonArray(videos.map(::JsonPrimitive)))
                    !model.videoField.isNullOrBlank() -> put(model.videoField, videos.first())
                    else -> put("video_urls", JsonArray(videos.map(::JsonPrimitive)))
                }
            }
        }
        return buildJsonObject {
            put("model", nativeModel)
            put("input", input)
        }
    }

    private fun nativeModelId(localId: String, hasImage: Boolean): String = when (localId) {
        "crun/kling-v3-std", "crun/kling-v3-pro" -> "kling/v3"
        "crun/kling-v3-motion-control-std", "crun/kling-v3-motion-control-pro" -> "kling/v3-motion-control"
        "crun/seedance-2.5" -> if (hasImage) "bytedance/seedance2-5-i2v" else "bytedance/seedance2-5-t2v"
        "crun/seedance-2.0" -> if (hasImage) "bytedance/seedance2-0-i2v" else "bytedance/seedance2-0-t2v"
        "crun/wan-3.0" -> if (hasImage) "wan/3-0-i2v" else "wan/3-0-t2v"
        "crun/minimax-h3" -> if (hasImage) "minimax/h3-i2v" else "minimax/h3-t2v"
        else -> throw IllegalArgumentException("Modelo Crun no reconocido: $localId")
    }

    private fun toNativeValue(parameter: ModelParameter, value: String): JsonElement = when (parameter.nativeType) {
        "boolean" -> JsonPrimitive(value.equals("true", true))
        "integer" -> JsonPrimitive(value.toLongOrNull() ?: throw IllegalArgumentException("${parameter.displayName} debe ser un número entero."))
        "number" -> JsonPrimitive(value.toDoubleOrNull() ?: throw IllegalArgumentException("${parameter.displayName} debe ser un número."))
        else -> JsonPrimitive(value)
    }

    fun parseCreatedTaskId(body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonObject
        return data?.get("task_id")?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException(root["message"]?.jsonPrimitive?.contentOrNull ?: "Crun no devolvió un identificador de tarea.")
    }

    fun parseUploadTicket(body: String): CrunUploadTicket {
        val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
            ?: throw IllegalStateException("Crun no devolvió datos para subir el archivo.")
        return CrunUploadTicket(
            fileUrl = data["file_url"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { throw IllegalStateException("Falta file_url en la respuesta de Crun.") },
            presignedUrl = data["presigned_url"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { throw IllegalStateException("Falta presigned_url en la respuesta de Crun.") }
        )
    }
}
