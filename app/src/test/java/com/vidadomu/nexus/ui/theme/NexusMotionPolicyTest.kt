package com.vidadomu.nexus.ui.theme

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NexusMotionPolicyTest {
    @Test fun zeroOrInvalidDurationScaleDisablesAnimators() {
        assertTrue(NexusMotionPolicy.animatorsEnabled(1f, processAnimatorsEnabled = true))
        assertTrue("una escala reducida sigue animando, solo más rápido", NexusMotionPolicy.animatorsEnabled(0.5f, true))
        assertFalse(NexusMotionPolicy.animatorsEnabled(0f, true))
        assertFalse(NexusMotionPolicy.animatorsEnabled(-1f, true))
        assertFalse(NexusMotionPolicy.animatorsEnabled(Float.NaN, true))
        assertFalse(NexusMotionPolicy.animatorsEnabled(Float.POSITIVE_INFINITY, true))
    }

    @Test fun processLevelDisableWinsOverGlobalSetting() {
        assertFalse("ahorro de batería apaga animadores sin tocar el ajuste global",
            NexusMotionPolicy.animatorsEnabled(1f, processAnimatorsEnabled = false))
    }

    @Test fun onlyResumedCountsAsVisible() {
        val visible = Lifecycle.State.values().filter(NexusMotionPolicy::isVisible)
        assertEquals(listOf(Lifecycle.State.RESUMED), visible)
    }

    @Test fun infiniteMotionNeedsRequestAnimatorsAndVisibility() {
        for (requested in listOf(true, false)) for (animators in listOf(true, false)) for (visible in listOf(true, false)) {
            assertEquals("requested=$requested animators=$animators visible=$visible",
                requested && animators && visible,
                NexusMotionPolicy.allowsInfiniteMotion(requested, animators, visible))
        }
    }

    @Test fun lifecycleSequenceStopsMotionWhenLeavingResumed() {
        val sequence = listOf(Lifecycle.State.CREATED, Lifecycle.State.STARTED, Lifecycle.State.RESUMED,
            Lifecycle.State.STARTED, Lifecycle.State.CREATED, Lifecycle.State.RESUMED, Lifecycle.State.DESTROYED)
        val motion = sequence.map { NexusMotionPolicy.allowsInfiniteMotion(true, true, NexusMotionPolicy.isVisible(it)) }
        assertEquals(listOf(false, false, true, false, false, true, false), motion)
    }

    @Test fun everyInfiniteTransitionInMainIsGatedByTheMotionPolicy() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first(File::isDirectory)
        val ungated = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { "rememberInfiniteTransition(" in it.readText() }
            .filterNot { text -> text.readText().let { "rememberNexusMotionAllowed" in it || "NexusMotionPolicy" in it } }
            .map { it.name }.toList()
        assertEquals("pulsos infinitos sin política de movimiento reducido", emptyList<String>(), ungated)
    }
}
