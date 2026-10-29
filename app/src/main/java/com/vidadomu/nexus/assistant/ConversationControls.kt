package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.ui.theme.NexusTokens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.settings.RuntimePreferences

/** Closing a tab changes presentation only. Conversations, drafts, media and jobs remain saved. */
internal class ConversationTabs(context: Context) {
    val prefs=context.getSharedPreferences("nexus_conversation_tabs",0)
    fun closed(): Set<String> = prefs.getStringSet("closed",emptySet()).orEmpty().toSet()
    fun open(id: String) {prefs.edit().putStringSet("closed",closed()-id).apply()}
    fun close(id: String) {prefs.edit().putStringSet("closed",closed()+id).apply()}
}

private fun entries(ui: AssistantUiState, vm: AssistantViewModel) =
    (listOf(AssistantChatEntry(ui.conversationId,ui.items.filterIsInstance<ChatItem.User>().firstOrNull()?.text?.take(70) ?: "Nueva conversación"))+vm.conversations()).distinctBy {it.id}

/**
 * Conversations panel, opened by dragging from left to right over the chat. The one where the agent is still
 * working shows a spinner; «Nueva con este contexto» starts a conversation from a summary of the current one.
 */
@Composable internal fun ConversationDrawer(ui: AssistantUiState, vm: AssistantViewModel, onClose: () -> Unit) {
    val conversationIndex by vm.conversationIndex.collectAsState()
    LaunchedEffect(Unit) {vm.refreshConversations()}
    val chats=remember(ui.conversationId,ui.items.size,ui.workingIn,ui.activeProjectId,conversationIndex) {entries(ui,vm)}
    val context=LocalContext.current
    val tabs=remember {ConversationTabs(context)}
    val titles=remember {ConversationTitles(context)}
    var titleRevision by remember {mutableIntStateOf(0)}
    var renaming by remember {mutableStateOf<AssistantChatEntry?>(null)}
    renaming?.let {chat ->
        var draft by remember(chat.id) {mutableStateOf(titles.display(chat.id, chat.title))}
        AlertDialog(onDismissRequest={renaming=null},title={Text("Nombre de la conversación")},
            text={com.vidadomu.nexus.ui.components.StudioTextField(draft,{draft=it},singleLine=true)},
            confirmButton={TextButton(onClick={titles.rename(chat.id,draft);titleRevision++;renaming=null}) {Text("Guardar")}},
            dismissButton={TextButton(onClick={titles.rename(chat.id,null);titleRevision++;renaming=null}) {Text("Usar automático")}})
    }
    var closed by remember {mutableStateOf(tabs.closed())}
    var showClosed by remember {mutableStateOf(false)}
    var query by remember {mutableStateOf("")}
    val canCarry=!ui.thinking && !ui.carrying && ui.workingIn==null && ui.items.any {it is ChatItem.User}
    ModalDrawerSheet(Modifier.widthIn(max=320.dp)) {
        Text("Conversaciones",Modifier.padding(start=28.dp,top=NexusTokens.space24,bottom=NexusTokens.space12),style=MaterialTheme.typography.titleMedium)
        NavigationDrawerItem(label={Text("Nueva conversación")},icon={Icon(Icons.Default.Add,null)},selected=false,
            onClick={onClose();vm.newConversation()},modifier=Modifier.padding(horizontal=NexusTokens.space12))
        if(canCarry || ui.carrying) NavigationDrawerItem(label={Text(if(ui.carrying) "Preparando el resumen…" else "Nueva con este contexto")},
            icon={if(ui.carrying) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else Icon(Icons.Default.Forum,null)},selected=false,
            onClick={if(canCarry) {vm.newConversationWithContext();onClose()}},modifier=Modifier.padding(horizontal=NexusTokens.space12))
        HorizontalDivider(Modifier.padding(vertical=NexusTokens.space8,horizontal=28.dp))
        com.vidadomu.nexus.ui.components.StudioTextField(query,{query=it},Modifier.padding(horizontal=NexusTokens.space16),singleLine=true,placeholder={Text("Buscar conversación")})
        if(closed.isNotEmpty()) TextButton(onClick={showClosed=!showClosed},modifier=Modifier.padding(horizontal=NexusTokens.space16)) {Text(if(showClosed) "Ocultar cerradas" else "Ver cerradas · ${closed.size}")}
        val shown=remember(chats,titleRevision,closed,showClosed,query) {
            chats.map {it.copy(title=titles.display(it.id,it.title))}
                .filter {(showClosed || it.id !in closed) && (it.title.contains(query,true) || it.preview.contains(query,true))}
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
            items(shown,key={it.id}) {chat->
                NavigationDrawerItem(selected=chat.id==ui.conversationId,onClick={tabs.open(chat.id);closed=tabs.closed();onClose();vm.openConversation(chat.id)},
                    label={Column {Text(chat.title,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if(chat.preview.isNotBlank() && chat.preview!=chat.title) Text(chat.preview,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(chat.updatedAt>0) Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(chat.updatedAt)),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}},
                    badge={Row(verticalAlignment=Alignment.CenterVertically) {
                        if(chat.id==ui.workingIn) CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
                        IconButton(onClick={renaming=chats.firstOrNull {it.id==chat.id} ?: chat}) {Icon(Icons.Default.Edit,"Cambiar el nombre de la conversación",Modifier.size(16.dp))}
                        IconButton(onClick={tabs.close(chat.id);closed=tabs.closed()}) {Icon(Icons.Default.Close,"Cerrar conversación de la lista; conservar historial",Modifier.size(16.dp))}
                    }},
                    modifier=Modifier.padding(horizontal=NexusTokens.space12).semantics {
                        if(chat.id==ui.workingIn) contentDescription="${chat.title}: Nexus sigue trabajando aquí"
                    })
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
