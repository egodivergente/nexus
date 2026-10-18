package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.AnimParam
import com.vidadomu.corte.model.AudioProps
import com.vidadomu.corte.model.Clip
import com.vidadomu.corte.model.ClipKind
import com.vidadomu.corte.model.Interpolation
import com.vidadomu.corte.model.Keyframe
import com.vidadomu.corte.model.KeyframeTrack
import com.vidadomu.corte.model.Marker
import com.vidadomu.corte.model.Rational
import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.Track
import com.vidadomu.corte.model.TrackKind
import com.vidadomu.corte.model.TrackRole
import com.vidadomu.corte.model.Transition
import com.vidadomu.corte.model.TransitionKind
import com.vidadomu.corte.model.TextProps
import com.vidadomu.corte.model.VideoProps
import com.vidadomu.corte.timeline.ClipOps.fmt
import com.vidadomu.corte.timeline.ClipOps.label

/** A clip to be created by [TimelineEditor.place]. [offsetUs] is relative to the edit point. */
data class Placement(
    val trackId: String,
    val kind: ClipKind,
    val mediaId: String,
    val sourceInUs: Long,
    val durationUs: Long,
    val offsetUs: Long = 0,
    val speed: Rational = Rational.ONE,
    val name: String = "",
    /** Placements sharing a key are linked together. */
    val linkKey: String? = null,
    val syncGroupKey: String? = null,
    val video: VideoProps = VideoProps(),
    val audio: AudioProps = AudioProps(),
    val keyframes: Map<AnimParam, KeyframeTrack> = emptyMap(),
    val text: TextProps? = null,
)

/**
 * Pure, UI-independent edit operations. Each returns a new timeline plus the invertible change set
 * (for undo), or a rejection with a human-readable reason. Inputs are never mutated.
 */
object TimelineEditor {

    // ---------------------------------------------------------------- placement

    fun place(timeline: Timeline, ctx: EditContext, placements: List<Placement>, atUs: Long, mode: EditMode): EditResult =
        Engine.run(timeline, ctx, if (mode == EditMode.INSERT) "Insertar" else "Sobrescribir") { w ->
            if (placements.isEmpty()) reject("Nada que insertar")
            if (atUs < 0) reject("Posición negativa")
            for (p in placements) {
                val t = w.track(p.trackId)
                if (t.header.locked) reject("${t.header.name} está bloqueada.")
                if (p.kind.trackKind != t.kind) reject("Un clip ${p.kind} no puede ir en una pista ${t.kind}.")
                if (p.durationUs <= 0 || p.offsetUs < 0) reject("Duración inválida")
            }
            val spanUs = placements.maxOf { it.offsetUs + it.durationUs }
            val targets = placements.map { it.trackId }.toSet()
            if (mode == EditMode.INSERT) {
                Ripple.insertSpace(w, atUs, spanUs, emptySet(), targets)
                shiftMarkersForInsert(w, atUs, spanUs)
            } else {
                for (p in placements) Ripple.clearRange(w, w.track(p.trackId), atUs + p.offsetUs, atUs + p.offsetUs + p.durationUs, emptySet())
            }
            val linkIds = HashMap<String, String>()
            val groupIds = HashMap<String, String>()
            for (p in placements) {
                val clip = ClipOps.clampFades(
                    Clip(
                        id = w.newId("clip"),
                        kind = p.kind,
                        mediaId = p.mediaId,
                        timelineStartUs = atUs + p.offsetUs,
                        durationUs = p.durationUs,
                        sourceInUs = p.sourceInUs,
                        speed = p.speed,
                        linkId = p.linkKey?.let { k -> linkIds.getOrPut(k) { w.ctx.ids.next("link") } },
                        syncGroupId = p.syncGroupKey?.let { k -> groupIds.getOrPut(k) { w.ctx.ids.next("sync") } },
                        name = p.name,
                        video = p.video,
                        audio = p.audio,
                        keyframes = p.keyframes,
                        text = p.text,
                    ),
                )
                val t = w.track(p.trackId)
                t.clips += clip
                t.sort()
            }
            spanUs
        }

    // ---------------------------------------------------------------- split

