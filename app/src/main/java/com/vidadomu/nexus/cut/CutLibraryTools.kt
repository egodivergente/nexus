package com.vidadomu.nexus.cut

import com.vidadomu.nexus.agent.core.tools.AgentTool
import com.vidadomu.nexus.agent.core.tools.InspectAssetTool
import com.vidadomu.nexus.agent.core.tools.NativeOperationTools
import com.vidadomu.nexus.agent.core.tools.NativeOperations
import com.vidadomu.nexus.agent.core.tools.OpenResultsWallTool
import com.vidadomu.nexus.agent.core.tools.SearchAssetsTool
import com.vidadomu.nexus.agent.core.tools.ToolDeps

/** Library reads and a frontend presentation event, without generation or native mutation tools. */
internal fun cutLibraryTools(deps: ToolDeps): List<AgentTool> =
    listOf(SearchAssetsTool(deps), InspectAssetTool(deps), OpenResultsWallTool(deps)) + cutLibraryNativeTools(deps.nativeOperations)

internal fun cutLibraryNativeTools(operations: NativeOperations?): List<AgentTool> =
    operations?.let { NativeOperationTools.all(it).filter { tool -> tool.name == "list_library_assets" } }.orEmpty()
