package com.vidadomu.nexus.assistant

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.registry.ModelSummary
import com.vidadomu.nexus.agent.core.spend.PlanState

internal data class ModelCompletion(val start: Int, val end: Int, val models: List<ModelSummary>)

/** Matches only catalog entries; insertion replaces the fragment ending at the actual cursor. */
internal fun completeModels(value: TextFieldValue, models: List<ModelSummary>): ModelCompletion? {
    if (!value.selection.collapsed) return null
    val end = value.selection.end
    val prefix = value.text.substring(0, end)
    val match = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}. -]{1,35}$").find(prefix) ?: return null
    val wordMatches = Regex("\\S+").findAll(match.value).toList()
    val words = wordMatches.map { it.value }
    for (length in minOf(words.size, 5) downTo 1) {
        val query = words.takeLast(length).joinToString(" ").lowercase()
        if(query.length < 2) continue
        val terms = query.split(' ')
        val found = models.filter { model ->
            val haystack = "${model.name} ${model.family.orEmpty()} ${model.ref.modelId}".lowercase()
            terms.all { it in haystack } && model.name.lowercase() != query
        }.sortedWith(compareByDescending<ModelSummary> { it.isFlagship }.thenBy { it.name }).take(6)
        if(found.isNotEmpty()) return ModelCompletion(match.range.first + wordMatches[words.size - length].range.first, end, found)
    }
    return null
}

internal fun insertModel(value: TextFieldValue, completion: ModelCompletion, model: ModelSummary): TextFieldValue {
    val separator = if(value.text.getOrNull(completion.end)?.isWhitespace() == true) "" else " "
    val text = value.text.replaceRange(completion.start, completion.end, model.name + separator)
    return TextFieldValue(text, TextRange(completion.start + model.name.length + separator.length))
}

@Composable
internal fun InputBar(ui: AssistantUiState, text: TextFieldValue, onInput: (TextFieldValue) -> Unit,
    onAttach: () -> Unit, onSend: () -> Unit, onStop: () -> Unit, onRemove: (String) -> Unit) {
    val completion = remember(text, ui.catalog) { completeModels(text, ui.catalog) }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            completion?.let { suggestions ->
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(suggestions.models, key = { it.ref.toString() }) { model ->
                        AssistChip(onClick = { onInput(insertModel(text, suggestions, model)) }, label = { Text(model.name) })
                    }
                }
            }
            if(ui.pendingAttachments.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ui.pendingAttachments, key = { it.id }) { att ->
                    InputChip(selected = true, onClick = { onRemove(att.id) }, label = { Text(att.name ?: "Referencia") },
                        trailingIcon = { Icon(Icons.Default.Close, "Quitar referencia") })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAttach, enabled = !ui.attaching) { Icon(Icons.Default.AttachFile, "Adjuntar") }
                val canSend = !ui.thinking && !ui.attaching && (text.text.isNotBlank() || ui.pendingAttachments.isNotEmpty())
                OutlinedTextField(text, onInput, modifier = Modifier.weight(1f), placeholder = { Text("Cuéntame qué quieres crear…") },
                    minLines = 1, maxLines = if(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480) 2 else 5, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if(canSend) onSend() }))
                if(ui.thinking) IconButton(onClick = onStop) { Icon(Icons.Default.Stop, "Parar respuesta") }
                else IconButton(onClick = onSend, enabled = canSend) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar mensaje") }
            }
        }
    }
}

@Composable
internal fun PendingGenerations(ui: AssistantUiState, onGenerate: () -> Unit) {
    val pending = ui.plans.values.filter { it.executable && !it.busy && it.state in setOf(PlanState.PREPARED, PlanState.AWAITING_CONFIRMATION) }
    if(pending.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${pending.size} encargos preparados", style = MaterialTheme.typography.labelLarge)
                val estimated = pending.mapNotNull { it.plan.estimate?.amountUsd }
                Text(if(estimated.size == pending.size) "≈ %.3f USD".format(estimated.sum()) else "Incluye coste sin estimación",
                    style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = onGenerate, enabled = !ui.thinking) { Text("Generar ${minOf(pending.size,20)}") }
        }
    }
}

@Composable
internal fun CompactGeneration(p: PlanUi, outputs: List<String>, onOpenOutput: () -> Unit, details: @Composable () -> Unit) {
    var expanded by rememberSaveable(p.plan.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.plan.modelName, style = MaterialTheme.typography.titleSmall)
                Text(p.jobStatus ?: if(p.busy) "Enviando…" else p.state.name, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if(expanded) "Ocultar" else "Detalles") }
        }
        p.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if(p.busy || p.jobStatus in listOf("PENDING", "PROCESSING")) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(outputs.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(outputs) { uri ->
                if(p.plan.modality == "IMAGE") AsyncImage(uri, "Resultado generado", Modifier.width(240.dp).aspectRatio(16f/9f)
                    .clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenOutput), contentScale = ContentScale.Fit)
                else OutlinedButton(onClick = onOpenOutput) { Icon(Icons.Default.PlayArrow, null); Text("Abrir vídeo") }
            }
        }
        if(expanded) details()
    }
}

@Composable
internal fun AgentModeSelector(mode: AgentMode, onMode: (AgentMode) -> Unit) {
    LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(AgentMode.entries) { value -> FilterChip(selected = value == mode, onClick = { onMode(value) },
            label = { Text(when(value) { AgentMode.ASSISTANT -> "Asistente"; AgentMode.COPILOT -> "Copiloto"; AgentMode.AGENT -> "Agente" }) }) }
    }
}

@Composable
internal fun ProjectReferences(ui: AssistantUiState, vm: AssistantViewModel) {
    if(ui.activeProjectId == null) return
    var open by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Asset?>(null) }
    var name by remember { mutableStateOf("") }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.importProjectFiles(it) }
    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { import.launch(arrayOf("image/*", "video/*")) }, enabled = !ui.attaching) { Icon(Icons.Default.AddPhotoAlternate, null); Text("Importar archivos") }
        TextButton(onClick = { open = !open }) { Text("Referencias · ${ui.projectAssets.size}") }
    }
    if(open) LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ui.projectAssets, key = { it.id }) { asset ->
            Column(Modifier.width(120.dp)) {
                AsyncImage(asset.uri, asset.displayName, Modifier.size(112.dp,64.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { vm.reuseAsset(asset.id) }, contentScale = ContentScale.Crop)
                Text(asset.displayName, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { renaming = asset; name = asset.displayName }) { Text("Renombrar") }
            }
        }
    }
    renaming?.let { asset -> AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Nombre de referencia") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { vm.renameReference(asset.id,name); renaming = null }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } }) }
}

@Composable
internal fun AgentStatus(ui: AssistantUiState, onSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val active = ui.plans.values.count { it.busy || it.jobStatus in listOf("PENDING", "PROCESSING") }
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text("${if(ui.thinking) "Agente trabajando" else "Agente listo"} · $active en curso" +
            (ui.lastLatencyMs?.let { " · Último turno ${it / 1000.0}s" } ?: ""), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(expanded) {
            com.vidadomu.nexus.ui.accounts.AccountBalanceBar(onSettings)
            Text("${ui.catalog.size} modelos · ${ui.skillsCount} skills · ${ui.skillErrors.size} errores de skills. La latencia mide el turno completo, incluyendo herramientas.",
            style = MaterialTheme.typography.bodySmall)
        }
    }
}
