package com.vidadomu.nexus.ui.create.capability

import com.vidadomu.nexus.assistant.ProviderPromptLimits
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.NativeModelSchema
import com.vidadomu.nexus.ui.create.CanvasInputField
import com.vidadomu.nexus.ui.create.CanvasInputPlan
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap
import java.math.BigDecimal

internal enum class CapabilitySupport { CONFIRMED, UNSUPPORTED, UNKNOWN }
internal enum class CapabilityVerification { PARSED_SCHEMA, DECLARED_ADAPTER, UNVERIFIED }

/** Neither a brand nor a display name identifies an executable capability. */
internal data class CapabilityKey(val providerId: String, val modelId: String, val endpoint: String?, val modality: Modality)

/** recordedAt is the existing contract observation time, never a newly invented remote verification date. */
internal data class CapabilityProvenance(val source: String?, val recordedAt: Long?, val verification: CapabilityVerification)

internal data class PromptCapability(
    val support: CapabilitySupport, val fieldKey: String, val required: Boolean,
    val minimum: Int?, val maximum: Int?, val unit: String?, val provenance: CapabilityProvenance,
    val visible: Boolean = true
)

internal data class InputCapability(
    val field: CanvasInputField, val minimum: Int, val maximum: Int?,
    val support: CapabilitySupport, val provenance: CapabilityProvenance
)

/** An existing published conditional rule, projected without changing the parameter or request key. */
internal data class CapabilityConstraint(
    val fieldKey: String, val whenValues: Map<String, Set<String>>,
    val allowedValues: List<String>? = null, val minimum: String? = null, val maximum: String? = null,
    val minLength: Int? = null, val maxLength: Int? = null, val required: Boolean = false,
    val minimumCount: Int? = null, val maximumCount: Int? = null,
    val hidden: Boolean? = null, val otherwise: Boolean = false,
    val conditionRequired: Set<String> = whenValues.keys,
    val whenMinimumItems: Map<String, Int> = emptyMap(), val whenMaximumItems: Map<String, Int> = emptyMap(),
    val multipleOf: String? = null
) {
    fun applies(values: Map<String, String>, counts: Map<String, Int> = emptyMap()): Boolean {
        val matches = conditionRequired.all { !values[it].isNullOrBlank() || (counts[it] ?: 0) > 0 } && whenValues.all { (key, allowed) ->
            val value = values[key]?.takeIf {it.isNotBlank()}
            value == null || value in allowed
        } && whenMinimumItems.all {(key, count) -> (counts[key] ?: 0) >= count} &&
            whenMaximumItems.all {(key, count) -> (counts[key] ?: 0) <= count}
        return if (otherwise) !matches else matches
    }
}

internal data class ModelUiCapabilities(
    val key: CapabilityKey, val model: NexusModel, val support: CapabilitySupport,
    val provenance: CapabilityProvenance, val inputs: List<InputCapability>,
    val primaryParameters: List<ModelParameter>, val advancedParameters: List<ModelParameter>,
    val prompt: PromptCapability, val defaults: Map<String, String>,
    val constraints: List<CapabilityConstraint>, val unknownReasons: List<String>,
    val declaredParameters: List<ModelParameter>, val declaredSteps: Map<String, BigDecimal> = emptyMap(),
    val stepOrigins: Map<String, BigDecimal> = emptyMap()
) {
    val inputFields: List<CanvasInputField> get() = inputs.map { it.field }
    val promptVisible: Boolean get() = prompt.visible && prompt.support != CapabilitySupport.UNSUPPORTED
    val modeLabel: String get() = when {
        support == CapabilitySupport.UNKNOWN -> "Configuración por verificar"
        inputs.isEmpty() && prompt.support == CapabilitySupport.CONFIRMED -> "Desde texto"
        inputs.any { it.field.slot == MediaSlotType.LAST_FRAME } -> "Fotogramas"
        inputs.any { it.field.mime.startsWith("video/") } -> "Con vídeo de entrada"
        inputs.any { it.field.maximum > 1 && it.field.mime.startsWith("image/") } -> "Con referencias"
        inputs.any { it.field.mime.startsWith("image/") } -> "Con imagen de entrada"
        else -> "Entradas del modelo"
    }
    fun fieldSupport(key: String): CapabilitySupport = when {
        declaredParameters.any { it.key == key } || inputs.any { it.field.key == key } -> CapabilitySupport.CONFIRMED
        support == CapabilitySupport.CONFIRMED -> CapabilitySupport.UNSUPPORTED
        else -> CapabilitySupport.UNKNOWN
    }
}

