package com.vidadomu.nexus.ui.cut

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.cut.CutHubProject
import com.vidadomu.nexus.cut.CutProjectHub
import com.vidadomu.nexus.cut.CutProjectHubSnapshot
import com.vidadomu.nexus.cut.NexusCut
import com.vidadomu.nexus.ui.components.NexusTopAppBar
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.ScreenBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Projects retain their Nexus identities; the hub projects their Library and Cut workspace together. */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun CutHomeScreen(
    isActive: Boolean = true,
    onSelectProject: (String) -> Unit,
    onArchivedProject: (String) -> Unit,
    onOpenStory: (String) -> Unit,
    onOpenMaterial: (String) -> Unit,
    onOpenGenerations: (String) -> Unit,
    onOpenAgent: (String, String) -> Unit,
    onImportMedia: (String?) -> Unit,
) {
    val app = LocalContext.current.applicationContext as NexusApplication
    val runtime = remember(app) { AssistantRuntime.get(app) }
    val workspace = remember(app) { NexusCut.workspace(app) }
    val hub = remember(runtime, workspace) { CutProjectHub(runtime.assets, workspace) }
    val index by runtime.assets.changes.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val detailStates = rememberSaveableStateHolder()
    var snapshot by remember { mutableStateOf(CutProjectHubSnapshot()) }
    var loading by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var nameAction by rememberSaveable { mutableStateOf<String?>(null) }
    var nameTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val selected = snapshot.projects.firstOrNull { it.id == selectedId }
    val imageLoader = remember(app) { ImageLoader.Builder(app).components { add(VideoFrameDecoder.Factory()) }.build() }
    DisposableEffect(imageLoader) { onDispose { imageLoader.shutdown() } }
    BackHandler(enabled = isActive && selectedId != null) { if (!busy) selectedId = null }
    BackHandler(enabled = isActive && busy) { /* Finish the current metadata transaction before leaving. */ }

    LaunchedEffect(isActive, index, refresh) {
        if (!isActive) return@LaunchedEffect
        loading = true
        failure = null
        try { snapshot = hub.snapshot(); loaded = true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error.message ?: "No se pudieron abrir los proyectos." }
        finally { loading = false }
    }
    fun openName(action: String, projectId: String? = null, initial: String = "") {
        if (busy) return
        nameAction = action; nameTarget = projectId; name = initial; nameError = null
    }
    fun mutate(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block(); snapshot = hub.snapshot(); loaded = true; failure = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val message = error.message ?: "No se pudo guardar el cambio."
                if (nameAction != null) nameError = message
                else scope.launch { snackbar.showSnackbar(message) }
            }
            finally { busy = false }
        }
    }
    fun archive(project: CutHubProject) = mutate {
        val changed = hub.setArchived(project.id, !project.archived)
        if (changed.archived) onArchivedProject(changed.id) else onSelectProject(changed.id)
    }
    fun select(project: CutHubProject) {
        if (busy) return
        selectedId = project.id
        if (!project.archived) onSelectProject(project.id)
    }
    fun saveName() {
        val action = nameAction ?: return
        val target = nameTarget
        val title = name.trim()
        if (title.isBlank()) return
        mutate {
            when (action) {
                "project" -> hub.createProject(title).let { selectedId = it.id; onSelectProject(it.id) }
                "rename" -> hub.renameProject(requireNotNull(target), title)
                "montage" -> {
                    val montage = hub.createMontage(target, title)
                    workspace.setActive(montage.projectId)
                    NexusCut.open(montage.projectId)
                }
            }
            nameAction = null
        }
    }

    Scaffold(
        containerColor = ScreenBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            NexusTopAppBar(
                title = { Text("Nexus Cut", fontWeight = FontWeight.SemiBold) }, showBalance = false,
                navigationIcon = {
                    if (selectedId != null) IconButton(onClick = { if (!busy) selectedId = null }, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver a proyectos")
                    }
                },
                actions = {
                    if (selectedId == null) {
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(if (searching) Icons.Default.Close else Icons.Default.Search, if (searching) "Cerrar búsqueda" else "Buscar proyectos")
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones de proyectos") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Mostrar archivados (${snapshot.projects.count { it.archived }})") },
                                    leadingIcon = { Icon(if (showArchived) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null) },
                                    onClick = { showArchived = !showArchived; menu = false })
                                DropdownMenuItem(text = { Text("Actualizar") }, leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                    onClick = { refresh++; menu = false }, enabled = !loading)
                            }
                        }
                    } else selected?.let { project ->
                        CutProjectMenu(project, !busy, onRename = { openName("rename", project.id, project.name) }, onArchive = { archive(project) })
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            failure?.let { message ->
                Row(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.screenMargin), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { refresh++ }) { Text("Reintentar") }
                }
            }
            if (selectedId == null) {
                val needle = query.trim()
                val visible = snapshot.projects.filter { project ->
                    (showArchived || !project.archived) && (needle.isBlank() || project.name.contains(needle, true) ||
                        project.project.description.orEmpty().contains(needle, true) || project.montages.any { it.name.contains(needle, true) })
                }
                val loose = snapshot.looseMontages.filter { needle.isBlank() || it.name.contains(needle, true) }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(NexusTokens.screenMargin),
                    verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                    item(key = "project-heading") {
                        Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Proyectos", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                                Text("${snapshot.projects.count { !it.archived }} activos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(onClick = { openName("project") }, enabled = !busy, shape = NexusTokens.action,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(NexusTokens.space8)); Text("Nuevo proyecto")
                            }
                            if (searching) OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                placeholder = { Text("Buscar proyectos o montajes") }, shape = NexusTokens.field,
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Limpiar búsqueda") } })
                            if (showArchived) Text("Incluye proyectos archivados", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(visible, key = { "project-${it.id}" }) { project ->
                        CutProjectRow(project, imageLoader, onOpen = { select(project) },
                            onRename = { openName("rename", project.id, project.name) }, onArchive = { archive(project) }, enabled = !busy)
                    }
                    if (loaded && !loading && visible.isEmpty()) item(key = "project-empty") {
                        when {
                            needle.isNotBlank() -> CutHubEmpty("No hay proyectos que coincidan", "Prueba con otro nombre.", "Limpiar búsqueda", { query = "" })
                            snapshot.projects.isEmpty() -> CutHubEmpty("Tu próximo proyecto empieza aquí", "Reúne su guion, material y montajes en un mismo lugar.")
                            !showArchived -> CutHubEmpty("Tus proyectos están archivados", "Puedes restaurarlos cuando quieras continuar.", "Ver archivados", { showArchived = true })
                        }
                    }
                    if (snapshot.looseMontages.isNotEmpty()) {
                        item(key = "loose-heading") {
                            Column(Modifier.padding(top = NexusTokens.space16), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                                Text("Montajes sin proyecto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${snapshot.looseMontages.size} guardados", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(loose, key = { "loose-${it.id}" }) { montage ->
                            CutHubMontageRow(montage, snapshot.montageCovers[montage.id], imageLoader, enabled = !busy,
                                onOpen = { if (!busy) NexusCut.open(montage.id) })
                        }
                        if (loose.isEmpty()) item(key = "loose-empty") { Text("Ningún montaje coincide con la búsqueda.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            } else if (selected != null) {
                detailStates.SaveableStateProvider(selected.id) {
                    CutProjectDetail(selected, snapshot, imageLoader, busy,
                        onNewMontage = { openName("montage", selected.id, "Montaje ${selected.montageCount + 1}") },
                        onRestore = { archive(selected) }, onImport = { if (!busy) onImportMedia(selected.id) },
                        onOpenStory = { if (!busy) onOpenStory(selected.id) }, onOpenMaterial = { if (!busy) onOpenMaterial(selected.id) },
                        onOpenGenerations = { if (!busy) onOpenGenerations(selected.id) }, onOpenAgent = { draft -> if (!busy) onOpenAgent(selected.id, draft) },
                        onOpenMontage = { id -> if (!busy) NexusCut.open(id) })
                }
            } else if (loaded && !loading && !busy) {
                CutHubEmpty("El proyecto ya no está disponible", "Sus montajes sin proyecto siguen en la lista principal.", "Volver a proyectos", { selectedId = null })
            }
        }
    }
    if (nameAction != null && isActive) AlertDialog(
        onDismissRequest = { if (!busy) nameAction = null },
        title = { Text(when (nameAction) { "project" -> "Nuevo proyecto"; "rename" -> "Renombrar proyecto"; else -> "Nuevo montaje" }) },
        text = { OutlinedTextField(name, { name = it; nameError = null }, enabled = !busy, singleLine = true, label = { Text("Nombre") },
            isError = nameError != null, supportingText = { nameError?.let { Text(it) } }, shape = NexusTokens.field) },
        confirmButton = { TextButton(onClick = ::saveName, enabled = !busy && name.isNotBlank()) { Text(if (busy) "Guardando…" else if (nameAction == "rename") "Guardar" else "Crear") } },
        dismissButton = { TextButton(onClick = { nameAction = null }, enabled = !busy) { Text("Cancelar") } },
    )
}

@Composable
private fun CutProjectDetail(
    project: CutHubProject, snapshot: CutProjectHubSnapshot, imageLoader: ImageLoader, busy: Boolean,
    onNewMontage: () -> Unit, onRestore: () -> Unit, onImport: () -> Unit,
    onOpenStory: () -> Unit, onOpenMaterial: () -> Unit, onOpenGenerations: () -> Unit,
    onOpenAgent: (String) -> Unit, onOpenMontage: (String) -> Unit,
) {
    var agentDraft by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(NexusTokens.screenMargin),
        verticalArrangement = Arrangement.spacedBy(NexusTokens.space16)) {
        item(key = "hero") {
            Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12), verticalAlignment = Alignment.CenterVertically) {
                CutHubThumbnail(project.coverUri, imageLoader, Modifier.size(76.dp), Icons.Default.FolderOpen)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                    Text(project.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(if (project.archived) "Proyecto archivado" else "Actividad ${cutHubDate(project.activityAt)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    project.project.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item(key = "project-actions") {
            if (project.archived) Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                Text("${cutHubCount(project.sceneCount, "escena", "escenas")} · ${cutHubCount(project.materialCount, "archivo", "archivos")} · ${cutHubCount(project.generationCount, "generación", "generaciones")}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = NexusTokens.action) {
                    Icon(Icons.Default.Unarchive, null); Spacer(Modifier.width(NexusTokens.space8)); Text("Restaurar proyecto")
                }
                Text("Restaura el proyecto para continuar editando. Su material y sus montajes se conservan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                Button(onClick = onNewMontage, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = NexusTokens.action) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(NexusTokens.space8)); Text("Nuevo montaje")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    CutProjectShortcut("Guion", cutHubCount(project.sceneCount, "escena", "escenas"), Icons.Default.MovieFilter, Modifier.weight(1f), onOpenStory, enabled = !busy)
                    CutProjectShortcut("Material", cutHubCount(project.materialCount, "archivo", "archivos"), Icons.Default.Collections, Modifier.weight(1f), onOpenMaterial, enabled = !busy)
                    CutProjectShortcut("Generaciones", project.generationCount.toString(), Icons.Default.AutoAwesome, Modifier.weight(1f), onOpenGenerations, enabled = !busy)
                }
                TextButton(onClick = onImport, enabled = !busy, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.FileUpload, null, Modifier.size(NexusTokens.iconSmall)); Spacer(Modifier.width(NexusTokens.space8)); Text("Importar medios")
                }
            }
        }
        item(key = "montages-heading") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Montajes", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(project.montageCount.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(project.montages, key = { it.id }) { montage ->
            CutHubMontageRow(montage, snapshot.montageCovers[montage.id], imageLoader,
                enabled = !project.archived && !busy, onOpen = { onOpenMontage(montage.id) })
        }
        if (project.montages.isEmpty()) item(key = "montage-empty") {
            Text(if (project.archived) "Este proyecto no tiene montajes guardados." else "Todavía no hay montajes. Crea uno para empezar a editar.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!project.archived) item(key = "project-agent") {
            Surface(shape = NexusTokens.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(NexusTokens.space16), verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                        Icon(Icons.Default.Assistant, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text("Agente del proyecto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(project.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    OutlinedTextField(agentDraft, { agentDraft = it }, enabled = !busy, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3,
                        placeholder = { Text("¿Qué quieres hacer con este proyecto?") }, shape = NexusTokens.field)
                    FilledTonalButton(onClick = { onOpenAgent(agentDraft.trim().ifBlank { "Ayúdame a continuar el proyecto «${project.name}». Revisa el guion y el material disponible y propón el siguiente paso." }) },
                        enabled = !busy, modifier = Modifier.align(Alignment.End), shape = NexusTokens.action) { Text("Abrir conversación") }
                }
            }
        }
    }
}
