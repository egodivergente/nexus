package com.vidadomu.nexus.assistant

import org.junit.Assert.*
import org.junit.Test

class AssistantSettingsProviderTest {
    @Test fun bedrockRemainsTheDefaultWhenAntigravityIsAdded() {
        val settings = AssistantSettings(
            awsAccessKeyId = "existing-access-key",
            maskedAwsSecret = "existing-secret",
        )

        assertEquals(AssistantSettings.BACKEND_DIRECT, settings.backend)
        assertTrue(settings.direct)
        assertEquals("bedrock-direct", settings.toConfig().provider)
        assertEquals(AssistantSettings.DEFAULT_MODEL, settings.toConfig().modelId)
    }

    @Test fun antigravityUsesItsOwnModelWithoutChangingBedrockSettings() {
        val settings = AssistantSettings(
            awsAccessKeyId = "existing-access-key",
            maskedAwsSecret = "existing-secret",
        ).copy(
            backend = AssistantSettings.BACKEND_ANTIGRAVITY,
            antigravityModelId = "gemini-fast",
        )

        assertFalse(settings.offline)
        assertFalse(settings.direct)
        assertEquals("antigravity", settings.toConfig().provider)
        assertEquals("gemini-fast", settings.toConfig().modelId)
        assertEquals(AssistantSettings.DEFAULT_MODEL, settings.modelId)
        assertEquals("existing-access-key", settings.awsAccessKeyId)
        assertEquals("existing-secret", settings.maskedAwsSecret)
    }
}
