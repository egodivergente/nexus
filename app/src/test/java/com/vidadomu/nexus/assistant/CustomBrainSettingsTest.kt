package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.config.AgentLimits
import com.vidadomu.nexus.agent.core.config.LimitOverrides
import com.vidadomu.nexus.agent.core.llm.BrainAuth
import com.vidadomu.nexus.agent.core.llm.BrainAuthMode
import com.vidadomu.nexus.agent.core.llm.BrainCapabilities
import com.vidadomu.nexus.agent.core.llm.BrainPresets
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.pauseExpiredTracking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomBrainSettingsTest {
    private fun custom(brain: com.vidadomu.nexus.agent.core.llm.BrainProfile? = BrainPresets.byteplus(), hasKey: Boolean = true, limits: AgentLimits = AgentLimits()) =
        AssistantSettings(backend = BrainStore.backendFor(brain?.id ?: "byteplus"), brain = brain, brainHasKey = hasKey, limits = limits)

    @Test fun `BytePlus activo y con clave es un cerebro en linea con su modelo`() {
        val s = custom()
        assertTrue(s.isCustom); assertFalse(s.offline)
        assertEquals("custom", s.toConfig().provider)
        assertEquals("dola-seed-2-1-turbo-260628", BrainPresets.byteplus().modelId)
    }

    @Test fun `sin clave, sin modelo o sin perfil el agente no finge y usa el planificador local`() {
        assertTrue(custom(hasKey = false).offline)
        assertTrue(custom(brain = BrainPresets.byteplus().copy(modelId = "")).offline)
        assertTrue(custom(brain = null).offline)
        // Sin clave pero declarado «sin clave»: sí está en línea (servidor local)
        assertFalse(custom(brain = BrainPresets.byteplus().copy(auth = BrainAuth(BrainAuthMode.NONE)), hasKey = false).offline)
    }

    @Test fun `los proveedores de siempre no cambian - mismas reglas offline y mismos valores por defecto del agente`() {
        val direct = AssistantSettings(awsAccessKeyId = "AKIA", maskedAwsSecret = "••••")
        assertFalse(direct.isCustom); assertFalse(direct.offline)
        val cfg = direct.toConfig()
        assertEquals("bedrock-direct", cfg.provider)
        assertEquals(20, cfg.maxToolRounds)
        assertEquals(4096, cfg.maxTokens)
        assertEquals(AgentLimits(), cfg.limits)
        assertTrue(cfg.sendImagesToLlm)
    }

    @Test fun `los limites del usuario llegan a la configuracion del agente`() {
        val cfg = AssistantSettings(awsAccessKeyId = "AKIA", maskedAwsSecret = "••••",
            limits = AgentLimits(maxToolRounds = 0, maxTokens = 123_456, maxImagesPerTurn = 9, flowMaxResults = 3)).toConfig()
        assertTrue("0 = sin límite", cfg.maxToolRounds > 1_000_000)
        assertEquals(123_456, cfg.maxTokens)
        assertEquals(9, cfg.limits.maxImagesPerTurn)
        assertEquals(3, cfg.limits.flowMaxResults)
    }

    @Test fun `un cerebro puede sustituir los limites globales y lo que no define hereda`() {
        val brain = BrainPresets.byteplus().copy(limits = LimitOverrides(maxToolRounds = 50, maxTokens = 64_000), temperature = 0.3)
        val s = custom(brain, limits = AgentLimits(maxToolRounds = 20, maxTokens = 4096, turnTimeoutSeconds = 120))
        assertEquals(50, s.effectiveLimits.maxToolRounds)
        assertEquals(64_000, s.effectiveLimits.maxTokens)
        assertEquals(120, s.effectiveLimits.turnTimeoutSeconds)
        val cfg = s.toConfig()
        assertEquals(50, cfg.maxToolRounds); assertEquals(64_000, cfg.maxTokens); assertEquals(0.3, cfg.temperature!!, 0.0)
    }

    @Test fun `la vision solo viaja si el ajuste esta activo y el cerebro la declara`() {
        assertTrue(custom().visionEnabled)
        assertFalse(custom(BrainPresets.byteplus().copy(capabilities = BrainCapabilities(vision = false))).visionEnabled)
        assertFalse(custom().copy(sendImages = false).visionEnabled)
        assertFalse(custom(hasKey = false).visionEnabled)
    }

    @Test fun `el prefijo de los cerebros propios no choca con ningun proveedor existente`() {
        val existing = listOf(AssistantSettings.BACKEND_DIRECT, AssistantSettings.BACKEND_ANTHROPIC, AssistantSettings.BACKEND_OPENAI, AssistantSettings.BACKEND_PROXY, AssistantSettings.BACKEND_ANTIGRAVITY)
        assertTrue(existing.none { it.startsWith(BrainStore.PREFIX) })
        assertEquals("custom:byteplus", BrainStore.backendFor("byteplus"))
        assertEquals("nexus_brain_key_byteplus", BrainStore.keyId("byteplus"))
    }

    @Test fun `la ventana de seguimiento de trabajos es ajustable y 0 la desactiva`() {
        val running = GenerationJob("j", "task", "p", "m", Modality.VIDEO, "x", status = GenerationStatus.GENERATING, startedAt = 0)
        assertEquals("30 min por defecto, como antes", GenerationStatus.FAILED, running.pauseExpiredTracking(1_800_001).status)
        assertEquals(GenerationStatus.GENERATING, running.pauseExpiredTracking(1_799_999).status)
        assertEquals("con 180 min no se pausa a los 31", GenerationStatus.GENERATING, running.pauseExpiredTracking(31 * 60_000L, windowMinutes = 180).status)
        assertEquals(GenerationStatus.FAILED, running.pauseExpiredTracking(181 * 60_000L, windowMinutes = 180).status)
        assertEquals("0 = nunca", GenerationStatus.GENERATING, running.pauseExpiredTracking(Long.MAX_VALUE / 2, windowMinutes = 0).status)
        assertTrue("180 minutos" in running.pauseExpiredTracking(181 * 60_000L, windowMinutes = 180).error.orEmpty())
        assertNull(running.pauseExpiredTracking(0, windowMinutes = 30).error)
    }
}
