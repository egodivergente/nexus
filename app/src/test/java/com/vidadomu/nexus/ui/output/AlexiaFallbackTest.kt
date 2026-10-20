package com.vidadomu.nexus.ui.output

import org.junit.Assert.*
import org.junit.Test

class AlexiaFallbackTest {
    @Test fun messageStatesTheResultStaysInNexusAndOnlyOffersRealActions() {
        val local = alexiaUnavailableMessage(hasLocalCopy = true)
        val remote = alexiaUnavailableMessage(hasLocalCopy = false)
        assertTrue(local.startsWith("No se pudo abrir Alexia") && "sigue guardado en Nexus" in local && "otra app" in local)
        assertTrue("sigue en Nexus" in remote && "Descargar archivo" in remote && "otra app" !in remote)
        assertEquals("com.axel.library", ALEXIA_PACKAGE)
    }
}

class LocalCopyTargetTest {
    @Test fun acceptsOnlyAbsoluteLocalFilesAndContentDocuments() {
        assertEquals(LocalCopyTarget.LocalFile("/data/user/0/app/files/a b.png"), localCopyTarget("file:///data/user/0/app/files/a%20b.png"))
        assertEquals(LocalCopyTarget.LocalFile("/sdcard/x.mp4"), localCopyTarget("/sdcard/x.mp4"))
        assertEquals(LocalCopyTarget.Content("content://media/external/images/1"), localCopyTarget("content://media/external/images/1"))
    }

    @Test fun rejectsRemoteRelativeAndMalformedValues() {
        listOf("https://cdn.example/a.png", "http://x/a.png", "HTTPS://x/a.png", "ftp://x/a", "files/a.png", "file:relative.png",
            "content:///sin-autoridad", "", "  ", "file://").forEach { assertNull(it, localCopyTarget(it)) }
    }
}
