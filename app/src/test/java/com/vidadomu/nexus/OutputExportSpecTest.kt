package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.output.outputExportSpec
import org.junit.Assert.*
import org.junit.Test

class OutputExportSpecTest {
    private fun output(type: Modality, mime: String = "") = GenerationOutput("../image?1", "job", type, "https://example.test/result", mimeType = mime)
    @Test fun mediaTypesHaveUsefulExtensions() {
        assertTrue(outputExportSpec(output(Modality.IMAGE, "image/jpeg; charset=utf-8")).fileName.endsWith(".jpg"))
        assertTrue(outputExportSpec(output(Modality.VIDEO)).fileName.endsWith(".mp4"))
        assertTrue(outputExportSpec(output(Modality.AUDIO)).fileName.endsWith(".mp3"))
    }
    @Test fun textExportIsTextRegardlessOfProviderMime() {
        assertEquals("text/plain", outputExportSpec(output(Modality.CHAT, "image/png")).mimeType)
    }
    @Test fun fileNamesCannotEscapeDestination() {
        val name = outputExportSpec(output(Modality.IMAGE)).fileName
        assertFalse(name.contains("/")); assertFalse(name.contains("..")); assertFalse(name.contains("?"))
    }
    @Test fun unknownMimeDoesNotInventAnImageExtension() {
        assertTrue(outputExportSpec(output(Modality.IMAGE, "application/octet-stream")).fileName.endsWith(".bin"))
    }
    @Test fun studioNavigationHasFiveDestinationsAndKeepsSettingsOffTheBar() {
        assertEquals(5, com.vidadomu.nexus.ui.navigation.Screen.bottomNavItems.size)
        assertFalse(com.vidadomu.nexus.ui.navigation.Screen.Settings in com.vidadomu.nexus.ui.navigation.Screen.bottomNavItems)
        assertTrue(com.vidadomu.nexus.ui.navigation.Screen.Flow in com.vidadomu.nexus.ui.navigation.Screen.bottomNavItems)
    }
}
