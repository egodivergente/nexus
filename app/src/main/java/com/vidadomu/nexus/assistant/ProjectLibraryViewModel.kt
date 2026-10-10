package com.vidadomu.nexus.assistant

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetType
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class ProjectLibraryOperation(val busy: Boolean = false, val progress: String? = null, val message: String? = null)

/** Retains export selection across rotation and document-picker process recreation. */
internal class ProjectLibraryViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val application = app as NexusApplication
    private val runtime = AssistantRuntime.get(application)
    private val mutable = MutableStateFlow(ProjectLibraryOperation())
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    @Volatile private var call: okhttp3.Call? = null

    fun clearMessage() { mutable.value = mutable.value.copy(message = null) }
    fun cancel() { call?.cancel(); operation?.cancel() }

    fun delete(projectId: String, assets: List<Asset>) {
        if (mutable.value.busy) return
        mutable.value = ProjectLibraryOperation(busy = true)
        operation = viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) { runtime.assets.deleteFromLibrary(projectId, assets.map { it.id }.toSet()) }
                mutable.value = ProjectLibraryOperation(message = "$count archivos borrados de la biblioteca")
            } catch (error: Exception) {
                mutable.value = ProjectLibraryOperation(message = "No se pudo borrar. Vuelve a intentarlo.")
                if (error is CancellationException) throw error
            }
        }
    }

    fun prepareExport(projectId: String, assets: List<Asset>) {
        saved["zipProject"] = projectId
        saved["zipIds"] = ArrayList(assets.filter { it.projectId == projectId && it.type == AssetType.IMAGE && !it.libraryDeleted }.map { it.id }.distinct())
    }

    fun export(destination: Uri?) {
        val projectId = saved.remove<String>("zipProject")
        val ids = saved.remove<ArrayList<String>>("zipIds")
        if (destination == null) return
        if (mutable.value.busy) return
        mutable.value = ProjectLibraryOperation(busy = true, progress = "Preparando ZIP…")
        operation = viewModelScope.launch {
            var temporary: File? = null
            try {
                withContext(Dispatchers.IO) {
                    check(projectId != null && !ids.isNullOrEmpty()) { "Selección no disponible" }
                    val assets = ids.map { id -> runtime.assets.asset(id)?.takeIf {
                        it.projectId == projectId && !it.libraryDeleted && it.type == AssetType.IMAGE
                    } ?: error("Una imagen ya no está disponible") }.distinctBy { it.uri }
                    // Complete the archive privately before touching the chosen destination.
                    val archive = File.createTempFile("project-images-", ".zip", application.cacheDir).also { temporary = it }
                    writeProjectMediaZip(assets, archive.outputStream(), ::open) { done ->
                        mutable.value = ProjectLibraryOperation(busy = true, progress = "Preparando ZIP · $done/${assets.size}")
                    }
                    application.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                        archive.inputStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                    } ?: error("Destino no disponible")
                }
                mutable.value = ProjectLibraryOperation(message = "ZIP guardado")
            } catch (error: Exception) {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { DocumentsContract.deleteDocument(application.contentResolver, destination) }
                }
                mutable.value = ProjectLibraryOperation(message = if (error is CancellationException) "Exportación cancelada" else
                    "No se pudo exportar el ZIP completo. Comprueba las imágenes, la conexión y el espacio disponible.")
                if (error is CancellationException) throw error
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { temporary?.delete() }
                call = null
            }
        }
    }

    private fun open(asset: Asset): InputStream {
        val uri = Uri.parse(asset.uri)
        if (uri.scheme == "http" || uri.scheme == "https") {
            val active = client.newCall(Request.Builder().url(asset.uri).build())
            call = active
            val response = active.execute()
            val body = response.body
            if (!response.isSuccessful || body == null || body.contentType()?.toString()?.startsWith("text/") == true) {
                response.close()
                error("Imagen remota no disponible")
            }
            return object : FilterInputStream(body.byteStream()) {
                override fun close() { try { super.close() } finally { response.close(); call = null } }
            }
        }
        return application.contentResolver.openInputStream(uri) ?: error("Imagen no disponible")
    }

    override fun onCleared() { call?.cancel(); super.onCleared() }
    companion object {
        private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES).build()
    }
}
