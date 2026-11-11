package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.agent.core.tools.AgentSession
import com.vidadomu.nexus.agent.core.tools.CutTools
import com.vidadomu.nexus.cut.command.Capability
import com.vidadomu.nexus.cut.command.CapabilityKind
import com.vidadomu.nexus.cut.command.CoreCapabilities
import com.vidadomu.nexus.cut.command.CutLibrary
import com.vidadomu.nexus.cut.command.CutRegistry
import com.vidadomu.nexus.cut.command.CutRenderer
import com.vidadomu.nexus.cut.command.CutSession
import com.vidadomu.nexus.cut.command.CutWorkspace
import com.vidadomu.nexus.cut.command.DeviceCapabilities
import com.vidadomu.nexus.cut.command.ExportOverrides
import com.vidadomu.nexus.cut.command.Handler
import com.vidadomu.nexus.cut.command.LibraryMedia
import com.vidadomu.nexus.cut.command.Origin
import com.vidadomu.nexus.cut.command.Outcome
import com.vidadomu.nexus.cut.model.ExportResolution
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.storage.ProjectStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BoundCutToolsTest {
    private val dir = Files.createTempDirectory("bound-cut").toFile()
    private val agent = AgentSession("cut-chat", AgentMode.AGENT)
    private var rendered: CutSession? = null
    private var overrides: ExportOverrides? = null
    private var cancelled = false
    private var jobOrigin: Origin? = null
    private val renderer = object : CutRenderer {
        override suspend fun start(session: CutSession, overrides: ExportOverrides): JsonObject {
            rendered = session
            this@BoundCutToolsTest.overrides = overrides
            return buildJsonObject { put("state", "running") }
        }
        override fun status(): JsonObject = buildJsonObject { put("state", if (rendered == null) "idle" else "running") }
        override fun cancel(): Boolean { cancelled = true; return true }
    }
    private val registry = CutRegistry { setOf(DeviceCapabilities.LIBRARY, DeviceCapabilities.EXPORT) }.register(
        CoreCapabilities.all() + DeviceCapabilities.all(CutLibrary { id ->
            LibraryMedia(MediaRef("", "file:///$id.mp4", id, MediaKind.VIDEO, 4_000_000, 1280, 720, hasVideo = true))
        }, renderer) + Capability("probe.job", CapabilityKind.JOB, "Trabajo testigo local", handler = Handler.Job { _, _, origin ->
            jobOrigin = origin
            buildJsonObject { put("state", "done") }
        }),
    )
    private val workspace = CutWorkspace(ProjectStore(dir), registry, io = Dispatchers.Unconfined)

    @AfterTest fun clean() { dir.deleteRecursively() }

    private fun input(value: String) = Json.parseToJsonElement(value).jsonObject
    private fun Outcome.done() = this as? Outcome.Done ?: throw AssertionError((this as Outcome.Failed).error.toString())

    @Test fun `un turno scripted sigue editando su montaje aunque cambie el activo y el usuario lo deshace`() = runTest {
        val montage = workspace.create("Montaje del chat")
        montage.run("media.import", Origin.USER, "asset_ids" to listOf("take")).done()
        montage.setPlayhead(1_500_000)
        val clip = montage.state.value.project.timeline.track("V1")!!.clips.single().id
        montage.run("timeline.select", Origin.USER, "clip_ids" to listOf(clip)).done()
        val before = montage.state.value.project.timeline
        val other = workspace.create("Otro montaje")
        workspace.setActive(montage.projectId)
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf(
            { request ->
                assertTrue(request.messages.last().text().contains("playhead_us=1500000"))
                assertTrue(request.messages.last().text().contains(clip))
                workspace.setActive(other.projectId)
                ScriptedLlmProvider.tools(use("read", "cut_read", """{"ops":[{"id":"timeline.get"}]}"""))
            },
            { _ -> ScriptedLlmProvider.tools(use("edit", "cut_edit", """{"label":"Corte pedido en el chat","ops":[{"id":"clip.split","args":{"at_us":1500000}}]}""")) },
            { _ -> ScriptedLlmProvider.text("He dividido el clip en el punto indicado.") },
        ))
        val orchestrator = AgentOrchestrator(
            llm, AgentConfig(provider = "scripted", modelId = "test", region = "", endpoint = "", mode = AgentMode.AGENT),
            CutTools.forProject(workspace, montage.projectId), h.skills, h.assets,
            uiContext = { CutTools.context(workspace, montage.projectId) },
        )
        val result = orchestrator.send(orchestrator.newConversation(), "Divide aquí lo seleccionado")
        assertTrue(result.text.contains("dividido"))
        assertEquals(listOf("cut_read", "cut_edit"), result.toolCalls)
        assertEquals(2, montage.state.value.project.timeline.track("V1")!!.clips.size)
        assertEquals(0, other.state.value.project.timeline.allTracks.sumOf { it.clips.size })
        assertTrue(File(workspace.store.fileOf(montage.projectId).parentFile, "actions.jsonl").readText().contains("\"origin\":\"AGENT\""))
        montage.undo(Origin.USER).done()
        assertEquals(before, montage.state.value.project.timeline)
        assertEquals(before, workspace.store.load(montage.projectId).project.timeline)
    }

    @Test fun `el catalogo fija destino sin crear montajes y deja intacta la lista global`() = runTest {
        val montage = workspace.create("Uno")
        val other = workspace.create("Dos")
        workspace.setActive(other.projectId)
        val tools = CutTools.forProject(workspace, montage.projectId)
        val catalog = tools.single { it.name == "cut_catalog" }.run(input("{}"), agent).result.jsonObject
        assertEquals(montage.projectId, catalog["active_project_id"]!!.jsonPrimitive.content)
        assertFalse(tools.any { it.name == "cut_create" })
        assertTrue(tools.any { it.name == "cut_run" })
        assertEquals(5, CutTools.all(workspace).size)
        assertFalse(CutTools.all(workspace).any { it.name == "cut_run" })
        assertTrue(CutTools.context(workspace, montage.projectId)!!.contains(montage.projectId))
        assertTrue(CutTools.context(workspace)!!.contains(other.projectId))
    }

    @Test fun `lectura edicion y trabajos rechazan otro id de montaje`() = runTest {
        val montage = workspace.create("Uno")
        val other = workspace.create("Dos")
        val tools = CutTools.forProject(workspace, montage.projectId).associateBy { it.name }
        val cases = mapOf("cut_read" to "project.get", "cut_edit" to "project.rename", "cut_run" to "render.export")
        for ((tool, operation) in cases) {
            val out = tools.getValue(tool).run(input("""{"project_id":"${other.projectId}","ops":[{"id":"$operation","args":{"name":"No aplicar"}}]}"""), agent)
            assertTrue(out.isError, tool)
        }
        assertEquals("Uno", montage.state.value.project.name)
        assertEquals("Dos", other.state.value.project.name)
        assertEquals(null, rendered)
    }

    @Test fun `cut_run exporta y cancela por el bus de la sesion fija con origen agente`() = runTest {
        val montage = workspace.create("Uno")
        val other = workspace.create("Dos")
        workspace.setActive(other.projectId)
        val tools = CutTools.forProject(workspace, montage.projectId).associateBy { it.name }
        val run = tools.getValue("cut_run")
        val started = run.run(input("""{"ops":[{"id":"render.export","args":{"resolution":"720p"}}]}"""), agent)
        assertFalse(started.isError)
        assertSame(montage, rendered)
        assertEquals(ExportResolution.P720, overrides!!.resolution)
        assertFalse(montage.state.value.canUndo)
        assertEquals(0L, montage.state.value.revision)
        assertFalse(run.run(input("""{"ops":[{"id":"render.cancel"}]}"""), agent).isError)
        assertTrue(cancelled)
        assertFalse(run.run(input("""{"ops":[{"id":"probe.job"}]}"""), agent).isError)
        assertEquals(Origin.AGENT, jobOrigin)
        val status = tools.getValue("cut_read").run(input("""{"ops":[{"id":"render.status"}]}"""), agent)
        assertFalse(status.isError)
        assertTrue(status.result.toString().contains("running"))
    }

    @Test fun `cut_run rechaza operaciones de lectura edicion y lotes antes de ejecutarlas`() = runTest {
        val montage = workspace.create("Intacto")
        val run = CutTools.forProject(workspace, montage.projectId).single { it.name == "cut_run" }
        for (request in listOf(
            """{"ops":[{"id":"project.rename","args":{"name":"Cambio"}}]}""",
            """{"ops":[{"id":"project.get"}]}""",
            """{"ops":[{"id":"render.export"},{"id":"render.cancel"}]}""",
            """{"ops":[]}""",
        )) assertTrue(run.run(input(request), agent).isError)
        assertEquals("Intacto", montage.state.value.project.name)
        assertEquals(null, rendered)
        assertFalse(cancelled)
    }
}
