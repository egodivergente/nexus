package com.vidadomu.nexus

import com.vidadomu.nexus.inpaint.selectInpaintWork
import org.junit.Assert.*
import org.junit.Test

class InpaintWorkSelectionTest {
    private data class Work(val id: String, val finished: Boolean = true)
    private fun select(items: List<Work>, tracked: String? = null) =
        selectInpaintWork(items, tracked, Work::id, Work::finished)

    @Test fun historyOrderNeverChoosesAnOlderResult() {
        val old = Work("old"); val current = Work("current")
        assertEquals(current, select(listOf(current, old), "current"))
        assertEquals(current, select(listOf(old, current), "current"))
    }
    @Test fun activeWorkWinsOverPreviouslyTrackedCompletion() {
        val active = Work("active", false)
        assertEquals(active, select(listOf(active, Work("old")), "old"))
    }
    @Test fun ambiguousLegacyHistoryDoesNotInventTheLatestJob() {
        assertNull(select(listOf(Work("a"), Work("b"))))
        assertEquals(Work("a"), select(listOf(Work("a"))))
    }
    @Test fun missingTrackedWorkDoesNotRestoreUnrelatedResult() {
        assertNull(select(listOf(Work("old")), "missing"))
        assertNull(select(emptyList(), "missing"))
    }
}
