package com.vidadomu.nexus.ui.output

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vidadomu.nexus.ui.theme.MediaForeground
import com.vidadomu.nexus.ui.theme.NexusTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Mesa de Luz Cinematográfica (Nexus Studio 2.0):
 * 1. Comparador A/B con cortinilla táctil a 120 Hz (SplitWipeCompare) para contrastar original vs resultado o 2 variantes.
 * 2. Extractor de fotograma exacto de vídeo (captureVideoFrameToLocalUri) para encadenar planos en Lienzo o Google Flow.
 */
@Composable
fun SplitWipeCompare(
    leftUri: String,
    rightUri: String,
    leftLabel: String = "A",
    rightLabel: String = "B",
    modifier: Modifier = Modifier,
) {
    var split by remember { mutableFloatStateOf(0.5f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    val prev = split
                    val next = (split + dragAmount / widthPx).coerceIn(0.05f, 0.95f)
                    if ((prev < 0.5f && next >= 0.5f) || (prev > 0.5f && next <= 0.5f)) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    split = next
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Imagen derecha (fondo completo)
        AsyncImage(
            model = rightUri,
            contentDescription = rightLabel,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        // Imagen izquierda recortada hasta la cortinilla
        AsyncImage(
            model = leftUri,
            contentDescription = leftLabel,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(right = size.width * split) {
                        this@drawWithContent.drawContent()
                    }
                }
        )

        // Línea divisoria y tirador táctil
        val lineX = (widthPx * split).roundToInt()
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .offset { IntOffset(lineX - (widthPx / 2f).roundToInt(), 0) }
                .width(2.dp)
                .background(Color.White.copy(alpha = 0.9f))
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 6.dp,
            modifier = Modifier
                .offset { IntOffset(lineX - (widthPx / 2f).roundToInt(), 0) }
                .size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Compare, contentDescription = "Deslizar cortinilla A/B", modifier = Modifier.size(20.dp))
            }
        }

        // Etiquetas A / B
        Surface(
            shape = NexusTokens.filter,
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(NexusTokens.space12)
        ) {
            Text(
                leftLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MediaForeground,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Surface(
            shape = NexusTokens.filter,
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(NexusTokens.space12)
        ) {
            Text(
                rightLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MediaForeground,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

suspend fun captureVideoFrameToLocalUri(
    context: Context,
    videoSource: String,
    positionMs: Long,
): Uri? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        when {
            videoSource.startsWith("http://") || videoSource.startsWith("https://") ->
                retriever.setDataSource(videoSource, emptyMap())
            videoSource.startsWith("content://") || videoSource.startsWith("file://") ->
                retriever.setDataSource(context, Uri.parse(videoSource))
            else -> retriever.setDataSource(videoSource)
        }
        val timeUs = (positionMs.coerceAtLeast(0L)) * 1000L
        val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
            ?: return@withContext null
        val dir = File(context.filesDir, "draft_media").apply { mkdirs() }
        val outFile = File(dir, "studio_frame_${System.currentTimeMillis()}.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        Uri.fromFile(outFile)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}
