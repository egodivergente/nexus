package com.vidadomu.nexus.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.max
import kotlin.math.sqrt

/**
 * A freehand mark. Points are (x, y) pairs: relative to the image (0–1 of its width and height) when the stroke belongs
 * to an image, so it follows the image when moved or resized; in board units (fractions of the board width) otherwise.
 */
@Serializable
internal data class BoardStroke(val points: List<Float>, val width: Float = .006f, val color: Long = 0xFFFF3B30)

/** Position and size are in board units (fractions of the board width); height follows the image's own proportion. */
@Serializable
internal data class BoardImage(val id: String, val uri: String, val name: String, val aspect: Float,
    val x: Float, val y: Float, val w: Float, val strokes: List<BoardStroke> = emptyList()) {
    val h: Float get() = w / aspect
}

@Serializable
internal data class ChatBoard(val id: String, val images: List<BoardImage>, val strokes: List<BoardStroke> = emptyList(),
    /** What the agent received last time; null until the board is sent once. */
    val sent: ChatBoardSent? = null) {
    val height: Float get() = max(.6f, (images.maxOfOrNull { it.y + it.h } ?: 0f) + GAP)
    val dirty: Boolean get() = sent == null || sent.images != images || sent.strokes != strokes
    fun markSent() = copy(sent = ChatBoardSent(images, strokes))

    companion object {
        const val GAP = .02f

        /** Initial grid, in the order the images were picked; the user rearranges from there. */
        fun arrange(id: String, images: List<Triple<String, String, Float>>): ChatBoard {
            val columns = when { images.size <= 1 -> 1; images.size <= 4 -> 2; images.size <= 9 -> 3; else -> 4 }
            val w = (1f - GAP * (columns + 1)) / columns
            var y = GAP
            val placed = images.chunked(columns).flatMap { row ->
                val rowHeight = row.maxOf { w / it.third }
                row.mapIndexed { index, (uri, name, aspect) ->
                    BoardImage(java.util.UUID.randomUUID().toString(), uri, name, aspect, GAP + index * (w + GAP), y, w)
                }.also { y += rowHeight + GAP }
            }
            return ChatBoard(id, placed)
        }
    }
}

@Serializable
internal data class ChatBoardSent(val images: List<BoardImage>, val strokes: List<BoardStroke>)

/** One private JSON file per board, written atomically. */
internal class ChatBoardStore(context: Context) {
    private val folder = File(context.filesDir, "assistant/boards").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    private fun file(id: String): File? = id.takeIf { Regex("[A-Za-z0-9_-]+").matches(it) }?.let { File(folder, "$it.json") }

    fun load(id: String): ChatBoard? = file(id)?.takeIf { it.isFile }?.let { runCatching { json.decodeFromString<ChatBoard>(it.readText()) }.getOrNull() }

    fun save(board: ChatBoard) {
        val target = AtomicFile(file(board.id) ?: return)
        val out = target.startWrite()
        try { out.write(json.encodeToString(board).toByteArray(Charsets.UTF_8)); target.finishWrite(out) }
        catch (error: Exception) { target.failWrite(out); throw error }
    }
}

/** What goes to the agent for one board: the board as the user sees it, each marked image, and a short note of what changed. */
internal data class BoardExport(val files: List<Uri>, val note: String)

internal object ChatBoardExport {
    /** Pixel budget for the flattened board, so a long board of many images stays within the phone's memory. */
    private const val BOARD_PIXELS = 6_000_000f
    private const val MAX_WIDTH = 1600f
    private const val MARKED_SIDE = 2048

    fun export(context: Context, board: ChatBoard, number: Int): BoardExport {
        val folder = File(context.cacheDir, "boards/${board.id}").apply { deleteRecursively(); mkdirs() }
        val files = mutableListOf<Uri>()
        val width = minOf(MAX_WIDTH, sqrt(BOARD_PIXELS / board.height)).toInt().coerceAtLeast(320)
        val height = (width * board.height).toInt().coerceAtLeast(1)
        val flat = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(flat)
            canvas.drawColor(android.graphics.Color.WHITE)
            board.images.forEach { image ->
                val rect = RectF(image.x * width, image.y * width, (image.x + image.w) * width, (image.y + image.h) * width)
                decode(context, image.uri, rect.width().toInt(), rect.height().toInt())?.let { bitmap ->
                    canvas.drawBitmap(bitmap, null, rect, Paint(Paint.FILTER_BITMAP_FLAG)); bitmap.recycle()
                }
                image.strokes.forEach { drawStroke(canvas, it, rect.left, rect.top, rect.width(), rect.height(), width.toFloat()) }
            }
            board.strokes.forEach { drawStroke(canvas, it, 0f, 0f, width.toFloat(), width.toFloat(), width.toFloat()) }
            files += save(flat, File(folder, "tablero-$number.jpg"))
        } finally { flat.recycle() }

