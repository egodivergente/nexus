@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.ui.components.NexusTopAppBar
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.components.noticeMessage
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val draftPrefs = LocalContext.current.getSharedPreferences("assistant_draft_v1", android.content.Context.MODE_PRIVATE)
    var input by rememberSaveable(stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(draftPrefs.getString("text", "").orEmpty())) }
    var showSettings by remember { mutableStateOf(false) }
    var showProject by remember {mutableStateOf(false)}
    val dirtyPlans=remember {mutableStateMapOf<String,Boolean>()}
    val included=ui.plans.values.count {it.includeInBatch && it.executable && !it.busy && it.state in setOf(PlanState.PREPARED,PlanState.AWAITING_CONFIRMATION)}
    val primaryPlanId=ui.items.filterIsInstance<ChatItem.PlanCard>().lastOrNull {ui.plans[it.planId]?.let {p->p.executable && p.state !in setOf(PlanState.SUBMITTED,PlanState.DISCARDED,PlanState.UNCERTAIN)}==true}?.planId
    val activeProject=ui.projects.firstOrNull {it.id==ui.activeProjectId}
    val listState = rememberLazyListState()
    val picker = rememberProjectMediaPicker { uris -> vm.attach(uris) }

    var following by rememberSaveable { mutableStateOf(true) }
    var automaticScroll by remember { mutableStateOf(false) }
    val latestUi by rememberUpdatedState(ui)
    suspend fun followLast() {
        if(latestUi.items.isEmpty()) return
        automaticScroll = true
        try { listState.scrollToItem(latestUi.items.size - 1 + if(latestUi.thinking) 1 else 0) }
        finally { automaticScroll = false }
    }
    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.isScrollInProgress, listState.canScrollForward, automaticScroll) }.collect { (moving, canForward, automatic) ->
            if (moving && !automatic) following = !canForward
        }
    }
    // Keyboard and reference panels resize the viewport without adding a message.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.viewportEndOffset }.collect {
            if (following) followLast()
        }
    }
    LaunchedEffect(ui.items.size, ui.thinking, ui.plans, ui.scrollRequest) {
        if (following) followLast()
    }
    LaunchedEffect(ui.scrollRequest) {
        if (ui.scrollRequest > 0) { following = true; followLast() }
    }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it.second); vm.consumeMessage() } }

    if(showSettings) AssistantSettingsDialog(ui,vm,onDismiss={showSettings=false})
    if(showProject) ProjectSheet(ui,vm) {showProject=false}

    Scaffold(containerColor=ScreenBackground,snackbarHost={SnackbarHost(snackbar)},topBar={
        NexusTopAppBar(title={Column(Modifier.clickable {showProject=true}.semantics {contentDescription="Abrir proyecto"}) {
            Text(activeProject?.name ?: "Agente",style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(listOfNotNull(ui.plans.values.lastOrNull()?.plan?.sceneCode?.let {"Escena $it"},"${ui.projectAssets.size} referencias").joinToString(" · "),style=MaterialTheme.typography.labelMedium,color=TextSecondary)
        }},actions={IconButton(onClick={showSettings=true}) {Icon(Icons.Default.Tune,"Ajustes del asistente")}})
    }) {pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if(ui.settings.offline) InlineNotice("Planificador local activo.",Modifier.padding(horizontal=NexusTokens.screenMargin),
                "Elige modelos y prepara planes sin razonamiento del LLM.","Configurar LLM",{showSettings=true},isError=false)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(NexusTokens.screenMargin), verticalArrangement = Arrangement.spacedBy(NexusTokens.space16)) {
                if (ui.items.isEmpty()) item { EmptyHint() }
                items(ui.items.filterNot { it is ChatItem.PlanCard && ui.plans[it.planId]?.state == PlanState.DISCARDED }, key = { it.id }) { item ->
                    when (item) {
                        is ChatItem.User -> UserBubble(item)
                        is ChatItem.Assistant -> AssistantBubble(item)
                        is ChatItem.Notice -> NoticeRow(item)
                        is ChatItem.ModeSwitch -> OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(NexusTokens.space12)) {
                                Text(item.text, style = MaterialTheme.typography.bodyMedium)
                                if (!item.resolved) TextButton(onClick = { vm.resolveModeSwitch(item.id) }, enabled = !ui.thinking) { Text("Activar Agente") }
                            }
                        }
                        is ChatItem.Proposal -> ProposalCard(item, onApply = { vm.applyProposal(item.id) }, onReject = { vm.rejectProposal(item.id) })
                        is ChatItem.PlanCard -> ui.plans[item.planId]?.let { p ->
                            CompactGeneration(p, vm.outputs(p.jobId),
                                onOpenOutput = { p.jobId?.let(onNavigateToOutput) },
                                onAttach = { vm.attachPlanImages(p.plan.id, it) }, onDiscard = { vm.discardPlan(p.plan.id) },
                                onToggleBatch = { vm.toggleBatchPlan(p.plan.id) }, assistantBusy = ui.thinking,
                                details = { PlanCardView(p,
                                onEdit = { vm.editPlan(p.plan.id, it) },
                                onDiscard = { vm.discardPlan(p.plan.id) },
                                onGenerate = { vm.generate(p.plan.id, p.plan.fingerprint()) },
                                assistantBusy = ui.thinking,
                                primaryAction = included<2 && primaryPlanId==p.plan.id,
                                showDiscard = false,
                                onDirtyChanged = {dirtyPlans[p.plan.id]=it},
                                onOpenCanvas = { scope.launch { vm.canvasState(p.plan.id)?.let(onOpenInCanvas) } },
                                onOpenOutput = { p.jobId?.let(onNavigateToOutput) }) })
                        }
                    }
                }
                if (ui.thinking) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(NexusTokens.space8)); Text("Pensando…", color = TextMuted) } }
            }
            if (!following) TextButton(onClick = { following = true; scope.launch { followLast() } }) { Text("Ir al último mensaje ↓") }
            PendingGenerations(ui,blockedByDraft=ui.plans.values.any {it.includeInBatch && it.executable && !it.busy && it.state in setOf(PlanState.PREPARED,PlanState.AWAITING_CONFIRMATION) && dirtyPlans[it.plan.id]==true},onGenerate=vm::generatePending)
            InputBar(ui, input, onInput = { input = it; draftPrefs.edit().putString("text", it.text).apply() }, onAttach = { picker.launch(arrayOf("image/*", "video/*"), maxSelection = 6) },
                onSend = { vm.send(input.text); draftPrefs.edit().remove("text").apply(); input = androidx.compose.ui.text.input.TextFieldValue("") }, onStop = vm::stop, onRemove = vm::removeAttachment)
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(Modifier.fillMaxWidth().padding(top = NexusTokens.space24, start = NexusTokens.space8, end = NexusTokens.space8), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        Text("¿Una imagen, un vídeo o una idea por desarrollar?", color = TextMuted)
    }
}

