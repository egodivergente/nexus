package com.vidadomu.nexus.provider.flow

import com.vidadomu.nexus.FakeSecureStorage
import com.vidadomu.nexus.ui.settings.FLOW_RECOVERY_KEY
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class FlowBridgeClientConnectionTest {
    private fun status(ready: Boolean) = MockResponse().setBody(
        """{"bridge_status":"connected","generation_ready":$ready,"expected_outputs":["image","video"]}""")
        .addHeader("Content-Type", "application/json")

    private fun bridge(primary: MockWebServer, backup: MockWebServer, seconds: Int) = FlowBridgeClient(
        OkHttpClient(), FakeSecureStorage().apply {saveProviderApiKey(FLOW_RECOVERY_KEY,"offline-test-token")},
        hosts=listOf("a" to primary.url("/api/flow").toString(),"s" to backup.url("/api/flow").toString()),
        probeTimeoutSeconds={seconds})

    @Test fun primaryTakingMoreThanFiveSecondsStaysSelectedAndIsOnlyQueriedOnce() = runBlocking {
        MockWebServer().use {primary->MockWebServer().use {backup->
            primary.enqueue(status(true).setHeadersDelay(5500,TimeUnit.MILLISECONDS))
            backup.enqueue(status(false))
            val state=bridge(primary,backup,8).status().getOrThrow()
            assertTrue(state.isReady);assertTrue(state.detail.orEmpty().contains("AXELPC"))
            assertEquals(1,primary.requestCount);assertEquals(0,backup.requestCount)
            assertEquals("Bearer offline-test-token",primary.takeRequest().getHeader("Authorization"))
        }}
    }

    @Test fun anUnreadyPrimaryUsesTheBackup() = runBlocking {
        MockWebServer().use {primary->MockWebServer().use {backup->
            primary.enqueue(status(false));backup.enqueue(status(true))
            val state=bridge(primary,backup,8).status().getOrThrow()
            assertTrue(state.isReady);assertTrue(state.detail.orEmpty().contains("ASUS"))
            assertEquals(1,primary.requestCount);assertEquals(1,backup.requestCount)
        }}
    }

    @Test fun workspaceKeepsTheExactObservedLabelsAndDoesNotSubmit() = runBlocking {
        MockWebServer().use {primary->MockWebServer().use {backup->
            primary.enqueue(status(true))
            primary.enqueue(MockResponse().setBody("""{"project_name":"Escena 4","model_name":"Veo 3.1 Fast","aspect_ratio":"16:9"}"""))
            val state=bridge(primary,backup,8).workspaceStatus().getOrThrow()
            assertEquals("Escena 4",state.projectName);assertEquals("Veo 3.1 Fast",state.modelName);assertEquals("16:9",state.aspectRatio)
            assertEquals("GET",primary.takeRequest().method)
            val workspace=primary.takeRequest();assertEquals("GET",workspace.method);assertEquals("/api/flow/workspace",workspace.path)
            assertEquals(0,backup.requestCount)
        }}
    }

    @Test fun configuredProbeTimeoutStillAllowsFallback() = runBlocking {
        MockWebServer().use {primary->MockWebServer().use {backup->
            primary.enqueue(status(true).setHeadersDelay(1600,TimeUnit.MILLISECONDS));backup.enqueue(status(true))
            val state=bridge(primary,backup,1).status().getOrThrow()
            assertTrue(state.detail.orEmpty().contains("ASUS"))
            assertEquals(1,backup.requestCount)
        }}
    }

    @Test fun cancellingAProbeDoesNotContactTheBackup() = runBlocking {
        MockWebServer().use {primary->MockWebServer().use {backup->
            primary.enqueue(status(true).setHeadersDelay(1,TimeUnit.SECONDS))
            try {withTimeout(150) {bridge(primary,backup,8).status()};fail("Expected cancellation")}
            catch (_: TimeoutCancellationException) {assertEquals(0,backup.requestCount)}
        }}
    }
}
