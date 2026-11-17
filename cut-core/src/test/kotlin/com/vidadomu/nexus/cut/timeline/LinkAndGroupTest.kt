package com.vidadomu.nexus.cut.timeline

import com.vidadomu.nexus.cut.model.ClipKind
import com.vidadomu.nexus.cut.model.Rational
import com.vidadomu.nexus.cut.model.Timeline
import com.vidadomu.nexus.cut.model.TrackRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkAndGroupTest {
    private val fx = Fx().also { it.media("long", 3600) }

    private val linked = Timeline(
        videoTracks = listOf(vtrack("V1", clip("v", 0, 10, link = "L")), vtrack("V2")),
        audioTracks = listOf(atrack("A1", TrackRole.DIALOGUE, aclip("a", 0, 10, link = "L")), atrack("A2", TrackRole.SFX)),
    )

    @Test
    fun movingVideoMovesLinkedAudio() {
        val t = TimelineEditor.move(linked, fx.ctx, setOf("v"), "v", s(5), 0, EditMode.OVERWRITE).ok().timeline
        assertStart(t, "v", 5)
        assertStart(t, "a", 5)
    }

    @Test
    fun movingToAnotherTrackOnlyChangesTracksOfTheDraggedKind() {
        val t = TimelineEditor.move(linked, fx.ctx, setOf("v"), "v", 0, 1, EditMode.OVERWRITE).ok().timeline
        assertEquals("V2", t.trackOfClip("v")!!.id)
        assertEquals("A1", t.trackOfClip("a")!!.id)
    }

    @Test
    fun splitSplitsBothAndRightHalvesShareANewLink() {
        val t = TimelineEditor.split(linked, fx.ctx, setOf("v"), s(4)).ok().timeline
        val vRight = t.track("V1")!!.clips[1]
        val aRight = t.track("A1")!!.clips[1]
        assertEquals(s(4), vRight.timelineStartUs)
        assertEquals(s(4), aRight.timelineStartUs)
        assertNotNull(vRight.linkId)
        assertEquals(vRight.linkId, aRight.linkId)
        assertNotEquals("L", vRight.linkId)
        assertEquals("L", t.c("v").linkId)
        assertEquals("L", t.c("a").linkId)
    }

    @Test
    fun trimKeepsLinkedRelation() {
        val t = TimelineEditor.trim(linked, fx.ctx, "v", Edge.TAIL, -s(2), ripple = false).ok().timeline
        assertEquals(s(8), t.c("a").durationUs)
        val t2 = TimelineEditor.trim(t, fx.ctx, "a", Edge.HEAD, s(1), ripple = false).ok().timeline
        assertStart(t2, "v", 1)
        assertEquals(s(1), t2.c("v").sourceInUs)
    }

    @Test
    fun unlinkSeparatesAudio() {
        val t = TimelineEditor.unlink(linked, fx.ctx, setOf("v")).ok().timeline
        assertNull(t.c("a").linkId)
        val moved = TimelineEditor.move(t, fx.ctx, setOf("v"), "v", s(3), 0, EditMode.OVERWRITE).ok().timeline
        assertStart(moved, "v", 3)
        assertStart(moved, "a", 0)
    }

    @Test
    fun rippleThatWouldTearALinkIsRejected() {
        val t0 = Timeline(
            videoTracks = listOf(vtrack("V1", clip("v", 10, 10, link = "L"))),
            audioTracks = listOf(atrack("A1", TrackRole.DIALOGUE, aclip("a", 10, 10, link = "L"), syncLock = false)),
        )
        val r = TimelineEditor.place(t0, fx.ctx, listOf(Placement("V1", ClipKind.VIDEO, "long", 0, s(2))), s(5), EditMode.INSERT).rejected()
        assertTrue(r.reason.contains("vínculo"))
    }

    @Test
    fun syncGroupMovesWithInternalOffsets() {
        val t0 = Timeline(
            videoTracks = listOf(vtrack("V1", clip("img", 10, 5, group = "G"))),
            audioTracks = listOf(
                atrack("A1", TrackRole.AMBIENCE, aclip("amb", 10, 4, group = "G")),
                atrack("A2", TrackRole.FOLEY, aclip("fol", 11, 1, group = "G"), aclip("other", 20, 1)),
            ),
        )
        val t = TimelineEditor.move(t0, fx.ctx, setOf("fol"), "fol", s(3), 0, EditMode.OVERWRITE).ok().timeline
        assertStart(t, "img", 13)
        assertStart(t, "amb", 13)
        assertStart(t, "fol", 14)
        assertStart(t, "other", 20)
        val dissolved = TimelineEditor.dissolveSyncGroup(t, fx.ctx, setOf("img")).ok().timeline
        val single = TimelineEditor.move(dissolved, fx.ctx, setOf("fol"), "fol", s(1), 0, EditMode.OVERWRITE).ok().timeline
        assertStart(single, "img", 13)
        assertStart(single, "fol", 15)
    }

    @Test
    fun overwriteMoveTrimsWhatIsUnderneath() {
        val t0 = Timeline(videoTracks = listOf(vtrack("V1", clip("X", 0, 5), clip("Y", 10, 10, sourceIn = 50))))
        val r = TimelineEditor.move(t0, fx.ctx, setOf("X"), "X", s(12), 0, EditMode.OVERWRITE).ok()
        assertEquals(listOf(s(10) to s(12), s(12) to s(17), s(17) to s(20)), r.timeline.spans("V1"))
        assertEquals(s(57), r.timeline.track("V1")!!.clips[2].sourceInUs)
        assertValid(r.timeline, fx)
    }

    @Test
    fun insertMoveReordersAndKeepsSoundUnderTheMovedScene() {
        val t0 = Timeline(
            videoTracks = listOf(vtrack("V1", clip("A", 0, 10), clip("B", 10, 10, sourceIn = 100, link = "LB"), clip("C", 20, 10, sourceIn = 200))),
            audioTracks = listOf(
                atrack("A1", TrackRole.DIALOGUE, aclip("aB", 10, 10, sourceIn = 100, link = "LB")),
                atrack("A3", TrackRole.SFX, aclip("sfxC", 22, 2)),
            ),
        )
        val t = TimelineEditor.move(t0, fx.ctx, setOf("B"), "B", s(20), 0, EditMode.INSERT).ok().timeline
        assertEquals(listOf("A", "C", "B"), t.track("V1")!!.clips.map { it.id })
        assertStart(t, "C", 10)
        assertStart(t, "B", 20)
        assertStart(t, "aB", 20)
        assertStart(t, "sfxC", 12)
        assertValid(t, fx)
    }

    private val row = Timeline(
        videoTracks = listOf(vtrack("V1", clip("A", 0, 4, link = "LA"), clip("B", 4, 3, sourceIn = 100), clip("C", 7, 6, sourceIn = 200), clip("D", 13, 5, sourceIn = 300))),
        audioTracks = listOf(atrack("A1", TrackRole.DIALOGUE, aclip("aA", 0, 4, link = "LA"))),
    )

    private fun reorder(t: Timeline, id: String, delta: Number) = TimelineEditor.reorderSlot(t, fx.ctx, setOf(id), id, s(delta))!!
    private val stay = TimelineEditor.ReorderSlot(0, null)

    @Test
    fun reorderSlotWaitsForTheMiddleOfTheNeighbourAndNeverSplitsIt() {
        // A [0,4) hacia la derecha: su borde derecho tiene que pasar la mitad de B (5,5 s) para saltar.
        assertEquals(stay, reorder(row, "A", 0))
        assertEquals(stay, reorder(row, "A", 1.4))
        // Detrás de B: en el montaje resultante A empieza en 3 s (B ha ocupado su sitio).
        assertEquals(TimelineEditor.ReorderSlot(s(7), s(3)), reorder(row, "A", 1.6))
        // Entre medias no vuelve a su sitio ni parpadea: sigue detrás de B hasta pasar la mitad de C (10 s).
        for (d in listOf(2.0, 3.0, 3.5, 4.0, 4.5, 5.9)) assertEquals("delta $d", TimelineEditor.ReorderSlot(s(7), s(3)), reorder(row, "A", d))
        assertEquals(TimelineEditor.ReorderSlot(s(13), s(9)), reorder(row, "A", 6.1))
        // Más allá del final: detrás del último, aunque el inicio del bloque aún pise a D.
        assertEquals(TimelineEditor.ReorderSlot(s(18), s(14)), reorder(row, "A", 15))
        for (d in listOf(1.6, 3.0, 5.9, 6.1, 9.5, 12.0, 15.0, 40.0)) {
            val slot = reorder(row, "A", d)
            val t = TimelineEditor.move(row, fx.ctx, setOf("A"), "A", slot.deltaUs, 0, EditMode.INSERT).ok().timeline
            assertEquals("delta $d: ningún clip partido", 4, t.track("V1")!!.clips.size)
            assertEquals("delta $d: el audio enlazado va debajo", t.c("A").timelineStartUs, t.c("aA").timelineStartUs)
            if (slot.landsAtUs != null) assertEquals("delta $d: aterriza donde dice", slot.landsAtUs, t.c("A").timelineStartUs)
            assertValid(t, fx)
        }
        val t = TimelineEditor.move(row, fx.ctx, setOf("A"), "A", reorder(row, "A", 1.6).deltaUs, 0, EditMode.INSERT).ok().timeline
        assertEquals(listOf("B", "A", "C", "D"), t.track("V1")!!.clips.map { it.id })
        assertEquals(listOf(s(0) to s(3), s(3) to s(7), s(7) to s(13), s(13) to s(18)), t.spans("V1"))
    }

    @Test
    fun reorderSlotToTheLeftUsesTheLeftEdge() {
        // D [13,18) hacia la izquierda: su borde izquierdo manda. Mitad de C = 10 s, mitad de B = 5,5 s.
        assertEquals(stay, reorder(row, "D", -2.9))
        assertEquals(TimelineEditor.ReorderSlot(s(-6), s(7)), reorder(row, "D", -3.1))
        assertEquals(TimelineEditor.ReorderSlot(s(-6), s(7)), reorder(row, "D", -7.4))
        assertEquals(TimelineEditor.ReorderSlot(s(-9), s(4)), reorder(row, "D", -7.6))
        assertEquals(TimelineEditor.ReorderSlot(s(-13), s(0)), reorder(row, "D", -30))
        for (d in listOf(-3.1, -5.0, -7.4, -7.6, -11.0, -12.9, -30.0)) {
            val slot = reorder(row, "D", d)
            val t = TimelineEditor.move(row, fx.ctx, setOf("D"), "D", slot.deltaUs, 0, EditMode.INSERT).ok().timeline
            assertEquals("delta $d: ningún clip partido", 4, t.track("V1")!!.clips.size)
            assertEquals("delta $d: aterriza donde dice", slot.landsAtUs, t.c("D").timelineStartUs)
            assertValid(t, fx)
        }
        val t = TimelineEditor.move(row, fx.ctx, setOf("D"), "D", reorder(row, "D", -7.6).deltaUs, 0, EditMode.INSERT).ok().timeline
        assertEquals(listOf("A", "D", "B", "C"), t.track("V1")!!.clips.map { it.id })
    }

    @Test
    fun reorderSlotPlacesFreelyOverAGapAndRejectsScatteredSelections() {
        val gap = Timeline(videoTracks = listOf(vtrack("V1", clip("A", 0, 4), clip("B", 4, 3, sourceIn = 100), clip("C", 20, 20, sourceIn = 200))))
        // Pasado B y con el inicio del bloque ya en el hueco [7,20): colocación libre, sin encaje.
        assertEquals(TimelineEditor.ReorderSlot(s(9), null), reorder(gap, "A", 9))
        // El borde derecho está en el hueco pero el inicio aún pisa a B: justo detrás de B, sin volver atrás.
        for (d in listOf(1.6, 3.5, 5.0, 7.0)) assertEquals("delta $d", TimelineEditor.ReorderSlot(s(7), s(3)), reorder(gap, "A", d))
        // El inicio entra en C sin haber pasado su mitad (30 s): pegado delante de C, no dentro.
        assertEquals(TimelineEditor.ReorderSlot(s(20), s(16)), reorder(gap, "A", 22))
        assertEquals(TimelineEditor.ReorderSlot(s(40), s(36)), reorder(gap, "A", 26.1))
        for (d in listOf(5.0, 9.0, 14.0, 22.0, 26.1, 50.0)) {
            val t = TimelineEditor.move(gap, fx.ctx, setOf("A"), "A", reorder(gap, "A", d).deltaUs, 0, EditMode.INSERT).ok().timeline
            assertEquals("delta $d: ningún clip partido", 3, t.track("V1")!!.clips.size)
            assertValid(t, fx)
        }
        // Sin vecino debajo: menos que su propia longitud no mueve nada; más allá, cae libre en el hueco.
        val alone = Timeline(videoTracks = listOf(vtrack("V1", clip("A", 0, 4), clip("C", 20, 6, sourceIn = 200))))
        assertEquals(stay, reorder(alone, "A", 2))
        assertEquals(TimelineEditor.ReorderSlot(s(9), null), reorder(alone, "A", 9))
        assertEquals(TimelineEditor.ReorderSlot(s(-9), null), reorder(alone, "C", -9))
        assertNull(TimelineEditor.reorderSlot(row, fx.ctx, setOf("A", "C"), "A", s(3)))
        assertNull(TimelineEditor.reorderSlot(row, fx.ctx, setOf("nope"), "nope", s(3)))
    }

    @Test
    fun speedChangeRipplesAndAppliesToLinkedAudio() {
        val t0 = Timeline(
            videoTracks = listOf(vtrack("V1", clip("A", 0, 10, link = "L"), clip("B", 10, 10))),
            audioTracks = listOf(atrack("A1", TrackRole.DIALOGUE, aclip("aA", 0, 10, link = "L"))),
        )
        val t = TimelineEditor.setSpeed(t0, fx.ctx, "A", Rational(2, 1), ripple = true).ok().timeline
        assertEquals(s(5), t.c("A").durationUs)
        assertEquals(s(5), t.c("aA").durationUs)
        assertEquals(Rational(2, 1), t.c("aA").speed)
        assertEquals(s(10), t.c("A").sourceOutUs)
        assertStart(t, "B", 5)
        TimelineEditor.setSpeed(t0, fx.ctx, "A", Rational(1, 2), ripple = false).rejected()
    }

    @Test
    fun duplicatePlacesCopyAfterSelectionWithItsOwnLink() {
        val t = TimelineEditor.duplicate(linked, fx.ctx, setOf("v"), EditMode.INSERT).ok().timeline
        val v2 = t.track("V1")!!.clips[1]
        val a2 = t.track("A1")!!.clips[1]
        assertEquals(s(10), v2.timelineStartUs)
        assertEquals(v2.linkId, a2.linkId)
        assertNotEquals("L", v2.linkId)
    }
}
