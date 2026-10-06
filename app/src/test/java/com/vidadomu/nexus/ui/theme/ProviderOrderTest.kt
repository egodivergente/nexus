package com.vidadomu.nexus.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderOrderTest {
    @Test fun firstReorderStoresVisibleSequence() {
        assertEquals(listOf("Kling", "MiniMax", "Alibaba"),
            mergeVisibleOrder(emptyList(), listOf("Kling", "MiniMax", "Alibaba")))
    }
    @Test fun searchAndFavoritesKeepHiddenSlotsUntouched() {
        assertEquals(listOf("MiniMax", "hidden1", "Kling", "hidden2"),
            mergeVisibleOrder(listOf("Kling", "hidden1", "MiniMax", "hidden2"), listOf("MiniMax", "Kling")))
    }
    @Test fun newProviderIsAddedWithoutDroppingExistingProviders() {
        assertEquals(listOf("ByteDance", "hidden", "Kling"),
            mergeVisibleOrder(listOf("Kling", "hidden"), listOf("ByteDance", "Kling")))
    }
    @Test fun emptyFilterDoesNotDestroySavedOrder() {
        assertEquals(listOf("Kling", "MiniMax"),
            mergeVisibleOrder(listOf("Kling", "MiniMax"), emptyList()))
    }
    @Test fun repeatedMovesPreserveHiddenSlots() {
        val first=mergeVisibleOrder(listOf("A", "hidden", "B", "C"), listOf("C", "A", "B"))
        assertEquals(listOf("B", "hidden", "C", "A"), mergeVisibleOrder(first,listOf("B", "C", "A")))
    }
}
