@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.vidadomu.nexus.assistant

import androidx.compose.foundation.background
import com.vidadomu.nexus.ui.components.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.vidadomu.nexus.ui.components.NexusTopAppBar
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.components.noticeMessage
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.net.Uri
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.vidadomu.nexus.agent.core.llm.BrainAuthMode
import com.vidadomu.nexus.agent.core.llm.BrainProfile
import com.vidadomu.nexus.agent.core.llm.BrainProtocol
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
    /** Abre la pestaña Historial: los avisos que la mencionan llevan aquí en un toque. */
    onOpenHistory: () -> Unit = {},
    vm: AssistantViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsState()
    val presentation by rememberAssistantPresentationStore().state()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val draftPrefs = LocalContext.current.getSharedPreferences("assistant_draft_v1", android.content.Context.MODE_PRIVATE)
    var input by rememberSaveable(stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(draftPrefs.getString("text", "").orEmpty())) }
    var showSettings by remember { mutableStateOf(false) }
    var showProject by remember {mutableStateOf(false)}
    var ordersPanel by remember {mutableStateOf(false)}
    val dirtyPlans=remember {mutableStateMapOf<String,Boolean>()}
    val included=ui.plans.values.count {it.includeInBatch && it.executable && !it.busy && it.state in setOf(PlanState.PREPARED,PlanState.AWAITING_CONFIRMATION)}
    val primaryPlanId=ui.items.filterIsInstance<ChatItem.PlanCard>().lastOrNull {ui.plans[it.planId]?.let {p->p.executable && p.state !in setOf(PlanState.SUBMITTED,PlanState.DISCARDED,PlanState.UNCERTAIN)}==true}?.planId
    val activeProject=ui.projects.firstOrNull {it.id==ui.activeProjectId}
    val listState = rememberLazyListState()
    val picker = rememberProjectMediaPicker { uris -> vm.attach(uris) }
    // Cámara: la foto va a la caché (FileProvider) y se adjunta como cualquier referencia.
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val camera = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
        cameraUri?.takeIf { ok }?.let { vm.attach(listOf(it)) }
    }
    val takePhoto: () -> Unit = {
        runCatching {
            val file = java.io.File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file).also { cameraUri = it; camera.launch(it) }
        }.onFailure { Toast.makeText(context, "No se pudo abrir la cámara", Toast.LENGTH_SHORT).show() }
    }

    var following by rememberSaveable { mutableStateOf(true) }
    var automaticScroll by remember { mutableStateOf(false) }
    val visibleItems=ui.items.filterNot {it is ChatItem.PlanCard && (ui.plans[it.planId]?.state==PlanState.DISCARDED ||
        it.orderId?.let { id -> ui.orders[id]?.status } == com.vidadomu.nexus.agent.core.orders.OrderStatus.DISCARDED)}
    // Varios resultados terminados seguidos (3 o más) se agrupan en una tarjeta de miniaturas; «Ver tarjetas» las separa.
    var expandedGroups by remember {mutableStateOf(setOf<String>())}
    val rows=remember(visibleItems,ui.orders,ui.plans,expandedGroups) {
        fun done(i:ChatItem):Boolean {
            if(i !is ChatItem.PlanCard) return false
            val order=i.orderId?.let {ui.orders[it]} ?: return false
            return order.status==com.vidadomu.nexus.agent.core.orders.OrderStatus.COMPLETED && vm.outputs(ui.plans[i.planId]?.jobId ?: order.jobId).isNotEmpty()
        }
        val out=mutableListOf<Any>();var run=mutableListOf<ChatItem.PlanCard>()
        fun flush() {
            if(run.size>=3 && run.first().id !in expandedGroups) out.add(CardGroup(run.toList()))
            else {if(run.size>=3) out.add(GroupHeader(run.first().id,run.size));out.addAll(run)}
            run=mutableListOf()
        }
        for(i in visibleItems) if(done(i)) run.add(i as ChatItem.PlanCard) else {flush();out.add(i)}
        flush();out
    }
    val latestVisible by rememberUpdatedState(visibleItems)
    val latestUi by rememberUpdatedState(ui)
    suspend fun followLast() {
        if(latestVisible.isEmpty() && latestUi.streamingText.isBlank()) return
        automaticScroll = true
        try {
            val extra = (if(latestUi.streamingText.isNotBlank()) 1 else 0) + (if(latestUi.thinking) 1 else 0)
            listState.scrollToItem((latestVisible.size + extra - 1).coerceAtLeast(0))
            val layout=listState.layoutInfo
            layout.visibleItemsInfo.lastOrNull()?.let { last ->
                listState.scrollBy((last.offset+last.size-layout.viewportEndOffset).coerceAtLeast(0).toFloat())
            }
        }
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
    LaunchedEffect(ui.items.size, ui.thinking, ui.streamingText, ui.plans, ui.scrollRequest) {
        if (following) followLast()
    }
    LaunchedEffect(ui.scrollRequest) {
        if (ui.scrollRequest > 0) { following = true; followLast() }
    }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it.second); vm.consumeMessage() } }

    if(showSettings) AssistantSettingsDialog(ui,vm,onDismiss={showSettings=false})
    if(showProject) ProjectSheet(ui,vm) {showProject=false}

    val latestFailure=ui.items.filterIsInstance<ChatItem.Notice>().lastOrNull {it.error}
    var shownFailure by rememberSaveable {mutableStateOf(latestFailure?.id)}
    LaunchedEffect(latestFailure?.id) {
        val failure=latestFailure ?: return@LaunchedEffect
        if(failure.id!=shownFailure) {
            shownFailure=failure.id
            val result=snackbar.showSnackbar(noticeMessage(failure.text),actionLabel=if(vm.canRetryTurn()) "Reintentar" else null,withDismissAction=true)
            if(result==SnackbarResult.ActionPerformed) vm.retryTurn()
        }
    }
    Scaffold(containerColor=ScreenBackground,snackbarHost={SnackbarHost(snackbar) { data ->
        Snackbar(data,modifier=Modifier.padding(12.dp),shape=NexusTokens.card,
            containerColor=MaterialTheme.colorScheme.surfaceContainerHigh,contentColor=TextPrimary,
            actionColor=MaterialTheme.colorScheme.primary)
    }},topBar={
        NexusTopAppBar(
            navigationIcon = {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.vidadomu.nexus.R.drawable.nexus_mark),
                    contentDescription = "Logo de NEXUS",
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp).size(26.dp),
                    contentScale = ContentScale.Fit
                )
            },
            title={Column(Modifier.heightIn(min=NexusTokens.minimumTouch).clickable {showProject=true}.semantics {
            contentDescription="Abrir proyecto"
            stateDescription=listOfNotNull(activeProject?.name ?: "Agente","v${com.vidadomu.nexus.BuildConfig.VERSION_NAME}",ui.plans.values.lastOrNull()?.plan?.sceneCode?.let {"Escena $it"},"${ui.projectAssets.size} referencias").joinToString(" · ")
        },verticalArrangement=Arrangement.Center) {
            // Una sola línea: el proyecto. Cerebro y razonamiento van junto al cuadro de texto; escena y referencias, en la hoja del proyecto.
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(activeProject?.name ?: "Agente",style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false))
                Icon(Icons.Default.KeyboardArrowDown,contentDescription=null,modifier=Modifier.size(16.dp),tint=TextSecondary)
            }
        }},settingsEntries=listOf("Asistente: modelo y conexión" to { showSettings=true }))
    }) {pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if(ui.settings.offline) InlineNotice("Planificador local activo.",Modifier.padding(horizontal=NexusTokens.screenMargin),
                "Elige modelos y prepara planes sin razonamiento del LLM.","Configurar LLM",{showSettings=true},isError=false)
            // Al bajar por el chat se esconde la barra inferior para ganar espacio; al subir vuelve (Ajustes → Apariencia).
            val appearance = com.vidadomu.nexus.ui.theme.rememberAppearancePreferences()
            val autoHide = remember(appearance) { appearance.autoHideBars() }
            DisposableEffect(Unit) { onDispose { com.vidadomu.nexus.ui.theme.ChromeVisibility.bottomBar.value = true } }
            val hideOnScroll = remember(autoHide) { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    if (autoHide && kotlin.math.abs(available.y) > 6f) com.vidadomu.nexus.ui.theme.ChromeVisibility.bottomBar.value = available.y > 0
                    return androidx.compose.ui.geometry.Offset.Zero
                }
            } }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().nestedScroll(hideOnScroll), state = listState,
                contentPadding = PaddingValues(horizontal = NexusTokens.space8, vertical = NexusTokens.space8), verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                if (ui.items.isEmpty() && ui.streamingText.isBlank()) item { EmptyHint() }
                items(rows, key = { when (it) { is ChatItem -> it.id; is CardGroup -> it.key; is GroupHeader -> "gh:" + it.firstId; else -> it.hashCode() } }) { row ->
                    if (row is CardGroup) {
                        GeneratedGroupCard(row.cards.mapNotNull { c ->
                            val order = c.orderId?.let { ui.orders[it] } ?: return@mapNotNull null
                            val plan = ui.plans[c.planId]
                            val jobId = plan?.jobId ?: order.jobId
                            val uri = vm.outputs(jobId).firstOrNull() ?: return@mapNotNull null
                            GroupEntry(jobId, plan?.plan?.modelName ?: "Resultado", uri, plan?.plan?.modality ?: "IMAGE")
                        }, onOpen = onNavigateToOutput, onExpand = { expandedGroups = expandedGroups + row.cards.first().id }, modifier = Modifier.animateItem())
                        return@items
                    }
                    if (row is GroupHeader) {
                        TextButton(onClick = { expandedGroups = expandedGroups - row.firstId }) { Text("Agrupar los ${row.count} resultados") }
                        return@items
                    }
                    val item = row as ChatItem
                    when (item) {
                        is ChatItem.User -> Box(Modifier.animateItem()) { UserBubble(item) }
                        is ChatItem.Assistant -> Box(Modifier.animateItem()) { AssistantBubble(item, onOpenHistory) }
                        is ChatItem.Notice -> NoticeRow(item, onOpenHistory, onRetry = if (item.error && item.id == latestFailure?.id && vm.canRetryTurn()) ({ vm.retryTurn() }) else null, onChangeModel = { showSettings = true })
                        is ChatItem.ModeSwitch -> Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(NexusTokens.space12)) {
                                Text(item.text, style = MaterialTheme.typography.bodyMedium)
                                if (!item.resolved) TextButton(onClick = { vm.resolveModeSwitch(item.id) }, enabled = !ui.thinking) { Text("Activar Agente") }
                            }
                        }
                        is ChatItem.Proposal -> ProposalCard(item, onApply = { vm.applyProposal(item.id) }, onReject = { vm.rejectProposal(item.id) })
                        is ChatItem.PlanCard -> item.orderId?.let { ui.orders[it] }?.takeIf { ui.plans[item.planId] != null }?.let { order ->
                            val p = ui.plans.getValue(item.planId)
                            OrderCard(order, p, vm.outputs(p.jobId ?: order.jobId), assistantBusy = ui.thinking, modifier = Modifier.animateItem(), presentation = presentation,
                                onGenerate = { vm.generate(p.plan.id, p.plan.fingerprint()) },
                                onApplyFix = { vm.applyFix(order.id) },
                                onAskAgent = { vm.askAgentToFix(order.id) },
                                onEdit = { vm.editPlan(p.plan.id, it) },
                                onDiscard = { vm.discardPlan(p.plan.id) },
                                onOpenCanvas = { scope.launch { vm.canvasState(p.plan.id)?.let(onOpenInCanvas) } },
                                onOpenOutput = { (p.jobId ?: order.jobId)?.let(onNavigateToOutput) },
                                onRendered = { vm.cardRendered(order.id, it) },
                                onOpenHistory = onOpenHistory)
                        } ?: ui.plans[item.planId]?.let { p ->
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
                                onOpenOutput = { p.jobId?.let(onNavigateToOutput) },
                                onOpenHistory = onOpenHistory,
                                onDiagnoseFailure = { vm.diagnoseFailure(p.plan.id) }) })
                        }
                    }
                }
                if (ui.streamingText.isNotBlank()) {
                    item(key = "streaming_assistant_preview") {
                        AssistantBubble(ChatItem.Assistant("streaming_assistant_preview", ui.streamingText, emptyList(), "STREAMING"), onOpenHistory)
                    }
                }
                if (ui.thinking) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(NexusTokens.space8)); Text("Pensando…", color = TextMuted) } }
            }
            // Un único botón redondo para bajar, solo cuando no estás al final; los encargos tienen su propio panel.
            val cardIds = remember(visibleItems) { visibleItems.filterIsInstance<ChatItem.PlanCard>().map { it.id } }
            Column(Modifier.align(Alignment.BottomEnd).padding(NexusTokens.space12), horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                AnimatedVisibility(cardIds.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    SmallFloatingActionButton(onClick = { ordersPanel = true }, shape = NexusTokens.pill,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = TextPrimary) {
                        Icon(Icons.Default.Inventory2, "Ver encargos")
                    }
                }
                AnimatedVisibility(!following, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    SmallFloatingActionButton(onClick = { following = true; scope.launch { followLast() } }, shape = NexusTokens.pill,
                        containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Icon(Icons.Default.KeyboardArrowDown, "Bajar al final")
                    }
                }
            }
            }
            if (ordersPanel) OrdersSheet(visibleItems.filterIsInstance<ChatItem.PlanCard>(), ui, onClose = { ordersPanel = false }) { id ->
                ordersPanel = false; following = false
                scope.launch { listState.animateScrollToItem(visibleItems.indexOfFirst { it is ChatItem.PlanCard && it.id == id }.coerceAtLeast(0)) }
            }
            // Fila fina sobre el cuadro de texto: cerebro a la izquierda, modo de razonamiento a la derecha.
            Row(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.space12).heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                BrainPicker(ui, vm)
                Spacer(Modifier.weight(1f))
                ReasoningPicker(ui, vm)
            }
            PendingGenerations(ui,blockedByDraft=ui.plans.values.any {it.includeInBatch && it.executable && !it.busy && it.state in setOf(PlanState.PREPARED,PlanState.AWAITING_CONFIRMATION) && dirtyPlans[it.plan.id]==true},onGenerate=vm::generatePending)
            InputBar(ui, input, onInput = { input = it; draftPrefs.edit().putString("text", it.text).apply() }, onAttach = { picker.launch(arrayOf("image/*", "video/*", "audio/*") + AgentDocuments.types, maxSelection = 20) },
                onSend = { if(vm.send(input.text)) {haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove);draftPrefs.edit().remove("text").apply(); input = androidx.compose.ui.text.input.TextFieldValue("")} }, onStop = vm::stop, onRemove = vm::removeAttachment, onReply = { vm.send(it) }, onCamera = takePhoto)
        }
    }
}

