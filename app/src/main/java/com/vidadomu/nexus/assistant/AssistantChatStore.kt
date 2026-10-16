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
    val orders: com.vidadomu.nexus.agent.core.orders.OrderBookState? = null)

/** Private, atomic snapshot. Restoring history never repeats a provider submission. */
internal data class AssistantChatEntry(val id: String, val title: String)

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
        if(state.items.isEmpty()) return@withContext
        val folder = File(context.filesDir, "assistant/conversations").apply { mkdirs() }
        val id = state.items.first().id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val archived = AtomicFile(File(folder, "$id.json"))
        val stream = archived.startWrite()
        try {
            stream.write(json.encodeToString(SavedAssistantChat(state.items,state.plans,state.pendingAttachments, knownAttachments(state), orders)).toByteArray(Charsets.UTF_8))
            archived.finishWrite(stream)
        } catch(error: Exception) { archived.failWrite(stream); throw error }
    } }

    fun conversations(): List<AssistantChatEntry> = File(context.filesDir,"assistant/conversations").listFiles().orEmpty()
        .filter { it.extension == "json" }.sortedByDescending { it.lastModified() }.mapNotNull { entry ->
            runCatching {
                val chat = json.decodeFromString<SavedAssistantChat>(entry.readText())
                AssistantChatEntry(entry.nameWithoutExtension,chat.items.filterIsInstance<ChatItem.User>().firstOrNull()?.text?.take(70) ?: "Conversación")
            }.getOrNull()
        }

    fun conversation(id: String): SavedAssistantChat? {
        if(!Regex("[A-Za-z0-9_-]+").matches(id)) return null
        return runCatching { json.decodeFromString<SavedAssistantChat>(File(context.filesDir,"assistant/conversations/$id.json").readText()) }.getOrNull()
    }

    suspend fun save(state: AssistantUiState, orders: com.vidadomu.nexus.agent.core.orders.OrderBookState? = null) = lock.withLock { withContext(Dispatchers.IO) {
        val items = state.items
        val ids = items.filterIsInstance<ChatItem.PlanCard>().map { it.planId }.toSet()
        val snapshot = SavedAssistantChat(items, state.plans.filterKeys { it in ids }, state.pendingAttachments, knownAttachments(state), orders)
        if (snapshot == previous) return@withContext
        val stream = file.startWrite()
        try {
            stream.write(json.encodeToString(snapshot).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
            previous = snapshot
        } catch (error: Exception) { file.failWrite(stream) }
    } }
}
