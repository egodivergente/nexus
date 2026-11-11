package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.tools.*
import com.vidadomu.nexus.cut.command.*
import com.vidadomu.nexus.cut.storage.ProjectStore
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/** Local synthetic fixtures: no provider, physical file move, app database or user project. */
class ProjectTransferTest {
    private val dir = Files.createTempDirectory("project-transfer-test").toFile()
    private val indexFile = File(dir, "index.json")
    private val cutDir = File(dir, "cut")
    private var assets = AssetIndex(indexFile) { 1_000 }
    private var cut = workspace()
    private fun workspace() = CutWorkspace(ProjectStore(cutDir), CutRegistry().register(CoreCapabilities.all()), clock = { 1_000 }, io = Dispatchers.Unconfined)
    private fun coordinator() = ProjectTransferCoordinator(assets, cut) { 1_000 }
    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject
    private fun JsonObject.flag(key: String) = getValue(key).jsonPrimitive.boolean
    private val context = StoryGenerationContext("source", "scene", "shot", 2, "Contexto original", fingerprint = "original")

    @AfterTest fun clean() { dir.deleteRecursively() }

    private suspend fun fixture(montages: Int = 2) {
        val content = IndexData(
            projects = listOf(
                Project("source", "Origen QA", createdAt = 1, memory = ProjectMemory(synopsis = "Memoria que conserva su identidad")),
                Project("target", "Destino QA", createdAt = 2), Project("other", "Ajeno QA", createdAt = 3),
            ),
            scenes = listOf(Scene("scene", "source", "OR1", order = 5), Scene("target-scene", "target", "DE1", order = 2)),
            shots = listOf(Shot("shot", "source", "scene", "OR1"), Shot("target-shot", "target", "target-scene", "DE1")),
            characters = listOf(Character("character", "source", "Persona QA", referenceAssetIds = listOf("owned"))),
            assets = listOf(
                Asset("owned", "content://qa/original.mp4", AssetType.VIDEO, displayName = "Original", projectId = "source", sceneId = "scene", shotId = "shot", createdAt = 1),
                Asset("deleted", "content://qa/deleted.mp4", AssetType.VIDEO, displayName = "Oculto", projectId = "source", libraryDeleted = true, createdAt = 1),
                Asset("shared", "content://qa/shared.png", AssetType.IMAGE, displayName = "Compartido", projectId = "other", createdAt = 1),
                Asset("legacy", "content://qa/legacy.png", AssetType.IMAGE, displayName = "Escena heredada", projectId = "other", sceneId = "scene", createdAt = 1),
                Asset("unowned", "content://qa/unowned.png", AssetType.IMAGE, displayName = "Plano heredado", shotId = "shot", createdAt = 1),
            ),
            generations = listOf(GenerationRecord("generation", nexusJobId = "job-original", providerId = "fake", modelId = "fake", modality = "video", prompt = "Original", status = "COMPLETED", projectId = "source", shotId = "shot", outputAssetIds = listOf("owned"), createdAt = 1, storyContext = context)),
            projectSessions = listOf(ProjectSession("source", 1, 2)),
            story = StoryState(
                blocks = listOf(StoryBlock("block", "source", "scene", text = "Texto", shotId = "shot")),
                links = listOf(StoryLink("link", "source", "shared", "project", "source", context = context)),
                elements = listOf(StoryElement("element", "source", "location", "Lugar", sceneId = "scene")),
                preferences = listOf(StoryPreference("preference", "source", "project", "source", "Regla editorial")),
                proposals = listOf(
                    StoryProposal("pending", "source", "Crear escena", listOf(StoryChange("c1", json("""{"action":"create_scene","project_id":"source","code":"NUEVA"}"""))), actor = "agent", createdAt = 1),
                    StoryProposal("applied", "source", "Historia aplicada", emptyList(), status = "applied", actor = "agent", createdAt = 1),
                ),
                tasks = listOf(StoryTask("queued", "source", "Preparar", context = context, updatedAt = 1), StoryTask("completed", "source", "Terminado", status = "complete", updatedAt = 1)),
                autonomy = listOf(StoryAutonomy("source", enabled = true)),
                receipts = listOf(StoryReceipt("old-operation", "source", "hash", json("""{"ok":true}"""))),
                history = listOf(StoryHistory("history", "source", "edit", "user", 1, json("{}"), json("{}"))),
                legacyAdoptedProjectIds = listOf("source"),
            ),
        )
        assets.edit { content to Unit }
        repeat(montages) { cut.create("Montaje QA ${it + 1}", nexusProjectId = "source", sceneId = "scene") }
    }

