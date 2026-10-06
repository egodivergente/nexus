package com.vidadomu.nexus.core.alexia

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationOutput
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.data.repository.GenerationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

object AlexiaBridge {
    private const val TAG = "AlexiaBridge"
    private const val ALEXIA_DIR_NAME = "ALEXIA"
    private const val PROJECT_NAME = "Nexus"
    private const val INDEX_FILE_NAME = "axel-index.json"
    private const val TMP_FILE_NAME = ".axel-index.tmp.json"
    private const val BACKUP_FILE_NAME = ".axel-index.backup.json"

    private val mutex = Mutex()
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Retorna el directorio raíz físico de ALEXIA en el almacenamiento externo.
     * Típicamente: /storage/emulated/0/Documents/ALEXIA
     */
    fun getAlexiaRootDir(): File {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val alexiaDir = File(docsDir, ALEXIA_DIR_NAME)
        if (!alexiaDir.exists()) {
            alexiaDir.mkdirs()
        }
        return alexiaDir
    }

    /**
     * Retorna la subcarpeta física para una modalidad dentro de Nexus.
     * Ejemplo: Documents/ALEXIA/Nexus/Imágenes
     */
    fun getNexusFolderForModality(modality: Modality): File {
        val root = getAlexiaRootDir()
        val nexusRoot = File(root, PROJECT_NAME)
        val subfolderName = when (modality) {
            Modality.IMAGE -> "Imágenes"
            Modality.VIDEO -> "Vídeos"
            Modality.AUDIO -> "Audio"
            Modality.CHAT -> "Chat"
        }
        val target = File(nexusRoot, subfolderName)
        if (!target.exists()) {
            target.mkdirs()
        }
        return target
    }

    /**
     * Clasifica de forma automática un trabajo de generación completado en Alexia.
     */
    suspend fun classifyJob(context: Context, job: GenerationJob, repository: GenerationRepository? = null): Boolean = withContext(Dispatchers.IO) {
        if (job.status != GenerationStatus.COMPLETED || job.outputs.isEmpty()) {
            return@withContext false
        }

        var anySuccess = false
        val updatedOutputs = mutableListOf<GenerationOutput>()

        for (output in job.outputs) {
            val url = output.url
            if (url.isBlank()) {
                updatedOutputs.add(output)
                continue
            }

            // Si ya tiene un archivo local válido, no volvemos a descargar
            val existingLocalFile = output.localUri?.let { uriStr ->
                try {
                    val uri = Uri.parse(uriStr)
                    val path = uri.path ?: uriStr
                    val f = File(path)
                    if (f.exists() && f.length() > 0) f else null
                } catch (e: Exception) { null }
            }

            val targetFile: File = if (existingLocalFile != null) {
                existingLocalFile
            } else {
                val downloaded = downloadMediaOutput(job, output)
                if (downloaded == null) {
                    updatedOutputs.add(output)
                    continue
                }
                downloaded
            }

            // Metadatos multimedia
            val sha256 = calculateSha256(targetFile)
            val sizeBytes = targetFile.length()
            val dims = extractDimensionsAndDuration(targetFile, job.modality)
            val mimeType = resolveMimeType(targetFile, output.mimeType, job.modality)

            // Registro en axel-index.json
            val registered = registerInAxelIndex(
                job = job,
                output = output,
                targetFile = targetFile,
                sha256 = sha256,
                sizeBytes = sizeBytes,
                mimeType = mimeType,
                width = dims.width,
                height = dims.height,
                durationMs = dims.durationMs
            )

            // Escanear en MediaStore del sistema Android
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(targetFile.absolutePath),
                arrayOf(mimeType),
                null
            )

            // Notificar a la app Alexia si está instalada
            sendSyncBroadcastToAlexia(context)

            val updatedOutput = output.copy(
                localUri = Uri.fromFile(targetFile).toString(),
                mimeType = mimeType,
                width = dims.width.takeIf { it > 0 } ?: output.width,
                height = dims.height.takeIf { it > 0 } ?: output.height,
                durationSeconds = if (dims.durationMs > 0) (dims.durationMs / 1000f) else output.durationSeconds
            )
            updatedOutputs.add(updatedOutput)
            anySuccess = true
        }

        if (anySuccess && repository != null) {
            val updatedJob = job.copy(outputs = updatedOutputs)
            repository.updateJob(updatedJob)
        }

