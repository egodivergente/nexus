package com.axel.link

import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Estado de la conexión con el coordinador, para pintarlo tal cual. */
sealed class LinkState {
    /** Esta aplicación aún no tiene identidad de cliente. */
    data class Unconfigured(val reason: String = "") : LinkState()
    object Connecting : LinkState()
    data class Online(val route: String, val latencyMs: Long, val since: Long) : LinkState()
    /** Ninguna ruta responde. [retryAt] es el instante del próximo intento automático, o 0 si nadie vigila la conexión. */
    data class Reconnecting(val attempt: Int, val retryAt: Long, val reason: String) : LinkState()
    /** El coordinador rechazó la credencial: hay que emparejar de nuevo. */
    data class Unauthorized(val reason: String) : LinkState()
}

open class LinkException(message: String, cause: Throwable? = null) : Exception(message, cause)
/** Ninguna ruta llegó al coordinador. */
class LinkOffline(message: String, cause: Throwable? = null) : LinkException(message, cause)
class LinkUnconfigured(message: String) : LinkException(message)
class LinkUnauthorized(message: String) : LinkException(message)
/** El coordinador respondió, pero no acepta la petición (permiso, argumento o capacidad). */
class LinkRejected(val code: Int, message: String) : LinkException(message)
/** El trabajo terminó sin éxito en el dispositivo. */
class LinkJobFailed(val job: LinkJob) : LinkException(job.error.ifBlank {
    when (job.status) {
        "cancelled" -> "La operación se canceló"
        "interrupted" -> "La operación se interrumpió en el dispositivo"
        else -> "El dispositivo no pudo completar la operación"
    }
})
/** El trabajo sigue en marcha pasado el plazo; su resultado se puede consultar después con su identificador. */
class LinkTimeout(val job: LinkJob) : LinkException("El dispositivo no ha terminado a tiempo")

/** Un equipo registrado en AXEL Commander y lo que anuncia que sabe hacer ahora mismo. */
data class LinkDevice(
    val id: String,
    val name: String,
    val platform: String,
    val online: Boolean,
    val capabilities: Set<String>,
    val lastSeenSeconds: Double?,
    val visual: Boolean,
) {
    fun can(operation: String): Boolean = online && operation in capabilities
}

data class LinkJob(
    val id: String,
    val device: String,
    val operation: String,
    val status: String,
    val result: Map<String, Any?>,
    val note: String,
) {
    val terminal: Boolean get() = status in TERMINAL
    val succeeded: Boolean get() = status == "succeeded"
    val error: String get() = result.text("error")

    companion object {
        private val TERMINAL = setOf("succeeded", "failed", "cancelled", "interrupted")
        internal fun from(value: Map<String, Any?>) = LinkJob(
            value.text("id"), value.text("device"), value.text("operation"), value.text("status"),
            value.child("result"), value.text("delivery_note"),
        )
    }
}

/**
 * Cliente único de AXEL Commander. Habla con el coordinador por HTTPS con la identidad de cliente de
 * la aplicación, prueba las rutas en orden, pasa a la siguiente si una cae y vuelve a la preferida
 * cuando responde. Las llamadas son bloqueantes: cada aplicación las lanza fuera de su hilo de interfaz.
 */
