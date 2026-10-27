package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SubmissionSafetyTest {
    private val request = GenerationRequest("one", "provider", "exact-pro", Modality.VIDEO, "prompt", parameters = mapOf("resolution" to "1080p"))
    private val accepted = GenerationJob("one", "remote", "provider", "exact-pro", Modality.VIDEO, "prompt",
        parameters = request.parameters + (SubmissionSafety.DIGEST_KEY to SubmissionSafety.digest(request)))

    @Test fun parsingTimeoutAndServerFailuresAreUncertain() {
        listOf(IllegalArgumentException("invalid response after POST"), java.net.SocketTimeoutException(),
            ProviderError("server", httpStatusCode = 503), ProviderError("conflict", httpStatusCode = 409), null).forEach {
            assertFalse(SubmissionSafety.definitelyRejected(it))
        }
    }
    @Test fun explicitQuotaBalanceAndUnavailableEndpointRejectionsAreDefinite() {
        for (status in listOf(400,401,402,403,404,422,429)) assertTrue(SubmissionSafety.definitelyRejected(ProviderError("rejected", httpStatusCode = status)))
    }
    @Test fun repeatOfAcceptedIdReturnsTheOriginalReceipt() {
        assertEquals(accepted, SubmissionSafety.reuse(accepted, request).getOrThrow())
    }
    @Test fun idCannotBeReusedForAnotherVariantOrConfiguration() {
        assertTrue(SubmissionSafety.reuse(accepted, request.copy(modelId = "exact-turbo")).isFailure)
        assertTrue(SubmissionSafety.reuse(accepted, request.copy(parameters = mapOf("resolution" to "720p"))).isFailure)
        assertTrue(SubmissionSafety.reuse(accepted, request.copy(providerId = "other")).isFailure)
    }
    @Test fun uncertainOrTerminalIdNeverPermitsAnotherPost() {
        assertTrue(SubmissionSafety.reuse(accepted.copy(parameters = accepted.parameters + ("__submission_uncertain" to "true")), request).isFailure)
        for (status in listOf(GenerationStatus.FAILED, GenerationStatus.CANCELLED))
            assertTrue(SubmissionSafety.reuse(accepted.copy(status = status), request).isFailure)
    }
    @Test fun allReferencesAndRolesBelongToTheReceipt() {
        val media = MediaInput(Modality.IMAGE, "file:///first.png", "image/png")
        assertNotEquals(SubmissionSafety.digest(request), SubmissionSafety.digest(request.copy(mediaInputs = listOf(media))))
        assertNotEquals(SubmissionSafety.digest(request.copy(mediaInputs = listOf(media))),
            SubmissionSafety.digest(request.copy(mediaInputs = listOf(media.copy(slotType = MediaSlotType.LAST_FRAME)))))
    }
    @Test fun creationTimeAndMapInsertionOrderDoNotChangeTheReceipt() {
        assertEquals(SubmissionSafety.digest(request), SubmissionSafety.digest(request.copy(createdAt = request.createdAt + 1)))
        assertEquals(SubmissionSafety.digest(request.copy(parameters = linkedMapOf("a" to "1", "b" to "2"))),
            SubmissionSafety.digest(request.copy(parameters = linkedMapOf("b" to "2", "a" to "1"))))
    }
}
