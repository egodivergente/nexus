package com.vidadomu.nexus.provider.custom

import com.vidadomu.nexus.agent.core.llm.BrainAuth
import com.vidadomu.nexus.agent.core.llm.BrainAuthMode
import com.vidadomu.nexus.agent.core.media.MediaCapability
import com.vidadomu.nexus.agent.core.media.MediaMode
import com.vidadomu.nexus.agent.core.media.MediaModelConfig
import com.vidadomu.nexus.agent.core.media.MediaParam
import com.vidadomu.nexus.agent.core.media.MediaProviderConfig
import com.vidadomu.nexus.core.model.GenerationRequest
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.MediaInput
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.ModelInputMode
import com.vidadomu.nexus.core.model.ProviderError
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Generación multimedia con un servidor simulado: no hace falta ninguna credencial ni cuesta nada. */
class ConfigurableMediaProviderTest {
    private val dur = MediaParam("duration", "Duración", "duration", "5", listOf("5", "10"))
    private fun asyncModel() = MediaModelConfig(
        id = "vid-1", name = "Vídeo 1", capability = MediaCapability.IMAGE_TO_VIDEO, createPath = "/v1/generate",
        requestTemplate = """{"model":"{{model}}","prompt":"{{prompt}}","image":"{{image_url}}","duration":"{{duration}}"}""", params = listOf(dur),
        mode = MediaMode.ASYNC, taskIdPath = "id", pollPath = "/v1/tasks/{{task_id}}", statusPath = "status", progressPath = "progress",
        errorPath = "error", resultPaths = listOf("output"), priceUsd = 0.5, priceUnit = "clip")
    private fun syncModel() = MediaModelConfig(
        id = "img-1", name = "Imagen 1", capability = MediaCapability.TEXT_TO_IMAGE, createPath = "/v1/images",
        requestTemplate = """{"prompt":"{{prompt}}"}""", mode = MediaMode.SYNC, errorPath = "error", resultPaths = listOf("data.*.url"))

    private fun provider(server: MockWebServer, vararg models: MediaModelConfig, key: String = "k-secreta", tweak: (MediaProviderConfig) -> MediaProviderConfig = { it }) =
        ConfigurableMediaProvider(tweak(MediaProviderConfig("prueba", "Prueba", server.url("/").toString().trimEnd('/'), models = models.toList())),
            OkHttpClient(), { key }, File(System.getProperty("java.io.tmpdir"), "nexus-test-out"))

    private fun request(model: String, vararg media: MediaInput, params: Map<String, String> = emptyMap(), prompt: String = "un faro") =
        GenerationRequest("job_1", "usr_prueba", model, if (model.contains("vid")) Modality.VIDEO else Modality.IMAGE, prompt, parameters = params, mediaInputs = media.toList())
    private val photo = MediaInput(Modality.IMAGE, "file:///a.png", "image/png", remoteUrl = "https://cdn.test/a.png")

    @Test fun `los modelos configurados llegan al catalogo con su capacidad, adjuntos y parametros`() = runBlocking {
        MockWebServer().use { server ->
            val models = provider(server, asyncModel(), syncModel()).getModels()
            assertEquals(listOf("custom/prueba/vid-1", "custom/prueba/img-1"), models.map { it.id })
            val v = models[0]
            assertEquals("usr_prueba", v.providerId)
            assertEquals(listOf(Modality.VIDEO), v.modalities)
            assertEquals(ModelInputMode.SINGLE_IMAGE, v.inputMode)
            assertTrue(v.isMediaRequired && v.supportsFirstFrame)
            assertEquals("duration", v.parameters.single().key)
            assertEquals(listOf("5", "10"), v.supportedDurations)
            assertEquals(0.5, v.basePriceUsd!!, 0.0)
            assertEquals(ModelInputMode.TEXT_ONLY, models[1].inputMode)
            assertFalse(models[1].isMediaRequired)
        }
    }

