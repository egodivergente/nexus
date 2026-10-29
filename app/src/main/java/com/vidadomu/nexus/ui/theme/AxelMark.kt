package com.vidadomu.nexus.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * A legible small-size reduction of nexus_logo.png: the same pointed ears, angular cheeks,
 * converging mesh and amber eyes. Vector strokes follow the selected theme, including previews.
 */
@Composable
fun AxelMark(
    modifier: Modifier = Modifier,
    appearance: NexusAppearance = LocalNexusAppearance.current,
    contentDescription: String? = null,
) {
    val colors = remember(appearance) { NexusThemeSpecs.forAppearance(appearance).colors }
    val outline = remember { Path().apply {
        moveTo(12f, 4f); lineTo(38f, 25f); lineTo(50f, 23f); lineTo(62f, 25f); lineTo(88f, 4f)
        lineTo(93f, 40f); lineTo(89f, 53f); lineTo(97f, 66f); lineTo(75f, 80f)
        lineTo(61f, 94f); lineTo(50f, 99f); lineTo(39f, 94f); lineTo(25f, 80f)
        lineTo(3f, 66f); lineTo(11f, 53f); lineTo(7f, 40f); close()
    } }
    val mesh = remember { Path().apply {
        moveTo(12f, 4f); lineTo(24f, 46f); lineTo(50f, 77f); lineTo(76f, 46f); lineTo(88f, 4f)
        moveTo(7f, 40f); lineTo(38f, 25f); lineTo(50f, 52f); lineTo(62f, 25f); lineTo(93f, 40f)
        moveTo(3f, 66f); lineTo(24f, 46f); lineTo(50f, 52f); lineTo(76f, 46f); lineTo(97f, 66f)
        moveTo(25f, 80f); lineTo(50f, 77f); lineTo(75f, 80f)
        moveTo(50f, 23f); lineTo(50f, 99f)
    } }
    val eyes = remember { Path().apply {
        moveTo(25f, 56f); lineTo(42f, 65f); lineTo(30f, 64f); close()
        moveTo(75f, 56f); lineTo(58f, 65f); lineTo(70f, 64f); close()
    } }
    val muzzle = remember { Path().apply {
        moveTo(40f, 83f); lineTo(50f, 79f); lineTo(60f, 83f); lineTo(50f, 90f); close()
    } }
    val description = contentDescription
    val semantics = if (description == null) Modifier else Modifier.semantics { this.contentDescription = description }
    Canvas(modifier.size(NexusTokens.iconDefault).then(semantics)) {
        val side = size.minDimension
        translate((size.width - side) / 2f, (size.height - side) / 2f) {
            scale(side / 104f, pivot = Offset.Zero) {
                translate(2f, 1f) {
                    drawPath(outline, colors.primary.copy(alpha = .065f))
                    drawPath(mesh, colors.primary.copy(alpha = .55f), style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(outline, Brush.linearGradient(listOf(colors.primary, colors.secondary), Offset(0f, 0f), Offset(100f, 100f)),
                        style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(eyes, colors.tertiary)
                    drawPath(muzzle, colors.tertiary.copy(alpha = .95f), style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawCircle(colors.primary, radius = 2f, center = Offset(50f, 52f))
                }
            }
        }
    }
}
