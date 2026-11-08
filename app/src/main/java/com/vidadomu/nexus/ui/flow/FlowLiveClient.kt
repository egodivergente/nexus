package com.vidadomu.nexus.ui.flow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetRole
import com.vidadomu.nexus.agent.core.assets.AssetSource
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.assistant.AssistantRuntime
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject

/** Ajustes de la pestaña Flow (dirección del servicio de AXELPC, vista y calidad). */
internal object FlowLivePrefs {
    const val DEFAULT_URL = "http://100.72.111.97:8797"
    private fun prefs(context: Context) = context.getSharedPreferences("flow_live", Context.MODE_PRIVATE)
    fun url(context: Context): String = prefs(context).getString("url", DEFAULT_URL)?.trimEnd('/').takeUnless { it.isNullOrBlank() } ?: DEFAULT_URL
    fun setUrl(context: Context, url: String) = prefs(context).edit().putString("url", url.trim().trimEnd('/')).apply()
    fun string(context: Context, key: String, default: String) = prefs(context).getString(key, default) ?: default
    fun putString(context: Context, key: String, value: String) = prefs(context).edit().putString(key, value).apply()
    fun imported(context: Context): Set<String> = prefs(context).getStringSet("imported", emptySet()).orEmpty().toSet()
    fun markImported(context: Context, id: String) {
        val next = (imported(context) + id).toList().takeLast(500).toSet()
        prefs(context).edit().putStringSet("imported", next).apply()
    }
}

internal data class FlowPage(val url: String = "", val title: String = "", val loading: Boolean = false, val tabs: Int = 1,
                             val cssWidth: Int = 0, val cssHeight: Int = 0)

internal data class RemoteAsset(val id: String, val name: String, val mime: String, val size: Long, val sha256: String?) {
    companion object {
        fun from(o: JSONObject) = RemoteAsset(o.getString("id"), o.optString("name", "flow"), o.optString("mime", "application/octet-stream"),
            o.optLong("size", 0), o.optString("sha256").takeIf { it.isNotBlank() })
    }
}

internal data class FlowImported(val assetId: String, val uri: String, val name: String, val mime: String)

/** Conexión en directo con el servicio de AXELPC: recibe la pantalla y envía toques, texto y teclas. */
internal class FlowLiveClient(private val app: NexusApplication) {
    enum class Phase { IDLE, CONNECTING, LIVE, ERROR }

    val phase = MutableStateFlow(Phase.IDLE)
    val error = MutableStateFlow<String?>(null)
    val frame = MutableStateFlow<ImageBitmap?>(null)
    val page = MutableStateFlow(FlowPage())
    val download = MutableStateFlow<String?>(null)
    /** El último toque dejó el cursor en un campo de texto de Flow. */
    val editable = MutableStateFlow(false)
    /** Estado del último encargo: «opening», «sent» o un mensaje de error. */
    val order = MutableStateFlow<String?>(null)

    private val http = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: WebSocket? = null
    private var wanted = false
    private var attempt = 0
    private var retryJob: Job? = null
    private var lastView: JSONObject? = null
    private var generation = 0

    fun start() {
        scope.launch { runCatching { FlowMedia.repair(app) } }
        wanted = true
        if (socket == null) connect()
    }

    fun stop() {
        wanted = false
        retryJob?.cancel()
        generation++
        socket?.close(1000, null)
        socket = null
        phase.value = Phase.IDLE
    }

    fun reconnect() {
        stop(); start()
    }

    private fun connect() {
        val base = FlowLivePrefs.url(app)
        val wsUrl = base.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/live"
        phase.value = Phase.CONNECTING
        val mine = ++generation
        socket = http.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (mine != generation) return
                attempt = 0
                error.value = null
                phase.value = Phase.LIVE
                lastView?.let { webSocket.send(it.toString()) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (mine != generation) return
                runCatching { onText(JSONObject(text), base) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (mine != generation) return
                val data = bytes.toByteArray()
                val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                BitmapFactory.decodeByteArray(data, 0, data.size, options)?.let { frame.value = it.asImageBitmap() }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (mine != generation) return
                lost(if (code == 4403) "AXELPC ha rechazado este dispositivo: solo admite los de tu cuenta de Tailscale." else null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (mine != generation) return
                lost(if (response?.code == 403) "AXELPC ha rechazado este dispositivo: solo admite los de tu cuenta de Tailscale."
                     else "No llego a AXELPC. Comprueba que está encendido y que Tailscale está activo en el móvil.")
            }
        })
    }

