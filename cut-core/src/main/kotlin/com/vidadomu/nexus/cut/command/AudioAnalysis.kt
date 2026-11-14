package com.vidadomu.nexus.cut.command

import com.vidadomu.nexus.cut.command.CapabilityKind.READ
import com.vidadomu.nexus.cut.model.Clip
import com.vidadomu.nexus.cut.model.ClipKind
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.model.TimeRange
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Volumen medido de un archivo: un valor en dBFS por cada ventana de [windowUs], en orden, empezando
 * en [fromUs] del propio archivo (no de la línea de tiempo). 0 dBFS es el máximo que admite el
 * formato; a partir de unos -40 ya se oye como silencio, y -90 o menos es silencio digital.
 */
class LoudnessEnvelope(val fromUs: Long, val windowUs: Long, val dbfs: List<Double>) {
    init {
        require(windowUs > 0) { "la ventana debe ser positiva ($windowUs)" }
        require(fromUs >= 0) { "el inicio no puede ser negativo ($fromUs)" }
    }

    /** Hasta dónde llega lo medido. */
    val toUs: Long get() = fromUs + windowUs * dbfs.size
}

/**
 * Mide el volumen real del audio de un medio. La implementa la app, que decodifica el archivo; en
 * pruebas, un doble. Debe cubrir desde [fromUs] hasta al menos [toUs]; si el archivo se acaba antes,
 * devuelve lo que haya. Lanza [CutFailure] NOT_FOUND si el archivo ya no está.
 */
fun interface CutAudioAnalyzer {
    suspend fun loudness(media: MediaRef, fromUs: Long, toUs: Long, windowUs: Long): LoudnessEnvelope
}

/**
 * Detección de silencios: lógica pura sobre la envolvente, sin tocar archivos ni el proyecto, para
 * poder probarla en la JVM y para que dé el mismo resultado en el móvil y en el escritorio.
 */
object Silences {
    /** Por debajo de esto se considera silencio. -40 dBFS funciona para voz grabada limpia. */
    const val THRESHOLD_DB = -40.0
    /** Un silencio más corto que esto es una pausa natural del habla: no se toca. */
    const val MIN_DURATION_US = 400_000L
    /** Aire que se deja a cada lado para no cortar el ataque ni la cola de la voz. */
    const val KEEP_US = 80_000L
    /** Resolución del análisis: 20 ms es fino para voz y barato de decodificar. */
    const val WINDOW_US = 20_000L

    /**
     * Tramos callados de [env], en su mismo reloj. Un tramo cuenta si está por debajo de
     * [thresholdDb] durante al menos [minDurationUs]; luego se le recortan [keepUs] por cada lado y,
     * si lo que queda no llega a [minKeptUs], se descarta.
     */
    fun detect(
        env: LoudnessEnvelope,
        thresholdDb: Double = THRESHOLD_DB,
        minDurationUs: Long = MIN_DURATION_US,
        keepUs: Long = KEEP_US,
        minKeptUs: Long = 1L,
    ): List<TimeRange> {
        val out = ArrayList<TimeRange>()
        var i = 0
        while (i < env.dbfs.size) {
            if (env.dbfs[i] > thresholdDb) { i++; continue }
            var j = i
            while (j < env.dbfs.size && env.dbfs[j] <= thresholdDb) j++
            val start = env.fromUs + i * env.windowUs
            val end = env.fromUs + j * env.windowUs
            if (end - start >= minDurationUs) {
                val s = start + keepUs
                val e = end - keepUs
                if (e - s >= minKeptUs) out += TimeRange(s, e)
            }
            i = j
        }
        return out
    }

    /** Une los tramos que se solapan o se tocan y los deja en orden: lo que espera `timeline.delete_ranges`. */
    fun merge(ranges: List<TimeRange>): List<TimeRange> {
        if (ranges.size < 2) return ranges.toList()
        val sorted = ranges.sortedBy { it.startUs }
        val out = ArrayList<TimeRange>(sorted.size)
        var cur = sorted.first()
        for (r in sorted.drop(1)) {
            cur = if (r.startUs <= cur.endUs) TimeRange(cur.startUs, maxOf(cur.endUs, r.endUs)) else { out += cur; r }
        }
        out += cur
        return out
    }
}

/**
 * Análisis del audio del montaje. Depende del dispositivo (hay que decodificar el archivo), así que
 * se registra con un [CutAudioAnalyzer] y solo está disponible donde el motor lo declare.
 */
object AudioAnalysisCapabilities {
    const val ANALYZER = "engine.audio_analysis"
    private const val MAX_CLIPS = 24

    fun all(analyzer: CutAudioAnalyzer): List<Capability> = listOf(findSilences(analyzer))

    private val RANGE_ITEM = buildJsonObject {
        put("type", "array"); put("minItems", 2); put("maxItems", 2)
        put("items", buildJsonObject { put("type", "integer") })
    }

