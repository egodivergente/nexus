package com.vidadomu.nexus.agent.core.llm

import com.vidadomu.nexus.agent.core.config.LimitOverrides
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Protocolo con el que se habla al cerebro. REST personalizado = solo texto, sin herramientas. */
@Serializable
enum class BrainProtocol { OPENAI_CHAT, OPENAI_RESPONSES, CUSTOM_REST }

/** Dónde viaja la clave. La clave en sí nunca está en el perfil: vive cifrada aparte. */
@Serializable
enum class BrainAuthMode { BEARER, HEADER, QUERY, NONE }

@Serializable
data class BrainAuth(
    val mode: BrainAuthMode = BrainAuthMode.BEARER,
    /** Nombre de la cabecera (HEADER, p. ej. «x-api-key») o del parámetro (QUERY, p. ej. «key»). */
    val name: String = "",
    /** Texto delante de la clave en la cabecera (HEADER), p. ej. «Key » para `Authorization: Key <clave>`. */
    val prefix: String = "",
)

@Serializable
data class BrainCapabilities(
    val vision: Boolean = false,
    val reasoning: Boolean = false,
    val streaming: Boolean = false,
    val tools: Boolean = true,
    val jsonSchema: Boolean = false,
)

/**
 * Un «cerebro» (LLM) del agente definido por configuración: BytePlus ModelArk o cualquier proveedor que
 * hable Chat Completions, Responses o un REST de solo texto. No contiene secretos.
 */
