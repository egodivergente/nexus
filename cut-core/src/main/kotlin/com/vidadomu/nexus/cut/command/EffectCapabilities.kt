package com.vidadomu.nexus.cut.command

import com.vidadomu.nexus.cut.command.CapabilityKind.EDIT
import com.vidadomu.nexus.cut.command.CapabilityKind.READ
import com.vidadomu.nexus.cut.command.CoreCapabilities.OK
import com.vidadomu.nexus.cut.command.CoreCapabilities.ids
import com.vidadomu.nexus.cut.command.CoreCapabilities.targets
import com.vidadomu.nexus.cut.model.AnimParam
import com.vidadomu.nexus.cut.model.ClipKind
import com.vidadomu.nexus.cut.model.ColorProps
import com.vidadomu.nexus.cut.model.Crop
import com.vidadomu.nexus.cut.model.FitMode
import com.vidadomu.nexus.cut.model.Interpolation
import com.vidadomu.nexus.cut.model.TextAlign
import com.vidadomu.nexus.cut.model.TextProps
import com.vidadomu.nexus.cut.model.TransitionKind
import com.vidadomu.nexus.cut.model.VideoProps
import com.vidadomu.nexus.cut.subtitle.Srt
import com.vidadomu.nexus.cut.timeline.TimelineEditor
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Imagen, color, audio, animación y rótulos: lo que cambia el aspecto y el sonido sin mover el montaje. */
internal object EffectCapabilities {
    fun all(): List<Capability> = transform() + effects() + audio() + keyframes() + subtitles()

    private val ENGINE = setOf(ErrorCode.NOT_FOUND, ErrorCode.REJECTED_BY_ENGINE)
    private val PARAMS = AnimParam.entries.map { it.name.lowercase() }
    private val INTERPOLATIONS = Interpolation.entries.map { it.name.lowercase() }

    private fun param(a: Args): AnimParam = a.enum<AnimParam>("param") ?: a.bad("falta «param»")

    // ------------------------------------------------------------------ clip.set_transform

    private fun transform() = listOf(
        Capability("clip.set_transform", EDIT, "Coloca y encaja la imagen de un clip: opacidad, posición (en fracciones del fotograma: 0,5 en X lo lleva medio ancho a la derecha; Y positivo baja), escala, giro, volteo, ajuste al fotograma y recorte. Son valores fijos; para animarlos usa keyframe.set.",
            input = Schema.obj {
                ids("clip_ids", "Clips de imagen; por defecto la selección.")
                num("opacity", "Opacidad.", min = 0.0, max = 1.0); num("position_x", "Posición horizontal.", min = -4.0, max = 4.0); num("position_y", "Posición vertical.", min = -4.0, max = 4.0)
                num("scale", "Escala (1 = tamaño encajado).", min = 0.01, max = 20.0); num("rotation_deg", "Giro en grados.")
                bool("flip_h", "Volteo horizontal."); bool("flip_v", "Volteo vertical.")
                str("fit", "Encaje en el fotograma: fit lo muestra entero, fill lo llena recortando, stretch lo deforma.", enum = FitMode.entries.map { it.name.lowercase() })
                num("crop_left", "Recorte por la izquierda, fracción del ancho.", min = 0.0, max = 0.95); num("crop_top", "Recorte por arriba.", min = 0.0, max = 0.95)
                num("crop_right", "Recorte por la derecha.", min = 0.0, max = 0.95); num("crop_bottom", "Recorte por abajo.", min = 0.0, max = 0.95)
                bool("reset", "Volver a los valores por defecto antes de aplicar lo indicado.")
            },
            errors = ENGINE,
            handler = Handler.Edit { d, a ->
                val clips = d.targets(a)
                CoreCapabilities.warnAnimated(d, clips, mapOf("opacity" to AnimParam.OPACITY, "position_x" to AnimParam.POSITION_X, "position_y" to AnimParam.POSITION_Y,
                    "scale" to AnimParam.SCALE, "rotation_deg" to AnimParam.ROTATION), a)
                d.timeline { t, c ->
                    TimelineEditor.updateClips(t, c, clips, "Transformar") { clip ->
                        val base = if (a.bool("reset", false)) VideoProps(fadeInUs = clip.video.fadeInUs, fadeOutUs = clip.video.fadeOutUs, color = clip.video.color) else clip.video
                        val crop = Crop(
                            left = a.float("crop_left")?.coerceIn(0f, 0.95f) ?: base.crop.left, top = a.float("crop_top")?.coerceIn(0f, 0.95f) ?: base.crop.top,
                            right = a.float("crop_right")?.coerceIn(0f, 0.95f) ?: base.crop.right, bottom = a.float("crop_bottom")?.coerceIn(0f, 0.95f) ?: base.crop.bottom)
                        if (crop.left + crop.right >= 1f || crop.top + crop.bottom >= 1f) a.bad("el recorte no deja imagen")
                        clip.copy(video = base.copy(
                            opacity = a.double("opacity")?.coerceIn(0.0, 1.0) ?: base.opacity,
                            positionX = a.double("position_x")?.coerceIn(-4.0, 4.0) ?: base.positionX, positionY = a.double("position_y")?.coerceIn(-4.0, 4.0) ?: base.positionY,
                            scale = a.double("scale")?.coerceIn(0.01, 20.0) ?: base.scale, rotationDeg = a.double("rotation_deg")?.coerceIn(-3600.0, 3600.0) ?: base.rotationDeg,
                            flipH = a.bool("flip_h") ?: base.flipH, flipV = a.bool("flip_v") ?: base.flipV, fit = a.enum<FitMode>("fit") ?: base.fit, crop = crop))
                    }
                }; OK
            }),
    )

