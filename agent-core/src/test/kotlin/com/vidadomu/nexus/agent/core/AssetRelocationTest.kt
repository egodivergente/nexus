package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class AssetRelocationTest {
    @Test fun relocationPersistsOnlyTheNameAndUriWhileLogicalEditsKeepTheUri() = runBlocking {
        val folder = Files.createTempDirectory("nexus-relocation").toFile()
        try {
            val file = File(folder, "index.json")
            val index = AssetIndex(file)
            val original = index.addAsset(Asset("", "file:///old.png", AssetType.IMAGE, displayName = "old.png", createdAt = 123, tags = listOf("keep"), version = 2))
            val logical = index.updateAsset(original.id) { it.copy(uri = "file:///forbidden.png", displayName = "label") }
            assertEquals(original.uri, logical.uri)
            val moved = index.relocateAsset(original.id, original.uri, "file:///new.png", "new.png")
            assertEquals(original.id, moved.id); assertEquals(original.createdAt, moved.createdAt)
            assertEquals(original.tags, moved.tags); assertEquals(original.version, moved.version)
            assertEquals(moved, AssetIndex(file).asset(original.id))
        } finally { folder.deleteRecursively() }
    }

    @Test fun staleRelocationLeavesTheCurrentIndexUntouched() = runBlocking {
        val index = AssetIndex(null)
        val asset = index.addAsset(Asset("", "file:///current.png", AssetType.IMAGE, displayName = "current.png", createdAt = 1))
        val before = index.snapshot()
        try {
            index.relocateAsset(asset.id, "file:///stale.png", "file:///next.png", "next.png")
            fail("Stale URI must be rejected")
        } catch (_: IllegalArgumentException) {}
        assertEquals(before, index.snapshot())
    }

    @Test fun failedPersistenceDoesNotPublishAnUncommittedIndex() = runBlocking {
        val folder = Files.createTempDirectory("nexus-index-failure").toFile()
        try {
            val blocker = File(folder, "blocked").apply { writeText("original") }
            val index = AssetIndex(File(blocker, "index.json"))
            try { index.createProject("Must not persist"); fail("Expected storage failure") }
            catch (_: java.io.IOException) {}
            assertTrue(index.projects().isEmpty())
            assertEquals("original", blocker.readText())
        } finally { folder.deleteRecursively() }
    }
}
