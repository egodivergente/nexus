package com.vidadomu.nexus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.vidadomu.nexus.ui.components.ConfirmedGenerationCost
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Solo presentación: ningún repositorio ni proveedor interviene. */
class ConfirmedGenerationCostUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun longAmountAtFontScale200IsNeverClipped() {
        check(BuildConfig.OFFLINE_QA)
        val amount="1.234.567,89 US$"
        compose.setContent {
            NexusTheme {
                // NexusTheme fija su propia densidad: la escala se aplica dentro para que cuente de verdad.
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {ConfirmedGenerationCost(amount)}
            }
        }
        val node=compose.onNodeWithText(amount,useUnmergedTree=true)
        node.assertIsDisplayed().assertContentDescriptionEquals("Coste: $amount")
        val results=mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        val layout=results.single()
        assertEquals(amount,layout.layoutInput.text.text)
        assertFalse(layout.hasVisualOverflow)
        assertEquals(amount.length,layout.getLineEnd(layout.lineCount-1,visibleEnd=true))
    }
}
