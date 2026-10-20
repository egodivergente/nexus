package com.vidadomu.nexus.provider.atlas

import com.vidadomu.nexus.core.model.*
import kotlinx.serialization.json.*

object AtlasModelMapper {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseModelsResponse(jsonString: String): List<NexusModel> {
        return try {
            val root = json.parseToJsonElement(jsonString).jsonObject
            val dataArray = root["data"] as? JsonArray ?: return emptyList()
            dataArray.mapNotNull { element ->
                (element as? JsonObject)?.let { mapJsonObjectToNexusModel(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun mapJsonObjectToNexusModel(obj: JsonObject): NexusModel? {
        val modelId = obj["model"]?.jsonPrimitive?.contentOrNull
            ?: obj["id"]?.jsonPrimitive?.contentOrNull
            ?: return null

        val uuid = obj["uuid"]?.jsonPrimitive?.contentOrNull
        val rawType = obj["type"]?.jsonPrimitive?.contentOrNull ?: "Image"
        val rawDisplayName = obj["displayName"]?.jsonPrimitive?.contentOrNull
            ?: obj["name"]?.jsonPrimitive?.contentOrNull
            ?: formatFallbackName(modelId)

        val rawOrg = obj["organization"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: obj["owned_by"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: extractOrgFromModelId(modelId)

        val orgName = normalizeOrganizationName(rawOrg)
        val profile = obj["profile"]?.jsonPrimitive?.contentOrNull
            ?: obj["description"]?.jsonPrimitive?.contentOrNull
            ?: ""

        val avatar = obj["avatar"]?.jsonPrimitive?.contentOrNull
            ?: obj["logo"]?.jsonPrimitive?.contentOrNull
        val coverUrl = obj["coverUrl"]?.jsonPrimitive?.contentOrNull
        val schemaUrl = obj["schema"]?.jsonPrimitive?.contentOrNull

        val categories = (obj["categories"] as? JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull?.trim()
        } ?: emptyList()

        val tags = (obj["tags"] as? JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull?.trim()
        } ?: emptyList()

        val family = obj["familyDisplayName"]?.jsonPrimitive?.contentOrNull
            ?: obj["familyName"]?.jsonPrimitive?.contentOrNull

        // Determine modality
        val modalities = determineModalities(rawType, categories, tags, modelId)

        // Determine capabilities
        val capabilities = determineCapabilities(modalities, categories, tags, modelId)

        val idLower = modelId.lowercase()
        val profileLower = profile.lowercase()
        val catUpper = categories.map { it.uppercase() }
        val tagsUpper = tags.map { it.uppercase() }

        // 1. Multi-modal detection (Gemini Omni Reference-to-Video, etc.)
        val isOmniRef = idLower.contains("gemini-omni") && idLower.contains("reference-to-video")
        val isGenericMultiModal = isOmniRef || catUpper.contains("MULTI-MODAL")

        // 2. Video input detection (video-extend, video-edit, video-to-video)
        val isVideoInput = !isGenericMultiModal && (
            idLower.contains("video-extend") ||
            idLower.contains("video-edit") ||
            idLower.contains("video-to-video") ||
            catUpper.contains("VIDEO-EXTEND") ||
            catUpper.contains("VIDEO-EDIT") ||
            catUpper.contains("VIDEO-TO-VIDEO") ||
            tagsUpper.contains("VIDEO-EXTEND") ||
            tagsUpper.contains("VIDEO-EDIT")
        )

        // 3. Multi-image edit detection (gpt-image-2.5 edit, seedream-4.7 edit, mai-image-2.6 edit, etc.)
        val isMultiImageEdit = !isGenericMultiModal && !isVideoInput && (
            (idLower.contains("gpt-image") && (idLower.contains("edit") || idLower.contains("editor"))) ||
            (idLower.contains("seedream") && (idLower.contains("edit") || idLower.contains("sequential"))) ||
            (idLower.contains("mai-image") && idLower.contains("edit")) ||
            tagsUpper.contains("MULTI-IMAGE") ||
            catUpper.contains("MULTI-IMAGE") ||
            idLower.contains("multi-image") ||
            (catUpper.contains("IMAGE-TOOLS") && (idLower.contains("multi") || tagsUpper.contains("MULTI")))
        )

        // 4. Image-to-Video & Dual Frame detection
        val isI2V = capabilities.contains(ModelCapability.IMAGE_TO_VIDEO) ||
            idLower.contains("image-to-video") ||
            idLower.contains("/i2v") ||
            idLower.contains("-i2v") ||
            idLower.contains("img2vid") ||
            catUpper.contains("IMAGE-TO-VIDEO") ||
            tagsUpper.contains("IMAGE-TO-VIDEO") ||
            tagsUpper.contains("IMAGE_TO_VIDEO")

        val isDualFrameI2V = isI2V && (
            idLower.contains("minimax") ||
            idLower.contains("wan-3.0") ||
            idLower.contains("wan-prime") ||
            idLower.contains("seedance") ||
            idLower.contains("gemini-omni") ||
            idLower.contains("kling-video-o3") ||
            profileLower.contains("first/last frame") ||
            profileLower.contains("last frame control") ||
            tagsUpper.contains("LAST-FRAME") ||
            tagsUpper.contains("DUAL-FRAME")
        )

        // 5. Single Image detection (I2I, Image-to-3D, single-frame I2V, single-image edit/upscale/inpaint)
        val isSingleImage = !isGenericMultiModal && !isVideoInput && !isMultiImageEdit && !isDualFrameI2V && (
            isI2V ||
            capabilities.contains(ModelCapability.IMAGE_TO_IMAGE) ||
            capabilities.contains(ModelCapability.IMAGE_TO_3D) ||
            capabilities.contains(ModelCapability.IMAGE_EDITING) ||
            idLower.contains("image-to-image") ||
            idLower.contains("/i2i") ||
            idLower.contains("-i2i") ||
            idLower.contains("img2img") ||
            idLower.contains("image-to-3d") ||
            idLower.contains("-i23d") ||
            idLower.contains("edit") ||
            idLower.contains("inpainting") ||
            idLower.contains("upscale") ||
            catUpper.contains("IMAGE-TO-IMAGE") ||
            catUpper.contains("IMAGE-TO-3D") ||
            catUpper.contains("IMAGE-TOOLS")
        )

        // Assemble native configuration
        val inputMode: ModelInputMode
        val maxImages: Int
        val maxVideos: Int
        val firstFrameField: String?
        val lastFrameField: String?
        val imagesArrayField: String?
        val videoField: String?
        val videosArrayField: String?
        val isMediaRequired: Boolean
        val supportsNativeAspectRatio: Boolean

        when {
            isGenericMultiModal -> {
                inputMode = ModelInputMode.MULTI_MODAL
                maxImages = 10
                maxVideos = 3
                firstFrameField = null
                lastFrameField = null
                imagesArrayField = "reference_images"
                videoField = null
                videosArrayField = "reference_videos"
                isMediaRequired = false
                supportsNativeAspectRatio = true
            }
            isVideoInput -> {
                inputMode = ModelInputMode.VIDEO_INPUT
                maxImages = if (idLower.contains("gemini-omni")) 10 else 0
                maxVideos = 1
                firstFrameField = null
                lastFrameField = null
                imagesArrayField = if (idLower.contains("gemini-omni")) "reference_images" else null
                videoField = "video"
                videosArrayField = null
                isMediaRequired = true
                supportsNativeAspectRatio = false
            }
            isMultiImageEdit -> {
                inputMode = ModelInputMode.MULTI_IMAGE
                maxImages = when {
                    idLower.contains("gpt-image") -> 16
                    idLower.contains("seedream") -> 10
                    idLower.contains("mai-image") -> 5
                    else -> 10
                }
                maxVideos = 0
                firstFrameField = null
                lastFrameField = null
                imagesArrayField = "images"
                videoField = null
                videosArrayField = null
                isMediaRequired = true
                supportsNativeAspectRatio = false
            }
            isDualFrameI2V -> {
                inputMode = ModelInputMode.DUAL_FRAME
                maxImages = 2
                maxVideos = 0
                firstFrameField = "image"
                lastFrameField = when {
                    idLower.contains("minimax") || idLower.contains("kling-video-o3") -> "end_image"
                    else -> "last_image"
                }
                imagesArrayField = null
                videoField = null
                videosArrayField = null
                isMediaRequired = true
                supportsNativeAspectRatio = false
            }
            isSingleImage -> {
                inputMode = ModelInputMode.SINGLE_IMAGE
                maxImages = 1
                maxVideos = 0
                firstFrameField = "image"
                lastFrameField = null
                imagesArrayField = null
                videoField = null
                videosArrayField = null
                isMediaRequired = true
                supportsNativeAspectRatio = !isI2V
            }
            else -> {
                // TEXT_ONLY
                inputMode = ModelInputMode.TEXT_ONLY
                maxImages = 0
                maxVideos = 0
                firstFrameField = null
                lastFrameField = null
                imagesArrayField = null
                videoField = null
                videosArrayField = null
                isMediaRequired = false
                supportsNativeAspectRatio = true
            }
        }

        // Backward compatibility flags
        val supportsImage = inputMode != ModelInputMode.TEXT_ONLY && inputMode != ModelInputMode.VIDEO_INPUT
        val supportsFirstFrame = inputMode == ModelInputMode.DUAL_FRAME || (inputMode == ModelInputMode.SINGLE_IMAGE && isI2V)
        val supportsLastFrame = inputMode == ModelInputMode.DUAL_FRAME
        val supportsReferenceImages = inputMode == ModelInputMode.MULTI_IMAGE || inputMode == ModelInputMode.MULTI_MODAL
        val supportsVideoInputFlag = inputMode == ModelInputMode.VIDEO_INPUT || inputMode == ModelInputMode.MULTI_MODAL
        val inputImageField = firstFrameField ?: imagesArrayField ?: videoField

        val supportsAudio = modalities.contains(Modality.AUDIO)
                || categories.any { it.contains("AUDIO", ignoreCase = true) }
                || tags.any { it.contains("AUDIO", ignoreCase = true) || it.contains("LIP-SYNC", ignoreCase = true) }

        // Price parsing
        val priceObj = obj["price"] as? JsonObject
        val actualPriceObj = priceObj?.get("actual") as? JsonObject
        val originPriceObj = priceObj?.get("origin") as? JsonObject
        val discount = priceObj?.get("discount")?.jsonPrimitive?.contentOrNull

        val basePriceStr = actualPriceObj?.get("base_price")?.jsonPrimitive?.contentOrNull
            ?: (actualPriceObj?.get("input_price")?.jsonPrimitive?.contentOrNull)
        val basePriceUsd = basePriceStr?.toDoubleOrNull()

        val originPriceStr = originPriceObj?.get("base_price")?.jsonPrimitive?.contentOrNull
            ?: (originPriceObj?.get("input_price")?.jsonPrimitive?.contentOrNull)
        val originPriceUsd = originPriceStr?.toDoubleOrNull()

        // Billing can be per second, per generation or tokens. A missing unit is not evidence
        // of per-second billing; the configuration calculator may still return a total.
        val rawUnit = actualPriceObj?.get("unit")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: originPriceObj?.get("unit")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val priceUnit = rawUnit?.let { normalizePriceUnit(it, modalities.firstOrNull()) } ?: "unidad sin especificar"
        val pricingInfo = formatPricingInfo(basePriceUsd, priceUnit, discount)

        // Badge extraction
        val badge = extractBadge(tags, discount)

        // Standard parameters based on modality and capabilities
        val parameters = buildParameters(modalities, capabilities, modelId)

        // Escaparate & Visual Catalog derived fields
        val isFlag = isFlagshipProvider(orgName)
        val shortDesc = deriveShortDescription(modelId, rawDisplayName, orgName, modalities, profile)
        val speedTag = deriveSpeedQualityTag(modelId, rawDisplayName, tags, discount, basePriceUsd)
        val releaseMillis = deriveReleaseDateMillis(obj, tags, modelId)
        val pairedId = derivePairedModelId(modelId)

        // Versioning and Model Hierarchy derived fields
        val modelFam = extractModelFamily(rawDisplayName, modelId, orgName)
        val verNum = extractVersionNumeric(rawDisplayName, modelId)
        val vRank = deriveVariantRank(rawDisplayName, modelId)
        val isPrimGen = deriveIsPrimaryGeneration(rawDisplayName, modelId)
        val provPrio = deriveProviderPriority(modelFam, modalities, modelId)
        val popScore = derivePopularityScore(modelId, rawDisplayName, orgName, tags, modalities, provPrio)

        return NexusModel(
            id = modelId,
            displayName = cleanDisplayName(rawDisplayName),
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = orgName,
            modalities = modalities,
            capabilities = capabilities,
            parameters = parameters,
            chatSamplingParameters = (obj["supported_sampling_parameters"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
            priceUnitVerified = !rawUnit.isNullOrBlank(),
            description = profile,
            pricingInfo = pricingInfo,
            badge = badge,
            basePriceUsd = basePriceUsd,
            originPriceUsd = originPriceUsd,
            priceUnit = priceUnit,
            uuid = uuid,
            family = family,
            categories = categories,
            tags = tags,
            avatarUrl = avatar,
            coverUrl = coverUrl,
            schemaUrl = schemaUrl,
            discount = discount,
            inputMode = inputMode,
            maxImages = maxImages,
            maxVideos = maxVideos,
            firstFrameField = firstFrameField,
            lastFrameField = lastFrameField,
            imagesArrayField = imagesArrayField,
            videoField = videoField,
            videosArrayField = videosArrayField,
            isMediaRequired = isMediaRequired,
            supportsNativeAspectRatio = supportsNativeAspectRatio,
            supportsImageInput = supportsImage,
            supportsFirstFrame = supportsFirstFrame,
            supportsLastFrame = supportsLastFrame,
            supportsReferenceImages = supportsReferenceImages,
            supportsVideoInput = supportsVideoInputFlag,
            inputImageField = inputImageField,
            supportsAudio = supportsAudio,
            supportedAspectRatios = defaultAspectRatiosForModality(modalities.firstOrNull()),
            supportedResolutions = defaultResolutionsForModality(modalities.firstOrNull()),
            supportedDurations = defaultDurationsForModality(modalities.firstOrNull()),
            shortDescription = shortDesc,
            speedQualityTag = speedTag,
            popularityScore = popScore,
            releaseDateMillis = releaseMillis,
            pairedEditModelId = pairedId,
            isFlagship = isFlag,
            versionNumeric = verNum,
            modelFamily = modelFam,
            variantRank = vRank,
            isPrimaryGeneration = isPrimGen,
            providerPriority = provPrio
        )
    }

    private fun determineModalities(
        rawType: String,
        categories: List<String>,
        tags: List<String>,
        modelId: String
    ): List<Modality> {
        val lowerType = rawType.lowercase()
        val lowerId = modelId.lowercase()

        return when {
            lowerType == "video" || categories.any { it.contains("VIDEO") } || lowerId.contains("text-to-video") || lowerId.contains("image-to-video") ->
                listOf(Modality.VIDEO)
            lowerType == "audio" || categories.any { it.contains("AUDIO") || it.contains("SPEECH") } || tags.any { it.contains("MUSIC") || it.contains("TTS") || it.contains("ASR") } ->
                listOf(Modality.AUDIO)
            lowerType == "text" || categories.contains("LLM") || tags.contains("LLM") ->
                listOf(Modality.CHAT)
            lowerType == "image" || categories.any { it.contains("IMAGE") || it.contains("3D") } || lowerId.contains("text-to-image") ->
                listOf(Modality.IMAGE)
            else -> listOf(Modality.IMAGE)
        }
    }

    private fun determineCapabilities(
        modalities: List<Modality>,
        categories: List<String>,
        tags: List<String>,
        modelId: String
    ): List<ModelCapability> {
        val caps = mutableListOf<ModelCapability>()
        val catUpper = categories.map { it.uppercase() }
        val tagsUpper = tags.map { it.uppercase() }
        val idLower = modelId.lowercase()

        if (catUpper.contains("TEXT-TO-IMAGE") || idLower.contains("text-to-image")) {
            caps.add(ModelCapability.TEXT_TO_IMAGE)
        }
        if (catUpper.contains("IMAGE-TO-IMAGE") || idLower.contains("image-to-image") || tagsUpper.contains("IMAGE-TO-IMAGE") || tagsUpper.contains("IMAGE_TO_IMAGE")) {
            caps.add(ModelCapability.IMAGE_TO_IMAGE)
        }
        if (catUpper.contains("IMAGE-TOOLS") || idLower.contains("edit") || idLower.contains("inpainting") || idLower.contains("upscale")) {
            caps.add(ModelCapability.IMAGE_EDITING)
        }
        if (catUpper.contains("TEXT-TO-VIDEO") || idLower.contains("text-to-video") || idLower.contains("t2v")) {
            caps.add(ModelCapability.TEXT_TO_VIDEO)
        }
        if (catUpper.contains("IMAGE-TO-VIDEO") || idLower.contains("image-to-video") || idLower.contains("i2v") || tagsUpper.contains("IMAGE-TO-VIDEO") || tagsUpper.contains("IMAGE_TO_VIDEO")) {
            caps.add(ModelCapability.IMAGE_TO_VIDEO)
        }
        if (catUpper.contains("REFERENCE-TO-VIDEO") || tagsUpper.contains("REFERENCE-TO-VIDEO") || idLower.contains("reference-to-video")) {
            caps.add(ModelCapability.REFERENCE_TO_VIDEO)
        }
        if (catUpper.contains("VIDEO-TO-VIDEO") || idLower.contains("video-to-video")) {
            caps.add(ModelCapability.VIDEO_TO_VIDEO)
        }
        if (tagsUpper.contains("VIDEO-EXTEND") || idLower.contains("video-extend")) {
            caps.add(ModelCapability.VIDEO_EXTEND)
        }
        if (tagsUpper.contains("VIDEO-EDIT") || idLower.contains("video-edit")) {
            caps.add(ModelCapability.VIDEO_EDIT)
        }
        if (catUpper.contains("AUDIO-TO-VIDEO") || tagsUpper.contains("LIP-SYNC")) {
            caps.add(ModelCapability.AUDIO_TO_VIDEO)
        }
        if (catUpper.contains("TEXT-TO-AUDIO") || tagsUpper.contains("MUSIC") || idLower.contains("music")) {
            caps.add(ModelCapability.TEXT_TO_AUDIO)
        }
        if (catUpper.contains("TEXT-TO-SPEECH") || tagsUpper.contains("TTS") || idLower.contains("tts")) {
            caps.add(ModelCapability.TEXT_TO_SPEECH)
        }
        if (catUpper.contains("SPEECH-TO-TEXT") || tagsUpper.contains("ASR") || idLower.contains("asr")) {
            caps.add(ModelCapability.SPEECH_TO_TEXT)
        }
        if (catUpper.contains("TEXT-TO-3D") || idLower.contains("text-to-3d")) {
            caps.add(ModelCapability.TEXT_TO_3D)
        }
        if (catUpper.contains("IMAGE-TO-3D") || idLower.contains("image-to-3d")) {
            caps.add(ModelCapability.IMAGE_TO_3D)
        }
        if (catUpper.contains("LLM") || tagsUpper.contains("LLM") || modalities.contains(Modality.CHAT)) {
            caps.add(ModelCapability.CHAT_COMPLETION)
        }

        // Fallback default capabilities if none matched
        if (caps.isEmpty()) {
            when {
                modalities.contains(Modality.IMAGE) -> caps.add(ModelCapability.TEXT_TO_IMAGE)
                modalities.contains(Modality.VIDEO) -> caps.add(ModelCapability.TEXT_TO_VIDEO)
                modalities.contains(Modality.AUDIO) -> caps.add(ModelCapability.TEXT_TO_AUDIO)
                modalities.contains(Modality.CHAT) -> caps.add(ModelCapability.CHAT_COMPLETION)
            }
        }

        return caps.distinct()
    }

    /**
     * Derives the exact API payload field name for the primary image input of a model.
     * This is critical for the input pipeline — each model family has different field names.
     *
     * Returns null for text-only models (no image input needed).
     */
    private fun deriveInputImageField(
        capabilities: List<ModelCapability>,
        categories: List<String>,
        modelId: String
    ): String? {
        val idLower = modelId.lowercase()
        val catUpper = categories.map { it.uppercase() }

        return when {
            // Reference-to-video: reference_image_url or reference_images
            capabilities.contains(ModelCapability.REFERENCE_TO_VIDEO)
                || catUpper.contains("REFERENCE-TO-VIDEO")
                || idLower.contains("reference-to-video") -> "reference_image_url"

            // Image editing (edit endpoint): image
            idLower.contains("/edit") -> "image"

            // Image-to-Image: input_image (most models)
            capabilities.contains(ModelCapability.IMAGE_TO_IMAGE)
                && !idLower.contains("image-to-video") -> "image"

            // Image-to-Video per provider:
            // ByteDance Seedance: first_frame_image
            idLower.contains("seedance") || idLower.contains("seedream") -> "first_frame_image"

            // Kling image-to-video: image
            idLower.contains("kling") && idLower.contains("image-to-video") -> "image"

            // MiniMax H3 image-to-video: first_frame_image
            idLower.contains("minimax") && idLower.contains("image-to-video") -> "first_frame_image"

            // Gemini Omni image-to-video / reference-to-video: image
            idLower.contains("gemini-omni") && idLower.contains("image-to-video") -> "image"

            // Wan 3.0 image-to-video: first_frame_image
            (idLower.contains("wan-3.0") || idLower.contains("wan-prime"))
                && idLower.contains("image-to-video") -> "first_frame_image"

            // Luma image-to-video: keyframes.frame0.url
            idLower.contains("luma") && idLower.contains("image-to-video") -> "image"

            // Runway: image (Gen-3, Gen-4 etc)
            idLower.contains("runway") && idLower.contains("image-to-video") -> "image"

            // Generic IMAGE_TO_VIDEO fallback
            capabilities.contains(ModelCapability.IMAGE_TO_VIDEO) -> "image"

            // Video-to-video / extend / edit: video_url
            capabilities.contains(ModelCapability.VIDEO_TO_VIDEO)
                || capabilities.contains(ModelCapability.VIDEO_EXTEND)
                || capabilities.contains(ModelCapability.VIDEO_EDIT) -> "video_url"

            // No image input
            else -> null
        }
    }

    fun normalizeOrganizationName(org: String): String {
        val clean = org.trim().replace("_", "-")
        return when (clean.uppercase()) {
            "OPENAI" -> "OpenAI"
            "GOOGLE" -> "Google"
            "BYTEDANCE", "BYTEPLUS", "DOUBAO" -> "ByteDance"
            "MINIMAX" -> "MiniMax"
            "BLACK-FOREST-LABS" -> "Black Forest Labs"
            "KUAISHOU", "KWAIVGI", "KWAIPILOT" -> "Kling (Kuaishou)"
            "DEEPSEEK" -> "DeepSeek"
            "ANTHROPIC" -> "Anthropic"
            "ELEVENLABS" -> "ElevenLabs"
            "MIDJOURNEY" -> "Midjourney"
            "VIDU" -> "Vidu"
            "PIXVERSE" -> "PixVerse"
            "SUNO" -> "Suno"
            "XAI" -> "xAI"
            "QWEN", "TONGYIMAI" -> "Alibaba (Qwen)"
            "ZHIPUAI" -> "Zhipu AI (GLM)"
            "MOONSHOTAI" -> "Moonshot AI"
            "TENCENT" -> "Tencent (Hunyuan)"
            "TRIPO", "MESHY" -> "3D Engine"
            "VEED" -> "Veed.io"
            "LIGHTRICKS" -> "Lightricks"
            "DOTS" -> "Dots Studio"
            "ATLASCLOUD" -> "Atlas Cloud"
            "SYNC.SO" -> "Sync.so"
            "XIAOMI" -> "Xiaomi"
            "MEITUAN" -> "Meituan"
            "MICROSOFT" -> "Microsoft"
            "KREA-2", "KREA" -> "Krea"
            "ERNIE" -> "Baidu (Ernie)"
            "NVIDIA" -> "Nvidia"
            "IDEOGRAM" -> "Ideogram"
            "RUNWAY", "RUNWAYML" -> "Runway"
            "LUMA", "LUMALABS" -> "Luma"
            "RECRAFT" -> "Recraft"
            "STABILITY", "STABILITYAI" -> "Stability AI"
            else -> clean.split("-", " ").joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
    }

    private fun extractOrgFromModelId(modelId: String): String {
        return if (modelId.contains("/")) {
            modelId.substringBefore("/")
        } else {
            "Atlas Cloud"
        }
    }

    private fun formatFallbackName(modelId: String): String {
        val namePart = if (modelId.contains("/")) modelId.substringAfter("/") else modelId
        return namePart.replace("-", " ").replace("_", " ").split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun cleanDisplayName(displayName: String): String {
        return displayName
            .replace(" Text-to-Image", "", ignoreCase = true)
            .replace(" Text-to-Video", "", ignoreCase = true)
            .replace(" Image-to-Video", "", ignoreCase = true)
            .trim()
    }

    private fun normalizePriceUnit(rawUnit: String?, modality: Modality?): String {
        return when (rawUnit?.lowercase()) {
            "image" -> "img"
            "second" -> "seg"
            "clip" -> "clip"
            "minute" -> "min"
            "1k_chars" -> "1k car."
            "generation" -> "gen"
            "token" -> "token"
            else -> rawUnit?.takeIf { it.isNotBlank() && it.lowercase() != "unknown" } ?: "unidad sin especificar"
        }
    }

    private fun formatPricingInfo(priceUsd: Double?, unit: String, discount: String?): String {
        if (priceUsd == null || priceUsd == 0.0) {
            return if (priceUsd == 0.0) "0 · tarifa publicada" else "Sin precio publicado"
        }
        val cents = priceUsd * 100
        val priceStr = if (cents < 0.1) {
            String.format(java.util.Locale.US, "%.4f cént. / %s", cents, unit)
        } else if (cents < 1.0) {
            String.format(java.util.Locale.US, "%.2f cént. / %s", cents, unit)
        } else {
            String.format(java.util.Locale.US, "%.1f cént. / %s", cents, unit)
        }

        val discSuffix = if (!discount.isNullOrBlank() && discount != "100") " ($discount% dto.)" else ""
        return priceStr + discSuffix
    }

    private fun extractBadge(tags: List<String>, discount: String?): String? {
        val upperTags = tags.map { it.uppercase() }
        return when {
            upperTags.contains("NEW") -> "NUEVO"
            upperTags.contains("PRO") -> "PRO"
            upperTags.contains("FAST") -> "RÁPIDO"
            upperTags.contains("FREE") -> "GRATIS"
            upperTags.contains("3D") -> "3D"
            discount == "100" -> "GRATIS"
            !discount.isNullOrBlank() && discount != "0" -> "-$discount%"
            else -> null
        }
    }

    private fun buildParameters(
        modalities: List<Modality>,
        capabilities: List<ModelCapability>,
        modelId: String
    ): List<ModelParameter> {
        val list = mutableListOf<ModelParameter>()

        when {
            modalities.contains(Modality.IMAGE) -> {
                list.add(
                    ModelParameter(
                        key = "aspect_ratio",
                        displayName = "Relación de aspecto",
                        type = ParameterType.ASPECT_RATIO,
                        defaultValue = "1:1",
                        options = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3"),
                        description = "Proporción de lienzo",
                        isAdvanced = false
                    )
                )
                list.add(
                    ModelParameter(
                        key = "seed",
                        displayName = "Semilla (Seed)",
                        type = ParameterType.SEED,
                        defaultValue = "-1",
                        description = "Usa -1 para aleatoria o un número entero fijo",
                        isAdvanced = true
                    )
                )
            }
            modalities.contains(Modality.VIDEO) -> {
                val durationOptions = when {
                    modelId.contains("wan-3.0", ignoreCase = true) -> listOf("-1") + (2..30).map(Int::toString)
                    modelId.contains("kling-v3", ignoreCase = true) || modelId.contains("kling-video-o3", ignoreCase = true) ->
                        (3..15).map(Int::toString)
                    modelId.contains("minimax/h3", ignoreCase = true) -> (5..15).map(Int::toString)
                    else -> listOf("5", "10")
                }
                list.add(
                    ModelParameter(
                        key = "duration",
                        displayName = "Duración",
                        type = ParameterType.DURATION,
                        defaultValue = "5",
                        options = durationOptions,
                        description = "Duración del clip en segundos",
                        isAdvanced = false
                    )
                )
                list.add(
                    ModelParameter(
                        key = "aspect_ratio",
                        displayName = "Relación de aspecto",
                        type = ParameterType.ASPECT_RATIO,
                        defaultValue = "16:9",
                        options = listOf("16:9", "9:16", "1:1"),
                        description = "Orientación de pantalla",
                        isAdvanced = false
                    )
                )
                val resolutionOptions = when {
                    modelId.contains("wan-3.0", ignoreCase = true) ->
                        listOf("1080p", "720p", "480p", "720p-esr", "1080p-esr", "1440p-esr", "4k-esr")
                    modelId.contains("kling-v3", ignoreCase = true) -> listOf("720p", "1080p")
                    modelId.contains("kling-video-o3", ignoreCase = true) -> emptyList()
                    modelId.contains("minimax/h3", ignoreCase = true) -> listOf("480p", "768p")
                    else -> listOf("720p", "1080p")
                }
                if (resolutionOptions.isNotEmpty()) {
                    list.add(
                        ModelParameter(
                            key = "resolution",
                            displayName = "Resolución",
                            type = ParameterType.RESOLUTION,
                            defaultValue = resolutionOptions.first(),
                            options = resolutionOptions,
                            description = "Calidad de render",
                            isAdvanced = false
                        )
                    )
                }
                if (modelId.contains("wan-3.0", ignoreCase = true)) {
                    list.add(
                        ModelParameter(
                            key = "audio",
                            displayName = "Audio nativo",
                            type = ParameterType.BOOLEAN,
                            defaultValue = "true",
                            description = "Incluye una pista de audio generada",
                            isAdvanced = true
                        )
                    )
                    list.add(
                        ModelParameter(
                            key = "seed",
                            displayName = "Semilla (Seed)",
                            type = ParameterType.SEED,
                            defaultValue = "-1",
                            description = "Usa -1 para aleatoria o un número entero fijo",
                            isAdvanced = true
                        )
                    )
                } else if (modelId.contains("kling-video-o3", ignoreCase = true)) {
                    list.add(
                        ModelParameter(
                            key = "sound",
                            displayName = "Sonido automático",
                            type = ParameterType.BOOLEAN,
                            defaultValue = "true",
                            description = "Añade audio automáticamente al vídeo",
                            isAdvanced = true
                        )
                    )
                }
            }
            modalities.contains(Modality.AUDIO) -> {
                list.add(
                    ModelParameter(
                        key = "duration",
                        displayName = "Duración",
                        type = ParameterType.DURATION,
                        defaultValue = "30",
                        options = listOf("15", "30", "60"),
                        description = "Duración del audio generado",
                        isAdvanced = false
                    )
                )
            }
            modalities.contains(Modality.CHAT) -> {
                // No generative canvas params needed
            }
        }

        return list
    }

    private fun defaultAspectRatiosForModality(modality: Modality?): List<String> {
        return when (modality) {
            Modality.IMAGE -> listOf("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3")
            Modality.VIDEO -> listOf("16:9", "9:16", "1:1")
            else -> emptyList()
        }
    }

    private fun defaultResolutionsForModality(modality: Modality?): List<String> {
        return when (modality) {
            Modality.IMAGE -> listOf("1024x1024", "1536x1024", "1024x1536")
            Modality.VIDEO -> listOf("720p", "1080p")
            else -> emptyList()
        }
    }

    private fun defaultDurationsForModality(modality: Modality?): List<String> {
        return when (modality) {
            Modality.VIDEO -> listOf("5", "10")
            Modality.AUDIO -> listOf("15", "30", "60")
            else -> emptyList()
        }
    }

    fun isFlagshipProvider(orgName: String): Boolean {
        val lower = orgName.lowercase()
        return lower.contains("bytedance") ||
            lower.contains("kling") ||
            lower.contains("kuaishou") ||
            lower.contains("minimax") ||
            lower.contains("hailuo") ||
            lower.contains("alibaba") ||
            lower.contains("qwen") ||
            lower.contains("wan") ||
            lower.contains("openai") ||
            lower.contains("black forest") ||
            lower.contains("flux") ||
            lower.contains("google") ||
            lower.contains("vidu") ||
            lower.contains("pixverse") ||
            lower.contains("ideogram") ||
            lower.contains("ltx") ||
            lower.contains("xai") ||
            lower.contains("grok") ||
            lower.contains("deepseek") ||
            lower.contains("anthropic") ||
            lower.contains("suno") ||
            lower.contains("elevenlabs") ||
            lower.contains("tripo") ||
            lower.contains("krea") ||
            lower.contains("luma") ||
            lower.contains("tencent") ||
            lower.contains("hunyuan")
    }

    fun extractVersionNumeric(displayName: String, modelId: String): Double {
        val text = "$displayName $modelId"
        // Match decimal versions like v5.0, 4.7, 3.5, 2.5, 1.5, etc.
        val decimalMatches = Regex("""(?i)(?:v|version|-v|\s|^)([0-9]+\.[0-9]+)\b""").findAll(text)
        for (m in decimalMatches) {
            val num = m.groupValues[1].toDoubleOrNull()
            if (num != null && num > 0.0 && num < 50.0) return num
        }
        // Match integer versions like v3, v2, -v2, etc.
        val intMatches = Regex("""(?i)(?:v|version|-v)\s*([0-9]+)\b""").findAll(text)
        for (m in intMatches) {
            val num = m.groupValues[1].toDoubleOrNull()
            if (num != null && num > 0.0 && num < 50.0) return num
        }
        // Match standalone single digit like " 3.0 "
        val singleMatch = Regex("""\b([1-9])\.0\b""").find(text)
        if (singleMatch != null) {
            val num = singleMatch.groupValues[1].toDoubleOrNull()
            if (num != null && num > 0.0 && num < 50.0) return num
        }
        return 1.0
    }

    fun extractModelFamily(displayName: String, modelId: String, orgName: String): String {
        val t = "$displayName $modelId".lowercase()
        return when {
            t.contains("qwen") && (t.contains("image") || t.contains("t2i") || t.contains("edit")) -> "Qwen Image"
            t.contains("qwen") -> "Qwen"
            t.contains("seedream") -> "Seedream"
            t.contains("kling") -> "Kling"
            t.contains("wan") -> "Wan"
            t.contains("seedance") -> "Seedance"
            t.contains("minimax") || t.contains("hailuo") -> "MiniMax"
            t.contains("flux") -> "FLUX"
            t.contains("imagen") -> "Google Imagen"
            t.contains("veo") -> "Google Veo"
            t.contains("gpt-image") || t.contains("dall-e") -> "OpenAI Image"
            t.contains("vidu") -> "Vidu"
            t.contains("pixverse") -> "PixVerse"
            t.contains("ideogram") -> "Ideogram"
            t.contains("ltx") -> "LTX Video"
            t.contains("suno") -> "Suno"
            t.contains("elevenlabs") -> "ElevenLabs"
            t.contains("deepseek") -> "DeepSeek"
            t.contains("claude") -> "Claude"
            t.contains("grok") -> "Grok"
            t.contains("gemini") -> "Gemini"
            t.contains("hidream") -> "HiDream"
            t.contains("hunyuan") -> "Hunyuan"
            t.contains("tripo") -> "Tripo 3D"
            t.contains("krea") -> "Krea"
            t.contains("luma") -> "Luma"
            else -> orgName
        }
    }

    fun deriveProviderPriority(
        family: String,
        modalities: List<Modality>,
        modelId: String
    ): Int {
        val id = modelId.lowercase()
        val isImage = modalities.contains(Modality.IMAGE)
        val isVideo = modalities.contains(Modality.VIDEO)
        val isAudio = modalities.contains(Modality.AUDIO)
        val isChat = modalities.contains(Modality.CHAT)

        return when {
            isImage -> when {
                family.equals("Qwen Image", ignoreCase = true) || id.contains("qwen-image") -> 100
                family.equals("Seedream", ignoreCase = true) || id.contains("seedream") -> 99
                family.equals("FLUX", ignoreCase = true) || id.contains("flux") -> 98
                family.equals("Google Imagen", ignoreCase = true) || id.contains("imagen") -> 97
                family.equals("OpenAI Image", ignoreCase = true) || id.contains("gpt-image") || id.contains("dall-e") -> 96
                family.equals("Ideogram", ignoreCase = true) || id.contains("ideogram") -> 95
                family.equals("Midjourney", ignoreCase = true) || id.contains("midjourney") || id.contains("youchuan") -> 94
                family.equals("HiDream", ignoreCase = true) || id.contains("hidream") -> 93
                family.equals("Krea", ignoreCase = true) || id.contains("krea") -> 92
                isFlagshipProvider(family) -> 85
                else -> 60
            }
            isVideo -> when {
                family.equals("Kling", ignoreCase = true) || id.contains("kling") -> 100
                family.equals("Wan", ignoreCase = true) || id.contains("wan-") -> 99
                family.equals("Seedance", ignoreCase = true) || id.contains("seedance") -> 98
                family.equals("MiniMax", ignoreCase = true) || id.contains("minimax") || id.contains("hailuo") -> 97
                family.equals("Vidu", ignoreCase = true) || id.contains("vidu") -> 96
                family.equals("PixVerse", ignoreCase = true) || id.contains("pixverse") -> 95
                family.equals("LTX Video", ignoreCase = true) || id.contains("ltx") -> 94
                family.equals("Google Veo", ignoreCase = true) || id.contains("veo") -> 93
                family.equals("Luma", ignoreCase = true) || id.contains("luma") -> 92
                family.equals("Hunyuan", ignoreCase = true) || id.contains("hunyuan") -> 91
                isFlagshipProvider(family) -> 85
                else -> 60
            }
            isAudio -> when {
                family.equals("Suno", ignoreCase = true) || id.contains("suno") -> 100
                family.equals("ElevenLabs", ignoreCase = true) || id.contains("elevenlabs") -> 99
                family.equals("MiniMax", ignoreCase = true) || id.contains("speech") || id.contains("music") -> 98
                isFlagshipProvider(family) -> 85
                else -> 50
            }
            isChat -> when {
                family.equals("DeepSeek", ignoreCase = true) || id.contains("deepseek") -> 100
                family.equals("Claude", ignoreCase = true) || id.contains("claude") || id.contains("anthropic") -> 99
                family.equals("OpenAI", ignoreCase = true) || id.contains("gpt-4") || id.contains("o1") || id.contains("o3") -> 98
                family.equals("Grok", ignoreCase = true) || id.contains("grok") || id.contains("xai") -> 97
                family.equals("Gemini", ignoreCase = true) || id.contains("gemini") -> 96
                family.equals("Qwen", ignoreCase = true) || id.contains("qwen") -> 95
                isFlagshipProvider(family) -> 85
                else -> 50
            }
            else -> 50
        }
    }

    fun deriveVariantRank(displayName: String, modelId: String): Int {
        val t = "$displayName $modelId".lowercase()
        return when {
            t.contains("pro") || t.contains("prime") || t.contains("ultra") || t.contains("max") -> 4
            t.contains("std") || t.contains("standard") || t.contains("dev") -> 3
            t.contains("turbo") || t.contains("plus") -> 2
            t.contains("lite") || t.contains("mini") || t.contains("fast") || t.contains("schnell") -> 1
            else -> 3
        }
    }

    fun deriveIsPrimaryGeneration(displayName: String, modelId: String): Boolean {
        val t = "$displayName $modelId".lowercase()
        if (t.contains("edit") || t.contains("sequential") || t.contains("inpaint") ||
            t.contains("motion-control") || t.contains("layer-decomposition") ||
            t.contains("avatar") || t.contains("upscale") || t.contains("extend")) {
            return false
        }
        return true
    }

    fun derivePopularityScore(
        modelId: String,
        displayName: String,
        orgName: String,
        tags: List<String>,
        modalities: List<Modality>,
        providerPriority: Int
    ): Int {
        return providerPriority
    }

    fun deriveShortDescription(
        modelId: String,
        displayName: String,
        orgName: String,
        modalities: List<Modality>,
        profile: String
    ): String {
        val id = modelId.lowercase()

        return when {
            id.contains("seedream-v5") -> "Ideal para fotorrealismo de nueva generación, retratos y estética publicitaria."
            id.contains("seedream-v4.7") -> "Destaca por edición quirúrgica de elementos y fidelidad a la identidad."
            id.contains("seedream") -> "Ideal para composición artística rica y seguimiento exacto de prompts."
            id.contains("qwen-image-3.0-pro") -> "Destaca por renderizado tipográfico perfecto en chino/inglés y texturas ricas."
            id.contains("qwen-image") -> "Ideal para ilustración editorial, diseño gráfico y estilo cinematográfico."
            id.contains("gpt-image-2.5") -> "Destaca por texturas hiperdetalladas y transformaciones en lenguaje natural."
            id.contains("flux") -> "Líder en arte digital, renderizado volumétrico y fotorrealismo estilizado."
            id.contains("nano-banana") -> "El más rápido para iteración de conceptos visuales y mockups inmediatos."
            id.contains("mai-image") -> "Destaca por tipografía limpia y diseño publicitario minimalista."
            id.contains("minimax/h3") || id.contains("hailuo") -> "Líder en cinemática fluida, física dinámica y movimiento humano realista."
            id.contains("wan-3") || id.contains("wan-2") -> "Destaca por iluminación cinematográfica de alta escala y consistencia temporal."
            id.contains("kling") -> "Ideal para secuencias de acción trepidante, acrobacias y efectos VFX."
            id.contains("seedance") -> "Líder en coreografía visual, movimiento rítmico y baile fluido."
            id.contains("veo") -> "Vídeo fotorrealista con control de cámara cinematográfica de Google."
            id.contains("gemini-omni") -> "Ideal para generación de vídeo guiada por múltiples imágenes y clips."
            id.contains("suno") || id.contains("chirp") -> "Composición musical completa con letra, voces realistas y arreglos."
            id.contains("seed-audio") || id.contains("audio") -> "Generación de audio de alta fidelidad, foley y efectos ambientales."
            id.contains("deepseek") -> "Razonamiento analítico avanzado, matemáticas y arquitectura de código."
            modalities.contains(Modality.VIDEO) -> "Ideal para generación de vídeo dinámico con fluidez temporal."
            modalities.contains(Modality.IMAGE) -> "Ideal para generación de imágenes de alta fidelidad estética."
            modalities.contains(Modality.AUDIO) -> "Generación de pistas de audio y efectos con calidad de estudio."
            modalities.contains(Modality.CHAT) -> "Modelo de lenguaje optimizado para diálogo, creatividad y código."
            profile.isNotBlank() && profile.length in 10..90 -> profile
            else -> "Modelo especializado de IA de alta precisión."
        }
    }

    fun deriveSpeedQualityTag(
        modelId: String,
        displayName: String,
        tags: List<String>,
        discount: String?,
        basePriceUsd: Double?
    ): String {
        val id = modelId.lowercase()
        val tagsUpper = tags.map { it.uppercase() }

        return when {
            id.contains("fast") || id.contains("turbo") || id.contains("schnell") || id.contains("flash") || id.contains("rapid") || id.contains("lite") -> "Ultrarrápido"
            id.contains("pro") || id.contains("max") || id.contains("ultra") || id.contains("master") || id.contains("sunburst") || id.contains("hd") -> "Ultra Calidad"
            discount != null && (discount.toIntOrNull() ?: 0) >= 80 -> "Económico"
            basePriceUsd != null && basePriceUsd <= 0.015 -> "Económico"
            tagsUpper.contains("HOT") -> "Ultra Calidad"
            else -> "Equilibrado"
        }
    }

    fun deriveReleaseDateMillis(
        obj: JsonObject,
        tags: List<String>,
        modelId: String
    ): Long {
        val tagsUpper = tags.map { it.uppercase() }
        val id = modelId.lowercase()
        val hash = (modelId.hashCode() and 0x7FFFFFFF)

        return when {
            tagsUpper.contains("NUEVO") || id.contains("3.0-pro") || id.contains("5.0-pro") || id.contains("v2.5") -> {
                // Between 1 and 18 days ago
                val daysAgo = (hash % 18) + 1
                System.currentTimeMillis() - daysAgo * 86_400_000L
            }
            tagsUpper.contains("HOT") || id.contains("v4.7") || id.contains("h3-max") -> {
                // Between 12 and 28 days ago
                val daysAgo = (hash % 16) + 12
                System.currentTimeMillis() - daysAgo * 86_400_000L
            }
            else -> {
                // Older than 30 days
                System.currentTimeMillis() - 45L * 86_400_000L
            }
        }
    }

    fun derivePairedModelId(modelId: String): String? {
        val id = modelId.lowercase()
        return when {
            id.endsWith("/text-to-image") -> modelId.substringBeforeLast("/text-to-image") + "/edit"
            id.endsWith("/text-to-video") -> modelId.substringBeforeLast("/text-to-video") + "/image-to-video"
            id.endsWith("/edit") -> modelId.substringBeforeLast("/edit") + "/text-to-image"
            id.endsWith("/image-to-video") -> modelId.substringBeforeLast("/image-to-video") + "/text-to-video"
            id.contains("t2v") -> modelId.replace("t2v", "i2v")
            id.contains("i2v") -> modelId.replace("i2v", "t2v")
            id.contains("t2i") -> modelId.replace("t2i", "edit")
            else -> null
        }
    }
}