    /**
     * Splits at [atUs]. With a selection: the selected clips and their link partners. Without one:
     * every clip under [atUs] on unlocked tracks.
     */
    fun split(timeline: Timeline, ctx: EditContext, clipIds: Set<String>, atUs: Long): EditResult =
        Engine.run(timeline, ctx, "Dividir") { w ->
            val targets: List<Pair<WorkingTrack, Clip>> = if (clipIds.isEmpty()) {
                w.tracks.filter { !it.header.locked }.mapNotNull { t ->
                    t.clips.firstOrNull { atUs > it.timelineStartUs && atUs < it.endUs }?.let { t to it }
                }
            } else {
                closure(w, clipIds, links = true, groups = false)
                    .map { w.require(it) }
                    .filter { (_, c) -> atUs > c.timelineStartUs && atUs < c.endUs }
            }
            if (targets.isEmpty()) reject("No hay ningún clip bajo el cursor para dividir.")
            for ((t, _) in targets) if (t.header.locked) reject("${t.header.name} está bloqueada.")
            for ((t, c) in targets) ClipOps.split(w, t, t.clip(c.id)!!, atUs)
            0
        }

    // ---------------------------------------------------------------- delete

    fun delete(timeline: Timeline, ctx: EditContext, clipIds: Set<String>, ripple: Boolean): EditResult =
        Engine.run(timeline, ctx, if (ripple) "Eliminar y cerrar hueco" else "Eliminar") { w ->
            val ids = closure(w, clipIds, links = true, groups = false)
            if (ids.isEmpty()) reject("Nada seleccionado.")
            val removed = ids.map { w.require(it) }
            for ((t, _) in removed) if (t.header.locked) reject("${t.header.name} está bloqueada.")
            for ((t, c) in removed) t.remove(c.id)
            if (ripple) {
                val targets = removed.map { it.first.id }.toSet()
                val intervals = mergeRanges(removed.map { it.second.timelineStartUs to it.second.endUs })
                for ((a, b) in intervals.asReversed()) {
                    Ripple.removeInterval(w, a, b, emptySet(), targets, allowCollateral = true)
                    shiftMarkersForRemoval(w, a, b)
                }
                intervals.sumOf { it.second - it.first }
            } else 0
        }

    /** Ripple-deletes the empty gap under [atUs] on [trackId]. Never cuts material on other tracks. */
    fun closeGap(timeline: Timeline, ctx: EditContext, trackId: String, atUs: Long): EditResult =
        Engine.run(timeline, ctx, "Cerrar hueco") { w ->
            val t = w.track(trackId)
            if (t.clips.any { it.contains(atUs) }) reject("Ahí no hay hueco.")
            val a = t.clips.filter { it.endUs <= atUs }.maxOfOrNull { it.endUs } ?: 0L
            val b = t.clips.filter { it.timelineStartUs > atUs }.minOfOrNull { it.timelineStartUs }
                ?: reject("No hay nada después del hueco.")
            Ripple.removeInterval(w, a, b, emptySet(), setOf(trackId), allowCollateral = false)
            shiftMarkersForRemoval(w, a, b)
            b - a
        }

    // ---------------------------------------------------------------- trim

