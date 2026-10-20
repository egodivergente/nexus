package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.SystemPrompt
import com.vidadomu.nexus.agent.core.config.AgentMode
import kotlin.test.*

/** El prompt que entrega el usuario se respeta tal cual; solo cambia por límite, sintaxis exigida o rechazo del proveedor. */
class PromptFidelityTest {
    @Test fun fidelityRulesAreAlwaysPresentEvenWithOverride() {
        for (mode in AgentMode.values()) {
            val prompt = SystemPrompt.build(mode, "Mi estilo", "", null, "2026-10-19")
            assertTrue(SystemPrompt.PROMPT_FIDELITY_RULES in prompt)
        }
        assertTrue(SystemPrompt.PROMPT_FIDELITY_RULES in SystemPrompt.build(AgentMode.AGENT, null, "", null, "2026-10-19"))
    }

    @Test fun rulesNameTheOnlyAllowedExceptionsAndRequireDisclosure() {
        val rules = SystemPrompt.PROMPT_FIDELITY_RULES
        assertTrue("EXACTAMENTE" in rules)
        assertTrue("límite de caracteres" in rules)
        assertTrue("sintaxis" in rules)
        assertTrue("el original se conserva" in rules)
        assertTrue("nunca se inyectan" in rules)
    }

    @Test fun projectMemoryDoesNotApplyToPromptsTheUserAlreadyWrote() {
        assertTrue("ese se envía tal cual" in SystemPrompt.BASE)
        assertFalse("en TODOS los prompts y recomendaciones" in SystemPrompt.BASE)
    }
}
