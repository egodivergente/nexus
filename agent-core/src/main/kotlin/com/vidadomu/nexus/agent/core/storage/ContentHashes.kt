package com.vidadomu.nexus.agent.core.storage

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * SHA-256 of local media, remembered by (path, length, modification time).
 *
 * Drive sync used to re-read and re-copy every unsynchronised file on each 15-minute cycle, and imports had no way to
 * recognise bytes that were already stored. An unchanged file is now read once. An entry is only trusted while the
 * file keeps the same length and modification time, so an edited file is always hashed again.
 */
class ContentHashes(private val file: File?) {
    @Serializable private data class Entry(val length: Long, val modified: Long, val sha256: String)
    @Serializable private data class Stored(val version: Int = 1, val entries: Map<String, Entry> = emptyMap())

    private val entries = HashMap<String, Entry>()
    private var dirty = false

    init {
        file?.takeIf { it.isFile }?.let { stored ->
            runCatching { JSON.decodeFromString(Stored.serializer(), stored.readText()) }.getOrNull()?.let { entries.putAll(it.entries) }
        }
    }

    /** The remembered hash, only if the file still has the length and modification time it had when it was read. */
    @Synchronized fun cached(target: File): String? {
        val entry = entries[target.absolutePath] ?: return null
        return entry.sha256.takeIf { target.isFile && entry.length == target.length() && entry.modified == target.lastModified() }
    }

    fun sha256(target: File): String {
        cached(target)?.let { return it }
        val length = target.length(); val modified = target.lastModified()
        val sha = target.inputStream().use(::hash)
        // A file that changed while it was being read is hashed again next time instead of being cached wrongly.
        if (target.length() == length && target.lastModified() == modified) record(target, sha, length, modified)
        return sha
    }

    @Synchronized fun record(target: File, sha256: String, length: Long = target.length(), modified: Long = target.lastModified()) {
        require(SHA.matches(sha256))
        val next = Entry(length, modified, sha256)
        if (entries.put(target.absolutePath, next) != next) dirty = true
    }

    @Synchronized fun forget(target: File) { if (entries.remove(target.absolutePath) != null) dirty = true }

    /** A stored file with exactly these bytes (by hash, and length when known) that still matches its cached state. */
    @Synchronized fun find(sha256: String, length: Long?, accept: (File) -> Boolean = { true }): File? =
        entries.entries.asSequence().filter { it.value.sha256 == sha256 && (length == null || it.value.length == length) }
            .map { File(it.key) to it.value }
            .firstOrNull { (candidate, entry) -> candidate.isFile && candidate.length() == entry.length && candidate.lastModified() == entry.modified && accept(candidate) }
            ?.first

    @Synchronized fun flush() {
        val target = file ?: return
        if (!dirty) return
        entries.keys.removeAll { !File(it).isFile }
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, target.name + ".tmp")
        try {
            java.io.FileOutputStream(temporary).use { output ->
                output.write(JSON.encodeToString(Stored.serializer(), Stored(1, HashMap(entries))).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            dirty = false
        } finally { temporary.delete() }
    }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        val SHA = Regex("[a-f0-9]{64}")
        fun hash(input: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
