package com.vidadomu.nexus.ui.library

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.scrollBy
import com.vidadomu.nexus.ui.models.DragHaptics
import com.vidadomu.nexus.ui.components.NexusTopAppBar as TopAppBar
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.components.StudioFilterChip
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.*
import androidx.compose.ui.graphics.graphicsLayer
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.assistant.LocalMediaEditsSheet
import com.vidadomu.nexus.assistant.rememberProjectMediaPicker
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.asImageBitmap
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.ui.components.matchesCreationSearch
import com.vidadomu.nexus.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/** Un trabajo en curso se considera atascado a partir de 10 minutos. */
internal const val STUCK_AFTER_MS=10*60*1000L

internal fun mediaDuration(seconds:Float?):String? = seconds?.takeIf {it.isFinite() && it>=0}?.toLong()?.let {
    "%d:%02d".format(Locale.ROOT,it/60,it%60)
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class,ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(onNavigateToOutput:(String)->Unit,onNavigateToResult:((String,Int)->Unit)?=null,
    onUseInCanvas:((com.vidadomu.nexus.agent.core.assets.Asset)->Unit)?=null,
    onSendToAgent:((com.vidadomu.nexus.agent.core.assets.Asset)->Unit)?=null,
    onSendResultToAgent:((String,String)->Unit)?=null, onOpenHistory:(()->Unit)?=null, onOpenArtBox:(()->Unit)?=null,
    onEditPhoto:((String)->Unit)?=null) {
    val context=LocalContext.current
    val app=context.applicationContext as NexusApplication
    val jobs by app.generationRepository.allJobs.collectAsState()
    var importing by remember {mutableStateOf(false)}
    val prefs=remember {context.getSharedPreferences("library_actions_v1",0)}
    var hidden by remember {mutableStateOf(prefs.getStringSet("hidden",emptySet())!!.toSet())}
    var favorites by remember {mutableStateOf(prefs.getStringSet("favorites",emptySet())!!.toSet())}
    var showHidden by rememberSaveable {mutableStateOf(false)}
    var favoriteOnly by rememberSaveable {mutableStateOf(false)}
    // Resolución, tamaño y fecha al pie de cada miniatura; se puede ocultar desde ⋮.
    var showDetails by remember {mutableStateOf(prefs.getBoolean("tile_details",true))}
    // Nombres que el usuario da a lo generado; lo importado guarda el suyo en su propio registro.
    var names by remember {mutableStateOf(prefs.all.mapNotNull {(k,v) -> if(k.startsWith("name:") && v is String) k.removePrefix("name:") to v else null}.toMap())}
    // Todo archivo se abre igual: visor a pantalla completa (por clave de la entrada) con sus opciones en ⋮.
    var viewerAt by remember {mutableStateOf<String?>(null)}
    // Carpeta (proyecto) abierta; null = vista principal. Lo importado aquí entra en ella.
    var openFolder by rememberSaveable {mutableStateOf<String?>(null)}
    var optionsFor by remember {mutableStateOf<LibraryEntry?>(null)}
    var renaming by remember {mutableStateOf<LibraryEntry?>(null)}
    // Marcado rápido: mantener pulsado marca y, sin soltar, arrastrar marca el tramo; con algo marcado, tocar suma o quita.
    var marked by remember {mutableStateOf(emptySet<String>())}
    val selecting=marked.isNotEmpty()
    fun toggleMark(token:String) {marked=if(token in marked) marked-token else marked+token}
    BackHandler(enabled=selecting) {marked=emptySet()}
    // Atrás dentro de una carpeta vuelve a la vista principal (la selección, si la hay, se cierra antes).
    BackHandler(enabled=openFolder!=null && !selecting) {openFolder=null}
    val scope=rememberCoroutineScope()
    val snackbar=remember {SnackbarHostState()}
    val haptic=com.vidadomu.nexus.ui.theme.rememberNexusHaptics()
    val runtime=remember(app) {AssistantRuntime.get(app)}
    var showLocalEdits by rememberSaveable {mutableStateOf(false)}
    if(showLocalEdits) LocalMediaEditsSheet {showLocalEdits=false}
    var showFlowImport by rememberSaveable {mutableStateOf(false)}
    if(showFlowImport) FlowImportSheet(onDismiss={showFlowImport=false}) {summary ->
        showFlowImport=false;scope.launch {snackbar.showSnackbar(summary)}
    }
    val index by runtime.assets.changes.collectAsState()
    val projectLibrary=rememberProjectMediaPicker(managementOnly=true) {}
    LaunchedEffect(jobs) {kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {runtime.importHistory()}}
    val importPicker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) {uris ->
        if(uris.isNotEmpty()) { importing=true; scope.launch {
            var imported=0;var lastError:String?=null
            try { for(uri in uris) {
                try { com.vidadomu.nexus.assistant.AgentMediaStore.persist(app,uri,context.contentResolver.getType(uri),openFolder); imported++ }
                catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(e:Exception) {lastError=e.message}
            }; snackbar.showSnackbar("Importados $imported de ${uris.size} archivos"+(lastError?.takeIf {imported<uris.size}?.let {". $it"} ?: "")) }
            finally {importing=false}
        } }
    }
    val removed=remember(index) {index.assets.filter {it.libraryDeleted}.map {it.uri}.toSet()}
    var query by rememberSaveable {mutableStateOf("")}
    var searchVisible by rememberSaveable {mutableStateOf(false)}
    var moreVisible by remember {mutableStateOf(false)}
    var filter by rememberSaveable {mutableStateOf<Modality?>(null)}
    var originFilter by rememberSaveable {mutableStateOf(LibraryOriginFilter.ALL)}
    var originMenu by remember {mutableStateOf(false)}
    val projectIndex=remember(index) {LibraryProjectIndex(index)}
    // Cada proyecto es una carpeta. La vista principal muestra las carpetas y solo lo que no tiene proyecto;
    // dentro de una carpeta, solo lo suyo. Buscar desde la vista principal busca en todo.
    val folderIds=remember(index) {index.projects.map {it.id}.toSet()}
    fun inScope(projects:Set<String>):Boolean {
        val known=projects.filter {it in folderIds}
        return openFolder?.let {it in known} ?: (query.isNotBlank() || known.isEmpty())
    }
    // Zoom con dos dedos como Google Fotos: 2–6 columnas, recordado entre sesiones.
    // Sin preferencia guardada, las columnas se adaptan al ancho (miniaturas de ~120 dp); el zoom las fija luego.
    val widthDp=androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    var columns by remember(widthDp) {mutableStateOf(prefs.getInt("grid_cols",(widthDp/120).coerceIn(3,6)).coerceIn(2,6))}
    val viewFilters=LibraryViewFilters(query,filter,originFilter,favoriteOnly,showHidden,emptySet())
    val focusManager=androidx.compose.ui.platform.LocalFocusManager.current
    fun applyFilters(next:LibraryViewFilters) {
        if(!next.hasQuery) focusManager.clearFocus()
        query=next.query;filter=next.modality;originFilter=next.origin;favoriteOnly=next.favoriteOnly;showHidden=next.showHidden
    }
    // Editado/Compartido solo se ofrecen si hay algo de ese origen; Nexus e Importado, siempre.
    val origins=remember(index) {
        val present=index.assets.filter {!it.libraryDeleted}.map(::libraryOrigin).toSet()
        LibraryOriginFilter.values().filter {it in setOf(LibraryOriginFilter.ALL,LibraryOriginFilter.GENERATED,LibraryOriginFilter.IMPORTED) || it in present}
    }
    val completed=remember(jobs,filter,query,removed,hidden,favorites,favoriteOnly,showHidden,originFilter,openFolder,folderIds,projectIndex,names) {
        if(originFilter !in setOf(LibraryOriginFilter.ALL,LibraryOriginFilter.GENERATED)) emptyList()
        else jobs.map {job -> job.copy(outputs=job.outputs.filter {inScope(projectIndex.projects(job,it)) && it.localUri !in removed && it.url !in removed && (OutputFiles.token(it) in hidden)==showHidden && (!favoriteOnly || OutputFiles.token(it) in favorites)})}.filter {it.status==GenerationStatus.COMPLETED && it.outputs.isNotEmpty()}
            .filter {filter==null || it.modality==filter}
            .filter {job -> matchesCreationSearch(job,query) || job.outputs.any {names[OutputFiles.token(it)]?.contains(query,true)==true}}.sortedByDescending {it.startedAt}
    }
    val pending=remember(jobs,filter,query,showHidden,favoriteOnly,originFilter,openFolder,projectIndex) {if(showHidden || favoriteOnly || originFilter !in setOf(LibraryOriginFilter.ALL,LibraryOriginFilter.GENERATED)) emptyList() else jobs.filter {
        (openFolder==null || openFolder in projectIndex.projects(it)) &&
        it.status !in setOf(GenerationStatus.COMPLETED,GenerationStatus.CANCELLED,GenerationStatus.FAILED) &&
        (filter==null || it.modality==filter) && matchesCreationSearch(it,query)
    }.sortedByDescending {it.startedAt}}
    val localAssets=remember(index,filter,query,hidden,showHidden,favorites,favoriteOnly,jobs,originFilter,openFolder,folderIds) {
        val generatedUris=jobs.flatMap {it.outputs}.flatMap {listOfNotNull(it.url,it.localUri)}.toSet()
        index.assets.filter { a -> !a.libraryDeleted && a.uri !in generatedUris && inScope(setOfNotNull(a.projectId)) &&
            a.matchesLibraryOrigin(originFilter) &&
            (filter==null || a.type.name==filter?.name) && a.displayName.contains(query,true) &&
            ("asset:"+a.id in hidden)==showHidden && (!favoriteOnly || "asset:"+a.id in favorites) }.sortedByDescending {it.createdAt}
    }
    val localAssetGroups=remember(localAssets) {localAssets.groupBy(::libraryOrigin)}
    // Archivos por carpeta (sin ocultos ni eliminados), para el recuento bajo cada carpeta.
    val folderCounts=remember(jobs,index,removed,hidden,projectIndex) {
        val counts=mutableMapOf<String,Int>()
        val generatedUris=jobs.flatMap {it.outputs}.flatMap {listOfNotNull(it.url,it.localUri)}.toSet()
        jobs.filter {it.status==GenerationStatus.COMPLETED}.forEach {job -> job.outputs.forEach {o ->
            if(o.localUri !in removed && o.url !in removed && OutputFiles.token(o) !in hidden)
                projectIndex.projects(job,o).forEach {counts[it]=(counts[it] ?: 0)+1}
        }}
        index.assets.filter {!it.libraryDeleted && it.uri !in generatedUris && "asset:"+it.id !in hidden}
            .forEach {a -> a.projectId?.let {counts[it]=(counts[it] ?: 0)+1}}
        counts.toMap()
    }
    // Los proyectos archivados solo aparecen como carpeta si aún tienen archivos.
    val folders=remember(index,folderCounts) {index.projects.filter {!it.archived || (folderCounts[it.id] ?: 0)>0}.sortedBy {it.name.lowercase()}}
    // Hasta tres archivos locales (los más recientes) por carpeta, para la vista previa de la tarjeta.
    val folderPreviews=remember(index) {
        index.assets.filter {!it.libraryDeleted && it.projectId!=null && !it.uri.startsWith("http") && (it.type==AssetType.IMAGE || it.type==AssetType.VIDEO)}
            .sortedByDescending {it.createdAt}.groupBy {it.projectId!!}
            .mapValues {(_,list) -> list.take(3).map {it.uri to (it.type==AssetType.VIDEO)}}
    }
    val showFolders=openFolder==null && query.isBlank() && !showHidden && !favoriteOnly && folders.isNotEmpty()
    val folderName=openFolder?.let {id -> index.projects.firstOrNull {it.id==id}?.name}
    // Si la carpeta abierta desaparece (borrada desde el Agente, p. ej.), se vuelve a la vista principal.
    if(openFolder!=null && folderName==null) LaunchedEffect(openFolder) {openFolder=null}
    var moving by remember {mutableStateOf<List<LibraryEntry>?>(null)}
    var newFolderFor by remember {mutableStateOf<List<LibraryEntry>?>(null)}
    var creatingFolder by remember {mutableStateOf(false)}
    var deletingFolder by remember {mutableStateOf<String?>(null)}
    var bulkRename by remember {mutableStateOf<List<LibraryEntry>?>(null)}
    var cropTarget by remember {mutableStateOf<LibraryEntry?>(null)}
    var cropping by remember {mutableStateOf(false)}
    val conflicts=remember(jobs) {OutputFiles.conflictingLocalUris(jobs.flatMap {it.outputs})}
    val timeline=remember(completed,localAssets) {
        val day=SimpleDateFormat("d MMMM yyyy",Locale("es","ES"))
        (completed.flatMap {job -> job.outputs.indices.map {LibraryEntry.Out(job,it)}}+localAssets.map {LibraryEntry.Local(it)})
            .sortedByDescending {it.time}.groupBy {day.format(Date(it.time))}
    }
    // Sin archivos locales que no se pueden abrir: no ocupan hueco ni dejan una baldosa vacía.
    val unavailableCount=LibraryUnavailable.count()
    val shownTimeline=remember(timeline,conflicts,unavailableCount) {
        timeline.mapValues {(_,entries) -> entries.filterNot {entry ->
            LibraryUnavailable.isUnavailable(when(entry) {
                is LibraryEntry.Local -> entry.asset.uri
                is LibraryEntry.Out -> OutputFiles.trustedLocalUri(entry.job.outputs[entry.index],conflicts)
            })
        }}.filterValues {it.isNotEmpty()}
    }
    // Mismo archivo exacto apareciendo más de una vez en la lista visible (p. ej. importado y luego generado otra vez).
    val duplicateUris=remember(completed,localAssets,conflicts) {
        (completed.flatMap {job -> job.outputs.map {OutputFiles.trustedLocalUri(it,conflicts) ?: it.url}}+localAssets.map {it.uri})
            .groupingBy {it}.eachCount().filterValues {it>1}.keys
    }
    // Solo cuenta y actúa sobre lo marcado que sigue visible con los filtros actuales.
    val visibleTokens=remember(completed,localAssets) {
        completed.flatMap {job -> job.outputs.map {OutputFiles.token(it)}}.toSet()+localAssets.map {"asset:"+it.id}.toSet()
    }
    val markedVisible=marked.intersect(visibleTokens)
    var exportItems by remember {mutableStateOf<List<ExportItem>>(emptyList())}
    var exportSkipped by remember {mutableStateOf(0)}
    val exportLauncher=androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")) {destination ->
        if(destination!=null) scope.launch {
            val written=try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(destination,"w")!!.use {writeZip(context,it,exportItems)}
                }
            } catch(e:kotlinx.coroutines.CancellationException) {throw e}
            catch(e:Exception) {
                runCatching {android.provider.DocumentsContract.deleteDocument(context.contentResolver,destination)}
                snackbar.showSnackbar("No se pudo crear el ZIP");return@launch
            }
            marked=emptySet()
            snackbar.showSnackbar(when {
                written==0 -> "No se pudo leer ningún archivo marcado"
                written<exportItems.size+exportSkipped -> "ZIP guardado con $written de ${exportItems.size+exportSkipped} archivos (sin copia local no se pueden incluir)"
                else -> "ZIP guardado con $written ${if(written==1) "archivo" else "archivos"}"
            })
        }
    }
    fun startExport() {
        val items=buildList {
            completed.forEach {job -> job.outputs.forEachIndexed {i,o ->
                if(OutputFiles.token(o) in markedVisible) OutputFiles.trustedLocalUri(o,conflicts)?.let {uri ->
                    val kind=o.type.name.lowercase()
                    val base=job.modelId.substringAfterLast('/')+"_"+job.id.takeLast(6)+if(job.outputs.size>1) "_${i+1}" else ""
                    add(ExportItem(uri,base+"."+exportExtension(o.mimeType,uri,kind)))
                }
            }}
            localAssets.forEach {a -> if("asset:"+a.id in markedVisible)
                add(ExportItem(a.uri,if('.' in a.displayName.takeLast(6)) a.displayName else a.displayName+"."+exportExtension(a.mimeType,a.uri,a.type.name.lowercase())))}
        }
        exportSkipped=markedVisible.size-items.size
        if(items.isEmpty()) {scope.launch {snackbar.showSnackbar("Lo marcado no tiene copia local que exportar")};return}
        exportItems=items
        exportLauncher.launch("Nexus-"+SimpleDateFormat("yyyyMMdd-HHmm",Locale.ROOT).format(Date())+".zip")
    }
    // El cargador propio de la Biblioteca dejaba las miniaturas en blanco (cola de decodificación limitada y cierre al
    // recomponer): se usa uno normal, con descodificación de fotogramas para vídeo, que Coil gestiona por sí solo.
    val imageLoader=remember(context) {ImageLoader.Builder(context.applicationContext).components {add(VideoFrameDecoder.Factory())}
        .memoryCache {coil.memory.MemoryCache.Builder(context).maxSizePercent(.15).build()}.build()}
    val flat=remember(timeline) {timeline.values.flatten()}
    fun tokenOf(entry:LibraryEntry):String=when(entry) {
        is LibraryEntry.Local -> "asset:"+entry.asset.id
        is LibraryEntry.Out -> OutputFiles.token(entry.job.outputs[entry.index])
    }
    fun assetOf(entry:LibraryEntry)=when(entry) {
        is LibraryEntry.Local -> entry.asset
        is LibraryEntry.Out -> entry.job.outputs[entry.index].let {o -> index.assets.firstOrNull {!it.libraryDeleted && (it.uri==o.localUri || it.uri==o.url)}}
    }
    fun nameOf(entry:LibraryEntry):String=when(entry) {
        is LibraryEntry.Local -> entry.asset.displayName
        is LibraryEntry.Out -> names[tokenOf(entry)] ?: listOf(
            app.modelCatalogRepository.getModelById(entry.job.modelId)?.displayName ?: entry.job.modelId.substringAfterLast('/'),
            entry.job.prompt.lineSequence().firstOrNull()?.trim()?.take(80).orEmpty()).filter {it.isNotBlank()}.joinToString(" · ")
    }
    fun message(text:String) {scope.launch {snackbar.showSnackbar(text)}}
    fun rename(entry:LibraryEntry,name:String) {
        if(entry is LibraryEntry.Out) {
            val token=tokenOf(entry);names=names+(token to name);prefs.edit().putString("name:"+token,name).apply()
        }
        assetOf(entry)?.let {asset -> scope.launch {
            runCatching {runtime.assets.updateAsset(asset.id) {it.copy(displayName=name)}}.onFailure {message("No se pudo cambiar el nombre")}
        }}
    }
    fun openResult(entry:LibraryEntry.Out) {
        // Keep the viewer in this exact visible collection; folder/filter UI is unchanged.
        com.vidadomu.nexus.ui.output.OutputViewerScope.prepare(entry.job.id,flat.filterIsInstance<LibraryEntry.Out>().map {it.job.id to it.index})
        val output=entry.job.outputs[entry.index]
        val position=jobs.firstOrNull {it.id==entry.job.id}?.outputs?.indexOfFirst {it.id==output.id} ?: -1
        if(onNavigateToResult!=null && position>=0) onNavigateToResult(entry.job.id,position) else onNavigateToOutput(entry.job.id)
    }
    /** Mete los archivos en la carpeta del proyecto [projectId] (null = vista principal). Solo cambia el registro; el archivo no se toca. */
    fun moveTo(entries:List<LibraryEntry>,projectId:String?) {
        val target=projectId?.let {id -> index.projects.firstOrNull {it.id==id}?.name} ?: "la Biblioteca"
        scope.launch {
            entries.forEach {entry -> when(entry) {
                is LibraryEntry.Local -> runtime.assets.updateAsset(entry.asset.id) {it.copy(projectId=projectId)}
                is LibraryEntry.Out -> {
                    val o=entry.job.outputs[entry.index]
                    val existing=index.assets.filter {it.uri==o.localUri || it.uri==o.url}
                    if(existing.isNotEmpty()) existing.forEach {a -> runtime.assets.updateAsset(a.id) {it.copy(projectId=projectId)}}
                    // Un resultado aún sin registro en la Biblioteca se registra para poder llevarlo a la carpeta.
                    else runtime.assets.addAsset(com.vidadomu.nexus.agent.core.assets.Asset(id=UUID.randomUUID().toString(),uri=o.localUri ?: o.url,
                        type=when(o.type) {Modality.IMAGE->com.vidadomu.nexus.agent.core.assets.AssetType.IMAGE;Modality.VIDEO->com.vidadomu.nexus.agent.core.assets.AssetType.VIDEO
                            Modality.AUDIO->com.vidadomu.nexus.agent.core.assets.AssetType.AUDIO;else->com.vidadomu.nexus.agent.core.assets.AssetType.OTHER},
                        source=com.vidadomu.nexus.agent.core.assets.AssetSource.GENERATED,displayName=nameOf(entry),mimeType=o.mimeType.ifBlank {null},
                        projectId=projectId,createdAt=entry.job.completedAt ?: entry.job.startedAt))
                }
            }}
            snackbar.showSnackbar(if(entries.size==1) "Movido a $target" else "${entries.size} archivos movidos a $target")
        }
    }
    fun cropAndSave(entry:LibraryEntry,crop:androidx.compose.ui.geometry.Rect) {
        cropping=true
        scope.launch {
            try {
                val file=cropToTempFile(context,viewerMedia(entry,conflicts).uri,crop)
                val original=assetOf(entry)
                val project=when(entry) {is LibraryEntry.Local -> entry.asset.projectId; is LibraryEntry.Out -> projectIndex.projects(entry.job,entry.job.outputs[entry.index]).firstOrNull()}
                val saved=com.vidadomu.nexus.assistant.AgentMediaStore.persist(app,android.net.Uri.fromFile(file),"image/jpeg",project)
                file.delete()
                saved.assetId?.let {id -> runtime.assets.updateAsset(id) {it.copy(displayName=nameOf(entry).substringBeforeLast('.')+" (recorte)",parentAssetId=original?.id)}}
                cropTarget=null
                snackbar.showSnackbar("Recorte guardado como copia; el original no cambia")
            } catch(e:kotlinx.coroutines.CancellationException) {throw e}
            catch(e:Exception) {snackbar.showSnackbar("No se pudo recortar: ${e.message ?: "error desconocido"}")}
            finally {cropping=false}
        }
    }
    fun isImage(entry:LibraryEntry)=viewerMedia(entry,conflicts).kind==Modality.IMAGE
    @Composable fun FileOptions(entry:LibraryEntry) {
        val token=tokenOf(entry)
        val asset=assetOf(entry)
        val visual=viewerMedia(entry,conflicts).kind in setOf(Modality.IMAGE,Modality.VIDEO)
        // Lo que lleva a otra pantalla cierra también el visor.
        fun leaving(action:()->Unit):()->Unit = {viewerAt=null;action()}
        LibraryFileSheet(entry,nameOf(entry),(entry as? LibraryEntry.Out)?.let {OutputFiles.trustedLocalUri(it.job.outputs[it.index],conflicts)},
            token in favorites,token in hidden,onDismiss={optionsFor=null},onRename={optionsFor=null;renaming=entry},
            onSelect={viewerAt=null;marked=marked+token},
            onFavorite={
                favorites=if(token in favorites) favorites-token else favorites+token
                prefs.edit().putStringSet("favorites",favorites).apply()
                haptic.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.SELECTION)
            },onHide={
                val wasHidden=token in hidden
                hidden=if(wasHidden) hidden-token else hidden+token;prefs.edit().putStringSet("hidden",hidden).apply()
                scope.launch {if(snackbar.showSnackbar(if(wasHidden) "Archivo restaurado" else "Archivo ocultado","Deshacer",withDismissAction=true)==SnackbarResult.ActionPerformed) {
                    hidden=if(wasHidden) hidden+token else hidden-token;prefs.edit().putStringSet("hidden",hidden).apply()
                }}
            },onDelete={
                // Close the viewer so the reversible action remains visible above the library.
                viewerAt=null
                val ids=when(entry) {
                    is LibraryEntry.Local -> setOf(entry.asset.id).takeUnless {entry.asset.libraryDeleted}.orEmpty()
                    is LibraryEntry.Out -> entry.job.outputs[entry.index].let {output ->
                        index.assets.filter {!it.libraryDeleted && (it.uri==output.url || it.uri==output.localUri)}.map {it.id}.toSet()
                    }
                }
                val wasHidden=token in hidden
                scope.launch {
                    try {
                        runtime.assets.deleteLibraryAssets(ids)
                        if(entry is LibraryEntry.Out) {hidden=hidden+token;prefs.edit().putStringSet("hidden",hidden).apply()}
                        if(snackbar.showSnackbar("Eliminado de la biblioteca; los originales se conservan","Deshacer",withDismissAction=true)==SnackbarResult.ActionPerformed) {
                            // Restore only the removed flag: names/project edits made meanwhile survive.
                            runtime.assets.restoreLibraryAssets(ids)
                            hidden=if(wasHidden) hidden+token else hidden-token
                            prefs.edit().putStringSet("hidden",hidden).apply()
                        }
                    } catch(e:kotlinx.coroutines.CancellationException) {throw e}
                    catch(e:Exception) {snackbar.showSnackbar("No se pudo completar la acción en la biblioteca")}
                }
            },onMessage=::message,
            onAddToChat=when(entry) {
                is LibraryEntry.Local -> onSendToAgent?.let {send -> leaving {send(entry.asset)}}
                is LibraryEntry.Out -> onSendResultToAgent?.let {send -> leaving {send(entry.job.id,entry.job.outputs[entry.index].id)}}
            },
            onUseInCanvas=if(visual && asset!=null) onUseInCanvas?.let {use -> leaving {use(asset)}} else null,
            onOpenResult=(entry as? LibraryEntry.Out)?.let {out -> leaving {openResult(out)}},
            onCrop=if(isImage(entry)) ({cropTarget=entry}) else null,onMove={moving=listOf(entry)},
            onEdit=if(isImage(entry)) onEditPhoto?.let {edit ->
                val uri=when(entry) {is LibraryEntry.Out -> OutputFiles.trustedLocalUri(entry.job.outputs[entry.index],conflicts); is LibraryEntry.Local -> entry.asset.uri}
                uri?.let {u -> {optionsFor=null;edit(u)}}
            } else null)
    }
    @Composable fun FileDialogs() {
        optionsFor?.let {FileOptions(it)}
        renaming?.let {entry -> RenameFileDialog(nameOf(entry),{renaming=null}) {name -> rename(entry,name);renaming=null}}
        moving?.let {entries -> MoveToFolderSheet(entries.size,folders,openFolder,{moving=null},onNewFolder={newFolderFor=entries;moving=null}) {id ->
            moving=null;marked=emptySet();moveTo(entries,id)
        }}
        newFolderFor?.let {entries -> RenameFileDialog("",{newFolderFor=null},title="Nueva carpeta") {name ->
            newFolderFor=null;marked=emptySet()
            scope.launch {moveTo(entries,runtime.assets.createProject(name).id)}
        }}
        deletingFolder?.let {id -> val folder=folders.firstOrNull {it.id==id}
            if(folder==null) deletingFolder=null else AlertDialog(onDismissRequest={deletingFolder=null},
                title={Text("Eliminar carpeta «${folder.name}»")},
                text={Text("Los archivos no se borran: vuelven a la Biblioteca general.")},
                confirmButton={TextButton(onClick={deletingFolder=null;scope.launch {runtime.assets.archiveProject(id)}}) {Text("Eliminar carpeta")}},
                dismissButton={TextButton(onClick={deletingFolder=null}) {Text("Cancelar")}})
        }
        if(creatingFolder) RenameFileDialog("",{creatingFolder=false},title="Nueva carpeta") {name ->
            creatingFolder=false;scope.launch {runtime.assets.createProject(name)}
        }
        // Varios a la vez: mismo nombre numerado en el orden de la cuadrícula («Playa 1», «Playa 2»…).
        bulkRename?.let {entries -> RenameFileDialog(if(entries.size==1) nameOf(entries[0]) else "",{bulkRename=null}) {name ->
            bulkRename=null;marked=emptySet()
            if(entries.size==1) rename(entries[0],name) else entries.forEachIndexed {i,entry -> rename(entry,"$name ${i+1}")}
        }}
        cropTarget?.let {entry -> ImageCropDialog(viewerMedia(entry,conflicts).uri,cropping,{if(!cropping) cropTarget=null}) {rect -> cropAndSave(entry,rect)}}
    }
    val viewerStart=viewerAt?.let {key -> flat.indexOfFirst {it.key==key}}
    // Si lo abierto deja de estar en la lista (ocultado o eliminado), el visor se cierra.
    if(viewerStart!=null && viewerStart<0) LaunchedEffect(viewerAt) {viewerAt=null}
    if(viewerStart!=null && viewerStart>=0) LibraryViewer(flat,viewerStart,conflicts,::nameOf,onClose={viewerAt=null},
        onOptions={optionsFor=it},onOpenDocument={entry ->
            val mime=when(entry) {is LibraryEntry.Local -> entry.asset.resolvedMime(); is LibraryEntry.Out -> entry.job.outputs[entry.index].mimeType}
            if(!com.vidadomu.nexus.ui.output.openLocalCopyWithAnotherApp(context,viewerMedia(entry,conflicts).uri,mime)) message("No hay una aplicación disponible para este archivo")
        }) {FileDialogs()}
    else FileDialogs()
    // Arrastrar para marcar: el tramo entre el archivo donde empezó la pulsación larga y el que está bajo el dedo.
    // Si el primero ya estaba marcado, el arrastre desmarca (como Google Fotos).
    val gridState=rememberLazyGridState()
    val view=androidx.compose.ui.platform.LocalView.current
    val order by rememberUpdatedState(remember(flat) {flat.map {it.key to tokenOf(it)}})
    val drag=remember {DragRange()}
    var edgeSpeed by remember {mutableStateOf(0f)}
    fun markRange(at:Int) {
        if(at==drag.last || drag.from<0) return
        val range=order.subList(minOf(drag.from,at),maxOf(drag.from,at)+1).map {it.second}
        marked=if(drag.adding) drag.base+range else drag.base-range
        if(drag.last>=0) DragHaptics.slot(view)
        drag.last=at
    }
    LaunchedEffect(edgeSpeed!=0f) {
        while(edgeSpeed!=0f) {
            gridState.scrollBy(edgeSpeed)
            gridState.keyAt(drag.point)?.let {key -> order.indexOfFirst {it.first==key}.takeIf {it>=0}?.let(::markRange)}
            withFrameNanos {}
        }
    }
    Scaffold(containerColor=ScreenBackground,snackbarHost={SnackbarHost(snackbar)},topBar={if(selecting) LibrarySelectionBar(markedVisible.size,markedVisible.size==1,
        favoritesOn=markedVisible.isNotEmpty() && markedVisible.all {it in favorites},
        onClose={marked=emptySet()},onSelectAll={marked=visibleTokens},onExport={startExport()},
        onFavorite={
            val allFav=markedVisible.all {it in favorites}
            favorites=if(allFav) favorites-markedVisible else favorites+markedVisible
            prefs.edit().putStringSet("favorites",favorites).apply()
            haptic.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.SELECTION)
        },onHide={
            val hide=!showHidden
            val tokens=markedVisible.toSet()
            val previouslyHidden=hidden.intersect(tokens)
            hidden=if(hide) hidden+tokens else hidden-tokens
            prefs.edit().putStringSet("hidden",hidden).apply()
            val count=tokens.size;marked=emptySet()
            scope.launch {
                if(snackbar.showSnackbar(if(hide) "$count ocultados" else "$count restaurados","Deshacer",withDismissAction=true)==SnackbarResult.ActionPerformed) {
                    hidden=(hidden-tokens)+previouslyHidden
                    prefs.edit().putStringSet("hidden",hidden).apply()
                }
            }
        },onOptions={
            val token=markedVisible.first()
            optionsFor=flat.firstOrNull {tokenOf(it)==token}
            marked=emptySet()
        },onMove={moving=flat.filter {tokenOf(it) in markedVisible}},
        onRename={bulkRename=flat.filter {tokenOf(it) in markedVisible}},
        onCrop=flat.singleOrNull {tokenOf(it) in markedVisible}?.takeIf {markedVisible.size==1 && isImage(it)}?.let {entry -> {cropTarget=entry;marked=emptySet()}},
        onArtBox=onOpenArtBox?.let {open -> flat.filter {tokenOf(it) in markedVisible && isImage(it)}.takeIf {it.isNotEmpty()}?.let {images -> {
            com.vidadomu.nexus.ui.models.ArtBoxInbox.images.value=images.map {entry ->
                android.net.Uri.parse(viewerMedia(entry,conflicts).uri) to when(entry) {
                    is LibraryEntry.Local -> entry.asset.resolvedMime()
                    is LibraryEntry.Out -> entry.job.outputs[entry.index].mimeType.ifBlank {"image/jpeg"}
                }
            }
            marked=emptySet();open()
        }}}) else Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start=if(openFolder!=null) NexusTokens.space4 else NexusTokens.space16),verticalAlignment=Alignment.CenterVertically) {
        // Dentro de una carpeta: flecha atrás y su nombre en lugar de «Biblioteca».
        if(openFolder!=null) IconButton(onClick={openFolder=null}) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"Volver a la Biblioteca")}
        // Título, recuento y saldo comparten una sola cabecera: ya no hay una fila entera solo para «N archivos · saldo».
        Column(Modifier.weight(1f)) {
            Text(folderName ?: "Biblioteca",style=MaterialTheme.typography.headlineSmall,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Row(verticalAlignment=Alignment.CenterVertically) {
                val count=completed.sumOf {it.outputs.size}+localAssets.size
                Text(if(count==1) "1 archivo" else "$count archivos",style=MaterialTheme.typography.labelMedium,color=TextSecondary,maxLines=1)
                com.vidadomu.nexus.ui.components.NexusBalanceChip(compact=true)
            }
        }

        onOpenHistory?.let {go->IconButton(onClick=go) {Icon(Icons.Default.History,"Historial de trabajos")}}

        IconButton(onClick={searchVisible=!searchVisible}) {Icon(Icons.Default.Search,if(searchVisible) "Ocultar búsqueda" else "Buscar en Biblioteca",tint=if(searchVisible) MaterialTheme.colorScheme.primary else TextSecondary)}
        IconButton(enabled=!importing,onClick={importPicker.launch(arrayOf("image/*","video/*","audio/*","application/pdf","text/*"))}) {Icon(Icons.Default.Add,"Importar archivos")}
        Box {
        IconButton(onClick={moreVisible=true}) {Icon(Icons.Default.MoreVert,"Más opciones de biblioteca",tint=TextSecondary)}
        DropdownMenu(expanded=moreVisible,onDismissRequest={moreVisible=false}) {
        DropdownMenuItem(text={Text("Nueva carpeta")},leadingIcon={Icon(Icons.Default.CreateNewFolder,null)},onClick={moreVisible=false;creatingFolder=true})
        DropdownMenuItem(text={Text(if(showDetails) "Ocultar detalles de miniaturas" else "Mostrar detalles de miniaturas")},leadingIcon={Icon(Icons.Default.Info,null)},onClick={
            moreVisible=false;showDetails=!showDetails;prefs.edit().putBoolean("tile_details",showDetails).apply()})
        DropdownMenuItem(text={Text("Ediciones locales")},leadingIcon={Icon(Icons.Default.Movie,null)},onClick={moreVisible=false;showLocalEdits=true})
        DropdownMenuItem(text={Text("Importar de Flow")},leadingIcon={Icon(Icons.Default.CloudDownload,null)},onClick={moreVisible=false;showFlowImport=true})
        DropdownMenuItem(text={Text("Imágenes del proyecto")},leadingIcon={Icon(Icons.Default.PhotoLibrary,null)},onClick={moreVisible=false;projectLibrary.launch(arrayOf("image/*"))})
        }
        }
    }}) {padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(searchVisible || query.isNotBlank()) StudioTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space8),singleLine=true,
                placeholder={Text("Buscar por modelo o descripción")},shape=NexusTokens.field,
                trailingIcon={if(query.isNotBlank()) IconButton(onClick={query=""}) {Icon(Icons.Default.Close,"Borrar búsqueda")}})
            LazyRow(contentPadding=PaddingValues(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space4),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8),verticalAlignment=Alignment.CenterVertically) {
                item {StudioFilterChip(filter==null,{filter=null},label={Text("Todos")},shape=NexusTokens.filter)}
                items(listOf(Modality.IMAGE to "Imagen",Modality.VIDEO to "Vídeo",Modality.AUDIO to "Audio")) {(value,label) ->
                    StudioFilterChip(filter==value,{filter=value},label={Text(label)},shape=NexusTokens.filter)
                }
                // Origen como un único desplegable dentro de esta fila (sin etiquetas en las miniaturas).
                item {Box {
                    StudioFilterChip(originFilter!=LibraryOriginFilter.ALL,{originMenu=true},
                        label={Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(if(originFilter==LibraryOriginFilter.ALL) "Origen" else originFilter.label)
                            Icon(Icons.Default.ArrowDropDown,null,Modifier.size(NexusTokens.space16))
                        }},modifier=Modifier.semantics {contentDescription="Filtrar por origen: ${originFilter.label}"})
                    DropdownMenu(expanded=originMenu,onDismissRequest={originMenu=false}) {
                        origins.forEach {origin -> DropdownMenuItem(text={Text(origin.label)},
                            trailingIcon={if(origin==originFilter) Icon(Icons.Default.Check,null,tint=MaterialTheme.colorScheme.primary)},
                            onClick={originFilter=origin;originMenu=false})}
                    }
                }}
                item {StudioFilterChip(favoriteOnly,{favoriteOnly=!favoriteOnly},label={Icon(Icons.Default.Star,null,Modifier.size(NexusTokens.space16))},
                    modifier=Modifier.semantics {contentDescription="Solo favoritos"})}
                if(hidden.isNotEmpty() || showHidden) item {StudioFilterChip(showHidden,{showHidden=!showHidden},label={Text("Ocultos")})}
            }
            if(completed.isEmpty() && pending.isEmpty() && localAssets.isEmpty() && !showFolders) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                if(openFolder!=null && !viewFilters.hasQuery && filter==null && !favoriteOnly && !showHidden && originFilter==LibraryOriginFilter.ALL)
                    Text("Carpeta vacía. Marca archivos en la Biblioteca y usa «Cambiar de proyecto» para traerlos aquí.",
                        Modifier.padding(NexusTokens.space24),style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
                else LibraryNoResults(viewFilters,::applyFilters)
            } else LazyVerticalGrid(GridCells.Fixed(columns),Modifier.fillMaxSize().pinchColumns(columns) {columns=it;prefs.edit().putInt("grid_cols",it).apply()}
                .dragToSelect(gridState::keyAt,
                    onStart={key -> val at=order.indexOfFirst {it.first==key}
                        if(at<0) false else {drag.base=marked;drag.adding=order[at].second !in marked;drag.from=at;drag.last=-1;markRange(at);DragHaptics.lift(view);true}},
                    onMove={key -> order.indexOfFirst {it.first==key}.takeIf {it>=0}?.let(::markRange)},
                    onEdge={speed,at -> drag.point=at;edgeSpeed=speed},
                    onEnd={edgeSpeed=0f;drag.from=-1}),
                state=gridState,
                horizontalArrangement=Arrangement.spacedBy(NexusTokens.galleryGap),verticalArrangement=Arrangement.spacedBy(NexusTokens.galleryGap),
                contentPadding=PaddingValues(bottom=NexusTokens.space16)) {
                // Trabajos en curso: una línea por trabajo (modelo, estado y tiempo), no una tarjeta.
                items(pending,key={"pending:${it.id}"},span={GridItemSpan(maxLineSpan)}) {job -> PendingJobRow(job,{onNavigateToOutput(job.id)},
                    // Solo consulta el estado: nunca reenvía la generación.
                    onRefresh={scope.launch {
                        val result=app.generationRepository.pollJob(job.id)
                        snackbar.showSnackbar(if(result.isFailure) "No se pudo consultar el proveedor. El trabajo se conserva sin reenviarlo."
                            else "Estado: "+com.vidadomu.nexus.ui.components.generationStatusLabel(result.getOrThrow()))
                    }},
                    onStopTracking={scope.launch {
                        val stopped=app.generationRepository.stopTrackingJob(job.id)
                        snackbar.showSnackbar(if(stopped) "Dejas de seguirlo aquí. No se ha cancelado en el proveedor: puede terminar y cobrarse."
                            else "No se pudo dejar de seguir este trabajo")
                    }})}
                // Carpetas arriba, como en el explorador de archivos: una por proyecto.
                if(showFolders) {
                    item(key="folders:title",span={GridItemSpan(maxLineSpan)}) {Text("Carpetas",style=MaterialTheme.typography.labelMedium,color=TextSecondary,
                        modifier=Modifier.padding(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space12))}
                    items(folders,key={"folder:"+it.id},span={GridItemSpan((maxLineSpan/3).coerceAtLeast(1))}) {folder ->
                        FolderTile(folder.name,folderCounts[folder.id] ?: 0,folderPreviews[folder.id].orEmpty(),onLongClick={deletingFolder=folder.id}) {openFolder=folder.id}
                    }
                }
                // Una sola línea de tiempo: lo generado y lo importado se ordenan juntos por fecha (lo último arriba).
                shownTimeline.forEach {(day,entries) ->
                    item(key="header:$day",span={GridItemSpan(maxLineSpan)}) {Text(day,style=MaterialTheme.typography.labelMedium,color=TextSecondary,
                        modifier=Modifier.padding(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space12))}
                    items(entries,key={it.key}) {entry ->
                        when(entry) {
                            is LibraryEntry.Local -> {
                                val asset=entry.asset
                                val token="asset:"+asset.id
                                LocalAssetTile(asset,imageLoader,showDetails && columns<3,mark=if(selecting) token in marked else null,duplicate=asset.uri in duplicateUris,
                                    onPreview={if(selecting) toggleMark(token) else viewerAt=entry.key})
                            }
                            is LibraryEntry.Out -> {
                                val job=entry.job;val output=job.outputs[entry.index]
                                val token=OutputFiles.token(output)
                                LibraryTile(job,output,conflicts,imageLoader,token in favorites,showDetails && columns<3,
                                    mark=if(selecting) token in marked else null,duplicate=(OutputFiles.trustedLocalUri(output,conflicts) ?: output.url) in duplicateUris) {
                                    if(selecting) toggleMark(token) else viewerAt=entry.key
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryTile(job:GenerationJob,output:GenerationOutput,conflicts:Set<String>,imageLoader:ImageLoader,favorite:Boolean,showDetails:Boolean,mark:Boolean?,duplicate:Boolean=false,onOpen:()->Unit) {
    val context=LocalContext.current
    val interaction=remember {MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .96f else 1f,tween(NexusTokens.pressDuration),label="Media press")
    val duration=mediaDuration(output.durationSeconds)
    val type=when(output.type) {Modality.VIDEO->"Vídeo";Modality.AUDIO->"Audio";Modality.IMAGE->"Imagen";else->"Texto"}
    Box(Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer {scaleX=scale;scaleY=scale}.background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(interactionSource=interaction,indication=null,role=Role.Button,onClickLabel="Abrir",onClick=onOpen)
        .semantics(mergeDescendants=true) {contentDescription="$type · ${job.prompt.ifBlank {"Creación"}}${duration?.let {" · $it"}.orEmpty()}"},
        contentAlignment=Alignment.Center) {
        val local=OutputFiles.trustedLocalUri(output,conflicts)
        if(output.type in listOf(Modality.IMAGE,Modality.VIDEO)) {
            val isVideo=output.type==Modality.VIDEO
            if(local!=null) {
                // Copia local: se lee directamente del móvil y, si no se puede, la baldosa lo dice con un icono.
                when(val thumb=rememberThumb(local,isVideo)) {
                    is Thumb.Ready -> androidx.compose.foundation.Image(thumb.bitmap.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    Thumb.Failed -> Icon(if(isVideo) Icons.Default.Movie else Icons.Default.BrokenImage,null,tint=TextSecondary)
                    Thumb.Loading -> Unit
                }
            } else {
                var failed by remember(output.id,output.url) {mutableStateOf(false)}
                if(!failed) AsyncImage(model=remember(output.id,output.url) {ImageRequest.Builder(context)
                    .data(output.url).crossfade(180).apply {if(isVideo) decoderFactory {result,options,_->VideoFrameDecoder(result.source,options)}}.build()},
                    imageLoader=imageLoader,contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop,onError={failed=true})
                if(failed) Icon(if(isVideo) Icons.Default.Movie else Icons.Default.BrokenImage,null,tint=TextSecondary)
            }
        } else Icon(if(output.type==Modality.AUDIO) Icons.Default.GraphicEq else Icons.Default.TextSnippet,null,Modifier.size(NexusTokens.space32),tint=TextSecondary)
        // Sin ⋮ encima de la imagen ni etiqueta de origen: las opciones están en el visor.
        if(mark!=null) TileMark(mark,Modifier.align(Alignment.TopStart))
        else if(favorite) Icon(Icons.Default.Star,"Favorito",Modifier.align(Alignment.TopStart).padding(NexusTokens.space8).size(NexusTokens.iconSmall),tint=MaterialTheme.colorScheme.primary)
        Column(modifier=Modifier.align(Alignment.TopEnd),horizontalAlignment=Alignment.End) {
            if(output.type in listOf(Modality.VIDEO,Modality.AUDIO)) TileMediaBadge(output.type==Modality.VIDEO,duration)
            if(duplicate) TileDuplicateBadge()
        }
        if(showDetails) {
            val known=output.width?.takeIf {it>0}!=null && output.height?.takeIf {it>0}!=null
            val mime=output.mimeType.ifBlank {when(output.type) {Modality.IMAGE->"image/*";Modality.VIDEO->"video/*";Modality.AUDIO->"audio/*";else->"application/octet-stream"}}
            val facts=rememberTileFacts(local,mime,dimensions=!known && output.type in listOf(Modality.IMAGE,Modality.VIDEO))
            TileDetailsCaption(
                quality=if(output.type in listOf(Modality.IMAGE,Modality.VIDEO)) tileQuality(output.type==Modality.IMAGE,output.width?.takeIf {it>0} ?: facts?.width,output.height?.takeIf {it>0} ?: facts?.height) else null,
                bytes=facts?.bytes,epochMillis=job.completedAt ?: job.startedAt,modifier=Modifier.align(Alignment.BottomCenter))
        }
    }
}


/** Estado de un arrastre para marcar: lo marcado al empezar, si suma o quita, y dónde empezó y va. */
private class DragRange {
    var base=emptySet<String>()
    var adding=true
    var from=-1
    var last=-1
    var point=androidx.compose.ui.geometry.Offset.Zero
}

/** Un trabajo en curso en una sola línea: progreso, modelo, estado y tiempo transcurrido. */
@Composable
private fun PendingJobRow(job:GenerationJob,onOpen:()->Unit,onRefresh:()->Unit,onStopTracking:()->Unit) {
    val app=LocalContext.current.applicationContext as com.vidadomu.nexus.NexusApplication
    val model=remember(job.modelId) {app.modelCatalogRepository.getModelById(job.modelId)?.displayName
        ?: job.modelId.substringAfterLast('/').replace('-',' ').replace('_',' ')}
    var now by remember {mutableStateOf(System.currentTimeMillis())}
    LaunchedEffect(job.id) {while(true) {kotlinx.coroutines.delay(1000);now=System.currentTimeMillis()}}
    val elapsed=((now-job.startedAt)/1000).coerceAtLeast(0).let {s -> if(s>=60) "${s/60} min" else "$s s"}
    val stuck=(now-job.startedAt)>=STUCK_AFTER_MS
    Row(Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch).clickable(role=Role.Button,onClick=onOpen)
        .padding(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space8),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
        val progress=job.progress
        if(progress!=null) CircularProgressIndicator(progress={progress.coerceIn(0f,1f)},modifier=Modifier.size(18.dp),strokeWidth=2.dp)
        else CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
        // Model plus the start of the prompt: two jobs of the same model are no longer two identical lines.
        Column(Modifier.weight(1f)) {
            Text(model,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            job.prompt.trim().takeIf {it.isNotEmpty()}?.let {
                Text(it,style=MaterialTheme.typography.labelSmall,color=TextSecondary,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Text(if(stuck) "Tarda más de lo normal · $elapsed" else "${com.vidadomu.nexus.ui.components.generationStatusLabel(job)} · $elapsed",
                style=MaterialTheme.typography.labelMedium,color=if(stuck) AccentAmber else TextSecondary)
            progress?.takeIf {it.isFinite() && it>0f}?.let {
                LinearProgressIndicator(progress={it.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().padding(top=NexusTokens.space4).height(3.dp))
            }
        }
        if(stuck) {
            IconButton(onClick=onRefresh,Modifier.size(NexusTokens.minimumTouch)) {Icon(Icons.Default.Refresh,"Consultar estado en el proveedor",Modifier.size(NexusTokens.iconMedium),tint=AccentAmber)}
            IconButton(onClick=onStopTracking,Modifier.size(NexusTokens.minimumTouch)) {Icon(Icons.Default.Close,"Dejar de seguir este trabajo",Modifier.size(NexusTokens.iconMedium),tint=TextSecondary)}
        }
    }
}

/**
 * Pellizco con dos dedos sobre la cuadrícula: abrir los dedos = miniaturas más grandes (menos columnas); juntarlos = más pequeñas.
 * Solo consume el gesto cuando hay dos dedos, así el desplazamiento con uno sigue siendo normal.
 */
private fun Modifier.pinchColumns(current:Int,onChange:(Int)->Unit):Modifier = this.pointerInput(current) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed=false)
        var scale=1f
        do {
            val event=awaitPointerEvent(PointerEventPass.Initial)
            if(event.changes.size>=2) {
                scale*=event.calculateZoom()
                event.changes.forEach {it.consume()}
                val next=when {scale>1.3f -> (current-1).coerceAtLeast(2); scale<.77f -> (current+1).coerceAtMost(6); else -> current}
                if(next!=current) {onChange(next);scale=1f;return@awaitEachGesture}
            }
        } while(event.changes.any {it.pressed})
    }
}
