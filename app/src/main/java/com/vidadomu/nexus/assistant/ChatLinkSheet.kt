package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.ui.theme.NexusTokens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.NexusApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** User-triggered link actions, shared by Markdown links and real exported documents. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ChatLinkSheet(source:String,name:String?=null,onDismiss:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val clipboard=LocalClipboardManager.current
    var busy by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    val uri=remember(source) {Uri.parse(source)}
    val fileName=(name ?: uri.lastPathSegment?.substringBefore('?')).orEmpty().replace(Regex("[\\\\/:*?\"<>|]"),"_").take(140).ifBlank {"archivo"}
    fun localUri():Uri {
        require(uri.scheme in setOf("file","content")) {"Este enlace no apunta a un archivo descargable."}
        if(uri.scheme=="file") {
            val file=File(requireNotNull(uri.path)).canonicalFile
            val appRoot=context.applicationInfo.dataDir.let {File(it).canonicalPath}+File.separator
            val allowed=listOf("agent_media","agent_exports","assistant/sync/workspaces").map {File(context.filesDir,it).canonicalPath+File.separator} + (context.cacheDir.canonicalPath+File.separator)
            require(!file.path.startsWith(appRoot) || allowed.any(file.path::startsWith)) {"El enlace no corresponde a un archivo compartible."}
            require(file.isFile) {"El archivo del enlace ya no está disponible."}
            return Uri.fromFile(file)
        }
        return uri
    }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) {destination ->
        if(destination!=null) scope.launch {
            busy=true
            message=withContext(Dispatchers.IO) {runCatching {
                context.contentResolver.openOutputStream(destination)?.use {output ->
                    if(uri.scheme in setOf("http","https")) {
                        ChatLinkDownload.client.newCall(Request.Builder().url(source).build()).execute().use {response ->
                            check(response.isSuccessful) {"El servidor devolvió HTTP ${response.code}."}
                            requireNotNull(response.body) {"El servidor no devolvió un archivo."}.byteStream().use {it.copyTo(output)}
                        }
                    } else context.contentResolver.openInputStream(localUri())?.use {it.copyTo(output)} ?: error("No se pudo leer el archivo.")
                } ?: error("No se pudo escribir en el destino.")
                "Archivo guardado"
            }.getOrElse {"No se pudo guardar: ${it.message}"}}
            busy=false
        }
    }
    ModalBottomSheet(onDismissRequest={if(!busy) onDismiss()}) {
        Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            Text(name ?: "Enlace",style=MaterialTheme.typography.titleLarge)
            Text(if(uri.scheme in setOf("http","https")) uri.host.orEmpty()+uri.path.orEmpty() else fileName,style=MaterialTheme.typography.bodySmall,maxLines=3)
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(enabled=!busy,onClick={save.launch(fileName)},modifier=Modifier.fillMaxWidth()) {Text(if(busy) "Guardando…" else "Guardar archivo")}
            TextButton(enabled=!busy,onClick={runCatching {
                val target=if(uri.scheme in setOf("http","https")) uri else {
                    val local=localUri()
                    if(local.scheme=="file") androidx.core.content.FileProvider.getUriForFile(context,context.packageName+".fileprovider",File(requireNotNull(local.path))) else local
                }
                context.startActivity(Intent(Intent.ACTION_VIEW,target).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }.onFailure {message="No se pudo abrir el enlace. Puedes guardar una copia o copiar su dirección."}}) {Text("Abrir")}
            TextButton(onClick={clipboard.setText(AnnotatedString(source));message="Enlace copiado"}) {Text("Copiar enlace")}
            message?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
        }
    }
}

private object ChatLinkDownload {
    val client=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).build()
}

@Composable internal fun AssistantDocumentPreview(media:com.vidadomu.nexus.agent.core.tools.Attachment) {
    val context=LocalContext.current
    var source by remember(media.uri) {mutableStateOf<String?>(null)}
    var failure by remember(media.uri) {mutableStateOf<String?>(null)}
    var actions by remember {mutableStateOf(false)}
    LaunchedEffect(media.uri) {
        if(media.mimeType=="text/html") withContext(Dispatchers.IO) {runCatching {
            val uri=Uri.parse(media.uri)
            require(uri.scheme in setOf("file","content")) {"Guarda el archivo para abrir la vista previa."}
            source=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {reader ->
                val buffer=CharArray(1_000_001);var used=0
                while(used<buffer.size) {val n=reader.read(buffer,used,buffer.size-used);if(n<0) break;used+=n}
                require(used<=1_000_000) {"Abre o guarda este archivo para verlo completo."};String(buffer,0,used)
            } ?: error("No se pudo abrir el documento.")
        }.onFailure {failure=it.message}}
    }
    Column(Modifier.fillMaxWidth()) {
        source?.let {ChatVisualArtifact("html",it,media.name ?: "Interactivo")}
        if(source==null) Text(media.name ?: "Archivo del agente",style=MaterialTheme.typography.bodyMedium)
        failure?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
        TextButton(onClick={actions=true}) {Text("Abrir o guardar archivo")}
    }
    if(actions) ChatLinkSheet(media.uri,media.name) {actions=false}
}
