package com.vidadomu.nexus.assistant

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.ui.theme.NexusTokens
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Una imagen colocada en el muro: posición y ancho en unidades del lienzo (el alto sale de la proporción). */
internal data class WallPlacement(val id: String, val x: Float, val y: Float, val w: Float, val z: Int)

/** Cálculos puros del muro (colocación inicial, ajuste a pantalla y guardado), separados para poder probarlos. */
internal object WallLayout {
    const val CELL = 320f
    const val GAP = 16f
    const val MIN_W = 80f
    const val MAX_W = 2400f

    /** Collage inicial: 2 columnas si hay 2–4 imágenes y, a partir de ahí, la raíz cuadrada; cada fila mide lo que su imagen más alta. */
    fun initial(ids: List<String>, aspects: List<Float>): List<WallPlacement> {
        if (ids.isEmpty()) return emptyList()
        val cols = when (ids.size) { 1 -> 1; in 2..4 -> 2; else -> ceil(sqrt(ids.size.toFloat())).toInt() }
        val out = mutableListOf<WallPlacement>()
        var y = 0f
        ids.indices.chunked(cols).forEach { row ->
            var rowHeight = 0f
            row.forEachIndexed { c, i ->
                val aspect = aspects.getOrElse(i) { 1f }.coerceIn(.2f, 5f)
                out += WallPlacement(ids[i], c * (CELL + GAP), y, CELL, i)
                rowHeight = max(rowHeight, CELL / aspect)
            }
            y += rowHeight + GAP
        }
        return out
    }

    /** Escala y desplazamiento para que todo el contenido quepa en la pantalla con un margen. */
    fun fit(placements: List<WallPlacement>, aspects: Map<String, Float>, viewW: Float, viewH: Float, margin: Float = 24f): Triple<Float, Float, Float> {
        if (placements.isEmpty() || viewW <= 0f || viewH <= 0f) return Triple(1f, 0f, 0f)
        val left = placements.minOf { it.x }
        val top = placements.minOf { it.y }
        val right = placements.maxOf { it.x + it.w }
        val bottom = placements.maxOf { it.y + it.w / (aspects[it.id] ?: 1f).coerceIn(.2f, 5f) }
        val scale = min((viewW - 2 * margin) / (right - left), (viewH - 2 * margin) / (bottom - top)).coerceIn(.1f, 4f)
        val offX = (viewW - (right - left) * scale) / 2f - left * scale
        val offY = (viewH - (bottom - top) * scale) / 2f - top * scale
        return Triple(scale, offX, offY)
    }

    fun encode(list: List<WallPlacement>): String = list.joinToString(";") { "${it.id}|${it.x}|${it.y}|${it.w}|${it.z}" }

    fun decode(text: String?, ids: List<String>): List<WallPlacement>? {
        if (text.isNullOrBlank()) return null
        val parsed = text.split(';').mapNotNull { part ->
            val f = part.split('|')
            if (f.size != 5) return@mapNotNull null
            val x = f[1].toFloatOrNull(); val y = f[2].toFloatOrNull(); val w = f[3].toFloatOrNull(); val z = f[4].toIntOrNull()
            if (x == null || y == null || w == null || z == null) null else WallPlacement(f[0], x, y, w.coerceIn(MIN_W, MAX_W), z)
        }
        // Solo vale si coincide con las imágenes actuales: así un muro guardado no mezcla conjuntos distintos.
        return parsed.takeIf { p -> p.size == ids.size && p.map { it.id }.toSet() == ids.toSet() }
    }

    fun key(ids: List<String>): String = "wall:" + ids.joinToString("|").hashCode()
}

private class WallItem(val att: Attachment, x: Float, y: Float, w: Float, z: Int, val aspect: Float) {
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)
    var w by mutableFloatStateOf(w)
    var z by mutableIntStateOf(z)
    val h get() = w / aspect
}

/**
 * Muro libre a pantalla completa: un lienzo sin cuadrícula donde cada imagen se mueve con un dedo y cambia de tamaño con dos.
 * Con el fondo se desplaza el lienzo y con dos dedos se amplía. Un toque abre la imagen. «Ordenar» recompone el collage.
 */
