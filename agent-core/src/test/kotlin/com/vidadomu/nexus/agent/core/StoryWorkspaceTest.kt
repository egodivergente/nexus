package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.*
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*

class StoryWorkspaceTest {
    private val json = Json { ignoreUnknownKeys = true }
    private var sequence = 0
    private suspend fun AssetIndex.cmd(project: String, action: String, vararg fields: Pair<String, JsonElement>): JsonObject = storyCommand(project,
        buildJsonObject { put("action", action); put("operation_id", "test_${++sequence}"); fields.forEach { (key, value) -> put(key, value) } })
    private fun str(value: String) = JsonPrimitive(value)
    private fun num(value: Long) = JsonPrimitive(value)
    private fun JsonObject.id() = getValue("id").jsonPrimitive.content
    private fun JsonObject.ok() = getValue("ok").jsonPrimitive.boolean
    private suspend fun AssetIndex.sc(p: String, title: String = "Patio") = cmd(p, "create_scene", "title" to str(title)).id()
    private suspend fun AssetIndex.sh(p: String, scene: String, title: String = "Mano") = cmd(p, "create_shot", "scene_id" to str(scene), "title" to str(title), "description" to str("La mano tiembla")).id()
    private suspend fun AssetIndex.asset() = addAsset(Asset("", "file:///reference-${++sequence}.jpg", AssetType.IMAGE, displayName = "Referencia", createdAt = 1))

    @Test fun `legacy projects migrate lazily and edits survive reopening`() = runTest {
        val file = Files.createTempDirectory("story-persist").resolve("index.json").toFile()
        val index = AssetIndex(file); val p = index.createProject("Película").id
        val scene = index.sc(p); val shot = index.sh(p, scene)
        val block = index.cmd(p, "create_block", "scene_id" to str(scene), "kind" to str("dialogue"), "speaker" to str("Mateo"), "text" to str("No me mires.")).id()
        assertTrue(index.cmd(p, "update_block", "block_id" to str(block), "expected_revision" to num(0), "text" to str("No me mires todavía.")).ok())
        val reopened = AssetIndex(file)
        assertEquals(shot, reopened.snapshot().shots.single().id)
        assertEquals("No me mires todavía.", reopened.snapshot().story.blocks.first { it.id == block }.text)
        assertEquals(1, reopened.snapshot().projects.size)
        file.parentFile.deleteRecursively()
    }

    @Test fun `one material serves multiple shots and reorder or deletion never deletes it`() = runTest {
        val index = AssetIndex(null); val p = index.createProject("Film").id; val scene = index.sc(p)
        val a = index.sh(p, scene, "Uno"); val b = index.sh(p, scene, "Dos"); val asset = index.asset()
        for (s in listOf(a, b)) assertTrue(index.cmd(p, "link_material", "asset_id" to str(asset.id), "scope" to str("shot"), "target_id" to str(s), "role" to str("candidate")).ok())
        assertTrue(index.cmd(p, "move_shot", "shot_id" to str(b), "scene_id" to str(scene), "expected_revision" to num(0), "order" to num(0)).ok())
        assertEquals(b, index.snapshot().shots.sortedBy { it.order }.first().id)
        assertEquals(b, index.snapshot().story.blocks.filter { it.kind == "shot" }.sortedBy { it.order }.first().shotId)
        assertEquals(2, index.snapshot().story.links.size)
        assertTrue(index.cmd(p, "delete_shot", "shot_id" to str(a), "expected_revision" to num(0)).ok())
        assertNotNull(index.asset(asset.id)); assertEquals(1, index.snapshot().story.links.size)
    }

    @Test fun `proposal conflict is atomic and cannot overwrite a recent edit`() = runTest {
        val index = AssetIndex(null); val p = index.createProject("Film").id; val scene = index.sc(p); val shot = index.sh(p, scene)
        val changes = JsonArray(listOf(buildJsonObject { put("action", "update_shot"); put("shot_id", shot); put("expected_revision", 0); put("description", "El agente propone cerrar la mano") }))
        val proposal = index.cmd(p, "propose", "title" to str("Variante"), "changes" to changes).id()
        index.cmd(p, "update_shot", "shot_id" to str(shot), "expected_revision" to num(0), "description" to str("El usuario abre la mano"))
        val result = index.cmd(p, "apply_proposal", "proposal_id" to str(proposal), "expected_revision" to num(0))
        assertFalse(result.ok()); assertEquals("revision_conflict", result["code"]?.jsonPrimitive?.content)
        assertEquals("El usuario abre la mano", index.snapshot().shots.single().description)
        assertEquals("pending", index.snapshot().story.proposals.single().status)
    }

