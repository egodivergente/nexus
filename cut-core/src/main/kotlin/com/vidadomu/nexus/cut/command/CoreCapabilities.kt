package com.vidadomu.nexus.cut.command

import com.vidadomu.nexus.cut.command.CapabilityKind.EDIT
import com.vidadomu.nexus.cut.command.CapabilityKind.READ
import com.vidadomu.nexus.cut.command.CapabilityKind.VIEW
import com.vidadomu.nexus.cut.model.BitratePreset
import com.vidadomu.nexus.cut.model.Clip
import com.vidadomu.nexus.cut.model.ClipKind
import com.vidadomu.nexus.cut.model.ExportResolution
import com.vidadomu.nexus.cut.model.FrameRate
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.model.Rational
import com.vidadomu.nexus.cut.model.StoryLink
import com.vidadomu.nexus.cut.model.Track
import com.vidadomu.nexus.cut.model.TrackKind
import com.vidadomu.nexus.cut.model.TrackRole
import com.vidadomu.nexus.cut.model.VideoCodec
import com.vidadomu.nexus.cut.timeline.AgentOps
import com.vidadomu.nexus.cut.timeline.Edge
import com.vidadomu.nexus.cut.timeline.EditMode
import com.vidadomu.nexus.cut.timeline.Placement
import com.vidadomu.nexus.cut.timeline.Snapping
import com.vidadomu.nexus.cut.timeline.TimelineEditor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Capacidades que el motor resuelve solo, sin Android: proyecto, medios, línea de tiempo y clips.
 * Las que necesitan el dispositivo (importar, analizar audio, LUT, exportar) las registra la app encima.
 */
object CoreCapabilities {
    fun all(): List<Capability> = project() + media() + timeline() + clip() + EffectCapabilities.all()

    internal val OK: JsonObject = JsonObject(emptyMap())
    internal fun ids(key: String, values: Iterable<String>): JsonObject = JsonObject(mapOf(key to Outcome.strings(values)))
    internal val CLIP_IDS_OUT = Schema.obj { ids("clip_ids", "Clips creados o afectados.") }
    private val ENGINE = setOf(ErrorCode.NOT_FOUND, ErrorCode.REJECTED_BY_ENGINE)

    /** Clips indicados o, si no se indican, la selección actual. Todos deben existir. */
    internal fun Draft.targets(a: Args, key: String = "clip_ids"): Set<String> =
        requireClips(a.ids(key).ifEmpty { view.selection }.ifEmpty { a.bad("indica «$key» o selecciona clips antes") })

    internal fun mode(a: Args, default: EditMode): EditMode = a.enum<EditMode>("mode") ?: default

    private val ASPECTS = mapOf("16:9" to (16 to 9), "9:16" to (9 to 16), "1:1" to (1 to 1), "4:5" to (4 to 5), "4:3" to (4 to 3), "3:4" to (3 to 4), "21:9" to (21 to 9))
    private val RATES = mapOf("23.976" to FrameRate.FPS_23_976, "24" to FrameRate.FPS_24, "25" to FrameRate.FPS_25, "29.97" to FrameRate.FPS_29_97,
        "30" to FrameRate.FPS_30, "50" to FrameRate.FPS_50, "60" to FrameRate.FPS_60)

    private fun rate(a: Args, key: String): FrameRate? = a.str(key)?.let { RATES[it] ?: a.bad("«$key» no admite «$it»; valores: ${RATES.keys.joinToString()}") }

    // ------------------------------------------------------------------ project.*

