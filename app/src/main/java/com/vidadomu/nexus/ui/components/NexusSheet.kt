package com.vidadomu.nexus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.vidadomu.nexus.ui.theme.NexusSheetSystemBars
import com.vidadomu.nexus.ui.theme.NexusTokens

/** Shared chrome; the caller owns scrolling so lazy lists retain bounded constraints. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    primaryAction: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = NexusTokens.sheet,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.screenMargin),
                verticalAlignment = Alignment.CenterVertically) {
                icon?.let { Icon(it,null,tint=MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier=Modifier.padding(end=NexusTokens.space12)) }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar $title") }
            }
            Column(Modifier.weight(1f).fillMaxWidth(), content = content)
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.screenMargin,
                    vertical = NexusTokens.space8)) {
                    if (primaryAction != null) primaryAction()
                    else FilledTonalButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
                }
            }
        }
    }
}

@Composable
fun NexusSheetGroup(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = NexusTokens.card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(NexusTokens.space12), verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}
