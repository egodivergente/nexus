package com.vidadomu.nexus.agent.core.llm

import com.vidadomu.nexus.agent.core.net.JsonPaths
import com.vidadomu.nexus.agent.core.net.JsonTemplate
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

/** Construye el [LlmProvider] de un [BrainProfile]. Aquí no hay lista de proveedores: todo sale de la configuración. */
object BrainFactory {
    /** Cliente con los tiempos del perfil. 0 = sin límite. En streaming no hay tope total, solo entre bytes. */
    fun clientFor(profile: BrainProfile, streaming: Boolean = profile.capabilities.streaming): OkHttpClient {
        val seconds = profile.limits.httpTimeoutSeconds ?: DEFAULT_HTTP_SECONDS
        val t = seconds.toLong()
        return BedrockProxyProvider.defaultClient().newBuilder()
            .readTimeout(t, TimeUnit.SECONDS)
            .callTimeout(if (streaming) 0 else t, TimeUnit.SECONDS)
            .build()
    }

    fun create(profile: BrainProfile, apiKey: () -> String, client: OkHttpClient? = null): LlmProvider {
        val problems = profile.problems()
        require(problems.isEmpty()) { problems.first() }
        val http = client ?: clientFor(profile)
        val target = HttpTarget(profile.endpointUrl(), profile.auth, apiKey, profile.extraHeaders)
        val streaming = profile.capabilities.streaming
        val sendTools = profile.capabilities.tools
        return when (profile.protocol) {
            BrainProtocol.OPENAI_CHAT -> {
                val o = ChatOptions(profile.reasoningEffort, profile.thinking, null, profile.parsedExtraBody(), sendTools)
                if (streaming) OpenAiCompatibleStreamingProvider(target, o, http, profile.name) else OpenAiCompatibleProvider(target, o, http, profile.name)
            }
            BrainProtocol.OPENAI_RESPONSES -> {
                val o = ResponsesOptions(profile.reasoningEffort, profile.thinking, profile.storeOnServer, profile.parsedExtraBody(), sendTools)
                if (streaming) ResponsesApiStreamingProvider(target, o, http, profile.name, "responses")
                else ResponsesApiProvider(target, o, http, profile.name, "responses")
            }
            BrainProtocol.CUSTOM_REST -> CustomRestProvider(target, JsonTemplate.parse(profile.restRequestTemplate), profile.restTextPath, http, profile.name)
        }
    }

    const val DEFAULT_HTTP_SECONDS = 120
}

/**
 * REST de solo texto definido por plantilla: sin herramientas ni visión. Sirve para conversar con un servicio
 * propio, no para que el agente ejecute acciones. Marcadores: {{model}}, {{system}}, {{prompt}}, {{messages}}.
 */
class CustomRestProvider(
    private val target: HttpTarget,
    private val template: JsonElement,
    private val textPath: String,
    private val client: OkHttpClient = BedrockProxyProvider.defaultClient(),
    private val who: String = "el proveedor",
) : LlmProvider {
    override val id: String = "custom-rest"

    override suspend fun complete(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        fun text(m: LlmMessage) = m.content.filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text }
        val vars = mapOf<String, JsonElement?>(
            "model" to JsonPrimitive(request.modelId),
            "system" to JsonPrimitive(request.system.joinToString("\n\n")),
            "prompt" to JsonPrimitive(request.messages.lastOrNull { it.role == Role.USER }?.let(::text).orEmpty()),
            "messages" to JsonArray(request.messages.filter { text(it).isNotBlank() }.map {
                buildJsonObject { put("role", it.role.name.lowercase()); put("content", text(it)) }
            }),
            "max_tokens" to JsonPrimitive(request.maxTokens),
            "temperature" to request.temperature?.let { JsonPrimitive(it) },
        )
        val body = JsonTemplate.render(template, vars).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val http = target.request().post(body).build()
        try {
            client.newCall(http).awaitBody().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw LlmHttpErrors.map(resp.code, raw, who)
                val json = runCatching { Json.parseToJsonElement(raw) }
                    .getOrElse { throw LlmException(LlmException.Kind.PROTOCOL, "El proveedor devolvió una respuesta no válida.") }
                val answer = JsonPaths.string(json, textPath)
                    ?: throw LlmException(LlmException.Kind.PROTOCOL, "No encuentro el texto en la respuesta (ruta «$textPath»). Revisa la ruta de la respuesta.")
                LlmResponse(LlmMessage(Role.ASSISTANT, listOf(ContentBlock.Text(answer))), StopReason.END_TURN, Usage(0, 0))
            }
        } catch (e: IOException) {
            throw LlmException(LlmException.Kind.NETWORK, "No se pudo contactar con $who (${e.javaClass.simpleName}).")
        }
    }
}

