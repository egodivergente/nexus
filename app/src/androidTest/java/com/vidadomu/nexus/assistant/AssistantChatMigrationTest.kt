package com.vidadomu.nexus.assistant

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AssistantChatMigrationTest {
    @Test fun legacyHistoryIsPreservedAndNewSnapshotReopensWithoutChangingLegacyFile() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(base.cacheDir,"chat-migration-test").apply { mkdirs() }
        val context = object: ContextWrapper(base) { override fun getFilesDir() = folder }
        val legacy = File(folder,"assistant/chat.json").apply { parentFile?.mkdirs() }
        val source = """{"items":[{"kind":"user","id":"u","text":"Mateo","attachments":[{"id":"ref","uri":"file:///reference.png","mimeType":"image/png"}]},{"kind":"assistant","id":"a","text":"Respuesta"},{"kind":"plan","id":"pc","planId":"p"}],"plans":[{"plan":{"id":"p","ref":{"providerId":"google_flow","modelId":"current_project_image"},"modelName":"Flow","modality":"IMAGE","prompt":"Referencia","params":{},"effectiveParams":{},"createdAt":1},"state":"SUBMITTED","jobId":"job","jobStatus":"COMPLETED"}],"transcript":[],"attachments":[{"id":"ref","uri":"file:///reference.png","mimeType":"image/png"}]}"""
        try {
            legacy.writeText(source)
            val store = AssistantChatStore(context)
            val migrated = requireNotNull(store.load())
            assertEquals(3,migrated.items.size)
            assertEquals("job",migrated.plans["p"]?.jobId)
            assertTrue(migrated.attachments.isEmpty())
            assertEquals("ref",migrated.sessionAttachments.single().id)
            store.save(AssistantUiState(items=migrated.items,plans=migrated.plans))
            assertEquals(migrated,AssistantChatStore(context).load())
            assertEquals(source,legacy.readText())
            store.archive(AssistantUiState(items=migrated.items,plans=migrated.plans))
            val entry = store.conversations().single()
            assertEquals("Mateo",entry.title)
            assertEquals(migrated,store.conversation(entry.id))
        } finally { folder.deleteRecursively() }
    }
}
