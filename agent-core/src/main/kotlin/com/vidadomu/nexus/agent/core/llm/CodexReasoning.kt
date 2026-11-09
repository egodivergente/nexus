package com.vidadomu.nexus.agent.core.llm

/**
 * Esfuerzo de razonamiento de Codex CLI (`codex exec -c model_reasoning_effort=…`), con los niveles del catálogo
 * de Codex (models_cache.json, cliente 0.166, 06-11): todos los modelos admiten de «low» a «max»; «ultra», que
 * delega partes de la tarea por su cuenta, no está en los Luna. null = el nivel que trae el modelo.
 * AGY no usa esto: su nivel va en el id del modelo ([AntigravityReasoning]).
 */
object CodexReasoning {
    val efforts = listOf("low", "medium", "high", "xhigh", "max", "ultra")
    private val withoutUltra = setOf("gpt-6-luna", "gpt-5.6-luna")
    private val lowByDefault = setOf("gpt-6.1-sol", "gpt-6.1-terra", "gpt-5.6-sol")
    private val mediumByDefault = setOf("gpt-6-astra", "gpt-6-sol", "gpt-6-luna", "gpt-5.6-terra", "gpt-5.6-luna")

    fun supported(model: String): List<String> = if (model.trim() in withoutUltra) efforts - "ultra" else efforts

    /** El nivel que se envía: el elegido si el modelo lo admite; «ultra» en un Luna baja a «max»; desconocido = ninguno. */
    fun effective(model: String, effort: String?): String? {
        val e = effort?.trim()?.lowercase()?.takeIf { it in efforts } ?: return null
        val levels = supported(model)
        return if (e in levels) e else levels.last()
    }

    /** Nivel por defecto de [model] según el catálogo de Codex, o null si no se conoce. */
    fun defaultFor(model: String): String? = when (model.trim()) {
        in lowByDefault -> "low"
        in mediumByDefault -> "medium"
        else -> null
    }
}
