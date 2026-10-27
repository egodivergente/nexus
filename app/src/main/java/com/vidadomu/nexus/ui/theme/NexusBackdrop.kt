package com.vidadomu.nexus.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.R
import com.vidadomu.nexus.settings.RuntimePreferences

/**
 * Lowest opacity of the readability veil over a personal photo. A recognisable blurred subject behind text
 * and cards hurts contrast, so the photo stays visible as colour and light but never as a sharp silhouette.
 */
internal const val PHOTO_VEIL_MINIMUM = .80f

/** Veil stops (top, middle, bottom): darker where headers and the composer sit, lighter in the middle. */
internal fun photoVeilStops(intensity: Float): List<Float> {
    val base = maxOf(1f - intensity.coerceIn(0f, 1f), PHOTO_VEIL_MINIMUM)
    return listOf(minOf(1f, base + .10f), base, minOf(1f, base + .14f))
}

/** Personal photos retain their readability veil; dark themes use a restrained ambient aurora. */
@Composable fun NexusBackdrop(content: @Composable ()->Unit) {
    val config by RuntimePreferences.get(LocalContext.current).state.collectAsState()
    val veil = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize()) {
        if(config.value("ui.background").toBoolean()) {
            val alpha=config.value("ui.backgroundOpacity").toFloat()
            val photo=config.value("ui.photo")
            if(photo.isNotBlank()) {
                val stops = photoVeilStops(alpha)
                coil.compose.AsyncImage(model=photo,contentDescription=null,
                    modifier=Modifier.matchParentSize().blur(config.value("ui.photoBlur").toFloat().dp).drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(stops.map { veil.copy(alpha = it) }))
                    },contentScale=ContentScale.Crop)
            }
            else if (veil.luminance() < .3f && alpha > 0f) NexusAurora(alpha)
        }
        content()
    }
}
