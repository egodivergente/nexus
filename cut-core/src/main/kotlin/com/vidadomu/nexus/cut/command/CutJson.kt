package com.vidadomu.nexus.cut.command

import com.vidadomu.nexus.cut.model.Clip
import com.vidadomu.nexus.cut.model.ClipKind
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.model.Project
import com.vidadomu.nexus.cut.model.Track
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Forma estable y compacta en la que las consultas describen el montaje (tiempos en microsegundos). */
object CutJson {
    fun project(s: CutState): JsonObject = buildJsonObject {
        val p = s.project
        put("project_id", p.id); put("name", p.name); put("revision", p.revision)
        put("width", p.settings.width); put("height", p.settings.height); put("fps", p.settings.frameRate.toString())
        put("duration_us", p.timeline.durationUs)
        put("video_tracks", p.timeline.videoTracks.size); put("audio_tracks", p.timeline.audioTracks.size)
        put("clips", p.timeline.allTracks.sumOf { it.clips.size }); put("media", p.media.size); put("markers", p.timeline.markers.size)
        p.nexusProjectId?.let { put("nexus_project_id", it) }; p.sceneId?.let { put("scene_id", it) }
        put("export", buildJsonObject {
            put("resolution", p.export.resolution.label); put("codec", p.export.codec.label)
            put("fps", (p.export.frameRate ?: p.settings.frameRate).toString()); put("bitrate", p.export.bitrate.name.lowercase())
        })
        put("modified_at_ms", p.modifiedAtMs)
    }

    fun context(s: CutState): JsonObject = buildJsonObject {
        put("project", project(s))
        put("playhead_us", s.view.playheadUs); put("playing", s.view.playing)
        put("selection", Outcome.strings(s.view.selection))
        put("selected_clips", buildJsonArray { for (id in s.view.selection) s.project.timeline.clip(id)?.let { add(clip(it, s.project, s.project.timeline.trackOfClip(id)?.id)) } })
        if (s.view.targetTracks.isNotEmpty()) put("target_tracks", Outcome.strings(s.view.targetTracks))
        put("under_playhead", buildJsonArray {
            for (t in s.project.timeline.allTracks) t.clipAt(s.view.playheadUs)?.let { add(clip(it, s.project, t.id)) }
        })
        put("history", buildJsonObject {
            put("can_undo", s.canUndo); s.undoLabel?.let { put("undo", it) }
            put("can_redo", s.canRedo); s.redoLabel?.let { put("redo", it) }
        })
    }

    fun timeline(p: Project, tracks: Set<String>? = null, fromUs: Long? = null, toUs: Long? = null): JsonObject = buildJsonObject {
        put("project_id", p.id); put("revision", p.revision); put("duration_us", p.timeline.durationUs)
        put("tracks", buildJsonArray {
            for (t in p.timeline.videoTracks.asReversed() + p.timeline.audioTracks) {
                if (tracks != null && t.id !in tracks) continue
                add(track(t, p, fromUs, toUs))
            }
        })
        put("markers", buildJsonArray {
            for (m in p.timeline.markers) add(buildJsonObject { put("id", m.id); put("at_us", m.timeUs); put("label", m.label) })
        })
    }

    fun track(t: Track, p: Project, fromUs: Long? = null, toUs: Long? = null): JsonObject = buildJsonObject {
        header(t)
        put("clips", buildJsonArray {
            for (c in t.clips) if ((toUs == null || c.timelineStartUs < toUs) && (fromUs == null || c.endUs > fromUs)) add(clip(c, p, null))
        })
        if (t.transitions.isNotEmpty()) put("transitions", buildJsonArray {
            for (x in t.transitions) add(buildJsonObject {
                put("id", x.id); put("left_clip_id", x.leftClipId); put("right_clip_id", x.rightClipId)
                put("kind", x.kind.name.lowercase()); put("duration_us", x.durationUs)
            })
        })
    }

    fun JsonObjectBuilder.header(t: Track) {
        put("id", t.id); put("kind", t.kind.name.lowercase()); put("name", t.name); put("role", t.role.name.lowercase())
        if (t.locked) put("locked", true); if (t.muted) put("muted", true); if (t.solo) put("solo", true)
        if (!t.syncLock) put("sync_lock", false); if (t.gainDb != 0.0) put("gain_db", t.gainDb)
        put("end_us", t.endUs)
    }