@Serializable
data class BrainProfile(
    val id: String,
    val name: String,
    val protocol: BrainProtocol = BrainProtocol.OPENAI_CHAT,
    val baseUrl: String,
    /** Ruta si no es la habitual (`/chat/completions`, `/responses`). Vacío = la del protocolo. */
    val endpointPath: String = "",
    val auth: BrainAuth = BrainAuth(),
    val modelId: String = "",
    /** Modelos conocidos (descubiertos o escritos); solo sirven para el selector. */
    val models: List<String> = emptyList(),
    val capabilities: BrainCapabilities = BrainCapabilities(),
    /** minimal | low | medium | high. */
    val reasoningEffort: String? = null,
    /** enabled | disabled | auto. */
    val thinking: String? = null,
    val temperature: Double? = null,
    /** Objeto JSON que se funde en la petición (parámetros avanzados). No puede pisar model/input/messages/tools/stream. */
    val extraBody: String = "",
    val extraHeaders: Map<String, String> = emptyMap(),
    val inputUsdPerM: Double? = null,
    val outputUsdPerM: Double? = null,
    /**
     * Límites propios de este cerebro (pasos, tiempos, tokens, gasto, llamadas, HTTP). Lo que no se define
     * hereda el valor global de Ajustes → Límites; 0 = sin límite donde aplica.
     */
    val limits: LimitOverrides = LimitOverrides(),
    /** Responses API: `store` (null = no se envía y manda el valor por defecto del servidor). */
    val storeOnServer: Boolean? = null,
    /** REST personalizado: plantilla de la petición con {{model}}, {{system}}, {{prompt}}, {{messages}}. */
    val restRequestTemplate: String = "",
    /** REST personalizado: ruta del texto en la respuesta, p. ej. `choices.0.message.content`. */
    val restTextPath: String = "",
    /** Preconfigurados de Nexus (BytePlus): no se pueden borrar, sí editar su modelo y clave. */
    val builtin: Boolean = false,
) {
    val vision get() = capabilities.vision

    /** Dirección final de la petición. */
    fun endpointUrl(): String {
        val base = baseUrl.trim().trimEnd('/')
        val path = endpointPath.trim().ifBlank {
            when (protocol) {
                BrainProtocol.OPENAI_CHAT -> "/chat/completions"
                BrainProtocol.OPENAI_RESPONSES -> "/responses"
                BrainProtocol.CUSTOM_REST -> ""
            }
        }
        if (path.isBlank()) return base
        val normalized = if (path.startsWith("/")) path else "/$path"
        return if (base.endsWith(normalized)) base else base + normalized
    }

    /** Problemas que impiden guardar; vacío = válido. Mensajes para el usuario. */
    fun problems(): List<String> = buildList {
        if (!ID.matches(id)) add("El identificador solo admite minúsculas, números y guiones (2–40).")
        if (name.isBlank() || name.length > 60) add("Escribe un nombre (hasta 60 caracteres).")
        if (!isAllowedUrl(baseUrl)) add("La URL base debe empezar por https:// (o http://127.0.0.1 / localhost para un servidor local).")
        if (endpointPath.isNotBlank() && ("://" in endpointPath || ".." in endpointPath || endpointPath.any { it.isWhitespace() }))
            add("El endpoint debe ser una ruta relativa, sin esquema ni espacios.")
        if (auth.mode in setOf(BrainAuthMode.HEADER, BrainAuthMode.QUERY) && !TOKEN.matches(auth.name)) add("Indica el nombre de la cabecera o del parámetro de la clave.")
        if (auth.prefix.any { it == '\r' || it == '\n' }) add("El prefijo de la clave no puede tener saltos de línea.")
        if (reasoningEffort != null && reasoningEffort !in EFFORTS) add("El esfuerzo de razonamiento debe ser minimal, low, medium o high.")
        if (thinking != null && thinking !in THINKING) add("El modo de pensamiento debe ser enabled, disabled o auto.")
        if (thinking == "disabled" && reasoningEffort != null) add("No se puede fijar esfuerzo de razonamiento con el pensamiento desactivado.")
        // Solo cordura: la API del proveedor decide qué temperatura o cuántos tokens admite, no Nexus.
        if (temperature != null && !(temperature.isFinite() && temperature >= 0.0)) add("La temperatura debe ser un número ≥ 0.")
        addAll(limits.problems())
        if ((inputUsdPerM == null) != (outputUsdPerM == null)) add("Indica la tarifa de entrada y la de salida, o ninguna.")
        listOfNotNull(inputUsdPerM, outputUsdPerM).forEach { if (!(it.isFinite() && it >= 0.0)) add("Tarifa no válida (USD por millón de tokens, ≥ 0).") }
        extraHeaders.forEach { (k, v) ->
            if (!TOKEN.matches(k)) add("Cabecera «$k» no válida.")
            else if (k.equals("authorization", true) && auth.mode == BrainAuthMode.BEARER) add("La cabecera Authorization la gestiona la clave de API.")
            if (v.any { it == '\r' || it == '\n' }) add("El valor de la cabecera «$k» no puede tener saltos de línea.")
        }
        if (extraBody.isNotBlank()) {
            val obj = runCatching { Json.parseToJsonElement(extraBody).jsonObject }.getOrNull()
            if (obj == null) add("Los parámetros avanzados deben ser un objeto JSON válido.")
            else obj.keys.filter { it in RESERVED_BODY_KEYS }.forEach { add("Los parámetros avanzados no pueden definir «$it».") }
        }
        if (protocol == BrainProtocol.CUSTOM_REST) {
            if (runCatching { Json.parseToJsonElement(restRequestTemplate) }.isFailure) add("La plantilla de la petición debe ser JSON válido.")
            if (restTextPath.isBlank()) add("Indica la ruta del texto en la respuesta (p. ej. choices.0.message.content).")
        }
    }

    /** Modelo, URL y clave: lo mínimo para llamar. Sin clave solo se admite NONE o un servidor local. */
    fun usable(hasKey: Boolean): Boolean = problems().isEmpty() && modelId.isNotBlank() &&
        (hasKey || auth.mode == BrainAuthMode.NONE || LOCAL.matches(baseUrl.trim()))

    fun parsedExtraBody(): JsonObject? = extraBody.takeIf { it.isNotBlank() }?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }

    companion object {
        val ID = Regex("^[a-z0-9][a-z0-9-]{1,39}$")
        private val TOKEN = Regex("^[A-Za-z0-9][A-Za-z0-9_\\-]{0,63}$")
        private val LOCAL = Regex("^http://(127\\.0\\.0\\.1|localhost)(:\\d+)?(/.*)?$")
        val EFFORTS = setOf("minimal", "low", "medium", "high")
        val THINKING = setOf("enabled", "disabled", "auto")
        val RESERVED_BODY_KEYS = setOf("model", "input", "messages", "tools", "stream", "instructions", "tool_choice")

        fun isAllowedUrl(url: String): Boolean {
            val u = url.trim()
            if (LOCAL.matches(u)) return true
            if (!u.startsWith("https://") || u.any { it.isWhitespace() }) return false
            // Sin credenciales incrustadas (https://usuario:clave@host) y con un host con punto.
            val authority = u.removePrefix("https://").substringBefore('/').substringBefore('?')
            return authority.isNotEmpty() && '@' !in authority && '.' in authority
        }

        /** Texto de [name] a un identificador libre; el llamante añade sufijo si ya existe. */
        fun slug(name: String): String =
            name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(32).ifBlank { "cerebro" }.let { if (it.length < 2) "$it-1" else it }
    }
}

