package com.vidadomu.nexus.provider.fal

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.runway.ContractPayload
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl

/** First complementary route: identity checked against Atlas, never selected by a guessed saving. */
object FalContract {
    const val ENDPOINT = "fal-ai/birefnet/v2"
    const val MODEL_ID = "fal/$ENDPOINT"
    const val SOURCE = "https://api.fal.ai/v1/models?endpoint_id=fal-ai%2Fbirefnet%2Fv2&expand=openapi-3.0"

    fun complementary(atlas: List<NexusModel>): Boolean {
        require(atlas.isNotEmpty()) { "No se ha podido comprobar el catálogo Atlas." }
        return atlas.none { model ->
            listOf(model.id, model.providerModelId.orEmpty(), model.displayName, model.canonicalFamily)
                .any { it.lowercase().replace(Regex("[^a-z0-9]"), "").contains("birefnet") }
        }
    }

    fun decode(raw: String, now: Long): NexusModel {
        val entry = Json.parseToJsonElement(raw).jsonObject["models"]!!.jsonArray
            .map { it.jsonObject }.single { it["endpoint_id"]?.jsonPrimitive?.content == ENDPOINT }
        val metadata = entry["metadata"]!!.jsonObject
        require(metadata["status"]?.jsonPrimitive?.content == "active") { "Fal ha retirado este modelo." }
        val schema = entry["openapi"]!!.jsonObject
        require(schema["servers"]!!.jsonArray.any { it.jsonObject["url"]?.jsonPrimitive?.content == "https://queue.fal.run" })
        val post = schema["paths"]!!.jsonObject["/$ENDPOINT"]!!.jsonObject["post"]!!.jsonObject
        val inputRef = post["requestBody"]!!.jsonObject["content"]!!.jsonObject["application/json"]!!.jsonObject["schema"]!!.jsonObject["\$ref"]!!.jsonPrimitive.content
        require(inputRef.startsWith("#/components/schemas/"))
        val input = schema["components"]!!.jsonObject["schemas"]!!.jsonObject[inputRef.substringAfterLast('/')]!!.jsonObject
        val properties = input["properties"]!!.jsonObject
        val required = input["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        require("image_url" in required && properties["image_url"]!!.jsonObject["type"]?.jsonPrimitive?.content == "string")
        val parameters = properties.filterKeys { it !in setOf("image_url", "sync_mode") }.map { (key, value) ->
            val p = value.jsonObject
            val type = p["type"]?.jsonPrimitive?.content
            require(type in setOf("string", "integer", "number", "boolean")) { "El contrato Fal ha cambiado: $key no es compatible." }
            val options = (p["enum"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }
            ModelParameter(key, p["title"]?.jsonPrimitive?.content ?: key,
                when { options.isNotEmpty() -> ParameterType.ENUM; type == "boolean" -> ParameterType.BOOLEAN;
                    type == "integer" -> ParameterType.INTEGER; type == "number" -> ParameterType.FLOAT; else -> ParameterType.TEXT },
                defaultValue = p["default"]?.jsonPrimitive?.contentOrNull.orEmpty(), options = options,
                description = p["description"]?.jsonPrimitive?.content.orEmpty(), isRequired = key in required,
                nativeType = type, minValue = p["minimum"]?.jsonPrimitive?.floatOrNull,
                maxValue = p["maximum"]?.jsonPrimitive?.floatOrNull)
        }
        require(required.all { it == "image_url" || parameters.any { p -> p.key == it } })
        return NexusModel(MODEL_ID, "BiRefNet V2 · Quitar fondo", "fal", ENDPOINT, "BiRefNet",
            listOf(Modality.IMAGE), listOf(ModelCapability.IMAGE_EDITING), parameters,
            description = metadata["description"]?.jsonPrimitive?.content.orEmpty(), modelFamily = "BiRefNet",
            categories = listOf("background-removal"), isPrimaryGeneration = false,
            inputMode = ModelInputMode.SINGLE_IMAGE, maxImages = 1, isMediaRequired = true,
            supportsImageInput = true, inputImageField = "image_url", promptRequired = false, promptAccepted = false,
            contractMedia = listOf(ContractMediaField("image_url", "Imagen original", "image/*", true)),
            nativeRequiredFields = required, contractEndpoint = ENDPOINT, contractSource = SOURCE,
            contractCheckedAt = now, catalogCheckedAt = now, pricingInfo = "Tarifa Fal por consultar")
    }

    fun payload(model: NexusModel, request: GenerationRequest): JsonObject {
        require(request.modelId == MODEL_ID && request.providerId == "fal")
        require(request.mediaInputs.size == 1 && request.mediaInputs.single().fieldKey == "image_url") { "Adjunta una imagen original." }
        val url = requireNotNull(request.mediaInputs.single().remoteUrl) { "La imagen no está preparada." }
        require(url.startsWith("https://") || url.startsWith("data:image/")) { "Formato de imagen no compatible." }
        require(request.parameters.keys.all { it.startsWith("__") || model.parameters.any { p -> p.key == it } }) { "Hay opciones ajenas al contrato Fal." }
        // A retained composer draft is not an input of this endpoint; keep it locally, omit it on the wire.
        val payload = ContractPayload.encode(model, request.copy(prompt = ""), includeModel = false)
        if(payload["operating_resolution"]?.jsonPrimitive?.content == "2304x2304") {
            require(payload["model"]?.jsonPrimitive?.content == "General Use (Dynamic)") { "2304 px requiere General Use (Dynamic)." }
        }
        return JsonObject(payload + ("sync_mode" to JsonPrimitive(false)))
    }

    fun queueUrl(raw: String, requestId: String, suffix: String): String {
        require(requestId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        val url = raw.toHttpUrl()
        require(url.isHttps && url.host == "queue.fal.run" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty()
            && url.query == null && url.fragment == null && url.encodedPath in setOf(
                "/fal-ai/birefnet/requests/$requestId$suffix", "/$ENDPOINT/requests/$requestId$suffix")) { "Ruta de seguimiento Fal no válida." }
        return url.toString()
    }
}
