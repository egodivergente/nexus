package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.agent.core.sync.*
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

/** Two real indexes and journals; only the remote object store is in memory. No model or account is called. */
class StoryDriveSyncWorkflowTest {
    private class Cloud : SyncRemote {
        val documents = linkedMapOf<String, ByteArray>()
        val blobs = linkedMapOf<String, ByteArray>()
        override fun commits() = documents.map { (name, data) -> SyncObject(name, name, data.size.toLong()) }
        override fun readCommit(item: SyncObject) = documents.getValue(item.name)
        override fun putCommit(commit: SyncCommit, bytes: ByteArray) { documents["commit-${commit.id}.json"] = bytes }
        override fun putBlob(name: String, file: File) { blobs.putIfAbsent(name, file.readBytes()) }
        override fun getBlob(name: String, destination: File) { destination.writeBytes(blobs.getValue(name)) }
    }
    private class Peer(val root: File, val legacy: Cloud, val story: Cloud, val label: String) {
        var index = AssetIndex(File(root, "index.json"))
        var workspace = workspace()
        private fun workspace() = SyncWorkspace(index, File(root, "sync"), legacy,
            open = { uri -> runCatching { File(URI(uri)).takeIf { it.isFile }?.let { SyncInput("png", it.inputStream()) } }.getOrNull() },
            localUri = { it.toURI().toString() }, deviceLabel = label, storyRemote = story)
        fun restart() { index = AssetIndex(File(root, "index.json")); workspace = workspace() }
        suspend fun cycle() = workspace.cycle(null)
    }
    private suspend fun command(index: AssetIndex, project: String, action: String, input: JsonObjectBuilder.() -> Unit = {}): JsonObject {
        val result = index.storyCommand(project, buildJsonObject { put("action", action); put("operation_id", UUID.randomUUID().toString()); input() })
        assertTrue(result["ok"]?.jsonPrimitive?.booleanOrNull == true, result.toString())
        return result
    }
    private fun JsonObject.id() = getValue("id").jsonPrimitive.content
    private suspend fun fixture(a: Peer): List<String> {
        val project = a.index.createProject("Película compartida").id
        val scene = command(a.index, project, "create_scene") { put("title", "El encuentro") }.id()
        val note = command(a.index, project, "create_block") { put("scene_id", scene); put("kind", "note"); put("text", "Su mano tiembla") }.id()
        val first = command(a.index, project, "create_shot") { put("scene_id", scene); put("origin_block_id", note); put("title", "La mano"); put("camera", "Cámara fija") }.id()
        val second = command(a.index, project, "create_shot") { put("scene_id", scene); put("title", "La mirada") }.id()
        val original = File(a.root, "original.png").apply { parentFile.mkdirs(); writeBytes("one verified audiovisual original".toByteArray()) }
        a.index.addAsset(Asset("same-original", original.toURI().toString(), AssetType.IMAGE, displayName = "Referencia de la mano", projectId = project, createdAt = 1))
        command(a.index, project, "link_material") { put("asset_id", "same-original"); put("scope", "shot"); put("target_id", first); put("role", "candidate"); put("selected", false) }
        command(a.index, project, "link_material") { put("asset_id", "same-original"); put("scope", "shot"); put("target_id", second); put("role", "chosen"); put("selected", true) }
        return listOf(project, scene, note, first, second)
    }
    private fun temporary() = Files.createTempDirectory("nexus-story-two-peer-").toFile()

    @Test fun twoPeersKeepOneOriginalMultipleUsesReorderingAndRestart() = runBlocking {
        val root = temporary()
        try {
            val cloud = Cloud(); val narrative = Cloud()
            val a = Peer(File(root, "windows"), cloud, narrative, "Windows")
            val b = Peer(File(root, "s25"), cloud, narrative, "S25")
            val (project, scene, note, first, second) = fixture(a)
            a.cycle(); b.cycle()
            assertEquals(1, b.index.snapshot().assets.size)
            assertEquals(2, b.index.snapshot().story.links.size)
            assertEquals(setOf(first, second), b.index.snapshot().story.links.map { it.targetId }.toSet())
            assertEquals(first, b.index.snapshot().story.blocks.single { it.id == note }.shotId)
            assertFalse(b.index.snapshot().story.links.single { it.targetId == first }.selected)
            assertTrue(b.index.snapshot().story.links.single { it.targetId == second }.selected)
            assertEquals(File(URI(a.index.asset("same-original")!!.uri)).readBytes().toList(), File(URI(b.index.asset("same-original")!!.uri)).readBytes().toList())
            val shot = b.index.snapshot().shots.single { it.id == second }
            command(b.index, project, "move_shot") { put("shot_id", second); put("scene_id", scene); put("order", 0); put("expected_revision", shot.revision) }
            b.cycle(); a.cycle(); b.restart(); b.cycle(); a.cycle()
            assertEquals(second, a.index.snapshot().shots.filter { it.sceneId == scene }.minBy { it.order }.id)
            assertEquals(2, a.index.snapshot().story.links.size)
            assertEquals(2, b.index.snapshot().story.links.size)
            assertEquals(1, cloud.blobs.size)
            val receipts = b.index.snapshot().story.receipts.map { it.operationId }.toSet()
            b.cycle(); assertEquals(receipts, b.index.snapshot().story.receipts.map { it.operationId }.toSet())
        } finally { root.deleteRecursively() }
    }

