package com.vidadomu.nexus.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.sin

/** Decorative drawing is capped at 12.5 Hz, with no blur layers, shaders or sensor polling. */
@Composable
internal fun NexusAurora(intensity: Float) {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var saving by remember { mutableStateOf(power?.isPowerSaveMode == true) }
    DisposableEffect(context, power) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { saving = power?.isPowerSaveMode == true }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val moving = rememberNexusMotionAllowed(!saving)
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(moving) {
        if (moving) {
            saving = power?.isPowerSaveMode == true
            val start = SystemClock.uptimeMillis()
            val previous = phase.floatValue
            while (true) {
                phase.floatValue = previous + ((SystemClock.uptimeMillis() - start) % 120_000L) / 120_000f
                delay(80)
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        // Read animation state only in draw; messages and layout do not recompose every tick.
        val t = phase.floatValue * (2 * Math.PI).toFloat()
        val opacity = intensity.coerceIn(0f, 1f) * .34f
        val radius = size.maxDimension * .65f
        listOf(
            Triple(Color(0xFF258EA8), .22f + .10f * sin(t), .20f + .08f * sin(t * 2)),
            Triple(Color(0xFF355FC4), .82f + .08f * sin(t + 2), .52f + .12f * sin(t)),
            Triple(Color(0xFF7558A3), .40f + .12f * sin(t + 4), .88f + .04f * sin(t * 2))
        ).forEach { (color, x, y) ->
            val center = Offset(size.width * x, size.height * y)
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = opacity), Color.Transparent), center, radius), radius, center)
        }
    }
}
