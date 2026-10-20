package com.vidadomu.nexus.agent.core.llm

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.*

/** Separate from Nexus estimates: official snapshots are never charged to the local ledger. */
class BytePlusOfficialCache(private val file: File) {
    @Synchronized fun get(period: String): JsonObject? = runCatching {
        Json.parseToJsonElement(file.readText()).jsonObject[period]?.jsonObject
    }.getOrNull()

    @Synchronized fun put(period: String, snapshot: JsonObject) {
        require(period in setOf("day", "month"))
        require(snapshot["schema"]?.jsonPrimitive?.intOrNull == 1 && snapshot["period"]?.jsonPrimitive?.contentOrNull == period)
        require((snapshot["fetched_at"]?.jsonPrimitive?.longOrNull ?: 0) > 0)
        val old = get(period)
        val merged = snapshot.toMutableMap()
        for (section in listOf("stats", "billing", "quota", "balance")) {
            val fresh = snapshot[section]?.jsonObject ?: error("Missing administrative section")
            require(fresh["status"]?.jsonPrimitive?.contentOrNull in setOf("ok", "stale", "unavailable"))
            if (fresh["status"]?.jsonPrimitive?.contentOrNull == "ok") {
                require((fresh["updated_at"]?.jsonPrimitive?.longOrNull ?: 0) > 0)
                merged[section] = JsonObject(fresh + mapOf("start" to (snapshot["start"] ?: JsonNull), "end" to (snapshot["end"] ?: JsonNull)))
            } else old?.get(section)?.jsonObject?.let { previous ->
                if (previous["updated_at"] != null) merged[section] = JsonObject(previous + mapOf(
                    "status" to JsonPrimitive("stale"), "error" to (fresh["error"] ?: JsonPrimitive("No se pudo actualizar."))))
            }
        }
        val all = if (file.exists()) Json.parseToJsonElement(file.readText()).jsonObject.toMutableMap() else mutableMapOf()
        all[period] = JsonObject(merged)
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        FileOutputStream(temp).use { it.write(JsonObject(all).toString().toByteArray()); it.fd.sync() }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
