package com.vidadomu.nexus.settings

import com.vidadomu.nexus.agent.core.registry.*
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.assistant.ProviderPromptLimits

object ModelCapabilities {
    val registry = CapabilityRegistry()
    fun destination(model: NexusModel) = CapabilityDestination(model.providerId,model.providerModelId ?: model.id,
        model.providerModelId ?: model.id,model.schemaUrl ?: model.contractEndpoint ?: model.id,
        model.inputMode.name,model.contractPromptField)
    fun prompt(model: NexusModel): PromptCapability? {
        val destination=destination(model)
        val limit=ProviderPromptLimits.forModel(model)
        val maximum=limit?.maximum ?: run { registry.invalidate(destination); return null }
        val unit=when(limit.unit) {
            "unicode_code_points" -> PromptUnit.UNICODE_CODE_POINTS
            "utf16_code_units" -> PromptUnit.UTF16_CODE_UNITS
            "utf8_bytes" -> PromptUnit.UTF8_BYTES
            else -> return null
        }
        return PromptCapability(destination,maximum.toLong(),unit,
            model.schemaUrl ?: limit.source.orEmpty(),java.time.Instant.parse("2026-10-23T00:00:00Z").toEpochMilli()).also(registry::record)
    }
}
