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
private val callbackReplies=java.util.concurrent.LinkedBlockingQueue<JsonObject>()
@Volatile private var activeRequestId=""
private val protocolOutput=Any()
private fun emit(value: JsonObject) = synchronized(protocolOutput) { println(value) }
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
    emit(obj("callback" to JsonPrimitive(op), "args" to args, "requestId" to JsonPrimitive(activeRequestId)))
    val reply = try { callbackReplies.take() } catch(e: InterruptedException) {
        throw kotlinx.coroutines.CancellationException("Turno detenido").also { it.initCause(e) }
    }
    if(reply["cancelled"]?.jsonPrimitive?.booleanOrNull==true) throw kotlinx.coroutines.CancellationException("Turno detenido")
    if (reply["ok"]?.jsonPrimitive?.boolean != true) error(reply.text("error", "Host operation failed"))
    return reply["value"] ?: JsonNull
}

private class DesktopRuntime(private val root: File, private val data: File) {
    private val assets = AssetIndex(File(data, "index.json"))
    private var settings = JsonObject(emptyMap())
    private var secrets = JsonObject(emptyMap())
    private var mode = AgentMode.COPILOT
    private val guard = SpendGuard()
    private var turnModels: List<ModelSummary>? = null
    private val turnSchemas = mutableMapOf<ModelRef, ModelSchema>()
    private val registry = object : ModelRegistry {
        override suspend fun list(): List<ModelSummary> = turnModels ?: json.decodeFromJsonElement<List<ModelSummary>>(callback("models")).also { turnModels = it }
        override suspend fun schema(ref: ModelRef): ModelSchema = turnSchemas[ref] ?: json.decodeFromJsonElement<ModelSchema>(callback("schema", encoded(ref))).also { turnSchemas[ref] = it }
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
            GenerationHistorySource { limit -> json.decodeFromJsonElement(callback("history", JsonPrimitive(limit))) }, directFlow = true,
            adaptPrompt = { prompt, schema, target -> promptAdapter.adapt(prompt, schema.textLimits["prompt"], target) },
            nativeOperations = NativeOperations { operation, arguments, session ->
                val response = callback("native", obj("operation" to JsonPrimitive(operation), "input" to arguments,
                    "assets" to encoded(assets.snapshot().assets), "projectId" to (session.activeProjectId?.let(::JsonPrimitive) ?: JsonNull))).jsonObject
                response["_assets"]?.jsonArray?.forEach { element ->
                    val asset = json.decodeFromJsonElement<Asset>(element)
                    if (assets.asset(asset.id) == null) assets.addAsset(asset) else assets.updateAsset(asset.id) { asset }
                }
                response["_attachments"]?.jsonArray?.forEach { element ->
                    val attachment=json.decodeFromJsonElement<Attachment>(element)
                    session.attachments.removeAll { it.id == attachment.id }; session.attachments.add(attachment)
                    viewedAssets.removeAll { it.id == attachment.id }; viewedAssets.add(attachment)
                }
                JsonObject(response.filterKeys { it !in setOf("_assets", "_attachments") })
            })
    private var deps = deps()
    private val viewedAssets = mutableListOf<Attachment>()
    private var activeLimits = AgentLimits()
    private var offline = true
    private lateinit var llmProvider: LlmProvider
    private fun modelId() = if(settings.text("backend") == "profile") settings["brainProfile"]?.jsonObject?.text("modelId").orEmpty() else settings.text("modelId", "us.anthropic.claude-sonnet-4-6")
    private val promptAdapter = PromptAdapter({ llmProvider }, { modelId() })
    private val knowledge = ExpertKnowledge(File(root, "assets/expert-guides.json"))
    private val budget = LlmBudgetLedger(File(data, "llm-budget.json"))
    private fun makeOrchestrator(): AgentOrchestrator {
        val backend = settings.text("backend", "direct")
        val profile = if(backend == "profile") settings["brainProfile"]?.let { json.decodeFromJsonElement<BrainProfile>(it) } else null
        val llm: LlmProvider = when(backend) {
            "profile" -> BrainFactory.create(profile ?: error("Falta el perfil del cerebro"), { secrets.text("brainKey") })
            "anthropic" -> AnthropicProvider({ secrets.text("apiKey") }, settings.text("endpoint").ifBlank { "https://api.anthropic.com" })
            "openai" -> OpenAiCompatibleProvider(settings.text("endpoint"), { secrets.text("apiKey") })
            "antigravity" -> AntigravityProvider(settings.text("endpoint"), { secrets.text("apiKey") })
            "proxy" -> if(settings.text("endpoint").isNotBlank() && secrets.text("token").isNotBlank())
                BedrockProxyProvider(settings.text("endpoint"), { secrets.text("token") }) else OfflinePlannerProvider()
            "direct" -> if(secrets.text("accessKey").isNotBlank() && secrets.text("secretKey").isNotBlank())
                BedrockDirectProvider(settings.text("region", "us-east-1"), { AwsCredentials(secrets.text("accessKey"), secrets.text("secretKey"), secrets.text("sessionToken").ifBlank { null }) }) else OfflinePlannerProvider()
            else -> error("Cerebro desconocido: $backend")
        }
        val limits = (settings["limits"]?.let { json.decodeFromJsonElement<AgentLimits>(it) } ?: AgentLimits(
            maxTokens=settings["maxTokens"]?.jsonPrimitive?.intOrNull ?: 4096)).let { if(profile != null) it.with(profile.limits) else it }
        require(limits.problems().isEmpty()) { limits.problems().joinToString("; ") }
        activeLimits = limits
        offline = llm is OfflinePlannerProvider
        val effective = if (llm is BedrockDirectProvider || backend in setOf("profile", "anthropic", "openai")) budgeted(llm, budget,
            customRates = { profile?.inputUsdPerM?.let { i -> profile.outputUsdPerM?.let { o -> i to o } } },
            limits = {
                fun amount(v: Double) = if(v == 0.0) Double.MAX_VALUE else v
                LlmLimits(perTurnUsd=amount(profile?.limits?.turnUsd ?: settings["perTurnUsd"]?.jsonPrimitive?.doubleOrNull ?: 1.0),
                    dailyUsd=amount(profile?.limits?.dailyUsd ?: settings["dailyUsd"]?.jsonPrimitive?.doubleOrNull ?: 5.0),
                    perTurnCalls=(profile?.limits?.maxCallsPerTurn ?: settings["perTurnCalls"]?.jsonPrimitive?.intOrNull ?: 8).let { if(it==0) Int.MAX_VALUE else it })
            }) else llm
        llmProvider = effective
        return AgentOrchestrator(effective, AgentConfig(
            provider = llm.id, modelId = profile?.modelId ?: settings.text("modelId", "us.anthropic.claude-sonnet-4-6"),
            region = settings.text("region", "us-east-1"), endpoint = settings.text("endpoint"),
            maxTokens = limits.maxTokens,
            maxToolRounds = limits.rounds, limits = limits,
            temperature = settings["temperature"]?.jsonPrimitive?.doubleOrNull,
            toolUse = profile?.capabilities?.tools ?: true,
            mode = mode, sendImagesToLlm = settings["sendImages"]?.jsonPrimitive?.booleanOrNull ?: true
        ), NexusToolkit.all(deps) +
            ExpertBriefTool(registry, knowledge) { settings.text("excludedFamilies", "veo").split(',', ';').map { it.trim().lowercase() }.toSet() },
            skills, assets, images = AttachmentImageLoader { attachment ->
            val response = callback("thumbnail", encoded(attachment)).jsonObject
            ContentBlock.Image(response.text("format"), Base64.getDecoder().decode(response.text("bytes")))
        }, documents = AttachmentDocumentLoader { attachment ->
            val profile=settings["brainProfile"]?.jsonObject
            val responses=settings.text("backend")=="profile" && profile?.text("protocol")=="OPENAI_RESPONSES"
            val vision=profile?.get("capabilities")?.jsonObject?.get("vision")?.jsonPrimitive?.booleanOrNull==true
            val response = callback("document", JsonObject(encoded(attachment).jsonObject + ("render_pdf" to JsonPrimitive(responses && vision)))).jsonObject
            val pages=response["pages"]?.jsonArray.orEmpty().map { page ->
                val p=page.jsonObject;ContentBlock.Image(p.text("format"),Base64.getDecoder().decode(p.text("bytes")))
            }
            require(!responses || response.text("format")!="pdf" || pages.isNotEmpty()) { "Este PDF requiere un cerebro Responses con visión y renderizado local de páginas." }
            ContentBlock.Document(response.text("format"), response.text("name"), Base64.getDecoder().decode(response.text("bytes")),pages,response.text("coverage").ifBlank { null })
        }, videos = AttachmentVideoLoader { attachment ->
            val response = callback("video", encoded(attachment)).jsonObject
            response["frames"]!!.jsonArray.flatMap { element ->
                val frame = element.jsonObject
                listOf(ContentBlock.Text("Fotograma a ${frame["timestamp_ms"]} ms"),
                    ContentBlock.Image(frame.text("format"), Base64.getDecoder().decode(frame.text("bytes"))))
            }
        })
    }
    private var orchestrator = makeOrchestrator()
    private var conversation = orchestrator.newConversation()
    private fun snapshot() = obj(
        "offline" to JsonPrimitive(offline), "mode" to JsonPrimitive(mode.name),
        "projectId" to (conversation.session.activeProjectId?.let(::JsonPrimitive) ?: JsonNull),
        "skills" to encoded(skills.all()), "skillErrors" to encoded(skills.errors),
        "index" to encoded(assets.snapshot()),
        "tools" to encoded(orchestrator.toolsFor(mode).map { it.name }),
        "budgetUsdToday" to JsonPrimitive(budget.spentToday()),
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
                val history = conversation.history().map { (if (it.role == Role.USER) "user" else "assistant") to it.text() }.filter { it.second.isNotBlank() }
                orchestrator = makeOrchestrator()
                // A provider switch starts fresh context; never replay old tools or payments.
                conversation = orchestrator.newConversation(mode = mode, activeProjectId = settings.text("projectId").ifBlank { null })
                conversation.restoreTextHistory(history)
                conversation.session.mode = mode
                conversation.session.activeProjectId = settings.text("projectId").ifBlank { null }
            }
            "validateLimits" -> {
                val limits=json.decodeFromJsonElement<AgentLimits>(args["limits"] ?: encoded(AgentLimits()))
                return obj("problems" to encoded(limits.problems()))
            }
            "validateBrain", "discoverBrain", "testBrain" -> {
                val profile=json.decodeFromJsonElement<BrainProfile>(args["profile"] ?: settings["brainProfile"] ?: error("Falta perfil"))
                val problems=profile.problems()
                if(op == "validateBrain") return obj("problems" to encoded(problems))
                require(problems.isEmpty()) { problems.joinToString("; ") }
                if(op == "discoverBrain") return obj("models" to encoded(BrainDiscovery.listModels(profile, { secrets.text("brainKey") })))
                val test=BrainDiscovery.test(profile, { secrets.text("brainKey") })
                return obj("ok" to JsonPrimitive(test.ok), "message" to JsonPrimitive(test.message), "models" to encoded(test.models), "modelListed" to (test.modelListed?.let(::JsonPrimitive) ?: JsonNull))
            }
            "rewritePrompt" -> {
                val limit = json.decodeFromJsonElement<TextLimit>(args["limit"] ?: error("Falta el límite del modelo."))
                val rewritten = promptAdapter.adapt(args.text("prompt"), limit, args.text("target"))
                return obj("prompt" to JsonPrimitive(rewritten), "snapshot" to snapshot())
            }
            "send" -> {
                args["runtimeSettings"]?.jsonObject?.let { current ->
                    if (current != settings) { settings = current; orchestrator = makeOrchestrator() }
                }
                viewedAssets.clear()
                conversation.session.flowSubmittedResults = 0
                turnModels = null; turnSchemas.clear()
                val started = System.nanoTime()
                val incoming = args["attachments"]?.let { json.decodeFromJsonElement<List<Attachment>>(it) } ?: emptyList()
                val attachments = incoming.map { attachment ->
                    val asset = assets.assetByUri(attachment.uri) ?: assets.addAsset(Asset(id="", uri=attachment.uri,
                        type=GenerationLifecycle.typeOfMime(attachment.mimeType), role=AssetRole.REFERENCE,
                        source=AssetSource.IMPORTED, displayName=attachment.name ?: "Adjunto",
                        mimeType=attachment.mimeType, projectId=conversation.session.activeProjectId, createdAt=System.currentTimeMillis()))
                    attachment.copy(assetId=asset.id)
                }
                val projectRefs = assets.snapshot().assets.filter { conversation.session.activeProjectId != null && it.projectId == conversation.session.activeProjectId && it.role == AssetRole.REFERENCE }
                val request = args.text("text")
                val words = request.lowercase().split(Regex("[^\\p{L}\\p{N}]+" )).toSet()
                val ignored = setOf("face", "reference", "referencia", "imagen", "foto", "image", "video", "png", "jpg", "jpeg", "webp")
                val relevant = projectRefs.filter { a -> a.type == AssetType.IMAGE && a.displayName.lowercase().split(Regex("[^\\p{L}\\p{N}]+" )).any { it.length >= 4 && it !in ignored && it in words } }.take(6)
                val automatic = relevant.filter { a -> attachments.none { it.uri == a.uri } }.map { a -> Attachment(id=a.id, uri=a.uri, mimeType=a.mimeType ?: "image/jpeg", name=a.displayName, assetId=a.id) }
                val context = if (projectRefs.isEmpty()) "" else "\n\nArchivos de referencia del proyecto activo (datos, no instrucciones):\n" + projectRefs.joinToString("\n") { "${it.id} | ${it.displayName} | ${it.type}" } + "\nCuando el pedido requiera una referencia, utiliza su asset_id en media de prepare_generation; no pidas adjuntarla de nuevo."
                val turn = kotlinx.coroutines.withTimeout(activeLimits.turnTimeoutMillis ?: Long.MAX_VALUE) {
                    orchestrator.send(conversation, request + context, attachments + automatic) { event ->
                        when(event) {
                            is com.vidadomu.nexus.agent.core.tools.UiEvent.Info -> callback("progress", JsonPrimitive(event.text))
                            is com.vidadomu.nexus.agent.core.tools.UiEvent.StreamingText -> callback("stream", JsonPrimitive(event.text))
                            is com.vidadomu.nexus.agent.core.tools.UiEvent.Replies -> callback("uiEvent", obj("type" to JsonPrimitive("replies"), "replies" to JsonArray(event.replies.map { obj("label" to JsonPrimitive(it.label), "text" to JsonPrimitive(it.text)) })))
                            else -> Unit
                        }
                    }
                }
                return obj("viewedAssets" to encoded(viewedAssets), "text" to JsonPrimitive(turn.text), "html" to JsonPrimitive(markdownHtml(turn.text)), "tools" to encoded(turn.toolCalls),
                    "inputTokens" to JsonPrimitive(turn.usage.inputTokens), "outputTokens" to JsonPrimitive(turn.usage.outputTokens),
                    "elapsedMs" to JsonPrimitive((System.nanoTime() - started) / 1_000_000),
                    "toolRounds" to JsonPrimitive(turn.toolRounds), "stop" to JsonPrimitive(turn.stop),
                    "expertVersion" to JsonPrimitive(ExpertPolicy.VERSION), "snapshot" to snapshot())
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
            "importReferences" -> {
                val projectId = args.text("projectId")
                require(assets.findProject(projectId) != null) { "Proyecto desconocido" }
                val files = args["files"]?.jsonArray ?: error("Faltan archivos")
                require(files.isNotEmpty()) { "Faltan archivos" }
                for (element in files) {
                    val f = element.jsonObject; val uri=f.text("uri")
                    require(Regex("/media/[a-f0-9]{32}\\.(png|jpe?g|webp|gif|mp4|mov|webm|mp3|wav|m4a|pdf|txt|md|csv|html|srt|ogg|flac)").matches(uri)) { "Archivo local no válido" }
                    val name=f.text("name").trim().take(180); require(name.isNotBlank())
                    val existing=assets.snapshot().assets.firstOrNull { it.uri==uri && it.projectId==projectId && it.role==AssetRole.REFERENCE }
                    if(existing==null) assets.addAsset(Asset(id="",uri=uri,type=GenerationLifecycle.typeOfMime(f.text("mime")),role=AssetRole.REFERENCE,source=AssetSource.IMPORTED,displayName=name,mimeType=f.text("mime"),projectId=projectId,createdAt=System.currentTimeMillis()))
                }
            }
            "renameReference" -> {
                val a=assets.asset(args.text("id")) ?: error("Referencia desconocida")
                require(a.role==AssetRole.REFERENCE && a.projectId==args.text("projectId"))
                val name=args.text("name").trim().take(180);require(name.isNotBlank())
                assets.updateAsset(a.id) { it.copy(displayName=name) }
            }
            "memory" -> {
                assets.updateProject(args.text("id"), args.text("description"))
                assets.updateProjectMemory(args.text("id")) { old -> old.copy(
                    synopsis = args.text("synopsis"), tone = args.text("tone"), visualStyle = args.text("visualStyle"),
                    rules = args.text("rules").lines().filter { it.isNotBlank() },
                    preferredModels = args.text("preferredModels").lines().filter { it.isNotBlank() }) }
            }
            "projectNote" -> assets.updateProjectMemory(args.text("projectId")) { memory ->
                if (args["index"] != null) memory.copy(notes = memory.notes.filterIndexed { index, _ -> index != args["index"]!!.jsonPrimitive.int })
                else memory.copy(notes = (memory.notes + ProjectNote(System.currentTimeMillis(), args.text("text").trim().take(8000))).takeLast(100))
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
                val rawHistory = callback("history", JsonPrimitive(10000)).jsonArray
                val history = json.decodeFromJsonElement<List<HistoryEntry>>(rawHistory)
                val byJob = rawHistory.associateBy { it.jsonObject.text("jobId") }
                for (job in history.filter { it.status == "COMPLETED" && it.outputs.isNotEmpty() }) {
                    val existing = assets.generation(job.jobId)
                    if (existing?.status == "COMPLETED" && existing.outputAssetIds.mapNotNull { assets.asset(it)?.uri }.toSet() == job.outputs.toSet()) continue
                    val type = runCatching { AssetType.valueOf(job.modality) }.getOrDefault(AssetType.OTHER)
                    val projectId = byJob[job.jobId]?.jsonObject?.text("projectId")?.takeIf { assets.findProject(it) != null }
                    val record = existing ?: GenerationRecord(id = AssetIndex.newId("gen"), nexusJobId = job.jobId,
                        providerId = job.providerId, modelId = job.modelId, modality = job.modality,
                        prompt = job.prompt, status = job.status, createdAt = job.createdAt, projectId = projectId)
                    val outputIds = job.outputs.mapIndexed { index, uri ->
                        assets.assetByUri(uri)?.id ?: assets.addAsset(Asset(id = "", uri = uri,
                            type = type, role = AssetRole.OUTPUT, source = AssetSource.GENERATED,
                            displayName = "${job.modelId.substringAfterLast('/')} ${index + 1}",
                            generationId = record.id, projectId = record.projectId, shotId = record.shotId, createdAt = job.createdAt)).id
                    }
                    assets.upsertGeneration(record.copy(status = "COMPLETED", outputAssetIds = outputIds, completedAt = record.completedAt ?: System.currentTimeMillis()))
                }
            }
            "reload" -> { skills = loadSkills(); deps = deps(); orchestrator = makeOrchestrator() }
            "snapshot" -> Unit
            "importProjectBundle" -> assets.importProject(json.decodeFromJsonElement<IndexData>(args.getValue("index")))
            "validateProjectBundle" -> AssetIndex(null).importProject(json.decodeFromJsonElement<IndexData>(args.getValue("index")))
            "importAssets" -> args["assets"]!!.jsonArray.forEach { element ->
                val asset = json.decodeFromJsonElement<Asset>(element)
                if (assets.asset(asset.id) == null) assets.addAsset(asset) else assets.updateAsset(asset.id) { asset }
            }
            "deleteLibrary" -> assets.deleteFromLibrary(args.text("projectId"), args["assetIds"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet())
            "restoreHistory" -> if (conversation.history().isEmpty()) conversation.restoreTextHistory(
                args["items"]!!.jsonArray.map { it.jsonObject.text("role") to it.jsonObject.text("text") })
            else -> error("Operacion desconocida")
        }
        return snapshot()
    }
}

