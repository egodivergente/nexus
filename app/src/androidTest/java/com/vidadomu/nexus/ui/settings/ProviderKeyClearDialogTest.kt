package com.vidadomu.nexus.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProviderKeyClearDialogTest {
    @get:Rule val compose = createComposeRule()
    private var clears = 0

    private fun showRow(providerName: String? = "Atlas Cloud", onClear: (() -> Unit)? = { clears++ }, masked: String? = "sk-••••1234") {
        compose.setContent { MaterialTheme { ProviderKeyRow(masked, {}, onClear, providerName) } }
    }

    @Test fun borrarOnlyOpensConfirmationNamingTheProvider() {
        showRow()
        compose.onNodeWithText("Borrar").performClick()
        compose.onNodeWithText("¿Borrar la clave de Atlas Cloud?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, clears) }
    }

    @Test fun cancelKeepsTheKey() {
        showRow()
        compose.onNodeWithText("Borrar").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("¿Borrar la clave de Atlas Cloud?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, clears) }
    }

    @Test fun backKeepsTheKey() {
        showRow()
        compose.onNodeWithText("Borrar").performClick()
        compose.onNodeWithText("¿Borrar la clave de Atlas Cloud?").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("¿Borrar la clave de Atlas Cloud?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, clears) }
    }

    @Test fun confirmClearsExactlyOnceAndCloses() {
        showRow()
        compose.onNodeWithText("Borrar").performClick()
        compose.onNodeWithText("Borrar clave").performClick()
        compose.onNodeWithText("¿Borrar la clave de Atlas Cloud?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, clears) }
    }

    @Test fun switchingProviderWithDialogOpenDiscardsConfirmation() {
        val target = mutableStateOf("Atlas Cloud" to "sk-••••1234")
        compose.setContent { MaterialTheme { ProviderKeyRow(target.value.second, {}, { clears++ }, target.value.first) } }
        compose.onNodeWithText("Borrar").performClick()
        compose.onNodeWithText("¿Borrar la clave de Atlas Cloud?").assertIsDisplayed()
        compose.runOnIdle { target.value = "Runway" to "rw-••••9876" }
        compose.onNodeWithText("¿Borrar la clave de Atlas Cloud?").assertDoesNotExist()
        compose.onNodeWithText("¿Borrar la clave de Runway?").assertDoesNotExist()
        compose.onNodeWithText("Borrar clave").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, clears) }
    }

    @Test fun missingNameFallsBackToGenericTitle() {
        showRow(providerName = null)
        compose.onNodeWithText("Borrar").performClick()
        compose.onNodeWithText("¿Borrar la clave API?").assertIsDisplayed()
    }

    @Test fun noBorrarWithoutKey() {
        showRow(masked = null)
        compose.onNodeWithText("Borrar").assertDoesNotExist()
        compose.onNodeWithText("Añadir").assertIsDisplayed()
    }

    @Test fun noBorrarWithoutClearHandler() {
        showRow(onClear = null)
        compose.onNodeWithText("Borrar").assertDoesNotExist()
        compose.onNodeWithText("Cambiar").assertIsDisplayed()
    }
}
