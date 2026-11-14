package com.vidadomu.nexus.cut

import com.vidadomu.nexus.cut.command.CutProjects
import com.vidadomu.nexus.cut.export.*
import com.vidadomu.nexus.cut.model.IdGenerator
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ExportQueueTest {
    private val project = CutProjects.create("Batch", 1, IdGenerator.sequential())
    private fun job(n: Int) = ExportJob(project, ExportMath.plan(project.settings, project.export, 1_000_000), File("clip$n.mp4"), emptySet()) { "asset$n" }
    private fun done(n: Int) = ExportState.Done(project.id, "asset$n", File("clip$n.mp4"), 1, 1_000_000, "")
    @After fun cleanup() { ExportManager.update(ExportState.Cancelled(project.id)); ExportManager.acknowledge() }
    @Test fun batchRunsSequentiallyAndReportsAllCompletedAssets() {
        assertTrue(ExportManager.enqueueBatch(listOf(job(1), job(2), job(3))))
        assertFalse(ExportManager.enqueue(job(4)))
        val first = requireNotNull(ExportManager.take())
        val second = requireNotNull(ExportManager.complete(first, done(1)))
        ExportManager.update(ExportState.Running(project.id, project.name, 50, "Renderizando"))
        assertEquals(50, (ExportManager.state.value as ExportState.Running).progress)
        val third = requireNotNull(ExportManager.complete(second, done(2)))
        assertNull(ExportManager.complete(third, done(3)))
        assertEquals(listOf("asset1", "asset2", "asset3"), (ExportManager.state.value as ExportState.Done).assetIds)
    }
    @Test fun cancellationKeepsCompletedFilesAndDropsPendingJobs() {
        assertTrue(ExportManager.enqueueBatch(listOf(job(1), job(2), job(3))))
        val first = requireNotNull(ExportManager.take()); ExportManager.complete(first, done(1))
        ExportManager.update(ExportState.Cancelled(project.id))
        assertNull(ExportManager.take())
        assertEquals(listOf("asset1"), (ExportManager.state.value as ExportState.Cancelled).assetIds)
    }
    @Test fun lateCompletionCannotConsumeOrOverwriteANewBatch() {
        assertTrue(ExportManager.enqueue(job(1)))
        val old = requireNotNull(ExportManager.take())
        ExportManager.update(ExportState.Cancelled(project.id))
        assertTrue(ExportManager.enqueue(job(2)))
        val current = ExportManager.state.value
        assertNull(ExportManager.complete(old, done(1)))
        assertEquals(current, ExportManager.state.value)
        val next = requireNotNull(ExportManager.take())
        assertNull(ExportManager.complete(next, done(2)))
        assertEquals(listOf("asset2"), (ExportManager.state.value as ExportState.Done).assetIds)
    }
}