        board.images.withIndex().filter { it.value.strokes.isNotEmpty() }.forEach { (index, image) ->
            val bitmap = decode(context, image.uri, MARKED_SIDE, MARKED_SIDE)?.copy(Bitmap.Config.ARGB_8888, true) ?: return@forEach
            try {
                val canvas = Canvas(bitmap)
                image.strokes.forEach { drawStroke(canvas, it, 0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), bitmap.width / image.w) }
                files += save(bitmap, File(folder, "tablero-$number-imagen-${index + 1}.jpg"))
            } finally { bitmap.recycle() }
        }
        return BoardExport(files, note(board, number))
    }

    /** Images are numbered in reading order (top to bottom, left to right), the same order the note uses. */
    internal fun note(board: ChatBoard, number: Int): String {
        val ordered = board.images.sortedWith(compareBy<BoardImage> { (it.y * 20).toInt() }.thenBy { it.x })
        fun label(image: BoardImage) = "${ordered.indexOf(image) + 1} (${image.name})"
        val lines = mutableListOf<String>()
        val previous = board.sent
        if (previous == null) {
            lines += "Tablero $number: ${board.images.size} imágenes en orden de lectura: " + ordered.joinToString(", ") { label(it) } + "."
            board.images.filter { it.strokes.isNotEmpty() }.takeIf { it.isNotEmpty() }?.let { marked ->
                lines += "Con marcas a mano: " + marked.joinToString(", ") { label(it) } + "; van también por separado."
            }
            if (board.strokes.isNotEmpty()) lines += "Hay marcas dibujadas sobre el tablero."
        } else {
            val before = previous.images.associateBy { it.id }
            board.images.forEach { image ->
                val old = before[image.id]
                when {
                    old == null -> lines += "Añadida la imagen ${label(image)}."
                    old.strokes != image.strokes -> lines += "Marcas nuevas en la imagen ${label(image)}."
                    old.x != image.x || old.y != image.y || old.w != image.w -> lines += "Movida o redimensionada la imagen ${label(image)}."
                }
            }
            previous.images.filter { old -> board.images.none { it.id == old.id } }.forEach { lines += "Quitada la imagen ${it.name}." }
            if (previous.strokes != board.strokes) lines += "Marcas nuevas sobre el tablero."
            lines.add(0, "Cambios en el tablero $number desde el último envío:")
        }
        return lines.joinToString("\n")
    }

    private fun decode(context: Context, uri: String, targetW: Int, targetH: Int): Bitmap? {
        val source = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(context, source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetW && bounds.outHeight / (sample * 2) >= targetH) sample *= 2
        return open(context, source)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }

    private fun open(context: Context, uri: Uri): java.io.InputStream? =
        if (uri.scheme == "file" || uri.scheme == null) uri.path?.let(::File)?.takeIf { it.isFile }?.inputStream()
        else context.contentResolver.openInputStream(uri)

    /** [unit] converts the stroke width (a fraction of the board width) to pixels. */
    private fun drawStroke(canvas: Canvas, stroke: BoardStroke, left: Float, top: Float, w: Float, h: Float, unit: Float) {
        if (stroke.points.size < 2) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color.toInt(); style = Paint.Style.STROKE; strokeWidth = max(2f, stroke.width * unit)
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val path = android.graphics.Path()
        stroke.points.chunked(2).forEachIndexed { index, (x, y) ->
            val px = left + x * w; val py = top + y * h
            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        if (stroke.points.size == 2) canvas.drawPoint(left + stroke.points[0] * w, top + stroke.points[1] * h, paint) else canvas.drawPath(path, paint)
    }

    private fun save(bitmap: Bitmap, file: File): Uri {
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) { "No se pudo guardar el tablero" } }
        return Uri.fromFile(file)
    }
}
