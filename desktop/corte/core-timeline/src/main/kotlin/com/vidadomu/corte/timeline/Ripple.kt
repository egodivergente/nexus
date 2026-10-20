package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.Clip
import com.vidadomu.corte.model.SpanPolicy
import com.vidadomu.corte.timeline.ClipOps.fmt
import com.vidadomu.corte.timeline.ClipOps.label

/**
 * The two ripple primitives. Every ripple edit (insert, delete, trim, insert-move, close gap) is a
 * composition of these, so the rules live in exactly one place:
 *
 * R1. Tracks with sync lock (plus the edit's own target tracks) follow the ripple. Others never move,
 *     and if they have material after the edit point the edit says so (warning): no silent desync.
 * R2. A locked track that follows the ripple is shifted as a whole but never cut. If the ripple would
 *     have to cut, split or extend one of its clips, the edit is rejected.
 * R3. A link group (video+audio of one file) moves rigidly. If some members would follow the ripple
 *     and others would not, the edit is rejected. Sync groups only produce a warning.
 * R4. A clip straddling the edit point is split, except unlinked clips on EXTEND tracks (beds), which
 *     are lengthened/shortened so they keep playing. Without media left, a bed is split (and reported).
 * R5. Keyframes are clip-relative, so shifting a clip shifts its automation with it.
 */
internal object Ripple {
    fun followers(w: WorkingTimeline, targets: Set<String>): Set<String> =
        w.tracks.filter { it.header.syncLock || it.id in targets }.map { it.id }.toSet()

    private fun checkGroups(w: WorkingTimeline, followers: Set<String>, atUs: Long, exclude: Set<String>) {
        val linkTracks = HashMap<String, MutableSet<Boolean>>()
        val groupTracks = HashMap<String, MutableSet<Boolean>>()
        val linkNames = HashMap<String, String>()
        for (t in w.tracks) for (c in t.clips) {
            if (c.id in exclude || c.endUs <= atUs) continue
            val follows = t.id in followers
            c.linkId?.let { linkTracks.getOrPut(it) { HashSet() } += follows; linkNames[it] = label(c) }
            c.syncGroupId?.let { groupTracks.getOrPut(it) { HashSet() } += follows }
        }
        // Members entirely before the edit point stay put on every track, which is consistent.
        for ((link, states) in linkTracks) {
            if (states.size > 1) {
                reject("El vínculo de «${linkNames[link]}» se rompería: una de sus pistas no sigue el ripple (sync lock desactivado). Activa su sync lock o desvincula.")
            }
        }
        for ((_, states) in groupTracks) {
            if (states.size > 1) w.warnings += "Un grupo de sincronía tiene miembros en pistas sin sync lock: sus offsets internos cambian."
        }
    }

    private fun warnUnfollowed(w: WorkingTimeline, followers: Set<String>, atUs: Long, amountUs: Long, exclude: Set<String>) {
        for (t in w.tracks) {
            if (t.id in followers) continue
            val n = t.clips.count { it.id !in exclude && it.endUs > atUs }
            if (n > 0) w.warnings += "${t.header.name}: sin sync lock, no se ha desplazado ($n clip(s) desfasados ${fmt(Math.abs(amountUs))})."
        }
    }

    /**
     * Opens [amountUs] of space at [atUs] on every follower track. Clips in [exclude] are untouched
     * (the caller handles them). Straddling clips on [targets] are always split.
     */
    fun insertSpace(w: WorkingTimeline, atUs: Long, amountUs: Long, exclude: Set<String>, targets: Set<String>) {
        require(amountUs > 0)
        val followers = followers(w, targets)
        checkGroups(w, followers, atUs, exclude)
        warnUnfollowed(w, followers, atUs, amountUs, exclude)
        for (t in w.tracks) {
            if (t.id !in followers) continue
            for (c in t.clips.toList()) {
                if (c.id in exclude) continue
                if (c.timelineStartUs >= atUs) {
                    t.replace(ClipOps.shift(c, amountUs))
                } else if (c.endUs > atUs) {
                    if (t.header.locked) reject("${t.header.name} está bloqueada y el ripple tendría que cortar «${label(c)}». Desbloquéala o desactiva su sync lock.")
                    val bed = t.header.effectiveSpanPolicy == SpanPolicy.EXTEND && c.linkId == null && t.id !in targets
                    if (bed && ClipOps.tailHandleUs(c, w.ctx.media(c.mediaId)) >= amountUs) {
                        t.replace(ClipOps.clampFades(c.copy(durationUs = c.durationUs + amountUs)))
                        w.collateral += Collateral(t.id, c.id, "alargado ${fmt(amountUs)}")
                    } else {
                        if (bed) w.warnings += "«${label(c)}» (${t.header.name}): no queda material para alargarlo; se ha dividido."
                        val (_, right) = ClipOps.split(w, t, c, atUs)
                        t.replace(ClipOps.shift(right, amountUs))
                        w.collateral += Collateral(t.id, c.id, "dividido en ${fmt(atUs)}")
                    }
                }
            }
            t.sort()
        }
    }

