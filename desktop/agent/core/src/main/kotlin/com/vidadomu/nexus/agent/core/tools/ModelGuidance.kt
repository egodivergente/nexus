package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.agent.core.skills.TASK_TEMPLATE_KEYS
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Guía específica del modelo que acompaña al preparar un encargo: lo que el agente no debe
 * improvisar (restricciones, errores habituales, estructura de prompt y plantilla de la tarea).
 * Se entrega una vez por skill y conversación para no gastar contexto repitiéndola.
 */
internal object ModelGuidance {
    private const val SECTION_CHARS = 1_400
    private val SECTIONS = listOf("Restricciones", "Errores habituales", "Estructura de prompt")

    fun forPlan(d: ToolDeps, modelId: String, task: String?, session: AgentSession): JsonObject? {
        val skill = d.skills.forModel(modelId) ?: return null
        if (!session.deliveredSkills.add(skill.id))
            return buildJsonObject { put("skill", skill.id); put("note", "Guía de ${skill.title} ya entregada en esta conversación; pide otra sección con load_skill si la necesitas.") }
        return buildJsonObject {
            put("skill", skill.id)
            put("title", skill.title)
            SECTIONS.forEach { name -> skill.section(name)?.trim()?.takeIf { it.isNotEmpty() }?.let { put(key(name), it.take(SECTION_CHARS)) } }
            task?.let { TASK_TEMPLATE_KEYS[it] }?.let { skill.subsection("Plantillas por tarea", it) }?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { put("task_template", it.take(SECTION_CHARS)) }
            put("rule", "Aplica esta guía del modelo antes de dar el encargo por bueno; si el plan la contradice, corrígelo con update_plan(order_id).")
        }
    }

    private fun key(section: String) = when (section) {
        "Restricciones" -> "constraints"
        "Errores habituales" -> "common_mistakes"
        else -> "prompt_structure"
    }
}
