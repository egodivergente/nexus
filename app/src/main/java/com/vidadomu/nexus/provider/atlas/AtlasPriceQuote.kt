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
    val baseTariff: Boolean = false)

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
        // Only source-video-duration models: explicit output duration and multi-video
        // models need their calculator's rules, so show their tariff without guessing a total.
        val sourceDurationModel = model.videoField != null && model.videosArrayField == null &&
            model.parameters.none { it.key in setOf("duration", "video_duration", "output_duration") }
        return if(unit == "s" && sourceDurationModel && seconds != null) {
            val duration = java.math.BigDecimal.valueOf(seconds).stripTrailingZeros().toPlainString()
            PriceQuote(rate * seconds, "$duration s de entrada · estimación según tarifa base", System.currentTimeMillis(), unitUsd = rate)
        } else PriceQuote(rate, "Tarifa publicada · total según configuración", System.currentTimeMillis(), unit = unit, unitUsd = rate)
    }
}


/**
 * Same non-generating calculator used by Atlas' web client (verified 2026-09-20).
 * No API key, prompts, local URIs or personal media leave the phone for a quotation.
 * Quotes are public tariff estimates, never actual ledger entries.
 */
object AtlasPriceQuote {
    private val client = OkHttpClient.Builder().addInterceptor(com.vidadomu.nexus.core.network.LocalQaInterceptor()).callTimeout(12, TimeUnit.SECONDS).build()
    private val gate = Mutex()
    private val cache = object : LinkedHashMap<String, PriceQuote>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PriceQuote>?) = size > 120
    }
    private var lastRequest = 0L

    fun payload(model: NexusModel, params: Map<String, String>, imageCount: Int): JsonObject = buildJsonObject {
        put("model", model.id)
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
        return PriceQuote(amount,configuration,now)
    }

    fun configuration(model: NexusModel, values: Map<String,String>): String {
        val p = model.parameters.associate { it.key to it.defaultValue } + values
        return if(Modality.VIDEO in model.modalities) {
            listOfNotNull((p["duration"] ?: p["video_duration"])?.takeIf { it.isNotBlank() }?.let { if(it=="-1") "duración automática" else "$it s" },
                p["resolution"]?.uppercase(), (p["sound"] ?: p["generate_audio"] ?: p["audio"]?.takeIf { it in listOf("true","false") })?.let { if(it=="true") "con sonido" else "sin sonido" }).joinToString(" · ")
        } else listOfNotNull((p["num_images"] ?: p["n"] ?: "1")+" imagen(es)",p["resolution"],p["quality"]).joinToString(" · ")
    }

    suspend fun fetch(model: NexusModel, params: Map<String,String>? = null, images: Int? = null, videoSeconds: Double? = null): PriceQuote? = withContext(Dispatchers.IO) {
        if(Modality.CHAT in model.modalities || Modality.AUDIO in model.modalities) return@withContext null
        val native = if(params == null) try { NativeModelSchema.load(model).model }
            catch(e: CancellationException) { throw e }
            catch(_: Exception) { return@withContext AtlasPublishedPrice.fetch(model) ?: VideoPricePreview.fallback(model) }
            else model
        val values = params ?: VideoPricePreview.initialValues(native)
        // Tarifa que depende del nivel de la plantilla: nunca la base de 720P para un 4K.
        if(com.vidadomu.nexus.agent.core.cost.TieredTariffs.covers(native.id)) {
            val effective = native.parameters.associate { it.key to it.defaultValue }.filterValues { it.isNotBlank() } + values
            val q = com.vidadomu.nexus.agent.core.cost.TieredTariffs.quote(native.id, effective, videoSeconds) ?: return@withContext null
            val total = q.usd
            return@withContext if(total != null) PriceQuote(total, q.basis, System.currentTimeMillis(), unitUsd = q.usdPerSecond)
                else PriceQuote(q.usdPerSecond, q.basis, System.currentTimeMillis(), unit = "s", unitUsd = q.usdPerSecond)
        }
        // Use the supported provider default when five seconds is unavailable; still request a quote.
        // The web calculator requires a remote media URL for these models. Keep private
        // media local, show the published unit rate and a clearly labelled local estimate.
        val hasSourceVideo = VideoPricePreview.requiresVideo(native)
        if(values["duration"] == "-1" || hasSourceVideo &&
            (videoSeconds != null || native.parameters.none { it.key == "duration" })) {
            return@withContext PublishedTariff.quote(native, videoSeconds) ?: AtlasPublishedPrice.fetch(native)
        }
        val count = images ?: if(native.supportsImageInput) 1 else 0
        val body = payload(native,values,count)
        val key = body.toString()
        try { gate.withLock {
            cache[key]?.takeIf { System.currentTimeMillis()-it.checkedAt < 300_000 }?.let { return@withLock it }
            delay((1500-(System.currentTimeMillis()-lastRequest)).coerceAtLeast(0))
            currentCoroutineContext().ensureActive()
            lastRequest=System.currentTimeMillis()
            val raw = client.newCall(Request.Builder().url("https://api.atlascloud.ai/api/v1/model/calculate")
                .post(key.toRequestBody("application/json".toMediaType())).build()).awaitResponse().use {
                check(it.isSuccessful) { "HTTP ${it.code}" }
                requireNotNull(it.body).string()
            }
            currentCoroutineContext().ensureActive()
            decode(raw,configuration(native,values)).copy(clipSeconds = VideoPricePreview.duration(native,values)).also { cache[key]=it }
        } } catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            val published = try { AtlasPublishedPrice.fetch(native) }
                catch(cancel: CancellationException) { throw cancel }
                catch(_: Exception) { null }
            published ?: VideoPricePreview.fallback(native,params,videoSeconds) ?: throw e
        }
    }
}
