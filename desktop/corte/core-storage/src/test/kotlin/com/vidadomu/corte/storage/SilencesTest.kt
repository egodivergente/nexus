package com.vidadomu.corte.storage

import com.vidadomu.corte.model.Clip
import com.vidadomu.corte.model.ClipKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SilencesTest {
    /** 1 s de voz, 1 s de silencio, 1 s de voz a 48 kHz. */
    private fun wave(): Waveform {
        val b = WaveformBuilder(48_000, 1)
        val loud = ShortArray(48_000) { if (it % 2 == 0) 12_000 else -12_000 }
        val quiet = ShortArray(48_000) { 20 }
        b.feed(loud); b.feed(quiet); b.feed(loud)
        return b.build()
    }

    @Test fun `encuentra el silencio central en la fuente`() {
        val r = Silences.inSource(wave(), thresholdDb = -40.0, minUs = 400_000)
        assertEquals(1, r.size)
        assertTrue("inicio ${r[0].startUs}", r[0].startUs in 990_000L..1_010_000L)
        assertTrue("fin ${r[0].endUs}", r[0].endUs in 1_990_000L..2_010_000L)
    }

    @Test fun `en la linea de tiempo respeta posicion del clip y margen`() {
        val clip = Clip("c", ClipKind.AUDIO, "m", timelineStartUs = 5_000_000, durationUs = 3_000_000)
        val r = Silences.inTimeline(clip, wave(), padUs = 100_000)
        assertEquals(1, r.size)
        assertTrue(r[0].startUs in 6_090_000L..6_110_000L)
        assertTrue(r[0].endUs in 6_890_000L..6_910_000L)
    }

    @Test fun `un silencio mas corto que el minimo no cuenta`() {
        assertTrue(Silences.inSource(wave(), minUs = 1_500_000).isEmpty())
    }
}
