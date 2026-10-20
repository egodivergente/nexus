package com.vidadomu.nexus.provider.flow

import org.junit.Assert.*
import org.junit.Test

class FlowRetryPolicyTest {
    @Test fun onlyFailuresBeforeTheClickAreRetried() {
        assertTrue(FlowRetryPolicy.shouldRetry("flow_browser_unavailable", 0))
        assertTrue(FlowRetryPolicy.shouldRetry("flow_controls_unavailable", 1))
        // Un envío incierto pudo llegar a Flow: repetirlo podría duplicar la generación.
        assertFalse(FlowRetryPolicy.shouldRetry("submit_uncertain", 0))
        assertFalse(FlowRetryPolicy.shouldRetry("flow_submit_failed", 0))
        assertFalse(FlowRetryPolicy.shouldRetry("flow_browser_unavailable", -1))
        assertFalse(FlowRetryPolicy.shouldRetry("reference_not_found", 0))
        assertFalse(FlowRetryPolicy.shouldRetry(null, 0))
    }

    @Test fun retriesAreBounded() {
        assertFalse(FlowRetryPolicy.shouldRetry("flow_browser_unavailable", FlowRetryPolicy.MAX_RETRIES))
    }

    @Test fun eachAttemptIsADistinctValidRequest() {
        val ids = (0..FlowRetryPolicy.MAX_RETRIES).map { FlowRetryPolicy.requestId("job_0123456789abcdef", 2, it) }
        assertEquals("job_0123456789abcdef_2", ids.first())
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue(it.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{15,95}"))) }
    }

    @Test fun waitsUntilTheRetryTime() {
        assertTrue(FlowRetryPolicy.waiting(2_000L, now = 1_000L))
        assertFalse(FlowRetryPolicy.waiting(1_000L, now = 2_000L))
        assertFalse(FlowRetryPolicy.waiting(null))
    }
}
