package com.vidadomu.nexus.agent.core

import com.axel.link.LinkClient
import com.axel.link.LinkConfig
import com.axel.link.LinkJson
import com.axel.link.LinkResponse
import com.axel.link.LinkRoute
import com.axel.link.LinkStore
import com.axel.link.LinkTransport
import com.vidadomu.nexus.agent.core.agent.GeneratedMediaStore
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.tools.AgentSession
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.agent.core.tools.NexusToolkit
import com.vidadomu.nexus.agent.core.tools.RemoteTools
import com.vidadomu.nexus.agent.core.tools.ToolCategory
import com.vidadomu.nexus.agent.core.tools.ToolDeps
import java.io.IOException
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Coordinador mínimo con el contrato HTTP de AXEL Commander: dispositivos y trabajos. */
private class Coordinator : LinkTransport {
    var offline = false
    val submitted = mutableListOf<Triple<String, String, Map<String, Any?>>>()
    var executor: (String, String, Map<String, Any?>) -> Map<String, Any?> = { _, _, _ -> emptyMap() }
    private val jobs = HashMap<String, Map<String, Any?>>()

    @Suppress("UNCHECKED_CAST")
    override fun post(url: String, token: String, body: String, timeoutMs: Long): LinkResponse {
        if (offline) throw IOException("sin red")
        if (token != "secreto") return LinkResponse(403, "{\"error\": \"Unauthorized or revoked credential\"}")
        val request = LinkJson.obj(body)
        val args = request["arguments"] as? Map<String, Any?> ?: emptyMap()
        return when (request["name"]) {
            "devices.list" -> ok(mapOf("devices" to listOf(
                mapOf("id" to "axelpc", "name" to "AXELPC", "platform" to "windows", "online" to true,
                    "capabilities" to listOf("process.run", "files.list", "files.read", "files.search", "files.mkdir", "files.write_chunk", "screen.capture", "ui.inspect"),
                    "last_seen_seconds" to 1.0, "visual_control_available" to true),
                mapOf("id" to "s25", "name" to "S25 Ultra", "platform" to "android", "online" to true,
                    "capabilities" to listOf("files.list", "files.read"), "last_seen_seconds" to 2.0, "visual_control_available" to false),
            )))
            "jobs.submit" -> {
                val device = args["device_id"].toString()
                val operation = args["operation"].toString()
                val inner = args["args"] as? Map<String, Any?> ?: emptyMap()
                submitted += Triple(device, operation, inner)
                val id = "job" + (jobs.size + 1)
                val (status, result) = try { "succeeded" to executor(device, operation, inner) } catch (error: Exception) { "failed" to mapOf("error" to error.message) }
                jobs[id] = mapOf("id" to id, "device" to device, "operation" to operation, "status" to status, "result" to result)
                ok(jobs.getValue(id))
            }
            "jobs.status" -> ok(jobs.getValue(args["job_id"].toString()))
            else -> LinkResponse(400, "{\"error\": \"Unknown tool\"}")
        }
    }

    private fun ok(value: Map<String, Any?>) = LinkResponse(200, LinkJson.write(value))
}

private class Memory(var text: String?) : LinkStore {
    override fun read(): String? = text
    override fun write(text: String) { this.text = text }
}

private fun paired() = LinkConfig("link-nexus-prueba", "secreto", listOf(LinkRoute("cloudflare", "https://publico.example"))).toJson()

private fun appsReport(state: String, result: String = "") =
    """{"schema":1,"host":"AXELPC","action":"x","apps":[{"id":"claude","name":"Claude","installed":true,"launch":"msix","target":"Claude!App","version":"1.2","cli":"C:\\bin\\claude.cmd","state":"$state","processes":3,"windows":${if (state == "open") """[{"title":"Claude","minimized":false,"foreground":true,"pid":7}]""" else "[]"}$result}]}"""

