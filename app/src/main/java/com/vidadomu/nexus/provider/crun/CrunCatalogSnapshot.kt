package com.vidadomu.nexus.provider.crun

import android.content.Context
import com.vidadomu.nexus.core.model.*
import kotlinx.serialization.json.*

/** Builds Nexus entries from the checked-in snapshot of Crun's public OpenAPI index. */
object CrunCatalogSnapshot {
    private val mediaTokens = setOf(
        "image", "image_url", "img_url", "img_urls", "image_urls", "images", "reference_image_url",
        "reference_images", "first_frame", "first_frame_image", "last_frame", "last_image", "end_image",
        "video", "video_url", "video_urls", "reference_video", "reference_videos", "videos_list",
        "audio_url", "audio_urls", "reference_audio", "reference_audios", "audios_list", "mask", "mask_url"
    )

    fun load(context: Context): List<NexusModel> = runCatching {
        val raw = context.assets.open("crun_models_snapshot.json").bufferedReader().use { it.readText() }
        Json.parseToJsonElement(raw).jsonArray.mapNotNull(::decode)
    }.getOrElse { emptyList() }

    internal fun decode(record: JsonElement): NexusModel? {
        val root = record.jsonObject
        val nativeId = root["providerModelId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (nativeId.isBlank()) return null
        val output = root["output"]?.jsonPrimitive?.contentOrNull ?: return null
        val modality = runCatching { Modality.valueOf(output) }.getOrNull() ?: return null
        val props = root["properties"]?.jsonObject ?: JsonObject(emptyMap())
        val required = root["required"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet().orEmpty()
        val media = props.filterKeys(::isMediaKey)
        val imageFields = media.filterKeys { isImageKey(it) }
        val videoFields = media.filterKeys { isVideoKey(it) }
        val requiredImages = imageFields.keys.filter(required::contains)
        val requiredVideos = videoFields.keys.filter(required::contains)
        val firstImage = imageFields.keys.firstOrNull()
        val firstVideo = videoFields.keys.firstOrNull()
        val imageSchema = firstImage?.let(imageFields::get)?.jsonObject
        val videoSchema = firstVideo?.let(videoFields::get)?.jsonObject
        val imageArray = imageSchema?.get("type")?.jsonPrimitive?.contentOrNull == "array"
        val videoArray = videoSchema?.get("type")?.jsonPrimitive?.contentOrNull == "array"
        val maxImages = schemaMaximum(imageSchema).coerceAtLeast(if (firstImage == null) 0 else 1)
        val maxVideos = schemaMaximum(videoSchema).coerceAtLeast(if (firstVideo == null) 0 else 1)
        val inputMode = when {
            firstImage != null && firstVideo != null -> ModelInputMode.MULTI_MODAL
            firstVideo != null -> ModelInputMode.VIDEO_INPUT
            imageArray && maxImages > 1 -> ModelInputMode.MULTI_IMAGE
            firstImage != null -> ModelInputMode.SINGLE_IMAGE
            else -> ModelInputMode.TEXT_ONLY
        }
        val sourcePath = root["sourcePath"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val family = family(nativeId, sourcePath)
        val title = cleanTitle(root["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), nativeId)
        val knownRate = knownSecondRate(nativeId)
        val version = version(nativeId, title)
        val rank = variantRank(nativeId, title)
        val featureText = "$nativeId $sourcePath".lowercase()
        val capabilities = buildList {
            if(modality==Modality.AUDIO) { add(ModelCapability.TEXT_TO_AUDIO) } else if (modality == Modality.IMAGE) {
                add(ModelCapability.TEXT_TO_IMAGE)
                if (firstImage != null) add(ModelCapability.IMAGE_TO_IMAGE)
                if (firstImage != null) add(ModelCapability.IMAGE_EDITING)
            } else {
                add(ModelCapability.TEXT_TO_VIDEO)
                if (firstImage != null) add(ModelCapability.IMAGE_TO_VIDEO)
                if ("reference" in featureText) add(ModelCapability.REFERENCE_TO_VIDEO)
                if (firstVideo != null) add(ModelCapability.VIDEO_TO_VIDEO)
                if ("edit" in featureText) add(ModelCapability.VIDEO_EDIT)
                if ("extend" in featureText) add(ModelCapability.VIDEO_EXTEND)
            }
        }.distinct()
        val params = props.mapNotNull { (key, schema) ->
            if (key in setOf("prompt","text") || key in mediaTokens || isMediaKey(key)) null
            else parameter(key, schema.jsonObject, key in required)
        }
        return NexusModel(
            id = "crun/$nativeId",
            providerModelId = nativeId,
            displayName = title,
            providerId = CrunProvider.PROVIDER_ID,
            organization = family,
            modalities = listOf(modality),
            capabilities = capabilities,
            parameters = params,
            description = root["summary"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            pricingInfo = if (knownRate == null) "Precio exacto disponible al cotizar en Crun" else "Tarifa pública de Crun para la configuración predeterminada",
            basePriceUsd = knownRate,
            priceUnit = if (knownRate == null) null else "second",
            priceUnitVerified = knownRate != null,
            inputMode = inputMode,
            maxImages = maxImages,
            maxVideos = maxVideos,
            firstFrameField = firstImage.takeUnless { imageArray },
            imagesArrayField = firstImage.takeIf { imageArray },
            videoField = firstVideo.takeUnless { videoArray },
            videosArrayField = firstVideo.takeIf { videoArray },
            isMediaRequired = requiredImages.isNotEmpty() || requiredVideos.isNotEmpty(),
            nativeRequiredFields = required.toList(),
            supportsImageInput = firstImage != null,
            supportsFirstFrame = modality == Modality.VIDEO && firstImage != null,
            // Multiple reference images do not imply control over a video's final frame.
            supportsLastFrame = modality == Modality.VIDEO && imageFields.keys.any {
                it in setOf("last_frame", "last_frame_image", "last_image", "end_image", "end_frame")
            },
            supportsReferenceImages = imageArray || maxImages > 1,
            supportsVideoInput = firstVideo != null,
            inputImageField = firstImage,
            supportedAspectRatios = enumValues(props["aspect_ratio"]),
            supportedResolutions = enumValues(props["resolution"]),
            supportedDurations = enumValues(props["duration"]),
            shortDescription = "Contrato OpenAPI verificado de Crun.",
            speedQualityTag = if (rank >= 4) "Alta calidad" else "Proveedor alternativo",
            versionNumeric = version,
            modelFamily = family,
            variantRank = rank,
            isPrimaryGeneration = "edit" !in featureText && "reference" !in featureText,
            providerPriority = 92,
            contractEndpoint = "/api/v1/client/job/CreateTask",
            contractPromptField = if("text" in props && "prompt" !in props) "text" else "prompt",
            promptRequired = "prompt" in required || "text" in required,
            contractMedia = media.map { (key,value) ->
                val isArray=value.jsonObject["type"]?.jsonPrimitive?.contentOrNull=="array"
                ContractMediaField(key,label(key),when { isVideoKey(key)->"video/*"; "audio" in key->"audio/*";else->"image/*" },
                    key in required,if(isArray) schemaMaximum(value.jsonObject).coerceAtLeast(1) else 1,if(isArray) "array" else "string")
            }
        )
    }

    private fun parameter(key: String, schema: JsonObject, required: Boolean): ModelParameter {
        val nativeType = schema["type"]?.jsonPrimitive?.contentOrNull ?: "string"
        val options = enumValues(schema)
        val type = when {
            options.isNotEmpty() -> ParameterType.ENUM
            key == "duration" -> ParameterType.DURATION
            key == "resolution" -> ParameterType.RESOLUTION
            key == "aspect_ratio" || key == "ratio" -> ParameterType.ASPECT_RATIO
            key == "seed" -> ParameterType.SEED
            nativeType == "boolean" -> ParameterType.BOOLEAN
            nativeType == "integer" -> ParameterType.INTEGER
            nativeType == "number" -> ParameterType.FLOAT
            else -> ParameterType.TEXT
        }
        val default = schema["default"]?.let(::primitiveText)
            ?: options.firstOrNull().orEmpty()
        val advanced = key in setOf(
            "seed", "negative_prompt", "prompt_extend", "prompt_expansion", "translate_prompt",
            "input_compliance", "output_compliance", "region", "callback_url", "watermark"
        )
        return ModelParameter(
            key = key,
            displayName = label(key),
            type = type,
            defaultValue = default,
            description = schema["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            options = options,
            minValue = schema["minimum"]?.jsonPrimitive?.floatOrNull,
            maxValue = schema["maximum"]?.jsonPrimitive?.floatOrNull,
            isAdvanced = advanced,
            isRequired = required,
            nativeType = nativeType
        )
    }

    private fun primitiveText(value: JsonElement): String = when (value) {
        is JsonNull -> ""
        is JsonPrimitive -> value.content
        else -> value.toString()
    }

    private fun enumValues(element: JsonElement?): List<String> = element?.jsonObject?.get("enum")
        ?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

    private fun schemaMaximum(schema: JsonObject?): Int = schema?.get("maxItems")?.jsonPrimitive?.intOrNull ?: 1

    private fun isMediaKey(key: String): Boolean {
        val k = key.lowercase()
        return k in mediaTokens || (("image" in k || "video" in k || "audio" in k || "mask" in k) &&
            ("url" in k || k.endsWith("s") || k.startsWith("reference_") || k.contains("frame")))
    }

    private fun isImageKey(key: String): Boolean = key.lowercase().let { "image" in it || it.startsWith("img_") || "frame" in it || "mask" in it }
    private fun isVideoKey(key: String): Boolean = "video" in key.lowercase()

    private fun cleanTitle(raw: String, nativeId: String): String = raw
        .replace(Regex("(?i)^crun\\s+"), "")
        .replace(Regex("(?i)\\s+api(?:\\s+documentation)?$"), "")
        .ifBlank { nativeId.substringAfter('/').replace('-', ' ').replaceFirstChar { it.uppercase() } }

    private fun family(nativeId: String, path: String): String {
        val text = "$nativeId $path".lowercase()
        return when {
            "kling" in text -> "Kling"
            "seedance" in text || "bytedance" in text -> "Seedance"
            "wan/" in text || "/wan/" in text -> "Wan"
            "minimax" in text || "hailuo" in text -> "MiniMax"
            "runway" in text -> "Runway"
            "vidu" in text -> "Vidu"
            "pixverse" in text -> "PixVerse"
            "happyhorse" in text -> "HappyHorse"
            "grok" in text -> "Grok"
            "google" in text || "veo" in text -> "Google"
            "openai" in text || "sora" in text -> "OpenAI"
            "qwen" in text -> "Qwen"
            "flux" in text -> "Black Forest Labs"
            "seedream" in text -> "Seedream"
            else -> nativeId.substringBefore('/').replace('-', ' ').replaceFirstChar { it.uppercase() }
        }
    }

    private fun version(nativeId: String, title: String): Double = Regex("(?i)(?:v|h)?(\\d+)[-._](\\d+)")
        .find("$nativeId $title")?.let { "${it.groupValues[1]}.${it.groupValues[2]}".toDoubleOrNull() } ?: 1.0

    private fun variantRank(nativeId: String, title: String): Int = "$nativeId $title".lowercase().let {
        when {
            "prime" in it || "pro" in it || "master" in it || "max" in it -> 4
            "turbo" in it || "fast" in it || "plus" in it -> 2
            "lite" in it || "mini" in it -> 1
            else -> 3
        }
    }

    /** Public Crun USD rates for each route's checked-in default resolution/mode. */
    private fun knownSecondRate(nativeId:String):Double? = nativeId.lowercase().let {
        when {
            "seedance2-5" in it && !it.endsWith("v2v") -> .2143 // 720p, no video input
            "seedance2-0" in it && "mini" !in it && "fast" !in it && !it.endsWith("v2v") -> .1429 // 720p
            it.startsWith("wan/3-0-prime-") -> .1071 // 720p
            it.startsWith("wan/3-0-") -> .067 // 720p
            it.startsWith("minimax/h3-") && "regeneration" !in it -> .0714
            it=="minimax/hailuo-02" -> .1339/6.0 // std, 768p, six-second minimum
            it=="minimax/hailuo-2-3" -> .1339/6.0 // std, 768p, six-second minimum
            else -> null
        }
    }

    private fun label(key: String): String = when (key) {
        "img_urls", "image_urls", "reference_images" -> "Imágenes de referencia"
        "reference_videos", "video_urls" -> "Vídeos de referencia"
        "reference_audios", "audio_urls" -> "Audios de referencia"
        "audio_url", "reference_audio" -> "Audio de entrada"
        "mask", "mask_url" -> "Máscara de edición"
        "duration" -> "Duración"
        "resolution" -> "Resolución"
        "aspect_ratio", "ratio" -> "Formato"
        "audio", "generate_audio" -> "Generar sonido"
        "keep_original_sound" -> "Conservar sonido original"
        "character_orientation" -> "Orientación del personaje"
        "negative_prompt" -> "Prompt negativo"
        "num_outputs" -> "Número de resultados"
        else -> key.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
