package com.vidadomu.nexus.ui.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import com.vidadomu.nexus.ui.components.NexusTopAppBar
import com.vidadomu.nexus.ui.components.nexusScreenTitleStyle

/** Barra mientras hay archivos marcados: cuántos, marcar todo, exportar ZIP y acciones sobre el lote. */
@Composable
internal fun LibrarySelectionBar(count: Int, single: Boolean, favoritesOn: Boolean,
    onClose: () -> Unit, onSelectAll: () -> Unit, onExport: () -> Unit,
    onFavorite: () -> Unit, onHide: () -> Unit, onOptions: () -> Unit,
    onMove: () -> Unit, onRename: () -> Unit, onCrop: (() -> Unit)?, onArtBox: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    NexusTopAppBar(title = {
        Text(if (count == 1) "1 marcado" else "$count marcados", style = nexusScreenTitleStyle(), maxLines = 1)
    }, actions = {
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Salir de la selección") }
        IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, "Marcar todo lo visible") }
        IconButton(onClick = onMove) { Icon(Icons.Default.DriveFileMove, "Cambiar de proyecto") }
        IconButton(onClick = onExport) { Icon(Icons.Default.Download, "Exportar en ZIP") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más acciones sobre lo marcado") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Cambiar el nombre") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onRename() })
                onCrop?.let { crop -> DropdownMenuItem(text = { Text("Recortar") },
                    leadingIcon = { Icon(Icons.Default.Crop, null) }, onClick = { menu = false; crop() }) }
                onArtBox?.let { send -> DropdownMenuItem(text = { Text("Enviar a ArtBox") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null) }, onClick = { menu = false; send() }) }
                DropdownMenuItem(text = { Text(if (favoritesOn) "Quitar de favoritos" else "Marcar como favorito") },
                    leadingIcon = { Icon(Icons.Default.Star, null) }, onClick = { menu = false; onFavorite() })
                DropdownMenuItem(text = { Text("Ocultar / mostrar") },
                    leadingIcon = { Icon(Icons.Default.VisibilityOff, null) }, onClick = { menu = false; onHide() })
                if (single) DropdownMenuItem(text = { Text("Más opciones del archivo") },
                    leadingIcon = { Icon(Icons.Default.OpenInNew, null) }, onClick = { menu = false; onOptions() })
            }
        }
    })
}

/** Círculo de marcado en la esquina de una miniatura: vacío = sin marcar, relleno con ✓ = marcada. */
@Composable
internal fun TileMark(marked: Boolean, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier.padding(6.dp).size(22.dp).clip(CircleShape)
        .background(if (marked) primary else Color.Black.copy(alpha = .35f))
        .border(1.5.dp, Color.White, CircleShape)
        .semantics { contentDescription = if (marked) "Marcado" else "Sin marcar" },
        contentAlignment = Alignment.Center) {
        if (marked) Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
    }
}
