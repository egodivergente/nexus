package com.vidadomu.nexus.ui.output

import com.vidadomu.nexus.core.model.GenerationOutput
import com.vidadomu.nexus.core.model.Modality

data class OutputExportSpec(val mimeType: String, val fileName: String)

fun outputExportSpec(output: GenerationOutput): OutputExportSpec {
    val fallback = when (output.type) {
        Modality.IMAGE -> "image/png"
        Modality.VIDEO -> "video/mp4"
        Modality.AUDIO -> "audio/mpeg"
        Modality.CHAT -> "text/plain"
    }
    val mime = if (output.type == Modality.CHAT) "text/plain" else output.mimeType
        .substringBefore(';').trim().takeIf { it.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")) } ?: fallback
    val extension = when (mime) {
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/png" -> "png"
        "video/webm" -> "webm"
        "video/mp4" -> "mp4"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/ogg" -> "ogg"
        "audio/mpeg" -> "mp3"
        "text/plain" -> "txt"
        else -> "bin"
    }
    val id = output.id.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(48).ifBlank { "resultado" }
    return OutputExportSpec(mime, "Nexus-$id.$extension")
}