/** Resultado de «Probar conexión»: mensaje listo para mostrar y, si lo hay, los modelos descubiertos. */
data class BrainTestResult(
    val ok: Boolean,
    val message: String,
    val models: List<String> = emptyList(),
    /** null = no se pudo comprobar; false = el modelo elegido no figura en la lista del proveedor. */
    val modelListed: Boolean? = null,
)

/** Descubrimiento de modelos (`/models`) y prueba de conexión real contra un perfil. */
object BrainDiscovery {
    suspend fun listModels(profile: BrainProfile, apiKey: () -> String, client: OkHttpClient? = null): List<String> = withContext(Dispatchers.IO) {
        val http = (client ?: BrainFactory.clientFor(profile, streaming = false)).newBuilder().callTimeout(30, TimeUnit.SECONDS).build()
        val target = HttpTarget(profile.endpointUrl(), profile.auth, apiKey, profile.extraHeaders).sibling("models")
        val request = target.request().get().build()
        try {
            http.newCall(request).awaitBody().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw LlmHttpErrors.map(resp.code, raw, profile.name)
                val json = runCatching { Json.parseToJsonElement(raw) }
                    .getOrElse { throw LlmException(LlmException.Kind.PROTOCOL, "La lista de modelos no es JSON válido.") }
                parseModelIds(json)
            }
        } catch (e: IOException) {
            throw LlmException(LlmException.Kind.NETWORK, "No se pudo contactar con ${profile.name} (${e.javaClass.simpleName}).")
        }
    }

    /** `data[].id` (OpenAI, ModelArk, OpenRouter) o `models[]` (cadenas, o `id`/`name` como Gemini nativo). */
    fun parseModelIds(json: JsonElement): List<String> {
        val items = ((json as? JsonObject)?.get("data") ?: (json as? JsonObject)?.get("models") ?: json) as? JsonArray ?: return emptyList()
        return items.mapNotNull { e ->
            when (e) {
                is JsonPrimitive -> e.contentOrNull
                is JsonObject -> (e["id"] ?: e["name"])?.jsonPrimitive?.contentOrNull
                else -> null
            }?.removePrefix("models/")?.trim()?.takeIf { it.isNotEmpty() }
        }.distinct().sorted()
    }

    /**
     * 1) `/models` (si existe) comprueba URL y clave; 2) una petición mínima al modelo elegido comprueba lo que
     * de verdad importa: que ese modelo responde (un modelo puede aparecer en la lista y no estar activado).
     */
    suspend fun test(profile: BrainProfile, apiKey: () -> String, client: OkHttpClient? = null): BrainTestResult {
        profile.problems().firstOrNull()?.let { return BrainTestResult(false, it) }
        var models = emptyList<String>()
        var listed: Boolean? = null
        if (profile.protocol != BrainProtocol.CUSTOM_REST) {
            try {
                models = listModels(profile, apiKey, client)
                if (profile.modelId.isNotBlank() && models.isNotEmpty()) listed = profile.modelId in models
            } catch (e: LlmException) {
                // Sin /models (404/405/formato distinto) no es un error: se sigue con la petición real.
                if (e.kind == LlmException.Kind.AUTH || e.kind == LlmException.Kind.NETWORK)
                    return BrainTestResult(false, e.message.orEmpty())
            }
        }
        if (profile.modelId.isBlank())
            return if (models.isNotEmpty()) BrainTestResult(true, "Conexión correcta: ${models.size} modelos disponibles. Elige uno.", models)
            else BrainTestResult(false, "No pude descubrir modelos; escribe el ID del modelo a mano.")
        return try {
            val provider = BrainFactory.create(profile.copy(capabilities = profile.capabilities.copy(streaming = false)), apiKey,
                client ?: BrainFactory.clientFor(profile, streaming = false).newBuilder().callTimeout(45, TimeUnit.SECONDS).build())
            provider.complete(LlmRequest(profile.modelId, listOf("Responde solo con la palabra OK."),
                listOf(LlmMessage(Role.USER, listOf(ContentBlock.Text("OK?")))), emptyList(), 64))
            val note = when (listed) {
                true -> " El modelo figura en la lista del proveedor."
                false -> " Ojo: el modelo no aparece en la lista del proveedor, aunque ha respondido."
                null -> ""
            }
            BrainTestResult(true, "Conexión correcta: «${profile.modelId}» responde.$note", models, listed)
        } catch (e: LlmException) {
            BrainTestResult(false, e.message.orEmpty(), models, listed)
        }
    }
}
