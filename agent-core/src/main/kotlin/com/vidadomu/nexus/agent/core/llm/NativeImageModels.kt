package com.vidadomu.nexus.agent.core.llm

/** Readable names for the image engines of the bridge brains. */
object NativeImageModels {
    private val LITE = Regex("(?i)(^|[-_. ])lite($|[-_. ])")

    /** Lite images are still delivered (something beats nothing), but the user is told a stronger engine may do better. */
    fun isLite(model: String?): Boolean = model != null && LITE.containsMatchIn(model)

    fun label(model: String?): String? {
        val raw = model?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val m = raw.lowercase()
        return when {
            m.startsWith("gemini-3.1-flash-image") || m.startsWith("gemini-3.1-flash-lite-image") ->
                if (isLite(m)) "Nano Banana 2 Lite" else "Nano Banana 2"
            m.startsWith("gemini-3-pro-image") -> "Nano Banana Pro"
            m.startsWith("gemini-2.5-flash-image") -> "Nano Banana"
            m.startsWith("gpt-image") -> m.removePrefix("gpt-image").trim('-', ' ').let { if (it.isEmpty()) "GPT Image" else "GPT Image $it" }
            else -> raw
        }
    }
}
