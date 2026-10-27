package com.vidadomu.nexus.ui.create

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.core.model.ModelParameter
import com.vidadomu.nexus.ui.theme.NexusTokens

/** The model parameter that is a picture shape (16:9, 1:1, 1024x768…), if it has a small set of drawable options. */
internal fun aspectFormatParameter(parameters: List<ModelParameter>): ModelParameter? = parameters.firstOrNull { param ->
    val options = ParameterPresentation.options(param)
    param.key.lowercase() in setOf("aspect_ratio", "aspectratio", "ratio", "size", "image_size") &&
        options.size in 2..9 && options.all { ParameterPresentation.proportion(param, it) != null }
}

/** One compact row: every shape drawn with its own outline, the chosen one highlighted. No scrolling the page to see it. */
@Composable
internal fun AspectFormatPicker(param: ModelParameter, value: String, onChange: (String) -> Unit) {
    val options = ParameterPresentation.options(param)
    val colors = MaterialTheme.colorScheme
    val scrollable = options.size > 5
    Surface(shape = NexusTokens.card, color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(4.dp).then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEach { option ->
                val selected = option == value
                val ratio = ParameterPresentation.proportion(param, option) ?: 1f
                val label = ParameterPresentation.label(param, option).primary
                val tint = if (selected) colors.onSurface else colors.onSurfaceVariant.copy(alpha = .75f)
                val cell = (if (scrollable) Modifier.width(64.dp) else Modifier.weight(1f))
                Surface(
                    shape = NexusTokens.field,
                    color = if (selected) colors.surfaceContainerHighest else androidx.compose.ui.graphics.Color.Transparent,
                    modifier = cell.semantics {
                        contentDescription = "Formato $label"
                        stateDescription = if (selected) "Seleccionado" else "No seleccionado"
                    }.clickable(role = Role.RadioButton) { onChange(option) }
                ) {
                    Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(Modifier.size(24.dp)) {
                            val edge = size.minDimension - 3.dp.toPx()
                            val w = if (ratio >= 1f) edge else edge * ratio
                            val h = if (ratio >= 1f) edge / ratio else edge
                            drawRoundRect(
                                tint, Offset((size.width - w) / 2, (size.height - h) / 2), Size(w, h),
                                CornerRadius(2.5.dp.toPx()), Stroke(if (selected) 2.dp.toPx() else 1.5.dp.toPx())
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
