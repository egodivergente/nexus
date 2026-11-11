package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.agent.core.tools.*
import com.vidadomu.nexus.cut.command.CoreCapabilities
import com.vidadomu.nexus.cut.command.CutRegistry
import com.vidadomu.nexus.cut.command.CutWorkspace
import com.vidadomu.nexus.cut.command.Origin
import com.vidadomu.nexus.cut.command.Outcome
import com.vidadomu.nexus.cut.model.IdGenerator
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.storage.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

/** El agente monta por el mismo bus que la interfaz: descubre, lee, edita en una transacción y el usuario lo deshace. */
class CutToolsTest {
    private val dir = Files.createTempDirectory("cut-agent").toFile()
    private val workspace = CutWorkspace(ProjectStore(dir), CutRegistry().register(CoreCapabilities.all()), IdGenerator.sequential(), clock = { 1_000 }, io = Dispatchers.Unconfined)
    private fun call(body: String) = Json.parseToJsonElement(body).jsonObject
    private val agent = AgentSession("montaje", AgentMode.AGENT, activeProjectId = "proj_1")

    /** El montaje que «el usuario tiene abierto»: la interfaz y el agente comparten esta misma sesión. */
    private suspend fun open() = workspace.create("Mateo", nexusProjectId = "proj_1").also { s ->
        workspace.setActive(s.projectId)
        // La app registra los medios al importarlos de la Biblioteca; aquí se dejan puestos igual que quedarían.
        val media = listOf(
            MediaRef("m1", "content://1", "Mateo PP 1", MediaKind.VIDEO, 8_000_000, 1920, 1080, hasVideo = true, hasAudio = true, assetId = "asset_1"),
            MediaRef("m2", "content://2", "Mateo PP 2", MediaKind.VIDEO, 6_000_000, 1920, 1080, hasVideo = true, hasAudio = true, assetId = "asset_2"),
        )
        workspace.store.save(s.state.value.project.copy(media = media)); workspace.close(s.projectId); workspace.setActive(s.projectId)
    }.let { workspace.session(it.projectId) }

    @AfterTest fun clean() { dir.deleteRecursively() }

