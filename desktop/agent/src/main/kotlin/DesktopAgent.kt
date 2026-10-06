import com.vidadomu.nexus.agent.core.agent.*
import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.agent.core.config.*
import com.vidadomu.nexus.agent.core.chat.*
import com.vidadomu.nexus.agent.core.cost.*
import com.vidadomu.nexus.agent.core.llm.*
import com.vidadomu.nexus.agent.core.log.Redactor
import com.vidadomu.nexus.agent.core.registry.*
import com.vidadomu.nexus.agent.core.router.ModelRouter
import com.vidadomu.nexus.agent.core.skills.*
import com.vidadomu.nexus.agent.core.spend.*
import com.vidadomu.nexus.agent.core.tools.*
import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val input = System.`in`.bufferedReader()
private fun JsonObject.text(key: String, fallback: String = "") = this[key]?.jsonPrimitive?.contentOrNull ?: fallback
private inline fun <reified T> encoded(value: T) = json.encodeToJsonElement(value)
private fun obj(vararg pairs: Pair<String, JsonElement>) = JsonObject(pairs.toMap())
private fun htmlEscape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
private fun spansHtml(spans: List<MdSpan>) = spans.joinToString("") { span ->
    var text = htmlEscape(span.text)
    if (span.code) text = "<code>$text</code>"
    if (span.bold) text = "<strong>$text</strong>"
    if (span.italic) text = "<em>$text</em>"
    if (span.strike) text = "<s>$text</s>"
    text
}
private fun markdownHtml(text: String) = ChatMarkdown.parse(text).joinToString("") { block ->
    when (block) {
        is MdBlock.Heading -> "<h3>${spansHtml(block.spans)}</h3>"
        is MdBlock.Paragraph -> "<p>${spansHtml(block.spans)}</p>"
        is MdBlock.Quote -> "<blockquote>${spansHtml(block.spans)}</blockquote>"
        is MdBlock.Code -> "<pre><code>${htmlEscape(block.code)}</code></pre>"
        is MdBlock.ListBlock -> "<ul>" + block.items.joinToString("") { "<li>${spansHtml(it.spans)}</li>" } + "</ul>"
        is MdBlock.Table -> "<div class=agent-table><table><thead><tr>" + block.header.joinToString("") { "<th>${spansHtml(it)}</th>" } +
            "</tr></thead><tbody>" + block.rows.joinToString("") { row -> "<tr>" + row.joinToString("") { "<td>${spansHtml(it)}</td>" } + "</tr>" } + "</tbody></table></div>"
        MdBlock.Rule -> "<hr>"
    }
}

// Private stdin/stdout protocol; the Windows host services bounded callbacks.
private fun callback(op: String, args: JsonElement = JsonObject(emptyMap())): JsonElement {
    println(obj("callback" to JsonPrimitive(op), "args" to args))
    val reply = json.parseToJsonElement(input.readLine() ?: error("Host disconnected")).jsonObject
    if (reply["ok"]?.jsonPrimitive?.boolean != true) error(reply.text("error", "Host operation failed"))
    return reply["value"] ?: JsonNull
}