/** Cerebro activo en la barra del Agente: un toque lista Bedrock, Claude, Antigravity, BytePlus/Dola y los propios, y cambia sin abrir Ajustes. */
@Composable
private fun BrainPicker(ui: AssistantUiState, vm: AssistantViewModel) {
    var open by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    val store = vm.runtime.settings.brains
    val brains = remember(tick, open) { store.all() }
    val builtins = listOf(
        AssistantSettings.BACKEND_DIRECT to "Bedrock", AssistantSettings.BACKEND_ANTHROPIC to "Claude API",
        AssistantSettings.BACKEND_OPENAI to "Compatible OpenAI", AssistantSettings.BACKEND_PROXY to "Proxy",
        AssistantSettings.BACKEND_ANTIGRAVITY to "Antigravity",
    )
    val current = ui.settings.backend
    val label = builtins.firstOrNull { it.first == current }?.second
        ?: brains.firstOrNull { BrainStore.backendFor(it.id) == current }?.name ?: "Cerebro"
    val context = LocalContext.current
    Box {
        // Texto discreto bajo el título (sin recuadro): el nombre corto del cerebro y una flecha.
        val short = if (label.length > 12) label.substringBefore(' ') else label
        Text("$short ▾", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1,
            modifier = Modifier.clickable(enabled = !ui.thinking) { open = true }.padding(vertical = 4.dp)
                .semantics { contentDescription = "Cerebro del agente: $label. Cambiar" })
        DropdownMenu(open, { open = false }) {
            (builtins.map { it.first to it.second } + brains.map { BrainStore.backendFor(it.id) to it.name }).forEach { (backend, name) ->
                DropdownMenuItem(text = { Text(name) },
                    trailingIcon = { if (backend == current) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        open = false
                        vm.switchBrain(backend)?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
                        tick++
                    })
            }
        }
    }
}

