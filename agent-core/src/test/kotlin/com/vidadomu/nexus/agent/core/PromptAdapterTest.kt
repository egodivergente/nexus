package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.llm.*
import com.vidadomu.nexus.agent.core.registry.TextLimit
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PromptAdapterTest {
    private val requests = mutableListOf<LlmRequest>()
    private fun adapter(vararg replies: Pair<String, StopReason>): PromptAdapter {
        var next = 0
        val provider = object : LlmProvider {
            override val id = "fixture"
            override suspend fun complete(request: LlmRequest): LlmResponse {
                requests += request
                val (text, stop) = replies[next++]
                return LlmResponse(LlmMessage(Role.ASSISTANT, listOf(ContentBlock.Text(text))), stop, Usage(0,0))
            }
        }
        return PromptAdapter({ provider }, { "fixture" })
    }
    @Test fun leavesUnknownAndAlreadyFittingPromptsAlone() = runBlocking {
        val a = adapter()
        assertEquals("long original", a.adapt("long original", null, "Unknown"))
        assertEquals("😀ab", a.adapt("😀ab", TextLimit("unicode_code_points", maximum=3), "Atlas"))
        assertTrue(requests.isEmpty())
    }
    @Test fun retriesOversizedOutputWithoutTruncatingOriginal() = runBlocking {
        val a = adapter("too long" to StopReason.END_TURN, "😀a" to StopReason.END_TURN)
        assertEquals("😀a", a.adapt("original unabridged", TextLimit("utf16_code_units",maximum=3), "Runway"))
        assertEquals(2, requests.size)
        assertTrue(requests.all { it.tools.isEmpty() && it.messages.single().text().contains("original unabridged") })
    }
    @Test fun doesNotAcceptPartialModelOutput() = runBlocking {
        val a = adapter("a" to StopReason.MAX_TOKENS, "ok" to StopReason.END_TURN)
        assertEquals("ok",a.adapt("original",TextLimit("unicode_code_points",maximum=2),"Test"))
        assertEquals(2,requests.size)
    }
    @Test fun persistentRewriteFailureIsExplicit() = runBlocking {
        val a = adapter("too long" to StopReason.END_TURN,"still long" to StopReason.END_TURN)
        assertFailsWith<IllegalStateException> { a.adapt("original",TextLimit("utf16_code_units",maximum=2),"Test") }
        assertEquals(2,requests.size)
    }
    @Test fun filteredOutputIsNotMistakenForTheUserPrompt() = runBlocking {
        val a = adapter("blocked" to StopReason.CONTENT_FILTERED,"ok" to StopReason.END_TURN)
        assertEquals("ok",a.adapt("original prompt",TextLimit("unicode_code_points",maximum=8),"Test"))
        assertEquals(2,requests.size)
    }
}