@Composable
private fun ProjectSheet(ui:AssistantUiState,vm:AssistantViewModel,onClose:()->Unit) {
    val holder=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var creating by remember {mutableStateOf(false)}
    var name by rememberSaveable {mutableStateOf("")}
    val active=ui.projects.firstOrNull {it.id==ui.activeProjectId}
    val conversations=remember {vm.conversations()}
    ModalBottomSheet(onDismissRequest=onClose,shape=NexusTokens.sheet,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).verticalScroll(rememberScrollState()).padding(horizontal=NexusTokens.screenMargin)
            .padding(bottom=NexusTokens.space24),verticalArrangement=Arrangement.spacedBy(NexusTokens.space16)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(active?.name ?: "Proyecto",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                IconButton(onClick=onClose) {Icon(Icons.Default.Close,"Cerrar proyecto")}
            }
            Text("Cambiar proyecto",style=MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                item {FilterChip(active==null,{vm.selectProject(null)},label={Text("Sin proyecto")},shape=NexusTokens.filter)}
                items(ui.projects,key={it.id}) {p->FilterChip(active?.id==p.id,{vm.selectProject(p.id)},label={Text(p.name)},shape=NexusTokens.filter)}
            }
            TextButton(onClick={creating=!creating},colors=ButtonDefaults.textButtonColors(contentColor=TextSecondary)) {Icon(Icons.Default.Add,null);Text("Nuevo proyecto")}
            if(creating) {
                OutlinedTextField(name,{name=it.take(80)},singleLine=true,label={Text("Nombre")},shape=NexusTokens.field,modifier=Modifier.fillMaxWidth())
                TextButton(onClick={vm.createProject(name);name="";creating=false},enabled=name.isNotBlank()) {Text("Crear")}
            }
            if(active!=null) holder.SaveableStateProvider(active.id) {ProjectMemoryEditor(active,vm,onClose)}
            ProjectReferences(ui,vm,expandedByDefault=true)
            HorizontalDivider()
            Text("Conversaciones",style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={vm.newConversation();onClose()},enabled=!ui.thinking) {Icon(Icons.Default.AddComment,null);Text("Nueva conversación",color=TextSecondary)}
            if(conversations.isEmpty()) Text("Aún no hay conversaciones guardadas",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
            conversations.forEach {entry->TextButton(onClick={vm.openConversation(entry.id);onClose()},enabled=!ui.thinking) {Text(entry.title,color=TextSecondary)}}
        }
    }
}

