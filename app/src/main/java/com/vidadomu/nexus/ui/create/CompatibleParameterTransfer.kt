package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.create.capability.*

/** A destination fallback changed this requested setting; the original stays in its source draft. */
data class ParameterTransferAdaptation(
    val key: String, val label: String, val requestedValue: String, val actualValue: String, val reason: String,
)

internal data class ParameterTransferSource(val capabilities: ModelUiCapabilities, val values: Map<String, String>)
internal fun parameterTransferSource(capabilities: ModelUiCapabilities, values: Map<String, String>,
    adaptations: List<ParameterTransferAdaptation>): ParameterTransferSource {
    val adapted = adaptations.map { it.key }.toSet()
    return ParameterTransferSource(capabilities, values.filterKeys { it !in adapted })
}
internal data class ParameterTransferResult(
    val parameters: Map<String, String>, val transferredKeys: Set<String>, val adaptations: List<ParameterTransferAdaptation>,
)

/** The endpoint may become known only when the requested destination's own schema resolves. */
internal data class PendingParameterTransfer(
    val target: CapabilityKey, val source: ParameterTransferSource, val editedValues: Map<String, String> = emptyMap(),
) {
    fun matches(key: CapabilityKey): Boolean = target.providerId == key.providerId && target.modelId == key.modelId &&
        target.modality == key.modality && (target.endpoint == null || target.endpoint == key.endpoint)
}

internal data class PendingWorkParameters(val target: CapabilityKey, val parameters: Map<String, String>,
    val adaptations: List<ParameterTransferAdaptation> = emptyList()) {
    fun matches(key: CapabilityKey): Boolean = target.providerId == key.providerId && target.modelId == key.modelId &&
        target.modality == key.modality && (target.endpoint == null || target.endpoint == key.endpoint)
}

private enum class PortableParameter { DURATION, ASPECT_RATIO, RESOLUTION }

private fun portableParameter(parameter: ModelParameter): PortableParameter? {
    // Renderer aliases such as size/image_size and keep_original_sound are deliberately not equivalent settings.
    return when (parameter.key.lowercase()) {
        "duration", "duration_seconds", "seconds" -> PortableParameter.DURATION
        "aspect_ratio", "aspectratio", "ratio" -> PortableParameter.ASPECT_RATIO
        "resolution", "target_resolution", "output_resolution" -> PortableParameter.RESOLUTION
        "size", "image_size", "keep_original_sound" -> null
        else -> when (parameter.type) {
            ParameterType.DURATION -> PortableParameter.DURATION
            ParameterType.ASPECT_RATIO -> PortableParameter.ASPECT_RATIO
            ParameterType.RESOLUTION -> PortableParameter.RESOLUTION
            else -> null
        }
    }
}

private fun valueFamily(parameter: ModelParameter): String = when (parameter.nativeType) {
    "integer", "number" -> "number"
    "boolean" -> "boolean"
    "string" -> "string"
    else -> when (parameter.type) {
        ParameterType.INTEGER, ParameterType.FLOAT, ParameterType.SLIDER, ParameterType.SEED -> "number"
        ParameterType.BOOLEAN -> "boolean"
        else -> "string"
    }
}

private fun acceptsTransferValue(parameter: ModelParameter, value: String): Boolean =
    FormContract.invalidValue(listOf(parameter), mapOf(parameter.key to value)) == null &&
        !ParameterBounds.outOfRange(value, ParameterBounds.minimum(parameter), ParameterBounds.maximum(parameter))

/**
 * Preserve the current compatible intent over the destination's own fallback. Values are never converted,
 * rounded or clamped; a destination must formally accept the original string. Joint published dependencies
 * are evaluated before anything is accepted. Unrelated missing prompt/media inputs do not affect transfer.
 */
internal fun transferCompatibleParameters(
    source: ParameterTransferSource,
    destination: ModelUiCapabilities,
    fallback: Map<String, String>,
    resolve: (Map<String, String>) -> ModelUiCapabilities = { destination },
    prompt: String = "",
    media: List<AttachedMedia> = emptyList(),
    editedValues: Map<String, String> = emptyMap(),
): ParameterTransferResult {
    val base = fallback + editedValues
    if (source.capabilities.support != CapabilitySupport.CONFIRMED || destination.support != CapabilitySupport.CONFIRMED)
        return ParameterTransferResult(base, emptySet(), emptyList())
    val sourceParameters = (source.capabilities.primaryParameters + source.capabilities.advancedParameters).distinctBy { it.key }
    val targetParameters = destination.declaredParameters
    val sourceByKey = sourceParameters.associateBy { it.key }
    val sourceByKind = sourceParameters.filter { portableParameter(it) != null }.groupBy(::portableParameter)
    val targetByKind = targetParameters.filter { portableParameter(it) != null }.groupBy(::portableParameter)
    val candidates = linkedMapOf<String, String>()
    val requested = linkedMapOf<String, Pair<ModelParameter, String>>()
    val reserved = setOf("model", "model_id", "provider", "provider_id", "endpoint")
    for (target in targetParameters) {
        if (target.key in editedValues || target.key.lowercase() in reserved || target.key.startsWith("__")) continue
        val kind = portableParameter(target)
        val exact = sourceByKey[target.key]
        val from = exact ?: kind?.takeIf { targetByKind[it]?.size == 1 }?.let { sourceByKind[it]?.singleOrNull() } ?: continue
        val value = source.values[from.key] ?: continue
        if (value.isBlank() || !acceptsTransferValue(from, value)) continue
        val sameMeaning = portableParameter(from)?.let { it == kind } == true
        if (!sameMeaning && valueFamily(from) != valueFamily(target)) continue
        requested[target.key] = target to value
        if (acceptsTransferValue(target, value)) candidates[target.key] = value
    }
    val relevantKinds = setOf(CapabilityIssueKind.PARAMETER, CapabilityIssueKind.DEPENDENCY, CapabilityIssueKind.UNKNOWN)
    fun issues(values: Map<String, String>): List<CapabilityIssue> {
        val resolved = resolve(values)
        if (resolved.key != destination.key || resolved.support != CapabilitySupport.CONFIRMED)
            return listOf(CapabilityIssue(CapabilityIssueKind.UNKNOWN, null, "El contrato de destino no está confirmado."))
        return CapabilityConstraintEngine.evaluate(resolved, prompt, values, media).issues.filter { it.kind in relevantKinds }
    }
    fun signature(issue: CapabilityIssue) = Triple(issue.kind, issue.fieldKey, issue.message)
    val baselineIssues = issues(base).map(::signature).toSet()
    fun compatible(values: Map<String, String>, keys: Set<String>): Boolean = issues(values).none { issue ->
        issue.fieldKey in keys || signature(issue) !in baselineIssues
    }
    // Try the complete intent first: two jointly valid choices must not be rejected by validating them separately.
    val accepted = if (compatible(base + candidates, candidates.keys)) candidates.toMap() else {
        val valid = linkedMapOf<String, String>()
        for ((key, value) in candidates) {
            val trial = valid + (key to value)
            if (compatible(base + trial, trial.keys)) valid[key] = value
        }
        valid
    }
    val result = base + accepted
    val adaptations = requested.filter { (key, value) -> key !in accepted && value.second != result[key] }.map { (key, value) ->
        ParameterTransferAdaptation(key, value.first.displayName, value.second, result[key].orEmpty(),
            "El contrato de destino conserva su ajuste compatible; el valor anterior sigue guardado en el modelo de origen.")
    }
    return ParameterTransferResult(result, accepted.keys, adaptations)
}
