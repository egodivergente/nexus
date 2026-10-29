package com.vidadomu.nexus.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
// Transparent screen containers expose the theme aura without painting over brand assets.
val ScreenBackground = Color.Transparent
// Media overlays are intentionally invariant to keep controls readable on any image.
val MediaScrim = Color.Black
val MediaForeground = Color.White

// Existing screen aliases resolve to the active design tokens.
val BackgroundDark: Color @Composable get() = MaterialTheme.colorScheme.background
val SurfaceDark: Color @Composable get() = MaterialTheme.colorScheme.surface
val SurfaceVariantDark: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val SurfaceCard: Color @Composable get() = MaterialTheme.colorScheme.surface
val SurfaceCardHover: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val PrimaryNeon: Color @Composable get() = MaterialTheme.colorScheme.primary
val PrimaryNeonDim: Color @Composable get() = MaterialTheme.colorScheme.primary
val PrimaryNeonGlow: Color @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha=.2f)
val SecondaryViolet: Color @Composable get() = MaterialTheme.colorScheme.secondary
val SecondaryVioletLight: Color @Composable get() = MaterialTheme.colorScheme.secondary
val AccentAmber: Color @Composable get() = MaterialTheme.colorScheme.tertiary
val AccentEmerald: Color @Composable get() = MaterialTheme.colorScheme.primary
val AccentCoral: Color @Composable get() = MaterialTheme.colorScheme.error
val AccentGold: Color @Composable get() = MaterialTheme.colorScheme.primary
val ErrorRed: Color @Composable get() = MaterialTheme.colorScheme.error
val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
/**
 * The single secondary-text token: counters, price notes, subtitles, «Última actividad», metadata.
 * Always opaque (never `.copy(alpha=…)`) so it keeps ≥ 4.5:1 on every surface of the dark palettes.
 */
val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val TextMuted: Color @Composable get() = TextSecondary
val BorderSubtle: Color @Composable get() = Color.Transparent
val BorderHighlight: Color @Composable get() = Color.Transparent
val BorderActive: Color @Composable get() = MaterialTheme.colorScheme.primary
// Essential: the edge is the only cue that identifies a control (unfilled chip, text field); opaque and >=3:1.
// Decorative: separators and card outlines that sit on top of a tonal difference; may stay faint.
val BorderEssential: Color @Composable get() = MaterialTheme.colorScheme.outline
val BorderDecorative: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
val CyanNeon: Color @Composable get() = MaterialTheme.colorScheme.primary
val NexusBackground: Color @Composable get() = MaterialTheme.colorScheme.background
val NexusSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
val NexusSurfaceVariant: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val NexusCyan: Color @Composable get() = MaterialTheme.colorScheme.primary
val NexusPurple: Color @Composable get() = MaterialTheme.colorScheme.secondary
val NexusSuccess: Color @Composable get() = LocalNexusVisuals.current.success
val NexusAmber: Color @Composable get() = LocalNexusVisuals.current.warning
val NexusTextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val NexusTextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val SurfaceHighlight: Color @Composable get() = MaterialTheme.colorScheme.outline


/** Studio foundation: elevation is tonal rather than a perimeter stroke. */
object StudioPalette {
    val Background = Color(0xFF0A0D12)
    val Surface = Color(0xFF17202C)
    val Interactive = Color(0xFF263346)
    val Accent = Color(0xFF4EBAE8)
    val Glass = Color(0xE60A0D12)
}

/** Barras superior e inferior: un tono propio, claramente distinto del fondo del contenido y de las tarjetas. */
internal fun chromeFor(theme:NexusAppearance):Color = NexusThemeSpecs.forAppearance(theme).chrome
internal fun palette(theme:NexusAppearance):ColorScheme = NexusThemeSpecs.forAppearance(theme).colors
internal fun legacyChromeFor(theme: NexusAppearance): Color = Color(when (theme) {
    NexusAppearance.AURORA -> 0xFF182030
    NexusAppearance.OLED -> 0xFF121821
    NexusAppearance.QUARTZ -> 0xFF2A231D
    NexusAppearance.OCEAN -> 0xFF17304A
    NexusAppearance.FOREST -> 0xFF1E3A2A
    NexusAppearance.SAND -> 0xFFEAD9BC
    NexusAppearance.ROSE -> 0xFFEBCFCB
    NexusAppearance.LIGHT -> 0xFFDCE3F0
    else -> 0xFF182030
})

val LocalChromeColor = androidx.compose.runtime.staticCompositionLocalOf { Color(0xFF182030) }

