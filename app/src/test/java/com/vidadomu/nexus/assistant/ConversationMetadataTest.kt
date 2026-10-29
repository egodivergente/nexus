package com.vidadomu.nexus.assistant

import org.junit.Assert.*
import org.junit.Test

class ConversationMetadataTest {
    @Test fun indexesMiddleMessagesBeyondTheVisiblePreviewWithAccentInsensitiveSearch() {
        val metadata=conversationMetadata(listOf(ChatItem.User("first","Título de conversación",emptyList()),
            ChatItem.User("middle","Contenido único: resolución",emptyList()),ChatItem.Assistant("last","Respuesta más reciente",emptyList(),"END_TURN")))
        assertEquals("Título de conversación",metadata.title)
        assertEquals("Respuesta más reciente",metadata.preview)
        assertTrue(metadata.searchText.contains(normalizeConversationSearch("ÚNICO: RESOLUCIÓN")))
    }
    @Test fun keepsEnoughTitleForTwoLinesWithoutTechnicalPrefixes() {
        val title="Conversación sobre un proyecto largo con decisiones y archivos que deben poder distinguirse del resto"
        val metadata=conversationMetadata(listOf(ChatItem.User("id",title,emptyList())))
        assertEquals(title,metadata.title);assertFalse(metadata.title.startsWith("↔"))
    }
    @Test fun emptyConversationHasAnExplicitEmptyState() {
        assertEquals(ConversationMetadata("Nueva conversación","",""),conversationMetadata(emptyList()))
    }
}
