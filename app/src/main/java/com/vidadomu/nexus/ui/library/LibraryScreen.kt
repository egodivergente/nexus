package com.vidadomu.nexus.ui.library

import com.vidadomu.nexus.ui.components.NexusTopAppBar as TopAppBar
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.components.StudioFilterChip
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.*
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Intent
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.assistant.rememberProjectMediaPicker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    val runtime=remember(app) {AssistantRuntime.get(app)}
    val index by runtime.assets.changes.collectAsState()
    val projectLibrary=rememberProjectMediaPicker(managementOnly=true) {}
    LaunchedEffect(jobs) {kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {runtime.importHistory()}}
    val removed=remember(index) {index.assets.filter {it.libraryDeleted}.map {it.uri}.toSet()}
    var query by rememberSaveable {mutableStateOf("")}
    var searchVisible by rememberSaveable {mutableStateOf(false)}
    var filter by rememberSaveable {mutableStateOf<Modality?>(null)}
    val completed=remember(jobs,filter,query,removed) {jobs.map {job -> job.copy(outputs=job.outputs.filter {it.localUri !in removed && it.url !in removed})}.filter {it.status==GenerationStatus.COMPLETED && it.outputs.isNotEmpty()}
        .filter {filter==null || it.modality==filter}.filter {matchesCreationSearch(it,query)}.sortedByDescending {it.startedAt}}
    val conflicts=remember(jobs) {OutputFiles.conflictingLocalUris(jobs.flatMap {it.outputs})}
    val groups=remember(completed) {completed.groupBy {SimpleDateFormat("d MMMM yyyy",Locale("es","ES")).format(Date(it.startedAt))}}
    val imageLoader=remember(context) {ImageLoader.Builder(context).components {add(VideoFrameDecoder.Factory())}
        .decoderDispatcher(kotlinx.coroutines.Dispatchers.IO.limitedParallelism(2))
        .memoryCache {coil.memory.MemoryCache.Builder(context).maxSizePercent(.08).build()}.build()}
    DisposableEffect(imageLoader) {onDispose {imageLoader.shutdown()}}
    Scaffold(containerColor=ScreenBackground,topBar={TopAppBar(title={Text("Biblioteca",style=MaterialTheme.typography.titleLarge)},actions={
        IconButton(onClick={try {
            val intent=context.packageManager.getLaunchIntentForPackage("com.axel.library") ?: Intent().apply {
                component=android.content.ComponentName("com.axel.library","com.axel.library.MainActivity")
                flags=Intent.FLAG_ACTIVITY_NEW_TASK
            };context.startActivity(intent)
        } catch (_:Exception) {}}) {Icon(Icons.Default.FolderSpecial,"Abrir Alexia",tint=TextSecondary)}
    })}) {padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FilledTonalButton(onClick={projectLibrary.launch(arrayOf("image/*"))},modifier=Modifier.fillMaxWidth().padding(horizontal=NexusTokens.screenMargin)) {
                Icon(Icons.Default.PhotoLibrary,null)
                Spacer(Modifier.width(NexusTokens.space8))
                Text("Imágenes del proyecto · Gestionar")
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=NexusTokens.screenMargin),verticalAlignment=Alignment.CenterVertically) {
                Text("${completed.sumOf {it.outputs.size}} archivos · ${completed.size} creaciones",style=MaterialTheme.typography.labelMedium,color=TextSecondary,modifier=Modifier.weight(1f))
                IconButton(onClick={searchVisible=!searchVisible}) {Icon(Icons.Default.Search,"Buscar en Biblioteca")}
            }
            if(searchVisible || query.isNotBlank()) StudioTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=NexusTokens.screenMargin),singleLine=true,
                placeholder={Text("Buscar por modelo o descripción")},shape=NexusTokens.field,
                trailingIcon={if(query.isNotBlank()) IconButton(onClick={query=""}) {Icon(Icons.Default.Close,"Borrar búsqueda")}})
            LazyRow(contentPadding=PaddingValues(horizontal=NexusTokens.screenMargin),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                item {StudioFilterChip(filter==null,{filter=null},label={Text("Todos")},shape=NexusTokens.filter)}
                items(listOf(Modality.IMAGE to "Imagen",Modality.VIDEO to "Vídeo",Modality.AUDIO to "Audio")) {(value,label) ->
                    StudioFilterChip(filter==value,{filter=value},label={Text(label)},shape=NexusTokens.filter)
                }
            }
            if(completed.isEmpty()) Box(Modifier.fillMaxSize().padding(NexusTokens.space32),contentAlignment=Alignment.Center) {
                Text(if(query.isNotBlank()) "No hay resultados." else "Tus creaciones aparecerán aquí.",style=MaterialTheme.typography.bodyLarge,color=TextSecondary)
            } else LazyVerticalGrid(GridCells.Fixed(3),Modifier.fillMaxSize(),
                horizontalArrangement=Arrangement.spacedBy(NexusTokens.galleryGap),verticalArrangement=Arrangement.spacedBy(NexusTokens.galleryGap),
                contentPadding=PaddingValues(bottom=NexusTokens.space16)) {
                groups.forEach {(day,entries) ->
                    item(span={GridItemSpan(maxLineSpan)}) {Text(day,style=MaterialTheme.typography.labelMedium,color=TextSecondary,
                        modifier=Modifier.padding(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space12))}
                    entries.forEach {job ->
                        items(job.outputs.size,key={index->"${job.id}:${job.outputs[index].id}:$index"}) {index ->
                            LibraryTile(job,job.outputs[index],conflicts,imageLoader) {
                                if(onNavigateToResult!=null) onNavigateToResult(job.id,jobs.first {it.id==job.id}.outputs.indexOf(job.outputs[index])) else onNavigateToOutput(job.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTile(job:GenerationJob,output:GenerationOutput,conflicts:Set<String>,imageLoader:ImageLoader,onOpen:()->Unit) {
    val context=LocalContext.current
    val interaction=remember {MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .96f else 1f,tween(120),label="Media press")
    val duration=mediaDuration(output.durationSeconds)
    val type=when(output.type) {Modality.VIDEO->"Vídeo";Modality.AUDIO->"Audio";Modality.IMAGE->"Imagen";else->"Texto"}
    Box(Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer {scaleX=scale;scaleY=scale}.background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(interactionSource=interaction,indication=null,role=Role.Button,onClickLabel="Ver resultado",onClick=onOpen)
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
        if(output.type in listOf(Modality.VIDEO,Modality.AUDIO)) Surface(color=MaterialTheme.colorScheme.scrim.copy(alpha=.78f),contentColor=MediaForeground,
            shape=NexusTokens.thumbnail,modifier=Modifier.align(Alignment.BottomEnd).padding(NexusTokens.space4)) {
            Row(Modifier.padding(NexusTokens.space4),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                Icon(if(output.type==Modality.VIDEO) Icons.Default.PlayArrow else Icons.Default.GraphicEq,null,Modifier.size(NexusTokens.space16))
                if(duration!=null) Text(duration,style=MaterialTheme.typography.labelMedium.tabularMoney())
            }
        }
    }
}
