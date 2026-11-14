package com.vidadomu.nexus.assistant

import android.content.Context
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.core.alexia.AlexiaBridge
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.OutputFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Guarda en el móvil los resultados que solo existen como URL del proveedor (caducan en horas o días).
 * Descarga a la carpeta de Nexus y apunta el índice al archivo local; no borra nada. Lo que ya caducó se
 * apunta como fallido y no se reintenta. Se llama al arrancar y tras cada generación.
 */
internal object AssetLocalizer {
    private const val PREFS = "asset_localizer"
    private const val FAILED = "failed_ids"
    private const val MERGED = "duplicates_merged_v1"
    private const val MAX_PER_RUN = 20
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    suspend fun run(context: Context, runtime: AssistantRuntime): Int = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val failed = prefs.getStringSet(FAILED, emptySet()).orEmpty().toMutableSet()
        val pending = runtime.assets.snapshot().assets.filter {
            !it.libraryDeleted && it.uri.startsWith("http") && it.id !in failed &&
                it.type in setOf(AssetType.IMAGE, AssetType.VIDEO, AssetType.AUDIO)
        }.sortedByDescending { it.createdAt }.take(MAX_PER_RUN)
        var saved = 0
        // Copia que la biblioteca ya guardó para ese mismo resultado: se reutiliza en vez de descargar otra.
        val libraryCopies = libraryCopiesByUrl(context)
        for (asset in pending) {
            val modality = when (asset.type) { AssetType.VIDEO -> Modality.VIDEO; AssetType.AUDIO -> Modality.AUDIO; else -> Modality.IMAGE }
            val file = libraryCopies[asset.uri]
                ?: runCatching { download(asset.uri, asset.mimeType, asset.id, AlexiaBridge.getNexusFolderForModality(modality)) }.getOrNull()
            if (file == null) { failed += asset.id; continue }
            val ok = runCatching {
                runtime.assets.relocateAsset(asset.id, asset.uri, android.net.Uri.fromFile(file).toString(), file.name)
            }.isSuccess
            if (ok) saved++ else { file.delete(); failed += asset.id }
        }
        prefs.edit().putStringSet(FAILED, failed).apply()
        saved + mergeExistingDuplicates(context, runtime, prefs)
    }

    private fun jobsOf(context: Context) =
        (context.applicationContext as? com.vidadomu.nexus.NexusApplication)?.generationRepository?.allJobs?.value.orEmpty()

    private fun libraryCopiesByUrl(context: Context): Map<String, File> {
        val jobs = jobsOf(context).filter { it.status == GenerationStatus.COMPLETED }
        val conflicts = OutputFiles.conflictingLocalUris(jobs.flatMap { it.outputs })
        val copies = mutableMapOf<String, File>()
        for (job in jobs) for (output in job.outputs) {
            val path = OutputFiles.trustedLocalUri(output, conflicts)?.let { runCatching { android.net.Uri.parse(it).path }.getOrNull() } ?: continue
            val f = File(path)
            if (output.url.isNotBlank() && f.isFile && f.length() > 0) copies.putIfAbsent(output.url, f)
        }
        return copies
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buf = ByteArray(65536); while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Una sola vez: los `Nexus-<id>` que se guardaron además de la copia de la biblioteca (mismo contenido exacto)
     * pasan a apuntar a esa copia, así la biblioteca deja de mostrarlos dos veces. No borra ningún archivo.
     */
    private fun mergeExistingDuplicates(context: Context, runtime: AssistantRuntime, prefs: android.content.SharedPreferences): Int {
        if (prefs.getBoolean(MERGED, false)) return 0
        val jobs = jobsOf(context).filter { it.status == GenerationStatus.COMPLETED }
        if (jobs.isEmpty()) return 0
        val conflicts = OutputFiles.conflictingLocalUris(jobs.flatMap { it.outputs })
        val bySize = mutableMapOf<Long, MutableList<Pair<File, String>>>()
        for (job in jobs) for (output in job.outputs) {
            val uri = OutputFiles.trustedLocalUri(output, conflicts) ?: continue
            val f = File(runCatching { android.net.Uri.parse(uri).path }.getOrNull() ?: continue)
            if (f.isFile && f.length() > 0) bySize.getOrPut(f.length()) { mutableListOf() }.add(f to uri)
        }
        var merged = 0
        val snapshot = runtime.assets.snapshot().assets.filter {
            !it.libraryDeleted && it.uri.startsWith("file:") && File(runCatching { android.net.Uri.parse(it.uri).path }.getOrNull() ?: "").name.startsWith("Nexus-")
        }
        for (asset in snapshot) {
            val mine = File(runCatching { android.net.Uri.parse(asset.uri).path }.getOrNull() ?: continue)
            val candidates = bySize[mine.length()]?.filter { it.first.absolutePath != mine.absolutePath } ?: continue
            if (!mine.isFile || candidates.isEmpty()) continue
            val digest = runCatching { sha256(mine) }.getOrNull() ?: continue
            val match = candidates.firstOrNull { runCatching { sha256(it.first) }.getOrNull() == digest } ?: continue
            val ok = runCatching { kotlinx.coroutines.runBlocking {
                runtime.assets.relocateAsset(asset.id, asset.uri, match.second, match.first.name) } }.isSuccess
            if (ok) merged++
        }
        prefs.edit().putBoolean(MERGED, true).apply()
        return merged
    }

    private fun download(url: String, mime: String?, id: String, folder: File): File? {
        folder.mkdirs()
        val ext = when {
            mime?.contains("png") == true -> "png"
            mime?.contains("webp") == true -> "webp"
            mime?.startsWith("video/") == true -> "mp4"
            mime?.startsWith("audio/") == true -> if (mime.contains("wav")) "wav" else "mp3"
            else -> url.substringBefore('?').substringAfterLast('.', "").take(4).ifBlank { "jpg" }
        }
        val target = File(folder, "Nexus-${id.takeLast(10)}.$ext")
        if (target.exists() && target.length() > 0) return target
        val temp = File(folder, ".${target.name}.download")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            body.byteStream().use { input -> temp.outputStream().use { input.copyTo(it) } }
        }
        if (temp.length() == 0L || !temp.renameTo(target)) { temp.delete(); return null }
        return target
    }
}
