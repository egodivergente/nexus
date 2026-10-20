package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.config.AgentLimits
import kotlinx.serialization.json.*

/** Controls follow the actual serialized settings, so a future field cannot silently disappear from the form. */
internal object AgentLimitForm {
    private val json=Json {encodeDefaults=true;ignoreUnknownKeys=true}
    val labels=mapOf(
        "maxToolRounds" to "Pasos de herramientas por mensaje",
        "turnTimeoutSeconds" to "Duración por mensaje (s)",
        "flowMaxResults" to "Resultados de Flow por encargo",
        "maxImagesPerTurn" to "Imágenes que ve el agente por mensaje",
        "maxVideosPerTurn" to "Vídeos que ve el agente por mensaje",
        "maxDocumentsPerTurn" to "Documentos por mensaje",
        "maxUiContextChars" to "Caracteres del estado de la interfaz",
        "jobTrackingMinutes" to "Seguimiento de trabajos (min)",
        "maxMessages" to "Mensajes de historial que se reenvían",
        "maxToolResultChars" to "Caracteres por resultado de herramienta",
        "maxGenerationsPerBatch" to "Encargos por lote",
        "maxConcurrentGenerations" to "Envíos simultáneos",
        "maxConcurrentFlowGenerations" to "Envíos simultáneos a Flow",
        "maxToolResults" to "Resultados por consulta de modelos, biblioteca e historial",
        "maxRecommendations" to "Candidatos por recomendación",
        "maxRecommendationSchemaChecks" to "Modelos cuyo esquema se consulta por recomendación",
        "maxProjectContextChars" to "Caracteres de memoria en la vista de proyecto",
        "maxOverviewScenes" to "Escenas en la vista de proyecto",
        "maxOverviewOutputs" to "Archivos en la vista de proyecto",
        "maxOverviewGenerations" to "Generaciones en la vista de proyecto",
        "maxOverviewPromptChars" to "Caracteres de prompt en la vista de proyecto",
        "maxMemorySearchResults" to "Coincidencias de memoria del proyecto",
        "maxMemoryExcerptChars" to "Caracteres por coincidencia de memoria",
        "maxFlowMedia" to "Archivos recuperados por respuesta de Flow",
        "maxFlowWaitSeconds" to "Espera máxima por consulta al agente de Flow (s)",
        "maxConcurrentSubagents" to "Subagentes simultáneos",
        "maxSubagentDepth" to "Niveles de delegación",
        "allowSubagentGeneration" to "Permitir que los subagentes preparen generaciones"
    )
    fun values(limits:AgentLimits):Map<String,String> = json.encodeToJsonElement(AgentLimits.serializer(),limits).jsonObject
        .mapValues {it.value.jsonPrimitive.content}
    fun parse(current:AgentLimits,draft:Map<String,String>):AgentLimits {
        val original=json.encodeToJsonElement(AgentLimits.serializer(),current).jsonObject
        val changed=original.mapValues {(key,value)->
            if(key=="maxTokens") value else {
                val text=draft[key] ?: value.jsonPrimitive.content
                if(value.jsonPrimitive.booleanOrNull!=null) JsonPrimitive(text.toBooleanStrictOrNull()
                    ?: throw IllegalArgumentException("${labels[key] ?: key}: indica sí o no."))
                else JsonPrimitive(text.toIntOrNull()
                    ?: throw IllegalArgumentException("${labels[key] ?: key}: indica un número entero válido."))
            }
        }
        val limits=json.decodeFromJsonElement(AgentLimits.serializer(),JsonObject(changed))
        require(limits.problems().isEmpty()) {limits.problems().joinToString("\n")}
        return limits
    }
}