/** Modo de razonamiento junto al cuadro de texto, solo si el cerebro activo lo admite: Auto · Rápido · Mínimo · Bajo · Medio · Alto. */
@Composable
private fun ReasoningPicker(ui: AssistantUiState, vm: AssistantViewModel) {
    val brain = ui.settings.brain
    if (brain == null || !brain.capabilities.reasoning || brain.protocol == BrainProtocol.CUSTOM_REST) return
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val modes = listOf<Triple<String, String?, String?>>(
        Triple("Auto", null, null), Triple("Rápido", null, "disabled"), Triple("Mínimo", "minimal", null),
        Triple("Bajo", "low", null), Triple("Medio", "medium", null), Triple("Alto", "high", null))
    val current = modes.firstOrNull { it.second == brain.reasoningEffort && it.third == brain.thinking }?.first ?: "Auto"
    Box {
        TextButton(onClick = { open = true }, enabled = !ui.thinking, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier.height(28.dp).semantics { contentDescription = "Modo de razonamiento: $current. Cambiar" }) {
            Text("Razonar · $current ▾", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
        DropdownMenu(open, { open = false }) {
            modes.forEach { (name, effort, thinking) ->
                DropdownMenuItem(text = { Text(name) },
                    trailingIcon = { if (name == current) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { open = false; vm.setReasoning(effort, thinking)?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() } })
            }
        }
    }
}

private fun reasoningAvailable(ui: AssistantUiState): Boolean =
    ui.settings.brain?.let { it.capabilities.reasoning && it.protocol != BrainProtocol.CUSTOM_REST } == true

/** Los IDs internos (ord_…) no se enseñan en el chat; la tarjeta y su «Detalles» ya identifican el encargo. Copiar conserva el texto original. */
internal fun hideOrderIds(text: String): String =
    text.replace(Regex("""[ \t]*\(?`?ord_[0-9a-f]{6,}`?\)?"""), "").replace(Regex(" {2,}"), " ")

/** Panel propio de encargos: estado con icono, modelo y coste; tocar uno lleva a su tarjeta en el chat. Sin IDs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrdersSheet(cards: List<ChatItem.PlanCard>, ui: AssistantUiState, onClose: () -> Unit, onPick: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as com.vidadomu.nexus.NexusApplication
    val currencyState by app.currencyManager.state.collectAsState()
    ModalBottomSheet(onDismissRequest = onClose, shape = NexusTokens.sheet, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Encargos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = NexusTokens.space16, vertical = NexusTokens.space8))
        LazyColumn(contentPadding = PaddingValues(bottom = NexusTokens.space24)) {
            items(cards.asReversed(), key = { it.id }) { card ->
                val plan = ui.plans[card.planId]?.plan
                val status = card.orderId?.let { ui.orders[it] }?.status
                val (icon, tint, label) = when (status) {
                    com.vidadomu.nexus.agent.core.orders.OrderStatus.COMPLETED -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, "Listo")
                    com.vidadomu.nexus.agent.core.orders.OrderStatus.FAILED, com.vidadomu.nexus.agent.core.orders.OrderStatus.UNCERTAIN -> Triple(Icons.Default.Warning, MaterialTheme.colorScheme.error, "Error")
                    com.vidadomu.nexus.agent.core.orders.OrderStatus.SUBMITTING, com.vidadomu.nexus.agent.core.orders.OrderStatus.PROCESSING -> Triple(Icons.Default.Autorenew, TextSecondary, "En curso")
                    com.vidadomu.nexus.agent.core.orders.OrderStatus.DISCARDED -> Triple(Icons.Default.Close, TextSecondary, "Descartado")
                    else -> Triple(Icons.Default.Schedule, TextSecondary, "Pendiente")
                }
                val cost = remember(plan?.estimate, currencyState) { plan?.estimate?.amountUsd?.let(app.currencyManager::formatCost) }
                Row(Modifier.fillMaxWidth().clickable { onPick(card.id) }.heightIn(min = NexusTokens.minimumTouch)
                    .padding(horizontal = NexusTokens.space16, vertical = NexusTokens.space8),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                    Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(plan?.modelName ?: "Encargo", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                    cost?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = TextSecondary) }
                }
            }
        }
    }
}

@Composable
private fun QuickLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(28.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = NexusTokens.space24, start = NexusTokens.space8, end = NexusTokens.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.vidadomu.nexus.R.drawable.nexus_logo),
            contentDescription = "Logo oficial de NEXUS",
            modifier = Modifier.sizeIn(maxWidth = 118.dp, maxHeight = 112.dp),
            contentScale = ContentScale.Fit
        )
        Surface(
            shape = NexusTokens.pill,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
        ) {
            Text(
                text = "v${com.vidadomu.nexus.BuildConfig.VERSION_NAME} · ${com.vidadomu.nexus.BuildConfig.CODENAME} · build ${com.vidadomu.nexus.BuildConfig.VERSION_CODE}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Text(
            text = "Las buenas ideas empiezan aquí.",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
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
                Text(active?.name ?: "Proyecto",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                IconButton(onClick=onClose) {Icon(Icons.Default.Close,"Cerrar proyecto")}
            }
            Text("Cambiar proyecto",style=MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                item {StudioFilterChip(active==null,{vm.selectProject(null)},label={Text("Sin proyecto")},shape=NexusTokens.filter)}
                items(ui.projects,key={it.id}) {p->StudioFilterChip(active?.id==p.id,{vm.selectProject(p.id)},label={Text(p.name,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)},shape=NexusTokens.filter)}
            }
            TextButton(onClick={creating=!creating},colors=ButtonDefaults.textButtonColors(contentColor=TextSecondary)) {Icon(Icons.Default.Add,null);Text("Nuevo proyecto")}
            if(creating) {
                StudioTextField(name,{name=it.take(80)},singleLine=true,label={Text("Nombre")},shape=NexusTokens.field,modifier=Modifier.fillMaxWidth())
                TextButton(onClick={vm.createProject(name);name="";creating=false},enabled=name.isNotBlank()) {Text("Crear")}
            }
            if(active!=null) holder.SaveableStateProvider(active.id) {ProjectMemoryEditor(active,vm,onClose)}
            ProjectReferences(ui,vm,expandedByDefault=true)
            HorizontalDivider()
            Text("Conversaciones",style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={vm.newConversation();onClose()},enabled=!ui.thinking) {Icon(Icons.Default.AddComment,null);Text("Nueva conversación",color=TextSecondary)}
            if(conversations.isEmpty()) Text("Aún no hay conversaciones guardadas",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
            conversations.forEach {entry->TextButton(onClick={vm.openConversation(entry.id);onClose()},enabled=!ui.thinking) {Text(entry.title,color=TextSecondary,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}}
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
                Text("El asistente lo lee en cada mensaje para que los prompts sean coherentes, y lo completa él cuando le cuentas cosas del proyecto. $assetCount archivos etiquetados con este proyecto.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                ProjectGroup(Icons.Default.MenuBook, "La historia") {
                    StudioTextField(description, { description = it.take(400) }, shape=NexusTokens.field, label = { Text("Descripción corta") }, placeholder = { Text("Ej.: Drama carcelario en Madrid, 1928") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    StudioTextField(synopsis, { synopsis = it.take(2000) }, shape=NexusTokens.field, label = { Text("Sinopsis") }, placeholder = { Text("De qué va, personajes principales y cómo termina") }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                }
                ProjectGroup(Icons.Default.Palette, "Aspecto y tono") {
                    StudioTextField(tone, { tone = it.take(2000) }, shape=NexusTokens.field, label = { Text("Tono / género") }, placeholder = { Text("Ej.: sobrio, tenso, realista") }, minLines = 1, maxLines = 4, modifier = Modifier.fillMaxWidth())
                    StudioTextField(style, { style = it.take(2000) }, shape=NexusTokens.field, label = { Text("Estilo visual") }, placeholder = { Text("Ej.: 35 mm, luz natural, paleta apagada, grano fino") }, minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
                }
                ProjectGroup(Icons.Default.Rule, "Reglas del asistente") {
                    StudioTextField(rules, { rules = it.take(6000) }, shape=NexusTokens.field, label = { Text("Reglas fijas") }, placeholder = { Text("Una por línea. Ej.: Mateo nunca sonríe") }, minLines = 2, maxLines = 8, modifier = Modifier.fillMaxWidth())
                    StudioTextField(models, { models = it.take(800) }, shape=NexusTokens.field, label = { Text("Modelos preferidos") }, placeholder = { Text("Uno por línea. Ej.: Seedance 2.0") }, minLines = 1, maxLines = 4, modifier = Modifier.fillMaxWidth())
                }
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

/** Grupo visual del formulario de proyecto: icono y título sobre una tarjeta, para escanear rápido. */
@Composable
private fun ProjectGroup(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f), shape = NexusTokens.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(NexusTokens.space12), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            content()
        }
    }
}
@Composable
private fun UserBubble(item:ChatItem.User) {
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.End) {
        if(item.attachments.isNotEmpty()) LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {items(item.attachments,key={it.id}) {AttachmentThumb(it)}}
        // Tu mensaje: completo (nunca truncado), ancho casi total para ganar altura, con el acento en degradado y seleccionable.
        val accent=MaterialTheme.colorScheme.primary
        val brush=remember(accent) {androidx.compose.ui.graphics.Brush.linearGradient(listOf(accent.copy(alpha=.30f),accent.copy(alpha=.16f)))}
        Box(Modifier.fillMaxWidth(.96f).wrapContentWidth(Alignment.End).padding(top=NexusTokens.space4)
            .background(brush,NexusTokens.card)) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(item.text,Modifier.padding(horizontal=NexusTokens.space12,vertical=NexusTokens.space8),style=MaterialTheme.typography.bodyMedium,color=TextPrimary)
            }
        }
    }
}