/** Adapts existing contracts. No network, provider requests, endpoint inference or model-family rules. */
internal object ModelCapabilityRegistry {
    private val records = ConcurrentHashMap<CapabilityKey, ModelUiCapabilities>()

    fun key(model: NexusModel, modality: Modality) = CapabilityKey(model.providerId,
        model.id, model.contractEndpoint ?: model.atlasEndpoint, modality)

    fun resolve(model: NexusModel, modality: Modality, values: Map<String, String> = emptyMap(),
        schemaReady: Boolean = true, media: List<AttachedMedia> = emptyList(),
        inputSchema: JsonObject? = null): ModelUiCapabilities {
        // Other existing adapters may supply their already-parsed Input; this never fetches a schema.
        val schema = inputSchema ?: if (model.providerId == "atlas_cloud") NativeModelSchema.inputSchemaSnapshot(model.id) else null
        val adapterDeclared = model.providerId != "atlas_cloud" && (model.contractEndpoint != null ||
            model.parameters.isNotEmpty() || model.contractMedia.isNotEmpty() || model.nativeRequiredFields.isNotEmpty() || model.capabilities.isNotEmpty())
        val representedFields = FormContract.normalize(model.parameters).map {it.key}.toSet() +
            CanvasInputPlan.fields(model, values).map {it.key} + model.nativeRequiredFields + model.contractPromptField + "prompt"
        val unsupportedRules = unsupportedConditionalFeatures(schema, representedFields) + model.parameters.filter {
            it.step?.let {step -> !step.isFinite() || step<=0f} == true
        }.map {"El paso declarado de «${it.displayName}» no es válido."}
        val provenance = CapabilityProvenance(model.contractSource ?: model.schemaUrl ?: "Declaración del adaptador local".takeIf {adapterDeclared}, model.contractCheckedAt,
            when { schema != null -> CapabilityVerification.PARSED_SCHEMA
                adapterDeclared -> CapabilityVerification.DECLARED_ADAPTER
                else -> CapabilityVerification.UNVERIFIED })
        val support = when {
            modality !in model.modalities -> CapabilitySupport.UNSUPPORTED
            !schemaReady -> CapabilitySupport.UNKNOWN
            unsupportedRules.isNotEmpty() -> CapabilitySupport.UNKNOWN
            schema != null || adapterDeclared -> CapabilitySupport.CONFIRMED
            else -> CapabilitySupport.UNKNOWN
        }
        val props = schema?.get("properties") as? JsonObject
        val declared = FormContract.normalize(model.parameters)
        val steps = declared.mapNotNull { parameter ->
            val property = props?.get(parameter.key) as? JsonObject
            val exact = if(property?.containsKey("multipleOf") == true)
                ParameterBounds.exact((property["multipleOf"] as? JsonPrimitive)?.contentOrNull)
            else ParameterBounds.exact(parameter.step?.toString())
            exact?.toBigDecimalOrNull()?.takeIf {it > BigDecimal.ZERO}?.let {parameter.key to it}
        }.toMap()
        val stepOrigins = declared.filter {it.key in steps}.associate { parameter ->
            val property = props?.get(parameter.key) as? JsonObject
            parameter.key to if(property?.containsKey("multipleOf") == true) BigDecimal.ZERO
                else ParameterBounds.minimum(parameter) ?: BigDecimal.ZERO
        }
        val defaults = model.contractDefaults + declared.associate { it.key to it.defaultValue }
        val effectiveValues = defaults + values
        val constraints = constraints(schema)
        val baseInputs = CanvasInputPlan.fields(model, effectiveValues)
        val inputCounts = baseInputs.associate {field -> field.key to media.count(field::contains)}
        val visibility = capabilityVisibility(constraints, effectiveValues, inputCounts)
        val visibleValues = effectiveValues.filterKeys {it !in visibility.hidden}
        val visibleCounts = inputCounts.filterKeys {it !in visibility.hidden}
        val parameters = ParameterDependencies.visible(model, declared, effectiveValues).filterNot { parameter ->
            parameter.key in visibility.hidden
        }.map { parameter ->
            val stepped = parameter.copy(step = steps[parameter.key]?.toFloat()?.takeIf {it.isFinite() && it > 0f} ?: parameter.step)
            constraints.filter { it.fieldKey == parameter.key && it.applies(visibleValues, visibleCounts) }.fold(stepped) { current, rule ->
                current.copy(options = rule.allowedValues?.let { allowed ->
                    if (current.options.isEmpty()) allowed else current.options.filter { it in allowed }
                } ?: current.options,
                    minExact = rule.minimum ?: current.minExact, maxExact = rule.maximum ?: current.maxExact,
                    minValue = rule.minimum?.let {ParameterBounds.legacyFloat(it, upper = false)} ?: current.minValue,
                    maxValue = rule.maximum?.let {ParameterBounds.legacyFloat(it, upper = true)} ?: current.maxValue,
                    isRequired = current.isRequired || rule.required,
                    step = rule.multipleOf?.toFloatOrNull()?.takeIf {it.isFinite() && it > 0f} ?: current.step)
            }
        }
        val inputCapabilities = baseInputs.map { original ->
            val property = props?.get(original.key) as? JsonObject
            val array = property?.get("type")?.jsonPrimitive?.contentOrNull == "array" || original.contract?.encoding == "array"
            val inputRules = constraints.filter {it.fieldKey == original.key && it.applies(visibleValues, visibleCounts)}
            val required = original.required || inputRules.any {it.required}
            val publishedMinimum = (property?.get("minItems") as? JsonPrimitive)?.intOrNull?.coerceAtLeast(0)
            val minimum = maxOf(publishedMinimum ?: if(required) 1 else 0, inputRules.mapNotNull {it.minimumCount}.maxOrNull() ?: 0)
            val publishedMaximum = (property?.get("maxItems") as? JsonPrimitive)?.intOrNull?.coerceAtLeast(0)
            val reviewedMaximum = original.maximum.takeIf {it != Int.MAX_VALUE &&
                (property == null || !array || publishedMaximum != null || original.key == model.imagesArrayField && model.structuredImageReferences)}
            val maximum = (inputRules.mapNotNull {it.maximumCount} + listOfNotNull(
                if(property != null && !array) 1 else null, publishedMaximum, reviewedMaximum)).minOrNull()
            // maxItems absent is unknown, not an artificial one/20/50-file provider limit.
            val field = original.copy(required = required, maximum = maximum ?: Int.MAX_VALUE)
            InputCapability(field, minimum, maximum, if (support == CapabilitySupport.CONFIRMED)
                CapabilitySupport.CONFIRMED else CapabilitySupport.UNKNOWN, provenance)
        }.filterNot { input -> input.field.key in visibility.hidden }
        val limit = ProviderPromptLimits.forModel(model)
        val promptSupport = when {
            support != CapabilitySupport.CONFIRMED -> CapabilitySupport.UNKNOWN
            !model.promptAccepted -> CapabilitySupport.UNSUPPORTED
            else -> CapabilitySupport.CONFIRMED
        }
        val promptField = if (props?.containsKey("prompt") == true) "prompt" else model.contractPromptField
        val promptRules = constraints.filter {it.fieldKey == promptField && it.applies(visibleValues, visibleCounts)}
        val prompt = PromptCapability(promptSupport, promptField, model.promptRequired || promptRules.any {it.required},
            limit?.minimum, limit?.maximum ?: model.promptMaxLength, limit?.unit, provenance,
            visible = promptField !in visibility.hidden)
        val reasons = buildList {
            if (!schemaReady) add("La configuración del modelo todavía no está lista.")
            if (support == CapabilitySupport.UNKNOWN && schemaReady && schema == null && !adapterDeclared)
                add("No hay un contrato de entrada cargado para este destino.")
            if (support == CapabilitySupport.UNSUPPORTED) add("El destino no admite la modalidad seleccionada.")
            addAll(unsupportedRules)
            if (!visibility.stable) add("Las reglas de visibilidad del contrato no convergen a una configuración comprobable.")
        }
        val quickKeys = setOf("aspect_ratio", "aspectRatio", "ratio", "size", "image_size", "resolution",
            "duration", "duration_seconds", "seconds", "fps", "frame_rate", "generate_audio", "audio", "sound", "enable_audio", "keep_original_sound")
        val quick = parameters.filter { !it.isAdvanced || it.isRequired || it.key in quickKeys }.sortedWith(compareBy<ModelParameter> { !it.isRequired }
            .thenBy { it.key !in quickKeys && it.type !in setOf(ParameterType.ASPECT_RATIO, ParameterType.RESOLUTION, ParameterType.DURATION) }).take(4)
        return ModelUiCapabilities(key(model, modality), model, if(visibility.stable) support else CapabilitySupport.UNKNOWN, provenance, inputCapabilities,
            quick, parameters.filter {it.key !in quick.map {p -> p.key}.toSet()}, prompt,
            defaults, constraints, reasons, declared, steps, stepOrigins).also { records[it.key] = it }
    }