    // ------------------------------------------------------------------ effects.*

    private fun effects() = listOf(
        Capability("effects.set_color", EDIT, "Corrección de color primaria por clip. 0 es neutro en todo: exposición en pasos de diafragma (−2 a 2), contraste, saturación, temperatura y tinte (−1 a 1) y desvanecer (0 a 1).",
            input = Schema.obj {
                ids("clip_ids", "Clips de imagen; por defecto la selección.")
                num("exposure", "Exposición en EV.", min = -2.0, max = 2.0); num("contrast", "Contraste.", min = -1.0, max = 1.0); num("saturation", "Saturación (−1 = blanco y negro).", min = -1.0, max = 1.0)
                num("temperature", "Temperatura: positivo más cálido.", min = -1.0, max = 1.0); num("tint", "Tinte: positivo más magenta.", min = -1.0, max = 1.0); num("fade", "Desvanecer (negros lavados).", min = 0.0, max = 1.0)
                bool("reset", "Volver a neutro antes de aplicar lo indicado (conserva el LUT).")
            },
            errors = ENGINE,
            handler = Handler.Edit { d, a ->
                d.timeline { t, c ->
                    TimelineEditor.updateClips(t, c, d.targets(a), "Color") { clip ->
                        val k = if (a.bool("reset", false)) ColorProps(lutUri = clip.video.color.lutUri) else clip.video.color
                        clip.copy(video = clip.video.copy(color = k.copy(
                            exposure = a.float("exposure")?.coerceIn(-2f, 2f) ?: k.exposure, contrast = a.float("contrast")?.coerceIn(-1f, 1f) ?: k.contrast,
                            saturation = a.float("saturation")?.coerceIn(-1f, 1f) ?: k.saturation, temperature = a.float("temperature")?.coerceIn(-1f, 1f) ?: k.temperature,
                            tint = a.float("tint")?.coerceIn(-1f, 1f) ?: k.tint, fade = a.float("fade")?.coerceIn(0f, 1f) ?: k.fade)))
                    }
                }; OK
            }),
        Capability("effects.copy_look", EDIT, "Copia a otros clips el color de un clip de referencia (y, si se pide, también su encuadre) para igualar tomas.",
            input = Schema.obj { str("from_clip_id", "Clip de referencia.", required = true); ids("clip_ids", "Clips destino; por defecto la selección.", required = false); bool("with_transform", "Copiar también posición, escala, giro, recorte y encaje.") },
            errors = ENGINE,
            handler = Handler.Edit { d, a ->
                val from = d.clip(a.req("from_clip_id")); val all = a.bool("with_transform", false)
                d.timeline { t, c ->
                    TimelineEditor.updateClips(t, c, d.targets(a) - from.id, "Copiar aspecto") { clip ->
                        if (clip.kind == ClipKind.AUDIO) clip
                        else clip.copy(video = (if (all) from.video.copy(fadeInUs = clip.video.fadeInUs, fadeOutUs = clip.video.fadeOutUs) else clip.video).copy(color = from.video.color))
                    }
                }; OK
            }),
        Capability("effects.set_fades", EDIT, "Fundido de imagen desde negro al entrar y a negro al salir, por clip.",
            input = Schema.obj { ids("clip_ids", "Clips de imagen; por defecto la selección."); us("fade_in_us", "Duración del fundido de entrada"); us("fade_out_us", "Duración del fundido de salida") },
            errors = ENGINE,
            handler = Handler.Edit { d, a ->
                d.timeline { t, c ->
                    TimelineEditor.updateClips(t, c, d.targets(a), "Fundidos") { clip ->
                        clip.copy(video = clip.video.copy(fadeInUs = a.long("fade_in_us")?.coerceAtLeast(0) ?: clip.video.fadeInUs, fadeOutUs = a.long("fade_out_us")?.coerceAtLeast(0) ?: clip.video.fadeOutUs))
                    }
                }; OK
            }),
        Capability("effects.set_transition", EDIT, "Pone o cambia la transición en el corte entre dos clips contiguos de una misma pista: fundido encadenado o a negro en vídeo, fundido cruzado en audio. Si no hay material sobrante la duración se ajusta y se avisa.",
            input = Schema.obj {
                str("left_clip_id", "Clip que sale.", required = true); str("right_clip_id", "Clip que entra; por defecto el siguiente de su pista.")
                str("kind", "Tipo; por defecto el encadenado propio de la pista.", enum = TransitionKind.entries.map { it.name.lowercase() }); us("duration_us", "Duración; por defecto 0,5 s")
            },
            output = Schema.obj { str("transition_id", "Transición creada o actualizada.") },
            preconditions = listOf("Los dos clips están pegados en la misma pista."), errors = ENGINE,
            handler = Handler.Edit { d, a ->
                val left = d.clip(a.req("left_clip_id")); val track = d.timeline.trackOfClip(left.id)!!
                val right = a.str("right_clip_id")?.let { d.clip(it) } ?: track.clips.getOrNull(track.clips.indexOfFirst { it.id == left.id } + 1)
                    ?: cutFail(ErrorCode.PRECONDITION_FAILED, "«${left.name.ifBlank { left.id }}» es el último clip de su pista: no hay corte donde poner la transición.")
                val kind = a.enum<TransitionKind>("kind") ?: if (track.kind == com.vidadomu.nexus.cut.model.TrackKind.AUDIO) TransitionKind.AUDIO_CROSSFADE else TransitionKind.CROSS_DISSOLVE
                d.timeline { t, c -> TimelineEditor.setTransition(t, c, track.id, left.id, right.id, kind, a.long("duration_us") ?: 500_000) }
                val id = d.timeline.track(track.id)?.transitions?.firstOrNull { it.leftClipId == left.id && it.rightClipId == right.id }?.id
                buildJsonObject { id?.let { put("transition_id", it) } }
            }),
        Capability("effects.remove_transition", EDIT, "Quita una transición y deja el corte seco.",
            input = Schema.obj { str("transition_id", "Transición (timeline.get).", required = true) }, errors = ENGINE,
            handler = Handler.Edit { d, a -> d.timeline { t, c -> TimelineEditor.removeTransition(t, c, a.req("transition_id")) }; OK }),
    )

