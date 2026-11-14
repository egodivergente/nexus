package com.vidadomu.nexus.assistant

import org.junit.Assert.*
import org.junit.Test

class ChatCopyTest {
    @Test fun selectedMessagesKeepConversationOrderAndCompleteText() {
        val long = "Texto íntegro\n".repeat(3000)
        val entries = listOf(ChatCopyEntry("1", "Tú", "Pregunta"), ChatCopyEntry("2", "Nexus", long), ChatCopyEntry("3", "Tú", "Otra"))
        assertEquals("Tú\nPregunta\n\nNexus\n$long", selectedChatText(entries, linkedSetOf("2", "1")))
        assertEquals("", selectedChatText(entries, emptySet()))
        assertTrue(selectedChatText(entries, entries.map { it.id }.toSet()).endsWith("Tú\nOtra"))
    }
}
