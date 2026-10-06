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
import com.vidadomu.nexus.provider.registry.ProviderRegistry

import com.vidadomu.nexus.core.currency.CurrencyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexusApplication : Application() {

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
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()

        secureStorage = SecureStorage(this)
        database = NexusDatabase(this)

        val okHttpClient = OkHttpClientFactory.create(secureStorage)
        currencyManager = CurrencyManager(okHttpClient, secureStorage)

        atlasProvider = AtlasCloudProvider(okHttpClient, secureStorage)
        val mockProvider = MockNexusProvider()

        providerRegistry = ProviderRegistry(atlasProvider, mockProvider, secureStorage)
        val catalogCache = com.vidadomu.nexus.data.repository.ModelCatalogCache(this)
        modelCatalogRepository = ModelCatalogRepository(providerRegistry, currencyManager, catalogCache)
        generationRepository = GenerationRepository(this, database, providerRegistry)
        settingsRepository = SettingsRepository(secureStorage, providerRegistry, currencyManager)

        // Asynchronously check and refresh currency rate on startup
        CoroutineScope(Dispatchers.IO).launch {
            try {
                currencyManager.refreshRate()
            } catch (_: Exception) {}
        }

        // Asynchronously classify any pending or existing Nexus creations into Alexia
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.vidadomu.nexus.core.alexia.AlexiaBridge.classifyPendingGenerations(this@NexusApplication, generationRepository)
            } catch (e: Exception) {
                android.util.Log.e("NexusApplication", "Error clasificando historial en Alexia: ${e.message}", e)
            }
        }
    }
}