    /** Resumen de un clip: lo necesario para localizarlo y decidir. Los ajustes completos, con `clip.get`. */
    fun clip(c: Clip, p: Project, trackId: String?): JsonObject = buildJsonObject {
        put("id", c.id); trackId?.let { put("track_id", it) }
        put("kind", c.kind.name.lowercase()); if (c.name.isNotBlank()) put("name", c.name)
        put("start_us", c.timelineStartUs); put("duration_us", c.durationUs); put("end_us", c.endUs)
        if (c.kind != ClipKind.TEXT) {
            put("media_id", c.mediaId); put("source_in_us", c.sourceInUs)
            p.media(c.mediaId)?.assetId?.let { put("asset_id", it) }
        }
        if (c.speed.num != c.speed.den) put("speed", c.speed.toString())
        c.linkId?.let { put("link_id", it) }; c.syncGroupId?.let { put("group_id", it) }
        c.story?.sceneId?.let { put("scene_id", it) }; c.story?.shotId?.let { put("shot_id", it) }
        c.text?.let { put("text", it.text) }
        if (c.audio.muted) put("muted", true)
        if (c.keyframes.isNotEmpty()) put("animated", Outcome.strings(c.keyframes.keys.map { it.name.lowercase() }))
    }

    fun clipFull(c: Clip, p: Project, trackId: String?): JsonObject = buildJsonObject {
        for ((k, v) in clip(c, p, trackId)) put(k, v)
        put("source_out_us", c.sourceOutUs)
        if (c.kind != ClipKind.AUDIO) put("video", buildJsonObject {
            val v = c.video
            put("opacity", v.opacity); put("position_x", v.positionX); put("position_y", v.positionY); put("scale", v.scale)
            put("rotation_deg", v.rotationDeg); put("flip_h", v.flipH); put("flip_v", v.flipV); put("fit", v.fit.name.lowercase())
            put("crop", buildJsonObject { put("left", v.crop.left); put("top", v.crop.top); put("right", v.crop.right); put("bottom", v.crop.bottom) })
            put("fade_in_us", v.fadeInUs); put("fade_out_us", v.fadeOutUs)
            put("color", buildJsonObject {
                val k = v.color
                put("exposure", k.exposure); put("contrast", k.contrast); put("saturation", k.saturation)
                put("temperature", k.temperature); put("tint", k.tint); put("fade", k.fade); k.lutUri?.let { put("lut", it) }
            })
        })
        if (c.kind == ClipKind.AUDIO) put("audio", buildJsonObject {
            val a = c.audio
            put("gain_db", a.gainDb); put("pan", a.pan); put("muted", a.muted); put("fade_in_us", a.fadeInUs); put("fade_out_us", a.fadeOutUs)
        })
        c.text?.let { t ->
            put("text_style", buildJsonObject {
                put("size", t.size); put("bold", t.bold); put("align", t.align.name.lowercase()); put("shadow", t.shadow)
                put("color", argb(t.colorArgb)); put("box_opacity", t.boxOpacity); put("box_color", argb(t.boxColorArgb))
            })
        }
        if (c.keyframes.isNotEmpty()) put("keyframes", buildJsonObject {
            for ((param, track) in c.keyframes) put(param.name.lowercase(), buildJsonArray {
                for (k in track.keys) add(buildJsonObject {
                    put("at_us", c.timelineStartUs + k.timeUs); put("value", k.value); put("interpolation", k.interpolation.name.lowercase())
                })
            })
        })
    }

    fun media(m: MediaRef, used: Boolean): JsonObject = buildJsonObject {
        put("id", m.id); put("name", m.displayName); put("kind", m.kind.name.lowercase())
        m.durationUs?.let { put("duration_us", it) }
        if (m.width > 0) { put("width", m.displayWidth); put("height", m.displayHeight) }
        m.frameRate?.let { put("fps", it) }
        put("has_video", m.hasVideo); put("has_audio", m.hasAudio)
        m.assetId?.let { put("asset_id", it) }; m.generationId?.let { put("generation_id", it) }
        put("used", used)
    }

    fun argb(v: Int): String = String.format(java.util.Locale.ROOT, "#%08X", v)

    fun parseArgb(text: String, args: Args): Int {
        val hex = text.trim().removePrefix("#")
        val value = hex.toLongOrNull(16) ?: args.bad("color no válido «$text»; usa #RRGGBB o #AARRGGBB")
        return when (hex.length) {
            6 -> (0xFF000000L or value).toInt()
            8 -> value.toInt()
            else -> args.bad("color no válido «$text»; usa #RRGGBB o #AARRGGBB")
        }
    }
}
