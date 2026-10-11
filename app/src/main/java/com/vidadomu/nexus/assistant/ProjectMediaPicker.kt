package com.vidadomu.nexus.assistant

import android.net.Uri
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.components.StudioFilterChip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.NexusSheetSystemBars
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class ProjectMediaPicker(private val open: (Array<String>, Int) -> Unit) {
    fun launch(mimeTypes: Array<String>, maxSelection: Int = 50) = open(mimeTypes, maxSelection)
}

internal fun projectMediaMimeType(context: Context, uri: Uri): String? =
    AssistantRuntime.get(context.applicationContext as NexusApplication).assets.assetByUri(uri.toString())?.mimeType
        ?: context.contentResolver.getType(uri)

private data class MediaPickerRequest(val projectId: String?, val projectName: String?, val types: List<String>, val maximum: Int) : java.io.Serializable

/** Keeps a private copy before returning to the caller, without uploading or generating anything. */
@Composable
internal fun rememberProjectMediaPicker(onResult: (List<Uri>) -> Unit): ProjectMediaPicker =
    rememberProjectMediaPicker(false, onResult)

@Composable
internal fun rememberProjectMediaPicker(managementOnly: Boolean, onResult: (List<Uri>) -> Unit): ProjectMediaPicker {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val runtime = remember(app) { AssistantRuntime.get(app) }
    val scope = rememberCoroutineScope()
    val recentPrefs=remember {context.getSharedPreferences("recent_media_v1",Context.MODE_PRIVATE)}
    fun recordRecent(uris: List<Uri>) {recentPrefs.edit().apply {uris.forEach {putLong(it.toString(),System.currentTimeMillis())}}.apply()}
    val deliver by rememberUpdatedState(onResult)
    var request by rememberSaveable { mutableStateOf<MediaPickerRequest?>(null) }
    var visible by rememberSaveable { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val operationKey = rememberSaveable { java.util.UUID.randomUUID().toString() }
    val library: ProjectLibraryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "project-library-$operationKey")
    val operation by library.state.collectAsState()
    val busy = importing || operation.busy
    val index by runtime.assets.changes.collectAsState()
    val assets = index.assets
    LaunchedEffect(operation.message) {
        operation.message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); library.clearMessage() }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"), library::export)
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val current = request
        if (current != null && uris.isNotEmpty()) {
            importing = true
            scope.launch {
                try {
                    val selected = mutableListOf<Uri>()
                    var failed = 0
                    var failureReason: String? = null
                    for (uri in uris.distinct().take(current.maximum)) {
                        try {
                            val mime = context.contentResolver.getType(uri).orEmpty()
                            selected += Uri.parse(AgentMediaStore.persist(app, uri, mime, current.projectId).uri)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { failed++;failureReason=error.message }
                    }
                    if (failed > 0) Toast.makeText(context, "No se pudieron importar $failed archivos. ${failureReason ?: "Puedes volver a intentarlo."}", Toast.LENGTH_LONG).show()
                    if (uris.distinct().size > current.maximum) Toast.makeText(context, "Se pueden seleccionar hasta ${current.maximum} archivos.", Toast.LENGTH_LONG).show()
                    if (selected.isNotEmpty()) {
                        recordRecent(selected)
                        visible = false
                        if (runtime.settings.activeProjectId() == current.projectId) deliver(selected)
                        else Toast.makeText(context, "Archivos guardados en el proyecto de origen. Ábrelo para seleccionarlos.", Toast.LENGTH_LONG).show()
                    }
                } finally { importing = false }
            }
        }
    }
    if (visible) request?.let { current ->
        ProjectMediaPickerSheet(
            assets = assets, projectId = current.projectId, projectName = current.projectName,
            mimeTypes = current.types, maximum = current.maximum, busy = busy,
            onDismiss = { if (!busy) visible = false },
            onImport = { documents.launch(current.types.toTypedArray()) },
            managementOnly = managementOnly,
            projects = index.projects.filter { !it.archived },
            onProjectChange = { project -> request = current.copy(projectId = project.id, projectName = project.name) },
            progress = operation.progress,
            onCancel = library::cancel,
            onDelete = { chosen -> current.projectId?.let { library.delete(it, chosen) } },
            onExport = { chosen -> current.projectId?.let {
                library.prepareExport(it, chosen)
                val name = current.projectName.orEmpty().replace(Regex("[^\\p{L}\\p{N} _-]"), "_").take(60).ifBlank { "proyecto" }
                export.launch("$name-imagenes.zip")
            } },
            onUse = { chosen ->
                recordRecent(chosen.map {Uri.parse(it.uri)})
                visible = false
                if (runtime.settings.activeProjectId() == current.projectId) deliver(chosen.map { Uri.parse(it.uri) })
            },
        )
    }
    return ProjectMediaPicker { types, maximum ->
        if (!busy) {
            val projectId = runtime.settings.activeProjectId()
            request = MediaPickerRequest(projectId, projectId?.let { runtime.assets.findProject(it)?.name }, types.toList(), maximum.coerceAtLeast(1))
            visible = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ProjectMediaPickerSheet(
    assets: List<Asset>, projectId: String?, projectName: String?, mimeTypes: List<String>, maximum: Int,
    busy: Boolean, onDismiss: () -> Unit, onImport: () -> Unit, onUse: (List<Asset>) -> Unit,
    managementOnly: Boolean = false, progress: String? = null, onCancel: () -> Unit = {},
    onDelete: (List<Asset>) -> Unit = {}, onExport: (List<Asset>) -> Unit = {},
    projects: List<Project> = emptyList(), onProjectChange: (Project) -> Unit = {},
) {
    val context=LocalContext.current
    val recentPrefs=remember {context.getSharedPreferences("recent_media_v1",Context.MODE_PRIVATE)}
    var recent by remember(projectId) {mutableStateOf(true)}
    var query by remember(projectId) { mutableStateOf("") }
    var selected by remember(projectId) { mutableStateOf(emptyList<String>()) }
    var managing by remember(projectId, managementOnly) { mutableStateOf(managementOnly) }
    var confirmDelete by remember(projectId) { mutableStateOf<List<Asset>?>(null) }
    var projectMenu by remember { mutableStateOf(false) }
    val all = remember(assets, projectId, mimeTypes, managing, recent) {
        projectMediaChoices(assets, projectId, if(managing) listOf("image/*") else mimeTypes,
            includeGenerated=managing, allProjects=recent && !managing)
            .let { if(recent && !managing) it.sortedByDescending {asset -> recentPrefs.getLong(asset.uri,asset.createdAt)} else it }
    }
    val chosen = all.filter { it.id in selected }
    LaunchedEffect(all) { selected = selected.filter { id -> all.any { it.id == id } } }
    confirmDelete?.let { toDelete ->
        AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("¿Borrar ${toDelete.size} de la biblioteca?") },
            text = { Text("Dejarán de aparecer en la biblioteca. Se conservarán los archivos para no romper las conversaciones ni los trabajos anteriores.") },
            confirmButton = { TextButton(onClick = { confirmDelete = null; onDelete(toDelete); selected = emptyList() }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } })
    }
    val filtered = remember(all, query) { all.filter { it.displayName.contains(query.trim(), ignoreCase = true) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = NexusTokens.screenMargin), verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
            Text(if (managing) "Biblioteca del proyecto" else "Importar fotografías y archivos", style = MaterialTheme.typography.titleMedium)
            if (managementOnly) Box {
                TextButton(onClick = { projectMenu = true }, enabled = !busy && projects.isNotEmpty()) {
                    Text(projectName?.let { "Proyecto: $it ▾" } ?: "Elegir proyecto ▾")
                }
                DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                    projects.forEach { project -> DropdownMenuItem(text = { Text(project.name) }, onClick = {
                        projectMenu = false; onProjectChange(project)
                    }) }
                }
            } else FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                StudioFilterChip(recent,{recent=true;selected=emptyList()},label={Text("Recientes")})
                if(projectId!=null) StudioFilterChip(!recent,{recent=false;selected=emptyList()},label={Text(projectName ?: "Proyecto")})
            }
            if (!managementOnly) TextButton(onClick = { managing = !managing; selected = emptyList() }, enabled = !busy) {
                Text(if (managing) "Volver a importar" else "Gestionar biblioteca")
            }
            if (!managing) FilledTonalButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AddPhotoAlternate, null)
                Spacer(Modifier.width(NexusTokens.space8))
                Text("Importar desde el dispositivo")
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                progress?.let { Row(Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text("Cancelar") }
                } }
            }
            if (managing && all.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { selected = if (chosen.size == all.size) emptyList() else all.map { it.id } }, enabled = !busy) {
                        Text(if (chosen.size == all.size) "Desmarcar todas" else "Seleccionar todas")
                    }
                    TextButton(onClick = { confirmDelete = chosen }, enabled = chosen.isNotEmpty() && !busy) { Text("Borrar · ${chosen.size}") }
                }
            }
            StudioTextField(query, { query = it }, placeholder = { Text("Buscar archivos") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (filtered.isEmpty()) {
                Text(if (projectId == null && managing) "Elige un proyecto para gestionar sus imágenes." else if (projectId == null) "Todavía no tienes recientes. Importa un archivo una vez para reutilizarlo aquí." else if (all.isEmpty()) "Este proyecto todavía no tiene imágenes guardadas." else "No hay fotografías con ese nombre.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
            } else LazyVerticalGrid(columns = GridCells.Adaptive(104.dp), modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                items(filtered, key = { it.id }) { asset ->
                    val checked = asset.id in selected
                    Column(Modifier.clip(NexusTokens.field).semantics {contentDescription=asset.displayName}.toggleable(value = checked, role = Role.Checkbox,
                        enabled = !busy && (managing || checked || maximum == 1 || selected.size < maximum),
                        onValueChange = { select -> selected = if (!select) selected - asset.id else if (!managing && maximum == 1) listOf(asset.id) else selected + asset.id })) {
                        if(asset.mimeType?.startsWith("image/")==true) AsyncImage(asset.uri, null, Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Crop)
                        else Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth().aspectRatio(1f)) {
                            Box(contentAlignment=Alignment.Center) {Icon(if(asset.mimeType?.startsWith("video/")==true) Icons.Default.Movie else Icons.Default.Description,null,Modifier.size(32.dp))}
                        }
                        Row {
                            Checkbox(checked, null)
                            Text(asset.displayName, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(top = NexusTokens.space8))
                        }
                    }
                }
            }
            if (managing) {
                Button(onClick = { onExport(chosen) }, enabled = chosen.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("ZIP de seleccionadas · ${chosen.size}")
                }
                OutlinedButton(onClick = { onExport(all) }, enabled = all.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("ZIP de todas las imágenes · ${all.size}")
                }
            } else Button(onClick = { onUse(chosen) }, enabled = chosen.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Usar seleccionadas · ${chosen.size}")
            }
            Spacer(Modifier.height(NexusTokens.space8))
        }
    }
}
