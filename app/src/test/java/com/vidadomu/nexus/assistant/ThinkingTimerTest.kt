package com.vidadomu.nexus.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class ThinkingTimerTest {
    @Test fun recyclerRecreationNeverRestartsTurnTime() {
        val sameTurnStartedAt = 10_000L
        assertEquals(72L, thinkingElapsedSeconds(82_000L, sameTurnStartedAt))
        // A freshly composed indicator uses the same ViewModel start, not another zero.
        assertEquals(157L, thinkingElapsedSeconds(167_000L, sameTurnStartedAt))
    }

    @Test fun absentStartOrInvalidClockDoesNotShowFakeElapsed() {
        assertEquals(0L, thinkingElapsedSeconds(180_000L, 0L))
        assertEquals(0L, thinkingElapsedSeconds(100L, 200L))
        assertEquals(0L, thinkingElapsedSeconds(3_000L, -1L))
    }
}
