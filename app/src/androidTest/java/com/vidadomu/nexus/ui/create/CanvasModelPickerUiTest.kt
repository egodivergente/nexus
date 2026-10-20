package com.vidadomu.nexus.ui.create

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.currency.CurrencyManager
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * OFFLINE_QA fixtures passed straight to the sheet: no catalog repository, provider,
 * network or generation. CurrencyManager is only read for formatting; its rate is never refreshed.
 * Every lookup scrolls first, so results do not depend on the device's screen height.
 */
class CanvasModelPickerUiTest {
    @get:Rule val compose=createComposeRule()
    private val chosen=mutableListOf<String>()
    private var dismissed=0
    private val currency:CurrencyManager
        get()=(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication).currencyManager

    private fun model(name:String,modality:Modality)=NexusModel("fixture-"+name.lowercase().replace(' ','-'),name,"fixture",
        organization="Estudio",modalities=listOf(modality),capabilities=emptyList(),parameters=emptyList())
    private val still=model("Retrato fijo",Modality.IMAGE).copy(basePriceUsd=0.05,priceUnit="image",priceUnitVerified=true)
    private val motion=model("Travelling lento",Modality.VIDEO)

    private fun show(initial:Modality,fontScale:Float?=null,observedScale:(Float)->Unit={}) {
        check(BuildConfig.OFFLINE_QA)
        val manager=currency
        compose.setContent {NexusTheme {
            val sheet=@Composable {
                ModelPickerSheet(listOf(still,motion),null,initial,emptyList(),manager,{chosen+=it.id},{dismissed++})
            }
            if(fontScale==null) sheet()
            else {
                val base=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density,fontScale)) {
                    observedScale(LocalDensity.current.fontScale)
                    sheet()
                }
            }
        }}
    }
    private val catalogList get()=compose.onNode(hasScrollToNodeAction() and !hasAnyChild(hasText("Imagen")))
    private val modalityChips get()=compose.onNode(hasScrollToNodeAction() and hasAnyChild(hasText("Imagen")))
    private fun reveal(text:String):SemanticsNodeInteraction {
        catalogList.performScrollToNode(hasText(text))
        return compose.onNodeWithText(text)
    }

    @Test fun startsInVideoAndHidesImageModels() {
        show(Modality.VIDEO)
        compose.onNodeWithText("1 modelo",substring=true).assertExists()
        reveal("Travelling lento").assertIsDisplayed()
        compose.onNodeWithText("Retrato fijo").assertDoesNotExist()
        compose.runOnIdle {assertTrue(chosen.isEmpty());assertEquals(0,dismissed)}
    }

    @Test fun impossibleSearchThenSeeWholeCatalogRestoresBothModalities() {
        show(Modality.VIDEO)
        compose.onNode(hasSetTextAction()).performTextInput("zzzz-imposible")
        reveal("No hay modelos con estos filtros").assertIsDisplayed()
        compose.onNodeWithText("Travelling lento").assertDoesNotExist()
        reveal("Ver todo el catálogo").performClick()
        reveal("Retrato fijo").assertIsDisplayed()
        reveal("Travelling lento").assertIsDisplayed()
        compose.onNodeWithText("No hay modelos con estos filtros").assertDoesNotExist()
        compose.runOnIdle {assertTrue(chosen.isEmpty())}
    }

    @Test fun selectingReportsExactlyThatModelAndNothingElse() {
        show(Modality.IMAGE)
        reveal("Retrato fijo").performClick()
        compose.runOnIdle {
            assertEquals(listOf(still.id),chosen)
            assertEquals(0,dismissed)
        }
    }

    @Test fun largeTextInsideThemeKeepsFiltersPriceAndSelectionReachable() {
        var observed=0f
        show(Modality.IMAGE,fontScale=1.3f) {observed=it}
        compose.runOnIdle {assertEquals(1.3f,observed,0.001f)}
        catalogList.performScrollToNode(hasAnyChild(hasText("Imagen")))
        modalityChips.performScrollToNode(hasText("Todo el catálogo"))
        compose.onNodeWithText("Todo el catálogo").assertIsDisplayed().performClick()
        val price="Estudio · ${currency.formatUnitPrice(0.05)} / imagen"
        reveal(price).assertExists()
        reveal("Travelling lento").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(listOf(motion.id),chosen)}
    }
}
