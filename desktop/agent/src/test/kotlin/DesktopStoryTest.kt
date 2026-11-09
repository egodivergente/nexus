import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.agent.core.config.*
import com.vidadomu.nexus.agent.core.cost.PublishedRateEstimator
import com.vidadomu.nexus.agent.core.llm.*
import com.vidadomu.nexus.agent.core.registry.*
import com.vidadomu.nexus.agent.core.router.ModelRouter
import com.vidadomu.nexus.agent.core.skills.SkillStore
import com.vidadomu.nexus.agent.core.spend.*
import com.vidadomu.nexus.agent.core.tools.*
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

class DesktopStoryTest {
    @Test fun selectionPersistsAndUserEditsDuringAgentTurnDetectConflictWithoutWideningItsScope() = runBlocking {
        val dir = Files.createTempDirectory("nexus-desktop-story").toFile()
        val assets = AssetIndex(dir.resolve("index.json")); val p = assets.createProject("Corto")
        val a = assets.ensureScene(p.id, "1", "Patio"); val b = assets.ensureScene(p.id, "2", "Celda")
        val shot = assets.ensureShot(p.id, "P1", a.id, "La mano tiembla")
        val workspace = DesktopStoryWorkspace(assets, dir)
        try {
            workspace.command("storySelection", buildJsonObject { put("projectId", p.id); put("sceneId", a.id); put("shotId", shot.id); put("mode", "direct") })
            val captured = workspace.captureForTurn(p.id)!!
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var calls = 0
            val llm = object : LlmProvider {
                override val id = "scripted"
                override suspend fun complete(request: LlmRequest): LlmResponse {
                    if (calls++ == 0) {
                        entered.complete(Unit); release.await()
                        return ScriptedLlmProvider.tools(ContentBlock.ToolUse("agent-edit", "edit_story_project", buildJsonObject { put("change", buildJsonObject {
                            put("action", "update_shot"); put("operation_id", "agent-edit-once"); put("shot_id", shot.id); put("expected_revision", 0); put("description", "El agente intenta cerrar la mano")
                        }) }))
                    }
                    val result = request.messages.flatMap { it.content }.filterIsInstance<ContentBlock.ToolResult>().last()
                    assertTrue(result.isError); assertTrue("revision_conflict" in result.toString())
                    return ScriptedLlmProvider.text("La edición reciente se conserva; hay un conflicto local.")
                }
            }
            val registry = object : ModelRegistry { override suspend fun list() = emptyList<ModelSummary>(); override suspend fun schema(ref: ModelRef): ModelSchema = error("No model/network lookup expected") }
            val cost = PublishedRateEstimator(emptyList()); val skills = SkillStore(emptyList()); val guard = SpendGuard(); val submissions = AtomicInteger()
            val backend = object : GenerationBackend {
                override suspend fun submit(plan: GenerationPlan): SubmitResult { submissions.incrementAndGet(); error("No generation expected") }
                override suspend fun status(jobId: String): JobStatus = error("No provider call expected")
                override suspend fun cancel(jobId: String) = false
            }
            val deps = ToolDeps(registry, skills, cost, ModelRouter(registry, skills, cost), guard, GuardedExecutor(backend, guard), assets)
            val agent = AgentOrchestrator(llm, AgentConfig(provider = "scripted", modelId = "test", region = "eu-west-1", endpoint = "http://localhost", mode = AgentMode.AGENT), NexusToolkit.all(deps), skills, assets)
            val conversation = agent.newConversation(activeProjectId = p.id, mode = AgentMode.AGENT).also { it.session.storySelection = captured }
            val turn = async(Dispatchers.Default) { agent.send(conversation, "Haz el cambio en este plano.", emptyList()) }
            entered.await()
            workspace.command("storySelection", buildJsonObject { put("projectId", p.id); put("sceneId", b.id); put("mode", "proposal") })
            val edit = workspace.command("storyCommand", buildJsonObject { put("projectId", p.id); put("input", buildJsonObject {
                put("action", "update_shot"); put("operation_id", "user-edit"); put("shot_id", shot.id); put("expected_revision", 0); put("description", "El usuario abre la mano")
            }) })
            assertTrue(edit["ok"]!!.jsonPrimitive.boolean)
            release.complete(Unit); turn.await()
            assertEquals(a.id, conversation.session.storySelection?.sceneId)
            assertEquals(b.id, workspace.selection()?.sceneId)
            assertEquals("El usuario abre la mano", assets.snapshot().shots.single().description)
            assertEquals(0, submissions.get())
            DesktopStoryWorkspace(assets, dir).use { reopened -> assertEquals(b.id, reopened.selection()?.sceneId) }
        } finally { workspace.close(); dir.deleteRecursively() }
    }

    @Test fun directBridgeSharesIndexAndRejectsFabricatedProviderResults() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("nexus-desktop-story-rpc").toFile(); val assets = AssetIndex(dir.resolve("index.json")); val p = assets.createProject("Proyecto")
        DesktopStoryWorkspace(assets, dir).use { workspace ->
            val input = buildJsonObject { put("action", "create_scene"); put("operation_id", "scene-once"); put("title", "Interior") }
            val args = buildJsonObject { put("projectId", p.id); put("input", input) }
            val first = workspace.command("storyCommand", args); val second = workspace.command("storyCommand", args)
            assertEquals(first["id"], second["id"]); assertEquals(1, assets.snapshot().scenes.size)
            assertEquals(1, workspace.command("storyView", buildJsonObject { put("projectId", p.id) })["scenes"]!!.jsonArray.size)
            assertFailsWith<IllegalArgumentException> { workspace.command("storyCommand", buildJsonObject { put("projectId", p.id); put("input", buildJsonObject { put("action", "register_result"); put("operation_id", "fake-result") }) }) }
        }
        dir.deleteRecursively()
    }
}
