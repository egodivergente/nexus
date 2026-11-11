package com.vidadomu.nexus.ui.cut

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.vidadomu.nexus.ui.theme.LocalNexusAppearance
import com.vidadomu.nexus.ui.theme.LocalNexusVisuals
import com.vidadomu.nexus.ui.theme.NexusAppearance
import com.vidadomu.nexus.ui.theme.NexusThemeSpecs

/** A neutral editing room keeps the image readable without changing the user's app theme. */
@Composable
internal fun CutEditorTheme(content: @Composable () -> Unit) {
    val appearance = LocalNexusAppearance.current
    val brand = MaterialTheme.colorScheme
    val darkBrand = brand.background.luminance() < 0.5f
    val accent = if (darkBrand) brand.primary else brand.inversePrimary
    val scheme = NexusThemeSpecs.forAppearance(NexusAppearance.OBSIDIAN).colors.copy(
        primary = accent, onPrimary = Color(0xFF0B0D0F),
        primaryContainer = accent.copy(alpha = .18f).compositeOver(Color(0xFF15171A)),
        onPrimaryContainer = accent,
        secondary = if (darkBrand) brand.secondary else brand.secondary.copy(alpha = .45f).compositeOver(Color.White),
        tertiary = if (darkBrand) brand.tertiary else brand.tertiary.copy(alpha = .45f).compositeOver(Color.White),
        background = Color(0xFF0B0D0F), surface = Color(0xFF15171A),
        surfaceContainer = Color(0xFF1A1D21), surfaceContainerLow = Color(0xFF121416),
        surfaceContainerHigh = Color(0xFF24282D), surfaceContainerLowest = Color(0xFF0B0D0F),
    )
    CompositionLocalProvider(LocalNexusAppearance provides appearance,
        LocalNexusVisuals provides NexusThemeSpecs.visuals(NexusAppearance.OBSIDIAN).copy(
            promptBorder = scheme.outline, conversationSurface = scheme.primaryContainer,
            responseSurface = scheme.surface, sheetSurface = scheme.surfaceContainerLow,
        )) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
    }
}

/**
 * Colores de la línea de tiempo derivados del tema activo de Nexus (Aurora, OLED, Cuarzo…). Los tipos de
 * clip se distinguen por tono sin depender solo del color: también cambian etiqueta e icono.
 */
@Immutable
internal data class CutPalette(
    val bg: Color, val panel: Color, val line: Color, val text: Color, val textDim: Color, val accent: Color, val playhead: Color,
    val videoRow: Color, val audioRow: Color, val videoDark: Color, val audioDark: Color, val image: Color, val title: Color,
    val offline: Color, val wave: Color,
)

@Composable
internal fun rememberCutPalette(): CutPalette {
    val c = MaterialTheme.colorScheme
    return remember(c) {
        val dark = c.background.luminance() < 0.5f
        val base = c.surface
        CutPalette(
            bg = c.background,
            panel = c.surfaceContainer,
            line = c.outlineVariant.copy(alpha = 0.6f),
            text = Color.White,
            textDim = c.onSurfaceVariant,
            accent = c.primary,
            playhead = c.onSurface,
            videoRow = c.surfaceContainerLow,
            audioRow = c.surfaceContainerLowest,
            videoDark = c.primary.copy(alpha = if (dark) 0.42f else 0.55f).compositeOver(Color(0xFF14161A)),
            audioDark = c.tertiary.copy(alpha = if (dark) 0.36f else 0.5f).compositeOver(Color(0xFF121416)),
            image = c.secondary.copy(alpha = if (dark) 0.42f else 0.55f).compositeOver(Color(0xFF14161A)),
            title = c.primaryContainer.compositeOver(base),
            offline = c.error.copy(alpha = 0.45f).compositeOver(Color(0xFF1A1414)),
            wave = c.tertiary.copy(alpha = 0.95f).compositeOver(Color.White.copy(alpha = 0.2f)),
        )
    }
}
