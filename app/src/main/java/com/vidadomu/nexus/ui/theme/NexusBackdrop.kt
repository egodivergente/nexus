package com.vidadomu.nexus.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.settings.RuntimePreferences

/**
 * Lowest opacity of the readability veil over a personal photo. A recognisable blurred subject behind text
 * and cards hurts contrast, so the photo stays visible as colour and light but never as a sharp silhouette.
 */
internal const val PHOTO_VEIL_MINIMUM = .80f

/** Veil stops (top, middle, bottom): darker where headers and the composer sit, lighter in the middle. */
internal fun photoVeilStops(intensity: Float): List<Float> {
    val safeIntensity = if (intensity.isFinite()) intensity.coerceIn(0f, 1f) else 0f
    val base = maxOf(1f - safeIntensity, PHOTO_VEIL_MINIMUM)
    return listOf(minOf(1f, base + .10f), base, minOf(1f, base + .14f))
}

/** Personal photo settings stay intact; the theme engine supplies the non-photo background. */
@Composable fun NexusBackdrop(content: @Composable ()->Unit) {
    val config by RuntimePreferences.get(LocalContext.current).state.collectAsState()
    val appearance by rememberAppearancePreferences().selectionState()
    val veil = MaterialTheme.colorScheme.background
    val enabled = config.value("ui.background").toBoolean()
    val photo = config.value("ui.photo")
    ProvideNexusAmbientPhase(appearance.ambientMode, appearance.intensity, (enabled && photo.isBlank()) || AppearancePreferences.preview.value!=null) {
        Box(Modifier.fillMaxSize()) {
            if (enabled) {
                if (photo.isNotBlank()) {
                    val stops = photoVeilStops(config.value("ui.backgroundOpacity").toFloatOrNull() ?: 0f)
                    coil.compose.AsyncImage(model=photo,contentDescription=null,
                        modifier=Modifier.matchParentSize().blur((config.value("ui.photoBlur").toFloatOrNull() ?: 0f).dp).drawWithContent {
                            drawContent()
                            drawRect(Brush.verticalGradient(stops.map { veil.copy(alpha = it) }))
                        },contentScale=ContentScale.Crop)
                } else {
                    NexusAmbientBackground(appearance.theme, appearance.ambientMode, appearance.intensity, Modifier.matchParentSize())
                }
            }
            content()
        }
    }
}
