package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.*
import com.vidadomu.nexus.provider.crun.CrunModelCatalog
import com.vidadomu.nexus.provider.crun.CrunPayloadCodec
import com.vidadomu.nexus.provider.crun.CrunCatalogSnapshot
import com.vidadomu.nexus.ui.models.cheapestRoute
import com.vidadomu.nexus.ui.models.canonicalRouteKey
import com.vidadomu.nexus.ui.models.providerName
import com.vidadomu.nexus.ui.models.fiveSecondPriceUsd
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PriceAndMediaRegressionTest {
    private val base=AtlasModelCatalog.curatedModels.first().copy(id="alibaba/wan-2.7/image-to-video")
    private fun schema(props:String,required:String="[]") =
        """{"components":{"schemas":{"Input":{"properties":$props,"required":$required}}}}"""

    @Test fun klingMotionControlKeepsEveryNativeInput() {
        val model = CrunModelCatalog.models.first { it.id == "crun/kling-v3-motion-control-std" }
        val request = GenerationRequest(
            id = "dry_run",
            providerId = model.providerId,
            modelId = model.id,
            modality = Modality.VIDEO,
            prompt = "Conserva el vestuario",
            parameters = mapOf(
                "character_orientation" to "video",
                "keep_original_sound" to "true",
                "mode" to "std"
            ),
            mediaInputs = listOf(
                MediaInput(Modality.IMAGE, "content://image", "image/png", remoteUrl = "https://files.example/character.png", slotType = MediaSlotType.PRIMARY_IMAGE),
                MediaInput(Modality.VIDEO, "content://video", "video/mp4", remoteUrl = "https://files.example/motion.mp4", slotType = MediaSlotType.VIDEO_INPUT)
            )
        )
        val payload = CrunPayloadCodec.encode(model, request)
        val input = payload["input"]!!.jsonObject
        assertEquals("kling/v3-motion-control", payload["model"]!!.jsonPrimitive.content)
        assertEquals("Conserva el vestuario", input["prompt"]!!.jsonPrimitive.content)
        assertEquals("video", input["character_orientation"]!!.jsonPrimitive.content)
        assertTrue(input["keep_original_sound"]!!.jsonPrimitive.boolean)
        assertEquals("std", input["mode"]!!.jsonPrimitive.content)
        assertEquals("https://files.example/character.png", input["img_urls"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("https://files.example/motion.mp4", input["video_urls"]!!.jsonArray.single().jsonPrimitive.content)
    }

    @Test fun crunSnapshotExposesWanThreePrimeImageRoute() {
        val model = CrunCatalogSnapshot.decode(Json.parseToJsonElement("""
            {
              "providerModelId":"wan/3-0-prime-i2v",
              "title":"Crun Wan 3.0 Prime Image to Video API",
              "summary":"Wan Prime",
              "sourcePath":"/models/wan/3-0-prime-image-to-video.json",
              "output":"VIDEO",
              "required":["prompt","img_urls"],
              "properties":{
                "prompt":{"type":"string"},
                "img_urls":{"type":"array","minItems":1,"maxItems":2,"items":{"type":"string","format":"uri"}},
                "duration":{"type":"integer","minimum":2,"maximum":30,"default":5},
                "resolution":{"type":"string","enum":["480P","720P","1080P"],"default":"720P"}
              }
            }
        """))!!
        assertEquals("wan/3-0-prime-i2v", model.providerModelId)
        assertEquals("crun/wan/3-0-prime-i2v", model.id)
        assertEquals(ModelInputMode.MULTI_IMAGE, model.inputMode)
        assertEquals("img_urls", model.imagesArrayField)
        assertEquals(2, model.maxImages)
        assertTrue(model.isMediaRequired)
        assertEquals(4, model.variantRank)
    }

    @Test fun cheapestProviderUsesComparableFiveSecondPrice() {
        val atlas = base.copy(providerId="atlas", basePriceUsd=.061, priceUnit="second", priceUnitVerified=true)
        val crun = base.copy(id="crun/wan/3-0-prime-i2v", providerId="crun", basePriceUsd=.375, priceUnit="5 s", priceUnitVerified=true)
        assertEquals(.305, fiveSecondPriceUsd(atlas)!!, 0.0001)
        assertEquals(atlas.id, cheapestRoute(listOf(crun, atlas)).id)
    }

    @Test fun cheaperLowResolutionRouteNeverOverridesMaximumQuality() {
        val resolution720=ModelParameter("resolution","Calidad",ParameterType.RESOLUTION,"720p",options=listOf("720p","1080p"))
        val resolution1080=resolution720.copy(defaultValue="1080p")
        val cheap=base.copy(id="cheap",providerId="crun",parameters=listOf(resolution720),basePriceUsd=.10,priceUnit="5 s",priceUnitVerified=true)
        val quality=base.copy(id="quality",providerId="runway",parameters=listOf(resolution1080),basePriceUsd=.50,priceUnit="5 s",priceUnitVerified=true)
        assertEquals("quality",cheapestRoute(listOf(cheap,quality)).id)
    }

    @Test fun atlasCloudHasAReadableProviderName() {
        assertEquals("Atlas Cloud",providerName("atlas_cloud"))
    }

    @Test fun equivalentWanPrimeRoutesFromThreeProvidersShareOneCard() {
        val atlas=base.copy(
            id="alibaba/wan-3.0-prime/image-to-video",providerId="atlas_cloud",displayName="Wan 3.0 Prime Image-to-Video",
            organization="Alibaba (Qwen)",modelFamily="Wan 3.0",versionNumeric=3.0,variantRank=4,inputMode=ModelInputMode.SINGLE_IMAGE
        )
        val crun=atlas.copy(id="crun/wan/3-0-prime-i2v",providerModelId="wan/3-0-prime-i2v",providerId="crun",modelFamily="Wan")
        val runway=atlas.copy(id="runway/wan3_prime/i2v",providerModelId="wan3_prime",providerId="runway",modelFamily="Wan")
        assertEquals(canonicalRouteKey(atlas),canonicalRouteKey(crun))
        assertEquals(canonicalRouteKey(atlas),canonicalRouteKey(runway))
    }

    @Test fun optionalVideoDoesNotReplaceWanImagePanel() {
        val m=NativeModelSchema.decode(base,schema("""{"image":{"type":"string"},"last_image":{"type":"string"},"video":{"type":"string"}}""")).model
        assertEquals(ModelInputMode.MULTI_MODAL,m.inputMode)
        assertEquals("image",m.firstFrameField)
        assertEquals("last_image",m.lastFrameField)
        assertEquals("video",m.videoField)
        // Optional schema fields must not become required because of an ID substring.
        assertFalse("image" in m.nativeRequiredFields)
        assertFalse(m.isMediaRequired)
    }
    @Test fun absentMaxItemsDoesNotMeanOneReference() {
        val m=NativeModelSchema.decode(base.copy(id="alibaba/wan-2.7/reference-to-video"),
            schema("""{"images":{"type":"array","items":{"type":"string"}},"videos":{"type":"array"}}""")).model
        assertEquals(ModelInputMode.MULTI_MODAL,m.inputMode)
        assertEquals(Int.MAX_VALUE,m.maxImages)
    }
    @Test fun explicitProviderLimitStillWins() {
        val m=NativeModelSchema.decode(base,schema("""{"images":{"type":"array","maxItems":7}}""")).model
        assertEquals(7,m.maxImages)
    }
    @Test fun wanThreeStructuredReferencesRenderAndKeepImageLimit() {
        val m=NativeModelSchema.decode(base.copy(id="alibaba/wan-3.0/reference-to-video"),
            schema("""{"refers":{"type":"array","maxItems":20,"items":{"type":"object","properties":{"url":{"type":"string"}}},"x-media-limits":{"image":{"maxItems":10}}}}""")).model
        assertEquals(ModelInputMode.MULTI_IMAGE,m.inputMode)
        assertEquals("refers",m.imagesArrayField)
        assertEquals(10,m.maxImages)
        assertTrue(m.structuredImageReferences)
        val payload=NativeImagePayload.encode(listOf("https://example.org/one.png","https://example.org/two.png"),true)
        assertEquals(2,payload.size)
        assertEquals("image",payload[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("https://example.org/two.png",payload[1].jsonObject["url"]!!.jsonPrimitive.content)
    }
    @Test fun requiredVideoDoesNotHideOptionalImage() {
        val m=NativeModelSchema.decode(base.copy(id="kling/motion-control"),
            schema("""{"image":{"type":"string"},"video":{"type":"string"}}""","""["video"]""")).model
        assertEquals(ModelInputMode.MULTI_MODAL,m.inputMode)
        assertEquals(listOf("video"),m.nativeRequiredFields)
        assertEquals("image",m.firstFrameField)
    }
    private fun pricedModel()=base.copy(imagesArrayField="image_urls",parameters=listOf(
        ModelParameter("duration","Duración",ParameterType.DURATION,defaultValue="5",nativeType="integer"),
        ModelParameter("sound","Sonido",ParameterType.BOOLEAN,defaultValue="true",nativeType="boolean"),
        ModelParameter("resolution","Resolución",ParameterType.RESOLUTION,defaultValue="1080P",nativeType="string"),
        ModelParameter("prompt","Texto",ParameterType.TEXT,nativeType="string")
    ))
    @Test fun quoteUsesNativeDefaultsAndSelectedOverrides() {
        val p=AtlasPriceQuote.payload(pricedModel(),mapOf("duration" to "10","sound" to "false"),2)
        assertEquals(10,p["duration"]!!.jsonPrimitive.int)
        assertFalse(p["sound"]!!.jsonPrimitive.boolean)
        assertEquals("1080P",p["resolution"]!!.jsonPrimitive.content)
        assertEquals(2,p["image_urls"]!!.jsonArray.size)
    }
    @Test fun quoteContainsNoPromptOrPersonalImageUris() {
        val p=AtlasPriceQuote.payload(pricedModel(),mapOf("prompt" to "private","image_urls" to "content://private","__token" to "secret"),2)
        assertFalse(p.toString().contains("private"))
        assertFalse(p.toString().contains("secret"))
        assertEquals(listOf("",""),p["image_urls"]!!.jsonArray.map { it.jsonPrimitive.content })
    }
    @Test fun readsWholeClipQuoteWithoutMultiplyingAgain() {
        val q=AtlasPriceQuote.decode("""{"code":200,"data":{"price":"0.19","origin_price":"0.19"}}""","6 s",1)
        assertEquals(.19,q.usd,0.0)
    }
    @Test fun readsSoundAndImageInputInclusiveQuotes() {
        assertEquals(.714,AtlasPriceQuote.decode("""{"code":200,"data":{"price":"0.714"}}""","5 s · con sonido").usd,0.0)
        assertEquals(.08,AtlasPriceQuote.decode("""{"code":200,"data":{"price":"0.08"}}""","1 imagen").usd,0.0)
    }
    @Test(expected=IllegalStateException::class) fun failedQuoteIsNotFree() {
        AtlasPriceQuote.decode("""{"code":429,"data":{"price":"0"}}""","")
    }
    @Test(expected=IllegalStateException::class) fun missingPriceIsNotFree() {
        AtlasPriceQuote.decode("""{"code":200,"data":{}}""","")
    }
}