    private fun lost(message: String?) {
        socket = null
        if (!wanted) return
        phase.value = Phase.ERROR
        error.value = message ?: "Se ha cortado la conexión con AXELPC."
        val wait = (1000L shl attempt.coerceAtMost(4)).coerceAtMost(15_000L)
        attempt++
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(wait)
            if (wanted && socket == null) connect()
        }
    }

    private fun onText(o: JSONObject, base: String) {
        when (o.optString("type")) {
            "hello" -> o.optJSONArray("assets")?.let { list ->
                for (i in 0 until list.length()) FlowLiveImporter.enqueue(app, base, RemoteAsset.from(list.getJSONObject(i)))
            }
            "state" -> {
                val size = o.optJSONArray("page")
                page.value = FlowPage(o.optString("url"), o.optString("title"), o.optBoolean("loading"), o.optInt("tabs", 1),
                    size?.optInt(0) ?: 0, size?.optInt(1) ?: 0)
            }
            "meta" -> page.value = page.value.copy(cssWidth = o.optInt("w", page.value.cssWidth), cssHeight = o.optInt("h", page.value.cssHeight))
            "download" -> download.value = o.optString("name")
            "asset" -> {
                download.value = null
                FlowLiveImporter.enqueue(app, base, RemoteAsset.from(o.getJSONObject("asset")))
            }
            "focus" -> editable.value = o.optBoolean("editable")
            "order" -> order.value = when (o.optString("state")) {
                "opening" -> "Abriendo un proyecto nuevo en Flow…"
                "sent" -> "Encargo enviado: mira cómo lo hace Flow."
                else -> o.optString("message", "No se pudo enviar el encargo.")
            }
            "error" -> error.value = o.optString("message")
        }
    }

    private fun send(o: JSONObject) {
        socket?.send(o.toString())
    }

    fun view(boxWidthDp: Float, boxHeightDp: Float, density: Float, mode: String, width: Int, quality: Int, fps: Int, maxPx: Int) {
        val o = JSONObject().put("t", "view").put("vw", boxWidthDp.toDouble()).put("vh", boxHeightDp.toDouble()).put("dpr", density.toDouble())
            .put("mode", mode).put("width", width).put("quality", quality).put("fps", fps).put("maxpx", maxPx)
        lastView = o
        send(o)
    }

    fun click(x: Float, y: Float) = send(JSONObject().put("t", "click").put("x", x.toDouble()).put("y", y.toDouble()))
    fun pointer(kind: String, x: Float, y: Float) = send(JSONObject().put("t", kind).put("x", x.toDouble()).put("y", y.toDouble()))
    fun wheel(x: Float, y: Float, dx: Float, dy: Float) =
        send(JSONObject().put("t", "wheel").put("x", x.toDouble()).put("y", y.toDouble()).put("dx", dx.toDouble()).put("dy", dy.toDouble()))
    fun text(s: String) { if (s.isNotEmpty()) send(JSONObject().put("t", "text").put("s", s)) }
    fun key(k: String) = send(JSONObject().put("t", "key").put("k", k))
    fun command(c: String) = send(JSONObject().put("t", "command").put("c", c))
    fun simple(t: String) = send(JSONObject().put("t", t))
    fun order(text: String) { if (text.isNotBlank()) { order.value = "Enviando el encargo…"; send(JSONObject().put("t", "order").put("text", text)) } }
}

internal data class FlowMediaKind(val mime: String, val type: AssetType, val ext: String)

internal object FlowMedia {
    private val byExt = mapOf("jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp", "gif" to "image/gif",
        "mp4" to "video/mp4", "mov" to "video/quicktime", "webm" to "video/webm", "m4v" to "video/mp4",
        "mp3" to "audio/mpeg", "wav" to "audio/wav", "m4a" to "audio/mp4", "zip" to "application/zip", "pdf" to "application/pdf")

    fun sniff(file: File, declaredMime: String?, name: String): FlowMediaKind {
        val header = runCatching { file.inputStream().use(com.vidadomu.nexus.ui.components.MediaSniffer::readHeader) }.getOrNull()
        val fromName = byExt[name.substringAfterLast('.', "").lowercase()]
        val mime = com.vidadomu.nexus.ui.components.MediaSniffer.resolve(header, declaredMime, fromName)
        val type = when {
            mime.startsWith("image/") -> AssetType.IMAGE
            mime.startsWith("video/") -> AssetType.VIDEO
            mime.startsWith("audio/") -> AssetType.AUDIO
            else -> AssetType.OTHER
        }
        val ext = byExt.entries.firstOrNull { it.value == mime && it.key.length == 3 }?.key
            ?: android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        return FlowMediaKind(mime, type, ext)
    }

