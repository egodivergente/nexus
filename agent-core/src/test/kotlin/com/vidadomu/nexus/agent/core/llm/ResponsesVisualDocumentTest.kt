package com.vidadomu.nexus.agent.core.llm

import kotlin.test.*
import kotlinx.serialization.json.*

class ResponsesVisualDocumentTest {
    @Test fun `visual PDF sends actual ordered pages and explicit coverage instead of a placeholder`() {
        val doc=ContentBlock.Document("pdf","Scan",byteArrayOf(80,68,70),
            listOf(ContentBlock.Image("jpeg",byteArrayOf(1,2)),ContentBlock.Image("jpeg",byteArrayOf(3,4))),
            "Páginas 1-2 de 9. LECTURA PARCIAL")
        val request=LlmRequest("test",emptyList(),listOf(LlmMessage(Role.USER,listOf(doc))),emptyList(),128,null)
        val payload=ResponsesCodec.encode(request,ResponsesOptions(),false)
        val content=payload["input"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray
        assertEquals(3,content.size)
        assertTrue(content[0].jsonObject["text"]!!.jsonPrimitive.content.contains("LECTURA PARCIAL"))
        assertEquals("data:image/jpeg;base64,AQI=",content[1].jsonObject["image_url"]!!.jsonPrimitive.content)
        assertEquals("data:image/jpeg;base64,AwQ=",content[2].jsonObject["image_url"]!!.jsonPrimitive.content)
        assertFalse(payload.toString().contains("no lo recibe"))
        assertFalse(payload.toString().contains("UERG"))
    }
}