    // ------------------------------------------------------------------ audio.*

    private fun audio() = listOf(
        Capability("audio.set", EDIT, "Mezcla por clip de audio: volumen en dB, panorama, silencio y fundidos de entrada y salida.",
            input = Schema.obj {
                ids("clip_ids", "Clips de audio; por defecto la selección.")
                num("gain_db", "Volumen en dB (0 = original).", min = -96.0, max = 24.0); num("pan", "Panorama: −1 izquierda, 1 derecha.", min = -1.0, max = 1.0); bool("muted", "En silencio.")
                us("fade_in_us", "Fundido de entrada"); us("fade_out_us", "Fundido de salida")
            },
            errors = ENGINE,
            handler = Handler.Edit { d, a ->
                val clips = d.targets(a)
                CoreCapabilities.warnAnimated(d, clips, mapOf("gain_db" to AnimParam.GAIN_DB), a)
                d.timeline { t, c ->
                    TimelineEditor.updateClips(t, c, clips, "Audio") { clip ->
                        clip.copy(audio = clip.audio.copy(
                            gainDb = a.double("gain_db")?.coerceIn(-96.0, 24.0) ?: clip.audio.gainDb, pan = a.double("pan")?.coerceIn(-1.0, 1.0) ?: clip.audio.pan,
                            muted = a.bool("muted") ?: clip.audio.muted,
                            fadeInUs = a.long("fade_in_us")?.coerceAtLeast(0) ?: clip.audio.fadeInUs, fadeOutUs = a.long("fade_out_us")?.coerceAtLeast(0) ?: clip.audio.fadeOutUs))
                    }
                }; OK
            }),
    )

