package com.vidadomu.nexus.cut

import com.vidadomu.nexus.agent.core.log.Redactor
import com.vidadomu.nexus.cut.storage.AtomicFile
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class CutChatRole { USER, ASSISTANT, NOTICE }

@Serializable
data class CutChatMessage(
    val id: String,
    val role: CutChatRole,
    val text: String,
    val at: Long,
    val by: String? = null,
    val error: Boolean = false,
)

/** A marker for an in-flight user message, never a saved request or an executable tool call. */
@Serializable
data class PendingCutChatTurn(val userMessageId: String, val startedAt: Long)

@Serializable
data class CutChatDocument(
    val schema: Int = 1,
    val messages: List<CutChatMessage> = emptyList(),
    val draft: String = "",
    val pending: PendingCutChatTurn? = null,
) {
    /** Only answered text turns enter a new provider conversation. Partial/failed replies stay in the UI. */
    fun completedTurns(): List<Pair<String, String>> {
        val turns = mutableListOf<Pair<String, String>>()
        var user: CutChatMessage? = null
        for (message in messages) when (message.role) {
            CutChatRole.USER -> user = message
            CutChatRole.ASSISTANT -> if (!message.error && message.text.isNotBlank()) {
                user?.takeIf { it.id != pending?.userMessageId }?.let {
                    turns += "user" to Redactor.redact(it.text)
                    turns += "assistant" to Redactor.redact(message.text)
                }
                user = null
            }
            CutChatRole.NOTICE -> Unit
        }
        return turns
    }
}

/**
 * The montage's own transcript. It has no reference to the Assistant conversation store, credentials,
 * provider messages or tools. Replacement uses the same verified, atomic writer as Cut projects.
 */
class CutChatStore(
    projectsRoot: File,
    projectId: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    val file: File
    private val lock: Any

    init {
        require(projectId.matches(Regex("[A-Za-z0-9_-]+"))) { "Identificador de montaje no válido." }
        val root = projectsRoot.canonicalFile
        val directory = File(root, projectId).canonicalFile
        require(directory.parentFile == root) { "El chat debe permanecer dentro del montaje." }
        file = File(directory, "chat-v1.json")
        lock = locks.getOrPut(file.path) { Any() }
    }

    /** Recovery is saved before returning; reopening never repeats the interrupted request or notice. */
    fun load(): CutChatDocument = synchronized(lock) {
        val backup = AtomicFile.backupOf(file)
        val loaded = when {
            file.exists() -> runCatching { decode(file.readBytes()) }.getOrElse { primary ->
                if (backup.exists()) runCatching { decode(backup.readBytes()) }.getOrElse {
                    throw IOException("No se pudo abrir el chat del montaje ni su copia de seguridad.", primary)
                } else throw IOException("No se pudo abrir el chat del montaje.", primary)
            }
            backup.exists() -> decode(backup.readBytes())
            else -> CutChatDocument()
        }.redacted()
        val pending = loaded.pending ?: return@synchronized loaded
        val interruptedUser = loaded.messages.firstOrNull { it.id == pending.userMessageId && it.role == CutChatRole.USER }
        val recovered = loaded.copy(
            draft = loaded.draft.ifBlank { interruptedUser?.text.orEmpty() },
            pending = null,
            messages = loaded.messages + CutChatMessage(
                newId(), CutChatRole.NOTICE,
                "La petición anterior se interrumpió. Los cambios ya aplicados se conservan; no se ha reenviado. Revisa el montaje antes de reintentar.",
                clock(),
            ),
        )
        save(recovered)
        recovered
    }

    fun save(document: CutChatDocument) = synchronized(lock) {
        require(document.schema == 1) { "Versión de chat no compatible." }
        val clean = document.redacted()
        AtomicFile.write(file, json.encodeToString(clean).toByteArray(Charsets.UTF_8)) { decode(it) }
    }

    private fun decode(bytes: ByteArray): CutChatDocument = json.decodeFromString<CutChatDocument>(bytes.toString(Charsets.UTF_8)).also {
        require(it.schema == 1) { "Versión de chat no compatible." }
        require(it.messages.map { message -> message.id }.distinct().size == it.messages.size) { "El chat contiene mensajes duplicados." }
    }

    private fun CutChatDocument.redacted() = copy(
        draft = Redactor.redact(draft),
        messages = messages.map { it.copy(text = Redactor.redact(it.text), by = it.by?.let(Redactor::redact)) },
    )

    private companion object {
        val locks = ConcurrentHashMap<String, Any>()
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
