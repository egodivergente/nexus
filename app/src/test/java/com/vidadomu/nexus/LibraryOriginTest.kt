package com.vidadomu.nexus

import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetSource
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.ui.library.LibraryOriginFilter
import com.vidadomu.nexus.ui.library.libraryOrigin
import com.vidadomu.nexus.ui.library.matchesLibraryOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryOriginTest {
    private fun asset(
        source: AssetSource = AssetSource.IMPORTED,
        parentAssetId: String? = null,
    ) = Asset(
        id = "asset",
        uri = "content://library/asset",
        type = AssetType.IMAGE,
        source = source,
        displayName = "Imagen",
        parentAssetId = parentAssetId,
        createdAt = 1,
    )

    @Test fun labelsImportedGeneratedEditedAndSharedOriginsSeparately() {
        assertEquals(LibraryOriginFilter.IMPORTED, libraryOrigin(asset()))
        assertEquals(LibraryOriginFilter.GENERATED, libraryOrigin(asset(AssetSource.GENERATED)))
        assertEquals(LibraryOriginFilter.EDITED, libraryOrigin(asset(parentAssetId = "source")))
        assertEquals(LibraryOriginFilter.SHARED, libraryOrigin(asset(AssetSource.SHARED)))
    }

    @Test fun originFilterIncludesOnlyMatchingAssetsOrEverything() {
        val imported = asset()
        val generated = asset(AssetSource.GENERATED)
        assertTrue(imported.matchesLibraryOrigin(LibraryOriginFilter.ALL))
        assertTrue(imported.matchesLibraryOrigin(LibraryOriginFilter.IMPORTED))
        assertFalse(imported.matchesLibraryOrigin(LibraryOriginFilter.GENERATED))
        assertTrue(generated.matchesLibraryOrigin(LibraryOriginFilter.GENERATED))
    }
}
