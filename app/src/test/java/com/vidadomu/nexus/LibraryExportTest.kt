package com.vidadomu.nexus

import com.vidadomu.nexus.ui.library.exportExtension
import com.vidadomu.nexus.ui.library.safeExportName
import com.vidadomu.nexus.ui.library.uniqueEntryNames
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryExportTest {
    @Test fun repeatedNamesGetANumberBeforeTheExtension() {
        // Se comparan sin distinguir mayúsculas (Windows lo exige) pero cada nombre conserva las suyas.
        assertEquals(listOf("a.png", "a (2).png", "b.png", "A (3).PNG"),
            uniqueEntryNames(listOf("a.png", "a.png", "b.png", "A.PNG")))
    }

    @Test fun namesCannotEscapeTheZipOrBreakTheFileSystem() {
        assertEquals("a_b_c", safeExportName("a/b\\c"))
        assertEquals("archivo", safeExportName("..."))
        assertEquals("x_y_.mp4", safeExportName("x:y*.mp4"))
    }

    @Test fun extensionComesFromMimeThenFromTheUriThenFromTheKind() {
        assertEquals("mp4", exportExtension("video/mp4", "file:///x/y", "video"))
        assertEquals("jpg", exportExtension("image/jpeg", "file:///x/y", "image"))
        assertEquals("webp", exportExtension("", "file:///x/foto.WEBP?v=1", "image"))
        assertEquals("mp3", exportExtension(null, "content://media/123", "audio"))
    }
}
