package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.registry.*
import com.vidadomu.nexus.agent.core.tools.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class DirectFlowTest {
    private val ref = ModelRef("google_flow", "google-flow/nano-pro")
    private fun harness(): Harness {
        val schema = ModelSchema(ref, listOf(
            ParamSpec("count", "Cantidad", "INTEGER", "integer", min = 1.0, max = 20.0, default = "1"),
            ParamSpec("aspect", "Formato", "ENUM", "string", options = listOf("16:9", "9:16"), default = "16:9")
        ), MediaSpec(inputMode = "TEXT_ONLY"))
        return Harness(FakeRegistry(listOf(ModelSummary(ref = ref, name = "Flow", modalities = setOf("IMAGE"))), mapOf(ref to schema)))
    }
    private fun deps(h: Harness) = ToolDeps(h.registry, h.skills, h.cost, h.router, h.guard, h.executor, h.assets, directFlow = true)
    private fun request(count: Int, aspect: String? = null) = buildJsonObject {
        put("model_id", ref.modelId); put("provider_id", ref.providerId); put("prompt", "A cinematic scene")
        put("execute", true); put("params", buildJsonObject { put("count", count); aspect?.let { put("aspect", it) } })
    }
    @Test fun `twenty results submit directly once and default to landscape`() = runTest {
        val h=harness();val d=deps(h);val s=AgentSession("turn", AgentMode.AGENT)
        val result=PrepareGenerationTool(d).run(request(20),s)
        assertFalse(result.isError);assertTrue(result.events.single() is UiEvent.GenerationSubmitted);assertEquals(1,h.backend.submitted.size)
        assertEquals("20",h.backend.submitted.single().params["count"])
        assertEquals("16:9",h.backend.submitted.single().effectiveParams["aspect"])
        val plan=h.backend.submitted.single()
        assertTrue(SubmitGenerationTool(d).run(buildJsonObject { put("plan_id",plan.id) },s).isError)
        assertEquals(1,h.backend.submitted.size)
    }
    @Test fun `twenty distinct scenes are allowed and explicit portrait is preserved`() = runTest {
        val h=harness();val d=deps(h);val s=AgentSession("turn", AgentMode.AGENT)
        repeat(20){assertFalse(PrepareGenerationTool(d).run(request(1,"9:16"),s).isError)}
        assertEquals(20,h.backend.submitted.size)
        assertTrue(h.backend.submitted.all { it.params["aspect"]=="9:16" })
        assertTrue(PrepareGenerationTool(d).run(request(1),s).isError)
    }
    @Test fun `next message can request another twenty without exhausting the conversation`() = runTest {
        val h = harness(); val d = deps(h)
        val llm = com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider(listOf(
            { com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.tools(com.vidadomu.nexus.agent.core.llm.ContentBlock.ToolUse("prepare", "prepare_generation", request(20))) },
            { com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.text("Enviado") },
            { com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.tools(com.vidadomu.nexus.agent.core.llm.ContentBlock.ToolUse("prepare", "prepare_generation", request(20))) },
            { com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.text("Enviado") }))
        val orchestrator = com.vidadomu.nexus.agent.core.agent.AgentOrchestrator(llm,
            com.vidadomu.nexus.agent.core.config.AgentConfig(provider="scripted", modelId="test", region="-", endpoint="-"),
            NexusToolkit.all(d), h.skills, h.assets)
        val conversation = orchestrator.newConversation(mode=AgentMode.AGENT)
        orchestrator.send(conversation,"Genera veinte imágenes")
        orchestrator.send(conversation,"Ahora genera otras veinte")
        assertEquals(2,h.backend.submitted.size)
        assertTrue(h.backend.submitted.all { it.params["count"] == "20" })
    }
    @Test fun `default policy still prepares without sending`() = runTest {
        val h=harness();val s=AgentSession("turn",AgentMode.AGENT)
        assertFalse(PrepareGenerationTool(h.deps).run(request(20),s).isError)
        assertTrue(h.backend.submitted.isEmpty())
    }
}
