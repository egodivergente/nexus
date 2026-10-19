package com.vidadomu.nexus.ui.library

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.assistant.AgentMediaStore
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.core.network.OkHttpClientFactory
import com.vidadomu.nexus.provider.flow.FlowBridgeClient
import com.vidadomu.nexus.ui.theme.NexusSheetSystemBars
import com.vidadomu.nexus.ui.theme.NexusTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/**
 * Trae a la Biblioteca lo que ya está generado en un proyecto de Google Flow, a través del puente del ASUS.
 * Solo descarga lo existente (no genera, no mejora la resolución, no gasta créditos) y recuerda lo ya
 * importado para no duplicarlo.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun FlowImportSheet(onDismiss:()->Unit,onDone:(String)->Unit) {
    val context=LocalContext.current
    val app=context.applicationContext as NexusApplication
    val scope=rememberCoroutineScope()
    val bridge=remember(app) {FlowBridgeClient(OkHttpClientFactory.create(app.secureStorage),app.secureStorage)}
    val prefs=remember {context.getSharedPreferences(FLOW_IMPORT_PREFS,0)}
    var imported by remember {mutableStateOf(prefs.getStringSet("keys",emptySet())!!.toSet())}
    var projects by remember {mutableStateOf<List<FlowBridgeClient.FlowProject>?>(null)}
    var project by remember {mutableStateOf<FlowBridgeClient.FlowProject?>(null)}
    var media by remember {mutableStateOf<List<FlowBridgeClient.FlowProjectMedia>?>(null)}
    var busy by remember {mutableStateOf(true)}
    var progress by remember {mutableStateOf<Pair<Int,Int>?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    // Tamaño de descarga: «Rápido» = lo que muestra Flow; 1K/2K/4K usan el menú «Descargar» de Flow (2K/4K mejoran resolución).
    var size by remember {mutableStateOf(prefs.getString("size",null))}
    LaunchedEffect(bridge) {
        bridge.projects().onSuccess {projects=it}.onFailure {error=it.message ?: "No se pudo leer la galería de Flow."}
        busy=false
    }
    fun open(target:FlowBridgeClient.FlowProject) {
        project=target;media=null;error=null;busy=true
        scope.launch {
            bridge.projectMedia(target.id).onSuccess {media=it}.onFailure {error=it.message ?: "No se pudo leer el proyecto."}
            busy=false
        }
    }
    fun importNew(target:FlowBridgeClient.FlowProject,items:List<FlowBridgeClient.FlowProjectMedia>) {
        val pending=items.filter {"${target.id}/${it.key}" !in imported}
        if(pending.isEmpty()) return
        busy=true;error=null
        scope.launch {
            val staging=File(context.cacheDir,"flow-import").apply {mkdirs()}
            val projectId=AssistantRuntime.get(app).settings.activeProjectId()
            var done=0;var lastError:String?=null;var upscaleFallbacks=0
            try {
                for((index,item) in pending.withIndex()) {
                    progress=index to pending.size
                    val wanted=size?.let {flowSizeFor(it,item.kind)}
                    var result=bridge.projectMediaFile(target.id,item,staging,wanted)
                    // Si Flow no consigue mejorar la resolución, se trae el original en vez de dejar el archivo fuera.
                    if(wanted!=null && wanted!="1K" && wanted!="720p" && result.isFailure) {
                        upscaleFallbacks++
                        result=bridge.projectMediaFile(target.id,item,staging,if(item.kind=="video") "720p" else "1K")
                    }
                    val file=result.getOrElse {lastError=it.message;null} ?: continue
                    try {
                        AgentMediaStore.persist(app,Uri.fromFile(file.file),file.mimeType,projectId)
                        imported=imported+"${target.id}/${item.key}"
                        prefs.edit().putStringSet("keys",imported).apply()
                        done++
                    } catch(e:CancellationException) {throw e}
                      catch(e:Exception) {lastError=e.message}
                      finally {file.file.delete()}
                }
            } finally {busy=false;progress=null}
            val summary="Importados de Flow: $done de ${pending.size}"+
                (if(upscaleFallbacks>0) ". $upscaleFallbacks en tamaño original (Flow no pudo mejorarlos)" else "")+
                (lastError?.takeIf {done<pending.size}?.let {". $it"} ?: "")
            if(done==pending.size) onDone(summary) else error=summary
        }
    }
    ModalBottomSheet(onDismissRequest={if(!busy || progress==null) onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(horizontal=NexusTokens.screenMargin)) {
            Text(project?.let {it.title.ifBlank {"Proyecto sin título"}} ?: "Importar de Flow",style=MaterialTheme.typography.titleLarge,
                maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("Trae lo que ya generaste en Flow. No genera nada ni gasta créditos.",style=MaterialTheme.typography.bodyMedium)
            if(busy) {
                val (current,total)=progress ?: (0 to 0)
                if(total>0) LinearProgressIndicator(progress={current/total.toFloat()},modifier=Modifier.fillMaxWidth().padding(vertical=NexusTokens.space8))
                else LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=NexusTokens.space8))
                Text(if(total>0) "Importando ${current+1} de $total…" else if(project==null) "Leyendo tus proyectos de Flow…" else "Recorriendo el proyecto en el ASUS…",
                    style=MaterialTheme.typography.labelMedium)
            }
            error?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(vertical=NexusTokens.space8))}
            val current=project
            if(current==null) {
                val list=projects.orEmpty()
                if(!busy && projects!=null && list.isEmpty()) Text("No hay proyectos en tu Flow.",Modifier.padding(vertical=NexusTokens.space24))
                LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=NexusTokens.space8)) {
                    items(list,key={it.id}) {item ->
                        ListItem(headlineContent={Text(item.title.ifBlank {"Proyecto sin título"},maxLines=2,overflow=TextOverflow.Ellipsis)},
                            supportingContent={Text(item.id.take(8),style=MaterialTheme.typography.labelSmall)},
                            modifier=Modifier.clickable(enabled=!busy) {open(item)})
                    }
                }
            } else {
                media?.let {items ->
                    val fresh=items.count {"${current.id}/${it.key}" !in imported}
                    val videos=items.count {it.kind=="video"}
                    Text(when {
                        items.isEmpty() -> "Este proyecto no tiene imágenes ni vídeos."
                        fresh==0 -> "Ya tienes en la Biblioteca los ${items.size} archivos de este proyecto."
                        else -> "${items.size} archivos (${items.size-videos} imágenes, $videos vídeos). Nuevos: $fresh."
                    },style=MaterialTheme.typography.bodyLarge,modifier=Modifier.padding(vertical=NexusTokens.space16))
                    Text("Tamaño",style=MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical=NexusTokens.space8)) {
                        val options=listOf(null to "Rápido","1K" to "1K","2K" to "2K","4K" to "4K")
                        options.forEachIndexed {index,(value,label) ->
                            SegmentedButton(selected=size==value,onClick={size=value;prefs.edit().putString("size",value).apply()},
                                shape=SegmentedButtonDefaults.itemShape(index,options.size),enabled=!busy) {Text(label)}
                        }
                    }
                    Text(when(size) {
                        null -> "Rápido: la versión que muestra Flow."
                        "1K" -> "Original de Flow (vídeo: 720p)."
                        else -> "Flow mejora la resolución a $size (vídeo: ${flowSizeFor(size!!,"video")}); tarda más por archivo."
                    },style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(bottom=NexusTokens.space8))
                    Button(onClick={importNew(current,items)},enabled=!busy && fresh>0,modifier=Modifier.fillMaxWidth()) {
                        Text(if(fresh>0) "Importar $fresh" else "Nada nuevo que importar")
                    }
                }
                TextButton(onClick={project=null;media=null;error=null},enabled=!busy) {Text("Elegir otro proyecto")}
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

private const val FLOW_IMPORT_PREFS="flow_import_v1"

/** Equivalencia de tamaños para vídeo en el menú «Descargar» de Flow. */
internal fun flowSizeFor(size:String,kind:String):String =
    if(kind!="video") size else when(size) {"1K"->"720p";"2K"->"1080p";else->"4K"}
