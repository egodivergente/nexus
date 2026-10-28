package com.vidadomu.nexus.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.assistant.TurnTelemetry
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.ui.theme.TextSecondary

private fun seconds(ms: Long): String = when {
    ms < 1000 -> "$ms ms"
    ms < 60_000 -> "%.1f s".format(ms / 1000.0)
    else -> "%d min %02d s".format(ms / 60_000, (ms % 60_000) / 1000)
}

private fun tokens(n: Long): String = when {
    n >= 1_000_000 -> "%.1f M".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1f k".format(n / 1_000.0)
    else -> n.toString()
}

@Composable
private fun Stat(title: String, detail: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Telemetría local: tiempo medio de respuesta, tokens y llamadas por agente (se registra desde esta versión),
 * y tiempo de generación y uso por modelo de imagen/vídeo (del historial). Plegada por defecto; no sale del teléfono.
 */
@Composable
internal fun TelemetryCard() {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    var open by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableStateOf(0) }
    val jobs by app.generationRepository.allJobs.collectAsState()
    val turns = remember(open, revision) { if (open) TurnTelemetry.all(context) else emptyList() }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Telemetría", style = MaterialTheme.typography.bodyLarge)
                Text("Tiempos, modelos más usados y consumo del agente", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Text(if (open) "Ocultar" else "Ver", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (open) {
            Text("Agentes", style = MaterialTheme.typography.titleSmall)
            if (turns.isEmpty()) Text("Aún no hay respuestas registradas. Se anotan desde esta versión.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            turns.groupBy { it.agent }.entries.sortedByDescending { it.value.size }.forEach { (agent, list) ->
                val avg = list.map { it.durationMs }.average().toLong()
                val inTok = list.sumOf { it.inputTokens.toLong() }
                val outTok = list.sumOf { it.outputTokens.toLong() }
                Stat(agent, "${list.size} respuestas · ${list.sumOf { it.calls }} llamadas a herramientas · ${tokens(inTok)} entrada / ${tokens(outTok)} salida", seconds(avg))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            fun name(id: String) = app.modelCatalogRepository.getModelById(id)?.displayName ?: id
            val done = jobs.filter { it.status == GenerationStatus.COMPLETED && it.completedAt != null && it.modality != Modality.CHAT }
            Text("Generación por modelo", style = MaterialTheme.typography.titleSmall)
            Text("Desde que se envía hasta que termina (incluye la cola del proveedor).", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            if (done.isEmpty()) Text("Aún no hay generaciones completadas.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            done.groupBy { it.modelId }.entries.sortedByDescending { it.value.size }.take(12).forEach { (id, list) ->
                val avg = list.map { (it.completedAt ?: it.startedAt) - it.startedAt }.filter { it >= 0 }.average().let { if (it.isNaN()) 0L else it.toLong() }
                val kind = if (list.first().modality == Modality.VIDEO) "vídeo" else if (list.first().modality == Modality.AUDIO) "audio" else "imagen"
                Stat(name(id), "$kind · ${list.size} completadas", seconds(avg))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Modelos más usados", style = MaterialTheme.typography.titleSmall)
            jobs.filter { it.modality != Modality.CHAT }.groupBy { it.modelId }.entries.sortedByDescending { it.value.size }.take(5).forEachIndexed { i, (id, list) ->
                val failed = list.count { it.status == GenerationStatus.FAILED }
                Stat("${i + 1}. ${name(id)}", if (failed > 0) "$failed con error" else "", "${list.size}")
            }
            if (jobs.isEmpty()) Text("Sin historial todavía.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            if (turns.isNotEmpty()) TextButton(onClick = { TurnTelemetry.clear(context); revision++ }) { Text("Borrar registro de respuestas del agente") }
        }
    }
}
