package com.vidadomu.nexus.ui.cut

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidadomu.nexus.cut.command.GroupStatus
import com.vidadomu.nexus.ui.theme.NexusTokens

/**
 * Panel del agente dentro del editor. Se abre desde el muelle y se cierra sin perder nada. Cada turno que
 * cambió el montaje deja una tarjeta con sus pasos, lo comprobado en el motor y «Deshacer» / «Rehacer» de
 * todo el turno; lo que el agente solo ensayó aparece como propuesta para aplicarla o descartarla.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CutChatPanel(
    state: CutChatUiState,
    contextLabel: () -> String,
    compact: Boolean,
    motion: Boolean,
    onClose: () -> Unit,
    onResize: (Float) -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onShowResults: () -> Unit,
    turnStatus: (String) -> GroupStatus?,
    onRevertTurn: (group: String, includeLater: Boolean) -> Unit,
    onRedoTurn: (String) -> Unit,
    onApplyProposal: (CutProposal) -> Unit,
    onDismissProposal: (CutProposal) -> Unit,
    modifier: Modifier = Modifier,
) {
    var copyChat by remember { mutableStateOf(false) }
    if (copyChat) com.vidadomu.nexus.assistant.ChatCopySheet(state.messages.map { message ->
        com.vidadomu.nexus.assistant.ChatCopyEntry(message.id,
            if (message.role == CutChatRole.USER) "Tú" else message.by?.takeIf { it.isNotBlank() } ?: "Nexus", message.text)
    }, onDismiss = { copyChat = false })
    val list = rememberLazyListState()
    val loose = state.proposals.filter { p -> p.messageId == null || state.messages.none { it.id == p.messageId } }
    val itemCount = state.messages.size + loose.size + if (state.thinking) 1 else 0
    LaunchedEffect(state.messages.size, state.streamingText, state.liveSteps.size, state.proposals.size) {
        if (itemCount > 0) list.animateScrollToItem(itemCount - 1)
    }
    val resize by rememberUpdatedState(onResize)
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))) {
        Column(Modifier.fillMaxSize()) {
            // Cabecera: arrastrarla cambia el alto del panel; la raya lo indica.
            Box(Modifier.fillMaxWidth().height(if (compact) 34.dp else 42.dp)
                .pointerInput(Unit) { detectVerticalDragGestures { change, amount -> change.consume(); resize(amount) } }
                .semantics {
                    contentDescription = "Tamaño del panel del agente"
                    customActions = listOf(
                        CustomAccessibilityAction("Agrandar el panel del agente") { resize(-64f); true },
                        CustomAccessibilityAction("Reducir el panel del agente") { resize(64f); true })
                }) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 4.dp).size(width = 28.dp, height = 3.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = .55f), NexusTokens.pill))
                Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(7.dp))
                    Text("Agente", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    val label: () -> String = if (state.thinking) ({ "Trabajando…" }) else contextLabel
                    ContextText(label, Modifier.weight(1f))
                    if (state.messages.isNotEmpty() && !compact) IconButton(onClick = { copyChat = true }) {
                        Icon(Icons.Default.ContentCopy, "Copiar chat o seleccionar mensajes", Modifier.size(18.dp))
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Default.ExpandMore, "Ocultar el agente") }
                }
            }
            if (state.messages.isEmpty() && !state.thinking) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Dime qué quieres cambiar. Veo el montaje, la selección y el cabezal; cada cambio se puede deshacer.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SuggestionChip(onClick = { onDraft("Resume cómo está montado el vídeo y qué mejorarías.") }, label = { Text("Revisar montaje") })
                        SuggestionChip(onClick = { onDraft("Busca los silencios largos y propón quitarlos.") }, label = { Text("Quitar silencios") })
                        SuggestionChip(onClick = { onDraft("Añade un título al principio del montaje.") }, label = { Text("Añadir título") })
                    }
                }
            } else {
                LazyColumn(state = list, modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.messages, key = { it.id }) { message ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MessageBubble(message)
                            val group = message.turnGroup
                            if (message.steps.isNotEmpty()) {
                                val status = remember(group, state.revision) { group?.let(turnStatus) }
                                TurnCard(message, status, onRevertTurn, onRedoTurn)
                            }
                            for (p in state.proposals) if (p.messageId == message.id) ProposalCard(p, onApplyProposal, onDismissProposal)
                        }
                    }
                    items(loose, key = { "proposal-" + it.id }) { p -> ProposalCard(p, onApplyProposal, onDismissProposal) }
                    if (state.thinking) item(key = "cut-agent-stream") { LiveTurn(state, motion) }
                }
            }
            if (state.libraryResults.isNotEmpty()) TextButton(onClick = onShowResults,
                modifier = Modifier.padding(horizontal = 10.dp).heightIn(min = 40.dp)) {
                Text(if (state.libraryResults.size == 1) "Ver archivo encontrado" else "Ver ${state.libraryResults.size} archivos encontrados")
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = state.draft, onValueChange = onDraft,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Pide un cambio al montaje…", style = MaterialTheme.typography.bodyMedium) },
                    enabled = state.ready, minLines = 1, maxLines = if (compact) 2 else 4, shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!state.thinking && state.draft.isNotBlank()) onSend() }),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                )
                FilledIconButton(onClick = if (state.thinking) onStop else onSend,
                    enabled = state.ready && (state.thinking || state.draft.isNotBlank()), modifier = Modifier.size(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(if (state.thinking) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        if (state.thinking) "Detener agente" else "Enviar orden al agente", Modifier.size(23.dp))
                }
            }
            if (!compact && state.modelLabel.isNotBlank()) Text(state.modelLabel,
                Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** El contexto (selección y cabezal) se lee aquí: cambia con la reproducción sin recomponer el chat. */
