package com.vidadomu.nexus.cut.engine

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.vidadomu.nexus.cut.model.ClipKind
import com.vidadomu.nexus.cut.model.FitMode
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.model.Project
import com.vidadomu.nexus.cut.model.Rational
import com.vidadomu.nexus.cut.timeline.render.AudioEvaluator
import com.vidadomu.nexus.cut.timeline.render.CubeLut
import com.vidadomu.nexus.cut.timeline.render.Lane
import com.vidadomu.nexus.cut.timeline.render.isAllGap
import com.vidadomu.nexus.cut.timeline.render.RenderPlan
import com.vidadomu.nexus.cut.timeline.render.RenderPlanner
import com.vidadomu.nexus.cut.timeline.render.Segment
import com.vidadomu.nexus.cut.timeline.render.TitleEvaluator
import com.vidadomu.nexus.cut.timeline.render.VideoEvaluator

/**
 * Translates the project into a Media3 [Composition]. The same code feeds preview and export, so
 * what you see is what you export (except for the preview resolution).
 *
 * Mapping: every render lane (see [RenderPlanner]) becomes one sequence that covers the whole
 * timeline (clips + gaps), so all sequences stay time-aligned. Video sequences come first, top-most
 * lane first, because Media3's compositor draws its primary input (sequence 0) on top.
 * Per-frame opacity/position/scale come from [VideoEvaluator]; per-sample gain from [AudioEvaluator].
 */
@UnstableApi
object CompositionFactory {
    /** Logs every compositor query (diagnostics only). */
    @JvmField var DEBUG = false

    class Built(
        val composition: Composition,
        val plan: RenderPlan,
        val width: Int,
        val height: Int,
        val videoSequences: Int,
        val audioSequences: Int,
        /** Timeline time of the composition's 0 (the plan is a window starting there). */
        val fromUs: Long = 0,
        /** False when there is nothing to see (audio only). */
        val hasPicture: Boolean = videoSequences > 0,
    )

    /**
     * @param width/height output (or preview) frame size, even numbers.
     * @param maxFrameRate cap applied to every video item (export frame rate); null = source rate.
     */
    fun build(
        project: Project, isOnline: (String) -> Boolean, width: Int, height: Int, maxFrameRate: Int?,
        fromUs: Long = 0,
        luts: (String) -> CubeLut? = { null },
    ): Built? {
        val fullPlan = RenderPlanner.plan(project.timeline, isOnline)
        val titles = TitleEvaluator(project.timeline)
        if (fullPlan.durationUs <= 0 || (fullPlan.isEmpty && titles.isEmpty)) return null
        // Preview playback starts at the playhead by rendering a window of the plan (see
        // PreviewController for why). Export always uses fromUs = 0.
        val plan = fullPlan.window(fromUs.coerceAtLeast(0))
        if (plan.durationUs <= 0) return null
        val media = project.media.associateBy { it.id }
        val videoEval = VideoEvaluator(project.timeline, fullPlan)
        val audioEval = AudioEvaluator(project.timeline)
        val sequences = ArrayList<EditedMediaItemSequence>()
        val fps = maxFrameRate ?: project.settings.frameRate.fps.toInt().coerceAtLeast(1)
        // Compositor input i draws plan lane laneOfInput[i]; lanes with nothing left are skipped.
        val laneOfInput = plan.videoLanes.indices.filter { !plan.videoLanes[it].isAllGap }
        for (i in laneOfInput) sequences += videoSequence(project, media, plan.videoLanes[i], width, height, fps, maxFrameRate, luts)
        // Media3's Transformer switches to a single-input graph (which rejects compositor
        // settings, i.e. fades/opacity) when there is only one video sequence. Gap-only bottom
        // sequences keep the compositor in use and double as the black background.
        val laneCount = laneOfInput.size
        val backgrounds = when {
            fullPlan.videoLanes.isEmpty() && titles.isEmpty -> 0
            laneCount == 0 -> 2
            laneCount == 1 -> 1
            else -> 0
        }
        repeat(backgrounds) {
            sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).addGap(plan.durationUs).build()
        }
        for (lane in plan.audioLanes) if (!lane.isAllGap) sequences += audioSequence(media, lane, audioEval, fromUs)
        if (sequences.isEmpty()) return null

