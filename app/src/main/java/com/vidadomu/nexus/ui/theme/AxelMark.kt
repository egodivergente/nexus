package com.vidadomu.nexus.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource

/**
 * The NEXUS wolf at icon size (headers, sheets, empty states). Vidal 14-11: the logo is the wolf itself,
 * not a vector reduction tinted by the theme, so every surface shows the same mark as the launcher and
 * the onboarding. [appearance] stays for call-site compatibility (theme previews pass it).
 */
@Composable
fun AxelMark(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") appearance: NexusAppearance = LocalNexusAppearance.current,
    contentDescription: String? = null,
) {
    Image(
        bitmap = ImageBitmap.imageResource(com.vidadomu.nexus.R.drawable.nexus_mark_small),
        contentDescription = contentDescription,
        modifier = modifier.size(NexusTokens.iconDefault),
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.High,
    )
}
