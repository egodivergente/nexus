package com.vidadomu.nexus.ui.models

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.TextSecondary

/** Images sent from the Library to ArtBox. They wait here until a model is chosen, then go in as its input images. */
internal object ArtBoxInbox {
    val images = mutableStateOf<List<Pair<Uri, String>>>(emptyList())
}

/** Reminder on the ArtBox lists while Library images are waiting for a model. */
@Composable
internal fun ArtBoxInboxNotice(count: Int, onClear: () -> Unit) {
    Surface(shape = NexusTokens.card, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = NexusTokens.space12), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(if (count == 1) "1 imagen de la Biblioteca · elige un modelo" else "$count imágenes de la Biblioteca · elige un modelo",
                Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Descartar las imágenes", tint = TextSecondary) }
        }
    }
}
