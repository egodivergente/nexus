package com.vidadomu.nexus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun nexusTypography(heading: FontFamily, body: FontFamily): Typography {
    val display = TextStyle(fontFamily = heading, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp)
    val headline = TextStyle(fontFamily = heading, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.25).sp)
    val titleLg = TextStyle(fontFamily = heading, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 32.sp)
    val titleMd = TextStyle(fontFamily = heading, fontWeight = FontWeight.Medium,
        fontSize = 17.sp, lineHeight = 24.sp)
    val titleSm = TextStyle(fontFamily = heading, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp)
    val main = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp)
    val secondary = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
    val labelLg = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp)
    val metadata = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 18.sp)
    val micro = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp)
    return Typography(
        displayLarge = display, displayMedium = display, displaySmall = headline,
        headlineLarge = display, headlineMedium = headline, headlineSmall = titleLg,
        titleLarge = titleLg, titleMedium = titleMd, titleSmall = titleSm,
        bodyLarge = main, bodyMedium = secondary, bodySmall = secondary,
        labelLarge = labelLg, labelMedium = metadata, labelSmall = micro
    )
}

fun TextStyle.tabularMoney(): TextStyle = copy(fontFeatureSettings = "tnum")
