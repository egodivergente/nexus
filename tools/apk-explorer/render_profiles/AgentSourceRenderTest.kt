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
import com.vidadomu.nexus.ui.create.*
import com.vidadomu.nexus.assistant.*
import com.vidadomu.nexus.core.security.SecureStorage
import com.vidadomu.nexus.provider.atlas.AtlasCloudProvider
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.core.app.ActivityOptionsCompat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.stubbing.Answer

/** Calls production composables; replaces data services only. No Application.onCreate. */
class AgentSourceRenderTest {
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
        val storage = mock(SecureStorage::class.java)
        val atlas = mock(AtlasCloudProvider::class.java)
        runBlocking { `when`(atlas.fetchBalance()).thenReturn(Result.failure(IllegalStateException("Offline render"))) }
        `when`(app.secureStorage).thenReturn(storage)
        `when`(app.atlasProvider).thenReturn(atlas)
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
            CompositionLocalProvider(LocalContext provides fixtureContext, LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                override val activityResultRegistry = object : ActivityResultRegistry() {
                    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {}
                }
            }) {
                NexusTheme { content() }
            }
        }
    }

    private fun canvas(theme: String, mode: String) = render(theme) {
        val vm = remember {
            mock(CreateViewModel::class.java).also { model ->
                val comparison = mock(ComparisonController::class.java)
                `when`(comparison.state).thenReturn(MutableStateFlow(ComparisonState()))
                `when`(model.comparison).thenReturn(comparison)
                `when`(model.uiState).thenReturn(MutableStateFlow(CreateUiState(
                    isLoadingSchema = mode == "Loading",
                    errorMessage = if (mode == "Error") "Error de ejemplo: no se pudo cargar el modelo" else null
                )))
            }
        }
        CreateScreen(onNavigateToOutput = {}, onBrowseModels = {}, viewModel = vm)
    }

    @Test fun agentEmptyAurora() = agent("AURORA", "Empty")
    @Test fun agentEmptyLight() = agent("LIGHT", "Empty")
    @Test fun agentConversationAurora() = agent("AURORA", "Conversation")
    @Test fun agentConversationLight() = agent("LIGHT", "Conversation")
    @Test fun agentThinkingAurora() = agent("AURORA", "Thinking")
    @Test fun agentErrorAurora() = agent("AURORA", "Error")
    @Test fun canvasEmptyAurora() = canvas("AURORA", "Empty")
    @Test fun canvasEmptyLight() = canvas("LIGHT", "Empty")
    @Test fun canvasLoadingAurora() = canvas("AURORA", "Loading")
    @Test fun canvasErrorAurora() = canvas("AURORA", "Error")

    private fun agent(theme: String, mode: String) = render(theme) {
        val vm = remember {
            mock(AssistantViewModel::class.java).also { model ->
                val items = when (mode) {
                    "Empty" -> emptyList()
                    "Error" -> listOf(ChatItem.Notice("error", "No se pudo completar la solicitud de ejemplo.", true))
                    "Thinking" -> listOf(ChatItem.User("user", "Prepara una escena de ejemplo", emptyList()))
                    else -> listOf(ChatItem.User("user", "Prepara una escena de ejemplo", emptyList()),
                        ChatItem.Assistant("assistant", "Podemos comenzar con un plano general al amanecer.", emptyList(), "end_turn"))
                }
                `when`(model.ui).thenReturn(MutableStateFlow(AssistantUiState(items = items, thinking = mode == "Thinking")))
            }
        }
        AssistantScreen(onOpenInCanvas = {}, onNavigateToOutput = {}, vm = vm)
    }
}
