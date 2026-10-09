package com.vidadomu.nexus

import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.ui.components.*
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BalanceSheetUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun balanceAndSettingsRemainReachableWithoutSubmitting() {
        check(BuildConfig.OFFLINE_QA)
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
        runBlocking { app.ready.await(); app.database.loading.first { !it } }
        val initial=app.generationRepository.allJobs.value.map {it.id}
        var settings=false
        compose.setContent { NexusTheme { NexusChrome({settings=true},{fail("No output was selected")}) {
            NexusTopAppBar(title={Text("Lienzo")})
        } } }
        compose.onNodeWithContentDescription("Ajustes de Nexus").performClick()
        compose.runOnIdle { assertTrue(settings) }
        compose.onNodeWithContentDescription("Saldo y gasto").performClick()
        compose.onNodeWithText("Saldo y gasto").assertIsDisplayed()
        compose.onAllNodesWithText("Actualizar")[0].performClick()
        compose.runOnIdle { assertEquals(initial,app.generationRepository.allJobs.value.map {it.id}) }
    }
}
