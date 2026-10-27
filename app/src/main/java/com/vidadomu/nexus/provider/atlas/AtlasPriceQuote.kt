package com.vidadomu.nexus.provider.atlas

import com.vidadomu.nexus.core.network.awaitResponse
import com.vidadomu.nexus.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class PriceQuote(val usd: Double, val configuration: String, val checkedAt: Long,
    val unit: String? = null, val unitUsd: Double? = null, val clipSeconds: Double? = null,
    val baseTariff: Boolean = false,
    /** Only an amount for this whole request from the calculator, never a published/unit/fallback rate. */
    val isRequestTotal: Boolean = false,
    val estimated: Boolean = true,
    val source: String? = null,
    val estimatedTokens: Long? = null, val exactUsd: String? = null)

/** A published tariff remains visible when a source-video total needs private media. */
object PublishedTariff {
    fun quote(model: NexusModel, videoSeconds: Double? = null): PriceQuote? {
        val rate = model.basePriceUsd?.takeIf { it.isFinite() && it >= 0 } ?: return null
        if (!model.priceUnitVerified) return null
        val unit = when(model.priceUnit?.lowercase()) {
            "seg", "second", "s" -> "s"
            "min", "minute" -> "min"
            "img", "image" -> "imagen"
            "gen", "generation" -> "generación"
            "clip" -> "clip"
            else -> return null
        }
        val seconds = videoSeconds?.takeIf { it.isFinite() && it > 0 }
        val checkedAt = model.catalogCheckedAt ?: 0L
        val source = model.catalogCheckedAt?.let { "https://api.atlascloud.ai/api/v1/models" }
        // Only source-video-duration models: explicit output duration and multi-video
        // models need their calculator's rules, so show their tariff without guessing a total.
        val sourceDurationModel = model.videoField != null && model.videosArrayField == null &&
            model.parameters.none { it.key in setOf("duration", "video_duration", "output_duration") }
        return if(unit == "s" && sourceDurationModel && seconds != null) {
            val duration = java.math.BigDecimal.valueOf(seconds).stripTrailingZeros().toPlainString()
            PriceQuote(rate * seconds, "$duration s de entrada · estimación según tarifa base", checkedAt, unitUsd = rate, source=source)
        } else PriceQuote(rate, "Tarifa publicada · total según configuración", checkedAt, unit = unit, unitUsd = rate, source=source)
    }
}