internal fun legacyPalette(theme: NexusAppearance): ColorScheme {
    if (theme == NexusAppearance.SAND || theme == NexusAppearance.ROSE) {
        val rose = theme == NexusAppearance.ROSE
        val bg = Color(if(rose) 0xFFFFF6F3 else 0xFFFFF8ED)
        val surface = Color(if(rose) 0xFFFFFBF9 else 0xFFFFFCF5)
        val layer = Color(if(rose) 0xFFF1DDD9 else 0xFFF0E2CD)
        val ink = Color(if(rose) 0xFF362225 else 0xFF33291E)
        val muted = Color(if(rose) 0xFF70545A else 0xFF6B5948)
        val accent = Color(if(rose) 0xFF814654 else 0xFF8C4B2D)
        return lightColorScheme(primary=accent,onPrimary=Color.White,secondary=accent,onSecondary=Color.White,
            primaryContainer=layer,onPrimaryContainer=ink,secondaryContainer=layer,onSecondaryContainer=ink,
            tertiary=accent,onTertiary=Color.White,tertiaryContainer=layer,onTertiaryContainer=ink,
            background=bg,onBackground=ink,surface=surface,onSurface=ink,surfaceVariant=layer,onSurfaceVariant=muted,
            surfaceContainerLowest=bg,surfaceContainerLow=surface,surfaceContainer=surface,
            surfaceContainerHigh=layer,surfaceContainerHighest=layer,surfaceDim=layer,surfaceBright=surface,
            outline=muted,outlineVariant=layer,error=Color(0xFF9C3038),onError=Color.White,
            errorContainer=Color(0xFFFFDAD8),onErrorContainer=Color(0xFF410008),surfaceTint=accent)
    }
    val light = theme == NexusAppearance.LIGHT
    val quartz = theme == NexusAppearance.QUARTZ
    val accent = if(theme == NexusAppearance.AURORA || theme == NexusAppearance.OLED) StudioPalette.Accent else Color(if(theme == NexusAppearance.OCEAN) 0xFF7DD3FC else if(theme == NexusAppearance.FOREST) 0xFFA3D9AB else if (light) 0xFF243E74 else if (quartz) 0xFFE0C598 else 0xFF4EBAE8)
    val bg = if(theme == NexusAppearance.AURORA) StudioPalette.Background else Color(if(theme == NexusAppearance.OCEAN) 0xFF0A1420 else if(theme == NexusAppearance.FOREST) 0xFF0D1712 else if (theme == NexusAppearance.OLED) 0xFF000000 else if (light) 0xFFF1F3F8 else if (quartz) 0xFF14110F else 0xFF0A0D12)
    val surface = if(theme == NexusAppearance.AURORA || theme == NexusAppearance.OLED) StudioPalette.Surface else Color(if(theme == NexusAppearance.OCEAN) 0xFF122437 else if(theme == NexusAppearance.FOREST) 0xFF182A20 else if (light) 0xFFFFFFFF else if (quartz) 0xFF201B17 else 0xFF17202C)
    val variant = if(!light && !quartz) StudioPalette.Interactive else Color(if (light) 0xFFE4E8F1 else if (quartz) 0xFF302820 else 0xFF263346)
    val ink = Color(if (light) 0xFF191C23 else if (quartz) 0xFFF6EDE0 else 0xFFF0F3F8)
    val muted = Color(if (light) 0xFF555C68 else if (quartz) 0xFFC2B5A3 else 0xFFADB7C6)
    // Opaque so the measured contrast does not depend on what is painted underneath.
    val essentialBorder = Color(when (theme) {
        NexusAppearance.QUARTZ -> 0xFF847A6E
        NexusAppearance.OCEAN -> 0xFF7A8697
        NexusAppearance.FOREST -> 0xFF79868C
        NexusAppearance.LIGHT -> 0xFF757B85
        else -> 0xFF7C8593
    })
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
        outline=essentialBorder, outlineVariant=muted.copy(alpha=.22f),
        error=Color(if(light) 0xFF9C3E42 else 0xFFE6A5AA), onError=if(light) Color.White else bg,
        errorContainer=Color(if(light) 0xFFF8E5E5 else 0xFF35262B), onErrorContainer=ink, surfaceTint=accent,
        surfaceContainerLowest=bg, surfaceContainerLow=surface,
        surfaceContainer=surface, surfaceContainerHigh=variant, surfaceContainerHighest=variant)
}
