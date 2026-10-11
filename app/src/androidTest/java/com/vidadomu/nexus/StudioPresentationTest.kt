package com.vidadomu.nexus

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.components.*
import com.vidadomu.nexus.ui.create.StudioGenerateButton
import com.vidadomu.nexus.ui.create.StudioToolRow
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Tests only presentation callbacks. No provider, repository or paid operation is invoked. */
class StudioPresentationTest {
    @get:Rule val compose=createComposeRule()

    @Test fun enlargedTextKeepsAllStudioActionsAndTheFullGeneratePrice() {
        val scale=mutableFloatStateOf(1f)
        val cost=mutableStateOf("8,27 €")
        val labels=listOf("Inspirar","5 s · 720p · 16:9","Varios modelos","Coste","Mis ajustes")
        var clicks=0
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale.floatValue)) {
                NexusTheme {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {Column(Modifier.width(360.dp).padding(16.dp)) {
                        StudioToolRow {labels.forEach {StudioToolChip(it) {clicks++}}}
                        StudioGenerateButton(cost.value,true,false,"Preparando…",{clicks++})
                    }}
                }
            }
        }
        for(fontScale in listOf(1.3f,1.5f)) {
            compose.runOnIdle {scale.floatValue=fontScale}
            labels.forEach { label -> compose.onNodeWithText(label).assertIsDisplayed().performClick() }
            for(price in listOf("8,27 €","123.456,78 €")) {
            compose.runOnIdle {cost.value=price}
            val generate=compose.onNodeWithText("Generar · $price")
            generate.assertIsDisplayed()
            val results=mutableListOf<TextLayoutResult>()
            generate.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
            assertTrue(results.isNotEmpty())
            captureLayout("price-$fontScale-${if(price=="8,27 €") "short" else "long"}",results.single())
            assertFullyVisible(results.single())
            generate.performClick()
            }
        }
        compose.runOnIdle {assertEquals(14,clicks)}
    }

    @Test fun technicalErrorsExpandOnlyWhenRequested() {
        compose.setContent {NexusTheme {InlineNotice("Comprueba la red.",technicalDetails="UnknownHostException fixture.invalid")}}
        compose.onNodeWithText("UnknownHostException fixture.invalid").assertDoesNotExist()
        compose.onNodeWithText("Detalles").performClick()
        compose.onNodeWithText("UnknownHostException fixture.invalid").assertIsDisplayed()
        compose.onNodeWithText("Ocultar detalles").performClick()
        compose.onNodeWithText("UnknownHostException fixture.invalid").assertDoesNotExist()
    }

    @Test fun creditBalanceIsCompleteAndHasAnAccessibleTouchTargetAtLargeScale() {
        var clicks=0
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {
                NexusTheme {Box(Modifier.fillMaxSize().safeDrawingPadding()) {StudioBalanceChip("1.622 créditos") {clicks++}}}
            }
        }
        val results=mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("1.622 créditos",useUnmergedTree=true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        captureLayout("credits-1.5",results.single())
        assertFullyVisible(results.single())
        compose.onNodeWithContentDescription("Saldo y gasto").assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle {assertEquals(1,clicks)}
    }

    private fun assertFullyVisible(result:TextLayoutResult) {
        assertFalse(result.didOverflowHeight)
        // The paragraph can retain the parent's maximum width while Text wraps to
        // its intrinsic width. Check drawn lines, allowing only IntSize rounding.
        for(line in 0 until result.lineCount) {
            assertTrue("Line exceeds its rendered width",result.getLineRight(line)<=result.size.width+1f)
            assertTrue("Line starts outside its rendered width",result.getLineLeft(line)>=-1f)
            assertFalse(result.isLineEllipsized(line))
        }
        assertEquals(result.layoutInput.text.length,result.getLineEnd(result.lineCount-1,visibleEnd=true))
    }

    private fun captureLayout(name:String,result:TextLayoutResult) {
        println("StudioLayout $name size=${result.size} lines=${result.lineCount} lineBounds=${(0 until result.lineCount).map {result.getLineLeft(it) to result.getLineRight(it)}} heightOverflow=${result.didOverflowHeight} constraints=${result.layoutInput.constraints} text=${result.layoutInput.text}")
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val folder=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"ui-audit/studio-layout").apply {mkdirs()}
        android.os.SystemClock.sleep(1000)
        val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(folder,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
}
