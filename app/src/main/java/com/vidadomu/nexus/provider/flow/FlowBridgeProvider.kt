package com.vidadomu.nexus.provider.flow

import android.net.Uri
import com.vidadomu.nexus.core.alexia.AlexiaBridge
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationOutput
import com.vidadomu.nexus.core.model.GenerationRequest
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.core.model.ProviderError
import com.vidadomu.nexus.core.security.SecureStorage
import com.vidadomu.nexus.provider.api.NexusProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.File

/**
 * Executes text and image-reference work in the already open Google Flow project through the private bridge.
 * This is deliberately not part of the public model catalog: Flow owns its active model,
 * configuration and cost, and Nexus must not present invented metadata for them.
 */
class FlowBridgeProvider(
    client: OkHttpClient,
    secureStorage: SecureStorage,
    context: android.content.Context? = null
) : NexusProvider {
    private val appContext = context?.applicationContext
    private val bridge = FlowBridgeClient(client, secureStorage)
    private val queue = context?.getSharedPreferences("flow_queue_v1", android.content.Context.MODE_PRIVATE)
    private val queueLock = kotlinx.coroutines.sync.Mutex()

    override val id: String = PROVIDER_ID
    override val displayName: String = "Google Flow"
    override val description: String = "Proyecto Flow activo mediante un puente privado; el modelo y el coste se deciden allí."

    suspend fun bridgeStatus(): Result<FlowBridgeStatus> = bridge.status()

    override suspend fun getModels(): List<NexusModel> = listOf(Modality.IMAGE, Modality.VIDEO).map(FlowReferences::model)

    override suspend fun testConnection(apiKey: String): Result<Boolean> =
        bridgeStatus().map { it.isReady }

    override suspend fun submitGeneration(request: GenerationRequest): Result<GenerationJob> = withContext(Dispatchers.IO) {
        val flowMode = if(request.modality==Modality.VIDEO) request.parameters["mode"] ?: "Texto" else "Referencias"
        val flowInputs = try { FlowReferences.inputs(request.mediaInputs, if (request.modality == Modality.VIDEO) flowMode else null) } catch (error: IllegalArgumentException) {
            return@withContext Result.failure(ProviderError(error.message ?: "Revisa las referencias de Flow.", httpStatusCode = 400))
        }
        if (request.modality !in setOf(Modality.IMAGE, Modality.VIDEO)) {
            return@withContext Result.failure(ProviderError("Flow solo puede recibir una salida de imagen o vídeo desde esta pantalla."))
        }
        val requestedCount = request.parameters["count"]?.toIntOrNull() ?: 1
        if(requestedCount !in 1..FlowBridgeClient.MAX_RESULT_COUNT) return@withContext Result.failure(ProviderError("Elige de 1 a ${FlowBridgeClient.MAX_RESULT_COUNT} resultados."))
        val expectedOutput = request.parameters[EXPECTED_OUTPUT_KEY]?.lowercase()
            ?: modalityName(request.modality)
        if (expectedOutput != modalityName(request.modality)) {
            return@withContext Result.failure(ProviderError("El tipo de salida esperado no coincide con la tarea de Flow."))
        }

        if (queue != null) return@withContext queueLock.withLock {
            val previous = queue.getString("tail", null)?.takeUnless { it == request.id }
            queue.edit().putString("tail", request.id).commit()
            Result.success(GenerationJob(id = request.id, providerId = id, modelId = request.modelId,
                modality = request.modality, prompt = request.prompt, negativePrompt = request.negativePrompt,
                providerJobId = "queue_${request.id}", status = GenerationStatus.QUEUED,
                parameters = request.parameters + mapOf("__flow_queue_previous" to previous.orEmpty(),
                    FlowReferences.PARAMETER to flowInputs.references.joinToString(","),
                    FlowReferences.INITIAL_FRAME to flowInputs.initialFrame.orEmpty(),
                    FlowReferences.END_FRAME to flowInputs.endFrame.orEmpty(),
                    FlowReferences.MODE to flowMode),
                inputMediaUri = request.mediaInputs.firstOrNull()?.uriString,
                technicalDetails = "En cola de Flow; se enviará automáticamente sin una segunda confirmación."))
        }
        bridge.submit(request.prompt, expectedOutput, request.id, flowInputs.references,
            mode = flowMode, initialFrameId = flowInputs.initialFrame, endFrameId = flowInputs.endFrame).map { remote ->
            initialJob(request, remote)
        }
    }

    override suspend fun pollJobStatus(job: GenerationJob): Result<GenerationJob> = withContext(Dispatchers.IO) {
        if (job.status in setOf(GenerationStatus.COMPLETED, GenerationStatus.FAILED, GenerationStatus.CANCELLED)) {
            return@withContext Result.success(job)
        }
        if (job.providerJobId?.startsWith("queue_") == true && queue != null) return@withContext queueLock.withLock {
            val previous = job.parameters["__flow_queue_previous"].orEmpty()
            if (previous.isNotBlank() && queue.getString("state_$previous", "") !in setOf("completed", "failed", "released")) {
                val previousJob = (appContext as? com.vidadomu.nexus.NexusApplication)?.generationRepository?.getJob(previous)
                val hasRemote = queue.contains("remote_${previous}_0")
                if(!FlowQueueDependency.canProceed(previousJob,hasRemote,waitingSince=job.startedAt))
                    return@withLock Result.success(job.copy(status = GenerationStatus.QUEUED, error = "Esperando al encargo anterior de Flow; su seguimiento sigue activo."))
                queue.edit().putString("state_$previous", "released").commit()
            }
            if (FlowRetryPolicy.waiting(job.parameters["__flow_retry_at"]?.toLongOrNull())) return@withLock Result.success(job)
            val block = job.parameters["__flow_block"]?.toIntOrNull() ?: 0
            val attempt = job.parameters["__flow_attempt"]?.toIntOrNull() ?: 0
            val requestId = FlowRetryPolicy.requestId(job.id, block, attempt)
            val recovered = queue.getString("remote_$requestId", null)
            val remote = if(recovered != null) bridge.job(recovered) else
                run {
                    // Lote: lo que falta se pide de una vez, en tantos envíos como hagan falta según el «xN» de Flow.
                    val goal = (job.parameters["count"]?.toIntOrNull() ?: 1).coerceIn(1, FlowBridgeClient.MAX_RESULT_COUNT)
                    val perRun = (job.parameters["per_run"]?.toIntOrNull() ?: 4).coerceIn(1, 4)
                    val missing = (goal - job.outputs.size).coerceAtLeast(1)
                    val runs = ((missing + perRun - 1) / perRun).coerceIn(1, FlowBridgeClient.MAX_RUNS)
                    bridge.submit(job.prompt, modalityName(job.modality), requestId, FlowReferences.savedIds(job.parameters),
                        mode = job.parameters[FlowReferences.MODE] ?: "Texto",
                        initialFrameId = job.parameters[FlowReferences.INITIAL_FRAME]?.takeIf { it.isNotBlank()},
                        endFrameId = job.parameters[FlowReferences.END_FRAME]?.takeIf { it.isNotBlank()},
                        runs = runs, expectedResults = if (goal > 1) missing else 0)
                }
            val submissionError = remote.exceptionOrNull() as? ProviderError
            if (submissionError?.errorCode == "flow_busy") {
                return@withLock Result.success(job.copy(error = FlowProgress.problem("flow_busy"),
                    parameters = job.parameters + ("__flow_retry_at" to (System.currentTimeMillis() + 8_000L).toString())))
            }
            if (submissionError?.errorCode in setOf("invalid_reference_ids", "invalid_reference_image",
                "reference_not_found", "reference_integrity_failed", "duplicate_reference",
                "flow_references_not_empty", "flow_reference_controls_unavailable",
                "flow_reference_multiple_unavailable", "flow_reference_upload_failed", "flow_references_unconfirmed",
                "invalid_frame_ids", "duplicate_frame", "end_frame_requires_initial", "initial_frame_required",
                "flow_frame_mode_unavailable", "flow_frame_controls_unavailable", "flow_frame_upload_failed",
                "invalid_video_mode", "text_mode_has_media", "references_mode_requires_images", "frame_mode_has_references")) {
                queue.edit().putString("state_${job.id}", "failed").commit()
                return@withLock Result.success(job.copy(providerJobId = null, status = GenerationStatus.FAILED,
                    completedAt = System.currentTimeMillis(), error = submissionError?.humanReadableMessage))
            }
            remote.map { submitted ->
                if (submitted.state == "failed" && FlowRetryPolicy.shouldRetry(submitted.error, attempt)) {
                    queue.edit().putString("state_${job.id}", "active").commit()
                    return@withLock Result.success(retryLater(job, submitted.error, attempt))
                }
                queue.edit().putString("remote_$requestId", submitted.id).putString("state_${job.id}", if(submitted.state == "failed") "failed" else "active").commit()
                job.copy(providerJobId = submitted.id, status = statusFor(submitted.state),
                    parameters = job.parameters + ("__tracking_started_at" to System.currentTimeMillis().toString()),
                    completedAt = if(submitted.state == "failed") System.currentTimeMillis() else null,
                    error = if(submitted.state == "failed") flowFailureMessage(submitted.error) else pendingMessage(submitted))
            }
        }
        val bridgeJobId = job.providerJobId
            ?: return@withContext Result.failure(ProviderError("La tarea de Flow no contiene un identificador de seguimiento."))
        val remoteResult = bridge.job(bridgeJobId)
        if (remoteResult.isFailure) {
            return@withContext Result.failure(remoteResult.exceptionOrNull()!!)
        }
        val remote = remoteResult.getOrThrow()
        if(remote.state == "failed") queue?.edit()?.putString("state_${job.id}", "failed")?.apply()
        try {
            when (remote.state) {
                "completed" -> {
                    // Keep the gallery reserved until original files have been copied.
                    (appContext as? com.vidadomu.nexus.NexusApplication)?.generationRepository?.updateJob(
                        job.copy(status = GenerationStatus.PROCESSING,
                            error = "Descargando ${remote.resultCount} archivos de Flow a Nexus…"))
                    copyCompletedResult(job, remote).map { copied ->
                    val goal = job.parameters["count"]?.toIntOrNull() ?: 1
                    if(copied.status == GenerationStatus.COMPLETED && copied.outputs.size < goal) {
                        val block = job.parameters["__flow_block"]?.toIntOrNull() ?: 0
                        copied.copy(providerJobId = "queue_${job.id}", status = GenerationStatus.QUEUED, completedAt = null,
                            parameters = copied.parameters + ("__flow_block" to (block + 1).toString()),
                            progress = copied.outputs.size.toFloat() / goal,
                            technicalDetails = "${copied.outputs.size} de $goal resultados recuperados. Continuando automáticamente.")
                    } else {
                        if(copied.status == GenerationStatus.COMPLETED) queue?.edit()?.putString("state_${job.id}", "completed")?.apply()
                        copied
                    }
                }
                }
                "failed" -> if (queue != null && FlowRetryPolicy.shouldRetry(remote.error, job.parameters["__flow_attempt"]?.toIntOrNull() ?: 0)) {
                    queue.edit().putString("state_${job.id}", "active").commit()
                    Result.success(retryLater(job, remote.error, job.parameters["__flow_attempt"]?.toIntOrNull() ?: 0))
                } else Result.success(
                    job.copy(
                        status = GenerationStatus.FAILED,
                        completedAt = System.currentTimeMillis(),
                        error = flowFailureMessage(remote.error),
                        technicalDetails = flowDetails(remote)
                    )
                )
                else -> Result.success(
                    job.copy(
                        status = statusFor(remote.state),
                        progress = progressFor(remote.state, job.progress),
                        error = pendingMessage(remote),
                        technicalDetails = flowDetails(remote)
                    )
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(if (error is ProviderError) error else ProviderError("No se pudo recuperar el resultado de Flow todavía."))
        }
    }

    /** Flow does not expose a reliable cancellation action through this private bridge. */
    override suspend fun cancelJob(jobId: String): Result<Boolean> =
        Result.failure(ProviderError("Google Flow no admite cancelación remota de generaciones ya enviadas. Usa «Detener seguimiento» para pausar las consultas locales."))

    override suspend fun uploadFile(file: File, fileName: String, mimeType: String,
        onProgress: (Long, Long) -> Unit): Result<String> = bridge.uploadReference(file, mimeType)

    override suspend fun uploadMedia(fileBytes: ByteArray, fileName: String, mimeType: String): Result<String> =
        Result.failure(ProviderError("Usa la copia local del archivo para subir la referencia sin cargarla entera en memoria."))

    private suspend fun copyCompletedResult(job: GenerationJob, remote: FlowBridgeJob): Result<GenerationJob> {
        if (remote.expectedOutput != modalityName(job.modality)) {
            return Result.success(
                job.copy(
                    status = GenerationStatus.PROCESSING,
                    progress = job.progress?.coerceAtLeast(0.9f) ?: 0.9f,
                    error = "El resultado de Flow no coincide con el tipo elegido en Nexus. Ábrelo en Flow para comprobarlo.",
                    technicalDetails = flowDetails(remote)
                )
            )
        }
        if (!remote.downloadAvailable || remote.resultCount <= 0) {
            return Result.success(
                job.copy(
                    status = GenerationStatus.PROCESSING,
                    progress = job.progress?.coerceAtLeast(0.9f) ?: 0.9f,
                    error = "Flow terminó, pero Nexus no puede identificar todavía un archivo descargable. Abre Flow para comprobar el resultado.",
                    technicalDetails = flowDetails(remote)
                )
            )
        }
        if (remote.resultCount > MAX_RESULT_COUNT) {
            return Result.success(
                job.copy(
                    status = GenerationStatus.PROCESSING,
                    progress = job.progress?.coerceAtLeast(0.9f) ?: 0.9f,
                    error = "Flow devolvió más resultados de los que Nexus puede copiar con seguridad. Ábrelos en Flow.",
                    technicalDetails = flowDetails(remote)
                )
            )
        }

        val destination = AlexiaBridge.getNexusFolderForModality(job.modality)
        val outputs = job.outputs.toMutableList()
        var copyError: Throwable? = null
        for (index in 0 until remote.resultCount) {
            val outputId = "${job.id}_flow_${job.parameters["__flow_block"] ?: "0"}_$index"
            val existing = outputs.firstOrNull { it.id == outputId }
            if (existing?.localUri?.let(::hasUsableLocalFile) == true) continue

            (appContext as? com.vidadomu.nexus.NexusApplication)?.generationRepository?.updateJob(
                job.copy(status = GenerationStatus.PROCESSING, outputs = outputs.toList(),
                    error = "Descargando archivo ${index + 1} de ${remote.resultCount}…"))
            bridge.download(
                jobId = remote.id,
                index = index,
                destinationDir = destination,
                expectedOutput = remote.expectedOutput
            ).onSuccess { downloaded ->
                val replacement = GenerationOutput(
                    id = outputId,
                    jobId = job.id,
                    type = job.modality,
                    // LocalUri is the usable asset. Keep a non-routable identifier out of sharing/UI.
                    url = "flow://${remote.id}/$index",
                    localUri = Uri.fromFile(downloaded.file).toString(),
                    mimeType = downloaded.mimeType
                )
                val existingIndex = outputs.indexOfFirst { it.id == outputId }
                if (existingIndex >= 0) outputs[existingIndex] = replacement else outputs += replacement
            }.onFailure { copyError = it }
            if (copyError != null) break
        }

        if (copyError != null) {
            val failures = (job.parameters["__flow_copy_failures"]?.toIntOrNull() ?: 0) + 1
            return Result.success(
                job.copy(
                    status = GenerationStatus.PROCESSING,
                    progress = job.progress?.coerceAtLeast(0.94f) ?: 0.94f,
                    outputs = outputs,
                    parameters = job.parameters + ("__flow_copy_failures" to failures.toString()),
                    error = "No se pudo copiar el resultado (intento $failures): ${copyError.message.orEmpty()} Se volverá a consultar sin generar otra vez.",
                    technicalDetails = flowDetails(remote)
                )
            )
        }

        return Result.success(
            job.copy(
                status = GenerationStatus.COMPLETED,
                progress = 1f,
                completedAt = System.currentTimeMillis(),
                error = null,
                outputs = outputs,
                technicalDetails = flowDetails(remote)
            )
        )
    }

    private fun initialJob(request: GenerationRequest, remote: FlowBridgeJob): GenerationJob {
        val flowMode = if (request.modality == Modality.VIDEO) request.parameters["mode"] ?: "Texto" else "Referencias"
        val flowInputs = FlowReferences.inputs(request.mediaInputs, if (request.modality == Modality.VIDEO) flowMode else null)
        return GenerationJob(
            id = request.id,
            providerJobId = remote.id,
            providerId = id,
            modelId = request.modelId,
            modality = request.modality,
            prompt = request.prompt,
            negativePrompt = request.negativePrompt,
            parameters = request.parameters + mapOf(
                FlowReferences.PARAMETER to flowInputs.references.joinToString(","),
                FlowReferences.INITIAL_FRAME to flowInputs.initialFrame.orEmpty(),
                FlowReferences.END_FRAME to flowInputs.endFrame.orEmpty(),
                FlowReferences.MODE to flowMode),
            inputMediaUri = request.mediaInputs.firstOrNull()?.uriString,
            status = statusFor(remote.state),
            progress = progressFor(remote.state, null),
            completedAt = if (remote.state == "failed") System.currentTimeMillis() else null,
            error = if (remote.state == "failed") flowFailureMessage(remote.error) else pendingMessage(remote),
            technicalDetails = flowDetails(remote)
        )
    }

    private fun statusFor(state: String): GenerationStatus = when (state) {
        "submitting" -> GenerationStatus.QUEUED
        "submitted", "generating" -> GenerationStatus.GENERATING
        "uncertain" -> GenerationStatus.PROCESSING
        "completed" -> GenerationStatus.PROCESSING
        "failed" -> GenerationStatus.FAILED
        else -> GenerationStatus.PROCESSING
    }

    private fun progressFor(state: String, previous: Float?): Float? = when (state) {
        "submitting" -> previous?.coerceAtLeast(0.05f) ?: 0.05f
        "submitted" -> previous?.coerceAtLeast(0.18f) ?: 0.18f
        "generating" -> previous?.coerceAtLeast(0.3f) ?: 0.3f
        "uncertain" -> previous?.coerceAtLeast(0.18f) ?: 0.18f
        "completed" -> previous?.coerceAtLeast(0.9f) ?: 0.9f
        else -> previous
    }

    /** Vuelve a la cola de Flow para un nuevo intento dentro de [FlowRetryPolicy.DELAY_MS]. */
    private fun retryLater(job: GenerationJob, error: String?, attempt: Int): GenerationJob = job.copy(
        providerJobId = "queue_${job.id}", status = GenerationStatus.QUEUED, completedAt = null,
        parameters = job.parameters + mapOf("__flow_attempt" to (attempt + 1).toString(),
            "__flow_retry_at" to (System.currentTimeMillis() + FlowRetryPolicy.DELAY_MS).toString()),
        error = "Reintento automático ${attempt + 1} de ${FlowRetryPolicy.MAX_RETRIES}: ${flowFailureMessage(error)}")

    private fun flowDetails(remote: FlowBridgeJob): String = buildString {
        append(FlowProgress.stage(remote.phase, remote.state))
        append(" · ").append(FlowProgress.elapsed(remote.elapsedSeconds))
        append("\nAjustes y coste: proyecto activo de Flow")
        if (remote.idempotentReplay) append("\nLa solicitud se recuperó sin volver a enviarla.")
        remote.error?.takeIf { it.isNotBlank() }?.let { append("\nPuente: ").append(flowStateMessage(it)) }
    }

    private fun pendingMessage(remote: FlowBridgeJob): String? = when (remote.state) {
        "uncertain" -> "Flow no confirmó el envío. No se reenviará automáticamente; comprueba el proyecto antes de repetir."
        else -> remote.error?.takeIf { it.isNotBlank() }?.let(::flowStateMessage)
            ?: "${FlowProgress.stage(remote.phase, remote.state)} · ${FlowProgress.elapsed(remote.elapsedSeconds)}"
    }

    private fun flowFailureMessage(error: String?): String =
        error?.takeIf { it.isNotBlank() }?.let(::flowStateMessage)
            ?: "Flow no pudo iniciar esta generación. Comprueba el proyecto antes de repetir."

    private fun flowStateMessage(error: String): String = FlowProgress.problem(error) ?: when (error) {
        "flow_project_not_open" -> "El proyecto de Flow no está abierto. Ábrelo y vuelve a consultar."
        "flow_project_ambiguous" -> "Hay más de un proyecto de Flow abierto. Deja abierto solo el proyecto que quieres usar."
        "flow_not_connected" -> "El puente (AXELPC o ASUS) no tiene una sesión de Flow disponible. Vuelve a conectar Flow."
        "flow_controls_unavailable" -> "Flow está abierto, pero aún no muestra los controles de creación."
        "flow_composer_not_empty" -> "Hay un borrador escrito en Flow. Revísalo o bórralo antes de enviar desde Nexus."
        "flow_composer_not_editable" -> "El cuadro de texto de Flow no está disponible todavía."
        "flow_submit_unavailable" -> "Flow no habilitó la generación para ese proyecto. Revisa sus ajustes."
        "flow_submit_failed" -> "Flow no confirmó el envío. Comprueba el proyecto antes de repetir."
        "flow_browser_unavailable" -> "El navegador privado de Flow no está disponible en el puente (AXELPC o ASUS)."
        "flow_not_ready" -> "Flow no está listo para crear en este momento."
        "auth_required" -> "Flow necesita volver a iniciar sesión en el navegador del puente (AXELPC o ASUS)."
        "submit_uncertain" -> "Flow no confirmó el envío. No se reenviará automáticamente."
        "result_not_ready" -> "El resultado todavía no está listo para descargarse."
        "download_ambiguous" -> "Nexus no puede identificar con seguridad qué resultado descargar. Ábrelo en Flow."
        "download_unavailable", "download_failed" -> "Flow tiene un resultado, pero Nexus no puede descargarlo todavía."
        else -> "El puente de Flow comunicó un estado que requiere revisión."
    }

    private fun hasUsableLocalFile(value: String): Boolean = runCatching {
        val uri = Uri.parse(value)
        val file = File(uri.path ?: return@runCatching false)
        file.isFile && file.length() > 0L
    }.getOrDefault(false)

    private fun modalityName(modality: Modality): String = when (modality) {
        Modality.IMAGE -> "image"
        Modality.VIDEO -> "video"
        else -> ""
    }

    companion object {
        const val PROVIDER_ID = "google_flow"
        const val CURRENT_PROJECT_MODEL = "current_project"
        const val EXPECTED_OUTPUT_KEY = "__flow_expected_output"
        private const val MAX_RESULT_COUNT = FlowBridgeClient.MAX_RESULT_COUNT
    }
}
