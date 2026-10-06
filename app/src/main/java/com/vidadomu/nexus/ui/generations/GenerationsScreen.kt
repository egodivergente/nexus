package com.vidadomu.nexus.ui.generations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.AccountBalance
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.ui.billing.BillingLedgerScreen
import com.vidadomu.nexus.ui.components.GenerationProgressPanel
import com.vidadomu.nexus.ui.components.VendorLogo
import com.vidadomu.nexus.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerationsScreen(
    onNavigateToOutput: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val jobs by app.generationRepository.allJobs.collectAsState()
    val currencyManager = app.currencyManager
    val scope = rememberCoroutineScope()

    var currentSection by remember { mutableStateOf<String>("TASKS") } // "TASKS" or "BILLING"
    var accountBalance by remember { mutableStateOf<AccountBalance?>(app.secureStorage.getCachedBalance()) }
    var statusFilter by remember { mutableStateOf<String>("ALL") }
    var selectedTechnicalJob by remember { mutableStateOf<GenerationJob?>(null) }

    fun refreshBalance() {
        scope.launch {
            val res = app.atlasProvider.fetchBalance()
            if (res.isSuccess) {
                accountBalance = res.getOrNull()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshBalance()
    }

    val filteredJobs = remember(jobs, statusFilter) {
        when (statusFilter) {
            "ACTIVE" -> jobs.filter {
                it.status == GenerationStatus.QUEUED ||
                it.status == GenerationStatus.GENERATING ||
                it.status == GenerationStatus.PROCESSING ||
                it.status == GenerationStatus.RENDERING ||
                it.status == GenerationStatus.UPLOADING
            }
            "COMPLETED" -> jobs.filter { it.status == GenerationStatus.COMPLETED }
            "FAILED" -> jobs.filter { it.status == GenerationStatus.FAILED }
            else -> jobs
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (currentSection == "TASKS") "HISTORIAL" else "FACTURACIÓN",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (currentSection == "TASKS") "${jobs.size} tareas registradas" else "Auditoría de costes y saldo en vivo",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
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
            // Segmented Switcher: TAREAS | FACTURACIÓN
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (currentSection == "TASKS") CyanNeon else Color.Transparent)
                        .clickable { currentSection = "TASKS" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎨 TAREAS (${jobs.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (currentSection == "TASKS") Color.Black else TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (currentSection == "BILLING") CyanNeon else Color.Transparent)
                        .clickable { currentSection = "BILLING" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💳 FACTURACIÓN",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (currentSection == "BILLING") Color.Black else TextSecondary
                    )
                }
            }

            if (currentSection == "BILLING") {
                BillingLedgerScreen(
                    jobs = jobs,
                    accountBalance = accountBalance,
                    currencyManager = currencyManager,
                    onRefreshBalance = { refreshBalance() },
                    onNavigateToOutput = onNavigateToOutput,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Filter Tabs for Tasks
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        "ALL" to "TODOS",
                        "ACTIVE" to "EN CURSO",
                        "COMPLETED" to "COMPLETADOS",
                        "FAILED" to "FALLIDOS"
                    )
                    items(filters) { (key, label) ->
                        val count = when (key) {
                            "ACTIVE" -> jobs.count {
                                it.status == GenerationStatus.QUEUED ||
                                it.status == GenerationStatus.GENERATING ||
                                it.status == GenerationStatus.PROCESSING ||
                                it.status == GenerationStatus.RENDERING ||
                                it.status == GenerationStatus.UPLOADING
                            }
                            "COMPLETED" -> jobs.count { it.status == GenerationStatus.COMPLETED }
                            "FAILED" -> jobs.count { it.status == GenerationStatus.FAILED }
                            else -> jobs.size
                        }
                        val isSelected = statusFilter == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { statusFilter = key },
                            label = { Text("$label ($count)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanNeon,
                                selectedLabelColor = Color.Black,
                                containerColor = SurfaceDark,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                if (filteredJobs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No hay generaciones registradas",
                                style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary)
                            )
                            Text(
                                text = "Crea tu primera generación desde la pestaña Lienzo.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(filteredJobs, key = { it.id }) { job ->
                            GenerationJobCard(
                                job = job,
                                currencyManager = currencyManager,
                                onOpenOutput = { onNavigateToOutput(job.id) },
                                onShowTechnical = { selectedTechnicalJob = job },
                                onRefresh = {
                                    scope.launch {
                                        app.generationRepository.pollJob(job.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Technical details dialog
    selectedTechnicalJob?.let { job ->
        val statusSpanish = when (job.status) {
            GenerationStatus.COMPLETED -> "COMPLETADO"
            GenerationStatus.FAILED -> "FALLIDO"
            GenerationStatus.QUEUED -> "EN COLA"
            GenerationStatus.GENERATING -> "GENERANDO"
            GenerationStatus.PROCESSING -> "PROCESANDO"
            GenerationStatus.RENDERING -> "RENDERIZANDO"
            GenerationStatus.UPLOADING -> "SUBIENDO"
            GenerationStatus.CANCELLED -> "CANCELADO"
        }
        AlertDialog(
            onDismissRequest = { selectedTechnicalJob = null },
            containerColor = SurfaceDark,
            title = {
                Text("Detalles Técnicos de la Tarea", style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ID Tarea: ${job.id}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("ID Proveedor: ${job.providerJobId ?: "N/D"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("Proveedor: ${job.providerId}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("Modelo: ${job.modelId}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("Modalidad: ${job.modality.name}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("Estado: $statusSpanish", style = MaterialTheme.typography.bodySmall, color = PrimaryNeon)
                    val cost = job.actualCostUsd
                    if (cost != null) {
                        Text("Coste: ${currencyManager.formatCost(cost)} (\$${String.format(Locale.US, "%.4f", cost)} USD)", style = MaterialTheme.typography.bodySmall, color = CyanNeon)
                    }
                    job.error?.let {
                        Text("Error: $it", style = MaterialTheme.typography.bodySmall, color = AccentCoral)
                    }
                    job.technicalDetails?.let {
                        Text("Traza Técnica:\n$it", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTechnicalJob = null }) {
                    Text("Cerrar", color = PrimaryNeon)
                }
            }
        )
    }
}

@Composable
private fun GenerationJobCard(
    job: GenerationJob,
    currencyManager: com.vidadomu.nexus.core.currency.CurrencyManager,
    onOpenOutput: () -> Unit,
    onShowTechnical: () -> Unit,
    onRefresh: () -> Unit
) {
    val isFinished = job.status == GenerationStatus.COMPLETED || job.status == GenerationStatus.FAILED
    val statusColor = when (job.status) {
        GenerationStatus.COMPLETED -> AccentEmerald
        GenerationStatus.FAILED -> AccentCoral
        GenerationStatus.GENERATING, GenerationStatus.PROCESSING, GenerationStatus.RENDERING -> PrimaryNeon
        else -> AccentAmber
    }

    val statusSpanish = when (job.status) {
        GenerationStatus.COMPLETED -> "COMPLETADO"
        GenerationStatus.FAILED -> "FALLIDO"
        GenerationStatus.QUEUED -> "EN COLA"
        GenerationStatus.GENERATING -> "GENERANDO"
        GenerationStatus.PROCESSING -> "PROCESANDO"
        GenerationStatus.RENDERING -> "RENDERIZANDO"
        GenerationStatus.UPLOADING -> "SUBIENDO"
        GenerationStatus.CANCELLED -> "CANCELADO"
    }

    val modalitySpanish = when (job.modality) {
        Modality.IMAGE -> "IMAGEN"
        Modality.VIDEO -> "VÍDEO"
        Modality.AUDIO -> "AUDIO"
        Modality.CHAT -> "CHAT"
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(job.startedAt) { timeFormat.format(Date(job.startedAt)) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Vendor Logo, Status Badge + Modality + Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val vendorName = job.modelId.split("/").firstOrNull() ?: "Atlas"
                    VendorLogo(
                        organization = vendorName,
                        size = 14.dp,
                        containerSize = 28.dp
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = statusSpanish,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = modalitySpanish,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
                    )
                }

                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Prompt snippet
            Text(
                text = job.prompt.ifBlank { "Sin prompt textual" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                ),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Model & Provider info with Cost Tag
            val costToDisplay = job.actualCostUsd
            val costTag = costToDisplay?.let { currencyManager.formatCost(it) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${job.modelId} • ${job.providerId}",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted),
                    fontSize = 11.sp
                )

                if (costTag != null) {
                    Text(
                        text = costTag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = CyanNeon,
                            fontWeight = FontWeight.Bold
                        ),
                        fontSize = 11.sp
                    )
                }
            }

            // Live progress if active
            if (job.status !in setOf(
                    GenerationStatus.COMPLETED,
                    GenerationStatus.FAILED,
                    GenerationStatus.CANCELLED
                )) {
                Spacer(modifier = Modifier.height(8.dp))
                GenerationProgressPanel(
                    job = job,
                    modifier = Modifier.fillMaxWidth(),
                    compact = true
                )
            }

            // Error display if failed
            if (job.status == GenerationStatus.FAILED && job.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = job.error ?: "Error desconocido en la generación.",
                    style = MaterialTheme.typography.bodySmall.copy(color = AccentCoral),
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onShowTechnical,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Diagnóstico",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isFinished) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sondear estado",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (job.status == GenerationStatus.COMPLETED && job.outputs.isNotEmpty()) {
                        Button(
                            onClick = onOpenOutput,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Ver",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
