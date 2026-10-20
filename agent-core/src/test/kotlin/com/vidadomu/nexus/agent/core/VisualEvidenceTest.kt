package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.*
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.*
import com.vidadomu.nexus.agent.core.tools.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class VisualEvidenceTest {
    private val config = AgentConfig(modelId="test", region="us-east-1", endpoint="http://test")
    private fun video(id: String) = Attachment(id, "file:///private/$id.mp4", "video/mp4", "$id.mp4", durationSec=6.63, assetId=id)
    private fun frames(count: Int = 6) = (0 until count).flatMap { index -> listOf(
        ContentBlock.Text("t≈${index * 1000} ms"), ContentBlock.Image("jpeg", byteArrayOf(1,2,3))) }
    private fun evidence(request: LlmRequest) = request.system.last()

    @Test fun twoAssetsWithIdenticalImageBytesKeepSeparateIdentityAndTimes() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf({ request ->
            val context = evidence(request)
            assertTrue(context.contains("\"asset_id\":\"tree\""))
            assertTrue(context.contains("\"asset_id\":\"bars\""))
            assertEquals(2, Regex("\"available_images\":6").findAll(context).count())
            assertTrue(context.contains("t≈5000 ms"))
            assertFalse(context.contains("file:///private"))
            val labels = request.messages.last().text()
            assertTrue(labels.contains("INICIO DE EVIDENCIA VISUAL"))
            assertTrue(labels.contains("FIN DE EVIDENCIA VISUAL"))
            ScriptedLlmProvider.text("Muestras identificadas")
        }))
        val o = AgentOrchestrator(llm, config, emptyList(), h.skills, h.assets, videos=AttachmentVideoLoader {frames()})
        o.send(o.newConversation(), "Compara", listOf(video("tree"), video("bars")))
    }

    @Test fun compactedPixelsAreNotReportedAsAvailableDespiteEarlierAssistantClaims() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf(
            { ScriptedLlmProvider.text("Hay césped y un chorro blanco") },
            { ScriptedLlmProvider.text("De acuerdo") },
            { request ->
                assertTrue(request.messages.any { it.text().contains("Hay césped") })
                assertTrue(request.messages.none { m -> m.content.any { it is ContentBlock.Image } })
                assertTrue(evidence(request).contains("PIXELS_NOT_IN_CONTEXT"))
                assertTrue(evidence(request).contains("\"available_images\":0"))
                ScriptedLlmProvider.text("Necesito volver a abrir el archivo para comprobarlo")
            }
        ))
        val o = AgentOrchestrator(llm, config, emptyList(), h.skills, h.assets, videos=AttachmentVideoLoader {frames()})
        val conv = o.newConversation()
        o.send(conv, "Describe", listOf(video("tree")))
        o.send(conv, "Espera")
        o.send(conv, "¿Qué césped?")
    }

    @Test fun restoredTextHistoryCannotClaimToCarryVisualEvidence() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf({ request ->
            assertTrue(evidence(request).contains("\"images_in_request\":0"))
            assertTrue(evidence(request).contains("\"sources\":[]"))
            ScriptedLlmProvider.text("Vuelvo a comprobar el asset")
        }))
        val o = AgentOrchestrator(llm, config, emptyList(), h.skills, h.assets)
        val conv = o.newConversation()
        conv.restoreTextHistory(listOf("user" to "Mira el árbol", "assistant" to "Veo césped"))
        o.send(conv, "Compruébalo")
    }

    @Test fun incompleteLibraryVideoDoesNotLeakPartialFramesToNextRequest() = runTest {
        val h = Harness()
        val a = video("bars")
        val tool = libraryTool(a)
        val llm = ScriptedLlmProvider(listOf(
            { ScriptedLlmProvider.tools(ContentBlock.ToolUse("open", tool.name, buildJsonObject {put("asset_id", a.id)})) },
            { request ->
                assertTrue(request.messages.last().content.none {it is ContentBlock.Image})
                assertTrue(request.messages.last().text().contains("no se ha analizado su contenido"))
                assertTrue(evidence(request).contains("\"sources\":[]"))
                ScriptedLlmProvider.text("No pude cargar el vídeo")
            }
        ))
        val o = AgentOrchestrator(llm, config, listOf(tool), h.skills, h.assets, videos=AttachmentVideoLoader {frames(5)})
        o.send(o.newConversation(), "Mira el vídeo")
    }

    @Test fun reopeningSameLibraryVideoLoadsPixelsAgainAndRefreshesTheManifest() = runTest {
        val h = Harness()
        val a = video("bars")
        val tool = libraryTool(a)
        fun open(id: String) = ScriptedLlmProvider.tools(ContentBlock.ToolUse(id, tool.name, buildJsonObject {put("asset_id", a.id)}))
        var loads = 0
        val llm = ScriptedLlmProvider(listOf(
            {open("first")}, {ScriptedLlmProvider.text("Muestras vistas")},
            {open("again")}, { request ->
                assertEquals(6, request.messages.last().content.filterIsInstance<ContentBlock.Image>().size)
                assertTrue(evidence(request).contains("\"available_images\":6"))
                ScriptedLlmProvider.text("Comprobado de nuevo")
            }
        ))
        val o = AgentOrchestrator(llm, config, listOf(tool), h.skills, h.assets, videos=AttachmentVideoLoader {loads++; frames()})
        val conv = o.newConversation()
        o.send(conv, "Revisa")
        o.send(conv, "No veo eso, compruébalo")
        assertEquals(2, loads)
    }

    @Test fun visualRulesCannotBeReplacedByCustomPrompt() {
        val system = SystemPrompt.build(AgentMode.AGENT, "Responde como director", "", null, "2026-10-20")
        assertTrue(system.contains(SystemPrompt.VISUAL_EVIDENCE_RULES))
    }

    @Test fun emptyVideoBytesFailBeforeCallingTheModel() = runTest {
        val h = Harness()
        val o = AgentOrchestrator(ScriptedLlmProvider(emptyList()), config, emptyList(), h.skills, h.assets,
            videos=AttachmentVideoLoader { List(6) { ContentBlock.Image("jpeg", byteArrayOf()) } })
        assertFailsWith<IllegalArgumentException> {o.send(o.newConversation(), "Revisa", listOf(video("bars")))}
    }

    private fun libraryTool(a: Attachment) = object : AgentTool {
        override val name = "view_library_asset"
        override val description = "Open"
        override val category = ToolCategory.READ
        override val inputSchema = JsonObject(emptyMap())
        override suspend fun run(input: JsonObject, session: AgentSession): ToolOutcome {
            if (session.attachment(a.id) == null) session.attachments += a
            return ToolOutcome(buildJsonObject {put("requested_context_load", true)})
        }
    }
}
