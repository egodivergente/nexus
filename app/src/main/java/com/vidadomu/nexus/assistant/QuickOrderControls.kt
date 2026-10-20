package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.registry.ModelSchema
import com.vidadomu.nexus.agent.core.spend.GenerationPlan

internal data class QuickOrderControl(val key: String, val label: String, val value: String, val options: List<String>)

internal fun quickOrderControls(plan: GenerationPlan, schema: ModelSchema?): List<QuickOrderControl> {
    if (schema == null || schema.ref != plan.ref) return emptyList()
    return schema.parameters.filter { spec ->
        !spec.advanced && (spec.uiType in setOf("ASPECT_RATIO", "DURATION", "RESOLUTION") ||
            spec.key in setOf("aspect_ratio", "aspectRatio", "ratio", "duration", "resolution", "size", "quality", "num_outputs", "count"))
    }.mapNotNull { spec ->
        val value = plan.effectiveParams[spec.key]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        QuickOrderControl(spec.key, spec.displayName, value, spec.options.distinct())
    }
}
