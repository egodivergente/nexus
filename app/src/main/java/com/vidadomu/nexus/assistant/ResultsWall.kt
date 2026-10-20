package com.vidadomu.nexus.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vidadomu.nexus.ui.theme.NexusSheetSystemBars
import com.vidadomu.nexus.ui.theme.NexusTokens

/**
 * Muro de resultados: todas las imágenes del lote en cuadrícula escalonada, cada una con su proporción real
 * (verticales, horizontales o cuadradas), sin marcos ni huecos. Tocar una abre su resultado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResultsWallSheet(outputs: List<String>, onOpen: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = NexusTokens.sheet,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).padding(horizontal = NexusTokens.space12)) {
            Text("Muro de resultados · ${outputs.size}", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = NexusTokens.space4, vertical = NexusTokens.space8))
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(150.dp),
                verticalItemSpacing = NexusTokens.space8,
                horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8),
                modifier = Modifier.weight(1f)
            ) {
                items(outputs) { uri ->
                    AsyncImage(
                        model = uri, contentDescription = "Resultado generado",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(NexusTokens.field).clickable { onOpen() })
                }
            }
        }
    }
}
