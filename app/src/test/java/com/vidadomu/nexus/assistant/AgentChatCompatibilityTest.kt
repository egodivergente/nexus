package com.vidadomu.nexus.assistant

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AgentChatCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun version182ModeSwitchAndMessagesRoundTripWithoutLosingHistory() {
        val raw = """{"items":[{"type":"com.vidadomu.nexus.assistant.ChatItem.User","id":"u","text":"Solicitud","attachments":[]},{"type":"com.vidadomu.nexus.assistant.ChatItem.ModeSwitch","id":"m","text":"Activar agente","resolved":false},{"type":"com.vidadomu.nexus.assistant.ChatItem.Assistant","id":"a","text":"Respuesta","toolCalls":[],"stop":"END_TURN"}],"plans":{},"futureField":true}"""
        val snapshot = json.decodeFromString<SavedAssistantChat>(raw)
        assertEquals(3, snapshot.items.size)
        val switch = snapshot.items[1] as ChatItem.ModeSwitch
        assertFalse(switch.resolved)
        assertEquals("Activar agente", switch.text)
        assertEquals(snapshot, json.decodeFromString<SavedAssistantChat>(json.encodeToString(snapshot)))
    }

    @Test fun resolvedModeSwitchIsPreserved() {
        val snapshot = SavedAssistantChat(listOf(ChatItem.ModeSwitch("m", "Agente", true)), emptyMap())
        val restored = json.decodeFromString<SavedAssistantChat>(json.encodeToString(snapshot))
        assertTrue((restored.items.single() as ChatItem.ModeSwitch).resolved)
    }
}
