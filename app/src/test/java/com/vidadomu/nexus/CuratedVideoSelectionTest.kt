package com.vidadomu.nexus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.data.repository.CuratedVideoSelection
import com.vidadomu.nexus.provider.atlas.NativeModelSchema
import com.vidadomu.nexus.routing.GenerationRoute
import com.vidadomu.nexus.routing.RoutingSubmissionPolicy
import com.vidadomu.nexus.routing.SeedanceBytePlusPolicy
import com.vidadomu.nexus.ui.models.CatalogPlacements
import com.vidadomu.nexus.ui.models.toolCategory
import com.vidadomu.nexus.ui.models.toolCategoryIcon
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CuratedVideoSelectionTest {
    private fun video(provider: String, id: String, providerModelId: String? = id.removePrefix("crun/"),
        caps: List<ModelCapability> = listOf(ModelCapability.IMAGE_TO_VIDEO), params: List<ModelParameter> = emptyList()) =
        NexusModel(id, id.substringAfterLast('/'), provider, providerModelId = providerModelId, organization = "Test",
            modalities = listOf(Modality.VIDEO), capabilities = caps, parameters = params)

    @Test fun keptGeneratorsStayInVideosWithEveryCompatibleProvider() {
        val kept = listOf(
            video("atlas_cloud", "bytedance/seedance-2.0-mini/image-to-video"),
            video("atlas_cloud", "bytedance/seedance-2.5/reference-to-video"),
            video("crun", "crun/bytedance/seedance2-0-fast-r2v"),
            video("runway", "runway/seedance2_5/i2v", providerModelId = "seedance2_5"),
            video("usr_byteplus-media", "custom/byteplus-media/seedance-2-0-i2v", providerModelId = "dreamina-seedance-2-0"),
            video("atlas_cloud", "kwaivgi/kling-video-o3-pro/reference-to-video"),
            video("crun", "crun/kling/v3-omni-i2v"),
            video("crun", "crun/kling-v3-pro"),
            video("atlas_cloud", "vidu/q3-pro/start-end-to-video"),
            video("atlas_cloud", "minimax/h3-developer/reference-to-video"),
            video("atlas_cloud", "pixverse/v6-max/image-to-video"),
            video("atlas_cloud", "xai/grok-imagine-video-v1.5/image-to-video"),
            video("atlas_cloud", "ltx/ltx-2.5-fast/image-to-video"),
            video("atlas_cloud", "black-forest-labs/flux-3/first-last-frame-to-video"),
            video("crun", "crun/agnes/video-2-5-r2v"),
            video("atlas_cloud", "nvidia/cosmos-3-super/image-to-video"))
        for (model in kept) {
            assertTrue(model.id, CuratedVideoSelection.isGenerator(model))
            assertTrue(model.id, CuratedVideoSelection.isAllowed(model))
            assertNull(model.id, toolCategory(model))
        }
        assertEquals(kept, CuratedVideoSelection.filter(kept))
    }

    @Test fun keptGeneratorsNeverSubmitFromTextAlone() {
        val image = AttachedMedia(uriString = "content://frame", mimeType = "image/png", slotType = MediaSlotType.PRIMARY_IMAGE)
        val clip = image.copy(mimeType = "video/mp4", slotType = MediaSlotType.VIDEO_REFERENCE)
        val audio = image.copy(mimeType = "audio/mpeg", slotType = MediaSlotType.REFERENCE_IMAGE)
        for (model in listOf(video("crun", "crun/kling-v3-pro"), video("crun", "crun/bytedance/seedance2-5-r2v"),
                video("atlas_cloud", "pixverse/v6/reference-to-video"))) {
            assertTrue(model.id, CuratedVideoSelection.needsVisualInput(model, emptyList()))
            assertTrue(model.id, CuratedVideoSelection.needsVisualInput(model, listOf(audio)))
            assertFalse(model.id, CuratedVideoSelection.needsVisualInput(model, listOf(image)))
            assertFalse(model.id, CuratedVideoSelection.needsVisualInput(model, listOf(clip)))
        }
        val image2 = video("atlas_cloud", "black-forest-labs/flux-2/edit").copy(modalities = listOf(Modality.IMAGE))
        assertFalse(CuratedVideoSelection.needsVisualInput(image2, emptyList()))
        assertFalse(CuratedVideoSelection.needsVisualInput(video("fal", "fal/any/text-to-video"), emptyList()))
    }

    @Test fun discardedModelsAndModalitiesLeaveNewGenerations() {
        val discarded = listOf(
            video("atlas_cloud", "bytedance/seedance-2.0/text-to-video", caps = listOf(ModelCapability.TEXT_TO_VIDEO)),
            video("crun", "crun/bytedance/seedance2-5-t2v", caps = listOf(ModelCapability.TEXT_TO_VIDEO)),
            video("usr_byteplus-media", "custom/byteplus-media/seedance-2-0", caps = listOf(ModelCapability.TEXT_TO_VIDEO)),
            video("atlas_cloud", "bytedance/seedance-2.5/draft-complete"),
            video("atlas_cloud", "luma/ray-3.2/image-to-video"),
            video("atlas_cloud", "luma/ray-3.2/reframe"),
            video("atlas_cloud", "kwaivgi/kling-v2.6-pro/image-to-video"),
            video("atlas_cloud", "kwaivgi/kling-v3.0-std/image-to-video"),
            video("atlas_cloud", "kwaivgi/kling-v4.0/video-edit"),
            video("atlas_cloud", "vidu/q3-turbo/image-to-video"),
            video("atlas_cloud", "vidu/q3-mix/reference-to-video"),
            video("atlas_cloud", "alibaba/wan-3.0-prime/image-to-video"),
            video("atlas_cloud", "alibaba/wan-2.6/image-to-video"),
            video("crun", "crun/wan/2-5-preview-i2v"),
            video("atlas_cloud", "minimax/h3-max/image-to-video"),
            video("atlas_cloud", "minimax/h3-developer/image-to-video"),
            video("atlas_cloud", "xai/grok-imagine-video-v1.5-lite/image-to-video"),
            video("crun", "crun/grok-imagine-video-1.5-preview"),
            video("atlas_cloud", "ltx/ltx-2.3-pro/image-to-video"),
            video("atlas_cloud", "black-forest-labs/flux-3/keyframes-to-video"),
            video("runway", "runway/gen4_turbo/i2v"),
            video("runway", "runway/seedance2/t2v", caps = listOf(ModelCapability.TEXT_TO_VIDEO)),
            video("atlas_cloud", "bytedance/avatar-omni-human-v1.5"),
            video("atlas_cloud", "veed/lipsync"),
            video("crun", "crun/vidu/lip-sync"),
            video("atlas_cloud", "atlascloud/video-extend"),
            video("atlas_cloud", "atlascloud/video-upscaler"),
            video("atlas_cloud", "tencent/video/upscaler"),
            video("atlas_cloud", "atlascloud/studio/video-clone"),
            video("atlas_cloud", "atlascloud/shot-reframe"),
            video("crun", "crun/video-face-swap"),
            video("crun", "crun/gif-face-swap-multi"),
            video("crun", "crun/kling/template"),
            video("crun", "crun/bytedance/dreamactor"))
        for (model in discarded) assertFalse(model.id, CuratedVideoSelection.isAllowed(model))
        assertTrue(CuratedVideoSelection.filter(discarded).isEmpty())
    }

    @Test fun nonVideoAndUnauditedProvidersAreNeverTouched() {
        val image = video("atlas_cloud", "bytedance/seedream-v5.0-pro").copy(modalities = listOf(Modality.IMAGE))
        val chat = video("atlas_cloud", "google/gemini-3-pro").copy(modalities = listOf(Modality.CHAT, Modality.VIDEO))
        val fal = video("fal", "fal-ai/some-video")
        val ownTemplate = video("usr_my-template", "custom/my-template/video")
        val all = listOf(image, chat, fal, ownTemplate)
        assertEquals(all, CuratedVideoSelection.filter(all))
    }

    @Test fun keptToolsMoveToHerramientasWithTheirCategory() {
        val expected = mapOf(
            video("atlas_cloud", "kwaivgi/kling-v3.0-pro/motion-control") to CuratedVideoSelection.MOTION,
            video("crun", "crun/wan/2-2-animate-replace") to CuratedVideoSelection.MOTION,
            video("runway", "runway/act_two/performance") to CuratedVideoSelection.MOTION,
            video("crun", "crun/video-face-swap-multi") to CuratedVideoSelection.EDIT,
            video("crun", "crun/video-watermark-remove") to CuratedVideoSelection.EDIT,
            video("runway", "runway/aleph2/v2v") to CuratedVideoSelection.EDIT,
            video("atlas_cloud", "black-forest-labs/flux-3/extend-video") to CuratedVideoSelection.EXTEND,
            video("atlas_cloud", "pixverse/v6/video-extend") to CuratedVideoSelection.EXTEND,
            video("atlas_cloud", "ltx/ltx-2.5-pro/audio-to-video") to CuratedVideoSelection.AVATARS,
            video("atlas_cloud", "bytedance/avatar-omni-human-v2.0") to CuratedVideoSelection.AVATARS,
            video("atlas_cloud", "heygen/avatar-5/digital-twin") to CuratedVideoSelection.AVATARS,
            video("atlas_cloud", "sync/lipsync-v3") to CuratedVideoSelection.LIPSYNC,
            video("runway", "runway/ruby/hdr") to CuratedVideoSelection.ENHANCE,
            video("atlas_cloud", "byteplus/video/upscaler") to CuratedVideoSelection.ENHANCE,
            video("atlas_cloud", "atlascloud/studio/tvc-maker") to CuratedVideoSelection.ADS,
            video("atlas_cloud", "atlascloud/studio/food-motion") to CuratedVideoSelection.ADS,
            video("atlas_cloud", "atlascloud/studio/trend-remix") to CuratedVideoSelection.ADS)
        for ((model, category) in expected) {
            assertTrue(model.id, CuratedVideoSelection.isAllowed(model))
            assertEquals(model.id, category, toolCategory(model))
        }
    }

    @Test fun newCategoriesCanBeChosenManuallyAndHaveTheirOwnIcon() {
        assertTrue(CatalogPlacements.toolCategories.containsAll(CuratedVideoSelection.categories))
        val icons = CatalogPlacements.toolCategories.map { toolCategoryIcon(it) }
        assertEquals(CatalogPlacements.toolCategories.size, icons.distinct().size)
        assertFalse(icons.any { it == Icons.Default.Build })
    }

    @Test fun bytePlusIsRecognisedDirectlyAndThroughCrunFlags() {
        val bytePlus = video("usr_byteplus-media", "custom/byteplus-media/seedance-2-5-i2v")
        val flag = ModelParameter("direct_byteplus", "BytePlus directo", ParameterType.BOOLEAN, "false", nativeType = "boolean")
        val fallback = ModelParameter("byteplus_fallback", "Respaldo BytePlus", ParameterType.BOOLEAN, "true", nativeType = "boolean")
        val crun = video("crun", "crun/bytedance/seedance2-5-i2v", params = listOf(flag))
        assertTrue(SeedanceBytePlusPolicy.reachesBytePlus(bytePlus, emptyMap()))
        assertFalse(SeedanceBytePlusPolicy.reachesBytePlus(crun, emptyMap()))
        assertTrue(SeedanceBytePlusPolicy.reachesBytePlus(crun, mapOf("direct_byteplus" to "true")))
        assertTrue(SeedanceBytePlusPolicy.reachesBytePlus(crun.copy(parameters = listOf(flag, fallback)), emptyMap()))
        assertFalse(SeedanceBytePlusPolicy.reachesBytePlus(crun.copy(parameters = listOf(flag, fallback)), mapOf("byteplus_fallback" to "false")))
        assertFalse(SeedanceBytePlusPolicy.reachesBytePlus(video("atlas_cloud", "bytedance/seedance-2.5/image-to-video"), emptyMap()))
        assertFalse(SeedanceBytePlusPolicy.reachesBytePlus(video("crun", "crun/kling-v3-pro"), mapOf("direct_byteplus" to "true")))
    }

    @Test fun automaticOrderLeavesBytePlusLastEvenWhenCheaperAndKeepsTheCeiling() {
        fun route(provider: String, cost: String, bytePlus: Boolean = false) =
            GenerationRoute(provider, "model", BigDecimal(cost), 1_000, bytePlusLast = bytePlus) { error("must not execute") }
        val routes = listOf(route("usr_byteplus-media", "0.10", true), route("crun-direct", "0.20", true),
            route("runway", "0.60"), route("atlas_cloud", "0.40"), route("usr_byteplus-over", "5.00", true))
        assertEquals(listOf("atlas_cloud", "runway", "usr_byteplus-media", "crun-direct"),
            RoutingSubmissionPolicy.ordered(routes, BigDecimal.ONE, 100).map { it.providerId })
    }

    @Test fun fluxFirstLastAndUgcProductImageUseRealUploadSlots() {
        val base = video("atlas_cloud", "black-forest-labs/flux-3/first-last-frame-to-video")
        val flux = NativeModelSchema.decode(base, """{"components":{"schemas":{"Input":{"properties":{
            "model":{"type":"string"},"prompt":{"type":"string"},
            "start_image_url":{"type":"string","format":"uri"},"end_image_url":{"type":"string","format":"uri"},
            "duration":{"type":"integer","enum":[5,10],"default":5}},
            "required":["model","prompt","start_image_url","end_image_url"]}}}}""")
        assertEquals(ModelInputMode.DUAL_FRAME, flux.model.inputMode)
        assertEquals("start_image_url", flux.model.firstFrameField)
        assertEquals("end_image_url", flux.model.lastFrameField)
        assertTrue(flux.unsupportedRequired.isEmpty())
        assertFalse(flux.model.parameters.any { it.key in setOf("start_image_url", "end_image_url") })

        val ugc = NativeModelSchema.decode(video("atlas_cloud", "atlascloud/studio/ugc-ad"), """{"components":{"schemas":{"Input":{"properties":{
            "model":{"type":"string"},"product_image":{"type":"string"},"model_image":{"type":"string"},
            "user_prompt":{"type":"string"}},"required":["model","product_image","user_prompt"]}}}}""")
        assertEquals("product_image", ugc.model.firstFrameField)
        assertTrue(ugc.model.isMediaRequired)
        assertTrue(ugc.unsupportedRequired.isEmpty())
        assertFalse(ugc.model.parameters.any { it.key == "product_image" })
    }
}
