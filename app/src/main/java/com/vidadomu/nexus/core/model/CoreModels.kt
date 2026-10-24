package com.vidadomu.nexus.core.model

import kotlinx.serialization.Serializable

enum class Modality {
    IMAGE,
    VIDEO,
    AUDIO,
    CHAT
}

enum class ModelCapability {
    MOTION_CONTROL,
    TEXT_TO_IMAGE,
    IMAGE_TO_IMAGE,
    IMAGE_EDITING,
    TEXT_TO_VIDEO,
    IMAGE_TO_VIDEO,
    REFERENCE_TO_VIDEO,
    VIDEO_TO_VIDEO,
    VIDEO_EXTEND,
    VIDEO_EDIT,
    AUDIO_TO_VIDEO,
    TEXT_TO_AUDIO,
    TEXT_TO_SPEECH,
    SPEECH_TO_TEXT,
    TEXT_TO_3D,
    IMAGE_TO_3D,
    CHAT_COMPLETION,
    UPSCALE,
    ANIMATE
}

enum class ParameterType {
    TEXT,
    LONG_TEXT,
    BOOLEAN,
    INTEGER,
    FLOAT,
    ENUM,
    SLIDER,
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    SEED,
    ASPECT_RATIO,
    RESOLUTION,
    DURATION,
    // Media-input subtypes (used to build per-model payload fields)
    FIRST_FRAME,   // first_frame_image / start_image / image
    LAST_FRAME,    // last_frame_image / end_image
    REFERENCE_IMAGES, // reference_images[] / reference_image_url
    MASK           // mask_image / inpaint_mask
}

@Serializable
data class ModelParameter(
    val key: String,
    val displayName: String,
    val type: ParameterType,
    val defaultValue: String = "",
    val description: String = "",
    val options: List<String> = emptyList(),
    val minValue: Float? = null,
    val maxValue: Float? = null,
    val step: Float? = null,
    val isAdvanced: Boolean = false,
    val isRequired: Boolean = false,
    val nativeType: String? = null,
    val minExact: String? = null,
    val maxExact: String? = null
)

enum class ModelInputMode {
    TEXT_ONLY,      // 0 attachments: text prompt only
    SINGLE_IMAGE,   // 1 image: Image-to-Image, Image-to-3D
    DUAL_FRAME,     // 2 slots: First frame (required/primary) + Last frame (optional interpolation)
    MULTI_IMAGE,    // 1 to N images (images array with maxImages limit)
    VIDEO_INPUT,    // 1 video to extend/edit
    MULTI_MODAL     // Reference images (up to maxImages) + Reference videos (up to maxVideos)
}

enum class MediaSlotType {
    PRIMARY_IMAGE,      // e.g. "image", "first_frame_image"
    LAST_FRAME,         // e.g. "end_image", "last_image"
    REFERENCE_IMAGE,    // elements for "images" or "reference_images" array
    VIDEO_INPUT,        // e.g. "video", "video_url"
    VIDEO_REFERENCE     // elements for "reference_videos" array
}

@Serializable
data class AttachedMedia(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uriString: String,
    val mimeType: String,
    val slotType: MediaSlotType,
    val fileName: String? = null,
    val remoteUrl: String? = null,
    val fieldKey: String? = null
)

