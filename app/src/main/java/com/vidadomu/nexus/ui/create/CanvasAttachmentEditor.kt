package com.vidadomu.nexus.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.vidadomu.nexus.assistant.projectMediaMimeType
import com.vidadomu.nexus.assistant.rememberProjectMediaPicker
import com.vidadomu.nexus.core.model.AttachedMedia
import com.vidadomu.nexus.ui.components.StudioRemoveMediaButton
import com.vidadomu.nexus.ui.theme.NexusTokens

@Composable
internal fun CanvasAttachmentEditor(
    field: CanvasInputField, media: List<AttachedMedia>, enabled: Boolean,
    onAdd: (List<Pair<Uri, String>>) -> Unit, onRemove: (String) -> Unit, compact: Boolean = false,
    onReorder: ((String, Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val selected = media.filter(field::contains)
    val remaining = minOf(CanvasInputPlan.PICKER_BATCH, field.remaining(media))
    val deliver by rememberUpdatedState(onAdd)
    val accept: (List<Uri>) -> Unit = { uris ->
        deliver(uris.map { it to (projectMediaMimeType(context, it) ?: "application/octet-stream") })
    }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) accept(listOf(uri))
    }
    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(remaining.coerceAtLeast(2))) { uris -> accept(uris) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> accept(uris) }
    val library = rememberProjectMediaPicker { uris -> accept(uris) }
    val pickGallery: () -> Unit = {
        val type = when(field.mime) {
            "image/*" -> ActivityResultContracts.PickVisualMedia.ImageOnly
            "video/*" -> ActivityResultContracts.PickVisualMedia.VideoOnly
            else -> ActivityResultContracts.PickVisualMedia.SingleMimeType(field.mime)
        }
        if (!field.mime.startsWith("image/") && !field.mime.startsWith("video/")) files.launch(arrayOf(field.mime))
        else if (remaining > 1) multiple.launch(PickVisualMediaRequest(type))
        else single.launch(PickVisualMediaRequest(type))
    }
    Surface(Modifier.fillMaxWidth(), shape = NexusTokens.card, color = if(compact) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(if(compact) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(field.label, style = if(compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall)
                    Text(if (field.maximum == 1) {
                        if (field.required) "Un archivo · obligatorio" else "Un archivo · opcional"
                    } else "${selected.size} seleccionados" +
                        (if (field.maximum < Int.MAX_VALUE) " de ${field.maximum}" else "") +
                        if (field.required) " · obligatorio" else " · opcional",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selected.isNotEmpty()) Icon(Icons.Default.CheckCircle, "${selected.size} archivos preparados", tint = MaterialTheme.colorScheme.primary)
            }
            if (selected.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(selected, key = { _, item -> item.id }) { index, item ->
                    Column {
                        CanvasAttachmentTile(item, "${field.label} ${index + 1}", enabled) { onRemove(item.id) }
                        if(selected.size > 1 && onReorder != null) Row {
                            IconButton(onClick={onReorder(item.id,-1)},enabled=enabled && index>0) {Icon(Icons.Default.ChevronLeft,"Mover referencia antes")}
                            IconButton(onClick={onReorder(item.id,1)},enabled=enabled && index<selected.lastIndex) {Icon(Icons.Default.ChevronRight,"Mover referencia después")}
                        }
                    }
                }
            }
            if (remaining > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = pickGallery, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (field.mime.startsWith("audio/")) "Archivos" else if (selected.isNotEmpty() && field.maximum == 1) "Cambiar" else "Galería")
                }
                OutlinedButton(onClick = { library.launch(arrayOf(field.mime), remaining) }, enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Biblioteca") }
            }
            if (selected.isNotEmpty() && !compact) Text("Preparado en el dispositivo · toca la miniatura para revisar",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CanvasAttachmentTile(media: AttachedMedia, label: String, enabled: Boolean = true, thumbnailSize:Dp=112.dp, onRemove: () -> Unit) {
    val context = LocalContext.current
    var preview by remember(media.id) { mutableStateOf(false) }
    val visual = media.mimeType.startsWith("image/") || media.mimeType.startsWith("video/")
    var imageFailed by remember(media.uriString) { mutableStateOf(false) }
    val imageModel = remember(media.uriString, media.mimeType) {
        if (media.mimeType.startsWith("video/")) coil.request.ImageRequest.Builder(context).data(Uri.parse(media.uriString))
            .decoderFactory { result, options, _ -> coil.decode.VideoFrameDecoder(result.source, options) }.build()
        else Uri.parse(media.uriString)
    }
    Column(Modifier.width(thumbnailSize), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(thumbnailSize).clip(NexusTokens.thumbnail).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = visual) { preview = true }.semantics { contentDescription = "Revisar $label" }) {
            if (visual) AsyncImage(imageModel, label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                onError = { imageFailed = true }, onSuccess = { imageFailed = false })
            else Icon(Icons.Default.GraphicEq, label, Modifier.align(Alignment.Center))
            if (imageFailed) Text("No se pudo abrir", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.Center).padding(8.dp))
            if (enabled) StudioRemoveMediaButton(onRemove, Modifier.align(Alignment.TopEnd))
        }
        if(thumbnailSize>=72.dp) Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
    }
    if (preview) AlertDialog(onDismissRequest = { preview = false }, title = { Text(label) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AsyncImage(imageModel, label, Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 380.dp), contentScale = ContentScale.Fit)
            media.fileName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("${media.mimeType} · preparado en el dispositivo", style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(onClick = { preview = false }) { Text("Cerrar") } })
}
