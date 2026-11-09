package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.agent.SystemPrompt
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentLimits
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.agent.core.registry.*
import com.vidadomu.nexus.agent.core.spend.PlanState
import com.vidadomu.nexus.agent.core.tools.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/** Flow se retiró del agente: un modelo google_flow que siga en el registro pasa por la confirmación normal. */
class RetiredFlowSubmissionTest {
    private val ref = ModelRef("google_flow", "google-flow/nano-pro")
    private fun harness() = Harness(FakeRegistry(listOf(ModelSummary(ref = ref, name = "Flow", modalities = setOf("IMAGE"))),
        mapOf(ref to ModelSchema(ref, listOf(ParamSpec("count", "Cantidad", "INTEGER", "integer", min = 1.0, max = 20.0, default = "1")), MediaSpec(inputMode = "TEXT_ONLY")))))
    private fun request() = buildJsonObject {
        put("model_id", ref.modelId); put("provider_id", ref.providerId); put("prompt", "A cinematic scene")
        put("execute", true); put("params", buildJsonObject { put("count", 20) })
    }
    private val retiredTools = setOf("flow_agent_send", "flow_agent_read", "diagnose_flow", "repair_flow")

    @Test fun `execute flag no longer submits a google_flow plan without confirmation`() = runTest {
        val h = harness(); val s = AgentSession("turn", AgentMode.AGENT)
        val prepared = PrepareGenerationTool(h.deps).run(request(), s)
        assertFalse(prepared.isError, prepared.result.toString())
        assertTrue(prepared.events.none { it is UiEvent.GenerationSubmitted })
        val plan = h.guard.plans().single()
        assertEquals(PlanState.PREPARED, h.guard.state(plan.id))
        val submit = SubmitGenerationTool(h.deps).run(buildJsonObject { put("plan_id", plan.id) }, s)
        assertFalse(submit.result.jsonObject["submitted"]!!.jsonPrimitive.boolean)
        assertTrue(submit.events.single() is UiEvent.AwaitingConfirmation)
        assertTrue(h.backend.submitted.isEmpty())
    }

    @Test fun `google_flow outside the catalog is rejected and nothing is sent`() = runTest {
        val h = Harness()
        val result = PrepareGenerationTool(h.deps).run(request(), AgentSession("turn", AgentMode.AGENT))
        assertTrue(result.isError)
        assertTrue("no está en el registro" in result.result.toString(), result.result.toString())
        assertTrue(h.guard.plans().isEmpty()); assertTrue(h.backend.submitted.isEmpty())
    }

    @Test fun `agent turn asking for Flow execution only prepares the card`() = runTest {
        val h = harness()
        val llm = ScriptedLlmProvider(listOf(
            { ScriptedLlmProvider.tools(ContentBlock.ToolUse("prepare", "prepare_generation", request())) },
            { ScriptedLlmProvider.text("Preparado") }))
        val o = AgentOrchestrator(llm, AgentConfig(provider = "scripted", modelId = "test", region = "-", endpoint = "-"),
            NexusToolkit.all(h.deps), h.skills, h.assets)
        o.send(o.newConversation(mode = AgentMode.AGENT), "Genera veinte imágenes con Flow")
        assertTrue(h.backend.submitted.isEmpty())
    }

    @Test fun `tools and prompt do not expose Flow execution`() {
        val h = harness()
        val deps = ToolDeps(h.registry, h.skills, h.cost, h.router, h.guard, h.executor, h.assets,
            nativeOperations = NativeOperations { _, _, _ -> JsonObject(emptyMap()) })
        val tools = NexusToolkit.all(deps)
        assertTrue(tools.none { it.name in retiredTools })
        assertTrue(tools.none { "Flow" in it.description }, tools.filter { "Flow" in it.description }.joinToString { it.name })
        val prepare = tools.single { it.name == "prepare_generation" }.inputSchema["properties"]!!.jsonObject
        assertFalse("execute" in prepare)
        for (mode in AgentMode.entries) {
            val prompt = SystemPrompt.build(mode, "Mi estilo", "", null, "2026-10-31", null, AgentLimits().promptBlock()).joinToString("\n")
            assertFalse("Flow" in prompt, mode.name)
            assertFalse("execute=true" in prompt, mode.name)
            assertTrue(SystemPrompt.SPEND_RULES in prompt && SystemPrompt.CATALOG_BOUNDARY in prompt)
        }
    }
    @Test fun `bundled skills no longer promise direct Flow submission`() {
        val promo = Regex("Flow directo|Google Flow|google_flow|acceso directo|execute=true", RegexOption.IGNORE_CASE)
        val skills = realSkills().all()
        assertTrue(skills.isNotEmpty())
        val offenders = skills.filter { promo.containsMatchIn(it.body) }.map { it.id }
        assertTrue(offenders.isEmpty(), offenders.joinToString())
    }
}
