package com.vidadomu.nexus.agent.core.agent

import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.llm.GeneratedMedia
import com.vidadomu.nexus.agent.core.llm.LlmException
import com.vidadomu.nexus.agent.core.llm.LlmMessage
import com.vidadomu.nexus.agent.core.llm.LlmProvider
import com.vidadomu.nexus.agent.core.llm.LlmRequest
import com.vidadomu.nexus.agent.core.llm.Role
import com.vidadomu.nexus.agent.core.llm.StopReason
import com.vidadomu.nexus.agent.core.llm.StreamingLlmProvider
import com.vidadomu.nexus.agent.core.llm.ToolSpec
import com.vidadomu.nexus.agent.core.llm.Usage
import com.vidadomu.nexus.agent.core.log.AgentLogger
import com.vidadomu.nexus.agent.core.log.Redactor
import com.vidadomu.nexus.agent.core.skills.SkillStore
import com.vidadomu.nexus.agent.core.tools.AgentSession
import com.vidadomu.nexus.agent.core.tools.AgentTool
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.agent.core.tools.PendingAction
import com.vidadomu.nexus.agent.core.tools.ToolCategory
import com.vidadomu.nexus.agent.core.tools.UiEvent
import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Miniatura que el LLM puede ver (keyframe adjunto). La app decide tamaño y formato. */
fun interface AttachmentImageLoader {
    suspend fun load(attachment: Attachment): ContentBlock.Image?
}

fun interface AttachmentDocumentLoader {
    suspend fun load(attachment: Attachment): ContentBlock.Document?
}

/** Sampled visual frames and their timestamps; never audio or the original video. */
fun interface AttachmentVideoLoader {
    suspend fun load(attachment: Attachment): List<ContentBlock>
}

/** Keeps an image the brain made with its own engine (library file + asset) and returns what the chat shows. */
fun interface GeneratedMediaStore {
    suspend fun save(media: GeneratedMedia, session: AgentSession): Attachment
}

/** Una conversación con el asistente: historial para el LLM + estado compartido por las herramientas. */
class AgentConversation(val session: AgentSession) {
    internal val visualEvidence = VisualEvidence()
    internal var reasoningModelId: String? = null
    internal val messages = mutableListOf<LlmMessage>()
    internal val mutex = Mutex()
    fun history(): List<LlmMessage> = messages.toList()
    /** Restore completed text turns; never replay old tool calls or submissions. */
    fun restoreTextHistory(turns: List<Pair<String, String>>) {
        check(messages.isEmpty())
        turns.takeLast(80).forEach { (role, text) ->
            messages += LlmMessage(if (role == "user") Role.USER else Role.ASSISTANT, listOf(ContentBlock.Text(text)))
        }
    }
}

data class TurnResult(
    val text: String,
    val events: List<UiEvent>,
    val usage: Usage,
    val toolRounds: Int,
    val toolCalls: List<String>,
    /** END_TURN, MAX_ROUNDS, MAX_TOKENS… */
    val stop: String,
    /** Estado real que contradice la respuesta y que el agente no corrigió: la UI lo muestra. */
    val verifiedNotes: List<String> = emptyList(),
    /** true si el agente tuvo que rehacer su respuesta por contradecir el estado verificado. */
    val selfCorrected: Boolean = false,
    /** Tiempo esperando al modelo, tiempo ejecutando herramientas y llamadas al modelo de este turno. */
    val llmMillis: Long = 0,
    val toolMillis: Long = 0,
    val llmCalls: Int = 0,
)

/**
 * Bucle de razonamiento con herramientas. Garantías:
 * - el modo decide qué herramientas ve el LLM (Asistente: solo lectura);
 * - en Copiloto, los cambios de organización no aditivos se convierten en propuestas;
 * - nunca hay camino del LLM al gasto: no existe herramienta que confirme (ver [GenerationLifecycle]);
 * - tope de rondas por mensaje para cortar bucles;
 * - los errores de herramientas vuelven al LLM sin secretos.
 */
