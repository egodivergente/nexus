package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.cost.CostConfidence
import com.vidadomu.nexus.agent.core.cost.CostEstimate
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantUiLogicTest {
    private fun plan(effective: Map<String, String>, estimate: CostEstimate? = null) = GenerationPlan(
        id = "p1", ref = ModelRef("atlas_cloud", "kwaivgi/kling-video-o3-pro/image-to-video"), modelName = "Kling O3 Pro",
        modality = "VIDEO", prompt = "dolly-in", params = effective, effectiveParams = effective, estimate = estimate, createdAt = 0,
    )

    @Test fun blankDashAndNullLikeParamsAreNotShown() {
        val shown = visibleParams(plan(mapOf("duration" to "8", "negative_prompt" to "", "seed" to "–", "sound" to "null", "aspect_ratio" to "16:9", "prompt" to "x")))
        assertEquals(listOf("duration" to "8", "aspect_ratio" to "16:9"), shown)
    }

    @Test fun costLineSaysWhereTheNumberComesFrom() {
        assertTrue(costLine(CostEstimate(0.76, CostConfidence.NEXUS_QUOTE, "calc")).contains("calculadora"))
        assertTrue(costLine(CostEstimate(0.76, CostConfidence.PUBLISHED_RATE, "tabla")).contains("tarifa publicada"))
        assertTrue(costLine(CostEstimate(null, CostConfidence.BASE_ONLY, "0,095 $ por segundo")).startsWith("Coste exacto no disponible"))
        assertEquals("Coste no disponible", costLine(null))
    }

    @Test fun assistantJobMetadataIsAcceptedByTheRepository() {
        // Revisión 02-10: con claves sin prefijo el repositorio rechazaba TODO envío del asistente.
        assertTrue(com.vidadomu.nexus.data.repository.LocalJobMetadata.allowed(NexusGenerationBackend.ORIGIN_KEY))
        assertTrue(com.vidadomu.nexus.data.repository.LocalJobMetadata.allowed(NexusGenerationBackend.PLAN_KEY))
        assertTrue(com.vidadomu.nexus.data.repository.LocalJobMetadata.allowed("__comparison_batch"))
        assertTrue(com.vidadomu.nexus.data.repository.LocalJobMetadata.allowed("__project_id"))
        assertTrue(com.vidadomu.nexus.data.repository.LocalJobMetadata.allowed("__conversation_id"))
        assertFalse(com.vidadomu.nexus.data.repository.LocalJobMetadata.allowed("duration"))
        assertFalse(com.vidadomu.nexus.data.repository.LocalJobMetadata.allowed("__arbitrary_provider_option"))
    }

    @Test fun withoutProxyTheAssistantIsOfflineAndNeverSendsImages() {
        val s = AssistantSettings(backend = AssistantSettings.BACKEND_PROXY, endpoint = "", modelId = "m", maskedToken = "••••", sendImages = true)
        assertTrue(s.offline)
        val c = s.toConfig()
        assertEquals("offline", c.provider)
        assertFalse(c.sendImagesToLlm)
        val online = s.copy(endpoint = "https://proxy.example", mode = AgentMode.AGENT)
        assertFalse(online.offline)
        assertEquals("bedrock-proxy", online.toConfig().provider)
        assertTrue(online.toConfig().sendImagesToLlm)
    }

    @Test fun otherLlmApisNeedTheirOwnKeyAndModel() {
        val anthropic = AssistantSettings(backend = AssistantSettings.BACKEND_ANTHROPIC, modelId = "claude-sonnet-5-5")
        assertTrue("sin clave no hay LLM", anthropic.offline)
        assertEquals("anthropic", anthropic.copy(maskedApiKey = "••••").toConfig().provider)
        val openRouter = AssistantSettings(backend = AssistantSettings.BACKEND_OPENAI, modelId = "openai/gpt-4o", llmBaseUrl = "https://openrouter.ai/api/v1")
        assertTrue(openRouter.offline)
        assertEquals("openai-compatible", openRouter.copy(maskedApiKey = "••••").toConfig().provider)
        assertFalse("un servidor local puede no pedir clave", openRouter.copy(llmBaseUrl = "http://127.0.0.1:11434/v1").offline)
        assertTrue("sin modelo tampoco", openRouter.copy(maskedApiKey = "••••", modelId = "").offline)
        assertFalse(anthropic.direct)
    }

    @Test fun directBedrockNeedsBothAwsKeysAndIsTheDefault() {
        val d = AssistantSettings()
        assertTrue(d.direct); assertTrue(d.offline)
        assertEquals("us.anthropic.claude-sonnet-4-6", d.modelId)
        assertTrue("sin secret sigue sin LLM", d.copy(awsAccessKeyId = "AKIA…").offline)
        val ok = d.copy(awsAccessKeyId = "AKIA…", maskedAwsSecret = "••••")
        assertFalse(ok.offline)
        assertEquals("bedrock-direct", ok.toConfig().provider)
        assertTrue(ok.copy(region = "").offline)
    }
}
