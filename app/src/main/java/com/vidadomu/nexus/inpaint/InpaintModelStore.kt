package com.vidadomu.nexus.inpaint

import android.content.Context
import androidx.work.*
import com.vidadomu.nexus.assistant.MediaEditWorker
import com.vidadomu.nexus.core.network.awaitResponse
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class InpaintModelStore(private val context: Context) {
    val folder = File(context.filesDir, "inpaint-models/${InpaintContract.revision}").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("inpaint-models", 0)
    fun ready(): Boolean = InpaintContract.weights.all {
        File(folder, it.name).length() == it.bytes && prefs.getString(it.name, null) == it.sha256
    }
    fun enqueue() = WorkManager.getInstance(context).enqueueUniqueWork("inpaint-download", ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<InpaintDownloadWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())

    suspend fun download(onProgress: suspend (Int) -> Unit) = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()
        val totalBytes = InpaintContract.weights.sumOf { it.bytes }
        var completed = 0L
        for (weight in InpaintContract.weights) {
            val target = File(folder, weight.name)
            if (target.length() == weight.bytes && prefs.getString(weight.name, null) == weight.sha256) {
                completed += weight.bytes
                continue
            }
            check(folder.usableSpace > weight.bytes + 128L * 1024 * 1024) { "No hay espacio suficiente para el modelo local." }
            val stage = File(folder, weight.name + ".part")
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                val call = client.newCall(Request.Builder().url("${InpaintContract.source}/resolve/${InpaintContract.revision}/${weight.path}").build())
                val response = call.awaitResponse()
                response.use {
                    check(it.isSuccessful) { "No se pudo descargar el modelo (HTTP ${it.code})." }
                    val body = checkNotNull(it.body) { "Descarga vacía." }
                    check(body.contentLength() == -1L || body.contentLength() == weight.bytes) { "Tamaño de descarga inesperado." }
                    var received = 0L
                    var previousPercent = -1
                    body.byteStream().use { input -> stage.outputStream().use { output ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            check(received <= weight.bytes) { "El archivo supera el tamaño verificado." }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            val percent = ((completed + received) * 100 / totalBytes).toInt()
                            if (percent != previousPercent) { onProgress(percent); previousPercent = percent }
                        }
                        output.fd.sync()
                    } }
                    check(received == weight.bytes && digest.digest().joinToString("") { byte -> "%02x".format(byte) } == weight.sha256) { "La integridad del modelo no coincide. Se conserva la descarga anterior verificada." }
                    check(stage.renameTo(target)) { "No se pudo guardar el modelo verificado." }
                    prefs.edit().putString(weight.name, weight.sha256).commit()
                    completed += received
                }
            } finally { stage.delete() }
        }
        onProgress(100)
    }
}

class InpaintDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        setForeground(MediaEditWorker.foreground(applicationContext, "inpaint-download", "Descargando modelo de inpainting local"))
        InpaintModelStore(applicationContext).download { percent -> setProgress(workDataOf("percent" to percent)) }
        Result.success()
    } catch (cancelled: CancellationException) { throw cancelled
    } catch (failure: Exception) { Result.failure(workDataOf("error" to (failure.message ?: "No se pudo descargar el modelo."))) }
}
