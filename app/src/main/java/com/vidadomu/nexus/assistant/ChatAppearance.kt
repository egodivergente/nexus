package com.vidadomu.nexus.assistant

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Reading roles are separate from operational labels. Android's bold-text adjustment still applies. */
@Immutable
internal data class ChatReadingStyles(
    val body: TextStyle,
    val heading: TextStyle,
    val subheading: TextStyle,
    val supporting: TextStyle,
)

internal fun chatReadingStyles(typography: Typography) = ChatReadingStyles(
    body = typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
    heading = typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    subheading = typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    supporting = typography.bodyMedium.copy(fontWeight = FontWeight.Normal, lineHeight = 22.sp),
)
