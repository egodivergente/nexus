package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.crun.CrunCatalogSnapshot
import com.vidadomu.nexus.ui.models.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CatalogMediaClaimsTest {
    private fun referenceModel(output: String) = CrunCatalogSnapshot.decode(Json.parseToJsonElement("""
        {"providerModelId":"fixture/reference-editor","output":"$output",
         "properties":{"images":{"type":"array","maxItems":4,"items":{"type":"string"}}},
         "required":["images"]}
    """))!!

    @Test fun multipleImageReferencesDoNotImplyVideoFrames() {
        val model = referenceModel("IMAGE")
        assertEquals(4, model.maxImages)
        assertTrue(model.supportsReferenceImages)
        assertFalse(model.supportsFirstFrame)
        assertFalse(model.supportsLastFrame)
        assertFalse(modelGuide(model).strength.contains("fotograma"))
        assertFalse(usefulDescription(model).contains("fotograma"))
    }

    @Test fun videoReferencesDoNotImplyAnEndFrameWithoutItsField() {
        assertFalse(referenceModel("VIDEO").supportsLastFrame)
        val model = CrunCatalogSnapshot.decode(Json.parseToJsonElement("""
            {"providerModelId":"fixture/animate","output":"VIDEO",
             "properties":{"image":{"type":"string"},"end_image":{"type":"string"}}}
        """))!!
        assertTrue(model.supportsLastFrame)
    }

    @Test fun staleImageMetadataDoesNotAdvertiseVideoCapabilities() {
        val model = referenceModel("IMAGE").copy(supportsLastFrame=true,
            supportsAudio=true, description="first and last frame control with native audio")
        assertFalse(modelGuide(model).strength.contains("plano"))
        assertFalse(modelGuide(model).strength.contains("audio"))
        assertFalse(usefulDescription(model).contains("fotograma"))
    }

    @Test fun modelCardDeduplicatesIndividualClaimsAcrossVariants() {
        val base = referenceModel("IMAGE")
        val models = listOf(base.copy(description="typography and rapid generation"),
            base.copy(id="fixture/fast", description="rapid generation"))
        val highlight = modelGroupHighlight(models)!!
        assertEquals(2, highlight.split(" · ").size)
        assertEquals(1, Regex("Iteración rápida").findAll(highlight).count())
    }
}