    private fun restart() {
        assets = AssetIndex(indexFile) { 1_000 }
        cut = workspace()
    }

    /** Inject a storage-boundary fault, without adding a production test hook or bypassing the command bus. */
    private fun probeRetirement(onArchivedRead: () -> Unit) {
        val io = object : CoroutineDispatcher() {
            override fun isDispatchNeeded(context: CoroutineContext): Boolean {
                if (assets.snapshot().projects.any { it.id == "source" && it.archived }) onArchivedRead()
                return false
            }
            override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
        }
        cut = CutWorkspace(ProjectStore(cutDir), CutRegistry().register(CoreCapabilities.all()), clock = { 1_000 }, io = io)
    }

    private fun forward(r: ProjectTransferReceipt, p: ProjectTransferMontage) = Request(
        listOf(Op("project.link_story", CutSession.argsOf("nexus_project_id" to r.targetId))), Origin.AGENT,
        expectedRevision = p.revision, idempotencyKey = "project-transfer:${r.requestId}:${p.id}",
    )

    private fun inverse(r: ProjectTransferReceipt, p: ProjectTransferMontage) = Request(
        listOf(Op("project.link_story", CutSession.argsOf("nexus_project_id" to r.sourceId))), Origin.AGENT,
        expectedRevision = p.appliedRevision, idempotencyKey = "project-transfer-undo:${r.requestId}:${p.id}",
    )

