package com.vidadomu.nexus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Shared visual scale. Geometry of icons, borders and media is not spacing. */
object NexusTokens {
    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space24 = 24.dp
    val space32 = 32.dp
    val screenMargin = space16
    val tabletMargin = 20.dp
    val galleryGap = 2.dp
    val minimumTouch = 48.dp
    val topBarHeight = 40.dp
    val thumbnail = RoundedCornerShape(8.dp)
    val field = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val prompt = RoundedCornerShape(16.dp)
    val action = RoundedCornerShape(28.dp)
    const val pressDuration = 120
    const val contentDuration = 180
    val filter = RoundedCornerShape(50)
    val shapes = Shapes(extraSmall = thumbnail, small = thumbnail, medium = field,
        large = card, extraLarge = RoundedCornerShape(24.dp))
}