    /**
     * Moves one edge of a clip (and the same edge of its link partners) by [deltaUs]; positive is
     * later in time. The delta is clamped to source media, minimum duration and, without ripple, the
     * neighbouring clips. With ripple the clip's start stays fixed for head trims (the content
     * slides) and everything after the edit point follows.
     */
    fun trim(timeline: Timeline, ctx: EditContext, clipId: String, edge: Edge, deltaUs: Long, ripple: Boolean): EditResult =
        Engine.run(timeline, ctx, if (ripple) "Recortar (ripple)" else "Recortar") { w ->
            val (anchorTrack, anchor) = w.require(clipId)
            val members = closure(w, setOf(clipId), links = true, groups = false).map { w.require(it) }
            for ((t, _) in members) if (t.header.locked) reject("${t.header.name} está bloqueada.")
            if (ripple) {
                val edgeTime = if (edge == Edge.HEAD) anchor.timelineStartUs else anchor.endUs
                for ((_, m) in members) {
                    val e = if (edge == Edge.HEAD) m.timelineStartUs else m.endUs
                    if (e != edgeTime) reject("El vídeo y el audio vinculados no tienen el borde alineado (J/L cut): desvincúlalos para recortar con ripple.")
                }
            }
            val memberIds = members.map { it.second.id }.toSet()
            var lo = Long.MIN_VALUE / 4
            var hi = Long.MAX_VALUE / 4
            for ((t, m) in members) {
                val min = ctx.minClipDurationUs
                val media = ctx.media(m.mediaId)
                if (edge == Edge.TAIL) {
                    lo = maxOf(lo, min - m.durationUs)
                    hi = minOf(hi, ClipOps.tailHandleUs(m, media))
                    if (!ripple) {
                        val next = t.clips.filter { it.timelineStartUs >= m.endUs && it.id !in memberIds }.minOfOrNull { it.timelineStartUs }
                        if (next != null) hi = minOf(hi, next - m.endUs)
                    }
                } else {
                    hi = minOf(hi, m.durationUs - min)
                    lo = maxOf(lo, -ClipOps.headHandleUs(m))
                    if (!ripple) {
                        val prev = t.clips.filter { it.endUs <= m.timelineStartUs && it.id !in memberIds }.maxOfOrNull { it.endUs } ?: 0L
                        lo = maxOf(lo, prev - m.timelineStartUs)
                    }
                }
            }
            val d = deltaUs.coerceIn(minOf(lo, 0), maxOf(hi, 0))
            if (d == 0L) reject(if (deltaUs == 0L) "Sin cambios." else "No se puede recortar más en esa dirección.")
            val targets = members.map { it.first.id }.toSet()
            if (!ripple) {
                for ((t, m) in members) t.replace(if (edge == Edge.TAIL) ClipOps.trimTailTo(m, m.endUs + d) else ClipOps.trimHeadTo(m, m.timelineStartUs + d))
            } else if (edge == Edge.TAIL) {
                val e = anchor.endUs
                for ((t, m) in members) t.replace(ClipOps.trimTailTo(m, m.endUs + d))
                if (d > 0) Ripple.insertSpace(w, e, d, memberIds, targets).also { shiftMarkersForInsert(w, e, d) }
                else Ripple.removeInterval(w, e + d, e, memberIds, targets, allowCollateral = true).also { shiftMarkersForRemoval(w, e + d, e) }
            } else {
                val s = anchor.timelineStartUs
                for ((t, m) in members) t.replace(ClipOps.shift(ClipOps.trimHeadTo(m, m.timelineStartUs + d), -d))
                if (d > 0) Ripple.removeInterval(w, s, s + d, memberIds, targets, allowCollateral = true).also { shiftMarkersForRemoval(w, s, s + d) }
                else Ripple.insertSpace(w, s, -d, memberIds, targets).also { shiftMarkersForInsert(w, s, -d) }
            }
            for ((t, _) in members) t.sort()
            check(anchorTrack.clip(clipId) != null)
            d
        }

    // ---------------------------------------------------------------- move

