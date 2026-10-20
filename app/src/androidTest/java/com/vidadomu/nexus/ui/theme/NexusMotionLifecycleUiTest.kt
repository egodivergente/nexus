package com.vidadomu.nexus.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.vidadomu.nexus.ui.create.StudioGenerateButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Presentation only: lifecycle is a fake owner, no provider or generation is involved. */
class NexusMotionLifecycleUiTest {
    @get:Rule val compose = createComposeRule()

    private class FakeOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private val indeterminate = SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)

    @Test fun leavingResumedStopsMotionAndReturningRestoresTheSystemDecision() {
        val owner = FakeOwner()
        val requested = mutableStateOf(true)
        var resumed: Boolean? = null
        var animators: Boolean? = null
        var motion: Boolean? = null
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                resumed = rememberLifecycleResumed()
                animators = rememberSystemAnimatorsEnabled(refreshKey = resumed)
                motion = rememberNexusMotionAllowed(requested.value)
            }
        }
        compose.runOnIdle { assertEquals(true, resumed); assertEquals(animators, motion) }
        for (hidden in listOf(Lifecycle.State.STARTED, Lifecycle.State.CREATED)) {
            compose.runOnIdle { owner.registry.currentState = hidden }
            compose.runOnIdle { assertEquals(false, resumed); assertEquals("oculta en $hidden", false, motion) }
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals(true, resumed); assertEquals(animators, motion) }
        compose.runOnIdle { requested.value = false }
        compose.runOnIdle { assertFalse(requireNotNull(motion)) }
    }

    @Test fun hiddenBusyGenerateButtonKeepsItsLabelWithoutSpinnerOrPercentage() {
        val owner = FakeOwner()
        compose.setContent {
            NexusTheme {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    StudioGenerateButton("1,00 €", enabled = true, busy = true, busyLabel = "Enviando…", onClick = {})
                }
            }
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.onNodeWithText("Enviando…", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodes(indeterminate, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("%", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }
}
