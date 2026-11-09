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
import com.vidadomu.nexus.provider.runway.RunwayProvider
import com.vidadomu.nexus.provider.registry.ProviderRegistry

import com.vidadomu.nexus.core.currency.CurrencyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import com.vidadomu.nexus.agent.core.assets.storyCommand

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
        com.vidadomu.nexus.provider.atlas.AtlasPriceQuote.configure { secureStorage.getAtlasApiKey() }
        // Ventana de seguimiento de trabajos: ajuste del usuario (Ajustes → Límites), 30 min por defecto, 0 = sin límite.
        com.vidadomu.nexus.core.model.TrackingPolicy.windowMinutes = getSharedPreferences("assistant_config_v1", MODE_PRIVATE).getInt("lim_tracking_min", com.vidadomu.nexus.core.model.TrackingPolicy.DEFAULT_MINUTES)
        database = NexusDatabase(this)

        val okHttpClient = OkHttpClientFactory.create(secureStorage,this)
        currencyManager = CurrencyManager(okHttpClient, secureStorage)

        atlasProvider = AtlasCloudProvider(okHttpClient, secureStorage)
        crunProvider = CrunProvider(this, okHttpClient, secureStorage)
        runwayProvider = RunwayProvider(okHttpClient, secureStorage)
        val mockProvider = MockNexusProvider()

        providerRegistry = ProviderRegistry(
            atlasProvider,
            crunProvider,
            runwayProvider,
            mockProvider,
            secureStorage,
            com.vidadomu.nexus.provider.fal.FalProvider(okHttpClient,
                { secureStorage.getProviderApiKey("fal") }, { atlasProvider.fetchLiveCatalog().getOrThrow() })
        )
        // Los proveedores API del usuario se registran ANTES de crear el catálogo para que entren en la primera carga.
        customMediaStore = com.vidadomu.nexus.provider.custom.CustomMediaStore(this, okHttpClient)
        customMediaStore.ensureBytePlus()
        providerRegistry.setCustomProviders(customMediaStore.providers())
        val catalogCache = com.vidadomu.nexus.data.repository.ModelCatalogCache(this)
        com.vidadomu.nexus.ui.models.CatalogPlacements.init(this)
        runCatching { com.vidadomu.nexus.data.repository.CatalogNameMigration.run(this) }
        modelCatalogRepository = ModelCatalogRepository(providerRegistry, currencyManager, catalogCache, getSharedPreferences("catalog_customization_v1", 0))
        generationRepository = GenerationRepository(this, database, providerRegistry)
        settingsRepository = SettingsRepository(secureStorage, providerRegistry, currencyManager)
        ready.complete(Unit)
        com.vidadomu.nexus.assistant.BytePlusConsumption.get(this).schedule()
        // The agent runtime (skills, asset index, registries) took ~1 s to build on the main thread when the chat
        // ViewModel first asked for it. Building it here in the background lets the first frame draw meanwhile;
        // AssistantRuntime.get is synchronized, so the ViewModel simply reuses the instance.
        CoroutineScope(Dispatchers.Default).launch { runCatching { com.vidadomu.nexus.assistant.AssistantRuntime.get(this@NexusApplication) } }
        CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
            val index = com.vidadomu.nexus.assistant.AssistantRuntime.get(this@NexusApplication).assets
            index.snapshot().story.tasks.filter { it.status == "running" && it.providerJobId == null }
                .map { it.projectId }.distinct().forEach { project ->
                    index.storyCommand(project, kotlinx.serialization.json.buildJsonObject {
                        put("action", kotlinx.serialization.json.JsonPrimitive("recover_tasks"))
                        put("operation_id", kotlinx.serialization.json.JsonPrimitive("resume:${java.util.UUID.randomUUID()}"))
                    }, "system:recovery")
                }
            index.changes.map { snapshot ->
                snapshot.story.autonomy.filter { it.enabled }.mapNotNull { autonomy ->
                    val pending = snapshot.story.tasks.filter { it.projectId == autonomy.projectId &&
                        it.status == "queued" && (autonomy.sceneId == null || it.sceneId == autonomy.sceneId) }
                    if (pending.isEmpty()) null else autonomy.projectId to pending.map { it.id }
                }
            }.distinctUntilChanged().collect { projects ->
                projects.forEach { (project, _) -> com.vidadomu.nexus.assistant.story.StoryPreparationWorker.enqueue(this@NexusApplication, project) }
            }
        }
        // Remote jobs already have a durable polling worker. Index their verified results independently
        // of which screen is open, also after process recreation. This observer never submits a job.
        CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
            generationRepository.allJobs.map { jobs ->
                jobs.filter { it.parameters.containsKey("__story_context") }
                    .map { Triple(it.id, it.status, it.outputs.map { output -> output.id }) }
            }.distinctUntilChanged().collect { changed ->
                if (changed.isNotEmpty()) runCatching {
                    com.vidadomu.nexus.assistant.AssistantRuntime.get(this@NexusApplication).importHistory()
                }.onFailure { android.util.Log.w("NexusStory", "Resultado pendiente de indexar: ${it.javaClass.simpleName}") }
            }
        }

        // Asynchronously check and refresh currency rate on startup
        CoroutineScope(Dispatchers.IO).launch {
            try {
                currencyManager.refreshRate()
            } catch (_: Exception) {}
        }

        // Una sola vez: los límites de la Lanzadera (modelos, generaciones por modelo, envíos simultáneos) vuelven a los
        // valores sin tope. Solo borra esos tres ajustes guardados; el resto de la configuración no se toca.
        runCatching {
            val flags = getSharedPreferences("migrations", MODE_PRIVATE)
            if (!flags.getBoolean("limits_unlimited_v1", false)) {
                val rules = com.vidadomu.nexus.settings.RuntimePreferences.get(this)
                listOf("generation.modelCount", "generation.perModelCount", "generation.parallel").forEach { rules.set(it, null) }
                flags.edit().putBoolean("limits_unlimited_v1", true).apply()
            }
        }

        // Guarda en el móvil los resultados que solo existen como URL caducable del proveedor.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if(!BuildConfig.OFFLINE_QA) {
                    kotlinx.coroutines.delay(20_000)
                    com.vidadomu.nexus.assistant.AssetLocalizer.run(this@NexusApplication, com.vidadomu.nexus.assistant.AssistantRuntime.get(this@NexusApplication))
                }
            } catch (e: Exception) {
                android.util.Log.e("NexusApplication", "Localizar resultados: ${e.javaClass.simpleName}")
            }
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
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
            com.vidadomu.nexus.assistant.WhisperCacheLifecycle.trim?.invoke()
    }

}
