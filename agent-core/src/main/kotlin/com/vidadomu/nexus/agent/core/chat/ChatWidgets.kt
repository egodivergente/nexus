package com.vidadomu.nexus.agent.core.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Opción de una encuesta o una comparación; [hint] es una línea de apoyo opcional. */
data class WidgetOption(val id: String, val label: String, val hint: String? = null)

/**
 * Panel interactivo que el modelo emite en un bloque ```nexus-ui con JSON. Solo describe datos: la app decide
 * el aspecto y lo que ocurre al tocar (responder equivale a que el usuario escriba el texto; nunca ejecuta nada).
 */
sealed interface WidgetSpec {
    val id: String

    /** Encuesta de elección; con [multi] el usuario marca varias y envía. */
    data class Poll(override val id: String, val question: String, val options: List<WidgetOption>, val multi: Boolean) : WidgetSpec

    /** Lista de pasos que el usuario va tachando (estado solo local, no se envía). */
    data class Steps(override val id: String, val title: String?, val items: List<String>) : WidgetSpec

    /** Tarjetas lado a lado con un botón «Elegir» por opción. */
    data class Compare(override val id: String, val title: String?, val options: List<WidgetOption>) : WidgetSpec
}

object ChatWidgets {
    private const val MAX_OPTIONS = 6
    private const val MAX_STEPS = 12
    private const val MAX_LABEL = 60
    private const val MAX_HINT = 120
    private const val MAX_TEXT = 200
    private val json = Json { isLenient = true }

    /** null si el JSON no es válido o no cumple los límites; el llamador lo muestra entonces como bloque de código. */
    fun parse(raw: String): WidgetSpec? = runCatching {
        val o = json.parseToJsonElement(raw.trim()).jsonObject
        val title = o.str("question") ?: o.str("title")
        when (o.str("type")?.lowercase()) {
            "poll" -> {
                val options = options(o["options"])
                val question = o.str("question") ?: return null
                if (options.size < 2) return null
                WidgetSpec.Poll(idOf(o, question, options.map { it.label }), question, options, o["multi"].bool())
            }
            "compare" -> {
                val options = options(o["options"])
                if (options.size < 2) return null
                WidgetSpec.Compare(idOf(o, title.orEmpty(), options.map { it.label }), title, options)
            }
            "steps" -> {
                val items = (o["items"] as? JsonArray).orEmpty().mapNotNull { it.text()?.take(MAX_LABEL * 2) }.take(MAX_STEPS)
                if (items.isEmpty()) return null
                WidgetSpec.Steps(idOf(o, title.orEmpty(), items), title, items)
            }
            else -> null
        }
    }.getOrNull()

    /** Resumen en texto plano (copiar, accesibilidad, clientes sin panel). */
    fun plain(spec: WidgetSpec): String = when (spec) {
        is WidgetSpec.Poll -> spec.question + "\n" + spec.options.joinToString("\n") { "• " + it.label + (it.hint?.let { h -> " — $h" } ?: "") }
        is WidgetSpec.Compare -> (spec.title?.let { "$it\n" } ?: "") + spec.options.joinToString("\n") { "• " + it.label + (it.hint?.let { h -> " — $h" } ?: "") }
        is WidgetSpec.Steps -> (spec.title?.let { "$it\n" } ?: "") + spec.items.joinToString("\n") { "☐ $it" }
    }

    private fun options(e: JsonElement?): List<WidgetOption> =
        (e as? JsonArray).orEmpty().mapIndexedNotNull { i, item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { WidgetOption("o$i", it.take(MAX_LABEL)) }
                is JsonObject -> item.str("label")?.let { WidgetOption(item.str("id") ?: "o$i", it.take(MAX_LABEL), item.str("hint")?.take(MAX_HINT)) }
                else -> null
            }
        }.distinctBy { it.label.lowercase() }.take(MAX_OPTIONS)

    private fun idOf(o: JsonObject, seed: String, parts: List<String>): String =
        o.str("id")?.filter { it.isLetterOrDigit() || it == '_' || it == '-' }?.take(40)?.ifEmpty { null }
            ?: "w" + (seed + parts.joinToString("|")).hashCode().toUInt().toString(16)

    private fun JsonObject.str(key: String): String? = this[key].text()?.take(MAX_TEXT)
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonElement?.bool(): Boolean = (this as? JsonPrimitive)?.booleanOrNull ?: false
}
