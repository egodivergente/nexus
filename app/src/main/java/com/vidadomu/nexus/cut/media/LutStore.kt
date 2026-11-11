package com.vidadomu.nexus.cut.media

import android.content.Context
import android.net.Uri
import android.util.Log
import com.vidadomu.nexus.cut.timeline.render.CubeLut
import com.vidadomu.nexus.cut.timeline.render.CubeLutException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException

/**
 * Parsed .cube LUTs by URI, shared by preview, still frames and export. The file is read once;
 * a LUT that cannot be read is remembered with its reason so the UI can show it, and renders as
 * "no LUT" (never a crash).
 */
object LutStore {
    private const val MAX_BYTES = 8L * 1024 * 1024
    private val cache = HashMap<String, CubeLut>()
    private val errors = HashMap<String, String>()
    private val _version = MutableStateFlow(0)
    /** Bumped whenever a load finishes, so UI showing [label] can recompose. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /** Cached LUT, loading it now if needed (blocking file read: prefer [load] off the main thread first). */
    fun get(context: Context, uri: String): CubeLut? {
        synchronized(this) {
            cache[uri]?.let { return it }
            if (uri in errors) return null
        }
        return when (val r = load(context, uri)) {
            is Loaded.Ok -> r.lut
            is Loaded.Failed -> null
        }
    }

    fun errorOf(uri: String): String? = synchronized(this) { errors[uri] }

    /** Cached LUT without touching the file (safe on the main thread). */
    fun peek(uri: String): CubeLut? = synchronized(this) { cache[uri] }

    /** Short human name: the LUT's TITLE, else the file name from the URI. */
    fun label(uri: String): String {
        val lut = peek(uri)
        val file = Uri.parse(uri).lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: uri
        return when {
            lut != null -> "${lut.title.ifBlank { file }} · ${lut.size}³"
            errorOf(uri) != null -> "$file · no válido: ${errorOf(uri)}"
            else -> "$file · sin cargar todavía"
        }
    }

    sealed interface Loaded {
        class Ok(val lut: CubeLut) : Loaded
        class Failed(val reason: String) : Loaded
    }

    /** Reads and parses [uri] (call on an IO thread). Retries a previous failure. */
    fun load(context: Context, uri: String): Loaded {
        synchronized(this) { cache[uri]?.let { return Loaded.Ok(it) } }
        val result: Loaded = try {
            val text = context.contentResolver.openInputStream(Uri.parse(uri))?.use { s ->
                val bytes = s.readBytes()
                if (bytes.size > MAX_BYTES) throw CubeLutException("El archivo es demasiado grande para ser un LUT (${bytes.size / 1024} KB).")
                String(bytes, Charsets.UTF_8)
            } ?: throw IOException("no se puede abrir")
            Loaded.Ok(CubeLut.parse(text))
        } catch (e: CubeLutException) {
            Loaded.Failed(e.message ?: "LUT no válido")
        } catch (e: IOException) {
            Loaded.Failed("No se pudo leer el LUT (${e.message}).")
        } catch (e: SecurityException) {
            Loaded.Failed("Sin permiso para leer el LUT; vuelve a elegir el archivo.")
        }
        synchronized(this) {
            when (result) {
                is Loaded.Ok -> { cache[uri] = result.lut; errors.remove(uri) }
                is Loaded.Failed -> { errors[uri] = result.reason; Log.w("LutStore", "$uri: ${result.reason}") }
            }
        }
        _version.update { it + 1 }
        return result
    }
}
