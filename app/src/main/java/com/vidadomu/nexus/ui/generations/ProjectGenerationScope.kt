package com.vidadomu.nexus.ui.generations

import com.vidadomu.nexus.agent.core.assets.IndexData
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.OutputFiles
import com.vidadomu.nexus.cut.projectCutHub

/** The project hub and its generation list share current material assignments, including shared links. */
internal fun projectGenerationJobs(index: IndexData, jobs: List<GenerationJob>, projectId: String): List<GenerationJob> {
    val project = projectCutHub(index, emptyList()).projects.firstOrNull { it.id == projectId } ?: return emptyList()
    val materialIds = project.material.mapTo(HashSet()) { it.id }
    val records = index.generations.mapNotNull { record -> record.nexusJobId?.let { it to record } }.toMap()
    val assetsByUri = index.assets.groupBy { it.uri }
    val assetsById = index.assets.associateBy { it.id }
    val assetsByGeneration = index.assets.filter { it.generationId != null }.groupBy { it.generationId }
    val localUriConflicts = OutputFiles.conflictingLocalUris(jobs.flatMap { it.outputs })
    return jobs.mapNotNull { job ->
        val record = records[job.id]
        val mappedOutputs = job.outputs.associate { output ->
            output.id to (listOfNotNull(OutputFiles.trustedLocalUri(output, localUriConflicts), output.url).flatMap { assetsByUri[it].orEmpty() } +
                listOfNotNull(assetsById[output.id])).distinctBy { it.id }
        }
        val indexed = (mappedOutputs.values.flatten() + record?.let {
            it.outputAssetIds.mapNotNull(assetsById::get) + assetsByGeneration[it.id].orEmpty()
        }.orEmpty()).distinctBy { it.id }
        if (indexed.isEmpty()) {
            // With no indexed output, only the saved record (or a legacy DB binding) establishes ownership.
            job.takeIf { (record?.projectId ?: job.parameters["__project_id"].takeIf { record == null }) == projectId }
        } else if (indexed.none { it.id in materialIds }) null
        else job.copy(outputs = job.outputs.filter { output -> mappedOutputs[output.id].orEmpty().any { it.id in materialIds } })
        // Relocated legacy outputs without an unambiguous URI/identity stay as a job with no selectable result.
    }
}
