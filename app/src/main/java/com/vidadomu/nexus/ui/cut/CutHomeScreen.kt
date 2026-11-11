package com.vidadomu.nexus.ui.cut

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.cut.NexusCut
import com.vidadomu.nexus.cut.isLocalCutUri
import com.vidadomu.nexus.cut.command.CutWorkspace
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.storage.ProjectStore
import com.vidadomu.nexus.ui.components.NexusTopAppBar
import com.vidadomu.nexus.ui.theme.NexusTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** The montage shelf reads the shared workspace. Creation and editing stay in its command/session API. */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun CutHomeScreen(isActive: Boolean = true) {
    val app = LocalContext.current.applicationContext as NexusApplication
    val runtime = remember(app) { AssistantRuntime.get(app) }
    val workspace = remember(app) { NexusCut.workspace(app) }
    val index by runtime.assets.changes.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var montages by remember { mutableStateOf<List<ProjectStore.Summary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val activeProject = index.projects.firstOrNull { !it.archived && it.id == runtime.settings.activeProjectId() }
    val imageLoader = remember(app) {
        ImageLoader.Builder(app).components { add(VideoFrameDecoder.Factory()) }.build()
    }
    DisposableEffect(imageLoader) { onDispose { imageLoader.shutdown() } }

    LaunchedEffect(isActive, refresh) {
        if (!isActive) return@LaunchedEffect
        loading = true
        failure = null
        try { montages = workspace.list() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error.message ?: "No se pudieron leer los montajes." }
        finally { loading = false }
    }
    fun create() {
        if (saving || name.isBlank()) return
        saving = true
        scope.launch {
            try {
                val montage = workspace.create(name.trim(), activeProject?.id)
                workspace.setActive(montage.projectId)
                creating = false
                name = ""
                NexusCut.open(montage.projectId)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { snackbar.showSnackbar(error.message ?: "No se pudo crear el montaje.") }
            finally { saving = false }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            NexusTopAppBar(title = { Text("Nexus Cut", fontWeight = FontWeight.SemiBold) }, showBalance = false,
                actions = { IconButton(onClick = { refresh++ }, enabled = !loading) { Icon(Icons.Default.Refresh, "Actualizar montajes") } })
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(164.dp), modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(NexusTokens.screenMargin),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tus montajes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text("Dale forma a tu historia.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { name = ""; creating = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Nuevo montaje")
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (failure != null) {
                        Text(failure!!, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { refresh++ }) { Text("Volver a intentar") }
                    }
                }
            }
            if (!loading && failure == null && montages.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Default.ContentCut, null, Modifier.padding(24.dp).size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text("Aquí empieza tu próximo vídeo", style = MaterialTheme.typography.titleMedium)
                    Text("Crea un montaje o marca archivos en Biblioteca y toca Montar.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(montages, key = { it.id }) { montage ->
                val nexusProject = index.projects.firstOrNull { it.id == montage.nexusProjectId }
                val scene = index.scenes.firstOrNull { it.id == montage.sceneId }
                CutMontageCard(montage, listOfNotNull(nexusProject?.name, scene?.let { it.title ?: it.code }).joinToString(" · "),
                    workspace, imageLoader, onOpen = { NexusCut.open(montage.id) })
            }
        }
    }
    if (creating && isActive) AlertDialog(
        onDismissRequest = { if (!saving) creating = false },
        title = { Text("Nuevo montaje") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, enabled = !saving, singleLine = true,
                    label = { Text("Nombre") }, placeholder = { Text("Mi montaje") })
                activeProject?.let { Text("Proyecto: ${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = { TextButton(onClick = ::create, enabled = !saving && name.isNotBlank()) { Text(if (saving) "Creando…" else "Crear montaje") } },
        dismissButton = { TextButton(onClick = { creating = false }, enabled = !saving) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CutMontageCard(summary: ProjectStore.Summary, projectLabel: String, workspace: CutWorkspace, imageLoader: ImageLoader, onOpen: () -> Unit) {
    val context = LocalContext.current
    val cover by produceState<String?>(null, summary.id, summary.modifiedAtMs) {
        value = withContext(Dispatchers.IO) {
            val project = try { workspace.opened(summary.id)?.state?.value?.project ?: workspace.store.load(summary.id).project }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@withContext null }
            project.timeline.videoTracks.flatMap { it.clips }.sortedBy { it.timelineStartUs }
                .firstNotNullOfOrNull { clip -> project.media(clip.mediaId)?.takeIf { it.kind != MediaKind.AUDIO && isLocalCutUri(it.uri) }?.uri }
        }
    }
    Card(onClick = onOpen, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Icon(Icons.Default.Movie, null, Modifier.align(Alignment.Center).size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f))
            if (cover != null) AsyncImage(model = ImageRequest.Builder(context).data(cover).crossfade(true).build(),
                imageLoader = imageLoader, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .65f)))))
            val seconds = (summary.durationUs / 1_000_000).coerceAtLeast(0)
            Text(String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60), color = Color.White,
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp))
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(summary.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (projectLabel.isNotBlank()) Text(projectLabel, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${summary.clips} clips · ${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(summary.modifiedAtMs))}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            summary.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}
