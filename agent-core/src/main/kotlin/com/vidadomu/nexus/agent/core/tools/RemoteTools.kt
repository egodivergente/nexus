package com.vidadomu.nexus.agent.core.tools

import com.axel.link.LinkClient
import com.axel.link.LinkException
import com.axel.link.LinkJobFailed
import com.axel.link.LinkOffline
import com.axel.link.LinkRemote
import com.axel.link.LinkState
import com.axel.link.LinkTimeout
import com.axel.link.LinkUnauthorized
import com.axel.link.LinkUnconfigured
import com.axel.link.RemoteApp
import com.axel.link.RemoteApps
import com.vidadomu.nexus.agent.core.agent.GeneratedMediaStore
import com.vidadomu.nexus.agent.core.llm.GeneratedMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Capacidades remotas de AXEL Commander para el agente: los mismos dispositivos, la misma identidad
 * de cliente y los mismos permisos que usa el resto de AXEL (cliente compartido `com.axel.link`).
 * Consultar es lectura; abrir, ejecutar y escribir modifican un equipo y siguen el modo del agente:
 * en Copiloto se proponen y en Agente se aplican.
 */
object RemoteTools {
    /** [media]: dónde guarda la app una captura de pantalla; sin ella no se ofrece la captura. */
    fun all(client: LinkClient, media: GeneratedMediaStore? = null): List<AgentTool> {
        val remote = LinkRemote(client)
        return listOfNotNull(
            RemoteDevicesTool(client),
            RemoteAppsStatusTool(remote),
            RemoteOpenAppTool(remote),
            RemoteRunProcessTool(remote),
            RemoteListFilesTool(remote),
            RemoteReadFileTool(remote),
            RemoteSearchFilesTool(remote),
            RemoteWriteFileTool(remote),
            RemoteListWindowsTool(remote),
            media?.let { RemoteCaptureScreenTool(remote, it) },
        )
    }

    /** Solo se ofrecen cuando este dispositivo ya tiene identidad de cliente en AXEL Commander. */
    fun available(client: LinkClient): Boolean {
        if (client.state is LinkState.Unconfigured) client.reload()
        return client.state !is LinkState.Unconfigured
    }

    internal fun explain(error: LinkException): String = when (error) {
        is LinkUnconfigured -> "Este dispositivo no está emparejado con AXEL Commander."
        is LinkUnauthorized -> "AXEL Commander ha rechazado la identidad de este dispositivo; hay que emparejarlo de nuevo."
        is LinkOffline -> "Sin conexión con AXEL Commander ahora mismo. No se ha hecho nada."
        is LinkTimeout -> "El dispositivo no terminó a tiempo (trabajo ${error.job.id}, estado ${error.job.status}). Puede seguir en marcha: no lo repitas sin comprobarlo."
        is LinkJobFailed -> "El dispositivo no pudo completarlo: ${error.message.orEmpty().take(300)}"
        else -> error.message?.take(300) ?: "AXEL Commander no pudo atender la petición."
    }

    internal fun appJson(app: RemoteApp): JsonObject = buildJsonObject {
        put("id", app.id)
        put("name", app.name)
        put("state", app.state.name.lowercase())
        put("installed", app.installed)
        put("windows", app.windows)
        put("minimized", app.minimized)
        put("foreground", app.foreground)
        if (app.version.isNotBlank()) put("version", app.version)
        if (app.launch.isNotBlank()) put("launch", app.launch)
        // La herramienta de terminal del mismo producto es otra cosa: no cuenta como aplicación abierta.
        put("cli_installed", app.cli.isNotBlank())
        if (app.error.isNotBlank()) put("error", app.error)
    }
}

private const val DEVICE_HELP = "Id del dispositivo, tal como lo devuelve remote_devices (p. ej. axelpc, asusvidal)."
private const val MAX_TEXT = 24_000

private fun clip(text: String): String = if (text.length <= MAX_TEXT) text else text.take(MAX_TEXT) + "\n… [recortado: ${text.length - MAX_TEXT} caracteres más]"

