package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.agent.AttachmentVideoLoader
import com.vidadomu.nexus.agent.core.agent.SystemPrompt
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentLimits
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.config.LimitOverrides
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.text
import com.vidadomu.nexus.agent.core.tools.Attachment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Los límites son ajustes del usuario: subirlos, bajarlos o quitarlos cambia el comportamiento real. */
class ConfigurableLimitsTest {
    private val base = AgentConfig(modelId = "test", region = "r", endpoint = "e")
    private fun video(id: String) = Attachment(id, "file:///private/$id.mp4", "video/mp4", "$id.mp4")
    private fun frames(): List<ContentBlock> = buildList {
        add(ContentBlock.Text("Duración: 10 s"))
        repeat(6) { add(ContentBlock.Text("t=${it * 2} s")); add(ContentBlock.Image("jpeg", byteArrayOf(1))) }
    }

    @Test fun `con 0 no hay tope de videos y con 4 se aceptan cuatro`() = runTest {
        for (limit in listOf(0, 4)) {
            val h = Harness()
            val llm = ScriptedLlmProvider(listOf({ request ->
                assertEquals(24, request.messages.last().content.filterIsInstance<ContentBlock.Image>().size)
                text("ok")
            }))
            val o = AgentOrchestrator(llm, base.copy(limits = AgentLimits(maxVideosPerTurn = limit)), emptyList(), h.skills, h.assets,
                videos = AttachmentVideoLoader { frames() })
            assertEquals("ok", o.send(o.newConversation(), "Describe", (1..4).map { video("v$it") }).text, "límite=$limit")
        }
    }

    @Test fun `un limite menor tambien se respeta y el aviso cita la cifra del usuario`() = runTest {
        val h = Harness()
        val o = AgentOrchestrator(ScriptedLlmProvider(emptyList()), base.copy(limits = AgentLimits(maxVideosPerTurn = 1)), emptyList(), h.skills, h.assets,
            videos = AttachmentVideoLoader { error("no debe leer") })
        val e = assertFailsWith<IllegalArgumentException> { o.send(o.newConversation(), "x", listOf(video("a"), video("b"))) }
        assertTrue("límite actual es de 1 vídeos" in e.message.orEmpty(), e.message)
    }

    @Test fun `el prompt cita los limites vigentes y no una cifra propia de Flow`() {
        val limits = AgentLimits(flowMaxResults = 7, maxToolRounds = 0, maxImagesPerTurn = 9)
        val prompt = SystemPrompt.build(AgentMode.AGENT, null, "", null, "2026-10-17", null, limits.promptBlock()).joinToString("\n")
        assertTrue("hasta 7" in prompt && "Pasos seguidos por mensaje: sin límite" in prompt && "Imágenes por mensaje que ves: 9" in prompt, prompt.takeLast(700))
        assertTrue("prevalecen" in prompt)
        assertFalse("hasta 20 resultados" in prompt, "el prompt no debe fijar 20 resultados")
    }

    @Test fun `sin limite es un numero grande que no desborda al sumar`() {
        val l = AgentLimits(maxToolRounds = 0, maxMessages = 0)
        assertTrue(l.rounds + 1 > 0 && l.messages + 1 > 0)
        assertEquals(null, AgentLimits(turnTimeoutSeconds = 0).turnTimeoutMillis)
        assertEquals(30_000L, AgentLimits(turnTimeoutSeconds = 30).turnTimeoutMillis)
    }

    @Test fun `un cerebro puede sustituir los globales y lo que no define hereda`() {
        val global = AgentLimits(maxToolRounds = 20, turnTimeoutSeconds = 600, maxTokens = 4096)
        val merged = global.with(LimitOverrides(maxToolRounds = 0, maxTokens = 200_000))
        assertEquals(0, merged.maxToolRounds)
        assertEquals(600, merged.turnTimeoutSeconds)
        assertEquals(200_000, merged.maxTokens)
    }

    @Test fun `la validacion solo rechaza negativos, sin topes artificiales`() {
        assertTrue(AgentLimits(maxTokens = 5_000_000, maxToolRounds = 100_000).problems().isEmpty())
        assertTrue(AgentLimits(maxToolRounds = -1).problems().isNotEmpty())
        assertTrue(LimitOverrides(turnUsd = 0.0, dailyUsd = 5000.0, httpTimeoutSeconds = 0).problems().isEmpty())
        assertTrue(LimitOverrides(turnUsd = -1.0).problems().isNotEmpty())
    }
}
