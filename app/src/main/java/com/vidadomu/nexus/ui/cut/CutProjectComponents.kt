package com.vidadomu.nexus.ui.cut

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vidadomu.nexus.cut.CutHubCover
import com.vidadomu.nexus.cut.CutHubProject
import com.vidadomu.nexus.cut.storage.ProjectStore
import com.vidadomu.nexus.ui.theme.NexusTokens
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun CutHubThumbnail(uri: String?, imageLoader: ImageLoader, modifier: Modifier, fallback: ImageVector = Icons.Default.Movie) {
    val context = LocalContext.current
    Box(modifier.clip(NexusTokens.thumbnail).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(fallback, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f))
        if (uri != null) AsyncImage(model = ImageRequest.Builder(context).data(uri).crossfade(true).build(), imageLoader = imageLoader,
            contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
internal fun CutProjectRow(project: CutHubProject, imageLoader: ImageLoader, onOpen: () -> Unit, onRename: () -> Unit, onArchive: () -> Unit, enabled: Boolean) {
    Surface(shape = NexusTokens.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onOpen).padding(start = NexusTokens.space12, top = NexusTokens.space12, bottom = NexusTokens.space12),
            horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12), verticalAlignment = Alignment.CenterVertically) {
            CutHubThumbnail(project.coverUri, imageLoader, Modifier.size(80.dp), if (project.archived) Icons.Default.Archive else Icons.Default.FolderOpen)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${cutHubCount(project.montageCount, "montaje", "montajes")} · ${cutHubCount(project.materialCount, "archivo", "archivos")}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (project.archived) "Archivado" else "Actividad ${cutHubDate(project.activityAt)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CutProjectMenu(project, enabled, onRename, onArchive)
        }
    }
}

@Composable
internal fun CutProjectMenu(project: CutHubProject, enabled: Boolean, onRename: () -> Unit, onArchive: () -> Unit) {
    var menu by remember(project.id) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }, enabled = enabled) { Icon(Icons.Default.MoreVert, "Opciones de ${project.name}") }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("Renombrar proyecto") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text(if (project.archived) "Restaurar proyecto" else "Archivar proyecto") },
                leadingIcon = { Icon(if (project.archived) Icons.Default.Unarchive else Icons.Default.Archive, null) }, onClick = { menu = false; onArchive() })
        }
    }
}

@Composable
internal fun CutProjectShortcut(title: String, count: String, icon: ImageVector, modifier: Modifier, onOpen: () -> Unit, enabled: Boolean = true) {
    Surface(shape = NexusTokens.card, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onOpen).padding(horizontal = NexusTokens.space8, vertical = NexusTokens.space12),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
            Icon(icon, null, Modifier.size(NexusTokens.iconDefault), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(count, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CutHubMontageRow(summary: ProjectStore.Summary, cover: CutHubCover?, imageLoader: ImageLoader, enabled: Boolean = true, onOpen: () -> Unit) {
    Surface(shape = NexusTokens.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onOpen).padding(NexusTokens.space12),
            horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12), verticalAlignment = Alignment.CenterVertically) {
            Box {
                CutHubThumbnail(cover?.uri, imageLoader, Modifier.width(92.dp).height(64.dp))
                Surface(shape = NexusTokens.thumbnail, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(NexusTokens.space4)) {
                    Text(cutHubDuration(summary.durationUs), modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                Text(summary.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${cutHubCount(summary.clips, "clip", "clips")} · ${cutHubDate(summary.modifiedAtMs)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                summary.error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            Icon(if (enabled) Icons.Default.ChevronRight else Icons.Default.Lock, if (enabled) null else "Proyecto archivado", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CutHubEmpty(title: String, text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = NexusTokens.space24), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

internal fun cutHubCount(count: Int, one: String, many: String): String = "$count ${if (count == 1) one else many}"
internal fun cutHubDate(at: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))
internal fun cutHubDuration(us: Long): String = (us / 1_000_000).coerceAtLeast(0).let { String.format(Locale.ROOT, "%d:%02d", it / 60, it % 60) }
