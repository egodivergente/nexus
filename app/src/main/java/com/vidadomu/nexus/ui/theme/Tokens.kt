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
    val topBarHeight = minimumTouch
    val divider = 0.5.dp
    val iconSmall = 18.dp
    val iconMedium = 20.dp
    val iconDefault = space24
    val thumbnail = RoundedCornerShape(space8)
    val field = RoundedCornerShape(space16)
    val card = field
    val sheet = RoundedCornerShape(topStart = space24, topEnd = space24)
    val prompt = field
    val action = RoundedCornerShape(28.dp)
    const val pressDuration = 120
    const val contentDuration = 180
    val filter = RoundedCornerShape(50)
    /** Radios únicos de la app: pequeño 8, medio 16, grande 24 y pastilla. */
    val radiusSmall = thumbnail
    val radiusMedium = card
    val radiusLarge = RoundedCornerShape(space24)
    val pill = RoundedCornerShape(999.dp)
    /** Curva estándar para entradas y salidas. */
    val easing = androidx.compose.animation.core.FastOutSlowInEasing
    val shapes = Shapes(extraSmall = thumbnail, small = thumbnail, medium = field,
        large = card, extraLarge = radiusLarge)
}
