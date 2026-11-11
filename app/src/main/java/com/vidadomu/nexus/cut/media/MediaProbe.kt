package com.vidadomu.nexus.cut.media

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.model.MediaRef
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest

class MediaImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Result of probing: the reference plus non-fatal warnings to show the user. */
data class Probed(val ref: MediaRef, val warnings: List<String>)

/**
 * Reads metadata of a user-picked file without copying it. Throws [MediaImportException] with a
 * user-facing reason when the file cannot be used.
 */
object MediaProbe {

    /** [mimeHint]: el tipo que ya conoce la Biblioteca; necesario con URIs file://, que no lo informan. */
    fun probe(context: Context, uri: Uri, id: String, mimeHint: String? = null): Probed {
        val cr = context.contentResolver
        var name = uri.lastPathSegment ?: "media"
        var size = -1L
        try {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        } catch (e: SecurityException) {
            throw MediaImportException("Sin permiso para leer «$name».", e)
        }
        val mime = cr.getType(uri) ?: mimeHint ?: android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()) ?: ""
        val fingerprint = fingerprint(cr, uri, size)
        if (mime.startsWith("image/")) return probeImage(cr, uri, id, name, size, fingerprint)

        val warnings = ArrayList<String>()
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
        } catch (e: IOException) {
            ex.release()
            throw MediaImportException("«$name» no es un archivo de vídeo o audio legible.", e)
        }
        try {
            var video: MediaFormat? = null
            var videoIndex = -1
            var audio: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("video/") && video == null) { video = f; videoIndex = i }
                if (m.startsWith("audio/") && audio == null) audio = f
            }
            if (video == null && audio == null) throw MediaImportException("«$name» no contiene pistas de vídeo ni de audio.")
            val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            video?.let { f ->
                if (codecs.findDecoderForFormat(decoderQuery(f)) == null) {
                    throw MediaImportException("El dispositivo no tiene decodificador para el vídeo de «$name» (${f.getString(MediaFormat.KEY_MIME)}).")
                }
                if (f.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                    val tr = f.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
                    if (tr == MediaFormat.COLOR_TRANSFER_ST2084 || tr == MediaFormat.COLOR_TRANSFER_HLG) {
                        warnings += "«$name» es HDR. La composición multipista de Media3 solo trabaja en SDR: el resultado puede verse con colores lavados."
                    }
                }
            }
            if (audio != null && codecs.findDecoderForFormat(decoderQuery(audio)) == null) {
                warnings += "El audio de «$name» (${audio.getString(MediaFormat.KEY_MIME)}) no se puede decodificar en este dispositivo; se importa solo la imagen."
                audio = null
                if (video == null) throw MediaImportException("El audio de «$name» no se puede decodificar en este dispositivo.")
            }
            val mmr = MediaMetadataRetriever()
            var rotation = 0
            var durationUs = 0L
            try {
                mmr.setDataSource(context, uri)
                rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                durationUs = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            } catch (e: RuntimeException) {
                warnings += "No se pudieron leer algunos metadatos de «$name»."
            } finally {
                mmr.release()
            }
            val trackDur = listOfNotNull(video, audio).maxOfOrNull { f ->
                if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L
            } ?: 0L
            if (trackDur > 0) durationUs = trackDur
            if (durationUs <= 0) throw MediaImportException("No se puede determinar la duración de «$name».")

            var fps: Double? = null
            var vfr = false
            if (video != null) {
                if (video.containsKey(MediaFormat.KEY_FRAME_RATE)) fps = numberOf(video, MediaFormat.KEY_FRAME_RATE)
                val (estimated, variable) = sampleFrameTiming(ex, videoIndex)
                if (fps == null || fps <= 0.0) fps = estimated
                vfr = variable
                if (vfr) warnings += "«$name» tiene frame rate variable (típico de móvil). Se edita bien, pero los cortes se ajustan al frame del proyecto."
            }
            val ref = MediaRef(
                id = id,
                uri = uri.toString(),
                displayName = name,
                kind = if (video != null) MediaKind.VIDEO else MediaKind.AUDIO,
                durationUs = durationUs,
                width = video?.getInteger(MediaFormat.KEY_WIDTH) ?: 0,
                height = video?.getInteger(MediaFormat.KEY_HEIGHT) ?: 0,
                rotationDegrees = rotation,
                frameRate = fps,
                variableFrameRate = vfr,
                videoMime = video?.getString(MediaFormat.KEY_MIME),
                audioMime = audio?.getString(MediaFormat.KEY_MIME),
                sampleRate = audio?.getInteger(MediaFormat.KEY_SAMPLE_RATE) ?: 0,
                channelCount = audio?.getInteger(MediaFormat.KEY_CHANNEL_COUNT) ?: 0,
                hasVideo = video != null,
                hasAudio = audio != null,
                sizeBytes = size,
                fingerprint = fingerprint,
            )
            return Probed(ref, warnings)
        } finally {
            ex.release()
        }
    }

    private fun probeImage(cr: ContentResolver, uri: Uri, id: String, name: String, size: Long, fp: String): Probed {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: IOException) {
            throw MediaImportException("No se puede leer la imagen «$name».", e)
        }
        if (opts.outWidth <= 0) throw MediaImportException("«$name» no es una imagen compatible.")
        return Probed(
            MediaRef(id, uri.toString(), name, MediaKind.IMAGE, null, opts.outWidth, opts.outHeight, sizeBytes = size, hasVideo = true, fingerprint = fp),
            emptyList(),
        )
    }

    /** Decoder lookup must not include the frame rate on API 21-29 (known platform quirk). */
    private fun decoderQuery(f: MediaFormat): MediaFormat {
        val m = f.getString(MediaFormat.KEY_MIME)!!
        return if (m.startsWith("video/")) {
            MediaFormat.createVideoFormat(m, f.getInteger(MediaFormat.KEY_WIDTH), f.getInteger(MediaFormat.KEY_HEIGHT)).also { q ->
                if (f.containsKey(MediaFormat.KEY_PROFILE)) q.setInteger(MediaFormat.KEY_PROFILE, f.getInteger(MediaFormat.KEY_PROFILE))
            }
        } else {
            MediaFormat.createAudioFormat(m, f.getInteger(MediaFormat.KEY_SAMPLE_RATE), f.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
        }
    }

    private fun numberOf(f: MediaFormat, key: String): Double? = try {
        f.getInteger(key).toDouble()
    } catch (e: ClassCastException) {
        f.getFloat(key).toDouble()
    }

    /** Looks at the first ~120 frames: average frame rate and whether spacing varies (VFR). */
    private fun sampleFrameTiming(ex: MediaExtractor, track: Int): Pair<Double?, Boolean> {
        ex.selectTrack(track)
        val times = ArrayList<Long>()
        while (times.size < 120) {
            val t = ex.sampleTime
            if (t < 0) break
            times += t
            if (!ex.advance()) break
        }
        ex.unselectTrack(track)
        if (times.size < 3) return null to false
        times.sort()
        val deltas = times.zipWithNext { a, b -> b - a }.filter { it > 0 }
        if (deltas.isEmpty()) return null to false
        val avg = deltas.average()
        val sorted = deltas.sorted()
        val p10 = sorted[sorted.size / 10]
        val p90 = sorted[(sorted.size * 9) / 10]
        return (1_000_000.0 / avg) to (p90 > p10 * 1.3)
    }

    private fun fingerprint(cr: ContentResolver, uri: Uri, size: Long): String {
        val md = MessageDigest.getInstance("SHA-1")
        try {
            cr.openInputStream(uri)?.use { s ->
                val buf = ByteArray(65536)
                val n = s.read(buf)
                if (n > 0) md.update(buf, 0, n)
            }
        } catch (e: FileNotFoundException) {
            throw MediaImportException("El archivo ya no existe.", e)
        }
        md.update(size.toString().toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }.take(20)
    }

    /** Keeps read access across reboots (SAF). Without it the project would go offline later. */
    fun persistAccess(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: SecurityException) {
        false
    }

    fun isReachable(context: Context, uri: String): Boolean = try {
        context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
    } catch (e: FileNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }
}
