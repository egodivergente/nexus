package com.vidadomu.nexus.assistant

import kotlinx.coroutines.sync.withLock
import android.content.Context
import android.os.Environment
import android.util.Log
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.agent.AttachmentImageLoader
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.AssetRole
import com.vidadomu.nexus.agent.core.assets.AssetSource
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.assets.GenerationRecord
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentLimits
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.BrainFactory
import com.vidadomu.nexus.agent.core.llm.BrainProfile
import com.vidadomu.nexus.agent.core.cost.ChainedCostEstimator
import com.vidadomu.nexus.agent.core.cost.PublishedRateEstimator
import com.vidadomu.nexus.agent.core.llm.AntigravityProvider
import com.vidadomu.nexus.agent.core.llm.BedrockProxyProvider
import com.vidadomu.nexus.agent.core.llm.BedrockDirectProvider
import com.vidadomu.nexus.agent.core.llm.AwsCredentials
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.agent.core.llm.LlmProvider
import com.vidadomu.nexus.agent.core.llm.OfflinePlannerProvider
import com.vidadomu.nexus.agent.core.log.AgentLogger
import com.vidadomu.nexus.agent.core.log.RedactingLogger
import com.vidadomu.nexus.agent.core.router.ModelRouter
import com.vidadomu.nexus.agent.core.skills.DirectorySkillSource
import com.vidadomu.nexus.agent.core.skills.SkillStore
import com.vidadomu.nexus.agent.core.spend.GuardedExecutor
import com.vidadomu.nexus.agent.core.spend.SpendGuard
import com.vidadomu.nexus.agent.core.tools.NexusToolkit
import com.vidadomu.nexus.agent.core.tools.ToolDeps
import com.vidadomu.nexus.agent.core.tools.turnContext
import com.vidadomu.nexus.core.model.GenerationStatus
import java.io.File

/**
 * Configuración del asistente. Proveedores disponibles:
 *  - «direct» (por defecto, decisión del usuario 02-10): la app firma con SigV4 y llama a Bedrock con sus claves de AWS,
 *    guardadas cifradas en SecureStorage.
 *  - «anthropic»: API oficial de Claude (Anthropic).
 *  - «openai»: cualquier endpoint compatible con OpenAI.
 *  - «proxy»: APK → proxy → Bedrock con un token de dispositivo.
 *  - «antigravity»: puente local AXEL/Termux hacia Antigravity CLI (`127.0.0.1:8788`).
 * Sin credenciales del backend elegido se usa el planificador local (sin LLM).
 */