    /**
     * Removes the interval [aUs, bUs) from every follower track and closes it. With
     * [allowCollateral] false the edit is rejected instead of touching anything but beds.
     */
    fun removeInterval(
        w: WorkingTimeline,
        aUs: Long,
        bUs: Long,
        exclude: Set<String>,
        targets: Set<String>,
        allowCollateral: Boolean,
    ) {
        require(bUs > aUs)
        val len = bUs - aUs
        val followers = followers(w, targets)
        checkGroups(w, followers, aUs, exclude)
        warnUnfollowed(w, followers, aUs, -len, exclude)
        for (t in w.tracks) {
            if (t.id !in followers) continue
            val bedTrack = t.header.effectiveSpanPolicy == SpanPolicy.EXTEND
            for (c in t.clips.toList()) {
                if (c.id in exclude) continue
                if (c.endUs <= aUs) continue
                if (c.timelineStartUs >= bUs) {
                    t.replace(ClipOps.shift(c, -len)); continue
                }
                if (t.header.locked) reject("${t.header.name} está bloqueada y tiene material en el intervalo. Desbloquéala o desactiva su sync lock.")
                val spansAll = c.timelineStartUs < aUs && c.endUs > bUs
                val isBed = bedTrack && c.linkId == null && t.id !in targets
                if (!allowCollateral && !(spansAll && isBed)) {
                    reject("${t.header.name} tiene material en ese intervalo («${label(c)}»); cerrar el hueco lo cortaría.")
                }
                when {
                    spansAll && isBed -> {
                        t.replace(ClipOps.clampFades(c.copy(durationUs = c.durationUs - len)))
                        w.collateral += Collateral(t.id, c.id, "acortado ${fmt(len)}")
                    }
                    spansAll -> {
                        val (left, right) = ClipOps.split(w, t, c, bUs)
                        t.replace(ClipOps.trimTailTo(left, aUs))
                        t.replace(ClipOps.shift(right, -len))
                        w.collateral += Collateral(t.id, c.id, "cortado ${fmt(aUs)}–${fmt(bUs)}")
                    }
                    c.timelineStartUs >= aUs && c.endUs <= bUs -> {
                        t.remove(c.id)
                        w.collateral += Collateral(t.id, c.id, "eliminado")
                    }
                    c.timelineStartUs < aUs -> {
                        t.replace(ClipOps.trimTailTo(c, aUs))
                        w.collateral += Collateral(t.id, c.id, "recortado el final")
                    }
                    else -> {
                        t.replace(ClipOps.shift(ClipOps.trimHeadTo(c, bUs), -len))
                        w.collateral += Collateral(t.id, c.id, "recortado el inicio")
                    }
                }
            }
            t.sort()
        }
    }

    /** Clears [aUs, bUs) on one track without shifting anything (overwrite). */
    fun clearRange(w: WorkingTimeline, t: WorkingTrack, aUs: Long, bUs: Long, exclude: Set<String>) {
        if (bUs <= aUs) return
        for (c in t.clips.toList()) {
            if (c.id in exclude || c.endUs <= aUs || c.timelineStartUs >= bUs) continue
            if (t.header.locked) reject("${t.header.name} está bloqueada.")
            when {
                c.timelineStartUs < aUs && c.endUs > bUs -> {
                    val (left, right) = ClipOps.split(w, t, c, bUs)
                    t.replace(ClipOps.trimTailTo(left, aUs))
                    w.collateral += Collateral(t.id, c.id, "sobrescrito en parte")
                    check(right.timelineStartUs == bUs)
                }
                c.timelineStartUs >= aUs && c.endUs <= bUs -> {
                    t.remove(c.id); w.collateral += Collateral(t.id, c.id, "sobrescrito")
                }
                c.timelineStartUs < aUs -> {
                    t.replace(ClipOps.trimTailTo(c, aUs)); w.collateral += Collateral(t.id, c.id, "sobrescrito el final")
                }
                else -> {
                    t.replace(ClipOps.trimHeadTo(c, bUs)); w.collateral += Collateral(t.id, c.id, "sobrescrito el inicio")
                }
            }
        }
        t.sort()
    }

    @Suppress("unused")
    private fun Clip.debug() = "$id[$timelineStartUs,$endUs)"
}
