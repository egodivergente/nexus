package com.vidadomu.nexus.provider.atlas

import com.vidadomu.nexus.core.network.awaitResponse
import com.vidadomu.nexus.core.model.NexusModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.*

/** Public model page, restricted to Atlas' authoritative pricing section, never vendor prose. */
object AtlasPublishedPrice {
    private val client = OkHttpClient.Builder().addInterceptor(com.vidadomu.nexus.core.network.LocalQaInterceptor()).callTimeout(15, TimeUnit.SECONDS).build()
    private val cache = com.vidadomu.nexus.core.util.BoundedCache<String, PriceQuote>(768)
    fun seedSnapshot(raw: String) {
        Json.parseToJsonElement(raw).jsonArray.forEach { item ->
            val row = item.jsonObject
            val id = row["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val usd = row["usd"]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() && it >= 0 } ?: return@forEach
            val unit = when(row["unit"]?.jsonPrimitive?.contentOrNull) {
                "second" -> "s"; "image" -> "imagen"; "generation" -> "generación"
                "minute" -> "min"; "operation" -> "operación"; else -> return@forEach
            }
            val checked = row["checkedAt"]?.jsonPrimitive?.longOrNull ?: return@forEach
            val date = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date(checked))
            cache.putIfAbsent(id, PriceQuote(usd, "Tarifa base publicada · revisada $date · las opciones pueden elevar el coste",
                checked, unit = unit, unitUsd = usd, baseTariff = true))
        }
    }
    fun cached(model: NexusModel): PriceQuote? = if(model.providerId in setOf(AtlasCloudProvider.PROVIDER_ID, "atlas", "atlas-cloud")) cache[model.providerModelId ?: model.id] else null

    fun decode(markdown: String, modelId: String, now: Long = System.currentTimeMillis()): PriceQuote? {
        val declared = Regex("\\*\\*Model ID\\*\\*:\\s*`([^`]+)`").find(markdown)?.groupValues?.get(1)
        if (declared != modelId) return null
        val section = Regex("(?s)## Pricing on Atlas Cloud\\s+(.*?)(?=\\n## |\\z)")
            .find(markdown)?.groupValues?.get(1) ?: return null
        val match = Regex("(?im)^- \\$([0-9]+(?:\\.[0-9]+)?) per (second|image|generation|minute|operation)\\b")
            .find(section) ?: return null
        val rate = match.groupValues[1].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
        val unit = when (match.groupValues[2].lowercase()) {
            "second" -> "s"
            "image" -> "imagen"
            "minute" -> "min"
            "operation" -> "operación"
            else -> "generación"
        }
        return PriceQuote(rate, "Tarifa base de la ficha oficial de Atlas; las opciones pueden elevar el coste",
            now, unit = unit, unitUsd = rate, baseTariff = true)
    }

    suspend fun fetch(model: NexusModel): PriceQuote? = withContext(Dispatchers.IO) {
        val id = model.providerModelId ?: model.id
        require(Regex("[a-zA-Z0-9_./-]+").matches(id) && ".." !in id) { "Identificador de modelo inválido" }
        cache[id]?.takeIf { System.currentTimeMillis() - it.checkedAt < 3_600_000 }?.let { return@withContext it }
        val text = client.newCall(Request.Builder().url("https://www.atlascloud.ai/models/$id")
            .header("Accept", "text/markdown").build()).awaitResponse().use {
            check(it.isSuccessful) { "No se pudo consultar la tarifa publicada" }
            requireNotNull(it.body).string()
        }
        decode(text, id)?.also { cache[id] = it }
    }
}

