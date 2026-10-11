package com.vidadomu.nexus.assistant

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.NexusSheetSystemBars
import kotlinx.coroutines.*
import java.io.File

/** Local edits have their own list; they never masquerade as paid generation jobs. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun LocalMediaEditsSheet(onDismiss:()->Unit) {
    val context=LocalContext.current
    val app=context.applicationContext as NexusApplication
    val scope=rememberCoroutineScope()
    val store=remember(app) {MediaEditJobs(app)}
    val runtime=remember(app) {AssistantRuntime.get(app)}
    val index by runtime.assets.changes.collectAsState()
    var jobs by remember {mutableStateOf(emptyList<MediaEditJob>())}
    var loading by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf<String?>(null)}
    var pending by remember {mutableStateOf<Asset?>(null)}
    var saving by remember {mutableStateOf(false)}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) {uri ->
        val asset=pending;pending=null
        if(uri!=null && asset!=null) scope.launch {
            saving=true
            try {
                withContext(Dispatchers.IO) {
                    val file=localEditFile(app,asset)
                    val output=context.contentResolver.openOutputStream(uri,"wt") ?: throw IllegalStateException("Destino no disponible")
                    output.use {out ->file.inputStream().use {it.copyTo(out)}}
                }
            } catch(cancelled:CancellationException) {throw cancelled}
              catch(e:Exception) {error="No se pudo guardar la copia. El resultado local se conserva."}
            finally {saving=false}
        }
    }
    LaunchedEffect(app) {
        app.ready.await()
        while(isActive) {jobs=withContext(Dispatchers.IO) {store.list()};loading=false;delay(1500)}
    }
    fun open(asset:Asset,share:Boolean) {
        try {
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",localEditFile(app,asset))
            val intent=Intent(if(share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
                if(share) {type=asset.mimeType ?: "application/octet-stream";putExtra(Intent.EXTRA_STREAM,uri)}
                else setDataAndType(uri,asset.mimeType ?: "application/octet-stream")
                clipData=android.content.ClipData.newRawUri(asset.displayName,uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(if(share) Intent.createChooser(intent,"Compartir resultado") else intent)
        } catch(e:Exception) {error="No se pudo abrir. Puedes guardar una copia y elegir otra aplicación."}
    }
    ModalBottomSheet(onDismissRequest={if(!saving) onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(horizontal=NexusTokens.screenMargin)) {
            Text("Ediciones locales",style=MaterialTheme.typography.titleLarge)
            Text("Resultados del agente. Los originales se conservan.",style=MaterialTheme.typography.bodyMedium)
            if(loading || saving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=NexusTokens.space8))
            error?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)}
            if(!loading && jobs.isEmpty()) Text("Pide al agente un recorte, una unión o una transcripción.",Modifier.padding(vertical=NexusTokens.space24))
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12),contentPadding=PaddingValues(vertical=NexusTokens.space16)) {
                items(jobs,key={it.id}) {job ->
                    Card(Modifier.fillMaxWidth(),shape=NexusTokens.card) {
                        Column(Modifier.padding(NexusTokens.space16),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                            Text(mediaEditTitle(job.spec.action),style=MaterialTheme.typography.titleMedium)
                            Text(job.spec.assetIds.mapNotNull {id ->index.assets.firstOrNull {it.id==id}?.displayName}.joinToString(" · "),
                                style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(when(job.state) {
                                "COMPLETED"->"Completado";"FAILED"->"No completado";"PAUSED"->"Interrumpido";"PROCESSING"->"En curso · ${job.progress}%";else->"En cola"
                            },style=MaterialTheme.typography.labelMedium)
                            if(job.state=="PROCESSING") LinearProgressIndicator(progress={job.progress/100f},modifier=Modifier.fillMaxWidth())
                            job.error?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)}
                            job.outputAssetIds.mapNotNull {id ->index.assets.firstOrNull {it.id==id}}.forEach {asset ->
                                Text(asset.displayName,style=MaterialTheme.typography.bodyMedium)
                                FlowRow {
                                    TextButton(onClick={open(asset,false)},enabled=!saving) {Text("Abrir")}
                                    TextButton(onClick={open(asset,true)},enabled=!saving) {Text("Compartir")}
                                    TextButton(onClick={pending=asset;save.launch(asset.displayName)},enabled=!saving) {Text("Guardar")}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun localEditFile(app:NexusApplication,asset:Asset):File {
    val uri=Uri.parse(asset.uri)
    require(uri.scheme=="file")
    val file=File(requireNotNull(uri.path)).canonicalFile
    require(file.isFile && file.path.startsWith(File(app.filesDir,"agent_exports").canonicalPath+File.separator))
    return file
}

internal fun mediaEditTitle(action:String)=when(action) {
    "trim"->"Recorte";"concat"->"Unión de clips";"remove_audio"->"Vídeo sin audio";"replace_audio"->"Audio sustituido"
    "mix_audio"->"Mezcla de audio";"extract_audio"->"Audio extraído";"frames"->"Fotogramas";"transcribe"->"Transcripción";else->"Edición"
}
