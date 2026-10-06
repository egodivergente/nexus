package com.vidadomu.nexus

import com.vidadomu.nexus.ui.create.PrivateMediaStorage
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class PrivateMediaStorageTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun cleanupOnlyRemovesOldUnreferencedNexusCopies() {
        val dir = temporary.newFolder("draft_media")
        val old = java.io.File(dir,"nexus_media_old.png").apply {writeText("copy");setLastModified(1)}
        val protected = java.io.File(dir,"nexus_media_history.png").apply {writeText("history");setLastModified(1)}
        val foreign = java.io.File(dir,"original.png").apply {writeText("original");setLastModified(1)}
        val fresh = java.io.File(dir,"nexus_media_active.png").apply {writeText("copy")}
        val bytes = PrivateMediaStorage.prepare(dir,setOf(protected.absolutePath))
        assertFalse(old.exists());assertTrue(protected.exists());assertTrue(foreign.exists());assertTrue(fresh.exists())
        assertEquals(protected.length()+foreign.length()+fresh.length(),bytes)
    }
}
