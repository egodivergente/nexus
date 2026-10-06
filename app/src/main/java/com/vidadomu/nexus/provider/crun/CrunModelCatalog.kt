package com.vidadomu.nexus.provider.crun

import com.vidadomu.nexus.core.model.*

object CrunModelCatalog {
    private fun enumParam(key: String, name: String, default: String, options: List<String>, advanced: Boolean = false) =
        ModelParameter(key, name, ParameterType.ENUM, default, options = options, isAdvanced = advanced, nativeType = "string")

    private fun boolParam(key: String, name: String, default: Boolean, advanced: Boolean = false) =
        ModelParameter(key, name, ParameterType.BOOLEAN, default.toString(), isAdvanced = advanced, nativeType = "boolean")

    private fun duration(default: String = "5", options: List<String> = listOf("5", "10", "15")) =
        ModelParameter("duration", "Duración", ParameterType.DURATION, default, options = options, nativeType = "integer")

    private fun videoModel(
        id: String,
        name: String,
        family: String,
        price: Double,
        priceInfo: String,
        parameters: List<ModelParameter>,
        inputMode: ModelInputMode = ModelInputMode.SINGLE_IMAGE,
        requiredMedia: Boolean = false,
        firstFrameField: String? = "img_urls",
        videoField: String? = null,
        nativeRequired: List<String> = emptyList(),
        version: Double,
        rank: Int = 3
    ) = NexusModel(
        id = id,
        displayName = name,
        providerId = CrunProvider.PROVIDER_ID,
        organization = family,
        modalities = listOf(Modality.VIDEO),
        capabilities = buildList {
            add(ModelCapability.TEXT_TO_VIDEO)
            if (firstFrameField != null) add(ModelCapability.IMAGE_TO_VIDEO)
            if (videoField != null) add(ModelCapability.VIDEO_TO_VIDEO)
        },
        parameters = parameters,
        description = "Ruta oficial de $family mediante Crun.",
        pricingInfo = priceInfo,
        basePriceUsd = price,
        priceUnit = "5 s",
        priceUnitVerified = true,
        inputMode = inputMode,
        maxImages = if (firstFrameField == null) 0 else 1,
        maxVideos = if (videoField == null) 0 else 1,
        firstFrameField = firstFrameField,
        videoField = videoField,
        isMediaRequired = requiredMedia,
        nativeRequiredFields = nativeRequired,
        supportsImageInput = firstFrameField != null,
        supportsFirstFrame = firstFrameField != null,
        supportsVideoInput = videoField != null,
        inputImageField = firstFrameField,
        supportedAspectRatios = listOf("16:9", "9:16", "1:1"),
        supportedResolutions = listOf("720p", "1080p"),
        supportedDurations = listOf("5", "10", "15"),
        shortDescription = "Calidad completa; sin variantes Eco o Lite automáticas.",
        speedQualityTag = "Alta calidad",
        isFlagship = true,
        versionNumeric = version,
        modelFamily = family,
        variantRank = rank,
        providerPriority = 100
    )

    private val klingCommon = listOf(
        duration("5"),
        enumParam("aspect_ratio", "Formato", "16:9", listOf("16:9", "9:16", "1:1")),
        boolParam("audio", "Generar sonido", false),
        boolParam("multi_shots", "Varios planos", false, true)
    )

    private val motionCommon = listOf(
        enumParam("character_orientation", "Orientación del personaje", "image", listOf("image", "video")),
        boolParam("keep_original_sound", "Conservar sonido original", true),
        boolParam("input_compliance", "Comprobar entrada", true, true),
        boolParam("output_compliance", "Comprobar salida", true, true)
    )

