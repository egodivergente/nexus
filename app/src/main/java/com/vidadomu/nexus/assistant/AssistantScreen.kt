@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.vidadomu.nexus.assistant

import androidx.compose.ui.res.imageResource
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import android.net.Uri
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.vidadomu.nexus.agent.core.llm.AntigravityReasoning
import com.vidadomu.nexus.agent.core.llm.CodexReasoning
import com.vidadomu.nexus.agent.core.llm.BrainAuthMode
import com.vidadomu.nexus.agent.core.llm.BrainProfile
import com.vidadomu.nexus.agent.core.llm.BrainProtocol
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun AssistantScreen(
    onOpenInCanvas: (CreateUiState) -> Unit,
    onNavigateToOutput: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    /** Abre la pestaña Historial: los avisos que la mencionan llevan aquí en un toque. */
    onOpenHistory: () -> Unit = {},
    /** «Continuar en…» drawn over the chat area only, so the composer and the bottom bar stay in place. */
    projectPicker: (@Composable () -> Unit)? = null,
    onPickerDismiss: () -> Unit = {},
    vm: AssistantViewModel = (LocalContext.current.applicationContext as com.vidadomu.nexus.NexusApplication).assistantViewModel(),
) {
    // Charcoal ground and matte surfaces for the whole Agent screen, its sheets and its drawer included.
    AgentChatTheme { AssistantScreenContent(onOpenInCanvas, onNavigateToOutput, onOpenSettings, onOpenHistory, projectPicker, onPickerDismiss, vm) }
}