    fun recorded(key: CapabilityKey): ModelUiCapabilities? = records[key]
    fun invalidate(key: CapabilityKey) { records.remove(key) }

    private fun constraints(schema: JsonObject?): List<CapabilityConstraint> = buildList {
        val props = schema?.get("properties") as? JsonObject ?: return@buildList
        for ((key, raw) in props) {
            val dependency = (raw as? JsonObject)?.get("x-depends-on") as? JsonObject ?: continue
            val on = dependency["field"]?.jsonPrimitive?.contentOrNull ?: continue
            for (entry in dependency["conditions"] as? JsonArray ?: JsonArray(emptyList())) {
                val rule = entry as? JsonObject ?: continue
                val options = (rule["when"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty().toSet()
                if (options.isNotEmpty()) add(parseRule(key, rule, mapOf(on to options)))
            }
        }
        for (entry in schema?.get("allOf") as? JsonArray ?: JsonArray(emptyList())) {
            val condition = entry as? JsonObject ?: continue
            val whenProps = (condition["if"] as? JsonObject)?.get("properties") as? JsonObject ?: continue
            val whenValues = whenProps.mapNotNull { (key, value) ->
                val predicate = value as? JsonObject ?: return@mapNotNull null
                val allowed = (predicate["const"] as? JsonPrimitive)?.contentOrNull?.let(::setOf)
                    ?: (predicate["enum"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet()
                allowed?.let { key to it }
            }.toMap()
            val minimumItems = whenProps.mapNotNull {(key, raw) -> ((raw as? JsonObject)?.get("minItems") as? JsonPrimitive)?.intOrNull?.let {key to it}}.toMap()
            val maximumItems = whenProps.mapNotNull {(key, raw) -> ((raw as? JsonObject)?.get("maxItems") as? JsonPrimitive)?.intOrNull?.let {key to it}}.toMap()
            if ((whenValues.keys + minimumItems.keys + maximumItems.keys).size != whenProps.size || whenProps.isEmpty()) continue
            val requiredWhen = ((condition["if"] as? JsonObject)?.get("required") as? JsonArray)
                ?.mapNotNull {(it as? JsonPrimitive)?.contentOrNull}.orEmpty().toSet()
            for ((branchName, otherwise) in listOf("then" to false, "else" to true)) {
                val branch = condition[branchName] as? JsonObject ?: continue
                val required = (branch["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                for ((key, raw) in branch["properties"] as? JsonObject ?: JsonObject(emptyMap())) {
                    val bounds = raw as? JsonObject ?: continue
                    add(parseRule(key, bounds, whenValues).copy(required = key in required, otherwise = otherwise, conditionRequired = requiredWhen,
                        whenMinimumItems = minimumItems, whenMaximumItems = maximumItems))
                }
                required.filter { it !in ((branch["properties"] as? JsonObject)?.keys.orEmpty()) }.forEach { key ->
                    add(CapabilityConstraint(key, whenValues, required = true, otherwise = otherwise, conditionRequired = requiredWhen,
                        whenMinimumItems = minimumItems, whenMaximumItems = maximumItems))
                }
            }
        }
    }

    /** Unsupported conditional operators stay explicit; this adapter does not claim full JSON Schema support. */
    private fun unsupportedConditionalFeatures(schema: JsonObject?, represented: Set<String>): List<String> = buildList {
        if (schema == null) return@buildList
        if (listOf("anyOf", "oneOf", "not", "dependentSchemas", "dependentRequired").any {it in schema})
            add("El contrato contiene reglas de combinación todavía no representadas.")
        for (entry in schema["allOf"] as? JsonArray ?: JsonArray(emptyList())) {
            val rule = entry as? JsonObject
            val condition = rule?.get("if") as? JsonObject
            val props = condition?.get("properties") as? JsonObject
            if (props.isNullOrEmpty() || props.values.any { raw ->
                val predicate = raw as? JsonObject
                predicate == null || predicate.keys.any {it !in setOf("const", "enum", "minItems", "maxItems", "description", "title")} ||
                    (predicate["const"] == null && predicate["enum"] !is JsonArray && predicate["minItems"] == null && predicate["maxItems"] == null)
            } || condition?.keys?.any {it !in setOf("properties", "required", "description", "title")} == true ||
                listOf("then", "else").mapNotNull {rule?.get(it) as? JsonObject}.any { branch ->
                    branch.keys.any {it !in setOf("properties", "required", "description", "title")}
                }) add("Hay una condición del contrato cuyo resultado no se puede comprobar todavía.")
        }
        fun simpleNullable(node: JsonObject): Boolean {
            val branches = node["anyOf"] as? JsonArray ?: return false
            val types = branches.mapNotNull {raw ->
                val branch = raw as? JsonObject ?: return false
                if (branch.keys.any {it !in setOf("type", "description", "title")}) return false
                (branch["type"] as? JsonPrimitive)?.contentOrNull
            }.toSet()
            return types.size == 2 && "null" in types && types.any {it in setOf("string", "integer", "number", "boolean")}
        }
        fun visit(element: JsonElement, path: String, top: Boolean = false) {
            when (element) {
                is JsonObject -> {
                    val unsupported = element.keys.filter {it in setOf("oneOf", "not", "dependentSchemas", "dependentRequired", "pattern", "exclusiveMinimum", "exclusiveMaximum") ||
                        it == "anyOf" && !simpleNullable(element) || it == "allOf" && !top}
                    if (unsupported.isNotEmpty()) add("Regla todavía no representada en $path: ${unsupported.joinToString()}.")
                    if ("multipleOf" in element && ParameterBounds.exact((element["multipleOf"] as? JsonPrimitive)?.contentOrNull)
                            ?.toBigDecimalOrNull()?.let {it > BigDecimal.ZERO} != true)
                        add("El paso publicado en $path no es un número positivo comprobable.")
                    for ((key, child) in element) {
                        if (top && key == "properties" && child is JsonObject) {
                            child.filterKeys {it in represented}.forEach {(field, value) -> visit(value, "$path.$field")}
                        } else visit(child, "$path.$key")
                    }
                }
                is JsonArray -> element.forEachIndexed {index, child -> visit(child, "$path[$index]")}
                else -> Unit
            }
        }
        visit(schema, "Input", top = true)
    }.distinct()

    private fun parseRule(key: String, rule: JsonObject, whenValues: Map<String, Set<String>>) = CapabilityConstraint(
        key, whenValues, (rule["enum"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
        ParameterBounds.exact((rule["minimum"] as? JsonPrimitive)?.contentOrNull),
        ParameterBounds.exact((rule["maximum"] as? JsonPrimitive)?.contentOrNull),
        (rule["minLength"] as? JsonPrimitive)?.intOrNull, (rule["maxLength"] as? JsonPrimitive)?.intOrNull,
        minimumCount = (rule["minItems"] as? JsonPrimitive)?.intOrNull,
        maximumCount = (rule["maxItems"] as? JsonPrimitive)?.intOrNull,
        hidden = (rule["x-hidden"] as? JsonPrimitive)?.booleanOrNull,
        multipleOf = ParameterBounds.exact((rule["multipleOf"] as? JsonPrimitive)?.contentOrNull))
}
