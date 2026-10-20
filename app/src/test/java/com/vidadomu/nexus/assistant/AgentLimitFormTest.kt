package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.config.AgentLimits
import org.junit.Test
import org.junit.Assert.*

class AgentLimitFormTest {
    @Test fun everyControlRoundTripsAndLargeValuesAreNotSilentlyClamped() {
        val current=AgentLimits()
        val all=AgentLimitForm.values(current)
        assertEquals(all.keys-setOf("maxTokens"),AgentLimitForm.labels.keys)
        val edited=all.toMutableMap().apply {
            this["maxConcurrentSubagents"]="90";this["maxSubagentDepth"]="0"
            this["maxConcurrentGenerations"]="200";this["maxGenerationsPerBatch"]="100000"
            this["flowMaxResults"]="0";this["allowSubagentGeneration"]="false"
        }
        val saved=AgentLimitForm.parse(current,edited)
        assertEquals(90,saved.maxConcurrentSubagents);assertEquals(0,saved.maxSubagentDepth)
        assertEquals(200,saved.maxConcurrentGenerations);assertEquals(100000,saved.maxGenerationsPerBatch)
        assertEquals(0,saved.flowMaxResults);assertFalse(saved.allowSubagentGeneration)
        assertEquals(saved,AgentLimitForm.parse(saved,AgentLimitForm.values(saved)))
        assertEquals(12345,AgentLimitForm.parse(current.copy(maxTokens=12345),edited).maxTokens)
    }

    @Test fun invalidValuesAreReportedAndNeverReplacedByOldDefaults() {
        for(value in listOf("", "garbage", "-1", "999999999999999999999")) {
            try {
                AgentLimitForm.parse(AgentLimits(),AgentLimitForm.values(AgentLimits())+("maxConcurrentGenerations" to value))
                fail("Expected invalid input: $value")
            } catch(expected:IllegalArgumentException) {assertNotNull(expected.message)}
        }
    }
}
