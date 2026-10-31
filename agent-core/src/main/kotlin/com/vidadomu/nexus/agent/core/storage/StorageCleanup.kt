package com.vidadomu.nexus.agent.core.storage

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

data class AreaUsage(val area: StorageArea, val files: Int, val bytes: Long)

/** What «Buscar duplicados» found. Nothing is changed until [StorageCleanup.free] runs with it. */
data class StorageAnalysis(val usage: List<AreaUsage>, val plan: DuplicatePlan, val hashedFiles: Int, val createdAt: Long) {
    val totalBytes: Long get() = usage.sumOf { it.bytes }
}

data class CleanupResult(val removed: Int, val bytes: Long, val kept: Int, val rewrites: Int, val report: File?)

/**
 * «Liberar espacio»: removes exact duplicate copies that Nexus made of the same file, keeping one.
 *
 * Order in [StorageCleanup.free], with Drive sync and peer sync held by the caller:
 * 1. A removal is dropped if its file or the copy that stays changed since the analysis.
 * 2. The library index, its backup and the saved conversations that are not open are rewritten to point to the copy
 *    that stays (identical bytes, so nothing that opens them can tell). Chat saves are paused only for this step.
 * 3. Every store is read again; a copy still referenced, or whose name appears in a store Nexus cannot rewrite
 *    (preferences, databases, open chats, drafts, background jobs, peer sync...), is kept.
 * 4. The rest are moved aside (`files/storage/pending/`), every store is read once more, and only copies nobody
 *    mentioned in between are deleted; the others go back. A crash leaves `pending.json`, and [restorePending] puts the
 *    files back on the next start. A report of each file (path, copy kept, size, SHA-256) is written.
 */
class StorageCleanup(private val scan: StorageScan, private val hashes: ContentHashes, private val clock: () -> Long = System::currentTimeMillis) {

    fun usage(files: List<StorageScan.Located> = scan.mediaFiles()): List<AreaUsage> =
        files.groupBy { it.area }.map { (area, list) -> AreaUsage(area, list.size, list.sumOf { it.file.length() / it.links }) }.sortedByDescending { it.bytes }

    /** Only files sharing length and extension with another one are hashed: the rest cannot have a duplicate. */
    fun analyse(indexText: String, cancelled: () -> Boolean = { false }, progress: (done: Int, total: Int) -> Unit = { _, _ -> }): StorageAnalysis {
        val located = scan.mediaFiles()
        // Hard-linked names share their bytes: removing one frees nothing, so they never take part.
        val candidates = located.filter { it.links == 1 }.groupBy { it.file.length() to DuplicatePlanner.extension(it.path) }.filter { it.key.first > 0 && it.value.size > 1 }.values.flatten()
        val stored = ArrayList<StoredFile>()
        candidates.forEachIndexed { done, item ->
            if (cancelled()) throw java.util.concurrent.CancellationException("analysis cancelled")
            progress(done, candidates.size)
            runCatching { StoredFile(item.path, item.file.length(), hashes.sha256(item.file), item.area) }.getOrNull()?.let(stored::add)
        }
        progress(candidates.size, candidates.size)
        runCatching { hashes.flush() }
        val plan = DuplicatePlanner.plan(stored, scan.fixedNames(), scan.referenced(sequenceOf(indexText)))
        return StorageAnalysis(usage(located), plan, stored.size, clock())
    }

    @Serializable private data class Entry(val path: String, val keep: String, val bytes: Long, val sha256: String, val result: String)
    @Serializable private data class Report(val version: Int = 1, val time: Long, val removed: Int, val bytes: Long, val rewrites: Int, val entries: List<Entry>)