/** Biblia del proyecto: lo que el asistente lee en cada mensaje mientras el proyecto está activo. */
@Composable
private fun ProjectMemoryEditor(p: com.vidadomu.nexus.agent.core.assets.Project, vm: AssistantViewModel, onClose: () -> Unit) {
    val m = p.memory
    var description by rememberSaveable(p.id) { mutableStateOf(p.description.orEmpty()) }
    var synopsis by rememberSaveable(p.id) { mutableStateOf(m.synopsis.orEmpty()) }
    var tone by rememberSaveable(p.id) { mutableStateOf(m.tone.orEmpty()) }
    var style by rememberSaveable(p.id) { mutableStateOf(m.visualStyle.orEmpty()) }
    var rules by rememberSaveable(p.id) { mutableStateOf(m.rules.joinToString("\n")) }
    var models by rememberSaveable(p.id) { mutableStateOf(m.preferredModels.joinToString("\n")) }
    var removed by rememberSaveable(p.id) {mutableStateOf(listOf<Long>())}
    val characters = remember(p.id) { vm.projectCharacters(p.id) }
    val assetCount = remember(p.id) { vm.projectAssetCount(p.id) }
            Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                Text("El asistente la lee en cada mensaje para que los prompts sean coherentes. También la rellena él cuando le cuentas cosas del proyecto. $assetCount archivos etiquetados con este proyecto.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                OutlinedTextField(description, { description = it.take(400) }, shape=NexusTokens.field, label = { Text("Descripción corta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(synopsis, { synopsis = it.take(2000) }, shape=NexusTokens.field, label = { Text("Sinopsis") }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tone, { tone = it.take(2000) }, shape=NexusTokens.field, label = { Text("Tono / género") }, minLines = 1, maxLines = 4, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(style, { style = it.take(2000) }, shape=NexusTokens.field, label = { Text("Estilo visual") }, minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(rules, { rules = it.take(6000) }, shape=NexusTokens.field, label = { Text("Reglas fijas (una por línea)") }, minLines = 2, maxLines = 8, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(models, { models = it.take(800) }, shape=NexusTokens.field, label = { Text("Modelos preferidos (uno por línea)") }, minLines = 1, maxLines = 4, modifier = Modifier.fillMaxWidth())
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
            TextButton(onClick = {
                vm.saveProjectMemory(p.id, description, synopsis, tone, style, rules, models,
                    keepNotes = if (removed.isEmpty()) null else m.notes.map { it.at }.filter { it !in removed }.toSet())
                onClose()
            }) { Text("Guardar") }
}

@Composable
private fun UserBubble(item:ChatItem.User) {
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.End) {
        if(item.attachments.isNotEmpty()) LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {items(item.attachments,key={it.id}) {AttachmentThumb(it)}}
        Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=NexusTokens.card,modifier=Modifier.widthIn(max=320.dp).padding(top=NexusTokens.space4,start=NexusTokens.space32)) {
            Text(item.text,Modifier.padding(NexusTokens.space16),style=MaterialTheme.typography.bodyLarge,color=TextPrimary)
        }
    }
}

@Composable
private fun AssistantBubble(item:ChatItem.Assistant) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
        if(item.text.isNotBlank()) ChatMarkdownView(item.text)
        if(item.toolCalls.isNotEmpty()) Text(item.toolCalls.distinct().joinToString(" · "),style=MaterialTheme.typography.labelMedium,color=TextSecondary)
    }
}

