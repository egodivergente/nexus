package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.tools.Attachment
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Marca en disco de un turno en curso. El turno vive en memoria del proceso: si Android lo mata a mitad, al volver
 * solo queda esta marca. Guarda lo que «Reintentar» necesita: el texto y los adjuntos, que ya son copias privadas.
 */
@Serializable
internal data class InterruptedTurn(val conversationId: String, val turnId: String, val text: String,
    val attachments: List<Attachment> = emptyList(), val startedAt: Long = 0L) {
    // Estable por turno: reabrir la conversación dos veces sin decidir no pinta dos avisos del mismo corte.
    val noticeId: String get() = InterruptedTurns.NOTICE_PREFIX + turnId
}

/** Una marca por conversación; la de un turno nuevo sustituye a la anterior. */
internal class InterruptedTurnStore(private val folder: File) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun file(conversationId: String) = conversationId.takeIf { SAFE_ID.matches(it) }?.let { File(folder, "$it.json") }

    @Synchronized fun begin(turn: InterruptedTurn) {
        val target = file(turn.conversationId) ?: return
        folder.mkdirs()
        val temp = File(folder, target.name + ".tmp")
        temp.writeText(json.encodeToString(turn), Charsets.UTF_8)
        // En Android renameTo sustituye de forma atómica; en Windows (pruebas JVM) no pisa un archivo existente.
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) { temp.delete(); error("No se pudo guardar la marca del turno.") }
        }
    }

    @Synchronized fun get(conversationId: String): InterruptedTurn? {
        val source = file(conversationId)?.takeIf { it.isFile } ?: return null
        return runCatching { json.decodeFromString<InterruptedTurn>(source.readText(Charsets.UTF_8)) }.getOrNull()
            ?.takeIf { it.conversationId == conversationId }
    }

    /** Solo quita la marca de [turnId]: el final tardío de un turno no borra la del turno que ya empezó después. */
    @Synchronized fun clear(conversationId: String, turnId: String) {
        if (get(conversationId)?.turnId == turnId) file(conversationId)?.delete()
    }

    private companion object { val SAFE_ID = Regex("[A-Za-z0-9_-]+") }
}

internal object InterruptedTurns {
    const val NOTICE_PREFIX = "interrupted_"
    const val NOTICE_TEXT = "El turno se cortó."
    const val TECHNICAL = "Android cerró Nexus mientras el agente respondía. La petición no se ha reenviado sola."

    fun isNotice(id: String) = id.startsWith(NOTICE_PREFIX)

    /** Si algún adjunto ya no está, el aviso lo dice: Reintentar mandará lo que queda ([kept] adjuntos y el texto). */
    fun noticeText(lost: Int, kept: Int): String {
        if (lost <= 0) return NOTICE_TEXT
        val what = if (lost == 1) "Un adjunto ya no está" else "$lost adjuntos ya no están"
        return "$NOTICE_TEXT $what; Reintentar enviará ${if (kept > 0) "el resto" else "solo el texto"}."
    }

    /** El aviso va al final del chat, una sola vez por turno cortado. */
    fun withNotice(items: List<ChatItem>, turn: InterruptedTurn, text: String, now: Long): List<ChatItem> =
        if (items.any { it.id == turn.noticeId }) items
        else items + ChatItem.Notice(turn.noticeId, text, error = true, at = now)
}
