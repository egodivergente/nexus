package com.vidadomu.nexus.assistant

import org.junit.Assert.*
import org.junit.Test

class AssistantPresentationTest {
    @Test fun existingUsersKeepBalancedCardsAndCanDisableAnimationIndependently() {
        assertEquals(AssistantCardSize.BALANCED, AssistantPresentation().cardSize)
        assertEquals(AssistantPresentation(animateGeneration = false).cardSize, AssistantCardSize.BALANCED)
    }
    @Test fun storedSizesRoundTripAndUnknownValuesFallBackSafely() {
        AssistantCardSize.entries.forEach { assertEquals(it, AssistantCardSize.fromStored(it.name)) }
        listOf(null, "", "future-size", "compact").forEach { assertEquals(AssistantCardSize.BALANCED, AssistantCardSize.fromStored(it)) }
    }
    @Test fun sizesGrowAndEvenSmallThumbnailsRemainTouchable() {
        val sizes = AssistantCardSize.entries
        assertTrue(sizes.all { it.imageDp >= 48 && it.videoDp >= 48 })
        assertTrue(sizes.zipWithNext().all { (a,b) -> a.imageDp < b.imageDp && a.videoDp < b.videoDp })
    }
    @Test fun zoomIsBoundedAndCorruptGesturesCannotHideAnImage() {
        assertEquals(1f, boundedImageScale(.5f), 0f)
        assertEquals(2f, boundedImageScale(2f), 0f)
        assertEquals(5f, boundedImageScale(10f), 0f)
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { assertEquals(1f, boundedImageScale(it), 0f) }
    }
}