    @Test fun `retries with different JSON key order execute only once`() = runTest {
        val index = AssetIndex(null); val p = index.createProject("Film").id
        val one = json.parseToJsonElement("""{"action":"create_scene","operation_id":"same","title":"Interior"}""").jsonObject
        val two = json.parseToJsonElement("""{"title":"Interior","operation_id":"same","action":"create_scene"}""").jsonObject
        assertEquals(index.storyCommand(p, one), index.storyCommand(p, two))
        assertEquals(1, index.snapshot().scenes.size)
        assertFalse(index.storyCommand(p, JsonObject(one + ("title" to str("Different")))).ok())
    }

    @Test fun `reference resolution isolates characters and scene costume and replaces visibly`() = runTest {
        val index = AssetIndex(null); val p = index.createProject("Film").id; val scene = index.sc(p); val other = index.sc(p, "Otra")
        val mateo = index.cmd(p, "upsert_element", "kind" to str("character"), "name" to str("Mateo")).id()
        val julian = index.cmd(p, "upsert_element", "kind" to str("character"), "name" to str("Julián")).id()
        val shot = index.sh(p, scene); index.cmd(p, "update_shot", "shot_id" to str(shot), "expected_revision" to num(0), "character_ids" to JsonArray(listOf(str(mateo))))
        val m = index.asset(); val j = index.asset(); val jacket = index.asset(); val wrong = index.asset()
        for ((character, asset) in listOf(mateo to m, julian to j)) index.cmd(p, "link_material", "asset_id" to str(asset.id), "scope" to str("character"), "target_id" to str(character), "character_id" to str(character), "role" to str("identity"))
        index.cmd(p, "link_material", "asset_id" to str(jacket.id), "scope" to str("scene"), "target_id" to str(scene), "character_id" to str(mateo), "role" to str("costume"))
        index.cmd(p, "link_material", "asset_id" to str(wrong.id), "scope" to str("scene"), "target_id" to str(other), "character_id" to str(mateo), "role" to str("costume"))
        val ids = index.storyGenerationContext(p, shot).referenceAssetIds
        assertEquals(setOf(m.id, jacket.id), ids.toSet())
        assertFalse(j.id in ids); assertFalse(wrong.id in ids)
    }

    @Test fun `generation origin stays stable and local changes only invalidate its dependencies`() = runTest {
        val index = AssetIndex(null); val p = index.createProject("Film").id; val scene = index.sc(p); val shot = index.sh(p, scene); val other = index.sh(p, scene)
        val context = index.storyGenerationContext(p, shot)
        index.cmd(p, "update_shot", "shot_id" to str(other), "expected_revision" to num(0), "description" to str("Otro plano cambia"))
        assertFalse(index.isStoryContextStale(context))
        index.cmd(p, "update_shot", "shot_id" to str(shot), "expected_revision" to num(0), "title" to str("Título corregido"))
        assertFalse(index.isStoryContextStale(context))
        index.cmd(p, "update_shot", "shot_id" to str(shot), "expected_revision" to num(1), "framing" to str("Primerísimo plano"))
        assertTrue(index.isStoryContextStale(context))
        val asset = index.asset()
        assertTrue(index.cmd(p, "register_result", "asset_id" to str(asset.id), "context" to json.encodeToJsonElement(context)).ok())
        assertTrue(index.storyView(p)["links"]!!.jsonArray.single().jsonObject["stale"]!!.jsonPrimitive.boolean)
        index.cmd(p, "delete_shot", "shot_id" to str(shot), "expected_revision" to num(2))
        assertTrue(index.cmd(p, "register_result", "asset_id" to str(asset.id), "context" to json.encodeToJsonElement(context)).ok())
        assertEquals("project", index.snapshot().story.links.single().scope)
    }