fun main(args: Array<String>) {
    val runtime = DesktopRuntime(File(args[0]), File(args[1]).also { it.mkdirs() })
    val worker=java.util.concurrent.atomic.AtomicReference<Thread?>()
    val cancelled=java.util.concurrent.atomic.AtomicBoolean(false)
    while (true) {
        val line = input.readLine() ?: break
        val request=runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
        if(request.text("op")=="__cancel") {
            if(request.text("requestId")==activeRequestId && worker.get()!=null) {
                cancelled.set(true)
                callbackReplies.offer(obj("ok" to JsonPrimitive(false),"cancelled" to JsonPrimitive(true),"error" to JsonPrimitive("Turno detenido")))
                worker.get()?.interrupt()
            }
            continue
        }
        if("op" !in request) {
            if(worker.get()!=null && request.text("requestId")==activeRequestId) callbackReplies.offer(request)
            continue
        }
        if(worker.get()!=null) { emit(obj("ok" to JsonPrimitive(false),"error" to JsonPrimitive("El agente está ocupado")));continue }
        val id=request.text("requestId");activeRequestId=id;callbackReplies.clear();cancelled.set(false)
        val thread=Thread({
            val reply=try {
                val result=runBlocking { runtime.command(request.text("op"),request["args"]?.jsonObject ?: JsonObject(emptyMap())) }
                obj("ok" to JsonPrimitive(true),"value" to result)
            } catch(e: Exception) {
                obj("ok" to JsonPrimitive(false),"error" to JsonPrimitive(if(cancelled.get()) "Turno detenido por el usuario. Las operaciones ya enviadas conservan su estado." else Redactor.redact(e.message ?: "Error del agente")))
            }
            activeRequestId="";worker.set(null)
            emit(JsonObject(reply + ("requestId" to JsonPrimitive(id))))
        },"nexus-command")
        worker.set(thread);thread.start()
    }
    worker.get()?.interrupt()
}
