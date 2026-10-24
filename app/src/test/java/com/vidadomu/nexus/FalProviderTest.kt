package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.fal.*
import com.vidadomu.nexus.ui.models.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class FalProviderTest {
    private val fixture get() = javaClass.getResource("/fal/birefnet-v2.json")!!.readText()
    private val model get() = FalContract.decode(fixture, 12345)
    private fun request(params: Map<String,String> = emptyMap()) = GenerationRequest("local-job", "fal", FalContract.MODEL_ID,
        Modality.IMAGE, "", parameters = params, mediaInputs = listOf(MediaInput(Modality.IMAGE, "content://original", "image/png",
            remoteUrl = "https://example.org/original.png", fieldKey = "image_url")))
    private val atlas get() = listOf(model.copy(id="atlas/other",providerModelId="other",displayName="Other",modelFamily="Other"))

    @Test fun liveContractDefinesToolAndInputsWithoutInventingPrice() {
        assertFalse(model.promptAccepted)
        assertNull(model.basePriceUsd)
        assertEquals(listOf("image_url"), model.nativeRequiredFields)
        assertTrue(model.parameters.single { it.key == "model" }.options.contains("Portrait"))
        assertEquals(12345L, model.contractCheckedAt)
        assertEquals(listOf(CatalogWorkflow.REMOVE_BACKGROUND), catalogWorkflows(model))
        assertNotNull(toolCategory(model))
    }
    @Test fun complementRequiresKnownAtlasAndRejectsEquivalentIdentity() {
        assertThrows(IllegalArgumentException::class.java) { FalContract.complementary(emptyList()) }
        assertTrue(FalContract.complementary(atlas))
        assertFalse(FalContract.complementary(listOf(model.copy(id="atlas/biref",providerId="atlas_cloud"))))
    }
    @Test fun incompatibleResolutionAndInventedParametersAreRejectedBeforeSubmit() {
        assertThrows(IllegalArgumentException::class.java) { FalContract.payload(model, request(mapOf("operating_resolution" to "2304x2304"))) }
        assertThrows(IllegalArgumentException::class.java) { FalContract.payload(model, request(mapOf("sync_mode" to "true"))) }
        val retainedDraft = request().copy(prompt="retained composer draft")
        assertFalse(FalContract.payload(model, retainedDraft).containsKey("prompt"))
        assertEquals("retained composer draft", retainedDraft.prompt)
        val payload = FalContract.payload(model, request(mapOf("operating_resolution" to "2304x2304", "model" to "General Use (Dynamic)")))
        assertFalse(payload["sync_mode"]!!.jsonPrimitive.boolean)
        assertFalse(payload.containsKey("prompt"))
        assertEquals("General Use (Dynamic)", payload["model"]!!.jsonPrimitive.content)
    }
    @Test fun followupUrlsCannotSendCredentialsToAnotherOriginOrJob() {
        assertEquals("https://queue.fal.run/fal-ai/birefnet/requests/abc/status",
            FalContract.queueUrl("https://queue.fal.run/fal-ai/birefnet/requests/abc/status", "abc", "/status"))
        listOf("https://evil.example/fal-ai/birefnet/requests/abc/status",
            "https://queue.fal.run/fal-ai/birefnet/requests/other/status",
            "https://queue.fal.run/fal-ai/birefnet/requests/abc/status?token=x").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { FalContract.queueUrl(url, "abc", "/status") }
        }
    }
    @Test fun missingCredentialDoesNotFetchOrCreateAJob() = runBlocking {
        val provider = FalProvider(OkHttpClient(), { "" }, { error("Must not fetch") })
        assertTrue(provider.getModels().isEmpty())
        assertTrue(provider.testConnection("").isFailure)
    }
    @Test fun authenticatedTariffDoesNotBecomeARequestTotal() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"prices":[{"endpoint_id":"fal-ai/birefnet/v2","unit_price":0.02,"unit":"image","currency":"USD"}]}"""))
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
            }.build()
            val price = FalProvider(client, { "test-key" }, { atlas }).tariff()
            assertEquals(0.02, price.usd, 0.0)
            assertEquals("image", price.unit)
            val query = server.takeRequest()
            assertEquals("GET", query.method)
            assertEquals("Key test-key", query.getHeader("Authorization"))
        }
    }
    @Test fun queueRoundTripKeepsIdAndFetchesBothOutputsWithoutResubmission() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(fixture))
            server.enqueue(MockResponse().setBody("""{"request_id":"abc","status_url":"https://queue.fal.run/fal-ai/birefnet/requests/abc/status","response_url":"https://queue.fal.run/fal-ai/birefnet/requests/abc"}"""))
            server.enqueue(MockResponse().setBody("""{"request_id":"abc","status":"COMPLETED"}"""))
            server.enqueue(MockResponse().setBody("""{"image":{"url":"https://fal.media/image.png"},"mask_image":{"url":"https://fal.media/mask.png"}}"""))
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
            }.build()
            val provider = FalProvider(client, { "test-key" }, { atlas })
            val job = provider.submitGeneration(request()).getOrThrow()
            assertEquals("abc", job.providerJobId)
            val completed = provider.pollJobStatus(job).getOrThrow()
            assertEquals(GenerationStatus.COMPLETED, completed.status)
            assertEquals(2, completed.outputs.size)
            assertNull(completed.actualCostUsd)
            val requests = (1..4).map { server.takeRequest() }
            assertEquals(listOf("GET", "POST", "GET", "GET"), requests.map { it.method })
            assertEquals("/fal-ai/birefnet/v2", requests[1].path)
        }
    }
    @Test fun invalidReceiptAfterPostRemainsUncertainAndIsNeverRetried() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(fixture))
            server.enqueue(MockResponse().setBody("""{"request_id":"abc","status_url":"https://evil.example/status","response_url":"https://evil.example/result"}"""))
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
            }.build()
            val error = FalProvider(client, { "test-key" }, { atlas }).submitGeneration(request()).exceptionOrNull()
            assertTrue(error is ProviderError)
            assertNull((error as ProviderError).httpStatusCode)
            assertEquals(2, server.requestCount)
        }
    }
}
