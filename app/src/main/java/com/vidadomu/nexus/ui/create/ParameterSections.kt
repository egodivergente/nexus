package com.vidadomu.nexus.ui.create

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.core.model.ModelParameter
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.core.model.ParameterDependencies

/** Groups the optional controls by purpose so a long schema reads as three or four short blocks. */
internal object ParameterSections {
    data class Section(val title: String, val parameters: List<ModelParameter>)

    private val scaleKeys = setOf("mode", "percent", "width", "height", "long_side", "short_side",
        "scale", "upscale_factor", "scale_factor", "target_resolution")
    private val qualityKeys = setOf("type", "quality")
    private val formatKeys = setOf("encode_format", "output_format", "encode_quality")

    fun split(parameters: List<ModelParameter>): List<Section> {
        val used = mutableSetOf<String>()
        fun take(title: String, keys: Set<String>): Section? = parameters.filter { it.key in keys }
            .also { list -> used += list.map { it.key } }.takeIf { it.isNotEmpty() }?.let { Section(title, it) }
        return listOfNotNull(
            take("Motor y calidad", qualityKeys),
            take("Escala y tamaño", scaleKeys),
            take("Formato de salida", formatKeys),
            parameters.filter { it.key !in used }.takeIf { it.isNotEmpty() }?.let { Section("Otras opciones", it) })
    }

    /** Width/height and long/short side travel as pairs so two short fields share one row. */
    fun rows(parameters: List<ModelParameter>): List<List<ModelParameter>> {
        val remaining = parameters.toMutableList()
        val rows = mutableListOf<List<ModelParameter>>()
        while (remaining.isNotEmpty()) {
            val first = remaining.removeAt(0)
            val partner = when (first.key) {
                "width" -> "height"
                "long_side" -> "short_side"
                else -> null
            }?.let { key -> remaining.firstOrNull { it.key == key } }
            if (partner != null) { remaining.remove(partner); rows += listOf(first, partner) } else rows += listOf(first)
        }
        return rows
    }
}

/** Mandatory fields first, then the grouped optional ones; fields that do not apply to the current mode are not drawn. */
@Composable
internal fun ParameterSheetContent(model: NexusModel, parameters: List<ModelParameter>, values: Map<String, String>,
    onChange: (String, String) -> Unit) {
    val shown = ParameterDependencies.visible(model, parameters, values)
    val primary = (shown.filter {it.isRequired} + shown.filter { !it.isAdvanced }.take(4)).distinctBy {it.key}
    val optional = shown.filter { it !in primary }
    var advanced by rememberSaveable(model.providerId, model.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        primary.forEach { param ->
            key(model.id, param.key) { NativeParameterControl(param, values[param.key] ?: param.defaultValue) { onChange(param.key, it) } }
        }
        if (optional.isNotEmpty()) TextButton(onClick = { advanced = !advanced }) {
            Text(if (advanced) "Ocultar ajustes avanzados" else "Avanzado · ${optional.size} opciones")
        }
        if (advanced) {
            val sections = ParameterSections.split(optional)
            sections.forEach { section ->
                Text(section.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp).semantics { heading() })
                if (section.title == "Escala y tamaño" && section.parameters.any { it.key == "width" } && section.parameters.any { it.key == "long_side" })
                    Text("Indica ancho y alto, o bien lado largo y corto.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                ParameterSections.rows(section.parameters).forEach { row ->
                    if (row.size == 1) key(model.id, row[0].key) {
                        NativeParameterControl(row[0], values[row[0].key] ?: row[0].defaultValue) { onChange(row[0].key, it) }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        row.forEach { param ->
                            Box(Modifier.weight(1f)) {
                                key(model.id, param.key) { NativeParameterControl(param, values[param.key] ?: param.defaultValue) { onChange(param.key, it) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One-line summary of what will actually be used, e.g. «Ultra · ×2 · JPEG». */
internal fun parameterSummary(model: NexusModel, parameters: List<ModelParameter>, values: Map<String, String>): String =
    ParameterDependencies.visible(model, parameters, values)
        .filter { it.key in setOf("type", "mode", "percent", "width", "height", "long_side", "short_side", "encode_format") || !it.isAdvanced }
        .mapNotNull { param ->
            val value = values[param.key]?.takeIf { it.isNotBlank() } ?: param.defaultValue.takeIf { it.isNotBlank() && param.options.isNotEmpty() || param.key == "percent" }
            value?.let { ParameterPresentation.label(param, it).primary }
        }.take(4).joinToString(" · ")
