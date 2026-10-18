package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.Rational
import com.vidadomu.corte.model.TextAlign
import com.vidadomu.corte.model.TextProps
import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.TransitionKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Operaciones que un agente externo (Nexus) pide en JSON. Una lista se aplica como UNA sola edición:
 * o entran todas y quedan validadas por el motor, o no cambia nada y se devuelve el motivo exacto.
 * El resultado se guarda en el historial como un único paso, así «Deshacer» quita lo que hizo el agente de una vez.
 *
 * Formato: `[{"op":"split","at_us":1200000}, {"op":"delete_ranges","ranges":[[1,2]],"ripple":true}, …]`.
 * Tiempos en microsegundos de la línea de tiempo; identificadores tal como los devuelve `read_project`.
 */
object AgentOps {
    val NAMES = listOf(
        "split", "delete", "delete_ranges", "trim", "move", "close_gap", "set_speed", "duplicate",
        "set_transition", "remove_transition", "add_title", "add_marker", "remove_marker", "set_audio", "set_video", "set_color",
    )

    fun apply(timeline: Timeline, ctx: EditContext, ops: JsonArray, label: String = "Edición del agente"): EditResult {
        if (ops.isEmpty()) return EditResult.Rejected("No hay operaciones")
        var current = timeline
        val warnings = ArrayList<String>()
        val collateral = ArrayList<Collateral>()
        val created = ArrayList<String>()
        for ((i, el) in ops.withIndex()) {
            val op = (el as? JsonObject) ?: return EditResult.Rejected("Operación ${i + 1}: no es un objeto JSON")
            val name = op.str("op") ?: return EditResult.Rejected("Operación ${i + 1}: falta «op»")
            val r = try {
                one(current, ctx, name, op)
            } catch (e: IllegalArgumentException) {
                EditResult.Rejected(e.message ?: "parámetros no válidos")
            }
            when (r) {
                is EditResult.Rejected -> return EditResult.Rejected("Operación ${i + 1} ($name): ${r.reason}")
                is EditResult.Applied -> {
                    current = r.timeline
                    warnings += r.warnings; collateral += r.collateral; created += r.createdIds
                }
            }
        }
        val changes = ChangeSet.diff(timeline, current)
        return EditResult.Applied(current, changes, label, warnings.distinct(), collateral.distinct(), createdIds = created)
    }

    private fun one(t: Timeline, ctx: EditContext, name: String, o: JsonObject): EditResult = when (name) {
        "split" -> TimelineEditor.split(t, ctx, o.ids("clip_ids"), o.long("at_us"))
        "delete" -> TimelineEditor.delete(t, ctx, o.ids("clip_ids").ifEmpty { bad("clip_ids vacío") }, o.bool("ripple", true))
        "delete_ranges" -> deleteRanges(t, ctx, o)
        "trim" -> TimelineEditor.trim(t, ctx, o.req("clip_id"), edge(o.str("edge") ?: "tail"), o.long("delta_us"), o.bool("ripple", true))
        "move" -> {
            val ids = o.ids("clip_ids").ifEmpty { bad("clip_ids vacío") }
            TimelineEditor.move(t, ctx, ids, o.str("anchor_clip_id") ?: ids.first(), o.long("delta_us"), o.long("track_offset", 0).toInt(), mode(o))
        }
        "close_gap" -> TimelineEditor.closeGap(t, ctx, o.req("track_id"), o.long("at_us"))
        "set_speed" -> TimelineEditor.setSpeed(t, ctx, o.req("clip_id"), Rational(o.long("num"), o.long("den", 1)), o.bool("ripple", true))
        "duplicate" -> TimelineEditor.duplicate(t, ctx, o.ids("clip_ids").ifEmpty { bad("clip_ids vacío") }, mode(o))
        "set_transition" -> TimelineEditor.setTransition(t, ctx, o.req("track_id"), o.req("left_clip_id"), o.req("right_clip_id"),
            enumValue<TransitionKind>(o.str("kind") ?: "CROSS_DISSOLVE"), o.long("duration_us", 500_000))
        "remove_transition" -> TimelineEditor.removeTransition(t, ctx, o.req("transition_id"))
        "add_title" -> TimelineEditor.addTitle(t, ctx, o.long("at_us"), o.long("duration_us", 3_000_000), TextProps(
            text = o.req("text"), size = o.double("size") ?: 0.08, bold = o.bool("bold", true),
            align = enumValue<TextAlign>(o.str("align") ?: "CENTER"), boxOpacity = o.double("box_opacity") ?: 0.0))
        "add_marker" -> TimelineEditor.addMarker(t, ctx, o.long("at_us"), o.str("label") ?: "")
        "remove_marker" -> TimelineEditor.removeMarker(t, ctx, o.req("marker_id"))
        "set_audio" -> TimelineEditor.updateClips(t, ctx, o.ids("clip_ids").ifEmpty { bad("clip_ids vacío") }, "Audio") { c ->
            c.copy(audio = c.audio.copy(
                gainDb = o.double("gain_db") ?: c.audio.gainDb, muted = o.boolOrNull("muted") ?: c.audio.muted,
                fadeInUs = o.longOrNull("fade_in_us") ?: c.audio.fadeInUs, fadeOutUs = o.longOrNull("fade_out_us") ?: c.audio.fadeOutUs))
        }
        "set_video" -> TimelineEditor.updateClips(t, ctx, o.ids("clip_ids").ifEmpty { bad("clip_ids vacío") }, "Imagen") { c ->
            c.copy(video = c.video.copy(
                opacity = o.double("opacity") ?: c.video.opacity, scale = o.double("scale") ?: c.video.scale,
                positionX = o.double("position_x") ?: c.video.positionX, positionY = o.double("position_y") ?: c.video.positionY,
                fadeInUs = o.longOrNull("fade_in_us") ?: c.video.fadeInUs, fadeOutUs = o.longOrNull("fade_out_us") ?: c.video.fadeOutUs))
        }
        "set_color" -> TimelineEditor.updateClips(t, ctx, o.ids("clip_ids").ifEmpty { bad("clip_ids vacío") }, "Color") { c ->
            val k = c.video.color
            c.copy(video = c.video.copy(color = k.copy(
                exposure = o.double("exposure")?.toFloat() ?: k.exposure, contrast = o.double("contrast")?.toFloat() ?: k.contrast,
                saturation = o.double("saturation")?.toFloat() ?: k.saturation, temperature = o.double("temperature")?.toFloat() ?: k.temperature,
                tint = o.double("tint")?.toFloat() ?: k.tint, fade = o.double("fade")?.toFloat() ?: k.fade)))
        }
        else -> EditResult.Rejected("operación desconocida; válidas: ${NAMES.joinToString()}")
    }

