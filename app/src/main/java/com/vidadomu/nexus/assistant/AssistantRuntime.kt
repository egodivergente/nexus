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
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.cost.ChainedCostEstimator
import com.vidadomu.nexus.agent.core.cost.PublishedRateEstimator
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
import com.vidadomu.nexus.core.model.GenerationStatus
import java.io.File

/**
 * Configuración del asistente. Dos backends:
 *  - «direct» (por defecto, decisión del usuario 02-10): la app firma con SigV4 y llama a Bedrock con sus claves de AWS,
 *    guardadas cifradas en SecureStorage.
 *  - «proxy»: APK → proxy → Bedrock con un token de dispositivo.
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
) {
    val excludedSet: Set<String> get() = parseFamilies(excludedFamilies)
    val direct: Boolean get() = backend != BACKEND_PROXY

    /** Sin credenciales del backend elegido se usa el planificador local (sin LLM). */
    val offline: Boolean get() = modelId.isBlank() || if (direct) awsAccessKeyId.isBlank() || maskedAwsSecret.isBlank() || region.isBlank()
        else endpoint.isBlank() || maskedToken.isBlank()

    fun toConfig() = AgentConfig(
        provider = when { offline -> "offline"; direct -> "bedrock-direct"; else -> "bedrock-proxy" },
        modelId = modelId.ifBlank { "offline" }, region = region,
        endpoint = endpoint, temperature = temperature, maxTokens = maxTokens, maxToolRounds = 40, mode = mode, sendImagesToLlm = sendImages && !offline,
    )

    companion object {
        const val BACKEND_DIRECT = "direct"
        const val BACKEND_PROXY = "proxy"
        const val DEFAULT_MODEL = "us.anthropic.claude-sonnet-4-6"
        const val DEFAULT_REGION = "us-east-1"
        const val DEFAULT_EXCLUDED = "veo"
        /** Modelos que respondieron en la cuenta del usuario el 02-10 (los 5.x y Opus 4.7/4.8 dan AccessDenied). */
        val KNOWN_MODELS = listOf(
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

    init { seedFromBuild() }

    /** Primera ejecución: si la build trae claves de Bedrock (fichero local fuera del repo) y no hay guardadas, se copian. */
    private fun seedFromBuild() = runCatching {
        if (BuildConfig.BEDROCK_ACCESS_KEY.isNotBlank() && !secure.hasProviderApiKey(AWS_SECRET_ID)) {
            secure.saveProviderApiKey(AWS_ACCESS_ID, BuildConfig.BEDROCK_ACCESS_KEY)
            secure.saveProviderApiKey(AWS_SECRET_ID, BuildConfig.BEDROCK_SECRET_KEY)
            prefs.edit().putString("backend", AssistantSettings.BACKEND_DIRECT)
                .putString("region", BuildConfig.BEDROCK_REGION.ifBlank { AssistantSettings.DEFAULT_REGION })
                .putString("model_id", BuildConfig.BEDROCK_MODEL.ifBlank { AssistantSettings.DEFAULT_MODEL }).apply()
        }
    }

    fun load() = AssistantSettings(
        backend = prefs.getString("backend", AssistantSettings.BACKEND_DIRECT).orEmpty(),
        endpoint = prefs.getString("endpoint", "").orEmpty(),
        modelId = prefs.getString("model_id", AssistantSettings.DEFAULT_MODEL).orEmpty(),
        region = prefs.getString("region", AssistantSettings.DEFAULT_REGION).orEmpty(),
        temperature = prefs.getString("temperature", null)?.toDoubleOrNull(),
        maxTokens = prefs.getInt("max_tokens", 4096),
        mode = runCatching { AgentMode.valueOf(prefs.getString("mode", "AGENT")!!) }.getOrDefault(AgentMode.AGENT),
        sendImages = prefs.getBoolean("send_images", true),
        maskedToken = masked(TOKEN_ID),
        awsAccessKeyId = runCatching { secure.getProviderApiKey(AWS_ACCESS_ID) }.getOrDefault(""),
        maskedAwsSecret = masked(AWS_SECRET_ID),
        excludedFamilies = prefs.getString("excluded_families", AssistantSettings.DEFAULT_EXCLUDED).orEmpty(),
    )

    /** Proyecto activo, persistido entre sesiones (lo generado mientras está activo se etiqueta con él). */
    fun activeProjectId(): String? = prefs.getString("active_project", null)?.takeIf { it.isNotBlank() }
    fun setActiveProject(id: String?) = prefs.edit().putString("active_project", id.orEmpty()).apply()

    private fun masked(id: String) = runCatching { secure.getMaskedProviderApiKey(id).takeIf { secure.hasProviderApiKey(id) }.orEmpty() }.getOrDefault("")

    /** [newToken] / [newAwsSecret]: null = no tocar; "" = borrar. */
    fun save(s: AssistantSettings, newToken: String?, newAwsSecret: String? = null) {
        val endpoint = s.endpoint.trim().trimEnd('/')
        require(endpoint.isBlank() || endpoint.startsWith("https://") || LOCAL.matches(endpoint)) {
            "El proxy debe usar https:// (o http://127.0.0.1 con adb reverse en depuración)."
        }
        require(s.region.isBlank() || REGION.matches(s.region.trim())) { "Región de AWS no válida (p. ej. us-east-1)." }
        prefs.edit().putString("backend", s.backend).putString("endpoint", endpoint).putString("model_id", s.modelId.trim()).putString("region", s.region.trim())
            .putString("temperature", s.temperature?.toString()).putInt("max_tokens", s.maxTokens.coerceIn(256, 32_000))
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
        model?.let { require(AssistantSettings.KNOWN_MODELS.any { pair -> pair.first == it }); edit.putString("model_id", it) }
        tokens?.let { require(it in 256..32000); edit.putInt("max_tokens", it) }
        images?.let { edit.putBoolean("send_images", it) }
        excluded?.let { edit.putString("excluded_families", AssistantSettings.parseFamilies(it).joinToString(", ")) }
        check(edit.commit()) { "No se pudieron guardar los ajustes." }
    }

    fun setMode(mode: AgentMode) = prefs.edit().putString("mode", mode.name).apply()

    fun excludedFamilies(): Set<String> =
        AssistantSettings.parseFamilies(prefs.getString("excluded_families", AssistantSettings.DEFAULT_EXCLUDED).orEmpty())

    fun token(): String = secure.getProviderApiKey(TOKEN_ID)

    fun awsCredentials() = AwsCredentials(secure.getProviderApiKey(AWS_ACCESS_ID), secure.getProviderApiKey(AWS_SECRET_ID))

    companion object {
        const val TOKEN_ID = "nexus_assistant_proxy"
        const val AWS_ACCESS_ID = "nexus_bedrock_access_key"
        const val AWS_SECRET_ID = "nexus_bedrock_secret_key"
        private val LOCAL = Regex("^http://(127\\.0\\.0\\.1|localhost)(:\\d+)?(/.*)?$")
        private val REGION = Regex("^[a-z]{2}(-[a-z]+)+-\\d$")
    }
}

/** Todo el asistente montado sobre los repositorios de Nexus. Se reconstruye al recargar skills o ajustes. */
class AssistantRuntime(private val app: NexusApplication) {
    val settings = AssistantSettingsStore(app)
    val registry = NexusModelRegistry(app)
    val assets = AssetIndex(File(app.filesDir, "assistant/index.json"))
    val guard = SpendGuard()
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
        router = ModelRouter(registry, skills, published, excludedFamilies = { settings.excludedFamilies() }),
        guard = guard, executor = executor, assets = assets, history = NexusHistorySource(app), directFlow = true,
        nativeOperations = NexusNativeOperations(app, this),
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
            s.direct -> BedrockDirectProvider(s.region.trim(), { settings.awsCredentials() })
            else -> BedrockProxyProvider(s.endpoint, { settings.token() })
        }

    fun orchestrator(s: AssistantSettings = settings.load()) = AgentOrchestrator(
        llm(s), s.toConfig(), NexusToolkit.all(deps), skills, assets, logger,
        images = if (s.sendImages && !s.offline) AttachmentImageLoader { AgentMediaStore.thumbnail(it) } else null,
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
