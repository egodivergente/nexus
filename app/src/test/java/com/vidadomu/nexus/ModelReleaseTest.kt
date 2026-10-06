package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.models.*
import org.junit.Assert.*
import org.junit.Test

class ModelReleaseTest {
    private fun model(id:String, providerId:String?=null)=NexusModel(id=id,displayName=id,providerId="atlas_cloud",providerModelId=providerId,organization="test",modalities=listOf(Modality.VIDEO),capabilities=emptyList(),parameters=emptyList())
    @Test fun exactVersionAndProviderIdentifierWin() {
        assertEquals("2026-02",modelRelease(model("crun-alias", "kling/v3-omni-t2v"))?.month)
        assertNull(modelRelease(model("crun-alias", "kling/v3-motion-control")))
        assertEquals("2026-02",modelRelease(model("local", "kwaivgi/kling-v3.0-pro/text-to-video"))?.month)
        assertEquals("2026-07",modelRelease(model("bytedance/seedance-2.5/image-to-video"))?.month)
        assertNull(modelRelease(model("bytedance/seedance-2.50/image-to-video")))
        assertNull(modelRelease(model("google/nano-banana-2-lite/edit")))
        assertNull(modelRelease(model("google/veo3.1-lite/text-to-video")))
    }
    @Test fun mixedAndUnknownVariantsNeverInheritAnotherDate() {
        val known=model("google/nano-banana/text-to-image")
        assertEquals("Salida · ago 2025",groupReleaseLabel(listOf(known)))
        assertEquals("Fechas según variante",groupReleaseLabel(listOf(known,model("google/nano-banana-pro/edit"))))
        assertEquals("Fechas según variante",groupReleaseLabel(listOf(known,model("unknown"))))
        assertEquals("Fecha no verificada",groupReleaseLabel(listOf(model("unknown"))))
    }
}