@Composable
private fun AssistantBubble(item:ChatItem.Assistant, onOpenHistory: () -> Unit = {}) {
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    var actionsExpanded by rememberSaveable(item.id) {mutableStateOf(false)}
    var showSteps by rememberSaveable(item.id) {mutableStateOf(false)}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
        if(item.text.isNotBlank()) {
            // Sin fila de botones bajo cada respuesta: mantener pulsado abre copiar, compartir y pasos.
            // Casi sin fondo: tus mensajes llevan el acento y las respuestas quedan limpias, a todo el ancho.
            Surface(color=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.35f),shape=NexusTokens.card,modifier=Modifier.fillMaxWidth()
                .pointerInput(Unit) {detectTapGestures(onLongPress={actionsExpanded=true})}
                .semantics {onLongClick("Acciones de la respuesta") {actionsExpanded=true;true}}) {
              Box(Modifier.padding(horizontal=NexusTokens.space12,vertical=NexusTokens.space8)) {
                ChatMarkdownView(remember(item.text) { hideOrderIds(item.text) })
                DropdownMenu(expanded=actionsExpanded,onDismissRequest={actionsExpanded=false}) {
                    DropdownMenuItem(text={Text("Copiar respuesta")},leadingIcon={Icon(Icons.Default.ContentCopy,null)},onClick={
                        clipboard.setText(AnnotatedString(item.text));actionsExpanded=false
                        Toast.makeText(context,"Respuesta copiada",Toast.LENGTH_SHORT).show()
                    })
                    DropdownMenuItem(text={Text("Compartir respuesta")},leadingIcon={Icon(Icons.Default.Share,null)},onClick={
                        actionsExpanded=false
                        runCatching {
                            val intent=Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,item.text)}
                            context.startActivity(Intent.createChooser(intent,"Compartir respuesta"))
                        }.onFailure {Toast.makeText(context,"No se pudo compartir la respuesta",Toast.LENGTH_SHORT).show()}
                    })
                    if(item.toolCalls.isNotEmpty()) DropdownMenuItem(text={Text(if(showSteps) "Ocultar pasos" else "Ver pasos (${item.toolCalls.size})")},
                        leadingIcon={Icon(Icons.Default.Build,null)},onClick={showSteps=!showSteps;actionsExpanded=false})
                }
              }
            }
        }
        if(showSteps && item.toolCalls.isNotEmpty()) Text(item.toolCalls.joinToString(" · "),style=MaterialTheme.typography.labelSmall,color=TextSecondary)
        if(com.vidadomu.nexus.ui.navigation.HistoryLinks.mentionsHistoryScreen(item.text)) HistoryLinkButton(onOpenHistory)
        if(item.media.isNotEmpty()) LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
            items(item.media,key={it.id}) { AssistantMediaPreview(it) }
        }
    }
}

