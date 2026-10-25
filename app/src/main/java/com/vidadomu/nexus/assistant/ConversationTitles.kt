package com.vidadomu.nexus.assistant

import android.content.Context

/**
 * Conversation titles: a cleaner automatic title from the first message, and the user's own name when set.
 * Custom names live apart from the chat files, so renaming never rewrites history or sync data.
 */
internal class ConversationTitles(context: Context) {
    private val prefs = context.getSharedPreferences("nexus_conversation_titles", Context.MODE_PRIVATE)
    fun custom(id: String): String? = prefs.getString(id, null)?.takeIf { it.isNotBlank() }
    fun rename(id: String, title: String?) {
        val clean = title?.trim()?.take(80)
        prefs.edit().apply { if (clean.isNullOrBlank()) remove(id) else putString(id, clean) }.apply()
    }
    fun display(id: String, automatic: String): String = custom(id) ?: smart(automatic)

    companion object {
        private val FILLER = Regex("^(?:(?:hola|buenas|buenos días|buenas tardes|oye|hey|vale|ok|bueno|por favor|porfa|nexus)[,.!:\\s]+)+", RegexOption.IGNORE_CASE)
        private val ASK = Regex("^(?:(?:quiero|necesito|me gustaría|querría) que (?:me )?|(?:puedes|podrías|podrias) |ayúdame a |ayudame a )", RegexOption.IGNORE_CASE)
        private val MARKUP = Regex("[*_`#>\\[\\]]+")

        /** «hola, puedes hacerme un vídeo de un faro…» → «Hacerme un vídeo de un faro…». Never empty. */
        fun smart(raw: String, max: Int = 48): String {
            val sync = raw.startsWith("↔ ")
            var text = raw.removePrefix("↔ ").replace(MARKUP, "").replace(Regex("\\s+"), " ").trim()
            text = text.replace(FILLER, "").replace(ASK, "").trim()
            text = text.split(Regex("(?<=[.!?¿¡])\\s|\\n")).firstOrNull { it.isNotBlank() }?.trim()?.trimEnd('.', '!', '?') ?: text
            if (text.length > max) text = text.take(max).substringBeforeLast(' ').ifBlank { text.take(max) } + "…"
            text = text.replaceFirstChar { it.uppercase() }.ifBlank { raw.ifBlank { "Nueva conversación" } }
            return (if (sync) "↔ " else "") + text
        }
    }
}
