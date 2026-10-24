package com.vidadomu.nexus.inpaint

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

class MaskPaintView(context: Context) : View(context) {
    var image: Bitmap? = null
    var mask: Bitmap? = null
    var erasing = false
    var brush = 24f
    var stylusOnly = false
    var changed: (() -> Unit)? = null
    private val bounds = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private var previousX = 0f
    private var previousY = 0f
    private var drawing = false
    private val undo = ArrayDeque<Bitmap>()
    fun undoStroke() { if(undo.isNotEmpty()) { mask = undo.removeLast(); invalidate(); changed?.invoke() } }
    fun clearMask() { mask?.eraseColor(Color.TRANSPARENT); undo.clear(); invalidate(); changed?.invoke() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val source = image ?: return
        val ratio = minOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val drawWidth = source.width * ratio
        val drawHeight = source.height * ratio
        bounds.set((width - drawWidth) / 2, (height - drawHeight) / 2, (width + drawWidth) / 2, (height + drawHeight) / 2)
        canvas.drawBitmap(source, null, bounds, null)
        val overlay = Paint().apply { alpha = 140; colorFilter = PorterDuffColorFilter(Color.MAGENTA, PorterDuff.Mode.SRC_IN) }
        mask?.let { canvas.drawBitmap(it, null, bounds, overlay) }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val source = image ?: return false
        val target = mask ?: return false
        if(!isEnabled || event.pointerCount != 1 || bounds.width() <= 0) return false
        val tool = event.getToolType(0)
        if(stylusOnly && tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER) return false
        val imageX = ((event.x - bounds.left) * source.width / bounds.width()).coerceIn(0f, source.width - 1f)
        val imageY = ((event.y - bounds.top) * source.height / bounds.height()).coerceIn(0f, source.height - 1f)
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if(!bounds.contains(event.x, event.y)) return false
                if(undo.size == 3) undo.removeFirst().recycle()
                undo.addLast(target.copy(Bitmap.Config.ARGB_8888, true))
                previousX = imageX; previousY = imageY; drawing = true
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if(!drawing) return false
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                drawing = false
                parent?.requestDisallowInterceptTouchEvent(false)
                changed?.invoke()
                performClick()
                return true
            }
            else -> return false
        }
        paint.color = Color.WHITE
        paint.strokeWidth = brush * source.width / bounds.width() * if(tool == MotionEvent.TOOL_TYPE_STYLUS) event.pressure.coerceIn(.25f, 1f) else 1f
        paint.xfermode = if(erasing || tool == MotionEvent.TOOL_TYPE_ERASER) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
        Canvas(target).apply {
            drawLine(previousX, previousY, imageX, imageY, paint)
            drawCircle(imageX, imageY, paint.strokeWidth / 2, paint)
        }
        previousX = imageX; previousY = imageY
        invalidate()
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
