package com.vidadomu.nexus.sync

import androidx.work.WorkInfo.State.BLOCKED
import androidx.work.WorkInfo.State.CANCELLED
import androidx.work.WorkInfo.State.ENQUEUED
import androidx.work.WorkInfo.State.FAILED
import androidx.work.WorkInfo.State.RUNNING
import androidx.work.WorkInfo.State.SUCCEEDED
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveRetryOnResumeTest {
    @Test fun aRetryThatIsOnlyWaitingIsReplacedWhenTheUserReturns() {
        assertTrue(replacesWaitingRetry(listOf(ENQUEUED to 9)))
        assertTrue(replacesWaitingRetry(listOf(SUCCEEDED to 0,ENQUEUED to 1)))
    }
    @Test fun runningWorkIsNeverReplaced() {
        assertFalse(replacesWaitingRetry(listOf(RUNNING to 3)))
        assertFalse(replacesWaitingRetry(listOf(ENQUEUED to 2,RUNNING to 0)))
    }
    @Test fun workThatHasNotFailedYetIsKept() {
        assertFalse(replacesWaitingRetry(emptyList()))
        assertFalse(replacesWaitingRetry(listOf(ENQUEUED to 0)))
        assertFalse(replacesWaitingRetry(listOf(SUCCEEDED to 4,FAILED to 2,CANCELLED to 1,BLOCKED to 0)))
    }
}
