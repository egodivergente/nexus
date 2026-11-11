package com.vidadomu.nexus.ui.cut

import android.app.Application
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.assistant.rememberProjectMediaPicker
import com.vidadomu.nexus.cut.export.ExportMath
import com.vidadomu.nexus.cut.export.ExportState
import com.vidadomu.nexus.cut.model.Clip
import com.vidadomu.nexus.cut.model.ClipKind
import com.vidadomu.nexus.cut.model.ExportResolution
import com.vidadomu.nexus.cut.model.Timecode
import com.vidadomu.nexus.cut.model.Track
import com.vidadomu.nexus.cut.model.TrackKind
import com.vidadomu.nexus.cut.model.VideoCodec
import com.vidadomu.nexus.cut.timeline.Edge
import com.vidadomu.nexus.ui.theme.NexusHapticEvent
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.rememberNexusHaptics

/**
 * Nexus Cut: espacio inmersivo de montaje. Arriba, la imagen; en medio, la línea de tiempo; abajo, las
 * herramientas del elemento seleccionado. Todo lo que se toca pasa por el bus del montaje, el mismo que usa
 * el agente, así que sus cambios aparecen aquí al instante y comparten «Deshacer».
 */
@UnstableApi
@Composable
fun CutEditorScreen(projectId: String, onBack: () -> Unit, onOpenLibraryAsset: (String) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: CutEditorViewModel = viewModel(key = "cut-$projectId", factory = CutEditorViewModel.Factory(app, projectId))
    val state by vm.state.collectAsState()
    val loadError by vm.loadError.collectAsState()
    BackHandler(onBack = onBack)
    val st = state
    if (st == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            if (loadError != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(loadError!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                TextButton(onClick = onBack) { Text("Volver") }
            } else CircularProgressIndicator()
        }
        return
    }
    CutEditor(vm, onBack, onOpenLibraryAsset)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
private fun CutEditor(vm: CutEditorViewModel, onBack: () -> Unit, onOpenLibraryAsset: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val haptics = rememberNexusHaptics()
    val palette = rememberCutPalette()
    val st = vm.state.collectAsState().value ?: return
    val project = st.project
    val drag by vm.dragTimeline.collectAsState()
    val timeline = drag ?: project.timeline
    val selection = st.view.selection
    val playhead by vm.preview.positionUs.collectAsState()
    val playing by vm.preview.playing.collectAsState()
    val buffering by vm.preview.buffering.collectAsState()
    val still by vm.preview.still.collectAsState()
    val showStill by vm.preview.showStill.collectAsState()
    val previewError by vm.preview.error.collectAsState()
    val offline by vm.offline.collectAsState()
    val waveforms by vm.waveforms.collectAsState()
    val thumbVersion by vm.thumbnails.version.collectAsState()
    val snapLine by vm.snapLineUs.collectAsState()
    val ripple by vm.ripple.collectAsState()
    val snapping by vm.snapping.collectAsState()
    val export by vm.export.collectAsState()
    val viewport = remember { TimelineViewport() }
    val snackbar = remember { SnackbarHostState() }
    val media = remember(project.media) { project.media.associateBy { it.id } }
    val selectedClips = remember(timeline, selection) { selection.mapNotNull { timeline.clip(it) } }

    var dialog by remember { mutableStateOf<CutDialog?>(null) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { m -> snackbar.showSnackbar(m.text, duration = if (m.error) SnackbarDuration.Long else SnackbarDuration.Short) } }
    LaunchedEffect(export) {
        val e = export
        if (e is ExportState.Done && e.projectId == project.id) {
            val r = snackbar.showSnackbar("Exportado y guardado en la Biblioteca · ${ExportMath.formatBytes(e.bytes)}", actionLabel = "Ver", duration = SnackbarDuration.Long)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) onOpenLibraryAsset(e.assetId)
            ExportStateAck.ack()
        } else if (e is ExportState.Failed && e.projectId == project.id) {
            snackbar.showSnackbar(e.message, duration = SnackbarDuration.Long); ExportStateAck.ack()
        }
    }

    // Biblioteca de Nexus (con «Importar» del teléfono): los archivos vuelven como URIs del índice.
    val picker = rememberProjectMediaPicker { uris ->
        val index = AssistantRuntime.get(app).assets
        val ids = uris.mapNotNull { index.assetByUri(it.toString())?.id }
        if (ids.isEmpty()) return@rememberProjectMediaPicker
        vm.importAssets(ids)
    }

    Surface(Modifier.fillMaxSize(), color = palette.bg) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
            val wide = maxWidth > maxHeight
            val previewMax = if (wide) maxHeight * 0.5f else maxHeight * 0.4f
            Column(Modifier.fillMaxSize()) {
                // ------------------------------------------------------------------ barra superior
                Row(Modifier.fillMaxWidth().height(NexusTokens.topBarHeight).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                    Column(Modifier.weight(1f).clickable { dialog = CutDialog.Rename }) {
                        Text(project.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${project.settings.width}×${project.settings.height} · ${project.settings.frameRate} fps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.undo(); haptics.perform(NexusHapticEvent.SELECTION) }, enabled = st.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, st.undoLabel?.let { "Deshacer: $it" } ?: "Deshacer") }
                    IconButton(onClick = { vm.redo(); haptics.perform(NexusHapticEvent.SELECTION) }, enabled = st.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, st.redoLabel?.let { "Rehacer: $it" } ?: "Rehacer") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Ripple: lo posterior acompaña") }, trailingIcon = { Switch(ripple, null) }, onClick = { vm.ripple.value = !ripple })
                            DropdownMenuItem(text = { Text("Imán en cortes y marcadores") }, trailingIcon = { Switch(snapping, null) }, onClick = { vm.snapping.value = !snapping })
                            DropdownMenuItem(text = { Text("Formato del montaje") }, leadingIcon = { Icon(Icons.Default.AspectRatio, null) }, onClick = { menu = false; dialog = CutDialog.Format })
                            DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menu = false; dialog = CutDialog.Rename })
                        }
                    }
                    Button(onClick = { dialog = CutDialog.Export }, enabled = timeline.durationUs > 0 && export !is ExportState.Running,
                        modifier = Modifier.padding(start = 4.dp, end = 8.dp).height(36.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)) {
                        Icon(Icons.Default.Upload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Exportar")
                    }
                }
                (export as? ExportState.Running)?.takeIf { it.projectId == project.id }?.let { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(progress = { r.progress / 100f }, modifier = Modifier.weight(1f))
                        Text("  ${r.stage} · ${r.progress} %", style = MaterialTheme.typography.labelSmall)
                        IconButton(onClick = vm::cancelExport, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Cancelar exportación", Modifier.size(18.dp)) }
                    }
                }

                // ------------------------------------------------------------------ imagen
                Box(Modifier.fillMaxWidth().heightIn(max = previewMax).background(Color.Black), contentAlignment = Alignment.Center) {
                    Box(Modifier.aspectRatio(project.settings.width.toFloat() / project.settings.height, matchHeightConstraintsFirst = true)) {
                        AndroidView(factory = { ctx ->
                            SurfaceView(ctx).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) = Unit
                                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = vm.preview.attachSurface(holder.surface, width, height)
                                    override fun surfaceDestroyed(holder: SurfaceHolder) = vm.preview.detachSurface()
                                })
                            }
                        }, modifier = Modifier.fillMaxSize())
                        val frame = still
                        if (showStill && frame != null && timeline.durationUs > 0) {
                            Image(frame.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                        }
                        if (timeline.durationUs <= 0) Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Montaje vacío", color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Text("Añade vídeos o imágenes de la Biblioteca", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { picker.launch(arrayOf("video/*", "image/*", "audio/*")) }) { Text("Añadir de la Biblioteca") }
                        }
                        if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 2.dp, color = Color.White)
                    }
                    previewError?.let {
                        Text(it, fontSize = 11.sp, color = Color.White, modifier = Modifier.align(Alignment.TopStart).background(Color(0xCC8B0000)).padding(6.dp).clickable { vm.preview.clearError() })
                    }
                }

                // ------------------------------------------------------------------ transporte
                Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Timecode.formatFrames(playhead, project.settings.frameRate), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(" / " + Timecode.formatFrames(timeline.durationUs, project.settings.frameRate), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    val rate = project.settings.frameRate
                    IconButton(onClick = { vm.preview.seekTo(rate.timeOf(rate.frameAt(playhead) - 1).coerceAtLeast(0)) }) { Icon(Icons.Default.SkipPrevious, "Fotograma anterior") }
                    Surface(onClick = { vm.preview.togglePlay(); haptics.perform(NexusHapticEvent.SELECTION) }, shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pausa" else "Reproducir", tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                    IconButton(onClick = { vm.preview.seekTo(rate.timeOf(rate.frameAt(playhead) + 1)) }) { Icon(Icons.Default.SkipNext, "Fotograma siguiente") }
                    Spacer(Modifier.weight(1f))
                    Text(if (selectedClips.isEmpty()) "${timeline.allTracks.sumOf { it.clips.size }} clips" else "${selectedClips.size} seleccionado${if (selectedClips.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // ------------------------------------------------------------------ línea de tiempo
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    TrackHeaders(timeline, viewport.scrollY, palette, onToggleLock = { t -> vm.toggleTrack(t.id, locked = !t.locked) }, onToggleMute = { t -> vm.toggleTrack(t.id, muted = !t.muted) })
                    TimelineCanvas(
                        timeline = timeline, selection = selection, playheadUs = playhead, frameRate = project.settings.frameRate, media = media,
                        offline = offline, waveforms = waveforms, thumbnails = vm.thumbnails, thumbVersion = thumbVersion, snapLineUs = snapLine,
                        viewport = viewport, palette = palette, modifier = Modifier.weight(1f).fillMaxHeight(),
                        actions = object : TimelineActions {
                            override fun seek(us: Long, scrubbing: Boolean) = vm.preview.seekTo(us, scrubbing)
                            override fun scrubEnd() = vm.preview.seekTo(vm.preview.positionUs.value)
                            override fun tap(clipId: String?, trackId: String?, additive: Boolean) { vm.select(clipId, additive); if (clipId != null) haptics.perform(NexusHapticEvent.SELECTION) }
                            override fun beginDrag(exclude: Set<String>) { vm.beginDrag(exclude); haptics.perform(NexusHapticEvent.GESTURE_START) }
                            override fun trim(clipId: String, edge: Edge, deltaUs: Long, snapUs: Long) = vm.dragTrim(clipId, edge, deltaUs, snapUs)
                            override fun move(clipId: String, deltaUs: Long, trackOffset: Int, snapUs: Long) = vm.dragMove(clipId, deltaUs, trackOffset, snapUs)
                            override fun fade(clipId: String, fadeIn: Boolean, fadeUs: Long) = vm.dragFade(clipId, fadeIn, fadeUs)
                            override fun endDrag(commit: Boolean) { vm.endDrag(commit); haptics.perform(NexusHapticEvent.GESTURE_END) }
                        },
                    )
                }

                // ------------------------------------------------------------------ herramientas
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (selectedClips.isEmpty()) {
                            Tool(Icons.Default.AddPhotoAlternate, "Añadir") { picker.launch(arrayOf("video/*", "image/*")) }
                            Tool(Icons.Default.MusicNote, "Audio") { picker.launch(arrayOf("audio/*")) }
                            Tool(Icons.Default.TextFields, "Texto") { dialog = CutDialog.Title(null) }
                            Tool(Icons.Default.ContentCut, "Dividir", enabled = timeline.durationUs > 0) { vm.split(); haptics.perform(NexusHapticEvent.CONFIRMATION) }
                            Tool(Icons.Default.AspectRatio, "Formato") { dialog = CutDialog.Format }
                        } else {
                            val one = selectedClips.singleOrNull()
                            Tool(Icons.Default.ContentCut, "Dividir") { vm.split(); haptics.perform(NexusHapticEvent.CONFIRMATION) }
                            Tool(Icons.Default.Delete, "Eliminar") { vm.deleteSelection(); haptics.perform(NexusHapticEvent.CONFIRMATION) }
                            Tool(Icons.Default.ContentCopy, "Duplicar") { vm.duplicateSelection() }
                            if (one != null && one.kind != ClipKind.TEXT) Tool(Icons.Default.Speed, "Velocidad") { dialog = CutDialog.Speed(one) }
                            val audible = selectedClips.filter { it.kind == ClipKind.AUDIO }
                            if (audible.isNotEmpty()) Tool(Icons.AutoMirrored.Filled.VolumeUp, "Volumen") { dialog = CutDialog.Volume(audible) }
                            if (selectedClips.any { it.linkId != null }) Tool(Icons.Default.LinkOff, "Separar audio") { vm.unlinkSelection() }
                            if (one?.kind == ClipKind.TEXT) Tool(Icons.Default.TextFields, "Editar texto") { dialog = CutDialog.Title(one) }
                            Tool(Icons.Default.Close, "Deseleccionar") { vm.select(null, false) }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
        }
    }

    when (val d = dialog) {
        null -> Unit
        CutDialog.Rename -> TextDialog("Nombre del montaje", project.name, onDismiss = { dialog = null }) { vm.rename(it); dialog = null }
        is CutDialog.Title -> TextDialog(if (d.clip == null) "Texto en pantalla" else "Editar texto", d.clip?.text?.text ?: "", multiline = true, onDismiss = { dialog = null }) {
            if (it.isNotBlank()) { if (d.clip == null) vm.addTitle(it) else vm.updateTitle(d.clip.id, it) }
            dialog = null
        }
        is CutDialog.Speed -> ChoiceDialog("Velocidad", listOf("0.25", "0.5", "0.75", "1", "1.5", "2", "3", "4"), d.clip.speed.toDouble().let { v -> listOf("0.25", "0.5", "0.75", "1", "1.5", "2", "3", "4").minByOrNull { kotlin.math.abs(it.toDouble() - v) } },
            label = { "${it}×" }, onDismiss = { dialog = null }) { vm.setSpeed(d.clip.id, it); dialog = null }
        is CutDialog.Volume -> VolumeDialog(d.clips, onDismiss = { dialog = null }, onMute = { vm.setMuted(d.clips.map { it.id }.toSet(), it); dialog = null }) { db -> vm.setVolume(d.clips.map { it.id }.toSet(), db); dialog = null }
        CutDialog.Format -> ChoiceDialog("Formato", listOf("16:9", "9:16", "1:1", "4:5", "4:3", "21:9"), null, label = { it }, onDismiss = { dialog = null }) { vm.setAspect(it); dialog = null }
        CutDialog.Export -> ExportSheet(project.export.resolution, project.export.codec, project.timeline.durationUs, onDismiss = { dialog = null }) { res, codec ->
            vm.run("project.set_export", "resolution" to res.label, "codec" to codec.name.lowercase(), quiet = true) { vm.export() }
            dialog = null
        }
    }
}

private sealed interface CutDialog {
    data object Rename : CutDialog
    data object Format : CutDialog
    data object Export : CutDialog
    data class Title(val clip: Clip?) : CutDialog
    data class Speed(val clip: Clip) : CutDialog
    data class Volume(val clips: List<Clip>) : CutDialog
}

/** Marca el resultado de la exportación como visto, para no repetir el aviso al volver al editor. */
private object ExportStateAck { fun ack() = com.vidadomu.nexus.cut.export.ExportManager.acknowledge() }

@Composable
private fun Tool(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier.clip(NexusTokens.radiusSmall).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp).width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Cabeceras de pista alineadas con las filas del lienzo: nombre, bloqueo y silencio. */
@Composable
private fun TrackHeaders(timeline: com.vidadomu.nexus.cut.model.Timeline, scrollY: Float, palette: CutPalette, onToggleLock: (Track) -> Unit, onToggleMute: (Track) -> Unit) {
    val density = LocalDensity.current
    Box(Modifier.width(52.dp).fillMaxHeight().background(palette.panel).clip(androidx.compose.ui.graphics.RectangleShape)) {
        Column(Modifier.offset { IntOffset(0, (with(density) { RULER_DP.dp.toPx() } - scrollY).toInt()) }) {
            for (t in timeline.videoTracks.asReversed() + timeline.audioTracks) {
                val h = if (t.collapsed) COLLAPSED_ROW_DP else if (t.kind == TrackKind.VIDEO) VIDEO_ROW_DP else AUDIO_ROW_DP
                Column(Modifier.height(h.dp).fillMaxWidth().padding(start = 4.dp), verticalArrangement = Arrangement.Center) {
                    Text(t.name.substringBefore(' '), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = palette.textDim, maxLines = 1)
                    Row {
                        Icon(if (t.locked) Icons.Default.Lock else Icons.Default.LockOpen, if (t.locked) "Desbloquear ${t.name}" else "Bloquear ${t.name}",
                            Modifier.size(20.dp).clickable { onToggleLock(t) }.padding(2.dp), tint = if (t.locked) palette.accent else palette.textDim)
                        Icon(if (t.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, if (t.muted) "Activar ${t.name}" else "Silenciar ${t.name}",
                            Modifier.size(20.dp).clickable { onToggleMute(t) }.padding(2.dp), tint = if (t.muted) palette.accent else palette.textDim)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextDialog(title: String, initial: String, multiline: Boolean = false, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = !multiline, minLines = if (multiline) 2 else 1, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceDialog(title: String, options: List<String>, current: String?, label: (String) -> String, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for (o in options) FilterChip(selected = o == current, onClick = { onChoose(o) }, label = { Text(label(o)) }) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable
private fun VolumeDialog(clips: List<Clip>, onDismiss: () -> Unit, onMute: (Boolean) -> Unit, onConfirm: (Double) -> Unit) {
    var db by remember { mutableFloatStateOf(clips.first().audio.gainDb.toFloat().coerceIn(-30f, 12f)) }
    val muted = clips.all { it.audio.muted }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Volumen") },
        text = { Column {
            Text(if (db == 0f) "Original" else "%+.1f dB".format(db), style = MaterialTheme.typography.titleMedium)
            Slider(db, { db = (Math.round(it * 2) / 2f) }, valueRange = -30f..12f)
            TextButton(onClick = { onMute(!muted) }) { Text(if (muted) "Quitar silencio" else "Silenciar") }
        } },
        confirmButton = { TextButton(onClick = { onConfirm(db.toDouble()) }) { Text("Aplicar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExportSheet(resolution: ExportResolution, codec: VideoCodec, durationUs: Long, onDismiss: () -> Unit, onExport: (ExportResolution, VideoCodec) -> Unit) {
    var res by remember { mutableStateOf(resolution) }
    var cod by remember { mutableStateOf(codec) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Exportar montaje", style = MaterialTheme.typography.titleMedium)
            Text("Duración ${Timecode.formatMillis(durationUs)} · el vídeo se guardará en la Biblioteca del proyecto.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text("Resolución", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for (r in ExportResolution.entries) FilterChip(r == res, { res = r }, { Text(r.label) }) }
            Spacer(Modifier.height(8.dp))
            Text("Códec", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(cod == VideoCodec.H264, { cod = VideoCodec.H264 }, { Text("H.264 · compatible") })
                FilterChip(cod == VideoCodec.H265, { cod = VideoCodec.H265 }, { Text("H.265 · más ligero") })
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { onExport(res, cod) }, modifier = Modifier.fillMaxWidth()) { Text("Exportar") }
        }
    }
}
