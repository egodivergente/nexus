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
    @Test fun terminalJobsReleaseQueueButAcceptedAndUncertainJobsKeepTheirTracking() {
        assertTrue(FlowQueueDependency.canProceed(job(GenerationStatus.COMPLETED),true))
        assertTrue(FlowQueueDependency.canProceed(job(GenerationStatus.FAILED),false))
        assertFalse(FlowQueueDependency.canProceed(job(GenerationStatus.GENERATING),true))
        assertFalse(FlowQueueDependency.canProceed(job(GenerationStatus.FAILED,true),true))
    }
}