@Composable
private fun ContextText(label: () -> String, modifier: Modifier) {
    Text(label(), modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun MessageBubble(message: CutChatMessage) {
    val user = message.role == CutChatRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(shape = RoundedCornerShape(12.dp), color = when {
            message.error -> MaterialTheme.colorScheme.errorContainer
            user -> MaterialTheme.colorScheme.primaryContainer
            message.role == CutChatRole.NOTICE -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }, modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(if (user) .90f else 1f)) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (!user && !message.by.isNullOrBlank()) Text(message.by!!, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SelectionContainer { Text(message.text, style = MaterialTheme.typography.bodySmall,
                    color = if (message.error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
}

/**
 * Lo que el agente cambió de verdad en un turno: sus pasos, el antes → después que dio el motor y la
 * acción para deshacerlo (o rehacerlo) entero. Si después hubo otros cambios, se pide confirmación.
 */
@Composable
private fun TurnCard(message: CutChatMessage, status: GroupStatus?, onRevert: (String, Boolean) -> Unit, onRedo: (String) -> Unit) {
    var open by remember(message.id) { mutableStateOf(false) }
    var confirm by remember(message.id) { mutableStateOf(false) }
    val group = message.turnGroup
    val undone = status != null && status.undoable == 0 && status.redoable > 0
    // Parte del turno deshecha desde la cabecera (paso a paso): se dice para que la tarjeta no mienta.
    val partial = status != null && status.undoable > 0 && status.redoable > 0
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (undone) .15f else .35f))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).clickable(enabled = message.steps.size > 1) { open = !open },
                verticalAlignment = Alignment.CenterVertically) {
                Icon(if (undone) Icons.AutoMirrored.Filled.Undo else Icons.Default.Check, null, Modifier.size(16.dp),
                    tint = if (undone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text((when { undone -> "Deshecho · "; partial -> "Deshecho en parte · "; else -> "Aplicado · " }) + CutTurnSummary.headline(message.steps), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (message.steps.size > 1) Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    if (open) "Ocultar pasos" else "Ver los ${message.steps.size} pasos", Modifier.size(18.dp))
            }
            message.verified?.let { v ->
                Text(if (v == "Comprobado en el montaje") v else "Comprobado: $v", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            AnimatedVisibility(open) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    message.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (group != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    status == null -> Unit
                    status.undoable > 0 -> OutlinedButton(onClick = { if (status.laterSteps > 0) confirm = true else onRevert(group, false) },
                        contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (message.steps.size > 1) "Deshacer el turno" else "Deshacer")
                    }
                    status.redoable == 0 -> Text("Ya no está en el historial de esta sesión.", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Unit
                }
                if (status != null && status.redoable > 0) TextButton(onClick = { onRedo(group) }, modifier = Modifier.heightIn(min = 40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Redo, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Rehacer")
                }
            }
        }
    }
    if (confirm && status != null && group != null) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Deshacer también lo posterior") },
        text = {
            val later = status.laterLabels.take(3).joinToString(", ")
            Text("Después de este turno hubo ${status.laterSteps} ${if (status.laterSteps == 1) "cambio" else "cambios"} más" +
                (if (later.isNotBlank()) " ($later${if (status.laterSteps > 3) "…" else ""})" else "") +
                ". Para deshacer los del agente se deshacen también esos; quedan en «Rehacer».")
        },
        confirmButton = { TextButton(onClick = { confirm = false; onRevert(group, true) }) { Text("Deshacer todo") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
    )
}

/** Un cambio que el agente ensayó sin aplicar: se ve qué haría y se decide. */
@Composable
private fun ProposalCard(p: CutProposal, onApply: (CutProposal) -> Unit, onDismiss: (CutProposal) -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Propuesta del agente · sin aplicar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(p.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull("${p.changeCount} ${if (p.changeCount == 1) "cambio" else "cambios"}", p.detail).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onApply(p) }, modifier = Modifier.heightIn(min = 40.dp),
                    colors = ButtonDefaults.buttonColors()) { Text("Aplicar") }
                TextButton(onClick = { onDismiss(p) }, modifier = Modifier.heightIn(min = 40.dp)) { Text("Descartar") }
            }
        }
    }
}

/** Mientras el agente trabaja: qué hace ahora y los pasos que ya ha aplicado en el montaje. */
@Composable
private fun LiveTurn(state: CutChatUiState, motion: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.streamingText.isBlank()) {
                if (motion) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(state.streamingText.ifBlank { state.activity.ifBlank { "Pensando el cambio…" } },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (s in state.liveSteps) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(s, style = MaterialTheme.typography.labelMedium)
        }
    }
}