    @Test fun `scoped agent cannot steal a shot from another scene or edit a preserved shot`() = runTest {
        val index = AssetIndex(null); val p = index.createProject("Film").id; val a = index.sc(p); val b = index.sc(p); val s = index.sh(p, a)
        val outside = index.cmd(p, "move_shot", "scope_scene_id" to str(b), "shot_id" to str(s), "scene_id" to str(b), "order" to num(0), "expected_revision" to num(0))
        assertFalse(outside.ok()); assertEquals(a, index.snapshot().shots.single().sceneId)
        val locked = index.cmd(p, "update_shot", "scope_scene_id" to str(a), "preserve_ids" to JsonArray(listOf(str(s))), "shot_id" to str(s), "expected_revision" to num(0), "description" to str("Overwrite"))
        assertFalse(locked.ok())
    }

    @Test fun `recover interrupted work without reenqueue and local preparation makes no submission`() = runTest {
        val index = AssetIndex(null); val p = index.createProject("Film").id; val scene = index.sc(p); val shot = index.sh(p, scene)
        val task = index.cmd(p, "enqueue_task", "title" to str("Preparar"), "shot_id" to str(shot)).id()
        index.cmd(p, "update_task", "task_id" to str(task), "expected_revision" to num(0), "status" to str("running"), "executor" to str("active_agent_turn"))
        val started = index.snapshot().story.tasks.single().startedAt
        index.cmd(p, "recover_tasks")
        assertEquals("pending_resume", index.snapshot().story.tasks.single().status)
        index.cmd(p, "run_preparation")
        val prepared = index.snapshot().story.tasks.single()
        assertEquals("prepared", prepared.status); assertEquals(started, prepared.startedAt); assertNotNull(prepared.context)
        index.cmd(p, "run_preparation"); assertEquals(1, index.snapshot().story.tasks.size)
        assertTrue(index.snapshot().generations.isEmpty())
    }

    @Test fun `undo preserves independent edits and stores only changed entities`() = runTest {
        val index = AssetIndex(null); val p = index.createProject("Film").id; val scene = index.sc(p); val a = index.sh(p, scene); val b = index.sh(p, scene)
        val edit = index.cmd(p, "update_shot", "shot_id" to str(a), "expected_revision" to num(0), "description" to str("Cambio A"))
        val history = index.snapshot().story.history.last()
        assertEquals(1, history.before["shots"]!!.jsonArray.size); assertTrue(history.before["scenes"]!!.jsonArray.isEmpty())
        index.cmd(p, "update_shot", "shot_id" to str(b), "expected_revision" to num(0), "description" to str("Cambio B"))
        assertTrue(index.cmd(p, "undo", "history_id" to edit.getValue("history_id")).ok())
        assertEquals("La mano tiembla", index.snapshot().shots.first { it.id == a }.description)
        assertEquals("Cambio B", index.snapshot().shots.first { it.id == b }.description)
    }

    @Test fun `legacy links adopt once can be removed and orphan material remains recoverable`() = runTest {
        val index = AssetIndex(null); val p = index.createProject("Film").id; val scene = index.ensureScene(p, "E1"); val shot = index.ensureShot(p, "P1", scene.id)
        val a = index.addAsset(Asset("", "file:///legacy.mp4", AssetType.VIDEO, role = AssetRole.FINAL, displayName = "Final previa", projectId = p, sceneId = scene.id, shotId = shot.id, createdAt = 1))
        index.cmd(p, "adopt_legacy_links")
        val link = index.snapshot().story.links.single()
        assertEquals(shot.id, link.targetId); assertTrue(link.selected)
        index.cmd(p, "adopt_legacy_links"); assertEquals(1, index.snapshot().story.links.size)
        val unlinked = index.cmd(p, "unlink_material", "link_id" to str(link.id), "expected_revision" to num(0))
        assertTrue(unlinked.ok()); assertNull(index.asset(a.id)?.shotId)
        index.cmd(p, "adopt_legacy_links"); assertTrue(index.snapshot().story.links.isEmpty())
        assertTrue(index.cmd(p, "undo", "history_id" to unlinked.getValue("history_id")).ok())
        assertEquals(shot.id, index.asset(a.id)?.shotId); assertEquals(1, index.snapshot().story.links.size)
        index.cmd(p, "delete_shot", "shot_id" to str(shot.id), "expected_revision" to num(0))
        assertNotNull(index.asset(a.id))
        assertTrue(str(a.id) in index.storyView(p).getValue("unplaced_asset_ids").jsonArray)
    }

