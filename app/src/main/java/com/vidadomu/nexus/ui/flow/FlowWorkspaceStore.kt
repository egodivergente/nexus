package com.vidadomu.nexus.ui.flow

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class FlowWorkspaceSnapshot(
    val drafts: List<FlowStudioDraft> = listOf(FlowStudioDraft()),
    val references: List<FlowStudioReference> = emptyList()
)

/** Private device draft, scoped to the Nexus project. Never contains account credentials. */
internal class FlowWorkspaceStore(context: Context, projectId: String?) {
    private val prefs = context.getSharedPreferences("flow_workspace_v1", Context.MODE_PRIVATE)
    private val key = projectId ?: "default"
    private var encoded = prefs.getString(key, null)
    fun read(): FlowWorkspaceSnapshot = runCatching {
        encoded?.let { Json.decodeFromString<FlowWorkspaceSnapshot>(it) }
    }.getOrNull()?.let { it.copy(drafts = it.drafts.distinctBy { draft -> draft.id }.take(FlowStudioPlan.MAX_DRAFTS)
        .ifEmpty { listOf(FlowStudioDraft()) }, references = it.references.distinctBy { ref -> ref.uri }.take(FlowStudioPlan.MAX_REFERENCES)) }
        ?: FlowWorkspaceSnapshot()
    fun write(drafts: List<FlowStudioDraft>, references: List<FlowStudioReference>) {
        val next = Json.encodeToString(FlowWorkspaceSnapshot(drafts, references))
        if(next != encoded) { encoded = next; prefs.edit().putString(key, next).apply() }
    }
}