    private fun project() = listOf(
        Capability("project.get", READ, "Ficha del montaje: nombre, formato, duración, número de pistas, clips y medios, ajustes de exportación, revisión y vínculo con el proyecto de Nexus.",
            handler = Handler.Read { s, _ -> CutJson.project(s) }),
        Capability("project.context", READ, "Lo que el usuario tiene delante ahora: cabezal, si reproduce, clips seleccionados, clips bajo el cabezal, pistas destino y qué se puede deshacer o rehacer. Léelo antes de interpretar «este clip» o «aquí».",
            handler = Handler.Read { s, _ -> CutJson.context(s) }),
        Capability("project.rename", EDIT, "Cambia el nombre del montaje.",
            input = Schema.obj { str("name", "Nombre nuevo.", required = true) },
            handler = Handler.Edit { d, a -> val name = a.req("name").trim().take(120); d.header("Renombrar montaje") { it.copy(name = name) }; OK }),
        Capability("project.set_format", EDIT, "Fija el formato de trabajo: proporción o tamaño exacto del fotograma y fotogramas por segundo. No recorta ni recoloca clips: cada uno se encaja según su ajuste.",
            input = Schema.obj {
                str("aspect", "Proporción; se combina con short_side.", enum = ASPECTS.keys.toList())
                int("short_side", "Lado corto en píxeles con «aspect»; por defecto 1080.")
                int("width", "Anchura exacta en píxeles (par). Alternativa a «aspect»."); int("height", "Altura exacta en píxeles (par).")
                str("fps", "Fotogramas por segundo.", enum = RATES.keys.toList())
            },
            handler = Handler.Edit { d, a ->
                val current = d.project.settings
                val size = a.str("aspect")?.let { key ->
                    val (rw, rh) = ASPECTS[key] ?: a.bad("«aspect» no admite «$key»; valores: ${ASPECTS.keys.joinToString()}")
                    val short = (a.int("short_side") ?: 1080).coerceIn(240, 4320)
                    fun even(v: Int) = v + (v and 1)
                    if (rw >= rh) even(short * rw / rh) to even(short) else even(short) to even(short * rh / rw)
                } ?: ((a.int("width") ?: current.width) to (a.int("height") ?: current.height))
                val next = current.copy(width = size.first, height = size.second, frameRate = rate(a, "fps") ?: current.frameRate)
                d.header("Formato") { it.copy(settings = next) }
                buildJsonObject { put("width", next.width); put("height", next.height); put("fps", next.frameRate.toString()) }
            }),
        Capability("project.set_export", EDIT, "Ajusta cómo se exportará: resolución, códec, fotogramas por segundo y bitrate. No exporta.",
            input = Schema.obj {
                str("resolution", "Resolución de salida.", enum = ExportResolution.entries.map { it.label })
                str("codec", "Códec de vídeo.", enum = listOf("h264", "h265"))
                str("fps", "Fotogramas por segundo; «project» usa los del montaje.", enum = RATES.keys.toList() + "project")
                str("bitrate", "Calidad.", enum = BitratePreset.entries.map { it.name.lowercase() })
                int("custom_bitrate_kbps", "Bitrate de vídeo en kbps cuando bitrate es «custom»."); int("audio_bitrate_kbps", "Bitrate de audio en kbps.")
            },
            handler = Handler.Edit { d, a ->
                val e = d.project.export
                val next = e.copy(
                    resolution = a.str("resolution")?.let { v -> ExportResolution.entries.firstOrNull { it.label.equals(v, true) } ?: a.bad("«resolution» no admite «$v»") } ?: e.resolution,
                    codec = a.enum<VideoCodec>("codec") ?: e.codec,
                    frameRate = if (a.str("fps") == "project") null else rate(a, "fps") ?: e.frameRate,
                    bitrate = a.enum<BitratePreset>("bitrate") ?: e.bitrate,
                    customBitrateKbps = a.int("custom_bitrate_kbps")?.coerceIn(200, 200_000) ?: e.customBitrateKbps,
                    audioBitrateKbps = a.int("audio_bitrate_kbps")?.coerceIn(32, 512) ?: e.audioBitrateKbps,
                )
                d.header("Ajustes de exportación") { it.copy(export = next) }; OK
            }),
        Capability("project.link_story", EDIT, "Vincula el montaje con un proyecto audiovisual de Nexus y, si procede, con la escena que monta. Así guion, biblioteca y montaje comparten identidad.",
            input = Schema.obj { str("nexus_project_id", "Id del proyecto de Nexus; vacío lo desvincula."); str("scene_id", "Id de la escena; vacío la quita.") },
            handler = Handler.Edit { d, a ->
                d.header("Vincular con el guion") {
                    it.copy(nexusProjectId = if (a.has("nexus_project_id")) a.str("nexus_project_id")?.takeIf(String::isNotBlank) else it.nexusProjectId,
                        sceneId = if (a.has("scene_id")) a.str("scene_id")?.takeIf(String::isNotBlank) else it.sceneId)
                }; OK
            }),
        Capability("project.undo", EDIT, "Deshace el último paso del historial compartido, lo hiciera el usuario o el agente.",
            preconditions = listOf("Hay algo que deshacer (project.context → history.can_undo)."), errors = setOf(ErrorCode.PRECONDITION_FAILED), handler = Handler.Undo),
        Capability("project.redo", EDIT, "Rehace el último paso deshecho.",
            preconditions = listOf("Hay algo que rehacer (project.context → history.can_redo)."), errors = setOf(ErrorCode.PRECONDITION_FAILED), handler = Handler.Redo),
    )

    // ------------------------------------------------------------------ media.*

