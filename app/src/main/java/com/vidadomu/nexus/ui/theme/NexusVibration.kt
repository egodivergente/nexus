package com.vidadomu.nexus.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Direct vibration for gestures that need a clear feel (resizing a logo). The platform "touch feedback" path can be
 * silent on phones where that system setting or its intensity is low; this one asks the vibrator itself, still
 * respecting the app's own «Respuesta háptica» switch.
 */
object NexusVibration {
    enum class Kind { TICK, CLICK, HEAVY }

    fun play(context: Context, kind: Kind) {
        if (!com.vidadomu.nexus.settings.RuntimePreferences.get(context).bool("ui.haptics")) return
        val vibrator: Vibrator = (if (Build.VERSION.SDK_INT >= 31)
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator) ?: return
        if (!vibrator.hasVibrator()) return
        val effect = when {
            Build.VERSION.SDK_INT >= 29 -> VibrationEffect.createPredefined(when (kind) {
                Kind.TICK -> VibrationEffect.EFFECT_TICK
                Kind.CLICK -> VibrationEffect.EFFECT_CLICK
                Kind.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
            })
            else -> VibrationEffect.createOneShot(when (kind) { Kind.TICK -> 8L; Kind.CLICK -> 18L; Kind.HEAVY -> 32L }, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        runCatching { vibrator.vibrate(effect) }
    }
}
