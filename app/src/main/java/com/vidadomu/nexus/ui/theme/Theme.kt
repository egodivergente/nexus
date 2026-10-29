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


@Composable
internal fun nexusThemeTypography(appearance:NexusAppearance):Typography = remember(appearance) {
    val heading=if(appearance in setOf(NexusAppearance.QUARTZ,NexusAppearance.CHAMPAGNE)) FontFamily.Serif else brandFont(R.font.space_grotesk)
    nexusTypography(heading,brandFont(R.font.manrope))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    val preferences = rememberAppearancePreferences()
    val theme by preferences.themeState()
    val scheme = remember(theme) { palette(theme) }
    val typography = nexusThemeTypography(theme)
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
                isAppearanceLightStatusBars=!NexusThemeSpecs.forAppearance(theme).isDark
                isAppearanceLightNavigationBars=!NexusThemeSpecs.forAppearance(theme).isDark
            }
        }
    }
    val platformHaptics=androidx.compose.ui.platform.LocalHapticFeedback.current
    val filteredHaptics=rememberNexusPlatformHaptics(platformHaptics)
    val fontScale by preferences.fontScaleState()
    val base = LocalDensity.current
    val density = remember(base, fontScale) { Density(base.density, base.fontScale * fontScale) }
    val decorativeMotion = rememberNexusMotionAllowed()
    CompositionLocalProvider(LocalNexusAppearance provides theme, LocalNexusVisuals provides NexusThemeSpecs.visuals(theme),
        LocalChromeColor provides chromeFor(theme), LocalDensity provides density,
        LocalNexusDecorativeMotion provides decorativeMotion, androidx.compose.ui.platform.LocalHapticFeedback provides filteredHaptics) {
        MaterialTheme(colorScheme=scheme, typography=typography, shapes=NexusTokens.shapes) {
            Surface(modifier=Modifier.fillMaxSize(),color=scheme.background,contentColor=scheme.onBackground) {
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
