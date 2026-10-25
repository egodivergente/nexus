package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.core.work.promoteToForeground
import android.content.Context
import androidx.work.*
import com.vidadomu.nexus.agent.core.tools.ToolInputException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Pinned multilingual Base model. Only model weights cross the network; audio stays local. */
internal class WhisperModelStore(private val context:Context) {
    val folder=File(context.filesDir,"whisper_models").apply {mkdirs()}
    val model=File(folder,"ggml-base.bin")
    private val prefs=context.getSharedPreferences("whisper_model_v1",Context.MODE_PRIVATE)
    fun ready()=model.isFile && model.length()==SIZE && prefs.getString("verified_sha",null)==SHA
    fun requireModel():File = if(ready()) model else throw ToolInputException("Descarga Whisper local en Ajustes del agente → Edición y Whisper (148 MB).")
    suspend fun verifyExisting():Boolean = withContext(Dispatchers.IO) {
        if(ready()) return@withContext true
        if(!model.isFile || model.length()!=SIZE) return@withContext false
        val digest=MessageDigest.getInstance("SHA-256")
        model.inputStream().use {input ->
            val bytes=ByteArray(65536)
            while(true) {coroutineContext.ensureActive();val n=input.read(bytes);if(n<0) break;digest.update(bytes,0,n)}
        }
        if(digest.digest().joinToString("") {"%02x".format(it)}!=SHA) return@withContext false
        prefs.edit().putString("verified_sha",SHA).putString("state","READY").remove("error").putInt("progress",100).commit()
        true
    }
    fun status()=buildJsonObject {
        put("model","Whisper Base multilingüe");put("ready",ready());put("download_bytes",SIZE)
        put("audio_uploaded",false);put("state",if(ready()) "READY" else prefs.getString("state","NOT_DOWNLOADED"))
        put("download_progress",prefs.getInt("progress",0));prefs.getString("error",null)?.let {put("error",it)}
    }
    fun enqueue():JsonObject {
        if(!ready()) {
            val work=OneTimeWorkRequestBuilder<WhisperModelWorker>().setConstraints(Constraints.Builder()
                .setRequiredNetworkType(if(model.isFile && model.length()==SIZE) NetworkType.NOT_REQUIRED else NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork("nexus-whisper-model",ExistingWorkPolicy.KEEP,work)
        }
        return status()
    }
    suspend fun download()=withContext(Dispatchers.IO) {
        if(verifyExisting()) return@withContext
        val stage=File(folder,"ggml-base.part")
        prefs.edit().putString("state","DOWNLOADING").remove("error").putInt("progress",0).apply()
        val client=OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).build()
        val digest=MessageDigest.getInstance("SHA-256")
        try {
            if(folder.usableSpace<SIZE+64*1024*1024) throw ToolInputException("No hay espacio suficiente para descargar Whisper.")
            client.newCall(Request.Builder().url(URL).build()).execute().use {response ->
                if(!response.isSuccessful) throw ToolInputException("No se pudo descargar el modelo (HTTP ${response.code}).")
                val body=response.body ?: throw ToolInputException("Descarga vacía.")
                if(body.contentLength() !in setOf(-1L,SIZE)) throw ToolInputException("El tamaño del modelo no coincide con el esperado.")
                var total=0L;var last=0
                body.byteStream().use {input ->stage.outputStream().use {out ->
                    val bytes=ByteArray(65536)
                    while(true) {
                        coroutineContext.ensureActive()
                        val n=input.read(bytes);if(n<0) break
                        total+=n;if(total>SIZE) throw ToolInputException("El modelo supera el tamaño verificado.")
                        digest.update(bytes,0,n);out.write(bytes,0,n)
                        val progress=(total*100/SIZE).toInt()
                        if(progress!=last) {last=progress;prefs.edit().putInt("progress",progress).apply()}
                    }
                    out.fd.sync()
                }}
                val sha=digest.digest().joinToString("") {"%02x".format(it)}
                if(total!=SIZE || sha!=SHA) throw ToolInputException("La integridad del modelo no coincide. No se utilizará esta descarga.")
                if(!stage.renameTo(model)) throw ToolInputException("No se pudo guardar el modelo.")
                prefs.edit().putString("verified_sha",SHA).putString("state","READY").putInt("progress",100).apply()
            }
        } catch(e:Exception) {
            prefs.edit().putString("state","FAILED").putString("error",(e as? ToolInputException)?.message ?: "No se pudo descargar Whisper. Puedes reintentar.").apply()
            throw e
        } finally {stage.delete()}
    }
    companion object {
        const val SIZE=147951465L
        const val SHA="60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe"
        const val URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
    }
}

class WhisperModelWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result = try {
        val store=WhisperModelStore(applicationContext)
        if(!store.verifyExisting()) {
            promoteToForeground(MediaEditWorker.foreground(applicationContext,"Whisper", "Descargando modelo local de 148 MB"))
            store.download()
        }
        Result.success()
    } catch(e:kotlinx.coroutines.CancellationException) {throw e}
      catch(e:Exception) {Result.failure()}
}
