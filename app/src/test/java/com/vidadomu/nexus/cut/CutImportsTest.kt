package com.vidadomu.nexus.cut

import com.vidadomu.nexus.cut.command.CoreCapabilities
import com.vidadomu.nexus.cut.command.CutFailure
import com.vidadomu.nexus.cut.command.CutLibrary
import com.vidadomu.nexus.cut.command.CutRegistry
import com.vidadomu.nexus.cut.command.CutRenderer
import com.vidadomu.nexus.cut.command.CutSession
import com.vidadomu.nexus.cut.command.CutWorkspace
import com.vidadomu.nexus.cut.command.DeviceCapabilities
import com.vidadomu.nexus.cut.command.ErrorCode
import com.vidadomu.nexus.cut.command.ExportOverrides
import com.vidadomu.nexus.cut.command.LibraryMedia
import com.vidadomu.nexus.cut.command.Origin
import com.vidadomu.nexus.cut.command.Outcome
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.storage.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CutImportsTest {
    @get:Rule val temporary = TemporaryFolder()

    private class Fixture(val workspace: CutWorkspace, val imports: CutImports = CutImports()) {
        suspend fun open(vararg assets: String): CutSession = imports.openWith(workspace, assets.toList()) {
            val existing = workspace.list().firstOrNull { it.nexusProjectId == "nexus-project" && it.sceneId == "scene" }
            // Deja que una segunda apertura intente entrar mientras la primera aún no ha creado nada.
            yield()
            existing?.let { workspace.session(it.id) } ?: workspace.create("Escena", "nexus-project", "scene")
        }
    }

    private fun fixture(onProbe: suspend (String) -> Unit = {}): Fixture {
        val library = CutLibrary { id ->
            onProbe(id)
            LibraryMedia(MediaRef("", "file:///$id.mp4", id, MediaKind.VIDEO, 2_000_000, 1280, 720, hasVideo = true))
        }
        val renderer = object : CutRenderer {
            override suspend fun start(session: CutSession, overrides: ExportOverrides): JsonObject = error("No render in JVM test")
            override fun status(): JsonObject = error("No render in JVM test")
            override fun cancel(): Boolean = error("No render in JVM test")
        }
        val registry = CutRegistry { setOf(DeviceCapabilities.LIBRARY, DeviceCapabilities.EXPORT) }
            .register(CoreCapabilities.all() + DeviceCapabilities.all(library, renderer))
        return Fixture(CutWorkspace(ProjectStore(temporary.newFolder()), registry, io = Dispatchers.Unconfined))
    }

    private fun CutSession.assetOrder(): List<String?> = state.value.project.let { p ->
        p.timeline.track("V1")!!.clips.map { p.media(it.mediaId)?.assetId }
    }

    private fun Outcome.done(): Outcome.Done = this as? Outcome.Done
        ?: throw AssertionError((this as Outcome.Failed).error.toString())

    @Test fun reopeningReusesTheMontageAndKeepsTheFirstRequestedOrder() = runTest {
        val f = fixture()
        val session = f.open("second", "first", "second")
        val revision = session.state.value.revision
        assertSame(session, f.open("first", "second"))
        assertEquals(listOf("second", "first"), session.assetOrder())
        assertEquals(revision, session.state.value.revision)
        assertEquals(1, f.workspace.list().size)
        assertEquals(session.projectId, f.workspace.active.value)
        // El orden y la ausencia de duplicados también quedaron guardados en disco.
        val saved = f.workspace.store.load(session.projectId).project
        assertEquals(2, saved.media.size)
        assertEquals(2, saved.timeline.track("V1")!!.clips.size)
    }

    @Test fun reopeningDoesNotRestoreDeletedClipsButAnExplicitImportCanPlaceThemAgain() = runTest {
        val f = fixture()
        val session = f.open("first", "second")
        val deleted = session.state.value.project.timeline.track("V1")!!.clips.first().id
        session.run("clip.delete", Origin.USER, "clip_ids" to listOf(deleted)).done()
        f.open("first", "second")
        assertEquals(listOf("second"), session.assetOrder())
        assertEquals(2, session.state.value.project.media.size)
        session.run("media.import", Origin.USER, "asset_ids" to listOf("first")).done()
        assertEquals(listOf("second", "first"), session.assetOrder())
        session.undo(Origin.USER).done()
        assertEquals(listOf("second"), session.assetOrder())
    }

    @Test fun simultaneousOpensCreateOneMontageAndImportOnce() = runTest {
        var probes = 0
        val f = fixture { probes++; yield() }
        val first = async { f.open("first", "second") }
        val second = async { f.open("first", "second") }
        val session = first.await()
        assertSame(session, second.await())
        assertEquals(1, f.workspace.list().size)
        assertEquals(listOf("first", "second"), session.assetOrder())
        assertEquals(2, probes)
    }

    @Test fun anAgentEditDuringTheProbeRejectsTheStaleImportWithoutPartialChanges() = runTest {
        lateinit var session: CutSession
        val f = fixture { session.run("project.rename", Origin.AGENT, "name" to "Nombre del agente").done() }
        session = f.open()
        val failure = try {
            f.open("first")
            throw AssertionError("Expected a revision conflict")
        } catch (e: CutFailure) { e }
        assertEquals(ErrorCode.REVISION_CONFLICT, failure.code)
        assertEquals("Nombre del agente", session.state.value.project.name)
        assertTrue(session.state.value.project.media.isEmpty())
        assertTrue(session.assetOrder().isEmpty())
    }
}
