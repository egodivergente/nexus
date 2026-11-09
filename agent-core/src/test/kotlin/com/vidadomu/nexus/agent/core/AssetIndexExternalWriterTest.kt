package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.AssetIndex
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking

/** Reproduces a stale JVM next to a newer restored/synchronized index, without touching a user profile. */
class AssetIndexExternalWriterTest {
    @Test fun secondInstanceCannotOverwriteTheNewerFirstInstanceIndex() = runBlocking<Unit> {
        val folder = Files.createTempDirectory("nexus-two-index-writers").toFile(); val file = folder.resolve("index.json")
        try {
            val first = AssetIndex(file); first.createProject("Original")
            val stale = AssetIndex(file)
            first.createProject("Nuevo desde la otra instancia")
            val bytes = file.readBytes()
            val failure = assertFailsWith<IllegalStateException> { stale.createProject("Intento desfasado") }
            assertTrue(failure.message.orEmpty().contains("Reabre Nexus"))
            assertContentEquals(bytes, file.readBytes())
            assertEquals(listOf("Original"), stale.projects().map { it.name })
            assertEquals(setOf("Original", "Nuevo desde la otra instancia"), AssetIndex(file).projects().map { it.name }.toSet())
        } finally { folder.deleteRecursively() }
    }

    @Test fun anExternalRestorationIsPreservedUntilTheIndexIsReopened() = runBlocking<Unit> {
        val folder = Files.createTempDirectory("nexus-index-restore").toFile(); val file = folder.resolve("index.json")
        try {
            val index = AssetIndex(file); index.createProject("Uno")
            val external = AssetIndex(null); external.createProject("Restaurado A"); external.createProject("Restaurado B")
            val restored = AssetIndex.JSON.encodeToString(com.vidadomu.nexus.agent.core.assets.IndexData.serializer(), external.snapshot())
            file.writeText(restored)
            assertFailsWith<IllegalStateException> { index.createProject("No debe pisar el restaurado") }
            assertEquals(restored, file.readText())
            val reopened = AssetIndex(file); reopened.createProject("Edición después de reabrir")
            assertEquals(3, AssetIndex(file).projects().size)
        } finally { folder.deleteRecursively() }
    }

    @Test fun aBusyWriterDoesNotLoseItsTemporaryFile() = runBlocking<Unit> {
        val folder = Files.createTempDirectory("nexus-index-locked").toFile(); val file = folder.resolve("index.json")
        try {
            val index = AssetIndex(file); index.createProject("Original")
            val pending = folder.resolve("index.json.tmp").apply { writeText("Otro proceso está escribiendo aquí") }
            java.nio.channels.FileChannel.open(folder.resolve("index.json.lock").toPath(), java.nio.file.StandardOpenOption.WRITE).use { channel ->
                channel.lock().use { assertFailsWith<IllegalStateException> { index.createProject("No debe tocar el otro temporal") } }
            }
            assertEquals("Otro proceso está escribiendo aquí", pending.readText())
            assertEquals(1, AssetIndex(file).projects().size)
        } finally { folder.deleteRecursively() }
    }
}
