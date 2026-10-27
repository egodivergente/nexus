package com.vidadomu.nexus.assistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vidadomu.nexus.ui.theme.NexusTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * A board of images inside the chat. One finger scrolls the chat as usual; two fingers on an image move and resize it;
 * drawing with either pen or finger requires long press → Anotaciones.
 */
@Composable
internal fun ChatBoardCard(boardId: String) {
    val context = LocalContext.current
    val store = remember { ChatBoardStore(context) }
    var board by remember(boardId) { mutableStateOf<ChatBoard?>(null) }
    LaunchedEffect(boardId) { board = withContext(Dispatchers.IO) { store.load(boardId) } }
    val current = board ?: return
    val scope = rememberCoroutineScope()
    var annotations by remember(boardId) { mutableStateOf(false) }
    var imageMenu by remember(boardId) {mutableStateOf(false)}
    // Owners of the strokes in the order they were drawn (image id, or "" for the board), for «deshacer».
    val drawn = remember(boardId) { mutableStateListOf<String>() }
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val ink = Color(0xFFFF3B30)
    fun commit(next: ChatBoard) { board = next; scope.launch(Dispatchers.IO) { store.save(next) } }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().clip(NexusTokens.card).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            val widthPx = constraints.maxWidth.toFloat()
            val density = LocalDensity.current
            val heightDp = with(density) { (widthPx * current.height).toDp() }
            val latest by rememberUpdatedState(current)
            val drawingEnabled by rememberUpdatedState(annotations)
            fun hit(point: Offset): BoardImage? = latest.images.lastOrNull { image ->
                point.x / widthPx in image.x..(image.x + image.w) && point.y / widthPx in image.y..(image.y + image.h)
            }
            Box(Modifier.fillMaxWidth().height(heightDp)
                .semantics {contentDescription="Tablero de imágenes; mantener pulsado para ver opciones"}
                .pointerInput(boardId) {awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false)
                    if(awaitLongPressOrCancellation(down.id)!=null) imageMenu=true
                }}
                .pointerInput(widthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (drawingEnabled) {
                        down.consume()
                        val target = hit(down.position)
                        val points = mutableListOf(down.position)
                        live = points.toList()
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            points += change.position; change.consume(); live = points.toList()
                        }
                        live = emptyList()
                        val stroke = BoardStroke(points.flatMap { p ->
                            if (target == null) listOf(p.x / widthPx, p.y / widthPx)
                            else listOf((p.x / widthPx - target.x) / target.w, (p.y / widthPx - target.y) / target.h)
                        })
                        val board = latest
                        commit(if (target == null) board.copy(strokes = board.strokes + stroke)
                            else board.copy(images = board.images.map { if (it.id == target.id) it.copy(strokes = it.strokes + stroke) else it }))
                        drawn += target?.id.orEmpty()
                        return@awaitEachGesture
                    }
                    // One finger is left to the chat's scroll; a second finger on an image takes it over.
                    var moving: String? = null
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            if (moving == null) moving = hit(event.calculateCentroid())?.id ?: break
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val snapshot = latest
                            val image = snapshot.images.firstOrNull { it.id == moving } ?: break
                            val w = (image.w * zoom).coerceIn(.06f, 1f)
                            val cx = image.x + image.w / 2 + pan.x / widthPx
                            val cy = image.y + image.h / 2 + pan.y / widthPx
                            val moved = image.copy(w = w, x = (cx - w / 2).coerceIn(0f, 1f - w), y = (cy - w / image.aspect / 2).coerceAtLeast(0f))
                            // The touched image comes to the front; it is saved once the fingers lift.
                            board = snapshot.copy(images = snapshot.images.filterNot { it.id == moving } + moved)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (moving != null) board?.let { commit(it) }
                }
            }) {
                current.images.forEach { image ->
                    Box(Modifier.offset { IntOffset((image.x * widthPx).roundToInt(), (image.y * widthPx).roundToInt()) }
                        .size(with(density) { (image.w * widthPx).toDp() }, with(density) { (image.h * widthPx).toDp() })) {
                        AsyncImage(image.uri, image.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Canvas(Modifier.fillMaxSize()) {
                            image.strokes.forEach { drawStroke(it, Offset.Zero, size.width, size.height, it.width * widthPx, ink) }
                        }
                    }
                }
                Canvas(Modifier.matchParentSize()) {
                    current.strokes.forEach { drawStroke(it, Offset.Zero, widthPx, widthPx, it.width * widthPx, ink) }
                    if (live.size > 1) drawPath(Path().apply { moveTo(live[0].x, live[0].y); live.drop(1).forEach { lineTo(it.x, it.y) } },
                        ink, style = Stroke(.006f * widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (drawn.isNotEmpty()) BoardButton({
                    val owner = drawn.removeAt(drawn.lastIndex)
                    val board = current
                    commit(if (owner.isEmpty()) board.copy(strokes = board.strokes.dropLast(1))
                        else board.copy(images = board.images.map { if (it.id == owner) it.copy(strokes = it.strokes.dropLast(1)) else it }))
                }, "Deshacer el último trazo", false) { Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp)) }
                if(annotations) BoardButton({annotations=false},"Terminar anotaciones",true) {Icon(Icons.Default.Check,null,Modifier.size(18.dp))}
                DropdownMenu(expanded=imageMenu,onDismissRequest={imageMenu=false}) {
                    DropdownMenuItem(text={Text(if(annotations) "Terminar anotaciones" else "Anotaciones")},
                        leadingIcon={Icon(Icons.Default.Draw,null)},onClick={imageMenu=false;annotations=!annotations})
                }
            }
        }
        if(annotations) Text("Anotaciones activas · termina con ✓",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
        if (current.dirty) Text("Se enviará al agente con tu próximo mensaje", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
private fun BoardButton(onClick: () -> Unit, description: String, active: Boolean, content: @Composable () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, modifier = Modifier.size(48.dp).semantics { contentDescription = description },
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .85f),
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private fun DrawScope.drawStroke(stroke: BoardStroke, origin: Offset, w: Float, h: Float, width: Float, color: Color) {
    if (stroke.points.size < 2) return
    val pts = stroke.points.chunked(2).map { (x, y) -> Offset(origin.x + x * w, origin.y + y * h) }
    if (pts.size == 1) { drawCircle(color, width / 2, pts[0]); return }
    drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }, color,
        style = Stroke(width.coerceAtLeast(2f), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
