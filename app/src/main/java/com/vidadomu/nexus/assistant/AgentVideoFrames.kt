package com.vidadomu.nexus.assistant

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.tools.Attachment
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Read-only local sampling. No upload of the video, audio decoding or generated files. */
internal object AgentVideoFrames {
    private const val MAX_DIMENSION = 1024

    suspend fun load(context: Context, attachment: Attachment): List<ContentBlock> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(attachment.uri)
        require(uri.scheme == "file" || uri.scheme == "content") {
            "Vuelve a adjuntar el vídeo para leer su copia local. No se ha analizado su contenido."
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            require(durationMs != null && durationMs > 0) {
                "No se puede leer la duración del vídeo. No se ha analizado su contenido."
            }
            val blocks = mutableListOf<ContentBlock>(
                ContentBlock.Text("Duración del vídeo: ${seconds(durationMs)} s. Fotogramas visuales equidistantes; marcas de tiempo aproximadas. SIN audio.")
            )
            repeat(AgentOrchestrator.VIDEO_FRAMES_PER_ATTACHMENT) { index ->
                currentCoroutineContext().ensureActive()
                val timeMs = ((durationMs - 1).toDouble() * index / (AgentOrchestrator.VIDEO_FRAMES_PER_ATTACHMENT - 1)).toLong()
                val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST, MAX_DIMENSION, MAX_DIMENSION)
                } else {
                    retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                } ?: throw IllegalArgumentException("No se pudo extraer el fotograma ${index + 1} de 6. No se ha enviado el vídeo al modelo.")
                val bytes = jpeg(frame)
                blocks += ContentBlock.Text("Fotograma ${index + 1}/6, t≈${seconds(timeMs)} s de ${seconds(durationMs)} s.")
                blocks += ContentBlock.Image("jpeg", bytes)
            }
            blocks
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("No se pudo analizar el vídeo: comprueba que sea legible y vuelve a adjuntarlo. No se ha enviado ningún fotograma de este turno.", e)
        } catch (e: Exception) {
            throw IllegalArgumentException("No se pudieron extraer los fotogramas del vídeo. Formato no compatible o permiso de lectura perdido; vuelve a adjuntarlo.", e)
        } catch (e: OutOfMemoryError) {
            throw IllegalArgumentException("No hay memoria suficiente para leer el vídeo. Prueba con una copia de menor resolución; el original se conserva.", e)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun seconds(milliseconds: Long) = String.format(Locale.ROOT, "%.3f", milliseconds / 1000.0)

    private fun jpeg(frame: Bitmap): ByteArray {
        var scaled: Bitmap? = null
        try {
            val ratio = minOf(1.0, MAX_DIMENSION.toDouble() / maxOf(frame.width, frame.height))
            val jpegFrame = if (ratio < 1.0) Bitmap.createScaledBitmap(frame,
                (frame.width * ratio).toInt().coerceAtLeast(1), (frame.height * ratio).toInt().coerceAtLeast(1), true) else frame
            scaled = jpegFrame
            return ByteArrayOutputStream().use { output ->
                check(jpegFrame.compress(Bitmap.CompressFormat.JPEG, 80, output)) { "No se pudo codificar el fotograma." }
                output.toByteArray()
            }
        } finally {
            if (scaled !== frame) scaled?.recycle()
            frame.recycle()
        }
    }
}
