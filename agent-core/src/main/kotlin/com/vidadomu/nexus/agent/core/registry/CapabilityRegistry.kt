package com.vidadomu.nexus.agent.core.registry

import kotlinx.serialization.Serializable

@Serializable data class CapabilityDestination(val provider: String, val model: String, val modelVersion: String,
    val endpoint: String, val workflow: String, val inputField: String = "prompt")
@Serializable enum class PromptUnit { UNICODE_CODE_POINTS, UTF16_CODE_UNITS, UTF8_BYTES, TOKENS }
@Serializable data class PromptCapability(val destination: CapabilityDestination, val maximum: Long,
    val unit: PromptUnit, val source: String, val verifiedAt: Long, val confidence: String = "official_contract",
    val enforcementLocation: String = "provider") {
    fun measured(text: String, tokenCounter: ((String) -> Long)? = null): Long? = when(unit) {
        PromptUnit.UNICODE_CODE_POINTS -> text.codePointCount(0,text.length).toLong()
        PromptUnit.UTF16_CODE_UNITS -> text.length.toLong()
        PromptUnit.UTF8_BYTES -> text.toByteArray(Charsets.UTF_8).size.toLong()
        PromptUnit.TOKENS -> tokenCounter?.invoke(text)
    }
    fun exceeds(text: String): Boolean = measured(text)?.let { it > maximum } == true
    val unitLabel get() = when(unit) {
        PromptUnit.UNICODE_CODE_POINTS -> "caracteres Unicode"
        PromptUnit.UTF16_CODE_UNITS -> "unidades UTF-16"
        PromptUnit.UTF8_BYTES -> "bytes UTF-8"
        PromptUnit.TOKENS -> "tokens"
    }
}

/** Exact destination lookup: a different version, endpoint or workflow is unknown until observed. */
class CapabilityRegistry(records: List<PromptCapability> = emptyList()) {
    private val records = records.associateBy { it.destination }.toMutableMap()
    @Synchronized fun record(value: PromptCapability) {
        require(value.maximum >= 0 && value.source.isNotBlank() && value.verifiedAt > 0)
        records[value.destination] = value
    }
    @Synchronized fun prompt(destination: CapabilityDestination): PromptCapability? = records[destination]
    @Synchronized fun snapshot(): List<PromptCapability> = records.values.toList()
    @Synchronized fun invalidate(destination: CapabilityDestination) { records.remove(destination) }
}
