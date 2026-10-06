package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.OfflinePlannerProvider
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.text
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.tools
import com.vidadomu.nexus.agent.core.spend.PlanState
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.agent.core.tools.NexusToolkit
import com.vidadomu.nexus.agent.core.tools.UiEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class OfflinePlannerTest {
    private val cfg = AgentConfig(provider = "offline", modelId = "-", region = "-", endpoint = "-")

    @Test fun `sin LLM prepara el plano de 8 s y espera GENERAR`() = runTest {
        val h = Harness()
        val o = AgentOrchestrator(OfflinePlannerProvider(), cfg, NexusToolkit.all(h.deps), h.skills, h.assets)
        val t = o.send(o.newConversation(mode = AgentMode.COPILOT), "Plano cinematográfico de 8 segundos, travelling lento, sin cambiar sus caras",
            listOf(Attachment("att_1", "file:///data/kf.png", "image/png", "kf.png")))
        assertEquals(listOf("recommend_models", "prepare_generation", "submit_generation"), t.toolCalls)
        val plan = t.events.filterIsInstance<UiEvent.PlanReady>().single().plan
        assertEquals("first_frame", plan.media.single().role)
        assertEquals("image_to_video", plan.task)
        assertTrue(plan.params["duration"] != null)
        assertEquals(PlanState.AWAITING_CONFIRMATION, h.guard.state(plan.id))
        assertTrue(h.backend.submitted.isEmpty())
        assertTrue(t.text.contains("GENERAR"))
    }

    @Test fun `sin LLM en modo asistente solo recomienda`() = runTest {
        val h = Harness()
        val o = AgentOrchestrator(OfflinePlannerProvider(), cfg, NexusToolkit.all(h.deps), h.skills, h.assets)
        val t = o.send(o.newConversation(mode = AgentMode.ASSISTANT), "anima este keyframe: vídeo de 8 s del faro al atardecer", listOf(Attachment("att_1", "file:///k.png", "image/png")))
        assertEquals(listOf("recommend_models"), t.toolCalls)
        assertTrue(t.text.startsWith("Recomiendo"))
        assertTrue(h.guard.plans().isEmpty())
    }

    @Test fun `prompt negativo solo si el schema lo admite`() = runTest {
        val h = Harness()
        val att = Attachment("att_1", "file:///k.png", "image/png")
        val llm = ScriptedLlmProvider(listOf(
            { tools(
                use("k", "prepare_generation", """{"model_id":"${KLING_PRO.modelId}","prompt":"slow dolly-in","negative_prompt":"blur","media":[{"role":"first_frame","attachment_id":"att_1"}]}"""),
                use("v", "prepare_generation", """{"model_id":"${VEO.modelId}","prompt":"slow dolly-in","negative_prompt":"blur","media":[{"role":"first_frame","attachment_id":"att_1"}]}"""),
            ) },
            { r -> assertTrue(r.result("k").isError); assertTrue(!r.result("v").isError, r.result("v").content.toString()); text("ok") },
        ))
        val o = AgentOrchestrator(llm, cfg, NexusToolkit.all(h.deps), h.skills, h.assets)
        o.send(o.newConversation(), "x", listOf(att))
        val plan = h.guard.plans().single()
        assertEquals("blur", plan.params["negative_prompt"])
        assertEquals("blur", plan.negativePrompt)
    }
}
