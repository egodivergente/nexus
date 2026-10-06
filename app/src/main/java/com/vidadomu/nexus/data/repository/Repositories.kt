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

import android.util.Log

class ModelCatalogRepository(
    private val providerRegistry: ProviderRegistry,
    val currencyManager: CurrencyManager,
    private val cache: ModelCatalogCache? = null
) {
    private val _models = MutableStateFlow<List<NexusModel>>(emptyList())
    val models: StateFlow<List<NexusModel>> = _models.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        refreshModels(forceNetwork = false)
    }

    fun refreshModels(forceNetwork: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            _isLoading.value = true
            _errorMessage.value = null

            // 1. Try loading from cache first if not forcing network
            if (!forceNetwork && cache != null) {
                val cached = cache.getCachedModels()
                if (!cached.isNullOrEmpty()) {
                    _models.value = cached
                    if (cache.isCacheValid()) {
                        _isLoading.value = false
                        return@launch
                    }
                }
            }

            // 2. Fetch fresh models from active provider
            try {
                val provider = providerRegistry.getActiveProvider()
                val list = provider.getModels()
                if (list.isNotEmpty()) {
                    _models.value = list
                    cache?.saveModels(list)
                } else if (_models.value.isEmpty()) {
                    val cached = cache?.getCachedModels()
                    if (!cached.isNullOrEmpty()) {
                        _models.value = cached
                    }
                }
            } catch (e: Exception) {
                Log.e("ModelCatalogRepo", "Error refreshing models: ${e.message}", e)
                _errorMessage.value = e.message
                if (_models.value.isEmpty()) {
                    val cached = cache?.getCachedModels()
                    if (!cached.isNullOrEmpty()) {
                        _models.value = cached
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getModelById(id: String): NexusModel? {
        return _models.value.find { it.id == id }
    }
}

class GenerationRepository(
    private val context: android.content.Context? = null,
    private val database: NexusDatabase,
    private val providerRegistry: ProviderRegistry
) {
    val allJobs: StateFlow<List<GenerationJob>> = database.jobsFlow

    suspend fun submitGeneration(request: GenerationRequest): Result<GenerationJob> {
        val provider = providerRegistry.getActiveProvider()
        val result = provider.submitGeneration(request)
        if (result.isSuccess) {
            val job = result.getOrThrow()
            database.insertOrUpdateJob(job)
        }
        return result
    }

    suspend fun pollJob(jobId: String): Result<GenerationJob> {
        val currentJob = database.getJobById(jobId)
            ?: return Result.failure(ProviderError("Job $jobId not found in local database"))

        if (currentJob.status == GenerationStatus.COMPLETED || currentJob.status == GenerationStatus.FAILED) {
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
                    Log.e("GenerationRepository", "Error clasificando en Alexia: ${e.message}", e)
                }
            }
            return Result.success(updated)
        }
        return pollResult
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
