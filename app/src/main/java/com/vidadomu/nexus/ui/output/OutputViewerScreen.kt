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

    val titleText = when (job?.modality) {
        Modality.IMAGE -> "IMAGEN"
        Modality.VIDEO -> "VÍDEO"
        Modality.AUDIO -> "AUDIO"
        Modality.CHAT -> "CHAT"
        null -> "GENERACIÓN"
    }

    Scaffold(
        containerColor = BackgroundDark,
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
                    val outputUrl = job?.outputs?.firstOrNull()?.url
                    if (outputUrl != null) {
                        IconButton(onClick = { shareMedia(context, outputUrl, job.prompt) }) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir", tint = PrimaryNeon)
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
            val output = job.outputs.firstOrNull()

            // 1. Output Media Canvas Viewer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                if (output != null) {
                    when (job.modality) {
                        Modality.IMAGE -> {
                            ZoomableImageViewer(imageUrl = output.url)
                        }
                        Modality.VIDEO -> {
                            NativeVideoPlayer(videoUrl = output.url)
                        }
                        Modality.AUDIO -> {
                            NativeAudioPlayer(audioUrl = output.url)
                        }
                        Modality.CHAT -> {
                            TextOutputViewer(text = output.url)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (job.modality == Modality.IMAGE) {
                        Button(
                            onClick = { onReuseAsInput(output.url) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceVariantDark,
                                contentColor = PrimaryNeon
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.MovieFilter, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Animar", fontSize = 12.sp)
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
                        Text("Remezclar", fontSize = 12.sp)
                    }

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
                        Text("Compartir", fontSize = 12.sp)
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
                                    text = "Clasificado en Alexia",
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
                                    text = "Documents / ALEXIA / $subfolder",
                                    style = MaterialTheme.typography.bodySmall.copy(color = PrimaryNeon, fontSize = 11.sp)
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
                            Text("Abrir Alexia", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    val costText = remember(job.technicalDetails, currencyManager) {
                        val match = Regex("""(?:Coste(?: de generación)?:\s*|price:\s*)([0-9.]+)""", RegexOption.IGNORE_CASE)
                            .find(job.technicalDetails ?: "")
                        val priceUsd = match?.groupValues?.get(1)?.toDoubleOrNull()
                        if (priceUsd != null) {
                            currencyManager.formatAtlasApiPrice(priceUsd)
                        } else null
                    }
                    if (costText != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Coste de generación", style = MaterialTheme.typography.bodySmall, color = AccentEmerald)
                            Text(costText, style = MaterialTheme.typography.bodySmall, color = AccentEmerald, fontWeight = FontWeight.Bold)
                        }
                    }

                    for ((k, v) in job.parameters) {
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
private fun ZoomableImageViewer(imageUrl: String) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
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
        AsyncImage(
            model = imageUrl,
            contentDescription = "Generated Image Output",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun NativeVideoPlayer(videoUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    DisposableEffect(Unit) {
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
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun NativeAudioPlayer(audioUrl: String) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(1L) }

    val exoPlayer = remember {
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

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            delay(500)
        }
    }

    DisposableEffect(Unit) {
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
                tint = BackgroundDark,
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
