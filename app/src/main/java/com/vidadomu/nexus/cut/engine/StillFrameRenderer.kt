package com.vidadomu.nexus.cut.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.vidadomu.nexus.cut.model.FitMode
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.model.Project
import com.vidadomu.nexus.cut.timeline.render.CubeLut
import com.vidadomu.nexus.cut.timeline.render.RenderPlan
import com.vidadomu.nexus.cut.timeline.render.RenderPlanner
import com.vidadomu.nexus.cut.timeline.render.TitleEvaluator
import com.vidadomu.nexus.cut.timeline.render.VideoEvaluator
import java.io.Closeable
import java.io.IOException

/**
 * Composes the frame at one timeline instant on the CPU: the exact decoded source frame of every
 * visible video layer, drawn bottom-up with the same fit/flip/opacity/scale/rotation/position the
 * export compositor uses ([VideoEvaluator]), the same colour correction ([ColorEffects]) and the
 * same titles ([TitlePainter]).
 *
 * Why it exists: in Media3 1.11.1 a [androidx.media3.transformer.CompositionPlayer] built with
 * MultipleInputVideoGraph (needed for multitrack) never renders after a seek while paused and then
 * stays in BUFFERING (reproduced on device without Corte code, see PreviewProbeTest). So the paused
 * preview is drawn here and the player is only used for continuous playback.
 *
 * Not thread-safe: call from one worker thread. [close] releases the decoders.
 */
