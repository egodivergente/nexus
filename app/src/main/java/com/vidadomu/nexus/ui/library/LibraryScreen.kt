package com.vidadomu.nexus.ui.library

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.*
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
    onSendResultToAgent:((String,String)->Unit)?=null, onOpenHistory:(()->Unit)?=null) {
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
    var actionTarget by remember {mutableStateOf<Pair<GenerationJob,GenerationOutput>?>(null)}
    // Marcado rápido: mantener pulsado marca; con algo marcado, tocar suma o quita. Vaciar la selección la cierra.
    var marked by remember {mutableStateOf(emptySet<String>())}
    val selecting=marked.isNotEmpty()
    fun toggleMark(token:String) {marked=if(token in marked) marked-token else marked+token}
    BackHandler(enabled=selecting) {marked=emptySet()}
    val scope=rememberCoroutineScope()
    val snackbar=remember {SnackbarHostState()}
    val haptic=LocalHapticFeedback.current
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
                try { com.vidadomu.nexus.assistant.AgentMediaStore.persist(app,uri,context.contentResolver.getType(uri),runtime.settings.activeProjectId()); imported++ }
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
    var projectMenu by remember {mutableStateOf(false)}
    var selectedProjects by remember {mutableStateOf(prefs.getStringSet("project_filter",emptySet()).orEmpty().toSet())}
    fun setProjects(next:Set<String>) {selectedProjects=next;prefs.edit().putStringSet("project_filter",next).apply()}
    val projectIndex=remember(index) {LibraryProjectIndex(index)}
    // Zoom con dos dedos como Google Fotos: 2–6 columnas, recordado entre sesiones.
    // Sin preferencia guardada, las columnas se adaptan al ancho (miniaturas de ~120 dp); el zoom las fija luego.
    val widthDp=androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    var columns by remember {mutableStateOf(prefs.getInt("grid_cols",(widthDp/120).coerceIn(3,6)).coerceIn(2,6))}
    val viewFilters=LibraryViewFilters(query,filter,originFilter,favoriteOnly,showHidden,selectedProjects)
    val focusManager=androidx.compose.ui.platform.LocalFocusManager.current
    fun applyFilters(next:LibraryViewFilters) {
        if(!next.hasQuery) focusManager.clearFocus()
        query=next.query;filter=next.modality;originFilter=next.origin;favoriteOnly=next.favoriteOnly;showHidden=next.showHidden
        setProjects(next.projects)
    }
    // Editado/Compartido solo se ofrecen si hay algo de ese origen; Nexus e Importado, siempre.
    val origins=remember(index) {
        val present=index.assets.filter {!it.libraryDeleted}.map(::libraryOrigin).toSet()
        LibraryOriginFilter.values().filter {it in setOf(LibraryOriginFilter.ALL,LibraryOriginFilter.GENERATED,LibraryOriginFilter.IMPORTED) || it in present}
    }
    val completed=remember(jobs,filter,query,removed,hidden,favorites,favoriteOnly,showHidden,originFilter,selectedProjects,projectIndex) {
        if(originFilter !in setOf(LibraryOriginFilter.ALL,LibraryOriginFilter.GENERATED)) emptyList()
        else jobs.map {job -> job.copy(outputs=job.outputs.filter {projectIndex.matches(selectedProjects,job,it) && it.localUri !in removed && it.url !in removed && (OutputFiles.token(it) in hidden)==showHidden && (!favoriteOnly || OutputFiles.token(it) in favorites)})}.filter {it.status==GenerationStatus.COMPLETED && it.outputs.isNotEmpty()}
            .filter {filter==null || it.modality==filter}.filter {matchesCreationSearch(it,query)}.sortedByDescending {it.startedAt}
    }
    val pending=remember(jobs,filter,query,showHidden,favoriteOnly,originFilter,selectedProjects,projectIndex) {if(showHidden || favoriteOnly || originFilter !in setOf(LibraryOriginFilter.ALL,LibraryOriginFilter.GENERATED)) emptyList() else jobs.filter {
        projectIndex.matches(selectedProjects,it) &&
        it.status !in setOf(GenerationStatus.COMPLETED,GenerationStatus.CANCELLED,GenerationStatus.FAILED) &&
        (filter==null || it.modality==filter) && matchesCreationSearch(it,query)
    }.sortedByDescending {it.startedAt}}
    val localAssets=remember(index,filter,query,hidden,showHidden,favorites,favoriteOnly,jobs,originFilter,selectedProjects,projectIndex) {
        val generatedUris=jobs.flatMap {it.outputs}.flatMap {listOfNotNull(it.url,it.localUri)}.toSet()
        index.assets.filter { a -> !a.libraryDeleted && a.uri !in generatedUris && projectIndex.matches(selectedProjects,a) &&
            a.matchesLibraryOrigin(originFilter) &&
            (filter==null || a.type.name==filter?.name) && a.displayName.contains(query,true) &&
            ("asset:"+a.id in hidden)==showHidden && (!favoriteOnly || "asset:"+a.id in favorites) }.sortedByDescending {it.createdAt}
    }
    val localAssetGroups=remember(localAssets) {localAssets.groupBy(::libraryOrigin)}
    // Una sola hoja por archivo importado (vista previa, ficha, Lienzo, Agente y acciones).
    var localTarget by remember {mutableStateOf<com.vidadomu.nexus.agent.core.assets.Asset?>(null)}
    localTarget?.let {asset -> LocalAssetSheet(asset,{localTarget=null},{message -> scope.launch {snackbar.showSnackbar(message)}},
        onUseInCanvas,onSendToAgent) }
    val conflicts=remember(jobs) {OutputFiles.conflictingLocalUris(jobs.flatMap {it.outputs})}
    val timeline=remember(completed,localAssets) {
        val day=SimpleDateFormat("d MMMM yyyy",Locale("es","ES"))
        (completed.flatMap {job -> job.outputs.indices.map {LibraryEntry.Out(job,it)}}+localAssets.map {LibraryEntry.Local(it)})
            .sortedByDescending {it.time}.groupBy {day.format(Date(it.time))}
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
    val imageLoader=remember(context) {ImageLoader.Builder(context).components {add(VideoFrameDecoder.Factory())}
        .decoderDispatcher(kotlinx.coroutines.Dispatchers.IO.limitedParallelism(2))
        .memoryCache {coil.memory.MemoryCache.Builder(context).maxSizePercent(.08).build()}.build()}
    DisposableEffect(imageLoader) {onDispose {imageLoader.shutdown()}}
    actionTarget?.let {(job,output) ->
        val token=OutputFiles.token(output)
        LibraryActions(job,output,OutputFiles.trustedLocalUri(output,conflicts),token in favorites,token in hidden,
            onDismiss={actionTarget=null},onOpen={
                val position=jobs.firstOrNull {it.id==job.id}?.outputs?.indexOfFirst {it.id==output.id} ?: -1
                if(onNavigateToResult!=null && position>=0) onNavigateToResult(job.id,position) else onNavigateToOutput(job.id)
            },onFavorite={
                favorites=if(token in favorites) favorites-token else favorites+token
                prefs.edit().putStringSet("favorites",favorites).apply()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },onHide={
                val wasHidden=token in hidden
                hidden=if(wasHidden) hidden-token else hidden+token;prefs.edit().putStringSet("hidden",hidden).apply()
                scope.launch {if(snackbar.showSnackbar(if(wasHidden) "Archivo restaurado" else "Archivo ocultado","Deshacer",withDismissAction=true)==SnackbarResult.ActionPerformed) {
                    hidden=if(wasHidden) hidden+token else hidden-token;prefs.edit().putStringSet("hidden",hidden).apply()
                }}
            },onDelete={scope.launch {
                val assets=index.assets.filter {it.uri==output.url || it.uri==output.localUri}
                runtime.assets.deleteLibraryAssets(assets.map {it.id}.toSet())
                hidden=hidden+token;prefs.edit().putStringSet("hidden",hidden).apply()
                snackbar.showSnackbar("Eliminado de la biblioteca; el historial se conserva")
            }},onError={message -> scope.launch {snackbar.showSnackbar(message)}},
            onAddToChat=onSendResultToAgent?.let { send -> { send(job.id,output.id) } }, onSelect={toggleMark(token)})
    }
    Scaffold(containerColor=ScreenBackground,snackbarHost={SnackbarHost(snackbar)},topBar={if(selecting) LibrarySelectionBar(markedVisible.size,markedVisible.size==1,
        favoritesOn=markedVisible.isNotEmpty() && markedVisible.all {it in favorites},
        onClose={marked=emptySet()},onSelectAll={marked=visibleTokens},onExport={startExport()},
        onFavorite={
            val allFav=markedVisible.all {it in favorites}
            favorites=if(allFav) favorites-markedVisible else favorites+markedVisible
            prefs.edit().putStringSet("favorites",favorites).apply()
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        },onHide={
            val hide=!showHidden
            hidden=if(hide) hidden+markedVisible else hidden-markedVisible
            prefs.edit().putStringSet("hidden",hidden).apply()
            val count=markedVisible.size;marked=emptySet()
            scope.launch {snackbar.showSnackbar(if(hide) "$count ocultados" else "$count restaurados")}
        },onOptions={
            val token=markedVisible.first()
            if(token.startsWith("asset:")) localTarget=localAssets.firstOrNull {"asset:"+it.id==token}
            else completed.firstNotNullOfOrNull {job -> job.outputs.firstOrNull {OutputFiles.token(it)==token}?.let {job to it}}?.let {actionTarget=it}
            marked=emptySet()
        }) else Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start=16.dp),verticalAlignment=Alignment.CenterVertically) {

        // Título, recuento y saldo comparten una sola cabecera: ya no hay una fila entera solo para «N archivos · saldo».
        Column(Modifier.weight(1f)) {
            Text("Biblioteca",style=MaterialTheme.typography.headlineSmall,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
        DropdownMenuItem(text={Text(if(showDetails) "Ocultar detalles de miniaturas" else "Mostrar detalles de miniaturas")},leadingIcon={Icon(Icons.Default.Info,null)},onClick={
            moreVisible=false;showDetails=!showDetails;prefs.edit().putBoolean("tile_details",showDetails).apply()})
        DropdownMenuItem(text={Text("Ediciones locales")},leadingIcon={Icon(Icons.Default.Movie,null)},onClick={moreVisible=false;showLocalEdits=true})
        DropdownMenuItem(text={Text("Importar de Flow")},leadingIcon={Icon(Icons.Default.CloudDownload,null)},onClick={moreVisible=false;showFlowImport=true})
        DropdownMenuItem(text={Text("Imágenes del proyecto")},leadingIcon={Icon(Icons.Default.PhotoLibrary,null)},onClick={moreVisible=false;projectLibrary.launch(arrayOf("image/*"))})
        DropdownMenuItem(text={Text("Abrir Alexia")},leadingIcon={Icon(Icons.Default.FolderSpecial,null)},onClick={moreVisible=false
            if(!com.vidadomu.nexus.ui.output.launchAlexia(context)) scope.launch {snackbar.showSnackbar("No se pudo abrir Alexia. Tus archivos siguen en la Biblioteca de Nexus.")}})
        }
        }
    }}) {padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(searchVisible || query.isNotBlank()) StudioTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space8),singleLine=true,
                placeholder={Text("Buscar por modelo o descripción")},shape=NexusTokens.field,
                trailingIcon={if(query.isNotBlank()) IconButton(onClick={query=""}) {Icon(Icons.Default.Close,"Borrar búsqueda")}})
            LazyRow(contentPadding=PaddingValues(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space4),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8),verticalAlignment=Alignment.CenterVertically) {
                item {Box {
                    StudioFilterChip(selectedProjects.isNotEmpty(),{projectMenu=true},label={Text(if(selectedProjects.isEmpty()) "Proyectos" else "Proyectos · ${selectedProjects.size}")},
                        leadingIcon={Icon(Icons.Default.FolderOpen,null)},modifier=Modifier.semantics {contentDescription="Filtrar por proyectos"})
                    DropdownMenu(projectMenu,{projectMenu=false}) {
                        DropdownMenuItem(text={Text("Todos los proyectos")},leadingIcon={Icon(if(selectedProjects.isEmpty()) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,null)},onClick={setProjects(emptySet())})
                        (index.projects.map {it.id to it.name}+listOf(LIBRARY_UNASSIGNED_PROJECT to "Sin proyecto")).forEach {(id,name)->
                            DropdownMenuItem(text={Text(name)},leadingIcon={Icon(if(id in selectedProjects) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,null)},onClick={setProjects(if(id in selectedProjects) selectedProjects-id else selectedProjects+id)})
                        }
                    }
                }}
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
            if(completed.isEmpty() && pending.isEmpty() && localAssets.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                LibraryNoResults(viewFilters,::applyFilters)
            } else LazyVerticalGrid(GridCells.Fixed(columns),Modifier.fillMaxSize().pinchColumns(columns) {columns=it;prefs.edit().putInt("grid_cols",it).apply()},
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
                // Una sola línea de tiempo: lo generado y lo importado se ordenan juntos por fecha (lo último arriba).
                timeline.forEach {(day,entries) ->
                    item(key="header:$day",span={GridItemSpan(maxLineSpan)}) {Text(day,style=MaterialTheme.typography.labelMedium,color=TextSecondary,
                        modifier=Modifier.padding(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space12))}
                    items(entries,key={it.key}) {entry ->
                        when(entry) {
                            is LibraryEntry.Local -> {
                                val asset=entry.asset
                                val token="asset:"+asset.id
                                LocalAssetTile(asset,imageLoader,showDetails && columns<3,mark=if(selecting) token in marked else null,duplicate=asset.uri in duplicateUris,
                                    onPreview={if(selecting) toggleMark(token) else localTarget=asset},onActions={toggleMark(token)})
                            }
                            is LibraryEntry.Out -> {
                                val job=entry.job;val output=job.outputs[entry.index]
                                val token=OutputFiles.token(output)
                                LibraryTile(job,output,conflicts,imageLoader,token in favorites,showDetails && columns<3,
                                    mark=if(selecting) token in marked else null,duplicate=(OutputFiles.trustedLocalUri(output,conflicts) ?: output.url) in duplicateUris,
                                    onActions={if(selecting) toggleMark(token) else actionTarget=job to output}) {
                                    if(selecting) toggleMark(token)
                                    else if(onNavigateToResult!=null) onNavigateToResult(job.id,jobs.first {it.id==job.id}.outputs.indexOf(output)) else onNavigateToOutput(job.id)
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
private fun LibraryTile(job:GenerationJob,output:GenerationOutput,conflicts:Set<String>,imageLoader:ImageLoader,favorite:Boolean,showDetails:Boolean,mark:Boolean?,duplicate:Boolean=false,onActions:()->Unit,onOpen:()->Unit) {
    val context=LocalContext.current
    val haptic=LocalHapticFeedback.current
    val interaction=remember {MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .96f else 1f,tween(120),label="Media press")
    val duration=mediaDuration(output.durationSeconds)
    val type=when(output.type) {Modality.VIDEO->"Vídeo";Modality.AUDIO->"Audio";Modality.IMAGE->"Imagen";else->"Texto"}
    Box(Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer {scaleX=scale;scaleY=scale}.background(MaterialTheme.colorScheme.surfaceContainer)
        .combinedClickable(interactionSource=interaction,indication=null,role=Role.Button,onClickLabel="Ver resultado",onClick=onOpen,onLongClickLabel="Opciones del archivo",onLongClick={haptic.performHapticFeedback(HapticFeedbackType.LongPress);onActions()})
        .semantics(mergeDescendants=true) {contentDescription="$type · ${job.prompt.ifBlank {"Creación"}}${duration?.let {" · $it"}.orEmpty()}"},
        contentAlignment=Alignment.Center) {
        val local=OutputFiles.trustedLocalUri(output,conflicts)
        if(output.type in listOf(Modality.IMAGE,Modality.VIDEO)) {
            var failed by remember(output.id,local,output.url) {mutableStateOf(false)}
            if(!failed) AsyncImage(model=remember(output.id,local,output.url) {ImageRequest.Builder(context)
                .data(local ?: output.url).crossfade(180).apply {if(output.type==Modality.VIDEO) decoderFactory {result,options,_->VideoFrameDecoder(result.source,options)}}.build()},
                imageLoader=imageLoader,contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop,onError={failed=true})
            if(failed) Icon(if(output.type==Modality.VIDEO) Icons.Default.Movie else Icons.Default.BrokenImage,null,tint=TextSecondary)
        } else Icon(if(output.type==Modality.AUDIO) Icons.Default.GraphicEq else Icons.Default.TextSnippet,null,Modifier.size(NexusTokens.space32),tint=TextSecondary)
        // Opciones al mantener pulsado (sin ⋮ encima de la imagen) y sin etiqueta de origen.
        if(mark!=null) TileMark(mark,Modifier.align(Alignment.TopStart))
        else if(favorite) Icon(Icons.Default.Star,"Favorito",Modifier.align(Alignment.TopStart).padding(NexusTokens.space8).size(18.dp),tint=MaterialTheme.colorScheme.primary)
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


/** Un trabajo en curso en una sola línea: progreso, modelo, estado y tiempo transcurrido. */
@Composable
private fun PendingJobRow(job:GenerationJob,onOpen:()->Unit,onRefresh:()->Unit,onStopTracking:()->Unit) {
    val app=LocalContext.current.applicationContext as com.vidadomu.nexus.NexusApplication
    val model=remember(job.modelId) {app.modelCatalogRepository.getModelById(job.modelId)?.displayName
        ?: job.modelId.substringAfterLast('/').replace('-',' ').replace('_',' ')}
    var now by remember {mutableStateOf(System.currentTimeMillis())}
    LaunchedEffect(job.id) {while(true) {kotlinx.coroutines.delay(1000);now=System.currentTimeMillis()}}
    val elapsed=((now-job.startedAt)/1000).coerceAtLeast(0).let {s -> if(s>=60) "${s/60} min" else "$s s"}
    Row(Modifier.fillMaxWidth().clickable(onClick=onOpen).padding(horizontal=NexusTokens.screenMargin,vertical=6.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
        val progress=job.progress
        if(progress!=null) CircularProgressIndicator(progress={progress.coerceIn(0f,1f)},modifier=Modifier.size(18.dp),strokeWidth=2.dp)
        else CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
        Text(model,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
        // Pasados ~10 min el trabajo «tarda más de lo normal»: se tiñe de ámbar y ofrece consultar o dejar de seguirlo.
        val stuck=(now-job.startedAt)>=STUCK_AFTER_MS
        Text(if(stuck) "Tarda más de lo normal · $elapsed" else "${com.vidadomu.nexus.ui.components.generationStatusLabel(job)} · $elapsed",
            style=MaterialTheme.typography.labelMedium,color=if(stuck) AccentAmber else TextSecondary,maxLines=1)
        if(stuck) {
            IconButton(onClick=onRefresh,Modifier.size(40.dp)) {Icon(Icons.Default.Refresh,"Consultar estado en el proveedor",Modifier.size(20.dp),tint=AccentAmber)}
            IconButton(onClick=onStopTracking,Modifier.size(40.dp)) {Icon(Icons.Default.Close,"Dejar de seguir este trabajo",Modifier.size(20.dp),tint=TextSecondary)}
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