private class DesktopRuntime(private val root: File, private val data: File) {
    private val assets = AssetIndex(File(data, "index.json"))
    private var settings = JsonObject(emptyMap())
    private var secrets = JsonObject(emptyMap())
    private var mode = AgentMode.COPILOT
    private val guard = SpendGuard()
    private val registry = object : ModelRegistry {
        override suspend fun list(): List<ModelSummary> = json.decodeFromJsonElement(callback("models"))
        override suspend fun schema(ref: ModelRef): ModelSchema = json.decodeFromJsonElement(callback("schema", encoded(ref)))
    }
    private var skills = loadSkills()
    private fun loadSkills() = SkillStore(listOf(
        DirectorySkillSource(File(root, "agent-skills/skills"), "bundled"),
        DirectorySkillSource(File(data, "skills"), "personal")))
    private val published = PublishedRateEstimator.fromJson(File(root, "assets/atlas_published_prices.json").readText())
    private val cost = object : CostEstimator {
        override suspend fun estimate(model: ModelSummary, params: Map<String, String>): CostEstimate =
            runCatching { json.decodeFromJsonElement<CostEstimate>(callback("quote", obj("ref" to encoded(model.ref), "params" to encoded(params)))) }
                .getOrElse { published.estimate(model, params) }
    }
    private val backend = object : GenerationBackend {
        override suspend fun submit(plan: GenerationPlan): SubmitResult = try {
            json.decodeFromJsonElement(callback("submit", encoded(plan)))
        } catch (e: Exception) { throw SubmissionUncertain(e.message ?: "Submission not confirmed") }
        override suspend fun status(jobId: String): JobStatus = json.decodeFromJsonElement(callback("status", JsonPrimitive(jobId)))
        override suspend fun cancel(jobId: String): Boolean = callback("cancel", JsonPrimitive(jobId)).jsonPrimitive.boolean
    }
    private fun deps() = ToolDeps(registry, skills, cost,
        ModelRouter(registry, skills, published, excludedFamilies = {
            settings.text("excludedFamilies", "veo").split(',', ';', '\n').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        }), guard, GuardedExecutor(backend, guard), assets,
        GenerationHistorySource { limit -> json.decodeFromJsonElement(callback("history", JsonPrimitive(limit))) })
    private var deps = deps()
    private var offline = true
    private fun makeOrchestrator(): AgentOrchestrator {
        val direct = settings.text("backend", "direct") != "proxy"
        val llm: LlmProvider = if (direct && secrets.text("accessKey").isNotBlank() && secrets.text("secretKey").isNotBlank()) {
            BedrockDirectProvider(settings.text("region", "us-east-1"), {
                AwsCredentials(secrets.text("accessKey"), secrets.text("secretKey"), secrets.text("sessionToken").ifBlank { null })
            })
        } else if (!direct && settings.text("endpoint").isNotBlank() && secrets.text("token").isNotBlank()) {
            BedrockProxyProvider(settings.text("endpoint"), { secrets.text("token") })
        } else OfflinePlannerProvider()
        offline = llm is OfflinePlannerProvider
        return AgentOrchestrator(llm, AgentConfig(
            provider = llm.id, modelId = settings.text("modelId", "us.anthropic.claude-sonnet-4-6"),
            region = settings.text("region", "us-east-1"), endpoint = settings.text("endpoint"),
            maxTokens = settings["maxTokens"]?.jsonPrimitive?.intOrNull?.coerceIn(256, 32000) ?: 4096,
            temperature = settings["temperature"]?.jsonPrimitive?.doubleOrNull,
            mode = mode, sendImagesToLlm = settings["sendImages"]?.jsonPrimitive?.booleanOrNull ?: true
        ), NexusToolkit.all(deps), skills, assets, images = AttachmentImageLoader { attachment ->
            val response = callback("thumbnail", encoded(attachment)).jsonObject
            ContentBlock.Image(response.text("format"), Base64.getDecoder().decode(response.text("bytes")))
        })
    }
    private var orchestrator = makeOrchestrator()
    private var conversation = orchestrator.newConversation()
    private fun snapshot() = obj(
        "offline" to JsonPrimitive(offline), "mode" to JsonPrimitive(mode.name),
        "projectId" to (conversation.session.activeProjectId?.let(::JsonPrimitive) ?: JsonNull),
        "skills" to encoded(skills.all()), "skillErrors" to encoded(skills.errors),
        "index" to encoded(assets.snapshot()),
        "plans" to JsonArray(guard.plans().map { plan -> obj("plan" to encoded(plan),
            "state" to JsonPrimitive(guard.state(plan.id).toString()), "fingerprint" to JsonPrimitive(plan.fingerprint())) }),
        "proposals" to JsonArray(conversation.session.pendingActions.values.map {
            obj("id" to JsonPrimitive(it.id), "description" to JsonPrimitive(it.description)) }))

