package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.llm.LlmRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

fun use(id: String, name: String, json: String = "{}") = ContentBlock.ToolUse(id, name, Json.parseToJsonElement(json).jsonObject)

/** Resultado de la herramienta [toolUseId] en la última petición (lo que el LLM «ve»). */
fun LlmRequest.result(toolUseId: String): ContentBlock.ToolResult =
    messages.last().content.filterIsInstance<ContentBlock.ToolResult>().first { it.toolUseId == toolUseId }

fun LlmRequest.resultJson(toolUseId: String): JsonObject = result(toolUseId).content as JsonObject

fun LlmRequest.toolNames(): Set<String> = tools.map { it.name }.toSet()

fun JsonElement.path(vararg keys: String): JsonElement = keys.fold(this) { acc, k -> (acc as JsonObject)[k] ?: error("falta $k en $acc") }
