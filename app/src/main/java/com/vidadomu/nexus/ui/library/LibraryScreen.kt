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
            Text("${completed.sumOf {it.outputs.size}+localAssets.size} archivos",style=MaterialTheme.typography.labelSmall,color=TextSecondary,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    },actions={
        IconButton(enabled=!importing,onClick={importPicker.launch(arrayOf("image/*","video/*","audio/*","application/pdf","text/*"))}) {Icon(Icons.Default.Add,"Importar archivos")}
        Box {
        IconButton(onClick={moreVisible=true}) {Icon(Icons.Default.MoreVert,"Más opciones de biblioteca",tint=TextSecondary)}
        DropdownMenu(expanded=moreVisible,onDismissRequest={moreVisible=false}) {
        DropdownMenuItem(text={Text("Buscar")},leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.semantics {contentDescription="Buscar en Biblioteca"},onClick={moreVisible=false;searchVisible=!searchVisible})
        DropdownMenuItem(text={Text("Ediciones locales")},leadingIcon={Icon(Icons.Default.Movie,null)},onClick={moreVisible=false;showLocalEdits=true})
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
                item {StudioFilterChip(favoriteOnly,{favoriteOnly=!favoriteOnly},label={Text("Favoritos")})}
                if(hidden.isNotEmpty() || showHidden) item {StudioFilterChip(showHidden,{showHidden=!showHidden},label={Text("Ocultos")})}
                item {
                    StudioFilterChip(false,{projectLibrary.launch(arrayOf("image/*"))},
                        label={Text("Imágenes del proyecto · Gestionar",maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)},
                        leadingIcon={Icon(Icons.Default.PhotoLibrary,null,Modifier.size(16.dp))},shape=NexusTokens.filter)
                }
            }
            LazyRow(contentPadding=PaddingValues(horizontal=NexusTokens.screenMargin),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8),verticalAlignment=Alignment.CenterVertically) {
                item {Text("Origen",style=MaterialTheme.typography.labelMedium,color=TextSecondary)}
                items(LibraryOriginFilter.values().toList(),key={it.name}) {origin ->
                    StudioFilterChip(originFilter==origin,{originFilter=origin},label={Text(origin.label)},shape=NexusTokens.filter)
                }
            }
            if(completed.isEmpty() && pending.isEmpty() && localAssets.isEmpty()) Box(Modifier.fillMaxSize().padding(NexusTokens.space32),contentAlignment=Alignment.Center) {
                Text(if(query.isNotBlank()) "No hay resultados." else "Tus creaciones aparecerán aquí.",style=MaterialTheme.typography.bodyLarge,color=TextSecondary)
            } else LazyVerticalGrid(GridCells.Fixed(3),Modifier.fillMaxSize(),
                horizontalArrangement=Arrangement.spacedBy(NexusTokens.galleryGap),verticalArrangement=Arrangement.spacedBy(NexusTokens.galleryGap),
                contentPadding=PaddingValues(bottom=NexusTokens.space16)) {
                if(pending.isNotEmpty()) item(span={GridItemSpan(maxLineSpan)}) {
                    Text("En curso · ${pending.size}",Modifier.padding(NexusTokens.screenMargin),style=MaterialTheme.typography.titleSmall)
                }
                items(pending,key={"pending:${it.id}"},span={GridItemSpan(maxLineSpan)}) {job ->
                    Column(Modifier.fillMaxWidth().padding(horizontal=NexusTokens.screenMargin).clickable {onNavigateToOutput(job.id)}) {
                        Text(job.prompt.ifBlank {"Preparando creación"},maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                        com.vidadomu.nexus.ui.components.GenerationProgressPanel(job,Modifier.fillMaxWidth(),compact=true)
                    }
                }
                listOf(LibraryOriginFilter.IMPORTED,LibraryOriginFilter.EDITED,LibraryOriginFilter.SHARED,LibraryOriginFilter.GENERATED).forEach {origin ->
                    val assets=localAssetGroups[origin].orEmpty()
                    if(assets.isNotEmpty()) {
                        items(assets,key={"asset:"+it.id}) {asset ->
                            LocalAssetTile(asset,imageLoader,onPreview={localPreviewTarget=asset},onActions={localTarget=asset})
                        }
                    }
                }
                groups.forEach {(day,entries) ->
                    item(span={GridItemSpan(maxLineSpan)}) {Text(day,style=MaterialTheme.typography.labelMedium,color=TextSecondary,
                        modifier=Modifier.padding(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space12))}
                    entries.forEach {job ->
                        items(job.outputs.size,key={index->"${job.id}:${job.outputs[index].id}:$index"}) {index ->
                            LibraryTile(job,job.outputs[index],conflicts,imageLoader,OutputFiles.token(job.outputs[index]) in favorites,
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
private fun LibraryTile(job:GenerationJob,output:GenerationOutput,conflicts:Set<String>,imageLoader:ImageLoader,favorite:Boolean,onActions:()->Unit,onOpen:()->Unit) {
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
        IconButton(onClick=onActions,modifier=Modifier.align(Alignment.TopEnd).size(NexusTokens.minimumTouch)) {
            Surface(color=MaterialTheme.colorScheme.scrim.copy(alpha=.55f),shape=NexusTokens.filter) {
                Icon(Icons.Default.MoreVert,"Opciones del archivo",Modifier.size(22.dp),tint=MediaForeground)
            }
        }
        if(favorite) Icon(Icons.Default.Star,"Favorito",Modifier.align(Alignment.TopStart).padding(NexusTokens.space8).size(18.dp),tint=MaterialTheme.colorScheme.primary)
        Surface(color=MaterialTheme.colorScheme.scrim.copy(alpha=.72f),contentColor=MediaForeground,shape=NexusTokens.filter,
            modifier=Modifier.align(Alignment.BottomStart).padding(NexusTokens.space4)) {
            Text("Generado",Modifier.padding(horizontal=NexusTokens.space8,vertical=NexusTokens.space4),style=MaterialTheme.typography.labelMedium)
        }
        if(output.type in listOf(Modality.VIDEO,Modality.AUDIO)) Surface(color=MaterialTheme.colorScheme.scrim.copy(alpha=.78f),contentColor=MediaForeground,
            shape=NexusTokens.thumbnail,modifier=Modifier.align(Alignment.BottomEnd).padding(NexusTokens.space4)) {
            Row(Modifier.padding(NexusTokens.space4),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                Icon(if(output.type==Modality.VIDEO) Icons.Default.PlayArrow else Icons.Default.GraphicEq,null,Modifier.size(NexusTokens.space16))
                if(duration!=null) Text(duration,style=MaterialTheme.typography.labelMedium.tabularMoney())
            }
        }
    }
}
