package explorer.render

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.currency.CurrencyManager
import com.vidadomu.nexus.core.currency.CurrencyState
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.data.repository.GenerationRepository
import com.vidadomu.nexus.data.repository.ModelCatalogRepository
import com.vidadomu.nexus.ui.components.GenerationProgressPanel
import com.vidadomu.nexus.ui.library.LibraryScreen
import com.vidadomu.nexus.ui.models.ModelsScreen
import com.vidadomu.nexus.ui.theme.NexusTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.stubbing.Answer

/** Calls production composables; replaces data services only. No Application.onCreate. */
class ExplorerSourceRenderTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    private fun context(theme: String): Context {
        val editor = mock(SharedPreferences.Editor::class.java, RETURNS_SELF)
        val preferences = mock(SharedPreferences::class.java, Answer { call ->
            when (call.method.name) {
                "getString" -> if (call.arguments[0] == "theme") theme else call.arguments[1]
                "getStringSet", "getInt", "getLong", "getFloat", "getBoolean" -> call.arguments[1]
                "getAll" -> emptyMap<String, Any>()
                "contains" -> false
                "edit" -> editor
                else -> null
            }
        })
        val app = mock(NexusApplication::class.java)
        val catalog = mock(ModelCatalogRepository::class.java)
        val generations = mock(GenerationRepository::class.java)
        val currency = mock(CurrencyManager::class.java)
        val models = listOf("Kling", "Wan", "Seedance", "Hailuo", "Vidu", "PixVerse").map { family ->
            NexusModel(id = "render/${family.lowercase()}", displayName = "$family · ejemplo",
                providerId = "render_fixture", organization = family, family = family,
                modalities = listOf(Modality.VIDEO), capabilities = listOf(ModelCapability.TEXT_TO_VIDEO),
                parameters = emptyList(), description = "Modelo de ejemplo para inspección visual")
        }
        `when`(catalog.models).thenReturn(MutableStateFlow(models))
        `when`(catalog.isLoading).thenReturn(MutableStateFlow(false))
        `when`(catalog.errorMessage).thenReturn(MutableStateFlow<String?>(null))
        `when`(catalog.hiddenEntries).thenReturn(MutableStateFlow(emptyMap<String, String>()))
        `when`(generations.allJobs).thenReturn(MutableStateFlow(emptyList<GenerationJob>()))
        `when`(currency.state).thenReturn(MutableStateFlow(CurrencyState()))
        `when`(app.modelCatalogRepository).thenReturn(catalog)
        `when`(app.generationRepository).thenReturn(generations)
        `when`(app.currencyManager).thenReturn(currency)
        `when`(app.getSharedPreferences(anyString(), anyInt())).thenReturn(preferences)
        return object : ContextWrapper(paparazzi.context) {
            override fun getApplicationContext(): Context = app
            override fun getSharedPreferences(name: String, mode: Int) = preferences
        }
    }

    private fun render(theme: String, content: @Composable () -> Unit) {
        val fixtureContext = context(theme)
        paparazzi.snapshot {
            CompositionLocalProvider(LocalContext provides fixtureContext) {
                NexusTheme { content() }
            }
        }
    }

    @Test fun modelsAurora() = render("AURORA") { ModelsScreen(onSelectModelAndCreate = {}) }
    @Test fun modelsLight() = render("LIGHT") { ModelsScreen(onSelectModelAndCreate = {}) }
    @Test fun libraryAurora() = render("AURORA") { LibraryScreen(onNavigateToOutput = {}) }
    @Test fun libraryLight() = render("LIGHT") { LibraryScreen(onNavigateToOutput = {}) }
    @Test fun progressAurora() = progress("AURORA")
    @Test fun progressLight() = progress("LIGHT")

    private fun progress(theme: String) = render(theme) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            GenerationProgressPanel(GenerationJob(id = "render-job", providerId = "render_fixture",
                modelId = "example-video", modality = Modality.VIDEO, prompt = "Datos de ejemplo",
                status = GenerationStatus.QUEUED, progress = .12f, startedAt = System.currentTimeMillis()))
        }
    }
}