        val settings = object : VideoCompositorSettings {
            override fun getOutputSize(inputSizes: List<Size>): Size = Size(width, height)
            override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
                if (inputId >= laneCount) return if (inputId < laneCount + backgrounds) OPAQUE else HIDDEN
                val s = videoEval.evaluate(laneOfInput[inputId], presentationTimeUs + fromUs)
                if (DEBUG) android.util.Log.d("CorteComp", "in=$inputId t=$presentationTimeUs a=${s.alpha}")
                if (s.alpha <= 0f) return HIDDEN
                return StaticOverlaySettings.Builder()
                    .setAlphaScale(s.alpha)
                    .setScale(s.scale, s.scale)
                    .setRotationDegrees(s.rotationDeg)
                    // Position is in frame units; anchors are in NDC (-1..1, y up).
                    .setBackgroundFrameAnchor(2f * s.positionX, -2f * s.positionY)
                    .build()
            }
        }
        val builder = Composition.Builder(sequences).setVideoCompositorSettings(settings)
        // Titles go over the composited picture, so they sit above every video layer.
        if (!titles.isEmpty) {
            builder.setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(TitleOverlay(titles, width, height, fromUs))))))
        }
        return Built(
            builder.build(), plan, width, height, laneCount, plan.audioLanes.count { !it.isAllGap }, fromUs,
            hasPicture = laneCount > 0 || !titles.isEmpty,
        )
    }

    // Lazy: StaticOverlaySettings touches Android classes, which keeps the object loadable in JVM tests.
    private val HIDDEN: OverlaySettings by lazy { StaticOverlaySettings.Builder().setAlphaScale(0f).build() }
    private val OPAQUE: OverlaySettings by lazy { StaticOverlaySettings.Builder().build() }

    private fun videoSequence(
        project: Project, media: Map<String, MediaRef>, lane: Lane, w: Int, h: Int, fps: Int, maxFrameRate: Int?,
        luts: (String) -> CubeLut?,
    ): EditedMediaItemSequence {
        val b = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
        laneItems(primeLeadingGap(lane, media), media) { seg, ref ->
            val clip = project.timeline.clip(seg.clipId!!)!!
            val effects = ArrayList<Effect>()
            if (clip.video.flipH || clip.video.flipV) {
                effects += ScaleAndRotateTransformation.Builder()
                    .setScale(if (clip.video.flipH) -1f else 1f, if (clip.video.flipV) -1f else 1f).build()
            }
            // Colour before Presentation so letterbox bars stay black.
            effects += ColorEffects.media3(clip.video.color, clip.video.color.lutUri?.let(luts))
            effects += Presentation.createForWidthAndHeight(w, h, when (clip.video.fit) {
                FitMode.FIT -> Presentation.LAYOUT_SCALE_TO_FIT
                FitMode.FILL -> Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                FitMode.STRETCH -> Presentation.LAYOUT_STRETCH_TO_FIT
            })
            val item = editedItem(seg, ref, fps)
                .setRemoveAudio(true)
                .setEffects(Effects(emptyList(), effects))
            if (maxFrameRate != null || ref.kind == MediaKind.IMAGE) item.setFrameRate(fps)
            item.build()
        }.forEach { it.addTo(b) }
        return b.build()
    }

    /**
     * CompositionPlayer (Media3 1.11.1) stops delivering frames for a video sequence that starts
     * with a gap once it reaches the first real item (verified on device by PreviewProbeTest;
     * gaps in the middle or at the end are fine). So a lane that starts with a gap starts instead
     * with a "primer": the lane's own first clip, played under the gap with opacity 0 (the
     * compositor reads the original plan, where that time is still a gap). The primer spans the
     * whole leading gap when the source is long enough: a one-frame primer followed by the rest
     * of the gap still stalled on device (cause not established), the full-length one does not.
     */
    internal fun primeLeadingGap(lane: Lane, media: Map<String, MediaRef>): Lane {
        val first = lane.segments.firstOrNull() ?: return lane
        if (!first.isGap) return lane
        val donor = lane.segments.firstOrNull { !it.isGap && it.mediaId?.let(media::get) != null } ?: return lane
        val ref = media.getValue(donor.mediaId!!)
        val gapUs = first.durationUs
        val primer: Segment = if (ref.kind == MediaKind.IMAGE || donor.kind == ClipKind.IMAGE) {
            Segment(0L, gapUs, donor.clipId, donor.mediaId, donor.kind, 0L, Rational.ONE)
        } else {
            val total = ref.durationUs ?: return lane
            val len = minOf(gapUs, total)
            val start = if (donor.sourceStartUs + len <= total) donor.sourceStartUs else 0L
            Segment(0L, len, donor.clipId, donor.mediaId, donor.kind, start, Rational.ONE)
        }
        if (primer.durationUs <= 0) return lane
        val rest = gapUs - primer.durationUs
        val head = if (rest > 0) listOf(primer, Segment(primer.durationUs, rest)) else listOf(primer)
        return lane.copy(segments = head + lane.segments.drop(1))
    }

    private fun audioSequence(media: Map<String, MediaRef>, lane: Lane, eval: AudioEvaluator, fromUs: Long): EditedMediaItemSequence {
        val b = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        laneItems(lane, media) { seg, ref ->
            val clipId = seg.clipId!!
            val (l, r) = eval.panOf(clipId)
            val processors: List<AudioProcessor> = listOf(
                ClipAudioProcessor(seg.timelineStartUs + fromUs, { t -> eval.gainAt(clipId, t) }, eval.isAnimated(clipId), l, r),
            )
            editedItem(seg, ref, 30)
                .setRemoveVideo(true)
                .setEffects(Effects(processors, emptyList()))
                .build()
        }.forEach { it.addTo(b) }
        return b.build()
    }

    /** An item to append to a sequence: either media or a gap of the given length. */
    private sealed interface Piece {
        fun addTo(b: EditedMediaItemSequence.Builder)
        class Item(val item: EditedMediaItem) : Piece { override fun addTo(b: EditedMediaItemSequence.Builder) { b.addItem(item) } }
        class Gap(val durationUs: Long) : Piece { override fun addTo(b: EditedMediaItemSequence.Builder) { b.addGap(durationUs) } }
    }

    /**
     * Walks a lane producing items and gaps. Any difference between what Media3 will actually
     * play for an item (millisecond image durations, media shorter than its metadata) and the
     * planned duration is compensated in the following gap, so lanes never drift apart.
     */
    private fun laneItems(lane: Lane, media: Map<String, MediaRef>, make: (Segment, MediaRef) -> EditedMediaItem): List<Piece> {
        val out = ArrayList<Piece>()
        var debt = 0L // produced - planned so far
        for (seg in lane.segments) {
            val ref = seg.mediaId?.let { media[it] }
            if (seg.isGap || ref == null) {
                val d = seg.durationUs - debt
                if (d > 0) { out += Piece.Gap(d); debt = 0 } else debt = -d
                continue
            }
            out += Piece.Item(make(seg, ref))
            debt += producedUs(seg, ref) - seg.durationUs
            if (debt < 0) { out += Piece.Gap(-debt); debt = 0 }
        }
        return out
    }

    private fun producedUs(seg: Segment, ref: MediaRef): Long {
        if (ref.kind == MediaKind.IMAGE) return imageMs(seg) * 1000
        val start = seg.sourceStartUs.coerceAtLeast(0)
        val end = sourceEnd(seg, ref)
        return seg.speed.inverseScale(end - start).coerceAtLeast(0)
    }

    private fun imageMs(seg: Segment): Long = (seg.durationUs + 999) / 1000

    private fun sourceEnd(seg: Segment, ref: MediaRef): Long {
        val end = seg.sourceStartUs + seg.speed.scale(seg.durationUs)
        return ref.durationUs?.let { minOf(end, it) } ?: end
    }

    private fun editedItem(seg: Segment, ref: MediaRef, fps: Int): EditedMediaItem.Builder {
        val mi = MediaItem.Builder().setUri(Uri.parse(ref.uri)).setMediaId(seg.clipId!!)
        return if (ref.kind == MediaKind.IMAGE || seg.kind == ClipKind.IMAGE) {
            mi.setImageDurationMs(imageMs(seg))
            EditedMediaItem.Builder(mi.build()).setDurationUs(imageMs(seg) * 1000).setFrameRate(fps)
        } else {
            val start = seg.sourceStartUs.coerceAtLeast(0)
            mi.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionUs(start)
                    .setEndPositionUs(sourceEnd(seg, ref))
                    .build(),
            )
            val item = EditedMediaItem.Builder(mi.build()).setDurationUs(ref.durationUs ?: seg.durationUs)
            if (seg.speed != Rational.ONE) item.setSpeed(ConstantSpeed(seg.speed))
            item
        }
    }
}
