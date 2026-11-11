package com.vidadomu.nexus.ui.generations

import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.assets.GenerationRecord
import com.vidadomu.nexus.agent.core.assets.IndexData
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.agent.core.assets.StoryLink
import com.vidadomu.nexus.agent.core.assets.StoryState
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationOutput
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectGenerationScopeTest {
    private val projects = listOf(Project("a", "A", createdAt = 1), Project("b", "B", createdAt = 1))
    private fun asset(id: String, owner: String?, generation: String = "record") = Asset(id, "file:///$id.mp4", AssetType.VIDEO,
        displayName = id, projectId = owner, generationId = generation, createdAt = 1)
    private fun record(owner: String?, outputs: List<String>) = GenerationRecord("record", nexusJobId = "job", providerId = "saved",
        modelId = "saved", modality = "VIDEO", prompt = "Saved", projectId = owner, status = "COMPLETED", outputAssetIds = outputs, createdAt = 1)
    private fun job(vararg outputs: String, owner: String = "a") = GenerationJob("job", providerId = "saved", modelId = "saved",
        modality = Modality.VIDEO, prompt = "Saved", parameters = mapOf("__project_id" to owner), status = GenerationStatus.COMPLETED,
        outputs = outputs.map { GenerationOutput("output-$it", "job", Modality.VIDEO, "https://example.invalid/$it.mp4", "file:///$it.mp4") })

    @Test fun mixedResultsKeepOnlyCurrentProjectOutputsWithoutChangingTheSourceJobOrItsOrder() {
        val source = job("foreign", "mine", "mine-second")
        val data = IndexData(projects = projects, assets = listOf(asset("foreign", "b"), asset("mine", "a"), asset("mine-second", "a")),
            generations = listOf(record("a", listOf("foreign", "mine", "mine-second"))))
        val filtered = projectGenerationJobs(data, listOf(source), "a").single()
        assertEquals(listOf("output-mine", "output-mine-second"), filtered.outputs.map { it.id })
        assertEquals(listOf("output-foreign", "output-mine", "output-mine-second"), source.outputs.map { it.id })
        assertEquals(1, source.outputs.indexOfFirst { it.id == filtered.outputs.first().id })
    }

    @Test fun movingOrUnassigningAnIndexedOutputWinsOverOldGenerationAndJobOwnership() {
        val source = job("moved")
        val data = IndexData(projects = projects, assets = listOf(asset("moved", "b")), generations = listOf(record("a", listOf("moved"))))
        assertTrue(projectGenerationJobs(data, listOf(source), "a").isEmpty())
        assertEquals(listOf("output-moved"), projectGenerationJobs(data, listOf(source), "b").single().outputs.map { it.id })
        assertTrue(projectGenerationJobs(data.copy(assets = listOf(asset("moved", null))), listOf(source), "a").isEmpty())
    }

    @Test fun anExplicitSharedLinkShowsTheSameOutputInBothProjectsWithoutReassigningIt() {
        val data = IndexData(projects = projects, assets = listOf(asset("shared", "b")), generations = listOf(record("b", listOf("shared"))),
            story = StoryState(links = listOf(StoryLink("link", "a", "shared", "project", "a"))))
        assertEquals(listOf("output-shared"), projectGenerationJobs(data, listOf(job("shared")), "a").single().outputs.map { it.id })
        assertEquals(listOf("output-shared"), projectGenerationJobs(data, listOf(job("shared")), "b").single().outputs.map { it.id })
        assertEquals("b", data.assets.single().projectId)
    }

    @Test fun pendingOrLegacyJobsUseRecordOwnershipBeforeTheDbFallbackAndUnknownProjectsStayEmpty() {
        val source = job(owner = "b")
        val data = IndexData(projects = projects, generations = listOf(record("a", emptyList())))
        assertEquals(listOf(source), projectGenerationJobs(data, listOf(source), "a"))
        assertTrue(projectGenerationJobs(data, listOf(source), "b").isEmpty())
        assertEquals(listOf(source), projectGenerationJobs(data.copy(generations = emptyList()), listOf(source), "b"))
        assertTrue(projectGenerationJobs(data, listOf(source), "missing").isEmpty())
    }

    @Test fun relocatedLegacyMediaDoesNotGuessWhichOldOutputIndexToOpen() {
        val data = IndexData(projects = projects, assets = listOf(asset("relocated", "a")), generations = listOf(record("a", listOf("relocated"))))
        val filtered = projectGenerationJobs(data, listOf(job("old-address")), "a").single()
        assertTrue(filtered.outputs.isEmpty())
    }

    @Test fun conflictingLocalCopiesDoNotAssignAnotherProjectsJobToTheLocalFilesOwner() {
        val sharedUri = "file:///shared.mp4"
        val outputA = GenerationOutput("output-a", "job-a", Modality.VIDEO, "https://example.invalid/a.mp4", sharedUri)
        val outputB = GenerationOutput("output-b", "job-b", Modality.VIDEO, "https://example.invalid/b.mp4", sharedUri)
        val jobA = job().copy(id = "job-a", outputs = listOf(outputA))
        val jobB = job(owner = "b").copy(id = "job-b", outputs = listOf(outputB))
        val data = IndexData(
            projects = projects,
            assets = listOf(asset("a", "a", "record-a").copy(uri = sharedUri),
                asset("b", "b", "record-b").copy(uri = outputB.url)),
            generations = listOf(record("a", listOf("a")).copy(id = "record-a", nexusJobId = jobA.id),
                record("b", listOf("b")).copy(id = "record-b", nexusJobId = jobB.id)),
        )
        val aJobs = projectGenerationJobs(data, listOf(jobA, jobB), "a")
        assertEquals(listOf("job-a"), aJobs.map { it.id })
        assertTrue(aJobs.single().outputs.isEmpty()) // An ambiguous copy is not a selectable result.
        val bJobs = projectGenerationJobs(data, listOf(jobA, jobB), "b")
        assertEquals(listOf("job-b"), bJobs.map { it.id })
        assertEquals(listOf(outputB), bJobs.single().outputs) // Its independent URL remains a valid identity.
        assertEquals(listOf(outputA), jobA.outputs)
        assertEquals(listOf(outputB), jobB.outputs)
    }
}
