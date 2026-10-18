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
import com.vidadomu.nexus.agent.core.tools.Attachment

internal data class ModelCompletion(val start: Int, val end: Int, val models: List<ModelSummary>)

/** Matches only catalog entries; insertion replaces the fragment ending at the actual cursor. */
internal fun completeModels(value: TextFieldValue, models: List<ModelSummary>): ModelCompletion? {
    if (!value.selection.collapsed) return null
    val end = value.selection.end
    val prefix = value.text.substring(0, end)
    val trigger=prefix.lastIndexOf('@')
    if(trigger<0 || (trigger>0 && !prefix[trigger-1].isWhitespace())) return null
    val fragment=prefix.substring(trigger+1)
    if(fragment.length>60 || fragment.any { !it.isLetterOrDigit() && it !in " _.-:/" }) return null
    val query=fragment.trim().lowercase()
    val terms=query.split(' ').filter {it.isNotBlank()}
    val found=models.filter {model ->
        val haystack="${model.name} ${model.family.orEmpty()} ${model.ref.modelId}".lowercase()
        terms.all {it in haystack}
    }.sortedWith(compareByDescending<ModelSummary> {it.modalities.any {modality -> modality in setOf("IMAGE","VIDEO")}}
        .thenByDescending {query.isNotEmpty() && it.name.lowercase().startsWith(query)}
        .thenByDescending {it.isFlagship}.thenBy {it.name})
    val representatives=found.distinctBy {it.family?.lowercase()?.takeIf(String::isNotBlank) ?: it.name.substringBefore(' ').lowercase()}
    val suggestions=(representatives+found).distinctBy {it.ref}.take(6)
    return suggestions.takeIf {it.isNotEmpty()}?.let {ModelCompletion(trigger,end,it)}
}

internal fun insertModel(value: TextFieldValue, completion: ModelCompletion, model: ModelSummary): TextFieldValue {
    val separator = if(value.text.getOrNull(completion.end)?.isWhitespace() == true) "" else " "
    val text = value.text.replaceRange(completion.start, completion.end, model.name + separator)
    return TextFieldValue(text, TextRange(completion.start + model.name.length + separator.length))
}

/** Añade lo dictado tras el texto que ya hubiera (con un espacio si hace falta) y deja el cursor al final. */
internal fun dictatedText(current: TextFieldValue, spoken: String): TextFieldValue {
    val base = current.text
    val glue = if (base.isEmpty() || base.last().isWhitespace()) "" else " "
    val text = base + glue + spoken.trim()
    return TextFieldValue(text, TextRange(text.length))
}

