package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.ClipKind
import com.vidadomu.corte.model.MediaKind
import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.TimelineValidator
import com.vidadomu.corte.model.TrackRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UndoAndPropertyTest {
    private val fx = Fx().also {
        it.media("long", 3600)
        it.media("music", 600, MediaKind.AUDIO)
    }

    private val scene = Timeline(
        videoTracks = listOf(vtrack("V1", clip("A", 0, 10, link = "LA"), clip("B", 10, 10, sourceIn = 100), clip("C", 20, 10, sourceIn = 200)), vtrack("V2", clip("O", 3, 4))),
        audioTracks = listOf(
            atrack("A1", TrackRole.DIALOGUE, aclip("aA", 0, 10, link = "LA"), aclip("dlg", 10, 20, sourceIn = 10)),
            atrack("A2", TrackRole.MUSIC, aclip("mus", 0, 32, media = "music")),
            atrack("A3", TrackRole.SFX, aclip("sfx1", 5, 2), aclip("sfx2", 24, 2)),
        ),
    )

    @Test
    fun undoAndRedoOfARippleAreExact() {
        val h = History()
        val r = TimelineEditor.place(scene, fx.ctx, listOf(Placement("V1", ClipKind.VIDEO, "long", 0, s(5))), s(20), EditMode.INSERT).ok()
        h.record(r)
        val (undone, label) = h.undo(r.timeline)!!
        assertEquals("Insertar", label)
        assertEquals(scene, undone)
        val (redone, _) = h.redo(undone)!!
        assertEquals(r.timeline, redone)
        assertFalse(h.canRedo)
    }

    @Test
    fun undoStoresChangesNotSnapshots() {
        val r = TimelineEditor.trim(scene, fx.ctx, "sfx1", Edge.TAIL, -s(1), ripple = false).ok()
        assertEquals(1, r.changes.changes.size)
        val ch = r.changes.changes.single() as Change.ClipUpdated
        assertEquals("sfx1", ch.before.id)
    }

    @Test
    fun newEditClearsRedo() {
        val h = History()
        val r1 = TimelineEditor.delete(scene, fx.ctx, setOf("sfx1"), ripple = false).ok()
        h.record(r1)
        val (u, _) = h.undo(r1.timeline)!!
        assertTrue(h.canRedo)
        h.record(TimelineEditor.delete(u, fx.ctx, setOf("sfx2"), ripple = false).ok())
        assertFalse(h.canRedo)
    }

    /**
     * Random edit sequences: after every edit the invariants hold and its change set is an exact
     * bidirectional diff; undoing everything restores the original exactly.
     */
    @Test
    fun randomEditSequencesKeepInvariantsAndUndoExactly() {
        repeat(25) { seed ->
            val rnd = Random(seed)
            val h = History()
            var t = scene
            val states = mutableListOf(t)
            repeat(40) {
                val r = randomEdit(rnd, t)
                if (r is EditResult.Applied) {
                    val v = TimelineValidator.violations(r.timeline) { fx.media[it] }
                    assertTrue("seed $seed ${r.label}: $v", v.isEmpty())
                    assertEquals("seed $seed ${r.label} forward", r.timeline, r.changes.applyTo(t))
                    assertEquals("seed $seed ${r.label} inverse", t, r.changes.inverse().applyTo(r.timeline))
                    h.record(r)
                    t = r.timeline
                    // No-op edits (e.g. setting a flag to its current value) are not recorded.
                    if (!r.changes.isEmpty) states += t
                }
            }
            var cur = t
            var i = states.size - 1
            while (h.canUndo) {
                cur = h.undo(cur)!!.first
                i--
                assertEquals("seed $seed undo step", states[i], cur)
            }
            assertEquals(scene, cur)
            while (h.canRedo) cur = h.redo(cur)!!.first
            assertEquals(t, cur)
        }
    }

    private fun randomEdit(rnd: Random, t: Timeline): EditResult {
        val clips = t.allTracks.flatMap { it.clips }
        val any = clips.randomOrNull(rnd)
        val time = s(rnd.nextInt(0, 400) / 10.0)
        val ripple = rnd.nextBoolean()
        return when (rnd.nextInt(9)) {
            0 -> TimelineEditor.place(
                t, fx.ctx,
                listOf(
                    Placement("V1", ClipKind.VIDEO, "long", s(rnd.nextInt(0, 100)), s(rnd.nextInt(1, 8)), linkKey = "k"),
                    Placement("A1", ClipKind.AUDIO, "long", s(rnd.nextInt(0, 100)), s(rnd.nextInt(1, 8)), linkKey = "k"),
                ),
                time, if (ripple) EditMode.INSERT else EditMode.OVERWRITE,
            )
            1 -> TimelineEditor.split(t, fx.ctx, any?.let { setOf(it.id) } ?: emptySet(), time)
            2 -> TimelineEditor.delete(t, fx.ctx, setOfNotNull(any?.id), ripple)
            3 -> if (any == null) EditResult.Rejected("none") else
                TimelineEditor.trim(t, fx.ctx, any.id, if (rnd.nextBoolean()) Edge.HEAD else Edge.TAIL, s(rnd.nextInt(-30, 30) / 10.0), ripple)
            4 -> if (any == null) EditResult.Rejected("none") else
                TimelineEditor.move(t, fx.ctx, setOf(any.id), any.id, s(rnd.nextInt(-50, 50) / 10.0), rnd.nextInt(-1, 2), if (ripple) EditMode.INSERT else EditMode.OVERWRITE)
            5 -> TimelineEditor.closeGap(t, fx.ctx, t.allTracks.random(rnd).id, time)
            6 -> if (any == null) EditResult.Rejected("none") else TimelineEditor.duplicate(t, fx.ctx, setOf(any.id), if (ripple) EditMode.INSERT else EditMode.OVERWRITE)
            7 -> {
                val track = t.videoTracks[0]
                val i = if (track.clips.size >= 2) rnd.nextInt(track.clips.size - 1) else -1
                val adjacent = i >= 0 && track.clips[i].endUs == track.clips[i + 1].timelineStartUs
                if (!adjacent) EditResult.Rejected("none") else TimelineEditor.setTransition(
                    t, fx.ctx, track.id, track.clips[i].id, track.clips[i + 1].id,
                    if (rnd.nextBoolean()) com.vidadomu.corte.model.TransitionKind.CROSS_DISSOLVE else com.vidadomu.corte.model.TransitionKind.DIP_TO_BLACK,
                    s(rnd.nextInt(1, 20) / 10.0),
                )
            }
            else -> TimelineEditor.updateTrack(t, fx.ctx, t.allTracks.random(rnd).id, "flags") { it.copy(syncLock = rnd.nextBoolean(), locked = rnd.nextInt(5) == 0) }
        }
    }
}
