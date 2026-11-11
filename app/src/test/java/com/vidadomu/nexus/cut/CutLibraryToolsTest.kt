package com.vidadomu.nexus.cut

import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.tools.AgentSession
import com.vidadomu.nexus.agent.core.tools.NativeOperations
import com.vidadomu.nexus.agent.core.tools.ToolCategory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CutLibraryToolsTest {
    @Test fun onlyThePagedLocalLibraryReadIsExposedFromNativeOperations() = runTest {
        val calls = mutableListOf<String>()
        val tools = cutLibraryNativeTools(NativeOperations { operation, _, _ ->
            calls += operation
            buildJsonObject { put("total", 0) }
        })
        assertEquals(listOf("list_library_assets"), tools.map { it.name })
        assertTrue(tools.all { it.category == ToolCategory.READ })
        assertTrue(calls.isEmpty())
        val result = tools.single().run(buildJsonObject { put("offset", 20); put("limit", 10) }, AgentSession("cut-chat", AgentMode.AGENT))
        assertFalse(result.isError)
        assertEquals(listOf("list_library_assets"), calls)
    }

    @Test fun unavailableNativeOperationsDoNotAdvertiseAnUnsupportedRead() {
        assertTrue(cutLibraryNativeTools(null).isEmpty())
    }
}
