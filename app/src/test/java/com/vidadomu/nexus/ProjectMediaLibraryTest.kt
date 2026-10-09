package com.vidadomu.nexus

import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.assistant.projectMediaChoices
import org.junit.Assert.*
import org.junit.Test

class ProjectMediaLibraryTest {
    private fun photo(id: String, project: String = "one", mime: String = "image/png", source: AssetSource = AssetSource.IMPORTED) =
        Asset(id, "file:///$id.png", AssetType.IMAGE, source = source, displayName = "Retrato $id", mimeType = mime, projectId = project, createdAt = 1)

    @Test fun filtersByProjectAndMediaTypeWithoutShowingGeneratedResults() {
        val list = listOf(photo("a"), photo("b", "two"), photo("video", mime = "video/mp4"), photo("generated", source = AssetSource.GENERATED))
        assertEquals(listOf("a"), projectMediaChoices(list, "one", listOf("image/*")).map { it.id })
        assertTrue(projectMediaChoices(list, null, listOf("image/*")).isEmpty())
    }

    @Test fun searchIsCaseInsensitiveAndKeepsNewestFirstWithoutDuplicateFiles() {
        val old = photo("old")
        val recent = photo("new").copy(createdAt = 2)
        val list = listOf(old, recent, old.copy(id = "duplicate"))
        assertEquals(listOf("new", "old"), projectMediaChoices(list, "one", listOf("image/*"), " RETRATO ").map { it.id })
        assertEquals(listOf("new"), projectMediaChoices(list, "one", listOf("image/png"), "NEW").map { it.id })
        assertTrue(projectMediaChoices(list, "one", listOf("image/jpeg")).isEmpty())
    }
}
