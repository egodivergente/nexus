package com.vidadomu.nexus.ui.theme

import androidx.compose.material3.MaterialTheme
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
val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val TextMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val BorderSubtle: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
val BorderHighlight: Color @Composable get() = MaterialTheme.colorScheme.outline
val BorderActive: Color @Composable get() = MaterialTheme.colorScheme.primary
val CyanNeon: Color @Composable get() = MaterialTheme.colorScheme.primary
val NexusBackground: Color @Composable get() = MaterialTheme.colorScheme.background
val NexusSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
val NexusSurfaceVariant: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val NexusCyan: Color @Composable get() = MaterialTheme.colorScheme.primary
val NexusPurple: Color @Composable get() = MaterialTheme.colorScheme.secondary
val NexusSuccess: Color @Composable get() = MaterialTheme.colorScheme.primary
val NexusAmber: Color @Composable get() = MaterialTheme.colorScheme.tertiary
val NexusTextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val NexusTextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val SurfaceHighlight: Color @Composable get() = MaterialTheme.colorScheme.outline
