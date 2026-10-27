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
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

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
    private var hiddenRoutes = _hiddenEntries.value.keys.associateWith {
        catalogPreferences?.getStringSet("routes:$it", emptySet()).orEmpty().toSet()
    }

    /** Serial background lane: catalogue classification never runs on the main thread (hiding a model froze the dialog). */
    private val catalogScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "nexus-catalog").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }.asCoroutineDispatcher())

    /**
     * Called from UI callbacks. Updates the hidden set and the visible list immediately (a cheap filter), persists the
     * choice, and recomputes the captured routes in the background. Never blocks the caller.
     */
    fun setCatalogHidden(key: String, label: String, hidden: Boolean) {
        // Lock-free on the caller's thread: a background publish holding the monitor must never stall a tap.
        val next = _hiddenEntries.updateAndGet { if (hidden) it + (key to label) else it - key }
        if (hidden) _models.update { list -> list.filterNot { key in catalogVisibilityKeys(it).keys } }
        catalogScope.launch {
            catalogPreferences?.edit()?.apply {
                putStringSet("hidden_keys", next.keys)
                if (hidden) putString("label:$key", label) else { remove("label:$key"); remove("routes:$key") }
            }?.commit()
            if (!hidden) synchronized(this@ModelCatalogRepository) { hiddenRoutes = hiddenRoutes - key }
            publishCatalog(_allModels.value)
        }
    }

    @Synchronized
    private fun publishCatalog(models: List<NexusModel>) {
        val hidden = _hiddenEntries.value.keys
        val previousRoutes = hiddenRoutes.filterKeys { it in hidden }
        hiddenRoutes = captureHiddenRoutes(_allModels.value + models, hidden, previousRoutes)
        if (hiddenRoutes != previousRoutes) catalogPreferences?.edit()?.apply {
            hiddenRoutes.forEach { (key, routes) -> putStringSet("routes:$key", routes) }
        }?.apply()
        _allModels.value = models
        _models.value = visibleCatalog(models, hidden, hiddenRoutes)
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
                if(forceNetwork) {
                    com.vidadomu.nexus.provider.atlas.NativeModelSchema.invalidate()
                    com.vidadomu.nexus.provider.atlas.AtlasPriceQuote.invalidate()
                    com.vidadomu.nexus.provider.atlas.AtlasPublishedPrice.invalidate()
                }
                val providers = providerRegistry.getCatalogProviders()
                val activeIds = providers.map { it.id }.toSet()
                // Configured models are local data: expose them before waiting for remote catalogs.
                val configured = providers.filterIsInstance<com.vidadomu.nexus.provider.custom.ConfigurableMediaProvider>()
                val configuredIds = configured.map {it.id}.toSet()
                val local = configured.flatMap {it.getModels()}
                val previous = (_allModels.value.ifEmpty { cache?.getCachedModels().orEmpty() })
                    .filter { it.providerId in activeIds && it.providerId !in configuredIds } + local
                publishCatalog(previous)
                val errors = mutableListOf<String>()
                val fresh = providers.flatMap { provider ->
                    try {
                        val catalog=provider.getModels()
                        // fal.ai is complementary by design: with no key, or when Atlas already offers its model, it has
                        // nothing to add. An empty list there is the expected answer, not a failed refresh.
                        if(catalog.isEmpty() && provider is com.vidadomu.nexus.provider.fal.FalProvider) return@flatMap emptyList()
                        require(catalog.isNotEmpty()) { "Catálogo vacío" }
                        val fetched=if(forceNetwork && provider is com.vidadomu.nexus.provider.atlas.AtlasCloudProvider && provider.catalogNotice==null)
                            provider.synchronizeContracts(catalog) { completed, total -> _errorMessage.value = "Atlas: verificando contratos $completed/$total" } else catalog
                        provider.catalogNotice?.let(errors::add)
                        val prior=previous.filter { it.providerId==provider.id }
                        if(provider.catalogNotice!=null && prior.size>fetched.size) prior else fetched
                    } catch (e: Exception) {
                        // Name the reason, so «sin actualizar» can be told apart: no network, expired key, empty answer…
                        val reason = (e as? com.vidadomu.nexus.core.model.ProviderError)?.humanReadableMessage ?: e.message
                        errors += "${provider.displayName}: no se pudo actualizar" +
                            (reason?.takeIf { it.isNotBlank() }?.let { " (${it.take(90)})" } ?: "") + "; uso el catálogo guardado"
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
    fun allowed(key: String): Boolean = key in setOf("__project_id", "__conversation_id") ||
        key.startsWith("__comparison_") || key.startsWith("__assistant_")

    fun validate(metadata: Map<String, String>) {
        require(metadata.keys.all(::allowed)) { "Metadatos locales no admitidos." }
    }
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
            database.jobsFlow.value.forEach { job ->
                val recovered = job.recoverInterruptedComparison().recoverInterruptedSubmission()
                if(recovered != job) database.insertOrUpdateJob(recovered)
                else if(context != null && !com.vidadomu.nexus.BuildConfig.OFFLINE_QA && !job.providerJobId.isNullOrBlank() &&
                    job.status in setOf(GenerationStatus.QUEUED, GenerationStatus.GENERATING, GenerationStatus.PROCESSING, GenerationStatus.RENDERING)) {
                    com.vidadomu.nexus.core.work.GenerationPollingWorker.enqueue(context, job.id)
                }
            }
        }
    }
    suspend fun reloadHistory() = database.loadHistory()

    private val submissionLocks = Array(64) { kotlinx.coroutines.sync.Mutex() }

    suspend fun submitGeneration(request: GenerationRequest, localMetadata: Map<String,String> = emptyMap()): Result<GenerationJob> =
        submissionLocks[(request.id.hashCode() and Int.MAX_VALUE) % submissionLocks.size].withLock {
            submitGenerationLocked(request, localMetadata)
        }

    private suspend fun submitGenerationLocked(request: GenerationRequest, localMetadata: Map<String,String>): Result<GenerationJob> {
        comparisonRecovery.await()
        try { LocalJobMetadata.validate(localMetadata) }
        catch (e: IllegalArgumentException) { return Result.failure(e) }
        database.getJobById(request.id)?.let { return SubmissionSafety.reuse(it, request) }
        val provider = providerRegistry.getProviderById(request.providerId)
            ?: return Result.failure(ProviderError("No existe un adaptador ejecutable para ${request.providerId}."))
        if(com.vidadomu.nexus.BuildConfig.OFFLINE_QA) return Result.failure(ProviderError("Laboratorio local: envío bloqueado."))
        val receiptMetadata = localMetadata + (SubmissionSafety.DIGEST_KEY to SubmissionSafety.digest(request))
        val pending=GenerationJob(id=request.id,providerId=request.providerId,modelId=request.modelId,modality=request.modality,
            prompt=request.prompt,negativePrompt=request.negativePrompt,parameters=request.parameters + receiptMetadata,
            inputMediaUri=request.mediaInputs.firstOrNull()?.uriString,status=GenerationStatus.QUEUED)
        database.insertOrUpdateJob(pending)
        return kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
            val result=try {provider.submitGeneration(request)} catch(e:Exception) {Result.failure(e)}
            if (result.isSuccess) {
                val submitted = result.getOrThrow().let { it.copy(parameters = it.parameters + receiptMetadata) }
                database.insertOrUpdateJob(submitted)
                if (context != null && submitted.status != GenerationStatus.COMPLETED) {
                    com.vidadomu.nexus.core.work.GenerationPollingWorker.enqueue(context, submitted.id)
                }
            } else {
                val error=result.exceptionOrNull()
                val definite = SubmissionSafety.definitelyRejected(error)
                database.insertOrUpdateJob(pending.copy(status=GenerationStatus.FAILED,
                    error=if(definite) (error as? ProviderError)?.humanReadableMessage ?: error?.message else "No se pudo confirmar el envío. Comprueba el proveedor antes de repetir; podría haberse iniciado.",
                    parameters=pending.parameters+("__submission_uncertain" to (!definite).toString())))
            }
            result
        }
    }

    /** One logical row and one lock across every provider attempt; no public retry path bypasses the receipt. */
    suspend fun submitRoutedGeneration(logical: GenerationRequest,
        routes: List<com.vidadomu.nexus.routing.GenerationRoute>, ceiling: java.math.BigDecimal,
        diagnostics: List<String>, localMetadata: Map<String,String> = emptyMap()): Result<GenerationJob> =
        submissionLocks[(logical.id.hashCode() and Int.MAX_VALUE) % submissionLocks.size].withLock {
            comparisonRecovery.await()
            LocalJobMetadata.validate(localMetadata)
            if(com.vidadomu.nexus.BuildConfig.OFFLINE_QA) return@withLock Result.failure(ProviderError("Laboratorio local: envío bloqueado."))
            val digest=SubmissionSafety.digest(logical)
            database.getJobById(logical.id)?.let { existing ->
                if(existing.parameters["__routing_logical_digest"]!=digest)
                    return@withLock Result.failure(ProviderError("El identificador ya pertenece a otro envío. No se ha repetido."))
                if(existing.parameters["__submission_uncertain"]=="true" || existing.status in setOf(GenerationStatus.FAILED,GenerationStatus.CANCELLED))
                    return@withLock Result.failure(ProviderError(existing.error ?: "Consulta este trabajo antes de preparar otro envío."))
                return@withLock Result.success(existing)
            }
            val ordered=com.vidadomu.nexus.routing.RoutingSubmissionPolicy.ordered(routes,ceiling,System.currentTimeMillis())
            if(ordered.isEmpty()) return@withLock Result.failure(ProviderError("Las cotizaciones han caducado o superan el importe aceptado. Revisa el coste."))
            val events=mutableListOf<kotlinx.serialization.json.JsonObject>()
            val metadata=localMetadata+mapOf("__routing_logical_digest" to digest,
                "__routing_original_provider" to logical.providerId,"__routing_original_model" to logical.modelId)
            var lastError:Throwable?=null
            var pending=GenerationJob(id=logical.id,providerId=logical.providerId,modelId=logical.modelId,modality=logical.modality,
                prompt=logical.prompt,negativePrompt=logical.negativePrompt,parameters=logical.parameters+metadata,
                inputMediaUri=logical.mediaInputs.firstOrNull()?.uriString,status=GenerationStatus.QUEUED)
            for(route in ordered) {
                if(System.currentTimeMillis()>=route.validUntil) continue
                val provider=providerRegistry.getProviderById(route.providerId) ?: continue
                // Upload/validation errors precede the billable POST and never trigger an untracked generation.
                val request=try { route.prepareRequest() }
                    catch(e:kotlinx.coroutines.CancellationException) {throw e}
                    catch(e:Exception) { lastError=e; continue }
                if(System.currentTimeMillis()>=route.validUntil) {
                    lastError=ProviderError("La cotización caducó durante la preparación. Revisa el importe antes de generar.")
                    continue
                }
                require(request.id==logical.id && request.providerId==route.providerId && request.modelId==route.modelId)
                val attempt=events.size+1
                fun event(outcome:String)=kotlinx.serialization.json.buildJsonObject {
                    put("attempt",kotlinx.serialization.json.JsonPrimitive(attempt))
                    put("provider",kotlinx.serialization.json.JsonPrimitive(route.providerId))
                    put("model",kotlinx.serialization.json.JsonPrimitive(route.modelId))
                    put("totalUsd",kotlinx.serialization.json.JsonPrimitive(route.totalUsd.toPlainString()))
                    put("outcome",kotlinx.serialization.json.JsonPrimitive(outcome))
                }
                events+=event("submitting")
                pending=pending.copy(providerId=request.providerId,modelId=request.modelId,status=GenerationStatus.QUEUED,error=null,parameters=request.parameters+metadata+
                    mapOf("__routing_attempts" to kotlinx.serialization.json.JsonArray(events).toString(),
                        "__submission_uncertain" to "true"),estimatedCostUsd=route.totalUsd.toDouble())
                database.insertOrUpdateJob(pending) // durable BEFORE POST, including process-death protection
                val accepted=kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable+kotlinx.coroutines.Dispatchers.IO) {
                    val result=try {provider.submitGeneration(request)} catch(e:Exception) {Result.failure(e)}
                    if(result.isSuccess) {
                        events[events.lastIndex]=event("accepted")
                        val received=result.getOrThrow()
                        val job=received.copy(id=logical.id,parameters=received.parameters+metadata+mapOf(
                            SubmissionSafety.DIGEST_KEY to SubmissionSafety.digest(request),
                            "__submission_uncertain" to "false", "__routing_attempts" to kotlinx.serialization.json.JsonArray(events).toString()),
                            estimatedCostUsd=route.totalUsd.toDouble(),technicalDetails=listOfNotNull(received.technicalDetails,
                                diagnostics.joinToString("\n")+"\nIntentos: "+kotlinx.serialization.json.JsonArray(events)).joinToString("\n"))
                        database.insertOrUpdateJob(job)
                        if(context!=null && job.status!=GenerationStatus.COMPLETED) com.vidadomu.nexus.core.work.GenerationPollingWorker.enqueue(context,job.id)
                        Result.success(job)
                    } else {
                        lastError=result.exceptionOrNull()
                        val definite=SubmissionSafety.definitelyRejected(lastError)
                        events[events.lastIndex]=event(if(definite) "rejected_before_acceptance" else "uncertain")
                        pending=pending.copy(status=GenerationStatus.FAILED,error=if(definite) lastError?.message else
                            "No se pudo confirmar el envío. Consulta este trabajo antes de repetir; podría haberse iniciado.",
                            parameters=pending.parameters+mapOf("__submission_uncertain" to (!definite).toString(),
                                "__routing_attempts" to kotlinx.serialization.json.JsonArray(events).toString()),
                            technicalDetails=diagnostics.joinToString("\n"))
                        database.insertOrUpdateJob(pending)
                        Result.failure(lastError ?: ProviderError("Envío no confirmado."))
                    }
                }
                if(accepted.isSuccess) return@withLock accepted
                // Reconcile the persisted server response. A remote id or ambiguity always stops fallback.
                val recorded=database.getJobById(logical.id) ?: return@withLock accepted
                if(!com.vidadomu.nexus.routing.RoutingSubmissionPolicy.canFallback(recorded,lastError)) return@withLock accepted
            }
            Result.failure(lastError ?: ProviderError("No queda un proveedor compatible dentro del importe aceptado."))
        }

    private val pollLocks = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    suspend fun pollJob(jobId: String, refreshFailed: Boolean = false): Result<GenerationJob> =
        pollLocks.getOrPut(jobId) { kotlinx.coroutines.sync.Mutex() }.withLock {
            pollJobLocked(jobId, refreshFailed)
        }

    private suspend fun pollJobLocked(jobId: String, refreshFailed: Boolean): Result<GenerationJob> {
        var currentJob = database.getJobById(jobId)
            ?: return Result.failure(ProviderError("Job $jobId not found in local database"))

        if (currentJob.status in setOf(GenerationStatus.COMPLETED, GenerationStatus.CANCELLED) ||
            (currentJob.status == GenerationStatus.FAILED && !refreshFailed)) {
            return Result.success(currentJob)
        }

        val persistedJob = currentJob
        if (refreshFailed && currentJob.parameters["__tracking_paused"] == "true") {
            currentJob = currentJob.copy(parameters = (currentJob.parameters - "__tracking_paused") + ("__tracking_started_at" to System.currentTimeMillis().toString()))
        }

        val provider = providerRegistry.getProviderById(currentJob.providerId)
            ?: return Result.failure(ProviderError("Proveedor no disponible para consultar este trabajo."))

        val pollResult = provider.pollJobStatus(currentJob)
        if (pollResult.isSuccess) {
            val remote = pollResult.getOrThrow()
            val normalized = if(currentJob.parameters["__tracking_poll_error"] == "true") remote.copy(
                error = remote.error.takeUnless { it == currentJob.error },
                parameters = remote.parameters - "__tracking_poll_error") else remote
            val updated = if(refreshFailed) normalized else normalized.pauseExpiredTracking()
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
        val problem = pollResult.exceptionOrNull()
        val detail = (problem as? ProviderError)?.humanReadableMessage ?: "No se pudo consultar el estado. Se reintentará sin volver a enviar la generación."
        database.insertOrUpdateJob(persistedJob.copy(error = detail,
            parameters = persistedJob.parameters + ("__tracking_poll_error" to "true")).pauseExpiredTracking())
        return pollResult
    }

    suspend fun refreshConfirmedCost(jobId:String):Result<GenerationJob> =
        pollLocks.getOrPut(jobId) { kotlinx.coroutines.sync.Mutex() }.withLock {
            val current=database.getJobById(jobId) ?: return@withLock Result.failure(ProviderError("Resultado no encontrado"))
            if (current.providerId == "google_flow" || current.providerJobId?.startsWith("queue_") == true) {
                return@withLock Result.failure(ProviderError("Flow utiliza créditos de suscripción y no expone consulta de importe final por tarea."))
            }
            if (current.status != GenerationStatus.COMPLETED) {
                return@withLock Result.failure(ProviderError("Solo se puede consultar el coste confirmado de generaciones completadas."))
            }
            val provider=providerRegistry.getProviderById(current.providerId)
                ?: return@withLock Result.failure(ProviderError("Proveedor no disponible"))
            provider.pollJobStatus(current).map { remote ->
                val updated=current.copy(actualCostUsd=remote.actualCostUsd ?: current.actualCostUsd,
                    outputs=if(remote.outputs.isEmpty()) current.outputs else remote.outputs.mapIndexed { index,output ->
                        output.copy(localUri=current.outputs.getOrNull(index)?.localUri)
                    },
                    parameters=current.parameters + remote.parameters.filterKeys { it.startsWith("__actual_") })
                database.insertOrUpdateJob(updated)
                updated
            }
        }

    fun supportsRemoteCancellation(providerId: String): Boolean =
        providerRegistry.getProviderById(providerId)?.supportsRemoteCancellation == true

    fun remoteCancellationSupportMessage(job: GenerationJob): String =
        GenerationCancellationPolicy.supportMessage(job, providerRegistry.getProviderById(job.providerId))

    suspend fun stopTrackingJob(jobId: String): Boolean =
        pollLocks.getOrPut(jobId) { kotlinx.coroutines.sync.Mutex() }.withLock {
            val current = database.getJobById(jobId) ?: return@withLock false
            val paused = GenerationCancellationPolicy.stopLocalTracking(current) ?: return@withLock false
            context?.let { ctx ->
                runCatching { androidx.work.WorkManager.getInstance(ctx).cancelUniqueWork("poll_$jobId") }
            }
            database.insertOrUpdateJob(paused)
            true
        }

    suspend fun cancelRemoteGeneration(jobId: String): Result<GenerationJob> =
        pollLocks.getOrPut(jobId) { kotlinx.coroutines.sync.Mutex() }.withLock {
            val current = database.getJobById(jobId)
                ?: return@withLock Result.failure(ProviderError("Trabajo no encontrado en la base de datos local."))
            val provider = providerRegistry.getProviderById(current.providerId)
            val result = GenerationCancellationPolicy.cancelRemote(current, provider)
            if (result.isSuccess) {
                context?.let { ctx ->
                    runCatching { androidx.work.WorkManager.getInstance(ctx).cancelUniqueWork("poll_$jobId") }
                }
                database.insertOrUpdateJob(result.getOrThrow())
            }
            result
        }

    suspend fun cancelJob(jobId: String): Boolean = stopTrackingJob(jobId)

    suspend fun getJob(jobId: String): GenerationJob? {
        return database.getJobById(jobId)
    }

    suspend fun updateJob(job: GenerationJob) {
        database.insertOrUpdateJob(job)
    }

    suspend fun deleteJob(jobId: String) {
        context?.let { ctx ->
            runCatching { androidx.work.WorkManager.getInstance(ctx).cancelUniqueWork("poll_$jobId") }
        }
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
        val provider = providerRegistry.getProviderById("atlas_cloud") ?: providerRegistry.getActiveProvider()
        return provider.testConnection(apiKey)
    }
}

