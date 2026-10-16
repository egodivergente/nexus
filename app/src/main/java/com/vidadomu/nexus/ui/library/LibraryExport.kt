package com.vidadomu.nexus.ui.library

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Un archivo local para el ZIP y el nombre con el que viaja dentro. */
internal data class ExportItem(val uri: String, val name: String)

private val forbidden = Regex("""[\\/:*?"<>|\u0000-\u001f]""")

internal fun safeExportName(raw: String): String =
    raw.replace(forbidden, "_").trim().trim('.').take(80).ifBlank { "archivo" }

/** Extensión por tipo MIME, luego por la del propio fichero y, si no, por la modalidad. */
internal fun exportExtension(mime: String?, uri: String, kind: String): String {
    val byMime = when (mime?.lowercase()) {
        "image/png" -> "png"; "image/jpeg", "image/jpg" -> "jpg"; "image/webp" -> "webp"; "image/gif" -> "gif"
        "video/mp4" -> "mp4"; "video/quicktime" -> "mov"; "video/webm" -> "webm"
        "audio/mpeg" -> "mp3"; "audio/wav", "audio/x-wav" -> "wav"; "audio/mp4", "audio/aac" -> "m4a"; "audio/ogg" -> "ogg"
        "application/pdf" -> "pdf"; "text/plain" -> "txt"
        else -> null
    }
    if (byMime != null) return byMime
    val fromUri = uri.substringBefore('?').substringAfterLast('/', "").substringAfterLast('.', "")
    if (fromUri.length in 2..4 && fromUri.all { it.isLetterOrDigit() }) return fromUri.lowercase()
    return when (kind) { "image" -> "jpg"; "video" -> "mp4"; "audio" -> "mp3"; else -> "bin" }
}

/** Nombres únicos dentro del ZIP: «a.png», «a (2).png», «a (3).png». */
internal fun uniqueEntryNames(names: List<String>): List<String> {
    val used = HashSet<String>()
    return names.map { raw ->
        val name = safeExportName(raw)
        var candidate = name
        var n = 2
        while (!used.add(candidate.lowercase())) {
            val dot = name.lastIndexOf('.')
            candidate = if (dot > 0) "${name.substring(0, dot)} ($n)${name.substring(dot)}" else "$name ($n)"
            n++
        }
        candidate
    }
}

private fun open(context: Context, uri: String): InputStream? = runCatching {
    val parsed = Uri.parse(uri)
    if (parsed.scheme == "file") FileInputStream(File(parsed.path.orEmpty())) else context.contentResolver.openInputStream(parsed)
}.getOrNull()

/**
 * Escribe los archivos en [out] como ZIP. Devuelve cuántos entraron: los que no se pueden leer
 * se omiten (no se aborta todo el ZIP). Sin compresión: vídeo, imagen y audio ya vienen comprimidos.
 */
internal suspend fun writeZip(context: Context, out: OutputStream, items: List<ExportItem>): Int = withContext(Dispatchers.IO) {
    val names = uniqueEntryNames(items.map { it.name })
    var written = 0
    ZipOutputStream(out.buffered()).use { zip ->
        zip.setLevel(Deflater.NO_COMPRESSION)
        items.forEachIndexed { index, item ->
            val input = open(context, item.uri) ?: return@forEachIndexed
            input.use {
                zip.putNextEntry(ZipEntry(names[index]))
                it.copyTo(zip)
                zip.closeEntry()
                written++
            }
        }
    }
    written
}