    suspend fun command(op: String, args: JsonObject): JsonElement {
        when (op) {
            "init", "settings" -> {
                settings = args["settings"]?.jsonObject ?: settings
                secrets = args["secrets"]?.jsonObject ?: secrets
                mode = runCatching { AgentMode.valueOf(settings.text("mode", "COPILOT")) }.getOrDefault(AgentMode.COPILOT)
                orchestrator = makeOrchestrator()
                conversation.session.mode = mode
                conversation.session.activeProjectId = settings.text("projectId").ifBlank { null }
            }
            "send" -> {
                val attachments = args["attachments"]?.let { json.decodeFromJsonElement<List<Attachment>>(it) } ?: emptyList()
                val turn = orchestrator.send(conversation, args.text("text"), attachments)
                return obj("text" to JsonPrimitive(turn.text), "html" to JsonPrimitive(markdownHtml(turn.text)), "tools" to encoded(turn.toolCalls),
                    "inputTokens" to JsonPrimitive(turn.usage.inputTokens), "outputTokens" to JsonPrimitive(turn.usage.outputTokens),
                    "snapshot" to snapshot())
            }
            "new" -> {
                guard.plans().forEach { guard.discard(it.id) }
                conversation = orchestrator.newConversation(mode = mode, activeProjectId = conversation.session.activeProjectId)
            }
            "mode" -> { mode = AgentMode.valueOf(args.text("mode")); conversation.session.mode = mode }
            "project" -> {
                val id = args.text("id").ifBlank { null }
                require(id == null || assets.findProject(id) != null) { "Proyecto desconocido" }
                conversation.session.activeProjectId = id; assets.startProjectSession(id)
            }
            "createProject" -> {
                val name = args.text("name").trim().take(80); require(name.isNotBlank())
                conversation.session.activeProjectId = (assets.findProject(name) ?: assets.createProject(name)).id
                assets.startProjectSession(conversation.session.activeProjectId)
            }
            "memory" -> {
                assets.updateProject(args.text("id"), args.text("description"))
                assets.updateProjectMemory(args.text("id")) { old -> old.copy(
                    synopsis = args.text("synopsis"), tone = args.text("tone"), visualStyle = args.text("visualStyle"),
                    rules = args.text("rules").lines().filter { it.isNotBlank() },
                    preferredModels = args.text("preferredModels").lines().filter { it.isNotBlank() }) }
            }
            "apply" -> orchestrator.applyPending(conversation, args.text("id"))
            "reject" -> orchestrator.rejectPending(conversation, args.text("id"))
            "discard" -> guard.discard(args.text("id"))
            "edit" -> {
                val plan = guard.plan(args.text("id")) ?: error("Plan desconocido")
                when (val result = deps.plans.rebuild(plan, PlanEdit(prompt = args.text("prompt"),
                    params = args["params"]?.let { json.decodeFromJsonElement<Map<String, String?>>(it) } ?: emptyMap()))) {
                    is PlanBuild.Ready -> guard.replace(result.plan)
                    is PlanBuild.Invalid -> error(result.issues.joinToString { it.toString() })
                }
            }
            "confirm" -> {
                require(args.text("fingerprint").isNotBlank())
                val result = deps.lifecycle.confirmAndSubmit(args.text("id"), args.text("fingerprint"))
                return obj("job" to encoded(result), "snapshot" to snapshot())
            }
            "sync" -> deps.lifecycle.sync(args.text("id"))
            "importHistory" -> {
                val history = json.decodeFromJsonElement<List<HistoryEntry>>(callback("history", JsonPrimitive(10000)))
                for (job in history.filter { it.status == "COMPLETED" && it.outputs.isNotEmpty() }) {
                    if (assets.generation(job.jobId) != null) continue
                    val type = runCatching { AssetType.valueOf(job.modality) }.getOrDefault(AssetType.OTHER)
                    val record = GenerationRecord(id = AssetIndex.newId("gen"), nexusJobId = job.jobId,
                        providerId = job.providerId, modelId = job.modelId, modality = job.modality,
                        prompt = job.prompt, status = job.status, createdAt = job.createdAt)
                    val outputIds = job.outputs.mapIndexed { index, uri ->
                        assets.assetByUri(uri)?.id ?: assets.addAsset(Asset(id = "", uri = uri,
                            type = type, role = AssetRole.OUTPUT, source = AssetSource.GENERATED,
                            displayName = "${job.modelId.substringAfterLast('/')} ${index + 1}",
                            generationId = record.id, createdAt = job.createdAt)).id
                    }
                    assets.upsertGeneration(record.copy(outputAssetIds = outputIds))
                }
            }
            "reload" -> { skills = loadSkills(); deps = deps(); orchestrator = makeOrchestrator() }
            "snapshot" -> Unit
            else -> error("Operacion desconocida")
        }
        return snapshot()
    }
}

fun main(args: Array<String>) = runBlocking {
    val runtime = DesktopRuntime(File(args[0]), File(args[1]).also { it.mkdirs() })
    while (true) {
        val line = input.readLine() ?: break
        try {
            val request = json.parseToJsonElement(line).jsonObject
            val result = runtime.command(request.text("op"), request["args"]?.jsonObject ?: JsonObject(emptyMap()))
            println(obj("ok" to JsonPrimitive(true), "value" to result))
        } catch (e: Exception) {
            println(obj("ok" to JsonPrimitive(false), "error" to JsonPrimitive(Redactor.redact(e.message ?: "Error del agente"))))
        }
    }
}