class RemoteToolsTest {
    private val coordinator = Coordinator()
    private fun client(text: String? = paired()) = LinkClient(Memory(text), coordinator, sleep = {})
    private fun session(mode: AgentMode = AgentMode.AGENT) = AgentSession("remoto", mode)
    private fun input(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test fun theRemoteToolsExistOnlyOnceTheDeviceIsPaired() {
        val h = Harness()
        fun names(link: LinkClient?, media: GeneratedMediaStore? = null) = NexusToolkit.all(
            ToolDeps(h.registry, h.skills, h.cost, h.router, h.guard, h.executor, h.assets, link = link, remoteMedia = media),
        ).map { it.name }.filter { it.startsWith("remote_") }
        assertEquals(emptyList(), names(null))
        assertEquals(emptyList(), names(client(text = null)))
        assertEquals(listOf("remote_devices", "remote_apps_status", "remote_open_app", "remote_run_process", "remote_list_files",
            "remote_read_file", "remote_search_files", "remote_write_file", "remote_list_windows"), names(client()))
        assertTrue("remote_capture_screen" in names(client(), GeneratedMediaStore { _, _ -> Attachment("a", "file:a", "image/png") }))
    }

    @Test fun readingIsFreeAndChangingAComputerFollowsTheAgentMode() {
        val tools = RemoteTools.all(client(), GeneratedMediaStore { _, _ -> Attachment("a", "file:a", "image/png") }).associateBy { it.name }
        val changing = setOf("remote_open_app", "remote_run_process", "remote_write_file")
        tools.values.forEach { tool ->
            assertEquals(if (tool.name in changing) ToolCategory.ORGANIZE else ToolCategory.READ, tool.category, tool.name)
            assertFalse(tool.additive, tool.name)
        }
        assertEquals("Abrir «Claude» en AXELPC", tools.getValue("remote_open_app").describe(input("""{"app":"claude"}""")))
        assertEquals("Ejecutar en asusvidal: uname -a", tools.getValue("remote_run_process").describe(input("""{"device":"asusvidal","argv":["uname","-a"]}""")))
    }

    @Test fun devicesAnswerWithWhatEachOneAnnouncesNow() = runTest {
        val out = RemoteTools.all(client()).single { it.name == "remote_devices" }.run(input("{}"), session())
        assertFalse(out.isError)
        val body = out.result.jsonObject
        assertEquals("online", body["connection"]!!.jsonPrimitive.content)
        assertEquals("cloudflare", body["route"]!!.jsonPrimitive.content)
        val devices = body["devices"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("axelpc", "s25"), devices.map { it["id"]!!.jsonPrimitive.content })
        assertEquals(listOf("files.list", "files.read"), devices[1]["capabilities"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun openingAnAppReportsWhatTheComputerConfirmed() = runTest {
        coordinator.executor = { _, operation, args ->
            assertEquals("process.run", operation)
            val argv = (args["argv"] as List<*>).map { it.toString() }
            assertEquals(listOf("-Action", "open", "-Id", "claude"), argv.takeLast(4))
            mapOf("exit_code" to 0, "stdout" to appsReport("open", ""","result":{"did":"launched","confirmed":true,"foreground":true,"error":""}"""), "stderr" to "")
        }
        val out = RemoteTools.all(client()).single { it.name == "remote_open_app" }.run(input("""{"app":"claude"}"""), session())
        assertFalse(out.isError)
        val body = out.result.jsonObject
        assertEquals("launched", body["did"]!!.jsonPrimitive.content)
        assertTrue(body["confirmed"]!!.jsonPrimitive.boolean)
        val app = body["app"]!!.jsonObject
        assertEquals("open", app["state"]!!.jsonPrimitive.content)
        // La herramienta de terminal está instalada, pero eso no es «aplicación abierta».
        assertTrue(app["cli_installed"]!!.jsonPrimitive.boolean)
        assertEquals(1, coordinator.submitted.size)
    }

    @Test fun anUnknownAppIsRefusedBeforeReachingTheComputer() = runTest {
        val out = RemoteTools.all(client()).single { it.name == "remote_open_app" }.run(input("""{"app":"notepad"}"""), session())
        assertTrue(out.isError)
        assertTrue(coordinator.submitted.isEmpty())
    }

    @Test fun aFailedProcessComesBackAsAnErrorWithItsRealOutput() = runTest {
        coordinator.executor = { _, _, _ -> mapOf("exit_code" to 3, "stdout" to "", "stderr" to "no existe") }
        val out = RemoteTools.all(client()).single { it.name == "remote_run_process" }
            .run(input("""{"device":"axelpc","argv":["cmd","/c","dir","Z:"],"timeout_seconds":5}"""), session())
        assertTrue(out.isError)
        assertEquals(3, out.result.jsonObject["exit_code"]!!.jsonPrimitive.int)
        assertEquals("no existe", out.result.jsonObject["stderr"]!!.jsonPrimitive.content)
        assertEquals(listOf("cmd", "/c", "dir", "Z:"), coordinator.submitted.single().third["argv"])
    }

    @Test fun aDeviceIsNotAskedForWhatItDoesNotAnnounce() = runTest {
        val out = RemoteTools.all(client()).single { it.name == "remote_run_process" }
            .run(input("""{"device":"s25","argv":["id"]}"""), session())
        assertTrue(out.isError)
        assertTrue("process.run" in out.result.jsonObject["error"]!!.jsonPrimitive.content)
        assertTrue(coordinator.submitted.isEmpty())
    }

    @Test fun withoutNetworkNothingIsDoneAndTheAgentIsToldSo() = runTest {
        coordinator.offline = true
        val out = RemoteTools.all(client()).single { it.name == "remote_apps_status" }.run(input("{}"), session())
        assertTrue(out.isError)
        assertEquals("Sin conexión con AXEL Commander ahora mismo. No se ha hecho nada.", out.result.jsonObject["error"]!!.jsonPrimitive.content)
    }

    @Test fun aScreenCaptureIsSavedAndShownInTheChat() = runTest {
        val png = byteArrayOf(1, 2, 3, 4)
        coordinator.executor = { _, operation, _ ->
            assertEquals("screen.capture", operation)
            mapOf("mime_type" to "image/png", "data_b64" to Base64.getEncoder().encodeToString(png), "width" to 1280, "height" to 720)
        }
        var saved: ByteArray? = null
        val store = GeneratedMediaStore { media, _ -> saved = media.bytes; Attachment("att1", "file:captura.png", media.mimeType, name = media.filename, assetId = "asset9") }
        val session = session()
        val out = RemoteTools.all(client(), store).single { it.name == "remote_capture_screen" }.run(input("""{"device":"axelpc"}"""), session)
        assertFalse(out.isError)
        assertEquals("asset9", out.result.jsonObject["asset_id"]!!.jsonPrimitive.content)
        assertTrue(png.contentEquals(saved))
        assertEquals(listOf("att1"), session.renderedMedia.keys.toList())
    }
}
