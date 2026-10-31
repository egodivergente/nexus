package com.vidadomu.nexus.agent.core.storage

/**
 * Finds and rewrites mentions of private media in stored text (JSON, preferences, markdown in chats).
 *
 * Nexus stores the same file as `file:///data/user/0/<package>/files/...`, as a bare path, with `/` escaped as `\/`
 * (org.json) and percent-encoded by `Uri.fromFile`. Paths are returned relative to the app data directory
 * (`files/agent_media/x.jpg`, `cache/y.png`).
 */
class MediaReferences(packageName: String) {
    private val pattern = Regex(Regex.escape(packageName) + """(?:/|\\/)((?:files|cache)(?:/|\\/)(?:[^"'\s<>()\[\]{},\\`*|]|\\/)+)""")

    fun find(text: String): Set<String> = pattern.findAll(text).mapNotNull { normalise(it.groupValues[1]) }.toSet()

    /** Replaces every mention whose normalised path is a key of [replacements]; returns the new text and how many changed. */
    fun rewrite(text: String, replacements: Map<String, String>): Pair<String, Int> {
        if (replacements.isEmpty()) return text to 0
        var changed = 0
        val next = pattern.replace(text) { match ->
            val raw = match.groupValues[1]
            val target = normalise(raw)?.let(replacements::get) ?: return@replace match.value
            changed++
            // Keep what precedes the relative path (package and separator) and the store's own slash escaping.
            val prefix = match.value.substring(0, match.value.length - raw.length)
            prefix + (if (raw.contains("\\/")) encode(target).replace("/", "\\/") else encode(target))
        }
        return next to changed
    }

    companion object {
        /** File names Nexus creates for media. Names outside this set (spaces, accents) are never deleted automatically. */
        val SAFE_NAME = Regex("[A-Za-z0-9_.-]{1,200}")
        private val NAME_TOKEN = Regex("[A-Za-z0-9_.%-]{3,240}")

        /** Every file-name-like token in [text], decoded: a conservative "is this name mentioned anywhere" index. */
        fun names(text: String, into: MutableSet<String> = HashSet()): MutableSet<String> {
            for (match in NAME_TOKEN.findAll(text.replace("\\/", "/"))) {
                val token = match.value.trim('.', '-')
                if ('.' !in token) continue
                into += token
                if ('%' in token) decode(token)?.let { into += it }
            }
            return into
        }

        fun normalise(raw: String): String? {
            val unescaped = raw.replace("\\/", "/").trimEnd('.', ':', ';', '!', '?')
            val decoded = decode(unescaped) ?: return null
            if (decoded.split('/').any { it == ".." || it == "." || it.isEmpty() }) return null
            return decoded
        }

        /** Strict percent-decoding (UTF-8). Unlike URLDecoder, `+` stays `+`. */
        fun decode(value: String): String? {
            if ('%' !in value) return value
            val bytes = java.io.ByteArrayOutputStream(); var index = 0
            while (index < value.length) {
                val c = value[index]
                if (c == '%') {
                    if (index + 2 >= value.length) return null
                    val byte = value.substring(index + 1, index + 3).toIntOrNull(16) ?: return null
                    bytes.write(byte); index += 3
                } else { bytes.write(c.toString().toByteArray(Charsets.UTF_8)); index++ }
            }
            return bytes.toString(Charsets.UTF_8.name())
        }

        /** Same encoding as Android's Uri.fromFile for the characters Nexus file names can contain. */
        fun encode(path: String): String = buildString {
            for (byte in path.toByteArray(Charsets.UTF_8)) {
                val c = byte.toInt() and 0xff
                if (c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c.toChar() in "/_-.~!*'()")
                    append(c.toChar()) else append('%').append("%02X".format(c))
            }
        }
    }
}
