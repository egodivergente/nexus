package com.vidadomu.nexus.assistant

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.cost.CostConfidence
import com.vidadomu.nexus.agent.core.cost.CostEstimate
import com.vidadomu.nexus.agent.core.schema.ParameterValidator
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.PlanState
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.agent.core.tools.PlanEdit
import com.vidadomu.nexus.ui.create.CreateUiState
import com.vidadomu.nexus.ui.theme.*
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun AssistantScreen(
    onOpenInCanvas: (CreateUiState) -> Unit,
    onNavigateToOutput: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    vm: AssistantViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsState()
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
    var showContext by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val draftPrefs = LocalContext.current.getSharedPreferences("assistant_draft_v1", android.content.Context.MODE_PRIVATE)
    var input by rememberSaveable(stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(draftPrefs.getString("text", "").orEmpty())) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris: List<Uri> -> vm.attach(uris) }

    var following by rememberSaveable { mutableStateOf(true) }
    var automaticScroll by remember { mutableStateOf(false) }
    suspend fun followLast() {
        if(ui.items.isEmpty()) return
        automaticScroll = true
        try { listState.scrollToItem(ui.items.size - 1 + if(ui.thinking) 1 else 0) }
        finally { automaticScroll = false }
    }
    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.isScrollInProgress, listState.canScrollForward, automaticScroll) }.collect { (moving, canForward, automatic) ->
            if (moving && !automatic) following = !canForward
        }
    }
    LaunchedEffect(ui.items.size, ui.thinking, ui.plans, ui.scrollRequest) {
        if (following) followLast()
    }
    LaunchedEffect(ui.scrollRequest) {
        if (ui.scrollRequest > 0) { following = true; followLast() }
    }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it.second); vm.consumeMessage() } }

    if (showSettings) AssistantSettingsDialog(ui, vm, onDismiss = { showSettings = false })

    Scaffold(containerColor = ScreenBackground, snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("¿Qué quieres crear?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
                if(compact) IconButton(onClick = { showContext = !showContext }) { Icon(Icons.Default.Tune, "Proyecto y modo") }
                Box {
                    IconButton(onClick = { showHistory = true }, enabled = !ui.thinking) { Icon(Icons.Default.History, "Conversaciones") }
                    DropdownMenu(expanded = showHistory, onDismissRequest = { showHistory = false }) {
                        val entries = remember(showHistory) { vm.conversations() }
                        if(entries.isEmpty()) DropdownMenuItem(text = { Text("Aún no hay conversaciones guardadas") }, onClick = { showHistory = false })
                        entries.forEach { entry -> DropdownMenuItem(text = { Text(entry.title, maxLines = 2) }, onClick = { showHistory = false; vm.openConversation(entry.id) }) }
                    }
                }
                IconButton(onClick = vm::newConversation, enabled = !ui.thinking) { Icon(Icons.Default.AddComment, "Nueva conversación") }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Ajustes del asistente") }
            }
            if (!compact || showContext) {
                AgentModeSelector(ui.mode, vm::setMode)
                ProjectRow(ui, vm)
                ProjectReferences(ui, vm)
            }
            if (ui.settings.offline && (!compact || showContext)) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("Sin LLM configurado: planificador local (elige modelo y prepara el plan, sin razonamiento). Configura Bedrock en ⚙.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ui.items.isEmpty()) item { EmptyHint() }
                items(ui.items, key = { it.id }) { item ->
                    when (item) {
                        is ChatItem.User -> UserBubble(item)
                        is ChatItem.Assistant -> AssistantBubble(item)
                        is ChatItem.Notice -> NoticeRow(item)
                        is ChatItem.Proposal -> ProposalCard(item, onApply = { vm.applyProposal(item.id) }, onReject = { vm.rejectProposal(item.id) })
                        is ChatItem.PlanCard -> ui.plans[item.planId]?.let { p ->
                            CompactGeneration(p, vm.outputs(p.jobId),
                                onOpenOutput = { p.jobId?.let(onNavigateToOutput) },
                                details = { PlanCardView(p,
                                onEdit = { vm.editPlan(p.plan.id, it) },
                                onDiscard = { vm.discardPlan(p.plan.id) },
                                onGenerate = { vm.generate(p.plan.id, p.plan.fingerprint()) },
                                assistantBusy = ui.thinking,
                                onOpenCanvas = { scope.launch { vm.canvasState(p.plan.id)?.let(onOpenInCanvas) } },
                                onOpenOutput = { p.jobId?.let(onNavigateToOutput) }) })
                        }
                    }
                }
                if (ui.thinking) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Pensando…", color = TextMuted) } }
            }
            if (!following) TextButton(onClick = { following = true; scope.launch { followLast() } }) { Text("Ir al último mensaje ↓") }
            PendingGenerations(ui, vm::generatePending)
            AgentStatus(ui, onOpenSettings)
            InputBar(ui, input, onInput = { input = it; draftPrefs.edit().putString("text", it.text).apply() }, onAttach = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                onSend = { vm.send(input.text); draftPrefs.edit().remove("text").apply(); input = androidx.compose.ui.text.input.TextFieldValue("") }, onStop = vm::stop, onRemove = vm::removeAttachment)
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, start = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("¿Una imagen, un vídeo o una idea por desarrollar?", color = TextMuted)
    }
}