    @Test fun `preview is persisted without moving content and transfer moves current references but preserves provenance`() = runTest {
        fixture()
        val before = assets.snapshot()
        val preview = coordinator().preview("qa-full", "Origen QA", "target", true)
        assertEquals(before, assets.snapshot().copy(projectTransfers = emptyList()))
        assertEquals(preview, AssetIndex(indexFile).projectTransfer("qa-full"))
        assertEquals(preview, coordinator().preview("qa-full", "source", "Destino QA", true))
        val result = coordinator().execute(preview.requestId, preview.fingerprint)
        assertTrue(result.flag("verified"), result.toString())
        assertTrue(result.flag("source_archived_recoverable"))
        assertFalse(result.flag("files_moved"))
        assertEquals(2, result.getValue("owned_assets").jsonPrimitive.int)
        assertEquals(4, result.getValue("material_associations").jsonPrimitive.int)
        assertEquals(3, result.getValue("shared_or_legacy_material").jsonPrimitive.int)
        val after = assets.snapshot()
        assertEquals(before.assets.associate { it.id to it.uri }, after.assets.associate { it.id to it.uri })
        assertTrue(after.assets.filter { it.id in setOf("owned", "deleted") }.all { it.projectId == "target" })
        assertEquals("other", after.assets.first { it.id == "shared" }.projectId)
        assertEquals("other", after.assets.first { it.id == "legacy" }.projectId)
        assertNull(after.assets.first { it.id == "unowned" }.projectId)
        assertTrue(listOf(after.scenes.first { it.id == "scene" }.projectId, after.shots.first { it.id == "shot" }.projectId,
            after.characters.single().projectId, after.story.blocks.single().projectId, after.story.elements.single().projectId).all { it == "target" })
        assertEquals(3, after.scenes.first { it.id == "scene" }.order)
        assertEquals("target", after.story.links.single().targetId)
        assertEquals("target", after.story.preferences.single().targetId)
        assertEquals(context, after.story.links.single().context)
        assertEquals(context, after.generations.single().storyContext)
        assertEquals("target", after.generations.single().projectId)
        assertEquals(before.story.history, after.story.history)
        assertEquals(before.story.receipts, after.story.receipts)
        assertEquals(before.projectSessions, after.projectSessions)
        assertEquals(before.projects.first().memory, after.projects.first { it.id == "source" }.memory)
        assertEquals("source", after.story.proposals.first { it.id == "applied" }.projectId)
        val pending = after.story.proposals.first { it.id == "pending" }
        assertEquals("invalidated_transfer", pending.status)
        assertEquals(before.story.proposals.first().changes, pending.changes)
        assertEquals("blocked", after.story.tasks.first { it.id == "queued" }.status)
        assertEquals(context, after.story.tasks.first { it.id == "queued" }.context)
        assertEquals("source", after.story.tasks.first { it.id == "completed" }.projectId)
        assertFalse(after.story.autonomy.single().enabled)
        assertEquals(setOf("source", "target"), after.story.legacyAdoptedProjectIds.toSet())
        assertTrue(cut.list().all { it.nexusProjectId == "target" })
        assertTrue(preview.montages.all { cut.session(it.id).state.value.undoLabel != null })
        val applyOldApproval = assets.storyCommand("target", json("""{"action":"apply_proposal","operation_id":"try-old-approval","proposal_id":"pending","expected_revision":${pending.revision}}"""))
        assertFalse(applyOldApproval.flag("ok"))
        assertTrue(applyOldApproval.getValue("error").jsonPrimitive.content.contains("pendiente"))
        // A failed proposal application adds no narrative edit, and does not invalidate the receipt.
        val receipt = assets.projectTransfer(preview.requestId)
        val revisions = cut.list().associate { it.id to cut.session(it.id).state.value.revision }
        assertTrue(coordinator().execute(preview.requestId, preview.fingerprint).flag("verified"))
        assertEquals(receipt, assets.projectTransfer(preview.requestId))
        assertEquals(revisions, cut.list().associate { it.id to cut.session(it.id).state.value.revision })
    }

    @Test fun `stale index including foreign legacy material and stale montage fail before moving anything`() = runTest {
        fixture()
        val p = coordinator().preview("qa-stale-index", "source", "target", true)
        assets.edit { d -> d.copy(assets = d.assets.map { if (it.id == "legacy") it.copy(displayName = "Cambio posterior") else it }) to Unit }
        assertFailsWith<IllegalArgumentException> { coordinator().execute(p.requestId, p.fingerprint) }
        assertTrue(cut.list().all { it.nexusProjectId == "source" })
        assertEquals("source", assets.snapshot().assets.first().projectId)
        val q = coordinator().preview("qa-stale-montage", "source", "target", true)
        val s = cut.session(q.montages.first().id)
        assertIs<Outcome.Done>(s.execute(Request(listOf(Op("project.rename", CutSession.argsOf("name" to "Editado"))), Origin.USER)))
        assertFailsWith<IllegalArgumentException> { coordinator().execute(q.requestId, q.fingerprint) }
        assertFalse(assets.snapshot().projects.first { it.id == "source" }.archived)
        assertEquals("source", assets.snapshot().assets.first().projectId)
    }

