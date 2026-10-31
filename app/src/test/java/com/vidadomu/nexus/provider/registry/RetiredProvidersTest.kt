package com.vidadomu.nexus.provider.registry

import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationOutput
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.pauseExpiredTracking
import com.vidadomu.nexus.core.model.recoverInterruptedSubmission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RetiredProvidersTest {
    private fun job(status: GenerationStatus, providerId: String = RetiredProviders.GOOGLE_FLOW, remote: String? = "queue_1") =
        GenerationJob(id = "j", providerId = providerId, providerJobId = remote, modelId = "current_project_video",
            modality = Modality.VIDEO, prompt = "Escena", status = status, parameters = mapOf("__project_id" to "p"),
            outputs = listOf(GenerationOutput("o", "j", Modality.VIDEO, "https://example.invalid/o.mp4")))

    @Test fun onlyGoogleFlowIsRetired() {
        assertTrue(RetiredProviders.isRetired("google_flow"))
        for (id in listOf("atlas_cloud", "crun", "runway", "fal", "mock_provider", "google", "flow", "", null))
            assertFalse("$id", RetiredProviders.isRetired(id))
    }

    @Test fun activeRetiredJobsStopTrackingAndKeepEverything() {
        for (status in listOf(GenerationStatus.QUEUED, GenerationStatus.UPLOADING, GenerationStatus.GENERATING,
                GenerationStatus.PROCESSING, GenerationStatus.RENDERING)) {
            val original = job(status)
            val paused = RetiredProviders.pauseTracking(original)
            assertEquals(GenerationStatus.FAILED, paused.status)
            assertEquals("true", paused.parameters["__tracking_paused"])
            assertEquals(status.name, paused.parameters[RetiredProviders.PREVIOUS_STATUS_KEY])
            assertEquals(RetiredProviders.TRACKING_MESSAGE, paused.error)
            assertEquals("p", paused.parameters["__project_id"])
            assertEquals(original.prompt, paused.prompt)
            assertEquals(original.providerJobId, paused.providerJobId)
            assertEquals(original.outputs, paused.outputs)
        }
    }

    @Test fun activeRetiredJobWithoutRemoteIdKeepsItsPreviousStatus() {
        for (status in listOf(GenerationStatus.QUEUED, GenerationStatus.UPLOADING, GenerationStatus.GENERATING)) {
            val paused = RetiredProviders.pauseTracking(job(status, remote = null))
            assertEquals(GenerationStatus.FAILED, paused.status)
            assertEquals(status.name, paused.parameters[RetiredProviders.PREVIOUS_STATUS_KEY])
            assertEquals("true", paused.parameters["__tracking_paused"])
            assertEquals(null, paused.providerJobId)
            assertFalse("__submission_uncertain" in paused.parameters)
            assertSame(paused, RetiredProviders.pauseTracking(paused))
        }
    }

    @Test fun finishedRetiredJobsAreUntouched() {
        for (status in listOf(GenerationStatus.COMPLETED, GenerationStatus.FAILED, GenerationStatus.CANCELLED)) {
            val original = job(status)
            assertSame(original, RetiredProviders.pauseTracking(original))
        }
    }

    @Test fun otherProvidersAreNeverPaused() {
        val active = job(GenerationStatus.GENERATING, providerId = "atlas_cloud", remote = "remote")
        assertSame(active, RetiredProviders.pauseTracking(active))
    }

    @Test fun pausingIsIdempotent() {
        val once = RetiredProviders.pauseTracking(job(GenerationStatus.GENERATING))
        assertSame(once, RetiredProviders.pauseTracking(once))
    }

    @Test fun messagesNeverInviteResubmittingToFlow() {
        for (text in listOf(RetiredProviders.SUBMIT_MESSAGE, RetiredProviders.TRACKING_MESSAGE)) {
            assertTrue(text.contains("ya no está disponible"))
            assertFalse(text.contains("Tailscale"))
            assertFalse(text.contains("vuelve a generar", ignoreCase = true))
        }
    }

    // Kept from the removed Flow queue tests: generic recovery that applies to every provider.
    @Test fun interruptedOrdinarySubmissionIsVisibleAndNeverResubmitted() {
        val recovered = job(GenerationStatus.QUEUED, providerId = "crun", remote = null).recoverInterruptedSubmission()
        assertEquals(GenerationStatus.FAILED, recovered.status)
        assertEquals("true", recovered.parameters["__submission_uncertain"])
        val tracked = job(GenerationStatus.QUEUED, providerId = "crun", remote = "accepted")
        assertEquals(tracked, tracked.recoverInterruptedSubmission())
        assertEquals(GenerationStatus.FAILED, tracked.copy(startedAt = 0).pauseExpiredTracking(1_800_001).status)
    }
}