@Composable
private fun ProjectRow(ui: AssistantUiState, vm: AssistantViewModel) {
    var open by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val active = ui.projects.firstOrNull { it.id == ui.activeProjectId }
    var editingMemory by remember { mutableStateOf(false) }
    Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
      Box {
        AssistChip(onClick = { open = true }, label = { Text(active?.name?.let { "Proyecto: $it" } ?: "Sin proyecto activo") }, leadingIcon = { Icon(Icons.Default.Folder, null, Modifier.size(18.dp)) })
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Sin proyecto") }, onClick = { vm.selectProject(null); open = false })
            ui.projects.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { vm.selectProject(p.id); open = false }) }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Nuevo proyecto…") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { open = false; creating = true })
        }
      }
      if (active != null) {
          Spacer(Modifier.width(6.dp))
          AssistChip(onClick = { editingMemory = true }, label = { Text(if (active.memory.isEmpty()) "Memoria (vacía)" else "Memoria") }, leadingIcon = { Icon(Icons.Default.Book, null, Modifier.size(18.dp)) })
      }
    }
    if (editingMemory && active != null) ProjectMemoryDialog(active, vm) { editingMemory = false }
    if (creating) AlertDialog(onDismissRequest = { creating = false }, title = { Text("Nuevo proyecto") },
        text = { OutlinedTextField(name, { name = it.take(80) }, singleLine = true, label = { Text("Nombre") }) },
        confirmButton = { TextButton(onClick = { vm.createProject(name); name = ""; creating = false }, enabled = name.isNotBlank()) { Text("Crear") } },
        dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancelar") } })
}