object GenerationCancellationPolicy {
    fun supportMessage(job: GenerationJob, provider: com.vidadomu.nexus.provider.api.NexusProvider?): String {
        val label = com.vidadomu.nexus.ui.models.providerName(job.providerId)
        val remoteId = job.providerJobId
        return when {
            provider?.supportsRemoteCancellation != true ->
                "$label no admite cancelación remota de generaciones ya enviadas. Puedes usar «Detener seguimiento» para pausar las consultas locales sin cancelar en el proveedor."
            remoteId.isNullOrBlank() || remoteId.startsWith("queue_") ->
                "Este trabajo aún no tiene un identificador remoto cancelable en $label. Usa «Detener seguimiento» para pausar las consultas locales."
            else ->
                "$label admite cancelación remota. Si confirmas «Cancelar generación», se solicitará al proveedor cancelar la tarea remota ($remoteId)."
        }
    }

    fun stopLocalTracking(current: GenerationJob): GenerationJob? {
        if (current.status in setOf(GenerationStatus.COMPLETED, GenerationStatus.FAILED, GenerationStatus.CANCELLED)) {
            return null
        }
        return current.copy(
            status = GenerationStatus.FAILED,
            parameters = current.parameters + ("__tracking_paused" to "true"),
            error = "Seguimiento local detenido por el usuario. No se ha cancelado en el proveedor."
        )
    }