/** Las llamadas a Commander bloquean: siempre fuera del hilo del agente, y con errores que el modelo pueda entender. */
private abstract class RemoteTool(name: String, category: ToolCategory, description: String, inputSchema: JsonObject) :
    BaseTool(name, category, description, inputSchema) {
    protected abstract suspend fun work(input: JsonObject, session: AgentSession): ToolOutcome

    final override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome = try {
        withContext(Dispatchers.IO) { work(input, session) }
    } catch (error: CancellationException) {
        throw error
    } catch (error: ToolInputException) {
        throw error
    } catch (error: LinkException) {
        fail(RemoteTools.explain(error))
    } catch (error: Exception) {
        // Sin eco de rutas ni cuerpos: solo qué falló.
        fail("No se pudo completar $name (${error.javaClass.simpleName}). No se ha cambiado nada más.")
    }
}

private class RemoteDevicesTool(private val client: LinkClient) : RemoteTool(
    "remote_devices", ToolCategory.READ,
    "Lista los equipos de Vidal conectados por AXEL Commander (PC Windows, servidor Linux, teléfonos), si están en línea y qué ofrece cada uno AHORA (procesos, archivos, pantalla, control visual). Consúltalo antes de usar otra herramienta remote_*; no supongas capacidades.",
    schema {},
) {
    override suspend fun work(input: JsonObject, session: AgentSession): ToolOutcome {
        val devices = client.refresh()
        val state = client.state
        return ok {
            put("connection", when (state) {
                is LinkState.Online -> "online"
                is LinkState.Reconnecting -> "reconnecting"
                is LinkState.Unauthorized -> "unauthorized"
                is LinkState.Unconfigured -> "unpaired"
                LinkState.Connecting -> "connecting"
            })
            if (state is LinkState.Online) { put("route", state.route); put("latency_ms", state.latencyMs) }
            put("devices", JsonArray(devices.map { device ->
                buildJsonObject {
                    put("id", device.id)
                    put("name", device.name)
                    put("platform", device.platform)
                    put("online", device.online)
                    put("capabilities", JsonArray(device.capabilities.sorted().map { JsonPrimitive(it) }))
                }
            }))
        }
    }
}

private class RemoteAppsStatusTool(private val remote: LinkRemote) : RemoteTool(
    "remote_apps_status", ToolCategory.READ,
    "Estado REAL de las aplicaciones de escritorio de AXELPC (Google Antigravity, Claude, Codex, Nexus Desktop): open = tiene ventana, background = proceso o servicio sin ventana, closed, missing = no instalada. Lo responde el propio PC; no lo deduzcas.",
    schema {},
) {
    override suspend fun work(input: JsonObject, session: AgentSession): ToolOutcome {
        val apps = RemoteApps(remote).status()
        return ok { put("device", "axelpc"); put("apps", JsonArray(apps.map(RemoteTools::appJson))) }
    }
}

private class RemoteOpenAppTool(private val remote: LinkRemote) : RemoteTool(
    "remote_open_app", ToolCategory.ORGANIZE,
    "Abre en AXELPC una aplicación de escritorio o trae su ventana al frente si ya está abierta; nunca crea una segunda instancia. Devuelve lo que pasó de verdad: did (activated|launched|none), confirmed (hay ventana) y foreground.",
    schema { string("app", "Aplicación.", required = true, enum = RemoteApps.KNOWN.map { it.first }) },
) {
    override fun describe(input: JsonObject): String {
        val id = input.str("app").orEmpty()
        return "Abrir «${RemoteApps.KNOWN.firstOrNull { it.first == id }?.second ?: id}» en AXELPC"
    }

    override suspend fun work(input: JsonObject, session: AgentSession): ToolOutcome {
        val id = input.req("app")
        if (RemoteApps.KNOWN.none { it.first == id }) throw ToolInputException("Aplicación desconocida «$id». Opciones: ${RemoteApps.KNOWN.joinToString { it.first }}.")
        val result = RemoteApps(remote).open(id)
        val body = buildJsonObject {
            put("did", result.did)
            put("confirmed", result.confirmed)
            put("foreground", result.foreground)
            if (result.error.isNotBlank()) put("error", result.error)
            put("app", RemoteTools.appJson(result.app))
        }
        return ToolOutcome(body, isError = !result.confirmed)
    }
}