    /**
     * Quita tramos [inicio, fin) de la línea de tiempo (p. ej. silencios): corta en los bordes y borra lo que queda dentro.
     * Se procesan de atrás hacia delante para que, con ripple, los tramos anteriores no se desplacen.
     * `track_ids` limita las pistas; sin él actúa en todas (los clips vinculados se cortan y borran juntos).
     */
    private fun deleteRanges(t: Timeline, ctx: EditContext, o: JsonObject): EditResult {
        val ranges = (o["ranges"] as? JsonArray ?: bad("falta «ranges»")).map { r ->
            val a = r.jsonArray
            require(a.size == 2) { "cada tramo es [inicio_us, fin_us]" }
            a[0].jsonPrimitive.long() to a[1].jsonPrimitive.long()
        }.onEach { (a, b) -> require(b > a && a >= 0) { "tramo no válido: [$a, $b]" } }
        val tracks = o.ids("track_ids").takeIf { it.isNotEmpty() }
        val ripple = o.bool("ripple", true)
        var cur = t
        val created = ArrayList<String>()
        for ((a, b) in merge(ranges).asReversed()) {
            for (cut in listOf(b, a)) {
                val crossing = cur.allTracks.filter { tracks == null || it.id in tracks }
                    .flatMap { tr -> tr.clips.filter { it.timelineStartUs < cut && it.endUs > cut }.map { it.id } }.toSet()
                if (crossing.isEmpty()) continue
                when (val r = TimelineEditor.split(cur, ctx, crossing, cut)) {
                    is EditResult.Rejected -> return r
                    is EditResult.Applied -> { cur = r.timeline; created += r.createdIds }
                }
            }
            val inside = cur.allTracks.filter { tracks == null || it.id in tracks }
                .flatMap { tr -> tr.clips.filter { it.timelineStartUs >= a && it.endUs <= b }.map { it.id } }.toSet()
            if (inside.isEmpty()) continue
            when (val r = TimelineEditor.delete(cur, ctx, inside, ripple)) {
                is EditResult.Rejected -> return r
                is EditResult.Applied -> cur = r.timeline
            }
        }
        return EditResult.Applied(cur, ChangeSet.diff(t, cur), "Quitar tramos", createdIds = created)
    }

    private fun merge(r: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        for (p in r.sortedBy { it.first }) {
            val last = out.lastOrNull()
            if (last != null && p.first <= last.second) out[out.size - 1] = last.first to maxOf(last.second, p.second) else out += p
        }
        return out
    }

    private fun edge(s: String) = if (s.equals("head", true)) Edge.HEAD else Edge.TAIL
    private fun mode(o: JsonObject) = if ((o.str("mode") ?: "insert").equals("overwrite", true)) EditMode.OVERWRITE else EditMode.INSERT
    private inline fun <reified E : Enum<E>> enumValue(s: String): E =
        enumValues<E>().firstOrNull { it.name.equals(s, true) } ?: bad("valor no válido «$s»; válidos: ${enumValues<E>().joinToString { it.name }}")
    private fun bad(msg: String): Nothing = throw IllegalArgumentException(msg)

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.req(k: String): String = str(k)?.takeIf { it.isNotBlank() } ?: bad("falta «$k»")
    private fun JsonObject.long(k: String, def: Long? = null): Long = longOrNull(k) ?: def ?: bad("falta «$k» (número entero)")
    private fun JsonObject.longOrNull(k: String): Long? = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    private fun JsonObject.double(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.bool(k: String, def: Boolean): Boolean = boolOrNull(k) ?: def
    private fun JsonObject.boolOrNull(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.ids(k: String): Set<String> = (this[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet().orEmpty()
    private fun JsonPrimitive.long(): Long = longOrNull ?: doubleOrNull?.toLong() ?: bad("se esperaba un número: $content")
    @Suppress("unused") private fun JsonElement.obj() = jsonObject
}
