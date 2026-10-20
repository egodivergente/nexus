package com.vidadomu.nexus.ui.library

import com.vidadomu.nexus.core.model.Modality
import org.junit.Assert.*
import org.junit.Test

class LibraryViewFiltersTest {
    private val narrowed = LibraryViewFilters("puerto", Modality.VIDEO, LibraryOriginFilter.IMPORTED, favoriteOnly = true, showHidden = true)

    @Test fun clearingReturnsTheWholeLibraryInOneStep() {
        val cleared = narrowed.cleared()
        assertFalse(cleared.narrowed)
        assertEquals(LibraryViewFilters(), cleared)
    }

    @Test fun removingFiltersKeepsTheEditableQuery() {
        val kept = narrowed.withoutFilters()
        assertEquals("puerto", kept.query)
        assertFalse(kept.hasFilters)
        assertTrue(kept.hasQuery)
    }

    @Test fun messagesExplainWhatIsNarrowing() {
        assertEquals("No hay resultados para «puerto» con estos filtros.", libraryNoResultsMessage(narrowed))
        assertEquals("No hay resultados para «puerto».", libraryNoResultsMessage(LibraryViewFilters(" puerto ")))
        assertEquals("No hay archivos con estos filtros.", libraryNoResultsMessage(LibraryViewFilters(favoriteOnly = true)))
        assertEquals("Tus creaciones aparecerán aquí.", libraryNoResultsMessage(LibraryViewFilters()))
    }

    @Test fun blankQueryIsNotASearch() {
        assertFalse(LibraryViewFilters("   ").narrowed)
    }
}
