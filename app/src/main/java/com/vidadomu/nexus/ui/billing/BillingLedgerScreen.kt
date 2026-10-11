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
import com.vidadomu.nexus.ui.components.GenerationSummaryRow
import com.vidadomu.nexus.ui.components.VendorLogo
import com.vidadomu.nexus.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BillingLedgerScreen(
    jobs: List<GenerationJob>,
    accountBalance: AccountBalance?,
    currencyManager: CurrencyManager,
    onRefreshBalance: () -> Unit,
    onNavigateToOutput: (String) -> Unit,
    modifier: Modifier = Modifier,
    showBalance: Boolean = true
) {
    val currencyState by currencyManager.state.collectAsState()
    currencyState.preference
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

    val totalSpentFormatted=currencyManager.formatCost(totalSpentUsd)
    val avgCostFormatted=currencyManager.formatCost(avgCostUsd)
    var balanceDetails by remember {mutableStateOf(false)}
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space16),
        verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
        if(showBalance) item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Saldo Atlas",style=MaterialTheme.typography.labelMedium,color=TextSecondary)
                    Text(accountBalance?.let {currencyManager.formatBalance(it.availableUsd)} ?: "Sin consultar",style=MaterialTheme.typography.titleMedium.tabularMoney())
                }
                TextButton(onClick={balanceDetails=!balanceDetails}, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {Text("Detalles",color=TextSecondary)}
                IconButton(onClick=onRefreshBalance) {Icon(Icons.Default.Refresh,"Actualizar saldo")}
            }
            if(balanceDetails) accountBalance?.let {Text(currencyManager.formatUsdDetails(it.availableUsd),style=MaterialTheme.typography.labelMedium.tabularMoney(),color=TextSecondary)}
        }
        item {
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space24),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                BillingMetric("Gasto acumulado",totalSpentFormatted)
                BillingMetric("Coste medio / salida",avgCostFormatted)
                BillingMetric("Registros",jobs.size.toString())
            }
        }
        item {
            Text("Recibos",style=MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                item {FilterChip(selectedFilter==null,{selectedFilter=null},label={Text("Todos · ${jobs.size}",style=MaterialTheme.typography.labelMedium)},shape=NexusTokens.filter,modifier=Modifier.heightIn(min=NexusTokens.minimumTouch))}
                items(Modality.values()) {mod ->
                    val label=when(mod) {Modality.IMAGE->"Imagen";Modality.VIDEO->"Vídeo";Modality.AUDIO->"Audio";Modality.CHAT->"Texto"}
                    FilterChip(selectedFilter==mod,{selectedFilter=mod},label={Text("$label · ${jobs.count {it.modality==mod}}",style=MaterialTheme.typography.labelMedium)},shape=NexusTokens.filter,modifier=Modifier.heightIn(min=NexusTokens.minimumTouch))
                }
            }
        }
        if(filteredJobs.isEmpty()) item {Text("Sin movimientos registrados",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)}
        else items(filteredJobs,key={it.id}) {job ->
            BilledOutputReceiptCard(job,currencyManager) {onNavigateToOutput(job.id)}
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun BillingMetric(label:String,value:String) {
    Column {
        Text(label,style=MaterialTheme.typography.labelMedium,color=TextSecondary)
        Text(value,style=MaterialTheme.typography.titleMedium.tabularMoney())
    }
}

@Composable
fun BilledOutputReceiptCard(job:GenerationJob,currencyManager:CurrencyManager,onViewClick:()->Unit) {
    var details by remember {mutableStateOf(false)}
    GenerationSummaryRow(job,currencyManager,onOpen=onViewClick,menu={close ->
        DropdownMenuItem(text={Text("Detalles del coste")},onClick={close();details=true})
        if(job.status==GenerationStatus.COMPLETED && job.outputs.isNotEmpty()) DropdownMenuItem(text={Text("Ver resultado")},onClick={close();onViewClick()})
    })
    if(details) AlertDialog(onDismissRequest={details=false},title={Text("Detalles del coste")},text={Column {
        Text("Proveedor: ${job.providerId}",style=MaterialTheme.typography.bodyMedium)
        job.actualCostUsd?.let {Text(currencyManager.formatUsdDetails(it),style=MaterialTheme.typography.bodyMedium.tabularMoney())}
        job.parameters["__actual_credits"]?.toDoubleOrNull()?.let {Text(currencyManager.formatCredits(it),style=MaterialTheme.typography.bodyMedium.tabularMoney())}
        Text("ID: ${job.id}",style=MaterialTheme.typography.labelMedium)
    }},confirmButton={TextButton(onClick={details=false}, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {Text("Cerrar")}})
}
