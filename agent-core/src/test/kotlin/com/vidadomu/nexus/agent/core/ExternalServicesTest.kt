package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.agent.ExternalServices
import com.vidadomu.nexus.agent.core.agent.SystemPrompt
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.OfflinePlannerProvider
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.agent.core.tools.AgentSession
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.agent.core.tools.NexusToolkit
import com.vidadomu.nexus.agent.core.tools.SearchModelsTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class ExternalServicesTest {
    @Test fun `Flow knowledge and catalog boundary survive overrides in all modes`() {
        for (mode in AgentMode.entries) {
            val prompt = SystemPrompt.build(mode, "Mi estilo", "", null, "2026-10-06")
            assertEquals("Mi estilo", prompt.first())
            assertTrue(prompt.contains(ExternalServices.FLOW_INFO))
            assertTrue(prompt.contains(SystemPrompt.CATALOG_BOUNDARY))
            assertTrue(prompt.contains(SystemPrompt.SPEND_RULES))
            assertFalse(prompt.joinToString().contains("Los modelos que existen son SOLO"))
        }
    }

    @Test fun `search Flow returns service information without a fake executable model`() = runTest {
        val result = SearchModelsTool(Harness().deps).run(
            buildJsonObject { put("query", "Google Flow") }, AgentSession("s", AgentMode.AGENT))
        val service = result.result.jsonObject["external_service"]!!.jsonObject
        assertEquals("Google Flow", service["name"]!!.jsonPrimitive.content)
        assertTrue(service["generation_available_in_nexus"]!!.jsonPrimitive.boolean)
        assertTrue(service["requires_explicit_confirmation"]!!.jsonPrimitive.boolean)
        assertEquals("check_in_app", service["authentication_status"]!!.jsonPrimitive.content)
        assertFalse(service.containsKey("model_id"))
    }

    @Test fun `offline Flow request does not select another provider or prepare a paid plan`() = runTest {
        val h = Harness()
        val o = AgentOrchestrator(OfflinePlannerProvider(), AgentConfig(provider="offline",modelId="-",region="-",endpoint="-"),
            NexusToolkit.all(h.deps), h.skills, h.assets)
        for (text in listOf("conoces Google Flow", "genera este video con Flow", "haz un prompt para flow")) {
            val result = o.send(o.newConversation(mode=AgentMode.AGENT), text)
            assertTrue(result.text.contains("Google Flow"))
            assertTrue(result.text.contains("Crear en Flow"))
            assertTrue(result.toolCalls.isEmpty())
            assertTrue(h.guard.plans().isEmpty())
            assertTrue(h.backend.submitted.isEmpty())
        }
    }

    @Test fun `workflow and optical flow are not treated as the Google service`() {
        assertFalse(ExternalServices.mentionsFlow("mi workflow de edicion"))
        assertFalse(ExternalServices.mentionsFlow("explica optical flow"))
        assertFalse(ExternalServices.mentionsFlow("flow matching"))
        assertTrue(ExternalServices.mentionsFlow("Google Flow y optical flow son lo mismo?"))
    }

    @Test fun `attachment filename Flow does not override a different creative request`() = runTest {
        val h = Harness()
        val o = AgentOrchestrator(OfflinePlannerProvider(), AgentConfig(provider="offline",modelId="-",region="-",endpoint="-"),
            NexusToolkit.all(h.deps), h.skills, h.assets)
        val result = o.send(o.newConversation(mode=AgentMode.ASSISTANT), "anima este keyframe durante 8 segundos",
            listOf(Attachment("att_1", "file:///flow.png", "image/png", "flow.png")))
        assertEquals(listOf("recommend_models"), result.toolCalls)
        assertFalse(result.text.contains(ExternalServices.OFFLINE_FLOW_REPLY))
    }

    @Test fun `provider request receives Flow context with existing mistaken history`() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf(
            { ScriptedLlmProvider.text("Flow no existe") },
            { request ->
                assertTrue(request.system.contains(ExternalServices.FLOW_INFO))
                assertTrue(request.system.any { it.contains("corrige esa afirmación") })
                ScriptedLlmProvider.text("Google Flow existe; se envia desde Crear en Flow tras una confirmacion explicita.")
            }))
        val o = AgentOrchestrator(llm, AgentConfig(provider="scripted",modelId="test",region="-",endpoint="-"),
            NexusToolkit.all(h.deps), h.skills, h.assets)
        val conversation = o.newConversation()
        o.send(conversation, "Flow?")
        assertTrue(o.send(conversation, "Google Flow").text.contains("Google Flow existe"))
    }
}
