package com.vidadomu.nexus.cut.command

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File

/**
 * Registro de acciones de un proyecto: una línea JSON por petición confirmada o rechazada. Sirve para
 * auditar qué hizo cada quien y para que una petición repetida tras un cierre no se aplique dos veces.
 */
class ActionLog(private val file: File, private val maxBytes: Long = 1_000_000, private val keepLines: Int = 400) {
    class KnownKey(val key: String, val revision: Long, val label: String)

    @Synchronized
    fun committed(revision: Long, atMs: Long, origin: Origin, label: String, key: String?, ops: List<Op>, created: List<String>) = append(buildJsonObject {
        put("rev", revision); put("at", atMs); put("origin", origin.name); put("label", label)
        key?.let { put("key", it) }
        put("ops", buildJsonArray { for (o in ops) add(buildJsonObject { put("id", o.id); put("args", o.args) }) })
        if (created.isNotEmpty()) put("created", Outcome.strings(created))
    })

    @Synchronized
    fun rejected(revision: Long, atMs: Long, origin: Origin, error: CutError, ops: List<Op>) = append(buildJsonObject {
        put("rev", revision); put("at", atMs); put("origin", origin.name); put("error", error.toJson())
        put("ops", buildJsonArray { for (o in ops) add(buildJsonObject { put("id", o.id) }) })
    })

    /** Las últimas entradas, de la más antigua a la más reciente. Las líneas ilegibles se ignoran. */
    @Synchronized
    fun recent(limit: Int = 50): List<JsonObject> {
        if (!file.exists()) return emptyList()
        return file.readLines().takeLast(limit.coerceAtLeast(1)).mapNotNull { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    }

    fun knownKeys(limit: Int = 200): List<KnownKey> = recent(limit).mapNotNull { e ->
        val key = e["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        KnownKey(key, e["rev"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null, e["label"]?.jsonPrimitive?.contentOrNull.orEmpty())
    }

    private fun append(entry: JsonObject) {
        // El registro nunca debe impedir una edición: si no se puede escribir, la edición sigue siendo válida.
        runCatching {
            file.parentFile?.mkdirs()
            if (file.exists() && file.length() > maxBytes) file.writeText(file.readLines().takeLast(keepLines).joinToString("\n", postfix = "\n"))
            file.appendText(entry.toString() + "\n")
        }
    }
}
