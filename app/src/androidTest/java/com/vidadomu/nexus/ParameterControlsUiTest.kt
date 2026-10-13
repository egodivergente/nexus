package com.vidadomu.nexus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.create.NativeParameterControl
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ParameterControlsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun booleanRetainsAllThreeNativeStatesAndRestore() {
        val param = ModelParameter("return_last_frame", "Return last frame", ParameterType.BOOLEAN, "false")
        val value = mutableStateOf("")
        compose.setContent { NexusTheme {
            NativeParameterControl(param, value.value) { value.value = it }
        } }
        compose.onNodeWithText("Automático").assertIsSelected()
        compose.onNodeWithText("Sí").performClick()
        compose.runOnIdle { assertEquals("true", value.value) }
        compose.onNodeWithText("No").performClick()
        compose.runOnIdle { assertEquals("false", value.value) }
        compose.onNodeWithText("Automático").performClick()
        compose.runOnIdle { assertEquals("", value.value) }
        compose.onNodeWithContentDescription("Detalles de Guardar último fotograma").performClick()
        compose.onNodeWithText("Restaurar").performClick()
        compose.runOnIdle { assertEquals("false", value.value) }
    }

    @Test fun requiredBooleanDoesNotOfferAnOmittedValue() {
        val param = ModelParameter("generate_audio", "Generate audio", ParameterType.BOOLEAN, "true", isRequired = true)
        compose.setContent { NexusTheme { NativeParameterControl(param, "true") {} } }
        compose.onNodeWithText("Automático").assertDoesNotExist()
        compose.onNodeWithText("Sí").assertIsSelected()
    }

    @Test fun visualOptionsKeepNativeValuesAndNoticesKeepDetails() {
        val ratio = mutableStateOf("16:9")
        compose.setContent { NexusTheme {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Parámetros")
                NativeParameterControl(ModelParameter("duration", "Duration", ParameterType.ENUM, "5",
                    options = listOf("4", "5", "10")), "5") {}
                NativeParameterControl(ModelParameter("resolution", "Resolution", ParameterType.ENUM, "1080p",
                    options = listOf("720p", "1080p")), "1080p") {}
                NativeParameterControl(ModelParameter("ratio", "Aspect ratio", ParameterType.ENUM, "16:9",
                    options = listOf("1:1", "16:9", "9:16")), ratio.value) { ratio.value = it }
                NativeParameterControl(ModelParameter("generate_audio", "Generate audio", ParameterType.BOOLEAN,
                    "true"), "true") {}
                InlineNotice("No se pudo actualizar este trabajo. Consulta su estado antes de repetir.",
                    technicalDetails = "Diagnóstico de prueba")
            }
        } }
        compose.onNodeWithText("9:16 · Vertical").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("9:16", ratio.value) }
        compose.onNodeWithText("9:16 · Vertical").assertIsSelected()
        capture("parameters")
        compose.onNodeWithText("Diagnóstico de prueba").assertDoesNotExist()
        compose.onNodeWithText("Detalles").performScrollTo().performClick()
        compose.onNodeWithText("Diagnóstico de prueba").performScrollTo().assertIsDisplayed()
        capture("notice-details")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "ui-audit/parameters").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(folder, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
