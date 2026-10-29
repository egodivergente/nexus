package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.ui.theme.NexusTokens

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Closing a tab changes presentation only. Conversations, drafts, media and jobs remain saved. */
internal class ConversationTabs(context: Context) {
    val prefs=context.getSharedPreferences("nexus_conversation_tabs",0)
    fun closed(): Set<String> = prefs.getStringSet("closed",emptySet()).orEmpty().toSet()
    fun open(id: String) {prefs.edit().putStringSet("closed",closed()-id).apply()}
    fun close(id: String) {prefs.edit().putStringSet("closed",closed()+id).apply()}
    fun pinned(): Set<String> = prefs.getStringSet("pinned", emptySet()).orEmpty().toSet()
    fun pin(id: String, enabled: Boolean) { prefs.edit().putStringSet("pinned", if (enabled) pinned()+id else pinned()-id).apply() }
}

private fun entries(ui: AssistantUiState, vm: AssistantViewModel): List<AssistantChatEntry> {
    val saved = vm.conversations()
    val current = saved.firstOrNull { it.id == ui.conversationId }
        ?: AssistantChatEntry(ui.conversationId,
            ui.items.filterIsInstance<ChatItem.User>().firstOrNull()?.text ?: "Nueva conversación",
            projectId = ui.activeProjectId)
    return (listOf(current) + saved).distinctBy { it.id }
}

private fun drawerTitle(titles: ConversationTitles, chat: AssistantChatEntry): String =
    titles.custom(chat.id) ?: ConversationTitles.smart(chat.title.removePrefix("↔ "), max = 180)

