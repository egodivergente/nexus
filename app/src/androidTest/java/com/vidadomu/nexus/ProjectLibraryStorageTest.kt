package com.vidadomu.nexus

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.assistant.*
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectLibraryStorageTest {
    @Test fun savedSelectionExportsExactBytesAndDeletedPhotosStayOutAfterReload() = runBlocking {
        check(BuildConfig.OFFLINE_QA)
        val app = ApplicationProvider.getApplicationContext<NexusApplication>()
        val runtime = AssistantRuntime.get(app)
        val project = runtime.assets.createProject("ZIP QA ${UUID.randomUUID()}")
        val folder = File(app.cacheDir, "zip-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val assets = (1..3).map { n ->
                val file = File(folder, "$n.png").apply { writeBytes(byteArrayOf(n.toByte(), 2, 3)) }
                runtime.assets.addAsset(Asset("", Uri.fromFile(file).toString(), AssetType.IMAGE,
                    displayName = "misma.png", mimeType = "image/png", projectId = project.id, createdAt = n.toLong()))
            }
            val saved = SavedStateHandle()
            val first = ProjectLibraryViewModel(app, saved)
            first.prepareExport(project.id, listOf(assets[0], assets[2]))
            // Recreated controller must retain the selection while DocumentsUI is open.
            val recreated = ProjectLibraryViewModel(app, saved)
            val zip = File(folder, "selection.zip")
            InstrumentationRegistry.getInstrumentation().runOnMainSync { recreated.export(Uri.fromFile(zip)) }
            withTimeout(15_000) { while (recreated.state.value.busy) delay(50) }
            assertEquals("ZIP guardado", recreated.state.value.message)
            ZipFile(zip).use { archive ->
                assertEquals(2, archive.size())
                val content = archive.entries().asSequence().map { archive.getInputStream(it).use { input -> input.readBytes().first().toInt() } }.toSet()
                assertEquals(setOf(1, 3), content)
            }
            runtime.assets.deleteFromLibrary(project.id, setOf(assets[0].id))
            val persisted = AssetIndex(File(app.filesDir, "assistant/index.json"))
            val remaining = projectMediaChoices(persisted.snapshot().assets, project.id, listOf("image/*"), includeGenerated = true)
            assertEquals(setOf(assets[1].id, assets[2].id), remaining.map { it.id }.toSet())
            assertTrue(File(Uri.parse(assets[0].uri).path!!).isFile)
            recreated.prepareExport(project.id, remaining)
            val allZip = File(folder, "all.zip")
            InstrumentationRegistry.getInstrumentation().runOnMainSync { recreated.export(Uri.fromFile(allZip)) }
            withTimeout(15_000) { while (recreated.state.value.busy) delay(50) }
            assertEquals("ZIP guardado", recreated.state.value.message)
            ZipFile(allZip).use { archive ->
                assertEquals(setOf(2, 3), archive.entries().asSequence().map { archive.getInputStream(it).use { input -> input.readBytes().first().toInt() } }.toSet())
            }
        } finally { folder.deleteRecursively() }
    }
}
