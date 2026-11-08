package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.agent.core.assets.StoryGenerationContext
import com.vidadomu.nexus.core.model.GenerationJob
import kotlinx.serialization.json.Json

/** History supplies its own origin. An unrelated current draft is never evidence of provenance. */
internal fun historyStoryOrigin(job: GenerationJob): StoryGenerationContext? {
    val raw = job.parameters["__story_context"] ?: return null
    return runCatching {
        Json { ignoreUnknownKeys = true }.decodeFromString(StoryGenerationContext.serializer(), raw)
    }.getOrNull()?.takeIf { origin ->
        origin.projectId.isNotBlank() && origin.shotId.isNotBlank() &&
            (job.parameters["__project_id"] == null || job.parameters["__project_id"] == origin.projectId)
    }
}
