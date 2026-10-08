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

private fun palette(theme: NexusAppearance): ColorScheme {
    val light = theme == NexusAppearance.LIGHT
    val quartz = theme == NexusAppearance.QUARTZ
    val accent = Color(if(theme == NexusAppearance.OCEAN) 0xFF7DD3FC else if(theme == NexusAppearance.FOREST) 0xFFA3D9AB else if (light) 0xFF243E74 else if (quartz) 0xFFE0C598 else 0xFF63DCCB)
    val bg = Color(if(theme == NexusAppearance.OCEAN) 0xFF0A1420 else if(theme == NexusAppearance.FOREST) 0xFF0D1712 else if (theme == NexusAppearance.OLED) 0xFF000000 else if (light) 0xFFF7F5EF else if (quartz) 0xFF14110F else 0xFF0C0E13)
    val surface = Color(if(theme == NexusAppearance.OCEAN) 0xFF122437 else if(theme == NexusAppearance.FOREST) 0xFF182A20 else if (light) 0xFFFFFFFF else if (quartz) 0xFF201B17 else 0xFF161A22)
    val variant = Color(if (light) 0xFFEAE9E5 else if (quartz) 0xFF302820 else 0xFF242A35)
    val ink = Color(if (light) 0xFF191C23 else if (quartz) 0xFFF6EDE0 else 0xFFF0F3F8)
    val muted = Color(if (light) 0xFF555C68 else if (quartz) 0xFFC2B5A3 else 0xFFADB7C6)
    val base = if (light) lightColorScheme() else darkColorScheme()
    return base.copy(primary=accent, onPrimary=if(light) Color.White else bg,
        primaryContainer=variant, onPrimaryContainer=accent, secondary=accent,
        onSecondary=if(light) Color.White else bg, secondaryContainer=variant, onSecondaryContainer=ink,
        tertiary=accent, onTertiary=if(light) Color.White else bg,
        tertiaryContainer=variant, onTertiaryContainer=ink,
        inverseSurface=ink, inverseOnSurface=bg, inversePrimary=accent,
        surfaceDim=bg, surfaceBright=variant, scrim=Color.Black,
        background=bg, onBackground=ink, surface=surface, onSurface=ink,
        surfaceVariant=variant, onSurfaceVariant=muted,
        outline=muted.copy(alpha=.6f), outlineVariant=muted.copy(alpha=.22f),
        error=Color(if(light) 0xFF9C3E42 else 0xFFE6A5AA), onError=if(light) Color.White else bg,
        errorContainer=Color(if(light) 0xFFF8E5E5 else 0xFF35262B), onErrorContainer=ink, surfaceTint=accent,
        surfaceContainerLowest=bg, surfaceContainerLow=surface,
        surfaceContainer=surface, surfaceContainerHigh=variant, surfaceContainerHighest=variant)
}

@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    val preferences = rememberAppearancePreferences()
    val theme by preferences.themeState()
    val scheme = remember(theme) { palette(theme) }
    val typography = remember(theme) {
        val heading = if(theme==NexusAppearance.QUARTZ) FontFamily.Serif else brandFont(R.font.space_grotesk)
        val body = brandFont(R.font.manrope)
        val base=Typography()
        base.copy(displayLarge=base.displayLarge.copy(fontFamily=heading), displayMedium=base.displayMedium.copy(fontFamily=heading), displaySmall=base.displaySmall.copy(fontFamily=heading),
            headlineLarge=base.headlineLarge.copy(fontFamily=heading), headlineMedium=base.headlineMedium.copy(fontFamily=heading), headlineSmall=base.headlineSmall.copy(fontFamily=heading),
            titleLarge=base.titleLarge.copy(fontFamily=heading), titleMedium=base.titleMedium.copy(fontFamily=heading), titleSmall=base.titleSmall.copy(fontFamily=heading),
            bodyLarge=base.bodyLarge.copy(fontFamily=body), bodyMedium=base.bodyMedium.copy(fontFamily=body), bodySmall=base.bodySmall.copy(fontFamily=body),
            labelLarge=base.labelLarge.copy(fontFamily=body), labelMedium=base.labelMedium.copy(fontFamily=body), labelSmall=base.labelSmall.copy(fontFamily=body))
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
    MaterialTheme(colorScheme=scheme, typography=typography) {
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
