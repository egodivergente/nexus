package com.vidadomu.nexus.cut.command

import com.vidadomu.nexus.cut.model.ClipKind
import com.vidadomu.nexus.cut.model.IdGenerator
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.model.Project
import com.vidadomu.nexus.cut.model.TrackRole
import com.vidadomu.nexus.cut.storage.ProjectCodec
import com.vidadomu.nexus.cut.storage.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** El contrato del bus: un solo camino para la interfaz y el agente, atómico, reversible y verificable. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CutSessionTest {
    private val take1 = MediaRef("m1", "content://a", "Mateo PP toma 1", MediaKind.VIDEO, 10_000_000, 1920, 1080, hasVideo = true, hasAudio = true, assetId = "asset_1", generationId = "gen_1")
    private val take2 = MediaRef("m2", "content://b", "Mateo PP toma 2", MediaKind.VIDEO, 12_000_000, 1920, 1080, hasVideo = true, hasAudio = true, assetId = "asset_2")
    private val short = MediaRef("m3", "content://c", "Inserto", MediaKind.VIDEO, 4_000_000, 1920, 1080, hasVideo = true)
    private val music = MediaRef("m4", "content://d", "Tema", MediaKind.AUDIO, 60_000_000, hasAudio = true)
    private val still = MediaRef("m5", "content://e", "Cartel", MediaKind.IMAGE, null, 1024, 1024, hasVideo = true)

    private fun project() = CutProjects.create("Corto", nowMs = 1, ids = IdGenerator.sequential(), nexusProjectId = "proj_1")
        .copy(media = listOf(take1, take2, short, music, still))

    private fun registry(features: Set<String> = emptySet()) = CutRegistry { features }.register(CoreCapabilities.all())

    private fun session(project: Project = project(), registry: CutRegistry = registry(), store: ProjectStore? = null, log: ActionLog? = null): CutSession {
        var now = 100L
        return CutSession(project, registry, store, log, IdGenerator.sequential(), clock = { ++now }, io = Dispatchers.Unconfined)
    }

    private fun args(vararg p: Pair<String, Any?>) = CutSession.argsOf(*p)
    private fun Outcome.done() = this as? Outcome.Done ?: throw AssertionError((this as Outcome.Failed).error.toString())
    private fun Outcome.error() = (this as Outcome.Failed).error
    private val CutSession.timeline get() = state.value.project.timeline
    private fun CutSession.clipsOn(track: String) = timeline.track(track)!!.clips
    private suspend fun CutSession.placeTwo() = run("timeline.place", Origin.USER, "items" to listOf(mapOf("media_id" to "m1"), mapOf("media_id" to "m3"))).done()

    // ---- catálogo

    @Test fun `el catalogo es descubrible por dominio e intencion y cada capacidad publica su contrato`() {
        val r = registry()
        assertTrue("capacidades: ${r.size}", r.size >= 50)
        assertEquals(setOf("project", "media", "timeline", "clip", "effects", "audio", "keyframe", "subtitle"), r.domains.keys)
        assertEquals("clip.split", r.search(query = "dividir en el cabezal").first().id)
        assertTrue(r.search(domain = "subtitle").all { it.domain == "subtitle" })
        assertTrue(r.search(kinds = setOf(CapabilityKind.READ)).none { it.kind != CapabilityKind.READ })
        val spec = r.describe(listOf("clip.trim", "no.existe"))
        val trim = spec["capabilities"]!!.jsonArray.single().jsonObject
        assertEquals("object", trim["input"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(trim["reversible"]!!.jsonPrimitive.boolean)
        assertTrue("REJECTED_BY_ENGINE" in trim["errors"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("no.existe", spec["unknown"]!!.jsonArray.single().jsonPrimitive.content)
        // El catálogo resumido no arrastra los schemas: es lo que el agente recibe al explorar.
        assertNull(r.catalog(domain = "clip")["capabilities"]!!.jsonArray.first().jsonObject["input"])
        for (c in r.all) assertTrue(c.id, c.summary.length > 10 && c.input["type"] != null)
    }

    // ---- edición por el bus

    @Test fun `colocar medios crea clips vinculados, sube la revision y queda guardado`() = runTest {
        val dir = Files.createTempDirectory("cut").toFile(); val store = ProjectStore(dir)
        val s = session(store = store, log = ActionLog(File(dir, "log.jsonl")))
        val events = ArrayList<CutEvent>(); val job = launch(UnconfinedTestDispatcher(testScheduler)) { s.events.toList(events) }
        val out = s.run("timeline.place", Origin.USER, "items" to listOf(mapOf("media_id" to "m1", "shot_id" to "shot_7"), mapOf("media_id" to "m3"), mapOf("media_id" to "m5", "duration_us" to 2_000_000))).done()
        job.cancel()
        assertTrue(out.committed); assertEquals(1, out.revision); assertEquals(1, s.state.value.revision)
        val v1 = s.clipsOn("V1"); val a1 = s.clipsOn("A1")
        assertEquals(listOf(0L, 10_000_000L, 14_000_000L), v1.map { it.timelineStartUs })
        assertEquals(listOf(ClipKind.VIDEO, ClipKind.VIDEO, ClipKind.IMAGE), v1.map { it.kind })
        assertEquals(1, a1.size); assertNotNull(v1[0].linkId); assertEquals(v1[0].linkId, a1[0].linkId)
        assertEquals("shot_7", v1[0].story?.shotId); assertEquals("shot_7", a1[0].story?.shotId)
        assertEquals(4, out.results.single()["clip_ids"]!!.jsonArray.size)
        // Guardado real y registro de la acción.
        val saved = store.load(s.projectId).project
        assertEquals(s.state.value.project, saved); assertEquals(1, saved.revision)
        assertEquals("timeline.place", ActionLog(File(dir, "log.jsonl")).recent().single()["ops"]!!.jsonArray.single().jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals(1, events.filterIsInstance<CutEvent.Committed>().size)
        dir.deleteRecursively()
    }

    @Test fun `una transaccion es atomica y dice que operacion fallo y por que`() = runTest {
        val s = session(); s.placeTwo()
        val before = s.state.value
        val first = s.clipsOn("V1").first().id
        val out = s.execute(Request(listOf(
            Op("clip.split", args("at_us" to 3_000_000)),
            Op("clip.trim", args("clip_id" to first, "edge" to "tail", "delta_us" to -1_000_000)),
            Op("clip.delete", args("clip_ids" to listOf("no_existe"))),
        ), Origin.AGENT))
        val e = out.error()
        assertEquals(ErrorCode.NOT_FOUND, e.code); assertEquals(2, e.opIndex); assertEquals("clip.delete", e.opId)
        assertEquals(before, s.state.value); assertEquals("Insertar", s.state.value.undoLabel)
    }

    @Test fun `el motor rechaza con motivo y el error es tipado`() = runTest {
        val s = session(); s.placeTwo()
        s.run("timeline.update_track", Origin.USER, "track_id" to "V1", "locked" to true).done()
        val e = s.run("clip.delete", Origin.AGENT, "clip_ids" to listOf(s.clipsOn("V1").first().id)).error()
        assertEquals(ErrorCode.REJECTED_BY_ENGINE, e.code); assertTrue(e.message, "bloqueada" in e.message)
        assertEquals(ErrorCode.UNKNOWN_CAPABILITY, s.run("clip.explotar", Origin.AGENT).error().code)
        assertEquals(ErrorCode.INVALID_ARGUMENT, s.run("clip.trim", Origin.AGENT, "clip_id" to s.clipsOn("V1").first().id).error().code)
        assertEquals(ErrorCode.NOT_FOUND, s.run("clip.trim", Origin.AGENT, "clip_id" to "x", "edge" to "tail", "delta_us" to 1).error().code)
    }

    @Test fun `un ensayo devuelve lo que cambiaria sin tocar nada`() = runTest {
        val s = session(); s.placeTwo()
        val before = s.state.value
        val out = s.execute(Request(listOf(Op("timeline.delete_ranges", args("ranges" to listOf(listOf(2_000_000, 5_000_000))))), Origin.AGENT, dryRun = true)).done()
        assertTrue(out.dryRun); assertFalse(out.committed); assertTrue(out.changeCount > 0)
        assertEquals(11_000_000L, out.preview!!.timeline.durationUs)
        assertEquals(before, s.state.value)
        assertEquals(true, out.toJson()["dry_run"]!!.jsonPrimitive.boolean)
    }

    @Test fun `deshacer y rehacer son compartidos entre usuario y agente e incluyen los ajustes del proyecto`() = runTest {
        val s = session(); s.placeTwo()
        val placed = s.timeline
        s.execute(Request(listOf(Op("clip.split", args("at_us" to 4_000_000)), Op("project.rename", args("name" to "Corto v2"))), Origin.AGENT, label = "Corte y nombre")).done()
        assertEquals("Corte y nombre", s.state.value.undoLabel); assertEquals("Corto v2", s.state.value.project.name)
        assertEquals(3, s.clipsOn("V1").size)
        val undone = s.undo(Origin.USER).done()
        assertEquals(placed, s.timeline); assertEquals("Corto", s.state.value.project.name)
        assertEquals("agent", undone.results.single()["by"]!!.jsonPrimitive.content)
        assertEquals(3, s.state.value.revision)   // la revisión nunca retrocede
        s.run("project.redo", Origin.AGENT).done()
        assertEquals(3, s.clipsOn("V1").size); assertEquals("Corto v2", s.state.value.project.name)
        s.undo(Origin.USER).done(); s.undo(Origin.USER).done()
        assertEquals(0, s.timeline.allTracks.sumOf { it.clips.size })
        assertEquals(ErrorCode.PRECONDITION_FAILED, s.undo(Origin.USER).error().code)
    }

    @Test fun `una revision caducada se rechaza y una peticion repetida no se aplica dos veces`() = runTest {
        val dir = Files.createTempDirectory("cut").toFile(); val store = ProjectStore(dir); val logFile = File(dir, "log.jsonl")
        val s = session(store = store, log = ActionLog(logFile)); s.placeTwo()
        val stale = s.execute(Request(listOf(Op("clip.split", args("at_us" to 2_000_000))), Origin.AGENT, expectedRevision = 0)).error()
        assertEquals(ErrorCode.REVISION_CONFLICT, stale.code); assertEquals(1L, stale.currentRevision)
        val request = Request(listOf(Op("clip.split", args("at_us" to 2_000_000))), Origin.AGENT, expectedRevision = 1, idempotencyKey = "turno-7-op-1")
        assertFalse(s.execute(request).done().replayed)
        val again = s.execute(request).done()
        assertTrue(again.replayed); assertEquals(2, again.revision); assertEquals(3, s.clipsOn("V1").size)
        // Tras cerrar y reabrir, la clave sigue reconocida gracias al registro.
        val reopened = CutSession(store.load(s.projectId).project, registry(), store, ActionLog(logFile), io = Dispatchers.Unconfined)
        assertTrue(reopened.execute(request.copy(expectedRevision = null)).done().replayed)
        assertEquals(3, reopened.state.value.project.timeline.track("V1")!!.clips.size)
        dir.deleteRecursively()
    }

    @Test fun `cabezal y seleccion se ven al instante y no entran en el historial`() = runTest {
        val s = session(); s.placeTwo()
        val first = s.clipsOn("V1").first().id
        s.run("timeline.seek", Origin.AGENT, "at_us" to 1_000_000).done()
        val step = s.run("timeline.seek", Origin.PEN, "step_frames" to 1).done()
        assertEquals(1_040_000L, step.results.single()["playhead_us"]!!.jsonPrimitive.long)   // 25 fps
        s.run("timeline.select", Origin.USER, "clip_ids" to listOf(first)).done()
        assertEquals(setOf(first), s.state.value.view.selection)
        assertEquals(1, s.state.value.revision); assertEquals("Insertar", s.state.value.undoLabel)
        // Sin argumentos, las operaciones actúan sobre la selección y el cabezal, como en la interfaz.
        s.run("clip.split", Origin.USER).done()
        assertEquals(listOf(0L, 1_040_000L), s.clipsOn("V1").take(2).map { it.timelineStartUs })
        // Si el clip seleccionado desaparece, la selección no apunta a nada inexistente.
        s.run("clip.delete", Origin.USER, "clip_ids" to listOf(first)).done()
        assertTrue(s.state.value.view.selection.isEmpty())
        val ctx = s.run("project.context", Origin.AGENT).done().results.single()
        assertEquals(true, ctx["history"]!!.jsonObject["can_undo"]!!.jsonPrimitive.boolean)
    }

    @Test fun `reemplazar una toma conserva el montaje y rechaza una toma que no cubre el clip`() = runTest {
        val s = session(); s.placeTwo()
        val clip = s.clipsOn("V1").first()
        s.run("clip.replace_media", Origin.AGENT, "clip_id" to clip.id, "media_id" to "m2").done()
        val after = s.clipsOn("V1").first()
        assertEquals("m2", after.mediaId); assertEquals(clip.timelineStartUs, after.timelineStartUs); assertEquals(clip.durationUs, after.durationUs)
        assertEquals("m2", s.clipsOn("A1").first().mediaId)   // el audio vinculado cambia con la imagen
        val e = s.run("clip.replace_media", Origin.AGENT, "clip_id" to clip.id, "media_id" to "m3").error()
        assertEquals(ErrorCode.REJECTED_BY_ENGINE, e.code)
        assertEquals("m2", s.clipsOn("V1").first().mediaId)
    }

    @Test fun `un srt entra como subtitulos en un solo paso de deshacer`() = runTest {
        val s = session(); s.placeTwo()
        val srt = "1\n00:00:01,000 --> 00:00:02,500\nHola, <i>Mateo</i>.\n\n2\n00:00:03,000 --> 00:00:05,000\nEsta es una línea bastante larga que debe partirse en dos para leerse bien.\n\n3\n00:00:05,000 --> 00:00:04,000\nInválido\n"
        val out = s.run("subtitle.import_srt", Origin.AGENT, "srt" to srt, "max_chars_per_line" to 40).done()
        assertEquals(2, out.results.single()["clip_ids"]!!.jsonArray.size)
        val titles = s.timeline.videoTracks.single { it.role == TrackRole.TITLES }.clips
        assertEquals(listOf(1_000_000L, 3_000_000L), titles.map { it.timelineStartUs })
        assertEquals("Hola, Mateo.", titles[0].text!!.text); assertTrue('\n' in titles[1].text!!.text)
        assertEquals(0.38, titles[0].video.positionY, 1e-9)
        val listed = s.run("subtitle.list", Origin.AGENT).done().results.single()["subtitles"]!!.jsonArray
        assertEquals(2, listed.size)
        s.undo(Origin.USER).done()
        assertTrue(s.timeline.videoTracks.none { it.role == TrackRole.TITLES && it.clips.isNotEmpty() })
    }

    @Test fun `formato, busqueda por guion, color y keyframes pasan por el mismo camino`() = runTest {
        val s = session()
        val fmt = s.run("project.set_format", Origin.USER, "aspect" to "9:16", "fps" to "30").done().results.single()
        assertEquals(1080, fmt["width"]!!.jsonPrimitive.int); assertEquals(1920, fmt["height"]!!.jsonPrimitive.int)
        s.run("timeline.place", Origin.AGENT, "items" to listOf(mapOf("media_id" to "m1", "scene_id" to "sc_2", "shot_id" to "sh_1"), mapOf("media_id" to "m2", "scene_id" to "sc_3"))).done()
        val found = s.run("timeline.find_clips", Origin.AGENT, "scene_id" to "sc_2", "kind" to "video").done().results.single()
        assertEquals(1, found["total"]!!.jsonPrimitive.int)
        val clip = found["clips"]!!.jsonArray.single().jsonObject
        assertEquals("asset_1", clip["asset_id"]!!.jsonPrimitive.content); assertEquals("sh_1", clip["shot_id"]!!.jsonPrimitive.content)
        val id = clip["id"]!!.jsonPrimitive.content
        s.execute(Request(listOf(
            Op("effects.set_color", args("clip_ids" to listOf(id), "saturation" to -1, "exposure" to 9)),
            Op("keyframe.set", args("clip_id" to id, "param" to "scale", "at_us" to 0, "value" to 1.0)),
            Op("keyframe.set", args("clip_id" to id, "param" to "scale", "at_us" to 2_000_000, "value" to 1.2, "interpolation" to "ease_out")),
            Op("clip.set_transform", args("clip_ids" to listOf(id), "scale" to 3)),
        ), Origin.AGENT)).done().also { out ->
            assertEquals("Edición del agente", out.label); assertTrue(out.warnings.single(), "animado" in out.warnings.single())
        }
        val edited = s.timeline.clip(id)!!
        assertEquals(-1f, edited.video.color.saturation); assertEquals(2f, edited.video.color.exposure)   // fuera de rango se acota
        val keys = s.run("keyframe.list", Origin.AGENT, "clip_id" to id).done().results.single()["keyframes"]!!.jsonObject["scale"]!!.jsonArray
        assertEquals(2, keys.size); assertEquals("ease_out", keys[1].jsonObject["interpolation"]!!.jsonPrimitive.content)
        val full = s.run("clip.get", Origin.AGENT, "clip_ids" to listOf(id)).done().results.single()["clips"]!!.jsonArray.single().jsonObject
        assertEquals(-1.0, full["video"]!!.jsonObject["color"]!!.jsonObject["saturation"]!!.jsonPrimitive.content.toDouble(), 1e-6)
    }

    @Test fun `lo colocado queda seleccionado y va al final de su pista, no detras de la musica`() = runTest {
        val s = session()
        // Una cama de música larga ya puesta no debe empujar las tomas al final de la película…
        s.run("timeline.place", Origin.USER, "items" to listOf(mapOf("media_id" to "m4")), "audio_track_id" to "A2").done()
        s.run("timeline.place", Origin.USER, "items" to listOf(mapOf("media_id" to "m1"))).done()
        assertEquals(0L, s.clipsOn("V1").single().timelineStartUs)
        // …y el montaje sigue creciendo por su propia pista.
        s.run("timeline.place", Origin.USER, "items" to listOf(mapOf("media_id" to "m3"))).done()
        assertEquals(listOf(0L, 10_000_000L), s.clipsOn("V1").map { it.timelineStartUs })
        // «Música a −12 dB» en el mismo paso: audio.set usa lo recién colocado sin conocer su id.
        val out = s.execute(Request(listOf(
            Op("timeline.place", args("items" to listOf(mapOf("media_id" to "m4")), "audio_track_id" to "A2")),
            Op("audio.set", args("gain_db" to -12)),
        ), Origin.AGENT, label = "Añadir música")).done()
        assertTrue(out.committed)
        val music = s.timeline.track("A2")!!.clips
        assertEquals(2, music.size)
        assertEquals(-12.0, music.last().audio.gainDb, 1e-9)
        assertEquals(0.0, music.first().audio.gainDb, 1e-9)   // la cama anterior no se toca
    }

    @Test fun `con un video con sonido, la mezcla de lo seleccionado va a su pista de audio`() = runTest {
        val s = session()
        s.run("timeline.place", Origin.USER, "items" to listOf(mapOf("media_id" to "m1"))).done()
        s.run("audio.set", Origin.AGENT, "gain_db" to -6).done()
        assertEquals(-6.0, s.clipsOn("A1").single().audio.gainDb, 1e-9)
        assertEquals(0.0, s.clipsOn("V1").single().audio.gainDb, 1e-9)
        // Con un id explícito se respeta lo pedido, aunque sea el clip de imagen.
        s.run("audio.set", Origin.AGENT, "clip_ids" to listOf(s.clipsOn("V1").single().id), "gain_db" to -3).done()
        assertEquals(-3.0, s.clipsOn("V1").single().audio.gainDb, 1e-9)
    }

    @Test fun `una capacidad que el dispositivo no ofrece se anuncia y no se ejecuta, y un trabajo largo va solo`() = runTest {
        var started = 0
        val export = Capability("render.export", CapabilityKind.JOB, "Exporta el montaje a un archivo de vídeo en segundo plano.", requires = setOf("engine.export"),
            permission = Permission.FREE, handler = Handler.Job { _, _, _ -> started++; JsonObject(mapOf("job_id" to JsonPrimitive("job_1"))) })
        val without = session(registry = registry().register(export))
        assertEquals(ErrorCode.UNAVAILABLE, without.run("render.export", Origin.AGENT).error().code)
        assertEquals(false, without.registry.catalog(domain = "render")["capabilities"]!!.jsonArray.single().jsonObject["available"]!!.jsonPrimitive.boolean)
        val with = session(registry = registry(setOf("engine.export")).register(export))
        assertEquals("job_1", with.run("render.export", Origin.USER).done().results.single()["job_id"]!!.jsonPrimitive.content)
        assertEquals(ErrorCode.INVALID_ARGUMENT, with.execute(Request(listOf(Op("render.export"), Op("project.get")), Origin.AGENT)).error().code)
        assertEquals(1, started)
    }

    @Test fun `un proyecto de Corte con esquema 1 se abre y se guarda ya en el esquema actual`() = runTest {
        val v1 = """{"schemaVersion":1,"id":"p_abc","name":"Viejo","media":[{"id":"m1","uri":"content://a","displayName":"a.mp4","kind":"VIDEO","durationUs":5000000,"hasVideo":true}],
            "timeline":{"videoTracks":[{"id":"V1","kind":"VIDEO","name":"V1","role":"PICTURE","clips":[{"id":"c1","kind":"VIDEO","mediaId":"m1","timelineStartUs":0,"durationUs":5000000}]}],"audioTracks":[]}}"""
        val project = ProjectCodec.decode(v1.toByteArray())
        assertEquals(Project.CURRENT_SCHEMA, project.schemaVersion); assertNull(project.nexusProjectId); assertEquals(0, project.revision)
        val dir = Files.createTempDirectory("cut").toFile(); val store = ProjectStore(dir)
        val s = session(project, store = store)
        s.execute(Request(listOf(Op("clip.split", args("at_us" to 2_000_000)), Op("project.link_story", args("nexus_project_id" to "proj_9"))), Origin.USER)).done()
        val saved = store.load("p_abc").project
        assertEquals(2, saved.timeline.track("V1")!!.clips.size); assertEquals("proj_9", saved.nexusProjectId); assertEquals(Project.CURRENT_SCHEMA, saved.schemaVersion)
        dir.deleteRecursively()
    }

    @Test fun `migrar el vinculo del montaje conserva medios clips y escena y se deshace por el bus`() = runTest {
        val dir = Files.createTempDirectory("cut-transfer").toFile()
        try {
            val store = ProjectStore(dir)
            val s = session(project().copy(sceneId = "scene-source"), store = store)
            s.placeTwo()
            val before = s.state.value.project
            val request = Request(listOf(Op("project.link_story", args("nexus_project_id" to "project-target"))),
                Origin.AGENT, label = "Migrar proyecto", expectedRevision = before.revision, idempotencyKey = "transfer-one")
            val result = s.execute(request).done()
            val moved = store.load(before.id).project
            assertTrue(result.committed)
            assertEquals("project-target", moved.nexusProjectId)
            assertEquals(before.sceneId, moved.sceneId)
            assertEquals(before.media, moved.media)
            assertEquals(before.timeline, moved.timeline)
            assertEquals(before.id, moved.id)
            assertTrue(s.execute(request).done().replayed)
            assertEquals(moved.revision, s.state.value.revision)
            s.undo(Origin.USER).done()
            assertEquals(before.nexusProjectId, store.load(before.id).project.nexusProjectId)
            assertEquals(before.timeline, s.timeline)
            s.redo(Origin.USER).done()
            assertEquals("project-target", store.load(before.id).project.nexusProjectId)
        } finally { dir.deleteRecursively() }
    }

    @Test fun `una edicion posterior al inventario impide migrar el montaje con revision antigua`() = runTest {
        val s = session(); s.placeTwo()
        val previewRevision = s.state.value.revision
        s.run("project.rename", Origin.USER, "name" to "Edición posterior").done()
        val before = s.state.value.project
        val result = s.execute(Request(listOf(Op("project.link_story", args("nexus_project_id" to "project-target"))),
            Origin.AGENT, expectedRevision = previewRevision, idempotencyKey = "transfer-stale"))
        assertEquals(ErrorCode.REVISION_CONFLICT, result.error().code)
        assertEquals(before, s.state.value.project)
    }

    @Test fun `si no se puede guardar no cambia nada`() = runTest {
        val blocked = Files.createTempFile("cut", "file").toFile()   // un archivo donde debería ir una carpeta
        val s = session(store = ProjectStore(blocked))
        val e = s.run("timeline.place", Origin.USER, "items" to listOf(mapOf("media_id" to "m1"))).error()
        assertEquals(ErrorCode.INTERNAL, e.code)
        assertEquals(0, s.state.value.revision); assertEquals(0, s.timeline.allTracks.sumOf { it.clips.size }); assertFalse(s.state.value.canUndo)
        blocked.delete()
    }
}