    private fun media() = listOf(
        Capability("media.list", READ, "Medios del montaje (vídeo, audio, imagen) con duración, tamaño, si tienen audio, de qué archivo de Biblioteca vienen y si se usan en la línea de tiempo.",
            input = Schema.obj { str("kind", "Filtrar por tipo.", enum = listOf("video", "audio", "image")); bool("unused_only", "Solo los que no están en la línea de tiempo.") },
            handler = Handler.Read { s, a ->
                val kind = a.enum<MediaKind>("kind"); val unused = a.bool("unused_only", false)
                buildJsonObject {
                    put("media", buildJsonArray {
                        for (m in s.project.media) {
                            val used = s.project.timeline.usesMedia(m.id)
                            if ((kind == null || m.kind == kind) && !(unused && used)) add(CutJson.media(m, used))
                        }
                    })
                }
            }),
        Capability("media.remove", EDIT, "Quita del montaje medios que ya no se usan en la línea de tiempo. No borra el archivo de la Biblioteca.",
            input = Schema.obj { ids("media_ids", "Medios que quitar.", required = true) },
            preconditions = listOf("Ningún clip usa esos medios."), errors = setOf(ErrorCode.NOT_FOUND, ErrorCode.PRECONDITION_FAILED),
            handler = Handler.Edit { d, a ->
                val ids = a.reqIds("media_ids")
                for (id in ids) {
                    val m = d.media(id)
                    if (d.timeline.usesMedia(id)) cutFail(ErrorCode.PRECONDITION_FAILED, "«${m.displayName}» se usa en la línea de tiempo: quita antes sus clips.")
                }
                d.header("Quitar medios") { p -> p.copy(media = p.media.filterNot { it.id in ids }) }; OK
            }),
    )

    // ------------------------------------------------------------------ timeline.*

