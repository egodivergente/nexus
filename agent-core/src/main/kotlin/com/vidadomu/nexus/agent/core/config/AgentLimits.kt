package com.vidadomu.nexus.agent.core.config

import kotlinx.serialization.Serializable

/**
 * Todos los límites del agente, configurables por el usuario. Ninguna cifra de estas está fijada en el
 * prompt: el prompt cita estos valores y, si alguna instrucción dijera otra cosa, mandan estos.
 * En los campos marcados «0 = sin límite» el cero desactiva el tope.
 */
@Serializable
data class AgentLimits(
    /** Pasos (llamadas a herramientas) seguidos por mensaje antes de parar. 0 = sin límite. */
    val maxToolRounds: Int = 20,
    /** Tiempo máximo de un mensaje completo, en segundos. 0 = sin límite. */
    val turnTimeoutSeconds: Int = 600,
    /** Máximo de tokens de salida por respuesta del LLM. */
    val maxTokens: Int = 4096,
    /** Resultados que se pueden pedir de una vez en un encargo de Google Flow. */
    val flowMaxResults: Int = 20,
    /** Imágenes que se envían al LLM por mensaje (las demás se omiten con aviso). 0 = sin límite. */
    val maxImagesPerTurn: Int = 4,
    /** Vídeos (fotogramas) que se envían al LLM por mensaje. 0 = sin límite. */
    val maxVideosPerTurn: Int = 2,
    /** Documentos que se leen por mensaje (y se conservan en el contexto). 0 = sin límite. */
    val maxDocumentsPerTurn: Int = 5,
    /** Caracteres del estado de la interfaz que se añaden a cada mensaje. 0 = completo. */
    val maxUiContextChars: Int = 4_000,
    /** Minutos que se sigue consultando un trabajo en curso antes de pausar el seguimiento (el proveedor puede seguir). 0 = sin límite. */
    val jobTrackingMinutes: Int = 30,
    /** Mensajes de la conversación que se reenvían al LLM. 0 = todos. */
    val maxMessages: Int = 60,
    /** Caracteres de un resultado de herramienta que se pasan al LLM. 0 = completo. */
    val maxToolResultChars: Int = 32_000,

    // Preferencias del usuario: los valores iniciales conservan el comportamiento anterior; 0 desactiva cada tope.
    val maxGenerationsPerBatch: Int = 20,
    val maxConcurrentGenerations: Int = 3,
    val maxConcurrentFlowGenerations: Int = 1,
    val maxToolResults: Int = 50,
    val maxRecommendations: Int = 6,
    val maxRecommendationSchemaChecks: Int = 8,
    val maxProjectContextChars: Int = 3000,
    val maxOverviewScenes: Int = 20,
    val maxOverviewOutputs: Int = 8,
    val maxOverviewGenerations: Int = 6,
    val maxOverviewPromptChars: Int = 180,
    val maxMemorySearchResults: Int = 12,
    val maxMemoryExcerptChars: Int = 900,
    val maxFlowMedia: Int = 20,
    val maxFlowWaitSeconds: Int = 240,
    val maxConcurrentSubagents: Int = 2,
    val maxSubagentDepth: Int = 1,
    val allowSubagentGeneration: Boolean = true,
) {
    /** Valores efectivos de los topes «0 = sin límite». */
    val rounds get() = if (maxToolRounds <= 0) UNLIMITED else maxToolRounds
    val images get() = if (maxImagesPerTurn <= 0) UNLIMITED else maxImagesPerTurn
    val videos get() = if (maxVideosPerTurn <= 0) UNLIMITED else maxVideosPerTurn
    val messages get() = if (maxMessages <= 0) UNLIMITED else maxMessages
    val documents get() = if (maxDocumentsPerTurn <= 0) UNLIMITED else maxDocumentsPerTurn
    val uiContextChars get() = if (maxUiContextChars <= 0) UNLIMITED else maxUiContextChars
    val toolResultChars get() = if (maxToolResultChars <= 0) UNLIMITED else maxToolResultChars
    val turnTimeoutMillis: Long? get() = turnTimeoutSeconds.takeIf { it > 0 }?.let { it * 1000L }

    companion object {
        /** Natural collection bound; arithmetic involving this sentinel uses Long. */
        const val UNLIMITED = Int.MAX_VALUE
    }

    /** Problemas de validación (solo cordura: sin topes artificiales por encima de lo que el usuario decida). */
    fun problems(): List<String> = buildList {
        if (maxRecommendationSchemaChecks < 0) add("El número de esquemas no puede ser negativo (0 = sin límite).")
        if (maxToolRounds < 0) add("Los pasos por mensaje no pueden ser negativos (0 = sin límite).")
        if (turnTimeoutSeconds < 0) add("El tiempo por mensaje no puede ser negativo (0 = sin límite).")
        if (listOf(maxGenerationsPerBatch,maxConcurrentGenerations,maxConcurrentFlowGenerations,maxToolResults,maxRecommendations,maxProjectContextChars,maxOverviewScenes,maxOverviewOutputs,maxOverviewGenerations,maxOverviewPromptChars,maxMemorySearchResults,maxMemoryExcerptChars,maxFlowMedia,maxFlowWaitSeconds,maxConcurrentSubagents,maxSubagentDepth).any { it < 0 }) add("Los límites no pueden ser negativos (0 = sin límite).")
        if (maxTokens < 1) add("El máximo de tokens debe ser al menos 1.")
        if (flowMaxResults < 0) add("Los resultados de Flow no pueden ser negativos (0 = sin límite).")
        if (maxImagesPerTurn < 0 || maxVideosPerTurn < 0 || maxMessages < 0 || maxToolResultChars < 0 || maxDocumentsPerTurn < 0 || maxUiContextChars < 0 || jobTrackingMinutes < 0) add("Los límites no pueden ser negativos (0 = sin límite).")
    }

    fun maximum(value: Int): Int = if (value == 0) Int.MAX_VALUE else value

    fun queryLimit(requested: Int?, fallback: Int): Int = boundedCount(requested, fallback, maxToolResults)

    fun boundedCount(requested: Int?, fallback: Int, configured: Int): Int {
        val wanted = requested ?: minOf(fallback, maximum(configured))
        require(wanted >= 0) { "La cantidad no puede ser negativa." }
        return minOf(maximum(wanted), maximum(configured))
    }

    /** Aplica los valores propios de un cerebro: lo que no define (null) se queda con el global. */
    fun with(o: LimitOverrides): AgentLimits = copy(
        maxToolRounds = o.maxToolRounds ?: maxToolRounds,
        turnTimeoutSeconds = o.turnTimeoutSeconds ?: turnTimeoutSeconds,
        maxTokens = o.maxTokens ?: maxTokens,
    )

    /** Texto que se añade al prompt: dice cuáles son los límites reales y que prevalecen sobre cualquier otra cifra. */
    fun promptBlock(): String = buildString {
        appendLine("LÍMITES CONFIGURADOS POR EL USUARIO (son los vigentes y prevalecen sobre cualquier otra cifra que aparezca en estas instrucciones):")
        appendLine("- Resultados por encargo en Google Flow: ${if(flowMaxResults==0) "sin límite" else "hasta $flowMaxResults"}.")
        appendLine("- Pasos seguidos por mensaje: ${if (maxToolRounds <= 0) "sin límite" else maxToolRounds}.")
        appendLine("- Imágenes por mensaje que ves: ${if (maxImagesPerTurn <= 0) "sin límite" else maxImagesPerTurn}; vídeos: ${if (maxVideosPerTurn <= 0) "sin límite" else maxVideosPerTurn}.")
        appendLine("- Encargos por lote: ${if(maxGenerationsPerBatch==0) "sin límite" else maxGenerationsPerBatch}; consultas: ${if(maxToolResults==0) "sin límite" else maxToolResults}; candidatos: ${if(maxRecommendations==0) "sin límite" else maxRecommendations}.")
        append("No te impongas límites más estrictos que estos ni rechaces una petición por una cifra que no esté en esta lista.")
    }
}

