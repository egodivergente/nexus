package com.vidadomu.nexus.assistant

import android.webkit.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.rememberNexusMotionAllowed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

@Composable internal fun ChatVisualArtifact(language:String,source:String,title:String=if(language=="mermaid") "Diagrama" else "Interactivo") {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    val rules by com.vidadomu.nexus.settings.RuntimePreferences.get(context).state.collectAsState()
    val motion=rules.value("ui.motion").toBoolean() && android.animation.ValueAnimator.areAnimatorsEnabled()
    var code by remember(source) {mutableStateOf(false)}
    var full by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) {uri ->
        if(uri!=null) scope.launch {
            message=withContext(Dispatchers.IO) {runCatching {
                val html=if(language=="mermaid") ChatVisualDocument.build(language,source,dark,motion,context.assets.open("renderer/mermaid.min.js").bufferedReader().use {it.readText()}) else source
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {it.write(html)} ?: error("No se pudo abrir el destino")
                "Archivo guardado"
            }.getOrElse {"No se pudo guardar: ${it.message}"}}
        }
    }
    Surface(Modifier.fillMaxWidth(),shape=NexusTokens.card,color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start=12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                IconButton(onClick={code=!code}) {Icon(Icons.Default.Code,if(code) "Ver contenido renderizado" else "Ver código")}
                IconButton(onClick={save.launch(if(language=="mermaid") "diagrama.html" else "interactivo.html")}) {Icon(Icons.Default.Download,"Guardar contenido")}
                IconButton(onClick={full=true}) {Icon(Icons.Default.Fullscreen,"Ampliar contenido")}
            }
            if(code) androidx.compose.foundation.text.selection.SelectionContainer {Text(source,Modifier.fillMaxWidth().heightIn(max=360.dp).verticalScroll(rememberScrollState()).padding(12.dp),fontFamily=androidx.compose.ui.text.font.FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}
            else ChatVisualWebView(language,source,dark,motion,Modifier.fillMaxWidth().height(if(language=="mermaid") 200.dp else 360.dp))
            message?.let {Text(it,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)}
        }
    }
    if(full) Dialog(onDismissRequest={full=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TextButton(onClick={full=false}) {Icon(Icons.Default.Close,null);Text("Cerrar contenido ampliado")}
            ChatVisualWebView(language,source,dark,motion,Modifier.fillMaxWidth().weight(1f))
        }}
    }
}

@Composable private fun ChatVisualWebView(language:String,source:String,dark:Boolean,motion:Boolean,modifier:Modifier) {
    val context=LocalContext.current
    val document=remember(language,source,dark,motion) {ChatVisualDocument.build(language,source,dark,motion)}
    var link by remember {mutableStateOf<String?>(null)}
    var web by remember {mutableStateOf<WebView?>(null)}
    val owner=androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event ->
            when(event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> web?.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> web?.onResume()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer)}
    }

    AndroidView(modifier=modifier.testTag("chat_visual"),factory={
        WebView(it).apply {
            web=this
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled=true
            settings.allowFileAccess=false;settings.allowContentAccess=false
            settings.domStorageEnabled=false;settings.blockNetworkLoads=true
            settings.setSupportZoom(true);settings.builtInZoomControls=true;settings.displayZoomControls=false
            webViewClient=object:WebViewClient() {
                override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse {
                    val uri=request.url
                    return if(uri.scheme=="https" && uri.host=="appassets.androidplatform.net" && uri.path=="/assets/renderer/mermaid.min.js")
                        WebResourceResponse("application/javascript","UTF-8",context.assets.open("renderer/mermaid.min.js"))
                    else WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(byteArrayOf()))
                }
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                    if(request.url.scheme in setOf("http","https") && request.hasGesture()) link=request.url.toString()
                    return request.url.scheme !in setOf("about","data","blob")
                }
            }
        }
    },update={view -> if(view.tag!=document) {view.tag=document;view.loadDataWithBaseURL("https://appassets.androidplatform.net/",document,"text/html","UTF-8",null)}},onRelease={web=null;it.stopLoading();it.destroy()})
    link?.let {ChatLinkSheet(it,onDismiss={link=null})}
}