@Composable
internal fun AssistantMediaPreview(media: Attachment) {
    val context=LocalContext.current
    when {
        media.mimeType.startsWith("image/") -> AssistantImagePreview(media)
        media.mimeType.startsWith("video/") -> {
            val player=remember(media.uri) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(media.uri)))
                    prepare()
                    playWhenReady=false
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(
                factory={viewContext -> PlayerView(viewContext).apply { this.player=player; useController=true }},
                update={it.player=player},
                modifier=Modifier.width(280.dp).aspectRatio(16f/9f).clip(NexusTokens.card),
            )
        }
        media.mimeType.startsWith("audio/") -> {
            val player=remember(media.uri) {
                ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(Uri.parse(media.uri))); prepare() }
            }
            var playing by remember(media.uri) { mutableStateOf(false) }
            DisposableEffect(player) {
                val listener=object:Player.Listener { override fun onIsPlayingChanged(isPlaying:Boolean) { playing=isPlaying } }
                player.addListener(listener)
                onDispose { player.removeListener(listener); player.release() }
            }
            Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=NexusTokens.field,modifier=Modifier.widthIn(min=220.dp,max=280.dp)) {
                Row(Modifier.padding(horizontal=NexusTokens.space8,vertical=NexusTokens.space4),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq,null,tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal=NexusTokens.space8)) {
                        Text(media.name ?: "Audio",maxLines=1,style=MaterialTheme.typography.bodyMedium)
                        media.durationSec?.let { Text(formatMediaDuration(it),style=MaterialTheme.typography.labelMedium,color=TextSecondary) }
                    }
                    IconButton(onClick={if(playing) player.pause() else player.play()}) {
                        Icon(if(playing) Icons.Default.Pause else Icons.Default.PlayArrow,if(playing) "Pausar audio" else "Reproducir audio")
                    }
                }
            }
        }
        else -> Surface(
            color=MaterialTheme.colorScheme.surfaceVariant,
            shape=NexusTokens.field,
            modifier=Modifier.widthIn(min=220.dp,max=280.dp).clickable { openAssistantMedia(context,media) },
        ) {
            Row(Modifier.padding(NexusTokens.space12),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                Icon(Icons.Default.Description,"Abrir archivo")
                Column(Modifier.weight(1f)) {
                    Text(media.name ?: "Archivo del asistente",maxLines=2,style=MaterialTheme.typography.bodyMedium)
                    Text(media.mimeType,style=MaterialTheme.typography.labelMedium,color=TextSecondary)
                }
                Icon(Icons.Default.OpenInNew,"Abrir")
            }
        }
    }
}

private fun openAssistantMedia(context:android.content.Context,media:Attachment) {
    runCatching {
        val original=Uri.parse(media.uri)
        val uri=if(original.scheme=="file") FileProvider.getUriForFile(
            context,"${com.vidadomu.nexus.BuildConfig.APPLICATION_ID}.fileprovider",java.io.File(original.path!!)) else original
        val view=Intent(Intent.ACTION_VIEW).setDataAndType(uri,media.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(view,media.name ?: "Abrir archivo"))
    }.onFailure { Toast.makeText(context,"No hay una aplicación compatible para abrir este archivo.",Toast.LENGTH_SHORT).show() }
}