    @Test fun `unknown montage prevents retirement both before merge and when discovered after completion`() = runTest {
        fixture(0)
        val p = coordinator().preview("qa-new", "source", "target", true)
        val late = cut.create("Nuevo QA", nexusProjectId = "source")
        assertFailsWith<IllegalArgumentException> { coordinator().execute(p.requestId, p.fingerprint) }
        assertEquals("source", assets.snapshot().assets.first().projectId)
        assertIs<Outcome.Done>(late.execute(Request(listOf(Op("project.link_story", CutSession.argsOf("nexus_project_id" to "target"))), Origin.USER)))
        val q = coordinator().preview("qa-late", "source", "target", true)
        assertTrue(coordinator().execute(q.requestId, q.fingerprint).flag("verified"))
        cut.create("Creado tras retiro", nexusProjectId = "source")
        val retry = coordinator().execute(q.requestId, q.fingerprint)
        assertFalse(retry.flag("ok"))
        assertFalse(assets.snapshot().projects.first { it.id == "source" }.archived)
        assertEquals("INDEX_MERGED", assets.projectTransfer(q.requestId).state)
    }

    @Test fun `forward restart after montage commit replays saved key without a second edit`() = runTest {
        fixture(1)
        val p = coordinator().preview("qa-crash-forward", "source", "target", true)
        assets.mergeTransferIndex(p.requestId, p.fingerprint, 1_001)
        val committed = assertIs<Outcome.Done>(cut.session(p.montages.single().id).execute(forward(p, p.montages.single())))
        assertNull(assets.projectTransfer(p.requestId).montages.single().appliedRevision)
        restart()
        assertTrue(coordinator().execute(p.requestId, p.fingerprint).flag("verified"))
        assertEquals(committed.revision, cut.session(p.montages.single().id).state.value.revision)
        assertEquals(committed.revision, assets.projectTransfer(p.requestId).montages.single().appliedRevision)
    }

    @Test fun `new montage after index merge keeps source visible and reports the partial receipt`() = runTest {
        fixture(1)
        val p = coordinator().preview("qa-partial", "source", "target", true)
        assets.mergeTransferIndex(p.requestId, p.fingerprint, 1_001)
        val newMontage = cut.create("Nuevo durante traslado", nexusProjectId = "source")
        val result = coordinator().execute(p.requestId, p.fingerprint)
        assertFalse(result.flag("ok"))
        assertFalse(result.flag("verified"))
        assertEquals("INDEX_MERGED", assets.projectTransfer(p.requestId).state)
        assertFalse(assets.snapshot().projects.first { it.id == "source" }.archived)
        assertEquals("target", assets.snapshot().assets.first().projectId)
        assertEquals("source", newMontage.state.value.project.nexusProjectId)
        assertEquals("target", cut.session(p.montages.single().id).state.value.project.nexusProjectId)
        assertEquals("UNDONE", coordinator().undo(p.requestId).getValue("state").jsonPrimitive.content)
        assertEquals("source", assets.snapshot().assets.first().projectId)
        assertEquals("source", newMontage.state.value.project.nexusProjectId)
    }

    @Test fun `post-retirement read failure repairs source visibility and never reports success`() = runTest {
        fixture(1)
        var faults = 0
        probeRetirement { faults++; throw IOException("synthetic read failure after retirement") }
        val p = coordinator().preview("qa-retire-read-failure", "source", "target", true)
        val result = coordinator().execute(p.requestId, p.fingerprint)
        assertTrue(faults > 0)
        assertFalse(result.flag("ok"))
        assertFalse(result.flag("verified"))
        assertFalse(result.flag("source_archived_recoverable"))
        assertEquals("INDEX_MERGED", assets.projectTransfer(p.requestId).state)
        assertFalse(AssetIndex(indexFile).snapshot().projects.first { it.id == "source" }.archived)
        // A normal retry checks again and completes, without repeating the montage edit.
        cut = workspace()
        val revision = cut.session(p.montages.single().id).state.value.revision
        assertTrue(coordinator().execute(p.requestId, p.fingerprint).flag("verified"))
        assertEquals(revision, cut.session(p.montages.single().id).state.value.revision)
    }

