package com.vidadomu.nexus.ui.models

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletedSnapshotWarmupTest {
    @Test
    fun equalSnapshotOnAnotherScreenVisitDoesNotWarmAgain() = runTest {
        val loaded = mutableListOf<Int>()
        val warmup = CompletedSnapshotWarmup<Int> { loaded += it }
        warmup.warm(listOf(1, 2))
        warmup.warm(listOf(1, 2).toMutableList())
        assertEquals(listOf(1, 2), loaded)
    }

    @Test
    fun changedSnapshotWarmsAgainAndReplacesTheCompletedSnapshot() = runTest {
        val loaded = mutableListOf<Int>()
        val warmup = CompletedSnapshotWarmup<Int> { loaded += it }
        warmup.warm(listOf(1, 2))
        warmup.warm(listOf(1, 3))
        warmup.warm(listOf(1, 3))
        warmup.warm(listOf(1, 2))
        assertEquals(listOf(1, 2, 1, 3, 1, 2), loaded)
    }

    @Test
    fun cancellationDoesNotMarkAnIncompleteCatalogAsDone() = runTest {
        val loaded = mutableListOf<Int>()
        val entered = CompletableDeferred<Unit>()
        var interrupt = true
        val warmup = CompletedSnapshotWarmup<Int> {
            loaded += it
            if (it == 2 && interrupt) {
                interrupt = false
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        val canceled = launch { warmup.warm(listOf(1, 2, 3)) }
        entered.await()
        canceled.cancelAndJoin()
        warmup.warm(listOf(1, 2, 3))
        warmup.warm(listOf(1, 2, 3))
        assertEquals(listOf(1, 2, 1, 2, 3), loaded)
    }

    @Test
    fun failedLoadCanBeRetried() = runTest {
        val loaded = mutableListOf<Int>()
        var fail = true
        val warmup = CompletedSnapshotWarmup<Int> {
            loaded += it
            if (it == 2 && fail) {
                fail = false
                error("Interrupted index")
            }
        }
        assertTrue(runCatching { warmup.warm(listOf(1, 2, 3)) }.isFailure)
        warmup.warm(listOf(1, 2, 3))
        assertEquals(listOf(1, 2, 1, 2, 3), loaded)
    }

    @Test
    fun concurrentEqualRequestsShareOneSuccessfulPass() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val loaded = mutableListOf<Int>()
        val warmup = CompletedSnapshotWarmup<Int> {
            loaded += it
            if (it == 1) {
                entered.complete(Unit)
                release.await()
            }
        }
        val first = launch { warmup.warm(listOf(1, 2)) }
        entered.await()
        val second = launch { warmup.warm(listOf(1, 2)) }
        release.complete(Unit)
        first.join()
        second.join()
        assertEquals(listOf(1, 2), loaded)
    }
}
