package com.vidadomu.nexus.agent.core.llm

import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
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

data class AwsCredentials(val accessKeyId: String, val secretAccessKey: String, val sessionToken: String? = null)

/**
 * Bedrock Converse llamado directamente desde el móvil, firmado con SigV4. Decisión del usuario (02-10):
 * claves en el dispositivo, sin proxy. Mismo cuerpo que [ConverseCodec] salvo `modelId`, que va en la ruta.
 */
class BedrockDirectProvider(
    private val region: String,
    private val credentials: () -> AwsCredentials,
    private val client: OkHttpClient = BedrockProxyProvider.defaultClient(),
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(ZoneOffset.UTC) },
) : LlmProvider {

    override val id: String = "bedrock-direct"

    override suspend fun complete(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val body = JsonObject(ConverseCodec.encodeRequest(request) - "modelId").toString()
        val host = "bedrock-runtime.$region.amazonaws.com"
        val path = "/model/${SigV4.encode(request.modelId)}/converse"
        val signed = SigV4.sign("POST", host, path, body, region, "bedrock", credentials(), now())
        val http = Request.Builder().url("https://$host$path")
            .apply { signed.forEach { (k, v) -> header(k, v) } }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(http).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw mapError(resp.code, text, resp.header("x-amzn-ErrorType"))
                val json = runCatching { Json.parseToJsonElement(text).jsonObject }
                    .getOrElse { throw LlmException(LlmException.Kind.PROTOCOL, "Bedrock devolvió una respuesta no válida.") }
                ConverseCodec.decodeResponse(json)
            }
        } catch (e: IOException) {
            throw LlmException(LlmException.Kind.NETWORK, "No se pudo contactar con Bedrock (${e.javaClass.simpleName}).")
        }
    }

    override suspend fun countInputTokens(request: LlmRequest): Int? = withContext(Dispatchers.IO) {
        val fields = ConverseCodec.encodeRequest(request).filterKeys { it in setOf("messages", "system", "toolConfig") }
        val body = kotlinx.serialization.json.buildJsonObject {
            put("input", kotlinx.serialization.json.buildJsonObject { put("converse", JsonObject(fields)) })
        }.toString()
        val host = "bedrock-runtime.$region.amazonaws.com"
        // CountTokens accepts a foundation model, whereas Converse uses the inference profile.
        // Passing us.anthropic.* here yields ValidationException and inflates the budget fallback.
        val countModel = request.modelId.replace(
            Regex("^(us|eu|apac|au|jp|global)\\.(anthropic\\..+)$"), "$2")
        val path = "/model/${SigV4.encode(countModel)}/count-tokens"
        val signed = SigV4.sign("POST", host, path, body, region, "bedrock", credentials(), now())
        val http = Request.Builder().url("https://$host$path").apply { signed.forEach { (k,v) -> header(k,v) } }
            .post(body.toRequestBody("application/json".toMediaType())).build()
        try {
            client.newCall(http).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject["inputTokens"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.takeIf { it >= 0 }
            }
        } catch (error: IOException) { null }
    }

    private fun mapError(code: Int, body: String, type: String?): LlmException {
        val detail = runCatching { Json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull }
            .getOrNull()?.take(240)
        val t = type.orEmpty()
        val kind = when {
            code == 429 || "Throttling" in t -> LlmException.Kind.THROTTLED
            "AccessDenied" in t && detail?.contains("not available", true) == true -> LlmException.Kind.MODEL_NOT_ALLOWED
            code == 401 || code == 403 -> LlmException.Kind.AUTH
            code == 404 -> LlmException.Kind.MODEL_NOT_ALLOWED
            code == 400 || code == 413 -> LlmException.Kind.BAD_REQUEST
            else -> LlmException.Kind.UPSTREAM
        }
        val msg = when (kind) {
            LlmException.Kind.AUTH -> "Bedrock rechazó las claves de AWS" + (detail?.let { ": $it" } ?: ".")
            LlmException.Kind.THROTTLED -> "Bedrock está limitando peticiones; reintenta en unos segundos."
            LlmException.Kind.MODEL_NOT_ALLOWED -> "Ese modelo no está disponible en tu cuenta de Bedrock" + (detail?.let { ": $it" } ?: ".")
            else -> detail ?: "Error de Bedrock (HTTP $code)."
        }
        return LlmException(kind, msg, code)
    }
}

/** Firma AWS Signature Version 4 (cabeceras), sin dependencias. */
object SigV4 {
    private val AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** Codificación RFC 3986 de un tramo de ruta. */
    fun encode(segment: String): String =
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

    /** Devuelve las cabeceras a añadir (host, x-amz-date, opcional token, Authorization). */
    fun sign(method: String, host: String, path: String, body: String, region: String, service: String, c: AwsCredentials, at: ZonedDateTime): Map<String, String> {
        val amzDate = at.withZoneSameInstant(ZoneOffset.UTC).format(AMZ_DATE)
        val date = at.withZoneSameInstant(ZoneOffset.UTC).format(DATE)
        val headers = sortedMapOf("host" to host, "x-amz-date" to amzDate)
        c.sessionToken?.takeIf { it.isNotBlank() }?.let { headers["x-amz-security-token"] = it }
        val signedHeaders = headers.keys.joinToString(";")
        // Servicios distintos de S3: cada tramo de la ruta (ya codificado una vez) se codifica de nuevo.
        val canonicalUri = path.split('/').joinToString("/") { encode(it) }
        val canonical = listOf(method, canonicalUri, "", headers.entries.joinToString("") { "${it.key}:${it.value.trim()}\n" }, signedHeaders, hex(sha256(body.toByteArray()))).joinToString("\n")
        val scope = "$date/$region/$service/aws4_request"
        val toSign = listOf("AWS4-HMAC-SHA256", amzDate, scope, hex(sha256(canonical.toByteArray()))).joinToString("\n")
        var key = hmac(("AWS4" + c.secretAccessKey).toByteArray(), date)
        key = hmac(key, region); key = hmac(key, service); key = hmac(key, "aws4_request")
        val signature = hex(hmac(key, toSign))
        val out = linkedMapOf("x-amz-date" to amzDate)
        c.sessionToken?.takeIf { it.isNotBlank() }?.let { out["x-amz-security-token"] = it }
        out["Authorization"] = "AWS4-HMAC-SHA256 Credential=${c.accessKeyId}/$scope, SignedHeaders=$signedHeaders, Signature=$signature"
        return out
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)
    private fun hmac(key: ByteArray, data: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data.toByteArray())
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}
