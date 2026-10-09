package com.vidadomu.nexus.assistant

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.system.Os
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.AssetSource
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.tools.AgentSession
import com.vidadomu.nexus.agent.core.tools.ToolInputException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

/** Native media changes only for indexed user assets. Originals and old URIs remain usable. */
internal class AgentFileOperations(private val app: NexusApplication, private val assets: AssetIndex) {
    private val lock = Mutex()
    private fun asset(input: JsonObject) = assets.asset(input.requiredText("asset_id"))
        ?: throw ToolInputException("Archivo desconocido; búscalo primero con search_assets.")

    suspend fun rename(input: JsonObject, session: AgentSession): JsonObject = lock.withLock {
        val asset = asset(input)
        val name = input.requiredText("file_name")
        if (name.length !in 1..120 || name in setOf(".", "..") || name.any { it < ' ' || it in "/\\:*?\"<>|" })
            throw ToolInputException("Usa un nombre de archivo sin rutas ni caracteres especiales.")
        val uri = Uri.parse(asset.uri)
        var compatibilityAlias = false
        var newUri = uri
        var rollback: (() -> Unit)? = null
        if (uri.scheme == "file") {
            val file = File(uri.path ?: throw ToolInputException("Ruta inválida."))
            // Do not grant access to preferences, credentials, databases or arbitrary private files.
            val allowedRoots = listOf(File(app.filesDir, "agent_media"), File(app.filesDir, "agent_exports"))
            val canonical = file.canonicalFile
            if (allowedRoots.none { root -> canonical.path.startsWith(root.canonicalPath + File.separator) } || !file.isFile)
                throw ToolInputException("Solo puedo renombrar copias de medios de Nexus o archivos MediaStore modificables.")
            if (!name.substringAfterLast('.', "").equals(file.extension, ignoreCase = true))
                throw ToolInputException("Conserva la extensión; para cambiar formato usa convert_image_asset.")
            val target = File(file.parentFile, name)
            if (target == file) return@withLock buildJsonObject { put("file_renamed", false); put("already_named", true) }
            if (target.exists()) throw ToolInputException("Ya existe un archivo con ese nombre.")
            if (!file.renameTo(target)) throw ToolInputException("No se pudo renombrar el archivo.")
            try {
                // Keep an old-path hard link: persisted chats/plans/history still refer to that path.
                Os.link(target.absolutePath, file.absolutePath)
            } catch (error: Exception) {
                // Some Android filesystems/SELinux policies reject link(). Keep a synced copy instead.
                val temporary = File(file.parentFile, ".compat_${UUID.randomUUID()}.part")
                try {
                    if (file.parentFile!!.usableSpace < target.length() + 2L * 1024 * 1024)
                        throw ToolInputException("No hay espacio para conservar los adjuntos anteriores.")
                    target.inputStream().use { input ->
                        java.io.FileOutputStream(temporary).use { output ->
                            input.copyTo(output)
                            output.fd.sync()
                        }
                    }
                    check(temporary.length() == target.length() && temporary.renameTo(file))
                } catch (copyError: Exception) {
                    temporary.delete()
                    if (file.exists()) file.delete()
                    check(target.renameTo(file)) { "No se pudo restaurar el nombre original." }
                    throw ToolInputException("No se pudo conservar la referencia anterior; el archivo original sigue intacto.")
                }
            }
            compatibilityAlias = true
            newUri = Uri.fromFile(target)
            rollback = { target.delete() } // old hard link holds the original bytes
        } else if (uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
            val previous = app.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
                ?: throw ToolInputException("No se pudo leer el archivo MediaStore.")
            if (!name.substringAfterLast('.', "").equals(previous.substringAfterLast('.', ""), ignoreCase = true))
                throw ToolInputException("Conserva la extensión; cambiar el nombre no convierte el archivo.")
            if (app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }, null, null) != 1)
                throw ToolInputException("Android no ha permitido renombrar este archivo.")
            val actual = app.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
            if (actual != name) {
                app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, previous) }, null, null)
                throw ToolInputException("Android cambió el nombre solicitado; se conserva el original.")
            }
            rollback = { app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, previous) }, null, null) }
        } else throw ToolInputException("El archivo no es una copia privada ni un MediaStore modificable. No puedo renombrar una URL remota.")
        try {
            val updated = assets.relocateAsset(asset.id, asset.uri, newUri.toString(), name)
            session.attachments.replaceAll { if (it.assetId == asset.id || it.uri == asset.uri) it.copy(uri = updated.uri, name = name) else it }
            buildJsonObject {
                put("asset_id", updated.id); put("file_name", name); put("uri", updated.uri)
                put("file_renamed", true); put("old_uri_preserved", true); put("compatibility_alias", compatibilityAlias)
            }
        } catch (error: Exception) { rollback?.invoke(); throw error }
    }

    suspend fun convert(input: JsonObject): JsonObject = lock.withLock {
        val source = asset(input)
        if (source.type != AssetType.IMAGE) throw ToolInputException("Selecciona una imagen; esta herramienta no transcodifica vídeo.")
        val format = input.requiredText("format").lowercase()
        val encoding = when (format) {
            "png" -> Bitmap.CompressFormat.PNG
            "jpeg" -> Bitmap.CompressFormat.JPEG
            "webp" -> if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            else -> throw ToolInputException("Formato no válido: png, jpeg o webp.")
        }
        val quality = input.integer("quality", 92, 1..100)
        val maxSide = input.integer("max_dimension", 2048, 64..4096)
        val ratio = input.text("aspect_ratio")?.let { raw ->
            val values = mapOf("16:9" to 16.0 / 9, "9:16" to 9.0 / 16, "1:1" to 1.0, "4:3" to 4.0 / 3, "3:4" to 3.0 / 4)
            values[raw] ?: throw ToolInputException("Formato de encuadre no válido.")
        }
        val uri = Uri.parse(source.uri)
        if (uri.scheme !in setOf("file", "content")) throw ToolInputException("Descarga la imagen antes de convertirla.")
        if (uri.scheme == "file") {
            val file = File(uri.path ?: throw ToolInputException("Ruta inválida.")).canonicalFile
            val roots = listOf(File(app.filesDir, "agent_media"), File(app.filesDir, "agent_exports"))
            if (roots.none { file.path.startsWith(it.canonicalPath + File.separator) })
                throw ToolInputException("Importa esta imagen en Nexus antes de convertirla.")
        }
        fun options(bounds: Boolean, sample: Int = 1) = BitmapFactory.Options().apply { inJustDecodeBounds = bounds; inSampleSize = sample }
        val bounds = options(true)
        app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ToolInputException("No es una imagen decodificable.")
        var sample = 1
        while (bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) > 16_777_216 ||
            maxOf(bounds.outWidth, bounds.outHeight) / sample > 8192) sample *= 2
        val rawBitmap = app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options(false, sample)) }
            ?: throw ToolInputException("No se pudo abrir la imagen.")
        val orientation = runCatching { app.contentResolver.openInputStream(uri)?.use {
            android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
        } }.getOrNull() ?: 1
        val matrix = android.graphics.Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }
                8 -> setRotate(270f)
            }
        }
        val decoded = if (matrix.isIdentity) rawBitmap else
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true).also {
                if (it !== rawBitmap) rawBitmap.recycle()
            }
        var cropped: Bitmap? = null
        var output: Bitmap? = null
        val folder = File(app.filesDir, "agent_exports").apply { mkdirs() }
        val destination = File(folder, "nexus_${UUID.randomUUID()}.${if (format == "jpeg") "jpg" else format}")
        val stage = File(folder, destination.name + ".part")
        try {
            val w = if (ratio != null && decoded.width.toDouble() / decoded.height > ratio) (decoded.height * ratio).roundToInt().coerceAtLeast(1) else decoded.width
            val h = if (ratio != null && decoded.width.toDouble() / decoded.height < ratio) (decoded.width / ratio).roundToInt().coerceAtLeast(1) else decoded.height
            cropped = Bitmap.createBitmap(decoded, (decoded.width - w) / 2, (decoded.height - h) / 2, w, h)
            val scale = min(1.0, maxSide.toDouble() / maxOf(w, h))
            val encoded = Bitmap.createScaledBitmap(cropped, (w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1), true)
            output = encoded
            stage.outputStream().use { stream -> check(encoded.compress(encoding, quality, stream)) { "No se pudo codificar." }; stream.fd.sync() }
            val verify = options(true)
            BitmapFactory.decodeFile(stage.path, verify)
            check(stage.length() > 0 && verify.outWidth == encoded.width && verify.outHeight == encoded.height) { "La exportación no se pudo verificar." }
            check(stage.renameTo(destination)) { "No se pudo guardar la exportación." }
            val result = assets.addAsset(source.copy(id = "", uri = Uri.fromFile(destination).toString(), displayName = destination.name,
                mimeType = "image/${if (format == "jpeg") "jpeg" else format}", width = encoded.width, height = encoded.height,
                source = AssetSource.IMPORTED, parentAssetId = source.id, generationId = null, createdAt = System.currentTimeMillis()))
            buildJsonObject {
                put("asset_id", result.id); put("uri", result.uri); put("format", format)
                put("width", encoded.width); put("height", encoded.height); put("original_preserved", true)
                put("center_cropped", ratio != null); put("decode_sample", sample)
            }
        } catch (error: Exception) { destination.delete(); throw error }
        finally { stage.delete(); listOfNotNull(output, cropped, decoded).distinct().forEach { it.recycle() } }
    }
}
