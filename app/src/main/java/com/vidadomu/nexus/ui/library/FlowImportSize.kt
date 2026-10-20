package com.vidadomu.nexus.ui.library

/** Values accepted by Flow's existing download menu for imported media. */
internal object FlowImportSize {
    val options = listOf(null to "Rápido", "1K" to "1K", "2K" to "2K", "4K" to "4K")

    fun requestedSize(selection: String?, kind: String): String? = selection?.let {
        if (kind != "video") it else when (it) {
            "1K" -> "720p"
            "2K" -> "1080p"
            "4K" -> "4K"
            else -> error("Tamaño de Flow no válido: $it")
        }
    }

    fun fallbackSize(kind: String): String = if (kind == "video") "720p" else "1K"
}
