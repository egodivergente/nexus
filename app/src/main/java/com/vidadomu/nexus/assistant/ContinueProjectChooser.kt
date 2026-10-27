package com.vidadomu.nexus.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin as OutlinedPushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
    var search by rememberSaveable {mutableStateOf("")}
    val showFox=androidx.compose.ui.platform.LocalDensity.current.fontScale<1.5f
    // Search appears only once the list is long enough to need it; while searching every project is a candidate.
    val searchable=projects.size>6
    val matching=if(search.isBlank()) projects else projects.filter {it.name.contains(search.trim(),ignoreCase=true)}
    val shown=if(all || search.isNotBlank()) matching else matching.take(store.int("startup.recentCount"))
    val pinnedShown=shown.filter {it.id in pinned}
    val recentShown=shown.filterNot {it.id in pinned}
    LaunchedEffect(ui.projects) {creatingName?.let {n->ui.projects.firstOrNull {it.name==n}?.let {creatingName=null;onChoose(it.id)}}}
    val container: @Composable (@Composable () -> Unit) -> Unit = {content ->
        if(embedded) content() else Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {content()}
    }
    val projectRow: @Composable (com.vidadomu.nexus.agent.core.assets.Project) -> Unit = {project ->
        val accent=listOf(Color(0xFF68DFE0),Color(0xFFAA8DFA),Color(0xFFF4BD6C),Color(0xFFEF8E9D))[Math.floorMod(project.id.hashCode(),4)]
        val isPinned=project.id in pinned
        // One compact row for every project: same radius, tone and edge; the colour bar is the only per-project mark.
        Surface(shape=NexusTokens.card,color=MaterialTheme.colorScheme.surfaceContainerLow,
            border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
            modifier=Modifier.fillMaxWidth().clickable(enabled=!busy && shared?.ready!=false) {onChoose(project.id)}) {
            Row(Modifier.heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(36.dp).background(accent,androidx.compose.foundation.shape.RoundedCornerShape(topEnd=2.dp,bottomEnd=2.dp)))
                Icon(Icons.Default.FolderOpen,null,Modifier.padding(horizontal=14.dp).size(22.dp),tint=accent)
                Column(Modifier.weight(1f).padding(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(project.name,style=MaterialTheme.typography.titleMedium,maxLines=2,
                        overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    val at=(activity[project.id] ?: 0L).takeIf {it>0} ?: project.createdAt
                    Text("Última actividad · "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(at)),
                        style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                }
                IconButton(onClick={pinned=if(isPinned) pinned-project.id else pinned+project.id;prefs.edit().putStringSet("pinned",pinned).apply()}) {
                    Icon(if(isPinned) Icons.Default.PushPin else Icons.Outlined.OutlinedPushPin,if(isPinned) "Desfijar ${project.name}" else "Fijar ${project.name}",
                        Modifier.size(18.dp),tint=if(isPinned) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    val sectionLabel: @Composable (String) -> Unit = {label ->
        Text(label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,
            modifier=Modifier.padding(top=4.dp,start=4.dp).semantics {heading()})
    }
    container {
        Box(Modifier.fillMaxSize()) {
            // Onboarding: the fox is the backdrop of the choice, not a block above it, so the projects get the screen.
            if(showFox && embedded) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.vidadomu.nexus.R.drawable.nexus_logo),null,
                Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(1f).padding(16.dp).blur(14.dp).alpha(.14f),
                contentScale=androidx.compose.ui.layout.ContentScale.Fit)
        LazyColumn(Modifier.fillMaxSize().then(if(embedded) Modifier else Modifier.safeDrawingPadding()),
            contentPadding=PaddingValues(horizontal=if(embedded) 20.dp else 24.dp,vertical=if(embedded) 12.dp else 24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(showFox && !embedded) item {
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.vidadomu.nexus.R.drawable.nexus_logo),"Logo oficial de NEXUS",
                    Modifier.fillMaxWidth().height(if(shared==null) 104.dp else 56.dp),
                    contentScale=androidx.compose.ui.layout.ContentScale.Fit)
            }
            if(!embedded || shared!=null) item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if(shared==null) "Continuar en…" else "Compartir en…",style=MaterialTheme.typography.headlineMedium)
                    Text(if(shared==null) "Tus proyectos, a mano." else "${shared.files.size} archivos conservados · elige proyecto",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick=onDismiss) {Icon(Icons.Default.Close,"Continuar en el proyecto actual")}
            }} else item {
                Row(Modifier.fillMaxWidth().padding(bottom=4.dp),verticalAlignment=Alignment.Bottom) {
                    Text("Elige un proyecto",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f).semantics {heading()})
                    Text("${projects.size}",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if(shared!=null) item {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(shared.files) {f->coil.compose.AsyncImage(model=f.attachment?.uri ?: f.uri,contentDescription=f.attachment?.name ?: "Archivo compartido",modifier=Modifier.size(64.dp),contentScale=androidx.compose.ui.layout.ContentScale.Crop)}
                }
                shared.files.firstOrNull {it.error!=null}?.error?.let {Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick=onRetryShare) {Text("Volver a importar")}}
            }
            if(searchable) item {
                com.vidadomu.nexus.ui.components.StudioTextField(search,{search=it},Modifier.fillMaxWidth(),singleLine=true,
                    placeholder={Text("Buscar proyecto")},leadingIcon={Icon(Icons.Default.Search,null)},
                    trailingIcon=if(search.isNotEmpty()) ({IconButton(onClick={search=""}) {Icon(Icons.Default.Close,"Borrar búsqueda")}}) else null)
            }
            if(pinnedShown.isNotEmpty()) {
                item {sectionLabel("Fijados")}
                items(pinnedShown,key={"p"+it.id}) {projectRow(it)}
                if(recentShown.isNotEmpty()) item {sectionLabel(if(search.isNotBlank()) "Resultados" else "Recientes")}
            }
            items(recentShown,key={it.id}) {projectRow(it)}
            if(search.isNotBlank() && shown.isEmpty()) item {
                Text("Ningún proyecto contiene «${search.trim()}».",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(search.isBlank() && matching.size>shown.size) item {TextButton(onClick={all=true}) {Text("Todos los proyectos (${projects.size})")}}
            item {OutlinedButton(onClick={creating=true},enabled=!busy,modifier=Modifier.fillMaxWidth().padding(top=4.dp)) {Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Nuevo proyecto")}}
            // Embedded: typing in the composer or choosing a card already continues; no extra link is needed.
            if(!embedded) ui.activeProjectId?.let {id->item {TextButton(onClick={onChoose(id)},enabled=!busy && shared?.ready!=false,modifier=Modifier.fillMaxWidth()) {Text("Abrir último proyecto")}}}
        }
        }
    }
    if(creating) AlertDialog(onDismissRequest={creating=false},title={Text("Nuevo proyecto")},text={OutlinedTextField(name,{name=it},label={Text("Nombre")},singleLine=true)},
        confirmButton={TextButton(onClick={creatingName=name.trim();vm.createProject(name);creating=false},enabled=name.isNotBlank()) {Text("Crear")}},dismissButton={TextButton(onClick={creating=false}) {Text("Cancelar")}})
}
