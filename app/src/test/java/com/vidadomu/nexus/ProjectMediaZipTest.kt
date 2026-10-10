package com.vidadomu.nexus

import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.assistant.*
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProjectMediaZipTest {
    private fun asset(id: String, name: String) = Asset(id, "file:///$id", AssetType.IMAGE,
        displayName = name, mimeType = "image/png", projectId = "p", createdAt = 1)

    @Test fun archiveContainsExactlySelectedBytesWithUniqueSafeNames() = runBlocking {
        val images = listOf(asset("1", "../portrait.png"), asset("2", "../portrait.png"), asset("3", "PORTRAIT.PNG"), asset("4", "portrait.png"))
        val output = ByteArrayOutputStream()
        writeProjectMediaZip(images, output, { it.id.byteInputStream() })
        val entries = linkedMapOf<String, String>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                assertFalse(entry.name.contains('/')); assertFalse(entry.name.contains('\\'))
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(4, entries.size)
        assertEquals(4, entries.keys.map { it.lowercase() }.toSet().size)
        assertEquals(listOf("1", "2", "3", "4"), entries.values.toList())
    }

    @Test fun removedAndOtherProjectImagesNeverEnterAllProjectExport() {
        val imported = asset("1", "photo")
        val generated = asset("2", "result").copy(source = AssetSource.GENERATED)
        val removed = asset("3", "deleted").copy(libraryDeleted = true)
        val other = asset("4", "other").copy(projectId = "other")
        assertEquals(setOf("1", "2"), projectMediaChoices(listOf(imported, generated, removed, other), "p", listOf("image/*"), includeGenerated = true).map { it.id }.toSet())
    }

    @Test fun unreadableOrEmptyImagesFailInsteadOfReportingAnIncompleteZipAsSuccess() = runBlocking {
        for (empty in listOf(true, false)) {
            try {
                writeProjectMediaZip(listOf(asset("1", "photo")), ByteArrayOutputStream(), {
                    if (empty) byteArrayOf().inputStream() else throw java.io.IOException("Missing")
                })
                fail("Must fail")
            } catch (_: java.io.IOException) {} catch (_: IllegalStateException) {}
        }
    }
}
