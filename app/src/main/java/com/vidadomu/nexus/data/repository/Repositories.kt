package com.vidadomu.nexus.data.repository

import com.vidadomu.nexus.core.database.NexusDatabase
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.core.security.SecureStorage
import com.vidadomu.nexus.provider.registry.ProviderRegistry
import com.vidadomu.nexus.core.currency.CurrencyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first

import android.util.Log

class ModelCatalogRepository(
    private val providerRegistry: ProviderRegistry,
    val currencyManager: CurrencyManager,
    private val cache: ModelCatalogCache? = null,
    private val catalogPreferences: android.content.SharedPreferences? = null
) {
    private val _models = MutableStateFlow<List<NexusModel>>(emptyList())
    val models: StateFlow<List<NexusModel>> = _models.asStateFlow()

    private val _allModels = MutableStateFlow<List<NexusModel>>(emptyList())
    private val _hiddenEntries = MutableStateFlow<Map<String,String>>(
        catalogPreferences?.getStringSet("hidden_keys", emptySet()).orEmpty().associateWith {
            catalogPreferences?.getString("label:$it", it) ?: it
        })
    val hiddenEntries: StateFlow<Map<String,String>> = _hiddenEntries.asStateFlow()

    @Synchronized
    fun setCatalogHidden(key: String, label: String, hidden: Boolean) {
        val next = if(hidden) _hiddenEntries.value + (key to label) else _hiddenEntries.value - key
        catalogPreferences?.edit()?.putStringSet("hidden_keys", next.keys)?.apply {
            if(hidden) putString("label:$key", label) else remove("label:$key")
        }?.apply()
        _hiddenEntries.value = next
        _models.value = _allModels.value.filter { isCatalogModelVisible(it, next.keys) }
    }

    @Synchronized
    private fun publishCatalog(models: List<NexusModel>) {
        _allModels.value = models
        _models.value = models.filter { isCatalogModelVisible(it, _hiddenEntries.value.keys) }
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val refreshLock = kotlinx.coroutines.sync.Mutex()

    init {
        refreshModels(forceNetwork = false)
    }
    fun refreshModels(forceNetwork: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            if (!refreshLock.tryLock()) return@launch
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val providers = providerRegistry.getCatalogProviders()
                val activeIds = providers.map { it.id }.toSet()
                val previous = (_allModels.value.ifEmpty { cache?.getCachedModels().orEmpty() })
                    .filter { it.providerId in activeIds }
                publishCatalog(previous)
                val errors = mutableListOf<String>()
                val fresh = providers.flatMap { provider ->
                    try {
                        val fetched=provider.getModels().also { require(it.isNotEmpty()) { "Catálogo vacío" } }
                        provider.catalogNotice?.let(errors::add)
                        val prior=previous.filter { it.providerId==provider.id }
                        if(provider.catalogNotice!=null && prior.size>fetched.size) prior else fetched
                    } catch (e: Exception) {
                        errors += "${provider.displayName}: no se pudo actualizar; se conserva el catálogo anterior"
                        previous.filter { it.providerId == provider.id }
                    }
                }.distinctBy { "${it.providerId}:${it.id}" }
                publishCatalog(fresh)
                if (fresh.isNotEmpty()) cache?.saveModels(fresh)
                _errorMessage.value = errors.takeIf { it.isNotEmpty() }?.joinToString(" · ")
            } finally {
                _isLoading.value = false
                refreshLock.unlock()
            }
        }
    }

    fun getModelById(id: String): NexusModel? {
        return _allModels.value.find { it.id == id }
    }
}

/** Claves locales que se guardan con el trabajo pero nunca viajan al proveedor. */
object LocalJobMetadata {
    fun allowed(key: String): Boolean = key.startsWith("__comparison_") || key.startsWith("__assistant_")
}

