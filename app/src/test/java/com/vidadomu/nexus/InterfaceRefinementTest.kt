package com.vidadomu.nexus

import com.vidadomu.nexus.ui.create.creationProviderLabel
import com.vidadomu.nexus.ui.components.matchesCreationSearch
import com.vidadomu.nexus.core.model.*
import org.junit.Assert.*
import org.junit.Test

class InterfaceRefinementTest {
    @Test fun providerBadgeNeverAttributesAtlasBalanceToCrunOrRunway() {
        assertEquals("Crun",creationProviderLabel("crun","37 €"))
        assertEquals("Runway",creationProviderLabel("runway","37 €"))
        assertEquals("Atlas · 37 €",creationProviderLabel("atlas_cloud","37 €"))
        assertEquals("Atlas Cloud",creationProviderLabel("atlas_cloud",null))
    }
    @Test fun historySearchCombinesAccentInsensitiveWordsAcrossFields() {
        val job=GenerationJob(id="test",providerId="atlas_cloud",modelId="kling-v3",modality=Modality.VIDEO,prompt="Una habitación azul")
        assertTrue(matchesCreationSearch(job,"HABITACION Kling"))
        assertFalse(matchesCreationSearch(job,"habitación bosque"))
        assertTrue(matchesCreationSearch(job,"   "))
    }
}