@Composable
private fun AssistantScreenContent(
    onOpenInCanvas: (CreateUiState) -> Unit,
    onNavigateToOutput: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    projectPicker: (@Composable () -> Unit)?,
    onPickerDismiss: () -> Unit,
    vm: AssistantViewModel,
) {
    val ui by vm.ui.collectAsState()
    val storySelection by com.vidadomu.nexus.assistant.story.StorySelectionStore.selection.collectAsState()
    val storyIndex by vm.runtime.assets.changes.collectAsState()
    // Outputs can be downloaded after the order status changes: observe the repository too.
    val generationJobs by (LocalContext.current.applicationContext as com.vidadomu.nexus.NexusApplication)
        .generationRepository.allJobs.collectAsState()
    var libraryExpanded by remember { mutableStateOf(false) }
    val libraryHaptics = rememberNexusHaptics()
    fun openLibrary() {
        if (!libraryExpanded) {
            libraryExpanded = true
            libraryHaptics.perform(NexusHapticEvent.SELECTION)
        }
    }
    var stage by remember { mutableStateOf<Pair<List<Attachment>, Int>?>(null) }
    if (ui.resultsWall.isNotEmpty()) ResultsWallSheet(ui.resultsWall,
        onOpen = { media -> val wall = ui.resultsWall; stage = wall to wall.indexOf(media).coerceAtLeast(0); vm.closeResultsWall() }, onDismiss = vm::closeResultsWall)
    stage?.let { (results, index) -> ResultStage(results, index) { stage = null } }
    val motion = rememberNexusMotionAllowed()
    val seenRows = remember { mutableSetOf<String>().apply { vm.ui.value.items.forEach { add(it.id) } } }
    // The reply that was being written is already on screen: once saved it takes the preview's place without entering again.
    val wasStreaming = remember { booleanArrayOf(false) }
    val streamingNow = ui.streamingText.isNotBlank()
    settledReplyId(wasStreaming[0], streamingNow, ui.items)?.let(seenRows::add)
    // The row that closes a turn takes the place of the live activity with the same steps on show, and folds them
    // itself (rememberTurnSettle): the end of a turn changes no height in a single step. Until it has settled the
    // rows keep their exact places, without sliding after it, and the end of the list is held frame by frame.
    val liveIn = remember { arrayOfNulls<String>(1) }
    var closingRow by remember { mutableStateOf<ClosingRow?>(null) }
    if (motion) turnClosingRowId(liveIn[0], ui.conversationId, ui.thinking, ui.items)?.let { id ->
        seenRows.add(id)
        closingRow = ClosingRow(id, textOnShow = wasStreaming[0])
    }
    SideEffect {
        wasStreaming[0] = streamingNow
        liveIn[0] = ui.conversationId.takeIf { ui.thinking && ui.steps.isNotEmpty() }
    }
    val presentation by rememberAssistantPresentationStore().state()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // A message on its way closes whatever notice was still up: it spoke about the moment before. Every way of
    // sending goes through here (the composer, a quick reply, a panel's answer, «Reintentar»).
    fun sent(accepted: Boolean): Boolean { if (accepted) snackbar.currentSnackbarData?.dismiss(); return accepted }
    val draftPrefs = LocalContext.current.getSharedPreferences("assistant_draft_v1", android.content.Context.MODE_PRIVATE)
    val draftKey="conversation:${ui.conversationId}"
    var input by rememberSaveable(ui.conversationId, stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(draftPrefs.getString(draftKey, draftPrefs.getString("text", "")).orEmpty())) }
    LaunchedEffect(ui.draftRevision) {if(ui.draftRevision>0) input=draftPrefs.getString(draftKey,"").orEmpty().let {androidx.compose.ui.text.input.TextFieldValue(it,androidx.compose.ui.text.TextRange(it.length))}}
    var showSettings by remember { mutableStateOf(false) }
    var copyMessageIds by remember(ui.conversationId) { mutableStateOf<Set<String>?>(null) }
    var showChatCopy by remember(ui.conversationId) { mutableStateOf(false) }
    if (showChatCopy) ChatCopySheet(ui.items.mapNotNull { item -> when (item) {
        is ChatItem.User -> ChatCopyEntry(item.id, "Tú", item.text)
        is ChatItem.Assistant -> item.text.takeIf { it.isNotBlank() }?.let { ChatCopyEntry(item.id, item.by.ifBlank { "Nexus" }, it) }
        is ChatItem.Notice -> ChatCopyEntry(item.id, "Nexus · Aviso", item.text)
        else -> null
    } }, copyMessageIds) { showChatCopy = false }
    var showProject by remember {mutableStateOf(false)}
    val projectDrafts = rememberSaveable(saver = ProjectMemoryDraftsSaver) { mutableStateMapOf<String, ProjectMemoryDraft>() }
    var editingProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var ordersPanel by remember {mutableStateOf(false)}
    val dirtyPlans=remember {mutableStateMapOf<String,Boolean>()}
    val included=ui.plans.values.count {it.includeInBatch && it.executable && !it.busy && it.state in setOf(PlanState.PREPARED,PlanState.AWAITING_CONFIRMATION)}
    val primaryPlanId=ui.items.filterIsInstance<ChatItem.PlanCard>().lastOrNull {ui.plans[it.planId]?.let {p->p.executable && p.state !in setOf(PlanState.SUBMITTED,PlanState.DISCARDED,PlanState.UNCERTAIN)}==true}?.planId
    val activeProject=ui.projects.firstOrNull {it.id==ui.activeProjectId}
    val listState = rememberSaveable(ui.conversationId,saver=androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState(draftPrefs.getInt("scroll:${ui.conversationId}",0),draftPrefs.getInt("offset:${ui.conversationId}",0))
    }
    LaunchedEffect(ui.conversationId,listState) {
        // Persist the settled position, rather than scheduling a preferences write on every scroll frame.
        snapshotFlow {
            if (listState.isScrollInProgress) null
            else listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.filterNotNull().distinctUntilChanged().collect {(index,offset)->
            draftPrefs.edit().putInt("scroll:${ui.conversationId}",index).putInt("offset:${ui.conversationId}",offset).apply()
        }
    }
    val picker = rememberProjectMediaPicker { uris -> vm.attach(uris) }
    val boardPicker = rememberProjectMediaPicker { uris -> vm.addBoard(uris) }
    // Cámara: la foto va a la caché (FileProvider) y se adjunta como cualquier referencia.
    val context = LocalContext.current
    val haptic = rememberNexusHaptics()
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

    var following by rememberSaveable(ui.conversationId) { mutableStateOf(draftPrefs.getInt("scroll:${ui.conversationId}",0)==0) }
    val initialScrollRequest=remember(ui.conversationId) {ui.scrollRequest}
    var automaticScroll by remember { mutableStateOf(false) }
    // Height in pixels of the notice on show at the foot of the conversation, and the room the list gives up for it at
    // its end (0 without a notice). The room is animated: the conversation slides out of the notice's way and back.
    var noticeHeight by remember { mutableStateOf(0) }
    val noticeRoom by androidx.compose.animation.core.animateIntAsState(
        if (snackbar.currentSnackbarData != null) noticeHeight else 0, label = "noticeRoom")
    val visibleItems=ui.items.filterNot {it is ChatItem.PlanCard && (ui.plans[it.planId]?.state==PlanState.DISCARDED ||
        it.orderId?.let { id -> ui.orders[id]?.status } == com.vidadomu.nexus.agent.core.orders.OrderStatus.DISCARDED)}
    // Your last message and its answer: «Editar» and «Otra respuesta», while that exchange can still be taken back.
    val takeBack = remember(ui.items, ui.plans, ui.thinking) { if (vm.canTakeBackLastTurn(ui)) LastTurn.ids(ui.items) else null }
    // Active and finished adjacent image/video orders share a live wall; failures keep their own actions.
    var expandedGroups by remember {mutableStateOf(setOf<String>())}
    var expandedPending by remember {mutableStateOf(setOf<String>())}
    val rows=remember(visibleItems,ui.orders,ui.plans,expandedGroups,expandedPending,generationJobs) {
        fun eligible(i:ChatItem):Boolean {
            if(i !is ChatItem.PlanCard) return false
            val order=i.orderId?.let {ui.orders[it]} ?: return false
            val plan=ui.plans[i.planId] ?: return false
            return mosaicEligible(order.status,order.modality)
        }
        fun pendingEligible(i:ChatItem):Boolean {
            if(i !is ChatItem.PlanCard) return false
            val order=i.orderId?.let {ui.orders[it]} ?: return false
            return order.status==com.vidadomu.nexus.agent.core.orders.OrderStatus.AWAITING_CONFIRMATION && ui.plans[i.planId]!=null
        }
        val out=mutableListOf<Any>();var run=mutableListOf<ChatItem.PlanCard>()
        var prun=mutableListOf<ChatItem.PlanCard>()
        // Varios encargos pendientes seguidos se presentan como un lote: cabecera con el resumen y, debajo, sus tarjetas.
        fun pflush() {
            if(prun.size>=2) {
                out.add(PendingHeader(prun.first().id,prun.toList()))
                if(prun.first().id in expandedPending) out.addAll(prun)
            } else out.addAll(prun)
            prun=mutableListOf()
        }
        fun flush() {
            if(run.isNotEmpty()) {
                if(run.first().id in expandedGroups) {out.add(GroupHeader(run.first().id,run.size));out.addAll(run)}
                else out.add(CardGroup(run.toList()))
            }
            run=mutableListOf()
        }
        for(i in visibleItems) {
            if(eligible(i)) {pflush();run.add(i as ChatItem.PlanCard)}
            else if(pendingEligible(i)) {flush();prun.add(i as ChatItem.PlanCard)}
            else {flush();pflush();out.add(i)}
        }
        flush();pflush();out
    }
    val latestRows by rememberUpdatedState(rows)
    val latestUi by rememberUpdatedState(ui)
    // The note about a stuck step appears under the last step while nothing else changes: the list follows it too.
    var slowNotice by remember { mutableStateOf(false) }
    suspend fun followLast() {
        if(latestRows.isEmpty() && latestUi.streamingText.isBlank()) return
        automaticScroll = true
        try {
            // Si el último elemento ya está en pantalla (lo normal mientras escribe), solo se desplaza lo que falta:
            // saltar primero al inicio del elemento y volver abajo en cada fragmento era lo que hacía parpadear el chat.
            val first=listState.layoutInfo
            val alreadyLast=first.visibleItemsInfo.lastOrNull()?.index==first.totalItemsCount-1
            if(!alreadyLast) {
                val extra = (if(latestUi.streamingText.isNotBlank()) 1 else 0) + (if(latestUi.thinking) 1 else 0)
                listState.scrollToItem((latestRows.size + extra - 1).coerceAtLeast(0))
            }
            val layout=listState.layoutInfo
            layout.visibleItemsInfo.lastOrNull()?.let { last ->
                // The reading area ends above the room the list keeps under its last row (the handle on the composer's
                // edge and the notice on show, if any), not at the composer: the last line always rests clear of both.
                listState.scrollBy(followDistance(last.offset+last.size, layout.viewportEndOffset, layout.afterContentPadding).toFloat())
            }
        }
        // A finger on the list outranks this scroll and cancels it. Whoever asked for it goes on (a collector that
        // follows the viewport must not die of it) and the next change tries again; a caller that was itself
        // cancelled still stops here.
        catch (e: java.util.concurrent.CancellationException) { if (kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.isActive != true) throw e }
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
    LaunchedEffect(ui.items.size, ui.thinking, ui.streamingText, ui.plans, ui.scrollRequest, ui.steps.size, slowNotice) {
        if (following) followLast()
    }
    // The closing row changes height for a few frames (its steps fold, its summary line and footer open): a list that
    // was at its end stays there on each of them. After that the rows slide to their places again.
    LaunchedEffect(closingRow) {
        val row = closingRow ?: return@LaunchedEffect
        try {
            val start = withFrameNanos { it }
            while (withFrameNanos { it } - start < (TurnSettleMs + 140) * 1_000_000L) {
                // A finger on the list outranks this scroll and cancels it: the next frame tries again.
                try { if (following) followLast() }
                catch (e: java.util.concurrent.CancellationException) { if (this@LaunchedEffect.coroutineContext[kotlinx.coroutines.Job]?.isActive != true) throw e }
            }
        } finally { if (closingRow === row) closingRow = null }
    }
    // Al enfocar el compositor (IME o teclado físico) el último mensaje queda justo encima del campo.
    var composerFocused by remember { mutableStateOf(false) }
    // Typing in the composer means «continue in the current project»: the chooser steps aside.
    LaunchedEffect(composerFocused, projectPicker != null) { if (composerFocused && projectPicker != null) onPickerDismiss() }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(composerFocused, imeVisible) {
        if (composerFocused) { following = true; followLast() }
    }
    LaunchedEffect(ui.scrollRequest) {
        if (ui.scrollRequest != initialScrollRequest) { following = true; followLast() }
    }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it.second); vm.consumeMessage() } }
    // A notice sits at the foot of the conversation, above the composer. The list gives up that room at its end, so the
    // notice never covers the last message; a conversation that was at its end stays there.
    LaunchedEffect(listState) {
        snapshotFlow { noticeRoom }.collect { room ->
            if (room > 0 && following) {
                withFrameNanos { } // The list has to be measured with the new room before it can scroll into it.
                automaticScroll = true
                // A finger on the list outranks this scroll and cancels it: the next step of the room tries again.
                try { listState.scrollBy(room.toFloat()) }
                catch (e: java.util.concurrent.CancellationException) { if (this@LaunchedEffect.coroutineContext[kotlinx.coroutines.Job]?.isActive != true) throw e }
                finally { automaticScroll = false }
            }
        }
    }

    if(showSettings) AssistantSettingsDialog(ui,vm,onDismiss={showSettings=false})
    LaunchedEffect(ui.projects, editingProjectId) { pruneProjectDrafts(projectDrafts, ui.projects, keep = editingProjectId) }
    if(showProject) ProjectSheet(ui,vm,projectDrafts,editingProjectId,{editingProjectId=it},
        onDraftKept = { id, name -> scope.launch {
            val result = snackbar.showSnackbar("Borrador de la memoria de «$name» conservado sin guardar.", actionLabel = "Seguir editando", withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed && projectDrafts[id] != null) { editingProjectId = id; showProject = true }
        } }) {showProject=false}

    val latestFailure=ui.items.filterIsInstance<ChatItem.Notice>().lastOrNull {it.error}
    // The failure the conversation is still about keeps its whole card; the earlier ones are one line each.
    val currentFailure = remember(ui.items) { currentFailureId(ui.items) }
    // A failure is said once, by its card in the conversation: no second notice repeats it over the composer with
    // an action of its own. A new one is still made known: a tap in the hand and, when its card is out of sight, a
    // mark on the way down. History is restored asynchronously and old notices are content: only errors created
    // during this visit do this, including when returning to another chat.
    val failureSince=remember(ui.conversationId) {System.currentTimeMillis()}
    var shownFailure by rememberSaveable(ui.conversationId) {mutableStateOf(latestFailure?.id)}
    var unseenFailure by rememberSaveable(ui.conversationId) { mutableStateOf(false) }
    LaunchedEffect(ui.conversationId,latestFailure?.id) {
        val failure=latestFailure ?: return@LaunchedEffect
        if(shouldAnnounceChatFailure(failure,failureSince,shownFailure)) {
            shownFailure=failure.id
            haptic.perform(NexusHapticEvent.ERROR)
            if (!following) unseenFailure = true
        }
    }
    LaunchedEffect(following) { if (following) unseenFailure = false }
    // Conversaciones a la izquierda: se abre arrastrando el dedo de izquierda a derecha sobre el chat.
    val drawer = rememberDrawerState(DrawerValue.Closed)
    ModalNavigationDrawer(drawerContent = { ConversationDrawer(ui, vm) { scope.launch { drawer.close() } } }, drawerState = drawer,
        gesturesEnabled = drawer.isOpen) {
    // Opaque ground: the animated aura of the app's theme stays behind the other tabs, not behind the conversation.
    // The notices are not in the Scaffold's slot: there they floated over the composer. They sit at the foot of the conversation.
    Scaffold(contentWindowInsets=com.vidadomu.nexus.ui.theme.NexusInsets.chromeOnly,containerColor=MaterialTheme.colorScheme.background,topBar={}) {pad ->
        Column(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).statusBarsPadding().windowInsetsPadding(com.vidadomu.nexus.ui.theme.NexusInsets.bottomEdge)) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val chatContext = LocalContext.current
            val clipboard = LocalClipboardManager.current
            var menuOpen by remember { mutableStateOf(false) }
            var menuAt by remember { mutableStateOf(androidx.compose.ui.unit.DpOffset.Zero) }
            var headerMenu by remember { mutableStateOf(false) }
            var showAppearance by remember { mutableStateOf(false) }
            val barPinned by com.vidadomu.nexus.ui.theme.ChromeVisibility.pinned
            // Compact header, fixed above the conversation: the title leads and the line under it says what the agent
            // is really doing. The model selector lives in the composer; the menu and the settings stay here.
            val headerHaptics = rememberNexusHaptics()
            val activeProjectName = remember(ui.projects, ui.activeProjectId) {
                ui.projects.firstOrNull { it.id == ui.activeProjectId }?.name?.takeIf { it.isNotBlank() } ?: "Nexus"
            }
            val activeModelShort = remember(ui.settings) {
                shortModelName(agentLabel(ui.settings)).ifBlank { agentLabel(ui.settings) }
            }
            val titles = remember(chatContext) { ConversationTitles(chatContext) }
            val firstRequest = ui.items.firstOrNull { it is ChatItem.User && it.text.isNotBlank() } as? ChatItem.User
            // Read again when the drawer closes: that is where a conversation is renamed.
            val headerTitle = remember(ui.conversationId, firstRequest?.text, drawer.isOpen) {
                titles.display(ui.conversationId, firstRequest?.text?.replace(Regex("\\s+"), " ")?.take(180) ?: "Nueva conversación")
            }
            val conversationOrders = remember(visibleItems, ui.orders) {
                visibleItems.filterIsInstance<ChatItem.PlanCard>().mapNotNull { card -> card.orderId?.let { ui.orders[it] } }
            }
            val openChatMenu: @Composable (Boolean, androidx.compose.ui.unit.DpOffset, () -> Unit) -> Unit = { expanded, at, dismiss ->
                ChatContextMenu(ui, vm, expanded, at, dismiss, onProjects = { showProject = true }, onAppearance = { showAppearance = true },
                    barPinned = barPinned, onPinBar = { com.vidadomu.nexus.ui.theme.ChromeVisibility.setPinned(chatContext, it) },
                    onCopyChat = {
                        copyMessageIds = null; showChatCopy = true
                    },
                    onShareChat = { shareChat(chatContext, ui) },
                    onImportImages = { boardPicker.launch(arrayOf("image/*"), Int.MAX_VALUE) },
                    onOrders = if (conversationOrders.isNotEmpty()) ({ ordersPanel = true }) else null,
                    onAssistantSettings = { showSettings = true })
            }
            AgentChatHeader(
                title = headerTitle,
                status = agentHeaderStatus(ui.thinking, ui.steps, ui.queuedSend, ui.workingIn != null, conversationOrders,
                    ui.attaching, ui.carrying, ui.settings.offline, activeProjectName, activeModelShort),
                onMenu = { scope.launch { drawer.open() } },
                onTitle = { headerHaptics.perform(NexusHapticEvent.SELECTION); showProject = true },
                onSettings = onOpenSettings,
                onMore = { headerMenu = true },
                moreMenu = { openChatMenu(headerMenu, androidx.compose.ui.unit.DpOffset.Zero) { headerMenu = false } },
            )
            if(ui.settings.offline) InlineNotice("Planificador local activo.",Modifier.padding(horizontal=NexusTokens.screenMargin),
                "Elige modelos y prepara planes sin razonamiento del LLM.","Configurar LLM",{showSettings=true},isError=false)
            Box(Modifier.weight(1f).fillMaxWidth()
                // Mantener pulsado un hueco del chat abre el menú de acciones rápidas, justo donde está el dedo.
                // Both detectors fire at the same timeout; a short wait lets a message claim the press first, so a long
                // press on text opens only that message's menu and the chat menu stays for the empty space between messages.
                .pointerInput(Unit) { detectTapGestures(onLongPress = { pos ->
                    val at = with(density) { androidx.compose.ui.unit.DpOffset(pos.x.toDp(), pos.y.toDp()) }
                    scope.launch {
                        kotlinx.coroutines.delay(120)
                        if (!MessageLongPress.recent()) { menuAt = at; menuOpen = true }
                    }
                }) }
                .dragRightToOpen { scope.launch { drawer.open() } }) {
            // The header is fixed above the list now, so nothing floats over the text and the list needs no fade mask.
            LazyColumn(Modifier.fillMaxSize(), state = listState,
                contentPadding = PaddingValues(start = NexusTokens.screenMargin, end = NexusTokens.screenMargin, top = NexusTokens.space8,
                    // Room for the tab-bar handle that sits on the composer's top edge, so it never covers the last line,
                    // plus the notice on show, if any.
                    bottom = 28.dp + with(density) { noticeRoom.toDp() }), verticalArrangement = Arrangement.spacedBy(NexusTokens.space16)) {
                if (ui.items.isEmpty() && ui.streamingText.isBlank()) item {
                    EmptyHint(onSuggest = { suggestion ->
                        input = androidx.compose.ui.text.input.TextFieldValue(suggestion, androidx.compose.ui.text.TextRange(suggestion.length))
                    })
                }
                items(rows, key = { when (it) { is ChatItem -> it.id; is CardGroup -> it.key; is GroupHeader -> "gh:" + it.firstId; is PendingHeader -> "ph:" + it.firstId; else -> it.hashCode() } }) { row ->
                    val still = closingRow != null
                    if (row is PendingHeader) {
                        val open = row.firstId in expandedPending
                        val currency = (LocalContext.current.applicationContext as com.vidadomu.nexus.NexusApplication).currencyManager
                        val currencyNow by currency.state.collectAsState()
                        val plans = row.cards.mapNotNull { ui.plans[it.planId] }
                        val amounts = plans.mapNotNull { it.plan.estimate?.amountUsd }
                        Card(Modifier.fillMaxWidth().then(rowPlacement(still)), shape = NexusTokens.card) {
                            Column(Modifier.padding(NexusTokens.space12), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${row.cards.size} encargos pendientes de confirmar", style = MaterialTheme.typography.titleSmall)
                                        Text(listOfNotNull(plans.map { it.plan.modelName }.distinct().joinToString(", ").takeIf { it.isNotBlank() },
                                            if (amounts.size == plans.size && amounts.isNotEmpty()) remember(amounts, currencyNow) { currency.formatCost(amounts.sum()) } else "coste por confirmar").joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                    TextButton(onClick = { expandedPending = if (open) expandedPending - row.firstId else expandedPending + row.firstId }) {
                                        Text(if (open) "Resumir" else "Revisar y editar")
                                    }
                                }
                                if (!open) plans.forEachIndexed { idx, pl ->
                                    Row(Modifier.fillMaxWidth().clip(NexusTokens.thumbnail).clickable { expandedPending = expandedPending + row.firstId }.padding(vertical = NexusTokens.space4),
                                        horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), verticalAlignment = Alignment.Top) {
                                        Text("${idx + 1}", style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.width(18.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(pl.plan.prompt.ifBlank { "(sin prompt)" }, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                            val pics = pl.plan.media.filter { it.mimeType?.startsWith("video") != true && it.mimeType?.startsWith("audio") != true }
                                            if (pics.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space4), modifier = Modifier.padding(top = NexusTokens.space4)) {
                                                items(pics.take(8)) { m -> AsyncImage(model = m.uri, contentDescription = "Imagen adjunta", contentScale = ContentScale.Crop,
                                                    modifier = Modifier.size(40.dp).clip(NexusTokens.thumbnail)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        return@items
                    }
                    if (row is CardGroup) {
                        val tiles = row.cards.flatMap { c ->
                            val order = c.orderId?.let { ui.orders[it] } ?: return@flatMap emptyList<MosaicTile>()
                            val p = ui.plans[c.planId] ?: return@flatMap emptyList<MosaicTile>()
                            val jobId = p.jobId ?: order.jobId
                            val outputs = vm.outputs(jobId).filter { it.isNotBlank() }
                            val media = if (outputs.isEmpty()) listOf<String?>(null) else outputs.map { it }
                            media.mapIndexed { index, uri ->
                                MosaicTile(key = "${c.id}:$index", orderId = order.id, jobId = jobId,
                                    model = p.plan.modelName, provider = p.plan.ref.providerId,
                                    modelId = p.plan.ref.modelId, modality = order.modality,
                                    status = order.status, uri = uri, progress = measuredOrderProgress(order))
                            }
                        }
                        // Tell the agent exactly which status and number of thumbnails the user sees.
                        LaunchedEffect(tiles.map { Triple(it.orderId, it.status, it.uri != null) }) {
                            tiles.groupBy { it.orderId }.forEach { (orderId, items) ->
                                val count = items.count { it.uri != null }
                                val label = if (items.first().status == com.vidadomu.nexus.agent.core.orders.OrderStatus.COMPLETED)
                                    "Completado · $count resultado(s) visibles en mosaico"
                                else if (items.first().status == com.vidadomu.nexus.agent.core.orders.OrderStatus.SUBMITTING)
                                    "Enviando · mosaico en vivo"
                                else "Procesando · mosaico en vivo"
                                vm.cardRendered(orderId, label)
                            }
                        }
                        GenerationMosaic(tiles, onOpen = { clicked ->
                            val ready = tiles.filter { it.uri != null }
                            stage = ready.mapIndexed { index, t ->
                                Attachment(t.key, t.uri!!,
                                    if (t.modality == "VIDEO") "video/mp4" else "image/*",
                                    "${t.model} · ${index + 1}")
                            } to ready.indexOfFirst { it.key == clicked.key }.coerceAtLeast(0)
                        }, onExpand = { expandedGroups = expandedGroups + row.cards.first().id },
                            modifier = rowPlacement(still))
                        return@items
                    }
                    if (row is GroupHeader) {
                        TextButton(onClick = { expandedGroups = expandedGroups - row.firstId }) { Text("Volver al mosaico (${row.count})") }
                        return@items
                    }
                    val item = row as ChatItem
                    EntranceSpring(item.id, seenRows, if (motion) rowPlacement(still) else Modifier) {
                    when (item) {
                        is ChatItem.User -> Box { UserBubble(item,
                            onEdit = if (takeBack?.first == item.id) ({ vm.editLastMessage() }) else null) { copyMessageIds = setOf(item.id); showChatCopy = true } }
                        is ChatItem.Assistant -> Box {
                            AssistantBubble(item, onOpenHistory,
                                onAnswerAgain = if (takeBack?.second == item.id) ({ vm.answerAgain() }) else null,
                                onSelectMessages = { copyMessageIds = setOf(item.id); showChatCopy = true },
                                answered = answerAfter(ui.items, item)?.text,
                                widgetAnswer = answerAfter(ui.items, item)?.widgetAnswer,
                                onReply = { sent(vm.send(it)) }, onWidgetReply = { sent(vm.send(it.text, widgetAnswer = it.answer)) },
                                closesTurn = closingRow?.id == item.id, textOnShow = closingRow?.textOnShow != false)
                        }
                        is ChatItem.Notice -> {
                            // «Reintentar» belongs to the notice that closed the failed turn, while the app holds its message.
                            val canRetry = item.error && item.id == ui.retryNoticeId && !ui.thinking
                            NoticeRow(item, onOpenHistory,
                                onRetry = if (canRetry) ({ sent(vm.retryTurn(item.id)) }) else null,
                                onChangeModel = { showSettings = true },
                                onNewChat = if (canRetry && (item.text.contains("conversación nueva", true) || item.text.contains("razonamiento", true)))
                                    ({ vm.continueFailedTurnInNewChat() }) else null,
                                compact = item.id != currentFailure, closesTurn = closingRow?.id == item.id,
                                // Turno cortado por Android: mientras ofrece Reintentar, también se puede descartar.
                                onDismiss = if (canRetry && InterruptedTurns.isNotice(item.id)) ({ vm.dismissInterruptedTurn(item.id) }) else null)
                        }
                        is ChatItem.ModeSwitch -> Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(NexusTokens.space12)) {
                                Text(item.text, style = MaterialTheme.typography.bodyMedium)
                                if (!item.resolved) TextButton(onClick = { vm.resolveModeSwitch(item.id) }, enabled = !ui.thinking) { Text("Activar Agente") }
                            }
                        }
                        is ChatItem.Board -> ChatBoardCard(item.boardId)
                        is ChatItem.Context -> CarriedContextCard(item)
                        is ChatItem.Proposal -> ProposalCard(item, onApply = { vm.applyProposal(item.id) }, onReject = { vm.rejectProposal(item.id) })
                        is ChatItem.PlanCard -> item.orderId?.let { ui.orders[it] }?.takeIf { ui.plans[item.planId] != null }?.let { order ->
                            val p = ui.plans.getValue(item.planId)
                            val orderOutputs = vm.outputs(p.jobId ?: order.jobId)
                            StagedOrder(order, orderOutputs, onOpenStage = { index ->
                                stage = orderOutputs.mapIndexed { i, uri -> Attachment("${order.id}:$i", uri,
                                    when (order.modality) { "VIDEO" -> "video/mp4"; "AUDIO" -> "audio/mpeg"; else -> "image/*" }, "Resultado ${i + 1}") } to index
                            }) {
                            OrderCard(order, p, orderOutputs, assistantBusy = ui.thinking, modifier = rowPlacement(), presentation = presentation,
                                onGenerate = { vm.generate(p.plan.id, p.plan.fingerprint()) },
                                onApplyFix = { vm.applyFix(order.id) },
                                onAskAgent = { vm.askAgentToFix(order.id) },
                                onEdit = { vm.editPlan(p.plan.id, it) },
                                onDiscard = { vm.discardPlan(p.plan.id) },
                                onRemove = { vm.removeFailedCard(p.plan.id) },
                                onPrepareRetry = { vm.editPlan(p.plan.id, PlanEdit()) },
                                onOpenCanvas = { scope.launch { vm.canvasState(p.plan.id)?.let(onOpenInCanvas) } },
                                onOpenOutput = { (p.jobId ?: order.jobId)?.let(onNavigateToOutput) },
                                onRendered = { vm.cardRendered(order.id, it) },
                                onOpenHistory = onOpenHistory,
                                recoverable = order.id in ui.recoverable,
                                onRecover = { vm.recoverOrder(order.id) })
                            }
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
                }
                // The turn in progress, in the conversation itself: its real steps first, then the text as it arrives.
                if (ui.thinking) item(key = "agent_activity_live") {
                    AgentActivityLive(ui.steps, ui.thinkingStartedAtElapsedMs, lastActivityAtElapsedMs = ui.lastActivityAtElapsedMs,
                        onSlowChanged = { slowNotice = it })
                }
                if (ui.streamingText.isNotBlank()) {
                    item(key = "streaming_assistant_preview") {
                        AssistantBubble(ChatItem.Assistant("streaming_assistant_preview", ui.streamingText, emptyList(), "STREAMING"), onOpenHistory)
                    }
                }
            }
            // Un único botón redondo para bajar, solo cuando no estás al final; los encargos tienen su propio panel.
            val cardIds = remember(visibleItems) { visibleItems.filterIsInstance<ChatItem.PlanCard>().map { it.id } }
            // The scroll-down button rides above the notice on show instead of hiding under it.
            Column(Modifier.align(Alignment.BottomEnd).padding(bottom = with(density) { noticeRoom.toDp() }).padding(NexusTokens.space12), horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                // «Ver encargos» moved to the header: only the scroll-down button floats over the reading area.
                AnimatedVisibility(!following,
                    enter = if (motion) fadeIn() + scaleIn() else androidx.compose.animation.EnterTransition.None,
                    exit = if (motion) fadeOut() + scaleOut() else androidx.compose.animation.ExitTransition.None) {
                    // Matte and small: it is a way back, not the main action of the screen.
                    Box {
                        Surface(onClick = { following = true; scope.launch { followLast() } }, shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.KeyboardArrowDown, if (unseenFailure) "Bajar al final: hay un aviso de error" else "Bajar al final", Modifier.size(22.dp))
                            }
                        }
                        // A failure arrived below while the reader was further up: the way down says so.
                        if (unseenFailure) Box(Modifier.align(Alignment.TopEnd).size(10.dp)
                            .background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape))
                    }
                }
            }
            projectPicker?.let { picker -> Surface(Modifier.matchParentSize(), color = MaterialTheme.colorScheme.background) { picker() } }
            // Long press on an empty spot of the chat: the same menu as the header's, where the finger is.
            openChatMenu(menuOpen, menuAt) { menuOpen = false }
            if (showAppearance) QuickAppearanceSheet({ showAppearance = false }, onOpenSettings)
            // Notices at the foot of the conversation, right above the composer: the field, its controls and the stop
            // button stay in sight and in reach while a notice is up.
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).onSizeChanged { noticeHeight = it.height }) { data ->
                Snackbar(data,modifier=Modifier.padding(horizontal=NexusTokens.space12,vertical=NexusTokens.space8),shape=NexusTokens.card,
                    containerColor=MaterialTheme.colorScheme.surfaceContainerHigh,contentColor=TextPrimary,
                    actionColor=MaterialTheme.colorScheme.primary)
            }
            }
            if (ordersPanel) OrdersSheet(visibleItems.filterIsInstance<ChatItem.PlanCard>(), ui, onClose = { ordersPanel = false }) { id ->
                ordersPanel = false; following = false
                scope.launch { listState.animateScrollToItem(visibleItems.indexOfFirst { it is ChatItem.PlanCard && it.id == id }.coerceAtLeast(0)) }
            }
            RecentLibraryPanel(libraryExpanded, onClose = { libraryExpanded = false }) {
                LibraryCarousel(onPick = { jobId, outputId ->
                    vm.attachLibraryResult(jobId, outputId)
                    libraryExpanded = false
                }, enabled = !ui.attaching && !ui.thinking, onBrowseAll = {
                    libraryExpanded = false
                    picker.launch(arrayOf("image/*", "video/*", "audio/*") + AgentDocuments.types, maxSelection = com.vidadomu.nexus.settings.RuntimePreferences.get(context).int("chat.attachmentCount").takeIf {it>0} ?: Int.MAX_VALUE)
                })
            }
            PendingGenerations(ui,blockedByDraft=ui.plans.values.any {it.includeInBatch && it.executable && !it.busy && it.state in setOf(PlanState.PREPARED,PlanState.AWAITING_CONFIRMATION) && dirtyPlans[it.plan.id]==true},onGenerate=vm::generatePending)
            storySelection?.takeIf { selection -> selection.projectId == ui.activeProjectId && storyIndex.projects.any { it.id == selection.projectId && !it.archived } }?.let { selection ->
                val scene=storyIndex.scenes.firstOrNull {it.id==selection.sceneId}
                val shot=storyIndex.shots.firstOrNull {it.id==selection.shotId}
                // Pastilla compacta: indica que el agente está trabajando sobre ese guion; la ✕ lo desvincula.
                Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,shape=NexusTokens.pill,modifier=Modifier.padding(horizontal=12.dp,vertical=2.dp).widthIn(max=260.dp).height(36.dp)) {
                    Row(Modifier.padding(start=10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.MovieFilter,"Guion vinculado",Modifier.size(16.dp))
                        Column(Modifier.weight(1f,fill=false).padding(horizontal=6.dp).clickable {showProject=true}) {
                            Text(listOfNotNull(scene?.title ?: scene?.code,shot?.title ?: shot?.code).joinToString(" › ").ifBlank {ui.projects.firstOrNull {it.id==selection.projectId}?.name ?: "Proyecto"},style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            // (el detalle «Propuestas · proyecto» se ve al tocar la pastilla; aquí solo el nombre)
                        }
                        Box(Modifier.width(NexusTokens.minimumTouch).fillMaxHeight().clickable(interactionSource=null,indication=androidx.compose.material3.ripple(bounded=false,radius=18.dp),role=androidx.compose.ui.semantics.Role.Button) {com.vidadomu.nexus.assistant.story.StorySelectionStore.clear()},contentAlignment=Alignment.Center) {Icon(Icons.Default.Close,"Desvincular el guion",Modifier.size(16.dp))}
                    }
                }
            }
            Box(Modifier.fillMaxWidth().onFocusChanged { composerFocused = it.hasFocus }) {
            InputBar(ui, input, onInput = { input = it; draftPrefs.edit().putString(draftKey, it.text).remove("text").apply() }, onAttach = { picker.launchDevice(arrayOf("image/*", "video/*", "audio/*") + AgentDocuments.types, maxSelection = com.vidadomu.nexus.settings.RuntimePreferences.get(context).int("chat.attachmentCount").takeIf {it>0} ?: Int.MAX_VALUE) },
                onSend = { if(sent(vm.sendWithBoards(input.text))) {haptic.perform(NexusHapticEvent.CONFIRMATION);draftPrefs.edit().remove(draftKey).remove("text").apply(); input = androidx.compose.ui.text.input.TextFieldValue("")} }, onStop = { vm.stop(); haptic.perform(NexusHapticEvent.CANCEL_CONFIRMED) }, onRemove = vm::removeAttachment, onReply = { sent(vm.send(it)) }, onCamera = takePhoto, onAudio=vm::attachAudio,onReorder=vm::reorderAttachment,
                onShowLibrary = { openLibrary() }, onHideLibrary = { libraryExpanded = false }, libraryExpanded = libraryExpanded,
                // Mode and model sit in the composer, wired to the same settings as before.
                reasoningControl = { ReasoningPicker(ui, vm); BridgeModelPicker(ui, vm) { showSettings = true } },
                // «Añade algo mientras trabaja»: the draft and its attachments wait, editable, and go out when the answer ends.
                onQueue = {
                    if (vm.queueSend()) haptic.perform(NexusHapticEvent.SELECTION)
                    else scope.launch { snackbar.showSnackbar("Ahora no se puede dejar en espera. Tu borrador sigue aquí.") }
                },
                onCancelQueue = vm::cancelQueuedSend)
            }
        }
    }
    }
}

/** Summary brought from another conversation: a short card, tap to read it whole. */
@Composable
private fun CarriedContextCard(item: ChatItem.Context) {
    var open by rememberSaveable(item.id) { mutableStateOf(false) }
    Surface(onClick = { open = !open }, shape = NexusTokens.card, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(NexusTokens.space12), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                Icon(Icons.Default.Forum, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Contexto de «${item.from}»", style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Text(item.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (open) Int.MAX_VALUE else 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

private val HIDE_ID_GROUP_PARENS = Regex("""[ \t]*\(\s*`?(?:ord|ast|att|job|req)_[0-9a-zA-Z_-]{6,24}`?(?:\s*[,;·•]\s*`?(?:ord|ast|att|job|req)_[0-9a-zA-Z_-]{6,24}`?)*\s*\)""")
private val HIDE_SINGLE_INTERNAL_ID = Regex("""[ \t]*`?(?:ord|ast|att|job|req)_[0-9a-zA-Z_-]{6,24}`?""")

/** Los IDs internos (ord_, ast_, att_…) no se enseñan en el chat; tampoco quedan paréntesis ni comas huérfanas. */
internal fun hideOrderIds(text: String): String =
    text.replace(HIDE_ID_GROUP_PARENS, "")
        .replace(HIDE_SINGLE_INTERNAL_ID, "")
        .replace(Regex("""\(\s*[,;·•\s]+\)"""), "")
        .replace(Regex("""\(\s*[,;·•]+\s*"""), "(")
        .replace(Regex("""\s*[,;·•]+\s*\)"""), ")")
        .replace(Regex("""[ \t]*,[ \t]*(?:,[ \t]*)+"""), ", ")
        .replace(Regex("""^[ \t]*,[ \t]*""", RegexOption.MULTILINE), "")
        .replace(Regex("""([:;(\[])\s*,\s*"""), "$1 ")
        .replace(Regex("""[ \t]*,[ \t]*(?=[.;:!?])"""), "")
        .replace(Regex(""" {2,}"""), " ")

/** Panel propio de encargos: estado con icono, modelo y coste; tocar uno lleva a su tarjeta en el chat. Sin IDs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrdersSheet(cards: List<ChatItem.PlanCard>, ui: AssistantUiState, onClose: () -> Unit, onPick: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as com.vidadomu.nexus.NexusApplication
    val currencyState by app.currencyManager.state.collectAsState()
    NexusSheet("Encargos", onClose, subtitle = "${cards.size} encargos", icon = Icons.Default.Assignment) {
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
                    Icon(icon, label, tint = tint, modifier = Modifier.size(NexusTokens.iconMedium))
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
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = NexusTokens.space8), modifier = Modifier.heightIn(min = NexusTokens.minimumTouch)) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyHint(onSuggest: ((String) -> Unit)? = null) {
    val haptics = rememberNexusHaptics()
    val suggestions = remember {
        listOf(
            "Diseña un plano cinematográfico con referencias",
            "Propón un guion corto de 3 escenas",
            "Revisa qué modelos me convienen para vídeo",
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = NexusTokens.space24, start = NexusTokens.space16, end = NexusTokens.space16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NexusTokens.space12),
    ) {
        // Vidal 14-11: la bienvenida es el onboarding, con el lobo de NEXUS en grande y a resolución completa.
        // Va dentro de la lista, así que con el teclado abierto se desplaza en vez de tapar las sugerencias.
        androidx.compose.foundation.Image(
            bitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(com.vidadomu.nexus.R.drawable.nexus_logo),
            contentDescription = "Logo de NEXUS",
            modifier = Modifier.fillMaxWidth(.66f).widthIn(max = 300.dp).aspectRatio(1f),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
        )
        Text(
            text = "Las buenas ideas empiezan aquí.",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Text(
            "¿Una imagen, un vídeo o una idea por desarrollar?",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        if (onSuggest != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = NexusTokens.space8),
                verticalArrangement = Arrangement.spacedBy(NexusTokens.space8),
            ) {
                suggestions.forEach { prompt ->
                    StudioTactileCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = NexusTokens.pill,
                        containerColor = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(NexusTokens.pill)
                                .clickable {
                                    haptics.perform(NexusHapticEvent.SELECTION)
                                    onSuggest(prompt)
                                }
                                .padding(horizontal = NexusTokens.space16, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8),
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(prompt, style = MaterialTheme.typography.bodySmall, color = TextPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoticeRow(
    item: ChatItem.Notice,
    onOpenHistory: () -> Unit = {},
    onRetry: (() -> Unit)? = null,
    onChangeModel: (() -> Unit)? = null,
    onNewChat: (() -> Unit)? = null,
    compact: Boolean = false,
    /** This notice has just closed the turn that was on show (stopped or failed): its steps stay where they were. */
    closesTurn: Boolean = false,
    onDismiss: (() -> Unit)? = null,
) {
    val history = com.vidadomu.nexus.ui.navigation.HistoryLinks.mentionsHistoryScreen(item.text)
    // The steps were on screen; the notice itself is what is new.
    val settle = rememberTurnSettle(item.id, closesTurn, bodyOnShow = false)
    Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
    // A turn that was stopped or failed keeps what it had done: the step with the incident is the explanation. They
    // fold with their notice when the conversation moves on.
    AgentActivitySaved(item.id + ":steps", item.steps, initiallyOpen = item.error && !compact, superseded = item.error && compact,
        arrived = settle.arrived, liveGap = LiveActivityGap)
    // Todo error ofrece una salida directa: repetir, cambiar de modelo, continuar en chat nuevo o revisar el historial.
    if (item.error) AgentErrorNotice(
        item.id, item.text,
        onRetry = onRetry,
        onChangeModel = onChangeModel,
        onNewChat = onNewChat,
        onOpenHistory = if (history) onOpenHistory else null,
        modifier = settle.entering,
        compact = compact,
        onDismiss = onDismiss,
    )
    else Column(settle.entering) {
        Text(item.text,style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
        if(history) HistoryLinkButton(onOpenHistory)
    }
    }
}
/** Acceso directo a la pestaña Historial desde un aviso que la menciona. */
@Composable
internal fun HistoryLinkButton(onOpenHistory: () -> Unit) {
    TextButton(onClick=onOpenHistory) {
        Icon(Icons.Default.Timelapse,null,Modifier.size(NexusTokens.iconSmall))
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

// ---- entrada ---------------------------------------------------------------------------------

@Composable
internal fun AttachmentThumb(a: Attachment, onRemove: (() -> Unit)? = null) {
    var failed by remember(a.uri) { mutableStateOf(false) }
    // Si el archivo ya no está (borrado de la biblioteca o permiso perdido) se muestra un hueco visible, no un cuadro vacío.
    Box(Modifier.size(64.dp).clip(NexusTokens.thumbnail).then(Modifier.background(MaterialTheme.colorScheme.surfaceVariant))) {
        if (a.mimeType.startsWith("image/") && failed) Column(Modifier.fillMaxSize().padding(NexusTokens.space4), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.BrokenImage, "Imagen no disponible", tint = TextSecondary, modifier = Modifier.size(22.dp))
            Text(a.name ?: "Imagen", style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        else if (a.mimeType.startsWith("image/")) AsyncImage(model = a.uri, contentDescription = a.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), onError = { failed = true })
        // Vídeo: primer fotograma real (como en Biblioteca) con ▶ encima; el icono solo si el archivo no se puede leer.
        else if (a.mimeType.startsWith("video/") && !failed) Box(Modifier.fillMaxSize()) {
            val context = androidx.compose.ui.platform.LocalContext.current
            AsyncImage(model = remember(a.uri) { coil.request.ImageRequest.Builder(context).data(a.uri)
                    .decoderFactory { result, options, _ -> coil.decode.VideoFrameDecoder(result.source, options) }.build() },
                contentDescription = a.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), onError = { failed = true })
            Icon(Icons.Default.PlayArrow, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.Center).size(26.dp)
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .45f), androidx.compose.foundation.shape.CircleShape).padding(3.dp))
        }
        else Surface(color = SurfaceVariantDark, modifier = Modifier.fillMaxSize()) { Box(contentAlignment = Alignment.Center) { Icon(if(a.mimeType.startsWith("video/")) Icons.Default.Movie else Icons.Default.Description, a.name) } }
        if (onRemove != null) IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(NexusTokens.minimumTouch)) {
            Icon(Icons.Default.Close, "Quitar", tint = MediaForeground)
        }
    }
}

