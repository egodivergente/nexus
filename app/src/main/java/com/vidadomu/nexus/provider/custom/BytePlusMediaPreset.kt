package com.vidadomu.nexus.provider.custom

import com.vidadomu.nexus.agent.core.llm.BrainAuth
import com.vidadomu.nexus.agent.core.llm.BrainAuthMode
import com.vidadomu.nexus.agent.core.llm.BrainPresets
import com.vidadomu.nexus.agent.core.media.MediaCapability
import com.vidadomu.nexus.agent.core.media.MediaMode
import com.vidadomu.nexus.agent.core.media.MediaModelConfig
import com.vidadomu.nexus.agent.core.media.MediaParam
import com.vidadomu.nexus.agent.core.media.MediaProviderConfig

/**
 * BytePlus ModelArk como proveedor de medios: Seedance (vídeo, tareas asíncronas) y Seedream (imagen, síncrono).
 * Usa la misma cuenta y la misma clave que el cerebro BytePlus; si el proveedor tiene clave propia, manda la suya.
 * Los IDs de modelo cambian con cada versión: si BytePlus responde «model not found», cámbialos en Ajustes → Proveedores
 * por el ID que muestra la consola de ModelArk (o por un endpoint `ep-…`).
 */
object BytePlusMediaPreset {
    const val ID = "byteplus-media"

    private val ratio = MediaParam("ratio", "Formato", "enum", "16:9", listOf("16:9", "9:16", "1:1", "4:3", "3:4", "21:9", "adaptive"))
    private val resolution = MediaParam("resolution", "Calidad", "enum", "1080p", listOf("480p", "720p", "1080p"))
    private val duration = MediaParam("duration", "Duración (s)", "integer", "5", min = 3f, max = 15f)
    private val audio = MediaParam("generate_audio", "Con audio", "boolean", "true")
    private val size = MediaParam("size", "Tamaño", "enum", "2K", listOf("1K", "2K", "4K"))
    private val watermark = MediaParam("watermark", "Marca de agua", "boolean", "false", advanced = true)

    private fun seedance(id: String, name: String, modelId: String, withImage: Boolean) = MediaModelConfig(
        id = id, name = name, providerModelId = modelId,
        capability = if (withImage) MediaCapability.IMAGE_TO_VIDEO else MediaCapability.TEXT_TO_VIDEO,
        createPath = "/contents/generations/tasks",
        requestTemplate = if (withImage)
            """{"model":"{{model}}","content":[{"type":"text","text":"{{prompt}}"},{"type":"image_url","image_url":{"url":"{{image_url}}"}}],"ratio":"{{ratio}}","resolution":"{{resolution}}","duration":"{{duration}}","generate_audio":"{{generate_audio}}","watermark":"{{watermark}}"}"""
        else
            """{"model":"{{model}}","content":[{"type":"text","text":"{{prompt}}"}],"ratio":"{{ratio}}","resolution":"{{resolution}}","duration":"{{duration}}","generate_audio":"{{generate_audio}}","watermark":"{{watermark}}"}""",
        params = listOf(ratio, resolution, duration, audio, watermark), mode = MediaMode.ASYNC,
        taskIdPath = "id", pollPath = "/contents/generations/tasks/{{task_id}}", statusPath = "status",
        successValues = listOf("succeeded"), failureValues = listOf("failed", "expired", "cancelled"),
        errorPath = "error.message", resultPaths = listOf("content.video_url"),
        description = "BytePlus ModelArk · cobra de tu saldo de BytePlus.")

    private fun seedream(id: String, name: String, modelId: String, edit: Boolean) = MediaModelConfig(
        id = id, name = name, providerModelId = modelId,
        capability = if (edit) MediaCapability.IMAGE_TO_IMAGE else MediaCapability.TEXT_TO_IMAGE,
        createPath = "/images/generations",
        requestTemplate = if (edit)
            """{"model":"{{model}}","prompt":"{{prompt}}","image":"{{image_urls}}","size":"{{size}}","response_format":"url","watermark":"{{watermark}}"}"""
        else
            """{"model":"{{model}}","prompt":"{{prompt}}","size":"{{size}}","response_format":"url","watermark":"{{watermark}}"}""",
        params = listOf(size, watermark), mode = MediaMode.SYNC, errorPath = "error.message",
        resultPaths = listOf("data.*.url"), maxImages = if (edit) 10 else 1,
        description = "BytePlus ModelArk · cobra de tu saldo de BytePlus.")

    fun config() = MediaProviderConfig(
        id = ID, name = "BytePlus", baseUrl = BrainPresets.BYTEPLUS_BASE_URL,
        auth = BrainAuth(BrainAuthMode.HEADER, "Authorization", "Bearer "),
        pollIntervalSeconds = 5, maxWaitMinutes = 30, httpTimeoutSeconds = 120,
        models = listOf(
            seedance("seedance-2-0", "Seedance 2.0", "dreamina-seedance-2-0-260128", false),
            seedance("seedance-2-0-i2v", "Seedance 2.0 (desde imagen)", "dreamina-seedance-2-0-260128", true),
            seedance("seedance-2-0-fast", "Seedance 2.0 Fast", "dreamina-seedance-2-0-fast-260128", false),
            seedance("seedance-2-0-fast-i2v", "Seedance 2.0 Fast (desde imagen)", "dreamina-seedance-2-0-fast-260128", true),
            seedream("seedream-5-0-pro", "Seedream 5.0 Pro", "dola-seedream-5-0-pro-260628", false),
            seedream("seedream-5-0-pro-edit", "Seedream 5.0 Pro Edit", "dola-seedream-5-0-pro-260628", true),
        ))
}
