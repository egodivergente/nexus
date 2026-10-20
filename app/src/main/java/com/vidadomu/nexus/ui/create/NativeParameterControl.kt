package com.vidadomu.nexus.ui.create

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.core.model.ModelParameter
import com.vidadomu.nexus.core.model.ParameterType
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.theme.NexusTokens

/** One shared, compact editor. Display labels never replace native contract values. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NativeParameterControl(param: ModelParameter, value: String, onChange: (String) -> Unit) {
    var showDetails by remember(param.key) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val title = ParameterPresentation.title(param)
    val choices = ParameterPresentation.options(param)
    val range = ParameterPresentation.range(param, LocalConfiguration.current.locales[0])
    Surface(shape = NexusTokens.field, color = colors.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (param.key.lowercase()) {
                    "generate_audio", "sound", "audio", "enable_audio", "keep_original_sound" -> Icons.Default.VolumeUp
                    "return_last_frame" -> Icons.Default.Image
                    "duration", "duration_seconds", "seconds" -> Icons.Default.Timer
                    "aspect_ratio", "aspectratio", "ratio", "size", "image_size" -> Icons.Default.AspectRatio
                    "resolution" -> Icons.Default.HighQuality
                    else -> null
                }
                if (icon != null) {
                    Icon(icon, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                }
                Text(title + if (param.isRequired) " *" else "", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { showDetails = true }) {
                    Icon(Icons.Default.MoreHoriz, "Detalles de $title", tint = colors.onSurfaceVariant)
                }
            }
            when {
                param.type == ParameterType.BOOLEAN -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ParameterPresentation.booleanChoices(param).forEach { (option, label) ->
                            ParameterChip(param, option, value, onChange, label)
                        }
                    }
                }
                choices.isNotEmpty() && choices.size <= 6 -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!param.isRequired) ParameterChip(param, "", value, onChange)
                        choices.forEach { ParameterChip(param, it, value, onChange) }
                    }
                }
                choices.isNotEmpty() -> {
                    var expanded by remember(param.key) { mutableStateOf(false) }
                    val label = ParameterPresentation.label(param, value)
                    ExposedDropdownMenuBox(expanded, { expanded = it }) {
                        StudioTextField(value = label.primary, onValueChange = {}, readOnly = true,
                            supportingText = label.secondary?.let { detail -> { Text(detail) } },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(), shape = NexusTokens.field)
                        ExposedDropdownMenu(expanded, { expanded = false }) {
                            if (!param.isRequired) DropdownMenuItem(text = { Text("Automático") },
                                trailingIcon = if (value.isEmpty()) {{ Icon(Icons.Default.Check, "Seleccionado") }} else null,
                                onClick = { onChange(""); expanded = false })
                            choices.forEach { option ->
                                DropdownMenuItem(text = { Text(ParameterPresentation.describe(param, option)) },
                                    leadingIcon = { FormatPreview(param, option) },
                                    trailingIcon = if (value == option) {{ Icon(Icons.Default.Check, "Seleccionado") }} else null,
                                    onClick = { onChange(option); expanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                else -> {
                    StudioTextField(value, onChange, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(if (param.isRequired) "Introduce un valor" else "Automático") },
                        supportingText = range?.let { r -> { Text("Rango: $r") } }, shape = NexusTokens.field)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
    if (showDetails) AlertDialog(onDismissRequest = { showDetails = false }, title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Actual: ${ParameterPresentation.describe(param, value)}")
            Text("Predeterminado: ${ParameterPresentation.describe(param, param.defaultValue)}")
            Text(ParameterPresentation.help(param))
            ParameterPresentation.providerNote(param)?.let { Text("Texto del proveedor: $it", style = MaterialTheme.typography.bodySmall) }
            if (!param.isRequired) Text("Automático deja que el proveedor elija el valor.")
            range?.let { Text("Rango: $it") }
            Text(ParameterPresentation.diagnosticKey(param), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }, confirmButton = { TextButton(onClick = { onChange(param.defaultValue); showDetails = false }) {
            Text("Restaurar")
        } }, dismissButton = { TextButton(onClick = { showDetails = false }) { Text("Cerrar") } })
}

@Composable
private fun ParameterChip(param: ModelParameter, option: String, value: String,
    onChange: (String) -> Unit, title: String? = null) {
    val label = ParameterPresentation.label(param, option)
    val selected = value == option
    val colors = MaterialTheme.colorScheme
    val hasPreview = ParameterPresentation.proportion(param, option) != null
    val description = ParameterPresentation.chipDescription(param, option, title)
    FilterChip(selected = selected, onClick = { onChange(option) },
        modifier = Modifier.semantics {
            contentDescription = description
            stateDescription = if (selected) "Seleccionado" else "No seleccionado"
        },
        label = { Column {
            Text(title ?: label.primary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            label.secondary?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        } },
        leadingIcon = when {
            selected && hasPreview -> {{ Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp)); FormatPreview(param, option)
            } }}
            selected -> {{ Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }}
            hasPreview -> {{ FormatPreview(param, option) }}
            else -> null
        },
        shape = NexusTokens.field,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.secondaryContainer,
            selectedLabelColor = colors.onSecondaryContainer,
            selectedLeadingIconColor = colors.onSecondaryContainer),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outline))
}

@Composable
private fun FormatPreview(param: ModelParameter, option: String) {
    val ratio = ParameterPresentation.proportion(param, option) ?: return
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(24.dp)) {
        val maxEdge = size.minDimension - 4.dp.toPx()
        val w = if (ratio >= 1f) maxEdge else maxEdge * ratio
        val h = if (ratio >= 1f) maxEdge / ratio else maxEdge
        drawRect(color, Offset((size.width - w) / 2, (size.height - h) / 2),
            Size(w, h), style = Stroke(1.5.dp.toPx()))
    }
}
