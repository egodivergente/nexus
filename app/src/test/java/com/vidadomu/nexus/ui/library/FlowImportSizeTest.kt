package com.vidadomu.nexus.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class FlowImportSizeTest {
    @Test fun `quick keeps Flow default for images and videos`() {
        assertEquals(null, FlowImportSize.requestedSize(null, "image"))
        assertEquals(null, FlowImportSize.requestedSize(null, "video"))
    }

    @Test fun `image sizes pass through to Flow download menu`() {
        listOf("1K", "2K", "4K").forEach {
            assertEquals(it, FlowImportSize.requestedSize(it, "image"))
        }
    }

    @Test fun `video sizes map to the Flow download menu`() {
        assertEquals("720p", FlowImportSize.requestedSize("1K", "video"))
        assertEquals("1080p", FlowImportSize.requestedSize("2K", "video"))
        assertEquals("4K", FlowImportSize.requestedSize("4K", "video"))
    }

    @Test fun `failed upscales fall back to the original resolution`() {
        assertEquals("1K", FlowImportSize.fallbackSize("image"))
        assertEquals("720p", FlowImportSize.fallbackSize("video"))
    }
}
