package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.*
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import org.junit.jupiter.api.Test

class LibraryDeletionTest {
    @Test fun deletionIsPersistentScopedAndPreservesHistoricalReferences() = runBlocking {
        val folder = Files.createTempDirectory("nexus-library-test").toFile()
        try {
            val original = File(folder, "image.png").apply { writeText("original photo bytes") }
            val file = File(folder, "index.json")
            val index = AssetIndex(file)
            val project = index.createProject("First")
            val other = index.createProject("Other")
            val asset = index.addAsset(Asset("a", original.toURI().toString(), AssetType.IMAGE, displayName = "Photo", projectId = project.id, createdAt = 1))
            index.addAsset(asset.copy(id = "b", uri = "file:///other.png", projectId = other.id))
            assertEquals(1, index.deleteFromLibrary(project.id, setOf("a", "b", "missing")))
            val loaded = AssetIndex(file)
            assertTrue(loaded.asset("a")!!.libraryDeleted)
            assertFalse(loaded.asset("b")!!.libraryDeleted)
            assertEquals(asset.uri, loaded.asset("a")!!.uri)
            assertEquals("original photo bytes", original.readText())
            assertTrue(loaded.search(AssetQuery(projectId = project.id)).isEmpty())
            assertEquals(index.snapshot(), index.changes.value)
            assertEquals(0, index.deleteFromLibrary(project.id, setOf("a")))
            // History re-indexing must not resurrect removed library entries.
            index.addAsset(asset)
            assertTrue(index.asset("a")!!.libraryDeleted)
        } finally { folder.deleteRecursively() }
    }
}
