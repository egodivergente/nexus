package com.vidadomu.nexus

import com.vidadomu.nexus.ui.library.*
import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.core.model.*
import org.junit.Assert.*
import org.junit.Test

class LibraryProjectFilterTest {
    private val output=GenerationOutput("out","job",Modality.IMAGE,"https://example.invalid/a.png","file:///a.png","image/png")
    private val job=GenerationJob("job",providerId="test",modelId="fixture",modality=Modality.IMAGE,prompt="local",parameters=mapOf("__project_id" to "A"),outputs=listOf(output))
    @Test fun projectsCombineAsUnionAndUnassignedStaysExplicit() {
        assertTrue(matchesLibraryProjects(emptySet(),setOf("A")))
        assertTrue(matchesLibraryProjects(setOf("A","B"),setOf("B")))
        assertFalse(matchesLibraryProjects(setOf("A"),setOf("B")))
        assertFalse(matchesLibraryProjects(setOf("A"),emptySet()))
        assertTrue(matchesLibraryProjects(setOf(LIBRARY_UNASSIGNED_PROJECT),emptySet()))
    }
    @Test fun currentFileAssignmentWinsOverTheOriginalGenerationProject() {
        val asset=Asset("file",output.localUri!!,AssetType.IMAGE,displayName="fixture",projectId="B",createdAt=0)
        val index=LibraryProjectIndex(IndexData(assets=listOf(asset)))
        assertTrue(index.matches(setOf("B"),job,output))
        assertFalse(index.matches(setOf("A"),job,output))
        assertTrue(index.matches(setOf("A"),job))
    }
    @Test fun clearingFiltersAlsoClearsProjectSelection() {
        val filters=LibraryViewFilters(query="fox",projects=setOf("A","B"))
        assertTrue(filters.narrowed)
        assertEquals(emptySet<String>(),filters.withoutFilters().projects)
        assertEquals("fox",filters.withoutFilters().query)
    }
}
