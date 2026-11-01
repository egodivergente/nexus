@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.vidadomu.nexus.assistant

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.vidadomu.nexus.ui.theme.onStylusHover
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.vidadomu.nexus.agent.core.orders.Order
import com.vidadomu.nexus.agent.core.orders.OrderStatus
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.NexusInsets
import com.vidadomu.nexus.ui.theme.rememberNexusMotionAllowed
import kotlinx.coroutines.delay

internal fun isRendering(status: OrderStatus): Boolean = status == OrderStatus.SUBMITTING || status == OrderStatus.PROCESSING

internal fun elapsedLabel(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}

private enum class StagePhase { RENDERING, REVEAL, NONE }

/**
 * Wraps an order card: while the provider works, an aurora surface shows the real state and elapsed
 * time (never a percentage); when it completes during this session it cross-fades into the result.
 */
@Composable
internal fun StagedOrder(order: Order, outputs: List<String>, onOpenStage: (Int) -> Unit, card: @Composable () -> Unit) {
    var sawRendering by remember(order.id) { mutableStateOf(false) }
    LaunchedEffect(order.id, order.status) { if (isRendering(order.status)) sawRendering = true }
    val phase = when {
        isRendering(order.status) -> StagePhase.RENDERING
        order.status == OrderStatus.COMPLETED && sawRendering && outputs.isNotEmpty() -> StagePhase.REVEAL
        else -> StagePhase.NONE
    }
    val motion = rememberNexusMotionAllowed()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        AnimatedContent(phase, label = "stage", transitionSpec = {
            if (motion) (fadeIn(tween(420)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow), initialScale = 0.92f)) togetherWith
                (fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 1.04f))
            else (EnterTransition.None togetherWith ExitTransition.None) using null
        }) { target ->
            when (target) {
                StagePhase.RENDERING -> LiveRenderSurface(order)
                StagePhase.REVEAL -> RevealThumbnail(outputs.first(), order.modality, onClick = { onOpenStage(0) })
                StagePhase.NONE -> Spacer(Modifier.fillMaxWidth())
            }
        }
        card()
    }
}

@Composable
internal fun LiveRenderSurface(order: Order, modifier: Modifier = Modifier) {
    val motion = rememberNexusMotionAllowed()
    val scheme = MaterialTheme.colorScheme
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(order.id) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val phase: State<Float> = if (motion) {
        val transition = rememberInfiniteTransition(label = "aurora")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "t")
    } else remember { mutableFloatStateOf(0.3f) }
    val state = if (order.status == OrderStatus.SUBMITTING) "Enviando el encargo al proveedor" else "Renderizando en el proveedor"
    val elapsed = elapsedLabel(now - order.createdAt)
    val colors = listOf(scheme.primary, scheme.tertiary, scheme.secondary)
    Box(modifier.fillMaxWidth().height(156.dp).clip(NexusTokens.card)
        .drawBehind {
            val t = phase.value
            val w = size.width; val h = size.height
            drawRect(Brush.linearGradient(listOf(colors[0].copy(alpha = .55f), colors[1].copy(alpha = .45f), colors[2].copy(alpha = .55f)),
                start = Offset(w * t, 0f), end = Offset(w * (1f - t), h)))
            val angle = t * 2f * Math.PI.toFloat()
            colors.forEachIndexed { i, c ->
                val a = angle + i * 2.1f
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = .55f), Color.Transparent),
                    center = Offset(w * (.5f + .35f * kotlin.math.cos(a)), h * (.5f + .35f * kotlin.math.sin(a))), radius = h),
                    radius = h, center = Offset(w * (.5f + .35f * kotlin.math.cos(a)), h * (.5f + .35f * kotlin.math.sin(a))))
            }
            if (motion) {
                val x = (w * 1.6f) * t - w * 0.3f
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .22f), Color.Transparent),
                    start = Offset(x - w * .25f, 0f), end = Offset(x + w * .25f, h)))
            }
        }
        .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.BottomStart) {
        Column(Modifier.padding(NexusTokens.space12)) {
            Text(order.modelName, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .85f), maxLines = 1)
            Text(state, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text("Tiempo transcurrido $elapsed", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .85f))
        }
    }
}

