package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.llm.*
import kotlinx.serialization.json.*
import kotlin.test.*

class ReasoningCodecTest {
    @Test fun signedReasoningRoundTripsWithoutBecomingVisibleText() {
        val original=Json.parseToJsonElement("""{"role":"assistant","content":[{"reasoningContent":{"reasoningText":{"text":"private reasoning","signature":"signed-context"}}},{"toolUse":{"toolUseId":"t1","name":"inspect_media_asset","input":{"asset_id":"a1"}}}]}""").jsonObject
        val message=ConverseCodec.decodeMessage(original)
        assertEquals(original,ConverseCodec.encodeMessage(message))
        assertEquals("",message.text())
        assertFalse(message.content.first().toString().contains("private reasoning"))
        assertEquals("inspect_media_asset",message.toolUses().single().name)
        val result=LlmMessage(Role.USER,listOf(ContentBlock.ToolResult("t1",JsonPrimitive("ready"))))
        val next=ConverseCodec.encodeRequest(LlmRequest("us.anthropic.claude-opus-5-5",emptyList(),listOf(message,result),emptyList(),4096,temperature=.2))
        assertEquals(original,next["messages"]!!.jsonArray.first())
        assertFalse("temperature" in next["inferenceConfig"]!!.jsonObject)
    }
    @Test fun redactedReasoningIsPreservedAndLegacySamplingIsUnchanged() {
        val original=Json.parseToJsonElement("""{"role":"assistant","content":[{"reasoningContent":{"redactedContent":"AQID"}},{"text":"Listo"}]}""").jsonObject
        assertEquals(original,ConverseCodec.encodeMessage(ConverseCodec.decodeMessage(original)))
        val request=ConverseCodec.encodeRequest(LlmRequest("us.anthropic.claude-sonnet-4-6",emptyList(),emptyList(),emptyList(),100,temperature=.2))
        assertEquals(.2,request["inferenceConfig"]!!.jsonObject["temperature"]!!.jsonPrimitive.double)
    }
}
