package com.vidadomu.nexus.provider.atlas

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class AtlasReportedProgressTest {
    private fun parse(body: String) = atlasReportedProgress(Json.parseToJsonElement(body).jsonObject)
    @Test fun acceptsFractionsAndPercentages() {
        assertEquals(0.42f, requireNotNull(parse("""{"progress":0.42}""")), 0f)
        assertEquals(0.42f, requireNotNull(parse("""{"progress":42}""")), 0f)
    }
    @Test fun percentFieldsHaveExplicitUnits() {
        assertEquals(0.01f, requireNotNull(parse("""{"percentage":1}""")), 0f)
        assertEquals(0.005f, requireNotNull(parse("""{"percent":0.5}""")), 0f)
    }
    @Test fun rejectsInvalidAndMissingWithoutClamping() {
        listOf("150", "-1", "\"Infinity\"", "\"NaN\"", "null", "\"unknown\"").forEach {
            assertNull(parse("""{"progress":$it}"""))
        }
        assertNull(parse("{}"))
    }
    @Test fun invalidPrimaryValueDoesNotFallBackToAnotherPercentage() {
        assertNull(parse("""{"progress":150,"percentage":80}"""))
    }
}
