package com.vidadomu.nexus.agent.core.chat

import kotlinx.serialization.Serializable
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

/** Persisted beside the user message, never appended to its text or interpreted as an action. */
@Serializable
data class WidgetAnswer(val widgetId: String, val optionIds: Set<String>, val messageId: String? = null)

data class WidgetReply(val text: String, val answer: WidgetAnswer)

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

    /** A reply contains only the chosen labels; model-supplied IDs and hints cannot become commands. */
    fun reply(spec: WidgetSpec, selectedIds: Set<String>): WidgetReply? {
        val options = choiceOptions(spec)
        if (!validSelection(spec, options, selectedIds)) return null
        val labels = options.filter { it.id in selectedIds }.map { it.label }
        val text = if (spec is WidgetSpec.Compare) "Elijo: ${labels.single()}" else labels.joinToString(", ")
        return WidgetReply(text, WidgetAnswer(spec.id, selectedIds.toSet()))
    }

    /**
     * New replies restore IDs. Old histories restore only an entire, unambiguous submitted answer.
     * Enumerating subsets is bounded by MAX_OPTIONS (63); splitting on commas would corrupt labels.
     */
    fun selectedIds(spec: WidgetSpec, text: String?, answer: WidgetAnswer? = null, messageId: String? = null): Set<String> {
        val options = choiceOptions(spec)
        if (answer != null) return if (answer.messageId == messageId && answer.widgetId == spec.id && validSelection(spec, options, answer.optionIds))
            answer.optionIds.toSet() else emptySet()
        val submitted = text?.trim() ?: return emptySet()
        val matches = (1 until (1 shl options.size)).mapNotNull { mask ->
            val chosen = options.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            val ids = chosen.map { it.id }.toSet()
            val reply = reply(spec, ids) ?: return@mapNotNull null
            val lines = spec is WidgetSpec.Poll && spec.multi &&
                chosen.joinToString("\n") { it.label }.equals(submitted, ignoreCase = true)
            ids.takeIf { reply.text.equals(submitted, ignoreCase = true) || lines }
        }
        return matches.singleOrNull() ?: emptySet()
    }

    private fun choiceOptions(spec: WidgetSpec): List<WidgetOption> = when (spec) {
        is WidgetSpec.Poll -> spec.options
        is WidgetSpec.Compare -> spec.options
        is WidgetSpec.Steps -> emptyList()
    }

    private fun validSelection(spec: WidgetSpec, options: List<WidgetOption>, ids: Set<String>): Boolean =
        options.size in 2..MAX_OPTIONS && options.map { it.id }.toSet().size == options.size &&
            ids.isNotEmpty() && ids.all { id -> options.any { it.id == id } } &&
            (ids.size == 1 || spec is WidgetSpec.Poll && spec.multi)

    private fun options(e: JsonElement?): List<WidgetOption> =
        (e as? JsonArray).orEmpty().mapIndexedNotNull { i, item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { WidgetOption("o$i", it.take(MAX_LABEL)) }
                is JsonObject -> item.str("label")?.let { WidgetOption(item.str("id") ?: "o$i", it.take(MAX_LABEL), item.str("hint")?.take(MAX_HINT)) }
                else -> null
            }
        }.distinctBy { it.label.lowercase() }.take(MAX_OPTIONS).let { options ->
            val reserved = options.map { it.id }.toSet()
            val used = mutableSetOf<String>()
            options.mapIndexed { index, option ->
                var id = option.id
                if (id in used) {
                    id = "${option.id}_$index"
                    while (id in reserved || id in used) id += "_"
                }
                used += id
                option.copy(id = id)
            }
        }

    private fun idOf(o: JsonObject, seed: String, parts: List<String>): String =
        o.str("id")?.filter { it.isLetterOrDigit() || it == '_' || it == '-' }?.take(40)?.ifEmpty { null }
            ?: "w" + (seed + parts.joinToString("|")).hashCode().toUInt().toString(16)

    private fun JsonObject.str(key: String): String? = this[key].text()?.take(MAX_TEXT)
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonElement?.bool(): Boolean = (this as? JsonPrimitive)?.booleanOrNull ?: false
}
