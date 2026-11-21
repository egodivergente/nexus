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
    val main = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val secondary = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
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

/**
 * One scale for Nexus text: the size chosen in Personalización, or Android's when there is none. Before, both were
 * multiplied (0,8 × 0,8 on the S25), so 12 sp ended at 7,7 sp. The floor keeps the smallest styles readable at any scale.
 */
object NexusTextScale {
    const val MIN = .8f
    const val MAX = 1.4f
    /** No text style is drawn smaller than this, in sp at scale 1. */
    const val FLOOR_SP = 11f

    /** The old saved value was relative to Android's scale; the new one is the scale itself. */
    fun migrate(oldRelative: Float, system: Float): Float = (oldRelative * system).coerceIn(MIN, MAX)

    /** Size to declare so that, at [scale], the text is never below [FLOOR_SP]. */
    fun flooredSp(sp: Float, scale: Float): Float = if (scale <= 0f || sp * scale >= FLOOR_SP) sp else FLOOR_SP / scale
}

/** Raises the size (and the line height with it) only when [scale] would draw this style below the floor. */
fun TextStyle.withReadableFloor(scale: Float): TextStyle {
    if (!fontSize.isSp) return this
    val size = NexusTextScale.flooredSp(fontSize.value, scale)
    if (size == fontSize.value) return this
    val ratio = size / fontSize.value
    return copy(fontSize = size.sp, lineHeight = if (lineHeight.isSp) (lineHeight.value * ratio).sp else lineHeight)
}

fun Typography.withReadableFloor(scale: Float): Typography = copy(
    displayLarge = displayLarge.withReadableFloor(scale), displayMedium = displayMedium.withReadableFloor(scale),
    displaySmall = displaySmall.withReadableFloor(scale), headlineLarge = headlineLarge.withReadableFloor(scale),
    headlineMedium = headlineMedium.withReadableFloor(scale), headlineSmall = headlineSmall.withReadableFloor(scale),
    titleLarge = titleLarge.withReadableFloor(scale), titleMedium = titleMedium.withReadableFloor(scale),
    titleSmall = titleSmall.withReadableFloor(scale), bodyLarge = bodyLarge.withReadableFloor(scale),
    bodyMedium = bodyMedium.withReadableFloor(scale), bodySmall = bodySmall.withReadableFloor(scale),
    labelLarge = labelLarge.withReadableFloor(scale), labelMedium = labelMedium.withReadableFloor(scale),
    labelSmall = labelSmall.withReadableFloor(scale),
)
