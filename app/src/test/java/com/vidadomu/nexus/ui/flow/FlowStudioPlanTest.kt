package com.vidadomu.nexus.ui.flow

import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.MediaSlotType
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.provider.flow.FlowBridgeProvider
import org.junit.Assert.*
import org.junit.Test

class FlowStudioPlanTest {
    @Test fun requestUsesExistingFlowRouteAndClampsCount() {
        val request = FlowStudioPlan.request(FlowStudioDraft(prompt = "  luz  ", count = 9, modality = Modality.IMAGE), emptyList(), id = "id")
        assertEquals(FlowBridgeProvider.PROVIDER_ID, request.providerId)
        assertEquals(FlowBridgeProvider.CURRENT_PROJECT_MODEL, request.modelId)
        assertEquals("image", request.parameters[FlowBridgeProvider.EXPECTED_OUTPUT_KEY])
        assertEquals("4", request.parameters["count"])
        assertEquals("luz", request.prompt)
        assertNull(request.parameters["mode"])
    }

    @Test fun videoModeFollowsReferences() {
        val refs = FlowStudioPlan.mediaInputs(listOf(
            FlowStudioReference("content://a", "image/png", "a.png", "ref_" + "a".repeat(64)),
            FlowStudioReference("content://b", "image/png", "b.png")))
        assertEquals(1, refs.size)
        assertEquals(MediaSlotType.REFERENCE_IMAGE, refs.single().slotType)
        assertEquals("Referencias", FlowStudioPlan.request(FlowStudioDraft(prompt = "x", modality = Modality.VIDEO), refs).parameters["mode"])
        assertEquals("Texto", FlowStudioPlan.request(FlowStudioDraft(prompt = "x", modality = Modality.VIDEO), emptyList()).parameters["mode"])
    }

    @Test fun onlyValidDraftsAreSendable() {
        val drafts = listOf(FlowStudioDraft(prompt = ""), FlowStudioDraft(prompt = "ok"),
            FlowStudioDraft(prompt = "largo".repeat(10)), FlowStudioDraft(prompt = "v", modality = Modality.VIDEO))
        assertEquals(listOf("ok"), FlowStudioPlan.sendable(drafts, 10, setOf(Modality.IMAGE)).map { it.prompt })
    }

    @Test fun gridShowsRequestedCellsAndRealStatus() {
        val job = GenerationJob(id = "j", providerId = "google_flow", modelId = "m", modality = Modality.IMAGE, prompt = "p",
            parameters = mapOf("count" to "3"), status = GenerationStatus.GENERATING)
        assertEquals(3, FlowStudioPlan.expectedCells(job))
        assertTrue(FlowStudioPlan.isActive(job.status))
        assertFalse(FlowStudioPlan.isActive(GenerationStatus.FAILED))
        assertEquals("Generando", FlowStudioPlan.statusLabel(job.status))
    }

    @Test fun trackedIdsRoundTrip() {
        assertEquals(listOf("a", "b"), FlowStudioPlan.decodeIds(FlowStudioPlan.encodeIds(listOf("a", "b"))))
        assertTrue(FlowStudioPlan.decodeIds("").isEmpty())
    }
}
