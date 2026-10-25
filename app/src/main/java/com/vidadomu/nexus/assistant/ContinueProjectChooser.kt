package com.vidadomu.nexus.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.settings.RuntimePreferences
import com.vidadomu.nexus.ui.theme.NexusTokens
import java.text.DateFormat
import java.util.Date

/** [embedded] draws only the content (big fox + project cards) so the Agent tab keeps its composer and the bottom bar; otherwise it is a full-screen sheet, as needed for shared files. */
@Composable internal fun ContinueProjectChooser(vm: AssistantViewModel, shared: PendingShare? = null,
    onRetryShare: () -> Unit = {}, onChoose: (String) -> Unit, onDismiss: () -> Unit, embedded: Boolean = false) {
    val ui by vm.ui.collectAsState()
    val context=LocalContext.current
    val store=remember {RuntimePreferences.get(context)}
    val rules by store.state.collectAsState()
    val prefs=remember {context.getSharedPreferences("nexus_project_appearance",0)}
    var pinned by remember {mutableStateOf(prefs.getStringSet("pinned",emptySet()).orEmpty().toSet())}
    var creating by rememberSaveable {mutableStateOf(false)}
    var name by rememberSaveable {mutableStateOf("")}
    var creatingName by remember {mutableStateOf<String?>(null)}
    val busy=ui.thinking || ui.plans.values.any {it.busy}
    // Activity per project from the in-memory index: computed once, never inside the sort comparator.
    val conversationIndex by vm.conversationIndex.collectAsState()
    val activity=remember(conversationIndex) {conversationIndex.groupBy {it.projectId}.mapValues {(_,v)->v.maxOf {it.updatedAt}}}
    val projects=ui.projects.sortedWith(compareByDescending<com.vidadomu.nexus.agent.core.assets.Project> {it.id in pinned}
        .thenBy {if(rules.value("startup.order")=="nombre") it.name.lowercase() else ""}
        .thenByDescending {(activity[it.id] ?: 0L).takeIf {at->at>0} ?: it.createdAt})
    var all by rememberSaveable {mutableStateOf(false)}
    val showFox=androidx.compose.ui.platform.LocalDensity.current.fontScale<1.5f
    val shown=if(all) projects else projects.take(store.int("startup.recentCount"))
    LaunchedEffect(ui.projects) {creatingName?.let {n->ui.projects.firstOrNull {it.name==n}?.let {creatingName=null;onChoose(it.id)}}}
    val container: @Composable (@Composable () -> Unit) -> Unit = {content ->
        if(embedded) content() else Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {content()}
    }
    container {
        LazyColumn(Modifier.fillMaxSize().then(if(embedded) Modifier else Modifier.safeDrawingPadding()),
            contentPadding=PaddingValues(horizontal=if(embedded) 44.dp else 24.dp,vertical=if(embedded) 8.dp else 24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            // The NEXUS fox (nodes in cyan, amber details) stays the identity of the entry screen; it shrinks for a share, and is omitted under very large text.
            if(showFox) item {
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.vidadomu.nexus.R.drawable.nexus_logo),"Logo oficial de NEXUS",
                    Modifier.fillMaxWidth().height(if(embedded) 250.dp else if(shared==null) 104.dp else 56.dp).padding(bottom=if(embedded) 20.dp else 0.dp),
                    contentScale=androidx.compose.ui.layout.ContentScale.Fit)
            }
            if(!embedded || shared!=null) item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if(shared==null) "Continuar en…" else "Compartir en…",style=MaterialTheme.typography.headlineMedium)
                    Text(if(shared==null) "Tus proyectos, a mano." else "${shared.files.size} archivos conservados · elige proyecto",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick=onDismiss) {Icon(Icons.Default.Close,"Continuar en el proyecto actual")}
            }}
            if(shared!=null) item {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(shared.files) {f->coil.compose.AsyncImage(model=f.attachment?.uri ?: f.uri,contentDescription=f.attachment?.name ?: "Archivo compartido",modifier=Modifier.size(64.dp),contentScale=androidx.compose.ui.layout.ContentScale.Crop)}
                }
                shared.files.firstOrNull {it.error!=null}?.error?.let {Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick=onRetryShare) {Text("Volver a importar")}}
            }
            items(shown,key={it.id}) {project->
                val accent=listOf(Color(0xFF68DFE0),Color(0xFFAA8DFA),Color(0xFFF4BD6C),Color(0xFFEF8E9D))[Math.floorMod(project.id.hashCode(),4)]
                Surface(shape=NexusTokens.card,color=MaterialTheme.colorScheme.surfaceContainerLow,
                    border=BorderStroke(1.dp,accent.copy(alpha=.28f)),modifier=Modifier.fillMaxWidth().clickable(enabled=!busy && shared?.ready!=false) {onChoose(project.id)}) {
                    Row(Modifier.heightIn(min=if(embedded) 80.dp else 96.dp),verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(60.dp).background(accent))
                        Icon(Icons.Default.FolderOpen,null,Modifier.padding(16.dp).size(25.dp),tint=accent)
                        Column(Modifier.weight(1f).padding(vertical=14.dp)) {
                            Text(project.name,style=MaterialTheme.typography.titleLarge)
                            val at=(activity[project.id] ?: 0L).takeIf {it>0} ?: project.createdAt
                            Text("Última actividad · "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(at)),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick={pinned=if(project.id in pinned) pinned-project.id else pinned+project.id;prefs.edit().putStringSet("pinned",pinned).apply()}) {
                            Icon(if(project.id in pinned) Icons.Default.PushPin else Icons.Default.PushPin,if(project.id in pinned) "Desfijar proyecto" else "Fijar proyecto",Modifier.size(18.dp),tint=if(project.id in pinned) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if(projects.size>shown.size) item {TextButton(onClick={all=true}) {Text("Todos los proyectos (${projects.size})")}}
            item {OutlinedButton(onClick={creating=true},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Icon(Icons.Default.Add,null);Text("Nuevo proyecto")}}
            // Embedded: typing in the composer or choosing a card already continues; no extra link is needed.
            if(!embedded) ui.activeProjectId?.let {id->item {TextButton(onClick={onChoose(id)},enabled=!busy && shared?.ready!=false,modifier=Modifier.fillMaxWidth()) {Text("Abrir último proyecto")}}}
        }
    }
    if(creating) AlertDialog(onDismissRequest={creating=false},title={Text("Nuevo proyecto")},text={OutlinedTextField(name,{name=it},label={Text("Nombre")},singleLine=true)},
        confirmButton={TextButton(onClick={creatingName=name.trim();vm.createProject(name);creating=false},enabled=name.isNotBlank()) {Text("Crear")}},dismissButton={TextButton(onClick={creating=false}) {Text("Cancelar")}})
}
