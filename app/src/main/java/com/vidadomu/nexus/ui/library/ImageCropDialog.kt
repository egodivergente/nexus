package com.vidadomu.nexus.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.vidadomu.nexus.ui.theme.NexusTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** Proportions offered for the crop; null is free. Values are width / height. */
private val CROP_RATIOS = listOf<Pair<String, Float?>>("Libre" to null, "1:1" to 1f, "16:9" to 16f / 9f, "9:16" to 9f / 16f)

/**
 * Crop an image: drag a corner to resize, drag inside to move. [onSave] receives the crop as fractions (0–1) of the image.
 * The original is never touched; the caller saves the result as a new file.
 */
@Composable
internal fun ImageCropDialog(uri: String, busy: Boolean, onDismiss: () -> Unit, onSave: (Rect) -> Unit) {
    var imageSize by remember { mutableStateOf(Size.Unspecified) }
    var crop by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    var ratio by remember { mutableStateOf<Float?>(null) }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(NexusTokens.space24), contentAlignment = Alignment.Center) {
                val box = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                // Where the image is drawn inside the box (fitted, centred).
                val shown = if (imageSize.isSpecified && imageSize.width > 0 && imageSize.height > 0) {
                    val fit = minOf(box.width / imageSize.width, box.height / imageSize.height)
                    val w = imageSize.width * fit; val h = imageSize.height * fit
                    Rect(Offset((box.width - w) / 2, (box.height - h) / 2), Size(w, h))
                } else null
                AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                    onSuccess = { imageSize = it.painter.intrinsicSize })
                if (shown != null) {
                    val latestCrop by rememberUpdatedState(crop)
                    val latestRatio by rememberUpdatedState(ratio)
                    // Pixel aspect of one unit of fraction, so a ratio in pixels can be held while dragging.
                    val pixelAspect = shown.width / shown.height
                    Canvas(Modifier.fillMaxSize().pointerInput(shown) {
                        val grab = 32.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val start = latestCrop
                            fun px(r: Rect) = Rect(shown.left + r.left * shown.width, shown.top + r.top * shown.height,
                                shown.left + r.right * shown.width, shown.top + r.bottom * shown.height)
                            val p = px(start)
                            val corners = listOf(p.topLeft, p.topRight, p.bottomLeft, p.bottomRight)
                            val corner = corners.indexOfFirst { (it - down.position).getDistance() <= grab }
                            if (corner < 0 && !p.contains(down.position)) return@awaitEachGesture
                            down.consume()
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                val d = change.position - down.position
                                val dx = d.x / shown.width; val dy = d.y / shown.height
                                crop = if (corner < 0) {
                                    val x = (start.left + dx).coerceIn(0f, 1f - start.width)
                                    val y = (start.top + dy).coerceIn(0f, 1f - start.height)
                                    Rect(Offset(x, y), start.size)
                                } else resizeCorner(start, corner, dx, dy, latestRatio?.let { it / pixelAspect })
                            }
                        }
                    }) {
                        val r = Rect(shown.left + crop.left * shown.width, shown.top + crop.top * shown.height,
                            shown.left + crop.right * shown.width, shown.top + crop.bottom * shown.height)
                        val dim = Color.Black.copy(alpha = .55f)
                        drawRect(dim, Offset(0f, 0f), Size(size.width, r.top))
                        drawRect(dim, Offset(0f, r.bottom), Size(size.width, size.height - r.bottom))
                        drawRect(dim, Offset(0f, r.top), Size(r.left, r.height))
                        drawRect(dim, Offset(r.right, r.top), Size(size.width - r.right, r.height))
                        drawRect(Color.White, r.topLeft, r.size, style = Stroke(1.5.dp.toPx()))
                        val arm = 18.dp.toPx(); val w = 4.dp.toPx()
                        listOf(r.topLeft to Offset(1f, 1f), r.topRight to Offset(-1f, 1f), r.bottomLeft to Offset(1f, -1f), r.bottomRight to Offset(-1f, -1f))
                            .forEach { (c, dir) ->
                                drawLine(Color.White, c, c + Offset(arm * dir.x, 0f), w)
                                drawLine(Color.White, c, c + Offset(0f, arm * dir.y), w)
                            }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.space16), horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                CROP_RATIOS.forEach { (label, value) ->
                    FilterChip(ratio == value, {
                        ratio = value
                        if (value != null && imageSize.isSpecified) crop = centredCrop(value / (imageSize.width / imageSize.height))
                    }, label = { Text(label) })
                }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(NexusTokens.space16), horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                OutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                Button(onClick = { onSave(crop) }, enabled = !busy && imageSize.isSpecified, modifier = Modifier.weight(1f)) {
                    Text(if (busy) "Guardando…" else "Guardar copia")
                }
            }
        }
    }
}

/** Largest centred crop with [fractionRatio] (width/height measured in fractions of the image). */
private fun centredCrop(fractionRatio: Float): Rect {
    val w = if (fractionRatio >= 1f) 1f else fractionRatio
    val h = if (fractionRatio >= 1f) 1f / fractionRatio else 1f
    return Rect(Offset((1f - w) / 2, (1f - h) / 2), Size(w, h))
}

/** Moves one corner (0 TL, 1 TR, 2 BL, 3 BR), keeping the opposite one fixed and, if given, the proportion. */
private fun resizeCorner(start: Rect, corner: Int, dx: Float, dy: Float, fractionRatio: Float?): Rect {
    val min = .05f
    val anchorX = if (corner % 2 == 0) start.right else start.left
    val anchorY = if (corner < 2) start.bottom else start.top
    val signX = if (corner % 2 == 0) -1f else 1f
    val signY = if (corner < 2) -1f else 1f
    val maxW = if (signX < 0) anchorX else 1f - anchorX
    val maxH = if (signY < 0) anchorY else 1f - anchorY
    var w = (start.width + signX * dx).coerceIn(min, maxW)
    var h = (start.height + signY * dy).coerceIn(min, maxH)
    if (fractionRatio != null) {
        // Follow whichever side the finger moved more, then fit inside the image.
        if (abs(dx) >= abs(dy)) h = w / fractionRatio else w = h * fractionRatio
        if (h > maxH) { h = maxH; w = h * fractionRatio }
        if (w > maxW) { w = maxW; h = w / fractionRatio }
    }
    val left = if (signX < 0) anchorX - w else anchorX
    val top = if (signY < 0) anchorY - h else anchorY
    return Rect(left, top, left + w, top + h)
}

/** Loads [uri] (local file, content or web link, rotation already applied) and writes the [crop] part to a temporary JPEG. */
internal suspend fun cropToTempFile(context: Context, uri: String, crop: Rect): File = withContext(Dispatchers.IO) {
    val request = coil.request.ImageRequest.Builder(context).data(uri).allowHardware(false).size(4096).build()
    val drawable = coil.ImageLoader(context).execute(request).drawable ?: error("No se pudo abrir la imagen")
    val source = (drawable as? BitmapDrawable)?.bitmap ?: error("No se pudo leer la imagen")
    val x = (crop.left * source.width).toInt().coerceIn(0, source.width - 1)
    val y = (crop.top * source.height).toInt().coerceIn(0, source.height - 1)
    val w = (crop.width * source.width).toInt().coerceIn(1, source.width - x)
    val h = (crop.height * source.height).toInt().coerceIn(1, source.height - y)
    val cut = Bitmap.createBitmap(source, x, y, w, h)
    val file = File(context.cacheDir, "recortes/recorte-${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
    file.outputStream().use { check(cut.compress(Bitmap.CompressFormat.JPEG, 95, it)) { "No se pudo guardar el recorte" } }
    file
}
