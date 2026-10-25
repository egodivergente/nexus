package com.vidadomu.nexus

import com.vidadomu.nexus.data.repository.CatalogNameMigration
import com.vidadomu.nexus.data.repository.toolVisibilityKey
import com.vidadomu.nexus.data.repository.familyVisibilityKey
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogNameMigrationTest {
    @Test fun savedCategoryKeysFollowTheNewNames() {
        assertEquals(toolVisibilityKey("Upscale imagen"), CatalogNameMigration.rename(toolVisibilityKey("Mejorar imagen")))
        assertEquals(familyVisibilityKey("Upscale vídeo"), CatalogNameMigration.rename(familyVisibilityKey("Mejorar vídeo")))
        assertEquals("[\"family:Upscale vídeo\",\"family:Seedance\"]", CatalogNameMigration.rename("[\"family:Mejorar vídeo\",\"family:Seedance\"]"))
    }

    @Test fun modelNamesAndOtherTextAreLeftAlone() {
        for (text in listOf("Mejorar imagenes", "route:atlas:vendor/upscale", "family:Seedance", "Magnific · Mejorar vídeos"))
            assertEquals(text, CatalogNameMigration.rename(text))
    }

    @Test fun migrationIsIdempotent() {
        val once = CatalogNameMigration.rename("tool:Mejorar imagen")
        assertEquals(once, CatalogNameMigration.rename(once))
    }
}
