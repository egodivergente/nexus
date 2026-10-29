package com.vidadomu.nexus.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal const val AMBIENT_FRAME_INTERVAL_MS = 80L
internal const val AMBIENT_CYCLE_MS = 120_000L

/** Decoration is capped; text and controls retain their opaque theme surfaces. */
internal fun ambientOpacity(mode: AmbientMode, intensity: Float, isDark: Boolean): Float {
    val level = if (intensity.isFinite()) intensity.coerceIn(0f, 1f) else 0f
    val maximum = when (mode) {
        AmbientMode.STATIC -> if (isDark) .20f else .16f
        AmbientMode.SUBTLE -> if (isDark) .25f else .20f
        AmbientMode.IMMERSIVE -> if (isDark) .38f else .30f
    }
    return maximum * level
}

internal fun ambientMotionRequested(mode: AmbientMode, intensity: Float, preview: Boolean, powerSaving: Boolean): Boolean =
    mode != AmbientMode.STATIC && intensity.isFinite() && intensity > 0f && !preview && !powerSaving

internal fun ambientPhase(elapsedMs: Long): Float =
    elapsedMs.coerceAtLeast(0L).toFloat() / AMBIENT_CYCLE_MS

private val StillAmbientPhase = object : State<Float> { override val value = 0f }
private val LocalAmbientPhase = staticCompositionLocalOf<State<Float>> { StillAmbientPhase }

/** One clock for the app and selected theme card. No renderer starts its own animation. */
@Composable
internal fun ProvideNexusAmbientPhase(mode: AmbientMode, intensity: Float, enabled: Boolean, content: @Composable () -> Unit) {
    val saving = rememberAmbientPowerSaving()
    val moving = rememberNexusMotionAllowed(enabled && ambientMotionRequested(mode, intensity, preview = false, powerSaving = saving))
    val phase = remember { mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(moving) {
        if (moving) {
            val started = SystemClock.uptimeMillis()
            val previous = phase.floatValue
            while (isActive) {
                phase.floatValue = previous + ambientPhase(SystemClock.uptimeMillis() - started)
                // An 80 ms minimum interval caps decorative invalidation at 12.5 Hz.
                delay(AMBIENT_FRAME_INTERVAL_MS)
            }
        }
    }
    CompositionLocalProvider(LocalAmbientPhase provides phase, content = content)
}

@Composable
private fun rememberAmbientPowerSaving(): Boolean {
    val context = LocalContext.current.applicationContext
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    val resumed = rememberLifecycleResumed()
    var saving by remember(power) { mutableStateOf(power?.isPowerSaveMode == true) }
    DisposableEffect(context, power, resumed) {
        saving = power?.isPowerSaveMode == true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { saving = power?.isPowerSaveMode == true }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return saving
}

/**
 * The gallery and app use this renderer. Unselected previews are deterministic and static;
 * a live card consumes the app clock. Outside NexusBackdrop it is safely static as well.
 * The changing phase is read only by Canvas, never during composition or measurement.
 */
@Composable
fun NexusAmbientBackground(
    appearance: NexusAppearance,
    mode: AmbientMode,
    intensity: Float,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
) {
    val spec = remember(appearance) { NexusThemeSpecs.forAppearance(appearance) }
    val phase = LocalAmbientPhase.current
    val opacity = ambientOpacity(mode, intensity, spec.isDark)
    Canvas(modifier.clipToBounds()) {
        drawRect(spec.colors.background)
        if (opacity > 0f && size.minDimension > 0f) {
            val t = (if (preview || mode == AmbientMode.STATIC) 0f else phase.value) * (2f * PI.toFloat())
            val colors = spec.ambientColors
            when (spec.ambientStyle) {
                AmbientStyle.AURORA -> drawAurora(colors, opacity, t)
                AmbientStyle.LIQUID -> drawLiquid(colors, opacity, t)
                AmbientStyle.SILK -> drawSilk(colors, opacity, t)
                AmbientStyle.ATMOSPHERE -> drawAtmosphere(colors, opacity, t)
                AmbientStyle.NEBULA -> drawNebula(colors, opacity, t)
            }
        }
    }
}

private fun DrawScope.glow(color: Color, opacity: Float, x: Float, y: Float, radius: Float) {
    val center = Offset(size.width * x, size.height * y)
    val r = size.maxDimension * radius
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = opacity), Color.Transparent), center, r), r, center)
}

