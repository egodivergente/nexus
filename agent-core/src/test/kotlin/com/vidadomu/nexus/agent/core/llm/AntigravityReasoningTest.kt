package com.vidadomu.nexus.agent.core.llm

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.*

class AntigravityReasoningTest {
    @Test fun `only confirmed Argon variants support reasoning`() {
        assertEquals("medium",AntigravityReasoning.effort("gemini-4-argon"))
        assertEquals("gemini-4-argon-medium",AntigravityReasoning.DEFAULT_MODEL)
        for(effort in AntigravityReasoning.efforts) assertEquals(effort,AntigravityReasoning.effort(AntigravityReasoning.model(effort)))
        assertNull(AntigravityReasoning.effort("gemini-3.8-flash"))
        assertNull(AntigravityReasoning.effort("gemini-4-argon-max"))
        assertFailsWith<IllegalArgumentException> {AntigravityReasoning.model("max")}
    }

    @Test fun `bridge receives exact confirmed variant in existing model field`() = runTest {
        MockWebServer().use {server ->
            for(effort in AntigravityReasoning.efforts) {
                server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream")
                    .setBody("data: {\"type\":\"final\",\"status\":\"completed\",\"text\":\"ok\"}\n\n"))
                val model=AntigravityReasoning.model(effort)
                AntigravityProvider(server.url("/").toString()).complete(LlmRequest(
                    modelId=model,system=emptyList(),messages=listOf(LlmMessage(Role.USER,listOf(ContentBlock.Text("Hola")))),tools=emptyList(),maxTokens=10))
                val sent=Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
                assertEquals(model,sent["model"]!!.jsonPrimitive.content)
                assertNull(sent["reasoning_effort"])
                assertNull(sent["effort"])
            }
        }
    }
}