/** Biblia del proyecto: lo que el asistente lee en cada mensaje mientras el proyecto está activo. */
@Composable
private fun ProjectMemoryDialog(p: com.vidadomu.nexus.agent.core.assets.Project, vm: AssistantViewModel, onClose: () -> Unit) {
    val m = p.memory
    var description by remember(p.id) { mutableStateOf(p.description.orEmpty()) }
    var synopsis by remember(p.id) { mutableStateOf(m.synopsis.orEmpty()) }
    var tone by remember(p.id) { mutableStateOf(m.tone.orEmpty()) }
    var style by remember(p.id) { mutableStateOf(m.visualStyle.orEmpty()) }
    var rules by remember(p.id) { mutableStateOf(m.rules.joinToString("\n")) }
    var models by remember(p.id) { mutableStateOf(m.preferredModels.joinToString("\n")) }
    val removed = remember(p.id) { mutableStateListOf<Long>() }
    val characters = remember(p.id) { vm.projectCharacters(p.id) }
    val assetCount = remember(p.id) { vm.projectAssetCount(p.id) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Memoria de «${p.name}»") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("El asistente la lee en cada mensaje para que los prompts sean coherentes. También la rellena él cuando le cuentas cosas del proyecto. $assetCount archivos etiquetados con este proyecto.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                OutlinedTextField(description, { description = it.take(400) }, label = { Text("Descripción corta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(synopsis, { synopsis = it.take(2000) }, label = { Text("Sinopsis") }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tone, { tone = it.take(2000) }, label = { Text("Tono / género") }, minLines = 1, maxLines = 4, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(style, { style = it.take(2000) }, label = { Text("Estilo visual") }, minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(rules, { rules = it.take(6000) }, label = { Text("Reglas fijas (una por línea)") }, minLines = 2, maxLines = 8, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(models, { models = it.take(800) }, label = { Text("Modelos preferidos (uno por línea)") }, minLines = 1, maxLines = 4, modifier = Modifier.fillMaxWidth())
                if (characters.isNotEmpty()) {
                    Text("Personajes", fontWeight = FontWeight.SemiBold)
                    characters.forEach { c -> Text("• ${c.name}" + (c.description?.let { ": $it" } ?: ""), style = MaterialTheme.typography.bodySmall) }
                }
                val notes = m.notes.filter { it.at !in removed }
                if (notes.isNotEmpty()) {
                    Text("Notas y decisiones", fontWeight = FontWeight.SemiBold)
                    notes.asReversed().forEach { n ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(n.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { removed += n.at }) { Icon(Icons.Default.Delete, "Borrar nota", Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveProjectMemory(p.id, description, synopsis, tone, style, rules, models,
                    keepNotes = if (removed.isEmpty()) null else m.notes.map { it.at }.filter { it !in removed }.toSet())
                onClose()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
    )
}

@Composable
private fun UserBubble(item: ChatItem.User) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (item.attachments.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(item.attachments, key = { it.id }) { AttachmentThumb(it) } }
        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)), modifier = Modifier.widthIn(max = 320.dp).padding(top = 4.dp, start = 40.dp)) {
            Text(item.text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp), color = TextPrimary)
        }
    }
}

@Composable
private fun AssistantBubble(item: ChatItem.Assistant) {
    Column(Modifier.fillMaxWidth()) {
        if (item.text.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("NEXUS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp), color = TextMuted)
            }
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
                ChatMarkdownView(item.text, Modifier.padding(horizontal = 18.dp, vertical = 16.dp))
            }
        }
        if (item.toolCalls.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp, start = 6.dp)) {
            Icon(Icons.Default.Build, null, Modifier.size(12.dp), tint = TextMuted)
            Spacer(Modifier.width(6.dp))
            Text(item.toolCalls.distinct().joinToString("  ·  "), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp), color = TextMuted)
        }
    }
}

