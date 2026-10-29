package com.vidadomu.nexus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.vidadomu.nexus.ui.theme.NexusTokens

/** Immediate, accessible feedback while the local workspace is opened. No network request or retry loop. */
@Composable
internal fun NexusStartupState(failed: Boolean, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(NexusTokens.space24)
        .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NexusTokens.space16, Alignment.CenterVertically)) {
        if (failed) Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
        else CircularProgressIndicator()
        Text(if (failed) "No se ha podido abrir tu espacio" else "Preparando tu espacio…",
            style = MaterialTheme.typography.titleMedium)
        if (failed) {
            Text("Vuelve a intentarlo para cargar tus conversaciones y archivos.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onRetry, modifier = Modifier.heightIn(min = NexusTokens.minimumTouch)) {
                Text("Reintentar")
            }
        }
    }
}
