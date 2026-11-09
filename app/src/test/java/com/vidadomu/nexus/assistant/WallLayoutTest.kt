package com.vidadomu.nexus.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallLayoutTest {
    @Test fun `el collage inicial no solapa y usa dos columnas para cuatro imagenes`() {
        val p = WallLayout.initial(listOf("a", "b", "c", "d"), listOf(1f, 1f, 1f, 1f))
        assertEquals(4, p.size)
        assertEquals(setOf(0f, WallLayout.CELL + WallLayout.GAP), p.map { it.x }.toSet())
        assertEquals(2, p.map { it.y }.toSet().size)
    }
    @Test fun `una sola imagen ocupa una columna y no hay imagenes vacias`() {
        assertEquals(1, WallLayout.initial(listOf("a"), listOf(1f)).size)
        assertTrue(WallLayout.initial(emptyList(), emptyList()).isEmpty())
    }
    @Test fun `ver todo cabe en la pantalla`() {
        val p = WallLayout.initial(listOf("a", "b", "c", "d", "e", "f"), List(6) { 1f })
        val (scale, ox, oy) = WallLayout.fit(p, p.associate { it.id to 1f }, 1000f, 2000f)
        val right = p.maxOf { it.x + it.w } * scale + ox
        val bottom = p.maxOf { it.y + it.w } * scale + oy
        assertTrue(right <= 1000f && bottom <= 2000f && ox >= 0f && oy >= 0f)
    }
    @Test fun `guardar y recuperar solo vale para el mismo conjunto de imagenes`() {
        val p = WallLayout.initial(listOf("a", "b"), listOf(1f, 1.5f))
        assertEquals(p, WallLayout.decode(WallLayout.encode(p), listOf("a", "b")))
        assertNull(WallLayout.decode(WallLayout.encode(p), listOf("a", "c")))
        assertNull(WallLayout.decode("basura", listOf("a", "b")))
    }
}
