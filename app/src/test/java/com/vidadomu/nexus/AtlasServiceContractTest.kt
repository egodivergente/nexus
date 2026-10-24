package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AtlasServiceContractTest {
    private val base = AtlasModelCatalog.curatedModels.first().copy(id="fixture/service", providerModelId="fixture/service", schemaUrl="https://static.atlascloud.ai/model/schema/fixture.json")
    private fun schema(prompt: String) = """{"servers":[{"url":"https://api.atlascloud.ai"}],"paths":{"/api/v1/model/generateVideo":{"post":{}}},"components":{"schemas":{"Input":{"properties":{"prompt":$prompt,"duration":{"type":"integer","default":5},"fps":{"type":"integer","default":24},"resolution":{"type":"string","enum":["720p","1080p"],"default":"720p"}}}}}}"""

    @Test fun endpointRequiresOfficialServerAndUniqueSupportedPost() {
        val root = Json.parseToJsonElement(schema("""{"type":"string"}""")).jsonObject
        assertEquals("/api/v1/model/generateVideo", NativeModelSchema.endpoint(root))
        assertNull(NativeModelSchema.endpoint(JsonObject(root + ("servers" to Json.parseToJsonElement("""[{"url":"https://evil.example"}]""")))))
        assertNull(NativeModelSchema.endpoint(JsonObject(root + ("paths" to Json.parseToJsonElement("""{"/api/v1/model/generateVideo":{"get":{}}}""")))))
    }
    @Test fun unknownMaximumRemainsUnknownDespiteRecommendation() {
        val model = NativeModelSchema.decode(base, schema("""{"type":"string","description":"We recommend fewer than 1000 words"}""")).model
        assertNull(model.promptMaxLength)
        assertNull(NativeModelSchema.promptLimit(model.id))
        assertEquals(base.schemaUrl, model.contractSource)
        assertNotNull(model.contractCheckedAt)
    }
    @Test fun refreshedSchemaRemovesObsoleteMaximum() {
        val old = NativeModelSchema.decode(base, schema("""{"type":"string","maxLength":1000}""")).model
        assertEquals(1000, old.promptMaxLength)
        NativeModelSchema.invalidate()
        val fresh = NativeModelSchema.decode(old, schema("""{"type":"string"}""")).model
        assertNull(fresh.promptMaxLength)
        assertNull(NativeModelSchema.promptLimit(fresh.id))
    }
    @Test fun quotationIncludesConfiguredDurationResolutionAndFps() {
        val model = NativeModelSchema.decode(base, schema("""{"type":"string"}""")).model
        val body = AtlasPriceQuote.payload(model, mapOf("duration" to "8", "resolution" to "1080p", "fps" to "60", "__private" to "secret", "prompt" to "private"), 0)
        assertEquals(8, body["duration"]!!.jsonPrimitive.int)
        assertEquals(60, body["fps"]!!.jsonPrimitive.int)
        assertEquals("1080p", body["resolution"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("prompt"))
        assertFalse(body.containsKey("__private"))
    }
    @Test fun estimatedFlagAndSourceAreNotLost() {
        val quote = AtlasPriceQuote.decode("""{"code":200,"data":{"price":0.6,"estimated":true}}""", "1080p", 1234)
        assertTrue(quote.estimated)
        assertTrue(quote.isRequestTotal)
        assertEquals(1234, quote.checkedAt)
        assertEquals("https://api.atlascloud.ai/api/v1/model/calculate", quote.source)
        assertTrue(AtlasPriceQuote.decode("""{"code":200,"data":{"price":0}}""", "").estimated)
        assertFalse(AtlasPriceQuote.decode("""{"code":200,"data":{"price":0,"estimated":false}}""", "").estimated)
    }
    @Test fun generationNeverGloballyTruncatesPrompt() {
        val prompt = "🎨 árbol ".repeat(4000)
        val request = GenerationRequest("fixture", "atlas_cloud", "fixture/service", Modality.IMAGE, prompt)
        assertEquals(prompt, AtlasGenerationPayload.build(request)["prompt"]!!.jsonPrimitive.content)
    }
    @Test fun maximumCountsUnicodeCodePointsNotUtf16Units() {
        val model = NativeModelSchema.decode(base, schema("""{"type":"string","maxLength":2}""")).model
        assertNull(NativeModelSchema.validate(model, mapOf("prompt" to "🎨🌳")))
        assertNotNull(NativeModelSchema.validate(model, mapOf("prompt" to "🎨🌳x")))
    }
    @Test fun verifiedPublicCalculatorResponsePreservesTokenEstimate() {
        val quote = AtlasPriceQuote.decode("""{"code":200,"msg":"succeed","data":{"discount":"80","estimated":true,"estimated_tokens":108900,"origin_price":"1.21968","price":"0.975744"}}""", "5 s · 720p · 24 FPS")
        assertEquals(0.975744, quote.usd, 0.000001)
        assertEquals(108900L, quote.estimatedTokens)
        assertTrue(quote.estimated)
    }
}