@Composable
private fun NoticeRow(item: ChatItem.Notice) {
    if(item.error) InlineNotice(noticeMessage(item.text),technicalDetails=item.text) else Text(item.text,style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
}

@Composable
private fun ProposalCard(item: ChatItem.Proposal, onApply: () -> Unit, onReject: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(NexusTokens.space12), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
            Text("Propuesta de organización", fontWeight = FontWeight.SemiBold)
            Text(item.description, style = MaterialTheme.typography.bodyMedium)
            when (item.state) {
                ProposalState.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    FilledTonalButton(onClick = onApply) { Text("Aplicar") }
                    OutlinedButton(onClick = onReject) { Text("Descartar") }
                }
                ProposalState.APPLYING -> LinearProgressIndicator(Modifier.fillMaxWidth())
                ProposalState.APPLIED -> Text("Aplicada (es lógica: no se mueve ni renombra ningún archivo del disco).", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                ProposalState.REJECTED -> Text("Descartada.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                ProposalState.FAILED -> InlineNotice("No se pudo aplicar la propuesta.",technicalDetails=item.result)
            }
        }
    }
}

// ---- tarjeta de plan ------------------------------------------------------------------------

internal fun visibleParams(plan: GenerationPlan): List<Pair<String, String>> =
    plan.effectiveParams.filter { (k, v) -> k != "prompt" && !ParameterValidator.isBlankLike(v) }.toList()

internal fun costLine(e:CostEstimate?,currency:com.vidadomu.nexus.core.currency.CurrencyManager?=null):String = when {
    e==null -> "Coste no disponible"
    e.amountUsd!=null -> (e.amountUsd?.let {currency?.formatCost(it)} ?: "Coste estimado") + when(e.confidence) {
        CostConfidence.NEXUS_QUOTE -> " · cotización de la calculadora"
        CostConfidence.PUBLISHED_RATE -> " · tarifa publicada × unidades"
        CostConfidence.BASE_ONLY -> " · orientativo"
        CostConfidence.UNKNOWN -> ""
    }
    else -> "Coste exacto no disponible · ${e.basis}"
}

