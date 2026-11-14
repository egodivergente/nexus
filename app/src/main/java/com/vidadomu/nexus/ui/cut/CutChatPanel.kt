package com.vidadomu.nexus.ui.cut

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The editor's conversation is always a single tap away; sending never leaves the montage. */
@Composable
internal fun CutChatPanel(
    state: CutChatUiState,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onMinimize: () -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onShowResults: () -> Unit,
    selectionLabel: String,
    agentActivity: String?,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var copyChat by remember { mutableStateOf(false) }
    if (copyChat) com.vidadomu.nexus.assistant.ChatCopySheet(state.messages.map { message ->
        com.vidadomu.nexus.assistant.ChatCopyEntry(message.id,
            if (message.role == CutChatRole.USER) "Tú" else message.by?.takeIf { it.isNotBlank() } ?: "Nexus", message.text)
    }, onDismiss = { copyChat = false })
    val list = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.streamingText, expanded) {
        if (expanded && (state.messages.isNotEmpty() || state.thinking)) {
            list.animateScrollToItem((state.messages.size + if (state.thinking) 1 else 0).coerceAtLeast(1) - 1)
        }
    }
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            if (!compact) {
            Row(Modifier.fillMaxWidth().heightIn(min = 38.dp).clickable { onExpanded(!expanded) }.padding(start = 14.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(7.dp))
                Text("Agente", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(if (state.thinking) "Trabajando…" else selectionLabel, Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.messages.isNotEmpty()) IconButton(onClick = { copyChat = true }) {
                    Icon(Icons.Default.ContentCopy, "Copiar chat o seleccionar mensajes")
                }
                Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    if (expanded) "Ocultar conversación" else "Mostrar conversación", Modifier.padding(4.dp).size(20.dp))
                IconButton(onClick = onMinimize) { Icon(Icons.Default.Close, "Minimizar agente") }
            }
            if (expanded) {
                if (state.messages.isEmpty() && !state.thinking) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Dime qué quieres cambiar en este montaje.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SuggestionChip(onClick = { onDraft("Resume cómo está montado el vídeo.") }, label = { Text("Revisar montaje") })
                            SuggestionChip(onClick = { onDraft("Añade un título al principio del montaje.") }, label = { Text("Añadir título") })
                        }
                    }
                } else {
                    LazyColumn(state = list, modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.messages, key = { it.id }) { message ->
                            val user = message.role == CutChatRole.USER
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                                Surface(shape = RoundedCornerShape(12.dp), color = when {
                                    message.error -> MaterialTheme.colorScheme.errorContainer
                                    user -> MaterialTheme.colorScheme.primaryContainer
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
                        if (state.thinking) item(key = "cut-agent-stream") {
                            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (state.streamingText.isBlank()) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(state.streamingText.ifBlank { state.activity.ifBlank { "Pensando el cambio…" } },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                val preview = if (state.thinking) state.activity.ifBlank { "Pensando el cambio…" }
                    else state.messages.lastOrNull()?.text ?: "Habla con el agente sin salir del editor"
                Text(preview, Modifier.weight(1f).fillMaxWidth().clickable { onExpanded(true) }.padding(horizontal = 14.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!state.thinking && agentActivity != null) Text(agentActivity,
                Modifier.padding(horizontal = 14.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (state.libraryResults.isNotEmpty()) TextButton(onClick = onShowResults,
                modifier = Modifier.padding(horizontal = 10.dp).heightIn(min = 40.dp)) {
                Text(if (state.libraryResults.size == 1) "Ver archivo encontrado" else "Ver ${state.libraryResults.size} archivos encontrados")
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = state.draft, onValueChange = onDraft,
                    modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) onExpanded(true) },
                    placeholder = { Text("Pide un cambio al montaje…", style = MaterialTheme.typography.bodyMedium) },
                    enabled = state.ready, minLines = 1, maxLines = 3, shape = RoundedCornerShape(14.dp),
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
                Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
