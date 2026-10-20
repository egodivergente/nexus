package com.vidadomu.nexus

import android.app.Application
import com.vidadomu.nexus.core.database.NexusDatabase
import com.vidadomu.nexus.core.network.OkHttpClientFactory
import com.vidadomu.nexus.core.security.SecureStorage
import com.vidadomu.nexus.data.repository.GenerationRepository
import com.vidadomu.nexus.data.repository.ModelCatalogRepository
import com.vidadomu.nexus.data.repository.SettingsRepository
import com.vidadomu.nexus.provider.atlas.AtlasCloudProvider
import com.vidadomu.nexus.provider.mock.MockNexusProvider
import com.vidadomu.nexus.provider.crun.CrunProvider
import com.vidadomu.nexus.provider.flow.FlowBridgeProvider
import com.vidadomu.nexus.provider.runway.RunwayProvider
import com.vidadomu.nexus.provider.registry.ProviderRegistry

import com.vidadomu.nexus.core.currency.CurrencyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class NexusApplication : Application() {

    // The chat is an application session, not the lifetime of a particular screen/Activity.
    private val assistantOwner = object : androidx.lifecycle.ViewModelStoreOwner {
        override val viewModelStore = androidx.lifecycle.ViewModelStore()
    }
    fun assistantViewModel(): com.vidadomu.nexus.assistant.AssistantViewModel =
        androidx.lifecycle.ViewModelProvider(assistantOwner,
            androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(this))[
            com.vidadomu.nexus.assistant.AssistantViewModel::class.java]


    /**
     * Se completa al terminar la inicialización síncrona de onCreate. Los ContentProvider (puente
     * NexusAgentProvider) pueden recibir llamadas antes de que Application.onCreate acabe en un
     * arranque en frío: deben esperar aquí en lugar de tocar los lateinit.
     */
    val ready: kotlinx.coroutines.CompletableDeferred<Unit> = kotlinx.coroutines.CompletableDeferred()

    lateinit var secureStorage: SecureStorage
        private set
    lateinit var database: NexusDatabase
        private set
    lateinit var currencyManager: CurrencyManager
        private set
    lateinit var providerRegistry: ProviderRegistry
        private set
    lateinit var modelCatalogRepository: ModelCatalogRepository
        private set
    lateinit var generationRepository: GenerationRepository
        private set
    lateinit var atlasProvider: AtlasCloudProvider
        private set
    lateinit var crunProvider: CrunProvider
        private set
    lateinit var runwayProvider: RunwayProvider
        private set
    lateinit var flowBridgeProvider: FlowBridgeProvider
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    /** Proveedores multimedia añadidos por el usuario (Ajustes → Generadores). */
    lateinit var customMediaStore: com.vidadomu.nexus.provider.custom.CustomMediaStore
        private set

    override fun onCreate() {
        super.onCreate()
        android.os.Handler(android.os.Looper.getMainLooper()).post {com.vidadomu.nexus.sync.NexusDriveSync.get(this).initialize()}
        runCatching {
            assets.open("atlas_editorial_evidence.json").bufferedReader().use {
                com.vidadomu.nexus.ui.models.PublishedModelNotes.seed(it.readText())
            }
        }
        runCatching {
            assets.open("atlas_published_prices.json").bufferedReader().use {
                com.vidadomu.nexus.provider.atlas.AtlasPublishedPrice.seedSnapshot(it.readText())
            }
        }

        secureStorage = SecureStorage(this)
        // Ventana de seguimiento de trabajos: ajuste del usuario (Ajustes → Límites), 30 min por defecto, 0 = sin límite.
        com.vidadomu.nexus.core.model.TrackingPolicy.windowMinutes = getSharedPreferences("assistant_config_v1", MODE_PRIVATE).getInt("lim_tracking_min", com.vidadomu.nexus.core.model.TrackingPolicy.DEFAULT_MINUTES)
        database = NexusDatabase(this)

        val okHttpClient = OkHttpClientFactory.create(secureStorage)
        currencyManager = CurrencyManager(okHttpClient, secureStorage)

        atlasProvider = AtlasCloudProvider(okHttpClient, secureStorage)
        crunProvider = CrunProvider(this, okHttpClient, secureStorage)
        runwayProvider = RunwayProvider(okHttpClient, secureStorage)
        flowBridgeProvider = FlowBridgeProvider(okHttpClient, secureStorage, this)
        val mockProvider = MockNexusProvider()

        providerRegistry = ProviderRegistry(
            atlasProvider,
            crunProvider,
            runwayProvider,
            flowBridgeProvider,
            mockProvider,
            secureStorage
        )
        // Los proveedores API del usuario se registran ANTES de crear el catálogo para que entren en la primera carga.
        customMediaStore = com.vidadomu.nexus.provider.custom.CustomMediaStore(this, okHttpClient)
        customMediaStore.ensureBytePlus()
        providerRegistry.setCustomProviders(customMediaStore.providers())
        val catalogCache = com.vidadomu.nexus.data.repository.ModelCatalogCache(this)
        modelCatalogRepository = ModelCatalogRepository(providerRegistry, currencyManager, catalogCache, getSharedPreferences("catalog_customization_v1", 0))
        generationRepository = GenerationRepository(this, database, providerRegistry)
        settingsRepository = SettingsRepository(secureStorage, providerRegistry, currencyManager)
        ready.complete(Unit)
        com.vidadomu.nexus.assistant.BytePlusConsumption.get(this).schedule()

        // Asynchronously check and refresh currency rate on startup
        CoroutineScope(Dispatchers.IO).launch {
            try {
                currencyManager.refreshRate()
            } catch (_: Exception) {}
        }

        // Asynchronously classify any pending or existing Nexus creations into Alexia
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if(!BuildConfig.OFFLINE_QA) {
                    database.loading.first { !it }
                    if (database.loadError.value == null) com.vidadomu.nexus.core.alexia.AlexiaBridge.classifyPendingGenerations(this@NexusApplication, generationRepository)
                }
            } catch (e: Exception) {
                android.util.Log.e("NexusApplication", "History classification: ${e.javaClass.simpleName}")
            }
        }
    }
}