/** Saved conversations stay scoped to the active project; closing only changes this local list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun ConversationDrawer(ui: AssistantUiState, vm: AssistantViewModel, onClose: () -> Unit) {
    val conversationIndex by vm.conversationIndex.collectAsState()
    LaunchedEffect(Unit) { vm.refreshConversations() }
    val chats = remember(ui.conversationId, ui.items.size, ui.activeProjectId, conversationIndex) { entries(ui, vm) }
    val context = LocalContext.current
    val tabs = remember(context) { ConversationTabs(context) }
    val titles = remember(context) { ConversationTitles(context) }
    val haptic = com.vidadomu.nexus.ui.theme.rememberNexusHaptics()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var titleRevision by remember { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf<AssistantChatEntry?>(null) }
    var closed by remember { mutableStateOf(tabs.closed()) }
    var pinned by remember { mutableStateOf(tabs.pinned()) }
    var showClosed by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var query by androidx.compose.runtime.saveable.rememberSaveable(ui.activeProjectId) { mutableStateOf("") }
    val listState = androidx.compose.runtime.saveable.rememberSaveable(ui.activeProjectId,
        saver = androidx.compose.foundation.lazy.LazyListState.Saver) { androidx.compose.foundation.lazy.LazyListState() }
    val closePanel: () -> Unit = { focus.clearFocus(); keyboard?.hide(); onClose() }
    val canCarry = !ui.thinking && !ui.carrying && ui.workingIn == null && ui.items.any { it is ChatItem.User }
    val projectName = ui.projects.firstOrNull { it.id == ui.activeProjectId }?.name
        ?: if (ui.activeProjectId == null) "Todos los proyectos" else "Proyecto"
    val closedCount = chats.count { it.id in closed }
    val drawerWidth = minOf(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp * .9f, 360.dp)
    val shown = remember(chats, titleRevision, closed, pinned, showClosed, query) {
        drawerConversations(chats.map { it.copy(title = drawerTitle(titles, it)) }, closed, pinned, showClosed, query)
    }

    renaming?.let { chat ->
        var draft by remember(chat.id) { mutableStateOf(drawerTitle(titles, chat)) }
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Nombre de la conversación") },
            text = { Column {
                com.vidadomu.nexus.ui.components.StudioTextField(draft, { draft = it }, singleLine = true,
                    modifier = Modifier.semantics { contentDescription = "Nombre de la conversación" })
                TextButton(onClick = { titles.rename(chat.id, null); titleRevision++; renaming = null }) { Text("Usar título automático") }
            } },
            confirmButton = { TextButton(onClick = {
                titles.rename(chat.id, draft); titleRevision++; renaming = null
                haptic.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.CONFIRMATION)
            }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } })
    }

    ModalDrawerSheet(Modifier.width(drawerWidth).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = NexusTokens.space16, end = NexusTokens.space4),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = NexusTokens.space8)) {
                Text("Conversaciones", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() })
                Text(projectName, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            FilledTonalButton(onClick = { closePanel(); vm.newConversation() },
                contentPadding=PaddingValues(horizontal=NexusTokens.space12),
                modifier=Modifier.heightIn(min=NexusTokens.minimumTouch).semantics {contentDescription="Nueva conversación"}) {
                Icon(Icons.Default.Add,null,Modifier.size(NexusTokens.iconSmall))
                Spacer(Modifier.width(NexusTokens.space4))
                Text("Nueva",style=MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = closePanel) { Icon(Icons.Default.Close, "Cerrar conversaciones", Modifier.size(NexusTokens.iconMedium)) }
        }
        if (canCarry || ui.carrying) TextButton(onClick = {
            if (canCarry) { vm.newConversationWithContext(); closePanel() }
        }, enabled = canCarry, modifier = Modifier.padding(horizontal = NexusTokens.space12)) {
            if (ui.carrying) CircularProgressIndicator(Modifier.size(NexusTokens.iconSmall), strokeWidth = 2.dp)
            else Icon(Icons.Default.Forum, null, Modifier.size(NexusTokens.iconSmall))
            Spacer(Modifier.width(NexusTokens.space8))
            Text(if (ui.carrying) "Preparando el resumen…" else "Nueva con este contexto", style = MaterialTheme.typography.labelMedium)
        }
        OutlinedTextField(query, { query = it },
            Modifier.fillMaxWidth().padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space8)
                .semantics { contentDescription = "Buscar conversaciones" },
            singleLine = true, textStyle=MaterialTheme.typography.bodyMedium,shape=NexusTokens.field,
            colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant),
            placeholder = { Text("Buscar conversaciones", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(NexusTokens.iconMedium)) },
            trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) {
                Icon(Icons.Default.Close, "Limpiar búsqueda", Modifier.size(NexusTokens.iconSmall))
            } }) else null)
        if (closedCount > 0) TextButton(onClick = { showClosed = !showClosed },
            modifier = Modifier.padding(horizontal = NexusTokens.space12)) {
            Text(if (showClosed) "Ocultar cerradas" else "Ver cerradas · $closedCount", style = MaterialTheme.typography.labelMedium)
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
            contentPadding = PaddingValues(horizontal = NexusTokens.space8, vertical = NexusTokens.space4),
            verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
            if (shown.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(NexusTokens.space16), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    Text(if (query.isNotBlank()) "No hay coincidencias" else "Aún no hay conversaciones visibles",
                        style = MaterialTheme.typography.bodyMedium)
                    if (query.isNotBlank()) TextButton(onClick = { query = "" }) { Text("Limpiar búsqueda") }
                    else if (closedCount > 0) TextButton(onClick = { showClosed = true }) { Text("Ver conversaciones cerradas") }
                }
            }
            items(shown, key = { it.id }) { chat ->
                ConversationDrawerRow(chat, selected = chat.id == ui.conversationId,
                    working = chat.id == ui.workingIn || chat.id == ui.conversationId && ui.thinking,
                    pinned = chat.id in pinned, closed = chat.id in closed,
                    onOpen = {
                        tabs.open(chat.id); closed = tabs.closed(); closePanel(); vm.openConversation(chat.id)
                        haptic.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.SELECTION, changed = chat.id != ui.conversationId)
                    },
                    onRename = { renaming = chats.firstOrNull { it.id == chat.id } ?: chat },
                    onPin = { tabs.pin(chat.id, chat.id !in pinned); pinned = tabs.pinned()
                        haptic.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.SELECTION) },
                    onRestore = { tabs.open(chat.id); closed = tabs.closed()
                        haptic.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.CONFIRMATION) },
                    onClose = {
                        tabs.close(chat.id); closed = tabs.closed()
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            if (snackbar.showSnackbar("Conversación cerrada de la lista", "Deshacer", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                                tabs.open(chat.id); closed = tabs.closed()
                            }
                        }
                    })
            }
        }
        SnackbarHost(snackbar, Modifier.padding(horizontal = NexusTokens.space8))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ConversationDrawerRow(chat: AssistantChatEntry, selected: Boolean, working: Boolean,
    pinned: Boolean, closed: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onPin: () -> Unit,
    onRestore: () -> Unit, onClose: () -> Unit) {
    var menu by remember(chat.id) { mutableStateOf(false) }
    val date = remember(chat.updatedAt, java.time.LocalDate.now(), androidx.compose.ui.platform.LocalConfiguration.current) {
        conversationRelativeDate(chat.updatedAt)
    }
    val status = listOfNotNull("Actual".takeIf { selected }, "Fijada".takeIf { pinned },
        "Cerrada".takeIf { closed }, "Nexus sigue trabajando aquí".takeIf { working }).joinToString(". ")
    Surface(shape = NexusTokens.field,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .1f) else androidx.compose.ui.graphics.Color.Transparent) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .combinedClickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen, onLongClick = { menu = true },
                onLongClickLabel = "Opciones de conversación")
            .semantics(mergeDescendants = true) { this.selected = selected; if (status.isNotBlank()) stateDescription = status }
            .padding(start = NexusTokens.space12, top = NexusTokens.space8, bottom = NexusTokens.space8),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                    if (pinned) Icon(Icons.Default.PushPin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(chat.title, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                if (working || chat.preview.isNotBlank() && chat.preview != chat.title || date.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                        Text(if (working) "Nexus está trabajando…" else chat.preview.takeIf { it != chat.title }.orEmpty(),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (date.isNotBlank()) Text(date, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, "Opciones de ${chat.title}", Modifier.size(NexusTokens.iconMedium)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (pinned) "Desfijar" else "Fijar arriba") },
                        leadingIcon = { Icon(Icons.Default.PushPin, null) }, onClick = { menu = false; onPin() })
                    DropdownMenuItem(text = { Text("Renombrar") }, leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(if (closed) "Restaurar en la lista" else "Cerrar de la lista") },
                        leadingIcon = { Icon(if (closed) Icons.Default.Restore else Icons.Default.Close, null) },
                        onClick = { menu = false; if (closed) onRestore() else onClose() })
                }
            }
        }
    }
}

/** Long press on an empty spot of the chat: the actions one reaches for most, as a small floating menu. */
@Composable internal fun ChatContextMenu(ui: AssistantUiState, vm: AssistantViewModel, expanded: Boolean, offset: DpOffset,
    onDismiss: () -> Unit, onProjects: () -> Unit, onAppearance: () -> Unit, onSettings: () -> Unit, barPinned: Boolean, onPinBar: (Boolean) -> Unit,
    onCopyChat: () -> Unit, onShareChat: () -> Unit, onImportImages: () -> Unit) {
    DropdownMenu(expanded=expanded,onDismissRequest=onDismiss,offset=offset) {
        DropdownMenuItem(text={Text("Nueva conversación")},leadingIcon={Icon(Icons.Default.Add,null)},onClick={onDismiss();vm.newConversation()})
        DropdownMenuItem(text={Text("Importar imágenes")},leadingIcon={Icon(Icons.Default.AddPhotoAlternate,null)},onClick={onDismiss();onImportImages()})
        DropdownMenuItem(text={Text("Cambiar de proyecto")},leadingIcon={Icon(Icons.Default.FolderOpen,null)},onClick={onDismiss();onProjects()})
        HorizontalDivider()
        val hasMessages=ui.items.any {it is ChatItem.User || it is ChatItem.Assistant}
        DropdownMenuItem(text={Text("Compartir chat")},leadingIcon={Icon(Icons.Default.Share,null)},enabled=hasMessages,onClick={onDismiss();onShareChat()})
        DropdownMenuItem(text={Text("Copiar todo el chat")},leadingIcon={Icon(Icons.Default.ContentCopy,null)},enabled=hasMessages,onClick={onDismiss();onCopyChat()})
        HorizontalDivider()
        DropdownMenuItem(text={Text("Apariencia: colores y tamaño de letra")},leadingIcon={Icon(Icons.Default.Palette,null)},onClick={onDismiss();onAppearance()})
        DropdownMenuItem(text={Text(if(barPinned) "Esconder la barra de pestañas" else "Dejar la barra de pestañas siempre visible")},
            leadingIcon={Icon(Icons.Default.ViewDay,null)},onClick={onDismiss();onPinBar(!barPinned)})
        DropdownMenuItem(text={Text("Todos los ajustes")},leadingIcon={Icon(Icons.Default.Settings,null)},onClick={onDismiss();onSettings()})
    }
}

