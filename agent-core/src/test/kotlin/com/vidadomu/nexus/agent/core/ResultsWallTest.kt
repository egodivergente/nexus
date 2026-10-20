package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.tools.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class ResultsWallTest {
    private fun asset(id: String, type: AssetType = AssetType.IMAGE, deleted: Boolean = false) =
        Asset(id,"file:///results/$id",type,displayName=id,createdAt=1,libraryDeleted=deleted)
    private fun input(vararg ids: String) = buildJsonObject { put("asset_ids",JsonArray(ids.map(::JsonPrimitive))) }

    @Test fun `opens selected images and videos in order without attaching or spending`() = runTest {
        val h=Harness()
        h.assets.addAsset(asset("portrait")); h.assets.addAsset(asset("video",AssetType.VIDEO))
        val session=AgentSession("chat",AgentMode.AGENT)
        val result=OpenResultsWallTool(h.deps).run(input("video","portrait","video"),session)
        assertFalse(result.isError)
        val media=(result.events.single() as UiEvent.ResultsWall).media
        assertEquals(listOf("video","portrait"),media.map {it.id})
        assertEquals("video/mp4",media.first().mimeType)
        assertTrue(session.attachments.isEmpty())
        assertTrue(h.backend.submitted.isEmpty())
    }

    @Test fun `empty missing deleted and nonvisual selections never open partial walls`() = runTest {
        val h=Harness()
        h.assets.addAsset(asset("ok")); h.assets.addAsset(asset("deleted",deleted=true)); h.assets.addAsset(asset("audio",AssetType.AUDIO))
        val tool=OpenResultsWallTool(h.deps)
        for(args in listOf(input(),input("ok","missing"),input("deleted"),input("audio"))) {
            val result=tool.run(args,AgentSession("chat",AgentMode.AGENT))
            assertTrue(result.isError); assertTrue(result.events.isEmpty())
        }
        assertTrue(h.backend.submitted.isEmpty())
    }
}
