package com.vidadomu.nexus.agent.core.schema

import com.vidadomu.nexus.agent.core.orders.SuggestedFix
import com.vidadomu.nexus.agent.core.registry.ModelSchema
import com.vidadomu.nexus.agent.core.spend.GenerationPlan

/**
 * Traduce el error literal del proveedor a una causa y, si el schema lo permite, a una corrección
 * aplicable sobre el MISMO encargo. Nunca propone una clave que el schema no tenga ni un valor
 * que no venga de las opciones publicadas o del propio mensaje del proveedor.
 */
object ErrorDiagnosis {
    data class Diagnosis(val field: String?, val allowed: List<String>, val explanation: String, val fix: SuggestedFix?)

    private val REQUIRED = listOf(
        Regex("""["'`]?([A-Za-z_][A-Za-z0-9_]*)["'`]?\s+(?:is|are)\s+required(?:\s*[(:]\s*([^)]*)\)?)?""", RegexOption.IGNORE_CASE),
        Regex("""missing\s+(?:required\s+)?(?:field|parameter|property|argument)s?\s*[:=]?\s*["'`]?([A-Za-z_][A-Za-z0-9_]*)["'`]?(?:\s*\(([^)]*)\))?""", RegexOption.IGNORE_CASE),
    )
    private val ONE_OF = Regex("""["'`]?([A-Za-z_][A-Za-z0-9_]*)["'`]?\s+(?:must|should)\s+be\s+(?:one\s+of|in)\s*[:\[(]?\s*([^\])]+)""", RegexOption.IGNORE_CASE)

    fun splitAllowed(raw: String?): List<String> = raw.orEmpty()
        .split(',', '|', '/', ';').flatMap { it.split(Regex("\\s+or\\s+|\\s+o\\s+", RegexOption.IGNORE_CASE)) }
        .map { it.trim().trim('"', '\'', '`', '.', '[', ']') }.filter { it.isNotEmpty() && it.length <= 40 }

    fun diagnose(message: String, schema: ModelSchema?, plan: GenerationPlan): Diagnosis? {
        val (field, allowed, verb) = REQUIRED.firstNotNullOfOrNull { r -> r.find(message)?.let { Triple(it.groupValues[1], splitAllowed(it.groupValues.getOrNull(2)), "exige") } }
            ?: ONE_OF.find(message)?.let { Triple(it.groupValues[1], splitAllowed(it.groupValues[2]), "no acepta el valor de") }
            ?: return null
        val key = field.trim()
        if (key.equals("prompt", true)) return Diagnosis(key, emptyList(), "El proveedor exige un prompt.", null)
        val effective = plan.effectiveParams
        val derived = schema?.derivedParam(key)
        if (derived != null) {
            val value = derived.valueFor(effective[derived.from])
            return Diagnosis(key, allowed, "El proveedor $verb «$key». Nexus lo deduce de «${derived.from}»" +
                (value?.let { " (= $it); este envío no lo incluía. Repite el encargo y se añadirá." } ?: ", pero el valor actual no lo permite."), null)
        }
        val spec = schema?.param(key)
        if (spec == null) {
            return Diagnosis(key, allowed, "El proveedor $verb «$key», pero el schema publicado del modelo no declara ese campo. " +
                "Nexus no puede enviarlo con seguridad; usa otro modelo o espera a que el proveedor publique el schema.", null)
        }
        val candidates = (if (spec.options.isNotEmpty()) spec.options else allowed).ifEmpty { allowed }
        val inferred = inferFrom(effective, candidates)
            ?: spec.default?.takeIf { it in candidates }
        val explanation = buildString {
            append("El proveedor $verb «${spec.displayName}»")
            if (allowed.isNotEmpty()) append(" (${allowed.joinToString(", ")})")
            append(".")
            if (inferred != null) append(" Corrección: ${spec.key}=$inferred.") else append(" Elige un valor válido.")
        }
        return Diagnosis(key, candidates, explanation, inferred?.let { SuggestedFix("Fijar ${spec.displayName} = $inferred", mapOf(spec.key to it)) })
    }

    /** Un valor permitido que ya está implícito en otra opción elegida (p. ej. «…-4k» → 4k). */
    private fun inferFrom(effective: Map<String, String>, candidates: List<String>): String? {
        if (candidates.isEmpty()) return null
        val aliases = mapOf("2k" to listOf("1440p", "2k"), "4k" to listOf("4k", "2160p"), "1080p" to listOf("1080p"), "720p" to listOf("720p"))
        for ((_, value) in effective) {
            val v = value.lowercase()
            for ((suffix, names) in aliases) {
                if (!Regex("(^|[-_ ])$suffix($|[-_ ])").containsMatchIn(v)) continue
                candidates.firstOrNull { c -> names.any { c.equals(it, ignoreCase = true) } }?.let { return it }
            }
            candidates.firstOrNull { c -> c.length >= 3 && Regex("(^|[-_ ])${Regex.escape(c.lowercase())}($|[-_ ])").containsMatchIn(v) }?.let { return it }
        }
        return null
    }
}
