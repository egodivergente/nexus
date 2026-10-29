package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.data.repository.*
import com.vidadomu.nexus.ui.models.CatalogPlacements
import org.junit.Assert.*
import org.junit.Test

class CatalogVisibilityCacheTest {
    private fun model() = NexusModel("visibility-cache-fixture", "Fixture original", "fixture",
        organization = "Fixture", modelFamily = "Visibility fixture family", modalities = listOf(Modality.VIDEO),
        capabilities = listOf(ModelCapability.TEXT_TO_VIDEO), parameters = emptyList())

    @Test fun refreshedSchemaForTheSameRouteRecomputesItsToolVisibility() {
        val original = model()
        val hidden = setOf(toolVisibilityKey("Edición de vídeo"))
        visibleCatalog(listOf(original), emptySet(), emptyMap())
        assertTrue(isCatalogModelVisible(original, hidden))
        val revisions = listOf(
            original.copy(inputMode = ModelInputMode.VIDEO_INPUT),
            original.copy(capabilities = listOf(ModelCapability.VIDEO_TO_VIDEO)),
            original.copy(contractEndpoint = "/fixture/video-to-video"),
            original.copy(providerModelId = "fixture/video-to-video"))
        for (updated in revisions) {
            assertEquals(routeVisibilityKey(original), routeVisibilityKey(updated))
            assertFalse(isCatalogModelVisible(updated, hidden))
            assertTrue(isCatalogModelVisible(original, hidden))
            assertTrue(visibleCatalog(listOf(updated), hidden, emptyMap()).isEmpty())
        }
    }

    @Test fun familyAndGroupChangesDoNotReuseKeysFromAnOlderSnapshot() {
        val original = model()
        val updated = original.copy(modelFamily = "New fixture family", displayName = "Another product")
        val previousKeys = setOf(familyVisibilityKey(original.modelFamily), groupVisibilityKey(original))
        visibleCatalog(listOf(original), emptySet(), emptyMap())
        assertFalse(isCatalogModelVisible(original, previousKeys))
        assertEquals(routeVisibilityKey(original), routeVisibilityKey(updated))
        assertTrue(isCatalogModelVisible(updated, previousKeys))
        val nextKey = groupVisibilityKey(updated)
        val captured = captureHiddenRoutes(listOf(updated), setOf(nextKey), emptyMap())
        assertEquals(setOf(routeVisibilityKey(updated)), captured.getValue(nextKey))
        assertTrue(visibleCatalog(listOf(updated), setOf(nextKey), captured).isEmpty())
    }

    @Test fun movingAGroupInvalidatesWarmVisibilityWhileItsRouteRemainsTheSame() {
        val model = model()
        val group = groupVisibilityKey(model)
        val previous = CatalogPlacements.target(group)
        val hidden = setOf(toolVisibilityKey("Edición de vídeo"))
        try {
            CatalogPlacements.set(group, "none")
            visibleCatalog(listOf(model), emptySet(), emptyMap())
            assertTrue(isCatalogModelVisible(model, hidden))
            CatalogPlacements.set(group, "tool:Edición de vídeo")
            assertFalse(isCatalogModelVisible(model, hidden))
            assertEquals(routeVisibilityKey(model), catalogVisibilityKeys(model).route)
            CatalogPlacements.set(group, "none")
            assertTrue(isCatalogModelVisible(model, hidden))
        } finally {
            CatalogPlacements.set(group, previous)
        }
    }
}