    @Test fun `el agente descubre, lee y edita en una transaccion que el usuario ve y deshace`() = runTest {
        val ui = open()
        val tools = CutTools.all(workspace).associateBy { it.name }
        // 1. Descubrir sin cargar contratos.
        val catalog = tools.getValue("cut_catalog").run(call("""{"query":"quitar tramos silencios"}"""), agent).result.jsonObject
        assertEquals(ui.projectId, catalog["active_project_id"]!!.jsonPrimitive.content)
        assertEquals("timeline.delete_ranges", catalog["capabilities"]!!.jsonArray.first().jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("proj_1", catalog["montages"]!!.jsonArray.single().jsonObject["nexus_project_id"]!!.jsonPrimitive.content)
        // 2. Contrato solo de lo que va a usar.
        val spec = tools.getValue("cut_describe").run(call("""{"ids":["timeline.place","timeline.delete_ranges"]}"""), agent).result.jsonObject
        assertEquals(2, spec["capabilities"]!!.jsonArray.size)
        // 3. Leer: una edición por la vía de lectura se rechaza sin tocar nada.
        val refused = tools.getValue("cut_read").run(call("""{"ops":[{"id":"clip.delete","args":{"clip_ids":["x"]}}]}"""), agent)
        assertTrue(refused.isError)
        val media = tools.getValue("cut_read").run(call("""{"ops":[{"id":"media.list"},{"id":"project.context"}]}"""), agent).result.jsonObject
        assertEquals(2, media["results"]!!.jsonArray[0].jsonObject["media"]!!.jsonArray.size)
        // 4. Editar: colocar las dos tomas y quitar una pausa, como un solo paso.
        val edit = tools.getValue("cut_edit").run(call("""{"label":"Montaje de primeros planos","idempotency_key":"t1","ops":[
            {"id":"timeline.place","args":{"items":[{"media_id":"m1"},{"media_id":"m2"}]}},
            {"id":"timeline.delete_ranges","args":{"ranges":[[3000000,5000000]]}}]}"""), agent)
        assertFalse(edit.isError, edit.result.toString())
        assertEquals(12_000_000L, ui.state.value.project.timeline.durationUs)   // la sesión de la interfaz ya lo tiene
        assertEquals("Montaje de primeros planos", ui.state.value.undoLabel)
        assertEquals(1, edit.result.jsonObject["revision"]!!.jsonPrimitive.long)
        // Repetir la misma llamada no duplica el montaje.
        assertTrue(tools.getValue("cut_edit").run(call("""{"idempotency_key":"t1","ops":[{"id":"timeline.place","args":{"items":[{"media_id":"m1"}]}}]}"""), agent).result.jsonObject["replayed"]!!.jsonPrimitive.boolean)
        assertEquals(12_000_000L, ui.state.value.project.timeline.durationUs)
        // 5. Un fallo dice cuál y por qué, y no cambia nada.
        val bad = tools.getValue("cut_edit").run(call("""{"ops":[{"id":"clip.split","args":{"at_us":1000000}},{"id":"clip.set_speed","args":{"clip_id":"nada","speed":"2"}}]}"""), agent)
        assertTrue(bad.isError); assertEquals("NOT_FOUND", bad.result.jsonObject["code"]!!.jsonPrimitive.content); assertEquals(1, bad.result.jsonObject["op_index"]!!.jsonPrimitive.int)
        assertEquals(1, ui.state.value.revision)
        // 6. El usuario deshace con su botón lo que hizo el agente.
        assertTrue(ui.undo(Origin.USER) is Outcome.Done)
        assertEquals(0L, ui.state.value.project.timeline.durationUs)
        // Y queda guardado de verdad.
        assertEquals(0, ProjectStore(dir).load(ui.projectId).project.timeline.allTracks.sumOf { it.clips.size })
    }

    @Test fun `dentro de un turno real el agente monta con dos llamadas y sin montaje abierto se le dice que hacer`() = runTest {
        val h = Harness()
        val deps = ToolDeps(h.registry, h.skills, h.cost, h.router, h.guard, h.executor, h.assets, clock = { h.now }, cut = workspace)
        val orphan = CutTools.all(workspace).first { it.name == "cut_read" }.run(call("""{"ops":[{"id":"timeline.get"}]}"""), agent)
        assertTrue(orphan.isError); assertTrue("cut_create" in orphan.result.toString())
        val ui = open()
        val llm = ScriptedLlmProvider(listOf(
            { _ -> ScriptedLlmProvider.tools(use("c1", "cut_read", """{"ops":[{"id":"media.list"}]}""")) },
            { r ->
                // El resultado de la lectura llega al modelo con los ids reales.
                assertTrue("\"m2\"" in r.messages.last().content.toString())
                ScriptedLlmProvider.tools(use("c2", "cut_edit", """{"label":"Primeros planos","ops":[{"id":"timeline.place","args":{"items":[{"media_id":"m2"},{"media_id":"m1"}]}},
                    {"id":"subtitle.add","args":{"text":"Mateo","at_us":0,"duration_us":2000000}}]}"""))
            },
            { _ -> ScriptedLlmProvider.text("Montaje listo: las dos tomas en orden y el rótulo «Mateo» al principio.") },
        ))
        val o = AgentOrchestrator(llm, AgentConfig(provider = "scripted", modelId = "test-model", region = "eu-west-1", endpoint = "http://proxy", maxToolRounds = 6),
            NexusToolkit.all(deps), h.skills, h.assets)
        val reply = o.send(o.newConversation(mode = AgentMode.AGENT), "Monta los primeros planos de Mateo, primero la toma 2")
        assertTrue("Montaje listo" in reply.text)
        val timeline = ui.state.value.project.timeline
        assertEquals(listOf("m2", "m1"), timeline.track("V1")!!.clips.map { it.mediaId })
        assertEquals("Mateo", timeline.videoTracks.flatMap { it.clips }.single { it.text != null }.text!!.text)
        assertEquals("Primeros planos", ui.state.value.undoLabel)
        assertEquals(47, NexusToolkit.all(deps).size)   // las 42 de siempre más las cinco de Nexus Cut
    }
}