    @Test fun `caller cancellation after retirement cannot skip the failed-read compensation`() = runTest {
        fixture(1)
        lateinit var caller: Job
        var faults = 0
        probeRetirement {
            faults++
            caller.cancel()
            throw IOException("synthetic read failure while caller cancelled")
        }
        val p = coordinator().preview("qa-retire-cancel", "source", "target", true)
        caller = launch(start = CoroutineStart.LAZY) { coordinator().execute(p.requestId, p.fingerprint) }
        caller.start()
        caller.join()
        assertTrue(caller.isCancelled)
        assertTrue(faults > 0)
        assertEquals("INDEX_MERGED", assets.projectTransfer(p.requestId).state)
        assertFalse(assets.snapshot().projects.first { it.id == "source" }.archived)
        assertFalse(AssetIndex(indexFile).snapshot().projects.first { it.id == "source" }.archived)
    }

    @Test fun `status repairs a persisted retirement when montages cannot be verified`() = runTest {
        fixture(1)
        val p = coordinator().preview("qa-status-repair", "source", "target", true)
        assertTrue(coordinator().execute(p.requestId, p.fingerprint).flag("verified"))
        restart()
        probeRetirement { throw IOException("synthetic unavailable storage") }
        val result = coordinator().status(p.requestId)
        assertFalse(result.flag("ok"))
        assertFalse(result.flag("verified"))
        assertFalse(result.flag("source_archived_recoverable"))
        assertEquals("INDEX_MERGED", assets.projectTransfer(p.requestId).state)
        assertFalse(assets.snapshot().projects.first { it.id == "source" }.archived)
    }

    @Test fun `undo refuses new destination montage dependencies in header or clip story before changing anything`() = runTest {
        fixture(1)
        val p = coordinator().preview("qa-new-dependent-montage", "source", "target", true)
        assertTrue(coordinator().execute(p.requestId, p.fingerprint).flag("verified"))
        val dependent = cut.create("Nuevo destino QA", nexusProjectId = "target", sceneId = "scene")
        val before = assets.snapshot()
        val plannedRevision = cut.session(p.montages.single().id).state.value.revision
        assertFailsWith<IllegalArgumentException> { coordinator().undo(p.requestId) }
        assertEquals(before, assets.snapshot())
        assertEquals(plannedRevision, cut.session(p.montages.single().id).state.value.revision)
        assertEquals("target", dependent.state.value.project.nexusProjectId)
        assertIs<Outcome.Done>(dependent.execute(Request(listOf(Op("project.link_story", CutSession.argsOf("scene_id" to ""))), Origin.USER)))
        val title = assertIs<Outcome.Done>(dependent.execute(Request(listOf(Op("subtitle.add", CutSession.argsOf("text" to "Nuevo trabajo QA"))), Origin.USER)))
        val clipId = title.results.single().getValue("clip_id").jsonPrimitive.content
        assertIs<Outcome.Done>(dependent.execute(Request(listOf(Op("clip.set_story", CutSession.argsOf("clip_ids" to listOf(clipId), "shot_id" to "shot"))), Origin.USER)))
        val dependentRevision = dependent.state.value.revision
        assertFailsWith<IllegalArgumentException> { coordinator().undo(p.requestId) }
        assertEquals(before, assets.snapshot())
        assertEquals(dependentRevision, dependent.state.value.revision)
        assertEquals(plannedRevision, cut.session(p.montages.single().id).state.value.revision)
        assertIs<Outcome.Done>(dependent.execute(Request(listOf(Op("clip.set_story", CutSession.argsOf("clip_ids" to listOf(clipId), "clear" to true))), Origin.USER)))
        assertEquals("UNDONE", coordinator().undo(p.requestId).getValue("state").jsonPrimitive.content)
        assertEquals("target", dependent.state.value.project.nexusProjectId)
        assertEquals("Nuevo trabajo QA", dependent.state.value.project.timeline.allTracks.flatMap { it.clips }.single().text!!.text)
    }

