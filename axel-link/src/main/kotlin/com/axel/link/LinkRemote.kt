package com.axel.link

import java.security.MessageDigest
import java.util.Base64

data class LinkProcess(val exitCode: Int, val stdout: String, val stderr: String, val truncated: Boolean)
data class LinkFile(val name: String, val path: String, val directory: Boolean, val size: Long?)
class LinkImage(val mime: String, val bytes: ByteArray, val width: Int, val height: Int)
data class LinkWindow(val handle: Long, val title: String, val active: Boolean)

/**
 * La API común sobre los dispositivos: procesos, archivos y pantalla. Cada llamada comprueba antes
 * lo que el dispositivo anuncia y explica en claro por qué no puede, en vez de fallar a medias.
 */
class LinkRemote(val client: LinkClient) {

    /** Ejecuta un programa sin shell intermedia y espera a que termine. */
    fun run(device: String, argv: List<String>, cwd: String? = null, timeoutMs: Long = 120_000): LinkProcess {
        need(device, "process.run")
        val args = linkedMapOf<String, Any?>("argv" to argv)
        if (!cwd.isNullOrBlank()) args["cwd"] = cwd
        // Commander da por fallido el trabajo de un programa que sale con error, pero conserva su código y
        // su salida: eso es un resultado. Solo es un fallo del trabajo si el programa no llegó a ejecutarse.
        val done = client.await(client.submit(device, "process.run", args), timeoutMs)
        val result = done.result
        val code = result.number("exit_code")
        if (!done.succeeded && code == null) throw LinkJobFailed(done)
        return LinkProcess(
            // Windows da códigos de 32 bits sin signo (4294770688): se leen como el entero que son.
            exitCode = code?.toLong()?.toInt() ?: -1,
            stdout = result.text("stdout"),
            stderr = result.text("stderr"),
            truncated = result.flag("stdout_truncated") || result.flag("stderr_truncated"),
        )
    }

    fun list(device: String, path: String, limit: Int = 200): List<LinkFile> {
        need(device, "files.list")
        val result = client.execute(device, "files.list", mapOf("path" to path, "limit" to limit), 30_000)
        return result.items("entries").mapNotNull { LinkJson.asObject(it) }.map {
            LinkFile(it.text("name"), it.text("path"), it.flag("directory"), it.number("size")?.toLong())
        }
    }

    /** Hasta 1 MiB de texto desde [offset]. */
    fun readText(device: String, path: String, offset: Long = 0): String {
        need(device, "files.read")
        return client.execute(device, "files.read", mapOf("path" to path, "offset" to offset), 30_000).text("text")
    }

    fun search(device: String, path: String, query: String, content: Boolean = false, limit: Int = 50): List<String> {
        need(device, "files.search")
        val result = client.execute(device, "files.search", mapOf("path" to path, "query" to query, "content" to content, "limit" to limit), 60_000)
        return result.items("matches").mapNotNull { LinkJson.asObject(it) }.map { match ->
            match.text("path") + (match.number("line")?.let { ":" + it.toLong() + ": " + match.text("text") } ?: "")
        }
    }

    fun makeDirectory(device: String, path: String) {
        need(device, "files.mkdir")
        client.execute(device, "files.mkdir", mapOf("path" to path, "parents" to true), 30_000)
    }

    /**
     * Crea un archivo nuevo. Commander no sobrescribe: si el destino existe con el mismo contenido se da
     * por hecho, y si existe con otro contenido el dispositivo lo rechaza.
     */
    fun write(device: String, path: String, bytes: ByteArray): String {
        need(device, "files.write_chunk")
        val sha = sha256(bytes)
        var offset = 0
        var result: Map<String, Any?> = emptyMap()
        do {
            val end = (offset + CHUNK).coerceAtMost(bytes.size)
            result = client.execute(device, "files.write_chunk", linkedMapOf(
                "path" to path,
                "transfer_id" to "link-" + sha.take(40),
                "sha256" to sha,
                "size" to bytes.size,
                "offset" to offset,
                "data_b64" to Base64.getEncoder().encodeToString(bytes.copyOfRange(offset, end)),
                "final" to (end == bytes.size),
            ), 60_000)
            offset = end
        } while (offset < bytes.size)
        return result.text("path", path)
    }

    /** Captura de pantalla; [maxWidth] la reduce en origen para no mover imágenes enormes. */
    fun capture(device: String, maxWidth: Int = 1280): LinkImage {
        need(device, "screen.capture")
        val result = client.execute(device, "screen.capture", mapOf("max_width" to maxWidth), 45_000)
        val data = result.text("data_b64")
        if (data.isBlank()) throw LinkException("El dispositivo no devolvió imagen")
        return LinkImage(result.text("mime_type", "image/png"), Base64.getDecoder().decode(data),
            result.number("width")?.toInt() ?: 0, result.number("height")?.toInt() ?: 0)
    }

    /** Ventanas visibles del escritorio remoto. */
    fun windows(device: String): List<LinkWindow> {
        need(device, "ui.inspect")
        val result = client.execute(device, "ui.inspect", emptyMap(), 30_000)
        return result.items("windows").mapNotNull { LinkJson.asObject(it) }.map {
            LinkWindow(it.number("hwnd")?.toLong() ?: 0L, it.text("title"), it.flag("active"))
        }
    }

    /** Comprueba, con lo último que anunció el coordinador, que el dispositivo puede hacer la operación. */
    fun need(device: String, operation: String): LinkDevice {
        val found = client.device(device) ?: client.refresh().firstOrNull { it.id == device }
            ?: throw LinkRejected(404, "Este cliente no tiene acceso al dispositivo $device")
        val fresh = if (found.can(operation)) found else client.refresh().firstOrNull { it.id == device } ?: found
        if (!fresh.online) throw LinkRejected(409, "${fresh.name} está sin conexión")
        if (operation !in fresh.capabilities) throw LinkRejected(409, "${fresh.name} no ofrece $operation ahora mismo")
        return fresh
    }

    companion object {
        private const val CHUNK = 512 * 1024

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
