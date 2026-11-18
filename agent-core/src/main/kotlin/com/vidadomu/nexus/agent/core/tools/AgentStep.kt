package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.agent.core.log.Redactor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Qué hizo el agente en un paso: esperar al modelo o ejecutar una herramienta. */
@Serializable
enum class AgentStepKind { THINK, TOOL }

/** Estado de un paso. La UI los distingue: en curso, completado, con incidencia y cancelado. */
@Serializable
enum class AgentStepStatus { RUNNING, DONE, ISSUE, CANCELLED }

/**
 * Un paso real del turno, tal como lo emite el orquestador: se anuncia al empezar ([AgentStepStatus.RUNNING])
 * y se vuelve a emitir con el mismo [id] al acabar, con su duración y, si lo hay, el detalle técnico.
 * Se guarda con la respuesta para poder enseñar la actividad al reabrir la conversación.
 */
@Serializable
data class AgentStep(
    val id: String,
    val kind: AgentStepKind,
    /** Frase corta para la fila («Buscando modelos en el catálogo»). */
    val label: String,
    val status: AgentStepStatus = AgentStepStatus.RUNNING,
    /** Nombre de la herramienta; null en los pasos de pensar. */
    val tool: String? = null,
    /** Hora de pared del inicio (ms). */
    val startedAt: Long = 0L,
    val durationMs: Long? = null,
    /** Una línea: herramienta y argumentos clave. */
    val command: String? = null,
    /** Entrada completa (JSON legible, sin secretos, recortada). */
    val input: String? = null,
    /** Resultado o error (sin secretos, recortado). */
    val result: String? = null,
) {
    val running: Boolean get() = status == AgentStepStatus.RUNNING
    val hasDetail: Boolean get() = !command.isNullOrBlank() || !input.isNullOrBlank() || !result.isNullOrBlank()

    fun finished(status: AgentStepStatus, durationMs: Long, result: String? = this.result): AgentStep =
        copy(status = status, durationMs = durationMs.coerceAtLeast(0L), result = result?.let(AgentSteps::clip))
}

object AgentSteps {
    /** Tope por campo: la actividad se guarda con el chat y no debe engordarlo. */
    const val DETAIL_CHARS = 2_000
    const val COMMAND_CHARS = 160
    private const val CLIP_MARGIN = 400
    /** Pasos que se conservan por respuesta (los últimos). */
    const val KEPT_PER_TURN = 60

    private const val HIDDEN = "«oculto»"

    /** Campos cuyo valor no se enseña ni se guarda con la actividad, se llame como se llame la credencial. */
    private val secretField = Regex("(?i)(password|passwd|passphrase|secret|token|api[_-]?key|authorization|credential|cookie|private[_-]?key)")

