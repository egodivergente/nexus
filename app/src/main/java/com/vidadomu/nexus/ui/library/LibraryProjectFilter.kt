package com.vidadomu.nexus.ui.library

import com.vidadomu.nexus.agent.core.assets.IndexData
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationOutput

internal const val LIBRARY_UNASSIGNED_PROJECT="__unassigned__"
internal fun matchesLibraryProjects(selected: Set<String>, projects: Set<String>) = selected.isEmpty() ||
    if(projects.isEmpty()) LIBRARY_UNASSIGNED_PROJECT in selected else projects.any {it in selected}

/** Explicit file assignments take precedence; legacy jobs use their existing generation record. */
internal class LibraryProjectIndex(index: IndexData) {
    private val jobs=index.generations.mapNotNull {r->r.nexusJobId?.let {it to r.projectId}}.toMap()
    private val files=index.assets.groupBy {it.uri}
    fun projects(job: GenerationJob, output: GenerationOutput? = null): Set<String> {
        val assigned=output?.let {o->listOfNotNull(o.localUri,o.url).flatMap {files[it].orEmpty()}}
        if(!assigned.isNullOrEmpty()) return assigned.mapNotNull {it.projectId}.toSet()
        return setOfNotNull(job.parameters["__project_id"] ?: jobs[job.id])
    }
    fun matches(selected: Set<String>, job: GenerationJob, output: GenerationOutput? = null) = matchesLibraryProjects(selected,projects(job,output))
    fun matches(selected: Set<String>, asset: Asset) = matchesLibraryProjects(selected,setOfNotNull(asset.projectId))
}