/** Preconfigurados y plantillas. BytePlus es un cerebro más, listo para poner la clave. */
object BrainPresets {
    const val BYTEPLUS_ID = "byteplus"
    const val BYTEPLUS_BASE_URL = "https://ark.ap-southeast.bytepluses.com/api/v3"
    const val BYTEPLUS_MODEL = "dola-seed-2-1-turbo-260628"

    fun byteplus() = BrainProfile(
        id = BYTEPLUS_ID, name = "BytePlus ModelArk", protocol = BrainProtocol.OPENAI_RESPONSES,
        baseUrl = BYTEPLUS_BASE_URL, modelId = BYTEPLUS_MODEL, models = listOf(BYTEPLUS_MODEL),
        capabilities = BrainCapabilities(vision = true, reasoning = true, streaming = true, tools = true, jsonSchema = true),
        builtin = true,
    )

    /** Plantillas de la pantalla «Añadir proveedor personalizado». */
    data class Template(val label: String, val profile: BrainProfile)

    val templates: List<Template> = listOf(
        Template("OpenAI", BrainProfile("openai", "OpenAI", BrainProtocol.OPENAI_CHAT, "https://api.openai.com/v1",
            capabilities = BrainCapabilities(vision = true, reasoning = true, streaming = true, tools = true, jsonSchema = true))),
        Template("OpenRouter", BrainProfile("openrouter", "OpenRouter", BrainProtocol.OPENAI_CHAT, "https://openrouter.ai/api/v1",
            capabilities = BrainCapabilities(vision = true, streaming = true, tools = true))),
        Template("Gemini (OpenAI)", BrainProfile("gemini", "Gemini", BrainProtocol.OPENAI_CHAT, "https://generativelanguage.googleapis.com/v1beta/openai",
            capabilities = BrainCapabilities(vision = true, reasoning = true, streaming = true, tools = true))),
        Template("DeepSeek", BrainProfile("deepseek", "DeepSeek", BrainProtocol.OPENAI_CHAT, "https://api.deepseek.com/v1",
            capabilities = BrainCapabilities(reasoning = true, streaming = true, tools = true))),
        Template("Groq", BrainProfile("groq", "Groq", BrainProtocol.OPENAI_CHAT, "https://api.groq.com/openai/v1",
            capabilities = BrainCapabilities(streaming = true, tools = true))),
        Template("Mistral", BrainProfile("mistral", "Mistral", BrainProtocol.OPENAI_CHAT, "https://api.mistral.ai/v1",
            capabilities = BrainCapabilities(vision = true, streaming = true, tools = true))),
        Template("Servidor local", BrainProfile("local", "Servidor local", BrainProtocol.OPENAI_CHAT, "http://127.0.0.1:11434/v1",
            auth = BrainAuth(BrainAuthMode.NONE), capabilities = BrainCapabilities(streaming = true, tools = true))),
        Template("Responses API", BrainProfile("responses", "Responses API", BrainProtocol.OPENAI_RESPONSES, "https://api.openai.com/v1",
            capabilities = BrainCapabilities(vision = true, reasoning = true, streaming = true, tools = true))),
        Template("REST personalizado (solo texto)", BrainProfile("rest", "REST personalizado", BrainProtocol.CUSTOM_REST, "https://",
            capabilities = BrainCapabilities(tools = false),
            restRequestTemplate = """{"model":"{{model}}","prompt":"{{prompt}}"}""", restTextPath = "text")),
    )
}
