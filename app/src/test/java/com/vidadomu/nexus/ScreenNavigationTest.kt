package com.vidadomu.nexus

import com.vidadomu.nexus.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScreenNavigationTest {
    @Test fun bottomNavigationHasEveryInitializedDestination() {
        assertEquals("models", Screen.Models.route)
        assertEquals("flow", Screen.Flow.route)
        org.junit.Assert.assertTrue(Screen.Flow in Screen.bottomNavItems)
        assertEquals(
            listOf("assistant", "models", "create", "flow", "library"),
            Screen.bottomNavItems.map { it.route }
        )
    }
}
