package com.vidadomu.nexus.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationDrawerModelTest {
    private val chats = listOf(
        AssistantChatEntry("recent", "Vídeo de Julián", preview = "Una escena en el jardín",
            searchText = "continuamos el montaje por la noche"),
        AssistantChatEntry("older", "Prueba de imagen", preview = "Un póster para Mateo"),
        AssistantChatEntry("closed", "Audio del corto", preview = "Julián ensaya el diálogo"),
    )

    @Test fun searchesAcrossTitleAndPreviewIgnoringAccentsCaseAndExtraSpaces() {
        val result = drawerConversations(chats, emptySet(), emptySet(), false, "  JULIAN jardin  ")
        assertEquals(listOf("recent"), result.map { it.id })
        assertTrue(drawerConversations(chats, emptySet(), emptySet(), false, "jardin mateo").isEmpty())
        assertEquals(listOf("recent"), drawerConversations(chats, emptySet(), emptySet(), false, "JULIAN montaje").map { it.id })
    }

    @Test fun pinnedRowsKeepRecentOrderWithinEachGroupAndNeverDuplicateRows() {
        val result = drawerConversations(chats, emptySet(), setOf("older", "missing"), false, "")
        assertEquals(listOf("older", "recent", "closed"), result.map { it.id })
        assertEquals(chats.size, result.distinctBy { it.id }.size)
    }

    @Test fun closedRowsStaySearchableWhenRequestedAndRestoringOnlyChangesVisibility() {
        assertTrue(drawerConversations(chats, setOf("closed"), setOf("closed"), false, "audio").isEmpty())
        assertEquals(listOf(chats.last()), drawerConversations(chats, setOf("closed"), emptySet(), true, "audio"))
        assertEquals(chats, drawerConversations(chats, emptySet(), emptySet(), false, "   "))
    }
    @Test fun relativeDateUsesTheLocalDayAcrossMidnightRatherThanElapsed24Hours() {
        val zone=java.time.ZoneId.of("Europe/Berlin")
        val now=java.time.ZonedDateTime.of(2026,10,29,0,5,0,0,zone).toInstant().toEpochMilli()
        assertEquals("Ayer",conversationRelativeDate(now-10*60*1000,now,zone))
        assertEquals("Hoy",conversationRelativeDate(now-60*1000,now,zone))
        assertEquals("",conversationRelativeDate(0,now,zone))
    }
}
