package com.vidadomu.nexus

import com.vidadomu.nexus.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenNavigationTest {
    @Test fun bottomNavigationHasEveryInitializedDestination() {
        assertEquals("models", Screen.Models.route)
        assertEquals(
            listOf("assistant", "create", "models", "generations", "library"),
            Screen.bottomNavItems.map { it.route }
        )
    }
}
