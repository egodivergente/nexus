package com.vidadomu.nexus.agent.core.llm

/**
 * Modelos de AGY que llevan el nivel de razonamiento en el propio id (`gemini-4-<familia>-<nivel>`).
 * Confirmados con `agy models`: Argon el 2026-10-20 (y en `/health` del puente del S25) y Hydrogen el 2026-11-13 en AXELPC.
 * El puente recibe el id tal cual en el campo `model`; no hay campos de esfuerzo aparte.
 */
object AntigravityReasoning {
    const val DEFAULT_MODEL = "gemini-4-argon-medium"
    const val HYDROGEN_MODEL = "gemini-4-hydrogen-medium"

    /** Familia → niveles que anuncia AGY, de menos a más. */
    val families: Map<String, List<String>> = linkedMapOf(
        "argon" to listOf("low", "medium", "high"),
        "hydrogen" to listOf("low", "medium", "high", "xhigh"),
    )

    /** Niveles de Argon (compatibilidad con quien no distingue familia). */
    val efforts: List<String> get() = families.getValue("argon")

    /** Familia de un id de AGY con nivel, o null si el modelo no tiene niveles. */
    fun family(model: String): String? = when (model) {
        "gemini-4-argon" -> "argon"
        else -> families.entries.firstOrNull { (name, levels) -> levels.any { model == "gemini-4-$name-$it" } }?.key
    }

    fun effort(model: String): String? = when (model) {
        "gemini-4-argon" -> "medium"
        else -> family(model)?.let { model.removePrefix("gemini-4-$it-") }
    }

    /** Niveles que admite el modelo; vacío si no tiene. */
    fun efforts(model: String): List<String> = family(model)?.let(families::getValue).orEmpty()

    /** Argon con ese nivel. */
    fun model(effort: String): String = model(DEFAULT_MODEL, effort)

    /** El mismo modelo que [current] con otro nivel. */
    fun model(current: String, effort: String): String {
        val name = family(current) ?: throw IllegalArgumentException("«$current» no tiene niveles de razonamiento.")
        val levels = families.getValue(name)
        require(effort in levels) { "${label(name)} admite ${levels.joinToString(", ") { LEVEL_NAMES.getValue(it) }}." }
        return "gemini-4-$name-$effort"
    }

    /** Al cambiar de modelo se conserva el nivel elegido si el nuevo lo admite; si no, el del modelo nuevo. */
    fun switchTo(target: String, current: String): String {
        val wanted = effort(current) ?: return target
        return if (family(target) != null && wanted in efforts(target)) model(target, wanted) else target
    }

    fun label(family: String): String = family.replaceFirstChar { it.uppercase() }

    private val LEVEL_NAMES = mapOf("low" to "bajo", "medium" to "medio", "high" to "alto", "xhigh" to "muy alto")
}
