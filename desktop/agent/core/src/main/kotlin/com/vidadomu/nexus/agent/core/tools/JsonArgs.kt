package com.vidadomu.nexus.agent.core.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/** Error de uso de una herramienta: vuelve al LLM como resultado con isError para que corrija. */
class ToolInputException(message: String) : IllegalArgumentException(message)

internal val TOOL_JSON = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

internal fun JsonObject.req(key: String): String = str(key) ?: throw ToolInputException("Falta «$key».")

internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()

internal fun JsonObject.dbl(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull ?: str(key)?.toDoubleOrNull()

internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.lowercase()?.toBooleanStrictOrNull() }

internal fun JsonObject.strList(key: String): List<String> = when (val v = this[key]) {
    is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
    is JsonPrimitive -> listOfNotNull(v.contentOrNull?.trim()?.takeIf(String::isNotEmpty))
    else -> emptyList()
}

internal fun JsonObject.objList(key: String): List<JsonObject> = (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

/** Parámetros del modelo: el LLM puede mandar números o booleanos; se pasan a texto como en el formulario. */
internal fun JsonObject.paramMap(key: String): Map<String, String?> {
    val o = this[key] as? JsonObject ?: return emptyMap()
    return o.mapValues { (_, v) ->
        when (v) {
            is JsonNull -> null
            is JsonPrimitive -> v.contentOrNull
            else -> v.toString()
        }
    }
}

// ---- construcción de schemas de entrada (JSON Schema mínimo que acepta Converse) ----

internal class SchemaBuilder {
    private val props = linkedMapOf<String, JsonObject>()
    private val required = mutableListOf<String>()

    fun string(name: String, description: String, required: Boolean = false, enum: List<String>? = null) =
        prop(name, required) { put("type", "string"); put("description", description); enum?.let { e -> put("enum", buildJsonArray { e.forEach { add(JsonPrimitive(it)) } }) } }

    fun integer(name: String, description: String, required: Boolean = false) =
        prop(name, required) { put("type", "integer"); put("description", description) }

    fun number(name: String, description: String, required: Boolean = false) =
        prop(name, required) { put("type", "number"); put("description", description) }

    fun boolean(name: String, description: String, required: Boolean = false) =
        prop(name, required) { put("type", "boolean"); put("description", description) }

    fun stringArray(name: String, description: String, required: Boolean = false) =
        prop(name, required) { put("type", "array"); put("description", description); put("items", buildJsonObject { put("type", "string") }) }

    fun integerArray(name: String, description: String, required: Boolean = false) =
        prop(name, required) { put("type", "array"); put("description", description); put("items", buildJsonObject { put("type", "integer") }) }

    fun freeObject(name: String, description: String, required: Boolean = false) =
        prop(name, required) { put("type", "object"); put("description", description); put("additionalProperties", true) }

    fun objectArray(name: String, description: String, required: Boolean = false, item: SchemaBuilder.() -> Unit) =
        prop(name, required) { put("type", "array"); put("description", description); put("items", SchemaBuilder().apply(item).build()) }

    private fun prop(name: String, isRequired: Boolean, block: JsonObjectBuilder.() -> Unit) {
        props[name] = buildJsonObject(block)
        if (isRequired) required += name
    }

    fun build(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(props))
        if (required.isNotEmpty()) put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
    }
}

internal fun schema(block: SchemaBuilder.() -> Unit): JsonObject = SchemaBuilder().apply(block).build()

internal fun ok(block: JsonObjectBuilder.() -> Unit): ToolOutcome = ToolOutcome(buildJsonObject(block))

internal fun ok(element: JsonElement, events: List<UiEvent> = emptyList()): ToolOutcome = ToolOutcome(element, events = events)

internal fun fail(message: String, extra: JsonObjectBuilder.() -> Unit = {}): ToolOutcome =
    ToolOutcome(buildJsonObject { put("error", message); extra() }, isError = true)

internal inline fun <reified T> T.toJson(): JsonElement = TOOL_JSON.encodeToJsonElement(kotlinx.serialization.serializer<T>(), this)
