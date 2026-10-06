package com.vidadomu.nexus.agent.core.llm

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Bedrock a través del proxy de Nexus (APK → proxy → Bedrock). La APK solo conoce la URL del proxy
 * y un token de dispositivo revocable; las credenciales de AWS viven en el servidor.
 *
 * Contrato: POST {baseUrl}/v1/converse con el cuerpo de Converse ([ConverseCodec]) y
 * `Authorization: Bearer <token>`. El proxy aplica lista blanca de modelos, topes de tokens y
 * presupuesto diario.
 */
class BedrockProxyProvider(
    private val baseUrl: String,
    private val deviceToken: () -> String,
    private val client: OkHttpClient = defaultClient(),
) : LlmProvider {

    override val id: String = "bedrock-proxy"

    override suspend fun complete(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val body = ConverseCodec.encodeRequest(request).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val http = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/converse")
            .header("Authorization", "Bearer " + deviceToken())
            .post(body)
            .build()
        try {
            client.newCall(http).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw mapError(resp.code, text)
                val json = runCatching { Json.parseToJsonElement(text).jsonObject }
                    .getOrElse { throw LlmException(LlmException.Kind.PROTOCOL, "El proxy devolvió una respuesta no válida.") }
                ConverseCodec.decodeResponse(json)
            }
        } catch (e: IOException) {
            throw LlmException(LlmException.Kind.NETWORK, "No se pudo contactar con el asistente (${e.javaClass.simpleName}).")
        }
    }

    private fun mapError(code: Int, body: String): LlmException {
        // El proxy responde {"error": {"code": "...", "message": "..."}}; nunca incluye secretos.
        val detail = runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.take(200)
        val kind = when (code) {
            401, 403 -> LlmException.Kind.AUTH
            402 -> LlmException.Kind.BUDGET
            422 -> LlmException.Kind.MODEL_NOT_ALLOWED
            429 -> LlmException.Kind.THROTTLED
            400, 413 -> LlmException.Kind.BAD_REQUEST
            else -> LlmException.Kind.UPSTREAM
        }
        val msg = when (kind) {
            LlmException.Kind.AUTH -> "El proxy rechazó el token del dispositivo."
            LlmException.Kind.BUDGET -> "Se alcanzó el presupuesto diario del asistente."
            LlmException.Kind.THROTTLED -> "El asistente está saturado; reintenta en unos segundos."
            else -> detail ?: "Error del asistente (HTTP $code)."
        }
        return LlmException(kind, msg, code)
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
