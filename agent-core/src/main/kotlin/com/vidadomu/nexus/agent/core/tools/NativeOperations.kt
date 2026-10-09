package com.vidadomu.nexus.agent.core.tools

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/** Android implements only these explicit operations; no shell, arbitrary paths or secrets. */
fun interface NativeOperations {
    suspend fun run(operation: String, input: JsonObject, session: AgentSession): JsonObject
}

object NativeOperationTools {
    fun all(operations: NativeOperations): List<AgentTool> = listOf(
        NativeTool(operations, "get_active_generations", ToolCategory.READ,
            "Consulta los trabajos pendientes reales, sus errores y antigüedad. No envía generaciones.",
            schema { integer("limit", "Máximo 50."); string("provider_id", "Filtrar proveedor.") }),
        NativeTool(operations, "recover_generation", ToolCategory.ORGANIZE,
            "Consulta de nuevo el proveedor y restaura el seguimiento de UN job existente. No vuelve a generar ni borra resultados.",
            schema { string("job_id", "Id local del historial.", required = true) }),
        NativeTool(operations, "diagnose_flow", ToolCategory.READ,
            "Comprueba conexión, sesión y capacidad real del puente Flow y enumera su cola local.",
            schema {}),
        NativeTool(operations, "repair_flow", ToolCategory.ORGANIZE,
            "Recupera el seguimiento local de Flow, sin nuevos envíos ni reiniciar el navegador. Devuelve si requiere iniciar sesión o abrir proyecto.",
            schema {}),
        NativeTool(operations, "get_nexus_settings", ToolCategory.READ,
            "Lee modelo del agente, tema y preferencias editables. Nunca devuelve credenciales.",
            schema {}),
        NativeTool(operations, "update_nexus_settings", ToolCategory.ORGANIZE,
            "Cambia ajustes reales; el modelo se aplica en el siguiente mensaje. No cambia claves ni conexiones.",
            schema {
                string("agent_model", "Modelo del agente.", enum = listOf("sonnet-4.6", "opus-4.6", "haiku-4.5"))
                integer("max_tokens", "De 256 a 32000.")
                boolean("send_images", "Permitir enviar adjuntos visuales al agente.")
                string("excluded_families", "Familias que no se recomiendan, separadas por comas.")
                string("theme", "Tema.", enum = listOf("AURORA", "OLED", "QUARTZ", "OCEAN", "FOREST", "LIGHT"))
                boolean("remember_parameters", "Recordar parámetros del Lienzo.")
            }),
        NativeTool(operations, "rename_asset_file", ToolCategory.ORGANIZE,
            "Renombra físicamente un archivo del índice: copias privadas Nexus o MediaStore modificable. Conserva compatibilidad de URIs y devuelve el resultado real.",
            schema { string("asset_id", "Id del archivo indexado.", required = true); string("file_name", "Nombre sin rutas; conserva extensión.", required = true) }),
        NativeTool(operations, "convert_image_asset", ToolCategory.ORGANIZE,
            "Crea una imagen PNG/JPEG/WebP local desde un asset, opcionalmente redimensionada o recortada al centro. Conserva el original e indexa el resultado.",
            schema {
                string("asset_id", "Id de la imagen.", required = true)
                string("format", "Formato.", required = true, enum = listOf("png", "jpeg", "webp"))
                integer("quality", "Calidad JPEG/WebP: 1 a 100.")
                integer("max_dimension", "Lado máximo, 64 a 4096; no amplía.")
                string("aspect_ratio", "Recorte al centro opcional.", enum = listOf("16:9", "9:16", "1:1", "4:3", "3:4"))
            }),
        NativeTool(operations, "atlas_get_balance", ToolCategory.READ,
            "Consulta saldo disponible, efectivo, bono y fondos retenidos en la API oficial de Atlas. No genera ni estima un saldo local.",
            schema {}),
    )
}

private class NativeTool(
    private val operations: NativeOperations,
    name: String, category: ToolCategory, description: String, inputSchema: JsonObject,
) : BaseTool(name, category, description, inputSchema) {
    override fun describe(input: JsonObject) = description + if (input.isEmpty()) "" else " · $input"
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome = try {
        ok(operations.run(name, input, session))
    } catch (error: CancellationException) {
        throw error
    } catch (error: ToolInputException) {
        fail(error.message ?: "Entrada no válida.")
    } catch (error: Exception) {
        // Do not echo provider bodies, signed URLs, paths or credentials through generic exceptions.
        fail("No se pudo completar $name (${error.javaClass.simpleName}). Los datos se conservan.")
    }
}
