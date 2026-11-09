package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.storyView
import com.vidadomu.nexus.agent.core.assets.storyCommand
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.AntigravityProvider
import com.vidadomu.nexus.agent.core.llm.LlmMessage
import com.vidadomu.nexus.agent.core.llm.LlmRequest
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.llm.Role
import com.vidadomu.nexus.agent.core.tools.*
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/** Real toolkit/repository operations. No LLM calls or provider submissions. */
class StoryAgentToolsTest {
    private fun command(action: String, id: String, scene: String, revision: Long, title: String) = buildJsonObject {
        put("action", action); put("operation_id", id); put("scene_id", scene)
        put("expected_revision", revision); put("title", title)
    }

    @Test fun `direct tool edits shared project and stable operation id does not repeat`() = runTest {
        val h = Harness(); val p = h.assets.createProject("Corto")
        val scene = h.assets.ensureScene(p.id, "1", "Antes")
        val session = AgentSession("story-direct", AgentMode.AGENT, p.id).also {
            it.storySelection = StoryAgentSelection(p.id, scene.id, mode = "direct")
        }
        val tools = StoryTools.all(h.deps).associateBy { it.name }
        val input = buildJsonObject { put("change", command("update_scene", "edit-once", scene.id, scene.revision, "Después")) }
        assertFalse(tools.getValue("edit_story_project").run(input, session).isError)
        val once = h.assets.snapshot().scenes.single { it.id == scene.id }
        assertEquals("Después", once.title)
        assertFalse(tools.getValue("edit_story_project").run(input, session).isError)
        assertEquals(once, h.assets.snapshot().scenes.single { it.id == scene.id })
        val read = tools.getValue("read_story_fragment").run(buildJsonObject {}, session)
        assertFalse(read.isError); assertTrue("Después" in read.result.toString())
        assertTrue(h.backend.submitted.isEmpty())
    }

    @Test fun `copilot proposal survives reopening without replacing current scene`() = runTest {
        val path = Files.createTempDirectory("nexus-story-tools").resolve("index.json").toFile()
        val index = AssetIndex(path); val h = Harness(assets = index)
        val p = index.createProject("Proyecto"); val scene = index.ensureScene(p.id, "1", "Actual")
        val session = AgentSession("story-proposal", AgentMode.COPILOT, p.id)
        val tool = StoryTools.all(h.deps).single { it.name == "edit_story_project" }
        val input = buildJsonObject { put("change", command("update_scene", "proposal-once", scene.id, scene.revision, "Alternativa")) }
        val proposed = tool.run(input, session)
        assertFalse(proposed.isError, proposed.result.toString())
        assertEquals("Actual", index.snapshot().scenes.single().title)
        assertTrue(session.pendingActions.isEmpty(), "Proposals must not depend on a transient lambda")
        val reopened = AssetIndex(path)
        val view = reopened.storyView(p.id)
        assertTrue("Alternativa" in view["proposals"].toString(), view.toString())
        val count = (view["proposals"] as JsonArray).size
        assertFalse(tool.run(input, session).isError)
        assertEquals(count, (index.storyView(p.id)["proposals"] as JsonArray).size)
        assertTrue(h.backend.submitted.isEmpty())
    }

    @Test fun `tools cannot widen selected scope or alter preserved content`() = runTest {
        val h = Harness(); val p = h.assets.createProject("Proyecto")
        val first = h.assets.ensureScene(p.id, "1", "Conservar")
        val other = h.assets.ensureScene(p.id, "2", "Fuera")
        val session = AgentSession("story-scope", AgentMode.AGENT, p.id).also {
            it.storySelection = StoryAgentSelection(p.id, first.id, mode = "direct", preserveIds = setOf(first.id))
        }
        val tool = StoryTools.all(h.deps).single { it.name == "edit_story_project" }
        val outside = tool.run(buildJsonObject { put("change", buildJsonObject {
            command("update_scene", "outside", other.id, other.revision, "No permitido").forEach { (k, v) -> put(k, v) }
            put("scope_scene_id", other.id); put("preserve_ids", JsonArray(emptyList()))
        }) }, session)
        assertTrue(outside.isError, outside.result.toString())
        val preserved = tool.run(buildJsonObject { put("change", command("update_scene", "preserved", first.id, first.revision, "No permitido")) }, session)
        assertTrue(preserved.isError, preserved.result.toString())
        assertEquals(listOf("Conservar", "Fuera"), h.assets.snapshot().scenes.map { it.title })
        val outsideShot = h.assets.ensureShot(p.id, "2.1", other.id, "Plano externo")
        session.storySelection = StoryAgentSelection(p.id, first.id, mode = "direct")
        val move = tool.run(buildJsonObject { put("change", buildJsonObject {
            put("action", "move_shot"); put("operation_id", "cannot-pull-external-content")
            put("shot_id", outsideShot.id); put("scene_id", first.id); put("expected_revision", outsideShot.revision); put("order", 0)
        }) }, session)
        assertTrue(move.isError, "An allowed destination must not authorize editing an external source: ${move.result}")
        assertEquals(other.id, h.assets.snapshot().shots.single().sceneId)
        val different = h.assets.createProject("Otro")
        val read = StoryTools.all(h.deps).single { it.name == "read_story_project" }
            .run(buildJsonObject { put("project_id", different.id) }, session)
        assertTrue(read.isError)
    }

