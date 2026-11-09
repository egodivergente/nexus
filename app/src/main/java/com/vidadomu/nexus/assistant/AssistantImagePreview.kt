package com.vidadomu.nexus.assistant

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
    // La caja toma la proporción de la imagen: una foto vertical ya no queda en medio de una caja apaisada con franjas vacías.
    var aspect by remember(media.uri) { mutableStateOf(media.width?.let { w -> media.height?.takeIf { it > 0 }?.let { h -> w.toFloat() / h } }) }
    val size = imagePreviewSize(aspect)
    Column(Modifier.width(size.width)) {
        AsyncImage(model = media.uri, contentDescription = "Ampliar ${media.name ?: "imagen del agente"}", contentScale = ContentScale.Fit,
            onSuccess = { state ->
                val intrinsic = state.painter.intrinsicSize
                if (intrinsic.width.isFinite() && intrinsic.height.isFinite() && intrinsic.width > 0f && intrinsic.height > 0f) aspect = intrinsic.width / intrinsic.height
            },
            modifier = Modifier.size(size.width, size.height).clip(NexusTokens.card).clickable { opened = true })
        media.caption?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
        com.vidadomu.nexus.ui.components.NexusTonalAction("Ampliar", { opened = true }, icon = Icons.Default.ZoomIn, description = "Ampliar imagen")
    }
    if (opened) AssistantImageForeground(media) { opened = false }
}

/**
 * Varias imágenes seguidas del agente se ven juntas, como un collage: 2 en mitades, 3 con una grande y dos pequeñas,
 * 4 en cuadrícula y, a partir de 5, la cuadrícula con «+N» en la última casilla. Un toque abre esa imagen ampliada.
 */
@Composable
internal fun ImageCollage(images: List<Attachment>) {
    var opened by rememberSaveable(images.first().id, images.size) { mutableStateOf<Int?>(null) }
    val gap = 3.dp
    val shown = images.take(4)
    val extra = images.size - shown.size
    @Composable fun Cell(i: Int, modifier: Modifier) {
        Box(modifier.clickable { opened = i }) {
            AsyncImage(model = shown[i].uri, contentDescription = "Ampliar imagen ${i + 1} de ${images.size}", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
            if (i == shown.lastIndex && extra > 0) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
                Text("+$extra", style = MaterialTheme.typography.headlineSmall, color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
    Column(Modifier.fillMaxWidth().widthIn(max = 360.dp).clip(NexusTokens.card), verticalArrangement = Arrangement.spacedBy(gap)) {
        when (shown.size) {
            2 -> Row(Modifier.fillMaxWidth().aspectRatio(2f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Cell(0, Modifier.weight(1f).fillMaxHeight()); Cell(1, Modifier.weight(1f).fillMaxHeight())
            }
            3 -> Row(Modifier.fillMaxWidth().aspectRatio(1.5f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Cell(0, Modifier.weight(2f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    Cell(1, Modifier.weight(1f).fillMaxWidth()); Cell(2, Modifier.weight(1f).fillMaxWidth())
                }
            }
            else -> {
                Row(Modifier.fillMaxWidth().aspectRatio(2f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Cell(0, Modifier.weight(1f).fillMaxHeight()); Cell(1, Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.fillMaxWidth().aspectRatio(2f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Cell(2, Modifier.weight(1f).fillMaxHeight()); Cell(3, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
    opened?.let { index -> AssistantImageForeground(images[index.coerceIn(0, images.lastIndex)]) { opened = null } }
}

/**
 * Tamaño de la vista previa en el chat con la proporción real de la imagen ([aspect] = ancho / alto; null = 4:5 hasta
 * que cargue). Ocupa todo el alto disponible salvo que el ancho la limite (imágenes apaisadas).
 */
internal fun imagePreviewSize(aspect: Float?, maxWidth: Dp = 320.dp, maxHeight: Dp = 360.dp): DpSize {
    val ratio = aspect?.takeIf { it.isFinite() && it > 0f }?.coerceIn(0.4f, 3f) ?: (4f / 5f)
    return if (maxHeight * ratio <= maxWidth) DpSize(maxHeight * ratio, maxHeight) else DpSize(maxWidth, maxWidth / ratio)
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
