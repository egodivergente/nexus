package com.vidadomu.nexus

import com.vidadomu.nexus.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScreenNavigationTest {
    @Test fun bottomNavigationHasEveryInitializedDestination() {
        assertEquals("models", Screen.Models.route)
        assertEquals("flow", Screen.Flow.route)
        assertFalse(Screen.Flow in Screen.bottomNavItems)
        assertEquals(
            listOf("assistant", "models", "create", "library"),
            Screen.bottomNavItems.map { it.route }
        )
    }
}
