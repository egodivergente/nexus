package com.vidadomu.corte.timeline.render

import com.vidadomu.corte.model.AnimParam
import com.vidadomu.corte.model.Clip
import com.vidadomu.corte.model.ClipKind
import com.vidadomu.corte.model.TextProps
import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.Track
import com.vidadomu.corte.model.TrackKind
import com.vidadomu.corte.model.Transition
import com.vidadomu.corte.model.TransitionKind

/** Per-frame layer state; everything a compositor needs, already including fades and transitions. */
data class LayerState(
    val alpha: Float,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
) {
    companion object {
        val HIDDEN = LayerState(0f)
    }
}

private class ClipInfo(val clip: Clip, val track: Track, val incoming: Transition?, val outgoing: Transition?)

private fun index(timeline: Timeline, kind: TrackKind): Map<String, ClipInfo> {
    val out = HashMap<String, ClipInfo>()
    for (t in timeline.allTracks) {
        if (t.kind != kind) continue
        for (c in t.clips) {
            out[c.id] = ClipInfo(c, t, t.transitions.firstOrNull { it.rightClipId == c.id }, t.transitions.firstOrNull { it.leftClipId == c.id })
        }
    }
    return out
}

private fun ramp(t: Long, from: Long, length: Long): Double =
    if (length <= 0) 1.0 else ((t - from).toDouble() / length).coerceIn(0.0, 1.0)

/**
 * Opacity (static or keyframed, times the clip's own fades) and transform of a visual clip at
 * timeline time [timeUs]. Transitions are applied on top of this by [VideoEvaluator].
 */
fun clipLayer(c: Clip, timeUs: Long): LayerState {
    var alpha = (c.keyframeValue(AnimParam.OPACITY, timeUs) ?: c.video.opacity)
    if (c.video.fadeInUs > 0 && timeUs < c.timelineStartUs + c.video.fadeInUs) alpha *= ramp(timeUs, c.timelineStartUs, c.video.fadeInUs)
    if (c.video.fadeOutUs > 0 && timeUs >= c.endUs - c.video.fadeOutUs) alpha *= 1 - ramp(timeUs, c.endUs - c.video.fadeOutUs, c.video.fadeOutUs)
    return LayerState(
        alpha = alpha.coerceIn(0.0, 1.0).toFloat(),
        positionX = (c.keyframeValue(AnimParam.POSITION_X, timeUs) ?: c.video.positionX).toFloat(),
        positionY = (c.keyframeValue(AnimParam.POSITION_Y, timeUs) ?: c.video.positionY).toFloat(),
        scale = (c.keyframeValue(AnimParam.SCALE, timeUs) ?: c.video.scale).toFloat(),
        rotationDeg = (c.keyframeValue(AnimParam.ROTATION, timeUs) ?: c.video.rotationDeg).toFloat(),
    )
}

/** Evaluates video layers for the compositor. Immutable; build a new one per timeline snapshot. */
class VideoEvaluator(timeline: Timeline, private val plan: RenderPlan) {
    private val clips = index(timeline, TrackKind.VIDEO)

    fun evaluate(laneIndex: Int, timeUs: Long): LayerState {
        val lane = plan.videoLanes.getOrNull(laneIndex) ?: return LayerState.HIDDEN
        val seg = lane.segmentAt(timeUs) ?: return LayerState.HIDDEN
        val info = clips[seg.clipId ?: return LayerState.HIDDEN] ?: return LayerState.HIDDEN
        val c = info.clip
        if (info.track.muted) return LayerState.HIDDEN
        val base = clipLayer(c, timeUs)
        var alpha = base.alpha.toDouble()
        val upper = lane.lane == 1
        info.incoming?.let { tr ->
            val cut = c.timelineStartUs
            when (tr.kind) {
                TransitionKind.CROSS_DISSOLVE -> if (upper) alpha *= ramp(timeUs, cut - tr.beforeCutUs, tr.durationUs)
                TransitionKind.DIP_TO_BLACK -> if (timeUs < cut + tr.afterCutUs) alpha *= ramp(timeUs, cut, tr.afterCutUs)
                TransitionKind.AUDIO_CROSSFADE -> Unit
            }
        }
        info.outgoing?.let { tr ->
            val cut = c.endUs
            when (tr.kind) {
                TransitionKind.CROSS_DISSOLVE -> if (upper) alpha *= 1 - ramp(timeUs, cut - tr.beforeCutUs, tr.durationUs)
                TransitionKind.DIP_TO_BLACK -> if (timeUs >= cut - tr.beforeCutUs) alpha *= 1 - ramp(timeUs, cut - tr.beforeCutUs, tr.beforeCutUs)
                TransitionKind.AUDIO_CROSSFADE -> Unit
            }
        }
        return base.copy(alpha = alpha.coerceIn(0.0, 1.0).toFloat())
    }
}