    @Test fun `peer merge and synced snapshot preserve only local transfer receipts`() = runTest {
        fixture(0)
        val p = coordinator().preview("qa-local-receipt", "source", "target", false)
        val local = assets.snapshot()
        val remote = local.copy(projectTransfers = listOf(p.copy(requestId = "foreign-receipt")))
        val merged = assets.mergeFromPeer(remote, local, true)
        assertEquals(listOf(p), merged.snapshot.projectTransfers)
        assertTrue(merged.conflicts.isEmpty(), merged.conflicts.toString())
        assets.applySyncedSnapshot(assets.snapshot(), remote)
        assertEquals(listOf(p), assets.snapshot().projectTransfers)
    }

    @Test fun `undo restores identities and references without touching unrelated new data`() = runTest {
        fixture()
        val before = assets.snapshot()
        val p = coordinator().preview("qa-undo", "source", "target", true)
        coordinator().execute(p.requestId, p.fingerprint)
        assets.edit { d -> d.copy(assets = d.assets + Asset("other-new", "content://qa/new.png", AssetType.IMAGE, displayName = "Ajeno nuevo", projectId = "other", createdAt = 5)) to Unit }
        val undo = coordinator().undo(p.requestId)
        assertEquals("UNDONE", undo.getValue("state").jsonPrimitive.content)
        assertFalse(assets.snapshot().projects.first { it.id == "source" }.archived)
        assertEquals(before.assets, assets.snapshot().assets.filter { it.id != "other-new" })
        assertTrue(assets.snapshot().assets.any { it.id == "other-new" && it.projectId == "other" })
        assertEquals(before.story.proposals, assets.snapshot().story.proposals)
        assertEquals(before.story.tasks, assets.snapshot().story.tasks)
        assertEquals(before.story.autonomy, assets.snapshot().story.autonomy)
        assertEquals(before.generations, assets.snapshot().generations)
        assertTrue(cut.list().all { it.nexusProjectId == "source" })
        assertEquals(before.scenes.associate { it.id to it.projectId }, assets.snapshot().scenes.associate { it.id to it.projectId })
        assertEquals(before.story.links.single().targetId, assets.snapshot().story.links.single().targetId)
        assertEquals(undo, coordinator().undo(p.requestId))
    }

    @Test fun `inverse restart gap recovers only our own committed idempotency key`() = runTest {
        fixture(1)
        val p = coordinator().preview("qa-crash-undo", "source", "target", true)
        coordinator().execute(p.requestId, p.fingerprint)
        val applied = assets.projectTransfer(p.requestId)
        assets.updateTransfer(p.requestId) { it.copy(state = "UNDOING") }
        val committed = assertIs<Outcome.Done>(cut.session(p.montages.single().id).execute(inverse(applied, applied.montages.single())))
        assertNull(assets.projectTransfer(p.requestId).montages.single().undoneRevision)
        restart()
        val result = coordinator().undo(p.requestId)
        assertEquals("UNDONE", result.getValue("state").jsonPrimitive.content)
        assertEquals(committed.revision, cut.session(p.montages.single().id).state.value.revision)
        assertEquals(committed.revision, assets.projectTransfer(p.requestId).montages.single().undoneRevision)
        assertEquals("source", assets.snapshot().assets.first().projectId)
    }

    @Test fun `later target edits or a manual inverse are never overwritten by undo`() = runTest {
        fixture(1)
        val p = coordinator().preview("qa-refuse-undo", "source", "target", false)
        coordinator().execute(p.requestId, p.fingerprint)
        assets.edit { d -> d.copy(assets = d.assets + Asset("new-target", "content://qa/new.mp4", AssetType.VIDEO, displayName = "Nuevo", projectId = "target", createdAt = 6)) to Unit }
        val snapshot = assets.snapshot()
        assertFailsWith<IllegalArgumentException> { coordinator().undo(p.requestId) }
        assertEquals(snapshot, assets.snapshot())
        assets.edit { d -> d.copy(assets = d.assets.filterNot { it.id == "new-target" }) to Unit }
        val s = cut.session(p.montages.single().id)
        assertIs<Outcome.Done>(s.execute(Request(listOf(Op("project.link_story", CutSession.argsOf("nexus_project_id" to "source"))), Origin.USER)))
        val revision = s.state.value.revision
        assertFailsWith<IllegalArgumentException> { coordinator().undo(p.requestId) }
        assertEquals(revision, s.state.value.revision)
        assertEquals("target", assets.snapshot().assets.first().projectId)
    }

