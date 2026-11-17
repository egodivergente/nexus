package com.vidadomu.nexus.cut.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LoudnessMeterTest {
    private fun sine(seconds: Double, rate: Int, amp: Float, channels: Int = 1): FloatArray {
        val frames = (seconds * rate).toInt()
        return FloatArray(frames * channels) { i -> (amp * sin(2 * PI * 1000.0 * (i / channels) / rate)).toFloat() }
    }

    @Test fun sineWindowsMeasureTheirRmsInDbfs() {
        val m = LoudnessMeter(0L, 1_000_000L, 100_000L, 10)
        val pcm = sine(1.0, 48_000, 0.5f)
        m.feed(0L, pcm, pcm.size, 1, 48_000)
        val env = m.envelope()
        assertEquals(10, env.dbfs.size)
        // RMS de un seno de amplitud 0,5 = 0,354 → −9,03 dBFS.
        env.dbfs.forEach { assertEquals(-9.03, it, 0.1) }
        assertEquals(1_000_000L, env.toUs)
    }

    @Test fun silenceIsTheFloorAndChannelsAreMixed() {
        val m = LoudnessMeter(0L, 200_000L, 100_000L, 2)
        val frames = 9_600
        val stereo = FloatArray(frames * 2) { i -> if (i % 2 == 0) .5f else -.5f }
        m.feed(0L, stereo, stereo.size, 2, 48_000)
        m.envelope().dbfs.forEach { assertEquals(LoudnessMeter.FLOOR_DB, it, 0.0) }
    }

    @Test fun samplesOutsideTheRangeAreIgnoredAndMarkTheEnd() {
        val m = LoudnessMeter(500_000L, 700_000L, 100_000L, 2)
        val pcm = sine(1.0, 48_000, 0.25f)
        m.feed(0L, pcm, pcm.size, 1, 48_000)
        assertTrue(m.full)
        val env = m.envelope()
        assertEquals(500_000L, env.fromUs)
        assertEquals(2, env.dbfs.size)
        env.dbfs.forEach { assertEquals(-15.05, it, 0.1) }
    }
}
