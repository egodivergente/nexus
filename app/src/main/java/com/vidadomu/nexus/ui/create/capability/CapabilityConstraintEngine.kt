package com.vidadomu.nexus.ui.create.capability

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.NativeModelSchema
import com.vidadomu.nexus.ui.create.CanvasInputPlan
import java.math.BigDecimal

internal enum class CapabilityIssueKind { MODEL, PROMPT, MEDIA, PARAMETER, DEPENDENCY, UNKNOWN }
internal data class CapabilityIssue(val kind: CapabilityIssueKind, val fieldKey: String?, val message: String)

/** The source prompt and all draft values stay available even when an endpoint does not consume them. */
internal data class EffectiveModelConfiguration(
    val key: CapabilityKey, val originalPrompt: String, val requestPrompt: String?,
    val parameters: Map<String, String>, val inactiveParameters: Map<String, String>, val media: List<AttachedMedia>
)

internal data class CapabilityValidation(val configuration: EffectiveModelConfiguration, val issues: List<CapabilityIssue>) {
    val canSubmit: Boolean get() = issues.isEmpty()
}

/** Same entry point for live feedback and the last check before upload/submission. No input is truncated. */
internal object CapabilityConstraintEngine {
    fun evaluate(capabilities: ModelUiCapabilities, prompt: String, parameters: Map<String, String>,
        media: List<AttachedMedia>): CapabilityValidation {
        val model = capabilities.model
        val allValues = capabilities.defaults + parameters
        val applicable = ParameterDependencies.effective(model, allValues)
        val reconciled = if (model.providerId == "atlas_cloud") NativeModelSchema.reconcile(model, applicable) else applicable
        val rawCounts = CanvasInputPlan.fields(model, reconciled).associate {field -> field.key to media.count(field::contains)}
        val visibility = capabilityVisibility(capabilities.constraints, reconciled, rawCounts)
        val effective = reconciled.filterKeys {it !in visibility.hidden}
        val conditionValues = effective
        val counts = rawCounts.filterKeys {it !in visibility.hidden}
        val config = EffectiveModelConfiguration(capabilities.key, prompt,
            prompt.takeIf { capabilities.prompt.support == CapabilitySupport.CONFIRMED && capabilities.prompt.fieldKey !in visibility.hidden }, effective,
            allValues.filterKeys { it !in effective }, media.toList())
        val issues = mutableListOf<CapabilityIssue>()
        fun issue(kind: CapabilityIssueKind, key: String?, text: String) { issues += CapabilityIssue(kind, key, text) }
        when (capabilities.support) {
            CapabilitySupport.UNSUPPORTED -> issue(CapabilityIssueKind.MODEL, null, "Este destino no admite la modalidad seleccionada.")
            CapabilitySupport.UNKNOWN -> capabilities.unknownReasons.ifEmpty {listOf("Carga el contrato del modelo antes de continuar.")}
                .forEach { issue(CapabilityIssueKind.UNKNOWN, null, it) }
            CapabilitySupport.CONFIRMED -> Unit
        }
        if (!visibility.stable) issue(CapabilityIssueKind.UNKNOWN, null, "Las reglas de visibilidad no permiten una configuración estable.")
        if (capabilities.prompt.support == CapabilitySupport.CONFIRMED) {
            val contract = capabilities.prompt
            if (contract.required && prompt.isBlank()) issue(CapabilityIssueKind.PROMPT, contract.fieldKey, "Escribe un prompt.")
            val count = when (contract.unit) {
                "unicode_code_points" -> prompt.codePointCount(0, prompt.length)
                "utf16_code_units" -> prompt.length
                "utf8_bytes" -> prompt.toByteArray(Charsets.UTF_8).size
                else -> null
            }
            if (count != null && (contract.minimum?.let {count < it} == true || contract.maximum?.let {count > it} == true))
                issue(CapabilityIssueKind.PROMPT, contract.fieldKey,
                    "${model.displayName}: longitud de prompt fuera del contrato (${contract.minimum ?: 0}–${contract.maximum ?: "sin máximo publicado"}, ${contract.unit}). El original se conserva.")
            if (count == null && (contract.minimum != null || contract.maximum != null))
                issue(CapabilityIssueKind.UNKNOWN, contract.fieldKey, "La unidad del límite de prompt no está confirmada.")
        }
        for (input in capabilities.inputs) {
            val attached = media.filter(input.field::contains)
            if (attached.size < input.minimum) issue(CapabilityIssueKind.MEDIA, input.field.key,
                "${input.field.label}: necesita al menos ${input.minimum}; has añadido ${attached.size}.")
            if (input.maximum != null && attached.size > input.maximum) issue(CapabilityIssueKind.MEDIA, input.field.key,
                "${input.field.label}: admite como máximo ${input.maximum}; has añadido ${attached.size}. Los archivos se conservan.")
            if (attached.any { !input.field.acceptsMime(it.mimeType) }) issue(CapabilityIssueKind.MEDIA, input.field.key,
                "Revisa el tipo de archivo de «${input.field.label}».")
        }
        if (media.any { item -> capabilities.inputs.none { it.field.contains(item) } })
            issue(CapabilityIssueKind.MEDIA, null, "Hay archivos sin una entrada compatible en este modelo. Se conservan para otro destino.")

        val known = capabilities.declaredParameters.map { it.key }.toSet() + model.contractDefaults.keys
        parameters.filter { (key, value) -> value.isNotBlank() && key !in known && !key.startsWith("__") }.forEach { (key, _) ->
            issue(CapabilityIssueKind.PARAMETER, key, "El contrato actual no declara «$key». Su valor se conserva en el borrador.")
        }
        for (parameter in capabilities.declaredParameters.filter { it.key !in visibility.hidden && ParameterDependencies.isApplicable(model, it.key, allValues) }) {
            FormContract.invalidValue(listOf(parameter), effective)?.let { issue(CapabilityIssueKind.PARAMETER, parameter.key, it) }
            val value = effective[parameter.key].orEmpty()
            if (value.isNotBlank() && ParameterBounds.outOfRange(value, ParameterBounds.minimum(parameter), ParameterBounds.maximum(parameter)))
                issue(CapabilityIssueKind.PARAMETER, parameter.key, "Revisa el rango publicado de «${parameter.displayName}».")
            capabilities.declaredSteps[parameter.key]?.let { step ->
                if (violatesStep(value, step, capabilities.stepOrigins[parameter.key] ?: BigDecimal.ZERO))
                    issue(CapabilityIssueKind.PARAMETER, parameter.key, "«${parameter.displayName}» admite pasos de ${step.toPlainString()}. El valor original se conserva.")
            }
        }
        for (rule in capabilities.constraints.filter { it.applies(conditionValues, counts) }) {
            if (rule.fieldKey in visibility.hidden) continue
            val value = if (rule.fieldKey == capabilities.prompt.fieldKey) prompt else effective[rule.fieldKey].orEmpty()
            val mediaField = capabilities.inputs.firstOrNull {it.field.key == rule.fieldKey}
            if (mediaField != null) continue // The same published cardinality is checked above.
            if (rule.required && rule.fieldKey !in known && rule.fieldKey != capabilities.prompt.fieldKey)
                issue(CapabilityIssueKind.UNKNOWN, rule.fieldKey, "Estas opciones exigen una entrada aún no representada: ${rule.fieldKey}.")
            else if (rule.required && value.isBlank()) issue(CapabilityIssueKind.DEPENDENCY, rule.fieldKey, "Completa «${rule.fieldKey}» para estas opciones.")
            if (value.isBlank()) continue
            rule.multipleOf?.toBigDecimalOrNull()?.takeIf {it > BigDecimal.ZERO}?.let { step ->
                if (violatesStep(value, step, BigDecimal.ZERO))
                    issue(CapabilityIssueKind.DEPENDENCY, rule.fieldKey, "«${rule.fieldKey}» admite pasos de ${step.toPlainString()} con estas opciones.")
            }
            if (rule.allowedValues != null && value !in rule.allowedValues ||
                ParameterBounds.outOfRange(value, rule.minimum?.toBigDecimalOrNull(), rule.maximum?.toBigDecimalOrNull()) ||
                rule.minLength?.let {value.codePointCount(0, value.length) < it} == true ||
                rule.maxLength?.let {value.codePointCount(0, value.length) > it} == true)
                issue(CapabilityIssueKind.DEPENDENCY, rule.fieldKey,
                    "«${rule.fieldKey}» no es compatible con ${rule.whenValues.keys.joinToString()}. Elige una combinación publicada.")
        }
        val represented = known + capabilities.inputs.map {it.field.key} + capabilities.prompt.fieldKey + "model"
        model.nativeRequiredFields.filter {it !in represented}.forEach { key ->
            issue(CapabilityIssueKind.UNKNOWN, key, "El contrato exige una entrada aún no representada: $key.")
        }
        // Keep reviewed provider-specific compatibility checks in their original adapter.
        if (model.providerId == "atlas_cloud" && capabilities.support == CapabilitySupport.CONFIRMED) {
            val values = effective.filterValues {it.isNotBlank()} +
                ("prompt" to config.requestPrompt.orEmpty())
            NativeModelSchema.validate(model, values)?.let { issue(CapabilityIssueKind.PARAMETER, null, it) }
        }
        return CapabilityValidation(config, issues.distinct())
    }

    private fun violatesStep(value: String, step: BigDecimal, origin: BigDecimal): Boolean =
        ParameterBounds.exact(value)?.toBigDecimalOrNull()?.let { number ->
            number.subtract(origin).remainder(step).compareTo(BigDecimal.ZERO) != 0
        } ?: false
}
