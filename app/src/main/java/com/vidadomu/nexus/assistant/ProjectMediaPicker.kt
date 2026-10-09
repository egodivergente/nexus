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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.ui.theme.NexusTokens
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
internal fun rememberProjectMediaPicker(onResult: (List<Uri>) -> Unit): ProjectMediaPicker {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val runtime = remember(app) { AssistantRuntime.get(app) }
    val scope = rememberCoroutineScope()
    val deliver by rememberUpdatedState(onResult)
    var request by rememberSaveable { mutableStateOf<MediaPickerRequest?>(null) }
    var visible by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var assets by remember { mutableStateOf(runtime.assets.snapshot().assets) }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val current = request
        if (current != null && uris.isNotEmpty()) {
            busy = true
            scope.launch {
                try {
                    val selected = mutableListOf<Uri>()
                    var failed = 0
                    for (uri in uris.distinct().take(current.maximum)) {
                        try {
                            val mime = context.contentResolver.getType(uri).orEmpty()
                            selected += if (current.projectId != null && (mime.startsWith("image/") || mime.startsWith("video/"))) {
                                Uri.parse(AgentMediaStore.persist(app, uri, mime, current.projectId).uri)
                            } else uri
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed++ }
                    }
                    if (failed > 0) Toast.makeText(context, "No se pudieron importar $failed archivos. Puedes volver a intentarlo.", Toast.LENGTH_LONG).show()
                    if (uris.distinct().size > current.maximum) Toast.makeText(context, "Se pueden seleccionar hasta ${current.maximum} archivos.", Toast.LENGTH_LONG).show()
                    if (selected.isNotEmpty()) {
                        visible = false
                        if (runtime.settings.activeProjectId() == current.projectId) deliver(selected)
                        else Toast.makeText(context, "Archivos guardados en el proyecto de origen. Ábrelo para seleccionarlos.", Toast.LENGTH_LONG).show()
                    }
                    assets = runtime.assets.snapshot().assets
                } finally { busy = false }
            }
        }
    }
    if (visible) request?.let { current ->
        ProjectMediaPickerSheet(
            assets = assets, projectId = current.projectId, projectName = current.projectName,
            mimeTypes = current.types, maximum = current.maximum, busy = busy,
            onDismiss = { if (!busy) visible = false },
            onImport = { documents.launch(current.types.toTypedArray()) },
            onUse = { chosen ->
                visible = false
                if (runtime.settings.activeProjectId() == current.projectId) deliver(chosen.map { Uri.parse(it.uri) })
            },
        )
    }
    return ProjectMediaPicker { types, maximum ->
        if (!busy) {
            val projectId = runtime.settings.activeProjectId()
            request = MediaPickerRequest(projectId, projectId?.let { runtime.assets.findProject(it)?.name }, types.toList(), maximum.coerceAtLeast(1))
            assets = runtime.assets.snapshot().assets
            visible = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectMediaPickerSheet(
    assets: List<Asset>, projectId: String?, projectName: String?, mimeTypes: List<String>, maximum: Int,
    busy: Boolean, onDismiss: () -> Unit, onImport: () -> Unit, onUse: (List<Asset>) -> Unit,
) {
    var query by remember(projectId) { mutableStateOf("") }
    var selected by remember(projectId) { mutableStateOf(emptyList<String>()) }
    val all = remember(assets, projectId, mimeTypes) { projectMediaChoices(assets, projectId, mimeTypes) }
    val filtered = remember(all, query) { all.filter { it.displayName.contains(query.trim(), ignoreCase = true) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = NexusTokens.screenMargin), verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
            Text("Importar fotografías y archivos", style = MaterialTheme.typography.titleMedium)
            Text(projectName?.let { "Guardadas en $it" } ?: "Selecciona un proyecto para conservar aquí tus fotografías.", style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AddPhotoAlternate, null)
                Spacer(Modifier.width(NexusTokens.space8))
                Text("Importar desde el dispositivo")
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(query, { query = it }, label = { Text("Buscar guardadas") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (filtered.isEmpty()) {
                Text(if (projectId == null) "Importa un archivo desde el dispositivo." else if (all.isEmpty()) "Las fotografías que importes a este proyecto aparecerán aquí." else "No hay fotografías con ese nombre.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
            } else LazyVerticalGrid(columns = GridCells.Adaptive(104.dp), modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                items(filtered, key = { it.id }) { asset ->
                    val checked = asset.id in selected
                    Column(Modifier.clip(NexusTokens.field).toggleable(value = checked, role = Role.Checkbox,
                        enabled = !busy && (checked || maximum == 1 || selected.size < maximum),
                        onValueChange = { select -> selected = if (!select) selected - asset.id else if (maximum == 1) listOf(asset.id) else selected + asset.id })) {
                        AsyncImage(asset.uri, asset.displayName, Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Crop)
                        Row {
                            Checkbox(checked, null)
                            Text(asset.displayName, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(top = NexusTokens.space8))
                        }
                    }
                }
            }
            Button(onClick = { onUse(selected.mapNotNull { id -> all.firstOrNull { it.id == id } }) }, enabled = selected.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Usar seleccionadas · ${selected.size}")
            }
            Spacer(Modifier.height(NexusTokens.space8))
        }
    }
}
