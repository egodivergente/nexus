package com.vidadomu.nexus.ui.create.capability

import com.vidadomu.nexus.core.model.AttachedMedia

internal data class ModelPreferences(val values: Map<String, String> = emptyMap())

/** Shared source material, independent model choices. Changing a destination cannot rewrite originals. */
internal data class ComposerState(
    val prompt: String = "", val resources: List<AttachedMedia> = emptyList(),
    val preferences: Map<CapabilityKey, ModelPreferences> = emptyMap()
) {
    fun withValues(key: CapabilityKey, values: Map<String, String>) =
        copy(preferences = preferences + (key to ModelPreferences(values.toMap())))

    fun evaluate(capabilities: ModelUiCapabilities): CapabilityValidation = CapabilityConstraintEngine.evaluate(
        capabilities, prompt, preferences[capabilities.key]?.values.orEmpty(), resources)

    /** Each model receives its own full contract/defaults; there is no intersection or common minimum. */
    fun evaluateAll(models: List<ModelUiCapabilities>): Map<CapabilityKey, CapabilityValidation> =
        models.associate { it.key to evaluate(it) }
}