    @Test fun concurrentScriptEditsRemainReviewableAndAnExplicitChoiceConverges() = runBlocking {
        val root = temporary()
        try {
            val cloud = Cloud(); val narrative = Cloud()
            val a = Peer(File(root, "windows"), cloud, narrative, "Windows")
            val b = Peer(File(root, "s25"), cloud, narrative, "S25")
            val (project, _, note) = fixture(a)
            a.cycle(); b.cycle()
            val revisionA = a.index.snapshot().story.blocks.single { it.id == note }.revision
            val revisionB = b.index.snapshot().story.blocks.single { it.id == note }.revision
            command(a.index, project, "update_block") { put("block_id", note); put("expected_revision", revisionA); put("text", "Mateo baja la mirada") }
            command(b.index, project, "update_block") { put("block_id", note); put("expected_revision", revisionB); put("text", "Mateo mira a Julián") }
            a.cycle(); val statusB = b.cycle(); val statusA = a.cycle()
            for (status in listOf(statusA, statusB)) assertEquals("conflicts", status.getValue("narrativeSync").jsonObject.getValue("state").jsonPrimitive.content)
            val conflict = statusA.getValue("conflicts").jsonArray.map { it.jsonObject }.single { it["key"]?.jsonPrimitive?.content == "story:blocks:$note" }
            val versions = conflict.getValue("versions").jsonArray.map { it.jsonObject }
            assertEquals(setOf("Mateo baja la mirada", "Mateo mira a Julián"), versions.map { it.getValue("value").jsonObject.getValue("payload").jsonObject.getValue("text").jsonPrimitive.content }.toSet())
            assertEquals("Mateo baja la mirada", a.index.snapshot().story.blocks.single { it.id == note }.text)
            assertEquals("Mateo mira a Julián", b.index.snapshot().story.blocks.single { it.id == note }.text)
            val selected = versions.single { it.getValue("value").jsonObject.getValue("payload").jsonObject.getValue("text").jsonPrimitive.content == "Mateo mira a Julián" }
            a.workspace.resolve("story:blocks:$note", selected.getValue("id").jsonPrimitive.content)
            a.cycle(); b.cycle(); a.restart(); a.cycle()
            assertEquals("Mateo mira a Julián", a.index.snapshot().story.blocks.single { it.id == note }.text)
            assertEquals("Mateo mira a Julián", b.index.snapshot().story.blocks.single { it.id == note }.text)
            assertEquals(2, a.index.snapshot().story.links.size)
            assertEquals(1, a.index.snapshot().assets.size)
        } finally { root.deleteRecursively() }
    }

    @Test fun retiredMaterialAndRemoteExecutionCannotBeResurrectedByReplay() = runBlocking {
        val root = temporary()
        try {
            val cloud = Cloud(); val narrative = Cloud()
            val a = Peer(File(root, "windows"), cloud, narrative, "Windows")
            val b = Peer(File(root, "s25"), cloud, narrative, "S25")
            val (project, scene, _, first) = fixture(a)
            val task = command(a.index, project, "enqueue_task") { put("scene_id", scene); put("shot_id", first); put("title", "Preparar referencias") }.id()
            command(a.index, project, "update_task") { put("task_id", task); put("expected_revision", 0); put("status", "running"); put("executor", "Windows-agent") }
            command(a.index, project, "set_autonomy") { put("enabled", true); put("mode", "direct") }
            a.cycle(); b.cycle()
            assertEquals("pending_resume", b.index.snapshot().story.tasks.single { it.id == task }.status)
            assertEquals("running", a.index.snapshot().story.tasks.single { it.id == task }.status)
            assertTrue(b.index.snapshot().story.autonomy.none { it.enabled })
            val original = File(URI(b.index.asset("same-original")!!.uri))
            b.index.deleteLibraryAssets(setOf("same-original"))
            b.cycle(); a.cycle(); b.restart(); b.cycle(); a.cycle()
            assertTrue(b.index.asset("same-original")!!.libraryDeleted)
            assertTrue(a.index.asset("same-original")!!.libraryDeleted)
            assertTrue(original.isFile)
            assertEquals(2, b.index.snapshot().story.links.size)
            assertEquals(1, b.index.snapshot().story.tasks.count { it.id == task })
            assertEquals("running", a.index.snapshot().story.tasks.single { it.id == task }.status)
            assertTrue(b.index.snapshot().story.autonomy.none { it.enabled })
        } finally { root.deleteRecursively() }
    }
}