    /**
     * Moves the selection (plus link partners and sync-group members) by [deltaUs] and, for clips of
     * the dragged clip's kind, by [trackOffset] tracks. OVERWRITE replaces what is underneath;
     * INSERT extracts the material with ripple and inserts it at the destination (reorder).
     */
    fun move(
        timeline: Timeline,
        ctx: EditContext,
        clipIds: Set<String>,
        anchorClipId: String,
        deltaUs: Long,
        trackOffset: Int,
        mode: EditMode,
    ): EditResult = Engine.run(timeline, ctx, if (mode == EditMode.INSERT) "Mover (insertar)" else "Mover") { w ->
        val ids = closure(w, clipIds + anchorClipId, links = true, groups = true)
        val items = ids.map { w.require(it) }
        for ((t, _) in items) if (t.header.locked) reject("${t.header.name} está bloqueada.")
        val anchorKind = w.require(anchorClipId).first.kind
        fun destTrack(t: WorkingTrack): WorkingTrack {
            if (trackOffset == 0 || t.kind != anchorKind) return t
            val list = if (t.kind == TrackKind.VIDEO) w.video else w.audio
            val i = list.indexOf(t) + trackOffset
            if (i !in list.indices) reject("No hay pista de destino; añade una pista primero.")
            val d = list[i]
            if (d.header.locked) reject("${d.header.name} está bloqueada.")
            return d
        }
        val dests = items.map { (t, c) -> Triple(t, destTrack(t), c) }
        val minStart = items.minOf { it.second.timelineStartUs }
        if (mode == EditMode.OVERWRITE) {
            val d = maxOf(deltaUs, -minStart)
            if (d == 0L && trackOffset == 0) reject("Sin cambios.")
            for ((src, _, c) in dests) src.remove(c.id)
            for ((_, dst, c) in dests) {
                val moved = ClipOps.shift(c, d)
                Ripple.clearRange(w, dst, moved.timelineStartUs, moved.endUs, ids)
                dst.clips += moved
                dst.sort()
            }
            d
        } else {
            val ranges = mergeRanges(items.map { it.second.timelineStartUs to it.second.endUs })
            if (ranges.size != 1) reject("Para mover con inserción (ripple) la selección debe ser un bloque continuo.")
            val (a, b) = ranges.single()
            val len = b - a
            val p = maxOf(0L, a + deltaUs)
            val insertAt = when {
                p >= b -> p - len
                p <= a -> p
                else -> a
            }
            if (insertAt == a && trackOffset == 0) reject("Sin cambios.")
            for ((src, _, c) in dests) src.remove(c.id)
            val sources = dests.map { it.first.id }.toSet()
            Ripple.removeInterval(w, a, b, emptySet(), sources, allowCollateral = true)
            shiftMarkersForRemoval(w, a, b)
            val targets = dests.map { it.second.id }.toSet()
            Ripple.insertSpace(w, insertAt, len, emptySet(), targets)
            shiftMarkersForInsert(w, insertAt, len)
            for ((_, dst, c) in dests) {
                dst.clips += ClipOps.shift(c, insertAt - a)
                dst.sort()
            }
            insertAt - a
        }
    }

    /** Copies the selection (with links) right after it, keeping relative offsets. */
    fun duplicate(timeline: Timeline, ctx: EditContext, clipIds: Set<String>, mode: EditMode): EditResult {
        val ids = closure(WorkingTimeline(timeline, ctx), clipIds, links = true, groups = false)
        if (ids.isEmpty()) return EditResult.Rejected("Nada seleccionado.")
        val items = ids.mapNotNull { id -> timeline.trackOfClip(id)?.let { t -> t to t.clip(id)!! } }
        val start = items.minOf { it.second.timelineStartUs }
        val end = items.maxOf { it.second.endUs }
        val placements = items.map { (t, c) ->
            Placement(
                trackId = t.id, kind = c.kind, mediaId = c.mediaId, sourceInUs = c.sourceInUs, durationUs = c.durationUs,
                offsetUs = c.timelineStartUs - start, speed = c.speed, name = c.name, linkKey = c.linkId, syncGroupKey = c.syncGroupId,
                video = c.video, audio = c.audio, keyframes = c.keyframes, text = c.text,
            )
        }
        return when (val r = place(timeline, ctx, placements, end, mode)) {
            is EditResult.Applied -> r.copy(label = "Duplicar")
            is EditResult.Rejected -> r
        }
    }

    // ---------------------------------------------------------------- speed

