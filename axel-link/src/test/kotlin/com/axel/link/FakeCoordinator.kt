package com.axel.link

import java.io.IOException

/** Coordinador simulado con el mismo contrato HTTP que AXEL Commander: rutas, credencial, dispositivos y trabajos. */
class FakeCoordinator(private val token: String = "secreto") : LinkTransport {
    val down = HashSet<String>()
    val broken = HashSet<String>()
    val seen = ArrayList<Pair<String, String>>()
    var devices: List<Map<String, Any?>> = listOf(
        device("axelpc", "windows", true, listOf("process.run", "files.read", "files.mkdir", "files.write_chunk", "screen.capture", "ui.inspect")),
        device("asusvidal", "linux", true, listOf("process.run", "files.read")),
        device("s25", "android", false, listOf("files.read")),
    )
    /** Lo que hace el dispositivo. Lanzar una excepción deja el trabajo como fallido con ese mensaje. */
    var executor: (device: String, operation: String, args: Map<String, Any?>) -> Map<String, Any?> = { _, _, _ -> emptyMap() }
    val submitted = ArrayList<Triple<String, String, Map<String, Any?>>>()
    private val jobs = HashMap<String, Map<String, Any?>>()
    private val keys = HashMap<String, String>()

    override fun post(url: String, token: String, body: String, timeoutMs: Long): LinkResponse {
        val base = url.removeSuffix("/v1/tools/call")
        val request = LinkJson.obj(body)
        val name = request.text("name")
        seen.add(base to name)
        if (base in down) throw IOException("sin red")
        if (base in broken) return LinkResponse(530, "<html>error 1033</html>")
        if (token != this.token) return LinkResponse(403, "{\"error\": \"Unauthorized or revoked credential\"}")
        val args = request.child("arguments")
        return when (name) {
            "devices.list" -> ok(mapOf("devices" to devices))
            "jobs.submit" -> {
                val device = args.text("device_id")
                if (devices.none { it["id"] == device }) return LinkResponse(403, "{\"error\": \"Device not granted\"}")
                val key = args.text("idempotency_key")
                val id = keys.getOrPut(key) {
                    val fresh = "job" + (jobs.size + 1)
                    val operation = args.text("operation")
                    submitted.add(Triple(device, operation, args.child("args")))
                    val (status, result) = try {
                        val answer = executor(device, operation, args.child("args"))
                        // Igual que el coordinador real: un programa que sale con error deja el trabajo fallido, con su salida.
                        val exit = answer["exit_code"] as? Number
                        (if (operation == "process.run" && exit != null && exit.toLong() != 0L) "failed" else "succeeded") to answer
                    } catch (error: Exception) {
                        "failed" to mapOf("error" to (error.message ?: "fallo"))
                    }
                    jobs[fresh] = mapOf("id" to fresh, "device" to device, "operation" to operation, "status" to status, "result" to result)
                    fresh
                }
                // La respuesta al envío aún no trae el resultado: obliga al cliente a consultar el estado.
                ok(jobs.getValue(id) + mapOf("status" to "running", "result" to null))
            }
            "jobs.status" -> jobs[args.text("job_id")]?.let { ok(it) } ?: LinkResponse(400, "{\"error\": \"'Job not found'\"}")
            else -> LinkResponse(400, "{\"error\": \"'Unknown tool'\"}")
        }
    }

    private fun ok(value: Map<String, Any?>) = LinkResponse(200, LinkJson.write(value))

    companion object {
        fun device(id: String, platform: String, online: Boolean, capabilities: List<String>): Map<String, Any?> = mapOf(
            "id" to id, "name" to id, "platform" to platform, "online" to online, "capabilities" to capabilities,
            "last_seen_seconds" to 2.5, "visual_control_available" to ("ui.action" in capabilities),
        )
    }
}

class MemoryStore(var text: String?) : LinkStore {
    var writes = 0
    override fun read(): String? = text
    override fun write(text: String) { this.text = text; writes += 1 }
}

const val PUBLIC = "https://publico.example"
const val STABLE = "https://estable.example:8443"

fun config(token: String = "secreto", discovery: Boolean = false): String = LinkConfig(
    "link-prueba", token, listOf(LinkRoute("cloudflare", PUBLIC), LinkRoute("asus", STABLE)),
    if (discovery) LinkDiscovery("asusvidal", "~/endpoint.json", "cloudflare") else null,
).toJson()
