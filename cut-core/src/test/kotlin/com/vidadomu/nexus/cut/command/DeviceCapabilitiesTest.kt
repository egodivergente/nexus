package com.vidadomu.nexus.cut.command

import com.vidadomu.nexus.cut.model.IdGenerator
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.model.MediaRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Biblioteca → montaje sin duplicados, y exportación pedida por el mismo bus. */
class DeviceCapabilitiesTest {
    private var probes = 0
    private val library = CutLibrary { id ->
        probes++
        when (id) {
            "asset_pp" -> LibraryMedia(MediaRef("", "content://pp", "Mateo PP", MediaKind.VIDEO, 6_000_000, 1920, 1080, hasVideo = true, hasAudio = true, generationId = "gen_9"), shotId = "shot_3")
            "asset_30" -> LibraryMedia(MediaRef("", "content://m30", "Móvil 30", MediaKind.VIDEO, 8_000_000, 1920, 1080, frameRate = 29.83, hasVideo = true, hasAudio = true))
            "asset_60" -> LibraryMedia(MediaRef("", "content://m60", "Móvil 60", MediaKind.VIDEO, 4_000_000, 1920, 1080, frameRate = 59.94, hasVideo = true))
            "asset_still" -> LibraryMedia(MediaRef("", "file:///still.png", "Cartel", MediaKind.IMAGE, null, 1024, 1024, hasVideo = true))
            "asset_song" -> LibraryMedia(MediaRef("", "content://song", "Canción", MediaKind.AUDIO, 12_000_000, hasAudio = true))
            "asset_doc" -> cutFail(ErrorCode.INVALID_ARGUMENT, "«guion.pdf» no es un vídeo, un audio ni una imagen.")
            else -> cutFail(ErrorCode.NOT_FOUND, "No existe el archivo $id en la Biblioteca.")
        }
    }
    private val renderer = object : CutRenderer {
        var started: ExportOverrides? = null
        override suspend fun start(session: CutSession, overrides: ExportOverrides): JsonObject {
            if (session.state.value.project.timeline.durationUs <= 0) cutFail(ErrorCode.PRECONDITION_FAILED, "La línea de tiempo está vacía.")
            started = overrides; return buildJsonObject { put("state", "running"); put("progress", 0) }
        }
        override fun status() = buildJsonObject { put("state", if (started == null) "idle" else "running") }
        override fun cancel() = started != null
    }
    private val registry = CutRegistry { setOf(DeviceCapabilities.LIBRARY, DeviceCapabilities.EXPORT) }
        .register(CoreCapabilities.all() + DeviceCapabilities.all(library, renderer))
    private fun session() = CutSession(CutProjects.create("Biblioteca", 1, IdGenerator.sequential()), registry, ids = IdGenerator.sequential(), io = Dispatchers.Unconfined)
    private fun Outcome.done() = this as? Outcome.Done ?: throw AssertionError((this as Outcome.Failed).error.toString())

    @Test fun `importar coloca en orden, conserva procedencia y plano, y no duplica al repetir`() = runTest {
        val s = session()
        val out = s.run("media.import", Origin.USER, "asset_ids" to listOf("asset_still", "asset_pp")).done().results.single()
        assertEquals(2, out["imported"]!!.jsonPrimitive.int)
        val project = s.state.value.project
        val pp = project.media.single { it.assetId == "asset_pp" }
        assertEquals("gen_9", pp.generationId)
        val v1 = project.timeline.track("V1")!!.clips
        assertEquals(listOf("asset_still", "asset_pp"), v1.map { c -> project.media(c.mediaId)!!.assetId })
        assertEquals(5_000_000L, v1[1].timelineStartUs)               // la imagen dura 5 s por defecto
        assertEquals("shot_3", v1[1].story?.shotId)
        assertEquals(1, project.timeline.track("A1")!!.clips.size)   // el sonido del vídeo, vinculado
        // Repetir: se reutiliza el medio (sin volver a analizarlo) y solo se coloca otra vez.
        val again = s.run("media.import", Origin.AGENT, "asset_ids" to listOf("asset_pp")).done().results.single()
        assertEquals(1, again["reused"]!!.jsonPrimitive.int); assertEquals(2, probes)
        assertEquals(2, s.state.value.project.media.size)
        assertEquals(3, s.state.value.project.timeline.track("V1")!!.clips.size)
        // Un solo paso de deshacer quita el medio importado y su colocación.
        s.undo(Origin.USER).done(); s.undo(Origin.USER).done()
        assertTrue(s.state.value.project.media.isEmpty())
    }

    @Test fun `un montaje vacio adopta los fps de su primer video y deshacer lo devuelve`() = runTest {
        val s = session()
        assertEquals(com.vidadomu.nexus.cut.model.FrameRate.FPS_25, s.state.value.project.settings.frameRate)
        // La imagen no decide; manda el primer vídeo del lote, aunque no vaya el primero.
        val out = s.run("media.import", Origin.USER, "asset_ids" to listOf("asset_still", "asset_30", "asset_60")).done().results.single()
        assertEquals("30", out["project_fps"]!!.jsonPrimitive.content)
        assertEquals(com.vidadomu.nexus.cut.model.FrameRate.FPS_30, s.state.value.project.settings.frameRate)
        // Con material ya dentro no vuelve a cambiar, venga lo que venga.
        s.run("clip.delete", Origin.USER, "clip_ids" to s.state.value.project.timeline.allTracks.flatMap { it.clips }.map { it.id }).done()
        val later = s.run("media.import", Origin.USER, "asset_ids" to listOf("asset_60")).done().results.single()
        assertTrue("project_fps" !in later)
        assertEquals(com.vidadomu.nexus.cut.model.FrameRate.FPS_30, s.state.value.project.settings.frameRate)
        // Es parte del mismo paso que la importación: al deshacerla vuelve la rejilla anterior.
        s.undo(Origin.USER).done(); s.undo(Origin.USER).done(); s.undo(Origin.USER).done()
        assertEquals(com.vidadomu.nexus.cut.model.FrameRate.FPS_25, s.state.value.project.settings.frameRate)
        assertTrue(s.state.value.project.media.isEmpty())
    }

