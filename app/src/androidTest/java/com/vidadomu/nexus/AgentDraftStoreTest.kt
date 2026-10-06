package com.vidadomu.nexus

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.agent.AgentDraft
import com.vidadomu.nexus.agent.AgentDraftStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AgentDraftStoreTest {
    private fun isolatedTest(test:(ContextWrapper)->Unit) {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(base.cacheDir,"agent-store-test-"+UUID.randomUUID()).apply {mkdirs()}
        val context=object:ContextWrapper(base) {override fun getFilesDir()=folder}
        try {test(context)} finally {folder.deleteRecursively()}
    }
    @Test fun preparedDraftSurvivesReadAndCanBeFoundAndDiscarded()=isolatedTest {context ->
        val draft=AgentDraft(UUID.randomUUID().toString(),"atlas_cloud","fixture-motion","Prueba local",
            mapOf("character_orientation" to "video","keep_original_sound" to "false"))
        AgentDraftStore.save(context,draft)
        assertEquals(draft,AgentDraftStore.read(context,draft.id))
        assertEquals(listOf(draft),AgentDraftStore.list(context))
        assertTrue(AgentDraftStore.discard(context,draft.id))
        assertTrue(AgentDraftStore.list(context).isEmpty())
    }
    @Test fun cannotOverwriteDraftOrEscapePrivateFolder()=isolatedTest {context ->
        val draft=AgentDraft(UUID.randomUUID().toString(),"fixture","model","Original",emptyMap())
        AgentDraftStore.save(context,draft)
        assertTrue(runCatching {AgentDraftStore.save(context,draft.copy(prompt="Replacement"))}.isFailure)
        assertEquals("Original",AgentDraftStore.read(context,draft.id).prompt)
        assertTrue(runCatching {AgentDraftStore.read(context,"../outside")}.isFailure)
        assertFalse(File(context.filesDir,"agent_drafts/${draft.id}.json.tmp").exists())
    }
}
