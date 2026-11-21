package com.vidadomu.nexus.agent.core.assets

import java.io.File
import java.net.URI

/** One Library tile: its index entries and every URI that may point at its bytes. */
data class DeletionTarget(val key: String, val name: String, val assetIds: Set<String>, val uris: Set<String>)

data class DeletionPlan(
    val removable: List<DeletionTarget>,
    /** Tile key → why it stays untouched. */
    val blocked: Map<String, String>,
    val files: List<File>,
    /** Files outside Nexus's private folder (gallery, Documents/ALEXIA…): only the reference goes. */
    val external: Int,
    /** Removable tiles whose file a chat still shows. */
    val inChats: Int,
    /** SHA-256 of the erased bytes, so sync does not fetch them back. */
    val hashes: Set<String> = emptySet(),
)

/**
 * Real deletion for the Library. Only files inside Nexus's own folders are erased, one by one and never
 * recursively; anything else (gallery, Documents/ALEXIA shared with Alexia, content://) keeps its bytes.
 */
class LibraryFileDeletion(private val filesDir: File, roots: List<String> = ROOTS) {
    companion object {
        val ROOTS = listOf("agent_media", "agent_exports", "agent_generated", "custom_outputs", "cut/exports",
            "assistant/sync/workspaces", "device-sync/blobs")
        private val HASH = Regex("[a-f0-9]{64}")
    }
    private val allowed = roots.map { File(filesDir, it).canonicalPath + File.separator }
    private val syncWorkspaces = File(filesDir, "assistant/sync/workspaces").canonicalPath + File.separator

    fun privateFile(uri: String): File? {
        val raw = when {
            uri.startsWith("file:") -> runCatching { File(URI(uri)) }.getOrNull()
            File(uri).isAbsolute -> File(uri)
            else -> null
        } ?: return null
        val file = runCatching { raw.canonicalFile }.getOrNull() ?: return null
        // A Drive workspace also holds its journal: only the copies in its blobs folder are media.
        if (file.path.startsWith(syncWorkspaces) && file.parentFile?.name != "blobs") return null
        return file.takeIf { f -> allowed.any { f.path.startsWith(it) } && !f.isDirectory }
    }

    /** [inUse]: URIs that something outside the index still shows (other generated results, for instance). */
    fun plan(index: IndexData, targets: List<DeletionTarget>, chatsDir: File? = null, cutProjectsDir: File? = null,
        inUse: Collection<String> = emptyList()): DeletionPlan {
        val deletingIds = targets.flatMap { it.assetIds }.toSet()
        val otherUris = index.assets.filter { !it.libraryDeleted && it.id !in deletingIds }.map { it.uri }.toSet() + inUse
        val otherFiles = otherUris.mapNotNull(::privateFile).toSet()
        val storyIds = index.story.links.map { it.assetId }.toSet()
        val identityIds = index.characters.flatMap { it.referenceAssetIds }.toSet()
        val hashOf = mutableMapOf<String, String?>()
        // Drive and device sync keep their own copy of each file, named after its SHA-256: those go too.
        val filesOf = targets.associate { t ->
            val own = t.uris.mapNotNull(::privateFile)
            val hashes = t.uris.mapNotNull { u -> hashOf.getOrPut(u) { contentHash(u) } }
            t.key to (own + hashes.flatMap(::syncCopies)).distinct()
        }
        val names = filesOf.values.flatten().map { it.name }.toSet()
        val inCut = mentioned(cutProjectsDir, names) { it.name == "project.json" }
        val inChat = mentioned(chatsDir, names) { it.extension == "json" }
        val blocked = linkedMapOf<String, String>()
        for (t in targets) {
            val files = filesOf.getValue(t.key)
            val reason = when {
                t.assetIds.any { it in storyIds } -> "se usa en el guion"
                t.assetIds.any { it in identityIds } -> "es referencia de un personaje"
                t.uris.any { it in otherUris } || files.any { it in otherFiles } -> "otro elemento de la Biblioteca usa el mismo archivo"
                files.any { it.name in inCut } -> "se usa en un montaje de Nexus Cut"
                else -> null
            }
            if (reason != null) blocked[t.key] = reason
        }
        val removable = targets.filter { it.key !in blocked }
        val protectedFiles = targets.filter { it.key in blocked }.flatMap { filesOf.getValue(it.key) }.toSet()
        val files = removable.flatMap { filesOf.getValue(it.key) }.distinct().filter { it !in protectedFiles }
        val external = removable.sumOf { t -> t.uris.count { u -> privateFile(u) == null && isLocalPath(u) } }
        val inChats = removable.count { t -> filesOf.getValue(t.key).any { it.name in inChat } }
        val hashes = removable.flatMap { t -> t.uris.mapNotNull { hashOf[it] } }.toSet()
        return DeletionPlan(removable, blocked, files, external, inChats, hashes)
    }

    /** Re-checks every path right before erasing it. Returns how many are gone. */
    fun delete(files: List<File>): Int = files.count { f ->
        val safe = privateFile(f.toURI().toString()) ?: return@count false
        !safe.exists() || safe.delete()
    }

    private fun contentHash(uri: String): String? {
        val file = when {
            uri.startsWith("file:") -> runCatching { File(URI(uri)) }.getOrNull()
            File(uri).isAbsolute -> File(uri)
            else -> null
        }?.takeIf { it.isFile } ?: return null
        val named = file.name.substringBefore('.')
        if (named.matches(HASH)) return named
        return runCatching { com.vidadomu.nexus.agent.core.sync.SyncBlobs.hash(file) }.getOrNull()
    }

    private fun syncCopies(hash: String): List<File> {
        val drive = File(filesDir, "assistant/sync/workspaces").listFiles().orEmpty().flatMap { workspace ->
            File(workspace, "blobs").listFiles().orEmpty().filter { it.isFile && it.name.substringBefore('.') == hash }
        }
        val device = File(filesDir, "device-sync/blobs/$hash").takeIf { it.isFile }
        return (drive + listOfNotNull(device)).mapNotNull { privateFile(it.toURI().toString()) }
    }

    private fun isLocalPath(uri: String) = uri.startsWith("file:") || uri.startsWith("content:") || File(uri).isAbsolute

    // File names are unique (UUID or content hash), so matching the name covers every URI spelling.
    // An unreadable document counts as a use: better to keep a file than to break what shows it.
    private fun mentioned(dir: File?, names: Set<String>, accept: (File) -> Boolean): Set<String> {
        if (dir == null || names.isEmpty()) return emptySet()
        val found = mutableSetOf<String>()
        for (doc in dir.walkTopDown().maxDepth(2).filter { it.isFile && accept(it) }) {
            val pending = names - found
            if (pending.isEmpty()) break
            val read = runCatching { doc.bufferedReader().useLines { lines -> lines.forEach { line -> pending.filterTo(found) { it in line } } } }
            if (read.isFailure) found += pending
        }
        return found
    }
}