private data class CardGroup(val cards: List<ChatItem.PlanCard>) { val key get() = "group:" + cards.first().id }
private data class GroupHeader(val firstId: String, val count: Int)
private data class PendingHeader(val firstId: String, val cards: List<ChatItem.PlanCard>)

/** Monotonic turn time: never depends on when a recycled LazyColumn indicator enters composition. */
internal fun thinkingElapsedSeconds(nowElapsedMs: Long, startedAtElapsedMs: Long): Long =
    if (startedAtElapsedMs <= 0L) 0L else ((nowElapsedMs - startedAtElapsedMs).coerceAtLeast(0L) / 1000L)

/**
 * A row slides to its new place when the list changes and fades out when it is removed, but the list never fades it
 * in. A jump (`scrollToItem`) makes the list forget every row it knew, so it replayed that fade on every row in view:
 * when a turn ended the whole conversation went blank and came back. New rows still enter once, with [EntranceSpring].
 */
private fun LazyItemScope.rowPlacement(still: Boolean = false): Modifier =
    // [still]: while the row that closes a turn settles its height changes on every frame. Sliding after each of
    // those changes would leave every row a little behind its place; they are simply put where they belong.
    if (still) Modifier.animateItem(fadeInSpec = null, placementSpec = null) else Modifier.animateItem(fadeInSpec = null)

