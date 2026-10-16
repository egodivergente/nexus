package com.vidadomu.nexus.ui.output

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationOutput
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.ui.theme.TextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class OutputFileFacts(
    val bytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Float? = null,
)

/** Metadatos locales únicamente; no descarga ni consulta el enlace remoto del proveedor. */
internal suspend fun inspectOutputFile(context: Context, localUri: String, mimeType: String): OutputFileFacts =
    withContext(Dispatchers.IO) {
        val uri = Uri.parse(localUri)
        val bytes = runCatching {
            if (uri.scheme == "file") File(uri.path.orEmpty()).takeIf { it.isFile }?.length()
            else context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)).takeIf { it >= 0 } else null
            }
        }.getOrNull()

        if (mimeType.startsWith("image/")) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
            OutputFileFacts(bytes, bounds.outWidth.takeIf { it > 0 }, bounds.outHeight.takeIf { it > 0 })
        } else if (mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                OutputFileFacts(
                    bytes,
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.div(1000f),
                )
            } catch (_: Exception) {
                OutputFileFacts(bytes)
            } finally {
                runCatching { retriever.release() }
            }
        } else OutputFileFacts(bytes)
    }

@Composable
internal fun OutputMetadataDetails(job: GenerationJob, output: GenerationOutput, localUri: String?) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    var facts by remember(output.id, localUri) { mutableStateOf(OutputFileFacts()) }
    val mimeType = output.mimeType.ifBlank { when (output.type) {
        Modality.IMAGE -> "image/*"
        Modality.VIDEO -> "video/*"
        Modality.AUDIO -> "audio/*"
        else -> "application/octet-stream"
    } }
    LaunchedEffect(output.id, localUri, mimeType) {
        facts = if (localUri != null) inspectOutputFile(context, localUri, mimeType) else OutputFileFacts()
    }

    val width = output.width?.takeIf { it > 0 } ?: facts.width
    val height = output.height?.takeIf { it > 0 } ?: facts.height
    val duration = output.durationSeconds?.takeIf { it.isFinite() && it > 0 } ?: facts.durationSeconds
    val date = remember(job.startedAt, job.completedAt) {
        SimpleDateFormat("d MMM yyyy · HH:mm", Locale("es", "ES")).format(Date(job.completedAt ?: job.startedAt))
    }

    Column(Modifier.fillMaxWidth()) {
        MetadataRow("Modelo", app.modelCatalogRepository.getModelById(job.modelId)?.displayName ?: job.modelId)
        MetadataRow("Proveedor", job.providerId)
        MetadataRow("Fecha y hora", date)
        if (output.type != Modality.AUDIO) {
            MetadataRow("Calidad · resolución", resolutionLabel(output.type, width, height) ?: "No disponible")
        }
        if (output.type == Modality.VIDEO || output.type == Modality.AUDIO) {
            MetadataRow("Duración", formatOutputDuration(duration) ?: "No disponible")
        }
        MetadataRow("Tamaño", facts.bytes?.let(::formatMegabytes) ?: if (localUri == null) "Sin copia local" else "No disponible")
        MetadataRow("Formato", output.mimeType.ifBlank { output.type.name.lowercase(Locale.ROOT) })
        Spacer(Modifier.height(8.dp))
    }
}

private fun resolutionLabel(type: Modality, width: Int?, height: Int?): String? {
    if (width == null || height == null || width <= 0 || height <= 0) return null
    return if (type == Modality.IMAGE) {
        String.format(Locale("es", "ES"), "%.1f MP · %d × %d", width.toLong() * height / 1_000_000.0, width, height)
    } else {
        val label = when (maxOf(width, height)) {
            in 3800..Int.MAX_VALUE -> "UHD 4K"
            in 2500..3799 -> "QHD"
            in 1900..2499 -> "Full HD"
            in 1200..1899 -> "HD"
            else -> "SD"
        }
        "$label · $width × $height"
    }
}

private fun formatMegabytes(bytes: Long): String =
    String.format(Locale("es", "ES"), "%.2f MB", bytes / (1024.0 * 1024.0))

private fun formatOutputDuration(seconds: Float?): String? = seconds?.takeIf { it.isFinite() && it >= 0 }?.toLong()?.let {
    "%d:%02d".format(Locale.ROOT, it / 60, it % 60)
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
