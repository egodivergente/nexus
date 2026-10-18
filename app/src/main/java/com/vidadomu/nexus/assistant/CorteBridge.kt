package com.vidadomu.nexus.assistant

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.vidadomu.nexus.agent.core.tools.ToolInputException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Cliente del puente de Corte (`content://com.vidadomu.corte.agent`, ver AGENT_BRIDGE.md en el repo de Corte).
 * Corte valida y aplica cada edición con su propio motor e historial; aquí solo se traduce la herramienta.
 */
internal class CorteBridge(private val context: Context) {
    fun run(operation: String, input: JsonObject): JsonObject {
        val extras = Bundle()
        input.str("project_id")?.let { extras.putString("project_id", it) }
        val method = when (operation) {
            "corte_status" -> "status"
            "corte_list_projects" -> "list_projects"
            "corte_read_project" -> "read_project"
            "corte_undo" -> "undo"
            "corte_find_silences" -> {
                (input["clip_ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.takeIf { it.isNotEmpty() }
                    ?.let { extras.putStringArray("clip_ids", it.toTypedArray()) }
                (input["threshold_db"] as? JsonPrimitive)?.doubleOrNull?.let { extras.putDouble("threshold_db", it) }
                (input["min_ms"] as? JsonPrimitive)?.longOrNull?.let { extras.putLong("min_ms", it) }
                (input["pad_ms"] as? JsonPrimitive)?.longOrNull?.let { extras.putLong("pad_ms", it) }
                "find_silences"
            }
            "corte_apply_ops" -> {
                val ops = input.str("ops_json") ?: throw ToolInputException("Indica ops_json (array JSON de operaciones).")
                extras.putString("ops", ops)
                input.str("label")?.let { extras.putString("label", it) }
                "apply_ops"
            }
            else -> throw ToolInputException("Operación de Corte desconocida.")
        }
        val reply = try {
            context.contentResolver.call(URI, method, null, extras)
        } catch (e: SecurityException) {
            throw ToolInputException("Corte rechazó la conexión: las dos apps deben estar firmadas con la misma clave y Corte debe ser la versión con el puente del agente.")
        } catch (e: IllegalArgumentException) {
            throw ToolInputException("Corte no está instalado o es una versión sin el puente del agente. Actualiza Corte.")
        } ?: throw ToolInputException("Corte no respondió. Ábrelo y vuelve a intentarlo.")
        val raw = reply.getString("json") ?: throw ToolInputException("Corte devolvió una respuesta vacía.")
        val obj = Json.parseToJsonElement(raw).jsonObject
        return if (obj["ok"]?.jsonPrimitive?.contentOrNull == "false")
            buildJsonObject { put("isError", true); put("error", obj["error"]?.jsonPrimitive?.contentOrNull ?: "Corte no pudo completar la operación.") }
        else obj
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        val URI: Uri = Uri.parse("content://com.vidadomu.corte.agent")
    }
}
