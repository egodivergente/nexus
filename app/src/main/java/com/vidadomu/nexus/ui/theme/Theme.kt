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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.font.*
import androidx.core.view.WindowCompat
import com.vidadomu.nexus.R

internal fun applyNexusFullscreen(window: android.view.Window) {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun brandFont(resource: Int): FontFamily = FontFamily(
    listOf(400, 500, 600, 700).map { weight ->
        Font(resource, weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    }
)


@OptIn(ExperimentalLayoutApi::class)
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
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if(!imeVisible) (view.context as? Activity)?.window?.let(::applyNexusFullscreen)
    }
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            applyNexusFullscreen(window)
            window.statusBarColor=Color.Transparent.toArgb()
            window.navigationBarColor=Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window,view).apply {
                isAppearanceLightStatusBars=theme in setOf(NexusAppearance.LIGHT, NexusAppearance.SAND, NexusAppearance.ROSE)
                isAppearanceLightNavigationBars=theme in setOf(NexusAppearance.LIGHT, NexusAppearance.SAND, NexusAppearance.ROSE)
            }
        }
    }
    val runtime by com.vidadomu.nexus.settings.RuntimePreferences.get(androidx.compose.ui.platform.LocalContext.current).state.collectAsState()
    val platformHaptics=androidx.compose.ui.platform.LocalHapticFeedback.current
    val filteredHaptics=remember(platformHaptics,runtime.value("ui.haptics")) {object: androidx.compose.ui.hapticfeedback.HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
            if(runtime.value("ui.haptics").toBoolean()) platformHaptics.performHapticFeedback(hapticFeedbackType)
        }
    }}
    val fontScale by preferences.fontScaleState()
    val base = LocalDensity.current
    val density = remember(base, fontScale) { Density(base.density, base.fontScale * fontScale) }
    CompositionLocalProvider(LocalChromeColor provides chromeFor(theme), LocalDensity provides density, androidx.compose.ui.platform.LocalHapticFeedback provides filteredHaptics) {
        MaterialTheme(colorScheme=scheme, typography=typography, shapes=NexusTokens.shapes) {
            Surface(modifier=Modifier.fillMaxSize(),color=scheme.background,contentColor=scheme.onBackground) {
                // Static background avoids motion distraction and continuous rendering.
                NexusBackdrop {content()}
            }
        }
    }
}

/** Modal windows must follow Nexus's chosen palette, not the device's light/dark setting. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NexusSheetSystemBars() {
    val view = LocalView.current
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if(!imeVisible) (view.parent as? DialogWindowProvider)?.window?.let(::applyNexusFullscreen)
    }
    val darkIcons = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.let { window ->
            applyNexusFullscreen(window)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = darkIcons
                isAppearanceLightNavigationBars = darkIcons
            }
        }
    }
}
