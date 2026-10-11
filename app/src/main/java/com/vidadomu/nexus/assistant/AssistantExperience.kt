@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.ui.theme.*
import com.vidadomu.nexus.ui.components.InlineNotice
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        }.sortedWith(compareByDescending<ModelSummary> { it.modalities.any { modality -> modality in setOf("IMAGE", "VIDEO") } }
            .thenByDescending { it.name.lowercase().startsWith(terms.first()) }
            .thenByDescending { it.isFlagship }.thenBy { it.name })
        // Show a representative of each family before filling the remaining variant slots.
        val representatives = found.distinctBy { it.family?.lowercase()?.takeIf(String::isNotBlank) ?: it.name.substringBefore(' ').lowercase() }
        val suggestions = (representatives + found).distinctBy { it.ref }.take(6)
        if(suggestions.isNotEmpty()) return ModelCompletion(match.range.first + wordMatches[words.size - length].range.first, end, suggestions)
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
    Surface(color=MaterialTheme.colorScheme.surface,shape=NexusTokens.card,modifier=Modifier.padding(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space8)) {
        Column(Modifier.fillMaxWidth().padding(NexusTokens.space8)) {
            completion?.let { suggestions ->
                LazyRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                    items(suggestions.models, key = { it.ref.toString() }) { model ->
                        AssistChip(onClick = { onInput(insertModel(text, suggestions, model)) }, label = { Text(model.name) })
                    }
                }
            }
            if(ui.pendingAttachments.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                items(ui.pendingAttachments, key = { it.id }) { att ->
                    InputChip(selected = true, onClick = { onRemove(att.id) }, label = { Text(att.name ?: "Referencia") },
                        trailingIcon = { Icon(Icons.Default.Close, "Quitar referencia") })
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onAttach, enabled = !ui.attaching) { Icon(Icons.Default.AttachFile, "Adjuntar") }
                val canSend = !ui.thinking && !ui.attaching && (text.text.isNotBlank() || ui.pendingAttachments.isNotEmpty())
                TextField(text, onInput, modifier = Modifier.weight(1f), placeholder = { Text("Cuéntame qué quieres crear…") },
                    textStyle=MaterialTheme.typography.bodyLarge,shape=NexusTokens.field,colors=TextFieldDefaults.colors(focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedContainerColor=MaterialTheme.colorScheme.surface,focusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent,unfocusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent),
                    minLines = 1, maxLines = if(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480) 2 else 5, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if(canSend) onSend() }))
                if(ui.thinking) IconButton(onClick = onStop) { Icon(Icons.Default.Stop, "Parar respuesta") }
                else IconButton(onClick = onSend, enabled = canSend) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar mensaje") }
            }
        }
    }
}

