package com.vidadomu.nexus.cut

import com.vidadomu.nexus.cut.storage.AtomicFile
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CutChatStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun user(id: String, text: String) = CutChatMessage(id, CutChatRole.USER, text, 10)
    private fun assistant(id: String, text: String, error: Boolean = false) = CutChatMessage(id, CutChatRole.ASSISTANT, text, 11, "Cerebro de prueba", error)

    @Test fun chatsAndDraftsStayInsideTheirOwnMontageAndNeverTouchTheMainAssistant() {
        val files = temporary.newFolder("files")
        val assistantFile = File(files, "assistant/conversations.json").apply { parentFile!!.mkdirs(); writeText("main-chat-sentinel") }
        val root = File(files, "cut/projects")
        val first = CutChatStore(root, "cut_one")
        val second = CutChatStore(root, "cut_two")
        first.save(CutChatDocument(messages = listOf(user("u1", "Divide aquí")), draft = "borrador uno"))
        second.save(CutChatDocument(messages = listOf(user("u2", "Ajusta el volumen")), draft = "borrador dos"))
        assertEquals("borrador uno", CutChatStore(root, "cut_one").load().draft)
        assertEquals("Divide aquí", first.load().messages.single().text)
        assertEquals("borrador dos", second.load().draft)
        assertEquals("Ajusta el volumen", second.load().messages.single().text)
        assertEquals("main-chat-sentinel", assistantFile.readText())
        assertEquals(File(root, "cut_one/chat-v1.json").canonicalFile, first.file.canonicalFile)
    }

    @Test fun aPendingTurnBecomesOneNoticeAndARetryDraftWithoutAnExecutableRequest() {
        val root = temporary.newFolder()
        val store = CutChatStore(root, "montage", clock = { 100 }, newId = { "interrupted-notice" })
        store.save(CutChatDocument(messages = listOf(user("pending-user", "Exporta a 720p")), pending = PendingCutChatTurn("pending-user", 10)))
        val restored = store.load()
        assertNull(restored.pending)
        assertEquals("Exporta a 720p", restored.draft)
        assertEquals(2, restored.messages.size)
        assertEquals(CutChatRole.NOTICE, restored.messages.last().role)
        assertTrue(restored.messages.last().text.contains("no se ha reenviado"))
        assertTrue(restored.completedTurns().isEmpty())
        assertEquals(restored, CutChatStore(root, "montage").load())
    }

    @Test fun recoveryKeepsAnAlreadyTypedNextDraftAndOnlyRestoresAnsweredTextTurns() {
        val root = temporary.newFolder()
        val store = CutChatStore(root, "montage")
        store.save(CutChatDocument(
            messages = listOf(user("u1", "Divide"), assistant("a1", "Dividido"), user("u2", "Exporta")),
            draft = "Otro cambio todavía sin enviar", pending = PendingCutChatTurn("u2", 12),
        ))
        val restored = store.load()
        assertEquals("Otro cambio todavía sin enviar", restored.draft)
        assertEquals(listOf("user" to "Divide", "assistant" to "Dividido"), restored.completedTurns())
    }

    @Test fun partialRepliesAndNoticesNeverCompleteAnInterruptedUserTurnInProviderHistory() {
        val document = CutChatDocument(messages = listOf(
            user("u0", "Petición que falló"), assistant("partial", "Texto recibido antes del corte", error = true),
            CutChatMessage("notice", CutChatRole.NOTICE, "Cancelado", 12),
            user("u1", "Petición nueva explícita"), assistant("a1", "Respuesta completa"),
        ))
        assertEquals(listOf("user" to "Petición nueva explícita", "assistant" to "Respuesta completa"), document.completedTurns())
    }

    @Test fun persistenceRedactsTextsAndNeverContainsProviderToolsOrSettings() {
        val store = CutChatStore(temporary.newFolder(), "montage")
        val secret = "api_key=abcdefghijklmnopqrstuvwxyz123456789"
        store.save(CutChatDocument(messages = listOf(user("u", secret), assistant("a", "authorization: bearer private_token_123")), draft = secret))
        val raw = store.file.readText()
        assertFalse(raw.contains("abcdefghijklmnopqrstuvwxyz123456789"))
        assertFalse(raw.contains("private_token_123"))
        assertFalse(raw.contains("tool_calls"))
        assertFalse(raw.contains("settings"))
        assertTrue(store.load().draft.contains("oculto"))
    }

    @Test fun unknownSavedToolFieldsAreNotRestoredAndCannotBecomeAReplay() {
        val store = CutChatStore(temporary.newFolder(), "montage")
        store.file.parentFile!!.mkdirs()
        store.file.writeText("""{"schema":1,"messages":[{"id":"u","role":"USER","text":"Exporta","at":1,"tool_calls":[{"name":"render.export"}]}],"draft":"","pending":{"userMessageId":"u","startedAt":1},"providerHistory":[{"tool":"cut_edit"}]}""")
        val restored = store.load()
        assertNull(restored.pending)
        assertTrue(restored.completedTurns().isEmpty())
        assertFalse(store.file.readText().contains("tool_calls"))
        assertFalse(store.file.readText().contains("providerHistory"))
    }

    @Test fun aCorruptPrimaryRecoversThePreviousCompleteChatAndRefusesToReplaceBothCorruptCopies() {
        val root = temporary.newFolder()
        val store = CutChatStore(root, "montage")
        store.save(CutChatDocument(draft = "borrador válido"))
        store.save(CutChatDocument(draft = "otro borrador"))
        store.file.writeText("incomplete")
        assertEquals("borrador válido", store.load().draft)
        AtomicFile.backupOf(store.file).writeText("also incomplete")
        try { store.load(); throw AssertionError("Expected a read failure") }
        catch (_: IOException) { }
        assertEquals("incomplete", store.file.readText())
    }

    @Test fun aProjectIdCannotEscapeIntoTheMainAssistantDirectory() {
        for (id in listOf("../assistant", "..", "a/b", "a\\b", "")) {
            try { CutChatStore(temporary.root, id); throw AssertionError("Accepted path: $id") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