    @Test fun `flujo asincrono - crea, consulta en curso y termina con la URL real, con la clave solo en la cabecera`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"id":"task_9"}"""))
            server.enqueue(MockResponse().setBody("""{"status":"RUNNING","progress":40}"""))
            server.enqueue(MockResponse().setBody("""{"status":"SUCCEEDED","output":["https://cdn.test/out.mp4"]}"""))
            val p = provider(server, asyncModel())
            val created = p.submitGeneration(request("custom/prueba/vid-1", photo, params = mapOf("duration" to "10", "__native_schema" to "true"))).getOrThrow()
            assertEquals("task_9", created.providerJobId)
            assertEquals(GenerationStatus.GENERATING, created.status)
            assertEquals(0.5, created.estimatedCostUsd!!, 0.0)
            val sent = server.takeRequest()
            assertEquals("/v1/generate", sent.path)
            assertEquals("Bearer k-secreta", sent.getHeader("Authorization"))
            val body = Json.parseToJsonElement(sent.body.readUtf8()).jsonObject
            assertEquals("vid-1", body["model"]!!.jsonPrimitive.content)
            assertEquals("https://cdn.test/a.png", body["image"]!!.jsonPrimitive.content)
            assertFalse("los metadatos locales __ no viajan al proveedor", body.keys.any { it.startsWith("__") })
            assertEquals(10, body["duration"]!!.jsonPrimitive.content.toInt())

            val running = p.pollJobStatus(created).getOrThrow()
            assertEquals(GenerationStatus.GENERATING, running.status); assertEquals(0.4f, running.progress!!, 0.001f)
            assertEquals("/v1/tasks/task_9", server.takeRequest().also { assertNull(it.body.readUtf8().takeIf { b -> b.isNotEmpty() }) }.path)
            val done = p.pollJobStatus(running).getOrThrow()
            assertEquals(GenerationStatus.COMPLETED, done.status)
            assertEquals("https://cdn.test/out.mp4", done.outputs.single().url)
            assertEquals("video/mp4", done.outputs.single().mimeType)
            assertNotNull(done.completedAt)
        }
    }

    @Test fun `un fallo del proveedor llega con su mensaje y nunca se marca como completado`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"id":"t1"}"""))
            server.enqueue(MockResponse().setBody("""{"status":"failed","error":"vídeo demasiado largo"}"""))
            val p = provider(server, asyncModel())
            val job = p.submitGeneration(request("custom/prueba/vid-1", photo)).getOrThrow()
            val failed = p.pollJobStatus(job).getOrThrow()
            assertEquals(GenerationStatus.FAILED, failed.status)
            assertEquals("vídeo demasiado largo", failed.error)
            assertTrue(failed.outputs.isEmpty())
        }
    }

    @Test fun `un resultado inmediato (sincrono) tambien termina por el sondeo y asi se descarga y archiva igual`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[{"url":"https://cdn.test/1.png"},{"url":"https://cdn.test/2.png"}]}"""))
            val p = provider(server, syncModel())
            val job = p.submitGeneration(request("custom/prueba/img-1")).getOrThrow()
            assertEquals(GenerationStatus.GENERATING, job.status)
            assertEquals(2, job.outputs.size)
            assertEquals(1, server.requestCount)
            val done = p.pollJobStatus(job).getOrThrow()
            assertEquals(GenerationStatus.COMPLETED, done.status)
            assertEquals("completar un resultado inmediato no vuelve a llamar al proveedor", 1, server.requestCount)
        }
    }

    @Test fun `un resultado base64 se guarda como archivo local en vez de pasar una URL data`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[{"url":"data:image/png;base64,iVBORw0KGgo="}]}"""))
            val job = provider(server, syncModel()).submitGeneration(request("custom/prueba/img-1")).getOrThrow()
            val url = job.outputs.single().url
            assertTrue(url, url.startsWith("file:"))
            assertTrue(File(java.net.URI(url)).isFile)
        }
    }

    @Test fun `lo que falta se avisa antes de enviar nada y no se llama al proveedor`() = runBlocking {
        MockWebServer().use { server ->
            val p = provider(server, asyncModel(), syncModel())
            val noImage = p.submitGeneration(request("custom/prueba/vid-1")).exceptionOrNull()
            assertTrue(noImage is IllegalArgumentException && "imagen" in noImage.message.orEmpty())
            val noPrompt = p.submitGeneration(request("custom/prueba/img-1", prompt = " ")).exceptionOrNull()
            assertTrue(noPrompt is IllegalArgumentException && "descripción" in noPrompt.message.orEmpty())
            assertTrue(p.submitGeneration(request("custom/otro/x")).isFailure)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun `errores HTTP, cuerpo no JSON y clave rechazada se traducen sin exponer la clave`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"bad key k-secreta"}"""))
            server.enqueue(MockResponse().setBody("<html>no json</html>"))
            val p = provider(server, syncModel())
            val unauthorized = p.submitGeneration(request("custom/prueba/img-1")).exceptionOrNull() as ProviderError
            assertEquals(401, unauthorized.httpStatusCode)
            val notJson = p.submitGeneration(request("custom/prueba/img-1")).exceptionOrNull() as ProviderError
            assertTrue("JSON" in notJson.humanReadableMessage)
        }
    }

    @Test fun `la espera maxima del proveedor es ajuste del usuario - 0 la desactiva y N pausa con aviso de recuperar`() = runBlocking {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody("""{"status":"RUNNING"}""")) }
            val old = com.vidadomu.nexus.core.model.GenerationJob("j", "t", "usr_prueba", "custom/prueba/vid-1", Modality.VIDEO, "x", startedAt = System.currentTimeMillis() - 90 * 60_000L)
            val limited = provider(server, asyncModel(), tweak = { it.copy(maxWaitMinutes = 60) }).pollJobStatus(old).getOrThrow()
            assertEquals(GenerationStatus.FAILED, limited.status)
            assertEquals("true", limited.parameters["__tracking_paused"])
            assertTrue("Recuperar" in limited.error.orEmpty())
            val unlimited = provider(server, asyncModel(), tweak = { it.copy(maxWaitMinutes = 0) }).pollJobStatus(old).getOrThrow()
            assertEquals(GenerationStatus.GENERATING, unlimited.status)
        }
    }

    @Test fun `autenticacion por cabecera propia con prefijo y por parametro`() = runBlocking {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody("""{"data":[{"url":"https://cdn.test/1.png"}]}""")) }
            provider(server, syncModel(), key = "K1") { it.copy(auth = BrainAuth(BrainAuthMode.HEADER, "Authorization", "Key ")) }.submitGeneration(request("custom/prueba/img-1"))
            server.takeRequest().let { assertEquals("Key K1", it.getHeader("Authorization")) }
            provider(server, syncModel(), key = "K2") { it.copy(auth = BrainAuth(BrainAuthMode.QUERY, "api_key")) }.submitGeneration(request("custom/prueba/img-1"))
            server.takeRequest().let { assertTrue(it.path!!.endsWith("?api_key=K2")); assertNull(it.getHeader("Authorization")) }
        }
    }

    @Test fun `los adjuntos se incrustan como data URI y respetan el limite de tamano del usuario (0 = sin limite)`() = runBlocking {
        MockWebServer().use { server ->
            val file = File.createTempFile("adj", ".png").apply { writeBytes(ByteArray(2 * 1024 * 1024)); deleteOnExit() }
            val ok = provider(server, syncModel(), tweak = { it.copy(maxUploadMb = 5) }).uploadFile(file, "a.png", "image/png").getOrThrow()
            assertTrue(ok.startsWith("data:image/png;base64,"))
            val tooBig = provider(server, syncModel(), tweak = { it.copy(maxUploadMb = 1) }).uploadFile(file, "a.png", "image/png").exceptionOrNull()
            assertTrue(tooBig is IllegalArgumentException && "1 MB" in tooBig.message.orEmpty())
            assertTrue(provider(server, syncModel(), tweak = { it.copy(maxUploadMb = 0) }).uploadFile(file, "a.png", "image/png").isSuccess)
        }
    }

    @Test fun `un proveedor sin ruta de comprobacion lo dice en vez de fingir exito`() = runBlocking {
        MockWebServer().use { server ->
            assertTrue(provider(server, syncModel()).testConnection("k").isFailure)
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            assertEquals(true, provider(server, syncModel()) { it.copy(healthPath = "/account") }.testConnection("k").getOrNull())
            assertEquals("/account", server.takeRequest().path)
        }
    }
}
