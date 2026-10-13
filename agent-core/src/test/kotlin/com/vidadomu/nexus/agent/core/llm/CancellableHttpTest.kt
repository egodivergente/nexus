package com.vidadomu.nexus.agent.core.llm

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.TimeUnit
import kotlin.test.*

class CancellableHttpTest {
    @Test fun `cancelar interrumpe la llamada HTTP sin esperar al timeout`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val call=OkHttpClient().newCall(Request.Builder().url(server.url("/slow")).build())
            val pending=launch(Dispatchers.IO) {call.awaitBody().close()}
            assertNotNull(server.takeRequest(5,TimeUnit.SECONDS))
            withTimeout(2000) {pending.cancelAndJoin()}
            assertTrue(call.isCanceled())
        }
    }
}
