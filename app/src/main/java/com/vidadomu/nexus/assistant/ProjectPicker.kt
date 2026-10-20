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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.agent.core.assets.AssetIndex
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
    maxLength: Int = Int.MAX_VALUE, onValidationError: (String) -> Unit = {}, enabled: Boolean = true,
) {
    val pasteWarning = remember(label) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StudioTextField(value, { input ->
            if (!enabled) return@StudioTextField
            if (input.length <= maxLength || input.length < value.length) { pasteWarning.value = null; onValueChange(input) }
            else { pasteWarning.value = "El texto supera $maxLength caracteres; no se ha insertado."; onValidationError(pasteWarning.value!!) }
        }, shape = NexusTokens.field, placeholder = { Text(placeholder) }, enabled = enabled,
            singleLine = singleLine, minLines = minLines, maxLines = if (singleLine) 1 else maxLines,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
        if (maxLength != Int.MAX_VALUE) Text("${value.length}/$maxLength caracteres", style = MaterialTheme.typography.bodySmall)
        pasteWarning.value?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}

internal const val CONFIRM_LEGACY_MODELS = "Confirmar modelos tal como están"

/**
 * Campos de la memoria del proyecto: los mismos que antes, una sola superficie por campo.
 * [enabled] = false mientras se guarda: ningún cambio puede llegar al borrador que está escribiéndose.
 */
@Composable
internal fun ProjectMemoryForm(
    draft: ProjectMemoryDraft,
    onChange: (ProjectMemoryDraft) -> Unit,
    assetCount: Int,
    characters: List<Character>,
    notes: List<ProjectNote>,
    onValidationError: (String) -> Unit = {},
    enabled: Boolean = true,
) {
    fun edit(next: ProjectMemoryDraft) { if (enabled) onChange(next.copy(saveRequested = false)) }
    Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
        Text("El asistente lo lee en cada mensaje para que los prompts sean coherentes, y lo completa él cuando le cuentas cosas del proyecto. $assetCount archivos etiquetados con este proyecto.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProjectSection(Icons.Default.MenuBook, "La historia") {
            ProjectMemoryField("Descripción corta", draft.description, { edit(draft.copy(description = it)) },
                "Ej.: Drama carcelario en Madrid, 1928", singleLine = true,
                maxLength = ProjectMemoryDraft.DESCRIPTION_LIMIT, onValidationError = onValidationError, enabled = enabled)
            ProjectMemoryField("Sinopsis", draft.synopsis, { edit(draft.copy(synopsis = it)) },
                "De qué va, personajes principales y cómo termina", minLines = 3, maxLines = 8,
                maxLength = ProjectMemoryDraft.TEXT_LIMIT, onValidationError = onValidationError, enabled = enabled)
        }
        ProjectSection(Icons.Default.Palette, "Aspecto y tono") {
            ProjectMemoryField("Tono / género", draft.tone, { edit(draft.copy(tone = it)) },
                "Ej.: sobrio, tenso, realista", maxLines = 4,
                maxLength = ProjectMemoryDraft.TEXT_LIMIT, onValidationError = onValidationError, enabled = enabled)
            ProjectMemoryField("Estilo visual", draft.style, { edit(draft.copy(style = it)) },
                "Ej.: 35 mm, luz natural, paleta apagada, grano fino", minLines = 2, maxLines = 6,
                maxLength = ProjectMemoryDraft.TEXT_LIMIT, onValidationError = onValidationError, enabled = enabled)
        }
        ProjectSection(Icons.Default.Rule, "Reglas del asistente") {
            ProjectMemoryField("Reglas fijas", draft.rules, { edit(draft.copy(rules = it)) },
                "Una por línea. Ej.: Mateo nunca sonríe", minLines = 2, maxLines = 8,
                maxLength = ProjectMemoryDraft.RULES_LIMIT, onValidationError = onValidationError, enabled = enabled)
            Text("Reglas: ${draft.rules.lines().count { it.isNotBlank() }}/${AssetIndex.MAX_RULES}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            draft.ruleWarning()?.let { Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            ProjectMemoryField("Modelos preferidos", draft.models, { edit(draft.copy(models = it, legacyModelsNeedReview = false)) },
                "Uno por línea. Ej.: Seedance 2.0", maxLines = 4,
                maxLength = ProjectMemoryDraft.MODELS_LIMIT, onValidationError = onValidationError, enabled = enabled)
            Text("Modelos: ${draft.models.lines().count { it.isNotBlank() }}/${AssetIndex.MAX_PREFERRED_MODELS}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            draft.modelWarning()?.let { Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            // Legacy drafts split models on commas; confirming keeps the text exactly as shown, one model per line.
            if (draft.legacyModelsNeedReview) {
                Text("Este borrador antiguo separaba los modelos con comas. Ahora va uno por línea y las comas forman parte del nombre. Revisa la lista: confírmala tal como se ve o edítala.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                OutlinedButton(onClick = { edit(draft.copy(legacyModelsNeedReview = false)) }, enabled = enabled,
                    modifier = Modifier.heightIn(min = NexusTokens.minimumTouch)) { Text(CONFIRM_LEGACY_MODELS) }
            }
        }
        if (characters.isNotEmpty()) ProjectSection(Icons.Default.Person, "Personajes") {
            characters.forEach { c -> Text("• ${c.name}" + (c.description?.let { ": $it" } ?: ""), style = MaterialTheme.typography.bodySmall) }
        }
        val visibleNotes = notes.filter { it.at !in draft.removedNotes }
        if (visibleNotes.isNotEmpty()) ProjectSection(Icons.Default.Lightbulb, "Notas y decisiones") {
            visibleNotes.asReversed().forEach { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { edit(draft.copy(removedNotes = draft.removedNotes + n.at)) }, enabled = enabled) {
                        Icon(Icons.Default.Delete, "Borrar nota", Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * Salir del editor con cambios: guardar en ESTE proyecto, conservar el borrador o descartarlo. Nunca se guarda solo.
 * Con [saving] el diálogo sigue visible y bloqueado hasta conocer el resultado: solo un guardado correcto sale.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProjectLeaveDialog(projectName: String, onSave: () -> Unit, onKeep: () -> Unit, onDiscard: () -> Unit, onStay: () -> Unit,
    saving: Boolean = false) {
    AlertDialog(onDismissRequest = { if (!saving) onStay() },
        icon = { Icon(Icons.Default.EditNote, null) },
        title = { Text("Cambios sin guardar") },
        text = { Text("La memoria de «$projectName» tiene cambios sin guardar. No se guardan solos ni pasan a otro proyecto.") },
        confirmButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8, Alignment.End)) {
                TextButton(onClick = onDiscard, enabled = !saving, modifier = Modifier.heightIn(min = NexusTokens.minimumTouch)) { Text("Descartar cambios") }
                TextButton(onClick = onKeep, enabled = !saving, modifier = Modifier.heightIn(min = NexusTokens.minimumTouch)) { Text("Conservar borrador") }
                Button(onClick = onSave, enabled = !saving, modifier = Modifier.heightIn(min = NexusTokens.minimumTouch)) { Text(if (saving) "Guardando…" else "Guardar") }
            }
        })
}