@Serializable
data class NexusModel(
    val id: String,
    val displayName: String,
    val providerId: String,
    /** Exact provider-side identifier; app id may be namespaced for uniqueness. */
    val providerModelId: String? = null,
    val organization: String,
    val modalities: List<Modality>,
    val capabilities: List<ModelCapability>,
    val parameters: List<ModelParameter>,
    val description: String = "",
    val pricingInfo: String = "",
    val badge: String? = null,
    val basePriceUsd: Double? = null,
    val originPriceUsd: Double? = null,  // Price before discount
    val priceUnit: String? = null,
    val priceUnitVerified: Boolean = false,
    val uuid: String? = null,
    val family: String? = null,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val avatarUrl: String? = null,
    val coverUrl: String? = null,
    val schemaUrl: String? = null,
    val chatSamplingParameters: List<String> = emptyList(),
    val nativeRequiredFields: List<String> = emptyList(),
    val discount: String? = null,
    // --- Native input configuration ---
    val inputMode: ModelInputMode = ModelInputMode.TEXT_ONLY,
    val maxImages: Int = 0,
    val maxVideos: Int = 0,
    val firstFrameField: String? = null,       // e.g. "image", "first_frame_image"
    val lastFrameField: String? = null,        // e.g. "end_image", "last_image"
    val imagesArrayField: String? = null,      // e.g. "images", "reference_images"
    val structuredImageReferences: Boolean = false, // e.g. Wan 3: refers [{url,type}]
    val videoField: String? = null,            // e.g. "video", "video_url"
    val videosArrayField: String? = null,      // e.g. "reference_videos"
    val isMediaRequired: Boolean = false,      // true for I2V, I2I, edit; false for T2I, T2V
    val supportsNativeAspectRatio: Boolean = true, // false for I2V where aspect ratio comes from input image
    // --- Input capability flags (backward compatibility & quick checks) ---
    val supportsImageInput: Boolean = false,
    val supportsFirstFrame: Boolean = false,   // accepts image for start frame
    val supportsLastFrame: Boolean = false,    // accepts image for end frame
    val supportsReferenceImages: Boolean = false, // accepts reference image(s)
    val supportsVideoInput: Boolean = false,   // accepts video as input
    // inputImageField: the actual API payload field name for the primary image input
    // e.g. "image", "first_frame_image", "input_image", "reference_image_url"
    val inputImageField: String? = null,
    val supportsAudio: Boolean = false,
    val supportedAspectRatios: List<String> = emptyList(),
    val supportedResolutions: List<String> = emptyList(),
    val supportedDurations: List<String> = emptyList(),
    // --- Escaparate & Visual Catalog fields ---
    val shortDescription: String = "",       // Una frase orientada a uso: "Ideal para...", "El más rápido para..."
    val speedQualityTag: String = "",        // "Ultrarrápido", "Alta Calidad", "Equilibrado", "Económico"
    val popularityScore: Int = 50,           // Métrica de popularidad / relevancia (1..100)
    val releaseDateMillis: Long = 0L,        // Timestamp de alta / actualización (para calcular "Novedades")
    val pairedEditModelId: String? = null,   // Modelo hermano de edición (para conmutar sin fricción al adjuntar)
    val isFlagship: Boolean = false,         // Prioridad visual destacada (ByteDance, Kling, MiniMax, Wan, GPT)
    // --- Model Hierarchy & Semantic Versioning fields ---
    val versionNumeric: Double = 1.0,        // Extracted numerical version: 5.0, 4.7, 3.0, 2.5, 1.0
    val modelFamily: String = "",            // Canonical model family: "Qwen Image", "Seedream", "Kling", "Wan"
    val variantRank: Int = 3,                // Pro/Prime/Max (4) > Std (3) > Turbo/Plus (2) > Lite/Fast (1)
    val isPrimaryGeneration: Boolean = true, // Text-to-Image / Text-to-Video generation before auxiliary edits
    val providerPriority: Int = 50,
    val contractEndpoint: String? = null,
    val atlasEndpoint: String? = null,
    val contractSource: String? = null,
    val contractCheckedAt: Long? = null,
    val catalogCheckedAt: Long? = null,
    val contractMedia: List<ContractMediaField> = emptyList(),
    val contractDefaults: Map<String,String> = emptyMap(),
    val promptRequired: Boolean = true,
    /** Longitud máxima del prompt según el contrato del proveedor (null = desconocida). */
    val promptMaxLength: Int? = null,
    /** false si el schema no tiene campo prompt (upscalers, herramientas): no se envía texto libre. */
    val promptAccepted: Boolean = true,
    val contractPromptField:String = "promptText"
) {
    val canonicalFamily: String
        get() = if (modelFamily.isNotBlank()) modelFamily else organization

    companion object {
        /**
         * Authoritative Comparator:
         * 1. Top Provider/Family Priority DESC (Qwen, Seedream, Kling, Wan, etc. first)
         * 2. Canonical Family Grouping ASC (Keeps all versions of the same model together)
         * 3. Numerical Version DESC (Most recent to oldest within model: 5.0 > 4.7 > 4.5 > 4.0; 3.0 > 2.5 > 2.0)
         * 4. Primary Generation DESC (Text-to-Image / Text-to-Video before Edit/Decomposition)
         * 5. Variant Rank DESC (Pro/Prime > Standard > Turbo > Lite)
         * 6. Display Name ASC (Stable tie-breaker)
         */
        val DEFAULT_COMPARATOR: Comparator<NexusModel> = Comparator { a, b ->
            val prioComp = b.providerPriority.compareTo(a.providerPriority)
            if (prioComp != 0) return@Comparator prioComp

            val famComp = a.canonicalFamily.compareTo(b.canonicalFamily, ignoreCase = true)
            if (famComp != 0) return@Comparator famComp

            val verComp = b.versionNumeric.compareTo(a.versionNumeric)
            if (verComp != 0) return@Comparator verComp

            val genA = if (a.isPrimaryGeneration) 1 else 0
            val genB = if (b.isPrimaryGeneration) 1 else 0
            val genComp = genB.compareTo(genA)
            if (genComp != 0) return@Comparator genComp

            val rankComp = b.variantRank.compareTo(a.variantRank)
            if (rankComp != 0) return@Comparator rankComp

            a.displayName.compareTo(b.displayName, ignoreCase = true)
        }
    }
}

