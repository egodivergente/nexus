package com.vidadomu.nexus.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.OutputFiles
import com.vidadomu.nexus.ui.output.NativeAudioPlayer
import com.vidadomu.nexus.ui.output.NativeVideoPlayer
import com.vidadomu.nexus.ui.output.TextOutputViewer
import com.vidadomu.nexus.ui.output.ZoomableImageViewer
import com.vidadomu.nexus.ui.theme.NexusTokens
import kotlinx.coroutines.launch

/** What the viewer shows for an entry: the file to load (local copy first) and what kind it is. */
internal data class ViewerMedia(val uri: String, val remote: String?, val kind: Modality?)

internal fun viewerMedia(entry: LibraryEntry, conflicts: Set<String>): ViewerMedia = when (entry) {
    is LibraryEntry.Local -> ViewerMedia(entry.asset.uri, null, when (entry.asset.type) {
        AssetType.IMAGE -> Modality.IMAGE; AssetType.VIDEO -> Modality.VIDEO; AssetType.AUDIO -> Modality.AUDIO; else -> null
    })
    is LibraryEntry.Out -> entry.job.outputs[entry.index].let { output ->
        val local = OutputFiles.trustedLocalUri(output, conflicts)
        ViewerMedia(local ?: output.url, output.url.takeIf { local != null }, output.type)
    }
}

/**
 * Every Library file opens here, generated or imported: full screen, its name on top and its options under ⋮.
 * Swipe sideways to go through the Library in the same order as the grid.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryViewer(entries: List<LibraryEntry>, start: Int, conflicts: Set<String>, name: (LibraryEntry) -> String,
    onClose: () -> Unit, onOptions: (LibraryEntry) -> Unit, onOpenDocument: (LibraryEntry) -> Unit, overlay: @Composable () -> Unit) {
    val pager = rememberPagerState(start.coerceIn(0, (entries.size - 1).coerceAtLeast(0))) { entries.size }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { entries.getOrNull(it)?.key ?: it }) { page ->
                val entry = entries.getOrNull(page) ?: return@HorizontalPager
                val media = remember(entry, conflicts) { viewerMedia(entry, conflicts) }
                val current = page == pager.currentPage
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (media.kind) {
                        // The image viewer keeps horizontal drags for itself (pan when zoomed), so it asks the pager to turn.
                        Modality.IMAGE -> ZoomableImageViewer(media.remote ?: media.uri, media.uri.takeIf { media.remote != null }, onNavigate = { step ->
                            scope.launch { pager.animateScrollToPage((page + step).coerceIn(0, entries.size - 1)) }
                        })
                        // Only the page on screen plays; the neighbours show their first frame.
                        Modality.VIDEO -> if (current) NativeVideoPlayer(media.uri)
                            else AsyncImage(media.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        Modality.AUDIO -> if (current) NativeAudioPlayer(media.uri)
                        Modality.CHAT -> TextOutputViewer(media.uri)
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                            Icon(Icons.Default.Description, null, Modifier.size(NexusTokens.space32 * 2), tint = Color.White.copy(alpha = .7f))
                            FilledTonalButton(onClick = { onOpenDocument(entry) }) { Text("Abrir con otra aplicación") }
                        }
                    }
                }
            }
            val entry = entries.getOrNull(pager.currentPage)
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .45f)).statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White) }
                Text(entry?.let(name).orEmpty(), Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry != null) IconButton(onClick = { onOptions(entry) }) { Icon(Icons.Default.MoreVert, "Opciones del archivo", tint = Color.White) }
            }
            overlay()
        }
    }
}