    // ------------------------------------------------------------------ keyframe.*

    private fun keyframes() = listOf(
        Capability("keyframe.list", READ, "Keyframes de un clip por parámetro, con su instante en la línea de tiempo, valor e interpolación.",
            input = Schema.obj { str("clip_id", "Clip.", required = true) }, errors = setOf(ErrorCode.NOT_FOUND),
            handler = Handler.Read { s, a ->
                val id = a.req("clip_id"); val c = s.project.timeline.clip(id) ?: cutFail(ErrorCode.NOT_FOUND, "Clip inexistente ($id)")
                buildJsonObject { put("clip_id", id); put("keyframes", CutJson.clipFull(c, s.project, null)["keyframes"] ?: buildJsonObject { }) }
            }),
        Capability("keyframe.set", EDIT, "Pone o cambia un keyframe de un parámetro (posición, escala, giro, opacidad o volumen) en un instante de la línea de tiempo. Con dos o más, el valor se anima entre ellos.",
            input = Schema.obj {
                str("clip_id", "Clip.", required = true); str("param", "Parámetro.", required = true, enum = PARAMS)
                us("at_us", "Instante en la línea de tiempo, dentro del clip; por defecto el cabezal"); num("value", "Valor en ese instante.", required = true)
                str("interpolation", "Cómo sale hacia el siguiente keyframe; por defecto linear.", enum = INTERPOLATIONS)
            },
            preconditions = listOf("El instante cae dentro del clip."), errors = ENGINE + ErrorCode.PRECONDITION_FAILED,
            handler = Handler.Edit { d, a ->
                val clip = d.clip(a.req("clip_id")); val at = a.long("at_us") ?: d.view.playheadUs
                if (!clip.contains(at) && at != clip.endUs) cutFail(ErrorCode.PRECONDITION_FAILED, "Ese instante queda fuera del clip (va de ${clip.timelineStartUs} a ${clip.endUs} µs).")
                d.timeline { t, c -> TimelineEditor.setKeyframe(t, c, clip.id, param(a), at - clip.timelineStartUs, a.double("value") ?: a.bad("falta «value»"), a.enum<Interpolation>("interpolation") ?: Interpolation.LINEAR) }; OK
            }),
        Capability("keyframe.remove", EDIT, "Quita el keyframe de un parámetro en un instante.",
            input = Schema.obj { str("clip_id", "Clip.", required = true); str("param", "Parámetro.", required = true, enum = PARAMS); us("at_us", "Instante del keyframe; por defecto el cabezal") },
            errors = ENGINE,
            handler = Handler.Edit { d, a ->
                val clip = d.clip(a.req("clip_id")); val p = param(a); val local = (a.long("at_us") ?: d.view.playheadUs) - clip.timelineStartUs
                if (clip.keyframes[p]?.keys?.none { it.timeUs == local } != false) cutFail(ErrorCode.NOT_FOUND, "No hay keyframe de ${p.name.lowercase()} en ese instante (keyframe.list).")
                d.timeline { t, c -> TimelineEditor.removeKeyframe(t, c, clip.id, p, local) }; OK
            }),
        Capability("keyframe.clear", EDIT, "Quita toda la animación de un parámetro, o de todos si no se indica; el clip vuelve a su valor fijo.",
            input = Schema.obj { str("clip_id", "Clip.", required = true); str("param", "Parámetro; por defecto todos.", enum = PARAMS) }, errors = ENGINE,
            handler = Handler.Edit { d, a ->
                val clip = d.clip(a.req("clip_id")); val only = a.enum<AnimParam>("param")
                d.timeline { t, c -> TimelineEditor.updateClips(t, c, setOf(clip.id), "Quitar keyframes") { it.copy(keyframes = if (only == null) emptyMap() else it.keyframes - only) } }; OK
            }),
        Capability("keyframe.set_interpolation", EDIT, "Cambia cómo salen hacia el siguiente los keyframes que hay en un instante: lineal, mantenido o con aceleración.",
            input = Schema.obj { str("clip_id", "Clip.", required = true); us("at_us", "Instante de los keyframes; por defecto el cabezal"); str("interpolation", "Interpolación.", required = true, enum = INTERPOLATIONS) },
            errors = ENGINE,
            handler = Handler.Edit { d, a ->
                d.timeline { t, c -> TimelineEditor.setKeyInterpolation(t, c, d.clip(a.req("clip_id")).id, a.long("at_us") ?: d.view.playheadUs, a.enum<Interpolation>("interpolation") ?: a.bad("falta «interpolation»")) }; OK
            }),
    )

