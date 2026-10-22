package com.vidadomu.nexus

import com.vidadomu.nexus.provider.custom.BytePlusMediaPreset
import com.vidadomu.nexus.provider.custom.normalizeMediaParameters
import org.junit.Assert.*
import org.junit.Test

class MediaConfigurationRecoveryTest {
    @Test fun exactLegacyDuplicatesDoNotHideByteplusAndPreserveConfiguration() {
        val source=BytePlusMediaPreset.config()
        val legacy=source.copy(models=source.models.map {m ->
            m.copy(params=m.params + m.params.filter {it.key=="duration"})
        })
        assertTrue(legacy.problems().any {it.contains("repetidos")})
        val recovered=normalizeMediaParameters(legacy)
        assertEquals(source,recovered)
        assertTrue(recovered.problems().isEmpty())
        assertEquals(recovered,normalizeMediaParameters(recovered))
    }
    @Test fun conflictingParametersAndUserEndpointsAreNeverOverwritten() {
        val source=BytePlusMediaPreset.config()
        val first=source.models.first()
        val custom=first.copy(providerModelId="ep-user-configured",params=first.params+first.params.first {it.key=="duration"}.copy(default="9"))
        val recovered=normalizeMediaParameters(source.copy(models=listOf(custom)))
        assertEquals(custom,recovered.models.single())
        assertTrue(recovered.problems().any {it.contains("repetidos")})
    }
}