private class RemoteRunProcessTool(private val remote: LinkRemote) : RemoteTool(
    "remote_run_process", ToolCategory.ORGANIZE,
    "Ejecuta un programa en un equipo que ofrezca procesos (process.run) y espera a que termine. Sin shell: cada argumento va por separado, y para una orden de shell hay que llamar al intérprete (p. ej. [\"bash\",\"-lc\",\"…\"] o [\"powershell.exe\",\"-NoProfile\",\"-Command\",\"…\"]). Devuelve código de salida, stdout y stderr reales.",
    schema {
        string("device", DEVICE_HELP, required = true)
        stringArray("argv", "Programa y argumentos, uno por elemento.", required = true)
        string("cwd", "Carpeta de trabajo; por defecto la carpeta personal del equipo.")
        integer("timeout_seconds", "Espera máxima, de 1 a 300; por defecto 60.")
    },
) {
    override fun describe(input: JsonObject): String =
        "Ejecutar en ${input.str("device") ?: "?"}: ${input.strList("argv").joinToString(" ").take(160)}"

    override suspend fun work(input: JsonObject, session: AgentSession): ToolOutcome {
        val device = input.req("device")
        val argv = input.strList("argv")
        if (argv.isEmpty()) throw ToolInputException("Falta «argv»: el programa y sus argumentos.")
        val seconds = (input.int("timeout_seconds") ?: 60).coerceIn(1, 300)
        val done = remote.run(device, argv, input.str("cwd"), seconds * 1000L)
        val body = buildJsonObject {
            put("device", device)
            put("exit_code", done.exitCode)
            put("stdout", clip(done.stdout))
            put("stderr", clip(done.stderr))
            if (done.truncated) put("truncated_by_device", true)
        }
        return ToolOutcome(body, isError = done.exitCode != 0)
    }
}

private class RemoteListFilesTool(private val remote: LinkRemote) : RemoteTool(
    "remote_list_files", ToolCategory.READ,
    "Lista una carpeta de un equipo remoto (nombre, ruta, si es carpeta, tamaño). En los teléfonos solo existe la carpeta de intercambio de Commander.",
    schema {
        string("device", DEVICE_HELP, required = true)
        string("path", "Carpeta; ~ es la carpeta personal del equipo.", required = true)
        integer("limit", "Máximo de entradas, de 1 a 500; por defecto 200.")
    },
) {
    override suspend fun work(input: JsonObject, session: AgentSession): ToolOutcome {
        val entries = remote.list(input.req("device"), input.req("path"), (input.int("limit") ?: 200).coerceIn(1, 500))
        return ok {
            put("entries", JsonArray(entries.map { entry ->
                buildJsonObject {
                    put("name", entry.name); put("path", entry.path); put("directory", entry.directory)
                    entry.size?.let { put("size", it) }
                }
            }))
        }
    }
}

private class RemoteReadFileTool(private val remote: LinkRemote) : RemoteTool(
    "remote_read_file", ToolCategory.READ,
    "Lee texto de un archivo de un equipo remoto (hasta 1 MiB por llamada desde offset; aquí se devuelve como mucho el principio).",
    schema {
        string("device", DEVICE_HELP, required = true)
        string("path", "Archivo de texto.", required = true)
        integer("offset", "Byte desde el que leer; por defecto 0.")
    },
) {
    override suspend fun work(input: JsonObject, session: AgentSession): ToolOutcome {
        val text = remote.readText(input.req("device"), input.req("path"), (input.int("offset") ?: 0).coerceAtLeast(0).toLong())
        return ok { put("text", clip(text)); put("characters", text.length) }
    }
}