class LinkClient(
    private val store: LinkStore,
    private val transport: LinkTransport = OkHttpLinkTransport(),
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    private val lock = Object()
    private val listeners = CopyOnWriteArrayList<(LinkState) -> Unit>()
    @Volatile private var config: LinkConfig? = null
    @Volatile private var loaded: String? = null
    @Volatile private var known: List<LinkDevice> = emptyList()
    @Volatile private var current: LinkState = LinkState.Unconfigured()
    private var active = 0
    private var failures = 0
    private var preferredTriedAt = 0L
    private var discoveredAt = 0L
    private var holders = 0
    private var monitor: Thread? = null
    private var kicked = false

    init { reload() }

    val state: LinkState get() = current
    val clientId: String get() = config?.clientId.orEmpty()
    val routes: List<LinkRoute> get() = config?.routes.orEmpty()
    /** Últimos dispositivos vistos; no consulta la red. */
    val devices: List<LinkDevice> get() = known

    fun device(id: String): LinkDevice? = known.firstOrNull { it.id == id }

    /** Avisa de cada cambio de estado, desde el hilo que lo provoca. */
    fun addListener(listener: (LinkState) -> Unit): AutoCloseable {
        listeners.add(listener)
        return AutoCloseable { listeners.remove(listener) }
    }

    /** Vuelve a leer la configuración privada; sirve tras emparejar o cambiar la credencial. */
    fun reload() {
        val text = try { store.read() } catch (error: Exception) { null }
        if (text == loaded && config != null) return
        if (text.isNullOrBlank()) {
            config = null; loaded = text; known = emptyList()
            publish(LinkState.Unconfigured("Sin emparejar"))
            return
        }
        try {
            config = LinkConfig.parse(text)
            loaded = text
            synchronized(lock) { active = 0; failures = 0; discoveredAt = 0L }
            publish(LinkState.Connecting)
        } catch (error: Exception) {
            config = null; loaded = text; known = emptyList()
            publish(LinkState.Unconfigured(error.message ?: "Configuración no válida"))
        }
    }

    /** Llama a una herramienta del coordinador. Es la única puerta de salida a la red. */
    fun call(tool: String, arguments: Map<String, Any?> = emptyMap(), timeoutMs: Long = 20_000): Map<String, Any?> {
        val settings = config ?: throw LinkUnconfigured((current as? LinkState.Unconfigured)?.reason?.ifBlank { null } ?: "Sin emparejar")
        val body = LinkJson.write(linkedMapOf("name" to tool, "arguments" to arguments))
        var failure: Exception? = null
        for (index in order(settings)) {
            val route = settings.routes[index]
            val started = now()
            val response = try {
                transport.post(route.url + "/v1/tools/call", settings.token, body, timeoutMs)
            } catch (error: IOException) {
                failure = error
                continue
            }
            val parsed = try { LinkJson.asObject(LinkJson.parse(response.body)) } catch (error: IllegalArgumentException) { null }
            // Una página de error del túnel o del proxy no es el coordinador: esa ruta está caída.
            if (parsed == null || response.code in GATEWAY_ERRORS) {
                failure = IOException("La ruta ${route.id} respondió ${response.code}")
                continue
            }
            if (response.code == 200) {
                reached(index, route, now() - started)
                return parsed
            }
            val message = parsed.text("error", "Solicitud rechazada")
            if (response.code == 401 || (response.code == 403 && message in CREDENTIAL_ERRORS)) {
                publish(LinkState.Unauthorized(message))
                throw LinkUnauthorized(message)
            }
            reached(index, route, now() - started)
            throw LinkRejected(response.code, message)
        }
        val reason = failure?.message ?: "Sin red"
        val attempt = synchronized(lock) { failures += 1; failures }
        val watched = synchronized(lock) { holders > 0 }
        publish(LinkState.Reconnecting(attempt, if (watched) now() + backoff(attempt) else 0L, reason))
        throw LinkOffline("Sin conexión con AXEL Commander", failure)
    }

    /** Pregunta al coordinador qué equipos hay y qué anuncia cada uno. También sirve de latido. */
    fun refresh(): List<LinkDevice> {
        val answer = call("devices.list")
        val list = answer.items("devices").mapNotNull { LinkJson.asObject(it) }.map {
            LinkDevice(
                id = it.text("id"),
                name = it.text("name", it.text("id")),
                platform = it.text("platform"),
                online = it.flag("online"),
                capabilities = it.items("capabilities").map { item -> item.toString() }.toSet(),
                lastSeenSeconds = it.number("last_seen_seconds"),
                visual = it.flag("visual_control_available"),
            )
        }
        known = list
        return list
    }

    fun submit(
        device: String,
        operation: String,
        args: Map<String, Any?> = emptyMap(),
        key: String = UUID.randomUUID().toString(),
        leaseToken: String? = null,
    ): LinkJob {
        val request = linkedMapOf<String, Any?>("device_id" to device, "operation" to operation, "args" to args, "idempotency_key" to key)
        if (leaseToken != null) request["lease_token"] = leaseToken
        return LinkJob.from(call("jobs.submit", request))
    }

    fun job(id: String): LinkJob = LinkJob.from(call("jobs.status", mapOf("job_id" to id)))

    fun cancel(id: String): LinkJob = LinkJob.from(call("jobs.cancel", mapOf("job_id" to id)))

    /** Espera el final de un trabajo. Un corte de red durante la espera no lo da por perdido: se sigue preguntando. */
    fun await(job: LinkJob, timeoutMs: Long = 60_000): LinkJob {
        var latest = job
        val deadline = now() + timeoutMs
        var pause = 250L
        while (!latest.terminal) {
            if (now() >= deadline) throw LinkTimeout(latest)
            sleep(pause)
            pause = (pause * 3 / 2).coerceAtMost(1_000)
            latest = try { job(latest.id) } catch (offline: LinkOffline) { latest }
        }
        return latest
    }

    /** Ejecuta una operación en un dispositivo y devuelve su resultado, o lanza si no terminó bien. */
    fun execute(
        device: String,
        operation: String,
        args: Map<String, Any?> = emptyMap(),
        timeoutMs: Long = 60_000,
        key: String = UUID.randomUUID().toString(),
    ): Map<String, Any?> {
        val done = await(submit(device, operation, args, key), timeoutMs)
        if (!done.succeeded) throw LinkJobFailed(done)
        return done.result
    }

    /**
     * Mantiene la conexión vigilada mientras alguien la esté usando: latido, reconexión con espera
     * creciente y descubrimiento de la dirección pública. Cerrar el resultado deja de vigilar.
     */
    fun hold(): AutoCloseable {
        synchronized(lock) {
            holders += 1
            if (monitor == null) {
                monitor = Thread({ watch() }, "axel-link").apply { isDaemon = true; start() }
            } else {
                kicked = true
                lock.notifyAll()
            }
        }
        var open = true
        return AutoCloseable {
            synchronized(lock) {
                if (open) {
                    open = false
                    holders -= 1
                    lock.notifyAll()
                }
            }
        }
    }

    /** Reintenta ya, sin esperar al siguiente latido: la red ha vuelto o el usuario lo pide. */
    fun kick() {
        synchronized(lock) {
            kicked = true
            lock.notifyAll()
        }
    }

    private fun watch() {
        while (true) {
            synchronized(lock) {
                if (holders <= 0) {
                    monitor = null
                    return
                }
            }
            var pause = HEARTBEAT_MS
            try {
                if (config == null || current is LinkState.Unauthorized) reload()
                if (config == null || current is LinkState.Unauthorized) {
                    pause = IDLE_MS
                } else {
                    refresh()
                    discover()
                }
            } catch (offline: LinkOffline) {
                pause = backoff(synchronized(lock) { failures })
            } catch (error: Exception) {
                pause = IDLE_MS
            }
            synchronized(lock) {
                if (!kicked && holders > 0) {
                    try { lock.wait(pause) } catch (interrupted: InterruptedException) { /* se reevalúa arriba */ }
                }
                kicked = false
            }
        }
    }

    /** Lee, a través del propio Commander, la dirección pública vigente y la guarda si ha cambiado. */
    private fun discover() {
        val settings = config ?: return
        val source = settings.discovery ?: return
        val due = synchronized(lock) { discoveredAt == 0L || now() - discoveredAt >= DISCOVERY_MS }
        if (!due) return
        if (device(source.device)?.can("files.read") != true) return
        synchronized(lock) { discoveredAt = now() }
        try {
            val file = execute(source.device, "files.read", mapOf("path" to source.path), 15_000)
            val url = LinkConfig.normalize(LinkJson.obj(file.text("text")).text("url"))
            if (!url.startsWith("https://")) return
            LinkConfig.checkEndpoint(url)
            if (settings.routes.none { it.id == source.route && it.url != url }) return
            val updated = settings.withRoute(source.route, url)
            val text = updated.toJson()
            store.write(text)
            config = updated
            loaded = text
        } catch (error: Exception) {
            // El descubrimiento es una mejora: si falla, las rutas conocidas siguen valiendo.
        }
    }

    private fun order(settings: LinkConfig): List<Int> {
        val start = synchronized(lock) {
            if (active >= settings.routes.size) active = 0
            // Cada cierto tiempo se vuelve a probar la ruta preferida, por si ya responde.
            if (active != 0 && now() - preferredTriedAt >= PREFERRED_RETRY_MS) {
                preferredTriedAt = now()
                0
            } else active
        }
        return settings.routes.indices.map { (start + it) % settings.routes.size }
    }

    private fun reached(index: Int, route: LinkRoute, latencyMs: Long) {
        val changed = synchronized(lock) {
            val moved = active != index
            if (moved) discoveredAt = 0L
            if (moved && index != 0) preferredTriedAt = now()
            active = index
            failures = 0
            moved
        }
        val previous = current
        val since = if (previous is LinkState.Online && !changed) previous.since else now()
        publish(LinkState.Online(route.id, latencyMs, since))
    }

    private fun publish(next: LinkState) {
        val previous = current
        current = next
        // El latido no molesta a la interfaz: solo se avisa si cambia algo más que la latencia.
        val same = previous is LinkState.Online && next is LinkState.Online && previous.route == next.route
        if (previous != next && !same) listeners.forEach { listener -> try { listener(next) } catch (error: Exception) { } }
    }

    companion object {
        const val HEARTBEAT_MS = 25_000L
        private const val IDLE_MS = 15_000L
        private const val DISCOVERY_MS = 30 * 60_000L
        private const val PREFERRED_RETRY_MS = 5 * 60_000L
        private val GATEWAY_ERRORS = setOf(502, 503, 504, 520, 521, 522, 523, 524, 525, 526, 530)
        private val CREDENTIAL_ERRORS = setOf(
            "Unauthorized or revoked credential", "Credential revoked", "Bearer credential required", "Client identity required",
        )

        /** 1 s, 2 s, 4 s… hasta 30 s. */
        fun backoff(attempt: Int): Long = (1_000L shl (attempt - 1).coerceIn(0, 5)).coerceAtMost(30_000L)
    }
}
