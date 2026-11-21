package com.vidadomu.nexus.ui.library

import android.content.Context
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.DeletionPlan
import com.vidadomu.nexus.agent.core.assets.DeletionTarget
import com.vidadomu.nexus.agent.core.assets.LibraryFileDeletion
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.core.model.GenerationOutput
import com.vidadomu.nexus.core.model.OutputFiles
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What «Eliminar» erased on this device. Never synced: results stay out of the Library and out of the
 * Alexia download queue, and sync does not fetch those bytes again. Other devices are not touched.
 */
object LibraryDeletions {
    private const val PREFS = "library_deletions_v1"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, 0)
    fun tokens(context: Context): Set<String> = prefs(context).getStringSet("tokens", emptySet()).orEmpty().toSet()
    fun blobHashes(context: Context): Set<String> = prefs(context).getStringSet("hashes", emptySet()).orEmpty().toSet()

    @Synchronized private fun record(context: Context, tokens: Set<String>, hashes: Set<String>) {
        prefs(context).edit().putStringSet("tokens", tokens(context) + tokens).putStringSet("hashes", blobHashes(context) + hashes).commit()
    }

    private fun deletion(app: NexusApplication) = LibraryFileDeletion(app.filesDir)

    /** [tokens]: generated results each tile stands for, so they stay out after the erase. */
    class Erase(val plan: DeletionPlan, val tokens: Map<String, Set<String>>)

    suspend fun plan(app: NexusApplication, targets: List<DeletionTarget>): Erase = withContext(Dispatchers.IO) {
        val outputs = app.generationRepository.allJobs.value.flatMap { it.outputs }
        // A tile is its own result, or, for an indexed file, every result saved as that same file.
        fun owns(target: DeletionTarget, output: GenerationOutput) = OutputFiles.token(output) == target.key ||
            (target.key.startsWith("asset:") && (output.localUri in target.uris || output.url in target.uris))
        val owned = targets.associate { t -> t.key to outputs.filter { owns(t, it) } }
        val ownedIds = owned.values.flatten().map { it.jobId + "\n" + it.id }.toSet()
        val inUse = outputs.filter { it.jobId + "\n" + it.id !in ownedIds }.mapNotNull { it.localUri?.takeIf(String::isNotBlank) }
        val expanded = targets.map { t -> t.copy(uris = t.uris + owned.getValue(t.key).mapNotNull { it.localUri?.takeIf(String::isNotBlank) }) }
        val plan = deletion(app).plan(AssistantRuntime.get(app).assets.snapshot(), expanded,
            File(app.filesDir, "assistant/conversations"), File(app.filesDir, "cut/projects"), inUse)
        Erase(plan, owned.mapValues { (_, list) -> list.map(OutputFiles::token).toSet() })
    }

    /** Returns how many private files could not be erased (they stay, still out of the Library). */
    suspend fun erase(app: NexusApplication, erase: Erase): Int = withContext(Dispatchers.IO) {
        val plan = erase.plan
        // Remembered before erasing: a sync that runs meanwhile already knows not to fetch them back.
        record(app, plan.removable.flatMap { erase.tokens[it.key].orEmpty() }.toSet(), plan.hashes)
        AssistantRuntime.get(app).assets.deleteLibraryAssets(plan.removable.flatMap { it.assetIds }.toSet())
        plan.files.size - deletion(app).delete(plan.files)
    }
}
