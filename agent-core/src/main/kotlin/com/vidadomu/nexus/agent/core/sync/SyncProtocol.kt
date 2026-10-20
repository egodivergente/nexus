package com.vidadomu.nexus.agent.core.sync

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class SyncDocument(val kind: String, val name: String, val hash: String, val bytes: Long, val baseHash: String? = null)

@Serializable
data class SyncManifest(val protocol: Int = 1, val device: String, val documents: List<SyncDocument>, val scopes: Set<String> = emptySet())

/** Preferences retain their string type; only actual containers participate in structural merge.
 * Kotlin's element parser accepts bare words, whose toString() is not portable JSON.
 */
fun syncSettingValue(value: String): JsonElement {
    val number = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
    fun portable(element: JsonElement): Boolean = when (element) {
        is JsonObject -> element.values.all(::portable)
        is JsonArray -> element.all(::portable)
        is JsonPrimitive -> element.isString || element.content in setOf("null", "true", "false") || number.matches(element.content)
    }
    val start = value.trimStart().firstOrNull()
    if (start == '{' || start == '[') {
        val parsed = runCatching { Json.parseToJsonElement(value) }.getOrNull()
        if ((parsed is JsonObject || parsed is JsonArray) && portable(parsed)) return parsed
    }
    return JsonPrimitive(value)
}

/** The pairing key never travels over HTTP. Requests and streamed replies are authenticated GCM. */
object SyncCrypto {
    fun newKey(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    fun validKey(value: String): Boolean = runCatching { key(value).encoded.size == 32 }.getOrDefault(false)
    private fun key(value: String) = SecretKeySpec(Base64.getUrlDecoder().decode(value).also { require(it.size == 32) }, "AES")
    fun encrypt(output: OutputStream, secret: String, context: String = "nexus-sync-request-v1"): OutputStream {
        // Individually authenticated, ordered 64 KiB frames avoid buffering a whole video in RAM.
        val target = java.io.DataOutputStream(output)
        val streamId = ByteArray(16).also { SecureRandom().nextBytes(it); target.write(it) }
        return object : OutputStream() {
            private val buffer = ByteArray(65536)
            private var used = 0
            private var sequence = 0L
            private var closed = false
            private fun frame(size: Int) {
                val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.ENCRYPT_MODE, key(secret), GCMParameterSpec(128, iv))
                    updateAAD(context.toByteArray()); updateAAD(streamId)
                    updateAAD(java.nio.ByteBuffer.allocate(8).putLong(sequence++).array())
                }
                val encrypted = cipher.doFinal(buffer, 0, size)
                target.writeInt(encrypted.size); target.write(iv); target.write(encrypted)
                used = 0
            }
            override fun write(b: Int) { write(byteArrayOf(b.toByte())) }
            override fun write(b: ByteArray, off: Int, len: Int) {
                check(!closed)
                var pos = off; var remaining = len
                while (remaining > 0) {
                    val n = minOf(buffer.size - used, remaining)
                    b.copyInto(buffer, used, pos, pos + n); used += n; pos += n; remaining -= n
                    if (used == buffer.size) frame(used)
                }
            }
            override fun close() {
                if (closed) return
                try { if (used > 0) frame(used); frame(0); target.flush() }
                finally { closed = true; target.close() }
            }
        }
    }
    fun decrypt(input: InputStream, secret: String, context: String = "nexus-sync-request-v1"): InputStream {
        val source = java.io.DataInputStream(input)
        val streamId = ByteArray(16).also { source.readFully(it) }
        return object : InputStream() {
            private var buffer = ByteArray(0)
            private var position = 0
            private var sequence = 0L
            private var ended = false
            private fun availableFrame(): Boolean {
                if (position < buffer.size) return true
                if (ended) return false
                val length = source.readInt() // EOF before an authenticated terminal frame is an error.
                require(length in 16..65552)
                val iv = ByteArray(12).also { source.readFully(it) }
                val encrypted = ByteArray(length).also { source.readFully(it) }
                buffer = Cipher.getInstance("AES/GCM/NoPadding").run {
                    init(Cipher.DECRYPT_MODE, key(secret), GCMParameterSpec(128, iv))
                    updateAAD(context.toByteArray()); updateAAD(streamId)
                    updateAAD(java.nio.ByteBuffer.allocate(8).putLong(sequence++).array())
                    doFinal(encrypted)
                }
                position = 0
                if (buffer.isEmpty()) { ended = true; require(source.read() == -1); return false }
                return true
            }
            override fun read(): Int = if (availableFrame()) buffer[position++].toInt() and 255 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                if (!availableFrame()) return -1
                val n = minOf(len, buffer.size - position)
                buffer.copyInto(b, off, position, position + n); position += n; return n
            }
            override fun close() { source.close() }
        }
    }
    fun request(path: String, secret: String): String = java.io.ByteArrayOutputStream().let { out ->
        encrypt(out, secret).use { it.write("nexus-sync-v1:$path".toByteArray()) }
        Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }
    fun authorized(token: String, path: String, secret: String): Boolean = runCatching {
        decrypt(Base64.getUrlDecoder().decode(token).inputStream(), secret).use { it.readBytes().toString(Charsets.UTF_8) } == "nexus-sync-v1:$path"
    }.getOrDefault(false)
    fun hash(input: InputStream): String = input.use {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        while (true) { val n = it.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
    }
    fun hash(bytes: ByteArray): String = hash(bytes.inputStream())
    fun safeFile(root: File, relative: String): File {
        require(relative.isNotBlank() && !relative.startsWith('/') && '\\' !in relative)
        val base = root.canonicalFile
        return File(base, relative).canonicalFile.also { require(it.toPath().startsWith(base.toPath()) && it != base) }
    }
    fun relativePath(root: File, file: File): String {
        val base = root.canonicalFile; val target = file.canonicalFile
        require(target.toPath().startsWith(base.toPath()) && target != base)
        return target.relativeTo(base).invariantSeparatorsPath
    }
}

