package com.vidadomu.nexus.assistant

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class SavedAssistantChat(val items: List<ChatItem>, val plans: Map<String, PlanUi>, val attachments: List<com.vidadomu.nexus.agent.core.tools.Attachment> = emptyList(), val sessionAttachments: List<com.vidadomu.nexus.agent.core.tools.Attachment> = emptyList(),
    /** Encargos de la conversación (1.9.7+). Ausente en chats antiguos: se migran al abrirlos. */
    val orders: com.vidadomu.nexus.agent.core.orders.OrderBookState? = null,
    val projectId: String? = null, val conversationId: String? = null, val updatedAt: Long = 0)

/** Private, atomic snapshot. Restoring history never repeats a provider submission. */
internal data class AssistantChatEntry(val id: String, val title: String, val projectId: String? = null, val updatedAt: Long = 0, val preview: String = "",
    val searchText: String = "")

internal class AssistantChatStore(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "assistant/chat-v1.json").apply { parentFile?.mkdirs() })
    private val json = Json { ignoreUnknownKeys = true }
    private var previous: SavedAssistantChat? = null
    private val lock = kotlinx.coroutines.sync.Mutex()

    fun load(): SavedAssistantChat? = runCatching {
        json.decodeFromString<SavedAssistantChat>(file.openRead().bufferedReader().use { it.readText() })
    }.getOrNull() ?: loadLegacy()

    private fun loadLegacy(): SavedAssistantChat? = runCatching {
        val legacy = org.json.JSONObject(File(context.filesDir, "assistant/chat.json").readText())
        val items = legacy.getJSONArray("items")
        val restored = mutableListOf<ChatItem>()
        for(index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            val id = item.optString("id", "legacy_$index")
            when(item.optString("kind")) {
                "user" -> restored += ChatItem.User(id,item.optString("text"), json.decodeFromString(item.optJSONArray("attachments")?.toString() ?: "[]"))
                "assistant" -> restored += ChatItem.Assistant(id,item.optString("text"), emptyList(), "END_TURN")
                "notice" -> restored += ChatItem.Notice(id,item.optString("text"),item.optBoolean("error"))
                "plan" -> restored += ChatItem.PlanCard(id,item.optString("planId"))
            }
        }
        val plans = legacy.optJSONArray("plans") ?: org.json.JSONArray()
        val restoredPlans = mutableMapOf<String,PlanUi>()
        for(index in 0 until plans.length()) {
            val item = plans.getJSONObject(index)
            val plan = runCatching { json.decodeFromString<com.vidadomu.nexus.agent.core.spend.GenerationPlan>(item.getJSONObject("plan").toString()) }.getOrNull()
            if(plan == null) {
                restored += ChatItem.Notice("legacy_plan_error_$index", "No se pudo reconstruir un encargo antiguo. El archivo original se conserva.", true)
                continue
            }
            restoredPlans[plan.id] = PlanUi(plan, true,
                runCatching { com.vidadomu.nexus.agent.core.spend.PlanState.valueOf(item.optString("state")) }.getOrDefault(com.vidadomu.nexus.agent.core.spend.PlanState.UNCERTAIN),
                jobId = item.optString("jobId").takeIf { it.isNotBlank() && it != "null" },
                jobStatus = item.optString("jobStatus").takeIf { it.isNotBlank() && it != "null" })
        }
        SavedAssistantChat(restored, restoredPlans, sessionAttachments = json.decodeFromString(legacy.optJSONArray("attachments")?.toString() ?: "[]"))
    }.getOrNull()

    private fun knownAttachments(state: AssistantUiState) = (state.items.filterIsInstance<ChatItem.User>().flatMap { it.attachments } + state.pendingAttachments).distinctBy { it.id }

    suspend fun archive(state: AssistantUiState, orders: com.vidadomu.nexus.agent.core.orders.OrderBookState? = null) = lock.withLock { withContext(Dispatchers.IO) {
        val folder = File(context.filesDir, "assistant/conversations").apply { mkdirs() }
        val id = state.conversationId
        val archived = AtomicFile(File(folder, "$id.json"))
        val stream = archived.startWrite()
        try {
            stream.write(json.encodeToString(SavedAssistantChat(state.items,state.plans,state.pendingAttachments, knownAttachments(state), orders, state.activeProjectId, state.conversationId, System.currentTimeMillis())).toByteArray(Charsets.UTF_8))
            archived.finishWrite(stream)
        } catch(error: Exception) { archived.failWrite(stream); throw error }
    } }

    /**
     * Metadata index of saved conversations. Only files whose size or modification time changed are decoded; the rest
     * come from memory or from a small index file. Listing used to decode every full chat on every call (on the main
     * thread, inside sort comparators): the cause of the start-up ANRs on the Pixel 8 Pro (25/10/2026).
     * Must be called off the main thread.
     */
    @Serializable private data class IndexedConversation(val modified: Long, val length: Long, val title: String,
        val projectId: String? = null, val updatedAt: Long = 0, val preview: String = "", val searchText: String = "")
    @Serializable private data class ConversationIndexFile(val version: Int = 1, val entries: Map<String, IndexedConversation> = emptyMap())
    private val indexFile = File(context.filesDir, "assistant/conversations-index-v1.json")
    private val index = java.util.concurrent.ConcurrentHashMap<String, IndexedConversation>()
    @Volatile private var indexLoaded = false

    private fun indexed(file: File, chat: SavedAssistantChat): IndexedConversation {
        val metadata=conversationMetadata(chat.items)
        return IndexedConversation(file.lastModified(),file.length(),metadata.title,chat.projectId,
            chat.updatedAt.takeIf {it>0} ?: file.lastModified(),metadata.preview,metadata.searchText)
    }

    @Synchronized
    fun conversations(projectId: String? = null): List<AssistantChatEntry> {
        if (!indexLoaded) {
            runCatching { json.decodeFromString<ConversationIndexFile>(indexFile.readText()) }.getOrNull()
                ?.takeIf {it.version>=3}?.let { index.putAll(it.entries) }
            indexLoaded = true
        }
        val files = File(context.filesDir,"assistant/conversations").listFiles().orEmpty().filter { it.extension == "json" }
        var changed = index.keys.retainAll(files.map { it.nameWithoutExtension }.toSet())
        files.forEach { file ->
            val name = file.nameWithoutExtension
            val known = index[name]
            if (known != null && known.modified == file.lastModified() && known.length == file.length()) return@forEach
            val chat = runCatching { json.decodeFromString<SavedAssistantChat>(file.readText()) }.getOrNull()
            if (chat == null) index.remove(name) else index[name] = indexed(file, chat)
            changed = true
        }
        if (changed) runCatching {
            val target = AtomicFile(indexFile); val out = target.startWrite()
            try { out.write(json.encodeToString(ConversationIndexFile(version = 3, entries = HashMap(index))).toByteArray(Charsets.UTF_8)); target.finishWrite(out) }
            catch (e: Exception) { target.failWrite(out); throw e }
        }
        return files.sortedByDescending { it.lastModified() }.mapNotNull { file ->
            index[file.nameWithoutExtension]?.let { AssistantChatEntry(file.nameWithoutExtension, it.title, it.projectId, it.updatedAt, it.preview,it.searchText) }
        }.filter { projectId == null || it.projectId == projectId }
    }

    fun conversation(id: String): SavedAssistantChat? {
        if(!Regex("[A-Za-z0-9_-]+").matches(id)) return null
        return runCatching { json.decodeFromString<SavedAssistantChat>(File(context.filesDir,"assistant/conversations/$id.json").readText()) }.getOrNull()
    }

    fun lastConversation(projectId: String): String? = context.getSharedPreferences("nexus_project_conversations",0).getString(projectId,null)
        ?.takeIf { conversation(it)?.projectId==projectId } ?: conversations(projectId).firstOrNull()?.id

    private fun writeConversation(id: String, snapshot: SavedAssistantChat) {
        require(Regex("[A-Za-z0-9_-]+").matches(id))
        val target=AtomicFile(File(context.filesDir,"assistant/conversations/$id.json").apply {parentFile?.mkdirs()})
        val out=target.startWrite()
        try {out.write(json.encodeToString(snapshot).toByteArray(Charsets.UTF_8));target.finishWrite(out)}
        catch(e: Exception) {target.failWrite(out);throw e}
    }

    suspend fun migrate(defaultProject: String) = lock.withLock {withContext(Dispatchers.IO) {
        val prefs=context.getSharedPreferences("nexus_project_conversations",0)
        if(prefs.getBoolean("migration_v2",false)) return@withContext
        val root=File(context.filesDir,"assistant")
        val backup=File(root,"migration-v2-originals").apply {mkdirs()}
        listOf("chat-v1.json","chat.json").forEach {name -> File(root,name).takeIf {it.exists()}?.let {
            val copy=File(backup,name);if(!copy.exists()) it.copyTo(copy)
        }}
        File(root,"conversations").listFiles().orEmpty().filter {it.extension=="json"}.forEach {source ->
            val copy=File(backup,"conversations/"+source.name).apply {parentFile?.mkdirs()}
            if(!copy.exists()) source.copyTo(copy)
            val chat=runCatching {json.decodeFromString<SavedAssistantChat>(source.readText())}.getOrNull() ?: return@forEach
            if(chat.projectId==null) writeConversation(source.nameWithoutExtension,chat.copy(projectId=defaultProject,conversationId=source.nameWithoutExtension,updatedAt=source.lastModified()))
        }
        prefs.edit().putBoolean("migration_v2",true).commit()
    }}

    suspend fun save(state: AssistantUiState, orders: com.vidadomu.nexus.agent.core.orders.OrderBookState? = null) = lock.withLock { withContext(Dispatchers.IO) {
        val items = state.items
        val ids = items.filterIsInstance<ChatItem.PlanCard>().map { it.planId }.toSet()
        val snapshot = SavedAssistantChat(items, state.plans.filterKeys { it in ids }, state.pendingAttachments, knownAttachments(state), orders, state.activeProjectId, state.conversationId)
        if (snapshot == previous) return@withContext
        val stream = file.startWrite()
        try {
            stream.write(json.encodeToString(snapshot).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
            writeConversation(state.conversationId,snapshot.copy(updatedAt=System.currentTimeMillis()))
            context.getSharedPreferences("nexus_project_conversations",0).edit().putString(state.activeProjectId ?: "general",state.conversationId).apply()
            previous = snapshot
        } catch (error: Exception) { file.failWrite(stream); throw error }
    } }
}
