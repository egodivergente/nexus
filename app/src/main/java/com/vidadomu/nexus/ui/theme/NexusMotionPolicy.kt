package com.vidadomu.nexus.ui.theme

import android.animation.ValueAnimator
import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Decides whether decorative, never-ending motion (pulses, glows, sweeps) may run. The textual
 * state is always rendered by the caller; this only gates the loop.
 */
object NexusMotionPolicy {
    /**
     * [processAnimatorsEnabled] is `ValueAnimator.areAnimatorsEnabled()`: it also turns false when
     * battery saver disables animations without touching the global setting.
     */
    fun animatorsEnabled(durationScale: Float, processAnimatorsEnabled: Boolean): Boolean =
        processAnimatorsEnabled && durationScale.isFinite() && durationScale > 0f

    fun isVisible(state: Lifecycle.State): Boolean = state.isAtLeast(Lifecycle.State.RESUMED)

    fun allowsInfiniteMotion(requested: Boolean, animatorsEnabled: Boolean, visible: Boolean): Boolean =
        requested && animatorsEnabled && visible
}

/** RESUMED tracked through lifecycle events, so hidden screens stop their loops without polling. */
@Composable
fun rememberLifecycleResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(NexusMotionPolicy.isVisible(owner.lifecycle.currentState)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> resumed = NexusMotionPolicy.isVisible(event.targetState) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

/**
 * Observes Settings.Global.ANIMATOR_DURATION_SCALE. [refreshKey] forces a re-read (e.g. on resume):
 * the process-wide animator scale can lag the setting and battery saver changes it without
 * notifying the observer.
 */
@Composable
fun rememberSystemAnimatorsEnabled(refreshKey: Any? = Unit): Boolean {
    val resolver = LocalContext.current.contentResolver
    var enabled by remember(resolver) { mutableStateOf(readAnimatorsEnabled(resolver)) }
    DisposableEffect(resolver, refreshKey) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = readAnimatorsEnabled(resolver) }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        enabled = readAnimatorsEnabled(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

@Composable
fun rememberNexusMotionAllowed(requested: Boolean = true): Boolean {
    val resumed = rememberLifecycleResumed()
    val animatorsEnabled = rememberSystemAnimatorsEnabled(refreshKey = resumed)
    val config by com.vidadomu.nexus.settings.RuntimePreferences.get(LocalContext.current).state.collectAsState()
    val appearance by rememberAppearancePreferences().selectionState()
    return NexusMotionPolicy.allowsInfiniteMotion(requested && !appearance.reduceMotion && config.value("ui.motion").toBoolean(), animatorsEnabled, resumed)
}

private fun readAnimatorsEnabled(resolver: ContentResolver): Boolean = NexusMotionPolicy.animatorsEnabled(
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
    ValueAnimator.areAnimatorsEnabled(),
)
