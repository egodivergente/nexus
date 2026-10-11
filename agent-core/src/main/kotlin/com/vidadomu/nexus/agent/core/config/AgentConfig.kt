package com.vidadomu.nexus.agent.core.config

import kotlinx.serialization.Serializable

/**
 * Niveles de autonomía. En TODOS, una operación que consume créditos exige confirmación explícita
 * del usuario para esa operación concreta (ver SpendGuard).
 */
@Serializable
enum class AgentMode {
    /** Recomienda; no prepara ejecuciones ni cambia nada. */
    ASSISTANT,
    /** Prepara modelo + prompt + parámetros y espera confirmación. Organizar assets = propuesta. */
    COPILOT,
    /** Puede organizar assets y encadenar pasos; el gasto sigue requiriendo confirmación. */
    AGENT,
}

/** Configuración del cerebro. Cambiar de modelo o proveedor no requiere tocar código. */
@Serializable
data class AgentConfig(
    /** Implementación de LlmProvider: "bedrock-proxy" (producción) o "scripted" (pruebas). */
    val provider: String = "bedrock-proxy",
    /** Model ID o inference profile ID de Bedrock. Lo valida además la lista blanca del proxy. */
    val modelId: String,
    /** Región de Bedrock (informativa en el cliente; la decide el proxy). */
    val region: String,
    /** URL del proxy, p. ej. https://nexus-proxy.<tailnet>.ts.net */
    val endpoint: String,
    val temperature: Double? = null,
    val maxTokens: Int = 4096,
    val toolUse: Boolean = true,
    val mode: AgentMode = AgentMode.COPILOT,
    /** Ids de skills activas; vacío = todas las disponibles. */
    val activeSkills: List<String> = emptyList(),
    /** Tope de rondas herramienta→modelo por mensaje del usuario (corta bucles). */
    val maxToolRounds: Int = 7,
    /** Enviar al LLM una miniatura de las imágenes adjuntas para que vea el keyframe. */
    val sendImagesToLlm: Boolean = true,
    /** Sustituye el prompt de sistema base (las reglas de gasto se añaden siempre). */
    val systemPromptOverride: String? = null,
)
