package com.vidadomu.nexus.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.runtime.saveable.rememberSaveable
import com.vidadomu.nexus.ui.components.matchesCreationSearch
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToOutput: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val jobs by app.generationRepository.allJobs.collectAsState()
    var searchQuery by rememberSaveable {mutableStateOf("")}

    var selectedFilter by remember { mutableStateOf<Modality?>(null) }

    val completedJobs = remember(jobs, selectedFilter,searchQuery) {
        jobs.filter { it.status == GenerationStatus.COMPLETED && it.outputs.isNotEmpty() }
            .filter { selectedFilter == null || it.modality == selectedFilter }
            .filter {matchesCreationSearch(it,searchQuery)}
    }

    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Biblioteca",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.sp,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "${completedJobs.sumOf { it.outputs.size }} archivos · ${completedJobs.size} creaciones",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            try {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.axel.library")
                                    ?: android.content.Intent().apply {
                                        component = android.content.ComponentName("com.axel.library", "com.axel.library.MainActivity")
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                context.startActivity(launchIntent)
                            } catch (_: Exception) {}
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = "Abrir Alexia",
                            tint = PrimaryNeon
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(searchQuery,{searchQuery=it},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),singleLine=true,
                placeholder={Text("Buscar por modelo o descripción")},leadingIcon={Icon(Icons.Default.Search,null)},
                trailingIcon={if(searchQuery.isNotEmpty()) IconButton(onClick={searchQuery=""}) {Icon(Icons.Default.Close,"Borrar búsqueda")}},shape=RoundedCornerShape(16.dp))
            // Filter Pills
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    LibraryFilterPill(
                        text = "TODOS",
                        isSelected = selectedFilter == null,
                        onClick = { selectedFilter = null }
                    )
                }
                items(listOf(Modality.IMAGE, Modality.VIDEO, Modality.AUDIO)) { mod ->
                    val label = when (mod) {
                        Modality.IMAGE -> "IMAGEN"
                        Modality.VIDEO -> "VÍDEO"
                        Modality.AUDIO -> "AUDIO"
                        Modality.CHAT -> "CHAT"
                    }
                    LibraryFilterPill(
                        text = label,
                        isSelected = selectedFilter == mod,
                        onClick = { selectedFilter = mod }
                    )
                }
            }

            if (completedJobs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CollectionsBookmark,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = TextMuted
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if(searchQuery.isNotBlank()) "No hay resultados" else "No hay archivos con este filtro",
                            style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary)
                        )
                        Text(
                            text = "Las creaciones generadas aparecerán aquí.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(completedJobs, key = { it.id }) { job ->
                        LibraryItemCard(
                            job = job,
                            onClick = { onNavigateToOutput(job.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryFilterPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) PrimaryNeon else SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) PrimaryNeon else BorderSubtle),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) BackgroundDark else TextSecondary
            )
        )
    }
}

@Composable
private fun LibraryItemCard(
    job: GenerationJob,
    onClick: () -> Unit
) {
    val firstOutput = job.outputs.firstOrNull()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(SurfaceVariantDark),
                contentAlignment = Alignment.Center
            ) {
                if (firstOutput != null && job.modality == Modality.IMAGE) {
                    AsyncImage(
                        model = firstOutput.url,
                        contentDescription = job.prompt,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (job.modality == Modality.VIDEO) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        tint = PrimaryNeon,
                        modifier = Modifier.size(44.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = "Audio",
                        tint = SecondaryVioletLight,
                        modifier = Modifier.size(44.dp)
                    )
                }

                // Modality tag
                val modalityLabel = when (job.modality) {
                    Modality.IMAGE -> "IMAGEN"
                    Modality.VIDEO -> "VÍDEO"
                    Modality.AUDIO -> "AUDIO"
                    Modality.CHAT -> "CHAT"
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = modalityLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize, color = TextPrimary)
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = job.prompt.ifBlank { "Archivo multimedia" },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, color = TextPrimary),
                    maxLines = 1
                )
                Text(
                    text = job.modelId.substringAfter("/"),
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted),
                    maxLines = 1
                )
            }
        }
    }
}