/**
 * The live activity is a row of its own, one list gap above the text being written; kept with the reply, the steps
 * sit closer to it. The row that closes a turn starts with that difference under its steps and closes it as it settles.
 */
internal val LiveActivityGap = NexusTokens.space16 - NexusTokens.space4

/** The row that is taking the place of the live activity right now, and whether its words were already being written. */
private class ClosingRow(val id: String, val textOnShow: Boolean)

/**
 * The row that has just closed the turn whose steps were on show: the reply, or the notice of an answer that was
 * stopped or failed. It takes the place of the live activity, so it settles from it instead of entering as a new
 * row. [liveIn] is the conversation that was showing live steps a moment ago. Null while the agent still works, in
 * any other conversation, when the turn kept no steps, and when the cards of the turn were moved after its reply:
 * then the rows change order and each one slides to its place as it did.
 */
internal fun turnClosingRowId(liveIn: String?, conversationId: String, working: Boolean, items: List<ChatItem>): String? {
    if (liveIn == null || liveIn != conversationId || working) return null
    val turn = items.takeLastWhile { it !is ChatItem.User }
    val at = turn.indexOfLast { (it is ChatItem.Assistant && it.steps.isNotEmpty()) || (it is ChatItem.Notice && it.steps.isNotEmpty()) }
    if (at < 0 || turn.drop(at + 1).any { it is ChatItem.PlanCard }) return null
    return turn[at].id
}

/**
 * How far the list still has to move for its last row to rest at the end of the reading area. [viewportEnd] is the
 * very edge of the list and [roomBelow] what it keeps free under its last row, so the reading area ends that much
 * higher. Never negative: a row that already rests above that line stays where it is.
 */
internal fun followDistance(lastRowEnd: Int, viewportEnd: Int, roomBelow: Int): Int =
    (lastRowEnd - (viewportEnd - roomBelow)).coerceAtLeast(0)

/**
 * The reply that has just taken the place of the text that was being written. Its words were already on screen,
 * so it must not play the entrance of a new row. Null while the text is still arriving, or when none was.
 */
internal fun settledReplyId(wasStreaming: Boolean, streamingNow: Boolean, items: List<ChatItem>): String? =
    if (wasStreaming && !streamingNow) items.lastOrNull { it is ChatItem.Assistant }?.id else null
