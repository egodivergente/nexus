package com.vidadomu.nexus.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.ui.theme.NexusTokens

/** The agent's existing showMedia channel stays inline. Only a user tap opens the foreground viewer. */
@Composable
internal fun AssistantImagePreview(media: Attachment) {
    var opened by rememberSaveable(media.id, media.uri) { mutableStateOf(false) }
    Column {
        AsyncImage(model = media.uri, contentDescription = "Ampliar ${media.name ?: "imagen del agente"}", contentScale = ContentScale.Fit,
            modifier = Modifier.width(280.dp).height(220.dp).clip(NexusTokens.card).clickable { opened = true })
        com.vidadomu.nexus.ui.components.NexusTonalAction("Ampliar", { opened = true }, icon = Icons.Default.ZoomIn, description = "Ampliar imagen")
    }
    if (opened) AssistantImageForeground(media) { opened = false }
}

internal fun boundedImageScale(scale: Float): Float = if (scale.isFinite()) scale.coerceIn(1f, 5f) else 1f

@Composable
private fun AssistantImageForeground(media: Attachment, onClose: () -> Unit) {
    var scale by remember(media.uri) { mutableFloatStateOf(1f) }
    var offset by remember(media.uri) { mutableStateOf(Offset.Zero) }
    var imageState by remember(media.uri) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = NexusTokens.space16), verticalAlignment = Alignment.CenterVertically) {
                    Text(media.name ?: "Imagen del agente", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar imagen ampliada") }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
                    val density = LocalDensity.current
                    val widthPx = with(density) { maxWidth.toPx() }
                    val heightPx = with(density) { maxHeight.toPx() }
                    val transform = rememberTransformableState { zoom, pan, _ ->
                        scale = boundedImageScale(scale * zoom)
                        val proposed = offset + pan
                        val maxX = widthPx * (scale - 1f) / 2f
                        val maxY = heightPx * (scale - 1f) / 2f
                        offset = Offset(proposed.x.coerceIn(-maxX, maxX), proposed.y.coerceIn(-maxY, maxY))
                    }
                    AsyncImage(model = media.uri, contentDescription = media.name ?: "Imagen ampliada", contentScale = ContentScale.Fit,
                        onState = { imageState = it },
                        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                            .transformable(transform).pointerInput(media.uri) {
                                detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2f; offset = Offset.Zero })
                            })
                    when (imageState) {
                        is AsyncImagePainter.State.Loading -> Text("Cargando imagen…", style = MaterialTheme.typography.bodyMedium)
                        is AsyncImagePainter.State.Error -> Text("No se pudo cargar la imagen. Cierra el visor y comprueba que el archivo sigue disponible.",
                            modifier = Modifier.padding(NexusTokens.space24), color = MaterialTheme.colorScheme.error)
                        else -> Unit
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.space16), verticalAlignment = Alignment.CenterVertically) {
                    Text("Pellizca o toca dos veces para ampliar", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { scale = 1f; offset = Offset.Zero }) { Text("Ajustar") }
                }
            }
        }
    }
}
