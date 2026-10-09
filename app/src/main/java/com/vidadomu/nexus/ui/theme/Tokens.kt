package com.vidadomu.nexus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val topBarHeight = 56.dp
    val thumbnail = RoundedCornerShape(8.dp)
    val field = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val filter = RoundedCornerShape(50)
    val shapes = Shapes(extraSmall = thumbnail, small = thumbnail, medium = field,
        large = card, extraLarge = RoundedCornerShape(24.dp))
}

fun nexusTypography(heading: FontFamily, body: FontFamily): Typography {
    val title = TextStyle(fontFamily=heading, fontWeight=FontWeight.Medium,
        fontSize=24.sp, lineHeight=32.sp)
    val section = TextStyle(fontFamily=heading, fontWeight=FontWeight.Medium,
        fontSize=17.sp, lineHeight=24.sp)
    val main = TextStyle(fontFamily=body, fontSize=16.sp, lineHeight=24.sp)
    val secondary = TextStyle(fontFamily=body, fontSize=14.sp, lineHeight=20.sp)
    val metadata = TextStyle(fontFamily=body, fontWeight=FontWeight.Medium,
        fontSize=12.sp, lineHeight=18.sp)
    return Typography(displayLarge=title, displayMedium=title, displaySmall=title,
        headlineLarge=title, headlineMedium=title, headlineSmall=title,
        titleLarge=title, titleMedium=section, titleSmall=section,
        bodyLarge=main, bodyMedium=secondary, bodySmall=secondary,
        labelLarge=secondary, labelMedium=metadata, labelSmall=metadata)
}

fun TextStyle.tabularMoney(): TextStyle = copy(fontFeatureSettings="tnum")
