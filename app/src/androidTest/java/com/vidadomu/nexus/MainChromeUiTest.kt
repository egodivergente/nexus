package com.vidadomu.nexus

import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.ui.navigation.Screen
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Runs the real Activity, including system font scale, navigation and edge-to-edge insets. */
class MainChromeUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun fiveDestinationsAndAssistantSettingsReturnRemainUsable() {
        check(BuildConfig.OFFLINE_QA)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as NexusApplication
        check(app.packageName.endsWith(".uiqa"))
        val originalIds=app.generationRepository.allJobs.value.map {it.id}
        val scale=app.resources.configuration.fontScale
        val folder=File(app.getExternalFilesDir(null),"ui-audit/main-$scale").apply {mkdirs()}
        for(screen in Screen.bottomNavItems) {
            compose.onNode(hasContentDescription(screen.title) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()
            compose.onNodeWithContentDescription("Saldo y gasto").assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithContentDescription("Ajustes de Nexus").assertIsDisplayed()
            compose.waitForIdle();android.os.SystemClock.sleep(700)
            val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(folder,"${screen.route}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
        fun assistantTab()=compose.onNode(hasContentDescription(Screen.Assistant.title) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()
        assistantTab()
        compose.onNodeWithContentDescription("Ajustes de Nexus").performClick()
        compose.onNode(hasContentDescription(Screen.Models.title) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()
        assistantTab()
        compose.onNodeWithContentDescription("Ajustes del asistente").assertIsDisplayed()
        compose.onNodeWithText("Cuéntame qué quieres crear…").assertExists()
        compose.runOnIdle {check(originalIds.all {id->app.generationRepository.allJobs.value.any {it.id==id}})}
    }
}
