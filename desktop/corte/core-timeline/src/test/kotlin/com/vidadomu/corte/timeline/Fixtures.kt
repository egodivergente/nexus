package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.Clip
import com.vidadomu.corte.model.ClipKind
import com.vidadomu.corte.model.IdGenerator
import com.vidadomu.corte.model.MediaKind
import com.vidadomu.corte.model.MediaRef
import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.TimelineValidator
import com.vidadomu.corte.model.Track
import com.vidadomu.corte.model.TrackKind
import com.vidadomu.corte.model.TrackRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

fun s(seconds: Number): Long = Math.round(seconds.toDouble() * 1_000_000)

class Fx {
    val media = HashMap<String, MediaRef>()
    val ctx = EditContext(media = { media[it] }, ids = IdGenerator.sequential())

    fun media(id: String, seconds: Number, kind: MediaKind = MediaKind.VIDEO): String {
        media[id] = MediaRef(id = id, uri = "content://$id", displayName = id, kind = kind, durationUs = s(seconds))
        return id
    }
}

fun clip(
    id: String, start: Number, dur: Number, media: String = "long", sourceIn: Number = 0,
    kind: ClipKind = ClipKind.VIDEO, link: String? = null, group: String? = null,
) = Clip(
    id = id, kind = kind, mediaId = media, timelineStartUs = s(start), durationUs = s(dur), sourceInUs = s(sourceIn),
    linkId = link, syncGroupId = group, name = id,
)

fun aclip(id: String, start: Number, dur: Number, media: String = "long", sourceIn: Number = 0, link: String? = null, group: String? = null) =
    clip(id, start, dur, media, sourceIn, ClipKind.AUDIO, link, group)

fun vtrack(id: String, vararg clips: Clip, locked: Boolean = false, syncLock: Boolean = true) =
    Track(id, TrackKind.VIDEO, id, TrackRole.PICTURE, clips.toList(), locked = locked, syncLock = syncLock)

fun atrack(id: String, role: TrackRole, vararg clips: Clip, locked: Boolean = false, syncLock: Boolean = true) =
    Track(id, TrackKind.AUDIO, id, role, clips.toList(), locked = locked, syncLock = syncLock)

fun EditResult.ok(): EditResult.Applied = when (this) {
    is EditResult.Applied -> this
    is EditResult.Rejected -> { fail("expected edit to apply but was rejected: $reason"); throw IllegalStateException() }
}

fun EditResult.rejected(): EditResult.Rejected = when (this) {
    is EditResult.Rejected -> this
    is EditResult.Applied -> { fail("expected rejection but edit applied: $label"); throw IllegalStateException() }
}

fun Timeline.c(id: String): Clip = clip(id) ?: throw AssertionError("no clip $id in timeline")

fun Timeline.spans(trackId: String): List<Pair<Long, Long>> = track(trackId)!!.clips.map { it.timelineStartUs to it.endUs }

fun assertStart(t: Timeline, id: String, seconds: Number) = assertEquals("start of $id", s(seconds), t.c(id).timelineStartUs)

fun assertValid(t: Timeline, fx: Fx) {
    val v = TimelineValidator.violations(t) { fx.media[it] }
    assertTrue("invariants violated: $v", v.isEmpty())
}
