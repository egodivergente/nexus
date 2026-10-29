package com.vidadomu.nexus.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
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
import com.vidadomu.nexus.ui.theme.stylusHover
import com.vidadomu.nexus.ui.theme.rememberNexusHaptics
import com.vidadomu.nexus.ui.theme.NexusHapticEvent

/** One input surface per declared field; sources live in a sheet, never as duplicate rows of buttons. */
@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
internal fun CanvasAttachmentEditor(
    field: CanvasInputField, media: List<AttachedMedia>, enabled: Boolean,
    onAdd: (List<Pair<Uri, String>>) -> Unit, onRemove: (String) -> Unit, compact: Boolean = false,
    onReorder: ((String, Int) -> Unit)? = null, frameColumn: Boolean = false
) {
    val context = LocalContext.current
    val selected = media.filter(field::contains)
    val remaining = minOf(CanvasInputPlan.PICKER_BATCH, field.remaining(media))
    var sourcesOpen by remember(field.key) { mutableStateOf(false) }
    var allReferencesVisible by remember(field.key) {mutableStateOf(false)}
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
        sourcesOpen = false
        val type = when(field.mime) {
            "image/*" -> ActivityResultContracts.PickVisualMedia.ImageOnly
            "video/*" -> ActivityResultContracts.PickVisualMedia.VideoOnly
            else -> ActivityResultContracts.PickVisualMedia.SingleMimeType(field.mime)
        }
        if (!field.mime.startsWith("image/") && !field.mime.startsWith("video/")) files.launch(arrayOf(field.mime))
        else if (remaining > 1) multiple.launch(PickVisualMediaRequest(type))
        else single.launch(PickVisualMediaRequest(type))
    }
    val haptics = rememberNexusHaptics()
    val canImport = enabled && remaining > 0
    val colors = MaterialTheme.colorScheme
    val icon = when {
        field.mime.startsWith("video/") -> Icons.Default.VideoLibrary
        field.mime.startsWith("audio/") -> Icons.Default.AudioFile
        else -> Icons.Default.AddPhotoAlternate
    }
    val countLabel = when {
        field.maximum == 1 -> if(field.required) "Obligatorio" else "Opcional"
        field.maximum == Int.MAX_VALUE -> "${selected.size} seleccionados" + if(field.required) " · obligatorio" else ""
        else -> "${selected.size} / ${field.maximum}" + if(field.required) " · obligatorio" else ""
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        if(selected.isEmpty() && !field.required && !frameColumn) {
            Surface(onClick={sourcesOpen=true},enabled=canImport,shape=NexusTokens.field,
                color=colors.surfaceContainerLow,border=androidx.compose.foundation.BorderStroke(NexusTokens.divider,colors.outlineVariant),
                modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).stylusHover(canImport)) {
                Row(Modifier.padding(horizontal=NexusTokens.space12,vertical=NexusTokens.space8),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                    Icon(icon,null,Modifier.size(NexusTokens.iconDefault),tint=colors.onSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text(field.label,style=MaterialTheme.typography.labelLarge)
                        Text(if(field.maximum==1) "Opcional" else "Opcional · $countLabel",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight,null,Modifier.size(NexusTokens.iconMedium),tint=colors.onSurfaceVariant)
                }
            }
        } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            Column(Modifier.weight(1f)) {
                Text(field.label, style = MaterialTheme.typography.labelLarge)
                Text(countLabel, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            if(selected.isNotEmpty()) IconButton(onClick={sourcesOpen=true}, enabled=canImport) {
                Icon(if(field.maximum==1) Icons.Default.SwapHoriz else Icons.Default.Add,
                    if(field.maximum==1) "Cambiar ${field.label}" else "Añadir a ${field.label}")
            }
        }
        if(selected.isEmpty()) {
            Surface(onClick={sourcesOpen=true}, enabled=canImport, shape=NexusTokens.field,
                color=colors.surfaceContainerLow, border=androidx.compose.foundation.BorderStroke(NexusTokens.divider,colors.outlineVariant),
                modifier=Modifier.fillMaxWidth().heightIn(min=if(frameColumn) 80.dp else 72.dp).stylusHover(canImport)) {
                Column(Modifier.padding(NexusTokens.space12),horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(NexusTokens.space4,Alignment.CenterVertically)) {
                    Icon(icon,null,Modifier.size(NexusTokens.iconDefault),tint=colors.primary)
                    Text("Añadir ${if(field.mime.startsWith("video/")) "vídeo" else if(field.mime.startsWith("audio/")) "audio" else "imagen"}",
                        style=MaterialTheme.typography.labelLarge,color=colors.primary)
                }
            }
        } else if(frameColumn && selected.size==1) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                CanvasAttachmentTile(selected.single(),field.label,enabled,thumbnailSize=maxWidth.coerceAtMost(168.dp),showLabel=false) {
                    onRemove(selected.single().id);haptics.perform(NexusHapticEvent.SELECTION)
                }
            }
        } else if(selected.size>1) {
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8),
                verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                val shown=if(allReferencesVisible) selected else selected.take(4)
                shown.forEachIndexed {index,item -> key(item.id) {
                    CanvasAttachmentTile(item,"${field.label} ${index+1}",enabled,thumbnailSize=88.dp,showLabel=false,position=index+1,
                        onMoveBefore=onReorder?.takeIf {index>0}?.let {move -> {move(item.id,-1)}},
                        onMoveAfter=onReorder?.takeIf {index<selected.lastIndex}?.let {move -> {move(item.id,1)}}) {
                        onRemove(item.id);haptics.perform(NexusHapticEvent.SELECTION)
                    }
                }}
            }
            if(selected.size>4) TextButton(onClick={allReferencesVisible=!allReferencesVisible}) {
                Text(if(allReferencesVisible) "Mostrar menos" else "Ver los ${selected.size} archivos")
            }
        } else selected.singleOrNull()?.let {item ->
            CanvasAttachmentTile(item,field.label,enabled,thumbnailSize=104.dp) {
                onRemove(item.id);haptics.perform(NexusHapticEvent.SELECTION)
            }
        }
        }
        if(selected.size>field.maximum) Text("Este borrador conserva ${selected.size} archivos; el modelo admite ${field.maximum}. Revisa la selección antes de generar.",
            style=MaterialTheme.typography.bodySmall,color=colors.error)
    }
    if(sourcesOpen) ModalBottomSheet(onDismissRequest={sourcesOpen=false},shape=NexusTokens.sheet,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        com.vidadomu.nexus.ui.theme.NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().padding(horizontal=NexusTokens.screenMargin).padding(bottom=NexusTokens.space24),
            verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(field.label,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                IconButton(onClick={sourcesOpen=false}) {Icon(Icons.Default.Close,"Cerrar fuentes de archivos")}
            }
            ListItem(headlineContent={Text(if(field.mime.startsWith("audio/")) "Archivos del dispositivo" else "Galería del dispositivo")},
                supportingContent={Text(if(field.maximum==1) "Elegir un archivo" else "Seleccionar varios archivos a la vez")},
                leadingContent={Icon(icon,null)},modifier=Modifier.clickable(enabled=canImport,role=androidx.compose.ui.semantics.Role.Button,onClick=pickGallery))
            if(field.mime.startsWith("image/") || field.mime.startsWith("video/")) ListItem(
                headlineContent={Text("Archivos")},supportingContent={Text("Explorar otras fuentes de Android")},
                leadingContent={Icon(Icons.Default.FolderOpen,null)},modifier=Modifier.clickable(enabled=canImport,role=androidx.compose.ui.semantics.Role.Button) {
                    sourcesOpen=false;files.launch(arrayOf(field.mime))
                })
            ListItem(headlineContent={Text("Biblioteca de Nexus")},supportingContent={Text("Elegir archivos de tus proyectos")},
                leadingContent={Icon(Icons.Default.PhotoLibrary,null)},modifier=Modifier.clickable(enabled=canImport,role=androidx.compose.ui.semantics.Role.Button) {
                    sourcesOpen=false;library.launch(arrayOf(field.mime),remaining)
                })
        }
    }
}

