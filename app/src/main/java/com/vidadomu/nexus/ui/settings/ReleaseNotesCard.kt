package com.vidadomu.nexus.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ReleaseNote(val version: String, val items: List<String>)

private val releaseNotes = listOf(
    ReleaseNote("1.9.116", listOf("Chat: las tablas de las respuestas se ajustan al ancho de la pantalla y el fondo del código en línea es más suave.")),
    ReleaseNote("1.9.115", listOf("Chat: mientras el agente trabaja muestra sus pasos reales (consultar modelos, mirar la biblioteca…).", "Chat: corregido el parpadeo al escribir o pensar (el desplazamiento automático saltaba arriba y abajo en cada fragmento).")),
    ReleaseNote("1.9.114", listOf("Chat: «Pensando…» muestra el tiempo transcurrido y avisa si tarda más de 30 s.")),
    ReleaseNote("1.9.113", listOf("ArtBox: bordes más suaves, buscador relleno sin borde, pestañas en píldora, logo a la izquierda y un único menú (con Ajustes).")),
    ReleaseNote("1.9.112", listOf("Chat: el selector de esfuerzo es una píldora con el modo actual y una descripción de cada modo.", "ArtBox: sonido de toque al abrir una tarjeta (el del sistema).")),
    ReleaseNote("1.9.111", listOf("Sistema: fecha de la última actualización y cambios por versión.")),
    ReleaseNote("1.9.110", listOf("Lanzadera: límites de modelos, generaciones por modelo y envíos simultáneos sin tope.", "Ajustes: Google Flow ya no ocupa una tarjeta en Proveedores.")),
    ReleaseNote("1.9.109", listOf("Chat: un «+» con archivo, cámara y recientes; micro y enviar juntos; márgenes de 16 dp.")),
    ReleaseNote("1.9.108", listOf("ArtBox: tarjetas con sombra que se elevan al acercar el S Pen.")),
    ReleaseNote("1.9.107", listOf("ArtBox: título más grande y centrado.")),
    ReleaseNote("1.9.106", listOf("Lanzadera: el panel de formato incluye calidad, resolución, duración y número de generaciones.")),
    ReleaseNote("1.9.105", listOf("Guarda en el móvil los resultados que solo existían como URL del proveedor.")),
    ReleaseNote("1.9.104", listOf("Biblioteca: las carpetas muestran tres miniaturas de su contenido.")),
    ReleaseNote("1.9.103", listOf("Biblioteca: «Eliminar carpeta» con pulsación larga (los archivos vuelven a la Biblioteca).")),
    ReleaseNote("1.9.102", listOf("Chat: sin panel de Encargos; el micro dicta con un toque.", "Ajustes: sin «Respuesta» ni «Tu espacio de creación».")),
)
private val pending = listOf(
    "Mover el logo dentro de la tarjeta de ArtBox y sonido al interactuar.",
    "Rediseñar el selector de esfuerzo (Bajo/Medio/Alto) y el gesto lateral entre pantallas.",
    "Telemetría: tiempos de respuesta, modelos más usados y tokens por agente.",
    "Modelos que faltan en ArtBox y diálogo de modelos de vídeo (esperan tus datos).",
)

/** Fecha de la última actualización instalada y qué trae cada versión, plegado por defecto. */
@Composable
internal fun ReleaseNotesCard() {
    val context = LocalContext.current
    val updated = remember {
        runCatching {
            val time = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")).format(Date(time))
        }.getOrDefault("—")
    }
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Última actualización: $updated", style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text("Cambios por versión y pendientes", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (open) "Plegar" else "Desplegar")
        }
        if (open) {
            releaseNotes.forEach { note ->
                Text(note.version, style = MaterialTheme.typography.labelLarge)
                note.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
            Text("Pendiente", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            pending.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
