package com.vidadomu.nexus.agent.core.tools

/** Catálogo de herramientas del asistente. No existe ninguna que confirme o ejecute un gasto. */
object NexusToolkit {
    fun all(d: ToolDeps): List<AgentTool> = listOf(
        ListModelsTool(d), SearchModelsTool(d), GetModelTool(d), GetModelSchemaTool(d), RecommendModelsTool(d),
        ValidateParametersTool(d), EstimateCostTool(d), ListSkillsTool(d), LoadSkillTool(d), CreateGenerationPromptTool(d), CompareModelFamiliesTool(d),
        PrepareGenerationTool(d), PrepareOrdersTool(d), PrepareImageUpscaleTool(d), UpdatePlanTool(d), SubmitGenerationTool(d), GetGenerationStatusTool(d), CancelGenerationTool(d), GetUiStateTool(d), SuggestRepliesTool(),
        ListProjectsTool(d), CreateProjectTool(d), OpenProjectTool(d), GetProjectOverviewTool(d), SearchProjectMemoryTool(d), UpdateProjectMemoryTool(d), SearchAssetsTool(d), OpenResultsWallTool(d), InspectAssetTool(d), ListShotsTool(d),
        GenerationHistoryTool(d), AssociateAssetTool(d), MoveAssetTool(d), RenameAssetTool(d),
    ) + StoryTools.all(d) + (d.cut?.let(CutTools::all) ?: emptyList()) + ProjectTransferTools.all(d) + (d.nativeOperations?.let(NativeOperationTools::all) ?: emptyList())
}