    /** Constant speed. Linked audio gets the same speed (pitch preserved at render). */
    fun setSpeed(timeline: Timeline, ctx: EditContext, clipId: String, speed: Rational, ripple: Boolean): EditResult =
        Engine.run(timeline, ctx, "Velocidad") { w ->
            if (speed.num <= 0) reject("Velocidad inválida")
            if (speed < Rational(1, 16) || speed > Rational(16, 1)) reject("La velocidad debe estar entre 1/16x y 16x.")
            val members = closure(w, setOf(clipId), links = true, groups = false).map { w.require(it) }
            for ((t, _) in members) if (t.header.locked) reject("${t.header.name} está bloqueada.")
            val anchor = w.require(clipId).second
            for ((_, m) in members) if (m.timelineStartUs != anchor.timelineStartUs || m.durationUs != anchor.durationUs) {
                reject("Los clips vinculados no están alineados; desvincúlalos para cambiar la velocidad por separado.")
            }
            val span = anchor.sourceOutUs - anchor.sourceInUs
            val newDur = speed.inverseScale(span).coerceAtLeast(ctx.minClipDurationUs)
            val d = newDur - anchor.durationUs
            val ids = members.map { it.second.id }.toSet()
            val targets = members.map { it.first.id }.toSet()
            if (!ripple && d > 0) {
                for ((t, m) in members) {
                    val next = t.clips.filter { it.timelineStartUs >= m.endUs && it.id !in ids }.minOfOrNull { it.timelineStartUs }
                    if (next != null && next < m.timelineStartUs + newDur) reject("No cabe a esa velocidad: activa ripple o deja hueco detrás.")
                }
            }
            for ((t, m) in members) t.replace(ClipOps.clampFades(ClipParams.rescaleKeys(m, newDur).copy(speed = speed, durationUs = newDur)))
            if (ripple && d > 0) Ripple.insertSpace(w, anchor.endUs, d, ids, targets).also { shiftMarkersForInsert(w, anchor.endUs, d) }
            if (ripple && d < 0) Ripple.removeInterval(w, anchor.endUs + d, anchor.endUs, ids, targets, allowCollateral = true)
                .also { shiftMarkersForRemoval(w, anchor.endUs + d, anchor.endUs) }
            d
        }

    // ---------------------------------------------------------------- properties & keyframes

    /** Changes non-temporal properties. Timing, identity and grouping fields cannot be altered here. */
    fun updateClips(timeline: Timeline, ctx: EditContext, clipIds: Set<String>, label: String, transform: (Clip) -> Clip): EditResult =
        Engine.run(timeline, ctx, label) { w ->
            if (clipIds.isEmpty()) reject("Nada seleccionado.")
            for (id in clipIds) {
                val (t, c) = w.require(id)
                if (t.header.locked) reject("${t.header.name} está bloqueada.")
                val n = transform(c)
                t.replace(
                    ClipOps.clampFades(
                        n.copy(
                            id = c.id, kind = c.kind, mediaId = c.mediaId, timelineStartUs = c.timelineStartUs,
                            durationUs = c.durationUs, sourceInUs = c.sourceInUs, speed = c.speed,
                            linkId = c.linkId, syncGroupId = c.syncGroupId,
                        ),
                    ),
                )
            }
            0
        }

    fun setKeyframe(
        timeline: Timeline, ctx: EditContext, clipId: String, param: AnimParam, localTimeUs: Long, value: Double,
        interpolation: Interpolation = Interpolation.LINEAR,
    ): EditResult = updateClips(timeline, ctx, setOf(clipId), "Keyframe") { c ->
        val v = value.coerceIn(param.min, param.max)
        val track = (c.keyframes[param] ?: KeyframeTrack()).withKey(Keyframe(localTimeUs, v, interpolation))
        c.copy(keyframes = c.keyframes + (param to track))
    }

    fun removeKeyframe(timeline: Timeline, ctx: EditContext, clipId: String, param: AnimParam, localTimeUs: Long): EditResult =
        updateClips(timeline, ctx, setOf(clipId), "Quitar keyframe") { c ->
            val track = c.keyframes[param]?.withoutKeyAt(localTimeUs) ?: return@updateClips c
            c.copy(keyframes = if (track.isEmpty) c.keyframes - param else c.keyframes + (param to track))
        }

    /**
     * What a property slider does: on an animated parameter it sets (or adds) the keyframe at the
     * playhead; otherwise it changes the static value.
     */
    fun setParam(timeline: Timeline, ctx: EditContext, clipId: String, param: AnimParam, timelineUs: Long, value: Double): EditResult {
        val c = timeline.clip(clipId) ?: return EditResult.Rejected("Clip inexistente")
        if (!ClipParams.isAnimated(c, param)) {
            return updateClips(timeline, ctx, setOf(clipId), ClipParams.label(param)) { ClipParams.withStatic(it, param, value) }
        }
        if (!c.contains(timelineUs)) return EditResult.Rejected("${ClipParams.label(param)} está animado: coloca el cursor dentro del clip para editarlo.")
        val existing = c.keyframes[param]?.keys?.firstOrNull { it.timeUs == timelineUs - c.timelineStartUs }
        return setKeyframe(timeline, ctx, clipId, param, timelineUs - c.timelineStartUs, value, existing?.interpolation ?: Interpolation.LINEAR)
    }