@Composable
internal fun FreeWall(images: List<Attachment>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val ids = remember(images) { images.map { it.id } }
    val prefs = remember(context) { context.getSharedPreferences("free_wall", Context.MODE_PRIVATE) }
    val aspects = remember(images) { images.map { a -> a.width?.let { w -> a.height?.takeIf { it > 0 }?.let { h -> w.toFloat() / h } } ?: 1f } }
    val items = remember(images) {
        val saved = WallLayout.decode(prefs.getString(WallLayout.key(ids), null), ids)
        val placement = saved ?: WallLayout.initial(ids, aspects)
        mutableStateListOf<WallItem>().apply {
            placement.forEach { p -> val i = ids.indexOf(p.id); add(WallItem(images[i], p.x, p.y, p.w, p.z, aspects[i])) }
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var fitted by remember(images) { mutableStateOf(false) }
    var opened by remember { mutableStateOf<Attachment?>(null) }

    fun snapshot() = items.map { WallPlacement(it.att.id, it.x, it.y, it.w, it.z) }
    fun save() = prefs.edit().putString(WallLayout.key(ids), WallLayout.encode(snapshot())).apply()
    fun fitAll() {
        val (s, ox, oy) = WallLayout.fit(snapshot(), items.associate { it.att.id to it.aspect }, viewport.width.toFloat(), viewport.height.toFloat())
        scale = s; offset = Offset(ox, oy)
    }
    LaunchedEffect(viewport, images) { if (!fitted && viewport.width > 0) { fitAll(); fitted = true } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B1512)).onSizeChanged { viewport = it }.clipToBounds()
            .pointerInput(images) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun worldOf(p: Offset) = Offset((p.x - offset.x) / scale, (p.y - offset.y) / scale)
                    val start = worldOf(down.position)
                    val target = items.sortedByDescending { it.z }.firstOrNull { it.x <= start.x && start.x <= it.x + it.w && it.y <= start.y && start.y <= it.y + it.h }
                    if (target != null) target.z = (items.maxOfOrNull { it.z } ?: 0) + 1
                    var travelled = 0f
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            val zoom = event.calculateZoom(); val pan = event.calculatePan(); val centre = event.calculateCentroid()
                            if (target != null) {
                                val newW = (target.w * zoom).coerceIn(WallLayout.MIN_W, WallLayout.MAX_W)
                                val cx = target.x + target.w / 2f + pan.x / scale
                                val cy = target.y + target.h / 2f + pan.y / scale
                                target.w = newW; target.x = cx - newW / 2f; target.y = cy - target.h / 2f
                            } else {
                                val next = (scale * zoom).coerceIn(.1f, 4f)
                                val k = next / scale
                                offset = Offset(centre.x - (centre.x - offset.x) * k + pan.x, centre.y - (centre.y - offset.y) * k + pan.y)
                                scale = next
                            }
                            travelled += 100f
                            event.changes.forEach { it.consume() }
                        } else if (pressed.size == 1) {
                            val d = pressed[0].positionChange()
                            if (d != Offset.Zero) {
                                travelled += d.getDistance()
                                if (target != null) { target.x += d.x / scale; target.y += d.y / scale } else offset += d
                                pressed[0].consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (target != null && travelled < viewConfiguration.touchSlop) opened = target.att
                    else if (travelled >= viewConfiguration.touchSlop) save()
                }
            }
        ) {
            items.sortedBy { it.z }.forEach { item -> key(item.att.id) {
                val wPx = item.w * scale
                Box(Modifier.offset { IntOffset((item.x * scale + offset.x).roundToInt(), (item.y * scale + offset.y).roundToInt()) }
                    .size(with(density) { wPx.toDp() }, with(density) { (wPx / item.aspect).toDp() }).clip(NexusTokens.field)) {
                    AsyncImage(model = item.att.uri, contentDescription = item.att.name ?: "Imagen del muro", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            } }
            Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(NexusTokens.space8), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar el muro", tint = Color.White) }
                Text("${items.size} imágenes · arrastra, pellizca, toca para ampliar", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .8f),
                    modifier = Modifier.padding(horizontal = NexusTokens.space8))
                IconButton(onClick = { fitAll() }) { Icon(Icons.Default.FitScreen, "Ver todo", tint = Color.White) }
                IconButton(onClick = {
                    val fresh = WallLayout.initial(ids, aspects)
                    fresh.forEach { p -> items.firstOrNull { it.att.id == p.id }?.let { it.x = p.x; it.y = p.y; it.w = p.w; it.z = p.z } }
                    fitAll(); save()
                }) { Icon(Icons.Default.AutoAwesomeMosaic, "Ordenar como collage", tint = Color.White) }
            }
        }
    }
    opened?.let { AssistantImageForeground(it) { opened = null } }
}
