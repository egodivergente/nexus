package com.vidadomu.nexus.assistant

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AudioMeasurementsTest {
    @Test fun sineHasExpectedLevelAndBand() {
        val result=AudioMeasurements.measure(FloatArray(32000) {(.5*sin(2*PI*1000*it/16000)).toFloat()})
        assertEquals(-9.0309,result.rmsDbfs,.01)
        assertEquals(-6.0206,result.peakDbfs,.01)
        assertTrue(result.bandsPercent[1]>99)
        assertEquals(0.0,result.nearFullScalePercent,0.0)
    }
    @Test fun silenceIsFiniteAndHasNoSpectralEnergy() {
        val result=AudioMeasurements.measure(FloatArray(3000))
        assertEquals(-120.0,result.rmsDbfs,0.0)
        assertEquals(100.0,result.quietPercent,0.0)
        assertTrue(result.bandsPercent.all {it==0.0})
    }
}
