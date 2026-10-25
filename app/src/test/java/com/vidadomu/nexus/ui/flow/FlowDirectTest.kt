package com.vidadomu.nexus.ui.flow

import com.vidadomu.nexus.core.model.MediaInput
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.provider.flow.FlowDirect
import com.vidadomu.nexus.provider.flow.FlowDirectCapabilities
import com.vidadomu.nexus.provider.flow.FlowDirectMode
import com.vidadomu.nexus.provider.flow.FlowDirectModel
import com.vidadomu.nexus.provider.flow.FlowDirectSelection
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/** Mirrors what the bridge's /capabilities returns for the verified contract (see tools/flow-linux-probe/test_flow_direct.py). */
class FlowDirectTest {
    private val caps = FlowDirectCapabilities(true, listOf(
        FlowDirectModel("veo_fast", "video", listOf(
            FlowDirectMode("t2v", listOf("16:9", "9:16"), listOf(4, 6, 8), listOf("720p")),
            FlowDirectMode("i2v", listOf("16:9", "9:16"), listOf(4, 6, 8), listOf("720p"), endFrame = true),
            FlowDirectMode("r2v", listOf("16:9", "9:16"), listOf(8), listOf("720p"), maxReferences = 3)), listOf(1, 2, 3, 4)),
        FlowDirectModel("veo_quality", "video", listOf(FlowDirectMode("t2v", listOf("16:9", "9:16"), listOf(8), listOf("720p"))), listOf(1, 2, 3, 4)),
        FlowDirectModel("nano-pro", "image", listOf(FlowDirectMode("t2i", listOf("16:9", "9:16", "1:1"), maxReferences = 10)), listOf(1, 2, 3, 4))))

    private val ref = "ref_" + "a".repeat(64)

    @Test fun defaultsAre16by9AndValid() {
        val video = FlowDirectSettings.default("video").normalized(caps, "video")!!
        assertEquals("16:9", video.aspect)
        assertNull(FlowDirect.validate(video.selection(4, null, null, emptyList()), caps))
    }

    @Test fun normalizingKeepsValidChoicesAndRepairsTheRest() {
        val quality = FlowDirectSettings("veo_quality", "i2v", "9:16", 4).normalized(caps, "video")!!
        assertEquals("t2v", quality.mode)          // Veo Quality has no frame mode in this capability set.
        assertEquals("9:16", quality.aspect)       // still valid: kept.
        assertEquals(8, quality.durationSeconds)   // 4 s is not offered: the only valid value.
    }

    @Test fun framesNeedAStartAndOnlyFrameModeUsesThem() {
        val frames = FlowDirectSelection("veo_fast", "i2v", "16:9", 6)
        assertEquals("Añade el fotograma inicial.", FlowDirect.validate(frames, caps))
        assertNull(FlowDirect.validate(frames.copy(initialFrameId = ref, endFrameId = ref.replace('a', 'b')), caps))
        assertNotNull(FlowDirect.validate(FlowDirectSelection("veo_fast", "t2v", initialFrameId = ref), caps))
    }

    @Test fun limitsComeFromCapabilitiesNotFromTheApp() {
        assertNotNull(FlowDirect.validate(FlowDirectSelection("veo_fast", "r2v", durationSeconds = 8, referenceIds = List(4) { ref + it }), caps))
        assertNotNull(FlowDirect.validate(FlowDirectSelection("veo_fast", "t2v", durationSeconds = 5), caps))
        assertNotNull(FlowDirect.validate(FlowDirectSelection("veo_fast", "t2v", count = 5), caps))
        assertNotNull(FlowDirect.validate(FlowDirectSelection("veo_fast", "t2v", aspect = "1:1"), caps))
    }

    @Test fun argumentsCarryOnlyContractFieldsAndTheConfirmedCost() {
        val args = FlowDirect.arguments(FlowDirectSelection("veo_fast", "i2v", "9:16", 6, "720p", 4, ref), "un faro", "req_12345678", 0, withCost = true)
        assertEquals(setOf("model", "aspect", "mode", "duration", "resolution", "count", "initial_frame", "prompt", "request_id", "cost"), args.keys)
        assertEquals("0", args.getValue("cost").jsonPrimitive.content)
        val unknownCost = FlowDirect.arguments(FlowDirectSelection("nano-pro", "t2i"), withCost = true)
        assertEquals("null", unknownCost.getValue("cost").toString())
        assertFalse("mode" in unknownCost)
    }

    @Test fun requestsTagTheRouteAndFrameRoles() {
        val start = MediaInput(Modality.IMAGE, "file:///a.png", "image/png", remoteUrl = ref)
        val request = FlowDirectPlan.request(FlowStudioDraft(prompt = "x", count = 4, modality = Modality.VIDEO),
            FlowDirectSettings("veo_fast", "i2v", "16:9", 8, "720p"), emptyList(), start, null, quotedCost = 0)
        assertEquals(FlowDirect.DIRECT, request.parameters[FlowDirect.ROUTE])
        assertEquals("0", request.parameters[FlowDirect.COST])
        assertEquals("initial_frame", request.mediaInputs.single().fieldKey)
        val unknown = FlowDirectPlan.request(FlowStudioDraft(prompt = "x"), FlowDirectSettings.default("image"), emptyList(), null, null, null)
        assertEquals("null", unknown.parameters[FlowDirect.COST])
    }

    @Test fun onlyDirectOutputsOfferFlowQuality() {
        assertEquals("req_12345678", FlowDirect.sourceRequest("flow-direct://req_12345678/0"))
        assertNull(FlowDirect.sourceRequest("flow://a.job/0"))
        assertNull(FlowDirect.sourceRequest("flow-direct://../x/0"))
    }

    @Test fun confirmationNeverCallsAnUnknownAmountFree() {
        val quote = com.vidadomu.nexus.provider.flow.FlowDirectQuote(null, "flow_account", "", 0)
        assertFalse(quote.included)
        val text = FlowDirectConfirmation.summary(FlowDirectConfirmation(listOf(FlowDirectConfirmation.Item(FlowStudioDraft(prompt = "x", count = 4, modality = Modality.VIDEO),
            FlowDirectSettings("veo_fast", "t2v", "16:9", 8), quote)), emptyList(), null, null))
        assertTrue(text, "no muestra el importe" in text)
        assertTrue(text, "×4" in text && "16:9" in text && "8 s" in text)
    }
}
