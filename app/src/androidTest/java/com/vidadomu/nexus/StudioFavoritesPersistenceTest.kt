package com.vidadomu.nexus

import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.ui.create.StudioFavoritesStore
import com.vidadomu.nexus.ui.create.StudioModelKey
import org.junit.Assert.*
import org.junit.Test

class StudioFavoritesPersistenceTest {
    @Test fun imageAndVideoPreferencesSurviveRecreationIndependently() {
        // Instrumentation runs only in the isolated offlineQa package.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("nexus_studio_favorites_v1", 0)
        prefs.edit().clear().commit()
        try {
            val first = StudioFavoritesStore(context)
            assertNull(first.read(Modality.IMAGE))
            val video = listOf(StudioModelKey("provider", "video"))
            first.save(Modality.IMAGE, emptyList())
            first.save(Modality.VIDEO, video)
            val recreated = StudioFavoritesStore(context)
            assertEquals(emptyList<StudioModelKey>(), recreated.read(Modality.IMAGE))
            assertEquals(video, recreated.read(Modality.VIDEO))
        } finally { prefs.edit().clear().commit() }
    }
}