@Composable
internal fun InputBar(ui: AssistantUiState, text: TextFieldValue, onInput: (TextFieldValue) -> Unit,
    onAttach: () -> Unit, onSend: () -> Unit, onStop: () -> Unit, onRemove: (String) -> Unit,
    onReply: (String) -> Unit = {}) {
    val completion = remember(text, ui.catalog) { completeModels(text, ui.catalog) }
    // Banda propia (otro tono y filetes arriba y abajo) para que el cuadro de texto se distinga del chat y de la navegación.
    Surface(color=com.vidadomu.nexus.ui.theme.LocalChromeColor.current,modifier=Modifier.fillMaxWidth()) {
      Column {
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Surface(color=MaterialTheme.colorScheme.surface,shape=if(text.text.contains('\n') || text.text.length>40) NexusTokens.card else NexusTokens.pill,
        border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=0.55f)),
        modifier=Modifier.padding(horizontal=NexusTokens.space8,vertical=NexusTokens.space4)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=NexusTokens.space4,vertical=2.dp)) {
            // Respuestas rápidas del agente: un toque las envía. Se ocultan mientras escribes o piensa.
            if(ui.replies.isNotEmpty() && !ui.thinking && text.text.isEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8),
                contentPadding = PaddingValues(horizontal = NexusTokens.space8, vertical = NexusTokens.space4)) {
                items(ui.replies, key = { it.label }) { reply ->
                    AssistChip(onClick = { onReply(reply.text) }, label = { Text(reply.label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 220.dp)) }, modifier = Modifier.semantics { contentDescription = "Responder: ${reply.text}" })
                }
            }
            completion?.let { suggestions ->
                LazyRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                    items(suggestions.models, key = { it.ref.toString() }) { model ->
                        AssistChip(onClick = { onInput(insertModel(text, suggestions, model)) }, label = { Text(model.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) })
                    }
                }
            }
            if(ui.pendingAttachments.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                items(ui.pendingAttachments, key = { it.id }) { att ->
                    InputChip(selected = true, onClick = { onRemove(att.id) },
                        label = { Text(att.name ?: "Referencia", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp)) },
                        trailingIcon = { Icon(Icons.Default.Close, "Quitar referencia") })
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onAttach, enabled = !ui.attaching) { Icon(Icons.Default.AttachFile, "Adjuntar") }
                val canSend = !ui.thinking && !ui.attaching && (text.text.isNotBlank() || ui.pendingAttachments.isNotEmpty())
                // Campo sin el alto mínimo de 56 dp de TextField: una línea ocupa lo mismo que los iconos.
                val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                androidx.compose.foundation.text.BasicTextField(text, onInput, modifier = Modifier.weight(1f).heightIn(min = NexusTokens.minimumTouch)
                    .semantics { contentDescription = "Mensaje para Nexus" },
                    textStyle = style, cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    minLines = 1, maxLines = if(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480) 2 else 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if(canSend) onSend() }),
                    decorationBox = { inner ->
                        Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                            if (text.text.isEmpty()) Text("Escribe a Nexus…", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    })
                if(ui.thinking) IconButton(onClick = onStop) { Icon(Icons.Default.Stop, "Parar respuesta", tint = MaterialTheme.colorScheme.error) }
                else if(!canSend && !ui.attaching) {
                    // Nada que enviar: el botón es el micrófono. El dictado se inserta en el campo SIN enviarlo.
                    val context = LocalContext.current
                    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        val spoken = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
                        if (result.resultCode == android.app.Activity.RESULT_OK && !spoken.isNullOrBlank()) onInput(dictatedText(text, spoken))
                    }
                    IconButton(onClick = {
                        try {
                            dictate.launch(android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                                putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Habla; podrás revisar el texto antes de enviarlo")
                            })
                        } catch (_: android.content.ActivityNotFoundException) {
                            android.widget.Toast.makeText(context, "Este móvil no tiene reconocimiento de voz disponible", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }) { Icon(Icons.Default.Mic, "Dictar mensaje", tint = MaterialTheme.colorScheme.primary) }
                }
                else IconButton(onClick = onSend, enabled = canSend) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar mensaje", tint = if(canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)) }
            }
        }
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
      }
    }
}

@Composable
internal fun PendingGenerations(ui: AssistantUiState, blockedByDraft:Boolean=false, onGenerate: () -> Unit) {
    val prepared = ui.plans.values.filter { it.executable && !it.busy && it.state in setOf(PlanState.PREPARED, PlanState.AWAITING_CONFIRMATION) }
    val pending = prepared.filter { it.includeInBatch }
    // Con un solo encargo, su tarjeta tiene el único botón: no se repite GENERAR abajo.
    if(pending.size < 2) return
    val app=LocalContext.current.applicationContext as com.vidadomu.nexus.NexusApplication
    val currencyState by app.currencyManager.state.collectAsState()
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space4), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${pending.size} encargos pendientes de confirmar", style = MaterialTheme.typography.labelLarge)
                if(blockedByDraft) Text("Aplica o deshaz los cambios antes de generar el lote.",style=MaterialTheme.typography.labelMedium)
                val estimated = pending.mapNotNull { it.plan.estimate?.amountUsd }
                Text(if(estimated.size == pending.size) remember(estimated,currencyState) {app.currencyManager.formatCost(estimated.sum())} else "Incluye coste sin estimación",
                    style = MaterialTheme.typography.labelSmall)
            }
            val enabled=!ui.thinking && ui.plans.values.none {it.busy} && !blockedByDraft
            FilledTonalButton(onClick=onGenerate,enabled=enabled,shape=NexusTokens.field) {Text("Generar los ${minOf(pending.size,20)}")}
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
                when(p.plan.modality) {
                    "IMAGE" -> AsyncImage(uri, "Resultado generado", Modifier.width(240.dp).aspectRatio(16f/9f)
                        .clip(NexusTokens.field).clickable(onClick = onOpenOutput), contentScale = ContentScale.Fit)
                    "AUDIO" -> AssistantMediaPreview(Attachment(uri,uri,"audio/mpeg","Resultado de audio"))
                    else -> AssistantMediaPreview(Attachment(uri,uri,"video/mp4","Resultado de vídeo"))
                }
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
        TextButton(onClick = { import.launch(arrayOf("image/*", "video/*", "audio/*") + AgentDocuments.types) }, enabled = !ui.attaching) { Icon(Icons.Default.AddPhotoAlternate, null); Text("Importar archivos") }
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
