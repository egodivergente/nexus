package com.vidadomu.nexus

import com.vidadomu.nexus.provider.flow.FlowRetryPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowRejectedRequestTest {
    @Test fun bridgeRejectionsFailInsteadOfWaitingForever() {
        listOf(400, 413, 422).forEach { assertTrue("$it", FlowRetryPolicy.rejectedBeforeSending(it)) }
    }

    @Test fun uncertainOrFixableErrorsKeepTheirRecovery() {
        listOf(null, 401, 403, 404, 409, 500, 503).forEach { assertFalse("$it", FlowRetryPolicy.rejectedBeforeSending(it)) }
    }
}
