package com.axel.link

import java.io.File
import java.net.URI

/** Una ruta HTTPS hasta el coordinador de AXEL Commander. */
data class LinkRoute(val id: String, val url: String)

/**
 * Dónde publica el coordinador su dirección pública vigente. Con cualquier ruta en pie, el cliente
 * la lee a través del propio Commander y actualiza la ruta [route] si ha cambiado.
 */
data class LinkDiscovery(val device: String, val path: String, val route: String)

/**
 * Identidad de cliente y rutas. Vive en un archivo privado de cada aplicación; nunca en el repositorio.
 * El orden de [routes] es el de preferencia.
 */
data class LinkConfig(
    val clientId: String,
    val token: String,
    val routes: List<LinkRoute>,
    val discovery: LinkDiscovery? = null,
) {
    init {
        require(token.isNotBlank()) { "Falta la credencial de cliente" }
        require(routes.isNotEmpty()) { "Falta al menos una ruta" }
        require(routes.map { it.id }.toSet().size == routes.size) { "Rutas repetidas" }
        routes.forEach { checkEndpoint(it.url) }
    }

    fun withRoute(id: String, url: String): LinkConfig = copy(routes = routes.map { if (it.id == id) LinkRoute(id, normalize(url)) else it })

    fun toJson(): String = LinkJson.write(linkedMapOf(
        "schema" to 1,
        "client_id" to clientId,
        "token" to token,
        "routes" to routes.map { linkedMapOf("id" to it.id, "url" to it.url) },
        "discovery" to discovery?.let { linkedMapOf("device" to it.device, "path" to it.path, "route" to it.route) },
    ))

    companion object {
        fun parse(text: String): LinkConfig {
            val root = LinkJson.obj(text)
            val routes = root.items("routes").mapNotNull { LinkJson.asObject(it) }.map { LinkRoute(it.text("id"), normalize(it.text("url"))) }
            val discovery = root.child("discovery").takeIf { it.isNotEmpty() }?.let {
                LinkDiscovery(it.text("device"), it.text("path"), it.text("route"))
            }?.takeIf { it.device.isNotBlank() && it.path.isNotBlank() && it.route.isNotBlank() }
            return LinkConfig(root.text("client_id"), root.text("token"), routes, discovery)
        }

        internal fun normalize(url: String): String = url.trim().trimEnd('/')

        /** HTTPS obligatorio; HTTP solo hacia el propio equipo. Sin credenciales, consulta ni fragmento en la dirección. */
        internal fun checkEndpoint(url: String) {
            val uri = try { URI(url) } catch (error: Exception) { throw IllegalArgumentException("Dirección no válida") }
            val host = uri.host ?: throw IllegalArgumentException("Dirección sin servidor")
            val loopback = host == "127.0.0.1" || host == "localhost" || host == "[::1]"
            require(uri.scheme == "https" || (uri.scheme == "http" && loopback)) { "La ruta debe ser HTTPS" }
            require(uri.userInfo == null && uri.query == null && uri.fragment == null) { "La dirección no admite credenciales, consulta ni fragmento" }
        }
    }
}

/** Dónde guarda cada aplicación su configuración privada. */
interface LinkStore {
    fun read(): String?
    fun write(text: String)
}

/** Archivo privado con escritura atómica. */
class FileLinkStore(private val file: File) : LinkStore {
    override fun read(): String? = if (file.isFile) file.readText(Charsets.UTF_8) else null

    override fun write(text: String) {
        file.parentFile?.mkdirs()
        val pending = File(file.parentFile, file.name + ".tmp")
        pending.writeText(text, Charsets.UTF_8)
        pending.setReadable(false, false)
        pending.setReadable(true, true)
        pending.setWritable(false, false)
        pending.setWritable(true, true)
        if (!pending.renameTo(file)) {
            file.delete()
            if (!pending.renameTo(file)) throw java.io.IOException("No se pudo guardar la configuración de AXEL Link")
        }
    }
}
