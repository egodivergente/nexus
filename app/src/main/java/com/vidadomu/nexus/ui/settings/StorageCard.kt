package com.vidadomu.nexus.ui.settings

import android.text.format.Formatter
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
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.storage.AreaUsage
import com.vidadomu.nexus.agent.core.storage.CleanupResult
import com.vidadomu.nexus.agent.core.storage.StorageAnalysis
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.assistant.NexusStorage
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Ajustes → Sistema → Almacenamiento: space per area, and exact duplicates Nexus made of the same file.
 * Searching changes nothing; «Liberar» asks first and always keeps one copy (the Drive one when there is one).
 */
@Composable
internal fun StorageCard() {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf(false) }
    var usage by remember { mutableStateOf<List<AreaUsage>?>(null) }
    var analysis by remember { mutableStateOf<StorageAnalysis?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var freeing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CleanupResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val recovery = remember { AssistantRuntime.get(app).assets.recovery }
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    fun refresh() { scope.launch { usage = runCatching { NexusStorage.usage(app) }.getOrNull() } }
    LaunchedEffect(open) { if (open && usage == null) refresh() }

    Column(Modifier.fillMaxWidth().padding(NexusTokens.space16), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Almacenamiento", style = MaterialTheme.typography.titleMedium)
                Text("Espacio que ocupa Nexus y copias repetidas", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (open) "Plegar" else "Desplegar")
        }
        if (recovery != null) Text(
            "La Biblioteca no se pudo leer al abrir Nexus y se recuperó " + (recovery.source?.let { "de la copia anterior ($it)" } ?: "vacía") +
                (recovery.quarantined?.let { ". El archivo dañado se guardó aparte como $it." } ?: "."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        if (!open) return@Column
        val areas = usage
        if (areas == null) Text("Midiendo…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        else {
            Text("Medios de Nexus: ${size(areas.sumOf { it.bytes })}", style = MaterialTheme.typography.bodyLarge)
            areas.forEach { area ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(area.area.label, style = MaterialTheme.typography.bodyMedium)
                        Text("${area.files} archivos", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                    Text(size(area.bytes), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val found = analysis
        when {
            freeing -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                CircularProgressIndicator(Modifier.size(20.dp))
                Text("Liberando… (espera a que termine la copia en Drive si hay una en curso)", style = MaterialTheme.typography.bodyMedium)
            }
            progress != null -> Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                val (done, total) = progress!!
                Text("Comprobando archivos ($done de $total)…", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { job?.cancel() }) { Text("Cancelar") }
            }
            found == null -> {
                Text("Busca copias idénticas del mismo archivo. Buscar no cambia nada.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Button(onClick = {
                    error = null; result = null; progress = 0 to 0
                    job = scope.launch {
                        try { analysis = NexusStorage.analyse(app) { done, total -> progress = done to total } }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = "No se pudo completar la búsqueda. No se ha cambiado nada." }
                        finally { progress = null }
                    }
                }) { Text("Buscar duplicados") }
            }
            else -> {
                val plan = found.plan
                if (plan.removals.isEmpty()) Text("No hay copias repetidas que se puedan quitar.", style = MaterialTheme.typography.bodyMedium)
                else Text("${plan.removals.size} copias idénticas de adjuntos y exportaciones: ${size(plan.bytes)}. Siempre se conserva una (la de Drive si existe), y la Biblioteca y los chats guardados pasan a usarla.",
                    style = MaterialTheme.typography.bodyMedium)
                if (plan.protectedCopies > 0) Text("Otras ${plan.protectedCopies} copias (${size(plan.protectedBytes)}) se conservan porque las usa algo que Nexus no puede cambiar ahora: el chat abierto, borradores, el Lienzo o trabajos en segundo plano. Si cambias de chat y vuelves a buscar, puede liberarse más.",
                    style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    if (plan.removals.isNotEmpty()) Button(onClick = { confirm = true }) { Text("Liberar ${size(plan.bytes)}") }
                    TextButton(onClick = { analysis = null }) { Text("Volver a buscar") }
                }
            }
        }
        result?.let { done ->
            Text("Liberados ${size(done.bytes)} (${done.removed} archivos)." + if (done.kept > 0) " ${done.kept} se conservaron porque cambiaron o están en uso." else "",
                style = MaterialTheme.typography.bodyMedium)
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    }

    val pending = analysis
    if (confirm && pending != null) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("¿Liberar ${size(pending.plan.bytes)}?") },
        text = { Text("Se borrarán ${pending.plan.removals.size} copias idénticas. De cada archivo queda una copia, y la Biblioteca y los chats guardados pasan a usarla; no se pierde ningún archivo. Se guarda un informe de lo borrado.") },
        confirmButton = {
            Button(onClick = {
                confirm = false; freeing = true; error = null
                scope.launch {
                    try { result = NexusStorage.free(app, pending); analysis = null; usage = runCatching { NexusStorage.usage(app) }.getOrNull() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = "No se pudo terminar. Lo que no se borró sigue intacto; vuelve a buscar." ; analysis = null }
                    finally { freeing = false }
                }
            }) { Text("Liberar") }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } })
}
