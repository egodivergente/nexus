package com.vidadomu.nexus.ui.output

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.ui.components.GenerationProgressPanel
import com.vidadomu.nexus.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun OutputViewerScreen(
    jobId: String,
    onBack: () -> Unit,
    onReuseAsInput: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val jobs by app.generationRepository.allJobs.collectAsState()
    val job = remember(jobs, jobId) { jobs.find { it.id == jobId } }
    val currencyManager = app.currencyManager
    val exporter: OutputExportViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val export by exporter.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val savePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result -> exporter.save(if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.data else null) }
    LaunchedEffect(export.message) {
        export.message?.let { snackbar.showSnackbar(it); exporter.dismissMessage() }
    }

    val titleText = when (job?.modality) {
        Modality.IMAGE -> "IMAGEN"
        Modality.VIDEO -> "VÍDEO"
        Modality.AUDIO -> "AUDIO"
        Modality.CHAT -> "CHAT"
        null -> "GENERACIÓN"
    }

    var selectedOutput by remember(job?.id) { mutableStateOf(0) }
    val chosenOutput=job?.outputs?.getOrNull(selectedOutput) ?: job?.outputs?.firstOrNull()
    val conflicts = remember(job?.outputs) { OutputFiles.conflictingLocalUris(job?.outputs.orEmpty()) }
    val trustedLocal = chosenOutput?.let { OutputFiles.trustedLocalUri(it, conflicts) }
    Scaffold(
        containerColor = ScreenBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    val outputUrl = chosenOutput?.url
                    if (outputUrl != null && (outputUrl.startsWith("https://") || outputUrl.startsWith("http://"))) {
                        IconButton(onClick = { shareMedia(context, outputUrl, job?.prompt.orEmpty()) }) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir enlace", tint = PrimaryNeon)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        }
    ) { innerPadding ->
        if (job == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Generación no encontrada", color = TextSecondary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val output = chosenOutput
            if(job.outputs.size>1) {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(job.outputs.size) { index ->
                        FilterChip(selected=selectedOutput==index,onClick={selectedOutput=index},label={Text("Resultado ${index+1}")})
                    }
                }
            }

            // 1. Output Media Canvas Viewer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                if (output != null) {
                    key(output.id) { when (job.modality) {
                        Modality.IMAGE -> {
                            ZoomableImageViewer(imageUrl = output.url, localUri = trustedLocal)
                        }
                        Modality.VIDEO -> {
                            NativeVideoPlayer(videoUrl = trustedLocal ?: output.url)
                        }
                        Modality.AUDIO -> {
                            NativeAudioPlayer(audioUrl = trustedLocal ?: output.url)
                        }
                        Modality.CHAT -> {
                            TextOutputViewer(text = output.url)
                        }
                    }
                    }
                } else if (job.status !in setOf(
                        GenerationStatus.COMPLETED,
                        GenerationStatus.FAILED,
                        GenerationStatus.CANCELLED
                    )) {
                    GenerationProgressPanel(
                        job = job,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    )
                } else {
                    Text(
                        text = job.error ?: "Generación completada sin resultado",
                        color = AccentCoral,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 2. Contextual Actions Pill Row
            if (output != null) {
                Button(
                    enabled = !export.busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp),
                    onClick = {
                        val spec = outputExportSpec(output)
                        exporter.prepare(output, trustedLocal)
                        savePicker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = spec.mimeType
                            putExtra(Intent.EXTRA_TITLE, spec.fileName)
                        })
                    }
                ) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (export.busy) "Guardando archivo…" else "Descargar archivo")
                }
                if (export.busy) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("${export.bytes / 1024} KB", Modifier.weight(1f).padding(horizontal = 12.dp))
                    TextButton(onClick = exporter::cancel) { Text("Cancelar") }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (job.modality == Modality.IMAGE) {
                        Button(
                            onClick = { onReuseAsInput(trustedLocal ?: output.url) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceVariantDark,
                                contentColor = PrimaryNeon
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.MovieFilter, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Animar", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                        }
                    }

                    Button(
                        onClick = { onReuseAsInput(job.prompt) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceVariantDark,
                            contentColor = SecondaryVioletLight
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remezclar", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                    }

                    if (output.url.startsWith("https://") || output.url.startsWith("http://")) {
                        Button(
                            onClick = { shareMedia(context, output.url, job.prompt) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceVariantDark,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartir enlace", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                        }
                    }
                }

                // Alexia Classification Badge
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryNeon.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = null,
                                tint = PrimaryNeon,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if(trustedLocal != null) "Copia local registrada" else "Sin copia local confirmada",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                val subfolder = when (job.modality) {
                                    Modality.IMAGE -> "Nexus / Imágenes"
                                    Modality.VIDEO -> "Nexus / Vídeos"
                                    Modality.AUDIO -> "Nexus / Audio"
                                    Modality.CHAT -> "Nexus / Chat"
                                }
                                Text(
                                    text = if(trustedLocal != null) "Consulta su disponibilidad en Alexia" else "El resultado depende del enlace del proveedor",
                                    style = MaterialTheme.typography.bodySmall.copy(color = PrimaryNeon, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                try {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.axel.library")
                                        ?: android.content.Intent().apply {
                                            component = android.content.ComponentName("com.axel.library", "com.axel.library.MainActivity")
                                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                    context.startActivity(launchIntent)
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryNeon.copy(alpha = 0.15f),
                                contentColor = PrimaryNeon
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Abrir Alexia", fontSize = MaterialTheme.typography.labelMedium.fontSize, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. Metadata & Details Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "METADATOS Y PARÁMETROS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNeon,
                            letterSpacing = 1.sp
                        )
                    )

                    Text(
                        text = job.prompt.ifBlank { "Sin instrucción / prompt" },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    )

                    Divider(color = BorderSubtle, thickness = 0.8.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Modelo", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(job.modelId, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Proveedor", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(job.providerId, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }

                    // Price info if available
                    val costText = job.actualCostUsd?.takeIf { it.isFinite() && it>=0 }?.let(currencyManager::formatCost)
                        ?: job.parameters["__actual_credits"]?.let { "$it créditos Crun" }
                    if (costText != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Coste de generación", style = MaterialTheme.typography.bodySmall, color = AccentEmerald)
                            Text(costText, style = MaterialTheme.typography.bodySmall, color = AccentEmerald, fontWeight = FontWeight.Bold)
                        }
                    }

                    for ((k, v) in job.parameters.filterKeys { !it.startsWith("__") }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(k, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(v, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                    }

                    job.providerJobId?.let { pId ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ID de Tarea", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Text(pId.take(16), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun ZoomableImageViewer(imageUrl: String, localUri: String? = null) {
    var scale by remember(imageUrl, localUri) { mutableStateOf(1f) }
    var offsetX by remember(imageUrl, localUri) { mutableStateOf(0f) }
    var offsetY by remember(imageUrl, localUri) { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imageUrl, localUri) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        var failed by remember(imageUrl,localUri) { mutableStateOf(false) }
        var loading by remember(imageUrl,localUri) { mutableStateOf(true) }
        var retry by remember(imageUrl,localUri) { mutableIntStateOf(0) }
        var useLocal by remember(imageUrl,localUri) { mutableStateOf(localUri!=null) }
        key(imageUrl,localUri,retry,useLocal) {
            AsyncImage(model=if(useLocal) localUri else imageUrl,contentDescription="Resultado generado",
                modifier=Modifier.fillMaxSize().testTag("output_image").graphicsLayer(scaleX=scale,scaleY=scale,translationX=offsetX,translationY=offsetY),
                contentScale=ContentScale.Fit,
                onLoading={loading=true;failed=false},onSuccess={loading=false;failed=false},
                onError={if(useLocal){useLocal=false}else{loading=false;failed=true}})
        }
        if(loading && !failed) CircularProgressIndicator(Modifier.testTag("output_loading"))
        if(failed) Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.BrokenImage,null,modifier=Modifier.size(40.dp),tint=TextSecondary)
            Text("No se puede cargar la imagen",style=MaterialTheme.typography.titleMedium)
            Text("El enlace puede haber caducado o no estar disponible. La tarea sigue guardada en tu historial.",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
            OutlinedButton(onClick={failed=false;loading=true;retry++}) {Text("Volver a intentar")}
        }
    }
}

@Composable
private fun NativeVideoPlayer(videoUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

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
}

@Composable
private fun NativeAudioPlayer(audioUrl: String) {
    val context = LocalContext.current
    var isPlaying by remember(audioUrl) { mutableStateOf(false) }
    var currentPosition by remember(audioUrl) { mutableStateOf(0L) }
    var duration by remember(audioUrl) { mutableStateOf(1L) }

    val exoPlayer = remember(audioUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(audioUrl)))
            prepare()
            addListener(object : Player.Listener {
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
            .padding(24.dp),
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

        Spacer(modifier = Modifier.height(16.dp))

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
            Text("${currentPosition / 1000}s", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text("${duration / 1000}s", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun TextOutputViewer(text: String) {
    val clipboardManager = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
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
                Spacer(modifier = Modifier.width(6.dp))
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
