package com.vidadomu.nexus.assistant

import org.junit.Assert.*
import org.junit.Test

class ChatBoardTest {
    private fun images(n: Int) = (1..n).map { Triple("file:///img$it.jpg", "foto$it.jpg", if (it % 2 == 0) .75f else 1.5f) }

    @Test fun anyNumberOfImagesIsPlacedWithoutOverlapOrLimit() {
        val board = ChatBoard.arrange("b", images(80))
        assertEquals(80, board.images.size)
        board.images.forEach { assertTrue(it.x >= 0f && it.x + it.w <= 1.0001f) }
        for (a in board.images) for (b in board.images) if (a !== b) {
            val apart = a.x + a.w <= b.x + 1e-4f || b.x + b.w <= a.x + 1e-4f || a.y + a.h <= b.y + 1e-4f || b.y + b.h <= a.y + 1e-4f
            assertTrue("${a.name} overlaps ${b.name}", apart)
        }
        assertTrue(board.height >= board.images.maxOf { it.y + it.h })
    }

    @Test fun boardIsDirtyUntilSentAndAgainAfterAnEdit() {
        val board = ChatBoard.arrange("b", images(3))
        assertTrue(board.dirty)
        val sent = board.markSent()
        assertFalse(sent.dirty)
        val marked = sent.copy(images = sent.images.mapIndexed { i, img -> if (i == 1) img.copy(strokes = listOf(BoardStroke(listOf(.1f, .1f, .5f, .5f)))) else img })
        assertTrue(marked.dirty)
    }

    @Test fun firstNoteListsImagesInReadingOrderAndTheMarkedOnes() {
        val board = ChatBoard.arrange("b", images(3)).let { b ->
            b.copy(images = b.images.mapIndexed { i, img -> if (i == 2) img.copy(strokes = listOf(BoardStroke(listOf(.2f, .2f, .3f, .3f)))) else img },
                strokes = listOf(BoardStroke(listOf(.1f, .1f, .9f, .9f))))
        }
        val note = ChatBoardExport.note(board, 1)
        assertTrue(note, note.startsWith("Tablero 1: 3 imágenes"))
        assertTrue(note, note.contains("1 (foto1.jpg), 2 (foto2.jpg), 3 (foto3.jpg)"))
        assertTrue(note, note.contains("Con marcas a mano: 3 (foto3.jpg)"))
        assertTrue(note, note.contains("sobre el tablero"))
    }

    @Test fun laterNoteOnlyDescribesWhatChanged() {
        val sent = ChatBoard.arrange("b", images(2)).markSent()
        val first = sent.images[0]
        val edited = sent.copy(images = listOf(first.copy(x = first.x + .1f), sent.images[1].copy(strokes = listOf(BoardStroke(listOf(0f, 0f, 1f, 1f))))))
        val note = ChatBoardExport.note(edited, 2)
        assertTrue(note, note.startsWith("Cambios en el tablero 2"))
        assertTrue(note, note.contains("Movida o redimensionada la imagen"))
        assertTrue(note, note.contains("Marcas nuevas en la imagen"))
        assertFalse(note, note.contains("sobre el tablero"))
    }
}
