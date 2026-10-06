package com.vidadomu.nexus.ui.billing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vidadomu.nexus.core.currency.CurrencyManager
import com.vidadomu.nexus.core.model.AccountBalance
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.ui.components.VendorLogo
import com.vidadomu.nexus.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BillingLedgerScreen(
    jobs: List<GenerationJob>,
    accountBalance: AccountBalance?,
    currencyManager: CurrencyManager,
    onRefreshBalance: () -> Unit,
    onNavigateToOutput: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf<Modality?>(null) }

    // Financial calculations
    val completedJobs = jobs.filter { it.status == GenerationStatus.COMPLETED }
    val billedJobs = completedJobs.filter { (it.actualCostUsd ?: 0.0) > 0.0 }
    val totalSpentUsd = jobs.sumOf { job ->
        if (job.status == GenerationStatus.COMPLETED) {
            job.actualCostUsd ?: 0.0
        } else 0.0
    }
    val avgCostUsd = if (billedJobs.isNotEmpty()) totalSpentUsd / billedJobs.size else 0.0

    val filteredJobs = if (selectedFilter == null) {
        jobs
    } else {
        jobs.filter { it.modality == selectedFilter }
    }

    val availableUsd = accountBalance?.availableUsd ?: 0.0
    val availableFormatted = currencyManager.formatBalance(availableUsd)
    val availableUsdSecondary = "(\$${String.format(Locale.US, "%.2f", availableUsd)} USD)"

    val totalSpentFormatted = currencyManager.formatCost(totalSpentUsd)
    val avgCostFormatted = if (avgCostUsd > 0) currencyManager.formatCost(avgCostUsd) else "0 cént."

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NexusBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // === CARD DE SALDO DISPONIBLE ===
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NexusSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(
                        colors = listOf(NexusCyan.copy(alpha = 0.5f), NexusPurple.copy(alpha = 0.3f))
                    )
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(NexusSuccess)
                            )
                            Text(
                                text = "SALDO DISPONIBLE ATLAS CLOUD",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = NexusTextSecondary,
                                letterSpacing = 1.sp
                            )
                        }
                        IconButton(
                            onClick = onRefreshBalance,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualizar saldo",
                                tint = NexusCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = availableFormatted,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = availableUsdSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NexusTextSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    HorizontalDivider(color = NexusSurfaceVariant, thickness = 1.dp)

                    // Métricas de facturación
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Gasto Acumulado",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusTextSecondary
                            )
                            Text(
                                text = totalSpentFormatted,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NexusAmber
                            )
                        }

                        Column {
                            Text(
                                text = "Coste Medio / Salida",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusTextSecondary
                            )
                            Text(
                                text = avgCostFormatted,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NexusCyan
                            )
                        }

                        Column {
                            Text(
                                text = "Total Salidas",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusTextSecondary
                            )
                            Text(
                                text = "${jobs.size} reg.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // === FILTROS POR MODALIDAD ===
        item {
            Text(
                text = "REGISTRO DE SALIDAS Y RECIBOS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = NexusTextSecondary,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("TODOS (${jobs.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexusCyan,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
                items(Modality.values()) { mod ->
                    val count = jobs.count { it.modality == mod }
                    val label = when (mod) {
                        Modality.IMAGE -> "IMAGEN"
                        Modality.VIDEO -> "VÍDEO"
                        Modality.AUDIO -> "AUDIO"
                        Modality.CHAT -> "CHAT"
                    }
                    FilterChip(
                        selected = selectedFilter == mod,
                        onClick = { selectedFilter = mod },
                        label = { Text("$label ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexusCyan,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        // === LISTA DE RECIBOS DE GENERACIÓN ===
        if (filteredJobs.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NexusSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Sin movimientos registrados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Las generaciones completadas aparecerán aquí con su coste final verificado por Atlas Cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusTextSecondary
                        )
                    }
                }
            }
        } else {
            items(filteredJobs, key = { it.id }) { job ->
                BilledOutputReceiptCard(
                    job = job,
                    currencyManager = currencyManager,
                    onViewClick = { onNavigateToOutput(job.id) }
                )
            }
        }
    }
}

@Composable
fun BilledOutputReceiptCard(
    job: GenerationJob,
    currencyManager: CurrencyManager,
    onViewClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("es", "ES")) }
    val formattedDate = remember(job.startedAt) { dateFormat.format(Date(job.startedAt)) }

    val actualCostUsd = job.actualCostUsd
    val isBilled = job.status == GenerationStatus.COMPLETED && (actualCostUsd ?: 0.0) > 0.0

    val costFormatted = if (isBilled && actualCostUsd != null) {
        currencyManager.formatCost(actualCostUsd)
    } else if (job.status == GenerationStatus.FAILED) {
        "0,00 € (Sin coste)"
    } else {
        actualCostUsd?.let { currencyManager.formatCost(it) } ?: "Calculando..."
    }

    val costUsdLabel = if (actualCostUsd != null && actualCostUsd > 0) {
        "(\$${String.format(Locale.US, "%.4f", actualCostUsd)} USD)"
    } else ""

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NexusSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                colors = listOf(NexusSurfaceVariant, NexusSurface)
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Vendor Logo, Model Name, Modality & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val vendorName = job.modelId.split("/").firstOrNull() ?: "Atlas"
                    VendorLogo(
                        organization = vendorName,
                        size = 18.dp,
                        containerSize = 36.dp
                    )

                    Column {
                        Text(
                            text = job.modelId.substringAfterLast("/"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "$vendorName • $formattedDate",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Status chip
                val (statusText, statusBg, statusColor) = when (job.status) {
                    GenerationStatus.COMPLETED -> Triple("FACTURADO", NexusSuccess.copy(alpha = 0.15f), NexusSuccess)
                    GenerationStatus.FAILED -> Triple("SIN COSTE", SurfaceVariantDark, NexusTextSecondary)
                    GenerationStatus.CANCELLED -> Triple("CANCELADO", SurfaceVariantDark, NexusTextSecondary)
                    else -> Triple("EN CURSO", NexusCyan.copy(alpha = 0.15f), NexusCyan)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusBg
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp
                    )
                }
            }

            // Preview Thumbnail + Prompt
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val outputUrl = job.outputs.firstOrNull()?.url
                if (outputUrl != null && job.modality == Modality.IMAGE) {
                    AsyncImage(
                        model = outputUrl,
                        contentDescription = "Output preview",
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, NexusSurfaceVariant, RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Text(
                    text = job.prompt.ifBlank { "Sin instrucción textual" },
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = NexusSurfaceVariant, thickness = 0.5.dp)

            // Footer: Cost Breakdown and "Ver ->" Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Coste final:",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusTextSecondary
                    )
                    Text(
                        text = costFormatted,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isBilled) NexusCyan else NexusTextSecondary
                    )
                    if (costUsdLabel.isNotBlank()) {
                        Text(
                            text = costUsdLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                if (job.status == GenerationStatus.COMPLETED && job.outputs.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = onViewClick,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NexusCyan.copy(alpha = 0.15f),
                            contentColor = NexusCyan
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = "Ver",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
