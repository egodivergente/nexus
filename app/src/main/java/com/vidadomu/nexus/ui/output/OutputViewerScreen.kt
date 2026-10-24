package com.vidadomu.nexus.ui.output

import com.vidadomu.nexus.ui.components.NexusTopAppBar as TopAppBar
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.media3.common.PlaybackException
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.vidadomu.nexus.core.model.OutputFiles
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.ui.components.GenerationProgressPanel
import com.vidadomu.nexus.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutputViewerScreen(jobId:String,onBack:()->Unit,onReuseAsInput:(String, String?)->Unit,initialOutputIndex:Int=0,onOpenHistory:(()->Unit)?=null,onOpenFlow:(()->Unit)?=null) {
    val context=LocalContext.current
    val app=context.applicationContext as NexusApplication
    val jobs by app.generationRepository.allJobs.collectAsState()
    var selection by remember(jobId,initialOutputIndex) {mutableStateOf(jobId to initialOutputIndex.coerceAtLeast(0))}
    val job=remember(jobs,selection.first) {jobs.find {it.id==selection.first}}
    val selectedOutput=selection.second
    val libraryPrefs=remember {context.getSharedPreferences("library_actions_v1",0)}
    val assetIndex by com.vidadomu.nexus.assistant.AssistantRuntime.get(app).assets.changes.collectAsState()
    val removed=remember(assetIndex) {assetIndex.assets.filter {it.libraryDeleted}.map {it.uri}.toSet()}
    val gallery=remember(jobs,removed) {
        val hidden=libraryPrefs.getStringSet("hidden",emptySet()).orEmpty()
        jobs.filter {it.status==GenerationStatus.COMPLETED}.sortedByDescending {it.startedAt}.flatMap {j ->
            j.outputs.mapIndexedNotNull {i,o -> if(o.type==Modality.IMAGE && o.localUri !in removed && o.url !in removed && OutputFiles.token(o) !in hidden) j.id to i else null}
        }
    }
    val galleryIndex=gallery.indexOf(selection)
    val previous=gallery.getOrNull(galleryIndex-1).takeIf {galleryIndex>0}
    val next=gallery.getOrNull(galleryIndex+1).takeIf {galleryIndex>=0}
    val movePhoto:(Int)->Unit={direction -> (if(direction<0) previous else next)?.let {selection=it}}
    val currencyManager=app.currencyManager
    val currencyState by currencyManager.state.collectAsState()
    val exporter:OutputExportViewModel=androidx.lifecycle.viewmodel.compose.viewModel()
    val export by exporter.state.collectAsState()
    val snackbar=remember {SnackbarHostState()}
    val savePicker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        result->exporter.save(if(result.resultCode==android.app.Activity.RESULT_OK) result.data?.data else null)
    }
    LaunchedEffect(export.message) {export.message?.let {snackbar.showSnackbar(it);exporter.dismissMessage()}}
    var details by remember(jobId) {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    var refreshingFlow by remember(jobId) {mutableStateOf(false)}
    val refreshExistingFlow: suspend () -> Unit = {
        if(!refreshingFlow && !BuildConfig.OFFLINE_QA) {
            refreshingFlow=true
            try {app.generationRepository.pollJob(jobId,refreshFailed=true)} finally {refreshingFlow=false}
        }
    }
    val existingFlow=job?.let {it.providerId=="google_flow" && it.providerJobId?.matches(Regex("[as]\\.[A-Za-z0-9_-]{16,96}"))==true &&
        it.status !in setOf(GenerationStatus.COMPLETED,GenerationStatus.FAILED,GenerationStatus.CANCELLED)}==true
    LaunchedEffect(job?.id) {if(existingFlow) refreshExistingFlow()}
    val chosenOutput=job?.outputs?.getOrNull(selectedOutput) ?: job?.outputs?.firstOrNull()
    val conflicts=remember(jobs) {OutputFiles.conflictingLocalUris(jobs.flatMap {it.outputs})}
    val trustedLocal=chosenOutput?.let {OutputFiles.trustedLocalUri(it,conflicts)}
    var alexiaFailed by remember(chosenOutput?.id) {mutableStateOf(false)}
    val title=when(job?.modality) {Modality.IMAGE->"Imagen";Modality.VIDEO->"Vídeo";Modality.AUDIO->"Audio";Modality.CHAT->"Texto";else->"Resultado"}
    Scaffold(containerColor=ScreenBackground,snackbarHost={SnackbarHost(snackbar)},topBar={TopAppBar(
        title={Text(title,style=com.vidadomu.nexus.ui.components.nexusScreenTitleStyle())},navigationIcon={IconButton(onClick=onBack) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"Atrás")}})
    }) {padding ->
        if(job==null) {Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center) {Text("Generación no encontrada",color=TextSecondary)};return@Scaffold}
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            val output=chosenOutput
            var splitCompareMode by remember(job.id, selectedOutput) { mutableStateOf(false) }
            val compareReferenceUri = remember(job, selectedOutput) {
                job.inputMediaUri?.takeIf { it.isNotBlank() && job.modality == Modality.IMAGE }
                    ?: job.outputs.getOrNull(if (selectedOutput == 0) 1 else 0)?.takeIf { it.type == Modality.IMAGE }?.let { OutputFiles.trustedLocalUri(it, conflicts) ?: it.url }
            }
            if(job.outputs.size>1 || compareReferenceUri != null) androidx.compose.foundation.lazy.LazyRow(contentPadding=PaddingValues(horizontal=NexusTokens.screenMargin),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                items(job.outputs.size) {index->FilterChip(selectedOutput==index && !splitCompareMode,{splitCompareMode=false;selection=job.id to index},label={Text("Resultado ${index+1}")},shape=NexusTokens.filter)}
                if(job.modality==Modality.IMAGE && compareReferenceUri != null) {
                    item {
                        FilterChip(
                            selected = splitCompareMode,
                            onClick = { splitCompareMode = !splitCompareMode },
                            leadingIcon = { Icon(Icons.Default.Compare, null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Cortinilla A/B") },
                            shape = NexusTokens.filter
                        )
                    }
                }
            }
            val ratio=output?.let {o->if(o.width!=null && o.height!=null && o.width>0 && o.height>0) o.width.toFloat()/o.height else null}
            Box(Modifier.fillMaxWidth().aspectRatio((ratio ?: if(job.modality==Modality.VIDEO) 16f/9f else 1f).coerceIn(.55f,2f)).background(SurfaceDark),contentAlignment=Alignment.Center) {
                if(output!=null) key(output.id, splitCompareMode) {when(job.modality) {
                    Modality.IMAGE-> if (splitCompareMode && compareReferenceUri != null) {
                        SplitWipeCompare(
                            leftUri = compareReferenceUri,
                            rightUri = trustedLocal ?: output.url,
                            leftLabel = if (job.inputMediaUri != null) "Original" else "Variante A",
                            rightLabel = "Resultado ${selectedOutput + 1}"
                        )
                    } else {
                        ZoomableImageViewer(output.url,trustedLocal,onNavigate=movePhoto,onOpenHistory=onOpenHistory)
                    }
                    Modality.VIDEO->NativeVideoPlayer(trustedLocal ?: output.url, onCaptureFrame = { frameUri -> onReuseAsInput(frameUri.toString(), "image/png") })
                    Modality.AUDIO->NativeAudioPlayer(trustedLocal ?: output.url)
                    Modality.CHAT->TextOutputViewer(output.url)
                }} else if(job.status !in setOf(GenerationStatus.COMPLETED,GenerationStatus.FAILED,GenerationStatus.CANCELLED)) {
                    Column(Modifier.fillMaxWidth().padding(NexusTokens.screenMargin)) {
                        GenerationProgressPanel(job,Modifier.fillMaxWidth())
                        if(existingFlow) TextButton(enabled=!refreshingFlow,onClick={scope.launch {refreshExistingFlow()}}) {
                            Text(if(refreshingFlow) "Consultando Flow…" else "Consultar Flow")
                        }
                    }
                } else com.vidadomu.nexus.ui.components.InlineNotice(
                    job.error?.takeIf {it.isNotBlank()} ?: "No hay un resultado disponible.",
                    Modifier.padding(NexusTokens.screenMargin),technicalDetails=job.technicalDetails,
                    actionLabel=if(job.providerId=="google_flow" && onOpenFlow!=null) "Conexión de Flow" else null,
                    onAction=onOpenFlow.takeIf {job.providerId=="google_flow"})
            }
            if(output?.type==Modality.IMAGE && galleryIndex>=0) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                IconButton(enabled=previous!=null,onClick={movePhoto(-1)}) {Icon(Icons.Default.ChevronLeft,"Imagen anterior")}
                Text("${galleryIndex+1} / ${gallery.size}",style=MaterialTheme.typography.labelLarge)
                IconButton(enabled=next!=null,onClick={movePhoto(1)}) {Icon(Icons.Default.ChevronRight,"Imagen siguiente")}
            }
            if(output!=null) {
                Row(Modifier.fillMaxWidth().padding(horizontal=NexusTokens.screenMargin),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    if(job.modality==Modality.IMAGE) ViewerAction(Icons.Default.MovieFilter,"Animar",Modifier.weight(1f)) {onReuseAsInput(trustedLocal ?: output.url, output.mimeType.takeIf { it.startsWith("image/") } ?: "application/octet-stream")}
                    ViewerAction(Icons.Default.Replay,"Remezclar",Modifier.weight(1f)) {onReuseAsInput(job.prompt, null)}
                    if(output.url.startsWith("https://") || output.url.startsWith("http://")) ViewerAction(Icons.Default.Share,"Compartir",Modifier.weight(1f)) {shareMedia(context,output.url,job.prompt)}
                    ViewerAction(Icons.Default.FolderSpecial,"Alexia",Modifier.weight(1f),"Abrir Alexia") {alexiaFailed=!launchAlexia(context)}
                }
                // Aviso que no desaparece solo: el botón nunca queda inerte y se ofrece una salida que no descarga ni envía nada.
                if(alexiaFailed) com.vidadomu.nexus.ui.components.InlineNotice(alexiaUnavailableMessage(trustedLocal!=null),
                    Modifier.padding(horizontal=NexusTokens.screenMargin),
                    actionLabel=OPEN_WITH_LABEL.takeIf {trustedLocal!=null},
                    onAction=if(trustedLocal==null) null else ({
                        if(!openLocalCopyWithAnotherApp(context,trustedLocal,output.mimeType.ifBlank {when(output.type) {Modality.IMAGE->"image/*";Modality.VIDEO->"video/*";Modality.AUDIO->"audio/*";Modality.CHAT->"text/plain"}})) scope.launch {snackbar.showSnackbar("Ninguna app puede abrir esta copia. Sigue disponible en Nexus.")}
                    }))
                Button(enabled=!export.busy,modifier=Modifier.fillMaxWidth().padding(horizontal=NexusTokens.screenMargin).heightIn(min=NexusTokens.minimumTouch),shape=NexusTokens.field,
                    onClick={val spec=outputExportSpec(output);exporter.prepare(output,trustedLocal);savePicker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE);type=spec.mimeType;putExtra(Intent.EXTRA_TITLE,spec.fileName)
                    })}) {Icon(Icons.Default.Download,null);Spacer(Modifier.width(NexusTokens.space8));Text(if(export.busy) "Guardando archivo…" else "Descargar archivo")}
                if(export.busy) Row(Modifier.fillMaxWidth().padding(horizontal=NexusTokens.screenMargin),verticalAlignment=Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(NexusTokens.space24),strokeWidth=2.dp)
                    Text("${export.bytes/1024} KB",Modifier.weight(1f).padding(horizontal=NexusTokens.space12))
                    TextButton(onClick=exporter::cancel) {Text("Cancelar",color=TextSecondary)}
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal=NexusTokens.screenMargin),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                TextButton(onClick={details=!details},colors=ButtonDefaults.textButtonColors(contentColor=TextSecondary)) {
                    Text(if(details) "Ocultar detalles" else "Detalles");Icon(if(details) Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)
                }
                if(details) {
                    Text(job.prompt.ifBlank {"Sin instrucción textual"},style=MaterialTheme.typography.bodyLarge)
                    output?.let { OutputMetadataDetails(job, it, trustedLocal) }
                    val cost=remember(job,currencyState) {com.vidadomu.nexus.ui.components.confirmedJobCost(job,currencyManager)}
                    ViewerDetail("Coste",cost)
                    job.actualCostUsd?.let {ViewerDetail("Importe original",currencyManager.formatUsdDetails(it))}
                    ViewerDetail("Disponibilidad",if(trustedLocal!=null) "Copia local registrada. Consulta su disponibilidad en Alexia." else "El resultado depende del enlace del proveedor.")
                    job.parameters.filterKeys {!it.startsWith("__")}.forEach {(key,value)->ViewerDetail(key,value)}
                    ViewerDetail("ID",job.id)
                    job.providerJobId?.let {ViewerDetail("ID del proveedor",it)}
                }
            }
            Spacer(Modifier.height(NexusTokens.space24))
        }
    }
}

