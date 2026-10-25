package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.IndexData
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.test.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class AssetIndexPersistenceTest {
    /** Runs every dispatched block on its own thread, optionally cancelling [victim] right before or after it. */
    private class ThreadDispatcher(private val cancelBefore: Boolean = false, private val cancelAfter: Boolean = false) : CoroutineDispatcher() {
        val victim = AtomicReference<Job?>()
        val threads = mutableListOf<Thread>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            val thread = Thread {
                if (cancelBefore) victim.get()?.cancel()
                block.run()
                if (cancelAfter) victim.get()?.cancel()
            }
            synchronized(threads) { threads += thread }
            thread.start()
        }
    }

    private fun onDisk(file: File): IndexData = AssetIndex.JSON.decodeFromString(IndexData.serializer(), file.readText())

    private fun withFolder(body: (File) -> Unit) {
        val folder = Files.createTempDirectory("nexus-index-persist").toFile()
        try { body(folder) } finally { folder.deleteRecursively() }
    }

    @Test fun persistenceRunsOnThePersistContextNotTheCallerThread() = withFolder { folder ->
        val dispatcher = ThreadDispatcher()
        val file = File(folder, "index.json")
        val index = AssetIndex(file, persistContext = dispatcher)
        runBlocking { index.createProject("Uno") }
        val caller = Thread.currentThread()
        assertTrue(synchronized(dispatcher.threads) { dispatcher.threads.isNotEmpty() && dispatcher.threads.none { it == caller } })
        assertEquals(index.snapshot(), onDisk(file))
    }

    @Test fun defaultConstructorStillPersistsThroughIo() = withFolder { folder ->
        val file = File(folder, "index.json")
        val index = AssetIndex(file)
        runBlocking { index.createProject("Uno") }
        assertEquals(index.snapshot(), onDisk(file))
        assertEquals(index.snapshot(), AssetIndex(file).snapshot())
    }

    @Test fun cancellationAroundTheWriteNeverLeavesMemoryAndDiskApart() {
        for ((before, after) in listOf(true to false, false to true)) withFolder { folder ->
            val dispatcher = ThreadDispatcher(cancelBefore = before, cancelAfter = after)
            val file = File(folder, "index.json")
            val index = AssetIndex(file, persistContext = dispatcher)
            runBlocking {
                index.createProject("Base")
                val job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) { index.createProject("Cancelada") }
                dispatcher.victim.set(job)
                job.start(); job.join()
                dispatcher.victim.set(null)
                assertEquals(index.snapshot(), onDisk(file), "cancel before=$before after=$after")
                assertEquals(index.changes.value, index.snapshot())
                // The lock was released: later edits still work and stay consistent.
                index.createProject("Después")
                assertEquals(index.snapshot(), onDisk(file))
                assertEquals(index.snapshot(), AssetIndex(file).snapshot())
            }
        }
    }

    @Test fun concurrentEditsAreSerializedAndAllPersisted() = withFolder { folder ->
        val file = File(folder, "index.json")
        val index = AssetIndex(file)
        val count = AtomicInteger()
        runBlocking {
            (1..20).map { n -> launch(Dispatchers.Default) { index.createProject("P$n"); count.incrementAndGet() } }.forEach { it.join() }
        }
        assertEquals(20, count.get())
        assertEquals(20, index.projects().size)
        assertEquals(index.snapshot(), onDisk(file))
    }

    @Test fun aFailedWriteOffThreadPublishesNothing() = withFolder { folder ->
        val blocker = File(folder, "blocked").apply { writeText("original") }
        val index = AssetIndex(File(blocker, "index.json"), persistContext = ThreadDispatcher())
        assertFailsWith<java.io.IOException> { runBlocking { index.createProject("No") } }
        assertTrue(index.projects().isEmpty())
        assertEquals(IndexData(), index.changes.value)
        assertEquals("original", blocker.readText())
    }
}
