package com.vidadomu.nexus.ui.cut

import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.assets.IndexData
import com.vidadomu.nexus.agent.core.tools.Attachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CutLibraryFrontendTest {
    private fun asset(id: String, type: AssetType = AssetType.IMAGE, deleted: Boolean = false) =
        Asset(id, "file:///current/$id", type, displayName = "Actual $id", createdAt = 1, libraryDeleted = deleted)
    private fun result(id: String) = Attachment("old-$id", "file:///old/$id", "image/png", assetId = id)

    @Test fun resultsUseCurrentAssetIdentityAndUriInTheToolOrderWithoutDuplicatingCards() {
        val index = IndexData(assets = listOf(asset("image"), asset("video", AssetType.VIDEO)))
        val results = currentCutLibraryResults(listOf(result("video"), result("image"), result("video")), index)
        assertEquals(listOf("video", "image"), results.map { it.id })
        assertEquals(listOf("video", "image"), results.map { it.assetId })
        assertEquals("file:///current/video", results.first().uri)
        assertEquals("video/mp4", results.first().mimeType)
        assertEquals("Actual video", results.first().name)
    }

    @Test fun missingDeletedAndNonvisualAssetsDoNotRemainActionable() {
        val index = IndexData(assets = listOf(asset("ok"), asset("deleted", deleted = true), asset("audio", AssetType.AUDIO)))
        assertEquals(listOf("ok"), currentCutLibraryResults(listOf(result("missing"), result("deleted"), result("audio"), result("ok")), index).map { it.assetId })
    }

    @Test fun presentingResultsOnlyChangesTheEphemeralFrontendFields() {
        val message = CutChatMessage("message", CutChatRole.USER, "Encuentra el vídeo", 10)
        val state = CutChatUiState(messages = listOf(message), draft = "Borrador conservado", ready = true, thinking = true,
            streamingText = "Buscando", activity = "Leyendo Biblioteca", modelLabel = "Cerebro")
        val index = IndexData(assets = listOf(asset("found")))
        val shown = presentCutLibraryResults(state, listOf(result("found")), index)
        assertTrue(shown.libraryResultsOpen)
        assertEquals(state, shown.copy(libraryResults = emptyList(), libraryResultsOpen = false))
        assertTrue(state.libraryResults.isEmpty())
        assertEquals("file:///current/found", index.assets.single().uri)
    }

    @Test fun reopeningAStaleResultSetCannotOpenAnEmptyWall() {
        val state = CutChatUiState(libraryResults = listOf(result("gone")), libraryResultsOpen = true)
        val shown = presentCutLibraryResults(state, state.libraryResults, IndexData())
        assertFalse(shown.libraryResultsOpen)
        assertTrue(shown.libraryResults.isEmpty())
    }
}