    /** The keyframe button: removes the key at the playhead, or adds one holding the current value. */
    fun toggleKeyframe(timeline: Timeline, ctx: EditContext, clipId: String, param: AnimParam, timelineUs: Long): EditResult {
        val c = timeline.clip(clipId) ?: return EditResult.Rejected("Clip inexistente")
        if (!c.contains(timelineUs)) return EditResult.Rejected("Coloca el cursor dentro del clip para poner un keyframe.")
        val local = timelineUs - c.timelineStartUs
        if (ClipParams.hasKeyAt(c, param, timelineUs)) return removeKeyframe(timeline, ctx, clipId, param, local)
        return setKeyframe(timeline, ctx, clipId, param, local, ClipParams.valueAt(c, param, timelineUs))
    }

    /** Sets how every keyframe at the playhead leaves towards the next one. */
    fun setKeyInterpolation(timeline: Timeline, ctx: EditContext, clipId: String, timelineUs: Long, interpolation: Interpolation): EditResult {
        val c = timeline.clip(clipId) ?: return EditResult.Rejected("Clip inexistente")
        val local = timelineUs - c.timelineStartUs
        if (c.keyframes.values.none { t -> t.keys.any { it.timeUs == local } }) return EditResult.Rejected("No hay keyframes en el cursor.")
        return updateClips(timeline, ctx, setOf(clipId), "Interpolación") { cl ->
            cl.copy(keyframes = cl.keyframes.mapValues { (_, t) ->
                KeyframeTrack(t.keys.map { if (it.timeUs == local) it.copy(interpolation = interpolation) else it })
            })
        }
    }

    fun clearKeyframes(timeline: Timeline, ctx: EditContext, clipId: String, param: AnimParam): EditResult =
        updateClips(timeline, ctx, setOf(clipId), "Quitar keyframes") { c -> c.copy(keyframes = c.keyframes - param) }

    // ---------------------------------------------------------------- links & sync groups

    fun link(timeline: Timeline, ctx: EditContext, clipIds: Set<String>): EditResult = Engine.run(timeline, ctx, "Vincular") { w ->
        val ids = closure(w, clipIds, links = true, groups = false)
        if (ids.size < 2) reject("Selecciona al menos dos clips para vincular.")
        val link = ctx.ids.next("link")
        for (id in ids) {
            val (t, c) = w.require(id)
            if (t.header.locked) reject("${t.header.name} está bloqueada.")
            t.replace(c.copy(linkId = link))
        }
        0
    }

    fun unlink(timeline: Timeline, ctx: EditContext, clipIds: Set<String>): EditResult = Engine.run(timeline, ctx, "Desvincular") { w ->
        val ids = closure(w, clipIds, links = true, groups = false)
        if (ids.none { w.require(it).second.linkId != null }) reject("Ningún clip seleccionado está vinculado.")
        for (id in ids) {
            val (t, c) = w.require(id)
            if (t.header.locked) reject("${t.header.name} está bloqueada.")
            t.replace(c.copy(linkId = null))
        }
        0
    }

    fun createSyncGroup(timeline: Timeline, ctx: EditContext, clipIds: Set<String>): EditResult =
        Engine.run(timeline, ctx, "Crear grupo de sincronía") { w ->
            val ids = closure(w, clipIds, links = true, groups = false)
            if (ids.size < 2) reject("Selecciona al menos dos clips.")
            val g = ctx.ids.next("sync")
            for (id in ids) {
                val (t, c) = w.require(id)
                if (t.header.locked) reject("${t.header.name} está bloqueada.")
                t.replace(c.copy(syncGroupId = g))
            }
            0
        }

