package com.vidadomu.nexus.ui.cut

import android.app.Application
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AlignHorizontalLeft
import androidx.compose.material.icons.automirrored.filled.AlignHorizontalRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.assistant.rememberProjectMediaPicker
import com.vidadomu.nexus.cut.command.Origin
import com.vidadomu.nexus.cut.export.ExportMath
import com.vidadomu.nexus.cut.export.ExportState
import com.vidadomu.nexus.cut.model.Clip
import com.vidadomu.nexus.cut.model.ClipKind
import com.vidadomu.nexus.cut.model.FrameRate
import com.vidadomu.nexus.cut.model.Timeline
import com.vidadomu.nexus.cut.model.Track
import com.vidadomu.nexus.cut.model.TrackKind
import com.vidadomu.nexus.cut.timeline.Edge
import com.vidadomu.nexus.ui.theme.NexusHapticEvent
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.rememberNexusHaptics
import com.vidadomu.nexus.ui.theme.rememberNexusMotionAllowed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Nexus Cut: la imagen arriba y el montaje debajo ocupan casi toda la pantalla. La cabecera, el transporte
 * y el muelle de herramientas son finos; lo secundario (formato, ripple, imán, calidad, exportación
 * avanzada) vive en hojas que se abren al pedirlo. El agente es un botón del muelle que abre su panel y,
 * cerrado, enseña en una píldora lo que está haciendo. Todo lo que se toca pasa por el bus del montaje, el
 * mismo que usa el agente: sus cambios aparecen aquí al instante y comparten «Deshacer».
 */
@UnstableApi
@Composable
fun CutEditorScreen(projectId: String, onBack: () -> Unit, onOpenLibraryAsset: (String) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: CutEditorViewModel = viewModel(key = "cut-$projectId", factory = CutEditorViewModel.Factory(app, projectId))
    val chat: CutChatViewModel = viewModel(key = "cut-chat-$projectId", factory = CutChatViewModel.Factory(app, projectId))
    val state by vm.state.collectAsState()
    val loadError by vm.loadError.collectAsState()
    BackHandler(onBack = onBack)
    val st = state
    CutEditorTheme {
        if (st == null) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                if (loadError != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(loadError!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                    TextButton(onClick = onBack) { Text("Volver") }
                } else CircularProgressIndicator()
            }
        } else {
            CutEditor(vm, chat, onBack, onOpenLibraryAsset)
        }
    }
}