data class SyncMergeResult(val value: JsonElement, val conflicts: List<String>)

/** Three-way merge: independent edits combine; concurrent scalar edits remain explicit conflicts.
 * The caller retains the complete incoming revision before committing a conflicted document.
 * Deletions are opt-in. Sessions and current chat selection are deliberately device-local.
 */
object SyncMerge {
    fun merge(local: JsonElement, remote: JsonElement, base: JsonElement? = null, deletions: Boolean = false,
        localBase: JsonElement? = base): SyncMergeResult {
        val conflicts = mutableListOf<String>()
        fun walk(l: JsonElement?, r: JsonElement?, b: JsonElement?, lb: JsonElement?, path: String): JsonElement? {
            if (l == r) return l
            if (r == b) return l
            if (r == null) return if (l == lb && deletions) null else l
            if (l == null) return r
            if (b == null && l == JsonNull) return r
            if (b == null && r == JsonNull) return l
            if (l is JsonObject && r is JsonObject) {
                val original = b as? JsonObject
                val ownOriginal = lb as? JsonObject
                return JsonObject((l.keys + r.keys).mapNotNull { k ->
                    walk(l[k], r[k], original?.get(k), ownOriginal?.get(k), "$path/$k")?.let { k to it }
                }.toMap())
            }
            if (l is JsonArray && r is JsonArray) {
                val all = l + r
                if (all.all { it is JsonObject && it["id"] is JsonPrimitive }) {
                    fun byId(a: JsonArray?) = a.orEmpty().associateBy { it.jsonObject.getValue("id").jsonPrimitive.content }
                    val left = byId(l); val right = byId(r); val old = byId(b as? JsonArray); val ownOld = byId(lb as? JsonArray)
                    return JsonArray((left.keys + right.keys).mapNotNull { id -> walk(left[id], right[id], old[id], ownOld[id], "$path/$id") })
                }
                // Rules, notes and references have no globally assigned record id.
                return if (l == lb && deletions) r else JsonArray((l + r).distinct())
            }
            if (l == lb) return r
            conflicts += path
            return l
        }
        return SyncMergeResult(walk(local, remote, base, localBase, "") ?: JsonNull, conflicts)
    }
}
