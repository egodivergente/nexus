package com.vidadomu.nexus.ui.create

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.core.model.ModelParameter
import com.vidadomu.nexus.core.model.ParameterType
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** Presentation only: no provider, repository, generation or stored data is touched. */
class NativeParameterControlUiTest {
    @get:Rule val compose = createComposeRule()

    private val seed = ModelParameter("seed", "Semilla", ParameterType.SEED, minValue = 0f, maxValue = 2.14748365E9f,
        isAdvanced = true, nativeType = "integer", minExact = "0", maxExact = "2147483647")
    private val camera = ModelParameter("camera_fixed", "Cámara fija", ParameterType.BOOLEAN, nativeType = "boolean")

    // NexusTheme fija su propia densidad (preferencias de apariencia): la escala de prueba va DENTRO del tema.
    private fun show(parameter: ModelParameter, initial: String, fontScale: Float, onValue: (String) -> Unit = {}) {
        check(BuildConfig.OFFLINE_QA)
        var applied = Float.NaN
        compose.setContent {
            NexusTheme {
                val spanish = Configuration(LocalConfiguration.current).apply { setLocale(Locale("es", "ES")) }
                val density = LocalDensity.current
                CompositionLocalProvider(LocalConfiguration provides spanish,
                    LocalDensity provides Density(density.density, fontScale)) {
                    applied = LocalDensity.current.fontScale
                    Column(Modifier.width(360.dp).padding(16.dp)) {
                        var value by remember { mutableStateOf(initial) }
                        NativeParameterControl(parameter, value) { value = it; onValue(it) }
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals(fontScale, applied, 0f) }
    }

    @Test fun seedRangeShowsExactInt32LimitAtFontScaleTwo() {
        show(seed, "", 2f)
        compose.onNodeWithText("Semilla").assertIsDisplayed()
        compose.onNodeWithText("Rango: 0 – 2.147.483.647").assertIsDisplayed()
        compose.onAllNodesWithText("E9", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("2.147.483.648", substring = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Detalles de Semilla").assertIsDisplayed().performClick()
        compose.onNodeWithText("Clave técnica: seed").assertIsDisplayed()
        assertEquals(2, compose.onAllNodesWithText("Rango: 0 – 2.147.483.647").fetchSemanticsNodes().size)
    }

    @Test fun automaticYesAndNoStayDistinctAndSelectedAtFontScaleTwo() {
        var last: String? = null
        show(camera, "", 2f) { last = it }
        val automatic = compose.onNodeWithContentDescription("Cámara fija: Automático")
        val yes = compose.onNodeWithContentDescription("Cámara fija: Sí")
        val no = compose.onNodeWithContentDescription("Cámara fija: No")
        automatic.assertIsDisplayed().assertIsSelected()
        yes.assertIsDisplayed().assertIsNotSelected()
        no.assertIsDisplayed().assertIsNotSelected()
        no.performClick()
        compose.runOnIdle { assertEquals("false", last) }
        no.assertIsSelected(); automatic.assertIsNotSelected()
        automatic.performClick()
        compose.runOnIdle { assertEquals("", last) }
        automatic.assertIsSelected(); no.assertIsNotSelected()
    }
}