data class AssistantSettings(
    val backend: String = BACKEND_DIRECT,
    val endpoint: String = "",
    val modelId: String = DEFAULT_MODEL,
    val region: String = DEFAULT_REGION,
    val temperature: Double? = null,
    val maxTokens: Int = 4096,
    val mode: AgentMode = AgentMode.AGENT,
    val sendImages: Boolean = true,
    val maskedToken: String = "",
    val awsAccessKeyId: String = "",
    val maskedAwsSecret: String = "",
    /** Familias que no se recomiendan, separadas por comas. Por defecto «veo»: el usuario lo tiene gratis en otro sitio. */
    val excludedFamilies: String = DEFAULT_EXCLUDED,
    /** URL base de la API compatible con OpenAI (OpenAI, OpenRouter, Gemini, DeepSeek, Groq, Mistral, local…). */
    val llmBaseUrl: String = "",
    /** Clave de API del proveedor elegido (Anthropic u OpenAI-compatible), enmascarada. */
    val maskedApiKey: String = "",
    /** USD por millón de tokens de entrada/salida para modelos sin tarifa conocida (opcional). */
    val inputUsdPerM: Double? = null,
    val outputUsdPerM: Double? = null,
    /** Endpoint del puente local de Antigravity CLI (por defecto http://127.0.0.1:8788). */
    val antigravityEndpoint: String = DEFAULT_ANTIGRAVITY_ENDPOINT,
    /** Cerebro personalizado activo (backend «custom:<id>»): BytePlus o uno añadido por el usuario. */
    val brain: BrainProfile? = null,
    val brainHasKey: Boolean = false,
    /** Límites globales del usuario (Ajustes → Límites). El cerebro activo puede sustituir algunos. */
    val limits: AgentLimits = AgentLimits(),
) {
    val excludedSet: Set<String> get() = parseFamilies(excludedFamilies)
    val direct: Boolean get() = backend == BACKEND_DIRECT
    val antigravity: Boolean get() = backend == BACKEND_ANTIGRAVITY
    val isCustom: Boolean get() = backend.startsWith(BrainStore.PREFIX)
    /** Límites efectivos: los globales con lo que defina el cerebro activo por encima. */
    val effectiveLimits: AgentLimits get() = brain?.let { limits.with(it.limits) } ?: limits
    /** Imágenes y vídeo solo van al LLM si el ajuste está activo y, en un cerebro propio, declara visión. */
    val visionEnabled: Boolean get() = sendImages && !offline && (brain?.vision ?: true)

    /** Sin credenciales del backend elegido se usa el planificador local (sin LLM). */
    val offline: Boolean get() = if (isCustom) brain?.usable(brainHasKey) != true else modelId.isBlank() || when (backend) {
        BACKEND_PROXY -> endpoint.isBlank() || maskedToken.isBlank()
        BACKEND_ANTHROPIC -> maskedApiKey.isBlank()
        // Un servidor local puede no pedir clave; uno remoto sí.
        BACKEND_OPENAI -> llmBaseUrl.isBlank() || (maskedApiKey.isBlank() && !LOCAL_URL.matches(llmBaseUrl.trim()))
        BACKEND_ANTIGRAVITY -> antigravityEndpoint.isBlank()
        else -> awsAccessKeyId.isBlank() || maskedAwsSecret.isBlank() || region.isBlank()
    }

    fun toConfig() = AgentConfig(
        provider = when {
            offline -> "offline"
            isCustom -> "custom"
            backend == BACKEND_PROXY -> "bedrock-proxy"
            backend == BACKEND_ANTHROPIC -> "anthropic"
            backend == BACKEND_OPENAI -> "openai-compatible"
            backend == BACKEND_ANTIGRAVITY -> "antigravity"
            else -> "bedrock-direct"
        },
        modelId = modelId.ifBlank { "offline" }, region = region,
        endpoint = if (backend == BACKEND_ANTIGRAVITY) antigravityEndpoint else endpoint,
        temperature = brain?.temperature ?: temperature, maxTokens = effectiveLimits.maxTokens, maxToolRounds = effectiveLimits.rounds,
        mode = AgentMode.AGENT, sendImagesToLlm = visionEnabled, limits = effectiveLimits,
    )

    companion object {
        const val BACKEND_DIRECT = "direct"
        const val BACKEND_PROXY = "proxy"
        const val BACKEND_ANTHROPIC = "anthropic"
        const val BACKEND_OPENAI = "openai"
        const val BACKEND_ANTIGRAVITY = "antigravity"
        const val DEFAULT_ANTIGRAVITY_ENDPOINT = AntigravityProvider.DEFAULT_ENDPOINT
        const val DEFAULT_ANTIGRAVITY_MODEL = AntigravityProvider.DEFAULT_MODEL
        val LOCAL_URL = Regex("^http://(127\\.0\\.0\\.1|localhost)(:\\d+)?(/.*)?$")
        /** Modelos de Claude para la API de Anthropic (ids oficiales). */
        val ANTHROPIC_MODELS = listOf("claude-sonnet-5-5" to "Sonnet 5.5", "claude-opus-5-5" to "Opus 5.5", "claude-haiku-5-5" to "Haiku 5.5")
        /** Modelos expuestos por el puente de Antigravity CLI (`/root/axel-native-v2/bridge/server.ts`). */
        val ANTIGRAVITY_MODELS = listOf(
            com.vidadomu.nexus.agent.core.llm.AntigravityReasoning.DEFAULT_MODEL to "Gemini 4 Argon",
            "gemini-3.8-flash" to "Gemini 3.8 Flash",
            "gemini-3.1-pro" to "Gemini 3.1 Pro",
            "gemini-3.7-flash" to "Gemini 3.7 Flash",
            "gemini-3.6-flash" to "Gemini 3.6 Flash",
            "gpt-oss-120b" to "GPT-OSS 120B",
            "claude-sonnet-4-6" to "Claude Sonnet 4.6",
            "claude-opus-4-6-thinking" to "Opus 4.6 Thinking",
            "gemini-fast" to "Auto (Rápido)",
        )
        /** Puntos de acceso compatibles con OpenAI más habituales; el modelo lo escribe el usuario. */
        val OPENAI_PRESETS = listOf(
            "OpenAI" to "https://api.openai.com/v1",
            "OpenRouter" to "https://openrouter.ai/api/v1",
            "Gemini" to "https://generativelanguage.googleapis.com/v1beta/openai",
            "DeepSeek" to "https://api.deepseek.com/v1",
            "Groq" to "https://api.groq.com/openai/v1",
            "Mistral" to "https://api.mistral.ai/v1",
        )
        const val DEFAULT_MODEL = "us.anthropic.claude-sonnet-4-6"
        const val DEFAULT_REGION = "us-east-1"
        const val DEFAULT_EXCLUDED = "veo"
        /** Official Bedrock profiles; account availability is separate from catalog support. */
        val KNOWN_MODELS = listOf(
            "us.anthropic.claude-sonnet-5-5" to "Sonnet 5.5",
            "us.anthropic.claude-opus-5-5" to "Opus 5.5",
            "us.anthropic.claude-sonnet-4-6" to "Sonnet 4.6",
            "us.anthropic.claude-opus-4-6-v1" to "Opus 4.6",
            "us.anthropic.claude-haiku-4-5-20251001-v1:0" to "Haiku 4.5",
        )
        fun parseFamilies(raw: String): Set<String> =
            raw.split(',', ';', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    }
}

class AssistantSettingsStore(private val app: NexusApplication) {
    private val prefs = app.getSharedPreferences("assistant_config_v1", Context.MODE_PRIVATE)
    private val secure get() = app.secureStorage
    /** Cerebros personalizados y BytePlus. */
    val brains = BrainStore(app)

    private val limitsJson = kotlinx.serialization.json.Json { encodeDefaults=true; ignoreUnknownKeys=true }
    private val legacyLimitKeys = setOf("maxToolRounds","turnTimeoutSeconds","maxTokens","flowMaxResults","maxImagesPerTurn","maxVideosPerTurn","maxDocumentsPerTurn","maxMessages","maxToolResultChars","maxUiContextChars","jobTrackingMinutes")
    val limitsState by lazy { kotlinx.coroutines.flow.MutableStateFlow(limits()) }

    /** Existing preferences remain authoritative; new controls share an extensible JSON record. */
    fun limits(): AgentLimits {
        val d=AgentLimits()
        val base=AgentLimits(
            maxToolRounds=prefs.getInt("lim_rounds",d.maxToolRounds),turnTimeoutSeconds=prefs.getInt("lim_turn_timeout_s",d.turnTimeoutSeconds),
            maxTokens=prefs.getInt("max_tokens",d.maxTokens).coerceAtLeast(1),flowMaxResults=prefs.getInt("lim_flow_results",d.flowMaxResults),
            maxImagesPerTurn=prefs.getInt("lim_images",d.maxImagesPerTurn),maxVideosPerTurn=prefs.getInt("lim_videos",d.maxVideosPerTurn),
            maxDocumentsPerTurn=prefs.getInt("lim_documents",d.maxDocumentsPerTurn),maxMessages=prefs.getInt("lim_messages",d.maxMessages),
            maxToolResultChars=prefs.getInt("lim_tool_chars",d.maxToolResultChars),maxUiContextChars=prefs.getInt("lim_ui_chars",d.maxUiContextChars),
            jobTrackingMinutes=prefs.getInt("lim_tracking_min",d.jobTrackingMinutes))
        return runCatching {
            val extras=prefs.getString("lim_extra_json",null)?.let {limitsJson.parseToJsonElement(it) as kotlinx.serialization.json.JsonObject}
            if(extras==null) base else {
                val original=limitsJson.encodeToJsonElement(AgentLimits.serializer(),base) as kotlinx.serialization.json.JsonObject
                limitsJson.decodeFromJsonElement(AgentLimits.serializer(),kotlinx.serialization.json.JsonObject(original+extras.filterKeys {it !in legacyLimitKeys}))
            }
        }.getOrDefault(base)
    }

    fun saveLimits(l: AgentLimits) {
        l.problems().firstOrNull()?.let {throw IllegalArgumentException(it)}
        val values=limitsJson.encodeToJsonElement(AgentLimits.serializer(),l) as kotlinx.serialization.json.JsonObject
        val extras=kotlinx.serialization.json.JsonObject(values.filterKeys {it !in legacyLimitKeys})
        check(prefs.edit().putInt("lim_rounds",l.maxToolRounds).putInt("lim_turn_timeout_s",l.turnTimeoutSeconds).putInt("max_tokens",l.maxTokens)
            .putInt("lim_flow_results",l.flowMaxResults).putInt("lim_images",l.maxImagesPerTurn).putInt("lim_videos",l.maxVideosPerTurn)
            .putInt("lim_documents",l.maxDocumentsPerTurn).putInt("lim_messages",l.maxMessages).putInt("lim_tool_chars",l.maxToolResultChars)
            .putInt("lim_ui_chars",l.maxUiContextChars).putInt("lim_tracking_min",l.jobTrackingMinutes).putString("lim_extra_json",extras.toString()).commit()) {"No se pudieron guardar los límites."}
        com.vidadomu.nexus.core.model.TrackingPolicy.windowMinutes=l.jobTrackingMinutes
        limitsState.value=l
    }

    fun effectiveLimits(s: AssistantSettings): AgentLimits = s.effectiveLimits

    /**
     * Límites de gasto del cerebro [p]: lo que él defina manda; lo demás, los globales (que pueden estar desactivados).
     * 0 = sin límite.
     */
    fun brainLlmLimits(p: BrainProfile): com.vidadomu.nexus.agent.core.llm.LlmLimits {
        val base = llmLimits()
        fun usd(v: Double?, fallback: Double) = if (v == null) fallback else if (v <= 0.0) Double.MAX_VALUE else v
        return base.copy(
            perTurnUsd = usd(p.limits.turnUsd, base.perTurnUsd),
            dailyUsd = usd(p.limits.dailyUsd, base.dailyUsd),
            perTurnCalls = p.limits.maxCallsPerTurn?.let { if (it <= 0) Int.MAX_VALUE else it } ?: base.perTurnCalls,
        )
    }

    init { seedFromBuild() }

    /** Primera ejecución o migración de build con claves de Bedrock (fichero local fuera del repo). */
    private fun seedFromBuild() = runCatching {
        if (BuildConfig.BEDROCK_ACCESS_KEY.isNotBlank() && (!secure.hasProviderApiKey(AWS_SECRET_ID) || !secure.hasProviderApiKey(AWS_ACCESS_ID) || !prefs.getBoolean("bedrock_direct_seeded_v2", false))) {
            secure.saveProviderApiKey(AWS_ACCESS_ID, BuildConfig.BEDROCK_ACCESS_KEY)
            secure.saveProviderApiKey(AWS_SECRET_ID, BuildConfig.BEDROCK_SECRET_KEY)
            val seededModel = BuildConfig.BEDROCK_MODEL.ifBlank { AssistantSettings.DEFAULT_MODEL }
            prefs.edit().putString("backend", AssistantSettings.BACKEND_DIRECT)
                .putString("region", BuildConfig.BEDROCK_REGION.ifBlank { AssistantSettings.DEFAULT_REGION })
                .putString("model_id", seededModel)
                .putString("model_id_direct", seededModel)
                .putBoolean("bedrock_direct_seeded_v2", true).apply()
        }
    }

    fun load(): AssistantSettings {
        val backend = prefs.getString("backend", AssistantSettings.BACKEND_DIRECT).orEmpty().ifBlank { AssistantSettings.BACKEND_DIRECT }
        // Un cerebro personalizado que ya no existe no deja la app sin respuesta: vuelve al predeterminado.
        val brain = if (backend.startsWith(BrainStore.PREFIX)) brains.get(backend.removePrefix(BrainStore.PREFIX)) else null
        val effectiveBackend = if (backend.startsWith(BrainStore.PREFIX) && brain == null) AssistantSettings.BACKEND_DIRECT else backend
        return loadFor(effectiveBackend, brain)
    }

    private fun loadFor(backend: String, brain: BrainProfile?) = AssistantSettings(
        backend = backend,
        brain = brain, brainHasKey = brain?.let { brains.hasKey(it.id) } ?: false, limits = limits(),
        endpoint = prefs.getString("endpoint", "").orEmpty(),
        modelId = brain?.modelId ?: prefs.getString("model_id", AssistantSettings.DEFAULT_MODEL).orEmpty(),
        region = prefs.getString("region", AssistantSettings.DEFAULT_REGION).orEmpty(),
        temperature = prefs.getString("temperature", null)?.toDoubleOrNull(),
        maxTokens = prefs.getInt("max_tokens", 4096),
        mode = AgentMode.AGENT,
        sendImages = prefs.getBoolean("send_images", true),
        maskedToken = masked(TOKEN_ID),
        awsAccessKeyId = runCatching { secure.getProviderApiKey(AWS_ACCESS_ID) }.getOrDefault(""),
        maskedAwsSecret = masked(AWS_SECRET_ID),
        excludedFamilies = prefs.getString("excluded_families", AssistantSettings.DEFAULT_EXCLUDED).orEmpty(),
        llmBaseUrl = prefs.getString("llm_base_url", "").orEmpty(),
        maskedApiKey = apiKeyId(prefs.getString("backend", AssistantSettings.BACKEND_DIRECT).orEmpty())?.let(::masked).orEmpty(),
        inputUsdPerM = prefs.getString("llm_input_usd_m", null)?.toDoubleOrNull(),
        outputUsdPerM = prefs.getString("llm_output_usd_m", null)?.toDoubleOrNull(),
        antigravityEndpoint = prefs.getString("antigravity_endpoint", AssistantSettings.DEFAULT_ANTIGRAVITY_ENDPOINT)
            .orEmpty().ifBlank { AssistantSettings.DEFAULT_ANTIGRAVITY_ENDPOINT },
    )

    /** Devuelve el último modelo guardado para [backend] sin perder la configuración de los demás proveedores. */
    fun savedModelFor(backend: String): String = when (backend) {
        AssistantSettings.BACKEND_ANTIGRAVITY ->
            prefs.getString("model_id_antigravity", AssistantSettings.DEFAULT_ANTIGRAVITY_MODEL).orEmpty().ifBlank { AssistantSettings.DEFAULT_ANTIGRAVITY_MODEL }
        AssistantSettings.BACKEND_ANTHROPIC ->
            prefs.getString("model_id_anthropic", AssistantSettings.ANTHROPIC_MODELS.first().first).orEmpty().ifBlank { AssistantSettings.ANTHROPIC_MODELS.first().first }
        AssistantSettings.BACKEND_OPENAI ->
            prefs.getString("model_id_openai", "").orEmpty()
        AssistantSettings.BACKEND_PROXY ->
            prefs.getString("model_id_proxy", AssistantSettings.DEFAULT_MODEL).orEmpty().ifBlank { AssistantSettings.DEFAULT_MODEL }
        else ->
            prefs.getString("model_id_direct", AssistantSettings.DEFAULT_MODEL).orEmpty().ifBlank { AssistantSettings.DEFAULT_MODEL }
    }

    /** Cada proveedor guarda su propia clave cifrada: cambiar de uno a otro no borra la del anterior. */
    private fun apiKeyId(backend: String): String? = when (backend) {
        AssistantSettings.BACKEND_ANTHROPIC -> ANTHROPIC_KEY_ID
        AssistantSettings.BACKEND_OPENAI -> OPENAI_KEY_ID
        else -> null
    }

    fun apiKey(backend: String): String = apiKeyId(backend)?.let { runCatching { secure.getProviderApiKey(it) }.getOrDefault("") }.orEmpty()

    /** Máscara de la clave guardada para [backend] (la UI la muestra al cambiar de proveedor). */
    fun maskedApiKey(backend: String): String = apiKeyId(backend)?.let(::masked).orEmpty()

    fun customRates(): Pair<Double, Double>? {
        val s = load()
        val input = s.inputUsdPerM ?: return null
        val output = s.outputUsdPerM ?: return null
        return input to output
    }

    /** Proyecto activo, persistido entre sesiones (lo generado mientras está activo se etiqueta con él). */
    fun activeProjectId(): String? = prefs.getString("active_project", null)?.takeIf { it.isNotBlank() }
    fun setActiveProject(id: String?) = prefs.edit().putString("active_project", id.orEmpty()).apply()

    private fun masked(id: String) = runCatching { secure.getMaskedProviderApiKey(id).takeIf { secure.hasProviderApiKey(id) }.orEmpty() }.getOrDefault("")

    /** [newToken] / [newAwsSecret]: null = no tocar; "" = borrar. */
    fun save(s: AssistantSettings, newToken: String?, newAwsSecret: String? = null, newApiKey: String? = null) {
        val endpoint = s.endpoint.trim().trimEnd('/')
        require(endpoint.isBlank() || endpoint.startsWith("https://") || LOCAL.matches(endpoint)) {
            "El proxy debe usar https:// (o http://127.0.0.1 con adb reverse en depuración)."
        }
        val baseUrl = s.llmBaseUrl.trim().trimEnd('/')
        require(baseUrl.isBlank() || baseUrl.startsWith("https://") || LOCAL.matches(baseUrl)) {
            "La URL de la API debe usar https:// (o http://127.0.0.1 para un servidor local)."
        }
        val agyEndpoint = s.antigravityEndpoint.trim().trimEnd('/').ifBlank { AssistantSettings.DEFAULT_ANTIGRAVITY_ENDPOINT }
        require(AntigravityProvider.isAllowedEndpoint(agyEndpoint)) {
            "El puente Antigravity debe usar http://127.0.0.1:8788, una IP de Tailscale (100.x.y.z) o https://."
        }
        require((s.inputUsdPerM == null) == (s.outputUsdPerM == null)) { "Indica la tarifa de entrada y la de salida, o ninguna." }
        listOfNotNull(s.inputUsdPerM, s.outputUsdPerM).forEach { require(it.isFinite() && it in 0.0..1000.0) { "Tarifa no válida (USD por millón de tokens, 0–1000)." } }
        val edit = prefs.edit().putString("llm_base_url", baseUrl).putString("antigravity_endpoint", agyEndpoint)
        if (s.inputUsdPerM != null && s.outputUsdPerM != null) edit.putString("llm_input_usd_m", s.inputUsdPerM.toString()).putString("llm_output_usd_m", s.outputUsdPerM.toString())
        else edit.remove("llm_input_usd_m").remove("llm_output_usd_m")
        edit.apply()
        apiKeyId(s.backend)?.let { id ->
            when {
                newApiKey == null -> Unit
                newApiKey.isBlank() -> secure.clearProviderApiKey(id)
                else -> secure.saveProviderApiKey(id, newApiKey.trim())
            }
        }
        require(s.region.isBlank() || REGION.matches(s.region.trim())) { "Región de AWS no válida (p. ej. us-east-1)." }
        val trimmedModel = s.modelId.trim()
        prefs.edit().putString("backend", s.backend).putString("endpoint", endpoint).putString("model_id", trimmedModel)
            .putString("model_id_${s.backend}", trimmedModel)
            .putString("region", s.region.trim())
            .putString("temperature", s.temperature?.toString()).putInt("max_tokens", s.maxTokens.coerceAtLeast(1))
            .putString("mode", s.mode.name).putBoolean("send_images", s.sendImages)
            .putString("excluded_families", AssistantSettings.parseFamilies(s.excludedFamilies).joinToString(", ")).apply()
        val access = s.awsAccessKeyId.trim()
        if (access.isBlank()) secure.clearProviderApiKey(AWS_ACCESS_ID) else secure.saveProviderApiKey(AWS_ACCESS_ID, access)
        when {
            newToken == null -> Unit
            newToken.isBlank() -> secure.clearProviderApiKey(TOKEN_ID)
            else -> secure.saveProviderApiKey(TOKEN_ID, newToken.trim())
        }
        when {
            newAwsSecret == null -> Unit
            newAwsSecret.isBlank() -> secure.clearProviderApiKey(AWS_SECRET_ID)
            else -> secure.saveProviderApiKey(AWS_SECRET_ID, newAwsSecret.trim())
        }
    }

    /** Whitelisted settings only: never rewrites SecureStorage credentials. */
    fun updateOperational(model: String?, tokens: Int?, images: Boolean?, excluded: String?) {
        val edit = prefs.edit()
        model?.let {
            require(
                AssistantSettings.KNOWN_MODELS.any { pair -> pair.first == it } ||
                    AssistantSettings.ANTHROPIC_MODELS.any { pair -> pair.first == it } ||
                    AssistantSettings.ANTIGRAVITY_MODELS.any { pair -> pair.first == it }
            )
            edit.putString("model_id", it)
        }
        tokens?.let { require(it > 0); edit.putInt("max_tokens", it) }
        images?.let { edit.putBoolean("send_images", it) }
        excluded?.let { edit.putString("excluded_families", AssistantSettings.parseFamilies(it).joinToString(", ")) }
        check(edit.commit()) { "No se pudieron guardar los ajustes." }
    }

    fun setMode(mode: AgentMode) = prefs.edit().putString("mode", mode.name).apply()

    fun excludedFamilies(): Set<String> =
        AssistantSettings.parseFamilies(prefs.getString("excluded_families", AssistantSettings.DEFAULT_EXCLUDED).orEmpty())

    fun budgetEnabled() = prefs.getBoolean("llm_budget_enabled",false)
    fun setBudgetEnabled(enabled:Boolean) {prefs.edit().putBoolean("llm_budget_enabled",enabled).apply()}
    fun configuredLlmLimits(): com.vidadomu.nexus.agent.core.llm.LlmLimits = com.vidadomu.nexus.agent.core.llm.LlmLimits(
        perTurnCalls = Int.MAX_VALUE,
        perTurnUsd = prefs.getString("llm_turn_usd", "1.0")?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.let { if (it == 0.0) Double.MAX_VALUE else it } ?: 1.0,
        dailyUsd = prefs.getString("llm_daily_usd", "5.0")?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.let { if (it == 0.0) Double.MAX_VALUE else it } ?: 5.0)
    fun llmLimits() = configuredLlmLimits().let { if(budgetEnabled()) it else it.copy(perTurnUsd=Double.MAX_VALUE,dailyUsd=Double.MAX_VALUE) }
    fun saveLlmLimits(turn: Double, daily: Double) {
        // Solo se rechazan negativos y no numéricos: 0 = sin límite y cualquier importe positivo es decisión del usuario.
        require(turn.isFinite() && daily.isFinite() && turn >= 0.0 && daily >= 0.0)
        check(prefs.edit().putString("llm_turn_usd", turn.toString()).putString("llm_daily_usd", daily.toString()).commit())
    }

    fun token(): String = secure.getProviderApiKey(TOKEN_ID)

    fun awsCredentials() = AwsCredentials(secure.getProviderApiKey(AWS_ACCESS_ID), secure.getProviderApiKey(AWS_SECRET_ID))

    companion object {
        const val TOKEN_ID = "nexus_assistant_proxy"
        const val AWS_ACCESS_ID = "nexus_bedrock_access_key"
        const val AWS_SECRET_ID = "nexus_bedrock_secret_key"
        const val ANTHROPIC_KEY_ID = "nexus_llm_anthropic_key"
        const val OPENAI_KEY_ID = "nexus_llm_openai_compatible_key"
        private val LOCAL = Regex("^http://(127\\.0\\.0\\.1|localhost)(:\\d+)?(/.*)?$")
        private val REGION = Regex("^[a-z]{2}(-[a-z]+)+-\\d$")
    }
}

/** Todo el asistente montado sobre los repositorios de Nexus. Se reconstruye al recargar skills o ajustes. */
class AssistantRuntime(private val app: NexusApplication) {
    val settings = AssistantSettingsStore(app)
    val llmBudget = com.vidadomu.nexus.agent.core.llm.LlmBudgetLedger(File(app.filesDir, "assistant/llm-budget.json"))
    val registry = NexusModelRegistry(app)
    val assets = AssetIndex(File(app.filesDir, "assistant/index.json"))
    val guard = SpendGuard()
    /** Encargos del chat actual: estado compartido por UI, agente y envío (sobrevive a recargar skills). */
    val orders = com.vidadomu.nexus.agent.core.orders.OrderBook()
    val backend = NexusGenerationBackend(app, registry)
    private val executor = GuardedExecutor(backend, guard)
    private val published = runCatching {
        PublishedRateEstimator.fromJson(app.assets.open("atlas_published_prices.json").bufferedReader().use { it.readText() })
    }.getOrElse { PublishedRateEstimator(emptyList()) }
    private val logger = RedactingLogger(AgentLogger { level, msg ->
        Log.println(when (level) { "E" -> Log.ERROR; "W" -> Log.WARN; else -> Log.INFO }, TAG, msg.take(900))
    })

    @Volatile var skills: SkillStore = loadSkills(); private set
    @Volatile var deps: ToolDeps = buildDeps(); private set

    private fun loadSkills() = SkillStore(listOf(
        AssetSkillSource(app.assets),
        DirectorySkillSource(File(app.filesDir, "skills"), "files/skills"),
        DirectorySkillSource(File(Environment.getExternalStorageDirectory(), "Documents/ALEXIA/Nexus/Skills"), "Documents/ALEXIA/Nexus/Skills"),
    )).also { s -> s.errors.forEach { logger.log("W", "skill: $it") }; logger.log("I", "skills cargadas: ${s.all().size}") }

    private fun buildDeps() = ToolDeps(
        registry = registry, skills = skills,
        cost = ChainedCostEstimator(listOf(NexusQuoteEstimator(registry), published)),
        // El router ordena con la tabla publicada (sin llamadas de red por candidato); el plan usa la calculadora.
        router = ModelRouter(registry, skills, published, excludedFamilies = { settings.excludedFamilies() }, schemaCheckLimit = { settings.limits().let { it.maximum(it.maxRecommendationSchemaChecks) } }),
        guard = guard, executor = executor, assets = assets, history = NexusHistorySource(app), directFlow = true,
        nativeOperations = NexusNativeOperations(app, this),
        orders = orders,
        probeFrameRate = { uri -> AgentMediaStore.frameRate(app, uri) },
        limits = { settings.limits() },
        adaptPrompt = { text, schema, target ->
            val current = settings.load()
            com.vidadomu.nexus.agent.core.llm.PromptAdapter({ llm(current) }, { current.modelId })
                .adapt(text, schema.textLimits["prompt"], target)
        },
    )

    /** Relee skills (assets + files/skills + Documents/ALEXIA/Nexus/Skills) sin recompilar. */
    fun reloadSkills(): SkillStore { skills = loadSkills(); deps = buildDeps(); return skills }

    fun llm(s: AssistantSettings): LlmProvider =
        when {
            s.offline -> OfflinePlannerProvider()
            s.backend == AssistantSettings.BACKEND_ANTIGRAVITY -> AntigravityProvider(
                s.antigravityEndpoint.ifBlank { AssistantSettings.DEFAULT_ANTIGRAVITY_ENDPOINT },
            )
            // BytePlus y cerebros propios: todo sale del perfil (protocolo, auth, razonamiento, límites propios).
            s.isCustom -> s.brain!!.let { p ->
                val profileRates = p.inputUsdPerM?.let { i -> p.outputUsdPerM?.let { o -> i to o } }
                val host = runCatching { java.net.URI(p.baseUrl).host.orEmpty().lowercase() }.getOrDefault("")
                val isBytePlus = p.id == com.vidadomu.nexus.agent.core.llm.BrainPresets.BYTEPLUS_ID ||
                    host == "bytepluses.com" || host.endsWith(".bytepluses.com")
                val rates = if (isBytePlus) BytePlusConsumption.get(app).ratesFor(p.modelId, profileRates)
                    else profileRates ?: settings.customRates()
                val provider = com.vidadomu.nexus.agent.core.llm.budgeted(
                    BrainFactory.create(p, { settings.brains.apiKey(p.id) }), llmBudget,
                    customRates = { rates }, limits = { settings.brainLlmLimits(p) })
                if (isBytePlus) com.vidadomu.nexus.agent.core.llm.trackedBytePlus(
                    provider, BytePlusConsumption.get(app).ledger, rates,
                ) { BytePlusConsumption.get(app).checkAlerts() }
                else provider
            }
            s.backend == AssistantSettings.BACKEND_ANTHROPIC -> com.vidadomu.nexus.agent.core.llm.BudgetedLlmProvider(
                com.vidadomu.nexus.agent.core.llm.AnthropicProvider({ settings.apiKey(AssistantSettings.BACKEND_ANTHROPIC) }), llmBudget,
                customRates = { settings.customRates() }, limits = { settings.llmLimits() })
            s.backend == AssistantSettings.BACKEND_OPENAI -> com.vidadomu.nexus.agent.core.llm.BudgetedLlmProvider(
                com.vidadomu.nexus.agent.core.llm.OpenAiCompatibleProvider(s.llmBaseUrl, { settings.apiKey(AssistantSettings.BACKEND_OPENAI) },
                    // OpenRouter pide identificar la app; el resto lo ignora.
                    extraHeaders = mapOf("HTTP-Referer" to "https://github.com/egodivergente/nexus", "X-Title" to "Nexus")), llmBudget,
                customRates = { settings.customRates() }, limits = { settings.llmLimits() })
            s.direct -> com.vidadomu.nexus.agent.core.llm.BudgetedLlmProvider(
                BedrockDirectProvider(s.region.trim(), { settings.awsCredentials() }), llmBudget, limits = { settings.llmLimits() })
            else -> BedrockProxyProvider(s.endpoint, { settings.token() })
        }

    fun orchestrator(s: AssistantSettings = settings.load()) = AgentOrchestrator(
        llm(s), s.toConfig(), NexusToolkit.all(deps), skills, assets, logger,
        documents = com.vidadomu.nexus.agent.core.agent.AttachmentDocumentLoader { AgentDocuments.load(it) },
        images = if (s.visionEnabled) AttachmentImageLoader { AgentMediaStore.thumbnail(app,it) } else null,
        videos = if (s.visionEnabled) com.vidadomu.nexus.agent.core.agent.AttachmentVideoLoader { AgentVideoFrames.load(app, it) } else null,
        uiContext = { orders.turnContext(guard) },
        verifiedOrders = { orders.all() },
    )

    /** Indexa (sin tocar nexus.db ni archivos) las creaciones ya terminadas, para buscarlas por prompt/modelo. */
    private val importLock = kotlinx.coroutines.sync.Mutex()

    suspend fun importHistory(): Int = importLock.withLock { importHistoryLocked() }

    private suspend fun importHistoryLocked(): Int {
        var added = 0
        for (job in app.generationRepository.allJobs.value.filter { it.status == GenerationStatus.COMPLETED && it.outputs.isNotEmpty() }) {
            if (assets.generation(job.id) != null) continue
            val type = when (job.modality.name) { "VIDEO" -> AssetType.VIDEO; "IMAGE" -> AssetType.IMAGE; "AUDIO" -> AssetType.AUDIO; else -> AssetType.OTHER }
            val rec = assets.upsertGeneration(GenerationRecord(
                id = AssetIndex.newId("gen"), nexusJobId = job.id, providerJobId = job.providerJobId, providerId = job.providerId,
                modelId = job.modelId, modality = job.modality.name, prompt = job.prompt, negativePrompt = job.negativePrompt.ifBlank { null },
                projectId = assets.projectAt(job.startedAt),
                params = job.parameters.filterKeys { !it.startsWith("__") }, seed = job.parameters["seed"],
                referenceUris = listOfNotNull(job.inputMediaUri), estimatedCostUsd = job.estimatedCostUsd, actualCostUsd = job.actualCostUsd,
                status = "COMPLETED", createdAt = job.startedAt, completedAt = job.completedAt,
            ))
            val ids = job.outputs.mapNotNull { o ->
                val uri = o.localUri ?: o.url
                assets.assetByUri(uri)?.id ?: assets.addAsset(Asset(
                    id = "", uri = uri, type = type, role = AssetRole.OUTPUT, source = AssetSource.GENERATED,
                    displayName = job.modelId.substringAfterLast('/') + " · " + job.prompt.take(40), mimeType = o.mimeType.ifBlank { null },
                    width = o.width, height = o.height, durationSec = o.durationSeconds?.toDouble(), generationId = rec.id, createdAt = job.startedAt,
                    projectId = rec.projectId,
                )).id
            }
            assets.upsertGeneration(rec.copy(outputAssetIds = ids)); added++
        }
        if (added > 0) logger.log("I", "historial indexado: $added generaciones")
        return added
    }

    companion object {
        const val TAG = "NexusAssistant"
        @Volatile private var instance: AssistantRuntime? = null
        fun get(app: NexusApplication): AssistantRuntime = instance ?: synchronized(this) { instance ?: AssistantRuntime(app).also { instance = it } }
    }
}
