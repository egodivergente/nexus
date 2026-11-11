package com.vidadomu.nexus.cut.engine

import androidx.media3.common.C
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import com.vidadomu.nexus.cut.model.Rational

/** Constant speed. The provider interface already supports ramps (speed change points) for later. */
@UnstableApi
class ConstantSpeed(private val speed: Rational) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = speed.toDouble().toFloat()
    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}