class GenerationRepository(
    private val context: android.content.Context? = null,
    private val database: NexusDatabase,
    private val providerRegistry: ProviderRegistry
) {
    val allJobs: StateFlow<List<GenerationJob>> = database.jobsFlow
    val historyLoading = database.loading
    val historyError = database.loadError
    private val comparisonRecovery = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).async {
        database.loading.first { !it }
        if(database.loadError.value == null) {
            database.jobsFlow.value.filter { it.parameters.containsKey("__comparison_id") }.forEach { job ->
                val recovered = job.recoverInterruptedComparison()
                if(recovered != job) database.insertOrUpdateJob(recovered)
                else if(context != null && !com.vidadomu.nexus.BuildConfig.OFFLINE_QA && !job.providerJobId.isNullOrBlank() &&
                    job.status in setOf(GenerationStatus.QUEUED, GenerationStatus.GENERATING, GenerationStatus.PROCESSING, GenerationStatus.RENDERING)) {
                    com.vidadomu.nexus.core.work.GenerationPollingWorker.enqueue(context, job.id)
                }
            }
        }
    }
    suspend fun reloadHistory() = database.loadHistory()

    suspend fun submitGeneration(request: GenerationRequest, localMetadata: Map<String,String> = emptyMap()): Result<GenerationJob> {
        comparisonRecovery.await()
        require(localMetadata.keys.all(LocalJobMetadata::allowed)) { "Metadatos locales no admitidos." }
        val provider = providerRegistry.getProviderById(request.providerId)
            ?: return Result.failure(ProviderError("No existe un adaptador ejecutable para ${request.providerId}."))
        if(com.vidadomu.nexus.BuildConfig.OFFLINE_QA) return Result.failure(ProviderError("Laboratorio local: envío bloqueado."))
        val pending=GenerationJob(id=request.id,providerId=request.providerId,modelId=request.modelId,modality=request.modality,
            prompt=request.prompt,negativePrompt=request.negativePrompt,parameters=request.parameters + localMetadata,
            inputMediaUri=request.mediaInputs.firstOrNull()?.uriString,status=GenerationStatus.QUEUED)
        database.insertOrUpdateJob(pending)
        val result=try {provider.submitGeneration(request)} catch(e:kotlinx.coroutines.CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                database.insertOrUpdateJob(pending.copy(status=GenerationStatus.FAILED,error="Envío interrumpido. Comprueba el proveedor antes de repetir.",parameters=pending.parameters+("__submission_uncertain" to "true")))
            };throw e
        } catch(e:Exception) {Result.failure(e)}
        if (result.isSuccess) database.insertOrUpdateJob(result.getOrThrow().let { it.copy(parameters = it.parameters + localMetadata) })
        else {
            val error=result.exceptionOrNull()
            val definite=(error as? ProviderError)?.httpStatusCode?.let {it in setOf(400, 401, 402, 403, 404, 422)} == true
            database.insertOrUpdateJob(pending.copy(status=GenerationStatus.FAILED,
                error=if(definite) (error as? ProviderError)?.humanReadableMessage else "No se pudo confirmar el envío. Comprueba el proveedor antes de repetir; podría haberse iniciado.",
                parameters=pending.parameters+("__submission_uncertain" to (!definite).toString())))
        }
        return result
    }

    suspend fun pollJob(jobId: String): Result<GenerationJob> {
        val currentJob = database.getJobById(jobId)
            ?: return Result.failure(ProviderError("Job $jobId not found in local database"))

        if (currentJob.status in setOf(GenerationStatus.COMPLETED, GenerationStatus.FAILED, GenerationStatus.CANCELLED)) {
            return Result.success(currentJob)
        }

        val provider = providerRegistry.getProviderById(currentJob.providerId)
            ?: providerRegistry.getActiveProvider()

        val pollResult = provider.pollJobStatus(currentJob)
        if (pollResult.isSuccess) {
            val updated = pollResult.getOrThrow()
            database.insertOrUpdateJob(updated)
            if (updated.status == GenerationStatus.COMPLETED && context != null) {
                try {
                    com.vidadomu.nexus.core.alexia.AlexiaBridge.classifyJob(context, updated, this)
                } catch (e: Exception) {
                    Log.e("GenerationRepository", "Alexia classification: ${e.javaClass.simpleName}")
                }
            }
            return Result.success(updated)
        }
        return pollResult
    }

    suspend fun refreshConfirmedCost(jobId:String):Result<GenerationJob> {
        val current=database.getJobById(jobId) ?: return Result.failure(ProviderError("Resultado no encontrado"))
        val provider=providerRegistry.getProviderById(current.providerId)
            ?: return Result.failure(ProviderError("Proveedor no disponible"))
        return provider.pollJobStatus(current).map { remote ->
            val updated=current.copy(actualCostUsd=remote.actualCostUsd ?: current.actualCostUsd,
                outputs=if(remote.outputs.isEmpty()) current.outputs else remote.outputs.mapIndexed { index,output ->
                    output.copy(localUri=current.outputs.getOrNull(index)?.localUri)
                },
                parameters=current.parameters + remote.parameters.filterKeys { it.startsWith("__actual_") })
            database.insertOrUpdateJob(updated)
            updated
        }
    }

    suspend fun getJob(jobId: String): GenerationJob? {
        return database.getJobById(jobId)
    }

    suspend fun updateJob(job: GenerationJob) {
        database.insertOrUpdateJob(job)
    }

    suspend fun deleteJob(jobId: String) {
        database.deleteJob(jobId)
    }
}

class SettingsRepository(
    private val secureStorage: SecureStorage,
    private val providerRegistry: ProviderRegistry,
    val currencyManager: CurrencyManager
) {
    fun getAtlasApiKey(): String = secureStorage.getAtlasApiKey()
    fun getMaskedAtlasApiKey(): String = secureStorage.getMaskedAtlasApiKey()
    fun saveAtlasApiKey(key: String) = secureStorage.saveAtlasApiKey(key)
    fun clearAtlasApiKey() = secureStorage.clearAtlasApiKey()

    fun isMockModeEnabled(): Boolean = secureStorage.isMockModeEnabled()
    fun setMockMode(enabled: Boolean) = providerRegistry.setMockMode(enabled)

    suspend fun testConnection(apiKey: String): Result<Boolean> {
        val provider = providerRegistry.getActiveProvider()
        return provider.testConnection(apiKey)
    }
}
