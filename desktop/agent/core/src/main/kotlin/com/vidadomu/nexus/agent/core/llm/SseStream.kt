package com.vidadomu.nexus.agent.core.llm

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Un evento de Server-Sent Events: [event] puede estar vacío (solo `data:`). */
internal data class SseEvent(val event: String, val data: String)

/**
 * Lee una respuesta `text/event-stream` línea a línea SIN esperar a que termine, y cancela la llamada si la
 * corrutina se cancela. Un error HTTP se entrega al llamante con su cuerpo (para traducirlo a un mensaje).
 */
internal object SseStream {
    suspend fun open(client: okhttp3.OkHttpClient, request: Request): Response = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

    /** Recorre los eventos hasta el fin o hasta que [onEvent] devuelva false. */
    suspend fun read(response: Response, onEvent: (SseEvent) -> Boolean) = withContext(Dispatchers.IO) {
        val source = response.body?.source() ?: return@withContext
        var event = ""
        val data = StringBuilder()
        fun flush(): Boolean {
            if (data.isEmpty() && event.isEmpty()) return true
            val ev = SseEvent(event, data.toString().trimEnd('\n'))
            event = ""; data.setLength(0)
            return onEvent(ev)
        }
        while (true) {
            currentCoroutineContext().ensureActive()
            val line = source.readUtf8Line() ?: break
            when {
                line.isEmpty() -> if (!flush()) return@withContext
                line.startsWith(":") -> Unit
                line.startsWith("event:") -> event = line.substring(6).trim()
                line.startsWith("data:") -> { if (data.isNotEmpty()) data.append('\n'); data.append(line.substring(5).removePrefix(" ")) }
            }
        }
        flush()
    }
}
