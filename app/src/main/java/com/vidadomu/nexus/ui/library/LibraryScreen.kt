package com.vidadomu.nexus.ui.library

import com.vidadomu.nexus.ui.components.NexusTopAppBar as TopAppBar
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.components.StudioFilterChip
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.*
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Intent
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.assistant.LocalMediaEditsSheet
import com.vidadomu.nexus.assistant.rememberProjectMediaPicker
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

internal fun mediaDuration(seconds:Float?):String? = seconds?.takeIf {it.isFinite() && it>=0}?.toLong()?.let {
    "%d:%02d".format(Locale.ROOT,it/60,it%60)
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun LibraryScreen(onNavigateToOutput:(String)->Unit,onNavigateToResult:((String,Int)->Unit)?=null) {
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
    val scope=rememberCoroutineScope()
    val snackbar=remember {SnackbarHostState()}
    val haptic=LocalHapticFeedback.current
    val runtime=remember(app) {AssistantRuntime.get(app)}
    var showLocalEdits by rememberSaveable {mutableStateOf(false)}
    if(showLocalEdits) LocalMediaEditsSheet {showLocalEdits=false}
    val index by runtime.assets.changes.collectAsState()
    val projectLibrary=rememberProjectMediaPicker(managementOnly=true) {}
    LaunchedEffect(jobs) {kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {runtime.importHistory()}}
    val importPicker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) {uris ->
        if(uris.isNotEmpty()) { importing=true; scope.launch {
            var imported=0
            try { for(uri in uris) {
                try { com.vidadomu.nexus.assistant.AgentMediaStore.persist(app,uri,context.contentResolver.getType(uri),runtime.settings.activeProjectId()); imported++ }
                catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(_:Exception) { }
            }; snackbar.showSnackbar("Importados $imported de ${uris.size} archivos") }
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
    // Editado/Compartido solo se ofrecen si hay algo de ese origen; Nexus e Importado, siempre.
    val origins=remember(index) {
        val present=index.assets.filter {!it.libraryDeleted}.map(::libraryOrigin).toSet()
        LibraryOriginFilter.values().filter {it in setOf(LibraryOriginFilter.ALL,LibraryOriginFilter.GENERATED,LibraryOriginFilter.IMPORTED) || it in present}
    }
    val completed=remember(jobs,filter,query,removed,hidden,favorites,favoriteOnly,showHidden,originFilter) {
        if(originFilter !in setOf(LibraryOriginFilter.ALL,LibraryOriginFilter.GENERATED)) emptyList()
        else jobs.map {job -> job.copy(outputs=job.outputs.filter {it.localUri !in removed && it.url !in removed && (OutputFiles.token(it) in hidden)==showHidden && (!favoriteOnly || OutputFiles.token(it) in favorites)})}.filter {it.status==GenerationStatus.COMPLETED && it.outputs.isNotEmpty()}
            .filter {filter==null || it.modality==filter}.filter {matchesCreationSearch(it,query)}.sortedByDescending {it.startedAt}
    }
    val pending=remember(jobs,filter,query,showHidden,favoriteOnly,originFilter) {if(showHidden || favoriteOnly || originFilter !in setOf(LibraryOriginFilter.ALL,LibraryOriginFilter.GENERATED)) emptyList() else jobs.filter {
        it.status !in setOf(GenerationStatus.COMPLETED,GenerationStatus.CANCELLED,GenerationStatus.FAILED) &&
        (filter==null || it.modality==filter) && matchesCreationSearch(it,query)
    }.sortedByDescending {it.startedAt}}
    val localAssets=remember(index,filter,query,hidden,showHidden,favorites,favoriteOnly,jobs,originFilter) {
        val generatedUris=jobs.flatMap {it.outputs}.flatMap {listOfNotNull(it.url,it.localUri)}.toSet()
        index.assets.filter { a -> !a.libraryDeleted && a.uri !in generatedUris &&
            a.matchesLibraryOrigin(originFilter) &&
            (filter==null || a.type.name==filter?.name) && a.displayName.contains(query,true) &&
            ("asset:"+a.id in hidden)==showHidden && (!favoriteOnly || "asset:"+a.id in favorites) }.sortedByDescending {it.createdAt}
    }
    val localAssetGroups=remember(localAssets) {localAssets.groupBy(::libraryOrigin)}
    var localTarget by remember {mutableStateOf<com.vidadomu.nexus.agent.core.assets.Asset?>(null)}
    localTarget?.let {asset -> LocalAssetActions(asset,{localTarget=null}) {message -> scope.launch {snackbar.showSnackbar(message)}} }
    var localPreviewTarget by remember {mutableStateOf<com.vidadomu.nexus.agent.core.assets.Asset?>(null)}
    localPreviewTarget?.let {asset -> LocalAssetPreviewSheet(asset,
        onDismiss={localPreviewTarget=null},
        onMoreOptions={localPreviewTarget=null;localTarget=asset}) }
    val conflicts=remember(jobs) {OutputFiles.conflictingLocalUris(jobs.flatMap {it.outputs})}
    val groups=remember(completed) {completed.groupBy {SimpleDateFormat("d MMMM yyyy",Locale("es","ES")).format(Date(it.startedAt))}}
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
            }},onError={message -> scope.launch {snackbar.showSnackbar(message)}})
    }
    Scaffold(containerColor=ScreenBackground,snackbarHost={SnackbarHost(snackbar)},topBar={TopAppBar(title={
        Column(verticalArrangement=Arrangement.Center) {
            Text("Biblioteca",style=com.vidadomu.nexus.ui.components.nexusScreenTitleStyle(),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            val count=completed.sumOf {it.outputs.size}+localAssets.size
            Text(if(count==1) "1 archivo" else "$count archivos",style=MaterialTheme.typography.labelSmall,color=TextSecondary,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    },actions={
        IconButton(enabled=!importing,onClick={importPicker.launch(arrayOf("image/*","video/*","audio/*","application/pdf","text/*"))}) {Icon(Icons.Default.Add,"Importar archivos")}
        Box {
        IconButton(onClick={moreVisible=true}) {Icon(Icons.Default.MoreVert,"Más opciones de biblioteca",tint=TextSecondary)}
        DropdownMenu(expanded=moreVisible,onDismissRequest={moreVisible=false}) {
        DropdownMenuItem(text={Text("Buscar")},leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.semantics {contentDescription="Buscar en Biblioteca"},onClick={moreVisible=false;searchVisible=!searchVisible})
        DropdownMenuItem(text={Text(if(showDetails) "Ocultar detalles de miniaturas" else "Mostrar detalles de miniaturas")},leadingIcon={Icon(Icons.Default.Info,null)},onClick={
            moreVisible=false;showDetails=!showDetails;prefs.edit().putBoolean("tile_details",showDetails).apply()})
        DropdownMenuItem(text={Text("Ediciones locales")},leadingIcon={Icon(Icons.Default.Movie,null)},onClick={moreVisible=false;showLocalEdits=true})
        DropdownMenuItem(text={Text("Imágenes del proyecto")},leadingIcon={Icon(Icons.Default.PhotoLibrary,null)},onClick={moreVisible=false;projectLibrary.launch(arrayOf("image/*"))})
        DropdownMenuItem(text={Text("Abrir Alexia")},leadingIcon={Icon(Icons.Default.FolderSpecial,null)},onClick={moreVisible=false;try {
            val intent=context.packageManager.getLaunchIntentForPackage("com.axel.library") ?: Intent().apply {
                component=android.content.ComponentName("com.axel.library","com.axel.library.MainActivity")
                flags=Intent.FLAG_ACTIVITY_NEW_TASK
            };context.startActivity(intent)
        } catch (_:Exception) {scope.launch {snackbar.showSnackbar("No se pudo abrir Alexia")}}})
        }
        }
    })}) {padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(searchVisible || query.isNotBlank()) StudioTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space8),singleLine=true,
                placeholder={Text("Buscar por modelo o descripción")},shape=NexusTokens.field,
                trailingIcon={if(query.isNotBlank()) IconButton(onClick={query=""}) {Icon(Icons.Default.Close,"Borrar búsqueda")}})
            LazyRow(contentPadding=PaddingValues(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space8),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8),verticalAlignment=Alignment.CenterVertically) {
                item {StudioFilterChip(filter==null,{filter=null},label={Text("Todos")},shape=NexusTokens.filter)}
                items(listOf(Modality.IMAGE to "Imagen",Modality.VIDEO to "Vídeo",Modality.AUDIO to "Audio")) {(value,label) ->
                    StudioFilterChip(filter==value,{filter=value},label={Text(label)},shape=NexusTokens.filter)
                }
                // Origen como un único desplegable dentro de esta fila (sin etiquetas en las miniaturas).
                item {Box {
                    StudioFilterChip(originFilter!=LibraryOriginFilter.ALL,{originMenu=true},
                        label={Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(if(originFilter==LibraryOriginFilter.ALL) "Origen" else originFilter.shortLabel())
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
            if(completed.isEmpty() && pending.isEmpty() && localAssets.isEmpty()) Box(Modifier.fillMaxSize().padding(NexusTokens.space32),contentAlignment=Alignment.Center) {
                val filtered=query.isNotBlank() || filter!=null || originFilter!=LibraryOriginFilter.ALL || favoriteOnly || showHidden
                Text(if(filtered) "No hay resultados con estos filtros." else "Tus creaciones aparecerán aquí.",style=MaterialTheme.typography.bodyLarge,color=TextSecondary)
            } else LazyVerticalGrid(GridCells.Fixed(3),Modifier.fillMaxSize(),
                horizontalArrangement=Arrangement.spacedBy(NexusTokens.galleryGap),verticalArrangement=Arrangement.spacedBy(NexusTokens.galleryGap),
                contentPadding=PaddingValues(bottom=NexusTokens.space16)) {
                // Trabajos en curso: una línea por trabajo (modelo, estado y tiempo), no una tarjeta.
                items(pending,key={"pending:${it.id}"},span={GridItemSpan(maxLineSpan)}) {job -> PendingJobRow(job) {onNavigateToOutput(job.id)}}
                listOf(LibraryOriginFilter.IMPORTED,LibraryOriginFilter.EDITED,LibraryOriginFilter.SHARED,LibraryOriginFilter.GENERATED).forEach {origin ->
                    val assets=localAssetGroups[origin].orEmpty()
                    if(assets.isNotEmpty()) {
                        items(assets,key={"asset:"+it.id}) {asset ->
                            LocalAssetTile(asset,imageLoader,showDetails,onPreview={localPreviewTarget=asset},onActions={localTarget=asset})
                        }
                    }
                }
                groups.forEach {(day,entries) ->
                    item(span={GridItemSpan(maxLineSpan)}) {Text(day,style=MaterialTheme.typography.labelMedium,color=TextSecondary,
                        modifier=Modifier.padding(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space12))}
                    entries.forEach {job ->
                        items(job.outputs.size,key={index->"${job.id}:${job.outputs[index].id}:$index"}) {index ->
                            LibraryTile(job,job.outputs[index],conflicts,imageLoader,OutputFiles.token(job.outputs[index]) in favorites,showDetails,
                                onActions={actionTarget=job to job.outputs[index]}) {
                                if(onNavigateToResult!=null) onNavigateToResult(job.id,jobs.first {it.id==job.id}.outputs.indexOf(job.outputs[index])) else onNavigateToOutput(job.id)
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
private fun LibraryTile(job:GenerationJob,output:GenerationOutput,conflicts:Set<String>,imageLoader:ImageLoader,favorite:Boolean,showDetails:Boolean,onActions:()->Unit,onOpen:()->Unit) {
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
        if(favorite) Icon(Icons.Default.Star,"Favorito",Modifier.align(Alignment.TopStart).padding(NexusTokens.space8).size(18.dp),tint=MaterialTheme.colorScheme.primary)
        if(output.type in listOf(Modality.VIDEO,Modality.AUDIO)) TileMediaBadge(output.type==Modality.VIDEO,duration,Modifier.align(Alignment.TopEnd))
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
private fun PendingJobRow(job:GenerationJob,onOpen:()->Unit) {
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
        Text("${com.vidadomu.nexus.ui.components.generationStatusLabel(job)} · $elapsed",style=MaterialTheme.typography.labelMedium,color=TextSecondary,maxLines=1)
    }
}