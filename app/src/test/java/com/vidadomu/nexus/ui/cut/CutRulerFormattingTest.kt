package com.vidadomu.nexus.ui.cut

import com.vidadomu.nexus.cut.model.FrameRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CutRulerFormattingTest {
    @Test fun secondScaleRulerLabelsAreZeroPaddedTabularMinutesSeconds() {
        val fps24 = FrameRate(24)
        assertEquals("00:00", formatTimelineRulerLabel(0L, 1_000_000.0, fps24))
        assertEquals("00:05", formatTimelineRulerLabel(5_000_000L, 2_000_000.0, fps24))
        assertEquals("01:05", formatTimelineRulerLabel(65_000_000L, 5_000_000.0, fps24))
        assertEquals("1:00:05", formatTimelineRulerLabel(3_605_000_000L, 60_000_000.0, fps24))
    }

    @Test fun subSecondRulerLabelsShowTheSecondOnWholeSecondsAndOnlyTheFrameBetweenThem() {
        val fps24 = FrameRate(24)
        assertEquals("00:00", formatTimelineRulerLabel(0L, 250_000.0, fps24))
        assertEquals("00:01", formatTimelineRulerLabel(1_000_000L, 250_000.0, fps24))
        assertEquals("6f", formatTimelineRulerLabel(1_250_000L, 250_000.0, fps24))
        assertEquals("12f", formatTimelineRulerLabel(1_500_000L, 250_000.0, fps24))
        // Un tick que cae a menos de medio fotograma del segundo se rotula como el segundo.
        assertEquals("00:02", formatTimelineRulerLabel(1_990_000L, 250_000.0, fps24))
    }

    @Test fun everySubSecondStepDividesTheSecondSoWholeSecondsAlwaysCarryAMark() {
        for (fps in listOf(24, 25, 30, 50, 60)) {
            val rate = FrameRate(fps)
            // Del zoom más cerrado al más abierto (px por microsegundo), con una etiqueta de 140 px.
            var pxPerUs = 7200.0 / 1_000_000
            while (pxPerUs > 2.0 / 1_000_000) {
                val step = rulerStep(pxPerUs, 140f, rate)
                assertTrue("fps=$fps: la marca mayor cabe", step.majorUs * pxPerUs >= 140f || step.majorUs >= 3600e6)
                assertTrue("fps=$fps: menores >= 1", step.minorPerMajor >= 1)
                if (step.majorUs < 1e6) {
                    val frames = Math.round(step.majorUs * fps / 1e6)
                    assertEquals("fps=$fps: $frames fotogramas no dividen el segundo", 0L, fps % frames)
                    val minorFrames = frames.toDouble() / step.minorPerMajor
                    assertEquals("fps=$fps: las menores caen en fotogramas enteros", minorFrames, Math.rint(minorFrames), 1e-9)
                }
                pxPerUs /= 1.37
            }
        }
    }

    @Test fun rulerAt25FpsNeverLabelsFramesThatDriftAcrossSeconds() {
        // Antes, a 25 fps salían pasos de 10 fotogramas («10f, 20f, 5f, 15f…») que no caían en los segundos.
        val rate = FrameRate(25)
        val step = rulerStep(900.0 / 1_000_000, 140f, rate)
        assertEquals(RulerStep(200_000.0, 5), step)
        val labels = (0..10).map { formatTimelineRulerLabel(Math.round(it * step.majorUs), step.majorUs, rate) }
        assertEquals(listOf("00:00", "5f", "10f", "15f", "20f", "00:01", "5f", "10f", "15f", "20f", "00:02"), labels)
        // Con menos zoom, directamente segundos con cinco divisiones (5 fotogramas cada una).
        assertEquals(RulerStep(1e6, 5), rulerStep(360.0 / 1_000_000, 140f, rate))
    }

    @Test fun subSecondMarksDoNotDriftInLongTimelines() {
        // A 30 y 24 fps el fotograma no es un número entero de microsegundos: las marcas tienen que salir de
        // la fracción exacta del segundo para que, una hora después, los segundos sigan cayendo en su marca.
        for ((fps, zoom) in listOf(30 to 2400.0, 24 to 2400.0, 60 to 2400.0, 30 to 500.0)) {
            val rate = FrameRate(fps)
            val step = rulerStep(zoom / 1_000_000, 140f, rate)
            assertTrue("fps=$fps: paso por debajo del segundo", step.majorUs < 1e6)
            val perSecond = Math.round(1e6 / step.majorUs)
            assertEquals("fps=$fps: el paso divide el segundo exacto", 1e6, step.majorUs * perSecond, 1e-6)
            val hour = 3600L * perSecond
            val labels = (0..perSecond).map { formatTimelineRulerLabel(Math.round((hour + it) * step.majorUs), step.majorUs, rate) }
            assertEquals("fps=$fps", "1:00:00", labels.first())
            assertEquals("fps=$fps", "1:00:01", labels.last())
            val framesPerStep = fps / perSecond.toInt()
            assertEquals("fps=$fps", (1 until perSecond.toInt()).map { "${it * framesPerStep}f" }, labels.subList(1, labels.size - 1))
        }
    }

    @Test fun secondStepsUseRoundSubdivisions() {
        assertEquals(RulerStep(1e6, 5), rulerStep(200.0 / 1_000_000, 140f, FrameRate(25)))
        // A 24 fps un quinto de segundo no es un número entero de fotogramas: se divide en cuartos.
        assertEquals(RulerStep(1e6, 4), rulerStep(200.0 / 1_000_000, 140f, FrameRate(24)))
        assertEquals(RulerStep(5e6, 5), rulerStep(30.0 / 1_000_000, 140f, FrameRate(30)))
    }

    @Test fun thumbnailsDecodeInHeightStepsSoResizingDoesNotRefetchEveryPixel() {
        assertEquals(168, thumbnailDecodeHeight(147f, 3f))   // fila de vídeo de 52 dp (menos márgenes) a densidad 3
        assertEquals(168, thumbnailDecodeHeight(160f, 3f))
        assertEquals(192, thumbnailDecodeHeight(169f, 3f))
        assertTrue(thumbnailDecodeHeight(0f, 3f) >= 1)
    }
}
