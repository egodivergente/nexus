package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.agent.AttachmentDocumentLoader
import com.vidadomu.nexus.agent.core.agent.AttachmentImageLoader
import com.vidadomu.nexus.agent.core.agent.AttachmentVideoLoader
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.text
import com.vidadomu.nexus.agent.core.tools.Attachment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VideoAttachmentsTest {
    private val config = AgentConfig(modelId = "test", region = "us-east-1", endpoint = "http://proxy")
    private fun video(id: String) = Attachment(id, "file:///private/$id.mp4", "video/mp4", "$id.mp4")
    private fun frames(): List<ContentBlock> = buildList {
        add(ContentBlock.Text("Duración: 10 s"))
        repeat(6) { add(ContentBlock.Text("t=${it * 2} s")); add(ContentBlock.Image("jpeg", byteArrayOf(1, 2, 3))) }
    }

    @Test fun twoVideosDeliverFramesAndTimestampsAlongsideImageWithoutLoadingDocuments() = runTest {
        val h = Harness()
        var loaded = 0
        val llm = ScriptedLlmProvider(listOf({ request ->
            val content = request.messages.last().content
            assertEquals(13, content.filterIsInstance<ContentBlock.Image>().size)
            val labels = content.filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text }
            assertTrue(labels.contains("SIN audio"))
            assertTrue(labels.contains("muestreado"))
            assertTrue(labels.contains("t=10 s"))
            assertTrue(labels.contains("Duración: 10 s"))
            text("Veo las muestras.")
        }))
        val o = AgentOrchestrator(llm, config, emptyList(), h.skills, h.assets,
            images = AttachmentImageLoader { ContentBlock.Image("png", byteArrayOf(4)) },
            documents = AttachmentDocumentLoader { error("El vídeo no es un documento") },
            videos = AttachmentVideoLoader { loaded++; frames() })
        val attachments = listOf(video("one"), video("two"), Attachment("image", "file:///image.png", "image/png"))
        val conv = o.newConversation()
        assertEquals("Veo las muestras.", o.send(conv, "Describe lo visible", attachments).text)
        assertEquals(2, loaded)
        assertEquals(attachments, conv.session.attachments)
    }

    @Test fun disabledVisionDoesNotOpenVideosAndStatesTheyWereNotRead() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf({ request ->
            assertTrue(request.messages.last().content.none { it is ContentBlock.Image })
            assertTrue(request.messages.last().text().contains("NO se han leído"))
            text("No he visto el vídeo.")
        }))
        val o = AgentOrchestrator(llm, config.copy(sendImagesToLlm = false), emptyList(), h.skills, h.assets,
            videos = AttachmentVideoLoader { error("No debe leer con visión desactivada") })
        o.send(o.newConversation(), "Organiza estas referencias", listOf(video("one")))
    }

    @Test fun missingLoaderDoesNotPretendToReadTheVideo() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf({ request ->
            assertTrue(request.messages.last().content.none { it is ContentBlock.Image })
            assertTrue(request.messages.last().text().contains("NO se han leído"))
            text("Sólo tengo la referencia.")
        }))
        val o = AgentOrchestrator(llm, config, emptyList(), h.skills, h.assets)
        o.send(o.newConversation(), "Describe", listOf(video("one")))
    }

    @Test fun thirdVideoIsRejectedBeforeExtractionOrModelCallAndHistoryIsIntact() = runTest {
        val h = Harness()
        val o = AgentOrchestrator(ScriptedLlmProvider(emptyList()), config, emptyList(), h.skills, h.assets,
            videos = AttachmentVideoLoader { error("No debe decodificar antes de validar el límite") })
        val conv = o.newConversation()
        val error = assertFailsWith<IllegalArgumentException> {
            o.send(conv, "Describe", listOf(video("one"), video("two"), video("three")))
        }
        assertTrue(error.message.orEmpty().contains("límite actual es de 2 vídeos"), error.message)
        assertTrue(conv.history().isEmpty())
        assertEquals(3, conv.session.attachments.size)
    }

    @Test fun incompleteExtractionFailsBeforeInference() = runTest {
        val h = Harness()
        val o = AgentOrchestrator(ScriptedLlmProvider(emptyList()), config, emptyList(), h.skills, h.assets,
            videos = AttachmentVideoLoader { listOf(ContentBlock.Text("No se pudo leer")) })
        val conv = o.newConversation()
        assertFailsWith<IllegalArgumentException> { o.send(conv, "Describe", listOf(video("one"))) }
        assertTrue(conv.history().isEmpty())
    }

    @Test fun cancellationIsNotConvertedToFakeVideoAnalysis() = runTest {
        val h = Harness()
        val o = AgentOrchestrator(ScriptedLlmProvider(emptyList()), config, emptyList(), h.skills, h.assets,
            videos = AttachmentVideoLoader { throw CancellationException("cancelled") })
        val conv = o.newConversation()
        assertFailsWith<CancellationException> { o.send(conv, "Describe", listOf(video("one"))) }
        assertTrue(conv.history().isEmpty())
    }
}
