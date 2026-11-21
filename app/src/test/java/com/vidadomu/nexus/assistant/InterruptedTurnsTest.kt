package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.tools.Attachment
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La marca de turno en curso: se pone al empezar, se quita al acabar y sobrevive a un reinicio leyendo de disco. */
class InterruptedTurnsTest {
    private val folder: File = Files.createTempDirectory("interrupted-turns").toFile()
    private val photo = Attachment("att1", "file:///data/user/0/com.vidadomu.nexus/files/agent/a.jpg", "image/jpeg", "a.jpg", 1024, 768)

    @After fun cleanUp() { folder.deleteRecursively() }

    private fun turn(chat: String = "chat-1", id: String = "t1", text: String = "Haz un vídeo", attachments: List<Attachment> = listOf(photo)) =
        InterruptedTurn(chat, id, text, attachments, startedAt = 1_000L)

    @Test fun aTurnInProgressIsReadBackAsItWasSaved() {
        val store = InterruptedTurnStore(folder)
        store.begin(turn())
        assertEquals(turn(), store.get("chat-1"))
    }

    @Test fun theMarkSurvivesARestart() {
        InterruptedTurnStore(folder).begin(turn())
        // Otra instancia sobre la misma carpeta: lo que ve la app al volver tras morir el proceso.
        val reopened = InterruptedTurnStore(folder).get("chat-1")
        assertEquals("Haz un vídeo", reopened?.text)
        assertEquals(listOf(photo), reopened?.attachments)
        assertEquals("interrupted_t1", reopened?.noticeId)
    }

    @Test fun aTurnThatEndedLeavesNoMark() {
        val store = InterruptedTurnStore(folder)
        store.begin(turn())
        store.clear("chat-1", "t1")
        assertNull(store.get("chat-1"))
        assertNull(InterruptedTurnStore(folder).get("chat-1"))
    }

    @Test fun theLateEndOfAnOldTurnDoesNotRemoveTheMarkOfTheNextOne() {
        val store = InterruptedTurnStore(folder)
        store.begin(turn(id = "t1"))
        store.begin(turn(id = "t2", text = "Otra cosa"))
        store.clear("chat-1", "t1")
        assertEquals("t2", store.get("chat-1")?.turnId)
        assertEquals("Otra cosa", store.get("chat-1")?.text)
    }

    @Test fun eachConversationKeepsItsOwnMark() {
        val store = InterruptedTurnStore(folder)
        store.begin(turn(chat = "chat-1"))
        store.begin(turn(chat = "chat-2", id = "t9"))
        store.clear("chat-1", "t1")
        assertNull(store.get("chat-1"))
        assertEquals("t9", store.get("chat-2")?.turnId)
    }

    @Test fun anUnsafeConversationIdIsNeverWrittenOutsideTheFolder() {
        val store = InterruptedTurnStore(folder)
        store.begin(turn(chat = "../fuera"))
        assertNull(store.get("../fuera"))
        assertFalse(File(folder.parentFile, "fuera.json").exists())
    }

    @Test fun aDamagedMarkReadsAsNone() {
        folder.mkdirs()
        File(folder, "chat-1.json").writeText("{no es json")
        assertNull(InterruptedTurnStore(folder).get("chat-1"))
    }

    @Test fun theNoticeIsAddedOnceAtTheEnd() {
        val items = listOf<ChatItem>(ChatItem.User("u1", "Haz un vídeo", listOf(photo)))
        val once = InterruptedTurns.withNotice(items, turn(), InterruptedTurns.NOTICE_TEXT, now = 5L)
        val twice = InterruptedTurns.withNotice(once, turn(), InterruptedTurns.NOTICE_TEXT, now = 6L)
        assertEquals(2, once.size)
        val notice = once.last() as ChatItem.Notice
        assertEquals("interrupted_t1", notice.id)
        assertTrue(notice.error)
        assertTrue(InterruptedTurns.isNotice(notice.id))
        assertEquals(once, twice)
    }

    @Test fun theNoticeSaysWhichAttachmentsAreGone() {
        assertEquals("El turno se cortó.", InterruptedTurns.noticeText(lost = 0, kept = 1))
        assertEquals("El turno se cortó. Un adjunto ya no está; Reintentar enviará el resto.", InterruptedTurns.noticeText(lost = 1, kept = 2))
        assertEquals("El turno se cortó. 2 adjuntos ya no están; Reintentar enviará solo el texto.", InterruptedTurns.noticeText(lost = 2, kept = 0))
    }

    @Test fun theCardShowsTheCutAsItsCauseAndNotAGenericError() {
        val plain = agentErrorNoticeContent(InterruptedTurns.NOTICE_TEXT, canRetry = true)
        assertEquals("El turno se cortó.", plain.cause)
        assertEquals("Reintentar usa el mismo texto y los mismos adjuntos.", plain.status)
        assertEquals(InterruptedTurns.TECHNICAL, plain.technical)
        val lost = agentErrorNoticeContent(InterruptedTurns.noticeText(lost = 1, kept = 0), canRetry = true)
        assertEquals("Un adjunto ya no está; Reintentar enviará solo el texto.", lost.status)
        // Reabierto más tarde, sin la marca, el aviso queda como constancia y no promete nada.
        assertNull(agentErrorNoticeContent(InterruptedTurns.NOTICE_TEXT, canRetry = false).status)
    }
}