    private fun timeline() = listOf(
        Capability("timeline.get", READ, "La línea de tiempo real: pistas (vídeo de arriba abajo, luego audio) con sus clips, transiciones y marcadores. Léela antes de proponer cortes; acota con pistas o un tramo si el montaje es largo.",
            input = Schema.obj { ids("track_ids", "Solo estas pistas."); us("from_us", "Desde este instante"); us("to_us", "Hasta este instante") },
            handler = Handler.Read { s, a -> CutJson.timeline(s.project, a.ids("track_ids").takeIf { it.isNotEmpty() }, a.long("from_us"), a.long("to_us")) }),
        Capability("timeline.find_clips", READ, "Busca clips por pista, tipo, tramo, texto del nombre o del rótulo, medio, archivo de Biblioteca, escena, plano o duración. Devuelve los que cumplen todo, en orden temporal.",
            input = Schema.obj {
                ids("track_ids", "Solo estas pistas."); str("kind", "Tipo de clip.", enum = ClipKind.entries.map { it.name.lowercase() })
                us("from_us", "Que terminen después de"); us("to_us", "Que empiecen antes de")
                str("text", "Texto contenido en el nombre o en el rótulo (sin distinguir mayúsculas).")
                str("media_id", "Medio del montaje."); str("asset_id", "Archivo de la Biblioteca de Nexus.")
                str("scene_id", "Escena del guion."); str("shot_id", "Plano del guion.")
                us("min_duration_us", "Duración mínima"); us("max_duration_us", "Duración máxima")
                int("limit", "Máximo de resultados; por defecto 200.")
            },
            handler = Handler.Read { s, a ->
                val p = s.project
                val tracks = a.ids("track_ids").takeIf { it.isNotEmpty() }; val kind = a.enum<ClipKind>("kind")
                val from = a.long("from_us"); val to = a.long("to_us"); val text = a.str("text")?.lowercase()
                val mediaId = a.str("media_id"); val assetId = a.str("asset_id"); val scene = a.str("scene_id"); val shot = a.str("shot_id")
                val min = a.long("min_duration_us"); val max = a.long("max_duration_us"); val limit = (a.int("limit") ?: 200).coerceIn(1, 1000)
                val found = p.timeline.allTracks.asSequence().filter { tracks == null || it.id in tracks }.flatMap { t -> t.clips.asSequence().map { t to it } }
                    .filter { (_, c) ->
                        (kind == null || c.kind == kind) && (from == null || c.endUs > from) && (to == null || c.timelineStartUs < to) &&
                            (text == null || text in c.name.lowercase() || c.text?.text?.lowercase()?.contains(text) == true) &&
                            (mediaId == null || c.mediaId == mediaId) && (assetId == null || p.media(c.mediaId)?.assetId == assetId) &&
                            (scene == null || c.story?.sceneId == scene) && (shot == null || c.story?.shotId == shot) &&
                            (min == null || c.durationUs >= min) && (max == null || c.durationUs <= max)
                    }.sortedBy { it.second.timelineStartUs }.toList()
                buildJsonObject {
                    put("total", found.size)
                    put("clips", buildJsonArray { for ((t, c) in found.take(limit)) add(CutJson.clip(c, p, t.id)) })
                }
            }),
        Capability("timeline.gaps", READ, "Huecos vacíos de cada pista (antes del primer clip y entre clips). Útil para cerrar pausas o encontrar sitio.",
            input = Schema.obj { ids("track_ids", "Solo estas pistas."); us("min_us", "Ignorar huecos más cortos que esto; por defecto 1 fotograma") },
            handler = Handler.Read { s, a ->
                val tracks = a.ids("track_ids").takeIf { it.isNotEmpty() }; val min = a.long("min_us") ?: s.project.settings.frameRate.frameDurationUs
                buildJsonObject {
                    put("gaps", buildJsonArray {
                        for (t in s.project.timeline.allTracks) {
                            if (tracks != null && t.id !in tracks) continue
                            var cursor = 0L
                            for (c in t.clips) {
                                if (c.timelineStartUs - cursor >= min) add(buildJsonObject { put("track_id", t.id); put("start_us", cursor); put("end_us", c.timelineStartUs); put("duration_us", c.timelineStartUs - cursor) })
                                cursor = c.endUs
                            }
                        }
                    })
                }
            }),
        Capability("timeline.snap", READ, "El punto de imán más cercano a un instante (borde de clip, marcador, cabezal o inicio), para colocar con precisión.",
            input = Schema.obj { us("at_us", "Instante", required = true); us("threshold_us", "Distancia máxima; por defecto 0,5 s"); ids("exclude_clip_ids", "Clips cuyos bordes no cuentan.") },
            handler = Handler.Read { s, a ->
                val at = a.reqLong("at_us")
                val hit = Snapping.nearest(Snapping.targets(s.project.timeline, s.view.playheadUs, a.ids("exclude_clip_ids")), at, a.long("threshold_us") ?: 500_000)
                buildJsonObject { put("snapped", hit != null); put("at_us", hit?.timeUs ?: at); hit?.let { put("to", it.kind.name.lowercase()) } }
            }),
        Capability("timeline.place", EDIT, "Coloca medios del montaje en la línea de tiempo, uno tras otro. Sin «at_us» se añaden al final de las pistas destino. Un vídeo con sonido entra con su audio vinculado. «insert» desplaza lo que hay; «overwrite» lo sustituye.",
            input = Schema.obj {
                list("items", "Qué colocar, en orden.", required = true, items = Schema.obj {
                    str("media_id", "Medio del montaje (media.list).", required = true)
                    us("source_in_us", "Punto de entrada dentro del medio; por defecto 0"); us("duration_us", "Duración en la línea de tiempo; por defecto todo el medio (5 s para imágenes)")
                    str("name", "Nombre del clip."); str("scene_id", "Escena del guion que cubre."); str("shot_id", "Plano del guion que cubre.")
                })
                us("at_us", "Dónde empieza el primero; por defecto al final"); str("mode", "Cómo entra.", enum = listOf("insert", "overwrite"))
                str("video_track_id", "Pista de vídeo destino."); str("audio_track_id", "Pista de audio destino.")
                bool("with_audio", "Colocar también el audio de los vídeos; por defecto true.")
            },
            output = CLIP_IDS_OUT, preconditions = listOf("Los medios ya están en el montaje.", "Hay una pista desbloqueada del tipo necesario."), errors = ENGINE,
            handler = Handler.Edit { d, a -> place(d, a) }),
        Capability("timeline.delete_ranges", EDIT, "Quita tramos de tiempo enteros (silencios, pausas, descartes): corta en los bordes y borra lo de dentro, cerrando el hueco si «ripple». Los clips vinculados se cortan juntos.",
            input = Schema.obj {
                list("ranges", "Tramos [inicio_us, fin_us].", required = true, items = buildJsonObject {
                    put("type", "array"); put("minItems", 2); put("maxItems", 2); put("items", buildJsonObject { put("type", "integer") })
                })
                ids("track_ids", "Limitar a estas pistas; por defecto todas."); bool("ripple", "Cerrar el hueco; por defecto true.")
            },
            errors = ENGINE,
            handler = Handler.Edit { d, a ->
                val op = buildJsonObject {
                    put("op", "delete_ranges"); put("ranges", a.array("ranges") ?: a.bad("falta «ranges»"))
                    a.ids("track_ids").takeIf { it.isNotEmpty() }?.let { put("track_ids", Outcome.strings(it)) }
                    put("ripple", a.bool("ripple", true))
                }
                d.timeline { t, c -> AgentOps.apply(t, c, JsonArray(listOf(op)), "Quitar tramos") }; OK
            }),
        Capability("timeline.close_gap", EDIT, "Cierra el hueco que hay en una pista en un instante, adelantando lo que viene después.",
            input = Schema.obj { str("track_id", "Pista.", required = true); us("at_us", "Un instante dentro del hueco", required = true) }, errors = ENGINE,
            handler = Handler.Edit { d, a -> d.timeline { t, c -> TimelineEditor.closeGap(t, c, a.req("track_id"), a.reqLong("at_us")) }; OK }),
        Capability("timeline.add_track", EDIT, "Añade una pista. El papel decide su tipo y su comportamiento (la música se alarga al insertar en vez de cortarse).",
            input = Schema.obj {
                str("role", "Papel de la pista.", required = true, enum = TrackRole.entries.map { it.name.lowercase() })
                str("name", "Nombre visible."); int("index", "Posición entre las de su tipo (0 = la de más abajo en vídeo, la primera en audio).")
            },
            output = Schema.obj { str("track_id", "Pista creada.") },
            handler = Handler.Edit { d, a ->
                val r = d.timeline { t, c -> TimelineEditor.addTrack(t, c, a.enum<TrackRole>("role") ?: a.bad("falta «role»"), a.str("name"), a.int("index")) }
                buildJsonObject { put("track_id", r.createdIds.first()) }
            }),
        Capability("timeline.remove_track", EDIT, "Elimina una pista con todo lo que contiene.",
            input = Schema.obj { str("track_id", "Pista.", required = true) }, errors = ENGINE,
            handler = Handler.Edit { d, a -> d.track(a.req("track_id")); d.timeline { t, c -> TimelineEditor.removeTrack(t, c, a.req("track_id")) }; OK }),
        Capability("timeline.update_track", EDIT, "Cambia la cabecera de una pista: nombre, bloqueo, silencio u ocultación, solo, si sigue a las inserciones (sync lock) y volumen.",
            input = Schema.obj {
                str("track_id", "Pista.", required = true); str("name", "Nombre."); bool("locked", "Bloqueada: nada en ella se puede cambiar.")
                bool("muted", "Vídeo: oculta. Audio: en silencio."); bool("solo", "Solo esta pista.")
                bool("sync_lock", "Se desplaza con las inserciones hechas en otras pistas."); num("gain_db", "Volumen de la pista en dB.", min = -96.0, max = 24.0)
            },
            errors = ENGINE,
            handler = Handler.Edit { d, a ->
                val track = d.track(a.req("track_id"))
                d.timeline { t, c ->
                    TimelineEditor.updateTrack(t, c, track.id, "Pista ${track.name}") { h: Track ->
                        h.copy(name = a.str("name")?.take(60) ?: h.name, locked = a.bool("locked") ?: h.locked, muted = a.bool("muted") ?: h.muted,
                            solo = a.bool("solo") ?: h.solo, syncLock = a.bool("sync_lock") ?: h.syncLock, gainDb = a.double("gain_db")?.coerceIn(-96.0, 24.0) ?: h.gainDb)
                    }
                }; OK
            }),
        Capability("timeline.add_marker", EDIT, "Pone un marcador con una nota en un instante (por defecto, en el cabezal).",
            input = Schema.obj { us("at_us", "Instante; por defecto el cabezal"); str("label", "Nota.") }, output = Schema.obj { str("marker_id", "Marcador creado.") },
            handler = Handler.Edit { d, a ->
                val r = d.timeline { t, c -> TimelineEditor.addMarker(t, c, a.long("at_us") ?: d.view.playheadUs, a.str("label").orEmpty().take(120)) }
                buildJsonObject { put("marker_id", r.createdIds.first()) }
            }),
        Capability("timeline.remove_marker", EDIT, "Quita un marcador.",
            input = Schema.obj { str("marker_id", "Marcador.", required = true) }, errors = ENGINE,
            handler = Handler.Edit { d, a -> d.timeline { t, c -> TimelineEditor.removeMarker(t, c, a.req("marker_id")) }; OK }),
        Capability("timeline.seek", VIEW, "Mueve el cabezal a un instante, o cuadro a cuadro con «step_frames». El usuario lo ve moverse.",
            input = Schema.obj { us("at_us", "Instante absoluto"); int("step_frames", "Fotogramas que avanzar (negativo retrocede) desde el cabezal.") },
            output = Schema.obj { us("playhead_us", "Cabezal resultante") },
            handler = Handler.Edit { d, a ->
                val rate = d.project.settings.frameRate
                val target = a.long("at_us") ?: a.int("step_frames")?.let { rate.timeOf(rate.frameAt(d.view.playheadUs) + it) } ?: a.bad("indica «at_us» o «step_frames»")
                d.view = d.view.copy(playheadUs = target.coerceIn(0, maxOf(0, d.timeline.durationUs)))
                buildJsonObject { put("playhead_us", d.view.playheadUs) }
            }),
        Capability("timeline.select", VIEW, "Cambia la selección de clips que ve el usuario y, opcionalmente, las pistas destino de las próximas colocaciones.",
            input = Schema.obj {
                ids("clip_ids", "Clips."); str("mode", "Cómo se combina con la selección actual.", enum = listOf("replace", "add", "remove", "clear"))
                ids("target_track_ids", "Pistas destino; lista vacía las libera.")
            },
            errors = setOf(ErrorCode.NOT_FOUND),
            handler = Handler.Edit { d, a ->
                val ids = d.requireClips(a.ids("clip_ids"))
                val selection = when (a.str("mode") ?: "replace") {
                    "replace" -> ids
                    "add" -> d.view.selection + ids
                    "remove" -> d.view.selection - ids
                    "clear" -> emptySet()
                    else -> a.bad("«mode» debe ser replace, add, remove o clear")
                }
                val targets = if (a.has("target_track_ids")) a.ids("target_track_ids").onEach { d.track(it) } else d.view.targetTracks
                d.view = d.view.copy(selection = selection, targetTracks = targets)
                ids("selection", selection)
            }),
    )