@Composable
internal fun PendingGenerations(ui: AssistantUiState, blockedByDraft:Boolean=false, onGenerate: () -> Unit) {
    val prepared = ui.plans.values.filter { it.executable && !it.busy && it.state in setOf(PlanState.PREPARED, PlanState.AWAITING_CONFIRMATION) }
    if(prepared.isEmpty()) return
    val pending = prepared.filter { it.includeInBatch }
    val app=LocalContext.current.applicationContext as com.vidadomu.nexus.NexusApplication
    val currencyState by app.currencyManager.state.collectAsState()
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space4), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(pending.isEmpty()) "Selecciona los encargos" else "${pending.size} de ${prepared.size} seleccionados", style = MaterialTheme.typography.labelLarge)
                if(blockedByDraft) Text("Aplica o deshaz los cambios antes de generar el lote.",style=MaterialTheme.typography.labelMedium)
                val estimated = pending.mapNotNull { it.plan.estimate?.amountUsd }
                Text(if(pending.isEmpty()) "Marca «Incluir en lote» en las tarjetas" else if(estimated.size == pending.size) remember(estimated,currencyState) {app.currencyManager.formatCost(estimated.sum())} else "Incluye coste sin estimación",
                    style = MaterialTheme.typography.labelSmall)
            }
            val enabled=pending.isNotEmpty() && !ui.thinking && ui.plans.values.none {it.busy} && !blockedByDraft
            if(pending.size>=2) Button(onClick=onGenerate,enabled=enabled,shape=NexusTokens.field) {Text("Generar ${minOf(pending.size,20)}")}
            else FilledTonalButton(onClick=onGenerate,enabled=enabled,shape=NexusTokens.field) {Text("Generar ${minOf(pending.size,20)}")}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompactGeneration(p: PlanUi, outputs: List<String>, onOpenOutput: () -> Unit,
    onAttach: (List<android.net.Uri>) -> Unit, onDiscard: () -> Unit, onToggleBatch: () -> Unit, assistantBusy: Boolean, details: @Composable () -> Unit) {
    val picker=rememberProjectMediaPicker(onAttach)
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
        details()
        if(p.state in setOf(PlanState.PREPARED, PlanState.AWAITING_CONFIRMATION)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(p.includeInBatch, { onToggleBatch() }, enabled = !p.busy && !assistantBusy)
                Text("Incluir en lote", Modifier.clickable(enabled = !p.busy && !assistantBusy, onClick = onToggleBatch), style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(verticalArrangement=Arrangement.spacedBy(NexusTokens.space4),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                TextButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !p.busy && !assistantBusy) {
                    Icon(Icons.Default.AttachFile, null, Modifier.size(16.dp))
                    Text(if(p.plan.media.isEmpty()) "Adjuntar referencias" else "Cambiar referencias · ${p.plan.media.size}")
                }
                TextButton(onClick = onDiscard, enabled = !p.busy && !assistantBusy) { Text("Descartar") }
            }
        }
        if(p.busy || p.jobStatus in listOf("PENDING", "PROCESSING")) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(outputs.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            items(outputs) { uri ->
                if(p.plan.modality == "IMAGE") AsyncImage(uri, "Resultado generado", Modifier.width(240.dp).aspectRatio(16f/9f)
                    .clip(NexusTokens.field).clickable(onClick = onOpenOutput), contentScale = ContentScale.Fit)
                else OutlinedButton(onClick = onOpenOutput) { Icon(Icons.Default.PlayArrow, null); Text("Abrir vídeo") }
            }
        }

    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProjectReferences(ui: AssistantUiState, vm: AssistantViewModel, expandedByDefault:Boolean=false) {
    if(ui.activeProjectId == null) return
    var open by remember(ui.activeProjectId) {mutableStateOf(expandedByDefault)}
    var renaming by remember { mutableStateOf<Asset?>(null) }
    var name by remember { mutableStateOf("") }
    val import = rememberProjectMediaPicker { vm.importProjectFiles(it) }
    FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
        TextButton(onClick = { import.launch(arrayOf("image/*", "video/*")) }, enabled = !ui.attaching) { Icon(Icons.Default.AddPhotoAlternate, null); Text("Importar archivos") }
        TextButton(onClick = { open = !open }) { Text("Referencias · ${ui.projectAssets.size}") }
    }
    if(open) LazyRow(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.space12), horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        items(ui.projectAssets, key = { it.id }) { asset ->
            Column(Modifier.width(120.dp)) {
                AsyncImage(asset.uri, "Usar referencia ${asset.displayName}", Modifier.size(112.dp,64.dp).clip(NexusTokens.thumbnail)
                    .clickable { vm.reuseAsset(asset.id) }, contentScale = ContentScale.Crop)
                Text(asset.displayName, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { renaming = asset; name = asset.displayName }) { Text("Renombrar") }
            }
        }
    }
    renaming?.let { asset -> AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Nombre de referencia") },
        text = { OutlinedTextField(name, { name = it }, shape=NexusTokens.field, singleLine = true) },
        confirmButton = { TextButton(onClick = { vm.renameReference(asset.id,name); renaming = null }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } }) }
}

@Composable
internal fun AgentStatus(ui: AssistantUiState, onSettings: () -> Unit) {
    val active=ui.plans.values.count {it.busy || it.jobStatus in listOf("PENDING","PROCESSING")}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
        Text("${if(ui.thinking) "Agente trabajando" else "Agente listo"} · $active en curso"+(ui.lastLatencyMs?.let {" · Último turno ${it/1000.0}s"} ?: ""),style=MaterialTheme.typography.labelMedium,color=TextSecondary)
        Text("${ui.catalog.size} modelos · ${ui.skillsCount} skills · ${ui.skillErrors.size} errores de skills. La latencia incluye herramientas.",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
    }
}
