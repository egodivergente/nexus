package com.vidadomu.nexus.agent.core.llm

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Keep cancellation connected until the response body has finished downloading. */
internal suspend fun Call.awaitBody(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            try {
                val buffered=response.use {
                    val body=it.body
                    it.newBuilder().body((body?.bytes() ?: byteArrayOf()).toResponseBody(body?.contentType())).build()
                }
                continuation.resume(buffered)
            } catch(e: Exception) {response.close();continuation.resumeWithException(e)}
        }
    })
}
