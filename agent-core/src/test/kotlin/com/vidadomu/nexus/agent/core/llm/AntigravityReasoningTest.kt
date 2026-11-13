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

    @Test fun `Hydrogen has its own four levels and switching keeps the level when it exists`() {
        assertEquals(listOf("low", "medium", "high", "xhigh"), AntigravityReasoning.efforts("gemini-4-hydrogen-high"))
        assertEquals("hydrogen", AntigravityReasoning.family(AntigravityReasoning.HYDROGEN_MODEL))
        assertEquals("xhigh", AntigravityReasoning.effort("gemini-4-hydrogen-xhigh"))
        assertEquals("gemini-4-hydrogen-xhigh", AntigravityReasoning.model("gemini-4-hydrogen-low", "xhigh"))
        // Argon no tiene «muy alto»; el mensaje dice lo que sí admite.
        val e = assertFailsWith<IllegalArgumentException> { AntigravityReasoning.model("gemini-4-argon-low", "xhigh") }
        assertTrue("bajo, medio, alto" in e.message!!)
        assertNull(AntigravityReasoning.effort("gemini-4-hydrogen-max"))
        assertNull(AntigravityReasoning.family("gemini-4-carbon-medium"))
        // Cambiar de modelo: Argon alto → Hydrogen alto; Hydrogen muy alto → Argon con su nivel por defecto; un modelo sin niveles queda igual.
        assertEquals("gemini-4-hydrogen-high", AntigravityReasoning.switchTo(AntigravityReasoning.HYDROGEN_MODEL, "gemini-4-argon-high"))
        assertEquals(AntigravityReasoning.DEFAULT_MODEL, AntigravityReasoning.switchTo(AntigravityReasoning.DEFAULT_MODEL, "gemini-4-hydrogen-xhigh"))
        assertEquals("gemini-3.8-flash", AntigravityReasoning.switchTo("gemini-3.8-flash", "gemini-4-argon-high"))
        assertEquals(AntigravityReasoning.HYDROGEN_MODEL, AntigravityReasoning.switchTo(AntigravityReasoning.HYDROGEN_MODEL, "gemini-3.8-flash"))
    }

    @Test fun `bridge receives exact confirmed variant in existing model field`() = runTest {
        MockWebServer().use {server ->
            val variants = AntigravityReasoning.families.flatMap { (name, levels) -> levels.map { "gemini-4-$name-$it" } }
            for(model in variants) {
                server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream")
                    .setBody("data: {\"type\":\"final\",\"status\":\"completed\",\"text\":\"ok\"}\n\n"))
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