    @Test fun `generation preparation captures selected stable shot without spending`() = runTest {
        val h = Harness(); val p = h.assets.createProject("Proyecto")
        val scene = h.assets.ensureScene(p.id, "1", "Escena")
        val shot = h.assets.ensureShot(p.id, "1", scene.id, "La mano tiembla")
        val session = AgentSession("story-generation", AgentMode.AGENT, p.id).also {
            it.storySelection = StoryAgentSelection(p.id, scene.id, shot.id, mode = "direct")
        }
        val result = PrepareGenerationTool(h.deps).run(buildJsonObject {
            put("model_id", SEEDREAM.modelId); put("provider_id", SEEDREAM.providerId)
            put("prompt", "Primerísimo plano de una mano temblando")
        }, session)
        assertFalse(result.isError, result.result.toString())
        val plan = h.guard.plans().single()
        assertEquals(shot.id, plan.storyContext?.shotId)
        assertEquals(p.id, plan.storyContext?.projectId)
        assertTrue(plan.storyContext?.context?.contains("La mano tiembla") == true)
        val originalContext = plan.storyContext
        session.storySelection = StoryAgentSelection(p.id, scene.id, mode = "direct")
        assertEquals(originalContext, h.guard.plan(plan.id)?.storyContext)
        assertTrue(h.backend.submitted.isEmpty())
    }

    @Test fun `scoped bridge instructions disable native generation for codex and agy`() {
        val request = LlmRequest("fixture", listOf("Preparar una propuesta audiovisual"),
            listOf(LlmMessage(Role.USER, listOf(ContentBlock.Text("Prepara el storyboard")))), emptyList(), 1024)
        for (engine in AntigravityProvider.BridgeEngine.entries) {
            val payload = AntigravityProvider(engine = engine, nativeImages = false).encodeStreamRequest(request, "story-no-spend")
            val sent = payload.toString()
            assertTrue("No uses tu motor nativo" in sent)
            assertFalse("EXCEPCIÓN — imágenes con tu propio motor" in sent)
        }
    }

    @Test fun `selected note can become a proposed shot while its original text remains`() = runTest {
        val h = Harness(); val p = h.assets.createProject("Proyecto")
        val scene = h.assets.ensureScene(p.id, "1", "La mano")
        val created = h.assets.storyCommand(p.id, buildJsonObject {
            put("action", "create_block"); put("operation_id", "note-original"); put("scene_id", scene.id)
            put("kind", "note"); put("text", "Aquí quiero un primerísimo plano de su mano temblando")
        })
        val block = created.getValue("id").jsonPrimitive.content
        val session = AgentSession("develop-note", AgentMode.AGENT, p.id).also {
            it.storySelection = StoryAgentSelection(p.id, scene.id, blockId = block, mode = "proposal")
        }
        val tools = StoryTools.all(h.deps).associateBy { it.name }
        val proposed = tools.getValue("edit_story_project").run(buildJsonObject { put("change", buildJsonObject {
            put("action", "create_shot"); put("operation_id", "develop-note-proposal"); put("title", "La mano tiembla")
            put("action_text", "Los dedos tiemblan sobre la mesa"); put("framing", "Primerísimo plano")
        }) }, session)
        assertFalse(proposed.isError, proposed.result.toString())
        assertTrue(h.assets.snapshot().shots.isEmpty())
        session.storySelection = session.storySelection!!.copy(mode = "direct")
        val accepted = tools.getValue("apply_story_proposal").run(buildJsonObject {
            put("proposal_id", proposed.result.jsonObject.getValue("id")); put("operation_id", "accept-note-proposal"); put("expected_revision", 0)
        }, session)
        assertFalse(accepted.isError, accepted.result.toString())
        assertEquals("La mano tiembla", h.assets.snapshot().shots.single().title)
        assertEquals("Aquí quiero un primerísimo plano de su mano temblando", h.assets.snapshot().story.blocks.single { it.id == block }.text)
        assertTrue(h.backend.submitted.isEmpty())
    }
}