    fun dissolveSyncGroup(timeline: Timeline, ctx: EditContext, clipIds: Set<String>): EditResult =
        Engine.run(timeline, ctx, "Deshacer grupo de sincronía") { w ->
            val groups = clipIds.mapNotNull { w.require(it).second.syncGroupId }.toSet()
            if (groups.isEmpty()) reject("Ningún clip seleccionado pertenece a un grupo.")
            for (t in w.tracks) for (c in t.clips.toList()) if (c.syncGroupId in groups) {
                if (t.header.locked) reject("${t.header.name} está bloqueada.")
                t.replace(c.copy(syncGroupId = null))
            }
            0
        }

    // ---------------------------------------------------------------- transitions

    fun setTransition(
        timeline: Timeline, ctx: EditContext, trackId: String, leftClipId: String, rightClipId: String,
        kind: TransitionKind, durationUs: Long,
    ): EditResult = Engine.run(timeline, ctx, "Transición") { w ->
        val t = w.track(trackId)
        if (t.header.locked) reject("${t.header.name} está bloqueada.")
        if (kind.trackKind != t.kind) reject("Esa transición no es para este tipo de pista.")
        val l = t.clip(leftClipId) ?: reject("Clip inexistente")
        val r = t.clip(rightClipId) ?: reject("Clip inexistente")
        if (l.endUs != r.timelineStartUs) reject("Las transiciones van en un corte entre dos clips contiguos.")
        val existing = t.transitions.firstOrNull { it.leftClipId == l.id && it.rightClipId == r.id }
        val fit = Transitions.fit(w, t, l, r, kind, durationUs, existing?.id)
            ?: reject(
                if (kind.needsHandles) "No hay material sobrante (handles) en los clips para un ${if (kind == TransitionKind.AUDIO_CROSSFADE) "crossfade" else "fundido encadenado"}: recorta el final de «${label(l)}» o el inicio de «${label(r)}»."
                else "Los clips son demasiado cortos para la transición.",
            )
        Transitions.describe(fit, durationUs)?.let { w.warnings += it }
        val tr = Transition(existing?.id ?: w.newId("tr"), l.id, r.id, kind, fit.durationUs, fit.alignment)
        if (existing != null) t.transitions.remove(existing)
        t.transitions += tr
        fit.durationUs
    }

    fun removeTransition(timeline: Timeline, ctx: EditContext, transitionId: String): EditResult =
        Engine.run(timeline, ctx, "Quitar transición") { w ->
            val t = w.tracks.firstOrNull { tr -> tr.transitions.any { it.id == transitionId } } ?: reject("Transición inexistente")
            if (t.header.locked) reject("${t.header.name} está bloqueada.")
            t.transitions.removeAll { it.id == transitionId }
            0
        }

    // ---------------------------------------------------------------- titles

    /**
     * Adds a title at [atUs] without moving anything else: it goes on the top-most unlocked titles
     * track that is free for its whole span, or on a new titles track placed on top of the stack.
     */
    fun addTitle(
        timeline: Timeline, ctx: EditContext, atUs: Long, durationUs: Long, text: TextProps,
        video: VideoProps = VideoProps(),
    ): EditResult = Engine.run(timeline, ctx, "Añadir texto") { w ->
        if (atUs < 0) reject("Posición negativa")
        if (durationUs <= 0) reject("Duración inválida")
        val end = atUs + durationUs
        val free = w.video.lastOrNull { t ->
            t.header.role == TrackRole.TITLES && !t.header.locked && t.clips.none { it.timelineStartUs < end && it.endUs > atUs }
        }
        val track = free ?: WorkingTrack(
            Track(id = w.newId("track"), kind = TrackKind.VIDEO, name = "V${w.video.size + 1} ${TrackRole.TITLES.label}", role = TrackRole.TITLES),
        ).also { w.video.add(it) }
        track.clips += Clip(
            id = w.newId("clip"), kind = ClipKind.TEXT, mediaId = "", timelineStartUs = atUs, durationUs = durationUs,
            name = text.text.lineSequence().first().take(40), video = video, text = text,
        )
        track.sort()
        0
    }

