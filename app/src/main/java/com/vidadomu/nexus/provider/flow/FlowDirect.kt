package com.vidadomu.nexus.provider.flow

import com.vidadomu.nexus.core.model.ProviderError
import com.vidadomu.nexus.core.security.ISecureStorage
import com.vidadomu.nexus.ui.settings.FLOW_RECOVERY_KEY
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

/**
 * Flow with explicit parameters (model, aspect ratio, seconds, start/end frames, references, 1-4 results) through the
 * bridge's `/api/flow/direct` routes on AXELPC. Those routes run the same verified contract as Nexus Desktop
 * (`desktop/flow_contract.py`); the options shown to the user come from [FlowDirectCapabilities], never from a list
 * written in the app. Every submission carries the amount of a fresh quote, which the bridge re-checks.
 */
@Serializable
data class FlowDirectMode(
    val mode: String,
    val aspects: List<String> = emptyList(),
    val durations: List<Int> = emptyList(),
    val resolutions: List<String> = emptyList(),
    @SerialName("end_frame") val endFrame: Boolean = false,
    @SerialName("max_references") val maxReferences: Int = 0,
)

@Serializable
data class FlowDirectModel(val model: String, val kind: String, val modes: List<FlowDirectMode> = emptyList(), val counts: List<Int> = listOf(1)) {
    val label: String get() = FlowDirect.modelLabel(model)
    fun mode(id: String): FlowDirectMode? = modes.firstOrNull { it.mode == id }
}

@Serializable
data class FlowDirectCapabilities(val available: Boolean = false, val models: List<FlowDirectModel> = emptyList(),
    val downloads: Map<String, List<String>> = emptyMap(), val source: String? = null) {
    fun models(kind: String) = models.filter { it.kind == kind }
}

/** What the user chose. [validate] mirrors the contract so nothing invalid reaches the bridge. */
data class FlowDirectSelection(
    val model: String,
    val mode: String,
    val aspect: String = "16:9",
    val durationSeconds: Int? = null,
    val resolution: String? = null,
    val count: Int = 1,
    val initialFrameId: String? = null,
    val endFrameId: String? = null,
    val referenceIds: List<String> = emptyList(),
)

data class FlowDirectQuote(val amount: Int?, val currency: String, val detail: String, val checkedAt: Long) {
    /** «Incluido» only when Flow itself reports 0 credits; an unknown amount is never shown as free. */
    val included: Boolean get() = amount == 0 && currency == "flow_credits"
}

data class FlowDirectJob(
    val id: String,
    val status: String,
    val outputs: List<String>,
    val error: String? = null,
    val uncertain: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Double? = null,
)

data class FlowDirectAssetOption(val resolution: String, val label: String, val credits: Int?)

object FlowDirect {
    const val ROUTE = "flow_route"
    const val DIRECT = "direct"
    const val MODEL = "flow_model"
    const val MODE = "flow_mode"
    const val ASPECT = "aspect_ratio"
    const val DURATION = "duration"
    const val RESOLUTION = "resolution"
    const val COUNT = "count"
    const val COST = "flow_quoted_cost"
    const val JOB_PREFIX = "direct_"
    /** Request id of a finished direct job whose file Flow should process (2K/4K image, 1080p/4K video). */
    const val SOURCE = "flow_source_request"
    const val OUTPUT_SCHEME = "flow-direct://"

    fun processModel(video: Boolean) = if (video) "video_upscale" else "image_upscale"
    /** `flow-direct://<request>/<index>` → request id, for outputs produced by the direct route. */
    fun sourceRequest(outputUrl: String): String? = outputUrl.takeIf { it.startsWith(OUTPUT_SCHEME) }
        ?.removePrefix(OUTPUT_SCHEME)?.substringBefore('/')?.takeIf { Regex("[A-Za-z0-9_-]{8,80}").matches(it) }

