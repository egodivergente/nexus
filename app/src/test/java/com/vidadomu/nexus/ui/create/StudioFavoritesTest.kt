package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.NexusModel
import org.junit.Assert.*
import org.junit.Test

class StudioFavoritesTest {
    private fun model(id: String, provider: String = "atlas", modality: Modality = Modality.IMAGE) =
        NexusModel(id, id, provider, organization = provider, modalities = listOf(modality),
            capabilities = emptyList(), parameters = emptyList())

    @Test fun defaultsApplyOnlyBeforeUserPersonalizes() {
        val catalog = listOf(model("seedream 5.0 pro"))
        assertEquals(1, studioFavorites(catalog, Modality.IMAGE, null).size)
        assertTrue(studioFavorites(catalog, Modality.IMAGE, emptyList()).isEmpty())
    }

    @Test fun providerIdentityOrderAndDuplicatesAreHandledExactly() {
        val a = model("same", "one")
        val b = model("same", "two")
        val selected = listOf(b.studioKey(), a.studioKey(), b.studioKey())
        assertEquals(listOf(b, a), studioFavorites(listOf(a, b), Modality.IMAGE, selected).map { it.model })
    }

    @Test fun missingOrWrongModalityNeverSubstitutesAnUnrequestedModel() {
        val image = model("image")
        val video = model("video", modality = Modality.VIDEO)
        val saved = listOf(StudioModelKey("missing", "missing"), video.studioKey(), image.studioKey())
        assertEquals(listOf(image), studioFavorites(listOf(image, video), Modality.IMAGE, saved).map { it.model })
        assertEquals(listOf(video), studioFavorites(listOf(image, video), Modality.VIDEO, saved).map { it.model })
    }

    @Test fun temporarilyMissingModelReturnsInItsSavedPosition() {
        val a = model("a"); val b = model("b")
        val saved = listOf(b.studioKey(), a.studioKey())
        assertEquals(listOf(a), studioFavorites(listOf(a), Modality.IMAGE, saved).map { it.model })
        assertEquals(listOf(b, a), studioFavorites(listOf(a, b), Modality.IMAGE, saved).map { it.model })
    }

    @Test fun reorderingKeepsIdentityAndIgnoresOutOfBoundsMoves() {
        val a = model("a").studioKey(); val b = model("b").studioKey()
        assertEquals(listOf(b, a), moveStudioFavorite(listOf(a, b), b, -1))
        assertEquals(listOf(a, b), moveStudioFavorite(listOf(a, b), a, -1))
        assertEquals(listOf(a, b), moveStudioFavorite(listOf(a, b), b, 1))
    }
}
