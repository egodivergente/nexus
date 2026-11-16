package com.vidadomu.nexus.ui.flow
import com.vidadomu.nexus.agent.core.assets.storyCommand
import com.vidadomu.nexus.agent.core.assets.storyContext
import com.vidadomu.nexus.assistant.story.StorySelectionStore
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import com.vidadomu.nexus.ui.components.NexusSettingsButton
import com.vidadomu.nexus.ui.components.StudioChrome
import com.vidadomu.nexus.ui.components.StudioTactileCard
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.theme.NexusInsets
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.ui.theme.NexusHapticEvent
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.rememberNexusHaptics
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ViewPreset(val label: String, val mode: String, val width: Int)
private data class QualityPreset(val label: String, val quality: Int, val fps: Int, val maxPx: Int)

private val VIEWS = listOf(
    ViewPreset("Escritorio amplio", "desktop", 1280),
    ViewPreset("Escritorio", "desktop", 1024),
    ViewPreset("Compacta", "desktop", 800),
    ViewPreset("Móvil", "mobile", 0),
)
private val QUALITIES = listOf(
    QualityPreset("Ahorro de datos", 38, 3, 640),
    QualityPreset("Normal", 45, 6, 820),
    QualityPreset("Alta", 70, 12, 1200),
)

/**
 * Pestaña Flow: tu Google Flow con sesión, en directo desde el Edge de AXELPC. Tocas, desplazas y escribes como en la web;
 * lo que descargues allí entra solo en la Biblioteca.
 */
