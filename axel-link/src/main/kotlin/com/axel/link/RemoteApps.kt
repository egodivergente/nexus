package com.axel.link

enum class RemoteAppState { OPEN, BACKGROUND, CLOSED, MISSING, UNKNOWN }

/** Lo que el propio equipo dice de una aplicación de escritorio. Nada se supone desde el cliente. */
data class RemoteApp(
    val id: String,
    val name: String,
    val state: RemoteAppState,
    val installed: Boolean,
    /** Cómo se lanza: exe, msix o shortcut. */
    val launch: String,
    val target: String,
    val version: String,
    /** Ruta de la herramienta de línea de órdenes del mismo producto, si está instalada. No cuenta como aplicación abierta. */
    val cli: String,
    val windows: Int,
    val minimized: Boolean,
    val foreground: Boolean,
    val error: String,
)

data class RemoteOpen(
    val app: RemoteApp,
    /** activated: ya estaba abierta y se trajo al frente; launched: se ha iniciado; none: no se hizo nada. */
    val did: String,
    /** Hay una ventana real tras la operación. */
    val confirmed: Boolean,
    /** Windows la dejó en primer plano. */
    val foreground: Boolean,
    val error: String,
)

/**
 * Aplicaciones de escritorio de un equipo Windows a través de AXEL Commander. El guion que detecta,
 * activa y abre viaja con el cliente y se instala solo en el equipo, con el nombre de su huella.
 */
class RemoteApps(private val remote: LinkRemote, val device: String = "axelpc") {
    constructor(client: LinkClient, device: String = "axelpc") : this(LinkRemote(client), device)

    private val script: ByteArray = RemoteApps::class.java.getResourceAsStream("/com/axel/link/axel-apps.ps1")?.use { it.readBytes() }
        ?: throw IllegalStateException("Falta el guion de aplicaciones")
    private val name = "axel-apps-" + LinkRemote.sha256(script).take(12) + ".ps1"

    /** Estado real de las aplicaciones conocidas. */
    fun status(): List<RemoteApp> = parse(invoke("status", "all", 45_000)).map { it.first }

    /** Activa la ventana si existe; si no, la inicia y confirma que aparece. Nunca crea una segunda instancia. */
    fun open(id: String): RemoteOpen {
        require(KNOWN.any { it.first == id }) { "Aplicación desconocida" }
        val (app, result) = parse(invoke("open", id, 90_000)).firstOrNull() ?: throw LinkException("El equipo no devolvió el estado de la aplicación")
        return RemoteOpen(app, result.text("did", "none"), result.flag("confirmed"), result.flag("foreground"), result.text("error"))
    }

    private fun invoke(action: String, id: String, timeoutMs: Long): Map<String, Any?> {
        val target = remote.need(device, "process.run")
        if (!target.platform.equals("windows", ignoreCase = true)) throw LinkRejected(409, "${target.name} no es un equipo Windows")
        val argv = listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-File", ".axel-link\\$name", "-Action", action, "-Id", id)
        report(remote.run(device, argv, null, timeoutMs))?.let { return it }
        // Primera vez en este equipo, o alguien borró el guion: se instala y se repite una sola vez.
        install()
        val second = remote.run(device, argv, null, timeoutMs)
        return report(second) ?: throw LinkException(second.stderr.lineSequence().firstOrNull { it.isNotBlank() }?.take(200)
            ?: "El equipo no pudo consultar sus aplicaciones")
    }

    private fun report(process: LinkProcess): Map<String, Any?>? =
        if (process.exitCode != 0) null
        else try { LinkJson.asObject(LinkJson.parse(process.stdout.trim().trimStart('\uFEFF')))?.takeIf { it.containsKey("apps") } } catch (error: IllegalArgumentException) { null }

    private fun install() {
        try { remote.makeDirectory(device, "~/.axel-link") } catch (exists: LinkJobFailed) { /* ya existe */ }
        // Commander no sobrescribe: si el guion ya estaba, el fallo era otro y lo dirá la segunda ejecución.
        try { remote.write(device, "~/.axel-link/$name", script) } catch (exists: LinkJobFailed) { /* ya instalado */ }
    }

    private fun parse(report: Map<String, Any?>): List<Pair<RemoteApp, Map<String, Any?>>> =
        report.items("apps").mapNotNull { LinkJson.asObject(it) }.map { item ->
            val windows = item.items("windows").mapNotNull { LinkJson.asObject(it) }
            val id = item.text("id")
            RemoteApp(
                id = id,
                name = item.text("name").ifBlank { KNOWN.firstOrNull { it.first == id }?.second ?: id },
                state = when (item.text("state")) {
                    "open" -> RemoteAppState.OPEN
                    "background" -> RemoteAppState.BACKGROUND
                    "closed" -> RemoteAppState.CLOSED
                    "missing" -> RemoteAppState.MISSING
                    else -> RemoteAppState.UNKNOWN
                },
                installed = item.flag("installed"),
                launch = item.text("launch"),
                target = item.text("target"),
                version = item.text("version"),
                cli = item.text("cli"),
                windows = windows.size,
                minimized = windows.isNotEmpty() && windows.all { it.flag("minimized") },
                foreground = windows.any { it.flag("foreground") },
                error = item.text("error"),
            ) to item.child("result")
        }

    companion object {
        /** Identificador y nombre de las aplicaciones que el guion sabe localizar. */
        val KNOWN: List<Pair<String, String>> = listOf(
            "agy" to "Google Antigravity",
            "claude" to "Claude",
            "codex" to "Codex",
            "nexus" to "Nexus Desktop",
        )
    }
}