private fun formatMediaDuration(seconds:Double):String {
    val total=seconds.toInt().coerceAtLeast(0)
    return if(total>=3600) "%d:%02d:%02d".format(total/3600,(total%3600)/60,total%60)
        else "%d:%02d".format(total/60,total%60)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoticeRow(item: ChatItem.Notice, onOpenHistory: () -> Unit = {}, onRetry: (() -> Unit)? = null, onChangeModel: (() -> Unit)? = null) {
    val history = com.vidadomu.nexus.ui.navigation.HistoryLinks.mentionsHistoryScreen(item.text)
    if(item.error) {
        var expanded by rememberSaveable(item.id) {mutableStateOf(false)}
        Column(Modifier.animateContentSize()) {
            // Todo error ofrece una salida directa: repetir, cambiar de modelo o revisar el historial.
            FlowRow(verticalArrangement=Arrangement.Center,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                if(onRetry != null) FilledTonalButton(onClick=onRetry,shape=NexusTokens.filter) {
                    Icon(Icons.Default.Refresh,null,Modifier.size(18.dp)); Spacer(Modifier.width(NexusTokens.space4)); Text("Reintentar")
                }
                if(onChangeModel != null) TextButton(onClick=onChangeModel) {
                    Icon(Icons.Default.Tune,null,Modifier.size(18.dp)); Spacer(Modifier.width(NexusTokens.space4)); Text("Cambiar modelo")
                }
                if(history) HistoryLinkButton(onOpenHistory)
                TextButton(onClick={expanded=!expanded}) {Text(if(expanded) "Ocultar detalle" else "Ver detalle del error",color=TextSecondary)}
            }
            if(expanded) Text(item.text,style=MaterialTheme.typography.bodySmall,color=TextSecondary)
        }
    } else Column {
        Text(item.text,style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
        if(history) HistoryLinkButton(onOpenHistory)
    }
}
/** Acceso directo a la pestaña Historial desde un aviso que la menciona. */
@Composable
private fun HistoryLinkButton(onOpenHistory: () -> Unit) {
    TextButton(onClick=onOpenHistory) {
        Icon(Icons.Default.Timelapse,null,Modifier.size(18.dp))
        Spacer(Modifier.width(NexusTokens.space4))
        Text("Abrir Historial")
    }
}

@Composable
private fun ProposalCard(item: ChatItem.Proposal, onApply: () -> Unit, onReject: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(NexusTokens.space12), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
            Text("Propuesta de organización", fontWeight = FontWeight.SemiBold)
            Text(item.description, style = MaterialTheme.typography.bodyMedium)
            when (item.state) {
                ProposalState.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    FilledTonalButton(onClick = onApply) { Text("Aplicar") }
                    FilledTonalButton(onClick = onReject) { Text("Descartar") }
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
    onDiagnoseFailure:()->Unit={},
    onOpenHistory:()->Unit={},
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

    Card(Modifier.fillMaxWidth(),shape=NexusTokens.card,border=null) {
        Column(Modifier.padding(NexusTokens.space16),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                Column(Modifier.weight(1f)) {
                    Text(plan.modelName,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
            StudioTextField(prompt,{prompt=it},enabled=editable && !p.busy,modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=8,
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
            p.error?.takeIf { p.state!=PlanState.UNCERTAIN }?.let {
                val history=com.vidadomu.nexus.ui.navigation.HistoryLinks.mentionsHistoryScreen(it)
                InlineNotice(noticeMessage(it,"No se pudo completar el encargo."),technicalDetails=it,
                    actionLabel=if(history) "Abrir Historial" else null,onAction=if(history) onOpenHistory else null)
            }
            if(p.state==PlanState.UNCERTAIN) {
                InlineNotice("Envío sin confirmar. Revisa Historial antes de generar otra vez.",
                    technicalDetails=listOfNotNull(p.error,"Este plan queda bloqueado para evitar un doble pago.").joinToString("\n"),isError=false,
                    actionLabel="Abrir Historial",onAction=onOpenHistory)
                if(p.jobId!=null) FilledTonalButton(onClick=onOpenOutput) {Text("Ver este trabajo",color=TextSecondary)}
            } else if(p.state==PlanState.SUBMITTED) {
                if(p.jobStatus=="PENDING" || p.jobStatus=="PROCESSING") {
                    if(p.jobProgress!=null) LinearProgressIndicator(progress={p.jobProgress.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if(p.jobStatus=="COMPLETED") FilledTonalButton(onClick=onOpenOutput) {Text("Ver resultado")}
                if(p.jobStatus in setOf("FAILED","CANCELLED")) FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    if(p.jobStatus=="FAILED") TextButton(onClick=onDiagnoseFailure,enabled=!assistantBusy) {Text("Analizar fallo",color=TextSecondary)}
                    TextButton(onClick=onDiscard) {Text("Quitar tarjeta",color=TextSecondary)}
                    TextButton(onClick=onOpenOutput) {Text("Ver detalle",color=TextSecondary)}
                }
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
        text = { Column { StudioTextField(v, { v = it }, shape=NexusTokens.field, singleLine = false, maxLines = 6); Text("Se valida contra el schema del modelo. Vacío o «–» = quitar.", style = MaterialTheme.typography.labelSmall, color = TextMuted) } },
        confirmButton = { TextButton(onClick = { onSave(v.trim()) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

// ---- entrada ---------------------------------------------------------------------------------

@Composable
private fun AttachmentThumb(a: Attachment, onRemove: (() -> Unit)? = null) {
    var failed by remember(a.uri) { mutableStateOf(false) }
    // Si el archivo ya no está (borrado de la biblioteca o permiso perdido) se muestra un hueco visible, no un cuadro vacío.
    Box(Modifier.size(64.dp).clip(NexusTokens.thumbnail).then(Modifier.background(MaterialTheme.colorScheme.surfaceVariant))) {
        if (a.mimeType.startsWith("image/") && failed) Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.BrokenImage, "Imagen no disponible", tint = TextSecondary, modifier = Modifier.size(22.dp))
            Text(a.name ?: "Imagen", style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        else if (a.mimeType.startsWith("image/")) AsyncImage(model = a.uri, contentDescription = a.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), onError = { failed = true })
        else Surface(color = SurfaceVariantDark, modifier = Modifier.fillMaxSize()) { Box(contentAlignment = Alignment.Center) { Icon(if(a.mimeType.startsWith("video/")) Icons.Default.Movie else Icons.Default.Description, a.name) } }
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
    var baseUrl by remember { mutableStateOf(s.llmBaseUrl) }
    var apiKey by remember { mutableStateOf("") }
    var clearApiKey by remember { mutableStateOf(false) }
    var inputRate by remember { mutableStateOf(s.inputUsdPerM?.toString().orEmpty()) }
    var outputRate by remember { mutableStateOf(s.outputUsdPerM?.toString().orEmpty()) }
    var agyEndpoint by remember { mutableStateOf(s.antigravityEndpoint) }
    // Cerebros personalizados y BytePlus: el selector los muestra junto a los de siempre.
    val brainStore = vm.runtime.settings.brains
    var brainTick by remember { mutableStateOf(0) }
    val brains = remember(brainTick) { brainStore.all() }
    var editingBrain by remember { mutableStateOf<BrainProfile?>(null) }
    var addingBrain by remember { mutableStateOf(false) }
    val isCustom = backend.startsWith(BrainStore.PREFIX)
    val maskedKey = remember(backend) { vm.maskedApiKey(backend) }
    var error by remember { mutableStateOf<String?>(null) }
    fun save() {
        val t = temperature.replace(',', '.').trim()
        val temp = if (t.isEmpty()) null else t.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
        if (t.isNotEmpty() && temp == null) { error = "Temperatura no válida (número ≥ 0; el máximo lo decide el modelo)."; return }
        val newToken = when { clearToken -> ""; token.isNotBlank() -> token; else -> null }
        val newSecret = when { clearSecret -> ""; secret.isNotBlank() -> secret; else -> null }
        val newApiKey = when { clearApiKey -> ""; apiKey.isNotBlank() -> apiKey; else -> null }
        val inRate = inputRate.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: run { error = "Tarifa de entrada no válida."; return } }
        val outRate = outputRate.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: run { error = "Tarifa de salida no válida."; return } }
        if (!isCustom && modelId.isBlank()) { error = "Escribe el id del modelo."; return }
        error = vm.saveSettings(s.copy(backend = backend, awsAccessKeyId = accessKey, endpoint = endpoint, modelId = modelId, region = region, temperature = temp,
            maxTokens = maxTokens.toIntOrNull() ?: s.maxTokens, sendImages = sendImages, excludedFamilies = excluded,
            llmBaseUrl = baseUrl, inputUsdPerM = inRate, outputUsdPerM = outRate,
            antigravityEndpoint = agyEndpoint.ifBlank { AssistantSettings.DEFAULT_ANTIGRAVITY_ENDPOINT }), newToken, newSecret, newApiKey)
        if (error == null) onDismiss()
    }
    ModalBottomSheet(onDismissRequest=onDismiss,shape=NexusTokens.sheet,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().fillMaxHeight(.96f)) {
            // Cabecera fija: título, estado y una línea de resumen.
            Column(Modifier.padding(horizontal = NexusTokens.screenMargin)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ajustes del asistente", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    Surface(shape = NexusTokens.filter, color = if (ui.thinking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                        Text(if (ui.thinking) "Trabajando" else "Listo", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
                            color = if (ui.thinking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
                Text("${ui.catalog.size} modelos · ${ui.skillsCount} skills" + (if (ui.skillErrors.isNotEmpty()) " · ${ui.skillErrors.size} con errores" else "") +
                    (ui.lastLatencyMs?.let { " · último turno ${"%.1f".format(it / 1000.0)} s" } ?: ""),
                    style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = NexusTokens.screenMargin, vertical = NexusTokens.space8),
                verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                // 1 · Cerebro
                AgentSection("Cerebro", Icons.Default.AutoAwesome) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                        listOf(
                            AssistantSettings.BACKEND_DIRECT to "Bedrock",
                            AssistantSettings.BACKEND_ANTHROPIC to "Claude API",
                            AssistantSettings.BACKEND_OPENAI to "Compatible OpenAI",
                            AssistantSettings.BACKEND_PROXY to "Proxy",
                            AssistantSettings.BACKEND_ANTIGRAVITY to "Antigravity",
                        ).forEach { (value, label) ->
                            StudioFilterChip(backend == value, {
                                if (backend != value) { backend = value; apiKey = ""; clearApiKey = false
                                    // Se recupera el modelo guardado para el nuevo proveedor o un valor por defecto válido.
                                    modelId = vm.savedModelFor(value)
                                }
                            }, label = { Text(label) })
                        }
                        brains.forEach { b ->
                            StudioFilterChip(backend == BrainStore.backendFor(b.id), {
                                backend = BrainStore.backendFor(b.id); apiKey = ""; clearApiKey = false; modelId = b.modelId
                            }, label = { Text(b.name) })
                        }
                        AssistChip(onClick = { addingBrain = true }, label = { Text("+ Añadir") })
                    }
                    if (isCustom) {
                        val brain = brains.firstOrNull { BrainStore.backendFor(it.id) == backend }
                        if (brain == null) Text("Este proveedor ya no existe. Elige otro y guarda.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        else {
                            val hasKey = brainStore.hasKey(brain.id)
                            val ready = hasKey || brain.auth.mode == BrainAuthMode.NONE
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                                Column(Modifier.weight(1f)) {
                                    Text(brain.modelId.ifBlank { "sin modelo" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(when (brain.protocol) { BrainProtocol.OPENAI_RESPONSES -> "Responses API"; BrainProtocol.CUSTOM_REST -> "REST"; else -> "Chat Completions" },
                                        style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                }
                                Surface(shape = NexusTokens.filter, color = if (ready) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer) {
                                    Text(if (ready) "Clave lista" else "Falta la clave", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
                                        color = if (ready) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                            if (!brain.capabilities.tools) Text("Sin herramientas: solo conversa, no prepara encargos.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            OutlinedButton(onClick = { editingBrain = brain }, shape = NexusTokens.field) { Text("Editar proveedor, clave y límites") }
                        }
                    } else if (backend == AssistantSettings.BACKEND_ANTHROPIC || backend == AssistantSettings.BACKEND_OPENAI) {
                        Text(if (backend == AssistantSettings.BACKEND_ANTHROPIC) "Claude con tu clave de la API de Anthropic (se guarda cifrada en el teléfono)."
                            else "Cualquier API compatible con OpenAI: elige un acceso rápido o escribe la URL. La clave se guarda cifrada y es distinta para cada proveedor.",
                            style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        if (backend == AssistantSettings.BACKEND_OPENAI) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                                AssistantSettings.OPENAI_PRESETS.forEach { (name, url) -> StudioFilterChip(baseUrl.trimEnd('/') == url, { baseUrl = url }, label = { Text(name) }) }
                            }
                            StudioTextField(baseUrl, { baseUrl = it.trim() }, shape = NexusTokens.field, label = { Text("URL de la API (https://…/v1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        StudioCredentialField(apiKey, { apiKey = it; clearApiKey = false }, shape = NexusTokens.field,
                            label = { Text(if (maskedKey.isNotBlank()) "Clave de API ($maskedKey)" else "Clave de API") }, singleLine = true)
                        if (maskedKey.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(clearApiKey, { clearApiKey = it }); Text("Borrar la clave guardada", style = MaterialTheme.typography.bodySmall)
                        }
                        if (backend == AssistantSettings.BACKEND_OPENAI) {
                            Text("Tarifa para el límite de consumo (opcional, USD por millón de tokens). Sin ella solo se limita el número de llamadas por mensaje.",
                                style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                                StudioTextField(inputRate, { inputRate = it }, shape = NexusTokens.field, label = { Text("Entrada") }, singleLine = true, modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                                StudioTextField(outputRate, { outputRate = it }, shape = NexusTokens.field, label = { Text("Salida") }, singleLine = true, modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            }
                        }
                    } else if (backend == AssistantSettings.BACKEND_ANTIGRAVITY) {
                        Text("Conecta con el puente local de Antigravity CLI (AGY) en el dispositivo o por túnel (127.0.0.1:8788).",
                            style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        StudioTextField(agyEndpoint, { agyEndpoint = it.trim() }, shape = NexusTokens.field,
                            label = { Text("Endpoint del puente AGY (http://127.0.0.1:8788)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    } else if (backend != AssistantSettings.BACKEND_PROXY) {
                        Text("La app firma y llama a Bedrock con tus claves de AWS (guardadas cifradas en el teléfono).", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        StudioCredentialField(accessKey, { accessKey = it.trim() }, shape = NexusTokens.field, label = { Text("AWS Access Key ID") }, singleLine = true)
                        StudioCredentialField(secret, { secret = it; clearSecret = false }, shape = NexusTokens.field, label = { Text(if (s.maskedAwsSecret.isNotBlank()) "AWS Secret (${s.maskedAwsSecret})" else "AWS Secret Access Key") },
                            singleLine = true)
                        if (s.maskedAwsSecret.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(clearSecret, { clearSecret = it }); Text("Borrar secret guardado", style = MaterialTheme.typography.bodySmall)
                        }
                        StudioTextField(region, { region = it }, shape = NexusTokens.field, label = { Text("Región (us-east-1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    } else {
                        Text("La app habla con tu proxy (APK → proxy → Bedrock); las claves de AWS se quedan en el proxy.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        StudioTextField(endpoint, { endpoint = it }, shape = NexusTokens.field, label = { Text("URL del proxy (https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        StudioCredentialField(token, { token = it; clearToken = false }, shape = NexusTokens.field, label = { Text(if (s.maskedToken.isNotBlank()) "Token del dispositivo (${s.maskedToken})" else "Token del dispositivo") },
                            singleLine = true)
                        if (s.maskedToken.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(clearToken, { clearToken = it }); Text("Borrar token guardado", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!isCustom) StudioTextField(modelId, { modelId = it.trim() }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(when (backend) {
                            AssistantSettings.BACKEND_OPENAI -> "Modelo (id exacto en tu proveedor)"
                            AssistantSettings.BACKEND_ANTHROPIC -> "Modelo de Claude"
                            AssistantSettings.BACKEND_ANTIGRAVITY -> "Modelo de Antigravity (AGY)"
                            else -> "Model ID / inference profile"
                        }) })
                    val modelChips = when (backend) {
                        AssistantSettings.BACKEND_ANTHROPIC -> AssistantSettings.ANTHROPIC_MODELS
                        AssistantSettings.BACKEND_ANTIGRAVITY -> AssistantSettings.ANTIGRAVITY_MODELS
                        AssistantSettings.BACKEND_OPENAI -> emptyList()
                        else -> AssistantSettings.KNOWN_MODELS
                    }
                    if (!isCustom && modelChips.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                        modelChips.forEach { (id, name) -> StudioFilterChip(modelId == id, { modelId = id }, label = { Text(name) }) }
                    }
                    if (!isCustom && modelId.contains("-5-5")) Text("Requiere acceso a este modelo en tu cuenta AWS. Usa los parámetros de muestreo del modelo; conserva tus límites de consumo.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                // 2 · Respuesta
                AgentSection("Respuesta", Icons.Default.Tune) {
                    Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                        StudioTextField(temperature, { temperature = it }, shape = NexusTokens.field, label = { Text("Temperatura") }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        StudioTextField(maxTokens, { maxTokens = it.filter(Char::isDigit) }, shape = NexusTokens.field, label = { Text("Máx. tokens") }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                        Text("Enviar miniatura de las imágenes adjuntas al LLM", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(sendImages, { sendImages = it })
                    }
                    StudioTextField(excluded, { excluded = it.take(200) }, shape = NexusTokens.field, label = { Text("No recomendar estas familias") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("Separadas por comas, p. ej. «veo, runway». Si pides uno por su nombre, se respeta.") })
                }
                // Presentación del agente (tamaño de las tarjetas y animación de actividad)
                AgentSection("Tu espacio de creación", Icons.Default.Palette) {
                    AssistantPresentationSettings()
                }
                // 3 · Consumo y límites
                AgentSection("Consumo y límites", Icons.Default.Speed) {
                    AgentBudgetSettings()
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AgentLimitsSettings()
                }
                // 4 · Herramientas
                AgentSection("Herramientas", Icons.Default.Build) {
                    MediaEditingSettings()
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AgentInternetSettings()
                }
                // 5 · Skills
                AgentSection("Skills", Icons.Default.Extension) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${ui.skillsCount} cargadas", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (ui.skillErrors.isNotEmpty()) Text("${ui.skillErrors.size} con errores", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                    if (ui.skillErrors.isNotEmpty()) InlineNotice("Algunas skills no se pudieron cargar.", technicalDetails = ui.skillErrors.joinToString("\n"))
                    Text("Se cargan de la app y, sin recompilar, de Documents/ALEXIA/Nexus/Skills (las de igual id sustituyen a las de la app).", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                        FilledTonalButton(onClick = vm::reloadSkills) { Text("Recargar skills") }
                        FilledTonalButton(onClick = { vm.reindexHistory() }) { Text("Indexar historial") }
                    }
                }
                error?.let { InlineNotice(noticeMessage(it, "Revisa los ajustes antes de guardar."), technicalDetails = it) }
            }
            // Botonera fija: siempre a la vista, sin tener que llegar al final de la lista.
            Surface(color = com.vidadomu.nexus.ui.theme.LocalChromeColor.current) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.screenMargin, vertical = NexusTokens.space8), horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = NexusTokens.minimumTouch)) { Text("Cerrar") }
                        Button(onClick = { save() }, modifier = Modifier.weight(1f).heightIn(min = NexusTokens.minimumTouch)) { Text("Guardar") }
                    }
                }
            }
        }
    }
    if (addingBrain || editingBrain != null) BrainEditorDialog(editingBrain, brainStore) { saved ->
        addingBrain = false; editingBrain = null; brainTick++
        // Un proveedor recién guardado queda elegido; hay que guardar los ajustes para activarlo.
        if (saved != null) brainStore.get(saved)?.let { backend = BrainStore.backendFor(it.id); modelId = it.modelId; apiKey = ""; clearApiKey = false }
    }
}

private data class CardGroup(val cards: List<ChatItem.PlanCard>) { val key get() = "group:" + cards.first().id }
private data class GroupHeader(val firstId: String, val count: Int)