/** A title visible at some instant, with its evaluated layer state. */
data class TitleState(val clipId: String, val text: TextProps, val layer: LayerState)

/**
 * Titles ([ClipKind.TEXT] clips) are drawn by the app above all video layers (they are not render
 * lanes). Draw order is bottom-first: titles on V1 first, then V2, and so on.
 */
class TitleEvaluator(timeline: Timeline) {
    private val tracks = timeline.videoTracks.filter { t -> !t.muted && t.clips.any { it.kind == ClipKind.TEXT } }

    val isEmpty: Boolean get() = tracks.isEmpty()

    fun visibleAt(timeUs: Long): List<TitleState> {
        if (tracks.isEmpty()) return emptyList()
        val out = ArrayList<TitleState>(2)
        for (t in tracks) {
            val c = t.clipAt(timeUs) ?: continue
            if (c.kind != ClipKind.TEXT) continue
            val text = c.text ?: continue
            if (text.text.isBlank()) continue
            val layer = clipLayer(c, timeUs)
            if (layer.alpha <= 0f) continue
            out += TitleState(c.id, text, layer)
        }
        return out
    }
}

/** Evaluates audio gain (linear) and pan for the mixer. */
class AudioEvaluator(timeline: Timeline) {
    private val clips = index(timeline, TrackKind.AUDIO)
    private val anySolo = timeline.audioTracks.any { it.solo }

    fun gainAt(clipId: String, timeUs: Long): Float {
        val info = clips[clipId] ?: return 0f
        val c = info.clip
        val t = info.track
        if (t.muted || c.audio.muted || (anySolo && !t.solo)) return 0f
        val db = (c.keyframeValue(AnimParam.GAIN_DB, timeUs) ?: c.audio.gainDb) + t.gainDb
        var g = Math.pow(10.0, db / 20.0)
        if (c.audio.fadeInUs > 0 && timeUs < c.timelineStartUs + c.audio.fadeInUs) g *= equalPowerIn(ramp(timeUs, c.timelineStartUs, c.audio.fadeInUs))
        if (c.audio.fadeOutUs > 0 && timeUs >= c.endUs - c.audio.fadeOutUs) g *= equalPowerIn(1 - ramp(timeUs, c.endUs - c.audio.fadeOutUs, c.audio.fadeOutUs))
        info.incoming?.takeIf { it.kind == TransitionKind.AUDIO_CROSSFADE }?.let { tr ->
            g *= equalPowerIn(ramp(timeUs, c.timelineStartUs - tr.beforeCutUs, tr.durationUs))
        }
        info.outgoing?.takeIf { it.kind == TransitionKind.AUDIO_CROSSFADE }?.let { tr ->
            g *= equalPowerIn(1 - ramp(timeUs, c.endUs - tr.beforeCutUs, tr.durationUs))
        }
        return g.toFloat()
    }

    /** Balance law: centre is unity on both channels. Returns (left, right). */
    fun panOf(clipId: String): Pair<Float, Float> {
        val p = (clips[clipId]?.clip?.audio?.pan ?: 0.0).coerceIn(-1.0, 1.0)
        return (if (p > 0) 1 - p else 1.0).toFloat() to (if (p < 0) 1 + p else 1.0).toFloat()
    }

    /** Whether any gain for this clip can differ from a constant (used to skip work). */
    fun isAnimated(clipId: String): Boolean {
        val info = clips[clipId] ?: return false
        val c = info.clip
        return c.audio.fadeInUs > 0 || c.audio.fadeOutUs > 0 || c.keyframes.containsKey(AnimParam.GAIN_DB) ||
            info.incoming?.kind == TransitionKind.AUDIO_CROSSFADE || info.outgoing?.kind == TransitionKind.AUDIO_CROSSFADE
    }

    companion object {
        fun equalPowerIn(p: Double): Double = Math.sin(p.coerceIn(0.0, 1.0) * Math.PI / 2)
    }
}
