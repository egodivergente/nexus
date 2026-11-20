package com.vidadomu.nexus.assistant

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFailureFeedbackTest {
    private val storeJson=Json {ignoreUnknownKeys=true}

    @Test
    fun asynchronousLegacyHistoryRestoreDoesNotAnnounceOldFailure() {
        // Same polymorphic envelope and Json options used by AssistantChatStore, without the new field.
        val chat=storeJson.decodeFromString<SavedAssistantChat>("""{"items":[{"type":"com.vidadomu.nexus.assistant.ChatItem.Notice","id":"old","text":"Proveedor no disponible","error":true}],"plans":{}}""")
        val restored=chat.items.single() as ChatItem.Notice
        assertEquals(0L,restored.at)
        assertFalse(shouldAnnounceChatFailure(restored,enteredAt=100,shownId=null))
    }

    @Test
    fun newTimestampSurvivesTheStoreSnapshotRoundTrip() {
        val failure=ChatItem.Notice("new","El asistente no respondió",error=true,at=101)
        val chat=SavedAssistantChat(items=listOf(failure),plans=emptyMap(),conversationId="chat")
        val restored=storeJson.decodeFromString<SavedAssistantChat>(storeJson.encodeToString(chat))
        assertEquals(failure,restored.items.single())
        assertTrue(shouldAnnounceChatFailure(restored.items.single() as ChatItem.Notice,enteredAt=100,shownId=null))
    }

    @Test
    fun newErrorsStillAnnounceIncludingAttachmentErrorsWithoutAUserMessage() {
        val failure=ChatItem.Notice("new","No se pudo adjuntar",error=true,at=101)
        assertTrue(shouldAnnounceChatFailure(failure,enteredAt=100,shownId="previous"))
        assertFalse(shouldAnnounceChatFailure(failure,enteredAt=100,shownId=failure.id))
    }

    @Test
    fun returningToConversationDoesNotReannounceItsTimestampedFailure() {
        val failure=ChatItem.Notice("earlier","El asistente no respondió",error=true,at=101)
        assertFalse(shouldAnnounceChatFailure(failure,enteredAt=200,shownId=null))
    }

    @Test
    fun boundaryAndInformationalNoticesStaySilent() {
        assertFalse(shouldAnnounceChatFailure(ChatItem.Notice("boundary","Error",error=true,at=100),enteredAt=100,shownId=null))
        assertFalse(shouldAnnounceChatFailure(ChatItem.Notice("info","Guardado",at=101),enteredAt=100,shownId=null))
    }

    private val ask=ChatItem.User("u1","Busca planos del faro",emptyList())
    private val reply=ChatItem.Assistant("a1","Aquí están.",emptyList(),"STOP")
    private val failed=ChatItem.Notice("f1","El modelo ha rechazado la petición.",error=true,at=101)

    @Test
    fun theLastFailureKeepsItsCardUntilTheUserSendsSomethingElse() {
        assertEquals("f1",currentFailureId(listOf(ask,failed)))
        // What the agent or the app add after it (a note, a card of the same turn) does not move the conversation on.
        assertEquals("f1",currentFailureId(listOf(ask,failed,ChatItem.Notice("n1","Guardado"),ChatItem.PlanCard("c1","p1"))))
        // The next message does, also when it is the same one sent again by «Reintentar».
        assertNull(currentFailureId(listOf(ask,failed,ask.copy(id="u2"))))
        assertNull(currentFailureId(listOf(ask,failed,ask.copy(id="u2"),reply)))
    }

    @Test
    fun aNewerFailureTakesTheCardAndOlderOnesFold() {
        val again=failed.copy(id="f2",at=202)
        assertEquals("f2",currentFailureId(listOf(ask,failed,ask.copy(id="u2"),again)))
        assertEquals("un error al adjuntar, sin mensaje detrás, también es el de ahora","f3",
            currentFailureId(listOf(ask,reply,ChatItem.Notice("f3","No se pudo adjuntar",error=true,at=303))))
    }

    @Test
    fun aConversationWithoutFailuresHasNone() {
        assertNull(currentFailureId(emptyList()))
        assertNull(currentFailureId(listOf(ask,reply,ChatItem.Notice("n1","Cancelado."))))
    }
}
