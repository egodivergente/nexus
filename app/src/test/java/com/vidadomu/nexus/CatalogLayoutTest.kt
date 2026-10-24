package com.vidadomu.nexus

import com.vidadomu.nexus.ui.models.catalogColumnCount
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogLayoutTest {
    @Test fun narrowScreenNeverForcesSixTinyCards() {
        assertEquals(2, catalogColumnCount(6, 360, 1f))
        assertEquals(1, catalogColumnCount(6, 280, 1f))
    }
    @Test fun accessibilityAndPreferencesArePreserved() {
        assertEquals(1, catalogColumnCount(6, 360, 1.5f))
        assertEquals(1, catalogColumnCount(1, 900, 1f))
        assertEquals(6, catalogColumnCount(6, 1000, 1f))
    }
}
