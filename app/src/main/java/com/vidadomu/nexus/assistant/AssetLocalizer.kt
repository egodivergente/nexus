package com.vidadomu.nexus.assistant

import android.content.Context
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.core.alexia.AlexiaBridge
import com.vidadomu.nexus.core.model.Modality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Guarda en el móvil los resultados que solo existen como URL del proveedor (caducan en horas o días).
 * Descarga a la carpeta de Nexus y apunta el índice al archivo local; no borra nada. Lo que ya caducó se
 * apunta como fallido y no se reintenta. Se llama al arrancar y tras cada generación.
 */
internal object AssetLocalizer {
    private const val PREFS = "asset_localizer"
    private const val FAILED = "failed_ids"
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
        for (asset in pending) {
            val modality = when (asset.type) { AssetType.VIDEO -> Modality.VIDEO; AssetType.AUDIO -> Modality.AUDIO; else -> Modality.IMAGE }
            val file = runCatching { download(asset.uri, asset.mimeType, asset.id, AlexiaBridge.getNexusFolderForModality(modality)) }.getOrNull()
            if (file == null) { failed += asset.id; continue }
            val ok = runCatching {
                runtime.assets.relocateAsset(asset.id, asset.uri, android.net.Uri.fromFile(file).toString(), file.name)
            }.isSuccess
            if (ok) saved++ else { file.delete(); failed += asset.id }
        }
        prefs.edit().putStringSet(FAILED, failed).apply()
        saved
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