        anySuccess
    }

    /**
     * Sincroniza y clasifica todas las generaciones existentes en la BD de Nexus que aún no estén en Alexia.
     */
    suspend fun classifyPendingGenerations(context: Context, repository: GenerationRepository) = withContext(Dispatchers.IO) {
        val jobs = repository.allJobs.value
        val pendingJobs = jobs.filter { job ->
            job.status == GenerationStatus.COMPLETED &&
                job.outputs.any { out ->
                    out.url.isNotBlank() && (out.localUri.isNullOrBlank() || !File(Uri.parse(out.localUri).path ?: "").exists())
                }
        }

        Log.i(TAG, "Clasificando automáticamente ${pendingJobs.size} generaciones históricas en Alexia...")
        for (job in pendingJobs) {
            try {
                classifyJob(context, job, repository)
            } catch (e: Exception) {
                Log.e(TAG, "Error clasificando job ${job.id} en Alexia: ${e.message}", e)
            }
        }
    }

    private fun downloadMediaOutput(job: GenerationJob, output: GenerationOutput): File? {
        val folder = getNexusFolderForModality(job.modality)
        val ext = determineExtension(output.url, output.mimeType, job.modality)
        val slug = sanitizePromptToSlug(job.prompt)
        val shortId = job.id.takeLast(8).replace("-", "")
        val typeLabel = when (job.modality) {
            Modality.IMAGE -> "Imagen"
            Modality.VIDEO -> "Video"
            Modality.AUDIO -> "Audio"
            Modality.CHAT -> "Documento"
        }
        val fileName = "$typeLabel-$PROJECT_NAME-${slug}_$shortId.$ext"
        val targetFile = File(folder, fileName)

        // Si ya existe y está completo, retornarlo
        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile
        }

        val tempFile = File(folder, ".$fileName.download")
        try {
            val request = Request.Builder().url(output.url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP error downloading media: ${response.code} from ${output.url}")
                return null
            }
            val body = response.body ?: return null
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { outputStream ->
                    input.copyTo(outputStream)
                }
            }
            if (tempFile.length() == 0L) {
                tempFile.delete()
                return null
            }
            if (targetFile.exists()) targetFile.delete()
            if (tempFile.renameTo(targetFile)) {
                Log.i(TAG, "Media guardado con éxito en Alexia: ${targetFile.absolutePath}")
                return targetFile
            } else {
                tempFile.delete()
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando media para Alexia: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
            return null
        }
    }

    private suspend fun registerInAxelIndex(
        job: GenerationJob,
        output: GenerationOutput,
        targetFile: File,
        sha256: String,
        sizeBytes: Long,
        mimeType: String,
        width: Int,
        height: Int,
        durationMs: Long
    ): Boolean = mutex.withLock {
        val root = getAlexiaRootDir()
        val indexFile = File(root, INDEX_FILE_NAME)
        val tmpFile = File(root, TMP_FILE_NAME)
        val backupFile = File(root, BACKUP_FILE_NAME)

        try {
            val json = if (indexFile.exists()) {
                try {
                    JSONObject(indexFile.readText(Charsets.UTF_8))
                } catch (e: Exception) {
                    createEmptyIndexJson()
                }
            } else {
                createEmptyIndexJson()
            }

            val projectsArr = json.optJSONArray("projects") ?: JSONArray().also { json.put("projects", it) }
            val assetsArr = json.optJSONArray("assets") ?: JSONArray().also { json.put("assets", it) }
            val tagsArr = json.optJSONArray("tags") ?: JSONArray().also { json.put("tags", it) }

            // 1. Asegurar proyecto Nexus
            var nexusProjectId: Long = -1
            var nexusProjectIndex = -1
            for (i in 0 until projectsArr.length()) {
                val p = projectsArr.getJSONObject(i)
                if (p.optString("name", "").equals(PROJECT_NAME, ignoreCase = true)) {
                    nexusProjectId = p.optLong("id", 100L)
                    nexusProjectIndex = i
                    break
                }
            }

            if (nexusProjectId == -1L) {
                var maxId = 0L
                for (i in 0 until projectsArr.length()) {
                    val pid = projectsArr.getJSONObject(i).optLong("id", 0L)
                    if (pid > maxId) maxId = pid
                }
                nexusProjectId = if (maxId > 0) maxId + 1 else 100L
                val newProject = JSONObject().apply {
                    put("id", nexusProjectId)
                    put("name", PROJECT_NAME)
                    put("description", "Generaciones IA multimedia de Nexus Studio")
                    put("coverAssetId", JSONObject.NULL)
                    put("createdAt", System.currentTimeMillis())
                    put("updatedAt", System.currentTimeMillis())
                    put("sortOrder", "NEWEST")
                    put("gridColumns", 3)
                }
                projectsArr.put(newProject)
            }

            // 2. Verificar si el asset ya existe en el índice
            val relativePath = "Nexus/${targetFile.parentFile?.name ?: ""}/${targetFile.name}"
            var alreadyExists = false
            for (i in 0 until assetsArr.length()) {
                val a = assetsArr.getJSONObject(i)
                val rel = a.optString("relativePath", "")
                if (rel.equals(relativePath, ignoreCase = true) || (sha256.isNotBlank() && a.optString("contentSha256", "") == sha256)) {
                    alreadyExists = true
                    break
                }
            }

            var nextAssetId = 1L
            for (i in 0 until assetsArr.length()) {
                val aid = assetsArr.getJSONObject(i).optLong("id", 0L)
                if (aid >= nextAssetId) nextAssetId = aid + 1
            }

            if (!alreadyExists) {
                val modalityLabel = when (job.modality) {
                    Modality.IMAGE -> "Imagen"
                    Modality.VIDEO -> "Video"
                    Modality.AUDIO -> "Audio"
                    Modality.CHAT -> "Chat"
                }
                val modelSlug = job.modelId.substringAfterLast("/")

                val tagsList = JSONArray().apply {
                    put("Nexus")
                    put(modalityLabel)
                    put(modelSlug)
                }

                val promptTitle = if (job.prompt.isNotBlank()) {
                    job.prompt.take(60).trim()
                } else {
                    targetFile.name
                }

                val notesText = buildString {
                    append("Prompt: ").append(job.prompt)
                    append("\nModelo: ").append(job.modelId)
                    if (job.actualCostUsd != null && job.actualCostUsd > 0) {
                        append("\nCoste: $").append(job.actualCostUsd).append(" USD")
                    }
                    append("\nJob ID: ").append(job.id)
                }

                val assetObj = JSONObject().apply {
                    put("id", nextAssetId)
                    put("projectId", nexusProjectId)
                    put("project", PROJECT_NAME)
                    put("displayName", promptTitle)
                    put("originalName", targetFile.name)
                    put("contentSha256", sha256)
                    put("people", JSONArray())
                    put("tags", tagsList)
                    put("collections", JSONArray())
                    put("role", "GENERATED")
                    put("masterReference", false)
                    put("favorite", false)
                    put("mimeType", mimeType)
                    put("storageMode", "MOVE")
                    put("relativePath", relativePath)
                    put("notes", notesText)
                    put("sizeBytes", sizeBytes)
                    put("width", width)
                    put("height", height)
                    put("durationMs", durationMs)
                    put("importedAt", System.currentTimeMillis())
                }

                // Insertar al inicio de la lista de assets para que aparezca primero
                val newAssetsList = JSONArray()
                newAssetsList.put(assetObj)
                for (i in 0 until assetsArr.length()) {
                    newAssetsList.put(assetsArr.get(i))
                }
                json.put("assets", newAssetsList)

                // Actualizar carátula del proyecto si es imagen
                if (job.modality == Modality.IMAGE && nexusProjectIndex >= 0) {
                    val p = projectsArr.getJSONObject(nexusProjectIndex)
                    p.put("coverAssetId", nextAssetId)
                    p.put("updatedAt", System.currentTimeMillis())
                }

                // 3. Asegurar tags globales
                val existingTagNames = mutableSetOf<String>()
                var maxTagId = 0L
                for (i in 0 until tagsArr.length()) {
                    val t = tagsArr.getJSONObject(i)
                    existingTagNames.add(t.optString("name", "").lowercase())
                    val tid = t.optLong("id", 0L)
                    if (tid > maxTagId) maxTagId = tid
                }

                listOf("Nexus", modalityLabel, modelSlug).forEach { tName ->
                    if (!existingTagNames.contains(tName.lowercase())) {
                        maxTagId++
                        tagsArr.put(JSONObject().apply {
                            put("id", maxTagId)
                            put("name", tName)
                            put("color", if (tName == "Nexus") "#00F5D4" else "#FF6D00")
                        })
                        existingTagNames.add(tName.lowercase())
                    }
                }
            }

            json.put("generatedAt", System.currentTimeMillis())

            // Escritura atómica
            val jsonBytes = json.toString(2).toByteArray(Charsets.UTF_8)
            tmpFile.writeBytes(jsonBytes)

            if (indexFile.exists()) {
                if (backupFile.exists()) backupFile.delete()
                indexFile.renameTo(backupFile)
            }
            if (tmpFile.renameTo(indexFile)) {
                if (backupFile.exists()) backupFile.delete()
                Log.i(TAG, "Índice axel-index.json actualizado correctamente con asset ID: $nextAssetId")
                true
            } else {
                if (backupFile.exists()) backupFile.renameTo(indexFile)
                tmpFile.delete()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando axel-index.json: ${e.message}", e)
            if (tmpFile.exists()) tmpFile.delete()
            false
        }
    }

    private fun createEmptyIndexJson(): JSONObject {
        return JSONObject().apply {
            put("schemaVersion", 1)
            put("generatedAt", System.currentTimeMillis())
            put("generator", "NEXUS AI Studio")
            put("projects", JSONArray())
            put("assets", JSONArray())
            put("people", JSONArray())
            put("tags", JSONArray())
            put("collections", JSONArray())
        }
    }

    private fun sendSyncBroadcastToAlexia(context: Context) {
        try {
            val syncIntent = Intent("com.axel.library.ACTION_SYNC").apply {
                setPackage("com.axel.library")
            }
            context.sendBroadcast(syncIntent)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo enviar broadcast a Alexia: ${e.message}")
        }
    }

    private data class MediaDims(val width: Int = 0, val height: Int = 0, val durationMs: Long = 0L)

    private fun extractDimensionsAndDuration(file: File, modality: Modality): MediaDims {
        return try {
            when (modality) {
                Modality.IMAGE -> {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, opts)
                    MediaDims(opts.outWidth.coerceAtLeast(0), opts.outHeight.coerceAtLeast(0), 0L)
                }
                Modality.VIDEO, Modality.AUDIO -> {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    retriever.release()
                    MediaDims(w, h, dur)
                }
                else -> MediaDims()
            }
        } catch (e: Exception) {
            MediaDims()
        }
    }

    private fun calculateSha256(file: File): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } > 0) {
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    private fun resolveMimeType(file: File, declaredMime: String, modality: Modality): String {
        if (declaredMime.isNotBlank()) return declaredMime
        val name = file.name.lowercase()
        return when {
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".webp") -> "image/webp"
            name.endsWith(".mp4") -> "video/mp4"
            name.endsWith(".mp3") -> "audio/mpeg"
            name.endsWith(".wav") -> "audio/wav"
            modality == Modality.IMAGE -> "image/jpeg"
            modality == Modality.VIDEO -> "video/mp4"
            modality == Modality.AUDIO -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }

    private fun determineExtension(url: String, mimeType: String, modality: Modality): String {
        val cleanUrl = url.substringBefore("?").substringBefore("#")
        val extFromUrl = cleanUrl.substringAfterLast(".", "").lowercase()
        if (extFromUrl in listOf("jpg", "jpeg", "png", "webp", "mp4", "mp3", "wav")) {
            return extFromUrl
        }
        return when {
            mimeType.contains("png") -> "png"
            mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpeg"
            mimeType.contains("webp") -> "webp"
            mimeType.contains("mp4") -> "mp4"
            mimeType.contains("mp3") || mimeType.contains("mpeg") -> "mp3"
            mimeType.contains("wav") -> "wav"
            modality == Modality.IMAGE -> "jpeg"
            modality == Modality.VIDEO -> "mp4"
            modality == Modality.AUDIO -> "mp3"
            else -> "bin"
        }
    }

    private fun sanitizePromptToSlug(prompt: String): String {
        if (prompt.isBlank()) return "nexus_creation"
        val normalized = prompt.take(30).lowercase(Locale.ROOT)
            .replace(Regex("[áàäâ]"), "a")
            .replace(Regex("[éèëê]"), "e")
            .replace(Regex("[íìïî]"), "i")
            .replace(Regex("[óòöô]"), "o")
            .replace(Regex("[úùüû]"), "u")
            .replace(Regex("[ñ]"), "n")
            .replace(Regex("[^a-z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return normalized.ifBlank { "nexus_creation" }
    }
}
