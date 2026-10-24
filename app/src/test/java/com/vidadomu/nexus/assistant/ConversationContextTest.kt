package com.vidadomu.nexus.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationContextTest {
    @Test fun carriedContextJoinsTheFirstMessageInsteadOfTwoUserTurns() {
        val merged = mergeTurns(listOf("user" to "Contexto: resumen", "user" to "Seguimos", "assistant" to "Vale", "assistant" to "Hecho", "user" to "Gracias"))
        assertEquals(listOf("user" to "Contexto: resumen\n\nSeguimos", "assistant" to "Vale\n\nHecho", "user" to "Gracias"), merged)
    }

    @Test fun liveVoiceReadsWholeSentencesWithoutCodeOrLinks() {
        assertEquals("Hecho. Mira el enlace", speakable("**Hecho.** Mira el https://x.y/z ```json\n{}\n```"))
        assertEquals("Primera frase. " to "Segunda a medi", completeSentences("Primera frase. Segunda a medi"))
        assertEquals("" to "sin punto", completeSentences("sin punto"))
    }

    @Test fun theAgentNameSaysProviderAndModel() {
        assertEquals("AGY · Gemini 3.8 Flash", agentLabel(AssistantSettings(backend = AssistantSettings.BACKEND_ANTIGRAVITY, modelId = "gemini-3.8-flash")))
        assertEquals("Bedrock · Claude Sonnet 4 6", agentLabel(AssistantSettings(modelId = "us.anthropic.claude-sonnet-4-6")))
    }
}
