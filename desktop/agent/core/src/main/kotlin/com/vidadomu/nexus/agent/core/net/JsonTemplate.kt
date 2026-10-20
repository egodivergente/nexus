package com.vidadomu.nexus.agent.core.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Rutas JSON mínimas para leer respuestas de APIs configurables: `data.0.url`, `output[0].video.url`,
 * `images.*.url` (el comodín recorre una lista). No hay expresiones ni ejecución: solo navegación.
 */
object JsonPaths {
    private fun tokens(path: String): List<String> =
        path.trim().replace(Regex("\\[(\\d+|\\*)\\]"), ".$1").split('.').map { it.trim() }.filter { it.isNotEmpty() }

    fun get(root: JsonElement, path: String): JsonElement? = all(root, path).firstOrNull()

    /** Todos los valores que casan con [path]; con `*` puede haber varios. */
    fun all(root: JsonElement, path: String): List<JsonElement> {
        var current: List<JsonElement> = listOf(root)
        for (token in tokens(path)) {
            current = current.flatMap { node ->
                when {
                    token == "*" -> (node as? JsonArray)?.toList() ?: (node as? JsonObject)?.values?.toList().orEmpty()
                    node is JsonObject -> listOfNotNull(node[token])
                    node is JsonArray -> token.toIntOrNull()?.let { listOfNotNull(node.getOrNull(it)) }.orEmpty()
                    else -> emptyList()
                }
            }
            if (current.isEmpty()) return emptyList()
        }
        return current.filter { it !is JsonNull }
    }

    /** Texto de un valor primitivo; los objetos y listas no se convierten en texto. */
    fun string(root: JsonElement, path: String): String? =
        (get(root, path) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /** Todas las cadenas halladas (p. ej. varias URL), sin vacías ni repetidas, en orden. */
    fun strings(root: JsonElement, path: String): List<String> =
        all(root, path).flatMap { e ->
            when (e) {
                is JsonPrimitive -> listOfNotNull(e.contentOrNull)
                is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                else -> emptyList()
            }
        }.filter { it.isNotBlank() }.distinct()

    fun number(root: JsonElement, path: String): Double? = string(root, path)?.toDoubleOrNull()
}

/**
 * Plantillas JSON con marcadores `{{nombre}}`. La plantilla es JSON válido: se recorre el árbol, así no hay
 * que escapar comillas a mano y un valor que sea EXACTAMENTE `"{{duration}}"` conserva su tipo (número,
 * booleano, lista…). Si falta el valor, esa clave (o ese elemento de lista) se omite; dentro de un texto
 * mezclado el marcador se sustituye por vacío.
 */
object JsonTemplate {
    private val placeholder = Regex("\\{\\{\\s*([A-Za-z0-9_.\\-]+)\\s*\\}\\}")
    private val whole = Regex("^\\s*\\{\\{\\s*([A-Za-z0-9_.\\-]+)\\s*\\}\\}\\s*$")
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(template: String): JsonElement = json.parseToJsonElement(template)

    /** Marcadores que usa la plantilla (para avisar de los que nadie rellena). */
    fun placeholders(template: JsonElement): Set<String> = buildSet { collect(template, this) }

    private fun collect(e: JsonElement, out: MutableSet<String>) {
        when (e) {
            is JsonObject -> e.values.forEach { collect(it, out) }
            is JsonArray -> e.forEach { collect(it, out) }
            is JsonPrimitive -> if (e.isString) placeholder.findAll(e.content).forEach { out += it.groupValues[1] }
            else -> Unit
        }
    }

    private object Omit

    fun render(template: JsonElement, vars: Map<String, JsonElement?>): JsonElement =
        (build(template, vars) as? JsonElement) ?: throw IllegalArgumentException("La plantilla raíz no puede ser un marcador vacío.")

    /** Sustituye marcadores en un texto simple (rutas, cabeceras). */
    fun renderText(text: String, vars: Map<String, JsonElement?>): String =
        placeholder.replace(text) { m -> primitiveText(vars[m.groupValues[1]]) }

    private fun build(e: JsonElement, vars: Map<String, JsonElement?>): Any = when (e) {
        is JsonObject -> JsonObject(e.mapNotNull { (k, v) ->
            val built = build(v, vars)
            if (built === Omit) null else k to (built as JsonElement)
        }.toMap())
        is JsonArray -> JsonArray(e.mapNotNull { v -> build(v, vars).takeIf { it !== Omit } as JsonElement? })
        is JsonPrimitive -> if (!e.isString) e else {
            val m = whole.matchEntire(e.content)
            if (m != null) vars[m.groupValues[1]]?.takeIf { it !is JsonNull && !(it is JsonPrimitive && it.isString && it.content.isEmpty()) } ?: Omit
            else JsonPrimitive(renderText(e.content, vars))
        }
        else -> e
    }

    private fun primitiveText(e: JsonElement?): String = when (e) {
        null, is JsonNull -> ""
        is JsonPrimitive -> e.content
        else -> e.toString()
    }
}
