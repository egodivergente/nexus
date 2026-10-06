package com.vidadomu.nexus.provider.api

import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationRequest
import com.vidadomu.nexus.core.model.NexusModel

interface NexusProvider {
    val id: String
    val displayName: String
    val description: String

    suspend fun getModels(): List<NexusModel>
    suspend fun testConnection(apiKey: String): Result<Boolean>
    suspend fun submitGeneration(request: GenerationRequest): Result<GenerationJob>
    suspend fun pollJobStatus(job: GenerationJob): Result<GenerationJob>
    suspend fun cancelJob(jobId: String): Result<Boolean>
    suspend fun uploadMedia(fileBytes: ByteArray, fileName: String, mimeType: String): Result<String>
}
