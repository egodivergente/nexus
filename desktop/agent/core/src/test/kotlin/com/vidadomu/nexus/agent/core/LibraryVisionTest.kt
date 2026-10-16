package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.*
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.llm.*
import com.vidadomu.nexus.agent.core.tools.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class LibraryVisionTest {
    @Test fun cancellingLibraryLoadStillAnswersTheToolCall() = runTest {
        val h=Harness()
        val tool=object : AgentTool {
            override val name="view_library_asset"
            override val description="Open"
            override val category=ToolCategory.READ
            override val inputSchema=JsonObject(emptyMap())
            override suspend fun run(input:JsonObject,session:AgentSession):ToolOutcome {
                session.attachments.add(Attachment("image","file:///image.png","image/png"))
                return ToolOutcome(buildJsonObject {})
            }
        }
        val llm=ScriptedLlmProvider(listOf({ScriptedLlmProvider.tools(ContentBlock.ToolUse("call",tool.name,buildJsonObject {put("asset_id","image")}))}))
        val o=AgentOrchestrator(llm,AgentConfig(modelId="test",region="us-east-1",endpoint="http://test"),listOf(tool),h.skills,h.assets,
            images=AttachmentImageLoader {throw kotlinx.coroutines.CancellationException("cancel")})
        val conversation=o.newConversation()
        assertFailsWith<kotlinx.coroutines.CancellationException> {o.send(conversation,"Mira la imagen")}
        assertEquals("call",conversation.history().last().content.filterIsInstance<ContentBlock.ToolResult>().single().toolUseId)
    }
    @Test fun selectedLibraryImageIsLoadedInTheSameTurnAndCanBeReopened() = runTest {
        val h = Harness()
        val attachment = Attachment("library-image", "file:///private/image.png", "image/png", "image.png")
        val tool = object : AgentTool {
            override val name = "view_library_asset"
            override val description = "Open an indexed library file"
            override val category = ToolCategory.READ
            override val inputSchema = JsonObject(emptyMap())
            override suspend fun run(input: JsonObject, session: AgentSession): ToolOutcome {
                if (session.attachments.none { it.id == attachment.id }) session.attachments += attachment
                return ToolOutcome(buildJsonObject { put("requested_context_load", true) })
            }
        }
        fun request(id: String) = ScriptedLlmProvider.tools(ContentBlock.ToolUse(id, tool.name, buildJsonObject { put("asset_id", attachment.id) }))
        fun assertImage(request: LlmRequest): LlmResponse {
            assertEquals(1, request.messages.last().content.filterIsInstance<ContentBlock.Image>().size)
            assertEquals(1, request.messages.last().content.filterIsInstance<ContentBlock.ToolResult>().size)
            return ScriptedLlmProvider.text("Imagen vista")
        }
        val llm = ScriptedLlmProvider(listOf({request("one")}, ::assertImage, {request("two")}, ::assertImage))
        var loads = 0
        val o = AgentOrchestrator(llm, AgentConfig(modelId="test",region="us-east-1",endpoint="http://test"), listOf(tool), h.skills, h.assets,
            images=AttachmentImageLoader {loads++; ContentBlock.Image("png", byteArrayOf(1,2,3))})
        val conversation = o.newConversation()
        o.send(conversation, "Abre la imagen")
        o.send(conversation, "Vuelve a abrir la imagen")
        assertEquals(2, loads)
        assertEquals(listOf(attachment), conversation.session.attachments)
    }
}
