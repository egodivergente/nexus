package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.agent.SystemPrompt
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.text
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.tools
import com.vidadomu.nexus.agent.core.tools.*
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*

class NativeOperationsTest {
    @Test fun agentRepairsDirectlyAndCopilotProposesOnce() = runTest {
        for (mode in listOf(AgentMode.AGENT, AgentMode.COPILOT)) {
            val h = Harness()
            val calls = mutableListOf<String>()
            val native = NativeOperations { name, input, _ ->
                calls += name
                assertEquals("job_existing", input["job_id"]!!.jsonPrimitive.content)
                buildJsonObject { put("tracking_restored", true); put("resubmitted", false) }
            }
            val llm = ScriptedLlmProvider(listOf(
                { tools(use("repair", "recover_generation", """{"job_id":"job_existing"}""")) },
                { text("Comprobado.") },
            ))
            val o = AgentOrchestrator(llm, AgentConfig(modelId = "test", region = "", endpoint = ""), NativeOperationTools.all(native), h.skills, h.assets)
            val conversation = o.newConversation(mode = mode)
            val result = o.send(conversation, "Recupera el vídeo atascado")
            if (mode == AgentMode.AGENT) {
                assertEquals(listOf("recover_generation"), calls)
                assertTrue(result.events.none { it is UiEvent.ProposedAction })
            } else {
                assertTrue(calls.isEmpty())
                val proposal = result.events.filterIsInstance<UiEvent.ProposedAction>().single()
                o.applyPending(conversation, proposal.id)
                assertEquals(listOf("recover_generation"), calls)
                assertFailsWith<IllegalArgumentException> { o.applyPending(conversation, proposal.id) }
            }
            assertTrue(h.backend.submitted.isEmpty())
        }
    }

    @Test fun assistantCannotMutateEvenIfModelRequestsIt() = runTest {
        val h = Harness()
        var executed = false
        val native = NativeOperations { _, _, _ -> executed = true; JsonObject(emptyMap()) }
        val llm = ScriptedLlmProvider(listOf(
            { r ->
                assertTrue("diagnose_flow" in r.toolNames())
                assertFalse("repair_flow" in r.toolNames())
                tools(use("bad", "repair_flow"))
            },
            { r -> assertTrue(r.result("bad").isError); text("Solo lectura.") },
        ))
        val o = AgentOrchestrator(llm, AgentConfig(modelId = "test", region = "", endpoint = ""), NativeOperationTools.all(native), h.skills, h.assets)
        o.send(o.newConversation(mode = AgentMode.ASSISTANT), "repara")
        assertFalse(executed)
    }

    @Test fun failuresDoNotExposeNativeExceptionSecretsAndCancellationPropagates() = runTest {
        val failure = NativeOperationTools.all(NativeOperations { _, _, _ -> error("Bearer PRIVATE-KEY") }).first()
        val result = failure.run(JsonObject(emptyMap()), AgentSession("test", AgentMode.AGENT))
        assertTrue(result.isError)
        assertFalse("PRIVATE-KEY" in result.result.toString())
        val cancelled = NativeOperationTools.all(NativeOperations { _, _, _ -> throw CancellationException() }).first()
        assertFailsWith<CancellationException> { cancelled.run(JsonObject(emptyMap()), AgentSession("test", AgentMode.AGENT)) }
    }

    @Test fun operationalRulesSurvivePromptOverride() {
        val prompt = SystemPrompt.build(AgentMode.AGENT, "Custom", "", null, "2026-10-09")
        assertTrue(SystemPrompt.OPERATIONS_RULES in prompt)
        assertTrue(SystemPrompt.SPEND_RULES in prompt)
    }
}
