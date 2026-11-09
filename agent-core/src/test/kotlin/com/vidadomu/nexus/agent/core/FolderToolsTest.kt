package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.tools.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/** Each project is a Library folder: the agent moves many files into one, and back out, in a single call. */
class FolderToolsTest {
    @Test fun `move many files into a folder and back out`() = runTest {
        val h = Harness()
        val session = AgentSession("turn", AgentMode.AGENT)
        val folder = h.assets.createProject("Playa")
        val ids = (1..3).map { i -> h.assets.addAsset(Asset("a$i", "file:///f$i.jpg", AssetType.IMAGE, displayName = "f$i.jpg", createdAt = i.toLong())).id }

        val moved = MoveAssetTool(h.deps).run(buildJsonObject { put("asset_ids", JsonArray(ids.map(::JsonPrimitive))); put("project", "Playa") }, session)
        assertFalse(moved.isError)
        assertTrue(ids.all { h.assets.asset(it)?.projectId == folder.id })

        val out = MoveAssetTool(h.deps).run(buildJsonObject { put("asset_ids", JsonArray(ids.take(2).map(::JsonPrimitive))); put("project", "ninguno") }, session)
        assertFalse(out.isError)
        assertEquals(listOf(null, null, folder.id), ids.map { h.assets.asset(it)?.projectId })
    }
}
