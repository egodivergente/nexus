package com.vidadomu.nexus.ui.generations

import com.vidadomu.nexus.ui.components.NexusTopAppBar as TopAppBar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.components.*
import com.vidadomu.nexus.ui.models.providerName
import com.vidadomu.nexus.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerationsScreen(onNavigateToOutput:(String)->Unit,onPrepareAgain:(GenerationJob)->Unit) {
    val context=LocalContext.current
    val app=context.applicationContext as NexusApplication
    val jobs by app.generationRepository.allJobs.collectAsState()
    val historyLoading by app.generationRepository.historyLoading.collectAsState()
    val historyError by app.generationRepository.historyError.collectAsState()
    var searchQuery by rememberSaveable {mutableStateOf("")}
    var searchVisible by rememberSaveable {mutableStateOf(false)}
    var comparisonId by rememberSaveable {mutableStateOf<String?>(null)}
    comparisonId?.let { id -> com.vidadomu.nexus.ui.create.ComparisonResultsDialog(id,{comparisonId=null},onNavigateToOutput) }
    val currencyManager=app.currencyManager
    val scope=rememberCoroutineScope()
    var statusFilter by rememberSaveable {mutableStateOf("ALL")}
    var selectedTechnicalJob by remember {mutableStateOf<GenerationJob?>(null)}
    val filteredJobs = remember(jobs, statusFilter,searchQuery) {
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
            "COMPARISONS" -> jobs.filter { it.parameters.containsKey(com.vidadomu.nexus.ui.create.ComparisonPlan.ID) }
            else -> jobs
        }.filter {matchesCreationSearch(it,searchQuery)}.let { filtered ->
            if(statusFilter == "COMPARISONS") filtered.distinctBy { it.parameters[com.vidadomu.nexus.ui.create.ComparisonPlan.ID] } else filtered
        }
    }

    Scaffold(containerColor=ScreenBackground,topBar={
        TopAppBar(title={Text("Historial",style=com.vidadomu.nexus.ui.components.nexusScreenTitleStyle())},actions={
            IconButton(onClick={searchVisible=!searchVisible}) {Icon(Icons.Default.Search,"Buscar en Historial")}
        },colors=TopAppBarDefaults.topAppBarColors(containerColor=BackgroundDark))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if(historyLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            historyError?.let { raw -> InlineNotice(noticeMessage(raw,"No se ha podido cargar el historial."),
                modifier=Modifier.padding(horizontal=NexusTokens.screenMargin),technicalDetails=raw,actionLabel="Reintentar",
                onAction={scope.launch {app.generationRepository.reloadHistory()}}) }
            if(searchVisible || searchQuery.isNotBlank()) OutlinedTextField(searchQuery,{searchQuery=it},
                modifier=Modifier.fillMaxWidth().padding(horizontal=NexusTokens.screenMargin),singleLine=true,
                placeholder={Text("Buscar modelo o descripción")},shape=NexusTokens.field,
                trailingIcon={IconButton(onClick={searchQuery="";searchVisible=false}) {Icon(Icons.Default.Close,"Cerrar búsqueda")}})
            LazyRow(contentPadding=PaddingValues(horizontal=NexusTokens.screenMargin),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                val choices=listOf("ALL" to "Todos","ACTIVE" to "En curso","COMPLETED" to "Completados","FAILED" to "Fallidos","COMPARISONS" to "Comparativas")
                items(choices) { (key,label) ->
                    val count=when(key) {
                        "ACTIVE" -> jobs.count {it.status in setOf(GenerationStatus.QUEUED,GenerationStatus.GENERATING,GenerationStatus.PROCESSING,GenerationStatus.RENDERING,GenerationStatus.UPLOADING)}
                        "COMPLETED" -> jobs.count {it.status==GenerationStatus.COMPLETED}
                        "FAILED" -> jobs.count {it.status==GenerationStatus.FAILED}
                        "COMPARISONS" -> jobs.mapNotNull {it.parameters[com.vidadomu.nexus.ui.create.ComparisonPlan.ID]}.distinct().size
                        else -> jobs.size
                    }
                    FilterChip(statusFilter==key,{statusFilter=key},label={Text("$label · $count",style=MaterialTheme.typography.labelMedium)},
                        shape=NexusTokens.filter,modifier=Modifier.heightIn(min=NexusTokens.minimumTouch))
                }
            }
            if(filteredJobs.isEmpty()) Box(Modifier.fillMaxSize().padding(NexusTokens.space32),contentAlignment=Alignment.Center) {
                Text(if(searchQuery.isNotBlank()) "No hay resultados. Prueba otra descripción." else "Tus creaciones aparecerán aquí.",
                    style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
            } else LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=NexusTokens.space24)) {
                val dayFormat=SimpleDateFormat("EEEE d MMMM",Locale("es","ES"))
                filteredJobs.groupBy {dayFormat.format(Date(it.startedAt))}.forEach { (day,dayJobs) ->
                    item(key="day-$day") {Text(day.replaceFirstChar {it.titlecase()},
                        Modifier.padding(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space12),
                        style=MaterialTheme.typography.labelMedium,color=TextSecondary)}
                    items(dayJobs,key={it.id}) {job ->
                        if(statusFilter=="COMPARISONS") {
                            val id=job.parameters[com.vidadomu.nexus.ui.create.ComparisonPlan.ID]!!
                            val group=jobs.filter {it.parameters[com.vidadomu.nexus.ui.create.ComparisonPlan.ID]==id}
                            var expanded by rememberSaveable(id) {mutableStateOf(false)}
                            ListItem(headlineContent={Text("Lote de ${job.parameters[com.vidadomu.nexus.ui.create.ComparisonPlan.SIZE]} creaciones",style=MaterialTheme.typography.titleMedium)},
                                supportingContent={Text("${group.count {it.status==GenerationStatus.COMPLETED}} completadas · ${group.size} enviadas",style=MaterialTheme.typography.labelMedium)},
                                trailingContent={IconButton(onClick={expanded=!expanded}) {Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,"Desplegar lote")}},
                                colors=ListItemDefaults.colors(containerColor=ScreenBackground))
                            if(expanded) TextButton(onClick={comparisonId=id},modifier=Modifier.padding(horizontal=NexusTokens.screenMargin), shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {Text("Ver comparativa",color=TextSecondary)}
                        } else GenerationSummaryRow(job,currencyManager,
                            onOpen={if(job.status==GenerationStatus.COMPLETED && job.outputs.isNotEmpty()) onNavigateToOutput(job.id) else selectedTechnicalJob=job},
                            menu={ close ->
                                DropdownMenuItem(text={Text("Preparar de nuevo")},onClick={close();onPrepareAgain(job)})
                                if(job.providerJobId!=null && job.providerId!="mock_provider") DropdownMenuItem(text={Text(if(job.status in setOf(GenerationStatus.COMPLETED,GenerationStatus.FAILED)) "Consultar coste" else "Actualizar estado")},onClick={close();
                                    scope.launch {
                                        val result=if(job.status in setOf(GenerationStatus.COMPLETED,GenerationStatus.FAILED)) app.generationRepository.refreshConfirmedCost(job.id) else app.generationRepository.pollJob(job.id)
                                        android.widget.Toast.makeText(context,
                                            if(result.isFailure) "No se pudo consultar el proveedor. Se conserva el trabajo sin volver a enviarlo."
                                            else if(job.status !in setOf(GenerationStatus.COMPLETED,GenerationStatus.FAILED)) "Estado actualizado: " + generationStatusLabel(result.getOrThrow())
                                            else if(result.getOrNull()?.actualCostUsd==null && result.getOrNull()?.parameters?.get("__actual_credits")==null) "El proveedor no ha informado un importe final."
                                            else "Coste actualizado",android.widget.Toast.LENGTH_LONG).show()
                                    }
                                })
                                DropdownMenuItem(text={Text("Diagnóstico")},onClick={close();selectedTechnicalJob=job})
                                job.parameters[com.vidadomu.nexus.ui.create.ComparisonPlan.ID]?.let {id ->
                                    DropdownMenuItem(text={Text("Ver comparativa")},onClick={close();comparisonId=id})
                                }
                            })
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    // Technical details dialog
    selectedTechnicalJob?.let { job ->
        val statusSpanish = if(job.parameters["__submission_uncertain"]=="true") "Por confirmar" else when (job.status) {
            GenerationStatus.COMPLETED -> "Completado"
            GenerationStatus.FAILED -> "Fallido"
            GenerationStatus.QUEUED -> "En cola"
            GenerationStatus.GENERATING -> "Generando"
            GenerationStatus.PROCESSING -> "Procesando"
            GenerationStatus.RENDERING -> "Renderizando"
            GenerationStatus.UPLOADING -> "Subiendo"
            GenerationStatus.CANCELLED -> "Cancelado"
        }
        AlertDialog(
            onDismissRequest = { selectedTechnicalJob = null },
            containerColor = SurfaceDark,
            title = {
                Text("Diagnóstico de la tarea", style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    Text("ID Tarea: ${job.id}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("ID Proveedor: ${job.providerJobId ?: "N/D"}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("Proveedor: ${providerName(job.providerId)}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("Modelo: ${job.modelId}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("Modalidad: ${job.modality.name}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("Estado: $statusSpanish", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    job.estimatedCostUsd?.let {Text("Estimación previa: ${currencyManager.formatCost(it)}",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)}
                    job.parameters["__actual_credits"]?.toDoubleOrNull()?.let {Text(currencyManager.formatCredits(it),style=MaterialTheme.typography.bodyMedium.tabularMoney(),color=TextSecondary)}
                    val cost = job.actualCostUsd
                    if (cost != null) {
                        Text("Coste: ${currencyManager.formatCost(cost)} (${currencyManager.formatUsdDetails(cost)})", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    if(job.status !in setOf(GenerationStatus.COMPLETED,GenerationStatus.FAILED,GenerationStatus.CANCELLED)) GenerationProgressPanel(job,compact=true)
                    job.error?.let {
                        Text("Error: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    job.technicalDetails?.let {
                        Text("Traza Técnica:\n$it", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTechnicalJob = null }, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Text("Cerrar", color = TextSecondary)
                }
            }
        )
    }
}
