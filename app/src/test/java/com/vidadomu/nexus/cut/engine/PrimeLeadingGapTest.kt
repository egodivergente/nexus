package com.vidadomu.nexus.cut.engine

import androidx.media3.common.util.UnstableApi
import com.vidadomu.nexus.cut.model.ClipKind
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.model.TrackKind
import com.vidadomu.nexus.cut.timeline.render.Lane
import com.vidadomu.nexus.cut.timeline.render.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@UnstableApi
class PrimeLeadingGapTest {
    private val media = mapOf(
        "m" to MediaRef("m", "file:///m.mp4", "m", MediaKind.VIDEO, 3_000_000),
        "s" to MediaRef("s", "file:///s.mp4", "s", MediaKind.VIDEO, 400_000),
    )

    private fun lane(vararg s: Segment) = Lane("V2", 0, TrackKind.VIDEO, s.toList())

    @Test fun leadingGapIsReplacedByAHiddenSliceOfTheFirstClip() {
        val l = lane(Segment(0, 1_000_000), Segment(1_000_000, 1_000_000, "c1", "m", ClipKind.VIDEO, 500_000))
        val out = CompositionFactory.primeLeadingGap(l, media)
        assertEquals(2, out.segments.size)
        val p = out.segments[0]
        assertEquals("c1", p.clipId); assertEquals(0L, p.timelineStartUs); assertEquals(1_000_000L, p.durationUs); assertEquals(500_000L, p.sourceStartUs)
        assertEquals(l.segments[1], out.segments[1])
    }

    @Test fun shortSourceFillsWhatItCanAndKeepsTheRestAsGap() {
        val l = lane(Segment(0, 1_000_000), Segment(1_000_000, 300_000, "c1", "s", ClipKind.VIDEO, 100_000))
        val out = CompositionFactory.primeLeadingGap(l, media).segments
        assertEquals(listOf(0L, 400_000L, 1_000_000L), out.map { it.timelineStartUs })
        assertEquals(400_000L, out[0].durationUs); assertEquals(0L, out[0].sourceStartUs) // source tail too short: start at 0
        assertEquals(true, out[1].isGap); assertEquals(600_000L, out[1].durationUs)
        // Total length is unchanged, so lanes stay aligned.
        assertEquals(l.segments.sumOf { it.durationUs }, out.sumOf { it.durationUs })
    }

    @Test fun lanesWithoutLeadingGapAreUntouched() {
        val l = lane(Segment(0, 1_000_000, "c1", "m", ClipKind.VIDEO), Segment(1_000_000, 500_000))
        assertSame(l, CompositionFactory.primeLeadingGap(l, media))
    }

    @Test fun offlineOnlyLaneIsUntouched() {
        val l = lane(Segment(0, 1_000_000), Segment(1_000_000, 1_000_000, "c1", "missing", ClipKind.VIDEO))
        assertSame(l, CompositionFactory.primeLeadingGap(l, media))
    }
}
