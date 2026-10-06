package com.vidadomu.nexus.ui.output

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidadomu.nexus.core.model.GenerationOutput
import com.vidadomu.nexus.core.model.Modality
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request

data class ExportState(val busy: Boolean = false, val bytes: Long = 0, val message: String? = null)

class OutputExportViewModel(app: Application) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(ExportState())
    val state = mutable.asStateFlow()
    private var pending: Pair<GenerationOutput, String?>? = null
    private var job: Job? = null
    private var call: okhttp3.Call? = null
    private val resolver get() = getApplication<Application>().contentResolver

    fun prepare(output: GenerationOutput, local: String?) { pending = output to local }
    fun dismissMessage() { mutable.value = mutable.value.copy(message = null) }
    fun cancel() { call?.cancel(); job?.cancel() }

    fun save(destination: Uri?) {
        val request = pending.also { pending = null } ?: return
        if (destination == null || mutable.value.busy) return
        mutable.value = ExportState(busy = true)
        job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val (output, local) = request
                    if (output.type == Modality.CHAT) {
                        output.url.byteInputStream(Charsets.UTF_8).use { copy(it, destination) }
                    } else {
                        val source = local?.let { runCatching { resolver.openInputStream(Uri.parse(it)) }.getOrNull() }
                        if (source != null) source.use { copy(it, destination) }
                        else {
                            require(output.url.startsWith("https://") || output.url.startsWith("http://"))
                            val active = client.newCall(Request.Builder().url(output.url).build())
                            call = active
                            try {
                                active.execute().use { response ->
                                    check(response.isSuccessful) { "HTTP ${response.code}" }
                                    val body = response.body ?: error("Empty response")
                                    check(body.contentType()?.toString()?.startsWith("text/html") != true) { "Not media" }
                                    body.byteStream().use { copy(it, destination) }
                                }
                            } finally { call = null }
                        }
                    }
                }
                mutable.value = mutable.value.copy(busy = false, message = "Archivo guardado")
            } catch (error: Exception) {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { DocumentsContract.deleteDocument(resolver, destination) }
                }
                mutable.value = ExportState(message = if (error is CancellationException) "Descarga cancelada" else
                    "No se pudo guardar. Comprueba la conexión y el espacio disponible; el enlace puede haber caducado.")
                if (error is CancellationException) throw error
            }
        }
    }

    private suspend fun copy(input: InputStream, destination: Uri) {
        resolver.openOutputStream(destination, "w")?.use { output ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                total += count
                mutable.value = ExportState(busy = true, bytes = total)
            }
            check(total > 0) { "Empty file" }
        } ?: error("Destination unavailable")
    }

    override fun onCleared() { call?.cancel(); super.onCleared() }

    companion object {
        private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(15, TimeUnit.MINUTES).build()
    }
}
