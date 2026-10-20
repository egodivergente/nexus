package com.vidadomu.nexus.assistant

/** VideoSize already applies rotation on modern decoders; older decoders report unappliedRotationDegrees. */
internal fun mediaAspectRatio(width: Int?, height: Int?, pixelRatio: Float = 1f, rotation: Int = 0): Float? {
    if (width == null || height == null || width <= 0 || height <= 0 || !pixelRatio.isFinite() || pixelRatio <= 0) return null
    val ratio = width.toFloat() * pixelRatio / height
    return (if (rotation % 180 != 0) 1f / ratio else ratio).takeIf { it.isFinite() && it > 0 }
}