@Composable
private fun ViewerAction(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,modifier:Modifier=Modifier,description:String=label,onClick:()->Unit) {
    Column(modifier.clickable(role=androidx.compose.ui.semantics.Role.Button,onClick=onClick)
        .semantics(mergeDescendants=true) {contentDescription=description}.heightIn(min=NexusTokens.minimumTouch)
        .padding(vertical=NexusTokens.space8),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
        Icon(icon,null,tint=TextSecondary)
        Text(label,style=MaterialTheme.typography.labelMedium,color=TextSecondary,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ViewerDetail(label:String,value:String) {
    Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
        Text(label,style=MaterialTheme.typography.labelMedium,color=TextSecondary)
        androidx.compose.foundation.text.selection.SelectionContainer {Text(value,style=MaterialTheme.typography.bodyMedium)}
    }
}

@Composable
internal fun ZoomableImageViewer(imageUrl: String, localUri: String? = null, onNavigate: ((Int)->Unit)? = null, onOpenHistory: (() -> Unit)? = null) {
    val navigate by rememberUpdatedState(onNavigate)
    var scale by remember(imageUrl, localUri) { mutableStateOf(1f) }
    var offsetX by remember(imageUrl, localUri) { mutableStateOf(0f) }
    var offsetY by remember(imageUrl, localUri) { mutableStateOf(0f) }
    var viewportSize by remember(imageUrl,localUri) {mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)}
    var imageSize by remember(imageUrl,localUri) {mutableStateOf(androidx.compose.ui.geometry.Size.Unspecified)}

    fun clampPan(targetScale:Float,x:Float,y:Float):androidx.compose.ui.geometry.Offset {
        val viewportWidth=viewportSize.width.toFloat().coerceAtLeast(1f)
        val viewportHeight=viewportSize.height.toFloat().coerceAtLeast(1f)
        val sourceWidth=imageSize.width.takeIf {it.isFinite() && it>0f} ?: viewportWidth
        val sourceHeight=imageSize.height.takeIf {it.isFinite() && it>0f} ?: viewportHeight
        val fit=minOf(viewportWidth/sourceWidth,viewportHeight/sourceHeight)
        val maxX=((sourceWidth*fit*targetScale-viewportWidth)/2f).coerceAtLeast(0f)
        val maxY=((sourceHeight*fit*targetScale-viewportHeight)/2f).coerceAtLeast(0f)
        return androidx.compose.ui.geometry.Offset(x.coerceIn(-maxX,maxX),y.coerceIn(-maxY,maxY))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {viewportSize=it}
            .pointerInput(imageUrl,localUri,scale,viewportSize,imageSize) {
                detectTapGestures(onDoubleTap={
                    val target=if(scale>1f) 1f else 2f
                    scale=target
                    if(target==1f) {offsetX=0f;offsetY=0f}
                    else clampPan(target,offsetX,offsetY).let {offsetX=it.x;offsetY=it.y}
                })
            }
            .pointerInput(imageUrl,localUri,viewportSize,imageSize) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed=false)
                    var horizontal=0f;var vertical=0f;var transformed=false
                    do {
                        val event=awaitPointerEvent()
                        val zoom=event.calculateZoom()
                        val pan=event.calculatePan()
                        if(event.changes.count {it.pressed}>1 || scale>1f) {
                            transformed=true
                            val nextScale=(scale*zoom).coerceIn(1f,5f)
                            scale=nextScale
                            if(nextScale>1f) clampPan(nextScale,offsetX+pan.x,offsetY+pan.y).let {offsetX=it.x;offsetY=it.y}
                            else {offsetX=0f;offsetY=0f}
                            event.changes.forEach {it.consume()}
                        } else {
                            horizontal+=pan.x;vertical+=pan.y
                            if(kotlin.math.abs(horizontal)>viewConfiguration.touchSlop && kotlin.math.abs(horizontal)>kotlin.math.abs(vertical)) event.changes.forEach {it.consume()}
                        }
                    } while(event.changes.any {it.pressed})
                    if(!transformed && kotlin.math.abs(horizontal)>80.dp.toPx() && kotlin.math.abs(horizontal)>kotlin.math.abs(vertical)*1.5f)
                        navigate?.invoke(if(horizontal>0) -1 else 1)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        var failed by remember(imageUrl,localUri) { mutableStateOf(false) }
        var loading by remember(imageUrl,localUri) { mutableStateOf(true) }
        var retry by remember(imageUrl,localUri) { mutableIntStateOf(0) }
        var useLocal by remember(imageUrl,localUri) { mutableStateOf(localUri!=null) }
        key(imageUrl,localUri,retry,useLocal) {
            AsyncImage(model=if(useLocal) localUri else imageUrl,
                contentDescription="Resultado generado. Pellizca para ampliar; doble toque para ajustar.",
                modifier=Modifier.fillMaxSize().testTag("output_image").graphicsLayer(scaleX=scale,scaleY=scale,translationX=offsetX,translationY=offsetY),
                contentScale=ContentScale.Fit,
                onLoading={loading=true;failed=false},onSuccess={result->
                    loading=false;failed=false
                    imageSize=result.painter.intrinsicSize
                    clampPan(scale,offsetX,offsetY).let {offsetX=it.x;offsetY=it.y}
                },
                onError={if(useLocal){useLocal=false}else{loading=false;failed=true}})
        }
        if(loading && !failed) CircularProgressIndicator(Modifier.testTag("output_loading"))
        if(failed) Column(Modifier.padding(NexusTokens.space24),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.BrokenImage,null,modifier=Modifier.size(40.dp),tint=TextSecondary)
            Text("No se puede cargar la imagen",style=MaterialTheme.typography.titleMedium)
            Text("El enlace puede haber caducado o no estar disponible. La tarea sigue guardada en Historial.",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
            OutlinedButton(onClick={failed=false;loading=true;retry++}) {Text("Volver a intentar")}
            // El visor oculta la barra inferior: acceso directo a la pestaña que menciona el aviso.
            onOpenHistory?.let { open -> TextButton(onClick=open) {Text("Abrir Historial")} }
        }
        if(scale>1f) Surface(Modifier.align(Alignment.BottomEnd).padding(NexusTokens.space16),
            shape=NexusTokens.filter,color=MaterialTheme.colorScheme.scrim.copy(alpha=.72f)) {
            TextButton(onClick={scale=1f;offsetX=0f;offsetY=0f}) {
                Text("Ajustar · ${(scale*100).toInt()}%",color=MediaForeground)
            }
        }
    }
}

