package com.vidadomu.nexus.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.ui.components.NexusSheet
import com.vidadomu.nexus.ui.theme.NexusTokens

/** Stable asset IDs keep equal URLs and multi-output jobs individually addressable. */
@Composable
internal fun ResultsWallSheet(outputs: List<Attachment>, onOpen: (Attachment) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    NexusSheet("Muro de resultados", onDismiss, subtitle = "${outputs.size} archivos") {
        LazyVerticalStaggeredGrid(columns=StaggeredGridCells.Adaptive(150.dp),
            contentPadding=PaddingValues(NexusTokens.space12),
            verticalItemSpacing=NexusTokens.space8,
            horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8), modifier=Modifier.weight(1f)) {
            items(outputs, key={it.id}) { media ->
                val video=media.mimeType.startsWith("video/")
                val request=remember(media.uri,video) { ImageRequest.Builder(context).data(media.uri).apply {
                    if(video) decoderFactory { result, options, _ -> VideoFrameDecoder(result.source,options) }
                }.build() }
                Box(Modifier.fillMaxWidth().clip(NexusTokens.field).clickable {onOpen(media)}) {
                    SubcomposeAsyncImage(model=request, contentDescription=media.name ?: "Resultado generado",
                        contentScale=ContentScale.FillWidth, modifier=Modifier.fillMaxWidth(),
                        loading={ Box(Modifier.fillMaxWidth().height(120.dp),contentAlignment=Alignment.Center) {CircularProgressIndicator()} },
                        error={ Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text("No se pudo cargar. Toca para abrir el archivo.",modifier=Modifier.padding(NexusTokens.space12))
                        } })
                    if(video) Icon(Icons.Default.PlayCircle,"Vídeo",tint=androidx.compose.ui.graphics.Color.White,
                        modifier=Modifier.align(Alignment.Center).size(36.dp))
                }
            }
        }
    }
}

@Composable
internal fun ResultMediaSheet(media: Attachment, onDismiss: () -> Unit) {
    NexusSheet(media.name ?: "Resultado",onDismiss) {
        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) { AssistantMediaPreview(media) }
    }
}
