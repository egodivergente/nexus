package com.vidadomu.nexus.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.components.matchesCreationSearch
import com.vidadomu.nexus.ui.theme.*

@Composable
fun LibraryScreen(onNavigateToOutput: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val jobs by app.generationRepository.allJobs.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var modality by rememberSaveable { mutableStateOf<Modality?>(null) }
    var filterMenu by remember { mutableStateOf(false) }
    val media = remember(jobs, modality, query) {
        jobs.filter { it.status == GenerationStatus.COMPLETED && (modality == null || it.modality == modality) && matchesCreationSearch(it, query) }
            .flatMap { job -> job.outputs.map { job to it } }
    }
    Column(Modifier.fillMaxSize().background(ScreenBackground)) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Biblioteca", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { searching = !searching }) { Icon(Icons.Default.Search, "Buscar creaciones") }
            Box {
                IconButton(onClick = { filterMenu = true }) {
                    Icon(Icons.Default.FilterList, "Filtrar archivos", tint = if(modality == null) TextSecondary else PrimaryNeon)
                }
                DropdownMenu(filterMenu, { filterMenu = false }) {
                    DropdownMenuItem(text = { Text("Todos") }, onClick = { modality = null; filterMenu = false })
                    listOf(Modality.IMAGE to "Imagen", Modality.VIDEO to "Vídeo", Modality.AUDIO to "Audio").forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label + if(modality == value) " ✓" else "") }, onClick = { modality = value; filterMenu = false })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Abrir Alexia") }, onClick = {
                        filterMenu = false
                        context.packageManager.getLaunchIntentForPackage("com.axel.library")?.let { context.startActivity(it) }
                    })
                }
            }
        }
        if(searching || query.isNotBlank()) OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), singleLine = true,
            placeholder = { Text("Buscar creaciones") }, trailingIcon = { IconButton(onClick = { query = ""; searching = false }) { Icon(Icons.Default.Close, "Cerrar búsqueda") } })
        if(media.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(if(query.isNotBlank()) "No hay resultados" else "Tus creaciones aparecerán aquí", color = TextSecondary)
        } else LazyVerticalGrid(columns = GridCells.Adaptive(112.dp), modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(media, key = { (job, output) -> "${job.id}:${output.id}" }) { (job, output) ->
                Box(Modifier.fillMaxWidth().aspectRatio(0.85f).clip(RoundedCornerShape(6.dp)).background(SurfaceVariantDark)
                    .clickable { onNavigateToOutput(job.id) }, contentAlignment = Alignment.Center) {
                    if(job.modality == Modality.IMAGE) AsyncImage(output.localUri ?: output.url, job.prompt,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(if(job.modality == Modality.VIDEO) Icons.Default.PlayCircle else Icons.Default.Audiotrack,
                        if(job.modality == Modality.VIDEO) "Abrir vídeo" else "Abrir audio", tint = PrimaryNeon, modifier = Modifier.size(32.dp))
                    if(job.modality != Modality.IMAGE) Text(job.modelId.substringAfterLast('/'), Modifier.align(Alignment.BottomStart).padding(6.dp),
                        maxLines = 1, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
    }
}
