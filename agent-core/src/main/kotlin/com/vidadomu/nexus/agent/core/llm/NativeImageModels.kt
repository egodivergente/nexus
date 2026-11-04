package com.vidadomu.nexus.agent.core.llm

/** Readable names for the image engines of the bridge brains, and the ones the user vetoes. */
object NativeImageModels {
    private val LITE = Regex("(?i)(^|[-_. ])lite($|[-_. ])")

    /** The user does not want Nano Banana Lite: its images are discarded instead of reaching the library. */
    fun isRejected(model: String?): Boolean = model != null && LITE.containsMatchIn(model)

    fun label(model: String?): String? {
        val raw = model?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val m = raw.lowercase()
        return when {
            m.startsWith("gemini-3.1-flash-image") || m.startsWith("gemini-3.1-flash-lite-image") ->
                if (isRejected(m)) "Nano Banana 2 Lite" else "Nano Banana 2"
            m.startsWith("gemini-3-pro-image") -> "Nano Banana Pro"
            m.startsWith("gemini-2.5-flash-image") -> "Nano Banana"
            m.startsWith("gpt-image") -> m.removePrefix("gpt-image").trim('-', ' ').let { if (it.isEmpty()) "GPT Image" else "GPT Image $it" }
            else -> raw
        }
    }
}
