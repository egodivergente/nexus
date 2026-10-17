package com.vidadomu.nexus.core.network

import com.vidadomu.nexus.core.model.ProviderError
import com.vidadomu.nexus.provider.atlas.NativeModelSchema
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Requests are intercepted in memory: no external network or paid generation. */
class NativeSchemaRetryTest {
    private val url = "https://static.atlascloud.ai/model/schema/test.json"

    @Test fun transientFailureRetriesOnlyOneGet() = runBlocking {
        val methods = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            methods.add(chain.request().method)
            if (methods.size == 1) throw IOException("Connection reset")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("schema".toResponseBody()).build()
        }.build()
        try {
            assertEquals("schema", NativeModelSchema.fetchWithRetry(url, client, 0))
            assertEquals(listOf("GET", "GET"), methods.toList())
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun persistentTlsFailureStopsAfterTwoAttempts() = runBlocking {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor {
            calls.incrementAndGet()
            throw SSLHandshakeException("Trust anchor for certification path not found")
        }.build()
        try {
            val error = runCatching { NativeModelSchema.fetchWithRetry(url, client, 0) }.exceptionOrNull()
            assertTrue(error is ProviderError)
            assertEquals("TLS_UNTRUSTED", (error as ProviderError).errorCode)
            assertEquals(2, calls.get())
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun httpRejectionIsNotRetried() = runBlocking {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(404).message("Not Found").body("missing".toResponseBody()).build()
        }.build()
        try {
            val error = runCatching { NativeModelSchema.fetchWithRetry(url, client, 0) }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertTrue(error!!.message!!.contains("HTTP 404"))
            assertEquals(1, calls.get())
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun cancellationStopsTheScheduledRetry() = runBlocking {
        val attempted = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor {
            calls.incrementAndGet()
            attempted.complete(Unit)
            throw IOException("Connection reset")
        }.build()
        try {
            val job = launch { NativeModelSchema.fetchWithRetry(url, client, 60_000) }
            withTimeout(5_000) { attempted.await() }
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertEquals(1, calls.get())
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun persistentIoFailureIsPreserved() = runBlocking {
        val calls = AtomicInteger()
        val failure = IOException("Connection reset")
        val client = OkHttpClient.Builder().addInterceptor {
            calls.incrementAndGet()
            throw failure
        }.build()
        try {
            val error = runCatching { NativeModelSchema.fetchWithRetry(url, client, 0) }.exceptionOrNull()
            // Coroutine stack-trace recovery may copy IOException; identity is not part of the contract.
            assertNotNull(error)
            assertEquals(IOException::class.java, error!!.javaClass)
            assertEquals(failure.message, error.message)
            assertEquals(2, calls.get())
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
}
