package com.vidadomu.nexus.ui.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.compose.AsyncImage
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.assistant.AssistantMediaPreview
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.ui.theme.*
import kotlinx.coroutines.*
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LocalAssetTile(asset:Asset,loader:ImageLoader,showDetails:Boolean,mark:Boolean?,onPreview:()->Unit,onActions:()->Unit) {
    val haptic=LocalHapticFeedback.current
    val visual=asset.type in setOf(AssetType.IMAGE,AssetType.VIDEO)
    Surface(shape=NexusTokens.thumbnail,modifier=Modifier.aspectRatio(1f).combinedClickable(
        role=androidx.compose.ui.semantics.Role.Button,onClickLabel="Previsualizar ${asset.displayName}",onClick=onPreview,
        onLongClickLabel="Opciones de ${asset.displayName}",onLongClick={haptic.performHapticFeedback(HapticFeedbackType.LongPress);onActions()})) {
        Box(contentAlignment=Alignment.Center) {
            if(visual) AsyncImage(asset.uri,asset.displayName,imageLoader=loader,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            else Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(8.dp).padding(bottom=if(showDetails) 24.dp else 0.dp)) {
                Icon(if(asset.type==AssetType.AUDIO) Icons.Default.GraphicEq else Icons.Default.Description,null)
                Text(asset.displayName,style=MaterialTheme.typography.labelMedium,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            // Opciones al mantener pulsado: sin ⋮ encima de la miniatura. ▶ y duración arriba; metadatos abajo.
            if(mark!=null) TileMark(mark,Modifier.align(Alignment.TopStart))
            if(asset.type in setOf(AssetType.VIDEO,AssetType.AUDIO))
                TileMediaBadge(asset.type==AssetType.VIDEO,mediaDuration(asset.durationSec?.toFloat()),Modifier.align(Alignment.TopEnd))
            if(showDetails) {
                val known=asset.width?.takeIf {it>0}!=null && asset.height?.takeIf {it>0}!=null
                val facts=rememberTileFacts(asset.uri,asset.mimeOrDefault(),dimensions=visual && !known)
                TileDetailsCaption(
                    quality=if(visual) tileQuality(asset.type==AssetType.IMAGE,asset.width?.takeIf {it>0} ?: facts?.width,asset.height?.takeIf {it>0} ?: facts?.height) else null,
                    bytes=facts?.bytes,epochMillis=asset.createdAt,modifier=Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

private fun Asset.mimeOrDefault():String = mimeType?.takeIf {it.isNotBlank()} ?: when(type) {
    AssetType.IMAGE->"image/*";AssetType.VIDEO->"video/*";AssetType.AUDIO->"audio/*";else->"application/octet-stream"
}

/** «1080p · 12,4 MB · 16 oct · 07:14 · video/mp4» para la hoja de vista previa. */
@Composable
private fun LocalAssetFacts(asset:Asset) {
    val visual=asset.type in setOf(AssetType.IMAGE,AssetType.VIDEO)
    val facts=rememberTileFacts(asset.uri,asset.mimeOrDefault(),dimensions=visual)
    val line=listOfNotNull(
        if(visual) tileQuality(asset.type==AssetType.IMAGE,asset.width?.takeIf {it>0} ?: facts?.width,asset.height?.takeIf {it>0} ?: facts?.height) else null,
        facts?.bytes?.let(::tileSize),
        mediaDuration(asset.durationSec?.toFloat() ?: facts?.durationSeconds),
        tileDateTime(asset.createdAt),
        asset.mimeType,
    ).joinToString(" · ")
    Text(line,Modifier.fillMaxWidth(),style=MaterialTheme.typography.labelMedium.tabularMoney(),color=TextSecondary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalAssetPreviewSheet(asset:Asset,onDismiss:()->Unit,onMoreOptions:()->Unit) {
    val context=LocalContext.current
    val mime=asset.mimeType ?: "application/octet-stream"
    ModalBottomSheet(onDismissRequest=onDismiss,shape=NexusTokens.sheet) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=NexusTokens.screenMargin)
            .padding(bottom=NexusTokens.space24),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12),
            horizontalAlignment=Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxWidth()) {
                Text(asset.displayName,Modifier.fillMaxWidth(),style=MaterialTheme.typography.titleMedium,maxLines=2,
                    overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                LocalAssetFacts(asset)
            }
            if(asset.type in setOf(AssetType.IMAGE,AssetType.VIDEO,AssetType.AUDIO)) {
                AssistantMediaPreview(Attachment(asset.id,asset.uri,mime,asset.displayName,assetId=asset.id))
            } else {
                Surface(Modifier.fillMaxWidth(),shape=NexusTokens.field,color=MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(NexusTokens.space16),horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                        Icon(Icons.Default.Description,null,Modifier.size(NexusTokens.space32),tint=MaterialTheme.colorScheme.primary)
                        Text(mime,style=MaterialTheme.typography.labelMedium,color=TextSecondary)
                        Text("Abre este documento con una aplicación compatible para ver su contenido.",
                            style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                OutlinedButton(onClick=onMoreOptions,modifier=Modifier.weight(1f)) {Text("Más opciones")}
                Button(onClick={
                    if(!openLocalAssetExternally(context,asset))
                        android.widget.Toast.makeText(context,"No hay una aplicación disponible para abrir este archivo",android.widget.Toast.LENGTH_SHORT).show()
                },modifier=Modifier.weight(1f)) {Text("Abrir archivo")}
            }
        }
    }
}

private fun openLocalAssetExternally(context:android.content.Context,asset:Asset):Boolean = runCatching {
    val raw=Uri.parse(asset.uri)
    val uri=if(raw.scheme=="file") FileProvider.getUriForFile(context,context.packageName+".fileprovider",File(raw.path!!)) else raw
    val intent=Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri,asset.mimeType ?: "application/octet-stream")
        clipData=android.content.ClipData.newRawUri(asset.displayName,uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent,"Abrir archivo"))
}.isSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalAssetActions(asset:Asset,onDismiss:()->Unit,onMessage:(String)->Unit) {
    val context=LocalContext.current
    val app=context.applicationContext as NexusApplication
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(asset.mimeType ?: "application/octet-stream")) {destination ->
        if(destination!=null) {busy=true;scope.launch {
            try {withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(Uri.parse(asset.uri))!!.use {input ->
                    context.contentResolver.openOutputStream(destination,"w")!!.use {output -> input.copyTo(output)}
                }
            };onMessage("Archivo guardado");onDismiss()}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                runCatching {android.provider.DocumentsContract.deleteDocument(context.contentResolver,destination)}
                onMessage("No se pudo guardar el archivo")
            }
            finally {busy=false}
        }}
    }
    fun open(share:Boolean) {
        runCatching {
            val raw=Uri.parse(asset.uri)
            val uri=if(raw.scheme=="file") FileProvider.getUriForFile(context,context.packageName+".fileprovider",File(raw.path!!)) else raw
            val intent=Intent(if(share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
                if(share) {type=asset.mimeType;putExtra(Intent.EXTRA_STREAM,uri)} else setDataAndType(uri,asset.mimeType ?: "application/octet-stream")
                clipData=android.content.ClipData.newRawUri(asset.displayName,uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent,if(share) "Compartir" else "Abrir archivo"))
        }.onFailure {onMessage("No hay una aplicación disponible para abrir este archivo")}
    }
    ModalBottomSheet(onDismissRequest={if(!busy) onDismiss()},shape=NexusTokens.sheet) {
        NexusSheetSystemBars()
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(asset.displayName,style=MaterialTheme.typography.titleMedium)
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ListItem(headlineContent={Text("Abrir")},leadingContent={Icon(Icons.Default.OpenInNew,null)},modifier=Modifier.clickable(enabled=!busy) {open(false)})
            ListItem(headlineContent={Text("Descargar archivo")},leadingContent={Icon(Icons.Default.Download,null)},modifier=Modifier.clickable(enabled=!busy) {save.launch(asset.displayName)})
            ListItem(headlineContent={Text("Compartir")},leadingContent={Icon(Icons.Default.Share,null)},modifier=Modifier.clickable(enabled=!busy) {open(true)})
            ListItem(headlineContent={Text("Eliminar de la biblioteca")},supportingContent={Text("Conserva los originales y las referencias anteriores")},leadingContent={Icon(Icons.Default.DeleteOutline,null)},modifier=Modifier.clickable(enabled=!busy) {
                scope.launch {AssistantRuntime.get(app).assets.deleteLibraryAssets(setOf(asset.id));onMessage("Eliminado de la biblioteca");onDismiss()}
            })
        }
    }
}
