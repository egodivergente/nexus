package com.vidadomu.nexus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
