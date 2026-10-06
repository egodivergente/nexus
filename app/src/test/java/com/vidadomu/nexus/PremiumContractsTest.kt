package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.runway.*
import com.vidadomu.nexus.provider.crun.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PremiumContractsTest {
    private fun model(id:String)=RunwayContracts.models.first { it.providerModelId==id }
    @Test fun allRoutesHaveDistinctIdsAndKnownEndpoints() {
        val models=RunwayContracts.models
        assertEquals(64,models.size)
        assertEquals(41,models.map { it.providerModelId }.distinct().size)
        assertEquals(models.size,models.map { it.id }.distinct().size)
        assertTrue(models.all { it.contractEndpoint in setOf("image_to_video","text_to_video","video_to_video","text_to_image","character_performance","text_to_speech","sound_effect","speech_to_speech","voice_isolation","voice_dubbing","video_upscale","image_upscale","video_to_hdr") })
    }
    @Test fun allRunwayContractsEncodeRequiredMediaAndDefaultTypes() {
        RunwayContracts.models.forEach { m ->
            val media=m.contractMedia.filter { it.required }.map { f -> MediaInput(type=if(f.mime.startsWith("video")) Modality.VIDEO else if(f.mime.startsWith("audio")) Modality.AUDIO else Modality.IMAGE,
                uriString="file:///test",mimeType=f.mime,remoteUrl="https://example.com/test",fieldKey=f.key) }
            val payload=ContractPayload.encode(m,GenerationRequest("test",m.providerId,m.id,m.modalities.first(),"Test",mediaInputs=media))
            assertEquals(m.providerModelId,payload["model"]?.jsonPrimitive?.content)
            m.contractMedia.filter { it.required }.forEach { assertNotNull(m.id+" "+it.key,payload[it.key.substringBefore('.')]) }
        }
    }
    @Test fun referenceArrayUsesUriObjectsAndNoInventedFields() {
        val m=RunwayContracts.models.first { it.contractMedia.any { it.encoding=="object-array" } }
        val f=m.contractMedia.first { it.encoding=="object-array" }
        val media=m.contractMedia.filter { it.required || it==f }.map { MediaInput(Modality.IMAGE,"file:///x",mimeType="image/png",remoteUrl="https://example.com/x",fieldKey=it.key) }
        val payload=ContractPayload.encode(m,GenerationRequest("t",m.providerId,m.id,m.modalities.first(),"Test",mediaInputs=media))
        assertEquals("https://example.com/x",payload[f.key]!!.jsonArray.first().jsonObject["uri"]!!.jsonPrimitive.content)
        assertFalse(payload.containsKey("aspect_ratio"))
    }
    @Test fun requiredMediaCannotBeSilentlyOmitted() {
        val m=RunwayContracts.models.first { it.contractMedia.any { it.required } }
        assertThrows(IllegalArgumentException::class.java) { ContractPayload.encode(m,GenerationRequest("t",m.providerId,m.id,m.modalities.first(),"test")) }
    }
    @Test fun creditsConvertToDollarsAndRespectImageTierAndCount() {
        assertEquals(.05,RunwayPricing.quote(model("gen4_image"),mapOf("ratio" to "1280:720")).usd!!,.00001)
        assertEquals(.08,RunwayPricing.quote(model("gen4_image"),mapOf("ratio" to "1920:1080")).usd!!,.00001)
        assertEquals(.06,RunwayPricing.quote(model("gen4_image_turbo"),mapOf("outputCount" to "3")).usd!!,.00001)
    }
    @Test fun audioAndQualityChangeVideoQuote() {
        val m=model("veo3.1")
        assertEquals(2.0,RunwayPricing.quote(m,mapOf("duration" to "5","audio" to "true")).usd!!,.00001)
        assertEquals(1.0,RunwayPricing.quote(m,mapOf("duration" to "5","audio" to "false")).usd!!,.00001)
    }
    @Test fun unknownPricesAreNotFreeOrGuessed() {
        val m=model("gen4_image").copy(providerModelId="unknown_model")
        assertNull(RunwayPricing.quote(m).usd)
        assertNull(RunwayPricing.quote(model("eleven_voice_isolation")).usd)
    }
    @Test fun speechPriceRoundsCharactersUp() {
        val m=model("eleven_v3")
        assertEquals(.02,RunwayPricing.quote(m,promptLength=51).usd!!,.00001)
    }
    @Test fun crunTtsUsesTextAndIndependentAudioField() {
        val m=CrunCatalogSnapshot.decode(Json.parseToJsonElement("""{"providerModelId":"qwen3-tts/speech-synthesis","title":"Qwen TTS","output":"AUDIO","required":["text","voice"],"properties":{"text":{"type":"string"},"voice":{"type":"string","enum":["Cherry"]},"reference_audio":{"type":"string"}}}"""))!!
        assertEquals("text",m.contractPromptField)
        assertEquals("audio/*",m.contractMedia.single().mime)
        val payload=CrunPayloadCodec.encode(m,GenerationRequest("t","crun",m.id,Modality.AUDIO,"Hola"))
        assertEquals("Hola",payload["input"]!!.jsonObject["text"]!!.jsonPrimitive.content)
        assertNull(payload["input"]!!.jsonObject["prompt"])
    }
    @Test fun finalChargeComesFromCostNotEstimatedCost() {
        assertEquals(.25,RunwayBilling.confirmedUsd(Json.parseToJsonElement("""{"status":"SUCCEEDED","cost":{"credits":25},"estimatedCost":{"credits":80}}""").jsonObject)!!,.00001)
        assertEquals(0.0,RunwayBilling.confirmedUsd(Json.parseToJsonElement("""{"status":"FAILED","cost":{"credits":0}}""").jsonObject)!!,.00001)
        assertNull(RunwayBilling.confirmedUsd(Json.parseToJsonElement("""{"status":"RUNNING","estimatedCost":{"credits":25}}""").jsonObject))
        assertNull(RunwayBilling.confirmedUsd(Json.parseToJsonElement("""{"status":"SUCCEEDED"}""").jsonObject))
    }
    @Test fun atlasPublicSnapshotLosesNoModel() {
        val raw=javaClass.classLoader!!.getResource("atlas-catalog-2026-09-22.json")!!.readText()
        val models=com.vidadomu.nexus.provider.atlas.AtlasModelMapper.parseModelsResponse(raw)
        assertEquals(509,models.size)
        assertEquals(231,models.count { Modality.VIDEO in it.modalities })
        assertEquals(141,models.count { Modality.IMAGE in it.modalities })
        assertEquals(121,models.count { Modality.CHAT in it.modalities })
        assertEquals(16,models.count { Modality.AUDIO in it.modalities })
    }
    @Test fun sunoKeepsNativeModelOutsideAndVersionInside() {
        val m=CrunTextCatalog.suno.first()
        val request=GenerationRequest("t","crun",m.id,Modality.AUDIO,"Canción de prueba")
        CrunTextCatalog.validateSuno(m,request)
        val p=CrunPayloadCodec.encode(m,request)
        assertEquals("suno/music-generate",p["model"]!!.jsonPrimitive.content)
        assertEquals("v5.5",p["input"]!!.jsonObject["model"]!!.jsonPrimitive.content)
        assertEquals("simple",p["input"]!!.jsonObject["mode"]!!.jsonPrimitive.content)
    }
    @Test fun sunoCustomCannotOmitLyricsOrTitle() {
        val m=CrunTextCatalog.suno.first()
        assertThrows(IllegalArgumentException::class.java) {
            CrunTextCatalog.validateSuno(m,GenerationRequest("t","crun",m.id,Modality.AUDIO,"",parameters=mapOf("mode" to "custom")))
        }
    }
    @Test fun invalidDurationRejectedBeforeGeneration() {
        val m=model("gen4.5")
        assertThrows(IllegalArgumentException::class.java) {
            ContractPayload.validate(m,GenerationRequest("t","runway",m.id,Modality.VIDEO,"test",parameters=mapOf("duration" to "999")))
        }
    }
    @Test fun accountTextCatalogPreservesExactIds() {
        val m=CrunTextCatalog.decode("""{"data":[{"id":"example/version-exact","owned_by":"Example"}]}""").single()
        assertEquals("example/version-exact",m.providerModelId)
        assertEquals(listOf(Modality.CHAT),m.modalities)
    }
    @Test fun atlasAudioAndMaskHaveIndependentAttachmentFields() {
        val base=NexusModel("test","Test","atlas_cloud",organization="Test",modalities=listOf(Modality.AUDIO),capabilities=emptyList(),parameters=emptyList())
        val c=com.vidadomu.nexus.provider.atlas.NativeModelSchema.decode(base,"""{"components":{"schemas":{"Input":{"type":"object","required":["audio_url","mask"],"properties":{"audio_url":{"type":"string"},"mask":{"type":"string"}}}}}}""")
        assertEquals(2,c.model.contractMedia.size)
        assertTrue(c.unsupportedRequired.isEmpty())
        val media=listOf(MediaInput(Modality.AUDIO,"file:///audio",mimeType="audio/wav",remoteUrl="https://example.com/audio",fieldKey="audio_url"),
            MediaInput(Modality.IMAGE,"file:///mask",mimeType="image/png",remoteUrl="https://example.com/mask",fieldKey="mask"))
        val request=com.vidadomu.nexus.provider.atlas.NativeGenerationRequest.build(c.model,Modality.AUDIO,"",emptyMap(),media,"t")
        val payload=com.vidadomu.nexus.provider.atlas.AtlasGenerationPayload.build(request)
        assertEquals("https://example.com/audio",payload["audio_url"]!!.jsonPrimitive.content)
        assertEquals("https://example.com/mask",payload["mask"]!!.jsonPrimitive.content)
        assertNull(payload["image"])
    }
    @Test fun atlasReservedAmountIsNotReportedAsFinalCharge() {
        assertNull(com.vidadomu.nexus.provider.atlas.AtlasBilling.confirmed("0.12",GenerationStatus.QUEUED))
        assertNull(com.vidadomu.nexus.provider.atlas.AtlasBilling.confirmed("NaN",GenerationStatus.COMPLETED))
        assertEquals(.12,com.vidadomu.nexus.provider.atlas.AtlasBilling.confirmed("0.12",GenerationStatus.COMPLETED)!!,.00001)
    }
    @Test fun sunoEmptyFormShowsMissingFieldsBeforeSubmission() {
        val m=CrunTextCatalog.suno.first()
        val defaults=m.parameters.associate { it.key to it.defaultValue }
        val simple=com.vidadomu.nexus.ui.create.CreateUiState(selectedModel=m,parameters=defaults)
        assertTrue(simple.missingRequiredFields.contains("Descripción de la canción"))
        val custom=simple.copy(parameters=defaults+("mode" to "custom"))
        assertTrue(custom.missingRequiredFields.containsAll(listOf("Título","Estilo musical","Letra")))
        assertTrue(m.parameters.first { it.key=="instrumental" }.isRequired)
    }
}
