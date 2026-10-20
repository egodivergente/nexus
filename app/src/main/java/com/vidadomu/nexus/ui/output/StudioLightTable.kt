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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vidadomu.nexus.ui.theme.MediaForeground
import com.vidadomu.nexus.ui.theme.NexusTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * Mesa de Luz Cinematográfica (Nexus Studio 2.0):
 * Comparación A/B con cortinilla táctil entre original, resultado o variantes.
 * Extrae el fotograma más cercano a la posición solicitada para reutilizarlo en el Lienzo.
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
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(split, 0.05f..0.95f)
                setProgress { value -> split = value.coerceIn(0.05f, 0.95f); true }
            }
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

suspend fun captureVideoFrameToLocalUri(context: Context, videoSource: String, positionMs: Long): Uri? {
    var output: File? = null
    try {
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            var bitmap: Bitmap? = null
            try {
                when (Uri.parse(videoSource).scheme?.lowercase()) {
                    "http", "https" -> retriever.setDataSource(videoSource, emptyMap())
                    "content", "file" -> retriever.setDataSource(context, Uri.parse(videoSource))
                    else -> retriever.setDataSource(videoSource)
                }
                coroutineContext.ensureActive()
                val timeUs = positionMs.coerceIn(0L, Long.MAX_VALUE / 1000L) * 1000L
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                bitmap = if (width.toLong() * height > 16_000_000L) {
                    check(android.os.Build.VERSION.SDK_INT >= 27) { "Este dispositivo no puede extraer un fotograma de este tamaño." }
                    val scale = minOf(4096.0 / width, 4096.0 / height, 1.0)
                    retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST,
                        (width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
                } else retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                if (bitmap == null) return@withContext null
                coroutineContext.ensureActive()
                val dir = File(context.filesDir, "draft_media")
                check(dir.isDirectory || dir.mkdirs())
                val file = File.createTempFile("nexus_frame_", ".png", dir)
                output = file
                FileOutputStream(file).use { check(bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                coroutineContext.ensureActive()
                Uri.fromFile(file)
            } finally {
                bitmap?.recycle()
                runCatching { retriever.release() }
            }
        }
    } catch (error: CancellationException) {
        output?.delete()
        throw error
    } catch (_: Exception) {
        output?.delete()
        return null
    }
}
