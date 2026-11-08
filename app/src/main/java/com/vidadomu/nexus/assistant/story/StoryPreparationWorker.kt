package com.vidadomu.nexus.assistant.story

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.storyCommand
import com.vidadomu.nexus.assistant.AssistantRuntime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Durable local preparation only. No LLM, upload, generation, subscription or spending entry point. */
class StoryPreparationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val project = inputData.getString("project_id") ?: return Result.failure()
        val app = applicationContext as NexusApplication
        app.ready.await()
        val index = AssistantRuntime.get(app).assets
        if (index.findProject(project)?.archived != false) return Result.success()
        val result = index.storyCommand(project, buildJsonObject {
            put("action", "run_preparation"); put("operation_id", "worker:$id")
        }, "worker")
        return if (result["ok"].toString() == "false") Result.failure() else Result.success()
    }

    companion object {
        fun enqueue(context: Context, projectId: String) {
            val work = OneTimeWorkRequestBuilder<StoryPreparationWorker>()
                .setInputData(workDataOf("project_id" to projectId)).addTag("nexus-story-preparation").build()
            WorkManager.getInstance(context).enqueueUniqueWork("story-preparation:$projectId", ExistingWorkPolicy.KEEP, work)
        }
    }
}
