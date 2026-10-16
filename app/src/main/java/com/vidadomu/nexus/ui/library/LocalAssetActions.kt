package com.vidadomu.nexus.ui.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.compose.AsyncImage
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.ui.theme.*
import kotlinx.coroutines.*
import java.io.File

@Composable
internal fun LocalAssetTile(asset:Asset,loader:ImageLoader,onOpen:()->Unit) {
    Surface(shape=NexusTokens.thumbnail,modifier=Modifier.aspectRatio(1f).clickable(onClick=onOpen)) {
        Box(contentAlignment=Alignment.Center) {
            if(asset.type in setOf(AssetType.IMAGE,AssetType.VIDEO)) AsyncImage(asset.uri,asset.displayName,imageLoader=loader,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            else Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(8.dp)) {
                Icon(if(asset.type==AssetType.AUDIO) Icons.Default.GraphicEq else Icons.Default.Description,null)
                Text(asset.displayName,style=MaterialTheme.typography.labelMedium,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            IconButton(onClick=onOpen,modifier=Modifier.align(Alignment.TopEnd)) {Icon(Icons.Default.MoreVert,"Opciones de ${asset.displayName}")}
        }
    }
}

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
