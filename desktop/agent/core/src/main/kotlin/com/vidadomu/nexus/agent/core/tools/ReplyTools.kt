package com.vidadomu.nexus.agent.core.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Normaliza las respuestas rápidas del modelo: sin vacías ni repetidas, rótulos y textos acotados, máximo 4. */
internal fun cleanSuggestedReplies(raw: List<JsonObject>): List<SuggestedReply> =
    raw.mapNotNull { o ->
        val label = o.str("label")?.take(MAX_LABEL) ?: return@mapNotNull null
        SuggestedReply(label, o.str("text")?.take(MAX_TEXT) ?: label)
    }.distinctBy { it.label.lowercase() }.take(MAX_REPLIES)

private const val MAX_REPLIES = 4
private const val MAX_LABEL = 40
private const val MAX_TEXT = 300

/**
 * Deja al usuario elegir con un toque cuando el agente termina con una pregunta de elección.
 * Solo muestra botones: no envía nada, no gasta y no cambia ningún encargo.
 */
class SuggestRepliesTool : BaseTool(
    "suggest_replies", ToolCategory.READ,
    "Muestra 2–4 botones de respuesta rápida bajo tu respuesta cuando terminas preguntando al usuario que elija entre opciones concretas (modelos, caminos, sí/no con matiz). Úsala UNA vez por turno, justo antes de responder, y no repitas aquí información que no esté en tu texto. Elegir un botón equivale a que el usuario escriba ese texto. No la uses para confirmar gastos: el envío siempre es el botón de la tarjeta.",
    schema {
        objectArray("replies", "Entre 2 y 4 respuestas, en el orden en que las has presentado.", required = true) {
            string("label", "Texto corto del botón (máx. 40 caracteres), p. ej. «Kling O3 · 0,84 €».", required = true)
            string("text", "Mensaje que se enviará como usuario al pulsarlo (por defecto, el rótulo). Que se entienda solo, sin depender del botón.")
        }
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val replies = cleanSuggestedReplies(input.objList("replies"))
        if (replies.size < 2) throw ToolInputException("Necesito al menos 2 respuestas distintas en «replies».")
        return ok(
            buildJsonObject {
                put("shown", replies.size)
                put("note", "El usuario verá estos botones bajo tu respuesta. Termina tu texto con la pregunta; no los enumeres otra vez.")
            },
            listOf(UiEvent.Replies(replies)),
        )
    }
}
