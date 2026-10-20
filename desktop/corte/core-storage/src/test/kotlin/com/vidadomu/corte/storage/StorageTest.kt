package com.vidadomu.corte.storage

import com.vidadomu.corte.model.Clip
import com.vidadomu.corte.model.ClipKind
import com.vidadomu.corte.model.MediaKind
import com.vidadomu.corte.model.MediaRef
import com.vidadomu.corte.model.Project
import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.Track
import com.vidadomu.corte.model.TrackKind
import com.vidadomu.corte.model.TrackRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun project(name: String = "Corto") = Project(
        id = "p1", name = name,
        media = listOf(MediaRef("m1", "content://x", "x.mp4", MediaKind.VIDEO, 10_000_000, 1920, 1080)),
        timeline = Timeline(
            videoTracks = listOf(Track("v1", TrackKind.VIDEO, "V1", TrackRole.PICTURE, listOf(Clip("c1", ClipKind.VIDEO, "m1", 0, 5_000_000)))),
        ),
    )

    @Test
    fun roundTrip() {
        val p = project()
        assertEquals(p, ProjectCodec.decode(ProjectCodec.encode(p)))
    }

    @Test
    fun saveKeepsPreviousVersionAndRecoversFromCorruption() {
        val store = ProjectStore(tmp.root)
        store.save(project("v1"))
        store.save(project("v2"))
        assertEquals("v2", store.load("p1").project.name)
        // Simulate a torn/corrupted primary file.
        store.fileOf("p1").writeText("{ broken")
        val l = store.load("p1")
        assertTrue(l.recoveredFromBackup)
        assertEquals("v1", l.project.name)
        assertTrue(store.list().single().error!!.contains("copia"))
    }

    @Test
    fun invalidTimelineIsRejectedOnLoad() {
        val bad = project().let { p ->
            val t = p.timeline.videoTracks[0]
            p.copy(timeline = Timeline(videoTracks = listOf(t.copy(clips = t.clips + Clip("c2", ClipKind.VIDEO, "m1", 1_000_000, 5_000_000)))))
        }
        try {
            ProjectCodec.decode(ProjectCodec.encode(bad))
            throw AssertionError("expected failure")
        } catch (e: ProjectFormatException) {
            assertTrue(e.message!!.contains("overlaps"))
        }
    }

    @Test
    fun waveformPeaksAndCacheFile() {
        val sr = 48_000
        val b = WaveformBuilder(sr, 2)
        val chunk = ShortArray(2 * 1000)
        var frame = 0
        repeat(48) {
            for (i in 0 until 1000) {
                val v = (Math.sin(2 * Math.PI * 440 * (frame + i) / sr) * (if (frame + i < sr / 2) 16000 else 4000)).toInt().toShort()
                chunk[2 * i] = v; chunk[2 * i + 1] = v
            }
            frame += 1000
            b.feed(chunk)
        }
        val w = b.build()
        assertEquals(48_000L, w.totalFrames)
        assertTrue(w.levels.size > 2)
        val f = tmp.newFile("w.cwf")
        w.writeTo(f)
        val r = Waveform.readFrom(f)
        val mn = FloatArray(2); val mx = FloatArray(2)
        r.fill(0, 48_000, 2, mn, mx)
        assertEquals(16000 / 32767f, mx[0], 0.01f)
        assertEquals(4000 / 32767f, mx[1], 0.01f)
    }
}
