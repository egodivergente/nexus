package com.vidadomu.nexus.agent.core.llm

import java.util.concurrent.ConcurrentHashMap

/**
 * Caché de prompt de Bedrock con red de seguridad: si una petición con cachePoint es rechazada (400) y la misma
 * sin caché funciona, ese modelo deja de usar caché hasta reiniciar la app. Un 400 no se factura.
 * Solo la piden las llamadas del orquestador (cacheableSystemBlocks > 0) fuera de la lectura de documentos: las de un
 * solo uso (resúmenes, sondas, adaptación de prompts) no pagan la escritura. Un 413 (demasiado grande) no se repite.
 */
internal object PromptCacheFallback {
    private val rejected: MutableSet<String> = ConcurrentHashMap.newKeySet()

    suspend fun complete(request: LlmRequest, send: suspend (cache: Boolean) -> LlmResponse): LlmResponse {
        val cache = request.cacheableSystemBlocks > 0 && ConverseCodec.supportsPromptCache(request.modelId) && request.modelId !in rejected
        if (!cache) return send(false)
        return try {
            send(true)
        } catch (e: LlmException) {
            if (e.kind != LlmException.Kind.BAD_REQUEST || e.httpStatus == 413) throw e
            val response = send(false)
            rejected += request.modelId
            response
        }
    }
}
