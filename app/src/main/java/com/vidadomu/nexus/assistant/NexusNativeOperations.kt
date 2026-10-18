package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.tools.AgentSession
import com.vidadomu.nexus.agent.core.tools.NativeOperations
import com.vidadomu.nexus.agent.core.tools.ToolInputException
import com.vidadomu.nexus.agent.core.tools.assetJson
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.work.GenerationPollingWorker
import com.vidadomu.nexus.provider.flow.FlowBridgeProvider
import com.vidadomu.nexus.ui.create.CreationPreferences
import com.vidadomu.nexus.ui.theme.AppearancePreferences
import com.vidadomu.nexus.ui.theme.NexusAppearance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Explicit native capabilities, shared by the in-app agent; no arbitrary execution. */
class NexusNativeOperations(
    private val app: NexusApplication,
    private val runtime: AssistantRuntime,
) : NativeOperations {
    private val internet = AgentInternet(app)
    private val media = AgentFileOperations(app, runtime.assets)
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()

    override suspend fun run(operation: String, input: JsonObject, session: AgentSession): JsonObject =
        withContext(Dispatchers.IO) {
            when (operation) {
                "create_document" -> AgentArtifacts(app,runtime.assets).create(input,session)
                "read_library_text" -> AgentArtifacts(app,runtime.assets).read(input)
                "analyze_audio_asset" -> MediaEditJobs(app).start("analyze_audio",input,session)
                "list_library_assets" -> {
                    runtime.importHistory()
                    val found=runtime.assets.changes.value.assets.filter {!it.libraryDeleted && (input.text("query")?.let {q->it.displayName.contains(q,true)} ?: true)}.sortedByDescending {it.createdAt}
                    val offset=input.integer("offset",0,0..Int.MAX_VALUE); val limit=input.integer("limit",50,1..100)
                    buildJsonObject {put("total",found.size);put("assets",JsonArray(found.drop(offset).take(limit).map {runtime.assets.assetJson(it)}));if(offset+limit<found.size) put("next_offset",offset+limit)}
                }
                "view_library_asset", "inspect_image_quality" -> {
                    val a=runtime.assets.asset(input.requiredText("asset_id"))?.takeIf {!it.libraryDeleted} ?: throw ToolInputException("Archivo desconocido.")
                    if(a.type==com.vidadomu.nexus.agent.core.assets.AssetType.AUDIO) throw ToolInputException("Usa transcribe_media_asset y analyze_audio_asset para analizar sonido.")
                    val local=AgentVideoOperations(app).source(a.id)
                    val mime=local.mimeType ?: when(local.type) {
                        com.vidadomu.nexus.agent.core.assets.AssetType.IMAGE -> "image/jpeg"
                        com.vidadomu.nexus.agent.core.assets.AssetType.VIDEO -> "video/mp4"
                        else -> "application/octet-stream"
                    }
                    val quality=if(operation=="inspect_image_quality") AgentImageQuality.inspect(app,local) else null
                    val attachment=com.vidadomu.nexus.agent.core.tools.Attachment(local.id,local.uri,mime,local.displayName,local.width,local.height,local.durationSec,local.id)
                    session.attachments.removeAll {it.id==a.id}
                    session.attachments.add(attachment)
                    session.showMedia(attachment)
                    quality ?: buildJsonObject {put("asset_id",a.id);put("requested_context_load",true)}
                }
                "delete_library_assets" -> {
                    val ids=(input["asset_ids"] as? JsonArray)?.map {it.jsonPrimitive.content}?.toSet() ?: throw ToolInputException("Indica asset_ids.")
                    buildJsonObject {put("removed",runtime.assets.deleteLibraryAssets(ids));put("originals_preserved",true)}
                }
                "archive_failed_generations" -> {
                    val prefs=app.getSharedPreferences("history_archive_v1",0)
                    val ids=app.generationRepository.allJobs.value.filter {it.status in setOf(GenerationStatus.FAILED,GenerationStatus.CANCELLED)}.map {it.id}.toSet()
                    prefs.edit().putStringSet("ids",prefs.getStringSet("ids",emptySet())!!.toSet()+ids).commit()
                    buildJsonObject {put("archived",ids.size);put("history_preserved",true)}
                }
                "web_search", "read_web_page", "list_mcp_servers", "connect_mcp_server", "list_mcp_tools", "call_mcp_tool", "disconnect_mcp_server" -> internet.run(operation,input)
                "get_active_generations" -> active(input)
                "recover_generation" -> recover(input.requiredText("job_id"))
                "diagnose_flow" -> flow(false)
                "repair_flow" -> flow(true)
                "get_nexus_settings" -> settings()
                "update_nexus_settings" -> updateSettings(input)
                "rename_asset_file" -> media.rename(input, session)
                "convert_image_asset" -> media.convert(input,session)
                "inspect_media_asset" -> AgentVideoOperations(app).inspect(input.requiredText("asset_id"))
                "get_whisper_status" -> WhisperModelStore(app).status()
                "prepare_whisper_model" -> WhisperModelStore(app).enqueue()
                "edit_video_assets" -> MediaEditJobs(app).start(input.requiredText("action"),input,session)
                "extract_video_frames" -> MediaEditJobs(app).start("frames",input,session)
                "transcribe_media_asset" -> MediaEditJobs(app).start("transcribe",input,session)
                "get_media_edit_job" -> MediaEditJobs(app).status(input.requiredText("edit_job_id"),input.integer("wait_seconds",5,0..20),session)
                "atlas_get_balance" -> atlasBalance()
                "corte_status", "corte_list_projects", "corte_read_project", "corte_find_silences", "corte_apply_ops", "corte_undo" -> CorteBridge(app).run(operation, input)
                else -> throw ToolInputException("Operación desconocida.")
            }
        }

    private suspend fun active(input: JsonObject): JsonObject {
        app.database.loading.firstReady()
        val jobs = app.generationRepository.allJobs.value.filter { (it.status in ACTIVE || it.parameters["__tracking_paused"]=="true" || it.parameters["__submission_uncertain"]=="true") &&
            (input.text("provider_id") == null || it.providerId == input.text("provider_id")) }
            .sortedBy { it.startedAt }.take(input.integer("limit", 20, 1..50))
        return buildJsonObject { put("jobs", JsonArray(jobs.map(::jobInfo))); put("checked_at", System.currentTimeMillis()) }
    }

    private suspend fun recover(id: String): JsonObject {
        val current = app.generationRepository.getJob(id) ?: throw ToolInputException("Trabajo desconocido.")
        if (!canQueryExisting(current)) return buildJsonObject {
            put("job", jobInfo(current)); put("resubmitted", false); put("tracking_restored", false)
            put("action", if (current.providerJobId?.startsWith("queue_") == true)
                "Está en cola local. La recuperación no crea ni repite envíos."
                else if (current.status == GenerationStatus.COMPLETED) "Ya está terminado."
                else "No hay identificador remoto verificable; comprueba el proveedor antes de repetir.")
        }
        val result = app.generationRepository.pollJob(id, refreshFailed = true)
        val latest = result.getOrNull() ?: app.generationRepository.getJob(id) ?: current
        val tracking = latest.status in ACTIVE
        if (tracking) GenerationPollingWorker.enqueue(app, id)
        return buildJsonObject {
            put("job", jobInfo(latest)); put("resubmitted", false); put("tracking_restored", tracking)
            put("provider_checked", result.isSuccess)
            if (result.isFailure) put("action", "La consulta falló; conserva el estado y se reintentará el seguimiento.")
        }
    }

    private suspend fun flow(repair: Boolean): JsonObject {
        val status = app.flowBridgeProvider.bridgeStatus()
        app.database.loading.firstReady()
        val jobs = app.generationRepository.allJobs.value.filter { it.providerId == FlowBridgeProvider.PROVIDER_ID && it.status in ACTIVE }
        val recoverable = jobs.filter(::canQueryExisting)
        if (repair) recoverable.forEach { GenerationPollingWorker.enqueue(app, it.id) }
        val s = status.getOrNull()
        return buildJsonObject {
            put("connected", s?.connected ?: false); put("generation_ready", s?.isReady ?: false)
            put("status_checked", status.isSuccess)
            put("jobs", JsonArray(jobs.take(50).map(::jobInfo)))
            put("tracking_restored_count", if (repair) recoverable.size else 0)
            put("resubmitted", false); put("browser_restarted", false)
            put("action", when {
                status.isFailure -> "No se pudo contactar con Flow. Revisa Tailscale y el emparejamiento en Ajustes."
                s?.isReady == true -> if (repair) "Seguimiento local recuperado. Flow está listo." else "Flow está listo."
                else -> s?.detail ?: "Abre el acceso de Flow, inicia sesión y deja un proyecto abierto."
            })
        }
    }

    private fun settings(): JsonObject {
        val s = runtime.settings.load()
        return buildJsonObject {
            put("agent_model", s.modelId); put("max_tokens", s.maxTokens); put("send_images", s.sendImages)
            put("excluded_families", s.excludedFamilies); put("theme", AppearancePreferences(app).theme().name)
            put("remember_parameters", CreationPreferences(app).rememberParameters())
            put("credentials_configured", !s.offline)
        }
    }

    private fun updateSettings(input: JsonObject): JsonObject {
        val allowed = setOf("agent_model", "max_tokens", "send_images", "excluded_families", "theme", "remember_parameters")
        if (input.isEmpty() || input.keys.any { it !in allowed }) throw ToolInputException("Indica solo ajustes editables.")
        val model = input.text("agent_model")?.let { name -> MODEL_IDS[name] ?: throw ToolInputException("Modelo no admitido.") }
        val tokens = if ("max_tokens" in input) input.integer("max_tokens", 4096, 256..32000) else null
        val sendImages = input.flag("send_images")
        val remember = input.flag("remember_parameters")
        val theme = input.text("theme")?.let { name -> NexusAppearance.entries.firstOrNull { it.name == name } ?: throw ToolInputException("Tema no válido.") }
        val excluded = input.text("excluded_families") ?: if ("excluded_families" in input) "" else null
        // Validate all fields before writing. Dedicated method never reads or rewrites keys.
        runtime.settings.updateOperational(model, tokens, sendImages, excluded)
        theme?.let { AppearancePreferences(app).setTheme(it) }
        remember?.let { CreationPreferences(app).setRememberParameters(it) }
        return buildJsonObject { put("settings", settings()); put("applies_next_message", true) }
    }

    private fun atlasBalance(): JsonObject {
        val key = app.secureStorage.getAtlasApiKey()
        if (key.isBlank()) throw ToolInputException("Configura tu clave Atlas en Ajustes.")
        // Official atlascloud-mcp publicApi('/balance') contract; native Android transport.
        val request = Request.Builder().url("https://api.atlascloud.ai/public/v1/balance")
            .header("Authorization", "Bearer $key").get().build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ToolInputException(
                if (response.code in setOf(401, 403)) "Atlas no autoriza consultar el saldo; revisa tu clave o sus permisos."
                else "Atlas no devolvió el saldo (HTTP ${response.code}).")
            val body = response.body ?: throw ToolInputException("Atlas devolvió una respuesta vacía.")
            val stream = body.source()
            stream.request(65_537)
            if (stream.buffer.size > 65_536) throw ToolInputException("Respuesta de saldo demasiado grande.")
            val text = stream.buffer.readUtf8()
            val json = Json.parseToJsonElement(text) as? JsonObject ?: throw ToolInputException("Respuesta de saldo no válida.")
            if (json["available"] !is JsonObject) throw ToolInputException("Atlas no devolvió el saldo disponible.")
            val snapshot = buildJsonObject {
                // Only money fields are returned: no account identity, keys or arbitrary provider text.
                for (field in listOf("available", "cash", "bonus", "subscription_bonus", "frozen")) {
                    val money = json[field] as? JsonObject ?: continue
                    val value = money["value"]?.jsonPrimitive?.content ?: continue
                    val currency = money["currency"]?.jsonPrimitive?.content ?: continue
                    if (value.toBigDecimalOrNull() != null && currency.length in 1..12)
                        put(field, buildJsonObject { put("value", value); put("currency", currency) })
                }
                put("source", "https://api.atlascloud.ai/public/v1/balance"); put("checked_at", System.currentTimeMillis())
            }
            if ("available" !in snapshot) throw ToolInputException("Atlas devolvió un saldo no válido.")
            snapshot
        }
    }

    companion object {
        val ACTIVE = setOf(GenerationStatus.QUEUED, GenerationStatus.UPLOADING, GenerationStatus.GENERATING, GenerationStatus.PROCESSING, GenerationStatus.RENDERING)
        val MODEL_IDS = mapOf("sonnet-5.5" to "us.anthropic.claude-sonnet-5-5",
            "opus-5.5" to "us.anthropic.claude-opus-5-5", "sonnet-4.6" to "us.anthropic.claude-sonnet-4-6",
            "opus-4.6" to "us.anthropic.claude-opus-4-6-v1", "haiku-4.5" to "us.anthropic.claude-haiku-4-5-20251001-v1:0")
        fun canQueryExisting(job: GenerationJob): Boolean = !job.providerJobId.isNullOrBlank() &&
            !job.providerJobId.startsWith("queue_") && job.status !in setOf(GenerationStatus.COMPLETED, GenerationStatus.CANCELLED)

        fun jobInfo(job: GenerationJob) = buildJsonObject {
            put("job_id", job.id); put("provider_id", job.providerId); put("model_id", job.modelId)
            put("status", job.status.name); put("age_minutes", ((System.currentTimeMillis() - job.startedAt) / 60_000).coerceAtLeast(0))
            job.progress?.let { put("progress", it) }
            // Human error only, no technical payloads or output URLs.
            job.error?.let { put("error", com.vidadomu.nexus.agent.core.log.Redactor.redact(it).take(500)) }
            put("output_count", job.outputs.size); put("can_query_provider", canQueryExisting(job))
        }
    }
}

internal fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
internal fun JsonObject.requiredText(key: String) = text(key) ?: throw ToolInputException("Falta $key.")
internal fun JsonObject.integer(key: String, default: Int, range: IntRange): Int {
    if (key !in this) return default
    val value = (this[key] as? JsonPrimitive)?.intOrNull ?: throw ToolInputException("$key debe ser entero.")
    if (value !in range) throw ToolInputException("$key fuera del intervalo $range.")
    return value
}
internal fun JsonObject.flag(key: String): Boolean? {
    if (key !in this) return null
    return (this[key] as? JsonPrimitive)?.booleanOrNull ?: throw ToolInputException("$key debe ser booleano.")
}
private suspend fun kotlinx.coroutines.flow.StateFlow<Boolean>.firstReady() {
    first { !it }
}
