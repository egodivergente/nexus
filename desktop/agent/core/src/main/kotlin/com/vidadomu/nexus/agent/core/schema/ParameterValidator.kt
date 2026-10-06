package com.vidadomu.nexus.agent.core.schema

import com.vidadomu.nexus.agent.core.registry.ModelSchema
import com.vidadomu.nexus.agent.core.registry.ParamSpec
import kotlinx.serialization.Serializable

@Serializable
data class ValidationIssue(val key: String, val message: String, val severity: Severity = Severity.ERROR) {
    @Serializable
    enum class Severity { ERROR, WARNING }
}

@Serializable
data class ValidationResult(
    /** Solo los valores que se enviarán (válidos, normalizados). Nexus aplica sus defaults al resto. */
    val normalized: Map<String, String>,
    val issues: List<ValidationIssue>,
    /** Claves descartadas por venir vacías / null / «–». */
    val dropped: List<String>,
) {
    val ok: Boolean get() = issues.none { it.severity == ValidationIssue.Severity.ERROR }
    val errors: List<ValidationIssue> get() = issues.filter { it.severity == ValidationIssue.Severity.ERROR }
}

/**
 * Valida parámetros contra el schema REAL del modelo. Mismas garantías que el formulario de Nexus:
 * - una clave que no está en el schema es un error (el agente no puede inventar parámetros);
 * - null, vacío o «–» no se envían ni se muestran;
 * - opciones, rangos y tipos se comprueban; solo se normalizan equivalencias seguras.
 */
object ParameterValidator {

    private val BLANK_LIKE = setOf("", "–", "—", "-", "null", "undefined", "none", "n/a")
    const val MAX_PARAMS = 64
    const val MAX_VALUE_LENGTH = 16_000

    fun isBlankLike(value: String?): Boolean = value == null || value.trim().lowercase() in BLANK_LIKE

    fun validate(schema: ModelSchema, params: Map<String, String?>): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        val dropped = mutableListOf<String>()
        val out = linkedMapOf<String, String>()

        if (params.size > MAX_PARAMS) issues += ValidationIssue("*", "Demasiados parámetros (máx. $MAX_PARAMS).")
        schema.unsupportedRequired.forEach {
            issues += ValidationIssue(it, "El proveedor exige «$it» y Nexus aún no puede enviarlo; elige otro modelo.")
        }
        val mediaFields = with(schema.media) {
            listOfNotNull(firstFrameField, lastFrameField, inputImageField, imagesArrayField, videoField).toSet()
        }

        for ((key, raw) in params) {
            if (isBlankLike(raw)) { dropped += key; continue }
            val value = raw!!.trim()
            if (key == "prompt" || (key == "negative_prompt" && schema.param(key) == null)) {
                issues += ValidationIssue(key, "«$key» va en su campo propio, no en parámetros.")
                continue
            }
            if (key in mediaFields) {
                issues += ValidationIssue(key, "«$key» es un campo de medios: adjunta el archivo en lugar de pasarlo como parámetro.")
                continue
            }
            val spec = schema.param(key)
            if (spec == null) {
                issues += ValidationIssue(key, "«$key» no existe en el schema de ${schema.ref.modelId}.")
                continue
            }
            if (value.length > MAX_VALUE_LENGTH) {
                issues += ValidationIssue(key, "Valor demasiado largo para «${spec.displayName}».")
                continue
            }
            when (val r = check(spec, value)) {
                is Check.Ok -> out[key] = r.normalized
                is Check.Bad -> issues += ValidationIssue(key, r.message)
            }
        }

        for (spec in schema.parameters) {
            if (spec.required && spec.default.isNullOrBlank() && spec.key !in out) {
                issues += ValidationIssue(spec.key, "Falta «${spec.displayName}», que es obligatorio.")
            }
        }
        return ValidationResult(out, issues, dropped)
    }

    /** Valores efectivos para mostrar: defaults del schema + lo elegido. Nunca muestra vacíos. */
    fun effective(schema: ModelSchema, normalized: Map<String, String>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for (spec in schema.parameters) {
            val v = normalized[spec.key] ?: spec.default
            if (!isBlankLike(v)) result[spec.key] = v!!
        }
        return result
    }

    private sealed interface Check {
        data class Ok(val normalized: String) : Check
        data class Bad(val message: String) : Check
    }

    private fun check(spec: ParamSpec, value: String): Check {
        val name = spec.displayName
        val type = (spec.nativeType ?: spec.uiType).lowercase()

        if (spec.options.isNotEmpty()) {
            spec.options.firstOrNull { it == value }?.let { return Check.Ok(it) }
            spec.options.firstOrNull { it.equals(value, ignoreCase = true) }?.let { return Check.Ok(it) }
            val num = value.toDoubleOrNull()
            if (num != null) spec.options.firstOrNull { it.toDoubleOrNull() == num }?.let { return Check.Ok(it) }
            return Check.Bad("«$value» no es una opción de «$name». Opciones: ${spec.options.joinToString(", ")}.")
        }

        return when {
            type == "boolean" -> when (value.lowercase()) {
                "true", "false" -> Check.Ok(value.lowercase())
                else -> Check.Bad("«$name» debe ser true o false.")
            }
            type == "integer" || type == "seed" || type == "duration" -> {
                val d = value.toDoubleOrNull()
                val l = d?.takeIf { it % 1.0 == 0.0 }?.toLong()
                    ?: return Check.Bad("«$name» debe ser un número entero.")
                rangeError(spec, l.toDouble())?.let { Check.Bad(it) } ?: Check.Ok(l.toString())
            }
            type == "number" || type == "float" -> {
                val d = value.toDoubleOrNull() ?: return Check.Bad("«$name» debe ser numérico.")
                rangeError(spec, d)?.let { Check.Bad(it) } ?: Check.Ok(value)
            }
            else -> Check.Ok(value)
        }
    }

    private fun rangeError(spec: ParamSpec, v: Double): String? {
        spec.min?.let { if (v < it) return "«${spec.displayName}» mínimo ${fmt(it)}." }
        spec.max?.let { if (v > it) return "«${spec.displayName}» máximo ${fmt(it)}." }
        return null
    }

    private fun fmt(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
}
