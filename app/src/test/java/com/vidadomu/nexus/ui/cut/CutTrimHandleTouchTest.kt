package com.vidadomu.nexus.ui.cut

import com.vidadomu.nexus.cut.model.Clip
import com.vidadomu.nexus.cut.model.ClipKind
import com.vidadomu.nexus.cut.model.Timeline
import com.vidadomu.nexus.cut.model.Track
import com.vidadomu.nexus.cut.model.TrackKind
import com.vidadomu.nexus.cut.model.TrackRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The trim handles are drawn 11 dp wide but answer across 48 dp; the fade handles keep their place and size. */
class CutTrimHandleTouchTest {
    private val dp = 3f
    private val clip = Clip(id = "c", kind = ClipKind.VIDEO, mediaId = "m", timelineStartUs = 0, durationUs = 10_000_000)
    private val track = Track(id = "v1", kind = TrackKind.VIDEO, name = "V1", role = TrackRole.PICTURE, clips = listOf(clip))

    // 100 px per second and the playhead at 5 s in the middle: the clip runs from x = 40 to x = 1040.
    private val g = TimelineGeometry(
        Timeline(videoTracks = listOf(track)), widthPx = 1080f, rulerPx = 0f,
        videoRowPx = 60f * dp, audioRowPx = 40f * dp, collapsedRowPx = 20f * dp,
        playheadUs = 5_000_000, pxPerUs = 100.0 / 1_000_000, scrollY = 0f,
        handlePx = 16f * dp, touchPx = 24f * dp,
    )
    private val x0 = 40f
    private val x1 = 1040f
    private val low = 150f
    private val selected = setOf("c")

    @Test fun headAndTailAnswerAcross48dp() {
        assertEquals(Zone.HEAD, g.hit(x0 - 23f * dp, low, selected)?.zone)
        assertEquals(Zone.HEAD, g.hit(x0 + 23f * dp, low, selected)?.zone)
        assertEquals(Zone.TAIL, g.hit(x1 + 23f * dp, low, selected)?.zone)
        assertEquals(Zone.TAIL, g.hit(x1 - 23f * dp, low, selected)?.zone)
        assertEquals(Zone.BODY, g.hit(x0 + 25f * dp, low, selected)?.zone)
        assertNull(g.hit(x0 - 25f * dp, low, selected)?.clip)
    }

    @Test fun anUnselectedClipHasNoHandles() {
        assertEquals(Zone.BODY, g.hit(x0 + 5f * dp, low, emptySet())?.zone)
        assertNull(g.hit(x0 - 5f * dp, low, emptySet())?.clip)
    }

    @Test fun fadeHandlesKeepTheirZone() {
        val top = 30f
        val (fadeIn, fadeOut) = g.fadeHandleXs(clip)
        assertEquals(x0 + 16f * dp * 1.2f, fadeIn, 0.01f)
        assertEquals(Zone.FADE_IN, g.hit(fadeIn - 7f * dp, top, selected)?.zone)
        assertEquals(Zone.FADE_OUT, g.hit(fadeOut + 7f * dp, top, selected)?.zone)
        assertEquals(Zone.HEAD, g.hit(x0 + 9f * dp, top, selected)?.zone)
    }
}
