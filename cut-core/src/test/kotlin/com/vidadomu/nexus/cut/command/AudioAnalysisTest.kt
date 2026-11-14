package com.vidadomu.nexus.cut.command

import com.vidadomu.nexus.cut.model.IdGenerator
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.model.Rational
import com.vidadomu.nexus.cut.model.TimeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Detección de silencios: la lógica pura y el encaje con el bus y con «timeline.delete_ranges». */
class AudioAnalysisTest {

    // -------------------------------------------------------------- lógica pura

    /** Una envolvente a 20 ms por ventana a partir de tramos «(dBFS, cuántas ventanas)». */
    private fun envelope(fromUs: Long = 0, vararg runs: Pair<Double, Int>) =
        LoudnessEnvelope(fromUs, Silences.WINDOW_US, runs.flatMap { (db, n) -> List(n) { db } })

    @Test fun `un silencio largo se detecta y se le deja aire, y una pausa corta no cuenta`() {
        // 0,4 s de voz · 1 s de silencio · 0,2 s de voz · 0,2 s de pausa corta · 0,4 s de voz
        val env = envelope(0, -12.0 to 20, -70.0 to 50, -12.0 to 10, -70.0 to 10, -12.0 to 20)
        val found = Silences.detect(env)
        assertEquals(1, found.size)
        // El silencio va de 0,4 s a 1,4 s; con 0,08 s de aire a cada lado queda 0,48–1,32 s.
        assertEquals(TimeRange(480_000, 1_320_000), found.single())
    }

    @Test fun `el umbral y el minimo mandan sobre lo que se considera silencio`() {
        val env = envelope(0, -12.0 to 10, -30.0 to 40, -12.0 to 10)
        // A -40 dBFS, -30 es sonido: no hay silencio.
        assertTrue(Silences.detect(env).isEmpty())
        // A -25 sí lo es, y con aire 0 el tramo sale entero.
        assertEquals(TimeRange(200_000, 1_000_000), Silences.detect(env, thresholdDb = -25.0, keepUs = 0).single())
        // Subiendo el mínimo por encima de su duración, deja de contar.
        assertTrue(Silences.detect(env, thresholdDb = -25.0, minDurationUs = 1_000_000).isEmpty())
    }

    @Test fun `un silencio al final sin voz despues tambien cuenta, y el aire puede dejarlo en nada`() {
        val env = envelope(0, -12.0 to 10, -70.0 to 30)
        assertEquals(TimeRange(280_000, 720_000), Silences.detect(env).single())
        // Con más aire que silencio, se descarta en vez de salir invertido.
        assertTrue(Silences.detect(env, keepUs = 400_000).isEmpty())
    }

    @Test fun `la envolvente empieza donde empieza el recorte del clip`() {
        val env = envelope(2_000_000, -12.0 to 5, -70.0 to 40)
        assertEquals(TimeRange(2_180_000, 2_820_000), Silences.detect(env).single())
    }

    @Test fun `unir tramos deja los solapados y pegados en uno solo y en orden`() {
        val merged = Silences.merge(listOf(
            TimeRange(5_000, 7_000), TimeRange(0, 2_000), TimeRange(1_500, 3_000), TimeRange(3_000, 4_000)))
        assertEquals(listOf(TimeRange(0, 4_000), TimeRange(5_000, 7_000)), merged)
        assertEquals(emptyList<TimeRange>(), Silences.merge(emptyList()))
    }

    // -------------------------------------------------------------- por el bus

    private val voice = MediaRef("m_voice", "file:///voz.m4a", "Voz", MediaKind.AUDIO, 10_000_000, hasAudio = true)
    private val mute = MediaRef("m_mute", "file:///cartel.png", "Cartel", MediaKind.IMAGE, null, 1024, 1024, hasVideo = true)

    /** Analizador falso: siempre voz, con un silencio de 1 s a partir del segundo 1 del archivo. */
    private var asked = 0
    private val analyzer = CutAudioAnalyzer { _, fromUs, toUs, windowUs ->
        asked++
        val windows = ((toUs - fromUs) / windowUs).toInt()
        LoudnessEnvelope(fromUs, windowUs, List(windows) { i ->
            val at = fromUs + i * windowUs
            if (at in 1_000_000 until 2_000_000) -70.0 else -12.0
        })
    }

    private fun registry(features: Set<String> = setOf(AudioAnalysisCapabilities.ANALYZER)) =
        CutRegistry { features }.register(CoreCapabilities.all() + AudioAnalysisCapabilities.all(analyzer))

    /** Un montaje con una pista de audio y un clip de voz de 6 s, más un cartel sin sonido. */
    private fun session(features: Set<String> = setOf(AudioAnalysisCapabilities.ANALYZER)): CutSession {
        val base = CutProjects.create("Silencios", 1, IdGenerator.sequential())
        val project = base.copy(media = listOf(voice, mute))
        return CutSession(project, registry(features), ids = IdGenerator.sequential(), io = Dispatchers.Unconfined)
    }

    private fun Outcome.done() = this as? Outcome.Done ?: throw AssertionError((this as Outcome.Failed).error.toString())

