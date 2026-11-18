package com.vidadomu.nexus.assistant

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.vidadomu.nexus.ui.components.LocalStudioFlat
import com.vidadomu.nexus.ui.theme.LocalChromeColor
import com.vidadomu.nexus.ui.theme.LocalNexusVisuals

/**
 * The Agent's reading room: charcoal ground, matte surfaces and one accent, the active theme's own, kept for what is
 * running and for the main action. Dark themes share the neutral greys; light themes keep their paper and only lose
 * the sheen. The user's theme preference is not touched: this applies to the Agent screen and its tab bar alone.
 */
internal object AgentChatColors {
    val Ground = Color(0xFF1B1C1E)
    val Sunken = Color(0xFF161719)
    val Low = Color(0xFF202124)
    val Surface = Color(0xFF232427)
    val Raised = Color(0xFF2A2B2F)
    val High = Color(0xFF2F3135)
    val Highest = Color(0xFF383A40)
    val Ink = Color(0xFFE6E7EA)
    val Soft = Color(0xFFA9ADB5)
    val Faint = Color(0xFF92959D)
    val Line = Color(0xFF3A3C41)

    /** Neutral greys under the brand's accent. Pure: the same input always gives the same scheme. */
    fun dark(brand: ColorScheme): ColorScheme = brand.copy(
        primary = brand.primary, onPrimary = Color(0xFF111214),
        primaryContainer = brand.primary.copy(alpha = .20f).compositeOver(Raised), onPrimaryContainer = brand.primary.copy(alpha = .80f).compositeOver(Color.White),
        secondaryContainer = Highest, onSecondaryContainer = Ink,
        tertiaryContainer = Highest, onTertiaryContainer = Ink,
        background = Ground, onBackground = Ink,
        surface = Surface, onSurface = Ink, surfaceVariant = High, onSurfaceVariant = Soft,
        surfaceContainerLowest = Sunken, surfaceContainerLow = Low, surfaceContainer = Surface,
        surfaceContainerHigh = Raised, surfaceContainerHighest = Highest, surfaceDim = Sunken, surfaceBright = Highest,
        outline = Faint, outlineVariant = Line,
        error = Color(0xFFFFB4BA), onError = Color(0xFF350C15), errorContainer = Color(0xFF3A2228), onErrorContainer = Color(0xFFFFDADD),
        inverseSurface = Ink, inverseOnSurface = Ground, surfaceTint = Faint, scrim = Color.Black,
    )

    /** What the Agent screen wears over [brand]: charcoal for a dark theme; a light theme keeps its paper, without tint. */
    fun matte(brand: ColorScheme): ColorScheme =
        if (brand.background.luminance() < 0.5f) dark(brand) else brand.copy(surfaceTint = brand.outline)
}

/**
 * [enabled] lets a shared piece (the tab bar) follow the Agent's finish only while the Agent is on screen without
 * being rebuilt when the tab changes: the same tree, with the app's own values when it is false.
 */
@Composable
internal fun AgentChatTheme(enabled: Boolean = true, content: @Composable () -> Unit) {
    val brand = MaterialTheme.colorScheme
    val scheme = remember(brand, enabled) { if (enabled) AgentChatColors.matte(brand) else brand }
    val visuals = LocalNexusVisuals.current
    val chrome = LocalChromeColor.current
    val flat = LocalStudioFlat.current
    val matte = remember(scheme, visuals, enabled) {
        if (!enabled) visuals else
        visuals.copy(promptSurface = scheme.surfaceContainerLow, promptBorder = scheme.outline, chromeSurface = scheme.background,
            disabledText = scheme.onSurfaceVariant, placeholder = scheme.onSurfaceVariant,
            conversationSurface = scheme.surfaceContainerHigh, responseSurface = scheme.background,
            codeSurface = scheme.surfaceContainerLowest, mediaSurface = scheme.surfaceContainerHigh,
            dialogSurface = scheme.surfaceContainerLow, sheetSurface = scheme.surfaceContainerLow, separator = scheme.outlineVariant)
    }
    CompositionLocalProvider(LocalChromeColor provides if (enabled) scheme.background else chrome, LocalNexusVisuals provides matte,
        LocalStudioFlat provides (enabled || flat)) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
    }
}
