package com.vidadomu.nexus.sync

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.AtomicFile
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.sync.*
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.agent.core.assets.IndexData
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

data class SyncOptions(
    val enabled: Boolean = false, val peer: String = "", val port: Int = 8795,
    val intervalSeconds: Long = 30, val timeoutSeconds: Long = 120,
    val maxFileMb: Long = 0, val chats: Boolean = true, val memory: Boolean = true,
    val skills: Boolean = true, val gallery: Boolean = true, val attachments: Boolean = true,
    val deletions: Boolean = false, val afterBoot: Boolean = true,
    val credentials: Boolean = true, val agy: Boolean = true, val settings: Boolean = true,
    val agyEndpoint: String = "http://127.0.0.1:8796",
    val desktop: Boolean = true,
)

/** One paired peer. Data categories are explicit; device routes and pairing material remain local. */
class DeviceSync private constructor(private val app: NexusApplication) {
    private val prefs = app.getSharedPreferences("device_sync_v1", Context.MODE_PRIVATE)
    private val root = File(app.filesDir, "device-sync").apply { mkdirs() }
    private val blobs = File(root, "blobs").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val sources = java.util.concurrent.ConcurrentHashMap<String, File>()
    private val secretSources = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    @Volatile private var agyAvailable = false
    private val fingerprints = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()
    private val verifiedFiles = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val skillReplicas = File(root, "skill-replicas.json").takeIf { it.isFile }?.let {
        json.decodeFromString<Map<String,String>>(it.readText()).mapKeys { (key, _) ->
            if (key.startsWith("private/../") && "/files/skills/" in key) "private/" + key.substringAfterLast("/files/skills/") else key
        }.toMutableMap()
    } ?: mutableMapOf()
    private val mutableStatus = MutableStateFlow(prefs.getString("status", "Sin vincular").orEmpty())
    val status = mutableStatus.asStateFlow()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    private val sockets = java.util.concurrent.ConcurrentHashMap.newKeySet<Socket>()
    @Volatile private var manifest: SyncManifest? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile private var wake: CompletableDeferred<Unit>? = null
    private val pendingPeers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val deviceId: String = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).commit() }
    fun options() = SyncOptions(
        prefs.getBoolean("enabled", false), prefs.getString("peer", "").orEmpty(), prefs.getInt("port", 8795),
        prefs.getLong("interval", 30), prefs.getLong("timeout", 120), prefs.getLong("max_file_mb", 0),
        prefs.getBoolean("chats", true), prefs.getBoolean("memory", true), prefs.getBoolean("skills", true),
        prefs.getBoolean("gallery", true), prefs.getBoolean("attachments", true), prefs.getBoolean("deletions", false), prefs.getBoolean("after_boot", true),
        prefs.getBoolean("credentials", true), prefs.getBoolean("agy", true), prefs.getBoolean("settings", true), prefs.getString("agy_endpoint", "http://127.0.0.1:8796").orEmpty(), prefs.getBoolean("desktop", true),
    )
    fun pairingKey(): String = app.secureStorage.getProviderApiKey(KEY)
    fun configure(o: SyncOptions, key: String = pairingKey()) {
        require(o.port in 1024..65535 && o.intervalSeconds > 0 && o.timeoutSeconds >= 0 && o.maxFileMb >= 0) { "Puerto o intervalo no válido." }
        require(o.peer.isBlank() || Regex("https?://[^/]+/?").matches(o.peer)) { "Usa http://dirección:puerto sin ruta." }
        require(Regex("http://(127\\.0\\.0\\.1|localhost):[0-9]+/?").matches(o.agyEndpoint)) { "El adaptador AGY debe usar localhost." }
        require(!o.enabled || (SyncCrypto.validKey(key) && o.peer.isNotBlank())) { "Faltan la dirección del otro móvil y la clave de vinculación." }
        require(key.isBlank() || SyncCrypto.validKey(key)) { "Clave de vinculación no válida." }
        val previousKey = pairingKey()
        val previousEndpoint = options().agyEndpoint
        if (key != previousKey) {
            app.secureStorage.saveProviderApiKey(KEY, key)
        }
        prefs.edit().putBoolean("enabled", o.enabled).putString("peer", o.peer.trimEnd('/')).putInt("port", o.port)
            .putLong("interval", o.intervalSeconds).putLong("timeout", o.timeoutSeconds).putLong("max_file_mb", o.maxFileMb)
            .putBoolean("chats", o.chats).putBoolean("memory", o.memory).putBoolean("skills", o.skills)
            .putBoolean("gallery", o.gallery).putBoolean("attachments", o.attachments).putBoolean("deletions", o.deletions)
            .putBoolean("after_boot", o.afterBoot).commit()
        prefs.edit().putBoolean("credentials",o.credentials).putBoolean("agy",o.agy).putBoolean("settings",o.settings).putString("agy_endpoint",o.agyEndpoint).putBoolean("desktop",o.desktop).commit()
        // Applied off the main thread; a missing Termux adapter never blocks the settings UI.
        CoroutineScope(Dispatchers.IO).launch { runCatching {
            if (key != previousKey && SyncCrypto.validKey(previousKey)) {
                fetch("/pair", previousEndpoint, buildJsonObject {put("key",key)}.toString().toByteArray(), previousKey) { it.readBytes() }
                root.walkTopDown().filter { it.isFile && it.extension == "enc" }.forEach { f ->
                    val old = runCatching { SyncCrypto.decrypt(f.inputStream(), previousKey, "nexus-private-state-v1").use { json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject } }.getOrNull()
                    if (old != null) saveEncryptedState(f, old)
                }
            }
            fetch("/options", o.agyEndpoint, buildJsonObject {put("enabled",o.agy && o.enabled);put("afterBoot",o.afterBoot)}.toString().toByteArray()) { it.readBytes() }
        } }
        // Rebind only when the service restarts; a disabled category cannot keep serving an old snapshot.
        manifest = null
        if (o.enabled) DeviceSyncService.start(app) else { DeviceSyncService.stop(app); report("Sincronización desactivada") }
    }
    fun syncSoon() { wake?.complete(Unit) }
    fun report(value: String) { mutableStatus.value = value; prefs.edit().putString("status", value).apply() }

    suspend fun run(scope: CoroutineScope) = coroutineScope {
        val o = options()
        if (!o.enabled) return@coroutineScope
        val listener = ServerSocket().apply { reuseAddress = true; bind(java.net.InetSocketAddress(o.port)) }
        server = listener
        val listenerJob = scope.launch(Dispatchers.IO) {
            while (isActive && !listener.isClosed) {
                try { listener.accept().use { socket -> sockets += socket; try { serve(socket) } finally { sockets -= socket } } }
                catch (_: Exception) { if (listener.isClosed) break }
            }
        }
        try {
            while (currentCoroutineContext().isActive && options().enabled) {
                wake = CompletableDeferred()
                for (peer in (listOf(options().peer) + pendingPeers.toList()).distinct()) {
                    if (peer != options().peer && !options().desktop) { pendingPeers.remove(peer); continue }
                    try { cycle(peer); pendingPeers.remove(peer) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { report("Pendiente de conexión · ${e.javaClass.simpleName}. Se reintentará automáticamente.") }
                }
                withTimeoutOrNull(options().intervalSeconds.coerceAtMost(Long.MAX_VALUE / 1000) * 1000) { wake?.await() }
            }
        } finally { listener.close(); listenerJob.cancel(); server = null }
    }
    fun close() { server?.close(); sockets.forEach { runCatching { it.close() } }; syncSoon() }

    private fun serve(socket: Socket) {
        socket.soTimeout = timeoutMillis()
        val input = socket.getInputStream()
        fun line(): String { val s = StringBuilder(); while (true) { val c = input.read(); require(c >= 0); if (c == 10) return s.toString().trimEnd('\r'); require(s.length < 8192); s.append(c.toChar()) } }
        val request = line().split(' ')
        require(request.size == 3 && request[0] in setOf("GET", "POST"))
        val path = request[1]
        var token = ""
        var length = 0
        while (true) { val h = line(); if (h.isEmpty()) break; if (h.startsWith("X-Nexus-Sync:", true)) token = h.substringAfter(':').trim(); if(h.startsWith("Content-Length:",true)) length=h.substringAfter(':').trim().toInt() }
        require(options().enabled && SyncCrypto.authorized(token, path, pairingKey()))
        if (request[0] == "POST") {
            require(path == "/sync" && options().desktop && length in 1..65536)
            val packet=ByteArray(length); java.io.DataInputStream(input).readFully(packet)
            val value=SyncCrypto.decrypt(packet.inputStream(),pairingKey(),token).use {json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject}
            require(value.keys == setOf("endpoint"))
            val endpoint=value.getValue("endpoint").jsonPrimitive.content.trimEnd('/')
            val address=java.net.URI(endpoint)
            require(address.scheme in setOf("http","https") && address.userInfo==null && address.rawQuery==null && address.rawFragment==null && address.path.orEmpty().isEmpty())
            // A paired caller can request a return connection only to its own network address.
            require(java.net.InetAddress.getByName(address.host)==socket.inetAddress)
            pendingPeers += endpoint; syncSoon()
            val output=socket.getOutputStream()
            output.write("HTTP/1.1 200 OK\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
            SyncCrypto.encrypt(output,pairingKey(),token).use {it.write("{\"queued\":true}".toByteArray())}
            return
        }
        val current = manifest ?: error("Snapshot pending")
        val source: InputStream = when {
            path == "/manifest" -> json.encodeToString(current).byteInputStream()
            path.matches(Regex("/blob/[a-f0-9]{64}")) -> {
                val hash = path.substringAfterLast('/')
                // Only hashes referenced by the current, scoped snapshot may be requested.
                val allowed = current.documents.any { it.hash == hash } || publishedMedia.contains(hash)
                require(allowed)
                secretSources[hash]?.inputStream() ?: (sources[hash] ?: File(blobs, hash)).inputStream()
            }
            else -> error("Unknown route")
        }
        val out = socket.getOutputStream()
        out.write("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
        SyncCrypto.encrypt(out, pairingKey(), token).use { encrypted -> source.use { it.copyTo(encrypted) } }
    }

    @Volatile private var publishedMedia: Set<String> = emptySet()
    private fun timeoutMillis(): Int = (options().timeoutSeconds.coerceAtMost(Int.MAX_VALUE / 1000L) * 1000).toInt()
    private fun fetch(path: String, endpoint: String = options().peer, body: ByteArray? = null, secret: String = pairingKey(), consume: (InputStream) -> Unit) {
        val peer = java.net.URI(endpoint)
        require(peer.scheme in setOf("http", "https") && peer.userInfo == null && peer.host != null)
        val token = SyncCrypto.request(path, secret)
        // This is an encrypted application protocol over a socket, not a plaintext provider API.
        // HTTP framing allows a private TLS reverse proxy too; Android's provider allowlist stays intact.
        val socket = if (peer.scheme == "https") javax.net.ssl.SSLSocketFactory.getDefault().createSocket() else Socket()
        socket.use {
            sockets += it
            try {
            it.soTimeout = timeoutMillis()
            it.connect(java.net.InetSocketAddress(peer.host, if (peer.port >= 0) peer.port else if (peer.scheme == "https") 443 else 80), timeoutMillis())
            if (it is javax.net.ssl.SSLSocket) {
                it.sslParameters = it.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }; it.startHandshake()
            }
            val encryptedBody = body?.let { bytes -> java.io.ByteArrayOutputStream().let { out -> SyncCrypto.encrypt(out, secret, token).use { enc -> enc.write(bytes) }; out.toByteArray() } }
            val method = if (body == null) "GET" else "POST"
            val lengthHeader = encryptedBody?.let { bytes -> "Content-Length: ${bytes.size}\r\n" }.orEmpty()
            it.getOutputStream().write("$method $path HTTP/1.1\r\nHost: ${peer.host}\r\nX-Nexus-Sync: $token\r\n${lengthHeader}Connection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
            encryptedBody?.let { bytes -> it.getOutputStream().write(bytes) }
            val input = it.getInputStream().buffered()
            fun line(): String {
                val value = StringBuilder()
                while (true) { val c = input.read(); require(c >= 0); if (c == 10) return value.toString().trimEnd('\r'); require(value.length < 8192); value.append(c.toChar()) }
            }
            check(line().startsWith("HTTP/1.1 200 "))
            while (line().isNotEmpty()) { /* no response bodies are logged */ }
            SyncCrypto.decrypt(input, secret, token).use(consume)
            } finally { sockets -= it }
        }
    }
    private fun atomic(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val target = AtomicFile(file); val out = target.startWrite()
        try { out.write(bytes); target.finishWrite(out) } catch (e: Exception) { target.failWrite(out); throw e }
    }
    private fun copyAtomic(file: File, source: File) {
        file.parentFile?.mkdirs()
        val target = AtomicFile(file); val out = target.startWrite()
        try { source.inputStream().use { it.copyTo(out) }; target.finishWrite(out) }
        catch (e: Exception) { target.failWrite(out); throw e }
    }
    private fun verified(file: File, hash: String): Boolean {
        val fingerprint = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        if (verifiedFiles[fingerprint] == hash) return true
        return (SyncCrypto.hash(file.inputStream()) == hash).also { if (it) verifiedFiles[fingerprint] = hash }
    }
    private fun document(kind: String, name: String, bytes: ByteArray): SyncDocument {
        val hash = SyncCrypto.hash(bytes)
        val f = File(blobs, hash)
        if (!f.exists()) atomic(f, bytes)
        sources[hash] = f
        return SyncDocument(kind, name, hash, bytes.size.toLong())
    }
    private fun authSnapshot(): JsonObject {
        val o = options()
        var agy: JsonElement = JsonNull
        if (o.agy) {
            agyAvailable = false
            runCatching { fetch("/auth", o.agyEndpoint) { agy = json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)); agyAvailable = true } }
        }
        return buildJsonObject {
            put("keys", if (o.credentials) JsonObject(app.secureStorage.syncCredentials().mapValues { JsonPrimitive(it.value) }) else JsonObject(emptyMap()))
            put("agy", agy)
            put("settings", if (o.settings) connectionSettings() else JsonObject(emptyMap()))
        }
    }
    private fun connectionSettings(): JsonObject = buildJsonObject {
        // Local routes, current project/session and device-specific synchronization settings stay local.
        for (name in listOf("assistant_config_v1", "brain_profiles_v1", "media_providers_v1", "agent_mcp_servers_v1")) {
            val values = app.getSharedPreferences(name, Context.MODE_PRIVATE).all.filterKeys {
                it !in setOf("antigravity_endpoint", "active_project_id", "active_project") && !it.startsWith("local_")
            }
            put(name, JsonObject(values.mapNotNull { (k, v) ->
                val element: JsonElement = when(v) {
                    is String -> runCatching { json.parseToJsonElement(v) }.getOrElse { JsonPrimitive(v) }
                    is Boolean -> JsonPrimitive(v)
                    is Number -> JsonPrimitive(v)
                    else -> return@mapNotNull null
                }
                val type = when(v) { is String -> "string"; is Boolean -> "boolean"; is Long -> "long"; is Float -> "float"; else -> "int" }
                k to buildJsonObject {put("type",type);put("value",element)}
            }.toMap()))
        }
    }
    private fun applyConnectionSettings(value: JsonObject) {
        for ((name, fields) in value) {
            require(name in setOf("assistant_config_v1", "brain_profiles_v1", "media_providers_v1", "agent_mcp_servers_v1"))
            val current = app.getSharedPreferences(name, Context.MODE_PRIVATE)
            val edit = current.edit()
            if (options().deletions) (connectionSettings()[name]?.jsonObject?.keys.orEmpty() - fields.jsonObject.keys).forEach { edit.remove(it) }
            for ((k, field) in fields.jsonObject) {
                if (k in setOf("antigravity_endpoint", "active_project_id", "active_project") || k.startsWith("local_")) continue
                val typed = field.jsonObject; val v = typed.getValue("value")
                when (typed.getValue("type").jsonPrimitive.content) {
                    "string" -> edit.putString(k, if (v is JsonPrimitive && v.isString) v.content else v.toString())
                    "boolean" -> edit.putBoolean(k, v.jsonPrimitive.boolean)
                    "int" -> edit.putInt(k, v.jsonPrimitive.int)
                    "long" -> edit.putLong(k, v.jsonPrimitive.long)
                    "float" -> edit.putFloat(k, v.jsonPrimitive.float)
                }
            }
            check(edit.commit())
        }
    }
    private fun encryptedState(file: File): JsonObject? = runCatching {
        SyncCrypto.decrypt(file.inputStream(), pairingKey(), "nexus-private-state-v1").use { json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject }
    }.getOrNull()
    private fun saveEncryptedState(file: File, value: JsonObject) {
        val bytes = java.io.ByteArrayOutputStream().let { out -> SyncCrypto.encrypt(out, pairingKey(), "nexus-private-state-v1").use { it.write(value.toString().toByteArray()) }; out.toByteArray() }
        atomic(file, bytes)
    }
    private fun media(uri: String, media: MutableSet<String>, missing: MutableSet<String>): String {
        if (!options().attachments || !(uri.startsWith("file:") || uri.startsWith("content:"))) return uri
        return runCatching {
            val parsed = Uri.parse(uri)
            val file = if (parsed.scheme == "file") File(requireNotNull(parsed.path)) else null
            val fingerprint = file?.let { "${it.length()}:${it.lastModified()}" }
            val cached = fingerprints[uri]?.takeIf { it.first == fingerprint }
            val hash = cached?.second ?: if (file != null) SyncCrypto.hash(file.inputStream()) else {
                // SAF handles may not be portable; snapshot the granted bytes while access exists.
                val tmp = File.createTempFile("media-", ".part", root)
                try {
                    app.contentResolver.openInputStream(parsed)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    val digest = SyncCrypto.hash(tmp.inputStream())
                    val dest = File(blobs, digest)
                    if (!dest.exists()) check(tmp.renameTo(dest)) else tmp.delete()
                    sources[digest] = dest
                    digest
                } finally { tmp.delete() }
            }
            if (file != null) { sources[hash] = file; fingerprints[uri] = (fingerprint!! to hash) }
            val source = sources[hash] ?: File(blobs, hash)
            if (options().maxFileMb > 0 && source.length() / 1048576.0 > options().maxFileMb) { missing += uri; return uri }
            media += hash
            "nexus-sync-blob:$hash"
        }.getOrElse { missing += uri; uri }
    }
    private fun transform(value: JsonElement, change: (String) -> String): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.mapValues { transform(it.value, change) })
        is JsonArray -> JsonArray(value.map { transform(it, change) })
        is JsonPrimitive -> if (value.isString) JsonPrimitive(change(value.content)) else value
    }
    private fun snapshot(): Pair<SyncManifest, Int> {
        val o = options(); val docs = mutableListOf<SyncDocument>(); val media = mutableSetOf<String>(); val missing = mutableSetOf<String>()
        fun addJson(kind: String, name: String, raw: String) {
            val portable = transform(json.parseToJsonElement(raw)) { media(it, media, missing) }
            docs += document(kind, name, portable.toString().toByteArray())
        }
        if (o.chats) {
            val folder = File(app.filesDir, "assistant/conversations")
            folder.listFiles().orEmpty().filter { it.extension == "json" && !it.name.startsWith("sync_") }.forEach { addJson("chat", it.name, it.readText()) }
            File(app.filesDir, "assistant/chat-v1.json").takeIf { it.isFile }?.let {
                val raw = AtomicFile(it).openRead().bufferedReader().use { r -> r.readText() }
                val first = json.parseToJsonElement(raw).jsonObject["items"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
                if (first != null) addJson("chat", first.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json", raw)
            }
        }
        if (o.memory || o.gallery) {
            val data = AssistantRuntime.get(app).assets.snapshot()
            val scoped = data.copy(assets = if (o.gallery) data.assets else emptyList(), generations = if (o.gallery) data.generations else emptyList(),
                projects = if (o.memory) data.projects else emptyList(), scenes = if (o.memory) data.scenes else emptyList(), shots = if (o.memory) data.shots else emptyList(),
                characters = if (o.memory) data.characters else emptyList(), projectSessions = emptyList())
            addJson("index", "index.json", json.encodeToString(scoped))
        }
        if (o.gallery && !app.database.loading.value && app.database.loadError.value == null) {
            // Running jobs remain on their submitting device: importing never resumes a paid task.
            app.database.jobsFlow.value.filter { it.status == GenerationStatus.COMPLETED }.forEach { job ->
                addJson("job", job.id + ".json", json.encodeToString(job))
            }
        }
        if (o.skills) {
            listOf("private" to File(app.filesDir, "skills"), "shared" to File(Environment.getExternalStorageDirectory(), "Documents/ALEXIA/Nexus/Skills")).forEach { (label, folder) ->
                if (folder.isDirectory) folder.walkTopDown().filter { it.isFile && it.canonicalFile.toPath().startsWith(folder.canonicalFile.toPath()) }.forEach {
                    // Include support files referenced by SKILL.md, not just its Markdown entrypoint.
                    if (o.maxFileMb == 0L || it.length() / 1048576.0 <= o.maxFileMb) {
                        val hash = SyncCrypto.hash(it.inputStream()); sources[hash] = it
                        val name = "$label/${it.relativeTo(folder).invariantSeparatorsPath}"
                        val original = skillReplicas[name]
                        // Equal base/hash marks an intact replica. Keep its presence in the
                        // manifest so enabling deletions cannot mistake ownership for removal.
                        docs += SyncDocument("skill", name, hash, it.length(), baseHash = original)
                    } else missing += it.path
                }
            }
        }
        if (o.credentials || o.agy || o.settings) {
            val bytes = authSnapshot().toString().toByteArray()
            val hash = SyncCrypto.hash(bytes)
            secretSources.clear(); secretSources[hash] = bytes
            docs += SyncDocument("credentials", "credentials.json", hash, bytes.size.toLong())
        }
        publishedMedia = media
        val scopes = buildSet {
            if (o.chats) add("chat"); if (o.memory) add("memory"); if (o.gallery) add("gallery"); if (o.skills) add("skill")
            if (o.credentials) add("credentials"); if (o.settings) add("settings"); if (o.agy) add("agy")
        }
        return SyncManifest(device = deviceId, documents = docs.associateBy { it.kind to it.name }.values.toList(), scopes = scopes) to missing.size
    }

    private suspend fun cycle(endpoint: String = options().peer) = mutex.withLock {
        report("Preparando cambios…")
        val (own, missing) = snapshot()
        manifest = own
        run {
            var remote: SyncManifest? = null
            fetch("/manifest", endpoint) { remote = json.decodeFromString<SyncManifest>(it.readBytes().toString(Charsets.UTF_8)) }
            val incoming = requireNotNull(remote)
            require(incoming.protocol == 1 && incoming.device.matches(Regex("[a-f0-9-]{36}")) && incoming.device != deviceId)
            val o = options()
            val selected = incoming.documents.filter { when (it.kind) { "chat" -> o.chats; "index" -> o.memory || o.gallery; "job" -> o.gallery; "skill" -> o.skills; "credentials" -> o.credentials || o.agy || o.settings; else -> false } }
            val needed = mutableSetOf<String>()
            fun obtain(hash: String): File {
                require(hash.matches(Regex("[a-f0-9]{64}")))
                val dest = File(blobs, hash)
                if (dest.isFile && verified(dest, hash)) return dest
                // Reuse the verified gallery migrated earlier; no second 2 GB download.
                val existing = File(app.filesDir, "gallery-transfer/s25-20261020").listFiles()?.firstOrNull { it.nameWithoutExtension == hash }
                    ?: sources[hash]?.takeIf { it.isFile }
                if (existing != null && verified(existing, hash)) return existing
                val tmp = File.createTempFile("download-", ".part", root)
                try {
                    fetch("/blob/$hash", endpoint) { input -> tmp.outputStream().use { out ->
                        val buffer = ByteArray(65536); var count = 0L
                        while (true) { val n = input.read(buffer); if (n < 0) break; count += n
                            check(o.maxFileMb == 0L || count / 1048576.0 <= o.maxFileMb) { "Archivo supera el límite elegido" }; out.write(buffer, 0, n) }
                    } }
                    check(verified(tmp, hash)) { "Integridad de archivo" }
                    if (dest.exists()) check(dest.delete()) // Only a corrupt, content-addressed cache file.
                    check(tmp.renameTo(dest))
                    return dest
                } finally { tmp.delete() }
            }
            var applied = 0; var conflicts = 0; var skillsChanged = false
            for (doc in selected) {
                currentCoroutineContext().ensureActive()
                if (o.maxFileMb > 0 && doc.bytes / 1048576.0 > o.maxFileMb) continue
                val stateKey = SyncCrypto.hash((doc.kind + ":" + doc.name).toByteArray())
                val policy = "merge2:${o.chats}:${o.memory}:${o.gallery}:${o.attachments}:${o.deletions}:${o.credentials}:${o.agy}:${o.settings}:${incoming.scopes.sorted()}"
                val sensitive = doc.kind == "credentials"
                val state = File(root, "peers/${incoming.device}/$stateKey.${if (sensitive) "enc" else "json"}")
                val oldState = state.takeIf { it.isFile }?.let { if (sensitive) encryptedState(it) else json.parseToJsonElement(it.readText()).jsonObject }
                if (oldState?.get("hash")?.jsonPrimitive?.content == doc.hash && oldState["policy"]?.jsonPrimitive?.content == policy) continue
                if (doc.kind == "skill" && doc.baseHash == doc.hash) {
                    val label = doc.name.substringBefore('/'); require(label in setOf("private","shared"))
                    val dest = SyncCrypto.safeFile(File(app.filesDir,"skills"),(if(label=="shared") "synced-shared/" else "") + doc.name.substringAfter('/'))
                    // A replica cannot overwrite its author's edit or undo a known local removal.
                    // An empty/new phone may still recover its first copy from a replica.
                    if (dest.exists() || oldState != null) {
                        val localHash = dest.takeIf { it.isFile }?.let { SyncCrypto.hash(it.inputStream()) }.orEmpty()
                        atomic(state, buildJsonObject {put("hash",doc.hash);put("policy",policy);put("localHash",localHash)}.toString().toByteArray())
                        continue
                    }
                }
                report("Recibiendo ${doc.kind} · ${++applied}/${selected.size}")
                if (sensitive) {
                    var incomingAuth = ByteArray(0)
                    fetch("/blob/${doc.hash}", endpoint) { incomingAuth = it.readBytes() }
                    check(SyncCrypto.hash(incomingAuth) == doc.hash)
                    val received = json.parseToJsonElement(incomingAuth.toString(Charsets.UTF_8)).jsonObject
                    val local = authSnapshot()
                    val base = oldState?.get("base") as? JsonObject
                    val scoped = buildJsonObject {
                        put("keys", if (o.credentials && "credentials" in incoming.scopes) received["keys"] ?: JsonObject(emptyMap()) else local.getValue("keys"))
                        put("settings", if (o.settings && "settings" in incoming.scopes) received["settings"] ?: JsonObject(emptyMap()) else local.getValue("settings"))
                    }
                    val localFields = JsonObject(local.filterKeys { it != "agy" })
                    val ownBase = (oldState?.get("localBase") as? JsonObject)?.let { JsonObject(it.filterKeys { key -> key != "agy" }) }
                    val merged = SyncMerge.merge(localFields, scoped, base?.let { JsonObject(it.filterKeys { key -> key != "agy" }) }, o.deletions,
                        ownBase ?: base?.let { JsonObject(it.filterKeys { key -> key != "agy" }) })
                    val fields = merged.value.jsonObject
                    if (o.credentials) app.secureStorage.applySyncedCredentials(fields.getValue("keys").jsonObject.mapValues { it.value.jsonPrimitive.content }, o.deletions)
                    if (o.settings && fields["settings"] != local["settings"]) {
                        applyConnectionSettings(fields.getValue("settings").jsonObject)
                        val runtime = AssistantRuntime.get(app)
                        runtime.settings.limitsState.value = runtime.settings.limits()
                        com.vidadomu.nexus.core.model.TrackingPolicy.windowMinutes = runtime.settings.limits().jobTrackingMinutes
                        runtime.reloadSkills()
                        app.customMediaStore.reload()
                    }
                    val receivedAgy = received["agy"] as? JsonObject
                    val localAgy = local["agy"] as? JsonObject
                    val priorAgy = base?.get("agy") as? JsonObject
                    var agyApplied = true
                    var agyConflict = false
                    if (o.agy && "agy" in incoming.scopes && receivedAgy != null && receivedAgy != localAgy && receivedAgy != priorAgy) {
                        if (choosePeerAuth(localAgy,receivedAgy,priorAgy) == PeerAuthChoice.PEER) {
                            val update = buildJsonObject {put("files",receivedAgy);put("removeMissing",o.deletions)}
                            agyApplied = runCatching { fetch("/auth", o.agyEndpoint, update.toString().toByteArray()) { it.readBytes() } }.isSuccess
                        } else agyConflict = true
                    }
                    if (merged.conflicts.isNotEmpty() || agyConflict) saveEncryptedState(File(root,"conflicts/${incoming.device}/$stateKey-${doc.hash}.enc"), received)
                    check(agyApplied) { "Adaptador AGY pendiente" }
                    saveEncryptedState(state, buildJsonObject { put("hash",doc.hash);put("policy",policy);put("base", received);put("localBase",merged.value) })
                    continue
                }
                val source = obtain(doc.hash)
                val bytes = if (doc.kind == "skill") ByteArray(0) else {
                    val raw = json.parseToJsonElement(source.readText())
                    needed.clear()
                    fun resolve(uri: String): String {
                        if (!uri.startsWith("nexus-sync-blob:")) return uri
                        val hash = uri.substringAfter(':')
                        if (!o.attachments) return uri
                        needed += hash
                        return Uri.fromFile(obtain(hash)).toString()
                    }
                    transform(raw, ::resolve).toString().toByteArray()
                }
                val base = oldState?.get("base")
                var committedBase: JsonElement = JsonPrimitive("")
                var ownCommittedBase: JsonElement = JsonPrimitive("")
                when (doc.kind) {
                    "chat" -> {
                        require(doc.name.matches(Regex("[A-Za-z0-9_-]+\\.json")))
                        val dest = File(app.filesDir, "assistant/conversations/sync_${incoming.device}_${doc.name}")
                        atomic(dest, bytes) // Peer replicas never overwrite the locally active conversation.
                    }
                    "index" -> {
                        val remoteData = json.decodeFromString<IndexData>(bytes.toString(Charsets.UTF_8))
                        val runtime = AssistantRuntime.get(app)
                        val useMemory = o.memory && "memory" in incoming.scopes
                        val useGallery = o.gallery && "gallery" in incoming.scopes
                        val incomingData = remoteData.copy(
                            projects = if (useMemory) remoteData.projects else emptyList(), scenes = if (useMemory) remoteData.scenes else emptyList(),
                            shots = if (useMemory) remoteData.shots else emptyList(), characters = if (useMemory) remoteData.characters else emptyList(),
                            assets = if (useGallery) remoteData.assets else emptyList(), generations = if (useGallery) remoteData.generations else emptyList(), projectSessions = emptyList())
                        val oldRemote = base?.takeUnless { it is JsonPrimitive }?.let { json.decodeFromJsonElement<IndexData>(it) }
                        val oldLocal = oldState?.get("localBase")?.takeUnless { it is JsonPrimitive }?.let { json.decodeFromJsonElement<IndexData>(it) }
                        val result = runtime.assets.mergeFromPeer(incomingData, oldRemote, o.deletions, useMemory, useGallery,
                            oldLocal ?: oldRemote, ::peerUriAvailable)
                        conflicts += result.conflicts.size
                        if (result.conflicts.isNotEmpty()) atomic(File(root, "conflicts/${incoming.device}/$stateKey-${doc.hash}.json"), bytes)
                        committedBase = json.encodeToJsonElement(incomingData)
                        ownCommittedBase = json.encodeToJsonElement(result.snapshot)
                    }
                    "job" -> {
                        val job = json.decodeFromString<GenerationJob>(bytes.toString(Charsets.UTF_8))
                        app.database.loading.first { !it }
                        app.database.mergeCompletedJobFromPeer(job)
                    }
                    "skill" -> {
                        val label = doc.name.substringBefore('/'); require(label in setOf("private", "shared"))
                        // Shared skills land in app-private storage too; no blanket external storage permission is needed.
                        val dest = SyncCrypto.safeFile(File(app.filesDir, "skills"), (if (label == "shared") "synced-shared/" else "") + doc.name.substringAfter('/'))
                        val localHash = dest.takeIf { it.isFile }?.let { SyncCrypto.hash(it.inputStream()) }
                        val priorHash = oldState?.get("localHash")?.jsonPrimitive?.contentOrNull
                        if (localHash != null && localHash != doc.hash && localHash != priorHash && localHash != doc.baseHash) {
                            copyAtomic(File(root, "conflicts/${incoming.device}/$stateKey-${doc.hash}.skill"), source); conflicts++
                        } else {
                            copyAtomic(dest, source); skillsChanged = true
                            skillReplicas["private/" + SyncCrypto.relativePath(File(app.filesDir,"skills"),dest)] = doc.hash
                        }
                        committedBase = JsonPrimitive(dest.takeIf { it.isFile }?.let { SyncCrypto.hash(it.inputStream()) }.orEmpty())
                    }
                }
                atomic(state, buildJsonObject { put("hash", doc.hash); put("base", committedBase);put("localBase",ownCommittedBase); put("policy", policy); if (doc.kind == "skill") put("localHash", committedBase) }.toString().toByteArray())
            }
            val seenFile = File(root, "peers/${incoming.device}/documents.json")
            val seen = seenFile.takeIf { it.isFile }?.let { json.decodeFromString<List<SyncDocument>>(it.readText()) }.orEmpty().associateBy { it.kind to it.name }.toMutableMap()
            val present = incoming.documents.map { it.kind to it.name }.toSet()
            if (o.deletions) for ((key, doc) in seen.toMap()) {
                if (key in present || doc.kind !in incoming.scopes) continue
                val removed = when {
                    doc.kind == "chat" && o.chats -> {
                        require(doc.name.matches(Regex("[A-Za-z0-9_-]+\\.json")))
                        val target = File(app.filesDir,"assistant/conversations/sync_${incoming.device}_${doc.name}")
                        AtomicFile(target).delete(); true
                    }
                    doc.kind == "skill" && o.skills -> {
                        val label=doc.name.substringBefore('/'); require(label in setOf("private","shared"))
                        val target=SyncCrypto.safeFile(File(app.filesDir,"skills"),(if(label=="shared") "synced-shared/" else "")+doc.name.substringAfter('/'))
                        if (!target.exists()) true else if (SyncCrypto.hash(target.inputStream()) == doc.hash) { AtomicFile(target).delete(); skillsChanged=true; true } else false
                    }
                    else -> false
                }
                if (removed) {
                    seen.remove(key)
                    File(root,"peers/${incoming.device}/${SyncCrypto.hash((doc.kind+":"+doc.name).toByteArray())}.json").delete()
                }
            }
            selected.forEach { seen[it.kind to it.name]=it }
            atomic(seenFile,json.encodeToString(seen.values.toList()).toByteArray())
            if (skillsChanged) atomic(File(root,"skill-replicas.json"),json.encodeToString(skillReplicas).toByteArray())
            if (skillsChanged) AssistantRuntime.get(app).reloadSkills()
            if (applied > 0) mutableRevision.value++
            prefs.edit().putLong("last_success", System.currentTimeMillis()).apply()
            val totalConflicts = File(root, "conflicts").walkTopDown().count { it.isFile }
            report("Sincronizado · ${java.text.DateFormat.getTimeInstance().format(java.util.Date())}" +
                (if (missing > 0) " · $missing archivos locales inaccesibles o excluidos" else "") +
                (if (o.agy && !agyAvailable) " · adaptador AGY sin conexión" else "") +
                (if (totalConflicts > 0) " · $totalConflicts versiones en conflicto conservadas" else ""))
        }
    }
    companion object {
        private const val KEY = "nexus_device_sync_pair"
        @Volatile private var instance: DeviceSync? = null
        fun get(context: Context): DeviceSync = instance ?: synchronized(this) {
            instance ?: DeviceSync(context.applicationContext as NexusApplication).also { instance = it }
        }
    }
}
