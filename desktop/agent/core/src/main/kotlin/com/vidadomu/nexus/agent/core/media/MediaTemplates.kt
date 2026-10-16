package com.vidadomu.nexus.agent.core.media

import com.vidadomu.nexus.agent.core.llm.BrainAuth
import com.vidadomu.nexus.agent.core.llm.BrainAuthMode

/**
 * Puntos de partida para el formulario «Añadir proveedor API». Son ejemplos de APIs REST/JSON sencillas, no un
 * contrato: revisa rutas, nombres de campo y estados con la documentación del servicio. Los protocolos que
 * necesiten firmas, subida en varios pasos o WebSocket no se pueden describir con este formulario.
 */
object MediaTemplates {
    data class Template(val label: String, val note: String, val config: MediaProviderConfig)

    private val ratio = MediaParam("aspect_ratio", "Formato", "aspect_ratio", "16:9", listOf("16:9", "9:16", "1:1", "4:3", "3:4"))
    private val duration = MediaParam("duration", "Duración (s)", "duration", "5", listOf("5", "10"))
    private val resolution = MediaParam("resolution", "Calidad", "resolution", "1080p", listOf("720p", "1080p"))
    private val seed = MediaParam("seed", "Semilla", "seed", "-1", advanced = true)

    val all: List<Template> = listOf(
        Template("Asíncrono genérico", "Crea la tarea, devuelve un id y se consulta hasta terminar.",
            MediaProviderConfig("mi-proveedor", "Mi proveedor", "https://", models = listOf(MediaModelConfig(
                id = "mi-modelo", name = "Mi modelo", capability = MediaCapability.TEXT_TO_VIDEO,
                createPath = "/v1/generate", requestTemplate = """{"model":"{{model}}","prompt":"{{prompt}}","duration":"{{duration}}","aspect_ratio":"{{aspect_ratio}}"}""",
                params = listOf(ratio, duration), mode = MediaMode.ASYNC, taskIdPath = "id", pollPath = "/v1/tasks/{{task_id}}",
                statusPath = "status", progressPath = "progress", errorPath = "error", resultPaths = listOf("output.*", "result.url"))))),
        Template("Síncrono genérico", "La respuesta ya trae la URL del resultado.",
            MediaProviderConfig("mi-proveedor", "Mi proveedor", "https://", models = listOf(MediaModelConfig(
                id = "mi-modelo", name = "Mi modelo", capability = MediaCapability.TEXT_TO_IMAGE,
                createPath = "/v1/images", requestTemplate = """{"model":"{{model}}","prompt":"{{prompt}}","aspect_ratio":"{{aspect_ratio}}","seed":"{{seed}}"}""",
                params = listOf(ratio, seed), mode = MediaMode.SYNC, errorPath = "error.message", resultPaths = listOf("data.*.url"))))),
        Template("Estilo AtlasCloud", "Rutas de la API pública de Atlas. Nexus ya trae Atlas integrado: úsalo solo para modelos que el catálogo no cubra.",
            MediaProviderConfig("atlas-extra", "Atlas (modelos extra)", "https://api.atlascloud.ai", models = listOf(MediaModelConfig(
                id = "mi-modelo", name = "Modelo de Atlas", capability = MediaCapability.TEXT_TO_VIDEO,
                createPath = "/api/v1/model/generateVideo", requestTemplate = """{"model":"{{model}}","prompt":"{{prompt}}","duration":"{{duration}}","aspect_ratio":"{{aspect_ratio}}"}""",
                params = listOf(ratio, duration), mode = MediaMode.ASYNC, taskIdPath = "data.id", pollPath = "/api/v1/model/prediction/{{task_id}}",
                statusPath = "data.status", successValues = listOf("completed", "succeeded", "success"), failureValues = listOf("failed", "error"),
                errorPath = "data.error", resultPaths = listOf("data.outputs"))))),
        Template("Estilo Runway (Dev API)", "Imagen a vídeo con tareas. Revisa el modelo y la versión de la API en la documentación de Runway.",
            MediaProviderConfig("runway-extra", "Runway (extra)", "https://api.dev.runwayml.com", extraHeaders = mapOf("X-Runway-Version" to "2024-11-06"),
                models = listOf(MediaModelConfig(
                    id = "mi-modelo", name = "Modelo de Runway", capability = MediaCapability.IMAGE_TO_VIDEO,
                    createPath = "/v1/image_to_video", requestTemplate = """{"model":"{{model}}","promptImage":"{{image_url}}","promptText":"{{prompt}}","ratio":"{{ratio}}","duration":"{{duration}}"}""",
                    params = listOf(MediaParam("ratio", "Formato", "enum", "1280:720", listOf("1280:720", "720:1280")), duration.copy(options = listOf("5", "10"))),
                    mode = MediaMode.ASYNC, taskIdPath = "id", pollPath = "/v1/tasks/{{task_id}}", statusPath = "status",
                    successValues = listOf("succeeded"), failureValues = listOf("failed", "cancelled"), progressPath = "progress", errorPath = "failure",
                    resultPaths = listOf("output"))))),
        Template("Estilo fal.ai (síncrono)", "Endpoint síncrono con `Authorization: Key …`. Las colas largas de fal no están soportadas.",
            MediaProviderConfig("fal-extra", "fal (síncrono)", "https://fal.run", auth = BrainAuth(BrainAuthMode.HEADER, "Authorization", "Key "), models = listOf(MediaModelConfig(
                id = "fal-ai/flux/schnell", name = "FLUX schnell", capability = MediaCapability.TEXT_TO_IMAGE, createPath = "/fal-ai/flux/schnell",
                requestTemplate = """{"prompt":"{{prompt}}","seed":"{{seed}}"}""", params = listOf(seed), mode = MediaMode.SYNC,
                errorPath = "detail", resultPaths = listOf("images.*.url"))))),
    )
}