    fun modelLabel(model: String): String = when (model) {
        "veo_fast" -> "Veo Fast"
        "veo_quality" -> "Veo Quality"
        "veo_lite" -> "Veo Lite"
        "omni_flash" -> "Omni Flash"
        "nano-pro" -> "Nano Banana Pro"
        "nano2" -> "Nano Banana 2"
        else -> model
    }

    fun modeLabel(mode: String): String = when (mode) {
        "r2v" -> "Referencias"
        "i2v" -> "Fotograma inicial y final"
        "t2v" -> "Texto"
        "t2i" -> "Imagen"
        else -> mode
    }

    /** Problems in the user's language, or null when the selection matches the capabilities. */
    fun validate(selection: FlowDirectSelection, capabilities: FlowDirectCapabilities): String? {
        val model = capabilities.models.firstOrNull { it.model == selection.model } ?: return "Este modelo de Flow no está disponible."
        val mode = model.mode(selection.mode) ?: return "${model.label} no admite «${modeLabel(selection.mode)}»."
        if (selection.aspect !in mode.aspects) return "${model.label} admite ${mode.aspects.joinToString(" o ")}."
        if (mode.durations.isNotEmpty() && selection.durationSeconds != null && selection.durationSeconds !in mode.durations)
            return "Duraciones válidas: ${mode.durations.joinToString(", ") { "$it s" }}."
        if (mode.resolutions.isNotEmpty() && selection.resolution != null && selection.resolution !in mode.resolutions)
            return "Resoluciones de generación: ${mode.resolutions.joinToString(", ")}."
        if (selection.count !in model.counts) return "Elige de ${model.counts.min()} a ${model.counts.max()} resultados por bloque."
        when (selection.mode) {
            "i2v" -> {
                if (selection.initialFrameId == null) return "Añade el fotograma inicial."
                if (selection.endFrameId != null && !mode.endFrame) return "${model.label} no admite fotograma final."
                if (selection.referenceIds.isNotEmpty()) return "Las referencias usan el modo «Referencias»."
            }
            "r2v" -> {
                if (selection.referenceIds.isEmpty()) return "Añade al menos una referencia."
                if (selection.referenceIds.size > mode.maxReferences) return "${model.label} admite hasta ${mode.maxReferences} referencias."
                if (selection.initialFrameId != null || selection.endFrameId != null) return "Este modo no usa fotogramas."
            }
            "t2v" -> if (selection.initialFrameId != null || selection.endFrameId != null || selection.referenceIds.isNotEmpty())
                return "El modo «Texto» no usa imágenes."
            "t2i" -> if (selection.referenceIds.size > mode.maxReferences) return "${model.label} admite hasta ${mode.maxReferences} referencias."
        }
        return null
    }

    /** Arguments for quote/submit; local ids never travel, only bridge reference ids (ref_<sha256>). */
    fun arguments(selection: FlowDirectSelection, prompt: String? = null, requestId: String? = null, cost: Int? = null, withCost: Boolean = false): JsonObject =
        buildJsonObject {
            put("model", selection.model)
            put("aspect", selection.aspect)
            if (selection.mode != "t2i") put("mode", selection.mode)
            selection.durationSeconds?.let { put("duration", it) }
            selection.resolution?.let { put("resolution", it) }
            if (selection.count != 1) put("count", selection.count)
            selection.initialFrameId?.let { put("initial_frame", it) }
            selection.endFrameId?.let { put("end_frame", it) }
            if (selection.referenceIds.isNotEmpty()) putJsonArray("reference_images") { selection.referenceIds.forEach { add(JsonPrimitive(it)) } }
            prompt?.let { put("prompt", it) }
            requestId?.let { put("request_id", it) }
            if (withCost) put("cost", cost?.let { JsonPrimitive(it) } ?: JsonNull)
        }

    fun requestId(jobId: String): String = jobId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
}