@Composable
internal fun NativeVideoPlayer(videoUrl: String, onCaptureFrame: ((Uri) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var capturingFrame by remember(videoUrl) { mutableStateOf(false) }
    var playbackError by remember(videoUrl) {mutableStateOf<String?>(null)}
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(object:Player.Listener {override fun onPlayerError(error:PlaybackException) {playbackError=error.message ?: error.errorCodeName}})
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(Modifier.fillMaxSize()) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { it.player = exoPlayer },
        modifier = Modifier.fillMaxSize()
    )
    if (onCaptureFrame != null) {
        Surface(
            shape = NexusTokens.pill,
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(NexusTokens.space12)
        ) {
            TextButton(
                enabled = !capturingFrame,
                onClick = {
                    val posMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    exoPlayer.pause()
                    capturingFrame = true
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                        val captured = try { captureVideoFrameToLocalUri(context, videoUrl, posMs) }
                            finally { capturingFrame = false }
                        if (captured != null) {
                            android.widget.Toast.makeText(context, "Fotograma extraído; abriendo Lienzo", android.widget.Toast.LENGTH_SHORT).show()
                            onCaptureFrame(captured)
                        } else {
                            android.widget.Toast.makeText(context, "No se pudo extraer el fotograma", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MediaForeground, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (capturingFrame) "Extrayendo…" else "Fotograma → Lienzo", style = MaterialTheme.typography.labelMedium, color = MediaForeground)
            }
        }
    }
    playbackError?.let {com.vidadomu.nexus.ui.components.InlineNotice("No se puede reproducir el vídeo.",Modifier.align(Alignment.BottomCenter).padding(NexusTokens.screenMargin),it,"Reintentar",{playbackError=null;exoPlayer.prepare()})}
    }
}

@Composable
internal fun NativeAudioPlayer(audioUrl: String) {
    val context = LocalContext.current
    var playbackError by remember(audioUrl) {mutableStateOf<String?>(null)}
    var isPlaying by remember(audioUrl) { mutableStateOf(false) }
    var currentPosition by remember(audioUrl) { mutableStateOf(0L) }
    var duration by remember(audioUrl) { mutableStateOf(1L) }

    val exoPlayer = remember(audioUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(audioUrl)))
            prepare()
            addListener(object : Player.Listener {
                override fun onPlayerError(error:PlaybackException) {playbackError=error.message ?: error.errorCodeName}
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        duration = this@apply.duration.coerceAtLeast(1L)
                    }
                }
            })
        }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            delay(500)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NexusTokens.space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(SecondaryViolet.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = SecondaryVioletLight, modifier = Modifier.size(36.dp))
        }

        playbackError?.let {com.vidadomu.nexus.ui.components.InlineNotice("No se puede reproducir el audio.",technicalDetails=it,actionLabel="Reintentar",onAction={playbackError=null;exoPlayer.prepare()})}
        Spacer(modifier = Modifier.height(NexusTokens.space16))

        Slider(
            value = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
            onValueChange = { ratio ->
                val newPos = (ratio * duration).toLong()
                currentPosition = newPos
                exoPlayer.seekTo(newPos)
            },
            colors = SliderDefaults.colors(thumbColor = PrimaryNeon, activeTrackColor = PrimaryNeon)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(com.vidadomu.nexus.ui.library.mediaDuration(currentPosition/1000f).orEmpty(), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(com.vidadomu.nexus.ui.library.mediaDuration(duration/1000f).orEmpty(), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }

        Spacer(modifier = Modifier.height(NexusTokens.space12))

        IconButton(
            onClick = {
                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
            },
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(PrimaryNeon)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if(isPlaying) "Pausar audio" else "Reproducir audio",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
internal fun TextOutputViewer(text: String) {
    val clipboardManager = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NexusTokens.space16),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
            modifier = Modifier.verticalScroll(rememberScrollState())
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = { clipboardManager.setText(AnnotatedString(text)) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(NexusTokens.space8))
                Text("Copiar")
            }
        }
    }
}

private fun shareMedia(context: Context, url: String, prompt: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Creación con Nexus AI")
        putExtra(Intent.EXTRA_TEXT, "Generado con Nexus AI Hub:\n$prompt\n\nURL: $url")
    }
    context.startActivity(Intent.createChooser(shareIntent, "Compartir con Nexus"))
}