    @Test fun `script changes invalidate local context and developing an idea keeps its original text`() = runTest {
        val index = AssetIndex(null); val p = index.createProject("Film").id; val scene = index.sc(p)
        val note = index.cmd(p, "create_block", "scene_id" to str(scene), "kind" to str("note"), "text" to str("Aquí quiero su mano temblando")).id()
        val shot = index.cmd(p, "create_shot", "origin_block_id" to str(note), "scope_block_id" to str(note), "scope_scene_id" to str(scene), "description" to str("Primerísimo plano de la mano")).id()
        val context = index.storyGenerationContext(p, shot)
        assertTrue("Aquí quiero su mano temblando" in context.context)
        assertEquals(shot, index.snapshot().story.blocks.first { it.id == note }.shotId)
        val dialogue = index.cmd(p, "create_block", "scene_id" to str(scene), "kind" to str("dialogue"), "text" to str("No te vayas")).id()
        assertTrue(index.isStoryContextStale(context))
        val next = index.storyGenerationContext(p, shot)
        index.cmd(p, "update_block", "block_id" to str(dialogue), "expected_revision" to num(0), "text" to str("No vuelvas"))
        assertTrue(index.isStoryContextStale(next))
        val corrected = index.storyGenerationContext(p, shot)
        index.cmd(p, "update_block", "block_id" to str(dialogue), "expected_revision" to num(1), "text" to str("No vuelvas."), "invalidate_material" to JsonPrimitive(false))
        assertFalse(index.isStoryContextStale(corrected))
    }

    @Test fun `undo cannot orphan later content and preserved links cannot be removed by indirect id`() = runTest {
        val index = AssetIndex(null); val p = index.createProject("Film").id
        val created = index.cmd(p, "create_scene", "title" to str("Nueva")); val scene = created.id(); val shot = index.sh(p, scene)
        assertFalse(index.cmd(p, "undo", "history_id" to created.getValue("history_id")).ok())
        assertNotNull(index.snapshot().scenes.firstOrNull { it.id == scene }); assertNotNull(index.snapshot().shots.firstOrNull { it.id == shot })
        val asset = index.asset(); val link = index.cmd(p, "link_material", "asset_id" to str(asset.id), "scope" to str("shot"), "target_id" to str(shot), "role" to str("reference")).id()
        val remove = index.cmd(p, "unlink_material", "link_id" to str(link), "expected_revision" to num(0), "preserve_ids" to JsonArray(listOf(str(shot))))
        assertFalse(remove.ok()); assertEquals(1, index.snapshot().story.links.size)
    }

    @Test fun `full editor payload with unchanged values or new title does not invalidate material`() = runTest {
        val index = AssetIndex(null); val p = index.createProject("Film").id; val scene = index.sc(p); val shot = index.sh(p, scene)
        val block = index.cmd(p, "create_block", "scene_id" to str(scene), "kind" to str("action"), "text" to str("Su mano tiembla"), "speaker" to str("")).id()
        val context = index.storyGenerationContext(p, shot)
        assertTrue(index.cmd(p, "update_scene", "scene_id" to str(scene), "expected_revision" to num(0), "title" to str("Patio renombrado"), "description" to str(""), "group" to str(""), "time_of_day" to str(""), "light" to str(""), "location_ids" to JsonArray(emptyList())).ok())
        assertFalse(index.isStoryContextStale(context))
        assertTrue(index.cmd(p, "update_shot", "shot_id" to str(shot), "expected_revision" to num(0), "scene_id" to str(scene), "title" to str("Nuevo título"), "description" to str("La mano tiembla"),
            "intent" to str(""), "framing" to str(""), "camera" to str(""), "performance" to str(""), "dialogue" to str(""), "sound" to str(""), "duration_sec" to JsonNull,
            "character_ids" to JsonArray(emptyList()), "narrative_state" to str("story"), "production_state" to str("pending")).ok())
        assertEquals(0L, index.snapshot().shots.single().contentRevision)
        assertFalse(index.isStoryContextStale(context))
        assertTrue(index.cmd(p, "update_block", "block_id" to str(block), "expected_revision" to num(0), "scene_id" to str(scene), "kind" to str("action"), "text" to str("Su mano tiembla"), "speaker" to str(""), "narrative_state" to str("story"), "shot_id" to JsonNull).ok())
        assertEquals(0L, index.snapshot().story.blocks.first { it.id == block }.contentRevision)
        assertFalse(index.isStoryContextStale(context))
        index.cmd(p, "update_shot", "shot_id" to str(shot), "expected_revision" to num(1), "framing" to str("Primerísimo plano"))
        assertTrue(index.isStoryContextStale(context))
    }
}