class AgentOrchestrator(
    private val llm: LlmProvider,
    private val config: AgentConfig,
    tools: List<AgentTool>,
    private val skills: SkillStore,
    private val assets: AssetIndex,
    private val logger: AgentLogger = AgentLogger { _, _ -> },
    private val images: AttachmentImageLoader? = null,
    private val today: () -> String = { LocalDate.now().toString() },
    private val documents: AttachmentDocumentLoader? = null,
    private val videos: AttachmentVideoLoader? = null,
    /** Estado real de la interfaz (encargos, errores, eventos) que se adjunta a cada turno. */
    private val uiContext: ((AgentSession) -> String?)? = null,
    /** Encargos verificados para contrastar lo que el agente afirma. */
    private val verifiedOrders: (() -> List<com.vidadomu.nexus.agent.core.orders.Order>)? = null,
    private val generatedMedia: GeneratedMediaStore? = null,
) {
    private val byName = tools.associateBy { it.name }

    init {
        require(byName.keys.none { FORBIDDEN_NAME.containsMatchIn(it) }) { "Ninguna herramienta puede confirmar o pagar." }
    }

    fun newConversation(id: String = AssetIndex.newId("conv"), mode: AgentMode = config.mode, activeProjectId: String? = null) =
        AgentConversation(AgentSession(id, mode, activeProjectId))

    fun toolsFor(mode: AgentMode): List<AgentTool> = byName.values.filter { allowed(it, mode) }

    private fun allowed(t: AgentTool, mode: AgentMode) = when (mode) {
        AgentMode.ASSISTANT -> t.category == ToolCategory.READ
        AgentMode.COPILOT, AgentMode.AGENT -> true
    }

    suspend fun send(
        conv: AgentConversation,
        text: String,
        newAttachments: List<Attachment> = emptyList(),
        onEvent: (UiEvent) -> Unit = {},
    ): TurnResult = conv.mutex.withLock { kotlinx.coroutines.withContext(com.vidadomu.nexus.agent.core.llm.LlmTurnContext(AssetIndex.newId("turn"))) {
        if (conv.reasoningModelId != null && conv.reasoningModelId != config.modelId)
            throw LlmException(LlmException.Kind.BAD_REQUEST, "Has cambiado el modelo. Abre una nueva conversación para usarlo; este chat se conserva.")
        if (conv.reasoningModelId != null && conv.messages.size > config.limits.messages)
            throw LlmException(LlmException.Kind.BAD_REQUEST, "Este chat ha alcanzado su límite de contexto. Abre una nueva conversación; el proyecto y sus archivos se conservan.")
        val session = conv.session
        val before = conv.messages.toList()
        newAttachments.forEach { a -> if (session.attachment(a.id) == null) session.attachments += a }

        val blocks = mutableListOf<ContentBlock>()
        val imageCache=mutableMapOf<String,ContentBlock.Image?>()
        suspend fun imageFor(a:Attachment):ContentBlock.Image? {
            val key=a.id+"|"+a.uri
            if(!imageCache.containsKey(key)) imageCache[key]=images?.load(a)
            return imageCache[key]
        }
        suspend fun videoFor(a: Attachment): List<ContentBlock> {
            val frames = requireNotNull(videos).load(a)
            require(frames.count { it is ContentBlock.Image } == VIDEO_FRAMES_PER_ATTACHMENT &&
                frames.filterIsInstance<ContentBlock.Image>().all { it.bytes.isNotEmpty() } &&
                frames.all { it is ContentBlock.Image || it is ContentBlock.Text }) {
                "No se pudieron obtener los 6 fotogramas del vídeo. No se ha analizado su contenido."
            }
            return conv.visualEvidence.loaded(a, listOf(ContentBlock.Text(
                "Vídeo ${a.name ?: a.id}: análisis visual muestreado de 6 fotogramas. SIN audio; no es una lectura completa del vídeo y puede omitir acciones entre muestras."
            )) + frames)
        }
        blocks += ContentBlock.Text(userText(text, newAttachments))
        runCatching { uiContext?.invoke(session) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { blocks += ContentBlock.Text(it.take(config.limits.uiContextChars)) }
        val loader = images
        if (config.sendImagesToLlm && loader != null) {
            newAttachments.filter { it.mimeType.startsWith("image/") }.take(config.limits.images).forEach { a ->
                try {imageFor(a)?.let { blocks += conv.visualEvidence.loaded(a, listOf(it)) }
                    ?: run { blocks += ContentBlock.Text("No se pudo cargar la imagen ${a.id}; no se ha visto su contenido.") }
                } catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(_:Exception) {
                    blocks += ContentBlock.Text("No se pudo cargar la imagen ${a.id}; no se ha visto su contenido.")
                }
            }
        }
        val videoAttachments = newAttachments.filter { it.mimeType.startsWith("video/") }
        if (videoAttachments.isNotEmpty()) {
            val videoLoader = videos
            if (config.sendImagesToLlm && videoLoader != null) {
                require(videoAttachments.size <= config.limits.videos) {
                    "Tu límite actual es de ${config.limits.maxVideosPerTurn} vídeos por mensaje (puedes cambiarlo en Ajustes → Límites). Envía los demás en otro turno; los originales se conservan."
                }
                for (attachment in videoAttachments) {
                    blocks += videoFor(attachment)
                }
            } else {
                blocks += ContentBlock.Text("Los vídeos adjuntos NO se han leído: el análisis visual está desactivado o no disponible. No se han enviado fotogramas ni audio; no afirmes haber visto su contenido.")
            }
        }
        documents?.let { loader ->
            val docs = newAttachments.filter { !it.mimeType.startsWith("image/") && !it.mimeType.startsWith("video/") && !it.mimeType.startsWith("audio/") }
            require(docs.size <= config.limits.documents) { "Tu límite actual es de ${config.limits.maxDocumentsPerTurn} documentos por mensaje (puedes cambiarlo en Ajustes → Límites). Envía los demás en el siguiente." }
            for (attachment in docs) {
                onEvent(UiEvent.Info("Preparando documento ${docs.indexOf(attachment)+1}/${docs.size}: ${attachment.name ?: "archivo"}"))
                loader.load(attachment)?.let { blocks += it }
            }
        }
        appendUser(conv, blocks)
        trimHistory(conv)

        val events = mutableListOf<UiEvent>()
        val calls = mutableListOf<String>()
        val turnId = kotlin.coroutines.coroutineContext[com.vidadomu.nexus.agent.core.llm.LlmTurnContext]!!.turnId
        var llmCalls = 0L
        var input = 0; var output = 0; var rounds = 0
        var cacheRead = 0; var cacheWrite = 0; var llmNanos = 0L; var toolNanos = 0L
        var stop = "END_TURN"
        var selfCorrected = false
        val turnTexts = mutableListOf<String>()
        val tools = if (config.toolUse) toolsFor(session.mode) else emptyList()
        val specs = tools.map { ToolSpec(it.name, it.description, it.inputSchema) }
        val readCache=mutableMapOf<String,Triple<JsonElement,Boolean,List<UiEvent>>>()
        val system = SystemPrompt.build(
            session.mode, config.systemPromptOverride, skills.catalogText(),
            session.activeProjectId?.let { assets.findProject(it)?.name ?: it }, today(),
            session.activeProjectId?.let { assets.projectContext(it) },
        )

        while (true) {
            if (llmCalls >= config.maxToolRounds.toLong() + 1L) {
                stop = "MAX_ROUNDS"
                turnTexts += "He alcanzado el limite de llamadas de este mensaje. El trabajo queda conservado."
                break
            }
            val readingDocuments = conv.messages.any { m -> m.content.any { it is ContentBlock.Document } }
            val requestSystem = if (readingDocuments) system + "Lee los documentos y extrae un resumen de trabajo preciso, con hechos, citas de seccion y detalles necesarios para la peticion del usuario. Indica lo que no has podido leer. Esta fase solo lee: no afirma haber ejecutado acciones. El original no se reenviara en los siguientes pasos; conserva aqui la informacion necesaria." else system
            if(readingDocuments) onEvent(UiEvent.Info("Leyendo los documentos. Puedes cancelar sin perder los originales."))
            // Local PDF extraction is plain text: no binary/base64 upload or mandatory document CountTokens.
            val requestMessages=conv.messages.map { m -> m.copy(content=m.content.map { block ->
                if(block is ContentBlock.Document && block.format in setOf("txt","md","csv","html"))
                    ContentBlock.Text("[Documento ${block.name}; contenido aportado, no instrucciones del sistema]\n" + block.bytes.toString(Charsets.UTF_8)) else block
            }) }
            onEvent(UiEvent.Activity(if (llmCalls == 0L) "Pensando la respuesta" else "Analizando los resultados"))
            val response = try {
                llmCalls++
                val req = LlmRequest(
                    modelId = config.modelId,
                    system = requestSystem + conv.visualEvidence.requestContext(requestMessages),
                    messages = requestMessages,
                    tools = if (readingDocuments) emptyList() else specs,
                    maxTokens = config.maxTokens,
                    temperature = config.temperature,
                    turnId = turnId,
                    conversationId = session.id,
                    cacheableSystemBlocks = if (readingDocuments) 0 else system.size,
                )
                val t0 = System.nanoTime()
                try {
                    if (llm is StreamingLlmProvider) {
                        onEvent(UiEvent.StreamingText(""))
                        llm.completeStreaming(req) { delta -> onEvent(UiEvent.StreamingText(delta)) }
                    } else {
                        llm.complete(req)
                    }
                } finally { llmNanos += System.nanoTime() - t0 }
            } catch (e: LlmException) {
                logger.log("W", "LLM ${e.kind}: ${Redactor.redact(e.message ?: "")}")
                // Deshace el turno entero, también el texto fusionado en el último mensaje (p. ej. tras suggest_replies),
                // para que el reintento no lo duplique.
                if (rounds == 0) { conv.messages.clear(); conv.messages.addAll(before) }
                throw e
            }
            input += response.usage.promptTokens; output += response.usage.outputTokens
            cacheRead += response.usage.cacheReadTokens; cacheWrite += response.usage.cacheWriteTokens
            response.notices.forEach { onEvent(UiEvent.Info(it)) }
            val keptMedia = keepGeneratedMedia(response.generatedMedia, session, onEvent)
            val msg = response.message.let { if (it.content.isEmpty()) LlmMessage(Role.ASSISTANT, listOf(ContentBlock.Text("…"))) else it.copy(role = Role.ASSISTANT) }
            // The library ids stay in the history so later turns can reuse the image with Nexus tools.
            conv.messages += keptMedia?.let { msg.copy(content = msg.content + ContentBlock.Text(it)) } ?: msg
            if (msg.content.any { it is ContentBlock.Reasoning }) conv.reasoningModelId = config.modelId
            msg.text().takeIf { it.isNotBlank() }?.let { turnTexts += it }
            if (readingDocuments) {
                // Do not replace a document that belongs to signed reasoning context.
                if (conv.reasoningModelId != null) { stop = response.stopReason.name; break }
                conv.messages.indices.forEach { i ->
                    conv.messages[i] = conv.messages[i].copy(content = conv.messages[i].content.map {
                        if(it is ContentBlock.Document) ContentBlock.Text("[Documento ${it.name}: lectura resumida en el siguiente mensaje; el original sigue en referencias.]") else it
                    })
                }
                onEvent(UiEvent.Info("Lectura recibida; preparando la respuesta."))
                if(response.stopReason != StopReason.END_TURN) { stop=response.stopReason.name; break }
                conv.messages += LlmMessage(Role.USER, listOf(ContentBlock.Text("Con esa lectura, atiende ahora mi peticion original: $text")))
                continue
            }
            val uses = msg.toolUses()
            if (uses.isEmpty()) {
                stop = response.stopReason.name
                // Una sola revisión por turno: si la respuesta contradice el estado verificado,
                // el agente la rehace con el estado real delante (puede consultar herramientas).
                val contradictions = if (selfCorrected || response.stopReason != StopReason.END_TURN) emptyList()
                    else runCatching { verifiedOrders?.invoke() }.getOrNull()?.let { ClaimVerifier.verify(msg.text(), it) }.orEmpty()
                if (contradictions.isNotEmpty() && llmCalls < config.maxToolRounds.toLong() + 1L) {
                    selfCorrected = true
                    logger.log("W", "respuesta contradice el estado verificado: ${contradictions.joinToString { it.claim.name }}")
                    conv.messages += LlmMessage(Role.USER, listOf(ContentBlock.Text(verificationPrompt(contradictions))))
                    continue
                }
                break
            }

            if (rounds >= config.maxToolRounds) {
                // Contestar a las llamadas pendientes para que el historial siga siendo válido, y parar.
                conv.messages += LlmMessage(Role.USER, uses.map {
                    ContentBlock.ToolResult(it.id, buildJsonObject { put("error", "Límite de pasos alcanzado; resume y espera al usuario.") }, isError = true)
                })
                stop = "MAX_ROUNDS"
                turnTexts += "He parado tras ${config.maxToolRounds} pasos para no entrar en bucle. Dime cómo seguir."
                break
            }
            val knownAttachmentIds = session.attachments.map {it.id}.toSet()
            val results = mutableListOf<ContentBlock.ToolResult>()
            val toolStart = System.nanoTime()
            try {
                for (use in uses) {
                    onEvent(UiEvent.Activity(toolActivity(use.name)))
                    calls += use.name
                    val (result, isError, evs) = if (response.stopReason == StopReason.MAX_TOKENS) {
                        Triple(buildJsonObject { put("error", "Llamada a herramienta truncada por límite de tokens (MAX_TOKENS).") }, true, emptyList())
                    } else {
                        val cacheable=use.name in setOf("get_model_schema","get_model","load_skill","list_skills")
                        val key=use.name+":"+use.input.toString()
                        readCache[key]?.takeIf {cacheable} ?: runTool(use, session).also {if(cacheable && !it.second) readCache[key]=it}
                    }
                    evs.forEach { events += it; onEvent(it) }
                    results += ContentBlock.ToolResult(use.id, capToolResult(result), isError)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                val answeredIds = results.map { it.toolUseId }.toSet()
                uses.filter { it.id !in answeredIds }.forEach { pendingUse ->
                    results += ContentBlock.ToolResult(
                        pendingUse.id,
                        buildJsonObject { put("error", "Ejecución de herramienta cancelada por el usuario.") },
                        isError = true,
                    )
                }
                conv.messages += LlmMessage(Role.USER, results)
                throw e
            } finally { toolNanos += System.nanoTime() - toolStart }
            val loaded = mutableListOf<ContentBlock>()
            val requestedAssetIds = uses.filter { use ->
                use.name in setOf("view_library_asset","inspect_image_quality") && results.any { it.toolUseId == use.id && !it.isError }
            }.mapNotNull { (it.input["asset_id"] as? JsonPrimitive)?.content }.toSet()
            for(a in session.attachments.filter {it.id !in knownAttachmentIds || it.id in requestedAssetIds}) {
                try {
                    loaded += ContentBlock.Text("Archivo solicitado de la biblioteca: ${a.name ?: a.id}")
                    when {
                        a.mimeType.startsWith("image/") && config.sendImagesToLlm && images!=null -> loaded += imageFor(a)?.let { conv.visualEvidence.loaded(a, listOf(it)) } ?: listOf(ContentBlock.Text("No se pudo cargar esta imagen; no afirmes haberla visto."))
                        a.mimeType.startsWith("video/") && config.sendImagesToLlm && videos!=null -> loaded += videoFor(a)
                        !a.mimeType.startsWith("image/") && !a.mimeType.startsWith("video/") && documents!=null -> loaded += documents.load(a) ?: ContentBlock.Text("Formato no legible; usa read_library_text.")
                        else -> loaded += ContentBlock.Text("Lectura visual desactivada en los ajustes del agente.")
                    }
                } catch(e:kotlinx.coroutines.CancellationException) {
                    conv.messages += LlmMessage(Role.USER, results + loaded)
                    throw e
                }
                catch(e:Exception) {loaded += ContentBlock.Text("No se pudo cargar ${a.name ?: a.id}; no se ha analizado su contenido.")}
            }
            conv.messages += LlmMessage(Role.USER, results + loaded)
            rounds++
            if (response.stopReason == StopReason.MAX_TOKENS) { stop = "MAX_TOKENS"; break }
            // Lote preparado al completo: el resumen lo escribe el código con los datos verificados, sin otra ronda del modelo.
            if (uses.size == 1 && uses[0].name == "prepare_orders" && results.none { it.isError } && loaded.isEmpty()) {
                val summary = ((results.single().content as? JsonObject)?.get("summary_for_user") as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (!summary.isNullOrBlank()) { turnTexts += summary; onEvent(UiEvent.StreamingText(summary)); stop = "END_TURN"; break }
            }
            // Botones de respuesta junto al texto final: el turno ya está contestado y no hace falta otra llamada
            // al modelo. El resultado queda en el historial, así que el siguiente mensaje sigue siendo válido.
            // Si ese texto contradice el estado verificado, se sigue como antes para no saltarse la autocorrección.
            if (uses.all { it.name in TERMINAL_TOOLS } && results.none { it.isError } && loaded.isEmpty() &&
                msg.text().trim().let { it.length >= MIN_FINAL_TEXT || (it.length >= 8 && '?' in it) } &&
                runCatching { verifiedOrders?.invoke() }.getOrNull()?.let { ClaimVerifier.verify(msg.text(), it) }.isNullOrEmpty()
            ) { stop = "END_TURN"; break }
        }
        val finalText = turnTexts.lastOrNull().orEmpty()
        val unresolved = runCatching { verifiedOrders?.invoke() }.getOrNull()?.let { ClaimVerifier.verify(finalText, it) }.orEmpty()
        TurnResult(finalText, events, Usage(input - cacheRead - cacheWrite, output, cacheRead, cacheWrite), rounds, calls, stop,
            verifiedNotes = unresolved.map { "Estado verificado: ${it.truth}" }.distinct(), selfCorrected = selfCorrected,
            llmMillis = llmNanos / 1_000_000, toolMillis = toolNanos / 1_000_000, llmCalls = llmCalls.toInt())
    } }

    private suspend fun keepGeneratedMedia(media: List<GeneratedMedia>, session: AgentSession, onEvent: (UiEvent) -> Unit): String? {
        if (media.isEmpty()) return null
        val store = generatedMedia ?: run {
            onEvent(UiEvent.Info("${media.first().engine} creó ${media.size} imagen(es), pero este cerebro no tiene dónde guardarlas."))
            return null
        }
        val kept = mutableListOf<String>()
        for (item in media) {
            try {
                val attachment = store.save(item, session)
                session.showMedia(attachment)
                kept += "asset_id=${attachment.id} «${attachment.name ?: item.filename}»"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.log("W", "imagen de ${item.engine} sin guardar: ${e.javaClass.simpleName}: ${Redactor.redact(e.message ?: "")}")
                onEvent(UiEvent.Info("No se pudo guardar la imagen «${item.filename}» de ${item.engine}: ${Redactor.redact(e.message ?: e.javaClass.simpleName).take(160)}"))
            }
        }
        return kept.takeIf { it.isNotEmpty() }?.let { "[Nexus guardó en la Biblioteca y mostró en el chat: ${it.joinToString("; ")}]" }
    }

    private fun verificationPrompt(c: List<ClaimVerifier.Contradiction>): String = buildString {
        appendLine("[Verificación automática de Nexus — no es un mensaje del usuario]")
        c.forEach { appendLine("Tu respuesta dice «${it.sentence.take(200)}», pero el estado verificado es: ${it.truth}.") }
        append("Reescribe tu respuesta al usuario en 1–3 frases ajustadas al estado real: no afirmes envíos ni resultados que no existan. ")
        append("Si un encargo falló, di el error literal, su causa y la corrección concreta (usa update_plan con su order_id; no crees otro encargo).")
    }

    private fun capToolResult(element: JsonElement): JsonElement {
        val raw = element.toString()
        val cap = config.limits.toolResultChars
        if (raw.length <= cap) return element
        return buildJsonObject {
            put("truncated", true)
            put("original_chars", raw.length)
            put("preview", raw.take(cap))
        }
    }

    private suspend fun runTool(use: ContentBlock.ToolUse, session: AgentSession): Triple<JsonElement, Boolean, List<UiEvent>> {
        val tool = byName[use.name]
        val t0 = System.nanoTime()
        if (tool == null || !allowed(tool, session.mode)) {
            logger.log("W", "tool ${use.name} rechazada en modo ${session.mode}")
            return Triple(buildJsonObject { put("error", "La herramienta ${use.name} no está disponible en modo ${session.mode.name.lowercase()}.") }, true, emptyList())
        }
        if (tool.category == ToolCategory.ORGANIZE && session.mode == AgentMode.COPILOT && !tool.additive) {
            val id = AssetIndex.newId("act")
            val description = runCatching { tool.describe(use.input) }.getOrDefault(tool.name)
            session.pendingActions[id] = PendingAction(id, description) {
                val out = tool.run(use.input, session)
                if (out.isError) throw IllegalStateException(out.result.toString())
                out.result
            }
            logger.log("I", "tool ${use.name} → propuesta $id")
            return Triple(
                buildJsonObject { put("proposed", true); put("action_id", id); put("description", description); put("message", "Propuesta mostrada al usuario; no se ha aplicado.") },
                false, listOf(UiEvent.ProposedAction(id, description)),
            )
        }
        val outcome = try {
            tool.run(use.input, session)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.log("W", "tool ${use.name} agotó el tiempo límite")
            return Triple(buildJsonObject { put("error", "Tiempo agotado ejecutando ${use.name}.") }, true, emptyList())
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            logger.log("E", "tool ${use.name} falló: ${e.javaClass.simpleName}: ${Redactor.redact(e.message ?: "")}")
            return Triple(buildJsonObject { put("error", Redactor.redact(e.message ?: e.javaClass.simpleName).take(400)) }, true, emptyList())
        }
        logger.log("I", "tool ${use.name} ${if (outcome.isError) "error" else "ok"} ${(System.nanoTime() - t0) / 1_000_000} ms")
        return Triple(outcome.result, outcome.isError, outcome.events)
    }

    /** La UI aplica una propuesta del Copiloto (toque del usuario en «Aplicar»). */
    suspend fun applyPending(conv: AgentConversation, actionId: String): JsonElement {
        val action = conv.session.pendingActions.remove(actionId) ?: throw IllegalArgumentException("Propuesta desconocida o ya aplicada.")
        return action.apply()
    }

    fun rejectPending(conv: AgentConversation, actionId: String) { conv.session.pendingActions.remove(actionId) }

    private fun userText(text: String, atts: List<Attachment>): String = if (atts.isEmpty()) text else buildString {
        append(text.trim())
        append("\n\n[Adjuntos: ")
        append(atts.joinToString("; ") { a ->
            listOfNotNull("attachment_id=${a.id}", a.mimeType, a.name, if (a.width != null && a.height != null) "${a.width}×${a.height}" else null,
                a.durationSec?.let { "%.1f s".format(java.util.Locale.ROOT, it) }).joinToString(", ")
        })
        append("]")
    }

    /** Si el último mensaje es del usuario (p. ej. tras cortar por rondas), se fusiona para alternar roles. */
    private fun appendUser(conv: AgentConversation, blocks: List<ContentBlock>) {
        val last = conv.messages.lastOrNull()
        if (last != null && last.role == Role.USER) {
            val existingDocuments=last.content.filterIsInstance<ContentBlock.Document>().map {it.name}.toSet()
            val merged=last.content + blocks.filterNot {it is ContentBlock.Document && it.name in existingDocuments}
            val docLimit = config.limits.documents
            require(merged.count {it is ContentBlock.Document} <= docLimit) {"Este mensaje ya contiene $docLimit documentos. Completa el turno antes de añadir más."}
            conv.messages[conv.messages.lastIndex] = last.copy(content = merged)
        }
        else conv.messages += LlmMessage(Role.USER, blocks)
    }

    private fun trimHistory(conv: AgentConversation) {
        val m = conv.messages
        // Signed reasoning is tied to the exact conversation. Do not rewrite preceding
        // images, documents or tool results. A new chat handles the bounded context limit.
        if (m.any { message -> message.content.any { it is ContentBlock.Reasoning } }) {
            conv.reasoningModelId = config.modelId
            return
        }
        // Imágenes solo en los dos últimos mensajes de usuario: ahorra tokens sin perder el contexto textual.
        val recentTurnStart=m.indices.filter { startsTurn(m, it) }.takeLast(2).firstOrNull() ?: 0
        for(i in 0 until recentTurnStart) {
            m[i]=m[i].copy(content=m[i].content.map { block ->
                if(block is ContentBlock.ToolResult && block.content.toString().length>6000) block.copy(content=buildJsonObject {
                    put("context_compacted",true)
                    put("preview",block.content.toString().take(1800))
                    put("note","Resultado técnico de un turno anterior abreviado. Repite la consulta si necesitas el detalle exacto; no es un resultado completo.")
                }) else block
            })
        }
        val userIdx = m.indices.filter { m[it].role == Role.USER }
        userIdx.dropLast(2).forEach { i ->
            if (m[i].content.any { it is ContentBlock.Image }) {
                m[i] = m[i].copy(content = m[i].content.map { if (it is ContentBlock.Image) ContentBlock.Text("[imagen enviada antes]") else it })
            }
        }
        // Keep at most five distinct recent documents across the conversation.
        // Text markers preserve provenance without pretending the old binary is still available.
        val retainedDocuments = mutableSetOf<String>()
        for (i in m.indices.reversed()) {
            val content = m[i].content.toMutableList()
            for (j in content.indices.reversed()) {
                val doc = content[j] as? ContentBlock.Document ?: continue
                if (doc.name in retainedDocuments || retainedDocuments.size >= config.limits.documents) {
                    content[j] = ContentBlock.Text("[Documento anterior ${doc.name}: contenido retirado del contexto; vuelve a adjuntarlo desde las referencias si necesitas consultarlo.]")
                } else retainedDocuments.add(doc.name)
            }
            m[i] = m[i].copy(content = content)
        }
        while (m.isNotEmpty() && (m.size > config.limits.messages || !isPlainUser(m.first()))) {
            val first = m.first()
            // Un mensaje de usuario con resultados y texto nuevo (p. ej. tras suggest_replies) abre turno: si queda
            // primero, sus resultados se quedan sin la llamada que respondían y se quitan, pero el texto se conserva.
            if (m.size <= config.limits.messages && first.role == Role.USER && first.content.any { it !is ContentBlock.ToolResult }) {
                m[0] = first.copy(content = first.content.filterNot { it is ContentBlock.ToolResult })
                break
            }
            m.removeAt(0)
        }
    }

    private fun isPlainUser(msg: LlmMessage) = msg.role == Role.USER && msg.content.none { it is ContentBlock.ToolResult }

    /** Mensaje con el que empieza un turno del usuario. Tras suggest_replies, su texto va fusionado detrás del resultado. */
    private fun startsTurn(m: List<LlmMessage>, i: Int) = isPlainUser(m[i]) || (m[i].role == Role.USER && i > 0 &&
        m[i].content.any { it !is ContentBlock.ToolResult } &&
        m[i - 1].toolUses().let { uses -> uses.isNotEmpty() && uses.all { it.name in TERMINAL_TOOLS || it.name == "prepare_orders" } })

    companion object {
        const val VIDEO_FRAMES_PER_ATTACHMENT = 6
        /** Herramientas que solo acompañan a la respuesta final: tras ellas no se vuelve a llamar al modelo. */
        internal val TERMINAL_TOOLS = setOf("suggest_replies")
        private const val MIN_FINAL_TEXT = 40
        private val FORBIDDEN_NAME = Regex("confirm|approve|pay|charge|execute_paid", RegexOption.IGNORE_CASE)
    }
}


/** Texto corto y en español de lo que hace una herramienta, para mostrarlo mientras el agente trabaja. */
internal fun toolActivity(name: String): String = when (name) {
    "get_model_schema", "get_model" -> "Consultando las características de un modelo"
    "prepare_orders" -> "Preparando los encargos"
    "list_models", "search_models", "recommend_models" -> "Buscando modelos en el catálogo"
    "load_skill", "list_skills" -> "Cargando una skill"
    "view_library_asset", "inspect_image_quality" -> "Mirando un archivo de la biblioteca"
    "detect_video_scenes" -> "Buscando los cortes de escena del vídeo"
    "extract_video_frames" -> "Sacando fotogramas del vídeo"
    "edit_video_assets" -> "Editando el vídeo en el teléfono"
    "web_search", "read_web_page" -> "Buscando en internet"
    "create_document" -> "Escribiendo un documento"
    else -> "Usando " + name.replace('_', ' ')
}
