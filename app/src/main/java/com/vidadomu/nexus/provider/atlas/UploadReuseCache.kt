package com.vidadomu.nexus.provider.atlas

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reutiliza la URL de un archivo ya subido a Atlas: un lote de 20 encargos con la misma referencia la sube una vez.
 * Si dos encargos suben el mismo archivo a la vez, el segundo espera al primero. Las URL caducan aquí a los
 * [ttlMs] (prudente: Atlas no publica cuánto duran) y un fallo nunca se guarda, así que el siguiente intento vuelve a subir.
 */
internal class UploadReuseCache(
    private val ttlMs: Long = 15 * 60_000L,
    private val maxEntries: Int = 64,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(val url: String, val at: Long)

    private val done = LinkedHashMap<String, Entry>()
    private val inFlight = HashMap<String, CompletableDeferred<Result<String>>>()
    private val mutex = Mutex()

    /** Devuelve la URL y si se reutilizó (sin subir de nuevo). */
    suspend fun getOrUpload(key: String, upload: suspend () -> Result<String>): Pair<Result<String>, Boolean> {
        var waitFor: CompletableDeferred<Result<String>>? = null
        val mine = CompletableDeferred<Result<String>>()
        mutex.withLock {
            done[key]?.let { entry ->
                if (clock() - entry.at <= ttlMs) return Result.success(entry.url) to true
                done.remove(key)
            }
            val running = inFlight[key]
            if (running != null) waitFor = running else inFlight[key] = mine
        }
        waitFor?.let { running ->
            val shared = running.await()
            return shared to shared.isSuccess
        }
        val result = try {
            upload()
        } catch (e: CancellationException) {
            mutex.withLock { inFlight.remove(key) }
            mine.complete(Result.failure(e))
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        mutex.withLock {
            inFlight.remove(key)
            result.getOrNull()?.takeIf { it.isNotBlank() }?.let { url ->
                done[key] = Entry(url, clock())
                while (done.size > maxEntries) done.remove(done.keys.first())
            }
        }
        mine.complete(result)
        return result to false
    }

    companion object {
        /** Clave por contenido (no por nombre ni ruta): el mismo archivo copiado dos veces se reconoce igual. */
        fun contentKey(file: File, mimeType: String, account: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            val accountTag = MessageDigest.getInstance("SHA-256").digest(account.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
            return digest.digest().joinToString("") { "%02x".format(it) } + ":" + mimeType + ":" + accountTag
        }
    }
}