@Composable
private fun RevealThumbnail(uri: String, modality: String, onClick: () -> Unit) {
    if (modality == "AUDIO") return
    Box(Modifier.fillMaxWidth().height(200.dp).clip(NexusTokens.card).clickable(onClick = onClick)) {
        StageThumbnail(uri, modality == "VIDEO", "Ver resultado en escenario", Modifier.fillMaxSize())
    }
}

@Composable
private fun StageThumbnail(uri: String, video: Boolean, description: String?, modifier: Modifier) {
    val context = LocalContext.current
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        AsyncImage(remember(uri, video) {
            coil.request.ImageRequest.Builder(context).data(uri).crossfade(200).size(512)
                .apply { if (video) decoderFactory { result, options, _ -> coil.decode.VideoFrameDecoder(result.source, options) } }
                .build()
        }, description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (video) Icon(Icons.Default.PlayArrow, null, tint = Color.White,
            modifier = Modifier.size(28.dp).background(Color.Black.copy(alpha = .45f), CircleShape))
    }
}

/** New chat rows enter once with a spring slide+fade; rows recycled by scrolling stay put. */
@Composable
internal fun EntranceSpring(key: String, seen: MutableSet<String>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motion = rememberNexusMotionAllowed()
    val fresh = remember(key) { motion && seen.add(key) }
    SideEffect { seen.add(key) }
    val progress = remember(key) { Animatable(if (fresh) 0f else 1f) }
    LaunchedEffect(key) { if (fresh) progress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)) }
    val offset = with(LocalDensity.current) { 28.dp.toPx() }
    Box(modifier.graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * offset
    }) { content() }
}

/** Full-screen stage with a horizontal carousel over every result of the set. */
@Composable
internal fun ResultStage(results: List<Attachment>, initial: Int, onDismiss: () -> Unit) {
    if (results.isEmpty()) return
    val pager = rememberPagerState(initialPage = initial.coerceIn(0, results.lastIndex)) { results.size }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(NexusInsets.fullScreen)) {
                HorizontalPager(pager, Modifier.fillMaxSize(), key = { results[it].id }) { page ->
                    Box(Modifier.fillMaxSize().padding(horizontal = NexusTokens.space8), contentAlignment = Alignment.Center) {
                        AssistantMediaPreview(results[page])
                    }
                }
                Row(Modifier.fillMaxWidth().padding(NexusTokens.space8).align(Alignment.TopCenter), verticalAlignment = Alignment.CenterVertically) {
                    Text(results.getOrNull(pager.currentPage)?.name ?: "Resultado", color = Color.White, style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, modifier = Modifier.weight(1f).padding(start = NexusTokens.space8))
                    if (results.size > 1) Text("${pager.currentPage + 1} / ${results.size}", color = Color.White.copy(alpha = .8f),
                        style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar escenario", tint = Color.White) }
                }
                if (results.size > 1) Row(Modifier.align(Alignment.BottomCenter).padding(NexusTokens.space16),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(results.size.coerceAtMost(12)) { i ->
                        Box(Modifier.size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i == pager.currentPage) 1f else .4f)))
                    }
                }
            }
        }
    }
}

internal data class LibraryPick(val jobId: String, val outputId: String, val uri: String, val video: Boolean, val label: String)

/**
 * Recent local outputs above the composer; a tap attaches one to the chat.
 * Vidal 01-11 (vídeo, 01:40): al acercar el S Pen cada miniatura se amplía en una vista previa grande
 * (con el dedo, pulsación larga). «Ver todo» abre la Biblioteca completa, para no depender de lo muy reciente.
 */
