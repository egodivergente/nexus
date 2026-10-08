package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.*
import org.junit.Assert.*
import org.junit.Test

class AtlasPredictionFailureTest {
    private val job = GenerationJob("local", "remote-123", "atlas_cloud", "minimax/h3/reference-to-video",
        Modality.VIDEO, "private prompt", status = GenerationStatus.PROCESSING,
        parameters = mapOf("__tracking_poll_error" to "true", "duration" to "8"))
    private fun response(id: String = "remote-123", status: String = "failed", error: String = "at most 9 reference images allowed (2013)") =
        """{"code":500,"data":{"id":"$id","status":"$status","error":"$error"}}"""
    @Test fun terminalFailureInsideHttp500EndsTrackingWithoutResubmission() {
        val result = requireNotNull(AtlasPredictionFailure.decode(job, response(), 100))
        assertEquals(GenerationStatus.FAILED, result.status)
        assertEquals(100L, result.completedAt)
        assertTrue(result.error!!.contains("máximo 9"))
        assertFalse(result.parameters.containsKey("__tracking_poll_error"))
        assertEquals("8", result.parameters["duration"])
        assertEquals(job.providerJobId, result.providerJobId)
        assertEquals(job.prompt, result.prompt)
        assertNull(result.actualCostUsd)
    }
    @Test fun failureForAnotherPredictionDoesNotChangeThisJob() {
        assertNull(AtlasPredictionFailure.decode(job, response(id = "other")))
    }
    @Test fun transientServerFailureDoesNotMarkProcessingAsFailed() {
        assertNull(AtlasPredictionFailure.decode(job, response(status = "processing")))
        assertNull(AtlasPredictionFailure.decode(job, """{"code":500,"message":"server error"}"""))
        assertNull(AtlasPredictionFailure.decode(job, "<html>gateway error</html>"))
        assertNull(AtlasPredictionFailure.decode(job, response(error = "")))
    }
    @Test fun cancellationRequiresMatchingPrediction() {
        assertEquals(GenerationStatus.CANCELLED, AtlasPredictionFailure.decode(job, response(status = "cancelled", error = ""))!!.status)
    }
    @Test fun h3SchemaOmissionUsesConfirmedImageLimitAndOtherModelsKeepTheirContract() {
        val schema = """{"components":{"schemas":{"Input":{"properties":{"refers":{"type":"array","items":{"type":"object","properties":{"url":{"type":"string"}}},"minItems":1}},"required":["refers"]}}}}"""
        val base = AtlasModelCatalog.curatedModels.first()
        val h3 = NativeModelSchema.decode(base.copy(id = "minimax/h3/reference-to-video"), schema).model
        assertEquals(9, h3.maxImages)
        assertEquals(Int.MAX_VALUE, NativeModelSchema.decode(base.copy(id = "another/reference"), schema).model.maxImages)
        val media = List(10) { MediaInput(Modality.IMAGE, "content://ref/$it", "image/png",
            slotType = MediaSlotType.REFERENCE_IMAGE, fieldKey = "refers") }
        val error = runCatching { NativeGenerationRequest.build(h3, Modality.VIDEO, "prompt", emptyMap(), media, "local") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(9, NativeGenerationRequest.build(h3, Modality.VIDEO, "prompt", emptyMap(), media.take(9), "local").mediaInputs.size)
    }
}