class StillFrameRenderer(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val retrievers = object : LinkedHashMap<String, MediaMetadataRetriever>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaMetadataRetriever>): Boolean {
            if (size <= MAX_RETRIEVERS) return false
            releaseQuietly(eldest.value)
            return true
        }
    }
    private val images = object : LinkedHashMap<String, Bitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>) = size > 4
    }
    private var planKey: Any? = null
    private var plan: RenderPlan? = null
    private var evaluator: VideoEvaluator? = null
    private var titles: TitleEvaluator? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    class Result(val bitmap: Bitmap, val timeUs: Long, val missing: List<String>)

    /**
     * @param exact true decodes the exact frame (slower); false uses the nearest sync frame, which
     * is what scrubbing needs to stay responsive.
     */
    fun render(
        project: Project, isOnline: (String) -> Boolean, timeUs: Long, width: Int, height: Int, exact: Boolean,
        luts: (String) -> CubeLut? = { null },
    ): Result {
        // The predicate is part of the key: a media going offline/online must re-plan the lanes.
        val key = Triple(project.timeline, project.media, isOnline)
        if (key != planKey) {
            val p = RenderPlanner.plan(project.timeline, isOnline)
            plan = p; evaluator = VideoEvaluator(project.timeline, p); titles = TitleEvaluator(project.timeline); planKey = key
        }
        val p = checkNotNull(plan); val eval = checkNotNull(evaluator)
        val out = Bitmap.createBitmap(width.coerceAtLeast(2), height.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val media = project.media.associateBy { it.id }
        val missing = ArrayList<String>()
        // Lane 0 is the top-most layer, so draw from the last lane up.
        for (laneIndex in p.videoLanes.indices.reversed()) {
            val lane = p.videoLanes[laneIndex]
            val seg = lane.segmentAt(timeUs) ?: continue
            if (seg.isGap) continue
            val ref = media[seg.mediaId] ?: continue
            val clip = project.timeline.clip(seg.clipId!!) ?: continue
            val state = eval.evaluate(laneIndex, timeUs)
            if (state.alpha <= 0f) continue
            val sourceUs = seg.sourceStartUs + seg.speed.scale(timeUs - seg.timelineStartUs)
            val decoded = frameOf(ref, sourceUs, out.width, out.height, exact)
            if (decoded == null) { missing += ref.displayName; continue }
            val color = clip.video.color
            val lut = color.lutUri?.let(luts)
            // Images are cached: correct a copy. Video frames are fresh: correct in place.
            val frame = if (ColorEffects.isNeutral(color, lut)) decoded
            else ColorEffects.applyCpu(if (ref.kind == MediaKind.IMAGE) decoded.copy(Bitmap.Config.ARGB_8888, true) else decoded, color, lut)
            val owned = ref.kind != MediaKind.IMAGE || frame !== decoded
            val m = Matrix()
            val bw = frame.width.toFloat(); val bh = frame.height.toFloat()
            val w = out.width.toFloat(); val h = out.height.toFloat()
            // Fit the frame into the output (Presentation in the export path).
            val (sx, sy) = when (clip.video.fit) {
                FitMode.FIT -> minOf(w / bw, h / bh).let { it to it }
                FitMode.FILL -> maxOf(w / bw, h / bh).let { it to it }
                FitMode.STRETCH -> (w / bw) to (h / bh)
            }
            m.postTranslate(-bw / 2, -bh / 2)
            m.postScale(sx * (if (clip.video.flipH) -1f else 1f), sy * (if (clip.video.flipV) -1f else 1f))
            // Layer transform: Media3 rotates counter-clockwise, Canvas clockwise.
            m.postScale(state.scale, state.scale)
            m.postRotate(-state.rotationDeg)
            m.postTranslate(w / 2 + state.positionX * w, h / 2 + state.positionY * h)
            paint.alpha = (state.alpha.coerceIn(0f, 1f) * 255).toInt()
            canvas.drawBitmap(frame, m, paint)
            if (owned) frame.recycle()
        }
        titles?.let { TitlePainter.draw(canvas, out.width, out.height, it.visibleAt(timeUs)) }
        return Result(out, timeUs, missing)
    }

    private fun frameOf(ref: MediaRef, sourceUs: Long, maxW: Int, maxH: Int, exact: Boolean): Bitmap? {
        if (ref.kind == MediaKind.IMAGE) return image(ref, maxW, maxH)
        val mmr = retriever(ref) ?: return null
        val option = if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        val t = sourceUs.coerceIn(0, maxOf(0, (ref.durationUs ?: Long.MAX_VALUE) - 1))
        return try {
            mmr.getScaledFrameAtTime(t, option, maxW, maxH)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "frame at $t of ${ref.displayName}", e); null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "frame at $t of ${ref.displayName}", e); null
        }
    }

    private fun image(ref: MediaRef, maxW: Int, maxH: Int): Bitmap? {
        images[ref.id]?.let { return it }
        return try {
            appContext.contentResolver.openInputStream(Uri.parse(ref.uri))?.use { s ->
                val sample = generateSequence(1) { it * 2 }.takeWhile { ref.width / (it * 2) >= maxW || ref.height / (it * 2) >= maxH }.lastOrNull() ?: 1
                BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }?.also { images[ref.id] = it }
        } catch (e: IOException) {
            Log.w(TAG, "image ${ref.displayName}", e); null
        } catch (e: SecurityException) {
            Log.w(TAG, "image ${ref.displayName}", e); null
        }
    }

    private fun retriever(ref: MediaRef): MediaMetadataRetriever? {
        retrievers[ref.id]?.let { return it }
        val m = MediaMetadataRetriever()
        return try {
            m.setDataSource(appContext, Uri.parse(ref.uri))
            retrievers[ref.id] = m
            m
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "open ${ref.displayName}", e); releaseQuietly(m); null
        } catch (e: SecurityException) {
            Log.w(TAG, "open ${ref.displayName}", e); releaseQuietly(m); null
        } catch (e: RuntimeException) {
            Log.w(TAG, "open ${ref.displayName}", e); releaseQuietly(m); null
        }
    }

    override fun close() {
        retrievers.values.forEach(::releaseQuietly)
        retrievers.clear()
        images.clear()
        planKey = null; plan = null; evaluator = null; titles = null
    }

    private fun releaseQuietly(m: MediaMetadataRetriever) {
        try { m.release() } catch (e: IOException) { Log.w(TAG, "release", e) }
    }

    private companion object {
        const val TAG = "StillFrame"
        const val MAX_RETRIEVERS = 6
    }
}