    /**
     * Formas de credencial que el redactor de registros no cubre. La actividad se guarda con el chat y se puede
     * copiar, así que aquí se oculta de más antes que de menos. El grupo 1 es lo que se conserva.
     */
    private val secretShapes = listOf(
        // clave: valor / clave=valor con un nombre de campo sospechoso (un número suelto, true, false o null no lo son).
        Regex("(?i)((?:password|passwd|passphrase|secret|token|api[_-]?key|credential)[A-Za-z0-9_.-]*\"?\\s*[:=]\\s*\"?)(?!(?:\\d+(?:[.,]\\d+)?|true|false|null)(?:[\\s,}\\]\"&;)]|$))[^\\s\",}]{6,}"),
        Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/\\-]{12,}=*"),
        Regex("()\\b(?:sk-[A-Za-z0-9_\\-]{12,}|gh[pousr]_[A-Za-z0-9]{20,}|AIza[0-9A-Za-z_\\-]{30,}|xox[abprs]-[A-Za-z0-9\\-]{10,}|eyJ[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{4,})"),
    )

    /** Texto sin secretos: el redactor de registros y, encima, las formas de arriba. Aplicarlo dos veces no cambia nada. */
    fun scrub(text: String): String = secretShapes.fold(Redactor.redact(text)) { acc, rx -> rx.replace(acc) { m -> m.groupValues[1] + HIDDEN } }

    /** El mismo JSON con los valores de texto de los campos sospechosos ocultos, a cualquier profundidad. */
    private fun masked(element: JsonElement, key: String? = null): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (name, value) -> masked(value, name) })
        is JsonArray -> JsonArray(element.map { masked(it, key) })
        is JsonNull -> element
        is JsonPrimitive -> if (key != null && element.isString && element.content.isNotBlank() && secretField.containsMatchIn(key)) JsonPrimitive(HIDDEN) else element
    }

    fun clip(text: String, max: Int = DETAIL_CHARS): String {
        // Solo se mira lo que puede llegar a enseñarse, con margen para que un secreto a caballo del corte se
        // reconozca entero: un resultado enorme no se recorre de punta a punta.
        val clean = scrub(if (text.length > max + CLIP_MARGIN) text.take(max + CLIP_MARGIN) else text)
        return if (text.length <= max) clean else clean.take(max) + "\n… (recortado: ${text.length} caracteres)"
    }

    /** `herramienta clave=valor …` con los argumentos simples; los objetos y listas se resumen. */
    fun command(tool: String, input: JsonElement?): String {
        val args = (input as? JsonObject)?.entries?.mapNotNull { (key, value) ->
            when (value) {
                is JsonNull -> null
                // Se oculta antes de acortar: un secreto partido por el recorte dejaría de reconocerse.
                is JsonPrimitive -> "$key=" + if (value.isString && secretField.containsMatchIn(key)) HIDDEN
                    else scrub(value.content.take(CLIP_MARGIN).replace('\n', ' ')).let { if (it.length > 48) it.take(47) + "…" else it }
                is JsonArray -> "$key=[${value.size}]"
                is JsonObject -> "$key={…}"
            }
        }.orEmpty()
        val line = scrub((listOf(tool) + args).joinToString(" "))
        return if (line.length <= COMMAND_CHARS) line else line.take(COMMAND_CHARS - 1) + "…"
    }

    private val pretty = kotlinx.serialization.json.Json { prettyPrint = true; prettyPrintIndent = "  " }

    /** JSON legible y sin secretos; null si no hay nada que enseñar. */
    fun detail(element: JsonElement?): String? {
        if (element == null || element is JsonNull) return null
        if (element is JsonObject && element.isEmpty()) return null
        val safe = masked(element)
        val text = runCatching { pretty.encodeToString(JsonElement.serializer(), safe) }.getOrElse { safe.toString() }
        return clip(text)
    }

    /** Resultado para el detalle: en un error, su mensaje tal cual; si no, el JSON legible. */
    fun outcome(result: JsonElement?, isError: Boolean): String? {
        val message = ((result as? JsonObject)?.get("error") as? JsonPrimitive)?.takeIf { it.isString }?.content
        return if (isError && !message.isNullOrBlank() && (result as JsonObject).size == 1) clip(message) else detail(result)
    }

    /** Inserta o sustituye por id, conservando el orden de llegada. */
    fun upsert(steps: List<AgentStep>, step: AgentStep): List<AgentStep> {
        val at = steps.indexOfFirst { it.id == step.id }
        return if (at < 0) steps + step else steps.toMutableList().also { it[at] = step }
    }

    /**
     * Cierra lo que quedó en curso al terminar el turno: nada puede seguir «trabajando» en una conversación
     * guardada. [now] es la hora de pared; la duración sale del inicio de cada paso.
     */
    fun close(steps: List<AgentStep>, status: AgentStepStatus, now: Long, reason: String? = null): List<AgentStep> =
        steps.map { step ->
            if (!step.running) step
            else step.finished(status, if (step.startedAt > 0L) now - step.startedAt else 0L, reason ?: step.result)
        }

    /** Lo que se guarda con la respuesta: sin pasos en curso y con tope de cantidad. */
    fun forStorage(steps: List<AgentStep>): List<AgentStep> = steps.filterNot { it.running }.takeLast(KEPT_PER_TURN)

    /** Suma de lo que tardaron los pasos (para la cabecera plegada). */
    fun totalMs(steps: List<AgentStep>): Long = steps.sumOf { it.durationMs ?: 0L }

    fun issues(steps: List<AgentStep>): Int = steps.count { it.status == AgentStepStatus.ISSUE }
}
