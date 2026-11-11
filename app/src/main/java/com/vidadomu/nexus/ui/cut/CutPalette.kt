package com.vidadomu.nexus.ui.cut

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

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
