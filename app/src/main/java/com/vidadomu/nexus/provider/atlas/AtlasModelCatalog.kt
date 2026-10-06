package com.vidadomu.nexus.provider.atlas

import com.vidadomu.nexus.core.model.*

object AtlasModelCatalog {
    @Volatile var publishedSnapshot: List<NexusModel> = emptyList()

    val curatedModels: List<NexusModel> = listOf(
        // ==========================================
        // === MODELOS DE IMAGEN (VERIFICADOS EN VIVO) ===
        // ==========================================
        NexusModel(
            id = "qwen-image-3.0-pro/text-to-image",
            displayName = "Qwen Image 3.0 Pro",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "Alibaba (Qwen)",
            modalities = listOf(Modality.IMAGE),
            capabilities = listOf(ModelCapability.TEXT_TO_IMAGE),
            badge = "NUEVO",
            description = "El modelo insignia de Alibaba: composición fotorrealista extrema, fidelidad textual perfecta en chino/inglés y estética editorial publicitaria.",
            shortDescription = "Destaca por renderizado tipográfico perfecto en chino/inglés y texturas ricas.",
            speedQualityTag = "Ultra Calidad",
            popularityScore = 100,
            releaseDateMillis = System.currentTimeMillis() - 3 * 86_400_000L,
            pairedEditModelId = "qwen-image-3.0-pro/edit",
            isFlagship = true,
            pricingInfo = "Premium (~3,20 cént. / img)",
            basePriceUsd = 0.035,
            priceUnit = "img",
            inputMode = ModelInputMode.TEXT_ONLY,
            maxImages = 0,
            isMediaRequired = false,
            supportsNativeAspectRatio = true,
            supportedAspectRatios = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3"),
            parameters = listOf(
                ModelParameter(
                    key = "aspect_ratio",
                    displayName = "Relación de aspecto",
                    type = ParameterType.ASPECT_RATIO,
                    defaultValue = "1:1",
                    options = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3"),
                    description = "Formato de proporción de salida",
                    isAdvanced = false
                ),
                ModelParameter(
                    key = "seed",
                    displayName = "Semilla (Seed)",
                    type = ParameterType.SEED,
                    defaultValue = "-1",
                    description = "Usa -1 para semilla aleatoria o un número entero para reproducibilidad",
                    isAdvanced = true
                )
            )
        ),
        NexusModel(
            id = "qwen-image-3.0-pro/edit",
            displayName = "Qwen Image 3.0 Pro Edit",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "Alibaba (Qwen)",
            modalities = listOf(Modality.IMAGE),
            capabilities = listOf(ModelCapability.IMAGE_EDITING, ModelCapability.IMAGE_TO_IMAGE),
            badge = "NUEVO",
            description = "Edición quirúrgica guiada por prompt con fidelidad absoluta a la geometría e iluminación de la imagen de origen.",
            shortDescription = "Ideal para transformar elementos manteniendo la identidad del sujeto.",
            speedQualityTag = "Ultra Calidad",
            popularityScore = 99,
            releaseDateMillis = System.currentTimeMillis() - 3 * 86_400_000L,
            pairedEditModelId = "qwen-image-3.0-pro/text-to-image",
            isFlagship = true,
            pricingInfo = "Premium (~3,49 cént. / img)",
            basePriceUsd = 0.038,
            priceUnit = "img",
            inputMode = ModelInputMode.SINGLE_IMAGE,
            maxImages = 1,
            firstFrameField = "image",
            isMediaRequired = true,
            supportsNativeAspectRatio = true,
            supportsImageInput = true,
            inputImageField = "image",
            supportedAspectRatios = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3"),
            parameters = listOf(
                ModelParameter(
                    key = "aspect_ratio",
                    displayName = "Relación de aspecto",
                    type = ParameterType.ASPECT_RATIO,
                    defaultValue = "1:1",
                    options = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "bytedance/seedream-v5.0-pro/text-to-image",
            displayName = "Seedream v5.0 Pro",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "ByteDance",
            modalities = listOf(Modality.IMAGE),
            capabilities = listOf(ModelCapability.TEXT_TO_IMAGE),
            badge = "HOT",
            description = "El buque insignia de ByteDance con arquitectura de difusión de escala completa, micropiel hiperrealista y control de iluminación volumétrica.",
            shortDescription = "Ideal para fotorrealismo de nueva generación, retratos y estética publicitaria.",
            speedQualityTag = "Ultra Calidad",
            popularityScore = 100,
            releaseDateMillis = System.currentTimeMillis() - 5 * 86_400_000L,
            pairedEditModelId = "bytedance/seedream-v5.0-pro/edit",
            isFlagship = true,
            pricingInfo = "Estándar (~3,14 cént. / img)",
            basePriceUsd = 0.034,
            priceUnit = "img",
            inputMode = ModelInputMode.TEXT_ONLY,
            maxImages = 0,
            isMediaRequired = false,
            supportsNativeAspectRatio = true,
            supportedAspectRatios = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3"),
            parameters = listOf(
                ModelParameter(
                    key = "aspect_ratio",
                    displayName = "Relación de aspecto",
                    type = ParameterType.ASPECT_RATIO,
                    defaultValue = "1:1",
                    options = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "bytedance/seedream-v5.0-pro/edit",
            displayName = "Seedream v5.0 Pro Edit",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "ByteDance",
            modalities = listOf(Modality.IMAGE),
            capabilities = listOf(ModelCapability.IMAGE_EDITING, ModelCapability.IMAGE_TO_IMAGE),
            badge = "HOT",
            description = "Multi-edición referencial y composición secuencial con consistencia absoluta de identidad entre múltiples tomas.",
            shortDescription = "Ideal para transferir estilos y combinar múltiples imágenes de referencia.",
            speedQualityTag = "Ultra Calidad",
            popularityScore = 99,
            releaseDateMillis = System.currentTimeMillis() - 5 * 86_400_000L,
            pairedEditModelId = "bytedance/seedream-v5.0-pro/text-to-image",
            isFlagship = true,
            pricingInfo = "Estándar (~3,14 cént. / img)",
            basePriceUsd = 0.034,
            priceUnit = "img",
            inputMode = ModelInputMode.MULTI_IMAGE,
            maxImages = 10,
            imagesArrayField = "images",
            isMediaRequired = true,
            supportsNativeAspectRatio = false,
            supportsImageInput = true,
            supportsReferenceImages = true,
            inputImageField = "images",
            parameters = listOf(
                ModelParameter(
                    key = "quality",
                    displayName = "Calidad",
                    type = ParameterType.ENUM,
                    defaultValue = "high",
                    options = listOf("medium", "high"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "google/nano-banana-pro/text-to-image",
            displayName = "Nano Banana Pro",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "Google",
            modalities = listOf(Modality.IMAGE),
            capabilities = listOf(ModelCapability.TEXT_TO_IMAGE),
            badge = "Ultrarrápido",
            description = "Generación de imagen fotorrealista y estilizada de altísima velocidad respaldada por Google.",
            pricingInfo = "Bajo coste (~1,04 cént. / img)",
            basePriceUsd = 0.012,
            priceUnit = "img",
            inputMode = ModelInputMode.TEXT_ONLY,
            maxImages = 0,
            isMediaRequired = false,
            supportsNativeAspectRatio = true,
            supportedAspectRatios = listOf("1:1", "16:9", "9:16", "4:3", "3:4"),
            parameters = listOf(
                ModelParameter(
                    key = "aspect_ratio",
                    displayName = "Relación de aspecto",
                    type = ParameterType.ASPECT_RATIO,
                    defaultValue = "1:1",
                    options = listOf("1:1", "16:9", "9:16", "4:3", "3:4"),
                    description = "Formato de proporción del lienzo de salida",
                    isAdvanced = false
                ),
                ModelParameter(
                    key = "seed",
                    displayName = "Semilla (Seed)",
                    type = ParameterType.SEED,
                    defaultValue = "-1",
                    description = "Usa -1 para semilla aleatoria o un número entero para reproducibilidad",
                    isAdvanced = true
                )
            )
        ),
        NexusModel(
            id = "bytedance/seedream-v4",
            displayName = "Seedream 4.0",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "ByteDance",
            modalities = listOf(Modality.IMAGE),
            capabilities = listOf(ModelCapability.TEXT_TO_IMAGE, ModelCapability.IMAGE_TO_IMAGE),
            badge = "Popular",
            description = "Generación de alta estética fotorrealista y conceptual de última generación desarrollada por ByteDance.",
            pricingInfo = "Estándar (~1,30 cént. / img)",
            basePriceUsd = 0.015,
            priceUnit = "img",
            inputMode = ModelInputMode.SINGLE_IMAGE,
            maxImages = 1,
            firstFrameField = "image",
            isMediaRequired = false,
            supportsNativeAspectRatio = true,
            supportsImageInput = true,
            inputImageField = "image",
            supportedAspectRatios = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3"),
            parameters = listOf(
                ModelParameter(
                    key = "aspect_ratio",
                    displayName = "Relación de aspecto",
                    type = ParameterType.ASPECT_RATIO,
                    defaultValue = "1:1",
                    options = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3"),
                    description = "Proporción estética del cuadro",
                    isAdvanced = false
                ),
                ModelParameter(
                    key = "seed",
                    displayName = "Semilla",
                    type = ParameterType.SEED,
                    defaultValue = "-1",
                    isAdvanced = true
                )
            )
        ),
        NexusModel(
            id = "bytedance/seedream-v4.7/text-to-image",
            displayName = "Seedream 4.7",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "ByteDance",
            modalities = listOf(Modality.IMAGE),
            capabilities = listOf(ModelCapability.TEXT_TO_IMAGE),
            badge = "NUEVO",
            description = "ByteDance Seedream 4.7 con fidelidad visual extrema y seguimiento de instrucciones perfeccionado.",
            pricingInfo = "3,00 cént. / img",
            basePriceUsd = 0.030,
            priceUnit = "img",
            inputMode = ModelInputMode.TEXT_ONLY,
            maxImages = 0,
            isMediaRequired = false,
            supportsNativeAspectRatio = true,
            supportedAspectRatios = listOf("1:1", "16:9", "9:16", "4:3", "3:4"),
            parameters = listOf(
                ModelParameter(
                    key = "aspect_ratio",
                    displayName = "Relación de aspecto",
                    type = ParameterType.ASPECT_RATIO,
                    defaultValue = "1:1",
                    options = listOf("1:1", "16:9", "9:16", "4:3", "3:4"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "bytedance/seedream-v4.7/edit",
            displayName = "Seedream 4.7 Edit",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "ByteDance",
            modalities = listOf(Modality.IMAGE),
            capabilities = listOf(ModelCapability.IMAGE_EDITING, ModelCapability.IMAGE_TO_IMAGE),
            badge = "HOT",
            description = "Edición quirúrgica por texto respetando identidad, iluminación y texturas de la imagen original.",
            pricingInfo = "3,00 cént. / img",
            basePriceUsd = 0.030,
            priceUnit = "img",
            inputMode = ModelInputMode.MULTI_IMAGE,
            maxImages = 10,
            imagesArrayField = "images",
            isMediaRequired = true,
            supportsNativeAspectRatio = false,
            supportsImageInput = true,
            supportsReferenceImages = true,
            inputImageField = "images",
            parameters = listOf(
                ModelParameter(
                    key = "size",
                    displayName = "Resolución",
                    type = ParameterType.RESOLUTION,
                    defaultValue = "1024x1024",
                    options = listOf("1024x1024", "1536x1024", "1024x1536"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "openai/gpt-image-2.5-sunburst/text-to-image",
            displayName = "GPT Image 2.5 Sunburst",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "OpenAI",
            modalities = listOf(Modality.IMAGE),
            capabilities = listOf(ModelCapability.TEXT_TO_IMAGE),
            badge = "-80%",
            discount = "80",
            originPriceUsd = 0.004,
            basePriceUsd = 0.003,
            priceUnit = "img",
            inputMode = ModelInputMode.TEXT_ONLY,
            maxImages = 0,
            isMediaRequired = false,
            supportsNativeAspectRatio = false,
            description = "OpenAI generación fotorrealista de última hornada hasta 4K con fondos transparentes nativos.",
            pricingInfo = "0,3 cént. / img (80% dto.)",
            parameters = listOf(
                ModelParameter(
                    key = "size",
                    displayName = "Resolución",
                    type = ParameterType.RESOLUTION,
                    defaultValue = "1024x1024",
                    options = listOf("1024x1024", "1536x1024", "1024x1536", "2048x2048"),
                    isAdvanced = false
                ),
                ModelParameter(
                    key = "quality",
                    displayName = "Calidad",
                    type = ParameterType.ENUM,
                    defaultValue = "medium",
                    options = listOf("low", "medium", "high", "xhigh"),
                    isAdvanced = true
                )
            )
        ),
        NexusModel(
            id = "openai/gpt-image-2.5-sunburst/edit",
            displayName = "GPT Image 2.5 Sunburst Edit",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "OpenAI",
            modalities = listOf(Modality.IMAGE),
            capabilities = listOf(ModelCapability.IMAGE_EDITING, ModelCapability.IMAGE_TO_IMAGE),
            badge = "-80%",
            discount = "80",
            originPriceUsd = 0.006,
            basePriceUsd = 0.005,
            priceUnit = "img",
            inputMode = ModelInputMode.MULTI_IMAGE,
            maxImages = 16,
            imagesArrayField = "images",
            isMediaRequired = true,
            supportsNativeAspectRatio = false,
            supportsImageInput = true,
            supportsReferenceImages = true,
            inputImageField = "images",
            description = "Aplica instrucciones complejas en lenguaje natural conservando consistencia del sujeto original.",
            pricingInfo = "0,5 cént. / img (80% dto.)",
            parameters = listOf(
                ModelParameter(
                    key = "quality",
                    displayName = "Calidad",
                    type = ParameterType.ENUM,
                    defaultValue = "medium",
                    options = listOf("low", "medium", "high"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "microsoft/mai-image-2.6-flash/text-to-image",
            displayName = "MAI Image 2.6 Flash",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "Microsoft",
            modalities = listOf(Modality.IMAGE),
            capabilities = listOf(ModelCapability.TEXT_TO_IMAGE),
            badge = "-90%",
            discount = "90",
            originPriceUsd = 0.044,
            basePriceUsd = 0.040,
            priceUnit = "img",
            inputMode = ModelInputMode.TEXT_ONLY,
            maxImages = 0,
            isMediaRequired = false,
            supportsNativeAspectRatio = true,
            supportedAspectRatios = listOf("1:1", "16:9", "9:16", "4:3", "3:4"),
            description = "Motor insignia de Microsoft con renderizado tipográfico de precisión y estética publicitaria.",
            pricingInfo = "4,0 cént. / img (90% dto.)",
            parameters = listOf(
                ModelParameter(
                    key = "aspect_ratio",
                    displayName = "Relación de aspecto",
                    type = ParameterType.ASPECT_RATIO,
                    defaultValue = "1:1",
                    options = listOf("1:1", "16:9", "9:16", "4:3", "3:4"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "black-forest-labs/flux-1-schnell",
            displayName = "FLUX.1 Schnell",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "Black Forest Labs",
            modalities = listOf(Modality.IMAGE),
            capabilities = listOf(ModelCapability.TEXT_TO_IMAGE),
            badge = "Alta Velocidad",
            description = "Modelo destilado ultrarrápido de Black Forest Labs para previsualización inmediata en 1 a 4 pasos.",
            pricingInfo = "Económico (~0,87 cént. / img)",
            basePriceUsd = 0.010,
            priceUnit = "img",
            inputMode = ModelInputMode.TEXT_ONLY,
            maxImages = 0,
            isMediaRequired = false,
            supportsNativeAspectRatio = true,
            supportedAspectRatios = listOf("1:1", "16:9", "9:16", "4:3", "3:4"),
            parameters = listOf(
                ModelParameter(
                    key = "aspect_ratio",
                    displayName = "Relación de aspecto",
                    type = ParameterType.ASPECT_RATIO,
                    defaultValue = "1:1",
                    options = listOf("1:1", "16:9", "9:16", "4:3", "3:4"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "black-forest-labs/flux-1-dev",
            displayName = "FLUX.1 Dev",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "Black Forest Labs",
            modalities = listOf(Modality.IMAGE),
            capabilities = listOf(ModelCapability.TEXT_TO_IMAGE),
            badge = "Máxima Calidad",
            description = "Modelo base de grado insignia con fidelidad prompt-to-image insuperable y realismo anatómico.",
            pricingInfo = "Premium (~2,60 cént. / img)",
            basePriceUsd = 0.030,
            priceUnit = "img",
            inputMode = ModelInputMode.TEXT_ONLY,
            maxImages = 0,
            isMediaRequired = false,
            supportsNativeAspectRatio = true,
            supportedAspectRatios = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "21:9"),
            parameters = listOf(
                ModelParameter(
                    key = "aspect_ratio",
                    displayName = "Relación de aspecto",
                    type = ParameterType.ASPECT_RATIO,
                    defaultValue = "1:1",
                    options = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "21:9"),
                    isAdvanced = false
                )
            )
        ),

        // ==========================================
        // === MODELOS DE VÍDEO (VERIFICADOS EN VIVO) ===
        // ==========================================
        NexusModel(
            id = "minimax/h3-max/image-to-video",
            displayName = "MiniMax H3 Max Image-to-Video",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "MiniMax",
            modalities = listOf(Modality.VIDEO),
            capabilities = listOf(ModelCapability.IMAGE_TO_VIDEO),
            badge = "HOT",
            discount = "95",
            originPriceUsd = 0.050,
            basePriceUsd = 0.048,
            priceUnit = "seg",
            inputMode = ModelInputMode.DUAL_FRAME,
            maxImages = 2,
            firstFrameField = "image",
            lastFrameField = "end_image",
            isMediaRequired = true,
            supportsNativeAspectRatio = false,
            supportsImageInput = true,
            supportsFirstFrame = true,
            supportsLastFrame = true,
            inputImageField = "image",
            description = "El estándar de oro en animación de vídeo: fluidez orgánica insuperable, física dinámica realista y soporte para fotograma final opcional.",
            shortDescription = "Líder en cinemática fluida, física dinámica y movimiento humano realista.",
            speedQualityTag = "Ultra Calidad",
            popularityScore = 100,
            releaseDateMillis = System.currentTimeMillis() - 4 * 86_400_000L,
            pairedEditModelId = "minimax/h3-max/text-to-video",
            isFlagship = true,
            pricingInfo = "4,8 cént. / seg (95% dto.)",
            parameters = listOf(
                ModelParameter(
                    key = "duration",
                    displayName = "Duración",
                    type = ParameterType.DURATION,
                    defaultValue = "5",
                    options = listOf("5", "10"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "minimax/h3-max/text-to-video",
            displayName = "MiniMax H3 Max Text-to-Video",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "MiniMax",
            modalities = listOf(Modality.VIDEO),
            capabilities = listOf(ModelCapability.TEXT_TO_VIDEO),
            badge = "-95%",
            discount = "95",
            originPriceUsd = 0.050,
            basePriceUsd = 0.048,
            priceUnit = "seg",
            inputMode = ModelInputMode.TEXT_ONLY,
            maxImages = 0,
            isMediaRequired = false,
            supportsNativeAspectRatio = true,
            supportedAspectRatios = listOf("16:9", "9:16", "1:1"),
            description = "Generación directa de texto a vídeo cinemático fotorrealista a 768P con dinamismo de cámara avanzado.",
            shortDescription = "Vídeo cinemático generado directamente desde descripción de texto.",
            speedQualityTag = "Ultra Calidad",
            popularityScore = 99,
            releaseDateMillis = System.currentTimeMillis() - 4 * 86_400_000L,
            pairedEditModelId = "minimax/h3-max/image-to-video",
            isFlagship = true,
            pricingInfo = "4,8 cént. / seg (95% dto.)",
            parameters = listOf(
                ModelParameter(
                    key = "duration",
                    displayName = "Duración",
                    type = ParameterType.DURATION,
                    defaultValue = "5",
                    options = listOf("5", "10", "15"),
                    isAdvanced = false
                ),
                ModelParameter(
                    key = "aspect_ratio",
                    displayName = "Relación de aspecto",
                    type = ParameterType.ASPECT_RATIO,
                    defaultValue = "16:9",
                    options = listOf("16:9", "9:16", "1:1"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "google/veo3.1-lite/text-to-video",
            displayName = "Veo 3.1 Lite",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "Google",
            modalities = listOf(Modality.VIDEO),
            capabilities = listOf(ModelCapability.TEXT_TO_VIDEO),
            badge = "Cine Google",
            description = "Modelo cinemático de vídeo en alta definición con movimiento fluido y coherencia física de Google.",
            pricingInfo = "Vídeo HD (~13 cént. / clip)",
            basePriceUsd = 0.15,
            priceUnit = "clip",
            inputMode = ModelInputMode.TEXT_ONLY,
            maxImages = 0,
            isMediaRequired = false,
            supportsNativeAspectRatio = true,
            supportedAspectRatios = listOf("16:9", "9:16", "1:1"),
            parameters = listOf(
                ModelParameter(
                    key = "duration",
                    displayName = "Duración (Segundos)",
                    type = ParameterType.DURATION,
                    defaultValue = "5",
                    options = listOf("5", "10"),
                    isAdvanced = false
                ),
                ModelParameter(
                    key = "aspect_ratio",
                    displayName = "Relación de aspecto",
                    type = ParameterType.ASPECT_RATIO,
                    defaultValue = "16:9",
                    options = listOf("16:9", "9:16", "1:1"),
                    isAdvanced = false
                ),
                ModelParameter(
                    key = "resolution",
                    displayName = "Resolución",
                    type = ParameterType.RESOLUTION,
                    defaultValue = "720p",
                    options = listOf("720p", "1080p"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "google/gemini-omni-1.1-flash/text-to-video",
            displayName = "Gemini Omni 1.1 Flash Video",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "Google",
            modalities = listOf(Modality.VIDEO),
            capabilities = listOf(ModelCapability.TEXT_TO_VIDEO),
            badge = "-90%",
            discount = "90",
            originPriceUsd = 0.041,
            basePriceUsd = 0.037,
            priceUnit = "seg",
            inputMode = ModelInputMode.TEXT_ONLY,
            maxImages = 0,
            isMediaRequired = false,
            supportsNativeAspectRatio = true,
            supportedAspectRatios = listOf("16:9", "9:16", "1:1"),
            description = "DeepMind nativo multimodal que genera clips de vídeo cinemático con audio nativo sincronizado.",
            pricingInfo = "3,7 cént. / seg (90% dto.)",
            parameters = listOf(
                ModelParameter(
                    key = "duration",
                    displayName = "Duración",
                    type = ParameterType.DURATION,
                    defaultValue = "5",
                    options = listOf("5", "10"),
                    isAdvanced = false
                ),
                ModelParameter(
                    key = "aspect_ratio",
                    displayName = "Relación de aspecto",
                    type = ParameterType.ASPECT_RATIO,
                    defaultValue = "16:9",
                    options = listOf("16:9", "9:16", "1:1"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "google/gemini-omni-1.1-flash/image-to-video",
            displayName = "Gemini Omni 1.1 Flash Image-to-Video",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "Google",
            modalities = listOf(Modality.VIDEO),
            capabilities = listOf(ModelCapability.IMAGE_TO_VIDEO),
            badge = "-90%",
            discount = "90",
            originPriceUsd = 0.043,
            basePriceUsd = 0.039,
            priceUnit = "seg",
            inputMode = ModelInputMode.DUAL_FRAME,
            maxImages = 2,
            firstFrameField = "image",
            lastFrameField = "last_image",
            isMediaRequired = true,
            supportsNativeAspectRatio = false,
            supportsImageInput = true,
            supportsFirstFrame = true,
            supportsLastFrame = true,
            inputImageField = "image",
            description = "Anima una imagen fija en un clip cinemático con audio nativo; control opcional de fotograma final.",
            pricingInfo = "3,9 cént. / seg (90% dto.)",
            parameters = listOf(
                ModelParameter(
                    key = "duration",
                    displayName = "Duración",
                    type = ParameterType.DURATION,
                    defaultValue = "5",
                    options = listOf("5", "10"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "minimax/h3-max/text-to-video",
            displayName = "MiniMax H3 Max",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "MiniMax",
            modalities = listOf(Modality.VIDEO),
            capabilities = listOf(ModelCapability.TEXT_TO_VIDEO),
            badge = "-95%",
            discount = "95",
            originPriceUsd = 0.050,
            basePriceUsd = 0.048,
            priceUnit = "seg",
            inputMode = ModelInputMode.TEXT_ONLY,
            maxImages = 0,
            isMediaRequired = false,
            supportsNativeAspectRatio = true,
            supportedAspectRatios = listOf("16:9", "9:16", "1:1"),
            description = "Generación de vídeo cinemático fotorrealista a 768P con dinamismo de cámara avanzado.",
            pricingInfo = "4,8 cént. / seg (95% dto.)",
            parameters = listOf(
                ModelParameter(
                    key = "duration",
                    displayName = "Duración",
                    type = ParameterType.DURATION,
                    defaultValue = "5",
                    options = listOf("5", "10", "15"),
                    isAdvanced = false
                ),
                ModelParameter(
                    key = "aspect_ratio",
                    displayName = "Relación de aspecto",
                    type = ParameterType.ASPECT_RATIO,
                    defaultValue = "16:9",
                    options = listOf("16:9", "9:16", "1:1"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "minimax/h3-max/image-to-video",
            displayName = "MiniMax H3 Max Image-to-Video",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "MiniMax",
            modalities = listOf(Modality.VIDEO),
            capabilities = listOf(ModelCapability.IMAGE_TO_VIDEO),
            badge = "-95%",
            discount = "95",
            originPriceUsd = 0.050,
            basePriceUsd = 0.048,
            priceUnit = "seg",
            inputMode = ModelInputMode.DUAL_FRAME,
            maxImages = 2,
            firstFrameField = "image",
            lastFrameField = "end_image",
            isMediaRequired = true,
            supportsNativeAspectRatio = false,
            supportsImageInput = true,
            supportsFirstFrame = true,
            supportsLastFrame = true,
            inputImageField = "image",
            description = "Anima fotograma inicial con transición física fluida hacia fotograma final opcional.",
            pricingInfo = "4,8 cént. / seg (95% dto.)",
            parameters = listOf(
                ModelParameter(
                    key = "duration",
                    displayName = "Duración",
                    type = ParameterType.DURATION,
                    defaultValue = "5",
                    options = listOf("5", "10"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "minimax/h3-fast/image-to-video",
            displayName = "MiniMax H3 Fast Image-to-Video",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "MiniMax",
            modalities = listOf(Modality.VIDEO),
            capabilities = listOf(ModelCapability.IMAGE_TO_VIDEO),
            badge = "RÁPIDO",
            discount = "95",
            originPriceUsd = 0.046,
            basePriceUsd = 0.044,
            priceUnit = "seg",
            inputMode = ModelInputMode.DUAL_FRAME,
            maxImages = 2,
            firstFrameField = "image",
            lastFrameField = "end_image",
            isMediaRequired = true,
            supportsNativeAspectRatio = false,
            supportsImageInput = true,
            supportsFirstFrame = true,
            supportsLastFrame = true,
            inputImageField = "image",
            description = "Animación ágil de imagen con latencia reducida y excelente fluidez gestual.",
            pricingInfo = "4,4 cént. / seg (95% dto.)",
            parameters = listOf(
                ModelParameter(
                    key = "duration",
                    displayName = "Duración",
                    type = ParameterType.DURATION,
                    defaultValue = "5",
                    options = listOf("5", "10"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "alibaba/wan-3.0-prime/text-to-video",
            displayName = "Wan 3.0 Prime",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "Alibaba (Qwen)",
            modalities = listOf(Modality.VIDEO),
            capabilities = listOf(ModelCapability.TEXT_TO_VIDEO),
            badge = "-90%",
            discount = "90",
            originPriceUsd = 0.068,
            basePriceUsd = 0.061,
            priceUnit = "seg",
            inputMode = ModelInputMode.TEXT_ONLY,
            maxImages = 0,
            isMediaRequired = false,
            supportsNativeAspectRatio = true,
            supportedAspectRatios = listOf("16:9", "9:16", "1:1"),
            description = "Renderer hiperrealista Wan 3.0 de Alibaba con control de duración inteligente hasta 30s.",
            pricingInfo = "6,1 cént. / seg (90% dto.)",
            parameters = listOf(
                ModelParameter(
                    key = "duration",
                    displayName = "Duración",
                    type = ParameterType.DURATION,
                    defaultValue = "5",
                    options = listOf("5", "10", "15"),
                    isAdvanced = false
                ),
                ModelParameter(
                    key = "aspect_ratio",
                    displayName = "Relación de aspecto",
                    type = ParameterType.ASPECT_RATIO,
                    defaultValue = "16:9",
                    options = listOf("16:9", "9:16", "1:1"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "alibaba/wan-3.0-prime/image-to-video",
            displayName = "Wan 3.0 Prime Image-to-Video",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "Alibaba (Qwen)",
            modalities = listOf(Modality.VIDEO),
            capabilities = listOf(ModelCapability.IMAGE_TO_VIDEO),
            badge = "HOT",
            discount = "90",
            originPriceUsd = 0.068,
            basePriceUsd = 0.061,
            priceUnit = "seg",
            inputMode = ModelInputMode.DUAL_FRAME,
            maxImages = 2,
            firstFrameField = "image",
            lastFrameField = "last_image",
            isMediaRequired = true,
            supportsNativeAspectRatio = false,
            supportsImageInput = true,
            supportsFirstFrame = true,
            supportsLastFrame = true,
            inputImageField = "image",
            description = "Anima fotograma inicial con audio nativo sincronizado y duración adaptable.",
            pricingInfo = "6,1 cént. / seg (90% dto.)",
            parameters = listOf(
                ModelParameter(
                    key = "duration",
                    displayName = "Duración",
                    type = ParameterType.DURATION,
                    defaultValue = "5",
                    options = listOf("5", "10"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "kwaivgi/kling-effects",
            displayName = "Kling Effects",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "Kling (Kuaishou)",
            modalities = listOf(Modality.VIDEO),
            capabilities = listOf(ModelCapability.IMAGE_TO_VIDEO),
            badge = "VFX Pro",
            description = "Efectos visuales y cinemática de alto impacto motorizados por la tecnología Kling.",
            pricingInfo = "12 cént. / clip",
            basePriceUsd = 0.12,
            priceUnit = "clip",
            inputMode = ModelInputMode.SINGLE_IMAGE,
            maxImages = 1,
            firstFrameField = "image",
            isMediaRequired = true,
            supportsNativeAspectRatio = false,
            supportsImageInput = true,
            supportsFirstFrame = true,
            inputImageField = "image",
            parameters = emptyList()
        ),

        // ==========================================
        // === MODELOS DE AUDIO (VERIFICADOS EN VIVO) ===
        // ==========================================
        NexusModel(
            id = "suno/chirp-v5",
            displayName = "Suno Chirp v5",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "Suno",
            modalities = listOf(Modality.AUDIO),
            capabilities = listOf(ModelCapability.TEXT_TO_AUDIO),
            badge = "Canción Completa",
            description = "Composición musical integral de estudio: instrumentación, arreglos y líricas de Suno.",
            pricingInfo = "3,5 cént. / min",
            basePriceUsd = 0.035,
            priceUnit = "min",
            parameters = listOf(
                ModelParameter(
                    key = "duration",
                    displayName = "Duración (Segundos)",
                    type = ParameterType.DURATION,
                    defaultValue = "30",
                    options = listOf("15", "30", "60"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "bytedance/seed-audio-1.0",
            displayName = "Seed Audio 1.0",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "ByteDance",
            modalities = listOf(Modality.AUDIO),
            capabilities = listOf(ModelCapability.TEXT_TO_AUDIO),
            badge = "Mezcla Completa",
            description = "Generación integral de mezclas sonoras: música, efectos ambientales y locución.",
            pricingInfo = "Audio completo (~1,73 cént. / min)",
            basePriceUsd = 0.020,
            priceUnit = "min",
            parameters = listOf(
                ModelParameter(
                    key = "duration",
                    displayName = "Duración (Segundos)",
                    type = ParameterType.DURATION,
                    defaultValue = "30",
                    options = listOf("15", "30", "60"),
                    isAdvanced = false
                )
            )
        ),
        NexusModel(
            id = "minimax/speech-2.6-hd",
            displayName = "Speech 2.6 HD",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "MiniMax",
            modalities = listOf(Modality.AUDIO),
            capabilities = listOf(ModelCapability.TEXT_TO_AUDIO),
            badge = "Voz HD",
            description = "Síntesis de voz y locución de altísima naturalidad y rango expresivo desarrollada por MiniMax.",
            pricingInfo = "Microprecio (~0,87 cént. / 1k car.)",
            basePriceUsd = 0.010,
            priceUnit = "1k chars",
            parameters = emptyList()
        ),

        // ==========================================
        // === MODELOS DE CHAT / LLM (VERIFICADOS EN VIVO) ===
        // ==========================================
        NexusModel(
            id = "deepseek-ai/deepseek-v4.1-flash",
            displayName = "DeepSeek V4.1 Flash",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "DeepSeek",
            modalities = listOf(Modality.CHAT),
            capabilities = listOf(ModelCapability.CHAT_COMPLETION),
            badge = "GRATIS",
            discount = "100",
            description = "Modelo MoE puntero en razonamiento y contexto largo integrado con herramientas nativas.",
            pricingInfo = "Incluido / 100% dto.",
            basePriceUsd = 0.0,
            priceUnit = "token",
            parameters = emptyList()
        ),
        NexusModel(
            id = "deepseek-ai/deepseek-v3",
            displayName = "DeepSeek V3",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "DeepSeek",
            modalities = listOf(Modality.CHAT),
            capabilities = listOf(ModelCapability.CHAT_COMPLETION),
            badge = "Popular",
            description = "Modelo de arquitectura MoE de 671B parámetros líder en benchmarks de razonamiento abierto.",
            pricingInfo = "Tokens (~0,0001 cént. / tok)",
            basePriceUsd = 0.000002,
            priceUnit = "token",
            parameters = emptyList()
        ),
        NexusModel(
            id = "moonshotai/kimi-k3",
            displayName = "Kimi K3",
            providerId = AtlasCloudProvider.PROVIDER_ID,
            organization = "Moonshot AI",
            modalities = listOf(Modality.CHAT),
            capabilities = listOf(ModelCapability.CHAT_COMPLETION),
            badge = "Contexto Gigante",
            description = "Modelo con capacidad de procesamiento de contexto ultra-largo y síntesis documental profunda.",
            pricingInfo = "Tokens (~0,0001 cént. / tok)",
            basePriceUsd = 0.0000015,
            priceUnit = "token",
            parameters = emptyList()
        )
    )

    fun findModel(id: String): NexusModel? {
        return curatedModels.firstOrNull { it.id.equals(id, ignoreCase = true) }
            ?: curatedModels.firstOrNull { it.id.contains(id, ignoreCase = true) }
    }
}
