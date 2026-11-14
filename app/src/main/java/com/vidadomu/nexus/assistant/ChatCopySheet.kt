package com.vidadomu.nexus.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class ChatCopyEntry(val id: String, val speaker: String, val text: String)

internal fun selectedChatText(entries: List<ChatCopyEntry>, ids: Set<String>): String =
    entries.filter { it.id in ids }.joinToString("\n\n") { "${it.speaker}\n${it.text}" }

/** Selection changes only the clipboard; messages remain in chronological order and are never truncated. */
@Composable
internal fun ChatCopySheet(entries: List<ChatCopyEntry>, initialIds: Set<String>? = null, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(initialIds ?: entries.map { it.id }.toSet()) }
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Copiar conversación") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = selected.size == entries.size, onCheckedChange = { all -> selected = if (all) entries.map { it.id }.toSet() else emptySet() })
                    Text("Todo el chat", Modifier.weight(1f))
                    Text("${selected.size}/${entries.size}", style = MaterialTheme.typography.labelMedium)
                }
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 420.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        Row(Modifier.fillMaxWidth().clickable {
                            selected = if (entry.id in selected) selected - entry.id else selected + entry.id
                        }.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                            Checkbox(checked = entry.id in selected, onCheckedChange = { checked -> selected = if (checked) selected + entry.id else selected - entry.id })
                            Column(Modifier.weight(1f)) {
                                Text(entry.speaker, style = MaterialTheme.typography.labelLarge)
                                Text(entry.text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = {
            TextButton(enabled = selected.isNotEmpty(), onClick = {
                try { clipboard.setText(AnnotatedString(selectedChatText(entries, selected))); onDismiss() }
                catch (_: RuntimeException) { error = "El sistema no ha podido copiar este texto completo. Prueba con menos mensajes." }
            }) { Text(if (selected.size == entries.size) "Copiar todo" else "Copiar selección") }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
