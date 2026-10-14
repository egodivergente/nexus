package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.data.repository.GenerationCancellationPolicy
import com.vidadomu.nexus.provider.api.NexusProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GenerationCancellationTest {
    private class FakeProvider(
        override val id: String,
        override val displayName: String,
        override val supportsRemoteCancellation: Boolean
    ) : NexusProvider {
        override val description: String = displayName
        val cancelledRemoteIds = mutableListOf<String>()

        override suspend fun getModels(): List<NexusModel> = emptyList()
        override suspend fun testConnection(apiKey: String): Result<Boolean> = Result.success(true)
        override suspend fun submitGeneration(request: GenerationRequest): Result<GenerationJob> =
            error("Not used in cancellation test")
        override suspend fun pollJobStatus(job: GenerationJob): Result<GenerationJob> =
            error("Not used in cancellation test")
        override suspend fun cancelJob(jobId: String): Result<Boolean> {
            cancelledRemoteIds += jobId
            return if (supportsRemoteCancellation) {
                Result.success(true)
            } else {
                Result.failure(ProviderError("$displayName no admite cancelación remota."))
            }
        }
        override suspend fun uploadMedia(fileBytes: ByteArray, fileName: String, mimeType: String): Result<String> =
            error("Not used in cancellation test")
    }

    private fun activeJob(providerId: String, remoteJobId: String? = "remote_123") = GenerationJob(
        id = "job_local_1",
        providerJobId = remoteJobId,
        providerId = providerId,
        modelId = "$providerId/model",
        modality = Modality.VIDEO,
        prompt = "Plano secuencia nocturno",
        status = GenerationStatus.PROCESSING
    )

    @Test
    fun stopLocalTrackingNeverCancelsRemotelyAndMarksTrackingPaused() {
        val job = activeJob("runway", "rw_task_99")
        val paused = GenerationCancellationPolicy.stopLocalTracking(job)
        assertNotNull(paused)
        assertEquals(GenerationStatus.FAILED, paused!!.status)
        assertEquals("true", paused.parameters["__tracking_paused"])
        assertTrue(paused.error!!.contains("No se ha cancelado en el proveedor"))
    }

    @Test
    fun unsupportedProvidersExplainLackOfRemoteCancellationWithoutCallingEndpoint() = runTest {
        for ((id, name) in listOf(
            "atlas_cloud" to "Atlas Cloud",
            "crun" to "cRun",
            "google_flow" to "Google Flow"
        )) {
            val provider = FakeProvider(id, name, supportsRemoteCancellation = false)
            val job = activeJob(id, "remote_$id")
            val message = GenerationCancellationPolicy.supportMessage(job, provider)
            assertTrue(message.contains("no admite cancelación remota"))
            assertTrue(message.contains("Detener seguimiento"))

            val result = GenerationCancellationPolicy.cancelRemote(job, provider)
            assertTrue(result.isFailure)
            assertTrue(provider.cancelledRemoteIds.isEmpty())
            val err = result.exceptionOrNull() as ProviderError
            assertTrue(err.humanReadableMessage.contains("no admite cancelación remota"))
        }
    }

    @Test
    fun supportedProviderCancelsRemotelyOnlyOnExplicitAction() = runTest {
        val runway = FakeProvider("runway", "Runway", supportsRemoteCancellation = true)
        val job = activeJob("runway", "rw_task_42")
        val message = GenerationCancellationPolicy.supportMessage(job, runway)
        assertTrue(message.contains("admite cancelación remota"))
        assertTrue(message.contains("rw_task_42"))

        val result = GenerationCancellationPolicy.cancelRemote(job, runway)
        assertTrue(result.isSuccess)
        assertEquals(listOf("rw_task_42"), runway.cancelledRemoteIds)
        val cancelled = result.getOrThrow()
        assertEquals(GenerationStatus.CANCELLED, cancelled.status)
        assertNull(cancelled.parameters["__tracking_paused"])
    }
}