    internal fun place(d: Draft, a: Args): JsonObject {
        val items = a.objects("items").ifEmpty { a.bad("«items» está vacío") }
        val mode = mode(a, EditMode.INSERT)
        val withAudio = a.bool("with_audio", true)
        val wantedVideo = a.str("video_track_id")?.let { d.track(it) }?.also { if (it.kind != TrackKind.VIDEO) a.bad("«video_track_id» no es una pista de vídeo") }
        val wantedAudio = a.str("audio_track_id")?.let { d.track(it) }?.also { if (it.kind != TrackKind.AUDIO) a.bad("«audio_track_id» no es una pista de audio") }
        fun chosen(kind: TrackKind): Track? = d.view.targetTracks.asSequence().mapNotNull { d.timeline.track(it) }.firstOrNull { it.kind == kind && !it.locked }
        fun videoTrack(): Track = wantedVideo ?: chosen(TrackKind.VIDEO)
            ?: d.timeline.videoTracks.firstOrNull { !it.locked && it.role != TrackRole.TITLES }
            ?: cutFail(ErrorCode.PRECONDITION_FAILED, "No hay ninguna pista de vídeo desbloqueada: añade una con timeline.add_track.")
        fun audioTrack(music: Boolean): Track? = wantedAudio ?: chosen(TrackKind.AUDIO)
            ?: (if (music) d.timeline.audioTracks.firstOrNull { !it.locked && it.role == TrackRole.MUSIC } else null)
            ?: d.timeline.audioTracks.firstOrNull { !it.locked && it.role != TrackRole.MUSIC }
            ?: d.timeline.audioTracks.firstOrNull { !it.locked }
        var at = a.long("at_us")
        val created = ArrayList<String>()
        for (item in items) {
            val m = d.media(item.req("media_id"))
            val sourceIn = (item.long("source_in_us") ?: 0).also { if (it < 0) item.bad("«source_in_us» no puede ser negativo") }
            val whole = m.durationUs?.let { it - sourceIn } ?: 5_000_000L
            val duration = item.long("duration_us") ?: whole
            if (duration <= 0) item.bad("«${m.displayName}» no tiene duración útil desde ese punto de entrada")
            val name = item.str("name") ?: m.displayName
            val placements = ArrayList<Placement>()
            when (m.kind) {
                MediaKind.VIDEO, MediaKind.IMAGE -> {
                    val audio = if (m.hasAudio && withAudio) audioTrack(music = false) else null
                    if (m.hasAudio && withAudio && audio == null) d.warnings += "No hay pista de audio desbloqueada: «${m.displayName}» entra sin su sonido."
                    placements += Placement(videoTrack().id, if (m.kind == MediaKind.IMAGE) ClipKind.IMAGE else ClipKind.VIDEO, m.id, sourceIn, duration, name = name, linkKey = if (audio != null) "av" else null)
                    if (audio != null) placements += Placement(audio.id, ClipKind.AUDIO, m.id, sourceIn, duration, name = name, linkKey = "av")
                }
                MediaKind.AUDIO -> {
                    val audio = audioTrack(music = true) ?: cutFail(ErrorCode.PRECONDITION_FAILED, "No hay ninguna pista de audio desbloqueada: añade una con timeline.add_track.")
                    placements += Placement(audio.id, ClipKind.AUDIO, m.id, sourceIn, duration, name = name)
                }
            }
            // «Al final» es el final de la pista principal de lo que se coloca (la imagen, o el audio si solo hay audio):
            // una cama de música larga ya puesta no debe empujar las tomas al final de la película.
            val lead = placements.firstOrNull { it.kind != ClipKind.AUDIO } ?: placements.first()
            val start = at ?: d.track(lead.trackId).endUs
            val r = d.timeline { t, c -> TimelineEditor.place(t, c, placements, start, mode) }
            val clips = r.createdIds.filter { d.timeline.clip(it) != null }
            val story = StoryLink(item.str("scene_id"), item.str("shot_id"))
            if (!story.isEmpty && clips.isNotEmpty()) d.timeline { t, c -> TimelineEditor.updateClips(t, c, clips.toSet(), "Insertar") { it.copy(story = story) } }
            created += clips
            at = start + duration
        }
        // Lo recién colocado queda seleccionado: dentro de la misma transacción, «ponle la música a −12 dB»
        // puede resolverse sin saber el id del clip, que no existía cuando se pidió.
        if (created.isNotEmpty()) d.view = d.view.copy(selection = created.toSet())
        return ids("clip_ids", created)
    }