    private fun findSilences(analyzer: CutAudioAnalyzer) = Capability("audio.find_silences", READ,
        "Busca los silencios reales del audio de unos clips: decodifica la onda (no se fía de los metadatos) y devuelve los tramos callados en tiempo de la línea de tiempo, ya unidos, para pasarlos tal cual como «ranges» a «timeline.delete_ranges». No cambia nada.",
        input = Schema.obj {
            ids("clip_ids", "Clips a analizar; por defecto la selección y, si no hay nada seleccionado, todos los clips con sonido. Hasta $MAX_CLIPS.")
            num("threshold_db", "Por debajo de cuántos dBFS se considera silencio; por defecto -40 (voz grabada limpia). Más cerca de 0, más agresivo.", min = -90.0, max = 0.0)
            us("min_duration_us", "Ignorar los silencios más cortos que esto, que son pausas del habla; por defecto ${Silences.MIN_DURATION_US}")
            us("keep_us", "Aire que se deja a cada lado del silencio para no cortar la voz; por defecto ${Silences.KEEP_US}")
        },
        output = Schema.obj {
            list("silences", "Cada silencio encontrado, con el clip en el que está.", items = Schema.obj {
                str("clip_id", "Clip"); str("track_id", "Pista")
                us("start_us", "Inicio en la línea de tiempo"); us("end_us", "Fin"); us("duration_us", "Duración")
            })
            list("ranges", "Los mismos tramos ya unidos, como [inicio_us, fin_us]: entrada directa de «timeline.delete_ranges».", items = RANGE_ITEM)
            us("total_us", "Cuánto silencio suma en total")
            int("analyzed", "Clips analizados")
        },
        requires = setOf(ANALYZER),
        errors = setOf(ErrorCode.NOT_FOUND, ErrorCode.PRECONDITION_FAILED),
        handler = Handler.Read { s, a ->
            val threshold = a.double("threshold_db") ?: Silences.THRESHOLD_DB
            if (threshold < -90.0 || threshold > 0.0) a.bad("«threshold_db» va de -90 a 0")
            val minDuration = a.long("min_duration_us") ?: Silences.MIN_DURATION_US
            if (minDuration <= 0) a.bad("«min_duration_us» debe ser positivo")
            val keep = a.long("keep_us") ?: Silences.KEEP_US
            if (keep < 0) a.bad("«keep_us» no puede ser negativo")

            val asked = a.ids("clip_ids")
            val candidates = targets(s, asked)
            if (candidates.size > MAX_CLIPS) a.bad("como mucho $MAX_CLIPS clips por vez; indica «clip_ids»")

            val found = ArrayList<Triple<Clip, String, TimeRange>>()
            var analyzed = 0
            for ((clip, trackId) in candidates) {
                val media = s.project.media(clip.mediaId)?.takeIf { it.hasAudio && !clip.audio.muted }
                if (media == null) {
                    // Pedido a mano: hay que decirlo. Por defecto, simplemente no se analiza.
                    if (asked.isEmpty()) continue
                    cutFail(ErrorCode.PRECONDITION_FAILED, if (clip.audio.muted)
                        "El clip ${clip.id} está silenciado: no tiene sonido que analizar."
                    else "El clip ${clip.id} no tiene sonido que analizar.")
                }
                analyzed++
                val env = analyzer.loudness(media, clip.sourceInUs, clip.sourceOutUs, Silences.WINDOW_US)
                for (quiet in Silences.detect(env, threshold, minDuration, keep)) {
                    val range = toTimeline(clip, quiet) ?: continue
                    found += Triple(clip, trackId, range)
                }
            }
            if (analyzed == 0) cutFail(ErrorCode.PRECONDITION_FAILED, "No hay clips con sonido que analizar.")

            val merged = Silences.merge(found.map { it.third })
            buildJsonObject {
                put("silences", buildJsonArray {
                    for ((clip, trackId, r) in found.sortedBy { it.third.startUs }) add(buildJsonObject {
                        put("clip_id", clip.id); put("track_id", trackId)
                        put("start_us", r.startUs); put("end_us", r.endUs); put("duration_us", r.durationUs)
                    })
                })
                put("ranges", JsonArray(merged.map { JsonArray(listOf(JsonPrimitive(it.startUs), JsonPrimitive(it.endUs))) }))
                put("total_us", merged.sumOf { it.durationUs })
                put("analyzed", analyzed)
            }
        })

    /** Los clips sobre los que mirar: los pedidos, o la selección, o todo lo que suene. */
    private fun targets(s: CutState, asked: Set<String>): List<Pair<Clip, String>> {
        if (asked.isNotEmpty()) return asked.map { id ->
            val track = s.project.timeline.trackOfClip(id) ?: cutFail(ErrorCode.NOT_FOUND, "Clip inexistente ($id)")
            track.clip(id)!! to track.id
        }
        val all = s.project.timeline.allTracks.flatMap { t -> t.clips.map { it to t.id } }
        val selected = all.filter { it.first.id in s.view.selection }
        return (selected.ifEmpty { all }).filter { (clip, _) ->
            clip.kind != ClipKind.TEXT && !clip.audio.muted && s.project.media(clip.mediaId)?.hasAudio == true
        }
    }

    /**
     * Pasa un tramo del archivo al reloj de la línea de tiempo teniendo en cuenta la velocidad del
     * clip, y lo recorta a lo que el clip muestra de verdad. Null si queda fuera.
     */
    private fun toTimeline(clip: Clip, source: TimeRange): TimeRange? {
        val start = clip.timelineStartUs + clip.speed.inverseScale(source.startUs - clip.sourceInUs)
        val end = clip.timelineStartUs + clip.speed.inverseScale(source.endUs - clip.sourceInUs)
        if (end <= start) return null
        return TimeRange(start, end).intersect(clip.range)
    }
}
