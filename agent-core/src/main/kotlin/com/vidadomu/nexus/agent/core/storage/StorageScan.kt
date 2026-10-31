package com.vidadomu.nexus.agent.core.storage

import java.io.File

/**
 * Read-only view of the app data directory (`/data/data/<package>`) used by «Liberar espacio».
 *
 * Text stores are split in three groups:
 * - rewritable: saved conversations that are not open and their migration backups (plain JSON files Nexus owns);
 *   the library index is rewritten through AssetIndex, not here.
 * - ignored: Drive/peer sync bookkeeping, the conversation list cache, the picker's last-use order and this feature's
 *   own files. They describe remote state, are rebuilt or only sort, and never open a local file.
 * - fixed: everything else (preferences, databases, drafts, boards, background jobs, open conversations...). A file
 *   name mentioned there is never removed, in any form.
 * [databaseText] turns a database file into its text and blob contents (SQLite on Android, raw bytes elsewhere).
 * [linkCount] returns a file's hard link count (Os.stat on Android).
 */
class StorageScan(val dataDir: File, packageName: String, private val openConversations: Set<String>,
                  private val linkCount: (File) -> Int = { 1 }, private val databaseText: (File) -> Sequence<String>) {
    val references = MediaReferences(packageName)

    /** [links] > 1: a hard link (a renamed export keeps its old name that way). Never removed: it frees nothing. */
    data class Located(val path: String, val file: File, val area: StorageArea, val links: Int = 1)

    fun links(file: File): Int = runCatching { linkCount(file) }.getOrDefault(1).coerceAtLeast(1)

    fun relative(file: File): String = file.absoluteFile.relativeTo(dataDir.absoluteFile).invariantSeparatorsPath

    fun area(path: String): StorageArea? = when {
        Regex("files/assistant/sync/workspaces/[^/]+/blobs/.+").matches(path) -> StorageArea.DRIVE_COPY
        path.startsWith("files/agent_media/") -> StorageArea.ATTACHMENTS
        path.startsWith("files/agent_exports/") -> StorageArea.EXPORTS
        path.startsWith("files/device-sync/blobs/") -> StorageArea.DEVICE_SYNC
        path.startsWith("files/draft_media/") -> StorageArea.DRAFTS
        path.startsWith("cache/") -> StorageArea.CACHE
        path.startsWith("files/custom_outputs/") -> StorageArea.OTHER
        else -> null
    }

    /** Media files, without partial downloads, temporary files or bookkeeping JSON. */
    fun mediaFiles(): List<Located> {
        val roots = listOf("files/agent_media", "files/agent_exports", "files/device-sync/blobs", "files/draft_media", "cache", "files/custom_outputs") +
            File(dataDir, "files/assistant/sync/workspaces").listFiles().orEmpty().filter { it.isDirectory }.map { "files/assistant/sync/workspaces/${it.name}/blobs" }
        return roots.map { File(dataDir, it) }.filter { it.isDirectory }.flatMap { root ->
            root.walkTopDown().filter { it.isFile && !it.name.startsWith(".") && !it.name.endsWith(".part") && !it.name.endsWith(".partial") &&
                !it.name.endsWith(".tmp") && it.extension.lowercase() != "json" }.mapNotNull { file ->
                val path = relative(file); area(path)?.let { Located(path, file, it, links(file)) }
            }.toList()
        }
    }

    private fun isText(file: File) = file.extension.lowercase() in setOf("json", "xml", "txt", "md", "csv", "html", "enc", "skill", "yaml", "yml")
    private fun isDatabase(file: File) = file.name.endsWith(".db") || file.name.endsWith(".db-wal") || file.name.endsWith("workdb") || file.name.endsWith("workdb-wal")

    fun ignored(path: String): Boolean =
        path.startsWith("files/assistant/sync/") || path == "files/assistant/conversations-index-v1.json" ||
            path.startsWith("files/storage/") || path.startsWith("files/device-sync/conflicts/") ||
            // Last-use time per URI, only to sort the media picker; the app moves those entries itself (RECENT_MEDIA).
            path == "shared_prefs/$RECENT_MEDIA.xml" ||
            Regex("files/assistant/index\\.json(\\.bak|\\.tmp|\\.corrupt-\\d+)?").matches(path)

    fun rewritable(path: String): Boolean {
        Regex("files/assistant/conversations/([A-Za-z0-9_-]+)\\.json").matchEntire(path)?.let { return it.groupValues[1] !in openConversations }
        return path.startsWith("files/assistant/migration-v2-originals/") && path.endsWith(".json")
    }

    private fun textFiles(): Sequence<File> = dataDir.walkTopDown()
        .onEnter { dir -> relative(dir).let { it.isEmpty() || area("$it/x") == null } }
        .filter { it.isFile && area(relative(it)) == null }

    /** Every file name mentioned in a fixed store. */
    fun fixedNames(): MutableSet<String> {
        val names = HashSet<String>()
        for (file in textFiles()) {
            val path = relative(file)
            if (ignored(path) || rewritable(path)) continue
            when {
                isDatabase(file) -> databaseText(file).forEach { MediaReferences.names(it, names) }
                isText(file) && file.length() <= 64L * 1024 * 1024 -> MediaReferences.names(file.readText(), names)
            }
        }
        return names
    }

    fun rewritableFiles(): List<File> = textFiles().filter { rewritable(relative(it)) && isText(it) }.toList()

    /** Paths referenced by any rewritable store or by [extra] text (the encoded library index). */
    fun referenced(extra: Sequence<String>): Set<String> {
        val out = HashSet<String>()
        rewritableFiles().forEach { out += references.find(it.readText()) }
        extra.forEach { out += references.find(it) }
        return out
    }

    companion object {
        const val RECENT_MEDIA = "recent_media_v1"

        /** Conversation ids that are on screen or still saving: the active one recorded in chat-v1.json. */
        fun activeConversation(dataDir: File): String? = runCatching {
            val saved = kotlinx.serialization.json.Json.parseToJsonElement(File(dataDir, "files/assistant/chat-v1.json").readText())
            (saved as kotlinx.serialization.json.JsonObject)["conversationId"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content }
        }.getOrNull()?.takeIf { Regex("[A-Za-z0-9_-]+").matches(it) }
    }
}