internal const val CUT_HEADER_DP = 44
internal const val CUT_TRANSPORT_DP = 40
internal const val CUT_DOCK_DP = 58
internal const val CUT_TRACK_HEADER_DP = 52
internal const val CUT_EXPORT_BAR_DP = 28

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@UnstableApi
@Composable
private fun CutEditor(vm: CutEditorViewModel, chat: CutChatViewModel, onBack: () -> Unit, onOpenLibraryAsset: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val haptics = rememberNexusHaptics()
    val palette = rememberCutPalette()
    val motion = rememberNexusMotionAllowed()
    val st = vm.state.collectAsState().value ?: return
    val project = st.project
    val drag by vm.dragTimeline.collectAsState()
    val timeline = drag ?: project.timeline
    val selection = st.view.selection
    val rate = project.settings.frameRate
    // El cabezal se pasa como State: lo leen el reloj y el lienzo al dibujar, no el editor entero.
    val playheadState = vm.preview.positionUs.collectAsState()
    val playing by vm.preview.playing.collectAsState()
    val buffering by vm.preview.buffering.collectAsState()
    val still by vm.preview.still.collectAsState()
    val showStill by vm.preview.showStill.collectAsState()
    val previewError by vm.preview.error.collectAsState()
    val offline by vm.offline.collectAsState()
    val waveforms by vm.waveforms.collectAsState()
    val thumbVersion = vm.thumbnails.version.collectAsState()
    val snapLine by vm.snapLineUs.collectAsState()
    val ripple by vm.ripple.collectAsState()
    val snapping by vm.snapping.collectAsState()
    val multiSelect by vm.multiSelect.collectAsState()
    val dragFeedback by vm.dragFeedback.collectAsState()
    val dragBlocked by vm.dragBlocked.collectAsState()
    val previewLevel by vm.previewLevel.collectAsState()
    val export by vm.export.collectAsState()
    val agentActivity by vm.agentActivity.collectAsState()
    val chatState by chat.ui.collectAsState()
    val libraryIndex by AssistantRuntime.get(app).assets.changes.collectAsState()
    val libraryResults = remember(chatState.libraryResults, libraryIndex) { currentCutLibraryResults(chatState.libraryResults, libraryIndex) }
    var resultStage by remember(vm.projectId) { mutableStateOf<Pair<List<com.vidadomu.nexus.agent.core.tools.Attachment>, Int>?>(null) }
    var addingFromResults by remember(vm.projectId) { mutableStateOf(false) }

    var agentOpen by rememberSaveable(vm.projectId) { mutableStateOf(false) }
    var immersive by rememberSaveable(vm.projectId) { mutableStateOf(false) }
    var immersiveControls by remember { mutableStateOf(true) }
    // Negativo = reparto automático (la imagen recibe el alto que pide su proporción).
    var timelineFraction by rememberSaveable(vm.projectId) { mutableFloatStateOf(vm.savedTimelineFraction ?: AUTO_FRACTION) }
    var chatFraction by rememberSaveable(vm.projectId) { mutableFloatStateOf(.40f) }
    var previewScale by rememberSaveable(vm.projectId) { mutableFloatStateOf(1f) }
    var previewX by rememberSaveable(vm.projectId) { mutableFloatStateOf(0f) }
    var previewY by rememberSaveable(vm.projectId) { mutableFloatStateOf(0f) }
    var settingsOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<CutDialog?>(null) }
    var canvasWidthPx by remember { mutableIntStateOf(0) }
    var zoomBeforeFit by remember { mutableStateOf<Float?>(null) }
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val viewport = remember(vm) { TimelineViewport(vm.savedZoom ?: CutViewPrefs.DEFAULT_PX_PER_SECOND) }
    // Un montaje que aún no se ha visto abre con un zoom acorde a su duración (en cuanto se conoce el ancho).
    var zoomChosen by remember(vm) { mutableStateOf(vm.savedZoom != null) }
    val snackbar = remember { SnackbarHostState() }
    val media = remember(project.media) { project.media.associateBy { it.id } }
    val selectedClips = remember(timeline, selection) { selection.mapNotNull { timeline.clip(it) } }
    val exporting = (export as? ExportState.Running)?.takeIf { it.projectId == project.id }

    // Al irse a segundo plano: se para la imagen y se guarda dónde estaba (zoom y cabezal) para volver igual.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> vm.onBackground(viewport.pxPerSecond.takeIf { zoomChosen })
                Lifecycle.Event.ON_START -> vm.onForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.saveView(viewport.pxPerSecond.takeIf { zoomChosen })
        }
    }
    LaunchedEffect(canvasWidthPx, project.timeline.durationUs > 0) {
        if (!zoomChosen && canvasWidthPx > 0) defaultTimelineZoom(canvasWidthPx / density.density, project.timeline.durationUs)?.let {
            viewport.pxPerSecond = it
            zoomChosen = true
        }
    }
    // Reproduciendo, la pantalla no se apaga.
    val view = LocalView.current
    DisposableEffect(playing) {
        view.keepScreenOn = playing
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(chatState.libraryResultsOpen) {
        if (chatState.libraryResultsOpen) {
            addingFromResults = false
            vm.preview.pause()
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    fun addFoundAsset(found: com.vidadomu.nexus.agent.core.tools.Attachment) {
        if (addingFromResults) return
        val current = currentCutLibraryResults(listOf(found), AssistantRuntime.get(app).assets.snapshot()).singleOrNull()
        if (current == null) {
            android.widget.Toast.makeText(context, "El archivo ya no está disponible en la Biblioteca.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        addingFromResults = true
        chat.closeLibraryResults()
        resultStage = null
        vm.importAssets(listOf(requireNotNull(current.assetId)))
    }
    if (chatState.libraryResultsOpen && libraryResults.isNotEmpty()) com.vidadomu.nexus.assistant.ResultsWallSheet(
        libraryResults,
        onOpen = { selected ->
            resultStage = libraryResults to libraryResults.indexOfFirst { it.assetId == selected.assetId }.coerceAtLeast(0)
            chat.closeLibraryResults()
        },
        onDismiss = chat::closeLibraryResults,
        onAdd = ::addFoundAsset,
    )
    resultStage?.let { (results, position) ->
        com.vidadomu.nexus.assistant.ResultStage(results, position, onDismiss = { resultStage = null }, onAdd = ::addFoundAsset)
    }

    LaunchedEffect(vm) {
        vm.messages.collect { m ->
            if (m.error) haptics.perform(NexusHapticEvent.ERROR)
            snackbar.showSnackbar(m.text, duration = if (m.error) SnackbarDuration.Long else SnackbarDuration.Short)
        }
    }
    LaunchedEffect(chat) { chat.toasts.collect { snackbar.showSnackbar(it, duration = SnackbarDuration.Short) } }
    LaunchedEffect(export) {
        val e = export
        if (e is ExportState.Done && e.projectId == project.id) {
            haptics.perform(NexusHapticEvent.CONFIRMATION)
            val r = snackbar.showSnackbar(if (e.assetIds.size > 1) "${e.assetIds.size} clips exportados y guardados en la Biblioteca" else "Exportado y guardado en la Biblioteca · ${ExportMath.formatBytes(e.bytes)}", actionLabel = "Ver", duration = SnackbarDuration.Long)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) onOpenLibraryAsset(e.assetId)
            ExportStateAck.ack()
        } else if (e is ExportState.Failed && e.projectId == project.id) {
            haptics.perform(NexusHapticEvent.ERROR)
            snackbar.showSnackbar(e.message, duration = SnackbarDuration.Long); ExportStateAck.ack()
        }
    }

    // Lo último que hizo el agente se ve unos segundos en una píldora aunque su panel esté cerrado.
    var recentAgent by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(agentActivity) {
        val a = agentActivity ?: return@LaunchedEffect
        recentAgent = a
        delay(6_000)
        recentAgent = null
    }

    // Biblioteca de Nexus (con «Importar» del teléfono): los archivos vuelven como URIs del índice.
    val picker = rememberProjectMediaPicker(managementOnly = false, useLabel = "Añadir") { uris ->
        val index = AssistantRuntime.get(app).assets
        val ids = uris.mapNotNull { index.assetByUri(it.toString())?.id }
        if (ids.isEmpty()) return@rememberProjectMediaPicker
        vm.importAssets(ids)
    }

    BackHandler(enabled = immersive) { immersive = false }
    BackHandler(enabled = agentOpen && !immersive) {
        agentOpen = false
        focusManager.clearFocus(force = true); keyboard?.hide()
    }

    val actions = remember(vm, haptics) {
        object : TimelineActions {
            override fun seek(us: Long, scrubbing: Boolean) = vm.preview.seekTo(us, scrubbing)
            override fun scrubEnd() = vm.preview.endScrub()
            override fun tap(clipId: String?, trackId: String?) {
                vm.select(clipId)
                if (clipId != null) haptics.perform(NexusHapticEvent.SELECTION)
            }
            override fun pickUp(clipId: String): Boolean = vm.beginMove(clipId)
            override fun beginDrag(exclude: Set<String>) = vm.beginDrag(exclude)
            override fun trim(clipId: String, edge: Edge, deltaUs: Long, snapUs: Long) = vm.dragTrim(clipId, edge, deltaUs, snapUs)
            override fun move(clipId: String, deltaUs: Long, trackOffset: Int, snapUs: Long) = vm.dragMove(clipId, deltaUs, trackOffset, snapUs)
            override fun fade(clipId: String, fadeIn: Boolean, fadeUs: Long) = vm.dragFade(clipId, fadeIn, fadeUs)
            override fun endDrag(commit: Boolean) { vm.endDrag(commit); haptics.perform(NexusHapticEvent.GESTURE_END) }
        }
    }

    fun undo() {
        val label = st.undoLabel
        val byAgent = st.undoOrigin == Origin.AGENT
        haptics.perform(NexusHapticEvent.CANCEL_CONFIRMED)
        vm.undo()
        if (label != null) scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(if (byAgent) "Deshecho (agente): $label" else "Deshecho: $label", duration = SnackbarDuration.Short)
        }
    }
    fun redo() {
        val label = st.redoLabel
        haptics.perform(NexusHapticEvent.CONFIRMATION)
        vm.redo()
        if (label != null) scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar("Rehecho: $label", duration = SnackbarDuration.Short)
        }
    }
    fun openAgent() {
        haptics.perform(NexusHapticEvent.SELECTION)
        agentOpen = true
    }
    fun closeAgent() {
        agentOpen = false
        focusManager.clearFocus(force = true); keyboard?.hide()
    }
    fun fitTimeline() {
        // El cabezal va en el centro: basta con que quepa el lado más largo del montaje desde donde está.
        val fit = fitTimelineZoom(canvasWidthPx / density.density, timeline.durationUs, vm.preview.positionUs.value) ?: return
        val previous = zoomBeforeFit
        if (previous != null) {
            viewport.pxPerSecond = previous; zoomBeforeFit = null
        } else {
            zoomBeforeFit = viewport.pxPerSecond
            viewport.pxPerSecond = fit
        }
        zoomChosen = true
        haptics.perform(NexusHapticEvent.SELECTION)
    }
    fun stepFrame(delta: Int) {
        haptics.perform(NexusHapticEvent.GESTURE_TICK)
        vm.preview.pause()
        val now = playheadState.value
        vm.preview.seekTo(rate.timeOf(rate.frameAt(now) + delta).coerceIn(0L, maxOf(0L, timeline.durationUs)))
    }

    val enter = if (motion) fadeIn(tween(NexusTokens.contentDuration)) else EnterTransition.None
    val exit = if (motion) fadeOut(tween(NexusTokens.pressDuration)) else ExitTransition.None

    @Composable
    fun EditorHeader() {
        Row(Modifier.fillMaxWidth().height(CUT_HEADER_DP.dp).background(palette.bg).padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CutIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onClick = onBack)
            Column(Modifier.weight(1f).clip(NexusTokens.radiusSmall)
                .clickable(onClickLabel = "Ajustes del montaje") { haptics.perform(NexusHapticEvent.SELECTION); settingsOpen = true }
                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(project.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val clips = timeline.allTracks.sumOf { it.clips.size }
                Text("${cutAspectLabel(project.settings.width, project.settings.height)} · ${CutDragText.Clock.short(timeline.durationUs)} · $clips ${if (clips == 1) "clip" else "clips"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val agentUndo = st.undoOrigin == Origin.AGENT
            CutIconButton(Icons.AutoMirrored.Filled.Undo,
                st.undoLabel?.let { if (agentUndo) "Deshacer cambio del agente: $it" else "Deshacer: $it" } ?: "Deshacer",
                enabled = st.canUndo, badge = agentUndo && st.canUndo, onClick = ::undo)
            CutIconButton(Icons.AutoMirrored.Filled.Redo, st.redoLabel?.let { "Rehacer: $it" } ?: "Rehacer",
                enabled = st.canRedo, badge = st.redoOrigin == Origin.AGENT && st.canRedo, onClick = ::redo)
            CutIconButton(Icons.Default.Tune, "Ajustes del montaje") { haptics.perform(NexusHapticEvent.SELECTION); settingsOpen = true }
            val canExport = timeline.durationUs > 0 && export !is ExportState.Running
            Button(
                onClick = { haptics.perform(NexusHapticEvent.SELECTION); dialog = CutDialog.Export },
                enabled = canExport,
                shape = NexusTokens.pill,
                modifier = Modifier.padding(start = 2.dp, end = 6.dp).height(34.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) { Text("Exportar", style = MaterialTheme.typography.labelLarge) }
        }
    }

    @Composable
    fun ExportBar(r: ExportState.Running) {
        Row(Modifier.fillMaxWidth().height(CUT_EXPORT_BAR_DP.dp).background(palette.bg).padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text((if (r.total > 1) "Exportando ${r.item}/${r.total}" else "Exportando") + " · ${r.progress} %",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            LinearProgressIndicator(progress = { r.progress / 100f }, modifier = Modifier.weight(1f).height(3.dp).clip(NexusTokens.pill))
            IconButton(onClick = vm::cancelExport, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, "Cancelar exportación", Modifier.size(16.dp))
            }
        }
    }

    @Composable
    fun PreviewPane(modifier: Modifier, fullscreen: Boolean) {
        fun fit() { previewScale = 1f; previewX = 0f; previewY = 0f }
        Box(modifier.fillMaxWidth().clipToBounds().background(Color.Black)
            .semantics {
                contentDescription = "Previsualización del montaje"
                stateDescription = "Ampliación ${((previewScale * 100).toInt())} por ciento"
                customActions = listOf(
                    CustomAccessibilityAction("Ampliar previsualización") { previewScale = (previewScale * 1.5f).coerceAtMost(6f); true },
                    CustomAccessibilityAction("Ajustar previsualización") { fit(); true })
            }
            .pointerInput(fullscreen) {
                detectTapGestures(
                    onDoubleTap = { fit() },
                    onTap = { if (fullscreen) immersiveControls = !immersiveControls },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val transformed = CutPreviewTransform(previewScale, previewX, previewY).transform(
                        zoom, pan.x, pan.y, centroid.x - size.width / 2f, centroid.y - size.height / 2f,
                        size.width.toFloat(), size.height.toFloat())
                    previewScale = transformed.scale; previewX = transformed.x; previewY = transformed.y
                }
            }, contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(project.settings.width.toFloat() / project.settings.height, matchHeightConstraintsFirst = true)
                .graphicsLayer { scaleX = previewScale; scaleY = previewScale; translationX = previewX; translationY = previewY }) {
                CutPreviewSurface(vm.preview, Modifier.fillMaxSize())
                val frame = still
                if (showStill && frame != null && timeline.durationUs > 0) {
                    Image(frame.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                }
                if (timeline.durationUs <= 0) Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Montaje vacío", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text("Añade vídeos, imágenes o audio de la Biblioteca", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { picker.launch(arrayOf("video/*", "image/*", "audio/*")) }) { Text("Añadir de la Biblioteca") }
                }
                if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 2.dp, color = Color.White)
            }
            if (previewScale > 1.01f) TextButton(onClick = ::fit, modifier = Modifier.align(Alignment.TopEnd)) {
                Text("Ajustar", color = Color.White)
            }
            previewError?.let {
                Text(it, fontSize = 11.sp, color = Color.White, modifier = Modifier.align(Alignment.TopStart)
                    .background(Color(0xCC8B0000)).clickable { vm.preview.clearError() }.padding(6.dp))
            }
        }
    }

    @Composable
    fun PlayButton(sizeDp: Int) {
        Surface(
            onClick = { vm.preview.togglePlay(); haptics.perform(NexusHapticEvent.SELECTION) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(sizeDp.dp).semantics { contentDescription = if (playing) "Pausa" else "Reproducir"; role = Role.Button },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null,
                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size((sizeDp * .56f).dp))
            }
        }
    }

    /**
     * Transporte fino entre imagen y montaje. Arrastrarlo en vertical reparte el espacio entre las dos (la
     * raya de arriba lo indica); doble toque en un hueco vuelve al reparto por defecto.
     */
    @Composable
    fun Transport(resizable: Boolean, total: Float, effectiveFraction: Float = CutViewPrefs.DEFAULT_TIMELINE_FRACTION) {
        // En automático se parte del reparto que hay en pantalla: el primer milímetro de arrastre no da un salto.
        val effective by rememberUpdatedState(effectiveFraction)
        fun current() = if (timelineFraction < 0f) effective else timelineFraction
        fun save() { if (timelineFraction >= 0f) vm.setTimelineFraction(timelineFraction) }
        val resize = if (!resizable) Modifier else Modifier
            .pointerInput(total) {
                detectVerticalDragGestures(onDragEnd = ::save, onDragCancel = ::save) { change, amount ->
                    change.consume()
                    timelineFraction = CutViewPrefs.clampFraction(current() - amount / density.density / total.coerceAtLeast(1f))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    timelineFraction = AUTO_FRACTION
                    vm.setTimelineFraction(null)
                    haptics.perform(NexusHapticEvent.SELECTION)
                })
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Agrandar la línea de tiempo") { timelineFraction = CutViewPrefs.clampFraction(current() + .08f); save(); true },
                    CustomAccessibilityAction("Agrandar la imagen") { timelineFraction = CutViewPrefs.clampFraction(current() - .08f); save(); true },
                    CustomAccessibilityAction("Reparto automático") { timelineFraction = AUTO_FRACTION; vm.setTimelineFraction(null); true },
                )
            }
        Box(Modifier.fillMaxWidth().height(CUT_TRANSPORT_DP.dp).background(palette.panel).then(resize).padding(horizontal = 4.dp)) {
            if (resizable) Box(Modifier.align(Alignment.TopCenter).padding(top = 3.dp).size(width = 28.dp, height = 3.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = .55f), NexusTokens.pill))
            PlayheadClock(playheadState, timeline.durationUs, rate, Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                CutIconButton(Icons.Default.SkipPrevious, "Fotograma anterior", sizeDp = 40) { stepFrame(-1) }
                PlayButton(34)
                CutIconButton(Icons.Default.SkipNext, "Fotograma siguiente", sizeDp = 40) { stepFrame(1) }
            }
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                CutIconButton(Icons.Default.FitScreen, if (zoomBeforeFit != null) "Volver al zoom anterior" else "Ver todo el montaje",
                    sizeDp = 40, enabled = timeline.durationUs > 0, tint = if (zoomBeforeFit != null) MaterialTheme.colorScheme.primary else null) { fitTimeline() }
                CutIconButton(Icons.Default.Fullscreen, "Pantalla completa", sizeDp = 40) {
                    haptics.perform(NexusHapticEvent.SELECTION); immersiveControls = true; immersive = true
                }
            }
        }
    }

    @Composable
    fun TimelinePane(modifier: Modifier) {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val paneDp = maxHeight.value
            val rowScale = remember(paneDp, timeline.videoTracks, timeline.audioTracks) {
                val tracks = timeline.videoTracks + timeline.audioTracks
                // Las pistas vacías van bajas (EMPTY_ROW_DP) y no crecen: el alto sobrante es para las que tienen clips.
                val full = { t: Track -> !t.collapsed && t.clips.isNotEmpty() }
                timelineRowScale(paneDp, timeline.videoTracks.count(full), timeline.audioTracks.count(full), tracks.count { it.collapsed },
                    tracks.count { !it.collapsed && it.clips.isEmpty() })
            }
            Row(Modifier.fillMaxSize()) {
                TrackHeaders(
                    timeline, viewport, palette, rowScale,
                    onToggleLock = { t -> haptics.perform(NexusHapticEvent.TOGGLE_DECISIVE); vm.toggleTrack(t.id, locked = !t.locked) },
                    onToggleMute = { t -> haptics.perform(NexusHapticEvent.TOGGLE_DECISIVE); vm.toggleTrack(t.id, muted = !t.muted) },
                    onSelectTrack = { t -> haptics.perform(NexusHapticEvent.SELECTION); vm.selectAllOnTrack(t.id) },
                )
                TimelineCanvas(
                    timeline = timeline, selection = selection, playhead = playheadState, livePlayhead = vm.preview::livePositionUs,
                    playing = playing, frameRate = rate, media = media, offline = offline, waveforms = waveforms,
                    thumbnails = vm.thumbnails, thumbVersion = thumbVersion, snapLineUs = snapLine, dragFeedback = dragFeedback,
                    blocked = dragBlocked, snapping = snapping, viewport = viewport, actions = actions, palette = palette,
                    modifier = Modifier.weight(1f).fillMaxHeight().onSizeChanged { canvasWidthPx = it.width },
                    rowScale = rowScale,
                )
            }
            val chip = when {
                chatState.thinking -> "Agente · " + (chatState.liveSteps.lastOrNull() ?: chatState.activity.ifBlank { "pensando el cambio" })
                else -> recentAgent
            }
            AnimatedVisibility(!agentOpen && chip != null, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 6.dp),
                enter = enter, exit = exit) {
                AgentChip(chip.orEmpty(), working = chatState.thinking, motion = motion, onClick = ::openAgent)
            }
        }
    }

    @Composable
    fun AgentButton() {
        val working = chatState.thinking
        Box(Modifier.padding(start = 2.dp, end = 8.dp).size(48.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClickLabel = "Abrir el agente", role = Role.Button, onClick = ::openAgent)
            .semantics { contentDescription = if (working) "Agente trabajando" else "Agente del montaje" },
            contentAlignment = Alignment.Center) {
            if (working) {
                if (motion) CircularProgressIndicator(Modifier.size(44.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                else Box(Modifier.size(44.dp).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape))
            }
            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
    }

    /** Herramientas del contexto: sin selección, añadir; con ella, lo que se puede hacer con lo elegido. */
    @Composable
    fun Dock() {
        val canSplit by remember(timeline, selection) {
            derivedStateOf {
                val at = playheadState.value
                timeline.allTracks.any { t -> !t.locked && t.clips.any { c -> (selection.isEmpty() || c.id in selection) && at > c.timelineStartUs && at < c.endUs } }
            }
        }
        val single = selectedClips.singleOrNull()
        val playheadInside by remember(single) {
            derivedStateOf { single != null && playheadState.value > single.timelineStartUs && playheadState.value < single.endUs }
        }
        Row(Modifier.fillMaxWidth().height(CUT_DOCK_DP.dp).background(palette.bg), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = selectedClips,
                contentKey = { it.isEmpty() },
                transitionSpec = { enter togetherWith exit },
                modifier = Modifier.weight(1f),
                label = "cut-dock",
            ) { clips ->
                val dockScroll = rememberScrollState()
                Row(Modifier.fillMaxWidth().scrollEdgeFade(dockScroll, palette.bg).horizontalScroll(dockScroll).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (clips.isEmpty()) {
                        DockTool(Icons.Default.AddPhotoAlternate, "Añadir", haptics) { picker.launch(arrayOf("video/*", "image/*")) }
                        DockTool(Icons.Default.MusicNote, "Audio", haptics) { picker.launch(arrayOf("audio/*")) }
                        DockTool(Icons.Default.TextFields, "Texto", haptics) { dialog = CutDialog.Title(null) }
                        DockTool(Icons.Default.ContentCut, "Dividir", haptics, enabled = canSplit, event = NexusHapticEvent.CONFIRMATION) { vm.split() }
                        DockTool(Icons.Default.Checklist, "Varios", haptics, active = multiSelect, event = NexusHapticEvent.TOGGLE_DECISIVE) { vm.setMultiSelect(!multiSelect) }
                    } else {
                        DockTool(Icons.Default.Close, "Listo", haptics) { vm.select(null, false) }
                        val one = clips.singleOrNull()
                        DockTool(Icons.Default.ContentCut, "Dividir", haptics, enabled = canSplit, event = NexusHapticEvent.CONFIRMATION) { vm.split() }
                        if (one != null) {
                            DockTool(Icons.AutoMirrored.Filled.AlignHorizontalLeft, "Inicio aquí", haptics, enabled = playheadInside, event = NexusHapticEvent.CONFIRMATION) { vm.trimToPlayhead(one.id, Edge.HEAD) }
                            DockTool(Icons.AutoMirrored.Filled.AlignHorizontalRight, "Final aquí", haptics, enabled = playheadInside, event = NexusHapticEvent.CONFIRMATION) { vm.trimToPlayhead(one.id, Edge.TAIL) }
                        }
                        // Eliminar va entre lo primero: se usa mucho y no debe quedar fuera de la pantalla.
                        DockTool(Icons.Default.Delete, "Eliminar", haptics, event = NexusHapticEvent.CONFIRMATION, destructive = true) { vm.deleteSelection() }
                        if (one != null) {
                            if (one.kind != ClipKind.TEXT) DockTool(Icons.Default.Speed, "Velocidad", haptics) { dialog = CutDialog.Speed(one) }
                            if (one.kind == ClipKind.TEXT) DockTool(Icons.Default.TextFields, "Editar", haptics) { dialog = CutDialog.Title(one) }
                        }
                        // El volumen es del sonido: el de los clips de audio elegidos y el del audio enlazado a un vídeo.
                        val links = clips.mapNotNull { it.linkId }.toSet()
                        val audible = (clips.filter { it.kind == ClipKind.AUDIO } +
                            timeline.audioTracks.flatMap { it.clips }.filter { it.linkId != null && it.linkId in links }).distinctBy { it.id }
                        if (audible.isNotEmpty()) DockTool(Icons.AutoMirrored.Filled.VolumeUp, "Volumen", haptics) { dialog = CutDialog.Volume(audible) }
                        DockTool(Icons.Default.ContentCopy, "Duplicar", haptics, event = NexusHapticEvent.CONFIRMATION) { vm.duplicateSelection() }
                        if (clips.any { it.linkId != null }) DockTool(Icons.Default.LinkOff, "Separar", haptics, event = NexusHapticEvent.CONFIRMATION) { vm.unlinkSelection() }
                        DockTool(Icons.Default.Checklist, "Varios", haptics, active = multiSelect, event = NexusHapticEvent.TOGGLE_DECISIVE) { vm.setMultiSelect(!multiSelect) }
                    }
                }
            }
            AgentButton()
        }
    }

    @Composable
    fun Agent(modifier: Modifier, compact: Boolean, total: Float) {
        CutChatPanel(
            state = chatState,
            contextLabel = { cutAgentContext(selectedClips.size, playheadState) },
            compact = compact,
            motion = motion,
            onClose = ::closeAgent,
            onResize = { delta -> chatFraction = (chatFraction - delta / density.density / total.coerceAtLeast(1f)).coerceIn(.18f, .72f) },
            onDraft = chat::setDraft,
            onSend = {
                if (chat.send()) {
                    focusManager.clearFocus(force = true)
                    keyboard?.hide()
                }
            },
            onStop = chat::stop,
            onShowResults = chat::showLibraryResults,
            turnStatus = chat::turnStatus,
            onRevertTurn = { group, later -> haptics.perform(NexusHapticEvent.CANCEL_CONFIRMED); chat.revertTurn(group, later) },
            onRedoTurn = { group -> haptics.perform(NexusHapticEvent.CONFIRMATION); chat.redoTurn(group) },
            onApplyProposal = { p -> haptics.perform(NexusHapticEvent.CONFIRMATION); chat.applyProposal(p) },
            onDismissProposal = chat::dismissProposal,
            modifier = modifier,
        )
    }

    @Composable
    fun ImmersiveControls(modifier: Modifier) {
        Column(modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .72f))))
            .padding(horizontal = 12.dp).padding(top = 24.dp, bottom = 8.dp)) {
            ImmersiveScrubber(playheadState, timeline.durationUs, onSeek = { vm.preview.seekTo(it, true) }, onEnd = vm.preview::endScrub)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PlayheadClock(playheadState, timeline.durationUs, rate, Modifier.weight(1f), onDark = true)
                PlayButton(44)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    CutIconButton(Icons.Default.FullscreenExit, "Salir de pantalla completa", tint = Color.White) {
                        haptics.perform(NexusHapticEvent.SELECTION); immersive = false
                    }
                }
            }
        }
    }

    // A pantalla completa también la franja de la cámara es negra: nada compite con la imagen.
    Surface(Modifier.fillMaxSize(), color = if (immersive) Color.Black else palette.bg) {
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .windowInsetsPadding(com.vidadomu.nexus.ui.theme.NexusInsets.top)
                .windowInsetsPadding(com.vidadomu.nexus.ui.theme.NexusInsets.bottomEdge)
        ) {
            val wide = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val typing = WindowInsets.isImeVisible
            // Dentro de Column/Row no se ve el receptor de BoxWithConstraints: el alto se lee aquí.
            val totalHeightDp = maxHeight.value
            val totalWidthDp = maxWidth.value
            if (immersive) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    PreviewPane(Modifier.fillMaxSize(), fullscreen = true)
                    AnimatedVisibility(immersiveControls, modifier = Modifier.align(Alignment.BottomCenter), enter = enter, exit = exit) {
                        ImmersiveControls(Modifier)
                    }
                }
            } else if (wide) {
                // El teclado se lleva casi todo el alto. El agente no cambia de sitio en el árbol (si cambiara, el
                // campo enfocado se destruiría y el teclado se cerraría al abrirse): solo se recogen la cabecera y
                // la línea de tiempo, y la imagen sigue a la vista a su izquierda mientras se escribe.
                val writing = agentOpen && typing
                Column(Modifier.fillMaxSize()) {
                    if (!writing) {
                        EditorHeader()
                        exporting?.let { ExportBar(it) }
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.weight(1.1f).fillMaxHeight()) {
                            PreviewPane(Modifier.weight(1f), fullscreen = false)
                            if (!writing) Transport(resizable = false, total = 0f)
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            if (!writing) TimelinePane(Modifier.weight(1f))
                            if (agentOpen) Agent(Modifier.weight(1.25f), compact = writing, total = totalHeightDp)
                            else Dock()
                        }
                    }
                }
            } else {
                val chromeDp = CUT_HEADER_DP + (if (exporting != null) CUT_EXPORT_BAR_DP else 0) + CUT_TRANSPORT_DP +
                    (if (agentOpen || typing) 0 else CUT_DOCK_DP)
                val available = (totalHeightDp - chromeDp).coerceAtLeast(0f)
                val frameFitDp = totalWidthDp * project.settings.height / project.settings.width.coerceAtLeast(1)
                val panels = cutPanelSizes(available, timelineFraction.takeIf { it >= 0f }, chatFraction, agentOpen, typing, frameFitDp)
                val editing = panels.preview + panels.timeline
                Column(Modifier.fillMaxSize()) {
                    EditorHeader()
                    exporting?.let { ExportBar(it) }
                    PreviewPane(Modifier.height(panels.preview.dp), fullscreen = false)
                    Transport(resizable = true, total = editing, effectiveFraction = if (editing > 0f) panels.timeline / editing else CutViewPrefs.DEFAULT_TIMELINE_FRACTION)
                    TimelinePane(Modifier.height(panels.timeline.dp))
                    if (agentOpen) Agent(Modifier.height(panels.chat.dp), compact = typing, total = available)
                    else if (!typing) Dock()
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (immersive) 96.dp else (CUT_DOCK_DP + 8).dp)) { CutToast(it) }
        }
    }

    if (settingsOpen) CutSettingsSheet(
        project = project,
        ripple = ripple, snapping = snapping, previewLevel = previewLevel,
        onRipple = { haptics.perform(NexusHapticEvent.TOGGLE_DECISIVE); vm.setRipple(it) },
        onSnapping = { haptics.perform(NexusHapticEvent.TOGGLE_DECISIVE); vm.setSnapping(it) },
        onPreviewLevel = { haptics.perform(NexusHapticEvent.SELECTION); vm.setPreviewLevel(it) },
        onAspect = { haptics.perform(NexusHapticEvent.SELECTION); vm.setAspect(it) },
        onFrameRate = { haptics.perform(NexusHapticEvent.SELECTION); vm.setFrameRate(it) },
        onRename = { settingsOpen = false; dialog = CutDialog.Rename },
        onDismiss = { settingsOpen = false },
    )

    when (val d = dialog) {
        null -> Unit
        CutDialog.Rename -> CutTextDialog("Nombre del montaje", project.name, onDismiss = { dialog = null }) { vm.rename(it); dialog = null }
        is CutDialog.Title -> CutTextDialog(if (d.clip == null) "Texto en pantalla" else "Editar texto", d.clip?.text?.text ?: "", multiline = true, onDismiss = { dialog = null }) {
            if (it.isNotBlank()) { if (d.clip == null) vm.addTitle(it) else vm.updateTitle(d.clip.id, it) }
            dialog = null
        }
        is CutDialog.Speed -> CutChoiceDialog("Velocidad", CUT_SPEEDS, d.clip.speed.toDouble().let { v -> CUT_SPEEDS.minByOrNull { kotlin.math.abs(it.toDouble() - v) } },
            label = { "${it.replace('.', ',')}×" }, onDismiss = { dialog = null }) { vm.setSpeed(d.clip.id, it); dialog = null }
        is CutDialog.Volume -> CutVolumeDialog(d.clips, onDismiss = { dialog = null }, onMute = { vm.setMuted(d.clips.map { it.id }.toSet(), it); dialog = null }) { db -> vm.setVolume(d.clips.map { it.id }.toSet(), db); dialog = null }
        CutDialog.Export -> CutExportSheet(project, project.timeline.durationUs,
            clipCount = project.timeline.videoTracks.sumOf { track -> track.clips.count { it.kind == ClipKind.VIDEO || it.kind == ClipKind.IMAGE } },
            selectedCount = runCatching { com.vidadomu.nexus.cut.command.clipExportRanges(project, selection, false).size }.getOrDefault(0),
            onDismiss = { dialog = null }) { res, codec, mode ->
            haptics.perform(NexusHapticEvent.CONFIRMATION)
            vm.export(res, codec, eachClip = mode == 1, clipIds = if (mode == 2) selection else emptySet())
            dialog = null
        }
    }
}

