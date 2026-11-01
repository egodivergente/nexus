package com.vidadomu.nexus.data.repository

import com.vidadomu.nexus.core.model.AttachedMedia
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.NexusModel

/**
 * Selección de vídeo curada por Vidal (2026-11-01). Decide qué rutas de VÍDEO se ofrecen para generaciones nuevas y en qué
 * sección de Herramientas aparecen las herramientas conservadas. No borra nada: el catálogo completo sigue en memoria para
 * historial, borradores y consultas por id. Imagen, audio, chat y proveedores no auditados (Fal, plantillas propias) no se tocan.
 *
 * Las claves son `proveedor:id` exactos publicados por cada API; nunca nombres visibles ni familias. Luma: descartado por el usuario.
 */
object CuratedVideoSelection {
    /** Escape local sin interfaz: `false` en las preferencias del catálogo restaura el catálogo de vídeo completo. */
    const val PREFERENCE_KEY = "curated_video_v1"

    const val ATLAS = "atlas_cloud"
    const val CRUN = "crun"
    const val RUNWAY = "runway"
    const val BYTEPLUS = "usr_byteplus-media"

    /** Solo se filtra el vídeo de los proveedores auditados para esta selección. */
    val curatedProviders = setOf(ATLAS, CRUN, RUNWAY, BYTEPLUS)

    const val MOTION = "Movimiento y pose"
    const val EDIT = "Edición de vídeo"
    const val EXTEND = "Extender vídeo"
    const val AVATARS = "Avatares y personajes"
    const val LIPSYNC = "Sincronización labial"
    const val ENHANCE = "Upscale vídeo"
    const val ADS = "Publicidad y producto"

    /** Generadores conservados: imagen→vídeo, inicio/fin y referencias. Ningún texto→vídeo. */
    val generators: Set<String> = buildSet {
        fun atlas(vararg ids: String) = ids.forEach { add("$ATLAS:$it") }
        fun crun(vararg ids: String) = ids.forEach { add("$CRUN:$it") }
        fun runway(vararg ids: String) = ids.forEach { add("$RUNWAY:$it") }
        fun byteplus(vararg ids: String) = ids.forEach { add("$BYTEPLUS:$it") }
        // Seedance 2.0 Mini, 2.0, 2.0 Fast y 2.5: i2v (con último fotograma) y referencias.
        for (v in listOf("2.0-mini", "2.0", "2.0-fast", "2.5"))
            atlas("bytedance/seedance-$v/image-to-video", "bytedance/seedance-$v/reference-to-video")
        for (v in listOf("2-0-mini", "2-0", "2-0-fast", "2-5"))
            crun("bytedance/seedance$v-i2v", "bytedance/seedance$v-r2v")
        runway("runway/seedance2/i2v", "runway/seedance2_5/i2v", "runway/seedance2_fast/i2v", "runway/seedance2_mini/i2v")
        byteplus("custom/byteplus-media/seedance-2-0-i2v", "custom/byteplus-media/seedance-2-0-fast-i2v",
            "custom/byteplus-media/seedance-2-5-i2v")
        // Kling 4.0, Kling 3.0 Pro y Kling 3.0 Omni (publicado por Atlas como Kling Video O3).
        atlas("kwaivgi/kling-v4.0/image-to-video", "kwaivgi/kling-v4.0/reference-to-video",
            "kwaivgi/kling-v3.0-pro/image-to-video",
            "kwaivgi/kling-video-o3-pro/image-to-video", "kwaivgi/kling-video-o3-pro/reference-to-video",
            "kwaivgi/kling-video-o3-std/image-to-video", "kwaivgi/kling-video-o3-std/reference-to-video",
            "kwaivgi/kling-video-o3-4k/image-to-video")
        crun("kling-v3-pro", "kling/v3-omni-i2v", "kling/v3-omni-r2v")
        // Vidu Q4 Preview (i2v, referencias) y Vidu Q3 Pro (i2v, inicio/fin).
        atlas("vidu/q4-preview/image-to-video", "vidu/q4-preview/reference-to-video",
            "vidu/q3-pro/image-to-video", "vidu/q3-pro/start-end-to-video")
        crun("vidu/q3-pro-i2v")
        // WAN 3.0 original (sin Prime).
        atlas("alibaba/wan-3.0/image-to-video", "alibaba/wan-3.0/reference-to-video")
        crun("wan/3-0-i2v", "wan/3-0-r2v")
        runway("runway/wan3/i2v")
        // MiniMax H3 original (2K nativo) y H3 Developer solo referencias.
        atlas("minimax/h3/image-to-video", "minimax/h3/reference-to-video", "minimax/h3-developer/reference-to-video")
        crun("minimax/h3-i2v", "minimax/h3-r2v")
        runway("runway/hailuo3/i2v")
        // PixVerse V6 y C1 (i2v, inicio/fin, referencias); V6 Max solo i2v.
        for (v in listOf("v6", "c1"))
            atlas("pixverse/$v/image-to-video", "pixverse/$v/start-end-to-video", "pixverse/$v/reference-to-video")
        atlas("pixverse/v6-max/image-to-video")
        crun("pixverse/v6-i2v", "pixverse/v6-r2v")
        // Grok Imagine Video 1.5 original: i2v (sin Developer ni Lite).
        atlas("xai/grok-imagine-video-v1.5/image-to-video")
        runway("runway/grok_imagine_1_5/i2v")
        // LTX 2.5 Pro y Fast: i2v con último fotograma opcional (sin 2.3).
        atlas("ltx/ltx-2.5-pro/image-to-video", "ltx/ltx-2.5-fast/image-to-video")
        // HappyHorse 1.1: solo referencias.
        atlas("alibaba/happyhorse-1.1/reference-to-video")
        crun("happyhorse-1-1-r2v")
        // FLUX 3: i2v e inicio/fin. Keyframes queda fuera: necesita un editor de fotogramas clave que no existe.
        atlas("black-forest-labs/flux-3/image-to-video", "black-forest-labs/flux-3/first-last-frame-to-video")
        // Runway Gen-4.5 i2v, Agnes Video 2.5 y NVIDIA Cosmos 3 Super.
        runway("runway/gen4.5/i2v")
        crun("agnes/video-2-5-i2v", "agnes/video-2-5-r2v")
        atlas("nvidia/cosmos-3-super/image-to-video")
    }

