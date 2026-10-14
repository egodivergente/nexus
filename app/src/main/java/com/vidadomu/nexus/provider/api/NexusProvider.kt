package com.vidadomu.nexus.provider.api

import com.vidadomu.nexus.core.model.ProviderError
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationRequest
import com.vidadomu.nexus.core.model.NexusModel
import kotlinx.serialization.json.*

data class ProviderAccountStatus(val balanceLabel: String, val details: String = "Saldo disponible")

object ProviderBalanceParser {
    fun decode(raw: String, preferredKeys: List<String>): ProviderAccountStatus {
        val root = Json.parseToJsonElement(raw)
        val candidates = preferredKeys.map { normalize(it) }.toSet()
        fun find(element: JsonElement): Pair<String, JsonPrimitive>? {
            if(element is JsonObject) {
                element.entries.firstOrNull { (key, value) ->
                    normalize(key) in candidates && value is JsonPrimitive && value.content.toBigDecimalOrNull() != null
                }?.let { return it.key to (it.value as JsonPrimitive) }
                element.values.forEach { value -> find(value)?.let { return it } }
            }
            return null
        }
        val (key,value) = find(root) ?: error("El proveedor no devolvió un saldo reconocible.")
        val amount = value.content.toBigDecimal().stripTrailingZeros().toPlainString()
        val money = normalize(key).contains("usd") || normalize(key).contains("dollar")
        return ProviderAccountStatus(if(money) "$" + amount + " USD" else "$amount créditos")
    }
    private fun normalize(value: String) = value.lowercase().replace(Regex("[^a-z0-9]"), "")
}

interface NexusProvider {
    val id: String
    val displayName: String
    val description: String

    val catalogNotice:String? get() = null
    val supportsRemoteCancellation: Boolean get() = false

    suspend fun getModels(): List<NexusModel>
    suspend fun testConnection(apiKey: String): Result<Boolean>
    suspend fun fetchAccountStatus(apiKey: String): Result<ProviderAccountStatus>? = null
    suspend fun submitGeneration(request: GenerationRequest): Result<GenerationJob>
    suspend fun pollJobStatus(job: GenerationJob): Result<GenerationJob>
    suspend fun cancelJob(jobId: String): Result<Boolean>
    suspend fun uploadFile(file:java.io.File,fileName:String,mimeType:String,onProgress:(Long,Long)->Unit = {_,_->}):Result<String> =
        Result.failure(ProviderError("Este proveedor no admite subida de archivos."))

    suspend fun uploadMedia(fileBytes: ByteArray, fileName: String, mimeType: String): Result<String>
}
