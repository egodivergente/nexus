package com.vidadomu.nexus.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.agent.core.assets.Character
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.agent.core.assets.ProjectNote
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.theme.NexusTokens

internal const val PROJECT_DRAFT_BADGE = "Borrador de memoria sin guardar"

/** Lista para cambiar de proyecto en un toque; el nombre se muestra completo, sin recorte. */
@Composable
internal fun ProjectPickerList(
    projects: List<Project>,
    activeId: String?,
    draftIds: Set<String>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().selectableGroup()) {
        ProjectPickerRow("Sin proyecto", "El agente no lee ninguna memoria", activeId == null, false) { onSelect(null) }
        projects.forEach { p ->
            ProjectPickerRow(p.name, p.description?.takeIf { it.isNotBlank() }, activeId == p.id, p.id in draftIds) { onSelect(p.id) }
        }
    }
}

@Composable
private fun ProjectPickerRow(name: String, detail: String?, selected: Boolean, hasDraft: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = NexusTokens.minimumTouch)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .semantics { if (hasDraft) stateDescription = PROJECT_DRAFT_BADGE }
        .padding(vertical = NexusTokens.space4),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else null)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            if (hasDraft) Text(PROJECT_DRAFT_BADGE, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Sección del formulario sin marco propio: título encima y separación por espacio. */
@Composable
internal fun ProjectSection(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = NexusTokens.space8), verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8),
            modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

/** Etiqueta encima del campo; el campo conserva la etiqueta como descripción accesible. */
@Composable
internal fun ProjectMemoryField(
    label: String, value: String, onValueChange: (String) -> Unit, placeholder: String,
    singleLine: Boolean = false, minLines: Int = 1, maxLines: Int = Int.MAX_VALUE,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StudioTextField(value, onValueChange, shape = NexusTokens.field, placeholder = { Text(placeholder) },
            singleLine = singleLine, minLines = minLines, maxLines = if (singleLine) 1 else maxLines,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
    }
}

/** Campos de la memoria del proyecto: los mismos que antes, una sola superficie por campo. */
@Composable
internal fun ProjectMemoryForm(
    draft: ProjectMemoryDraft,
    onChange: (ProjectMemoryDraft) -> Unit,
    assetCount: Int,
    characters: List<Character>,
    notes: List<ProjectNote>,
) {
    fun edit(next: ProjectMemoryDraft) = onChange(next.copy(saveRequested = false))
    Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
        Text("El asistente lo lee en cada mensaje para que los prompts sean coherentes, y lo completa él cuando le cuentas cosas del proyecto. $assetCount archivos etiquetados con este proyecto.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProjectSection(Icons.Default.MenuBook, "La historia") {
            ProjectMemoryField("Descripción corta", draft.description, { edit(draft.copy(description = it.take(ProjectMemoryDraft.DESCRIPTION_LIMIT))) },
                "Ej.: Drama carcelario en Madrid, 1928", singleLine = true)
            ProjectMemoryField("Sinopsis", draft.synopsis, { edit(draft.copy(synopsis = it.take(ProjectMemoryDraft.TEXT_LIMIT))) },
                "De qué va, personajes principales y cómo termina", minLines = 3, maxLines = 8)
        }
        ProjectSection(Icons.Default.Palette, "Aspecto y tono") {
            ProjectMemoryField("Tono / género", draft.tone, { edit(draft.copy(tone = it.take(ProjectMemoryDraft.TEXT_LIMIT))) },
                "Ej.: sobrio, tenso, realista", maxLines = 4)
            ProjectMemoryField("Estilo visual", draft.style, { edit(draft.copy(style = it.take(ProjectMemoryDraft.TEXT_LIMIT))) },
                "Ej.: 35 mm, luz natural, paleta apagada, grano fino", minLines = 2, maxLines = 6)
        }
        ProjectSection(Icons.Default.Rule, "Reglas del asistente") {
            ProjectMemoryField("Reglas fijas", draft.rules, { edit(draft.copy(rules = it.take(ProjectMemoryDraft.RULES_LIMIT))) },
                "Una por línea. Ej.: Mateo nunca sonríe", minLines = 2, maxLines = 8)
            ProjectMemoryField("Modelos preferidos", draft.models, { edit(draft.copy(models = it.take(ProjectMemoryDraft.MODELS_LIMIT))) },
                "Uno por línea. Ej.: Seedance 2.0", maxLines = 4)
        }
        if (characters.isNotEmpty()) ProjectSection(Icons.Default.Person, "Personajes") {
            characters.forEach { c -> Text("• ${c.name}" + (c.description?.let { ": $it" } ?: ""), style = MaterialTheme.typography.bodySmall) }
        }
        val visibleNotes = notes.filter { it.at !in draft.removedNotes }
        if (visibleNotes.isNotEmpty()) ProjectSection(Icons.Default.Lightbulb, "Notas y decisiones") {
            visibleNotes.asReversed().forEach { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { edit(draft.copy(removedNotes = draft.removedNotes + n.at)) }) {
                        Icon(Icons.Default.Delete, "Borrar nota", Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Salir del editor con cambios: guardar en ESTE proyecto, conservar el borrador o descartarlo. Nunca se guarda solo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProjectLeaveDialog(projectName: String, onSave: () -> Unit, onKeep: () -> Unit, onDiscard: () -> Unit, onStay: () -> Unit) {
    AlertDialog(onDismissRequest = onStay,
        icon = { Icon(Icons.Default.EditNote, null) },
        title = { Text("Cambios sin guardar") },
        text = { Text("La memoria de «$projectName» tiene cambios sin guardar. No se guardan solos ni pasan a otro proyecto.") },
        confirmButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8, Alignment.End)) {
                TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = NexusTokens.minimumTouch)) { Text("Descartar cambios") }
                TextButton(onClick = onKeep, modifier = Modifier.heightIn(min = NexusTokens.minimumTouch)) { Text("Conservar borrador") }
                Button(onClick = onSave, modifier = Modifier.heightIn(min = NexusTokens.minimumTouch)) { Text("Guardar") }
            }
        })
}