    /** Arregla lo que una versión anterior guardó como «archivo genérico»: lee su contenido y lo reclasifica. */
    suspend fun repair(app: NexusApplication): Int {
        val runtime = AssistantRuntime.get(app)
        var fixed = 0
        for (a in runtime.assets.snapshot().assets) {
            if (a.type != AssetType.OTHER || "flow" !in a.tags || a.libraryDeleted) continue
            val file = Uri.parse(a.uri).path?.let(::File)?.takeIf { it.isFile } ?: continue
            val kind = sniff(file, a.mimeType, a.displayName)
            if (kind.type == AssetType.OTHER) continue
            val renamed = File(file.parentFile, file.nameWithoutExtension + "." + kind.ext)
            val finalFile = if (renamed != file && !renamed.exists() && file.renameTo(renamed)) renamed else file
            var w: Int? = null; var h: Int? = null
            if (kind.type == AssetType.IMAGE) {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(finalFile.path, o); w = o.outWidth.takeIf { it > 0 }; h = o.outHeight.takeIf { it > 0 }
            }
            runCatching {
                runtime.assets.updateAsset(a.id) { it.copy(type = kind.type, mimeType = kind.mime, uri = Uri.fromFile(finalFile).toString(), width = w, height = h) }
                fixed++
            }
        }
        return fixed
    }
}

/**
 * Guarda en la Biblioteca lo que se descarga desde la pestaña de Flow de AXELPC. Sigue trabajando aunque salgas de
 * la pestaña; cada resultado se importa una sola vez y se comprueba su huella SHA-256.
 */
internal object FlowLiveImporter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    val imported = MutableSharedFlow<FlowImported>(extraBufferCapacity = 32)
    val failures = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun enqueue(app: NexusApplication, base: String, asset: RemoteAsset) {
        if (asset.id in FlowLivePrefs.imported(app)) return
        scope.launch {
            mutex.withLock {
                if (asset.id in FlowLivePrefs.imported(app)) return@withLock
                runCatching { importOne(app, base, asset) }
                    .onSuccess { imported.emit(it) }
                    .onFailure { failures.emit("No se pudo guardar «${asset.name}»: ${it.message ?: "error de red"}") }
            }
        }
    }

    private suspend fun importOne(app: NexusApplication, base: String, asset: RemoteAsset): FlowImported {
        val dir = File(app.filesDir, "flow_live").apply { mkdirs() }
        val part = File(dir, "flow_${asset.id}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            http.newCall(Request.Builder().url("$base/assets/${Uri.encode(asset.id)}").build()).execute().use { r ->
                check(r.isSuccessful) { "AXELPC respondió ${r.code}" }
                val body = checkNotNull(r.body) { "respuesta vacía" }
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            digest.update(buf, 0, n); out.write(buf, 0, n)
                        }
                        out.fd.sync()
                    }
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            check(asset.sha256 == null || asset.sha256.equals(hex, ignoreCase = true)) { "la huella no coincide" }
        } catch (e: Exception) {
            part.delete(); throw e
        }
        // El tipo sale del CONTENIDO (firma del archivo), no del nombre: Flow a veces entrega nombres sin extensión.
        val sniffed = FlowMedia.sniff(part, asset.mime, asset.name)
        val mime = sniffed.mime
        val type = sniffed.type
        val target = File(dir, "flow_${asset.id}.${sniffed.ext}")
        check(part.renameTo(target)) { "no se pudo guardar el archivo" }
        var width: Int? = null
        var height: Int? = null
        var seconds: Double? = null
        if (type == AssetType.IMAGE) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.path, o)
            width = o.outWidth.takeIf { it > 0 }; height = o.outHeight.takeIf { it > 0 }
        } else if (type == AssetType.VIDEO || type == AssetType.AUDIO) runCatching {
            android.media.MediaMetadataRetriever().apply {
                try {
                    setDataSource(target.path)
                    seconds = extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }?.div(1000.0)
                    width = extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    height = extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                } finally { release() }
            }
        }
        val runtime = AssistantRuntime.get(app)
        val saved = runtime.assets.addAsset(Asset(
            id = "", uri = Uri.fromFile(target).toString(), type = type, role = AssetRole.OUTPUT, source = AssetSource.GENERATED,
            displayName = "Flow · ${asset.name.substringBeforeLast('.')}".take(120), mimeType = mime,
            width = width, height = height, durationSec = seconds, tags = listOf("flow"), createdAt = System.currentTimeMillis(),
        ))
        FlowLivePrefs.markImported(app, asset.id)
        return FlowImported(saved.id, saved.uri, saved.displayName, mime)
    }
}
