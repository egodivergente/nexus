package com.vidadomu.nexus.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the list stops when it follows the conversation: its last row rests above the room kept under it (the handle
 * on the composer's edge and the notice on show), never against the composer.
 */
class ChatFollowTest {
    @Test fun theLastRowRestsAboveTheRoomKeptUnderIt() {
        // A list 2000 px tall that keeps 84 px free: the reading area ends at 1916.
        assertEquals("lo que sobresale de la zona de lectura", 100, followDistance(lastRowEnd = 2016, viewportEnd = 2000, roomBelow = 84))
        assertEquals("una fila que llega al borde de la lista aún tapa el hueco", 84, followDistance(2000, 2000, 84))
        assertEquals("un aviso a la vista sube el final de la zona de lectura", 84 + 168, followDistance(2000, 2000, 84 + 168))
    }

    @Test fun aRowThatAlreadyRestsThereDoesNotMove() {
        assertEquals(0, followDistance(lastRowEnd = 1916, viewportEnd = 2000, roomBelow = 84))
        assertEquals("una conversación corta no baja", 0, followDistance(600, 2000, 84))
        assertEquals("sin hueco, el borde de la lista es el final", 0, followDistance(2000, 2000, 0))
    }
}