internal fun stateLabel(p: PlanUi): String = when (p.state) {
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
internal fun PlanCardView(
    p: PlanUi,
    onEdit: (PlanEdit) -> Unit,
    onDiscard: () -> Unit,
    onGenerate: () -> Unit,
    onOpenCanvas: () -> Unit,
    onOpenOutput: () -> Unit,
    assistantBusy: Boolean = false,
    primaryAction:Boolean=true,
    showDiscard:Boolean=true,
    onDirtyChanged:(Boolean)->Unit={},
) {
    val app=LocalContext.current.applicationContext as com.vidadomu.nexus.NexusApplication
    val currency=app.currencyManager
    val currencyState by currency.state.collectAsState()
    var expanded by rememberSaveable(p.plan.id) {mutableStateOf(false)}
    val plan = p.plan
    val editable = p.state != PlanState.SUBMITTED && p.state != PlanState.DISCARDED && p.state != PlanState.UNCERTAIN
    // El texto que edita el usuario no se pisa si el asistente cambia el plan a la vez.
    var prompt by rememberSaveable(plan.id) { mutableStateOf(plan.prompt) }
    var basePrompt by rememberSaveable(plan.id) { mutableStateOf(plan.prompt) }
    var changedUnderEdit by rememberSaveable(plan.id) { mutableStateOf(false) }
    LaunchedEffect(plan.prompt) {
        if (plan.prompt != basePrompt) {
            if (prompt == basePrompt) prompt = plan.prompt else changedUnderEdit = true
            basePrompt = plan.prompt
        }
        if (prompt == plan.prompt) changedUnderEdit = false
    }
    var editParam by remember { mutableStateOf<Pair<String, String>?>(null) }
    val dirty = prompt != plan.prompt
    SideEffect {onDirtyChanged(dirty)}
    editParam?.let { (k, v) -> ParamEditDialog(k, v, onSave = { nv -> editParam = null; onEdit(PlanEdit(params = mapOf(k to nv))) }, onDismiss = { editParam = null }) }

    OutlinedCard(Modifier.fillMaxWidth(),shape=NexusTokens.card,border=BorderStroke(1.dp,BorderSubtle)) {
        Column(Modifier.padding(NexusTokens.space16),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                Column(Modifier.weight(1f)) {
                    Text(plan.modelName,style=MaterialTheme.typography.titleMedium)
                    Text(remember(plan.estimate,currencyState) {plan.estimate?.amountUsd?.let(currency::formatCost) ?: "Por confirmar"},style=MaterialTheme.typography.bodyMedium.tabularMoney(),color=TextSecondary)
                    Text(stateLabel(p),style=MaterialTheme.typography.labelMedium,color=TextSecondary)
                }
                if(editable) {
                    val enabled=p.executable && !p.busy && !dirty && !assistantBusy
                    if(primaryAction) Button(onClick=onGenerate,enabled=enabled,shape=NexusTokens.field) {Text("Generar")}
                    else FilledTonalButton(onClick=onGenerate,enabled=enabled,shape=NexusTokens.field) {Text("Generar")}
                }
            }
            if(p.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(prompt,{prompt=it},enabled=editable && !p.busy,modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=8,
                textStyle=MaterialTheme.typography.bodyLarge,shape=NexusTokens.field,label={Text("Prompt")})
            if(changedUnderEdit && dirty) InlineNotice("El asistente cambió el prompt mientras editabas.",technicalDetails="Deshacer carga su versión; Aplicar guarda la tuya.",isError=false)
            if(dirty && editable) FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                TextButton(onClick={onEdit(PlanEdit(prompt=prompt))},enabled=!p.busy) {Text("Aplicar cambios",color=TextSecondary)}
                TextButton(onClick={prompt=plan.prompt;changedUnderEdit=false}) {Text("Deshacer",color=TextSecondary)}
            }
            if(dirty) Text("Aplica o deshaz el cambio antes de generar.",style=MaterialTheme.typography.labelMedium,color=TextSecondary)
            else if(assistantBusy) Text("Espera a que el asistente termine de ajustar el plan.",style=MaterialTheme.typography.labelMedium,color=TextSecondary)
            if(plan.warnings.isNotEmpty()) InlineNotice("Revisa los avisos del plan.",technicalDetails=plan.warnings.joinToString("\n"),isError=false)
            if(p.issues.isNotEmpty()) InlineNotice("Faltan datos para preparar este encargo.",technicalDetails=p.issues.joinToString("\n"))
            p.error?.let {InlineNotice(noticeMessage(it,"No se pudo completar el encargo."),technicalDetails=it)}
            if(p.state==PlanState.UNCERTAIN) {
                InlineNotice("Envío sin confirmar. Revisa Historial antes de generar otra vez.",technicalDetails="Este plan queda bloqueado para evitar un doble pago.",isError=false)
                if(p.jobId!=null) OutlinedButton(onClick=onOpenOutput) {Text("Ver en Nexus",color=TextSecondary)}
            } else if(p.state==PlanState.SUBMITTED) {
                if(p.jobStatus=="PENDING" || p.jobStatus=="PROCESSING") {
                    if(p.jobProgress!=null) LinearProgressIndicator(progress={p.jobProgress.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if(p.jobStatus=="COMPLETED") FilledTonalButton(onClick=onOpenOutput) {Text("Ver resultado")}
            } else if(p.state!=PlanState.DISCARDED) FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                TextButton(onClick=onOpenCanvas,enabled=!p.busy) {Text("Abrir en el Lienzo",color=TextSecondary)}
                if(showDiscard) TextButton(onClick=onDiscard,enabled=!p.busy) {Text("Descartar",color=TextSecondary)}
            }
            TextButton(onClick={expanded=!expanded},colors=ButtonDefaults.textButtonColors(contentColor=TextSecondary)) {
                Text(if(expanded) "Ocultar detalles" else "Detalles");Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)
            }
            if(expanded) {
                Text("${plan.modelName} · ${modalityLabel(plan.modality)}",style=MaterialTheme.typography.bodyMedium)
                Text(plan.ref.modelId,style=MaterialTheme.typography.labelMedium,color=TextSecondary)
                plan.rationale?.takeIf {it.isNotBlank()}?.let {Text(it,style=MaterialTheme.typography.bodyMedium,color=TextSecondary)}
                listOfNotNull(plan.sceneCode?.let {"Escena $it"},plan.shotCode?.let {"Plano $it"},plan.characters.takeIf {it.isNotEmpty()}?.joinToString()).takeIf {it.isNotEmpty()}
                    ?.let {Text(it.joinToString(" · "),style=MaterialTheme.typography.labelMedium,color=TextSecondary)}
                if(plan.alternatives.isNotEmpty()) Text("Alternativas: "+plan.alternatives.joinToString {it.modelId.substringAfter('/')},style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
                if(plan.media.isNotEmpty()) {
                    Section("Medios")
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                        items(plan.media) {m->Column(Modifier.width(96.dp)) {
                            AsyncImage(m.uri,roleLabel(m.role),Modifier.size(96.dp).clip(NexusTokens.thumbnail),contentScale=ContentScale.Crop)
                            Text(roleLabel(m.role),style=MaterialTheme.typography.labelMedium,color=TextSecondary)
                        }}
                    }
                }
            plan.originalPrompt?.takeIf { it != plan.prompt }?.let { original ->
                var showOriginal by remember(plan.id) { mutableStateOf(false) }
                TextButton(onClick = { showOriginal = !showOriginal }) { Text(if(showOriginal) "Ocultar original" else "Ver prompt original") }
                if(showOriginal) androidx.compose.foundation.text.selection.SelectionContainer { Text(original, style = MaterialTheme.typography.bodySmall) }
            }
            val params = visibleParams(plan)
            if (params.isNotEmpty()) {
                Section("Configuración")
                params.forEach { (k, v) ->
                    Column(Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch).clip(NexusTokens.thumbnail).clickable(enabled=editable && !p.busy) {editParam=k to v}.padding(vertical=NexusTokens.space8)) {
                        Text(k, style = MaterialTheme.typography.bodySmall, color=TextSecondary)
                        Text(v, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Section("Coste")
            Text(costLine(plan.estimate,currency), fontWeight = FontWeight.Medium)
            plan.estimate?.notes?.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = TextMuted) }
                plan.estimate?.amountUsd?.let {Text(currency.formatUsdDetails(it),style=MaterialTheme.typography.labelMedium.tabularMoney(),color=TextSecondary)}
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title,style=MaterialTheme.typography.titleMedium,color=TextPrimary,modifier=Modifier.padding(top=NexusTokens.space4))
}

private fun modalityLabel(m: String) = when (m.uppercase()) { "VIDEO" -> "vídeo"; "IMAGE" -> "imagen"; "AUDIO" -> "audio"; else -> m.lowercase() }
private fun roleLabel(r: String) = when (r) { "first_frame" -> "Primer fotograma"; "last_frame" -> "Último fotograma"; "reference" -> "Referencia"; "video" -> "Vídeo"; else -> r }

@Composable
private fun ParamEditDialog(key: String, value: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var v by remember(key, value) { mutableStateOf(value) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(key) },
        text = { Column { OutlinedTextField(v, { v = it }, shape=NexusTokens.field, singleLine = false, maxLines = 6); Text("Se valida contra el schema del modelo. Vacío o «–» = quitar.", style = MaterialTheme.typography.labelSmall, color = TextMuted) } },
        confirmButton = { TextButton(onClick = { onSave(v.trim()) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

// ---- entrada ---------------------------------------------------------------------------------

@Composable
private fun AttachmentThumb(a: Attachment, onRemove: (() -> Unit)? = null) {
    Box(Modifier.size(64.dp).clip(NexusTokens.thumbnail)) {
        if (a.mimeType.startsWith("image/")) AsyncImage(model = a.uri, contentDescription = a.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Surface(color = SurfaceVariantDark, modifier = Modifier.fillMaxSize()) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Movie, a.name) } }
        if (onRemove != null) IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(NexusTokens.minimumTouch)) {
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
    ModalBottomSheet(onDismissRequest=onDismiss,shape=NexusTokens.sheet,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        NexusSheetSystemBars()
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=NexusTokens.screenMargin).padding(bottom=NexusTokens.space24), verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                Text("Ajustes del asistente",style=MaterialTheme.typography.titleLarge)
                AgentStatus(ui,{})
                FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    FilterChip(backend != AssistantSettings.BACKEND_PROXY, { backend = AssistantSettings.BACKEND_DIRECT }, label = { Text("Bedrock directo") })
                    FilterChip(backend == AssistantSettings.BACKEND_PROXY, { backend = AssistantSettings.BACKEND_PROXY }, label = { Text("Proxy") })
                }
                if (backend != AssistantSettings.BACKEND_PROXY) {
                    Text("La app firma y llama a Bedrock con tus claves de AWS (guardadas cifradas en el teléfono).", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    OutlinedTextField(accessKey, { accessKey = it.trim() }, shape=NexusTokens.field, label = { Text("AWS Access Key ID") }, singleLine = true)
                    OutlinedTextField(secret, { secret = it; clearSecret = false }, shape=NexusTokens.field, label = { Text(if (s.maskedAwsSecret.isNotBlank()) "AWS Secret (${s.maskedAwsSecret})" else "AWS Secret Access Key") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation())
                    if (s.maskedAwsSecret.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(clearSecret, { clearSecret = it }); Text("Borrar secret guardado", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(region, { region = it }, shape=NexusTokens.field, label = { Text("Región (us-east-1)") }, singleLine = true)
                } else {
                    Text("La app habla con tu proxy (APK → proxy → Bedrock); las claves de AWS se quedan en el proxy.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    OutlinedTextField(endpoint, { endpoint = it }, shape=NexusTokens.field, label = { Text("URL del proxy (https://…)") }, singleLine = true)
                    OutlinedTextField(token, { token = it; clearToken = false }, shape=NexusTokens.field, label = { Text(if (s.maskedToken.isNotBlank()) "Token del dispositivo (${s.maskedToken})" else "Token del dispositivo") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation())
                    if (s.maskedToken.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(clearToken, { clearToken = it }); Text("Borrar token guardado", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(modelId, { modelId = it.trim() }, shape=NexusTokens.field, label = { Text("Model ID / inference profile") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    AssistantSettings.KNOWN_MODELS.forEach { (id, name) -> FilterChip(modelId == id, { modelId = id }, label = { Text(name) }) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                    OutlinedTextField(temperature, { temperature = it }, shape=NexusTokens.field, label = { Text("Temperatura") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(maxTokens, { maxTokens = it.filter(Char::isDigit) }, shape=NexusTokens.field, label = { Text("Máx. tokens") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(sendImages, { sendImages = it }); Spacer(Modifier.width(NexusTokens.space8)); Text("Enviar miniatura de las imágenes adjuntas al LLM", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(excluded, { excluded = it.take(200) }, shape=NexusTokens.field, label = { Text("No recomendar estas familias") }, singleLine = true,
                    supportingText = { Text("Separadas por comas, p. ej. «veo, runway». Si pides uno por su nombre, se respeta.") })
                HorizontalDivider()
                Text("Skills: ${ui.skillsCount}" + if (ui.skillErrors.isNotEmpty()) " · ${ui.skillErrors.size} con errores" else "", style = MaterialTheme.typography.bodyMedium)
                if(ui.skillErrors.isNotEmpty()) InlineNotice("Algunas skills no se pudieron cargar.",technicalDetails=ui.skillErrors.joinToString("\n"))
                Text("Se cargan de la app y, sin recompilar, de Documents/ALEXIA/Nexus/Skills (las de igual id sustituyen a las de la app).", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    OutlinedButton(onClick = vm::reloadSkills) { Text("Recargar skills") }
                    OutlinedButton(onClick = { vm.reindexHistory() }) { Text("Indexar historial") }
                }
                error?.let {InlineNotice(noticeMessage(it,"Revisa los ajustes antes de guardar."),technicalDetails=it)}
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
                TextButton(onClick=onDismiss) {Text("Cerrar",color=TextSecondary)}
            }
    }
}
