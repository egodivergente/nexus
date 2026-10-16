package com.vidadomu.nexus.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantProviderPreservationTest {

    @Test fun defaultProviderRemainsBedrockDirect() {
        val defaults = AssistantSettings()
        assertEquals(AssistantSettings.BACKEND_DIRECT, defaults.backend)
        assertEquals(AssistantSettings.DEFAULT_MODEL, defaults.modelId)
        assertEquals(AssistantSettings.DEFAULT_ANTIGRAVITY_ENDPOINT, defaults.antigravityEndpoint)
    }

    @Test fun allFiveProvidersExistAndCoexist() {
        val backends = listOf(
            AssistantSettings.BACKEND_DIRECT,
            AssistantSettings.BACKEND_ANTHROPIC,
            AssistantSettings.BACKEND_OPENAI,
            AssistantSettings.BACKEND_PROXY,
            AssistantSettings.BACKEND_ANTIGRAVITY,
        )
        assertEquals(5, backends.distinct().size)
        assertTrue(backends.all { it.isNotBlank() })
    }

    @Test fun antigravityProviderIsOnlineWithLocalBridgeEndpoint() {
        val agy = AssistantSettings(
            backend = AssistantSettings.BACKEND_ANTIGRAVITY,
            modelId = AssistantSettings.DEFAULT_ANTIGRAVITY_MODEL,
            antigravityEndpoint = "http://127.0.0.1:8788",
        )
        assertFalse(agy.offline)

        val agyBlankEndpoint = agy.copy(antigravityEndpoint = "")
        assertTrue(agyBlankEndpoint.offline)
    }

    @Test fun existingProvidersKeepTheirOfflineAndCredentialRules() {
        assertTrue(AssistantSettings(backend = AssistantSettings.BACKEND_DIRECT, awsAccessKeyId = "", maskedAwsSecret = "").offline)
        assertFalse(AssistantSettings(backend = AssistantSettings.BACKEND_DIRECT, awsAccessKeyId = "AKIA123", maskedAwsSecret = "••••").offline)

        assertTrue(AssistantSettings(backend = AssistantSettings.BACKEND_ANTHROPIC, maskedApiKey = "").offline)
        assertFalse(AssistantSettings(backend = AssistantSettings.BACKEND_ANTHROPIC, maskedApiKey = "••••").offline)

        assertTrue(AssistantSettings(backend = AssistantSettings.BACKEND_OPENAI, llmBaseUrl = "https://api.openai.com/v1", maskedApiKey = "").offline)
        assertFalse(AssistantSettings(backend = AssistantSettings.BACKEND_OPENAI, llmBaseUrl = "https://api.openai.com/v1", maskedApiKey = "••••").offline)

        assertTrue(AssistantSettings(backend = AssistantSettings.BACKEND_PROXY, endpoint = "").offline)
        assertFalse(AssistantSettings(backend = AssistantSettings.BACKEND_PROXY, endpoint = "https://proxy.example.com", maskedToken = "••••").offline)
    }
}