@Composable
internal fun LibraryCarousel(onPick: (jobId: String, outputId: String) -> Unit, enabled: Boolean, modifier: Modifier = Modifier,
    onBrowseAll: (() -> Unit)? = null) {
    val app = LocalContext.current.applicationContext as com.vidadomu.nexus.NexusApplication
    val jobs by app.generationRepository.allJobs.collectAsState()
    val recent = remember(jobs) {
        jobs.sortedByDescending { it.completedAt ?: it.startedAt }.asSequence().flatMap { job ->
            job.outputs.asSequence().filter { !it.localUri.isNullOrBlank() && (it.type == Modality.IMAGE || it.type == Modality.VIDEO) }
                .map { LibraryPick(job.id, it.id, it.localUri!!, it.type == Modality.VIDEO, job.prompt.take(80).ifBlank { "Resultado" }) }
        }.take(48).toList()
    }
    if (recent.isEmpty()) {
        Row(modifier.fillMaxWidth().padding(NexusTokens.space12), verticalAlignment = Alignment.CenterVertically) {
            Text("Aún no hay imágenes o vídeos descargados en la Biblioteca.", Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            onBrowseAll?.let { TextButton(onClick = it) { Text("Ver todo") } }
        }
        return
    }
    var preview by remember { mutableStateOf<LibraryPick?>(null) }
    var previewCenterX by remember { mutableIntStateOf(0) }
    Box(modifier.fillMaxWidth()) {
        LazyRow(contentPadding = PaddingValues(horizontal = NexusTokens.space12), horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            items(recent, key = { it.jobId + ":" + it.outputId }) { pick ->
                var centerX by remember { mutableIntStateOf(0) }
                StageThumbnail(pick.uri, pick.video, null, Modifier.size(64.dp)
                    .onGloballyPositioned { centerX = it.boundsInWindow().center.x.roundToInt() }
                    .onStylusHover { inside ->
                        if (inside) { previewCenterX = centerX; preview = pick } else if (preview == pick) preview = null
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(enabled = enabled, onLongClickLabel = "Vista previa",
                        onLongClick = { previewCenterX = centerX; preview = pick },
                        onClick = { preview = null; onPick(pick.jobId, pick.outputId) })
                    .semantics { contentDescription = "Adjuntar de la Biblioteca: ${pick.label}" })
            }
            onBrowseAll?.let { browse ->
                item(key = "library_browse_all") {
                    Surface(onClick = { preview = null; browse() }, shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(64.dp)
                            .semantics { contentDescription = "Ver toda la Biblioteca" }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.GridView, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Ver todo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        preview?.let { pick ->
            val gap = with(LocalDensity.current) { NexusTokens.space8.roundToPx() }
            Popup(popupPositionProvider = remember(previewCenterX, gap) { AboveAnchorPosition(previewCenterX, gap) },
                onDismissRequest = { preview = null }, properties = PopupProperties(focusable = false, dismissOnClickOutside = true)) {
                LibraryHoverPreview(pick, onClick = { preview = null; if (enabled) onPick(pick.jobId, pick.outputId) })
            }
        }
    }
}

/** Large preview of one recent item, shown above the strip; it fits the whole frame instead of cropping it. */
@Composable
private fun LibraryHoverPreview(pick: LibraryPick, onClick: () -> Unit) {
    val context = LocalContext.current
    Surface(onClick = onClick, shape = NexusTokens.card, shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.width(248.dp).semantics { contentDescription = "Vista previa: ${pick.label}. Toca para adjuntar" }) {
        Column {
            Box(Modifier.fillMaxWidth().height(280.dp).background(Color.Black), contentAlignment = Alignment.Center) {
                AsyncImage(remember(pick.uri, pick.video) {
                    coil.request.ImageRequest.Builder(context).data(pick.uri).crossfade(150).size(1024)
                        .apply { if (pick.video) decoderFactory { result, options, _ -> coil.decode.VideoFrameDecoder(result.source, options) } }
                        .build()
                }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                if (pick.video) Icon(Icons.Default.PlayArrow, null, tint = Color.White,
                    modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = .45f), CircleShape))
            }
            Text(pick.label, style = MaterialTheme.typography.labelMedium, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space8))
        }
    }
}

/** Places a popup centred on [centerX] (window coordinates) just above its anchor, kept inside the window. */
private class AboveAnchorPosition(private val centerX: Int, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (centerX - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