    suspend fun cancelRemote(
        current: GenerationJob,
        provider: com.vidadomu.nexus.provider.api.NexusProvider?
    ): Result<GenerationJob> {
        if (current.status in setOf(GenerationStatus.COMPLETED, GenerationStatus.FAILED, GenerationStatus.CANCELLED)) {
            return Result.failure(ProviderError("El trabajo ya no está en curso."))
        }
        if (provider == null) {
            return Result.failure(ProviderError("Proveedor no disponible."))
        }
        val remoteJobId = current.providerJobId
        if (!provider.supportsRemoteCancellation || remoteJobId.isNullOrBlank() || remoteJobId.startsWith("queue_")) {
            return Result.failure(ProviderError(supportMessage(current, provider)))
        }
        val remoteCancel = provider.cancelJob(remoteJobId)
        if (remoteCancel.isFailure || remoteCancel.getOrDefault(false) != true) {
            val err = (remoteCancel.exceptionOrNull() as? ProviderError)
                ?: ProviderError(remoteCancel.exceptionOrNull()?.message ?: "El proveedor rechazó la cancelación remota.")
            return Result.failure(err)
        }
        return Result.success(
            current.copy(
                status = GenerationStatus.CANCELLED,
                completedAt = System.currentTimeMillis(),
                parameters = current.parameters - "__tracking_paused",
                error = "Generación cancelada en ${provider.displayName} por solicitud explícita del usuario."
            )
        )
    }
}

