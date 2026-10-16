package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.core.model.FormContract
import com.vidadomu.nexus.core.model.MediaInput
import com.vidadomu.nexus.core.model.MediaSlotType
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.provider.atlas.AtlasGenerationPayload
import com.vidadomu.nexus.provider.atlas.AtlasModelCatalog
import com.vidadomu.nexus.provider.atlas.AtlasPriceQuote
import com.vidadomu.nexus.provider.atlas.NativeGenerationRequest
import com.vidadomu.nexus.provider.atlas.NativeModelSchema
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Caso reproducible: Atlas devolvía «resolution is required (720p, 1080p, 1440p or 4k)» porque
 * Nexus enviaba template=live-action-enhance-4k sin resolution, con short=0 (inválido en 4K) y
 * con un prompt que el schema no declara. Fixture: schema público oficial (2026-10-16).
 */
class TencentContractTest {
    private val base = AtlasModelCatalog.curatedModels.first().copy(id = "tencent/video/upscaler", providerId = "atlas_cloud",
        displayName = "Tencent Video Upscaler", modalities = listOf(Modality.VIDEO), schemaUrl = "https://static.atlascloud.ai/model/schema/tencent-video-upscaler.json")
    private fun model() = NativeModelSchema.decode(base, requireNotNull(javaClass.getResource("/native/tencent_video_upscaler.json")).readText()).model

    @Test fun schemaWithoutPromptIsNotSentAsFreeText() {
        val m = model()
        assertFalse(m.promptAccepted)
        assertFalse(m.promptRequired)
        assertEquals("video_url", m.videoField)
        assertTrue(m.isMediaRequired)
        assertEquals(4, NativeModelSchema.conditions(m.id)["short"]!!.size)
    }

    @Test fun reconcileDropsIncompatibleDefaultAndDerivesResolution() {
        val m = model()
        val form = FormContract.restoredValues(m, mapOf("template" to "live-action-enhance-4k"))
        assertEquals("0", form["short"])
        val sent = NativeModelSchema.reconcile(m, form.filterValues { it.isNotBlank() })
        assertNull("short=0 no es válido con una plantilla 4K", sent["short"])
        assertEquals("4k", sent["resolution"])
        assertEquals("1440p", NativeModelSchema.reconcile(m, mapOf("template" to "anime-enhance-2k"))["resolution"])
        assertEquals("720p", NativeModelSchema.reconcile(m, mapOf("template" to "restore-720p", "short" to "0"))["resolution"])
        assertEquals("0", NativeModelSchema.reconcile(m, mapOf("template" to "restore-720p", "short" to "0"))["short"])
    }

    @Test fun payloadCarriesApiFieldsAndNoPrompt() {
        val m = model()
        val form = FormContract.restoredValues(m, mapOf("template" to "live-action-enhance-4k"))
        val video = MediaInput(Modality.VIDEO, "file:///clip.mp4", "video/mp4", remoteUrl = "https://cdn.example/clip.mp4", slotType = MediaSlotType.VIDEO_INPUT)
        val payload = AtlasGenerationPayload.build(NativeGenerationRequest.build(m, Modality.VIDEO, "", form, listOf(video), "job_1"))
        assertFalse("sin campo prompt en el schema", payload.containsKey("prompt"))
        assertEquals("live-action-enhance-4k", payload["template"]!!.jsonPrimitive.content)
        assertEquals("4k", payload["resolution"]!!.jsonPrimitive.content)
        assertEquals("https://cdn.example/clip.mp4", payload["video_url"]!!.jsonPrimitive.content)
        assertFalse(payload.containsKey("short"))
        assertEquals("tencent/video/upscaler", payload["model"]!!.jsonPrimitive.content)
    }

    @Test fun formValidationMatchesTheAgent() {
        val m = model()
        assertNull(NativeModelSchema.validate(m, FormContract.restoredValues(m, mapOf("template" to "live-action-enhance-4k"))))
        val wrongShort = NativeModelSchema.validate(m, mapOf("template" to "live-action-enhance-4k", "short" to "1000"))
        assertNotNull(wrongShort); assertTrue(wrongShort!!, "1441" in wrongShort)
        assertNull(NativeModelSchema.validate(m, mapOf("template" to "live-action-enhance-4k", "short" to "2160")))
        assertNotNull("un texto libre no tiene dónde ir", NativeModelSchema.validate(m, mapOf("template" to "live-action-enhance-4k", "prompt" to "upscale 4k")))
    }

    @Test fun agentSchemaIsTheSameContract() {
        val m = model()
        val schema = NexusModelRegistry.toSchema(ModelRef("atlas_cloud", m.id), m, emptyList(), m.schemaUrl)
        assertFalse(schema.promptAccepted)
        assertFalse(schema.promptRequired)
        assertEquals("4k", schema.derivedParam("resolution")!!.valueFor("live-action-enhance-4k"))
        assertEquals("1440p", schema.derivedParam("resolution")!!.valueFor("seedance2-enhance-2k"))
        assertEquals(4, schema.param("short")!!.conditions.size)
        assertEquals("video_url", schema.media.videoField)
    }

    @Test fun priceUsesTheTemplateTierNotThe720pBase() = runBlocking {
        val m = model()
        val fourK = AtlasPriceQuote.fetch(m, mapOf("template" to "live-action-enhance-4k"), videoSeconds = 30.0)!!
        assertEquals(30 * 0.036542, fourK.usd, 1e-9)
        val fps60 = AtlasPriceQuote.fetch(m, mapOf("template" to "anime-enhance-2k", "fps" to "60"), videoSeconds = 30.0)!!
        assertEquals(30 * 0.016507 * 2, fps60.usd, 1e-9)
        val unknownLength = AtlasPriceQuote.fetch(m, mapOf("template" to "live-action-enhance-4k"), videoSeconds = null)!!
        assertEquals("s", unknownLength.unit)
        assertEquals(0.036542, unknownLength.unitUsd!!, 1e-9)
    }
}
