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
import androidx.compose.ui.input.pointer.pointerInput
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

internal fun aspectLabel(param: ModelParameter, option: String): String =
    Regex("\\d+\\s*[:x×]\\s*\\d+").find(option)?.value?.replace(" ", "")?.takeIf { option.contains(":") }
        ?: ParameterPresentation.label(param, option).primary.substringBefore(" ·").trim()

/** Small outlined rectangle with the proportion of the option. */
@Composable
private fun ShapeGlyph(ratio: Float, tint: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val edge = this.size.minDimension - 2.dp.toPx()
        val w = if (ratio >= 1f) edge else edge * ratio
        val h = if (ratio >= 1f) edge / ratio else edge
        drawRoundRect(tint, Offset((this.size.width - w) / 2, (this.size.height - h) / 2), Size(w, h),
            CornerRadius(2.dp.toPx()), Stroke(1.5.dp.toPx()))
    }
}

/**
 * Format as a single chip that shows the current shape. Touch opens the picker; a pen or mouse hovering over it
 * (no contact) opens the same picker as a preview, so the options cost no screen space at all.
 */
@Composable
internal fun AspectFormatChip(param: ModelParameter, value: String, onChange: (String) -> Unit) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val ratio = ParameterPresentation.proportion(param, value) ?: 1f
    Box {
        Surface(
            onClick = { open = true }, shape = NexusTokens.filter, color = colors.surfaceContainerHigh,
            modifier = Modifier.heightIn(min = 36.dp)
                .semantics { contentDescription = "Formato ${aspectLabel(param, value)}" }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Enter) open = true
                        }
                    }
                }
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShapeGlyph(ratio, colors.onSurface)
                Text(aspectLabel(param, value), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Box(Modifier.width(320.dp).padding(horizontal = 8.dp)) {
                AspectFormatPicker(param, value) { onChange(it); open = false }
            }
        }
    }
}

/** One compact row: every shape drawn with its own outline, the chosen one highlighted. No scrolling the page to see it. */
@Composable
internal fun AspectFormatPicker(param: ModelParameter, value: String, onChange: (String) -> Unit) {
    val options = ParameterPresentation.options(param)
    val colors = MaterialTheme.colorScheme
    val scrollable = options.size > 8
    Surface(shape = NexusTokens.card, color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(4.dp).then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEach { option ->
                val selected = option == value
                val ratio = ParameterPresentation.proportion(param, option) ?: 1f
                val label = aspectLabel(param, option)
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
                    Column(Modifier.padding(vertical = 6.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
                        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, softWrap = false,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
