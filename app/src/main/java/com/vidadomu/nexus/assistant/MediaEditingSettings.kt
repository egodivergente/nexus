package com.vidadomu.nexus.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vidadomu.nexus.ui.theme.NexusTokens
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

@Composable
internal fun MediaEditingSettings() {
    val context=LocalContext.current
    val store=remember {WhisperModelStore(context)}
    var expanded by remember {mutableStateOf(false)}
    var status by remember {mutableStateOf(store.status())}
    LaunchedEffect(expanded) {while(expanded) {status=store.status();delay(1500)}}
    TextButton(onClick={expanded=!expanded}) {Text("Edición y Whisper · ${if(store.ready()) "Local" else "Sin modelo"}")}
    if(expanded) Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
        Text("Pide al agente recortar, unir clips, quitar o añadir audio y extraer fotogramas. Guarda archivos nuevos y conserva los originales.",style=MaterialTheme.typography.bodyMedium)
        Text("Whisper Base multilingüe transcribe en este teléfono y crea TXT y SRT. Sólo se descarga el modelo (148 MB); el audio no se sube.",style=MaterialTheme.typography.bodyMedium)
        when(status["state"]?.jsonPrimitive?.content) {
            "READY" -> Text("Modelo listo. Puedes pedir una transcripción.")
            "DOWNLOADING" -> Text("Descargando modelo · ${status["download_progress"]?.jsonPrimitive?.int ?: 0}%")
            else -> FilledTonalButton(onClick={status=store.enqueue()}) {Text("Descargar Whisper (148 MB)")}
        }
        status["error"]?.jsonPrimitive?.content?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }
}
