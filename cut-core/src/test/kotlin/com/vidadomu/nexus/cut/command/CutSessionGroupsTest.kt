package com.vidadomu.nexus.cut.command

import com.vidadomu.nexus.cut.model.IdGenerator
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.storage.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Lo que hace el agente en un turno se revisa y se revierte como un todo, sin arrastrar en silencio lo del usuario. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CutSessionGroupsTest {
    private val take = MediaRef("m1", "content://a", "Toma", MediaKind.VIDEO, 10_000_000, 1920, 1080, hasVideo = true, hasAudio = true)
    private val insert = MediaRef("m3", "content://c", "Inserto", MediaKind.VIDEO, 4_000_000, 1920, 1080, hasVideo = true)

    private fun session(store: ProjectStore? = null): CutSession {
        var now = 100L
        val project = CutProjects.create("Corto", nowMs = 1, ids = IdGenerator.sequential()).copy(media = listOf(take, insert))
        return CutSession(project, CutRegistry().register(CoreCapabilities.all()), store, null, IdGenerator.sequential(), clock = { ++now }, io = Dispatchers.Unconfined)
    }

    private fun Outcome.done() = this as? Outcome.Done ?: throw AssertionError((this as Outcome.Failed).error.toString())
    private fun Outcome.error() = (this as Outcome.Failed).error
    private fun agent(group: String, label: String, vararg ops: Op) = Request(ops.toList(), Origin.AGENT, label, group = group)
    private fun op(id: String, vararg a: Pair<String, Any?>) = Op(id, CutSession.argsOf(*a))
    private val CutSession.clips get() = state.value.project.timeline.allTracks.sumOf { it.clips.size }

    @Test fun `deshacer un turno del agente revierte todos sus pasos en una sola revision y se puede rehacer`() = runTest {
        val s = session()
        s.run("timeline.place", Origin.USER, "items" to listOf(mapOf("media_id" to "m1"), mapOf("media_id" to "m3"))).done()
        val start = s.state.value.project.timeline
        val first = s.state.value.project.timeline.track("V1")!!.clips.first().id
        s.execute(agent("turn_1", "Cortar en 2 s", op("clip.split", "clip_ids" to listOf(first), "at_us" to 2_000_000))).done()
        s.execute(agent("turn_1", "Renombrar", op("project.rename", "name" to "Corto final"))).done()
        val afterAgent = s.state.value.project.timeline
        assertEquals(Origin.AGENT, s.state.value.undoOrigin); assertEquals("turn_1", s.state.value.undoGroup)
        val status = s.groupStatus("turn_1")
        assertEquals(2, status.undoable); assertTrue(status.onTop); assertEquals(listOf("Cortar en 2 s", "Renombrar"), status.labels)

        val events = ArrayList<CutEvent>(); val job = launch(UnconfinedTestDispatcher(testScheduler)) { s.events.toList(events) }
        val rev = s.state.value.revision
        val out = s.undoGroup("turn_1", Origin.USER).done()
        job.cancel()
        assertEquals(rev + 1, out.revision)
        assertEquals(start, s.state.value.project.timeline)
        assertEquals("Corto", s.state.value.project.name)
        assertEquals(Origin.USER, s.state.value.undoOrigin)
        val undone = events.filterIsInstance<CutEvent.Undone>().single()
        assertEquals(2, undone.count); assertEquals("turn_1", undone.group)
        assertEquals(0, s.groupStatus("turn_1").undoable); assertEquals(2, s.groupStatus("turn_1").redoable)

        s.redoGroup("turn_1", Origin.USER).done()
        assertEquals("Corto final", s.state.value.project.name)
        assertEquals(afterAgent, s.state.value.project.timeline)
        // Tras rehacer el grupo, «Deshacer» normal sigue yendo paso a paso.
        assertEquals("Renombrar", s.state.value.undoLabel)
        s.undo(Origin.USER).done(); assertEquals("Corto", s.state.value.project.name); assertEquals("Cortar en 2 s", s.state.value.undoLabel)
    }

    @Test fun `si el usuario edito despues, revertir el turno se niega y solo con confirmacion arrastra lo posterior`() = runTest {
        val s = session()
        s.run("timeline.place", Origin.USER, "items" to listOf(mapOf("media_id" to "m1"))).done()
        s.execute(agent("turn_2", "Renombrar", op("project.rename", "name" to "Del agente"))).done()
        s.run("timeline.add_marker", Origin.USER, "at_us" to 1_000_000, "label" to "Mío").done()
        val status = s.groupStatus("turn_2")
        assertFalse(status.onTop); assertEquals(1, status.laterSteps)
        val refused = s.undoGroup("turn_2", Origin.USER).error()
        assertEquals(ErrorCode.PRECONDITION_FAILED, refused.code)
        assertTrue(refused.message, "otro cambio" in refused.message && "«Añadir marcador»" in refused.message || "otro cambio" in refused.message)
        assertEquals("Del agente", s.state.value.project.name)
        assertEquals(1, s.state.value.project.timeline.markers.size)

        s.undoGroup("turn_2", Origin.USER, includeLater = true).done()
        assertEquals("Corto", s.state.value.project.name)
        assertTrue(s.state.value.project.timeline.markers.isEmpty())
        assertTrue(s.clips > 0)
        assertEquals(ErrorCode.PRECONDITION_FAILED, s.undoGroup("turn_2", Origin.USER).error().code)
    }

    @Test fun `un ensayo del agente se publica como propuesta aplicable con su antes y despues`() = runTest {
        val s = session()
        s.run("timeline.place", Origin.USER, "items" to listOf(mapOf("media_id" to "m1"), mapOf("media_id" to "m3"))).done()
        val v1 = s.state.value.project.timeline.track("V1")!!.clips
        val events = ArrayList<CutEvent>(); val job = launch(UnconfinedTestDispatcher(testScheduler)) { s.events.toList(events) }
        val rev = s.state.value.revision
        val trial = s.execute(Request(listOf(op("clip.delete", "clip_ids" to listOf(v1[0].id))), Origin.AGENT, "Quitar la toma", dryRun = true, group = "turn_3")).done()
        job.cancel()
        assertEquals(rev, s.state.value.revision)
        val json = trial.toJson()
        val would = json["would_change"]!!.jsonObject
        assertEquals(14_000_000L, would["duration_us"]!!.jsonObject["before"]!!.jsonPrimitive.long)
        assertEquals(4_000_000L, would["duration_us"]!!.jsonObject["after"]!!.jsonPrimitive.long)
        assertTrue(would["clip_changes"]!!.jsonArray.any { it.jsonObject["what"]!!.jsonPrimitive.content == "removed" })
        val proposal = events.filterIsInstance<CutEvent.Proposed>().single()
        assertEquals(rev, proposal.request.expectedRevision); assertFalse(proposal.request.dryRun); assertEquals("turn_3", proposal.request.group)

        val applied = s.execute(proposal.request).done()
        assertTrue(applied.committed)
        assertNotNull(applied.toJson()["verified"])
        assertEquals(1, s.groupStatus("turn_3").undoable)
        // Una propuesta preparada sobre una revisión anterior ya no se aplica a ciegas.
        assertEquals(ErrorCode.REVISION_CONFLICT, s.execute(proposal.request).error().code)
    }

    @Test fun `al agente se le rechaza un argumento que el contrato no tiene y se le sugiere el bueno`() = runTest {
        val s = session()
        s.run("timeline.place", Origin.USER, "items" to listOf(mapOf("media_id" to "m1"))).done()
        val rev = s.state.value.revision
        val e = s.execute(Request(listOf(op("clip.split", "at" to 2_000_000)), Origin.AGENT)).error()
        assertEquals(ErrorCode.INVALID_ARGUMENT, e.code)
        assertTrue(e.message, "«at»" in e.message && "at_us" in e.message)
        assertEquals(rev, s.state.value.revision)
        val ok = s.execute(Request(listOf(op("clip.split", "at_us" to 2_000_000)), Origin.AGENT)).done()
        assertTrue(ok.committed)
        assertEquals(2, ok.toJson()["verified"]!!.jsonObject["clips"]!!.jsonObject["before"]!!.jsonPrimitive.int)
    }

    @Test fun `el registro de acciones se escribe en el contexto de E-S y no decide el resultado`() = runTest {
        val dir = Files.createTempDirectory("cutlog").toFile()
        val store = ProjectStore(dir)
        val project = CutProjects.create("Corto", nowMs = 1, ids = IdGenerator.sequential()).copy(media = listOf(take))
        store.save(project)
        val threads = java.util.Collections.synchronizedSet(HashSet<String>())
        val io = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "cut-io") }
        val log = object : ActionLog(File(dir, "log.jsonl")) {
            override fun committed(revision: Long, atMs: Long, origin: Origin, label: String, key: String?, ops: List<Op>, created: List<String>) {
                threads += Thread.currentThread().name; super.committed(revision, atMs, origin, label, key, ops, created)
            }
        }
        val dispatcher = io.asCoroutineDispatcher()
        val s = CutSession(project, CutRegistry().register(CoreCapabilities.all()), store, log, IdGenerator.sequential(), io = dispatcher)
        s.run("timeline.place", Origin.USER, "items" to listOf(mapOf("media_id" to "m1"))).done()
        assertEquals(setOf("cut-io"), threads)
        dispatcher.close(); dir.deleteRecursively()
    }
}
