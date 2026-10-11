package com.vidadomu.nexus.ui.theme

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.font.*
import androidx.core.view.WindowCompat
import com.vidadomu.nexus.R

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun brandFont(resource: Int): FontFamily = FontFamily(
    listOf(400, 500, 600, 700).map { weight ->
        Font(resource, weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    }
)


@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    val preferences = rememberAppearancePreferences()
    val theme by preferences.themeState()
    val scheme = remember(theme) { palette(theme) }
    val typography = remember(theme) {
        val heading = if(theme==NexusAppearance.QUARTZ) FontFamily.Serif else brandFont(R.font.space_grotesk)
        val body = brandFont(R.font.manrope)
        nexusTypography(heading, body)
    }
    val view=LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            window.statusBarColor=scheme.background.toArgb()
            window.navigationBarColor=scheme.background.toArgb()
            WindowCompat.getInsetsController(window,view).apply {
                isAppearanceLightStatusBars=theme==NexusAppearance.LIGHT
                isAppearanceLightNavigationBars=theme==NexusAppearance.LIGHT
            }
        }
    }
    MaterialTheme(colorScheme=scheme, typography=typography, shapes=NexusTokens.shapes) {
        Surface(modifier=Modifier.fillMaxSize(),color=scheme.background,contentColor=scheme.onBackground) {
            // Static background avoids motion distraction and continuous rendering.
            content()
        }
    }
}

/** Modal windows must follow Nexus's chosen palette, not the device's light/dark setting. */
@Composable
fun NexusSheetSystemBars() {
    val view = LocalView.current
    val darkIcons = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = darkIcons
                isAppearanceLightNavigationBars = darkIcons
            }
        }
    }
}

@Composable
private fun AuroraAura(accent: Color) {
    val transition=rememberInfiniteTransition(label="Aurora")
    val phase=transition.animateFloat(0f,1f,infiniteRepeatable(tween(24000,easing=LinearEasing),RepeatMode.Reverse),label="Aura drift")
    // Background drawing does not consume gestures. State read is confined to draw.
    Canvas(Modifier.fillMaxSize()) {
        val t=phase.value
        listOf(Offset(size.width*(.1f+.3f*t),size.height*(.14f+.06f*t)),
            Offset(size.width*(.9f-.25f*t),size.height*(.85f-.1f*t))).forEach { center ->
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha=.08f),Color.Transparent),center,size.width*.8f),size.width*.8f,center)
        }
    }
}