    @Test fun `ambiguous names duplicate codes active work and reused request ids are rejected without mutation`() = runTest {
        fixture(0)
        assets.edit { d -> d.copy(projects = d.projects + Project("ambiguous", "Orígen QA", createdAt = 3)) to Unit }
        assertFailsWith<IllegalArgumentException> { coordinator().preview("qa-ambiguous", "Origen QA", "target", true) }
        assets.edit { d -> d.copy(scenes = d.scenes.map { if (it.id == "target-scene") it.copy(code = "OR1") else it }) to Unit }
        assertFailsWith<IllegalArgumentException> { coordinator().preview("qa-collision", "source", "target", true) }
        assets.edit { d -> d.copy(scenes = d.scenes.map { if (it.id == "target-scene") it.copy(code = "DE1") else it }, generations = d.generations.map { it.copy(status = "RUNNING") }) to Unit }
        assertFailsWith<IllegalArgumentException> { coordinator().preview("qa-running", "source", "target", true) }
        assets.edit { d -> d.copy(generations = d.generations.map { it.copy(status = "COMPLETED") }) to Unit }
        coordinator().preview("qa-stable-id", "source", "target", true)
        assertFailsWith<IllegalArgumentException> { coordinator().preview("qa-stable-id", "source", "other", true) }
        assertEquals("source", assets.snapshot().assets.first().projectId)
    }

    @Test fun `tools return explicit errors move only caller source context and undo restores it`() = runTest {
        fixture(1)
        val h = Harness(assets = assets)
        val deps = ToolDeps(h.registry, h.skills, h.cost, h.router, h.guard, h.executor, assets, clock = { 1_000 }, cut = cut)
        val tools = ProjectTransferTools.all(deps).associateBy { it.name }
        assertEquals(4, tools.size)
        assertEquals(ToolCategory.ORGANIZE, tools.getValue("transfer_project").category)
        val session = AgentSession("caller", AgentMode.AGENT, "source")
        val preview = tools.getValue("preview_project_transfer").run(json("""{"request_id":"qa-tools","source_project":"source","target_project":"target","retire_source":true}"""), session)
        assertFalse(preview.isError)
        val fingerprint = preview.result.jsonObject.getValue("fingerprint").jsonPrimitive.content
        val refused = tools.getValue("transfer_project").run(json("""{"request_id":"qa-tools","expected_fingerprint":"wrong"}"""), session)
        assertTrue(refused.isError)
        assertFalse(refused.result.jsonObject.flag("ok"))
        assertEquals("source", session.activeProjectId)
        val result = tools.getValue("transfer_project").run(json("""{"request_id":"qa-tools","expected_fingerprint":"$fingerprint"}"""), session)
        assertFalse(result.isError, result.result.toString())
        assertEquals("target", session.activeProjectId)
        val unrelated = AgentSession("unrelated", AgentMode.AGENT, "other")
        tools.getValue("transfer_project").run(json("""{"request_id":"qa-tools","expected_fingerprint":"$fingerprint"}"""), unrelated)
        assertEquals("other", unrelated.activeProjectId)
        val undo = tools.getValue("undo_project_transfer").run(json("""{"request_id":"qa-tools"}"""), session)
        assertFalse(undo.isError, undo.result.toString())
        assertEquals("source", session.activeProjectId)
        assertEquals("other", unrelated.activeProjectId)
    }
}
