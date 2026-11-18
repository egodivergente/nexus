package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.tools.AgentStep
import com.vidadomu.nexus.agent.core.tools.AgentStepKind
import com.vidadomu.nexus.agent.core.tools.AgentStepStatus
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

    @Test fun chatsSavedBeforeTheActivityTimelineOpenWithoutSteps() {
        val raw = """{"items":[{"type":"com.vidadomu.nexus.assistant.ChatItem.Assistant","id":"a","text":"Respuesta","toolCalls":["search_assets"],"stop":"END_TURN","by":"AGY"},{"type":"com.vidadomu.nexus.assistant.ChatItem.Notice","id":"n","text":"Detenido","error":false}],"plans":{}}"""
        val snapshot = json.decodeFromString<SavedAssistantChat>(raw)
        val answer = snapshot.items[0] as ChatItem.Assistant
        assertTrue(answer.steps.isEmpty())
        assertEquals("las herramientas guardadas siguen ahí para la actividad heredada", listOf("search_assets"), answer.toolCalls)
        assertTrue((snapshot.items[1] as ChatItem.Notice).steps.isEmpty())
        assertEquals(snapshot, json.decodeFromString<SavedAssistantChat>(json.encodeToString(snapshot)))
    }

    @Test fun activityIsSavedWithTheAnswerAndWithTheNoticeThatEndedATurn() {
        val steps = listOf(
            AgentStep("t1:llm:1", AgentStepKind.THINK, "Pensando", AgentStepStatus.DONE, startedAt = 1_700_000_000_000, durationMs = 1_840),
            AgentStep("t1:tool:1", AgentStepKind.TOOL, "Buscando en tu Biblioteca", AgentStepStatus.ISSUE, tool = "search_assets",
                startedAt = 1_700_000_001_900, durationMs = 212, command = "search_assets text=faro project=x",
                input = "{\n  \"text\": \"faro\"\n}", result = "No existe el proyecto x."),
            AgentStep("t1:llm:2", AgentStepKind.THINK, "Pensando", AgentStepStatus.CANCELLED, startedAt = 1_700_000_002_200, durationMs = 400, result = "Detenido por ti."),
        )
        val snapshot = SavedAssistantChat(listOf(
            ChatItem.User("u", "Busca el faro", emptyList()),
            ChatItem.Assistant("a", "Hecho", listOf("search_assets"), "END_TURN", steps = steps.take(2)),
            ChatItem.Notice("n", "Respuesta detenida.", steps = steps),
        ), emptyMap())
        val restored = json.decodeFromString<SavedAssistantChat>(json.encodeToString(snapshot))
        assertEquals(snapshot, restored)
        val issue = (restored.items[1] as ChatItem.Assistant).steps.single { it.status == AgentStepStatus.ISSUE }
        assertEquals("No existe el proyecto x.", issue.result)
        assertEquals(212L, issue.durationMs)
        assertTrue("el detalle sigue disponible al reabrir", issue.hasDetail)
        assertEquals(AgentStepStatus.CANCELLED, (restored.items[2] as ChatItem.Notice).steps.last().status)
    }

    /** A build without the timeline (vc221 and older) decodes with ignoreUnknownKeys: the new field must not break it. */
    @Test fun aChatWithActivityStillOpensWhereTheFieldIsUnknown() {
        val encoded = json.encodeToString(SavedAssistantChat(listOf(ChatItem.Assistant("a", "Hecho", emptyList(), "END_TURN",
            steps = listOf(AgentStep("s", AgentStepKind.TOOL, "Buscando", AgentStepStatus.DONE, durationMs = 5)))), emptyMap()))
        assertTrue(encoded.contains("\"steps\""))
        val older = encoded.replace("\"steps\"", "\"campoDeUnaVersionPosterior\"")
        val opened = json.decodeFromString<SavedAssistantChat>(older).items.single() as ChatItem.Assistant
        assertEquals("Hecho", opened.text)
        assertTrue(opened.steps.isEmpty())
    }

    @Test fun resolvedModeSwitchIsPreserved() {
        val snapshot = SavedAssistantChat(listOf(ChatItem.ModeSwitch("m", "Agente", true)), emptyMap())
        val restored = json.decodeFromString<SavedAssistantChat>(json.encodeToString(snapshot))
        assertTrue((restored.items.single() as ChatItem.ModeSwitch).resolved)
    }
}
