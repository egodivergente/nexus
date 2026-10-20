package com.vidadomu.nexus.ui.flow

import kotlin.math.max
import kotlin.math.roundToInt

enum class FlowExportFormat(val label: String) {
    ORIGINAL("Original"),
    PNG("PNG"),
    JPG("JPG"),
    WEBP("WebP")
}

enum class FlowExportSize(val label: String, val longEdge: Int?) {
    ORIGINAL("Original", null),
    UHD_4K("4K", 3840),
    QHD_2K("2K", 2048),
    FHD_1080("1080", 1080),
    HD_720("720", 720)
}

data class FlowExportDimensions(val width: Int, val height: Int)

data class FlowExportPlan(
    val mimeType: String,
    val extension: String,
    val encodeAs: FlowExportFormat?,
    val target: FlowExportDimensions?,
    val fileName: String
) {
    val isPassThrough: Boolean get() = encodeAs == null
}

object FlowExport {
    const val JPG_QUALITY = 92
    const val WEBP_QUALITY = 90
    const val VIDEO_ORIGINAL_ONLY = "El puente solo entrega el vídeo original."

    fun targetSize(width: Int, height: Int, size: FlowExportSize): FlowExportDimensions {
        require(width > 0 && height > 0) { "Dimensiones no válidas: ${width}x$height" }
        val longEdge = size.longEdge ?: return FlowExportDimensions(width, height)
        val current = max(width, height)
        if (current <= longEdge) return FlowExportDimensions(width, height)
        val scale = longEdge.toDouble() / current
        return FlowExportDimensions(
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1)
        )
    }

    fun sampleSize(width: Int, height: Int, target: FlowExportDimensions): Int {
        var sample = 1
        while (width / (sample * 2) >= target.width && height / (sample * 2) >= target.height) sample *= 2
        return sample
    }

    fun formatForMime(mimeType: String): FlowExportFormat? = when (mimeType.lowercase().substringBefore(';').trim()) {
        "image/png" -> FlowExportFormat.PNG
        "image/jpeg", "image/jpg" -> FlowExportFormat.JPG
        "image/webp" -> FlowExportFormat.WEBP
        else -> null
    }

    fun mimeType(format: FlowExportFormat): String? = when (format) {
        FlowExportFormat.ORIGINAL -> null
        FlowExportFormat.PNG -> "image/png"
        FlowExportFormat.JPG -> "image/jpeg"
        FlowExportFormat.WEBP -> "image/webp"
    }

    fun extension(mimeType: String): String = when (mimeType.lowercase().substringBefore(';').trim()) {
        "image/png" -> "png"
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic", "image/heif" -> "heic"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        else -> if (mimeType.startsWith("video", ignoreCase = true)) "mp4" else "bin"
    }

    fun suggestedName(outputId: String, dimensions: FlowExportDimensions?, extension: String): String {
        val id = outputId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48).ifBlank { "resultado" }
        val size = dimensions?.let { "_${it.width}x${it.height}" } ?: ""
        return "nexus_flow_$id$size.$extension"
    }

    /**
     * Resolves what to write. A null [source] (unknown dimensions) forces a byte copy because
     * resizing or re-encoding without knowing the original size is not possible.
     */
    fun plan(
        outputId: String,
        originalMime: String,
        isVideo: Boolean,
        source: FlowExportDimensions?,
        format: FlowExportFormat,
        size: FlowExportSize
    ): FlowExportPlan {
        val originalFormat = formatForMime(originalMime)
        val fallbackMime = originalMime.ifBlank { if (isVideo) "video/mp4" else "application/octet-stream" }
        if (isVideo || source == null) {
            return FlowExportPlan(fallbackMime, extension(fallbackMime), null, source,
                suggestedName(outputId, source, extension(fallbackMime)))
        }
        val target = targetSize(source.width, source.height, size)
        val resized = target != source
        val requested = if (format == FlowExportFormat.ORIGINAL) originalFormat else format
        val encodeAs = when {
            requested == null && !resized -> null
            requested == null -> FlowExportFormat.PNG
            !resized && requested == originalFormat -> null
            else -> requested
        }
        val mime = encodeAs?.let { requireNotNull(mimeType(it)) } ?: fallbackMime
        val ext = extension(mime)
        return FlowExportPlan(mime, ext, encodeAs, target, suggestedName(outputId, target, ext))
    }
}
