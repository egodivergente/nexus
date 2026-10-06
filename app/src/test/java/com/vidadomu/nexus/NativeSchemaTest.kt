package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.*
import com.vidadomu.nexus.ui.models.savingPercent
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class NativeSchemaTest {
    private val base = AtlasModelCatalog.curatedModels.first()
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/native/" + name + ".json")).readText()

    @Test fun wanUsesPublishedFirstLastAndDurations() {
        val config = NativeModelSchema.decode(base, fixture("alibaba_wan-3.0-prime_image-to-video"))
        assertEquals(ModelInputMode.DUAL_FRAME, config.model.inputMode)
        assertEquals("last_image", config.model.lastFrameField)
        assertTrue(config.model.supportedDurations.containsAll(listOf("-1", "2", "30")))
        assertFalse(config.model.supportsNativeAspectRatio)
        assertTrue(config.unsupportedRequired.isEmpty())
    }
    @Test fun textVideoClearsIncorrectCatalogMedia() {
        val config = NativeModelSchema.decode(base.copy(supportsLastFrame = true, lastFrameField = "end_image"),
            fixture("alibaba_wan-3.0-prime_text-to-video"))
        assertEquals(ModelInputMode.TEXT_ONLY, config.model.inputMode)
        assertNull(config.model.lastFrameField)
        assertFalse(config.model.supportsLastFrame)
        assertTrue(config.model.parameters.any { it.key == "ratio" && "adaptive" in it.options })
    }
    @Test fun editUsesSixteenImagesAndRejectsInconsistentDefault() {
        val config = NativeModelSchema.decode(base, fixture("openai_gpt-image-2.5-flare_edit"))
        assertEquals(16, config.model.maxImages)
        assertEquals("images", config.model.imagesArrayField)
        assertEquals("", config.model.parameters.first { it.key == "size" }.defaultValue)
        assertTrue(config.notices.any { "size" in it })
        assertTrue(config.model.contractMedia.any { it.key=="mask" || it.key=="mask_image" || it.key=="mask_url" })
    }
    @Test fun transparentBackgroundRejectsJpeg() {
        val config = NativeModelSchema.decode(base, fixture("openai_gpt-image-2.5-sunburst_text-to-image"))
        assertNotNull(NativeModelSchema.validate(config.model, mapOf("background" to "transparent", "output_format" to "jpeg")))
        assertNull(NativeModelSchema.validate(config.model, mapOf("background" to "transparent", "output_format" to "png")))
    }
    @Test fun requiredStructuredInputIsExplicitlyUnsupported() {
        val config = NativeModelSchema.decode(base, """{"components":{"schemas":{"Input":{"properties":{"messages":{"type":"array"}},"required":["messages"]}}}}""")
        assertEquals(listOf("messages"), config.unsupportedRequired)
    }
    @Test fun referenceImageUrlsIsRecognizedAndSentWithItsExactNativeKey() {
        val config = NativeModelSchema.decode(base, """{"components":{"schemas":{"Input":{"properties":{"prompt":{"type":"string"},"reference_image_urls":{"type":"array","items":{"type":"string"},"minItems":1,"maxItems":4}},"required":["prompt","reference_image_urls"]}}}}""")
        assertTrue(config.unsupportedRequired.isEmpty())
        assertEquals(ModelInputMode.MULTI_IMAGE, config.model.inputMode)
        assertEquals("reference_image_urls", config.model.imagesArrayField)
        assertEquals(4, config.model.maxImages)
        assertTrue(config.model.isMediaRequired)

        val payload = AtlasGenerationPayload.build(GenerationRequest(
            id="qwen-edit", providerId="atlas_cloud", modelId="qwen-image-3.0-pro/edit",
            modality=Modality.IMAGE, prompt="outpaint",
            parameters=mapOf("__images_array_field" to "reference_image_urls", "__native_schema" to "true"),
            mediaInputs=listOf(MediaInput(Modality.IMAGE, "content://ref", "image/jpeg",
                remoteUrl="https://cdn.example/ref.jpg", slotType=MediaSlotType.REFERENCE_IMAGE))
        ))
        assertEquals("https://cdn.example/ref.jpg", payload["reference_image_urls"]!!.jsonArray.single().jsonPrimitive.content)
        assertFalse("Nexus aliases must not leak", payload.containsKey("images"))
    }
    @Test fun discountFlagDoesNotMeanFree() {
        assertNull(savingPercent(base.copy(discount = "100", basePriceUsd = 1.0, originPriceUsd = 1.0)))
        assertEquals(50, savingPercent(base.copy(basePriceUsd = 0.5, originPriceUsd = 1.0)))
        assertNull(savingPercent(base.copy(basePriceUsd = null, originPriceUsd = 1.0)))
    }
}