private class RemoteSearchFilesTool(private val remote: LinkRemote) : RemoteTool(
    "remote_search_files", ToolCategory.READ,
    "Busca en un equipo remoto archivos por nombre o, con content=true, líneas que contengan el texto.",
    schema {
        string("device", DEVICE_HELP, required = true)
        string("path", "Carpeta desde la que buscar.", required = true)
        string("query", "Texto que buscar.", required = true)
        boolean("content", "Buscar dentro de los archivos en vez de en los nombres.")
        integer("limit", "Máximo de resultados, de 1 a 200; por defecto 50.")
    },
) {
    override suspend fun work(input: JsonObject, session: AgentSession): ToolOutcome {
        val matches = remote.search(input.req("device"), input.req("path"), input.req("query"),
            input.bool("content") ?: false, (input.int("limit") ?: 50).coerceIn(1, 200))
        return ok { put("matches", JsonArray(matches.map { JsonPrimitive(it.take(400)) })) }
    }
}

private class RemoteWriteFileTool(private val remote: LinkRemote) : RemoteTool(
    "remote_write_file", ToolCategory.ORGANIZE,
    "Crea un archivo de texto NUEVO en un equipo remoto. No sobrescribe: si ya existe con otro contenido, el equipo lo rechaza y no cambia nada.",
    schema {
        string("device", DEVICE_HELP, required = true)
        string("path", "Ruta del archivo nuevo.", required = true)
        string("text", "Contenido completo, en UTF-8.", required = true)
    },
) {
    override fun describe(input: JsonObject): String =
        "Crear ${input.str("path") ?: "un archivo"} en ${input.str("device") ?: "?"} (${(input["text"] as? JsonPrimitive)?.content?.length ?: 0} caracteres)"

    override suspend fun work(input: JsonObject, session: AgentSession): ToolOutcome {
        // El contenido se toma tal cual: un archivo puede empezar o acabar con espacios.
        val text = (input["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw ToolInputException("Falta «text».")
        val bytes = text.toByteArray(Charsets.UTF_8)
        val path = remote.write(input.req("device"), input.req("path"), bytes)
        return ok { put("path", path); put("bytes", bytes.size) }
    }
}

private class RemoteListWindowsTool(private val remote: LinkRemote) : RemoteTool(
    "remote_list_windows", ToolCategory.READ,
    "Ventanas visibles ahora en el escritorio de un equipo con control visual (títulos y cuál está activa). No captura imagen.",
    schema { string("device", DEVICE_HELP, required = true) },
) {
    override suspend fun work(input: JsonObject, session: AgentSession): ToolOutcome {
        val windows = remote.windows(input.req("device"))
        return ok {
            put("windows", JsonArray(windows.filter { it.title.isNotBlank() }.map { window ->
                buildJsonObject { put("title", window.title.take(200)); put("active", window.active) }
            }))
        }
    }
}

private class RemoteCaptureScreenTool(private val remote: LinkRemote, private val media: GeneratedMediaStore) : RemoteTool(
    "remote_capture_screen", ToolCategory.READ,
    "Captura la pantalla de un equipo que ofrezca screen.capture, la guarda en la Biblioteca y la muestra en el chat. Devuelve asset_id: para mirarla tú, cárgala con view_library_asset.",
    schema {
        string("device", DEVICE_HELP, required = true)
        integer("max_width", "Ancho máximo en píxeles, de 480 a 2560; por defecto 1280.")
    },
) {
    override suspend fun work(input: JsonObject, session: AgentSession): ToolOutcome {
        val device = input.req("device")
        val image = remote.capture(device, (input.int("max_width") ?: 1280).coerceIn(480, 2560))
        val extension = if (image.mime.contains("jpeg") || image.mime.contains("jpg")) "jpg" else "png"
        val attachment = media.save(GeneratedMedia(image.bytes, image.mime, "pantalla-$device.$extension", "Commander",
            width = image.width.takeIf { it > 0 }, height = image.height.takeIf { it > 0 }), session)
        session.showMedia(attachment)
        return ok {
            put("asset_id", attachment.assetId ?: attachment.id)
            put("width", image.width); put("height", image.height); put("bytes", image.bytes.size)
        }
    }
}
