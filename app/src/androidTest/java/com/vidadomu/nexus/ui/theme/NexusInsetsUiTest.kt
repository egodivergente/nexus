package com.vidadomu.nexus.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

// Insets falsos y fijos: no dependen del recorte ni de las barras del dispositivo que ejecute la prueba.
class NexusInsetsUiTest {
    @get:Rule val compose = createComposeRule()
    private val none = WindowInsets(0, 0, 0, 0)
    private val topOnly = WindowInsetsSides.Top
    private val overlay = WindowInsetsSides.Top + WindowInsetsSides.Horizontal

    @Composable private fun Header(insets: WindowInsets) {
        Box(Modifier.windowInsetsPadding(insets)) { Box(Modifier.size(10.dp).testTag("header")) }
    }

    private fun header() = compose.onNodeWithTag("header")

    @Test fun hiddenBarsStillClearTheCameraCutout() {
        compose.setContent { Header(nexusChromeInsets(none, WindowInsets(top = 36.dp), topOnly)) }
        header().assertTopPositionInRootIsEqualTo(36.dp)
    }

    @Test fun visibleBarAndCutoutTakeTheLargerNotTheSum() {
        compose.setContent { Header(nexusChromeInsets(WindowInsets(top = 24.dp), WindowInsets(top = 36.dp), topOnly)) }
        header().assertTopPositionInRootIsEqualTo(36.dp)
    }

    @Test fun noCutoutAndHiddenBarsReserveNothing() {
        compose.setContent { Header(nexusChromeInsets(none, none, overlay)) }
        header().assertTopPositionInRootIsEqualTo(0.dp)
        header().assertLeftPositionInRootIsEqualTo(0.dp)
    }

    @Test fun landscapeSideCutoutIsNotAppliedTwiceUnderTheRoot() {
        val sideCutout = WindowInsets(left = 36.dp)
        compose.setContent {
            // Como MainActivity: la raíz ya aplica (y consume) el borde horizontal seguro.
            Box(Modifier.fillMaxSize().windowInsetsPadding(sideCutout.only(WindowInsetsSides.Horizontal))) {
                Header(nexusChromeInsets(none, sideCutout, overlay))
            }
        }
        header().assertLeftPositionInRootIsEqualTo(36.dp)
        header().assertTopPositionInRootIsEqualTo(0.dp)
    }

    @Test fun overlayInOwnWindowClearsSideCutout() {
        compose.setContent { Header(nexusChromeInsets(none, WindowInsets(right = 36.dp, left = 36.dp), overlay)) }
        header().assertLeftPositionInRootIsEqualTo(36.dp)
        header().assertTopPositionInRootIsEqualTo(0.dp)
    }

    @Test fun topOnlyHeaderLeavesSidesToTheRoot() {
        compose.setContent { Header(nexusChromeInsets(none, WindowInsets(left = 36.dp), topOnly)) }
        header().assertLeftPositionInRootIsEqualTo(0.dp)
    }

    @Test fun insetAlreadyConsumedByParentIsNotAddedAgain() {
        val consumed = PaddingValues(top = 36.dp)
        compose.setContent {
            Box(Modifier.fillMaxSize().padding(consumed).consumeWindowInsets(consumed)) {
                Header(nexusChromeInsets(none, WindowInsets(top = 36.dp), topOnly))
            }
        }
        header().assertTopPositionInRootIsEqualTo(36.dp)
    }
}