class FlowDirectClient(
    private val client: OkHttpClient,
    private val secureStorage: ISecureStorage,
    private val base: String = FlowBridgeClient.HOSTS.first().second + "/direct",
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val slow by lazy { client.newBuilder().readTimeout(16, java.util.concurrent.TimeUnit.MINUTES).callTimeout(16, java.util.concurrent.TimeUnit.MINUTES).build() }

    suspend fun capabilities(): Result<FlowDirectCapabilities> = call(get("$base/capabilities")) { json.decodeFromString(FlowDirectCapabilities.serializer(), it) }

    suspend fun quote(selection: FlowDirectSelection): Result<FlowDirectQuote> = call(post("$base/quote", FlowDirect.arguments(selection)), slow) { raw ->
        val o = json.parseToJsonElement(raw).jsonObject
        FlowDirectQuote(o.int("amount"), o.string("currency") ?: "flow_account", o.string("detail").orEmpty(), o.long("time") ?: System.currentTimeMillis())
    }

    suspend fun submit(selection: FlowDirectSelection, prompt: String, requestId: String, cost: Int?): Result<FlowDirectJob> =
        call(post("$base/jobs", FlowDirect.arguments(selection, prompt, requestId, cost, withCost = true)), slow, ::job)

    private fun processArgs(video: Boolean, source: String, resolution: String) = buildJsonObject {
        put("model", FlowDirect.processModel(video)); put("source_request_id", source); put("resolution", resolution)
    }

    /** Credits Flow's own download menu shows for [resolution] of an existing result. Generates nothing. */
    suspend fun quoteProcess(video: Boolean, source: String, resolution: String): Result<FlowDirectQuote> =
        call(post("$base/quote", processArgs(video, source, resolution)), slow) { raw ->
            val o = json.parseToJsonElement(raw).jsonObject
            FlowDirectQuote(o.int("amount"), o.string("currency") ?: "flow_account", o.string("detail").orEmpty(), o.long("time") ?: System.currentTimeMillis())
        }

    suspend fun process(video: Boolean, source: String, resolution: String, requestId: String, cost: Int?): Result<FlowDirectJob> =
        call(post("$base/jobs", JsonObject(processArgs(video, source, resolution) + mapOf("request_id" to JsonPrimitive(requestId),
            "cost" to (cost?.let { JsonPrimitive(it) } ?: JsonNull)))), slow, ::job)

    suspend fun status(requestId: String, recover: Boolean = false): Result<FlowDirectJob> =
        call(get("$base/jobs/$requestId" + if (recover) "?recover=true" else ""), slow, ::job)

    suspend fun assetOptions(requestId: String): Result<List<FlowDirectAssetOption>> = call(get("$base/assets/$requestId/options"), slow) { raw ->
        (json.parseToJsonElement(raw).jsonObject["options"] as? kotlinx.serialization.json.JsonArray).orEmpty().map { e ->
            val o = e.jsonObject
            FlowDirectAssetOption(o.string("resolution").orEmpty(), o.string("label") ?: o.string("resolution").orEmpty(), o.int("credits"))
        }
    }

    /** Downloads a completed output (`/media/<sha256[:32]>.<ext>` from [status]) into [destinationDir]. */
    suspend fun media(output: String, destinationDir: File, prefix: String): Result<FlowBridgeDownload> = withContext(Dispatchers.IO) {
        val name = output.substringAfterLast('/')
        if (!Regex("[a-f0-9]{32}\\.(png|jpg|jpeg|webp|mp4|gif)").matches(name)) return@withContext Result.failure(ProviderError("Resultado de Flow no válido."))
        try {
            destinationDir.mkdirs()
            client.newCall(authorized("$base/media/$name").get().build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(ProviderError("Flow no entregó el archivo (${response.code}). Se volverá a intentar sin generar."))
                val body = response.body ?: return@withContext Result.failure(ProviderError("Flow no entregó el archivo."))
                val target = File(destinationDir, "$prefix-$name")
                val partial = File(destinationDir, "$prefix-$name.part")
                body.byteStream().use { input -> FileOutputStream(partial).use { input.copyTo(it) } }
                if (partial.length() <= 0L || !partial.renameTo(target)) { partial.delete(); return@withContext Result.failure(ProviderError("La copia del resultado de Flow está vacía.")) }
                val mime = when (name.substringAfterLast('.')) { "mp4" -> "video/mp4"; "gif" -> "image/gif"; "webp" -> "image/webp"; "png" -> "image/png"; else -> "image/jpeg" }
                Result.success(FlowBridgeDownload(target, mime))
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { Result.failure(ProviderError("No se pudo descargar el resultado de Flow: ${error.message.orEmpty()}")) }
    }

    private fun job(raw: String): FlowDirectJob {
        val o = json.parseToJsonElement(raw).jsonObject
        val dims = o["dimensions"]
        val (w, h) = when (dims) {
            is kotlinx.serialization.json.JsonArray -> dims.getOrNull(0)?.jsonPrimitive?.content?.toIntOrNull() to dims.getOrNull(1)?.jsonPrimitive?.content?.toIntOrNull()
            is JsonObject -> dims.int("width") to dims.int("height")
            else -> null to null
        }
        return FlowDirectJob(o.string("id").orEmpty(), o.string("status") ?: "needs_reconciliation",
            (o["outputs"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) },
            o.string("error"), o["uncertain"]?.jsonPrimitive?.content == "true", w, h, o.string("duration_seconds")?.toDoubleOrNull())
    }

    private fun authorized(url: String): Request.Builder {
        val token = secureStorage.getProviderApiKey(FLOW_RECOVERY_KEY)
        if (token.isBlank()) throw ProviderError("Vincula el puente privado de Flow en Ajustes antes de continuar.")
        return Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", "application/json")
    }
    private fun get(url: String) = authorized(url).get().build()
    private fun post(url: String, body: JsonObject) = authorized(url).post(body.toString().toRequestBody(JSON)).build()

    private suspend fun <T> call(request: Request, http: OkHttpClient = client, decode: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val body = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
                    val code = body?.string("error")
                    val detail = body?.get("detail")?.let { (it as? JsonPrimitive)?.content ?: it.toString() }
                    // Bridge refusals happen before anything reaches Google: report them as validation (nothing sent),
                    // so the job is not flagged as a possibly started submission. «flow_direct_uncertain» keeps its 503.
                    val nothingSent = code in setOf("flow_direct_rejected", "quote_confirmation_required", "unsupported_field",
                        "invalid_request_id", "invalid_request", "reference_not_found", "reference_integrity_failed", "flow_direct_not_installed")
                    return@use Result.failure(ProviderError(detail ?: problem(code, response.code),
                        httpStatusCode = if (nothingSent) 422 else response.code, errorCode = code))
                }
                Result.success(decode(raw))
            }
        } catch (error: CancellationException) { throw error }
        catch (error: ProviderError) { Result.failure(error) }
        catch (error: Exception) { Result.failure(ProviderError("No se pudo contactar con el puente de Flow en AXELPC.")) }
    }

    private fun problem(code: String?, status: Int): String = when (code) {
        "flow_direct_not_installed" -> "El puente de AXELPC no tiene instalada la ruta de Flow con ajustes."
        "quote_confirmation_required" -> "Consulta el importe y confírmalo antes de enviar."
        "unsupported_field" -> "Flow no admite uno de los ajustes enviados."
        "reference_not_found", "reference_integrity_failed" -> "Vuelve a añadir la imagen: el puente no la tiene íntegra."
        else -> "El puente de Flow respondió con un error ($status)."
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
private fun JsonObject.int(key: String): Int? = string(key)?.toDoubleOrNull()?.toInt()
private fun JsonObject.long(key: String): Long? = string(key)?.toDoubleOrNull()?.toLong()