/** Fast look-and-feel changes without leaving the chat. The full screen stays in Ajustes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun QuickAppearanceSheet(onDismiss: () -> Unit, onMoreSettings: () -> Unit) {
    val appearance=com.vidadomu.nexus.ui.theme.rememberAppearancePreferences()
    var theme by remember {mutableStateOf(appearance.theme())}
    var scale by remember {mutableFloatStateOf(appearance.fontScale())}
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=NexusTokens.space24),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("Apariencia",style=MaterialTheme.typography.titleLarge)
            Text("Tema",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                items(com.vidadomu.nexus.ui.theme.NexusAppearance.entries.toList(),key={option -> option.name}) {option ->
                    FilterChip(theme==option,{theme=option;appearance.setTheme(option)},label={Text(option.label)})
                }
            }
            Text("Tamaño de letra · ${(scale*100).toInt()} %",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(scale,{scale=it},onValueChangeFinished={appearance.setFontScale(scale)},valueRange=.8f..1.4f)
            TextButton(onClick={onDismiss();onMoreSettings()},contentPadding=PaddingValues(0.dp)) {Text("Más opciones: fondo, movimiento, háptica…")}
        }
    }
}


/** The whole conversation as plain text, with the time of each message and how long each reply took. */
internal fun chatTranscript(ui: AssistantUiState): String {
    val time=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT)
    fun stamp(at: Long) = if(at>0) " · "+time.format(java.util.Date(at)) else ""
    return ui.items.mapNotNull {item->
        when(item) {
            is ChatItem.User -> "Tú"+stamp(item.at)+"\n"+item.text
            is ChatItem.Assistant -> if(item.text.isBlank()) null else "Nexus"+(if(item.by.isNotBlank()) " ("+item.by+")" else "")+stamp(item.at)+(if(item.tookMs>0) " · respondió en "+formatTook(item.tookMs) else "")+"\n"+item.text
            else -> null
        }
    }.joinToString("\n\n")
}

/** Sends the conversation as a .txt attachment through the Android share sheet. Nothing leaves the phone otherwise. */
internal fun shareChat(context: Context, ui: AssistantUiState) {
    runCatching {
        val folder=java.io.File(context.cacheDir,"chat_export").apply {mkdirs()}
        val stamp=java.text.SimpleDateFormat("yyyyMMdd-HHmm",java.util.Locale.ROOT).format(java.util.Date())
        val file=java.io.File(folder,"Nexus-chat-$stamp.txt").apply {writeText(chatTranscript(ui),Charsets.UTF_8)}
        val uri=androidx.core.content.FileProvider.getUriForFile(context,context.packageName+".fileprovider",file)
        val intent=android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type="text/plain";putExtra(android.content.Intent.EXTRA_STREAM,uri);putExtra(android.content.Intent.EXTRA_SUBJECT,"Chat de Nexus")
            clipData=android.content.ClipData.newRawUri(file.name,uri);addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent,"Compartir chat"))
    }.onFailure {android.widget.Toast.makeText(context,"No se pudo compartir el chat",android.widget.Toast.LENGTH_SHORT).show()}
}