    // ------------------------------------------------------------------ subtitle.*

    private fun Schema.Props.style() {
        num("size", "Tamaño de letra como fracción de la altura del fotograma (0,08 = título; 0,045 = subtítulo).", min = 0.01, max = 0.5)
        bool("bold", "Negrita."); str("align", "Alineación.", enum = TextAlign.entries.map { it.name.lowercase() }); bool("shadow", "Sombra.")
        str("color", "Color del texto, #RRGGBB o #AARRGGBB."); num("box_opacity", "Opacidad de la caja de fondo (0 = sin caja).", min = 0.0, max = 1.0); str("box_color", "Color de la caja.")
        num("position_x", "Posición horizontal en fracciones del fotograma (0 = centro).", min = -1.0, max = 1.0); num("position_y", "Posición vertical (0 = centro; 0,38 = tercio inferior).", min = -1.0, max = 1.0)
    }

    private fun styled(base: TextProps, a: Args, text: String = base.text) = base.copy(
        text = text, size = a.double("size")?.coerceIn(0.01, 0.5) ?: base.size, bold = a.bool("bold") ?: base.bold, align = a.enum<TextAlign>("align") ?: base.align,
        shadow = a.bool("shadow") ?: base.shadow, colorArgb = a.str("color")?.let { CutJson.parseArgb(it, a) } ?: base.colorArgb,
        boxOpacity = a.double("box_opacity")?.coerceIn(0.0, 1.0) ?: base.boxOpacity, boxColorArgb = a.str("box_color")?.let { CutJson.parseArgb(it, a) } ?: base.boxColorArgb)

    private fun placed(base: VideoProps, a: Args) = base.copy(
        positionX = a.double("position_x")?.coerceIn(-1.0, 1.0) ?: base.positionX, positionY = a.double("position_y")?.coerceIn(-1.0, 1.0) ?: base.positionY)

