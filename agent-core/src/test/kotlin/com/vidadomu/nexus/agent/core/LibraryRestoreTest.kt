package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.AssetRole
import com.vidadomu.nexus.agent.core.assets.AssetType
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import org.junit.jupiter.api.Test

class LibraryRestoreTest {
    private fun asset(id: String, deleted: Boolean = false) = Asset(
        id = id, uri = "file:///$id.png", type = AssetType.IMAGE,
        displayName = "Original $id", libraryDeleted = deleted, createdAt = 1
    )

    @Test fun restoreOnlyRequestedRemovedAssetsAndIgnoreUnknownOrVisibleIds() = runBlocking {
        val index = AssetIndex(null)
        index.addAsset(asset("restore", deleted = true))
        val untouched = index.addAsset(asset("keep-removed", deleted = true))
        val visible = index.addAsset(asset("already-visible"))

        assertEquals(1, index.restoreLibraryAssets(setOf("restore", "already-visible", "unknown")))
        assertFalse(index.asset("restore")!!.libraryDeleted)
        assertEquals(untouched, index.asset("keep-removed"))
        assertEquals(visible, index.asset("already-visible"))
        assertNull(index.asset("unknown"))
        assertEquals(3, index.snapshot().assets.size)
        assertEquals(index.snapshot(), index.changes.value)
    }

    @Test fun restoreKeepsInterveningEditsIdentitiesAndOriginalBytesAcrossReload() = runBlocking {
        val folder = Files.createTempDirectory("nexus-library-restore").toFile()
        try {
            val original = File(folder, "original.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val bytes = original.readBytes()
            val file = File(folder, "index.json")
            val index = AssetIndex(file)
            val before = index.addAsset(asset("photo").copy(uri = original.toURI().toString(),
                width = 1024, height = 768, generationId = "generation", parentAssetId = "parent"))
            assertEquals(1, index.deleteLibraryAssets(setOf(before.id)))
            val edited = index.updateAsset(before.id) {
                it.copy(displayName = "Renamed after removal", projectId = "new-project",
                    sceneId = "new-scene", role = AssetRole.REFERENCE, tags = listOf("keep"),
                    characterIds = listOf("character"))
            }

            assertEquals(1, index.restoreLibraryAssets(setOf(before.id)))
            val restored = AssetIndex(file).asset(before.id)!!
            assertEquals(edited.copy(libraryDeleted = false), restored)
            assertEquals(before.id, restored.id)
            assertEquals(before.uri, restored.uri)
            assertEquals(before.createdAt, restored.createdAt)
            assertContentEquals(bytes, original.readBytes())
        } finally { folder.deleteRecursively() }
    }

    @Test fun repeatedAndEmptyRestoreAreIdempotentAndBatchPreservesOrder() = runBlocking {
        val index = AssetIndex(null)
        val a = index.addAsset(asset("a", deleted = true))
        val b = index.addAsset(asset("b", deleted = true))
        assertEquals(2, index.restoreLibraryAssets(setOf("b", "a")))
        val once = index.snapshot()
        assertEquals(listOf(a.copy(libraryDeleted = false), b.copy(libraryDeleted = false)), once.assets)
        assertEquals(0, index.restoreLibraryAssets(setOf("a", "b")))
        assertEquals(0, index.restoreLibraryAssets(emptySet()))
        assertEquals(0, index.restoreLibraryAssets(setOf("missing")))
        assertEquals(once.assets, index.snapshot().assets)
        assertEquals(index.snapshot(), index.changes.value)
    }
}