/** Tall, folded curtains of light, distinct from the round liquid pools. */
private fun DrawScope.drawAurora(colors: List<Color>, opacity: Float, t: Float) {
    glow(colors[0], opacity * .6f, .12f, .15f, .62f)
    for (i in 0..2) {
        val drift = sin(t * (1f + i * .173f) + i * 1.7f) * .045f
        val x = .08f + i * .31f + drift
        val ribbon = Path().apply {
            moveTo(size.width * (x - .22f), -size.height * .1f)
            cubicTo(size.width * (x + .40f), size.height * .26f, size.width * (x - .20f), size.height * .54f, size.width * (x + .24f), size.height * 1.1f)
            lineTo(size.width * (x + .48f), size.height * 1.1f)
            cubicTo(size.width * (x + .04f), size.height * .55f, size.width * (x + .60f), size.height * .24f, size.width * x, -size.height * .1f)
            close()
        }
        drawPath(ribbon, Brush.horizontalGradient(listOf(Color.Transparent, colors[i].copy(alpha = opacity * .44f), Color.Transparent)))
    }
    glow(colors[2], opacity * .45f, .84f, .88f, .54f)
}

/** Off-centre graphite pools and a reflected edge leave most OLED pixels near black. */
private fun DrawScope.drawLiquid(colors: List<Color>, opacity: Float, t: Float) {
    glow(colors[1], opacity * .65f, -.06f + .03f * sin(t), .28f, .47f)
    glow(colors[0], opacity * .58f, 1.08f, .76f + .035f * cos(t * .731f), .45f)
    val edge = Path().apply {
        moveTo(-size.width * .1f, size.height * .44f)
        cubicTo(size.width * .15f, size.height * (.26f + .025f * sin(t)), size.width * .78f, size.height * .82f, size.width * 1.1f, size.height * .62f)
    }
    drawPath(edge, Brush.linearGradient(listOf(Color.Transparent, colors[2].copy(alpha = opacity * .30f), Color.Transparent), Offset.Zero, Offset(size.width, size.height)),
        style = Stroke(width = size.minDimension * .002f))
    val pool = Path().apply {
        moveTo(size.width * .60f, size.height * 1.08f)
        cubicTo(size.width * .35f, size.height * .80f, size.width * 1.18f, size.height * .58f, size.width * 1.1f, size.height * .34f)
        lineTo(size.width * 1.1f, size.height * 1.1f)
        close()
    }
    drawPath(pool, Brush.verticalGradient(listOf(Color.Transparent, colors[0].copy(alpha = opacity * .22f))))
}

/** Warm satin folds with fine parallel threads rather than stars or luminous blobs. */
private fun DrawScope.drawSilk(colors: List<Color>, opacity: Float, t: Float) {
    glow(colors[2], opacity * .7f, .12f, .06f, .75f)
    glow(colors[1], opacity * .45f, 1.04f, .94f, .63f)
    for (i in 0..3) {
        val y = .16f + i * .19f + .018f * sin(t * .817f + i)
        val fold = Path().apply {
            moveTo(-size.width * .15f, size.height * (y + .16f))
            cubicTo(size.width * .22f, size.height * (y - .15f), size.width * .62f, size.height * (y + .19f), size.width * 1.1f, size.height * (y - .08f))
            lineTo(size.width * 1.1f, size.height * (y + .08f))
            cubicTo(size.width * .62f, size.height * (y + .28f), size.width * .22f, size.height * (y - .05f), -size.width * .15f, size.height * (y + .30f))
            close()
        }
        drawPath(fold, Brush.verticalGradient(listOf(Color.Transparent, colors[i % colors.size].copy(alpha = opacity * .36f), Color.Transparent)))
        drawPath(fold, colors[1].copy(alpha = opacity * .12f), style = Stroke(width = size.minDimension * .0012f))
    }
}

/** Low horizon haze and off-screen halos keep the emerald centre quiet. */
private fun DrawScope.drawAtmosphere(colors: List<Color>, opacity: Float, t: Float) {
    glow(colors[0], opacity * .8f, -.08f, .37f + .025f * sin(t), .67f)
    glow(colors[1], opacity * .6f, 1.08f, .83f + .025f * cos(t * .731f), .58f)
    val horizon = size.height * (.68f + .018f * sin(t))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, colors[2].copy(alpha = opacity * .17f), Color.Transparent),
        startY = horizon - size.height * .19f, endY = horizon + size.height * .22f))
}

/** Clustered indigo clouds and a sparse fixed constellation, never a scrolling particle field. */
private fun DrawScope.drawNebula(colors: List<Color>, opacity: Float, t: Float) {
    glow(colors[0], opacity * .70f, .14f + .025f * sin(t), .12f, .62f)
    glow(colors[1], opacity * .58f, .92f, .58f + .035f * cos(t * .731f), .53f)
    glow(colors[2], opacity * .65f, .24f, .98f, .62f)
    for (i in 0..12) {
        val x = ((i * 37 + 13) % 101) / 100f
        val y = ((i * 23 + 9) % 97) / 100f
        drawCircle(colors[i % colors.size].copy(alpha = opacity * .27f),
            radius = size.minDimension * if (i % 4 == 0) .0017f else .001f,
            center = Offset(size.width * x, size.height * y))
    }
}
