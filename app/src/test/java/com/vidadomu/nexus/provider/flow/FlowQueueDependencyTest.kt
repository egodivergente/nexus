package com.vidadomu.nexus.provider.flow

import com.vidadomu.nexus.core.model.*
import org.junit.Assert.*
import org.junit.Test

class FlowQueueDependencyTest {
    private fun job(status: GenerationStatus, uncertain: Boolean = false) = GenerationJob(id="previous",providerId="google_flow",modelId="current_project_image",modality=Modality.IMAGE,prompt="Test",status=status,parameters=if(uncertain) mapOf("__submission_uncertain" to "true") else emptyMap())
    @Test fun abandonedPreferenceDoesNotBlockFollowingOrders() {
        assertTrue(FlowQueueDependency.canProceed(null,false))
        assertFalse(FlowQueueDependency.canProceed(null,true))
    }
    @Test fun staleProcessingReleasesQueueWithoutChangingPriorJob() {
        val prior=job(GenerationStatus.PROCESSING).copy(startedAt=0)
        assertTrue(FlowQueueDependency.canProceed(prior,true,now=700_000,waitingSince=0))
        assertEquals(GenerationStatus.PROCESSING,prior.status)
        assertTrue(FlowQueueDependency.canProceed(null,true,now=700_000,waitingSince=0))
    }
    @Test fun interruptedOrdinarySubmissionIsVisibleAndNeverResubmitted() {
        val recovered=job(GenerationStatus.QUEUED).copy(providerId="crun").recoverInterruptedSubmission()
        assertEquals(GenerationStatus.FAILED,recovered.status)
        assertEquals("true",recovered.parameters["__submission_uncertain"])
        val tracked=job(GenerationStatus.QUEUED).copy(providerJobId="accepted")
        assertEquals(tracked,tracked.recoverInterruptedSubmission())
        assertEquals(GenerationStatus.FAILED,tracked.copy(startedAt=0).pauseExpiredTracking(1_800_001).status)
    }
    @Test fun terminalJobsReleaseQueueButAcceptedAndUncertainJobsKeepTheirTracking() {
        assertTrue(FlowQueueDependency.canProceed(job(GenerationStatus.COMPLETED),true))
        assertTrue(FlowQueueDependency.canProceed(job(GenerationStatus.FAILED),false))
        assertFalse(FlowQueueDependency.canProceed(job(GenerationStatus.GENERATING),true))
        assertTrue(FlowQueueDependency.canProceed(job(GenerationStatus.FAILED,true),true))
    }
}
