package com.vidadomu.nexus.cut.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** The arithmetic that keeps an audio lane from drifting cut after cut (see ClipAudioProcessor). */
class ItemFramesTest {
    @Test
    fun consecutiveItemsAddUpToTheWholeSpanAtAnySampleRate() {
        // Cuts on a 25 fps grid and off it, like the ones made by hand and by the agent.
        val cuts = longArrayOf(0, 600_000, 1_480_000, 2_480_000, 3_360_000, 3_840_000, 4_480_001, 5_479_999, 6_480_000, 7_171_355)
        for (rate in intArrayOf(8_000, 22_050, 44_100, 48_000, 96_000)) {
            var sum = 0L
            for (i in 0 until cuts.size - 1) sum += itemFrames(cuts[i], cuts[i + 1] - cuts[i], rate)
            assertEquals("rate $rate", itemFrames(0, cuts.last(), rate), sum)
        }
    }

    @Test
    fun aThousandOddCutsLoseNoFrame() {
        val rate = 44_100
        var at = 0L
        var sum = 0L
        repeat(1_000) { i ->
            val len = 333_333L + (i % 7) * 1_111L
            sum += itemFrames(at, len, rate)
            at += len
        }
        assertEquals(itemFrames(0, at, rate), sum)
    }

    @Test
    fun anItemLastsItsDurationInFrames() {
        assertEquals(48_000L, itemFrames(0, 1_000_000, 48_000))
        assertEquals(480L, itemFrames(3_840_000, 10_000, 48_000))
        assertEquals(0L, itemFrames(5, 0, 48_000))
        assertEquals(0L, itemFrames(5, 1_000_000, 0))
    }

    @Test
    fun onlyTheFramesThatStillFitAreKept() {
        // One AAC packet too many (1024 frames) at the end of a 3,84 s item: only 512 fit.
        assertEquals(512, framesToKeep(1_024, produced = 183_808, maxFrames = 184_320))
        assertEquals(1_024, framesToKeep(1_024, produced = 0, maxFrames = 184_320))
        assertEquals(0, framesToKeep(1_024, produced = 184_320, maxFrames = 184_320))
        assertEquals(0, framesToKeep(1_024, produced = 190_000, maxFrames = 184_320))
        // No length to keep: everything passes.
        assertEquals(1_024, framesToKeep(1_024, produced = 1L shl 40, maxFrames = Long.MAX_VALUE))
    }
}