object AtlasPriceQuote {
    private var credentials: () -> String = { "" }
    fun configure(credentials: () -> String) { this.credentials = credentials }
    fun invalidate() { synchronized(cache) { cache.clear() } }
    private val client = OkHttpClient.Builder().addInterceptor(com.vidadomu.nexus.core.network.LocalQaInterceptor()).callTimeout(12, TimeUnit.SECONDS).build()
    private val gate = Mutex()
    private val cache = object : LinkedHashMap<String, PriceQuote>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PriceQuote>?) = size > 120
    }
    private var lastRequest = 0L

    fun payload(model: NexusModel, params: Map<String, String>, imageCount: Int): JsonObject = buildJsonObject {
        put("model", model.providerModelId ?: model.id)
        val defaults = model.parameters.associate { it.key to it.defaultValue }
        (defaults + params).toSortedMap().forEach { (key, value) ->
            if(value.isBlank() || key.startsWith("__") || key in setOf("prompt","negative_prompt","system_prompt")) return@forEach
            val parameter = model.parameters.find { it.key == key } ?: return@forEach
            when(parameter.nativeType) {
                "boolean" -> value.toBooleanStrictOrNull()?.let { put(key,it) }
                "integer" -> value.toLongOrNull()?.let { put(key,it) }
                "number" -> value.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { put(key,it) }
                else -> put(key,value)
            }
        }
        // The calculator counts images; it does not need or receive actual image bytes.
        model.imagesArrayField?.let { field ->
            if(imageCount > 0) put(field,NativeImagePayload.encode(List(imageCount.coerceAtMost(150)) { "" },model.structuredImageReferences))
        }
    }

    fun decode(raw: String, configuration: String, now: Long = System.currentTimeMillis()): PriceQuote {
        val root = Json.parseToJsonElement(raw).jsonObject
        check(root["code"]?.jsonPrimitive?.contentOrNull == "200") { "Cotización rechazada" }
        val amount = root["data"]?.jsonObject?.get("price")?.jsonPrimitive?.doubleOrNull
        check(amount != null && amount.isFinite() && amount >= 0) { "Cotización inválida" }
        val estimated = root["data"]?.jsonObject?.get("estimated")?.jsonPrimitive?.booleanOrNull ?: true
        val tokens = root["data"]?.jsonObject?.get("estimated_tokens")?.jsonPrimitive?.longOrNull?.takeIf { it >= 0 }
        return PriceQuote(amount,configuration,now,isRequestTotal = true,estimated=estimated,source="https://api.atlascloud.ai/api/v1/model/calculate",estimatedTokens=tokens,
            exactUsd=root["data"]!!.jsonObject["price"]!!.jsonPrimitive.content)
    }

    fun configuration(model: NexusModel, values: Map<String,String>): String {
        val p = model.parameters.associate { it.key to it.defaultValue } + values
        return if(Modality.VIDEO in model.modalities) {
            listOfNotNull((p["duration"] ?: p["video_duration"])?.takeIf { it.isNotBlank() }?.let { if(it=="-1") "duración automática" else "$it s" },
                p["resolution"]?.uppercase(), p["fps"]?.let { "$it FPS" }, (p["sound"] ?: p["generate_audio"] ?: p["audio"]?.takeIf { it in listOf("true","false") })?.let { if(it=="true") "con sonido" else "sin sonido" }).joinToString(" · ")
        } else listOfNotNull((p["num_images"] ?: p["n"] ?: "1")+" imagen(es)",p["resolution"],p["quality"]).joinToString(" · ")
    }

    suspend fun fetch(model: NexusModel, params: Map<String,String>? = null, images: Int? = null, videoSeconds: Double? = null): PriceQuote? = withContext(Dispatchers.IO) {
        if(Modality.CHAT in model.modalities || Modality.AUDIO in model.modalities) return@withContext null
        val native = if(params == null) try { NativeModelSchema.load(model).model }
            catch(e: CancellationException) { throw e }
            catch(_: Exception) { return@withContext AtlasPublishedPrice.fetch(model) ?: VideoPricePreview.fallback(model) }
            else model
        val values = params ?: VideoPricePreview.initialValues(native)
        // Use the supported provider default when five seconds is unavailable; still request a quote.
        // The web calculator requires a remote media URL for these models. Keep private
        // media local, show the published unit rate and a clearly labelled local estimate.
        val hasSourceVideo = VideoPricePreview.requiresVideo(native)
        if(hasSourceVideo && com.vidadomu.nexus.agent.core.cost.TieredTariffs.covers(native.id)) {
            val effective = native.parameters.associate { it.key to it.defaultValue }.filterValues { it.isNotBlank() } + values
            val historical = com.vidadomu.nexus.agent.core.cost.TieredTariffs.quote(native.id, effective, videoSeconds) ?: return@withContext null
            return@withContext PriceQuote(historical.usd ?: historical.usdPerSecond,
                "Tarifa histórica guardada, no verificada hoy · " + historical.basis, 0,
                unit=if(historical.usd == null) "s" else null, unitUsd=historical.usdPerSecond)
        }
        if(values["duration"] == "-1" || hasSourceVideo &&
            (videoSeconds != null || native.parameters.none { it.key == "duration" })) {
            return@withContext PublishedTariff.quote(native, videoSeconds) ?: AtlasPublishedPrice.fetch(native)
        }
        val count = images ?: if(native.supportsImageInput) 1 else 0
        val body = payload(native,values,count)
        val credential = credentials().trim()
        val account = java.security.MessageDigest.getInstance("SHA-256").digest(credential.toByteArray()).joinToString("") { "%02x".format(it) }
        val key = account + ":" + body.toString()
        try { gate.withLock {
            synchronized(cache) { cache[key] }?.takeIf { System.currentTimeMillis()-it.checkedAt < 300_000 }?.let { return@withLock it }
            delay((1500-(System.currentTimeMillis()-lastRequest)).coerceAtLeast(0))
            currentCoroutineContext().ensureActive()
            lastRequest=System.currentTimeMillis()
            val raw = client.newCall(Request.Builder().url("https://api.atlascloud.ai/api/v1/model/calculate")
                .apply { if(credential.isNotBlank()) header("Authorization", "Bearer $credential") }
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()).awaitResponse().use {
                check(it.isSuccessful) { "HTTP ${it.code}" }
                requireNotNull(it.body).string()
            }
            currentCoroutineContext().ensureActive()
            decode(raw,configuration(native,values)).copy(clipSeconds = VideoPricePreview.duration(native,values)).also { synchronized(cache) { cache[key]=it } }
        } } catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            val published = try { AtlasPublishedPrice.fetch(native) }
                catch(cancel: CancellationException) { throw cancel }
                catch(_: Exception) { null }
            published ?: VideoPricePreview.fallback(native,params,videoSeconds) ?: throw e
        }
    }
}
