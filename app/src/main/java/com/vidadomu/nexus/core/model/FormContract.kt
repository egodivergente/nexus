package com.vidadomu.nexus.core.model

/** Shared UI/payload contract. Remote names are never rewritten into API keys. */
object FormContract {
    private val scalarTypes = setOf(ParameterType.TEXT, ParameterType.LONG_TEXT, ParameterType.BOOLEAN,
        ParameterType.INTEGER, ParameterType.FLOAT, ParameterType.ENUM, ParameterType.SLIDER,
        ParameterType.SEED, ParameterType.ASPECT_RATIO, ParameterType.RESOLUTION, ParameterType.DURATION)
    fun token(value: String): Boolean = value.isNotBlank() &&
        value.trim().lowercase() !in setOf("null", "undefined", "-", "–", "—") && value.any {it.isLetterOrDigit()}
    fun visible(parameter: ModelParameter): Boolean = token(parameter.key) && token(parameter.displayName) &&
        parameter.type in scalarTypes
    fun normalize(parameters: List<ModelParameter>): List<ModelParameter> = parameters.filter(::visible)
        .distinctBy {it.key}.map {it.copy(options=it.options.filter(::token).distinct())}
    fun invalidValue(parameters: List<ModelParameter>, values: Map<String,String>): String? {
        for (p in normalize(parameters)) {
            val value=values[p.key].orEmpty()
            if(value.isBlank()) { if(p.isRequired) return "Completa «${p.displayName}»."; continue }
            val numeric=value.toDoubleOrNull()
            val nativeType=p.nativeType ?: when(p.type) {
                ParameterType.INTEGER,ParameterType.SEED->"integer"
                ParameterType.FLOAT,ParameterType.SLIDER->"number"
                ParameterType.BOOLEAN->"boolean"
                else->"string"
            }
            // Some image schemas use minimum/maximum for each edge of a string size.
            val dimensions = if(nativeType == "string" && p.key in setOf("size","image_size","resolution"))
                Regex("^([0-9]+)[xX*×:]([0-9]+)$").matchEntire(value)?.groupValues?.drop(1)?.mapNotNull { it.toDoubleOrNull() }
            else null
            val ranged = if(nativeType in setOf("integer","number")) listOfNotNull(numeric) else dimensions.orEmpty()
            val invalid=(p.options.isNotEmpty() && value !in p.options) ||
                (nativeType=="integer" && value.toLongOrNull()==null) ||
                (nativeType=="number" && numeric?.isFinite()!=true) ||
                (nativeType=="boolean" && value !in setOf("true","false")) ||
                (dimensions != null && dimensions.any { it <= 0 }) ||
                (p.minValue!=null && ranged.any { !it.isFinite() || it<p.minValue }) ||
                (p.maxValue!=null && ranged.any { !it.isFinite() || it>p.maxValue })
            if(invalid) return "Revisa «${p.displayName}» y sus valores permitidos."
        }
        return null
    }
    fun previewValues(model:NexusModel, values:Map<String,String>):Map<String,String> {
        val freeText=normalize(model.parameters).filter {it.type in setOf(ParameterType.TEXT,ParameterType.LONG_TEXT) && it.options.isEmpty()}.map {it.key}.toSet()
        return values.mapValues { (key,value) -> if(key in freeText && value.isNotBlank()) "[Texto omitido: ${value.length} caracteres]" else value }
    }
    fun restoredValues(model: NexusModel, saved: Map<String,String>): Map<String,String> =
        normalize(model.parameters).associate { p ->
            val candidate=saved[p.key]
            p.key to if(candidate!=null && invalidValue(listOf(p), mapOf(p.key to candidate))==null) candidate else p.defaultValue
        }
}
