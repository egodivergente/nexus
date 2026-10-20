package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.agent.core.assets.ProjectMemory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ProjectMemorySaveGateTest {
    @Test fun secondSaveOfTheSameProjectIsRejectedUntilReleased() {
        val gate = ProjectMemorySaveGate()
        assertTrue(gate.tryAcquire("p1"))
        assertFalse("Reentrar no debe lanzar otro guardado", gate.tryAcquire("p1"))
        assertEquals(setOf("p1"), gate.savingIds.value)
        assertTrue(gate.isSaving("p1"))
        gate.release("p1")
        assertFalse(gate.isSaving("p1"))
        assertTrue(gate.tryAcquire("p1"))
    }

    @Test fun projectsAreIndependentAndReleasingAnIdleProjectIsHarmless() {
        val gate = ProjectMemorySaveGate()
        assertTrue(gate.tryAcquire("a"))
        assertTrue(gate.tryAcquire("b"))
        gate.release("c")
        gate.release("a")
        assertEquals(setOf("b"), gate.savingIds.value)
    }

    @Test fun concurrentAttemptsOnOneProjectAdmitExactlyOne() {
        val gate = ProjectMemorySaveGate()
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val winners = AtomicInteger()
        try {
            repeat(64) { pool.execute { start.await(); if (gate.tryAcquire("p1")) winners.incrementAndGet() } }
            start.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        } finally { pool.shutdownNow() }
        assertEquals(1, winners.get())
    }

    @Test fun storageErrorsNeverExposePaths() {
        val path = "/data/user/0/com.vidadomu.nexus/files/assets/index.json.tmp"
        listOf(IOException("$path: EACCES"), FileNotFoundException("$path (Is a directory)"),
            SerializationException("Unexpected JSON token at offset 3: {\"synopsis\":\"privado\"}"),
            IllegalArgumentException("C:\\Users\\x\\index.json"), RuntimeException("boom"), IOException()).forEach {
            val message = projectMemorySaveFailureMessage(it)
            assertEquals(PROJECT_MEMORY_STORAGE_ERROR, message)
            assertFalse('/' in message || '\\' in message)
        }
    }

    @Test fun validationMessagesStayActionable() {
        val project = Project("p1", "QA", null, createdAt = 0, memory = ProjectMemory(updatedAt = 1))
        val stale = ProjectMemoryDraft.from(project).copy(synopsis = "Mío", baseUpdatedAt = 0, baseSnapshot = null)
        val blocked = stale.prepareSave(project) as ProjectMemorySaveDecision.Blocked
        assertEquals(blocked.message, projectMemorySaveFailureMessage(IllegalArgumentException(blocked.message)))
        val tooManyModels = ProjectMemoryDraft.from(project).copy(models = "a\na")
        val warning = tooManyModels.saveWarning()!!
        assertEquals(warning, projectMemorySaveFailureMessage(IllegalArgumentException(warning), tooManyModels))
    }

    @Test fun unrecognisedValidationTextIsNeverShown() {
        val draft = ProjectMemoryDraft.from(Project("p1", "QA", null, createdAt = 0, memory = ProjectMemory(updatedAt = 1)))
        listOf("Proyecto desconocido: p1", "Texto privado de la memoria sin barras", "").forEach {
            assertEquals(PROJECT_MEMORY_STORAGE_ERROR, projectMemorySaveFailureMessage(IllegalArgumentException(it), draft))
        }
        assertEquals(PROJECT_MEMORY_STORAGE_ERROR, projectMemorySaveFailureMessage(IllegalArgumentException(null as String?), draft))
    }

    @Test fun realWriteFailureFromTheStoreIsReportedGenerically() = runBlocking {
        val dir = Files.createTempDirectory("nexus-memory-io").toFile()
        try {
            val file = File(dir, "index.json")
            var tick = 0L
            val index = AssetIndex(file) { ++tick }
            val project = index.createProject("QA sintético")
            File(dir, "index.json.tmp").apply { mkdirs(); File(this, "bloqueo").writeText("x") }
            val draft = ProjectMemoryDraft.from(project).copy(synopsis = "Cambio")
            val error = runCatching { persistProjectMemoryDraft(index, draft) }.exceptionOrNull()
            assertTrue("Se esperaba un fallo de E/S", error is IOException)
            val shown = projectMemorySaveFailureMessage(error!!)
            assertEquals(PROJECT_MEMORY_STORAGE_ERROR, shown)
            assertFalse(shown.contains(dir.name) || shown.contains(dir.absolutePath))
        } finally { dir.deleteRecursively() }
    }

    @Test fun confirmingLegacyModelsKeepsTheTextAndOnlyThenAllowsSaving() {
        val project = Project("p1", "QA", null, createdAt = 0, memory = ProjectMemory(updatedAt = 1))
        val base = ProjectMemoryDraft.from(project)
        val legacy = ProjectMemoryDraft.fromSaved(base.copy(models = "Seedance 2.0, Kling").toSaved().take(ProjectMemoryDraft.LEGACY_FIELDS))!!
        assertTrue(legacy.legacyModelsNeedReview)
        assertTrue(legacy.prepareSave(project) is ProjectMemorySaveDecision.Blocked)
        val confirmed = legacy.copy(legacyModelsNeedReview = false)
        val ready = confirmed.prepareSave(project) as ProjectMemorySaveDecision.Ready
        assertEquals("Seedance 2.0, Kling", ready.draft.models)
        assertEquals(listOf("Seedance 2.0, Kling"), ready.draft.toMemory(project.memory).preferredModels)
    }
}