    // ------------------------------------------------------------------ clip.*

    private fun clip() = listOf(
        Capability("clip.get", READ, "Todos los ajustes de uno o varios clips: tiempos, imagen (posición, escala, giro, opacidad, recorte, color, fundidos), audio, rótulo y keyframes.",
            input = Schema.obj { ids("clip_ids", "Clips; por defecto la selección.") }, errors = setOf(ErrorCode.NOT_FOUND),
            handler = Handler.Read { s, a ->
                val ids = a.ids("clip_ids").ifEmpty { s.view.selection }.ifEmpty { a.bad("indica «clip_ids» o selecciona clips antes") }
                buildJsonObject {
                    put("clips", buildJsonArray {
                        for (id in ids) {
                            val t = s.project.timeline.trackOfClip(id) ?: cutFail(ErrorCode.NOT_FOUND, "Clip inexistente ($id)")
                            add(CutJson.clipFull(t.clip(id)!!, s.project, t.id))
                        }
                    })
                }
            }),
        Capability("clip.split", EDIT, "Divide en un instante (por defecto, el cabezal). Con clips indicados o seleccionados corta esos y sus vinculados; sin ellos, todo lo que haya bajo ese instante en pistas desbloqueadas.",
            input = Schema.obj { us("at_us", "Instante del corte; por defecto el cabezal"); ids("clip_ids", "Clips que cortar.") }, output = CLIP_IDS_OUT, errors = ENGINE,
            handler = Handler.Edit { d, a ->
                val r = d.timeline { t, c -> TimelineEditor.split(t, c, d.requireClips(a.ids("clip_ids").ifEmpty { d.view.selection }), a.long("at_us") ?: d.view.playheadUs) }
                ids("clip_ids", r.createdIds)
            }),
        Capability("clip.delete", EDIT, "Elimina clips (y sus vinculados). Con «ripple» cierra el hueco y lo posterior se adelanta; sin él queda el hueco.",
            input = Schema.obj { ids("clip_ids", "Clips; por defecto la selección."); bool("ripple", "Cerrar el hueco; por defecto true.") }, errors = ENGINE,
            handler = Handler.Edit { d, a -> d.timeline { t, c -> TimelineEditor.delete(t, c, d.targets(a), a.bool("ripple", true)) }; OK }),
        Capability("clip.trim", EDIT, "Recorta o alarga el principio o el final de un clip, por incremento o hasta un instante. No puede pasar de lo que dura el medio; devuelve lo aplicado de verdad.",
            input = Schema.obj {
                str("clip_id", "Clip.", required = true); str("edge", "Borde.", required = true, enum = listOf("head", "tail"))
                us("delta_us", "Incremento del borde: negativo lo mueve hacia la izquierda"); us("to_us", "Instante al que llevar el borde. Alternativa a delta_us")
                bool("ripple", "Lo posterior acompaña al cambio de duración; por defecto true.")
            },
            output = Schema.obj { us("applied_delta_us", "Incremento realmente aplicado") }, errors = ENGINE,
            handler = Handler.Edit { d, a ->
                val clip = d.clip(a.req("clip_id")); val edge = a.enum<Edge>("edge") ?: a.bad("falta «edge»")
                val delta = a.long("delta_us") ?: a.long("to_us")?.let { it - if (edge == Edge.HEAD) clip.timelineStartUs else clip.endUs } ?: a.bad("indica «delta_us» o «to_us»")
                val r = d.timeline { t, c -> TimelineEditor.trim(t, c, clip.id, edge, delta, a.bool("ripple", true)) }
                buildJsonObject { put("applied_delta_us", r.appliedDeltaUs) }
            }),
        Capability("clip.move", EDIT, "Mueve clips (con sus vinculados y su grupo) en el tiempo y entre pistas. «overwrite» pisa lo que haya debajo; «insert» los extrae y los inserta en el destino, que es como se reordena.",
            input = Schema.obj {
                ids("clip_ids", "Clips; por defecto la selección."); str("anchor_clip_id", "Clip de referencia para «to_us»; por defecto el primero.")
                us("delta_us", "Desplazamiento"); us("to_us", "Nuevo inicio del clip de referencia. Alternativa a delta_us")
                int("track_offset", "Pistas que subir (+) o bajar (−)."); str("mode", "Cómo entra en el destino; por defecto overwrite.", enum = listOf("overwrite", "insert"))
            },
            output = Schema.obj { us("applied_delta_us", "Desplazamiento aplicado") }, errors = ENGINE,
            handler = Handler.Edit { d, a ->
                val ids = d.targets(a); val anchor = d.clip(a.str("anchor_clip_id") ?: ids.first())
                val delta = a.long("delta_us") ?: a.long("to_us")?.let { it - anchor.timelineStartUs } ?: 0L
                val r = d.timeline { t, c -> TimelineEditor.move(t, c, ids, anchor.id, delta, a.int("track_offset") ?: 0, mode(a, EditMode.OVERWRITE)) }
                buildJsonObject { put("applied_delta_us", r.appliedDeltaUs) }
            }),
        Capability("clip.duplicate", EDIT, "Duplica clips justo a continuación de los originales.",
            input = Schema.obj { ids("clip_ids", "Clips; por defecto la selección."); str("mode", "Cómo entra la copia; por defecto insert.", enum = listOf("insert", "overwrite")) },
            output = CLIP_IDS_OUT, errors = ENGINE,
            handler = Handler.Edit { d, a -> ids("clip_ids", d.timeline { t, c -> TimelineEditor.duplicate(t, c, d.targets(a), mode(a, EditMode.INSERT)) }.createdIds) }),
        Capability("clip.set_speed", EDIT, "Cambia la velocidad constante de un clip (entre 1/16x y 16x). Su duración cambia y los keyframes se reescalan con él.",
            input = Schema.obj { str("clip_id", "Clip.", required = true); str("speed", "Velocidad: «2», «0.5» o «1/4».", required = true); bool("ripple", "Lo posterior acompaña; por defecto true.") },
            errors = ENGINE,
            handler = Handler.Edit { d, a ->
                val speed = runCatching { Rational.parse(a.req("speed")) }.getOrElse { a.bad("«speed» no es una velocidad válida") }
                d.timeline { t, c -> TimelineEditor.setSpeed(t, c, d.clip(a.req("clip_id")).id, speed, a.bool("ripple", true)) }; OK
            }),
        Capability("clip.replace_media", EDIT, "Sustituye la toma de un clip por otro medio del montaje (otra generación, otra variante) conservando posición, duración y ajustes. El audio vinculado cambia con ella.",
            input = Schema.obj { str("clip_id", "Clip.", required = true); str("media_id", "Medio nuevo (media.list).", required = true) },
            preconditions = listOf("El medio nuevo cubre la duración del clip."), errors = ENGINE,
            handler = Handler.Edit { d, a -> d.timeline { t, c -> TimelineEditor.replaceMedia(t, c, d.clip(a.req("clip_id")).id, d.media(a.req("media_id")).id) }; OK }),
        Capability("clip.rename", EDIT, "Cambia el nombre visible de un clip.",
            input = Schema.obj { str("clip_id", "Clip.", required = true); str("name", "Nombre.", required = true) }, errors = ENGINE,
            handler = Handler.Edit { d, a -> val name = a.req("name").take(80); d.timeline { t, c -> TimelineEditor.updateClips(t, c, setOf(d.clip(a.req("clip_id")).id), "Renombrar") { it.copy(name = name) } }; OK }),
        Capability("clip.set_story", EDIT, "Vincula clips con una escena o un plano del guion de Nexus, o quita el vínculo.",
            input = Schema.obj { ids("clip_ids", "Clips; por defecto la selección."); str("scene_id", "Escena."); str("shot_id", "Plano."); bool("clear", "Quitar el vínculo.") },
            errors = ENGINE,
            handler = Handler.Edit { d, a ->
                val link = if (a.bool("clear", false)) null else StoryLink(a.str("scene_id"), a.str("shot_id")).takeUnless { it.isEmpty } ?: a.bad("indica «scene_id», «shot_id» o «clear»")
                d.timeline { t, c -> TimelineEditor.updateClips(t, c, d.targets(a), "Vincular con el guion") { it.copy(story = link) } }; OK
            }),
        Capability("clip.link", EDIT, "Vincula clips para que se editen juntos (como el vídeo y el audio de una misma toma).",
            input = Schema.obj { ids("clip_ids", "Dos o más clips; por defecto la selección.") }, errors = ENGINE,
            handler = Handler.Edit { d, a -> d.timeline { t, c -> TimelineEditor.link(t, c, d.targets(a)) }; OK }),
        Capability("clip.unlink", EDIT, "Separa clips vinculados para editar imagen y sonido por separado.",
            input = Schema.obj { ids("clip_ids", "Clips; por defecto la selección.") }, errors = ENGINE,
            handler = Handler.Edit { d, a -> d.timeline { t, c -> TimelineEditor.unlink(t, c, d.targets(a)) }; OK }),
        Capability("clip.group", EDIT, "Agrupa clips para que se muevan juntos conservando su separación.",
            input = Schema.obj { ids("clip_ids", "Dos o más clips; por defecto la selección.") }, errors = ENGINE,
            handler = Handler.Edit { d, a -> d.timeline { t, c -> TimelineEditor.createSyncGroup(t, c, d.targets(a)) }; OK }),
        Capability("clip.ungroup", EDIT, "Deshace el grupo de los clips indicados.",
            input = Schema.obj { ids("clip_ids", "Clips; por defecto la selección.") }, errors = ENGINE,
            handler = Handler.Edit { d, a -> d.timeline { t, c -> TimelineEditor.dissolveSyncGroup(t, c, d.targets(a)) }; OK }),
    )

    /** Aviso común: un valor fijo no se ve mientras ese parámetro tenga keyframes. */
    internal fun warnAnimated(d: Draft, clips: Set<String>, params: Map<String, com.vidadomu.nexus.cut.model.AnimParam>, a: Args) {
        for (id in clips) {
            val c: Clip = d.clip(id)
            val hidden = params.filter { (key, p) -> a.has(key) && c.keyframes[p]?.isEmpty == false }.keys
            if (hidden.isNotEmpty()) d.warnings += "«${c.name.ifBlank { c.id }}» tiene animado ${hidden.joinToString()}: el valor fijo no se verá hasta quitar sus keyframes (keyframe.clear) o usa keyframe.set."
        }
    }
}