    /** Herramientas conservadas, siempre en Herramientas, con sus controles actuales. */
    val tools: Map<String, String> = buildMap {
        fun tool(provider: String, category: String, vararg ids: String) = ids.forEach { put("$provider:$it", category) }
        tool(ATLAS, MOTION, "kwaivgi/kling-v3.0-std/motion-control", "kwaivgi/kling-v3.0-pro/motion-control",
            "alibaba/wan-2.2/animate-move", "alibaba/wan-2.2/animate-mix")
        tool(CRUN, MOTION, "kling-v3-motion-control-std", "kling-v3-motion-control-pro",
            "wan/2-2-animate-move", "wan/2-2-animate-replace")
        tool(RUNWAY, MOTION, "runway/act_two/performance")
        tool(ATLAS, EDIT, "alibaba/wan-2.7/video-edit", "alibaba/happyhorse-1.0/video-edit", "black-forest-labs/flux-3/edit-video")
        tool(CRUN, EDIT, "wan/2-7-video-edit", "happyhorse-1-0-video-edit", "video-face-swap-multi", "video-watermark-remove")
        tool(RUNWAY, EDIT, "runway/aleph2/v2v")
        tool(ATLAS, EXTEND, "black-forest-labs/flux-3/extend-video", "pixverse/v6/video-extend")
        tool(ATLAS, AVATARS, "ltx/ltx-2.5-pro/audio-to-video", "ltx/ltx-2.5-fast/audio-to-video",
            "bytedance/avatar-omni-human-v2.0", "heygen/avatar-5/digital-twin")
        tool(ATLAS, LIPSYNC, "sync/lipsync-v3")
        tool(ATLAS, ENHANCE, "byteplus/video/upscaler")
        tool(RUNWAY, ENHANCE, "runway/magnific_video_upscaler_creative/upscale-video",
            "runway/enhance_frame_rate/upscale-video", "runway/ruby/hdr")
        // TVC Maker con Food Motion como opción; «Video Clone» publicado por Atlas como trend-remix.
        tool(ATLAS, ADS, "atlascloud/studio/tvc-maker", "atlascloud/studio/food-motion", "atlascloud/studio/virtual-try-on",
            "atlascloud/studio/ugc-ad", "atlascloud/studio/trend-remix")
    }

    val categories: List<String> = listOf(MOTION, EDIT, EXTEND, AVATARS, LIPSYNC, ENHANCE, ADS)

    fun keys(model: NexusModel): Set<String> = buildSet {
        for (id in listOfNotNull(model.id, model.providerModelId)) {
            add("${model.providerId}:$id")
            add("${model.providerId}:${id.removePrefix("crun/")}")
        }
    }

    /** Vídeo de un proveedor auditado; el chat con entrada de vídeo nunca entra en el filtro. */
    fun inScope(model: NexusModel): Boolean =
        model.providerId in curatedProviders && Modality.VIDEO in model.modalities && Modality.CHAT !in model.modalities

    fun isGenerator(model: NexusModel): Boolean = inScope(model) && keys(model).any { it in generators }

    fun toolCategory(model: NexusModel): String? =
        if (!inScope(model)) null else keys(model).firstNotNullOfOrNull { tools[it] }

    fun isAllowed(model: NexusModel): Boolean = !inScope(model) || isGenerator(model) || toolCategory(model) != null

    fun filter(models: List<NexusModel>): List<NexusModel> = models.filter(::isAllowed)

    const val VISUAL_INPUT_MESSAGE = "Añade una imagen o un vídeo de entrada: este modelo no genera solo desde texto."

    /** Ningún generador conservado se envía solo con texto, aunque la API admita el prompt sin imagen (Crun r2v, Kling 3.0 Pro). */
    fun needsVisualInput(model: NexusModel, media: List<AttachedMedia>): Boolean =
        isGenerator(model) && media.none { it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/") }
}
