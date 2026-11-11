package com.vidadomu.nexus

import com.vidadomu.nexus.ui.navigation.Screen
import com.vidadomu.nexus.ui.navigation.adjacentTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenNavigationTest {
    @Test fun bottomNavigationHasEveryInitializedDestination() {
        val routes = Screen.bottomNavItems.map { it.route }
        assertEquals(
            listOf("assistant", "story", "models", "cut", "library", "flow"),
            routes
        )
        assertEquals(routes.size, routes.toSet().size)
        assertEquals("Guion", Screen.Story.title)
        assertEquals("Cut", Screen.Cut.title)
    }

    @Test fun existingSavedRoutesStillResolveToTheirDestinationAfterAddingCut() {
        // The workspace stores route names. Existing selections must survive insertion of a tab.
        val byRoute = Screen.bottomNavItems.associateBy { it.route }
        assertEquals(Screen.Library, byRoute["library"])
        assertEquals(Screen.Flow, byRoute["flow"])
        assertEquals(Screen.Models, byRoute["models"])
        assertEquals(Screen.Cut, byRoute["cut"])
        assertEquals(Screen.Assistant, Screen.bottomNavItems.first())
    }

    @Test fun swipingIncludesStoryAndCutAndStopsAtTheWorkspaceEdges() {
        val routes = Screen.bottomNavItems.map { it.route }
        assertEquals(Screen.Story.route, adjacentTab(Screen.Assistant.route, 1, routes))
        assertEquals(Screen.Assistant.route, adjacentTab(Screen.Story.route, -1, routes))
        assertEquals(Screen.Models.route, adjacentTab(Screen.Story.route, 1, routes))
        assertEquals(Screen.Cut.route, adjacentTab(Screen.Models.route, 1, routes))
        assertEquals(Screen.Library.route, adjacentTab(Screen.Cut.route, 1, routes))
        assertEquals(Screen.Cut.route, adjacentTab(Screen.Library.route, -1, routes))
        assertEquals(Screen.Flow.route, adjacentTab(Screen.Library.route, 1, routes))
        assertNull(adjacentTab(Screen.Assistant.route, -1, routes))
        assertNull(adjacentTab(Screen.Flow.route, 1, routes))
        assertNull(adjacentTab(Screen.Settings.route, 1, routes))
        assertNull(adjacentTab("cut/a-saved-montage", 1, routes))
    }
}
