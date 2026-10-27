package com.vidadomu.nexus.agent.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MediaEngineTest {
    private fun json(s: String) = Json.parseToJsonElement(s)
    private val atlas = MediaTemplates.all.first { it.label == "Estilo AtlasCloud" }.config
    private val atlasModel = atlas.models.single()

    @Test fun `la peticion rellena la plantilla con tipos correctos y omite lo vacio`() {
        val call = MediaEngine.buildCreate(atlas, atlasModel, MediaInput("un gato", mapOf("duration" to "10", "aspect_ratio" to "9:16")))
        assertEquals("POST", call.method)
        assertEquals("https://api.atlascloud.ai/api/v1/model/generateVideo", call.url)
        val b = call.body!!
        assertEquals("mi-modelo", b["model"]!!.jsonPrimitive.content)
        assertEquals("un gato", b["prompt"]!!.jsonPrimitive.content)
        assertFalse(b["duration"]!!.jsonPrimitive.isString, "la duración viaja como número")
        assertEquals(10, b["duration"]!!.jsonPrimitive.content.toInt())
        assertEquals("9:16", b["aspect_ratio"]!!.jsonPrimitive.content)
    }

    @Test fun `la semilla -1 y los vacios no se envian, y los booleanos y decimales conservan su tipo`() {
        val m = atlasModel.copy(requestTemplate = """{"prompt":"{{prompt}}","seed":"{{seed}}","audio":"{{audio}}","cfg":"{{cfg}}"}""",
            params = listOf(MediaParam("seed", type = "seed", default = "-1"), MediaParam("audio", type = "boolean", default = "true"), MediaParam("cfg", type = "float", default = "")))
        val b = MediaEngine.buildCreate(atlas, m, MediaInput("x", mapOf("cfg" to "7,5"))).body!!
        assertNull(b["seed"]); assertEquals("true", b["audio"]!!.jsonPrimitive.content); assertFalse(b["audio"]!!.jsonPrimitive.isString)
        assertEquals(7.5, b["cfg"]!!.jsonPrimitive.content.toDouble())
        assertEquals(42, MediaEngine.buildCreate(atlas, m, MediaInput("x", mapOf("seed" to "42"))).body!!["seed"]!!.jsonPrimitive.content.toInt())
    }

    @Test fun `flujo asincrono completo - crear, en curso con progreso, terminado con URLs`() {
        val created = MediaEngine.parseCreate(atlasModel, json("""{"code":200,"data":{"id":"pred_77"}}"""))
        assertEquals(CreateOutcome.Async("pred_77"), created)
        val poll = MediaEngine.buildPoll(atlas, atlasModel, "pred_77")
        assertEquals("GET", poll.method); assertEquals("https://api.atlascloud.ai/api/v1/model/prediction/pred_77", poll.url)
        val m = atlasModel.copy(progressPath = "data.progress")
        assertEquals(PollOutcome.Running(0.4f), MediaEngine.parsePoll(m, json("""{"data":{"status":"processing","progress":40}}""")))
        assertEquals(PollOutcome.Running(null), MediaEngine.parsePoll(atlasModel, json("""{"data":{"status":"queued"}}""")))
        assertEquals(PollOutcome.Done(listOf("https://cdn.x/v1.mp4", "https://cdn.x/v2.mp4")),
            MediaEngine.parsePoll(atlasModel, json("""{"data":{"status":"Completed","outputs":["https://cdn.x/v1.mp4","https://cdn.x/v2.mp4"]}}""")))
    }

    @Test fun `un fallo del proveedor llega con su mensaje real y no se da por completado`() {
        val failed = MediaEngine.parsePoll(atlasModel, json("""{"data":{"status":"failed","error":"video demasiado largo"}}"""))
        assertEquals(PollOutcome.Failed("video demasiado largo"), failed)
        assertTrue((MediaEngine.parsePoll(atlasModel, json("""{"data":{"status":"failed"}}""")) as PollOutcome.Failed).message.contains("fallo"))
        // «completado» sin URL = error explicado, nunca éxito inventado
        val empty = MediaEngine.parsePoll(atlasModel, json("""{"data":{"status":"completed","outputs":[]}}"""))
        assertTrue(empty is PollOutcome.Failed && "data.outputs" in empty.message, empty.toString())
        // cosas que no son URL no cuentan como resultado
        assertTrue(MediaEngine.parsePoll(atlasModel, json("""{"data":{"status":"completed","outputs":["no-es-url"]}}""")) is PollOutcome.Failed)
    }

    @Test fun `errores al crear - mensaje del proveedor, id ausente y resultado inmediato`() {
        assertEquals(CreateOutcome.Failed("saldo insuficiente"), MediaEngine.parseCreate(atlasModel.copy(errorPath = "error.message"), json("""{"error":{"message":"saldo insuficiente"}}""")))
        val noId = MediaEngine.parseCreate(atlasModel, json("""{"data":{}}"""))
        assertTrue(noId is CreateOutcome.Failed && "data.id" in noId.message, noId.toString())
        // API asíncrona que ya devuelve el resultado
        assertEquals(CreateOutcome.Done(listOf("https://cdn.x/a.png")),
            MediaEngine.parseCreate(atlasModel.copy(taskIdPath = "id", resultPaths = listOf("data.outputs")), json("""{"data":{"outputs":["https://cdn.x/a.png"]}}""")))
    }

    @Test fun `flujo sincrono con comodin y autenticacion con prefijo (estilo fal)`() {
        // Previously saved synchronous Fal configs remain supported, even though the picker is curated.
        val fal = MediaProviderConfig("fal-extra", "fal", "https://fal.run", models = listOf(MediaModelConfig(
            id = "fal-ai/flux/schnell", name = "FLUX schnell", capability = MediaCapability.TEXT_TO_IMAGE,
            createPath = "/fal-ai/flux/schnell", requestTemplate = """{"prompt":"{{prompt}}"}""",
            mode = MediaMode.SYNC, errorPath = "detail", resultPaths = listOf("images.*.url"))))
        val m = fal.models.single()
        val call = MediaEngine.buildCreate(fal, m, MediaInput("un faro"))
        assertEquals("https://fal.run/fal-ai/flux/schnell", call.url)
        assertEquals("fal-ai/flux/schnell", m.modelValue)
        assertEquals(CreateOutcome.Done(listOf("https://v3.fal.media/1.png", "https://v3.fal.media/2.png")),
            MediaEngine.parseCreate(m, json("""{"images":[{"url":"https://v3.fal.media/1.png"},{"url":"https://v3.fal.media/2.png"}]}""")))
        val failed = MediaEngine.parseCreate(m, json("""{"detail":"Unauthorized"}"""))
        assertEquals(CreateOutcome.Failed("Unauthorized"), failed)
    }

    @Test fun `antes de enviar se comprueba lo que falta - imagen, video, obligatorios y opciones`() {
        val i2v = atlasModel.copy(capability = MediaCapability.IMAGE_TO_VIDEO, maxImages = 1,
            params = listOf(MediaParam("quality", "Calidad", "enum", "hd", listOf("hd", "sd"), required = true)))
        assertTrue(MediaEngine.inputProblems(i2v, MediaInput("x")).any { "imagen" in it })
        assertTrue(MediaEngine.inputProblems(i2v, MediaInput("x", imageUrls = listOf("https://a/1.png", "https://a/2.png"))).any { "como máximo 1" in it })
        assertTrue(MediaEngine.inputProblems(i2v, MediaInput("x", mapOf("quality" to "ultra"), listOf("https://a/1.png"))).any { "debe ser una de" in it })
        assertTrue(MediaEngine.inputProblems(i2v, MediaInput("x", mapOf("quality" to "sd"), listOf("https://a/1.png"))).isEmpty())
        val up = atlasModel.copy(capability = MediaCapability.UPSCALE_VIDEO)
        assertTrue(MediaEngine.inputProblems(up, MediaInput("")).any { "vídeo" in it })
        assertTrue(MediaEngine.inputProblems(up, MediaInput("", videoUrl = "https://a/v.mp4")).isEmpty())
        assertTrue(MediaEngine.inputProblems(atlasModel, MediaInput(" ")).any { "descripción" in it })
    }

    @Test fun `adjuntos - la imagen y la lista llegan a la plantilla`() {
        val m = atlasModel.copy(capability = MediaCapability.IMAGE_TO_VIDEO, maxImages = 3,
            requestTemplate = """{"model":"{{model}}","image":"{{image_url}}","refs":"{{image_urls}}","video":"{{video_url}}"}""", params = emptyList())
        val b = MediaEngine.buildCreate(atlas, m, MediaInput("p", imageUrls = listOf("https://a/1.png", "https://a/2.png"))).body!!
        assertEquals("https://a/1.png", b["image"]!!.jsonPrimitive.content)
        assertEquals(2, (b["refs"] as kotlinx.serialization.json.JsonArray).size)
        assertNull(b["video"])
    }

    @Test fun `todas las plantillas describen una configuracion coherente que solo pide URL base`() {
        MediaTemplates.all.forEach { t ->
            val filled = t.config.copy(baseUrl = if (t.config.baseUrl == "https://") "https://api.ejemplo.com" else t.config.baseUrl)
            assertTrue(filled.problems().isEmpty(), "${t.label}: ${filled.problems()}")
            assertTrue(t.config.problems().isNotEmpty() == (t.config.baseUrl == "https://"), "${t.label}: la plantilla genérica obliga a poner la URL")
        }
    }

    @Test fun `la validacion explica marcadores huerfanos, rutas que faltan y duplicados`() {
        fun problems(f: (MediaModelConfig) -> MediaModelConfig) = atlas.copy(models = listOf(f(atlasModel))).problems()
        assertTrue(problems { it.copy(requestTemplate = """{"p":"{{prompt}}","x":"{{inventado}}"}""") }.any { "{{inventado}}" in it })
        assertTrue(problems { it.copy(requestTemplate = "no json") }.any { "JSON" in it })
        assertTrue(problems { it.copy(taskIdPath = "") }.any { "id de la tarea" in it })
        assertTrue(problems { it.copy(resultPaths = emptyList()) }.any { "URL del resultado" in it })
        assertTrue(problems { it.copy(successValues = emptyList()) }.any { "éxito" in it })
        assertTrue(atlas.copy(models = listOf(atlasModel, atlasModel)).problems().any { "mismo identificador" in it })
        assertTrue(atlas.copy(baseUrl = "http://api.x.com").problems().any { "https" in it })
        assertTrue(atlas.copy(models = emptyList()).problems().any { "al menos un modelo" in it })
        assertTrue(atlas.copy(pollIntervalSeconds = -1).problems().isNotEmpty())
        // 0 = sin límite es válido en todos los límites del proveedor
        assertTrue(atlas.copy(pollIntervalSeconds = 0, maxWaitMinutes = 0, httpTimeoutSeconds = 0, maxUploadMb = 0).problems().isEmpty())
    }

    @Test fun `la configuracion se guarda y se recupera tal cual y no contiene la clave`() {
        val text = Json.encodeToString(atlas)
        assertEquals(atlas, MediaProviderConfig.parse(text))
        assertFalse(text.contains("apiKey", true))
        assertEquals("atlas-extra", Json.parseToJsonElement(text).jsonObject["id"]!!.jsonPrimitive.content)
    }
}
