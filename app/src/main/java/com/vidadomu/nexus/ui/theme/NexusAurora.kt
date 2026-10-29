package com.vidadomu.nexus.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Kept for existing callers; all ambient drawing now shares the backdrop's clock. */
@Composable
internal fun NexusAurora(intensity: Float) {
    NexusAmbientBackground(NexusAppearance.AURORA, AmbientMode.SUBTLE, intensity, Modifier.fillMaxSize())
}