@Serializable
data class MediaInput(
    val type: Modality,
    val uriString: String,
    val mimeType: String,
    val fileName: String? = null,
    val remoteUrl: String? = null,
    val slotType: MediaSlotType = MediaSlotType.PRIMARY_IMAGE,
    val fieldKey: String? = null
)

@Serializable
data class GenerationRequest(
    val id: String,
    val providerId: String,
    val modelId: String,
    val modality: Modality,
    val prompt: String,
    val negativePrompt: String = "",
    val parameters: Map<String, String> = emptyMap(),
    val mediaInputs: List<MediaInput> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

enum class GenerationStatus {
    UPLOADING,
    QUEUED,
    GENERATING,
    PROCESSING,
    RENDERING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Serializable
data class GenerationOutput(
    val id: String,
    val jobId: String,
    val type: Modality,
    val url: String,
    val localUri: String? = null,
    val mimeType: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Float? = null
)

@Serializable
data class GenerationJob(
    val id: String,
    val providerJobId: String? = null,
    val providerId: String,
    val modelId: String,
    val modality: Modality,
    val prompt: String,
    val negativePrompt: String = "",
    val parameters: Map<String, String> = emptyMap(),
    val inputMediaUri: String? = null,
    val status: GenerationStatus = GenerationStatus.QUEUED,
    val progress: Float? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val error: String? = null,
    val technicalDetails: String? = null,
    val actualCostUsd: Double? = null,
    val estimatedCostUsd: Double? = null,
    val outputs: List<GenerationOutput> = emptyList()
)

@Serializable
data class AccountBalance(
    val availableUsd: Double,
    val cashUsd: Double,
    val bonusUsd: Double = 0.0,
    val frozenUsd: Double = 0.0,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class ProviderError(
    val humanReadableMessage: String,
    val technicalDetails: String? = null,
    val httpStatusCode: Int? = null,
    val errorCode: String? = null
) : Exception(humanReadableMessage)

@Serializable
data class ContractMediaField(val key:String,val label:String,val mime:String,val required:Boolean=false,
    val maximum:Int=1,val encoding:String="string",val fixedType:String?=null)