@Composable
fun FlowLiveScreen(onOpenLibrary: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val storySelection by StorySelectionStore.selection.collectAsState()
    val storyIndex = remember { com.vidadomu.nexus.assistant.AssistantRuntime.get(app).assets }
    val storyData by storyIndex.changes.collectAsState()
    val storyShot = storyData.shots.firstOrNull { it.id == storySelection?.shotId && it.projectId == storySelection?.projectId }
    var linkImported by remember { mutableStateOf<FlowImported?>(null) }
    val client = remember { FlowLiveClient(app) }
    val haptic = rememberNexusHaptics()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> client.start()
                Lifecycle.Event.ON_STOP -> client.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) client.start()
        onDispose { lifecycle.removeObserver(observer); client.stop() }
    }

    val phase by client.phase.collectAsState()
    val error by client.error.collectAsState()
    val frame by client.frame.collectAsState()
    val page by client.page.collectAsState()
    val download by client.download.collectAsState()
    val editable by client.editable.collectAsState()
    val order by client.order.collectAsState()

    var viewIndex by rememberSaveable { mutableStateOf(FlowLivePrefs.string(context, "view", "1").toIntOrNull()?.coerceIn(0, VIEWS.lastIndex) ?: 1) }
    var qualityIndex by rememberSaveable { mutableStateOf(FlowLivePrefs.string(context, "quality", "1").toIntOrNull()?.coerceIn(0, QUALITIES.lastIndex) ?: 1) }
    var menu by remember { mutableStateOf(false) }
    var serverDialog by remember { mutableStateOf(false) }
    var keyboard by rememberSaveable { mutableStateOf(true) }
    val recent = remember { mutableStateListOf<FlowImported>() }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { FlowLiveImporter.imported.collect { recent.add(0, it); haptic.perform(NexusHapticEvent.CONFIRMATION) } }
    LaunchedEffect(Unit) { FlowLiveImporter.failures.collect { failure = it } }
    LaunchedEffect(editable) { if (editable) keyboard = true }

    // Vista local: zoom con dos dedos sobre el fotograma (no cambia la página).
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val img = frame
    val rect = remember(box, img?.width, img?.height) {
        FlowLiveGeometry.fit(box.width.toFloat(), box.height.toFloat(), img?.width ?: box.width, img?.height ?: box.height)
    }
    val preset = VIEWS[viewIndex]
    val quality = QUALITIES[qualityIndex]
    LaunchedEffect(box, viewIndex, qualityIndex, phase) {
        if (phase == FlowLiveClient.Phase.LIVE && box.width > 0 && box.height > 0) {
            delay(200)
            val wDp = box.width / density.density
            val hDp = box.height / density.density
            client.view(wDp, hDp, density.density, preset.mode, FlowLiveGeometry.pageWidth(preset.mode, preset.width, wDp),
                quality.quality, quality.fps, quality.maxPx)
        }
    }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    fun cssPerPx() = FlowLiveGeometry.cssPerPixel(page.cssWidth.takeIf { it > 0 } ?: FlowLiveGeometry.pageWidth(preset.mode, preset.width, box.width / density.density), rect, zoom)
    fun toPage(p: Offset) = FlowLiveGeometry.normalize(p.x, p.y, rect, zoom, panX, panY)

    Column(Modifier.fillMaxSize()) {
        // Barra superior: estado, navegación de la página y menú, respetando recorte físico y fondo Studio.
        StudioChrome(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(NexusInsets.top)
                    .padding(start = NexusTokens.space12, end = NexusTokens.space8, top = NexusTokens.space4, bottom = NexusTokens.space8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)
            ) {
                val dot = when (phase) {
                    FlowLiveClient.Phase.LIVE -> MaterialTheme.colorScheme.primary
                    FlowLiveClient.Phase.CONNECTING -> MaterialTheme.colorScheme.tertiary
                    FlowLiveClient.Phase.ERROR -> MaterialTheme.colorScheme.error
                    FlowLiveClient.Phase.IDLE -> MaterialTheme.colorScheme.outline
                }
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(dot.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Flow",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        maxLines = 1
                    )
                    val sub = when (phase) {
                        FlowLiveClient.Phase.LIVE -> page.title.removePrefix("Google Flow - ").removePrefix("Google Flow: ").ifBlank { "En directo · AXELPC" }
                        FlowLiveClient.Phase.CONNECTING -> "Conectando con AXELPC…"
                        FlowLiveClient.Phase.ERROR -> "Sin conexión · reintentando"
                        FlowLiveClient.Phase.IDLE -> "En pausa"
                    }
                    Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (page.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                StudioTactileCard(
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { haptic.perform(NexusHapticEvent.SELECTION); client.simple("back") }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás en Flow", modifier = Modifier.size(19.dp))
                        }
                        IconButton(onClick = { haptic.perform(NexusHapticEvent.SELECTION); client.simple("home") }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.Home, "Inicio de Flow", modifier = Modifier.size(19.dp))
                        }
                        IconButton(onClick = { haptic.perform(NexusHapticEvent.SELECTION); keyboard = !keyboard }, modifier = Modifier.size(38.dp)) {
                            Icon(if (keyboard) Icons.Default.KeyboardHide else Icons.Default.Keyboard, "Teclado", modifier = Modifier.size(19.dp),
                                tint = if (keyboard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.Default.MoreVert, "Más opciones", modifier = Modifier.size(19.dp))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Recargar la página") }, leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                    onClick = { menu = false; haptic.perform(NexusHapticEvent.SELECTION); client.simple("reload") })
                                DropdownMenuItem(text = { Text("Abrir la Biblioteca") }, leadingIcon = { Icon(Icons.Default.Collections, null) },
                                    onClick = { menu = false; haptic.perform(NexusHapticEvent.SELECTION); onOpenLibrary() })
                                HorizontalDivider()
                                Text("Vista", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                VIEWS.forEachIndexed { i, v ->
                                    DropdownMenuItem(text = { Text((if (i == viewIndex) "✓ " else "    ") + v.label) },
                                        onClick = { menu = false; haptic.perform(NexusHapticEvent.SELECTION); viewIndex = i; FlowLivePrefs.putString(context, "view", "$i"); zoom = 1f; panX = 0f; panY = 0f })
                                }
                                HorizontalDivider()
                                Text("Calidad", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                QUALITIES.forEachIndexed { i, q ->
                                    DropdownMenuItem(text = { Text((if (i == qualityIndex) "✓ " else "    ") + q.label) },
                                        onClick = { menu = false; haptic.perform(NexusHapticEvent.SELECTION); qualityIndex = i; FlowLivePrefs.putString(context, "quality", "$i") })
                                }
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Dirección de AXELPC…") }, onClick = { menu = false; serverDialog = true })
                            }
                        }
                    }
                }
                NexusSettingsButton()
            }
        }

        // La página en directo.
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(Color.Black).onSizeChanged { box = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    flingJob?.cancel()
                    val slop = viewConfiguration.touchSlop
                    val start = down.position
                    var last = start
                    var mode = 0 // 0 sin decidir · 1 desplazar · 2 arrastrar (pulsación larga) · 3 zoom local
                    val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    while (true) {
                        val event = if (mode == 0) withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPointerEvent() } else awaitPointerEvent()
                        if (event == null) {
                            mode = 2
                            haptic.perform(NexusHapticEvent.GESTURE_START)
                            toPage(start)?.let { (x, y) -> client.pointer("down", x, y) }
                            continue
                        }
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            val end = event.changes.firstOrNull()?.position ?: last
                            when (mode) {
                                0 -> toPage(start)?.let { (x, y) -> client.click(x, y) }
                                1 -> if (zoom <= 1.01f) {
                                    val v = tracker.calculateVelocity()
                                    if (abs(v.y) > 400f || abs(v.x) > 400f) {
                                        val at = toPage(end) ?: (0.5f to 0.5f)
                                        flingJob = scope.launch {
                                            var vx = v.x; var vy = v.y
                                            while (abs(vx) > 60f || abs(vy) > 60f) {
                                                val k = cssPerPx()
                                                client.wheel(at.first, at.second, -vx * 0.016f * k, -vy * 0.016f * k)
                                                vx *= 0.92f; vy *= 0.92f
                                                delay(16)
                                            }
                                        }
                                    }
                                }
                                2 -> toPage(end)?.let { (x, y) -> client.pointer("up", x, y) }
                            }
                            break
                        }
                        if (pressed.size >= 2 && mode != 2) {
                            mode = 3
                            val centroid = event.calculateCentroid()
                            val pan = event.calculatePan()
                            val next = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                            val k = next / zoom
                            panX = FlowLiveGeometry.clampPan(centroid.x - (centroid.x - panX) * k + pan.x, box.width.toFloat(), next)
                            panY = FlowLiveGeometry.clampPan(centroid.y - (centroid.y - panY) * k + pan.y, box.height.toFloat(), next)
                            zoom = next
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        val change = pressed.first()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val pos = change.position
                        if (mode == 0 && (pos - start).getDistance() > slop) mode = 1
                        when (mode) {
                            1 -> {
                                val d = pos - last
                                if (zoom > 1.01f) {
                                    panX = FlowLiveGeometry.clampPan(panX + d.x, box.width.toFloat(), zoom)
                                    panY = FlowLiveGeometry.clampPan(panY + d.y, box.height.toFloat(), zoom)
                                } else {
                                    val k = cssPerPx()
                                    val at = toPage(pos) ?: (0.5f to 0.5f)
                                    client.wheel(at.first, at.second, -d.x * k, -d.y * k)
                                }
                            }
                            2 -> toPage(pos)?.let { (x, y) -> client.pointer("move", x, y) }
                        }
                        last = pos
                        change.consume()
                    }
                }
            }
        ) {
            if (img != null) {
                Canvas(Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom; scaleY = zoom; translationX = panX; translationY = panY; transformOrigin = TransformOrigin(0f, 0f)
                }) {
                    drawImage(img, dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                        dstSize = IntSize(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)), filterQuality = FilterQuality.Medium)
                }
            } else {
                StudioTactileCard(
                    modifier = Modifier.align(Alignment.Center).padding(NexusTokens.space24),
                    shape = NexusTokens.card,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        Modifier.padding(horizontal = NexusTokens.space24, vertical = NexusTokens.space16),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)
                    ) {
                        if (phase != FlowLiveClient.Phase.ERROR) CircularProgressIndicator()
                        Text(
                            if (phase == FlowLiveClient.Phase.ERROR) (error ?: "Sin conexión con AXELPC") else "Abriendo tu Flow en AXELPC…",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (phase == FlowLiveClient.Phase.ERROR) {
                            androidx.compose.material3.FilledTonalButton(
                                onClick = { haptic.perform(NexusHapticEvent.SELECTION); client.reconnect() },
                                shape = NexusTokens.pill
                            ) {
                                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(NexusTokens.space8))
                                Text("Reintentar")
                            }
                        }
                    }
                }
            }
            if (img != null && phase == FlowLiveClient.Phase.ERROR) {
                Surface(Modifier.align(Alignment.TopCenter).padding(NexusTokens.space8), shape = NexusTokens.pill, color = MaterialTheme.colorScheme.errorContainer) {
                    Text(error ?: "Sin conexión", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, maxLines = 2)
                }
            }
            if (zoom > 1.01f) {
                AssistChip(onClick = { zoom = 1f; panX = 0f; panY = 0f }, label = { Text("Quitar zoom") }, leadingIcon = { Icon(Icons.Default.ZoomOut, null) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(NexusTokens.space8))
            }
        }

        // Resultados que ya están en la Biblioteca.
        AnimatedVisibility(recent.isNotEmpty() || download != null || failure != null || order != null) {
            StudioChrome(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.space8, vertical = NexusTokens.space4), verticalAlignment = Alignment.CenterVertically) {
                    if (recent.isNotEmpty()) {
                        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                            items(recent.take(12), key = { it.assetId }) { item ->
                                AsyncImage(model = item.uri, contentDescription = item.name, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(44.dp).clip(NexusTokens.thumbnail).clickable { linkImported = item })
                            }
                        }
                        TextButton(onClick = onOpenLibrary) { Text("${recent.size} en Biblioteca") }
                        IconButton(onClick = { recent.clear(); failure = null }) { Icon(Icons.Default.Close, "Ocultar") }
                    } else {
                        val text = failure ?: download?.let { "Descargando «$it»…" } ?: order ?: ""
                        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = if (failure != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (failure != null || order != null) IconButton(onClick = { failure = null; client.order.value = null }) { Icon(Icons.Default.Close, "Ocultar") }
                    }
                }
            }
        }

        // Teclado: lo escrito aquí se inserta donde esté el cursor en Flow.
        AnimatedVisibility(keyboard) {
            FlowKeyboardBar(client, editable, storySelection?.takeIf { storyShot != null }?.let {
                storyIndex.storyContext(it.projectId, it.sceneId, it.shotId)
            })
        }
    }

    linkImported?.let { item ->
        AlertDialog(onDismissRequest = { linkImported = null }, title = { Text(item.name) },
            text = { Text(if (storyShot == null) "Guardado en Biblioteca · puedes asignarlo desde Material del proyecto." else
                "Vincular como toma candidata al plano «${storyShot.description?.take(70) ?: storyShot.code}».") },
            confirmButton = { if (storyShot != null) TextButton(onClick = {
                val shot = storyShot
                linkImported = null
                scope.launch {
                    runCatching { storyIndex.storyCommand(shot.projectId, buildJsonObject {
                        put("action", "link_material"); put("operation_id", "flow-link-${item.assetId}-${shot.id}")
                        put("scope", "shot"); put("target_id", shot.id); put("asset_id", item.assetId); put("role", "candidate")
                    }) }.onFailure { failure = it.message ?: "No se pudo vincular el material." }
                }
            }) { Text("Vincular al plano") } },
            dismissButton = { TextButton(onClick = { linkImported = null; onOpenLibrary() }) { Text("Biblioteca") } })
    }
    if (serverDialog) {
        var url by remember { mutableStateOf(FlowLivePrefs.url(context)) }
        AlertDialog(onDismissRequest = { serverDialog = false },
            title = { Text("Dirección de AXELPC") },
            text = {
                Column {
                    Text("Servicio «Flow en directo» por Tailscale. Por defecto: ${FlowLivePrefs.DEFAULT_URL}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { FlowLivePrefs.setUrl(context, url); serverDialog = false; client.reconnect() }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { serverDialog = false }) { Text("Cancelar") } })
    }
}

@Composable
private fun FlowKeyboardBar(client: FlowLiveClient, editable: Boolean, storyContext: String? = null) {
    val context = LocalContext.current
    val haptics = com.vidadomu.nexus.ui.theme.rememberNexusHaptics()
    var draft by rememberSaveable { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(editable) { if (editable) runCatching { focus.requestFocus() } }
    // Con un campo de Flow activo, lo escrito va a ese campo; si no, es un encargo para el agente de Flow.
    fun send() {
        if (draft.isBlank()) return
        haptics.perform(NexusHapticEvent.CONFIRMATION)
        if (editable) client.text(draft) else client.order(draft)
        draft = ""
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = NexusTokens.space8, vertical = NexusTokens.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexusTokens.space4)
    ) {
        StudioTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f).focusRequester(focus),
            placeholder = { Text(if (editable) "Escribe en el campo de Flow" else "Encarga algo a Flow y míralo hacerlo") },
            maxLines = 4,
            shape = NexusTokens.pill,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() })
        )
        IconButton(onClick = { send() }) { Icon(Icons.AutoMirrored.Filled.Send, if (editable) "Escribir en Flow" else "Encargar a Flow") }
        Box {
            TextButton(onClick = { haptics.perform(NexusHapticEvent.SELECTION); more = true }) { Text("Teclas") }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                if (storyContext != null) DropdownMenuItem(text = { Text("Preparar encargo del plano") },
                    onClick = { more = false; draft = storyContext })
                DropdownMenuItem(text = { Text("Intro") }, onClick = { more = false; client.key("Enter") })
                DropdownMenuItem(text = { Text("Borrar") }, onClick = { more = false; client.key("Backspace") })
                DropdownMenuItem(text = { Text("Tabulador") }, onClick = { more = false; client.key("Tab") })
                DropdownMenuItem(text = { Text("Escape") }, onClick = { more = false; client.key("Escape") })
                DropdownMenuItem(text = { Text("Seleccionar todo") }, onClick = { more = false; client.command("selectAll") })
                DropdownMenuItem(text = { Text("Deshacer") }, onClick = { more = false; client.command("undo") })
                DropdownMenuItem(text = { Text("Pegar del portapapeles") }, onClick = {
                    more = false
                    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                    clip?.getItemAt(0)?.coerceToText(context)?.toString()?.let(client::text)
                })
            }
        }
    }
}