    val models: List<NexusModel> = listOf(
        videoModel(
            "crun/kling-v3-std", "Kling 3.0 Standard", "Kling", .35,
            "≈ 0,35 USD / 5 s sin audio · tramo base Crun",
            klingCommon + enumParam("mode", "Calidad", "std", listOf("std")), version = 3.0
        ),
        videoModel(
            "crun/kling-v3-pro", "Kling 3.0 Pro", "Kling", .45,
            "≈ 0,45 USD / 5 s sin audio · tramo base Crun",
            klingCommon + enumParam("mode", "Calidad", "pro", listOf("pro")), version = 3.0, rank = 4
        ),
        videoModel(
            "crun/kling-v3-motion-control-std", "Kling 3.0 Std Motion Control", "Kling", .35,
            "Precio según duración del vídeo de movimiento",
            motionCommon + enumParam("mode", "Calidad", "std", listOf("std")),
            inputMode = ModelInputMode.MULTI_MODAL, requiredMedia = true,
            firstFrameField = "img_urls", videoField = "video_urls",
            nativeRequired = listOf("img_urls", "video_urls"), version = 3.0
        ),
        videoModel(
            "crun/kling-v3-motion-control-pro", "Kling 3.0 Pro Motion Control", "Kling", .45,
            "Precio según duración del vídeo de movimiento",
            motionCommon + enumParam("mode", "Calidad", "pro", listOf("pro")),
            inputMode = ModelInputMode.MULTI_MODAL, requiredMedia = true,
            firstFrameField = "img_urls", videoField = "video_urls",
            nativeRequired = listOf("img_urls", "video_urls"), version = 3.0, rank = 4
        ),
        videoModel(
            "crun/seedance-2.5", "Seedance 2.5", "Seedance", 1.20,
            "≈ 1,20 USD / 5 s 720p · tramo base Crun",
            listOf(
                enumParam("resolution", "Resolución", "720p", listOf("480p", "720p", "1080p")),
                enumParam("aspect_ratio", "Formato", "16:9", listOf("16:9", "9:16", "1:1", "4:3", "3:4", "21:9")),
                duration("5", listOf("5", "10", "15", "20", "30")),
                boolParam("audio", "Generar sonido", true),
                boolParam("direct_byteplus", "BytePlus directo", false, true),
                boolParam("byteplus_fallback", "Respaldo BytePlus", false, true),
                boolParam("return_last_frame", "Devolver último fotograma", false, true)
            ), version = 2.5, rank = 4
        ),
        videoModel(
            "crun/seedance-2.0", "Seedance 2.0 Standard", "Seedance", .80,
            "≈ 0,80 USD / 5 s 720p · tramo base Crun",
            listOf(
                enumParam("resolution", "Resolución", "720p", listOf("480p", "720p", "1080p")),
                enumParam("aspect_ratio", "Formato", "16:9", listOf("16:9", "9:16", "1:1", "4:3", "3:4", "21:9")),
                duration(), boolParam("audio", "Generar sonido", true),
                boolParam("return_last_frame", "Devolver último fotograma", false, true)
            ), version = 2.0
        ),
        videoModel(
            "crun/wan-3.0", "Wan 3.0", "Wan", .375,
            "≈ 0,375 USD / 5 s 720p · tramo base Crun",
            listOf(
                enumParam("resolution", "Resolución", "720P", listOf("480P", "720P", "1080P")),
                enumParam("aspect_ratio", "Formato", "16:9", listOf("16:9", "9:16", "1:1")),
                duration("5", listOf("5", "10", "15", "20", "30")),
                boolParam("audio", "Generar sonido", true),
                boolParam("prompt_extend", "Ampliar prompt", true, true)
            ), version = 3.0, rank = 4
        ),
        videoModel(
            "crun/minimax-h3", "MiniMax H3", "MiniMax", .40,
            "≈ 0,40 USD / 5 s 768p · tramo base Crun",
            listOf(
                enumParam("resolution", "Resolución", "768P", listOf("768P", "2K")),
                duration("5", listOf("5", "10", "15")),
                boolParam("audio", "Generar sonido", true),
                boolParam("prompt_expansion", "Ampliar prompt", false, true)
            ), version = 3.0, rank = 4
        )
    )
}
