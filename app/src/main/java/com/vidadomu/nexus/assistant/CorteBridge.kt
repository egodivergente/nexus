package com.vidadomu.nexus.assistant

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.tools.ToolInputException
import java.io.File
import kotlinx.serialization.json.booleanOrNull
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
internal class CorteBridge(private val context: Context, private val resolve: (String) -> Asset? = { null }) {
    fun run(operation: String, input: JsonObject): JsonObject {
        val extras = Bundle()
        val open = ArrayList<ParcelFileDescriptor>()
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
            "corte_import_media" -> {
                val ids = (input["asset_ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty().distinct()
                if (ids.isEmpty()) throw ToolInputException("Indica asset_ids (ids de la biblioteca de Nexus; usa list_library_assets o search_assets).")
                if (ids.size > 10) throw ToolInputException("Importa como máximo 10 archivos por llamada.")
                val names = ArrayList<String>()
                try {
                    ids.forEachIndexed { i, id ->
                        val asset = resolve(id) ?: throw ToolInputException("Archivo desconocido: $id. Usa list_library_assets o search_assets.")
                        val uri = Uri.parse(asset.uri)
                        val fd = try {
                            when (uri.scheme) {
                                "file" -> ParcelFileDescriptor.open(File(uri.path ?: ""), ParcelFileDescriptor.MODE_READ_ONLY)
                                "content" -> context.contentResolver.openFileDescriptor(uri, "r")
                                else -> null
                            }
                        } catch (e: Exception) { null } ?: throw ToolInputException("No pude abrir «${asset.displayName}»: ya no está disponible en este dispositivo.")
                        open += fd
                        extras.putParcelable("fd$i", fd)
                        names += fileNameFor(asset)
                    }
                } catch (e: ToolInputException) { open.forEach { runCatching { it.close() } }; throw e }
                extras.putStringArray("names", names.toTypedArray())
                extras.putBoolean("place", (input["place"] as? JsonPrimitive)?.booleanOrNull ?: true)
                (input["at_us"] as? JsonPrimitive)?.longOrNull?.let { extras.putLong("at_us", it) }
                "import_media"
            }
            else -> throw ToolInputException("Operación de Corte desconocida.")
        }
        val reply = try {
            context.contentResolver.call(URI, method, null, extras)
        } catch (e: SecurityException) {
            throw ToolInputException("Corte rechazó la conexión: las dos apps deben estar firmadas con la misma clave y Corte debe ser la versión con el puente del agente.")
        } catch (e: IllegalArgumentException) {
            throw ToolInputException("Corte no está instalado o es una versión sin el puente del agente. Actualiza Corte.")
        } finally {
            open.forEach { runCatching { it.close() } }
        } ?: throw ToolInputException("Corte no respondió. Ábrelo y vuelve a intentarlo.")
        val raw = reply.getString("json") ?: throw ToolInputException("Corte devolvió una respuesta vacía.")
        val obj = Json.parseToJsonElement(raw).jsonObject
        return if (obj["ok"]?.jsonPrimitive?.contentOrNull == "false")
            buildJsonObject {
                put("isError", true)
                val msg = obj["error"]?.jsonPrimitive?.contentOrNull ?: "Corte no pudo completar la operación."
                put("error", if (msg.startsWith("Método desconocido")) "Esta versión de Corte no sabe importar archivos: actualiza Corte (puente protocolo 2)." else msg)
            }
        else obj
    }

    /** Nombre de archivo con extensión según el tipo, para que Corte lo muestre y lo reconozca bien. */
    private fun fileNameFor(a: Asset): String {
        val base = a.displayName.ifBlank { "nexus" }
        val ext = when (a.mimeType?.lowercase()) {
            "video/mp4" -> ".mp4"; "video/quicktime" -> ".mov"; "video/webm" -> ".webm"
            "image/jpeg" -> ".jpg"; "image/png" -> ".png"; "image/webp" -> ".webp"
            "audio/mpeg" -> ".mp3"; "audio/mp4", "audio/aac" -> ".m4a"; "audio/wav", "audio/x-wav" -> ".wav"
            else -> ""
        }
        return if (ext.isNotEmpty() && !base.lowercase().endsWith(ext)) base + ext else base
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        val URI: Uri = Uri.parse("content://com.vidadomu.corte.agent")
    }
}