    private suspend fun CutSession.placeVoice(speed: String? = null): String {
        run("timeline.place", Origin.USER, "items" to listOf(mapOf("media_id" to "m_voice", "duration_us" to 6_000_000L))).done()
        val clip = state.value.project.timeline.allTracks.first { it.clips.isNotEmpty() }.clips.first()
        if (speed != null) run("clip.set_speed", Origin.USER, "clip_id" to clip.id, "speed" to speed).done()
        return clip.id
    }

    @Test fun `los silencios salen en tiempo de la linea de tiempo y se pueden borrar tal cual`() = runTest {
        val s = session()
        val clip = s.placeVoice()
        val out = s.run("audio.find_silences", Origin.AGENT).done().results.single()
        assertEquals(1, out["analyzed"]!!.jsonPrimitive.int)
        val silence = out["silences"]!!.jsonArray.single().jsonObject
        assertEquals(clip, silence["clip_id"]!!.jsonPrimitive.content)
        assertEquals(1_080_000L, silence["start_us"]!!.jsonPrimitive.long)
        assertEquals(1_920_000L, silence["end_us"]!!.jsonPrimitive.long)
        assertEquals(840_000L, out["total_us"]!!.jsonPrimitive.long)

        // Lo que devuelve es exactamente lo que come «timeline.delete_ranges»: sin traducir nada.
        val before = s.state.value.project.timeline.durationUs
        s.run("timeline.delete_ranges", Origin.AGENT, "ranges" to out["ranges"]!!).done()
        assertEquals(before - 840_000L, s.state.value.project.timeline.durationUs)
        // Y se deshace de una vez, como cualquier edición.
        s.undo(Origin.USER).done()
        assertEquals(before, s.state.value.project.timeline.durationUs)
    }

    @Test fun `la velocidad del clip se tiene en cuenta al pasar del archivo a la linea de tiempo`() = runTest {
        val s = session()
        s.placeVoice(speed = "2")
        val out = s.run("audio.find_silences", Origin.AGENT).done().results.single()
        // A doble velocidad, el segundo de silencio del archivo ocupa medio segundo de montaje.
        val silence = out["silences"]!!.jsonArray.single().jsonObject
        assertEquals(540_000L, silence["start_us"]!!.jsonPrimitive.long)
        assertEquals(960_000L, silence["end_us"]!!.jsonPrimitive.long)
    }

    @Test fun `consultar no cambia nada ni deja nada que deshacer`() = runTest {
        val s = session()
        s.placeVoice()
        val revision = s.state.value.revision
        s.run("audio.find_silences", Origin.AGENT).done()
        assertEquals(revision, s.state.value.revision)
        assertEquals("audio.find_silences", registry()["audio.find_silences"]!!.id)
        assertTrue(!registry()["audio.find_silences"]!!.reversible)
    }

    @Test fun `sin audio que analizar lo dice en vez de devolver una lista vacia`() = runTest {
        val s = session()
        s.run("timeline.place", Origin.USER, "items" to listOf(mapOf("media_id" to "m_mute", "duration_us" to 3_000_000L))).done()
        val error = (s.run("audio.find_silences", Origin.AGENT) as Outcome.Failed).error
        assertEquals(ErrorCode.PRECONDITION_FAILED, error.code)
        // Pedido a mano, nombra el clip en vez de callárselo.
        val still = s.state.value.project.timeline.allTracks.first { it.clips.isNotEmpty() }.clips.first().id
        val named = (s.run("audio.find_silences", Origin.AGENT, "clip_ids" to listOf(still)) as Outcome.Failed).error
        assertEquals(ErrorCode.PRECONDITION_FAILED, named.code)
        assertTrue(named.message, still in named.message)
        assertEquals(ErrorCode.NOT_FOUND, (s.run("audio.find_silences", Origin.AGENT, "clip_ids" to listOf("clip_99")) as Outcome.Failed).error.code)
    }

    @Test fun `donde el motor no analiza audio la capacidad existe pero no se ejecuta`() = runTest {
        val s = session(features = emptySet())
        s.placeVoice()
        val error = (s.run("audio.find_silences", Origin.AGENT) as Outcome.Failed).error
        assertEquals(ErrorCode.UNAVAILABLE, error.code)
        assertTrue(error.message, AudioAnalysisCapabilities.ANALYZER in error.message)
        // Sigue en el catálogo, marcada como no disponible.
        val spec = registry(emptySet()).describe(listOf("audio.find_silences"))["capabilities"]!!.jsonArray.single().jsonObject
        assertEquals(false, spec["available"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test fun `el umbral fuera de rango se rechaza antes de decodificar nada`() = runTest {
        val s = session()
        s.placeVoice()
        asked = 0
        assertEquals(ErrorCode.INVALID_ARGUMENT, (s.run("audio.find_silences", Origin.AGENT, "threshold_db" to -200.0) as Outcome.Failed).error.code)
        assertEquals(ErrorCode.INVALID_ARGUMENT, (s.run("audio.find_silences", Origin.AGENT, "min_duration_us" to 0L) as Outcome.Failed).error.code)
        assertEquals(0, asked)
    }

    @Test fun `la velocidad racional no desplaza el silencio`() {
        // Comprobación directa del mapeo: 3/2 de velocidad, silencio de 3 s en el archivo → 2 s en montaje.
        val speed = Rational(3, 2)
        assertEquals(2_000_000L, speed.inverseScale(3_000_000L))
    }
}
