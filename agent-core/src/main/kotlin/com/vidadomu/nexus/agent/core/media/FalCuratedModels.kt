package com.vidadomu.nexus.agent.core.media

import com.vidadomu.nexus.agent.core.llm.BrainAuth
import com.vidadomu.nexus.agent.core.llm.BrainAuthMode

/** Small editorial selection, checked against all 510 Atlas catalog entries on 2026-10-27.
 * MiniMax has a dated public promotion; Topaz/Recraft were absent. No synthetic prices.
 * Applying the template does not activate a service or generate anything. Existing configs stay intact.
 */
object FalCuratedModels {
    private fun scale() = MediaParam("upscale_factor", "Ampliación", "enum", "2", listOf("1", "2", "4"))
    private fun queued(id: String, name: String, capability: MediaCapability, body: String,
        params: List<MediaParam>, result: String, description: String) = MediaModelConfig(
        id = id, name = name, capability = capability, createPath = "/$id", requestTemplate = body,
        params = params, mode = MediaMode.ASYNC, taskIdPath = "request_id",
        pollPath = "/unused-provider-returned-status", statusPath = "status", errorPath = "detail",
        resultPaths = listOf(result), falQueue = true, description = description,
    )
    private fun miniMax(): MediaModelConfig {
        val slug = "h3-max"
        val label = "MiniMax H3 Max"
        val comparison = "0,416 USD frente a 0,608 USD"
        return queued("minimax/$slug/image-to-video", label, MediaCapability.IMAGE_TO_VIDEO,
            """{"prompt":"{{prompt}}","image_url":"{{image_url}}","duration":"{{duration}}","resolution":"{{resolution}}","prompt_expansion_mode":"{{prompt_expansion_mode}}","seed":"{{seed}}","enable_safety_checker":true}""",
            listOf(MediaParam("duration", "Duración (segundos)", "float", "8", min = 0.92f, max = 15f, required = true),
                MediaParam("resolution", "Resolución", "enum", "768P", listOf("480P", "768P", "1080P"), required = true),
                MediaParam("prompt_expansion_mode", "Expansión del prompt", "enum", "disabled", listOf("disabled", "balanced", "quality"), required = true),
                MediaParam("seed", "Semilla (vacío = aleatoria)", "integer")), "video.url",
            "Imagen inicial a vídeo. Comparación publicada 27/10/2026 para 8 s, 768P: Fal $comparison en Atlas. Promoción hasta 31/10/2026; después hay que revisar tarifas. No es una cotización de cuenta. 1080P usa refinamiento de 768P.")
    }
    fun isMiniMax(id: String) = id == "minimax/h3-max/image-to-video"
    val config = MediaProviderConfig(
        "fal-curated", "Fal · selección", "https://queue.fal.run",
        auth = BrainAuth(BrainAuthMode.HEADER, "Authorization", "Key "),
        models = listOf(
            miniMax(),
            queued("topaz/upscale/video/generative", "Topaz · vídeo", MediaCapability.UPSCALE_VIDEO,
                """{"video_url":"{{video_url}}","model":"{{topaz_model}}","upscale_factor":"{{upscale_factor}}","target_fps":"{{target_fps}}","H264_output":"{{h264}}"}""",
                listOf(MediaParam("topaz_model", "Modelo", "enum", "Starlight Precise 2.6", listOf("Starlight Precise 2.6")),
                    scale().copy(type = "float", min = 1f, max = 4f),
                    MediaParam("target_fps", "FPS de salida (vacío conserva origen)", "integer", min = 16f, max = 60f),
                    MediaParam("h264", "Salida H.264", "boolean", "false")), "video.url",
                "Restauración y ampliación generativa con Starlight. Puede reconstruir detalles. No está en el catálogo Atlas revisado."),
            queued("topaz/upscale/image/generative", "Topaz · imagen", MediaCapability.UPSCALE_IMAGE,
                """{"image_url":"{{image_url}}","model":"Wonder 3.5","upscale_factor":"{{upscale_factor}}","face_enhancement":"{{face_enhancement}}","output_format":"{{output_format}}"}""",
                listOf(scale().copy(type = "float", min = 1f, max = 4f),
                    MediaParam("face_enhancement", "Reconstruir caras", "boolean", "false"),
                    MediaParam("output_format", "Formato", "enum", "png", listOf("png", "jpeg"))), "image.url",
                "Ampliación generativa con Wonder 3.5. Reconstrucción facial desactivada por defecto. No está en el catálogo Atlas revisado."),
            queued("fal-ai/recraft/v4.1/text-to-vector", "Recraft 4.1 · SVG", MediaCapability.TEXT_TO_IMAGE,
                """{"prompt":"{{prompt}}","image_size":"{{image_size}}"}""",
                listOf(MediaParam("image_size", "Formato", "enum", "square_hd", listOf("square_hd", "square", "portrait_4_3", "portrait_16_9", "landscape_4_3", "landscape_16_9"))),
                "images.*.url", "Vectores SVG editables para logos e ilustración. No está en el catálogo Atlas revisado."),
        ),
    )
    val template get() = MediaTemplates.Template("Fal · selección", "MiniMax H3 Max (oferta hasta 31/10/2026), Topaz y Recraft SVG. Introduce tu clave Fal; cada uso es de pago.", config)
}
