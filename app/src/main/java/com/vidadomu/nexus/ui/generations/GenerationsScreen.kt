package com.vidadomu.nexus.ui.generations

import com.vidadomu.nexus.ui.components.NexusTopAppBar as TopAppBar
import androidx.compose.foundation.clickable
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
    var statusFilter by rememberSaveable {mutableStateOf("VISIBLE")}
    val archivePrefs=remember {context.getSharedPreferences("history_archive_v1",0)}
    var archived by remember {mutableStateOf(archivePrefs.getStringSet("ids",emptySet())!!.toSet())}
    DisposableEffect(archivePrefs) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs,key ->
            if(key=="ids") archived=prefs.getStringSet("ids",emptySet())!!.toSet()
        }
        archivePrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {archivePrefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    var expandedRuns by remember {mutableStateOf(emptySet<String>())}
    var selectedTechnicalJob by remember {mutableStateOf<GenerationJob?>(null)}
    var pendingCancelJob by remember {mutableStateOf<GenerationJob?>(null)}
    val filteredJobs = remember(jobs, statusFilter,searchQuery,archived) {
        when (statusFilter) {
            "VISIBLE" -> jobs.filter {it.id !in archived}
            "ARCHIVED" -> jobs.filter {it.id in archived}
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
            IconButton(onClick={
                archived=archived+jobs.filter {it.status in setOf(GenerationStatus.FAILED,GenerationStatus.CANCELLED)}.map {it.id}
                archivePrefs.edit().putStringSet("ids",archived).apply()
            }) {Icon(Icons.Default.Inventory2,"Archivar fallidos y cancelados")}
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
                // «Todos» = sin archivar. Los filtros vacíos no ocupan sitio (salvo el que esté elegido).
                val choices=listOf("VISIBLE" to "Todos","ACTIVE" to "En curso","COMPLETED" to "Completados","FAILED" to "Fallidos","COMPARISONS" to "Comparativas","ARCHIVED" to "Archivados")
                items(choices.filter {(key,_) -> key=="VISIBLE" || key==statusFilter || when(key) {
                    "ARCHIVED" -> jobs.any {it.id in archived}
                    "ACTIVE" -> jobs.any {it.status in setOf(GenerationStatus.QUEUED,GenerationStatus.GENERATING,GenerationStatus.PROCESSING,GenerationStatus.RENDERING,GenerationStatus.UPLOADING)}
                    "COMPLETED" -> jobs.any {it.status==GenerationStatus.COMPLETED}
                    "FAILED" -> jobs.any {it.status==GenerationStatus.FAILED}
                    "COMPARISONS" -> jobs.any {it.parameters.containsKey(com.vidadomu.nexus.ui.create.ComparisonPlan.ID)}
                    else -> true
                }}) { (key,label) ->
                    val count=when(key) {
                        "VISIBLE" -> jobs.count {it.id !in archived}
                        "ARCHIVED" -> jobs.count {it.id in archived}
                        "ACTIVE" -> jobs.count {it.status in setOf(GenerationStatus.QUEUED,GenerationStatus.GENERATING,GenerationStatus.PROCESSING,GenerationStatus.RENDERING,GenerationStatus.UPLOADING)}
                        "COMPLETED" -> jobs.count {it.status==GenerationStatus.COMPLETED}
                        "FAILED" -> jobs.count {it.status==GenerationStatus.FAILED}
                        "COMPARISONS" -> jobs.mapNotNull {it.parameters[com.vidadomu.nexus.ui.create.ComparisonPlan.ID]}.distinct().size
                        else -> jobs.size
                    }
                    com.vidadomu.nexus.ui.components.StudioFilterChip(statusFilter==key,{statusFilter=key},label={Text("$label · $count",style=MaterialTheme.typography.labelMedium)},
                        shape=NexusTokens.filter,modifier=Modifier.heightIn(min=NexusTokens.minimumTouch))
                }
            }
            if(filteredJobs.isEmpty()) Box(Modifier.fillMaxSize().padding(NexusTokens.space32),contentAlignment=Alignment.Center) {
                Text(if(searchQuery.isNotBlank()) "No hay resultados. Prueba otra descripción." else "Tus creaciones aparecerán aquí.",
                    style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
            } else LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=NexusTokens.space24)) {
                val dayFormat=SimpleDateFormat("EEEE d MMMM",Locale("es","ES"))
                filteredJobs.groupBy {dayFormat.format(Date(it.startedAt))}.forEach { (day,dayJobs) ->
                    // Total de gasto del día: solo trabajos completados con coste real conocido en €.
                    val dayTotalUsd=dayJobs.filter {it.status==GenerationStatus.COMPLETED}
                        .mapNotNull {it.actualCostUsd?.takeIf {c->c.isFinite() && c>=0}}.sum()
                    item(key="day-$day") {Row(Modifier.fillMaxWidth()
                        .padding(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space12),
                        horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text(day.replaceFirstChar {it.titlecase()},style=MaterialTheme.typography.labelMedium,color=TextSecondary)
                        if(dayTotalUsd>0) Text("Total: ${currencyManager.formatCost(dayTotalUsd)}",
                            style=MaterialTheme.typography.labelMedium,color=TextSecondary)
                    }}
                    val rows=if(statusFilter=="COMPARISONS") dayJobs.map {HistoryRow.Job(it)} else groupFailedRuns(dayJobs,expandedRuns)
                    items(rows,key={it.key}) {row ->
                      if(row is HistoryRow.Header) {
                        // Racha de fallos del mismo intento: una fila que se despliega, en vez de un muro de errores.
                        val first=row.jobs.first();val last=row.jobs.last()
                        val name=remember(first.modelId) {app.modelCatalogRepository.getModelById(first.modelId)?.displayName ?: first.modelId.substringAfterLast('/').replace('-',' ').replace('_',' ')}
                        val timeFormat=remember {SimpleDateFormat("HH:mm",Locale("es","ES"))}
                        val open=row.runId in expandedRuns
                        ListItem(headlineContent={Text("$name · ${row.jobs.size} intentos fallidos",maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)},
                            supportingContent={Text("${timeFormat.format(Date(last.startedAt))}–${timeFormat.format(Date(first.startedAt))} · ${if(open) "toca para plegar" else "toca para ver los intentos"}",style=MaterialTheme.typography.labelMedium)},
                            leadingContent={Icon(Icons.Default.ErrorOutline,null,tint=MaterialTheme.colorScheme.error)},
                            trailingContent={Icon(if(open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)},
                            colors=ListItemDefaults.colors(containerColor=ScreenBackground),
                            modifier=Modifier.clickable(onClickLabel=if(open) "Plegar intentos" else "Ver intentos") {
                                expandedRuns=if(open) expandedRuns-row.runId else expandedRuns+row.runId
                            })
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                      } else {
                        val job=(row as HistoryRow.Job).job
                        if(statusFilter=="COMPARISONS") {
                            val id=job.parameters[com.vidadomu.nexus.ui.create.ComparisonPlan.ID]!!
                            val group=jobs.filter {it.parameters[com.vidadomu.nexus.ui.create.ComparisonPlan.ID]==id}
                            var expanded by rememberSaveable(id) {mutableStateOf(false)}
                            ListItem(headlineContent={Text("Lote de ${job.parameters[com.vidadomu.nexus.ui.create.ComparisonPlan.SIZE]} creaciones",style=MaterialTheme.typography.titleMedium)},
                                supportingContent={Text("${group.count {it.status==GenerationStatus.COMPLETED}} completadas · ${group.size} enviadas",style=MaterialTheme.typography.labelMedium)},
                                trailingContent={IconButton(onClick={expanded=!expanded}) {Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,"Desplegar lote")}},
                                colors=ListItemDefaults.colors(containerColor=ScreenBackground))
                            if(expanded) TextButton(onClick={comparisonId=id},modifier=Modifier.padding(horizontal=NexusTokens.screenMargin)) {Text("Ver comparativa",color=TextSecondary)}
                        } else GenerationSummaryRow(job,currencyManager,
                            onOpen={if(job.status==GenerationStatus.COMPLETED && job.outputs.isNotEmpty()) onNavigateToOutput(job.id) else selectedTechnicalJob=job},
                            onRetry={onPrepareAgain(job)},
                            menu={ close ->
                                if(job.parameters["__tracking_paused"]=="true") DropdownMenuItem(text={Text("Recuperar seguimiento")},onClick={close();scope.launch {
                                    val recovered=app.generationRepository.pollJob(job.id,refreshFailed=true)
                                    if(recovered.getOrNull()?.status in setOf(GenerationStatus.QUEUED,GenerationStatus.GENERATING,GenerationStatus.PROCESSING,GenerationStatus.RENDERING))
                                        com.vidadomu.nexus.core.work.GenerationPollingWorker.enqueue(context,job.id)
                                    android.widget.Toast.makeText(context,if(recovered.isSuccess) "Estado consultado sin volver a generar" else "No se pudo consultar; se conserva el trabajo",android.widget.Toast.LENGTH_LONG).show()
                                }})
                                DropdownMenuItem(text={Text("Preparar de nuevo")},onClick={close();onPrepareAgain(job)})
                                val isTerminal = job.status in setOf(GenerationStatus.COMPLETED,GenerationStatus.FAILED,GenerationStatus.CANCELLED)
                                val canCheckCost = job.status==GenerationStatus.COMPLETED && job.providerId !in setOf("mock_provider","google_flow") && job.providerJobId!=null && !job.providerJobId.startsWith("queue_")
                                val canPollStatus = !isTerminal && job.providerId!="mock_provider" && job.providerJobId!=null
                                if(canCheckCost || canPollStatus) DropdownMenuItem(text={Text(if(canCheckCost) "Consultar coste" else "Actualizar estado")},onClick={close();
                                    scope.launch {
                                        val result=if(canCheckCost) app.generationRepository.refreshConfirmedCost(job.id) else app.generationRepository.pollJob(job.id)
                                        android.widget.Toast.makeText(context,
                                            if(result.isFailure) "No se pudo consultar el proveedor. Se conserva el trabajo sin volver a enviarlo."
                                            else if(canPollStatus) "Estado actualizado: " + generationStatusLabel(result.getOrThrow())
                                            else if(result.getOrNull()?.actualCostUsd==null && result.getOrNull()?.parameters?.get("__actual_credits")==null) "El proveedor no ha informado un importe final."
                                            else "Coste actualizado",android.widget.Toast.LENGTH_LONG).show()
                                    }
                                })
                                if(!isTerminal) {
                                    DropdownMenuItem(text={Text("Detener seguimiento")},onClick={close();
                                        scope.launch {
                                            val stopped = app.generationRepository.stopTrackingJob(job.id)
                                            android.widget.Toast.makeText(context, if(stopped) "Seguimiento local detenido. No se ha cancelado en el proveedor." else "No se pudo detener el seguimiento", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    })
                                    DropdownMenuItem(text={Text("Cancelar generación")},onClick={close();
                                        pendingCancelJob = job
                                    })
                                }
                                DropdownMenuItem(text={Text(if(job.id in archived) "Restaurar en recientes" else "Archivar")},onClick={close();archived=if(job.id in archived) archived-job.id else archived+job.id;archivePrefs.edit().putStringSet("ids",archived).apply()})
                                DropdownMenuItem(text={Text("Diagnóstico")},onClick={close();selectedTechnicalJob=job})
                                if(isTerminal) DropdownMenuItem(text={Text("Eliminar del historial")},onClick={close();
                                    scope.launch {
                                        app.generationRepository.deleteJob(job.id)
                                        android.widget.Toast.makeText(context, "Trabajo eliminado del historial", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                })
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
    }

    pendingCancelJob?.let { job ->
        val canRemoteCancel = app.generationRepository.supportsRemoteCancellation(job.providerId) &&
            !job.providerJobId.isNullOrBlank() &&
            !job.providerJobId.startsWith("queue_")
        val supportMessage = app.generationRepository.remoteCancellationSupportMessage(job)
        AlertDialog(
            onDismissRequest = { pendingCancelJob = null },
            containerColor = SurfaceDark,
            title = {
                Text("Cancelar generación", style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    Text(supportMessage, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text(
                        "«Detener seguimiento» solo pausa las consultas en este dispositivo sin cancelar la tarea en el servidor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
                if (canRemoteCancel) {
                    TextButton(onClick = {
                        val targetId = job.id
                        pendingCancelJob = null
                        scope.launch {
                            val result = app.generationRepository.cancelRemoteGeneration(targetId)
                            val message = result.fold(
                                onSuccess = { "Generación cancelada en ${providerName(it.providerId)}." },
                                onFailure = { (it as? ProviderError)?.humanReadableMessage ?: it.message ?: "No se pudo cancelar en el proveedor." }
                            )
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Text("Cancelar en proveedor", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = {
                        val targetId = job.id
                        pendingCancelJob = null
                        scope.launch {
                            val stopped = app.generationRepository.stopTrackingJob(targetId)
                            android.widget.Toast.makeText(
                                context,
                                if (stopped) "Seguimiento local detenido. No se ha cancelado en el proveedor." else "No se pudo detener el seguimiento",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }) {
                        Text("Detener seguimiento local", color = TextPrimary)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancelJob = null }) {
                    Text("Cerrar", color = TextSecondary)
                }
            }
        )
    }

    // Technical details dialog
    selectedTechnicalJob?.let { job ->
        val statusSpanish = if(job.parameters["__tracking_paused"]=="true") "SEGUIMIENTO PAUSADO" else if(job.parameters["__submission_uncertain"]=="true") "POR CONFIRMAR" else when (job.status) {
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
                Text("Diagnóstico de la tarea", style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    Text("ID Tarea: ${job.id}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("ID Proveedor: ${job.providerJobId ?: "N/D"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("Proveedor: ${providerName(job.providerId)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("Modelo: ${job.modelId}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("Modalidad: ${job.modality.name}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("Estado: $statusSpanish", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    job.estimatedCostUsd?.let {Text("Estimación previa: ${currencyManager.formatCost(it)}",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)}
                    job.parameters["__actual_credits"]?.toDoubleOrNull()?.let {Text(currencyManager.formatCredits(it),style=MaterialTheme.typography.bodyMedium.tabularMoney(),color=TextSecondary)}
                    val cost = job.actualCostUsd
                    if (cost != null) {
                        Text("Coste: ${currencyManager.formatCost(cost)} (${currencyManager.formatUsdDetails(cost)})", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    if(job.status !in setOf(GenerationStatus.COMPLETED,GenerationStatus.FAILED,GenerationStatus.CANCELLED)) GenerationProgressPanel(job,compact=true)
                    job.error?.let {
                        Text("Error: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    job.technicalDetails?.let {
                        Text("Traza Técnica:\n$it", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTechnicalJob = null }) {
                    Text("Cerrar", color = TextSecondary)
                }
            }
        )
    }
}
