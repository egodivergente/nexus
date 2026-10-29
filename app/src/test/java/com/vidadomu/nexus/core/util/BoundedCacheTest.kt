package com.vidadomu.nexus.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedCacheTest {
    @Test
    fun growingToTheActualCatalogSizeAvoidsRepeatedSequentialRecomputation() {
        val cache = BoundedCache<Int, String>(768)
        var computations = 0
        fun load(id: Int) = cache.getOrPut(id) { computations++; "model-$id" }
        repeat(768) { load(it) }
        cache.resize(863)
        repeat(2) { repeat(863) { load(it) } }
        assertEquals(863, computations)
        assertEquals(863, cache.size())
    }

    @Test
    fun shrinkingEvictsOldestAccessesAndKeepsRecentEntries() {
        val cache = BoundedCache<Int, String>(4)
        (1..4).forEach { cache[it] = "model-$it" }
        assertEquals("model-2", cache[2])
        cache.resize(2)
        assertEquals(2, cache.size())
        assertNull(cache[1])
        assertNull(cache[3])
        assertEquals("model-4", cache[4])
        assertEquals("model-2", cache[2])
        cache[5] = "model-5"
        assertNull(cache[4])
        assertEquals("model-2", cache[2])
        assertEquals("model-5", cache[5])
    }

    @Test
    fun invalidResizeLeavesContentsAndCapacityIntact() {
        val cache = BoundedCache<Int, String>(2)
        cache[1] = "one"
        cache[2] = "two"
        listOf(0, -1).forEach { invalid ->
            assertTrue(runCatching { cache.resize(invalid) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(2, cache.size())
        assertEquals("one", cache[1])
        assertEquals("two", cache[2])
        cache.clear()
        cache[1] = "one"
        cache[2] = "two"
        assertEquals(2, cache.size())
        assertEquals("one", cache[1])
        assertEquals("two", cache[2])
    }
}