/** Valor de «reparto automático» en el estado guardado (un Float no admite null en rememberSaveable). */
private const val AUTO_FRACTION = -1f

/** Aviso breve: una píldora compacta sobre el muelle, no una banda que tape el montaje. */
@Composable
private fun CutToast(data: SnackbarData) {
    val scheme = MaterialTheme.colorScheme
    val action = data.visuals.actionLabel
    Surface(shape = NexusTokens.pill, color = scheme.inverseSurface.copy(alpha = .95f), shadowElevation = 4.dp,
        modifier = Modifier.padding(horizontal = 20.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(Modifier.heightIn(min = 36.dp).padding(start = 14.dp, end = if (action != null) 2.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(data.visuals.message, style = MaterialTheme.typography.labelMedium, color = scheme.inverseOnSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(vertical = 6.dp))
            if (action != null) TextButton(onClick = data::performAction, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(36.dp)) {
                Text(action, color = scheme.inversePrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Degradado en los bordes de una fila desplazable: avisa de que hay más herramientas a ese lado. */
private fun Modifier.scrollEdgeFade(scroll: ScrollState, color: Color): Modifier = drawWithContent {
    drawContent()
    val w = 28.dp.toPx()
    if (scroll.canScrollBackward) drawRect(Brush.horizontalGradient(listOf(color, Color.Transparent), startX = 0f, endX = w), size = Size(w, size.height))
    if (scroll.canScrollForward) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, color), startX = size.width - w, endX = size.width),
        topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
}

internal val CUT_SPEEDS = listOf("0.25", "0.5", "0.75", "1", "1.5", "2", "3", "4")

private sealed interface CutDialog {
    data object Rename : CutDialog
    data object Export : CutDialog
    data class Title(val clip: Clip?) : CutDialog
    data class Speed(val clip: Clip) : CutDialog
    data class Volume(val clips: List<Clip>) : CutDialog
}

/** Marca el resultado de la exportación como visto, para no repetir el aviso al volver al editor. */
private object ExportStateAck { fun ack() = com.vidadomu.nexus.cut.export.ExportManager.acknowledge() }

/** «16:9», «9:16»… o el tamaño si no es una proporción conocida. */
internal fun cutAspectLabel(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "—"
    val r = width.toDouble() / height
    val known = listOf("16:9" to 16.0 / 9, "9:16" to 9.0 / 16, "1:1" to 1.0, "4:5" to 4.0 / 5, "4:3" to 4.0 / 3, "3:4" to 3.0 / 4, "21:9" to 21.0 / 9)
    return known.firstOrNull { kotlin.math.abs(it.second - r) < .01 }?.first ?: "${width}×$height"
}

/** Lo que el agente tiene delante: la selección y el instante del cabezal. */
internal fun cutAgentContext(selected: Int, playhead: State<Long>): String {
    val at = CutDragText.Clock.short(playhead.value)
    return when (selected) {
        0 -> "Todo el montaje · $at"
        1 -> "1 clip seleccionado · $at"
        else -> "$selected clips seleccionados · $at"
    }
}

/** Icono pequeño con área táctil completa (40–48 dp). */
@Composable
internal fun CutIconButton(
    icon: ImageVector, description: String, enabled: Boolean = true, sizeDp: Int = 44, tint: Color? = null,
    badge: Boolean = false, onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(sizeDp.dp)) {
        Box {
            Icon(icon, description, Modifier.size(20.dp), tint = if (enabled && tint != null) tint else LocalContentColor.current)
            if (badge) Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

/** Herramienta del muelle: icono discreto, etiqueta breve y una superficie de toque de 56 × 58 dp. */
@Composable
private fun DockTool(
    icon: ImageVector, label: String, haptics: com.vidadomu.nexus.ui.theme.NexusHaptics, enabled: Boolean = true,
    active: Boolean = false, destructive: Boolean = false, event: NexusHapticEvent = NexusHapticEvent.SELECTION, onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = when {
        !enabled -> scheme.onSurface.copy(alpha = .32f)
        active -> scheme.primary
        destructive -> scheme.error
        else -> scheme.onSurface
    }
    Column(
        Modifier.widthIn(min = 56.dp).height(CUT_DOCK_DP.dp).clip(NexusTokens.radiusSmall)
            .clickable(enabled = enabled, role = Role.Button) { haptics.perform(event); onClick() }
            .semantics { if (active) stateDescription = "Activado" }
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(width = 36.dp, height = 26.dp).background(if (active) scheme.primary.copy(alpha = .16f) else Color.Transparent, NexusTokens.pill),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, lineHeight = 12.sp, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Píldora con lo que hace (o acaba de hacer) el agente cuando su panel está cerrado. */
@Composable
private fun AgentChip(text: String, working: Boolean, motion: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = NexusTokens.pill, color = scheme.surfaceContainerHigh.copy(alpha = .95f),
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = .4f)), shadowElevation = 3.dp,
        modifier = Modifier.semantics { contentDescription = "$text. Abrir el agente" }) {
        Row(Modifier.padding(start = 10.dp, end = 12.dp).height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            if (working && motion) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = scheme.primary)
            else Icon(Icons.Default.AutoAwesome, null, Modifier.size(14.dp), tint = scheme.primary)
            Spacer(Modifier.width(7.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = scheme.onSurface, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp))
        }
    }
}

/** Reloj del cabezal «mm:ss·ff / mm:ss». Lee el cabezal aquí dentro: solo se recompone este texto. */
@Composable
private fun PlayheadClock(playhead: State<Long>, durationUs: Long, rate: FrameRate, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val us = playhead.value
    val main = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val dim = if (onDark) Color.White.copy(alpha = .7f) else MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")
    Row(modifier.semantics(mergeDescendants = true) {
        contentDescription = "Cabezal en ${CutDragText.Clock.short(us)}, fotograma ${CutDragText.Clock.frameInSecond(us, rate)}, de ${CutDragText.Clock.short(durationUs)}"
    }, verticalAlignment = Alignment.CenterVertically) {
        Text(CutDragText.Clock.short(us), style = style, fontWeight = FontWeight.SemiBold, color = main)
        Text("·" + CutDragText.Clock.frameInSecond(us, rate), style = style.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.primary)
        Text(" / " + CutDragText.Clock.short(durationUs), style = style.copy(fontSize = 10.sp), color = dim)
    }
}

@Composable
private fun ImmersiveScrubber(playhead: State<Long>, durationUs: Long, onSeek: (Long) -> Unit, onEnd: () -> Unit) {
    val d = durationUs.coerceAtLeast(1L)
    Slider(
        value = (playhead.value.toFloat() / d).coerceIn(0f, 1f),
        onValueChange = { onSeek((it * d).toLong()) },
        onValueChangeFinished = onEnd,
        enabled = durationUs > 0,
        modifier = Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription = "Posición en el montaje" },
    )
}

/**
 * Cabeceras de pista alineadas con las filas del lienzo. Tocar abre las opciones de la pista (bloquear,
 * silenciar, seleccionarla entera); mantener pulsado la selecciona de una vez. El estado se ve siempre.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackHeaders(
    timeline: Timeline, viewport: TimelineViewport, palette: CutPalette, rowScale: Float,
    onToggleLock: (Track) -> Unit, onToggleMute: (Track) -> Unit, onSelectTrack: (Track) -> Unit,
) {
    Box(Modifier.width(CUT_TRACK_HEADER_DP.dp).fillMaxHeight().background(palette.panel).clipToBounds()) {
        Column(Modifier.offset { IntOffset(0, (RULER_DP.dp.toPx() - viewport.scrollY).roundToInt()) }) {
            for (t in timeline.videoTracks.asReversed() + timeline.audioTracks) {
                // Misma cuenta que el lienzo (cutRowPx): las cabeceras quedan alineadas al píxel con las filas.
                val h = with(LocalDensity.current) { cutRowPx(t, density, rowScale).toDp() }
                var menu by remember(t.id) { mutableStateOf(false) }
                val state = listOfNotNull(if (t.locked) "bloqueada" else null, if (t.muted) "en silencio" else null).joinToString(", ")
                Box(Modifier.height(h).fillMaxWidth()
                    .combinedClickable(
                        onClickLabel = "Opciones de la pista", onLongClickLabel = "Seleccionar toda la pista",
                        onClick = { menu = true }, onLongClick = { onSelectTrack(t) })
                    .semantics(mergeDescendants = true) { contentDescription = t.name + if (state.isNotEmpty()) ", $state" else "" },
                    contentAlignment = Alignment.CenterStart) {
                    Column(Modifier.padding(start = 9.dp)) {
                        Text(t.name.substringBefore(' '), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = if (t.locked || t.muted || t.clips.isEmpty()) palette.textDim else palette.text, maxLines = 1)
                        if (!t.collapsed && t.clips.isNotEmpty() && (t.locked || t.muted)) Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (t.locked) Icon(Icons.Default.Lock, null, Modifier.size(12.dp), tint = palette.accent)
                            if (t.muted) Icon(Icons.AutoMirrored.Filled.VolumeOff, null, Modifier.size(12.dp), tint = palette.accent)
                        }
                    }
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(palette.line))
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(if (t.locked) "Desbloquear pista" else "Bloquear pista") },
                            leadingIcon = { Icon(if (t.locked) Icons.Default.LockOpen else Icons.Default.Lock, null) },
                            onClick = { menu = false; onToggleLock(t) })
                        DropdownMenuItem(text = { Text(if (t.muted) "Activar sonido" else "Silenciar pista") },
                            leadingIcon = { Icon(if (t.muted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, null) },
                            onClick = { menu = false; onToggleMute(t) })
                        DropdownMenuItem(text = { Text("Seleccionar toda la pista") }, enabled = t.clips.isNotEmpty(),
                            leadingIcon = { Icon(Icons.Default.SelectAll, null) },
                            onClick = { menu = false; onSelectTrack(t) })
                    }
                }
            }
        }
    }
}