    private fun subtitles() = listOf(
        Capability("subtitle.list", READ, "Rótulos y subtítulos del montaje en orden, con su texto, tiempos y estilo. Para revisarlos, traducirlos o corregirlos.",
            input = Schema.obj { us("from_us", "Desde"); us("to_us", "Hasta") },
            handler = Handler.Read { s, a ->
                val from = a.long("from_us"); val to = a.long("to_us")
                buildJsonObject {
                    put("subtitles", buildJsonArray {
                        for (t in s.project.timeline.videoTracks) for (c in t.clips) {
                            if (c.kind != ClipKind.TEXT || (from != null && c.endUs <= from) || (to != null && c.timelineStartUs >= to)) continue
                            add(buildJsonObject {
                                put("clip_id", c.id); put("track_id", t.id); put("start_us", c.timelineStartUs); put("end_us", c.endUs); put("text", c.text?.text.orEmpty())
                                put("size", c.text?.size ?: 0.0); put("position_y", c.video.positionY)
                            })
                        }
                    })
                }
            }),
        Capability("subtitle.add", EDIT, "Añade un rótulo o subtítulo encima de la imagen sin mover nada: va a una pista de rótulos libre o a una nueva.",
            input = Schema.obj { str("text", "Texto (admite saltos de línea).", required = true); us("at_us", "Inicio; por defecto el cabezal"); us("duration_us", "Duración; por defecto 3 s"); style() },
            output = Schema.obj { str("clip_id", "Rótulo creado.") }, errors = ENGINE,
            handler = Handler.Edit { d, a ->
                val r = d.timeline { t, c -> TimelineEditor.addTitle(t, c, a.long("at_us") ?: d.view.playheadUs, a.long("duration_us") ?: 3_000_000, styled(TextProps(), a, a.req("text")), placed(VideoProps(), a)) }
                buildJsonObject { r.createdIds.firstOrNull { d.timeline.clip(it) != null }?.let { put("clip_id", it) } }
            }),
        Capability("subtitle.update", EDIT, "Cambia el texto o el estilo de rótulos existentes. Con varios clips aplica el mismo estilo a todos (el texto solo si se indica).",
            input = Schema.obj { ids("clip_ids", "Rótulos; por defecto la selección."); str("text", "Texto nuevo."); style() },
            preconditions = listOf("Los clips son rótulos."), errors = ENGINE + ErrorCode.PRECONDITION_FAILED,
            handler = Handler.Edit { d, a ->
                val clips = d.targets(a)
                clips.firstOrNull { d.clip(it).kind != ClipKind.TEXT }?.let { cutFail(ErrorCode.PRECONDITION_FAILED, "«$it» no es un rótulo.") }
                d.timeline { t, c ->
                    TimelineEditor.updateClips(t, c, clips, "Texto") { clip ->
                        val text = styled(clip.text ?: TextProps(), a, a.str("text") ?: clip.text?.text.orEmpty())
                        clip.copy(text = text, video = placed(clip.video, a), name = text.text.lineSequence().first().take(40))
                    }
                }; OK
            }),
        Capability("subtitle.import_srt", EDIT, "Convierte un SRT o VTT (por ejemplo, la transcripción de Whisper) en subtítulos sobre la línea de tiempo, todos con el mismo estilo y en un solo paso de deshacer.",
            input = Schema.obj {
                str("srt", "Contenido del archivo SRT o VTT.", required = true); us("offset_us", "Desplazamiento de todos los subtítulos; por defecto 0")
                int("max_chars_per_line", "Parte las líneas más largas que esto por un espacio; por defecto 42."); style()
            },
            output = Schema.obj { ids("clip_ids", "Subtítulos creados."); int("skipped", "Subtítulos que no se pudieron colocar.") }, errors = ENGINE,
            handler = Handler.Edit { d, a ->
                val cues = Srt.parse(a.req("srt")).ifEmpty { a.bad("«srt» no contiene ningún subtítulo con tiempos válidos") }
                if (cues.size > 3000) a.bad("demasiados subtítulos (${cues.size}); divídelo en partes de hasta 3000")
                val offset = a.long("offset_us") ?: 0; val width = (a.int("max_chars_per_line") ?: 42).coerceIn(12, 120)
                val base = styled(TextProps(size = 0.045, boxOpacity = 0.0), a); val video = placed(VideoProps(positionY = 0.38), a)
                val created = ArrayList<String>(); var skipped = 0
                for (cue in cues) {
                    val start = cue.startUs + offset
                    if (start < 0) { skipped++; continue }
                    val r = d.timeline { t, c -> TimelineEditor.addTitle(t, c, start, cue.endUs - cue.startUs, base.copy(text = wrap(cue.text, width)), video) }
                    r.createdIds.firstOrNull { d.timeline.clip(it) != null }?.let { created += it }
                }
                buildJsonObject { put("clip_ids", Outcome.strings(created)); put("skipped", skipped) }
            }),
    )

    /** Parte por espacios las líneas largas, sin romper palabras. */
    internal fun wrap(text: String, width: Int): String = text.split('\n').joinToString("\n") { line ->
        val out = StringBuilder(); var column = 0
        for (word in line.split(' ').filter { it.isNotEmpty() }) {
            if (column > 0 && column + 1 + word.length > width) { out.append('\n'); column = 0 }
            if (column > 0) { out.append(' '); column++ }
            out.append(word); column += word.length
        }
        out.toString()
    }
}