    @Test fun `con la musica ya puesta el primer video adopta sus fps y añadir al final no la mueve`() = runTest {
        val s = session()
        s.run("media.import", Origin.USER, "asset_ids" to listOf("asset_song")).done()
        assertEquals(com.vidadomu.nexus.cut.model.FrameRate.FPS_25, s.state.value.project.settings.frameRate)
        // El primer vídeo manda aunque antes entrase la música (el audio no tiene rejilla de fotogramas).
        val out = s.run("media.import", Origin.USER, "asset_ids" to listOf("asset_30")).done().results.single()
        assertEquals("30", out["project_fps"]!!.jsonPrimitive.content)
        val t = s.state.value.project.timeline
        assertEquals(0L, t.track("V1")!!.clips.single().timelineStartUs)
        // La música sigue donde estaba: el vídeo entra a su lado, no la empuja detrás.
        val song = t.track("A2")!!.clips.single()
        assertEquals(0L to 12_000_000L, song.timelineStartUs to song.endUs)
        // Otro vídeo al final de V1 (en mitad de la música): ni la parte ni la desplaza.
        s.run("media.import", Origin.USER, "asset_ids" to listOf("asset_pp")).done()
        val after = s.state.value.project.timeline
        assertEquals(listOf(0L, 8_000_000L), after.track("V1")!!.clips.map { it.timelineStartUs })
        val bed = after.track("A2")!!.clips.single()
        assertEquals(0L to 12_000_000L, bed.timelineStartUs to bed.endUs)
        // Insertar en medio sí abre hueco en todo (sync lock), como antes.
        s.run("media.import", Origin.USER, "asset_ids" to listOf("asset_still"), "at_us" to 2_000_000L).done()
        val mid = s.state.value.project.timeline
        assertEquals(listOf(0L, 2_000_000L, 7_000_000L, 13_000_000L), mid.track("V1")!!.clips.map { it.timelineStartUs })
        assertTrue(mid.track("A2")!!.clips.size > 1 || mid.track("A2")!!.clips.single().durationUs > 12_000_000L)
    }

    @Test fun `sin colocar o sin fps conocidos el formato no se toca`() = runTest {
        val s = session()
        val out = s.run("media.import", Origin.AGENT, "asset_ids" to listOf("asset_30"), "place" to false).done().results.single()
        assertTrue("project_fps" !in out)
        assertEquals(com.vidadomu.nexus.cut.model.FrameRate.FPS_25, s.state.value.project.settings.frameRate)
        val t = session()
        t.run("media.import", Origin.USER, "asset_ids" to listOf("asset_pp")).done()
        assertEquals(com.vidadomu.nexus.cut.model.FrameRate.FPS_25, t.state.value.project.settings.frameRate)
    }

    @Test fun `un archivo que no sirve no deja nada a medias y dice cual`() = runTest {
        val s = session()
        val e = (s.run("media.import", Origin.AGENT, "asset_ids" to listOf("asset_pp", "asset_doc")) as Outcome.Failed).error
        assertEquals(ErrorCode.INVALID_ARGUMENT, e.code); assertTrue(e.message, "guion.pdf" in e.message)
        assertTrue(s.state.value.project.media.isEmpty()); assertEquals(0, s.state.value.revision)
        assertEquals(ErrorCode.NOT_FOUND, (s.run("media.import", Origin.AGENT, "asset_ids" to listOf("nada")) as Outcome.Failed).error.code)
    }

    @Test fun `exportar es un trabajo con sus condiciones y no toca el historial`() = runTest {
        val s = session()
        assertEquals(ErrorCode.PRECONDITION_FAILED, (s.run("render.export", Origin.AGENT) as Outcome.Failed).error.code)
        s.run("media.import", Origin.USER, "asset_ids" to listOf("asset_pp")).done()
        val rev = s.state.value.revision
        assertEquals("running", s.run("render.export", Origin.AGENT, "resolution" to "720p", "codec" to "h265").done().results.single()["state"]!!.jsonPrimitive.content)
        assertEquals(com.vidadomu.nexus.cut.model.ExportResolution.P720, renderer.started!!.resolution)
        assertEquals(rev, s.state.value.revision)
        s.run("render.export", Origin.AGENT, "each_clip" to true).done()
        assertTrue(renderer.started!!.eachClip)
        val videoId = s.state.value.project.timeline.track("V1")!!.clips.single().id
        s.run("render.export", Origin.AGENT, "clip_ids" to listOf(videoId)).done()
        assertEquals(setOf(videoId), renderer.started!!.clipIds)
        assertTrue(!renderer.started!!.eachClip)
        assertEquals(rev, s.state.value.revision)
        assertEquals("running", s.run("render.status", Origin.AGENT).done().results.single()["state"]!!.jsonPrimitive.content)
        assertEquals(ErrorCode.INVALID_ARGUMENT, (s.run("render.export", Origin.AGENT, "resolution" to "8K") as Outcome.Failed).error.code)
    }
}
