package com.vidadomu.nexus.assistant

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.settings.RuntimePreferences
import com.vidadomu.nexus.ui.theme.NexusTokens

/** Closing a tab changes presentation only. Conversations, drafts, media and jobs remain saved. */
internal class ConversationTabs(context: Context) {
    val prefs=context.getSharedPreferences("nexus_conversation_tabs",0)
    fun closed(): Set<String> = prefs.getStringSet("closed",emptySet()).orEmpty().toSet()
    fun open(id: String) {prefs.edit().putStringSet("closed",closed()-id).apply()}
    fun close(id: String) {prefs.edit().putStringSet("closed",closed()+id).apply()}
}

@Composable private fun rememberTabs(): Pair<ConversationTabs,Set<String>> {
    val context=LocalContext.current
    val tabs=remember {ConversationTabs(context)}
    var closed by remember {mutableStateOf(tabs.closed())}
    DisposableEffect(tabs) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,_->closed=tabs.closed()}
        tabs.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {tabs.prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return tabs to closed
}

private fun entries(ui: AssistantUiState, vm: AssistantViewModel) =
    (listOf(AssistantChatEntry(ui.conversationId,ui.items.filterIsInstance<ChatItem.User>().firstOrNull()?.text?.take(70) ?: "Nueva conversación"))+vm.conversations()).distinctBy {it.id}

private fun closeTab(id: String, tabs: ConversationTabs, ui: AssistantUiState, vm: AssistantViewModel) {
    tabs.close(id)
    if(id==ui.conversationId) {
        val next=vm.conversations().firstOrNull {it.id !in tabs.closed() && it.id!=id}
        if(next==null) vm.newConversation() else vm.openConversation(next.id)
    }
}

@Composable internal fun ConversationTabStrip(ui: AssistantUiState, vm: AssistantViewModel) {
    val rules by RuntimePreferences.get(LocalContext.current).state.collectAsState()
    if(!rules.value("chat.tabsVisible").toBoolean()) return
    val (tabs,closed)=rememberTabs()
    LazyRow(Modifier.fillMaxWidth().heightIn(min=40.dp),contentPadding=PaddingValues(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        items(entries(ui,vm).filter {it.id !in closed},key={it.id}) {chat->
            Surface(shape=NexusTokens.pill,color=if(chat.id==ui.conversationId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(chat.title,Modifier.widthIn(max=116.dp).clickable(enabled=!ui.thinking) {tabs.open(chat.id);vm.openConversation(chat.id)}.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    IconButton(onClick={closeTab(chat.id,tabs,ui,vm)},enabled=!ui.thinking,modifier=Modifier.size(32.dp)) {Icon(Icons.Default.Close,"Cerrar conversación ${chat.title}",Modifier.size(13.dp))}
                }
            }
        }
        item {IconButton(onClick=vm::newConversation,enabled=!ui.thinking,modifier=Modifier.size(36.dp)) {Icon(Icons.Default.Add,"Nueva conversación",Modifier.size(18.dp))}}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ConversationMenu(ui: AssistantUiState, vm: AssistantViewModel, onProjects: ()->Unit, onSettings: ()->Unit) {
    var open by remember {mutableStateOf(false)}
    val store=RuntimePreferences.get(LocalContext.current)
    val rules by store.state.collectAsState()
    val (tabs,closed)=rememberTabs()
    IconButton(onClick={open=true},modifier=Modifier.size(40.dp)) {Icon(Icons.Default.MoreHoriz,"Conversaciones y proyectos",Modifier.size(18.dp))}
    if(open) ModalBottomSheet(onDismissRequest={open=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=560.dp),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            item {Text("Conversaciones",style=MaterialTheme.typography.titleLarge)}
            item {Row {
                TextButton(onClick={vm.newConversation();open=false},enabled=!ui.thinking) {Icon(Icons.Default.Add,null,Modifier.size(16.dp));Text("Nueva")}
                TextButton(onClick={open=false;onProjects()}) {Text("Proyectos")}
                TextButton(onClick={open=false;onSettings()}) {Text("Ajustes")}
            }}
            item {Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Mostrar pestañas en el chat",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                Switch(rules.value("chat.tabsVisible").toBoolean(),{store.set("chat.tabsVisible",it.toString())})
            }}
            items(entries(ui,vm),key={it.id}) {chat->Row(verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={tabs.open(chat.id);vm.openConversation(chat.id);open=false},enabled=!ui.thinking,modifier=Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(chat.title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                        if(chat.id in closed) Text("Guardada · tocar para reabrir",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if(chat.id !in closed) IconButton(onClick={closeTab(chat.id,tabs,ui,vm)},enabled=!ui.thinking) {Icon(Icons.Default.Close,"Cerrar conversación ${chat.title}",Modifier.size(16.dp))}
            }}
            item {Text("Cerrar una pestaña conserva su conversación y su borrador.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}