    /**
     * [rewriteIndex] applies a text transform to every string of the library index in one atomic edit and returns how
     * many changed; [indexText] returns the index as stored after that edit. [rewriteOther] applies the same transform
     * to stores only the app can edit safely (the picker's last-use order).
     */
    suspend fun free(plan: DuplicatePlan, rewriteIndex: suspend ((String) -> String) -> Int, indexText: () -> String, reportDir: File,
                     rewriteOther: suspend ((String) -> String) -> Int = { 0 },
                     chatsPaused: suspend (suspend () -> Unit) -> Unit = { it() }): CleanupResult {
        restorePending(scan.dataDir)
        val entries = ArrayList<Entry>()
        fun keep(removal: Removal, reason: String) { entries += Entry(removal.file.path, removal.keep.path, removal.file.length, removal.file.sha256, reason) }
        fun unchanged(stored: StoredFile): Boolean {
            val file = File(scan.dataDir, stored.path)
            return file.isFile && file.length() == stored.length && runCatching { hashes.sha256(file) }.getOrNull() == stored.sha256
        }
        val valid = plan.removals.filter { removal ->
            when {
                !unchanged(removal.keep) -> { keep(removal, "kept_copy_changed"); false }
                !unchanged(removal.file) -> { keep(removal, "file_changed"); false }
                else -> true
            }
        }
        val replacements = valid.associate { it.file.path to it.keep.path }
        var rewrites = 0
        if (replacements.isNotEmpty()) {
            val transform = { value: String -> scan.references.rewrite(value, replacements).first }
            rewrites += rewriteIndex(transform)
            rewrites += rewriteOther(transform)
            chatsPaused { for (file in scan.rewritableFiles()) rewrites += rewriteFile(file, replacements) }
        }
        // Read everything again after the rewrite: anything that still mentions a copy keeps it.
        fun mentioned(): (Removal) -> String? {
            val fixed = scan.fixedNames(); val referenced = scan.referenced(sequenceOf(indexText()))
            return { r -> when { DuplicatePlanner.name(r.file.path) in fixed -> "mentioned_in_fixed_store"; r.file.path in referenced -> "still_referenced"; else -> null } }
        }
        val nothing: (Removal) -> String? = { null }
        val first = if (valid.isEmpty()) nothing else mentioned()
        val pendingDir = File(scan.dataDir, PENDING_DIR)
        val staged = ArrayList<Pair<Removal, File>>()
        val manifest = LinkedHashMap<String, String>()
        for (removal in valid) {
            val file = File(scan.dataDir, removal.file.path)
            val reason = first(removal) ?: when {
                scan.links(file) > 1 -> "hard_link"
                !unchanged(removal.keep) -> "kept_copy_changed"
                else -> null
            }
            if (reason != null) { keep(removal, reason); continue }
            pendingDir.mkdirs()
            val aside = File(pendingDir, "${clock()}-${staged.size}-${file.name}")
            if (aside.exists()) { keep(removal, "move_failed"); continue }
            manifest[scan.relative(aside)] = removal.file.path
            writeManifest(scan.dataDir, manifest)
            if (!file.renameTo(aside)) { manifest.remove(scan.relative(aside)); keep(removal, "move_failed"); continue }
            staged += removal to aside
        }
        var removed = 0; var bytes = 0L
        if (staged.isNotEmpty()) {
            val last = mentioned()
            for ((removal, aside) in staged) {
                val reason = last(removal)
                val original = File(scan.dataDir, removal.file.path)
                if (reason != null) {
                    keep(removal, if (aside.renameTo(original)) "mentioned_during_cleanup" else "restore_failed_see_pending")
                    if (original.isFile) manifest.remove(scan.relative(aside))
                } else if (aside.delete()) {
                    manifest.remove(scan.relative(aside))
                    hashes.forget(original); removed++; bytes += removal.file.length
                    entries += Entry(removal.file.path, removal.keep.path, removal.file.length, removal.file.sha256, "removed")
                } else keep(removal, "delete_failed_see_pending")
            }
            writeManifest(scan.dataDir, manifest)
        }
        runCatching { hashes.flush() }
        val report = runCatching {
            val target = File(reportDir, "cleanup-${clock()}.json")
            reportDir.mkdirs()
            val temporary = File(reportDir, target.name + ".tmp")
            temporary.writeText(JSON.encodeToString(Report.serializer(), Report(1, clock(), removed, bytes, rewrites, entries)))
            check(temporary.renameTo(target)); target
        }.getOrNull()
        return CleanupResult(removed, bytes, entries.size - removed, rewrites, report)
    }

    /** Atomic rewrite that keeps the modification time, so conversations do not jump to the top of the list. */
    private fun rewriteFile(file: File, replacements: Map<String, String>): Int {
        val text = runCatching { file.readText() }.getOrNull() ?: return 0
        val (next, count) = scan.references.rewrite(text, replacements)
        if (count == 0 || next == text) return 0
        val modified = file.lastModified()
        val temporary = File(file.parentFile, ".${file.name}.rewrite")
        try {
            java.io.FileOutputStream(temporary).use { it.write(next.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            try {
                java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temporary.delete() }
        if (modified > 0) file.setLastModified(modified)
        return count
    }

    companion object {
        private val JSON = Json { prettyPrint = true }
        private const val PENDING_DIR = "files/storage/pending"
        private const val MANIFEST = "files/storage/pending.json"
        private val MAP = MapSerializer(String.serializer(), String.serializer())

        private fun writeManifest(dataDir: File, entries: Map<String, String>) {
            val target = File(dataDir, MANIFEST)
            if (entries.isEmpty()) { target.delete(); return }
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, target.name + ".tmp")
            java.io.FileOutputStream(temporary).use { it.write(JSON.encodeToString(MAP, entries).toByteArray(Charsets.UTF_8)); it.fd.sync() }
            java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }

        /** Puts back copies a cleanup moved aside and could not finish (the app was closed in the middle). Returns how many. */
        fun restorePending(dataDir: File): Int {
            val target = File(dataDir, MANIFEST)
            if (!target.isFile) return 0
            val entries = runCatching { JSON.decodeFromString(MAP, target.readText()) }.getOrNull() ?: return 0
            var restored = 0; val left = LinkedHashMap<String, String>()
            for ((aside, original) in entries) {
                val from = File(dataDir, aside); val to = File(dataDir, original)
                if (!from.isFile) continue
                to.parentFile?.mkdirs()
                if (!to.exists() && from.renameTo(to)) restored++ else left[aside] = original
            }
            writeManifest(dataDir, left)
            return restored
        }
    }
}