    // ---------------------------------------------------------------- tracks & markers

    fun addTrack(timeline: Timeline, ctx: EditContext, role: TrackRole, name: String? = null, index: Int? = null): EditResult =
        Engine.run(timeline, ctx, "Añadir pista") { w ->
            val list = if (role.kind == TrackKind.VIDEO) w.video else w.audio
            val prefix = if (role.kind == TrackKind.VIDEO) "V" else "A"
            val id = w.newId("track")
            val track = Track(id = id, kind = role.kind, name = name ?: "$prefix${list.size + 1} ${role.label}", role = role)
            list.add((index ?: list.size).coerceIn(0, list.size), WorkingTrack(track))
            0
        }

    fun removeTrack(timeline: Timeline, ctx: EditContext, trackId: String): EditResult = Engine.run(timeline, ctx, "Eliminar pista") { w ->
        val t = w.track(trackId)
        if (t.header.locked) reject("${t.header.name} está bloqueada.")
        val removedLinks = t.clips.mapNotNull { it.linkId }.toSet()
        (if (t.kind == TrackKind.VIDEO) w.video else w.audio).remove(t)
        // Partners of removed clips lose their link: a link with one member is meaningless.
        for (o in w.tracks) for (c in o.clips.toList()) if (c.linkId in removedLinks) o.replace(c.copy(linkId = null))
        0
    }

    /** Header changes: lock, mute, solo, sync lock, gain, name, span policy, collapsed. */
    fun updateTrack(timeline: Timeline, ctx: EditContext, trackId: String, label: String, transform: (Track) -> Track): EditResult =
        Engine.run(timeline, ctx, label) { w ->
            val t = w.track(trackId)
            val n = transform(t.header)
            t.header = ChangeSet.header(n.copy(id = t.header.id, kind = t.header.kind))
            0
        }

    fun addMarker(timeline: Timeline, ctx: EditContext, atUs: Long, label: String = ""): EditResult = Engine.run(timeline, ctx, "Marcador") { w ->
        w.markers += Marker(w.newId("marker"), atUs.coerceAtLeast(0), label)
        0
    }

    fun removeMarker(timeline: Timeline, ctx: EditContext, markerId: String): EditResult = Engine.run(timeline, ctx, "Quitar marcador") { w ->
        if (!w.markers.removeAll { it.id == markerId }) reject("Marcador inexistente")
        0
    }

    // ---------------------------------------------------------------- helpers

    /** Selection expanded with link partners and/or sync-group members, transitively. */
    internal fun closure(w: WorkingTimeline, ids: Set<String>, links: Boolean, groups: Boolean): Set<String> {
        val result = LinkedHashSet<String>()
        val queue = ArrayDeque(ids.filter { w.locate(it) != null })
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!result.add(id)) continue
            val c = w.locate(id)!!.second
            for (t in w.tracks) for (o in t.clips) {
                if (o.id in result) continue
                if ((links && c.linkId != null && o.linkId == c.linkId) || (groups && c.syncGroupId != null && o.syncGroupId == c.syncGroupId)) queue += o.id
            }
        }
        return result
    }

    internal fun mergeRanges(ranges: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        val sorted = ranges.sortedBy { it.first }
        val out = ArrayList<Pair<Long, Long>>()
        for (r in sorted) {
            val last = out.lastOrNull()
            if (last != null && r.first <= last.second) out[out.size - 1] = last.first to maxOf(last.second, r.second) else out += r
        }
        return out
    }

    private fun shiftMarkersForInsert(w: WorkingTimeline, atUs: Long, amountUs: Long) {
        w.markers.replaceAll { if (it.timeUs >= atUs) it.copy(timeUs = it.timeUs + amountUs) else it }
    }

    private fun shiftMarkersForRemoval(w: WorkingTimeline, aUs: Long, bUs: Long) {
        w.markers.replaceAll {
            when {
                it.timeUs >= bUs -> it.copy(timeUs = it.timeUs - (bUs - aUs))
                it.timeUs >= aUs -> it.copy(timeUs = aUs)
                else -> it
            }
        }
    }

    @Suppress("unused")
    private fun describe(us: Long) = fmt(us)
}
