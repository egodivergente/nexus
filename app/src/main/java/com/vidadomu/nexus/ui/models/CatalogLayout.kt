package com.vidadomu.nexus.ui.models

internal fun catalogColumnCount(preferred: Int, widthDp: Int, fontScale: Float): Int {
    val minimumWidth = if(fontScale >= 1.3f) 180 else 144
    return minOf(preferred.coerceIn(1, 6), ((widthDp - 24) / minimumWidth).coerceAtLeast(1))
}