@Composable
private fun NoticeRow(item: ChatItem.Notice) {
    Text(item.text, style = MaterialTheme.typography.bodySmall, color = if (item.error) ErrorRed else TextMuted, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun ProposalCard(item: ChatItem.Proposal, onApply: () -> Unit, onReject: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Propuesta de organización", fontWeight = FontWeight.SemiBold)
            Text(item.description, style = MaterialTheme.typography.bodyMedium)
            when (item.state) {
                ProposalState.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onApply) { Text("Aplicar") }
                    OutlinedButton(onClick = onReject) { Text("Descartar") }
                }
                ProposalState.APPLYING -> LinearProgressIndicator(Modifier.fillMaxWidth())
                ProposalState.APPLIED -> Text("Aplicada (es lógica: no se mueve ni renombra ningún archivo del disco).", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                ProposalState.REJECTED -> Text("Descartada.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                ProposalState.FAILED -> Text("No se pudo aplicar: ${item.result.orEmpty()}", color = ErrorRed, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---- tarjeta de plan ------------------------------------------------------------------------

internal fun visibleParams(plan: GenerationPlan): List<Pair<String, String>> =
    plan.effectiveParams.filter { (k, v) -> k != "prompt" && !ParameterValidator.isBlankLike(v) }.toList()

internal fun costLine(e: CostEstimate?): String = when {
    e == null -> "Coste no disponible"
    e.amountUsd != null -> "≈ " + String.format(Locale("es", "ES"), "%.3f $", e.amountUsd) + when (e.confidence) {
        CostConfidence.NEXUS_QUOTE -> " · cotización de la calculadora"
        CostConfidence.PUBLISHED_RATE -> " · tarifa publicada × unidades"
        CostConfidence.BASE_ONLY -> " · orientativo"
        CostConfidence.UNKNOWN -> ""
    }
    else -> "Coste exacto no disponible · ${e.basis}"
}

private fun stateLabel(p: PlanUi): String = when (p.state) {
    PlanState.PREPARED -> "Preparado"
    PlanState.AWAITING_CONFIRMATION -> "Esperando tu confirmación"
    PlanState.CONFIRMED -> "Confirmado"
    PlanState.SUBMITTED -> when (p.jobStatus) {
        "COMPLETED" -> "Terminado"; "FAILED" -> "Falló"; "CANCELLED" -> "Cancelado"; "PROCESSING" -> "Generando…"; else -> "En cola…"
    }
    PlanState.DISCARDED -> "Descartado"
    PlanState.UNCERTAIN -> "Envío sin confirmar"
}

@Composable
private fun PlanCardView(
    p: PlanUi,
    onEdit: (PlanEdit) -> Unit,
    onDiscard: () -> Unit,
    onGenerate: () -> Unit,
    onOpenCanvas: () -> Unit,
    onOpenOutput: () -> Unit,
    assistantBusy: Boolean = false,
) {
    val plan = p.plan
    val editable = p.state != PlanState.SUBMITTED && p.state != PlanState.DISCARDED && p.state != PlanState.UNCERTAIN
    // El texto que edita el usuario no se pisa si el asistente cambia el plan a la vez.
    var prompt by remember(plan.id) { mutableStateOf(plan.prompt) }
    var basePrompt by remember(plan.id) { mutableStateOf(plan.prompt) }
    var changedUnderEdit by remember(plan.id) { mutableStateOf(false) }
    LaunchedEffect(plan.prompt) {
        if (plan.prompt != basePrompt) {
            if (prompt == basePrompt) prompt = plan.prompt else changedUnderEdit = true
            basePrompt = plan.prompt
        }
        if (prompt == plan.prompt) changedUnderEdit = false
    }
    var editParam by remember { mutableStateOf<Pair<String, String>?>(null) }
    val dirty = prompt != plan.prompt
    editParam?.let { (k, v) -> ParamEditDialog(k, v, onSave = { nv -> editParam = null; onEdit(PlanEdit(params = mapOf(k to nv))) }, onDismiss = { editParam = null }) }

    OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, if (p.state == PlanState.AWAITING_CONFIRMATION) PrimaryNeon else BorderSubtle)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(stateLabel(p), style = MaterialTheme.typography.labelMedium, color = if (p.state == PlanState.AWAITING_CONFIRMATION) PrimaryNeon else TextMuted)
            }
            plan.rationale?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted) }
            listOfNotNull(plan.sceneCode?.let { "Escena $it" }, plan.shotCode?.let { "Plano $it" }, plan.characters.takeIf { it.isNotEmpty() }?.joinToString()).takeIf { it.isNotEmpty() }
                ?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = TextMuted) }

            Section("Modelo")
            Text("${plan.modelName} · ${modalityLabel(plan.modality)}", fontWeight = FontWeight.Medium)
            Text(plan.ref.modelId, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            if (plan.alternatives.isNotEmpty()) Text("Alternativas: " + plan.alternatives.joinToString { it.modelId.substringAfter('/') }, style = MaterialTheme.typography.labelSmall, color = TextMuted)

            if (plan.media.isNotEmpty()) {
                Section("Medios")
                plan.media.forEach { m -> Text("${roleLabel(m.role)}: ${Uri.parse(m.uri).lastPathSegment ?: m.uri}", style = MaterialTheme.typography.bodySmall) }
            }

            plan.originalPrompt?.takeIf { it != plan.prompt }?.let { original ->
                var showOriginal by remember(plan.id) { mutableStateOf(false) }
                TextButton(onClick = { showOriginal = !showOriginal }) { Text(if(showOriginal) "Ocultar original" else "Ver prompt original") }
                if(showOriginal) androidx.compose.foundation.text.selection.SelectionContainer { Text(original, style = MaterialTheme.typography.bodySmall) }
            }
            Section("Prompt optimizado")
            OutlinedTextField(prompt, { prompt = it }, enabled = editable && !p.busy, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 12, textStyle = MaterialTheme.typography.bodySmall)
            if (changedUnderEdit && dirty) Text("El asistente cambió el prompt mientras editabas. «Deshacer» carga su versión; «Aplicar» guarda la tuya.", style = MaterialTheme.typography.labelSmall, color = AccentAmber)
            if (dirty && editable) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onEdit(PlanEdit(prompt = prompt)) }, enabled = !p.busy) { Text("Aplicar cambios") }
                TextButton(onClick = { prompt = plan.prompt; changedUnderEdit = false }) { Text("Deshacer") }
            }

            val params = visibleParams(plan)
            if (params.isNotEmpty()) {
                Section("Configuración")
                params.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(enabled = editable && !p.busy) { editParam = k to v }.padding(vertical = 3.dp)) {
                        Text(k, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.weight(1f))
                        Text(v.take(60), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Section("Coste")
            Text(costLine(plan.estimate), fontWeight = FontWeight.Medium)
            plan.estimate?.notes?.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = TextMuted) }
            plan.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = AccentAmber) }
            p.issues.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = ErrorRed) }
            p.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ErrorRed) }

            if (p.state == PlanState.UNCERTAIN) {
                Text("No se pudo confirmar si el proveedor aceptó el envío. Revisa Historial antes de generar otra vez; este plan queda bloqueado para no pagar dos veces.",
                    style = MaterialTheme.typography.bodySmall, color = AccentAmber)
                if (p.jobId != null) OutlinedButton(onClick = onOpenOutput) { Text("Ver en Nexus") }
            } else if (p.state == PlanState.SUBMITTED) {
                if (p.jobStatus == "PENDING" || p.jobStatus == "PROCESSING") {
                    if (p.jobProgress != null) LinearProgressIndicator(progress = { p.jobProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (p.jobStatus == "COMPLETED") Button(onClick = onOpenOutput) { Text("Ver resultado") }
            } else if (p.state != PlanState.DISCARDED) {
                if (p.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDiscard, enabled = !p.busy) { Text("Descartar") }
                    TextButton(onClick = onOpenCanvas, enabled = !p.busy) { Text("Abrir en el Lienzo") }
                    Spacer(Modifier.weight(1f))
                }
                Button(onClick = onGenerate, enabled = p.executable && !p.busy && !dirty && !assistantBusy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("GENERAR", fontWeight = FontWeight.Bold)
                }
                if (dirty) Text("Aplica o deshaz el cambio del prompt antes de generar.", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                else if (assistantBusy) Text("Espera a que el asistente termine: aún puede ajustar este plan.", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = PrimaryNeon, letterSpacing = 1.sp, modifier = Modifier.padding(top = 4.dp))
}

private fun modalityLabel(m: String) = when (m.uppercase()) { "VIDEO" -> "vídeo"; "IMAGE" -> "imagen"; "AUDIO" -> "audio"; else -> m.lowercase() }
private fun roleLabel(r: String) = when (r) { "first_frame" -> "Primer fotograma"; "last_frame" -> "Último fotograma"; "reference" -> "Referencia"; "video" -> "Vídeo"; else -> r }

@Composable
private fun ParamEditDialog(key: String, value: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var v by remember(key, value) { mutableStateOf(value) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(key) },
        text = { Column { OutlinedTextField(v, { v = it }, singleLine = false, maxLines = 6); Text("Se valida contra el schema del modelo. Vacío o «–» = quitar.", style = MaterialTheme.typography.labelSmall, color = TextMuted) } },
        confirmButton = { TextButton(onClick = { onSave(v.trim()) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

// ---- entrada ---------------------------------------------------------------------------------

@Composable
private fun AttachmentThumb(a: Attachment, onRemove: (() -> Unit)? = null) {
    Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))) {
        if (a.mimeType.startsWith("image/")) AsyncImage(model = a.uri, contentDescription = a.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Surface(color = SurfaceVariantDark, modifier = Modifier.fillMaxSize()) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Movie, a.name) } }
        if (onRemove != null) IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(24.dp)) {
            Icon(Icons.Default.Close, "Quitar", tint = MediaForeground)
        }
    }
}

// ---- ajustes ---------------------------------------------------------------------------------

@Composable
private fun AssistantSettingsDialog(ui: AssistantUiState, vm: AssistantViewModel, onDismiss: () -> Unit) {
    val s = ui.settings
    var backend by remember { mutableStateOf(s.backend) }
    var accessKey by remember { mutableStateOf(s.awsAccessKeyId) }
    var secret by remember { mutableStateOf("") }
    var clearSecret by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf(s.endpoint) }
    var modelId by remember { mutableStateOf(s.modelId) }
    var region by remember { mutableStateOf(s.region) }
    var token by remember { mutableStateOf("") }
    var clearToken by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf(s.temperature?.toString().orEmpty()) }
    var maxTokens by remember { mutableStateOf(s.maxTokens.toString()) }
    var sendImages by remember { mutableStateOf(s.sendImages) }
    var excluded by remember { mutableStateOf(s.excludedFamilies) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Asistente") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(backend != AssistantSettings.BACKEND_PROXY, { backend = AssistantSettings.BACKEND_DIRECT }, label = { Text("Bedrock directo") })
                    FilterChip(backend == AssistantSettings.BACKEND_PROXY, { backend = AssistantSettings.BACKEND_PROXY }, label = { Text("Proxy") })
                }
                if (backend != AssistantSettings.BACKEND_PROXY) {
                    Text("La app firma y llama a Bedrock con tus claves de AWS (guardadas cifradas en el teléfono).", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    OutlinedTextField(accessKey, { accessKey = it.trim() }, label = { Text("AWS Access Key ID") }, singleLine = true)
                    OutlinedTextField(secret, { secret = it; clearSecret = false }, label = { Text(if (s.maskedAwsSecret.isNotBlank()) "AWS Secret (${s.maskedAwsSecret})" else "AWS Secret Access Key") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation())
                    if (s.maskedAwsSecret.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(clearSecret, { clearSecret = it }); Text("Borrar secret guardado", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(region, { region = it }, label = { Text("Región (us-east-1)") }, singleLine = true)
                } else {
                    Text("La app habla con tu proxy (APK → proxy → Bedrock); las claves de AWS se quedan en el proxy.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    OutlinedTextField(endpoint, { endpoint = it }, label = { Text("URL del proxy (https://…)") }, singleLine = true)
                    OutlinedTextField(token, { token = it; clearToken = false }, label = { Text(if (s.maskedToken.isNotBlank()) "Token del dispositivo (${s.maskedToken})" else "Token del dispositivo") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation())
                    if (s.maskedToken.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(clearToken, { clearToken = it }); Text("Borrar token guardado", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(modelId, { modelId = it.trim() }, label = { Text("Model ID / inference profile") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistantSettings.KNOWN_MODELS.forEach { (id, name) -> FilterChip(modelId == id, { modelId = id }, label = { Text(name) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(temperature, { temperature = it }, label = { Text("Temperatura") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(maxTokens, { maxTokens = it.filter(Char::isDigit) }, label = { Text("Máx. tokens") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(sendImages, { sendImages = it }); Spacer(Modifier.width(8.dp)); Text("Enviar miniatura de las imágenes adjuntas al LLM", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(excluded, { excluded = it.take(200) }, label = { Text("No recomendar estas familias") }, singleLine = true,
                    supportingText = { Text("Separadas por comas, p. ej. «veo, runway». Si pides uno por su nombre, se respeta.") })
                HorizontalDivider()
                Text("Skills: ${ui.skillsCount}" + if (ui.skillErrors.isNotEmpty()) " · ${ui.skillErrors.size} con errores" else "", style = MaterialTheme.typography.bodyMedium)
                ui.skillErrors.take(4).forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = ErrorRed) }
                Text("Se cargan de la app y, sin recompilar, de Documents/ALEXIA/Nexus/Skills (las de igual id sustituyen a las de la app).", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::reloadSkills) { Text("Recargar skills") }
                    OutlinedButton(onClick = { vm.reindexHistory() }) { Text("Indexar historial") }
                }
                error?.let { Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = temperature.replace(',', '.').trim()
                val temp = if (t.isEmpty()) null else t.toDoubleOrNull()?.coerceIn(0.0, 1.0)
                if (t.isNotEmpty() && temp == null) { error = "Temperatura no válida (0–1)."; return@TextButton }
                val newToken = when { clearToken -> ""; token.isNotBlank() -> token; else -> null }
                val newSecret = when { clearSecret -> ""; secret.isNotBlank() -> secret; else -> null }
                error = vm.saveSettings(s.copy(backend = backend, awsAccessKeyId = accessKey, endpoint = endpoint, modelId = modelId, region = region, temperature = temp,
                    maxTokens = maxTokens.toIntOrNull() ?: s.maxTokens, sendImages = sendImages, excludedFamilies = excluded), newToken, newSecret)
                if (error == null) onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}
