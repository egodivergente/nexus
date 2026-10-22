package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.ui.components.NexusTopAppBar as TopAppBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.models.providerName
import com.vidadomu.nexus.ui.models.ImageModelChoices
import com.vidadomu.nexus.ui.models.catalogFamily

@Composable
fun ComparisonScreen(controller: ComparisonController, onDismiss: () -> Unit, onOpenOutput: (String) -> Unit) {
    val state by controller.state.collectAsState()
    val app = LocalContext.current.applicationContext as NexusApplication
    val jobs by app.generationRepository.allJobs.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf(false) }
    val currency by app.currencyManager.state.collectAsState()
    currency.preference
    fun cost(amount: Double, unit: ComparisonCurrency): String = if(unit == ComparisonCurrency.USD)
        app.currencyManager.formatUsd(amount) else java.math.BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()+" créditos Crun"
    val total = state.total?.entries?.joinToString(" + ") { cost(it.value, it.key) }
    val completed = state.phase in setOf(ComparisonPhase.SENDING, ComparisonPhase.RESULTS)
    val dismiss = { controller.dismiss(); onDismiss() }
    if(results && state.batchId != null) ComparisonResultsDialog(state.batchId!!, { results = false }, onOpenOutput)

    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null, shape = androidx.compose.ui.graphics.RectangleShape, contentWindowInsets = { WindowInsets(0) }) {
        com.vidadomu.nexus.ui.theme.NexusSheetSystemBars()
        Scaffold(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
            topBar = {
                TopAppBar(showBalance=false,title = { Column {
                    Text("Varios modelos", style = MaterialTheme.typography.titleLarge)
                    Text(when(state.phase) {
                        ComparisonPhase.CHOOSE -> "1 · Elige modelos y cantidades"
                        ComparisonPhase.PREPARING -> "Comprobando compatibilidad y costes"
                        ComparisonPhase.REVIEW -> "2 · Revisa antes de generar"
                        else -> "Una idea · distintas interpretaciones"
                    }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }, navigationIcon = { IconButton(onClick = dismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver al lienzo") } })
            },
            bottomBar = {
                Surface(tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if(state.phase in setOf(ComparisonPhase.REVIEW, ComparisonPhase.PREPARING)) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Total estimado", style = MaterialTheme.typography.labelMedium)
                                Text(if(total != null) "≈ $total" else "Coste sin estimación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        when(state.phase) {
                            ComparisonPhase.CHOOSE -> {
                                if(state.withReferences && state.source.attachedMedia.isEmpty()) Text("Añade tus imágenes de referencia en el lienzo.", style = MaterialTheme.typography.bodySmall)
                                Button(onClick = controller::prepare,
                                enabled = state.canReview,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Revisar lote · ${state.selectedRequests} generaciones") }
                            }
                            ComparisonPhase.PREPARING -> {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                TextButton(onClick = controller::edit, modifier = Modifier.fillMaxWidth()) { Text("Volver a la selección") }
                            }
                            ComparisonPhase.REVIEW -> {
                                if(!state.canSend || state.quotedAt == 0L) Text("Se enviarán los modelos listos; los que tengan errores quedan pendientes.", style = MaterialTheme.typography.bodySmall)
                                Button(onClick = controller::send, enabled = state.canSend && state.quotedAt != 0L,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if(BuildConfig.OFFLINE_QA) "Validar sin enviar" else "Generar lote · ${state.requestCount} generaciones") }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    TextButton(onClick = controller::edit) { Text("Cambiar modelos") }
                                    TextButton(onClick = controller::prepare) { Text("Actualizar costes") }
                                }
                            }
                            ComparisonPhase.SENDING -> {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                TextButton(onClick = controller::stop, enabled = !state.stopped, modifier = Modifier.fillMaxWidth()) { Text("Detener pendientes") }
                            }
                            ComparisonPhase.RESULTS -> Button(onClick = { results = true },
                                enabled = jobs.any { it.parameters[ComparisonPlan.ID] == state.batchId }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Ver resultados") }
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(state.source.prompt.ifBlank { "Vuelve al lienzo y escribe un prompt para comparar sus interpretaciones." },
                        style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text("Mismo prompt · ${state.source.attachedMedia.size} ${if(state.source.attachedMedia.size == 1) "referencia" else "referencias"} · ${if(state.phase == ComparisonPhase.CHOOSE) state.selectedRequests else state.requestCount} generaciones en total",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(BuildConfig.OFFLINE_QA) item { Text("LABORATORIO · tarifas simuladas · envíos bloqueados", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                if(state.phase == ComparisonPhase.CHOOSE) {
                    item {
                        if(state.source.attachedMedia.isEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(state.withReferences, { controller.setReferenceMode(true) }, label = { Text("Con referencias") })
                            FilterChip(!state.withReferences, { controller.setReferenceMode(false) }, label = { Text("Desde texto") })
                        }
                        Text(if(state.withReferences) "Se comprobarán tus imágenes con cada modelo." else "Mismo prompt, con los ajustes válidos de cada modelo.", style = MaterialTheme.typography.bodySmall)
                    }
                    item { Text("De 1 a ${ComparisonPlan.MAX_GENERATIONS_PER_MODEL} generaciones por modelo. Puedes usar un solo modelo o combinar hasta ${ComparisonPlan.MAX_MODELS}.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text(if(state.source.selectedModality==Modality.VIDEO) "Hailuo, Kling, Seedance…" else "Seedream, GPT, Flux…") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = { if(query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Borrar búsqueda") } })
                    }
                    if(state.selected.isNotEmpty()) item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.catalog.filter { ComparisonPlan.key(it) in state.selected }, key = ComparisonPlan::key) { model ->
                                InputChip(selected = true, onClick = { controller.toggle(model) }, label = { Text(canvasModelIdentity(model).title, maxLines = 2) },
                                    trailingIcon = { Icon(Icons.Default.Close, "Quitar ${model.displayName}", Modifier.size(16.dp)) })
                            }
                        }
                    }
                    val shortlist = if(state.source.selectedModality==Modality.VIDEO) state.catalog.take(12) else ImageModelChoices.shortlist(state.catalog)
                    val visible = (if(showAll || query.isNotBlank()) state.catalog else
                        (shortlist + state.catalog.filter { ComparisonPlan.key(it) in state.selected }).distinctBy(ComparisonPlan::key))
                        .filter { query.isBlank() || (it.displayName+" "+it.organization+" "+providerName(it.providerId)).contains(query, true) }
                    item { Text(if(showAll || query.isNotBlank()) "Modelos y variantes" else "Tus familias · versiones recientes", style = MaterialTheme.typography.titleSmall) }
                    if(visible.isEmpty()) item { Text("No hay modelos visibles que coincidan. Comprueba los proveedores y los modelos ocultos en el catálogo.", style = MaterialTheme.typography.bodyMedium) }
                    items(visible, key = ComparisonPlan::key) { model ->
                        val key = ComparisonPlan.key(model)
                        val selected = key in state.selected
                        val checking = key in state.checking
                        val problem = state.selectionProblems[key]
                        val enabled = !checking && (problem == null || key !in state.checked)
                        Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { controller.toggle(model) }.heightIn(min = 64.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            com.vidadomu.nexus.ui.components.VendorLogo(model.organization, size = 28.dp)
                            Column(Modifier.weight(1f)) {
                                Text(canvasModelIdentity(model).title, style = MaterialTheme.typography.titleSmall)
                                Text(canvasModelIdentity(model).detail + if(catalogFamily(model) == "Midjourney") " · Midjourney / Youchuan" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val limit = ComparisonPlan.referenceLimit(model)
                                Text(when {
                                    checking -> "Comprobando referencias…"
                                    problem != null -> problem
                                    key !in state.checked -> "Toca para comprobar compatibilidad"
                                    !state.withReferences -> "Desde texto"
                                    limit == Int.MAX_VALUE -> "Referencias admitidas · sin límite publicado"
                                    else -> "Hasta $limit " + if(limit == 1) "referencia" else "referencias"
                                }, style = MaterialTheme.typography.labelSmall, color = if(problem != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                            }
                            if(checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Checkbox(selected, { controller.toggle(model) }, enabled = enabled)
                        }
                        if(selected) Row(Modifier.fillMaxWidth().padding(start = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Generaciones", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                            IconButton(onClick = { controller.setQuantity(model, state.quantity(model)-1) }, enabled = state.quantity(model) > 1) {
                                Icon(Icons.Default.Remove, "Menos generaciones de ${model.displayName}")
                            }
                            Text(state.quantity(model).toString(), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { controller.setQuantity(model, state.quantity(model)+1) }, enabled = state.quantity(model) < ComparisonPlan.MAX_GENERATIONS_PER_MODEL) {
                                Icon(Icons.Default.Add, "Más generaciones de ${model.displayName}")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                    if(query.isBlank() && state.catalog.size > shortlist.size) item {
                        TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
                            Text(if(showAll) "Ver solo mis familias" else "Otros modelos y variantes")
                        }
                    }
                } else {
                    if(state.phase == ComparisonPhase.REVIEW) item {
                        Text("Tus referencias se conservan en cada solicitud. Revisa los ajustes que cambian entre modelos. Una semilla fija puede repetir resultados.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(state.entries, key = { ComparisonPlan.key(it.model) }) { entry ->
                        var details by rememberSaveable(ComparisonPlan.key(entry.model)) { mutableStateOf(false) }
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(canvasModelIdentity(entry.model).title, style = MaterialTheme.typography.titleSmall)
                                    Text(providerName(entry.model.providerId), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                entry.subtotal?.let { Text("≈ "+cost(it.amount, it.currency), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                            }
                            entry.cost?.let { Text("${entry.quantity} generaciones × ≈ ${cost(it.amount, it.currency)}", style = MaterialTheme.typography.labelSmall) }
                            Text(entry.error ?: entry.status, style = MaterialTheme.typography.bodySmall,
                                color = if(entry.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            entry.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                            entry.prepared?.changes?.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if(!completed && entry.prepared != null) {
                                TextButton(onClick = { details = !details }, contentPadding = PaddingValues(0.dp)) { Text(if(details) "Ocultar ajustes" else "Ajustes y tarifa") }
                                if(details) {
                                    entry.prepared.state.selectedModel?.parameters?.forEach { parameter ->
                                        val value = entry.prepared.state.parameters[parameter.key].orEmpty()
                                        if(value.isNotBlank()) Text("${ParameterPresentation.title(parameter)}: ${ParameterPresentation.describe(parameter,value)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    entry.cost?.let { Text(it.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                    if(completed) item { Text("Los resultados quedan agrupados en Historial. Los envíos pendientes no se reanudan automáticamente si cierras Nexus.", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
fun ComparisonResultsDialog(batchId: String, onDismiss: () -> Unit, onOpenOutput: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val allJobs by app.generationRepository.allJobs.collectAsState()
    val jobs = remember(allJobs, batchId) { allJobs.filter { it.parameters[ComparisonPlan.ID] == batchId }.sortedBy { it.startedAt } }
    val expected = jobs.maxOfOrNull { it.parameters[ComparisonPlan.SIZE]?.toIntOrNull() ?: 0 } ?: 0
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null, shape = androidx.compose.ui.graphics.RectangleShape, contentWindowInsets = { WindowInsets(0) }) {
        com.vidadomu.nexus.ui.theme.NexusSheetSystemBars()
        Scaffold(Modifier.fillMaxSize().safeDrawingPadding(), topBar = {
            TopAppBar(title = { Text("Resultados del lote") }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar resultados del lote") } })
        }) { padding ->
            LazyVerticalGrid(GridCells.Adaptive(152.dp), Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) { Column {
                    Text(jobs.firstOrNull()?.prompt.orEmpty(), maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    if(jobs.size < expected) Text("${jobs.size} de $expected solicitudes registradas. Las restantes no se reanudan automáticamente.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                } }
                items(jobs, key = { it.id }) { job ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val output = job.outputs.firstOrNull()
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable(enabled = output != null) { onDismiss(); onOpenOutput(job.id) }) {
                            if(output != null) AsyncImage(model = ImageRequest.Builder(context).data(output.localUri ?: output.url).size(512).crossfade(true).apply {
                                if(output.type==Modality.VIDEO) decoderFactory {result,options,_->coil.decode.VideoFrameDecoder(result.source,options)}
                            }.build(),
                                contentDescription = "Resultado de ${job.parameters[ComparisonPlan.NAME] ?: job.modelId}", contentScale = ContentScale.Fit)
                            else Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                val active = job.status !in setOf(GenerationStatus.FAILED, GenerationStatus.CANCELLED, GenerationStatus.COMPLETED)
                                if(active) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                else Icon(if(job.status == GenerationStatus.COMPLETED) Icons.Default.ImageNotSupported else Icons.Default.ErrorOutline, null)
                                Text(when(job.status) {
                                    GenerationStatus.FAILED -> "Revisa el envío"
                                    GenerationStatus.CANCELLED -> "Cancelado"
                                    GenerationStatus.COMPLETED -> "Sin vista previa"
                                    else -> "Generando…"
                                }, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text(job.parameters[ComparisonPlan.NAME] ?: job.modelId, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        job.parameters[ComparisonPlan.NUMBER]?.let { number ->
                            Text("Generación $number de ${job.parameters[ComparisonPlan.MODEL_SIZE] ?: "1"}", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(providerName(job.providerId), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        job.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        if(job.outputs.size > 1) Text("${job.outputs.size} resultados · toca para verlos", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