/**
 * Valores propios de un cerebro que sustituyen a los globales. null = usar el global;
 * en los campos con «0 = sin límite», 0 desactiva el tope solo para este cerebro.
 */
@Serializable
data class LimitOverrides(
    val maxToolRounds: Int? = null,
    val turnTimeoutSeconds: Int? = null,
    val maxTokens: Int? = null,
    /** Gasto máximo por mensaje en USD. 0 = sin límite. */
    val turnUsd: Double? = null,
    /** Gasto máximo diario en USD. 0 = sin límite. */
    val dailyUsd: Double? = null,
    /** Llamadas al LLM por mensaje. 0 = sin límite. */
    val maxCallsPerTurn: Int? = null,
    /** Tiempo de espera de cada petición HTTP, en segundos. 0 = sin límite. */
    val httpTimeoutSeconds: Int? = null,
) {
    fun problems(): List<String> = buildList {
        if (listOfNotNull(maxToolRounds, turnTimeoutSeconds, maxCallsPerTurn, httpTimeoutSeconds).any { it < 0 }) add("Los límites no pueden ser negativos (0 = sin límite).")
        if (maxTokens != null && maxTokens < 1) add("El máximo de tokens debe ser al menos 1.")
        listOfNotNull(turnUsd, dailyUsd).forEach { if (!(it.isFinite() && it >= 0.0)) add("El gasto máximo debe ser un número ≥ 0 (0 = sin límite).") }
    }
}
