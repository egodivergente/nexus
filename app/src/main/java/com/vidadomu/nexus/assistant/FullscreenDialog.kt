package com.vidadomu.nexus.assistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vidadomu.nexus.ui.theme.NexusSheetSystemBars
import com.vidadomu.nexus.ui.theme.NexusTokens

/**
 * Contenedor de los editores largos: una hoja inferior a altura completa, como el resto de formularios de la app.
 * Un Dialog de Compose a pantalla completa se desbordaba por abajo en este dispositivo (la barra de acciones quedaba
 * bajo la barra de gestos); la hoja gestiona sola los márgenes del sistema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullscreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = NexusTokens.sheet, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        NexusSheetSystemBars()
        Box(Modifier.fillMaxWidth().fillMaxHeight()) { content() }
    }
}
