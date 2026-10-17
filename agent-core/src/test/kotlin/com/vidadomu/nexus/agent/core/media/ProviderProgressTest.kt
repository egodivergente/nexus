package com.vidadomu.nexus.agent.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class ProviderProgressTest {
    @Test fun acceptsFractionsAndPercentages() {
        assertEquals(0f, normalizedProviderProgress(0.0))
        assertEquals(0.42f, normalizedProviderProgress(0.42))
        assertEquals(1f, normalizedProviderProgress(1.0))
        assertEquals(0.42f, normalizedProviderProgress(42.0))
        assertEquals(1f, normalizedProviderProgress(100.0))
    }
    @Test fun percentageFieldsDoNotTreatOneAsOneHundredPercent() {
        assertEquals(0.01f, normalizedProviderProgress(1.0, percentage = true))
        assertEquals(0.005f, normalizedProviderProgress(0.5, percentage = true))
        assertEquals(1f, normalizedProviderProgress(100.0, percentage = true))
    }
    @Test fun rejectsNonfiniteAndMissingValues() {
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertNull(normalizedProviderProgress(it))
            assertNull(normalizedProviderProgress(it, percentage = true))
        }
    }
    @Test fun rejectsOutOfRangeInsteadOfClamping() {
        listOf(-0.1, -1.0, 100.01, 150.0, Double.MAX_VALUE).forEach {
            assertNull(normalizedProviderProgress(it))
            assertNull(normalizedProviderProgress(it, percentage = true))
        }
    }
    @Test fun configuredParserDoesNotTurnBadProviderValuesIntoCompletion() {
        val m = MediaTemplates.all.first { it.label == "Estilo AtlasCloud" }.config.models.single().copy(progressPath = "data.progress")
        listOf("150", "-1", "\"Infinity\"", "\"NaN\"", "null").forEach { raw ->
            val body = Json.parseToJsonElement("""{"data":{"status":"processing","progress":$raw}}""")
            assertEquals(PollOutcome.Running(null), MediaEngine.parsePoll(m, body), raw)
        }
    }
}