@Composable
internal fun CanvasAttachmentTile(media: AttachedMedia, label: String, enabled: Boolean = true, thumbnailSize:Dp=112.dp, showLabel: Boolean = true,
    position:Int?=null,onMoveBefore:(()->Unit)?=null,onMoveAfter:(()->Unit)?=null,onRemove: () -> Unit) {
    val context = LocalContext.current
    var preview by remember(media.id) { mutableStateOf(false) }
    val visual = media.mimeType.startsWith("image/") || media.mimeType.startsWith("video/")
    var imageFailed by remember(media.uriString) { mutableStateOf(false) }
    val imageModel = remember(media.uriString, media.mimeType) {
        if (media.mimeType.startsWith("video/")) coil.request.ImageRequest.Builder(context).data(Uri.parse(media.uriString))
            .decoderFactory { result, options, _ -> coil.decode.VideoFrameDecoder(result.source, options) }.build()
        else Uri.parse(media.uriString)
    }
    Column(Modifier.width(thumbnailSize), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
        Box(Modifier.size(thumbnailSize).clip(NexusTokens.field).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = visual || onMoveBefore!=null || onMoveAfter!=null) { preview = true }.semantics { contentDescription = "Revisar $label" }) {
            if (visual) AsyncImage(imageModel, label, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                onError = { imageFailed = true }, onSuccess = { imageFailed = false })
            else Column(Modifier.align(Alignment.Center).padding(NexusTokens.space8),horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(Icons.Default.GraphicEq,null)
                media.fileName?.let {Text(it,style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}
            }
            if (imageFailed) Text("No se pudo abrir", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.Center).padding(NexusTokens.space8))
            position?.let {number -> Surface(Modifier.align(Alignment.BottomStart).padding(NexusTokens.space4),
                shape=NexusTokens.pill,color=MaterialTheme.colorScheme.surface.copy(alpha=.9f)) {
                Text(number.toString(),Modifier.padding(horizontal=NexusTokens.space8,vertical=NexusTokens.space4),style=MaterialTheme.typography.labelSmall)
            }}
            if (enabled) StudioRemoveMediaButton(onRemove, Modifier.align(Alignment.TopEnd))
        }
        if(showLabel && thumbnailSize>=72.dp) Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
    }
    if (preview) AlertDialog(onDismissRequest = { preview = false }, title = { Text(label) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            if(visual) AsyncImage(imageModel, label, Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 380.dp), contentScale = ContentScale.Fit)
            else Icon(Icons.Default.GraphicEq,null,Modifier.size(NexusTokens.minimumTouch),tint=MaterialTheme.colorScheme.primary)
            media.fileName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("${media.mimeType} · preparado en el dispositivo", style = MaterialTheme.typography.bodySmall)
            if(onMoveBefore!=null || onMoveAfter!=null) {
                Text("Orden de las referencias",style=MaterialTheme.typography.labelLarge)
                onMoveBefore?.let {move -> TextButton(onClick={move();preview=false},enabled=enabled) {Text("Mover referencia antes")} }
                onMoveAfter?.let {move -> TextButton(onClick={move();preview=false},enabled=enabled) {Text("Mover referencia después")} }
            }
        } }, confirmButton = { TextButton(onClick = { preview = false }) { Text("Cerrar") } })
}
