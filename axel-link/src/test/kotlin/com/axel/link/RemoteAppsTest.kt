package com.axel.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class RemoteAppsTest {
    private val coordinator = FakeCoordinator()
    private val client = LinkClient(MemoryStore(config()), coordinator, sleep = {})
    private val installed = HashMap<String, ByteArray>()
    private val report = """{"schema":1,"host":"AXELPC","action":"status","apps":[
        {"id":"agy","name":"Google Antigravity","installed":true,"launch":"exe","target":"C:\\Apps\\Antigravity.exe","version":null,"cli":"C:\\bin\\agy.EXE","state":"open","processes":16,"service":false,"windows":[{"title":"Proyecto","minimized":false,"foreground":true,"pid":1}]},
        {"id":"claude","name":"Claude","installed":true,"launch":"msix","target":"Claude_x!Claude","version":"5.1","cli":"C:\\bin\\claude.exe","state":"closed","processes":0,"service":false,"windows":[]},
        {"id":"codex","name":"Codex","installed":true,"launch":"msix","target":"OpenAI.Codex_x!App","version":"26","cli":null,"state":"open","processes":3,"service":false,"windows":[{"title":"ChatGPT","minimized":true,"foreground":false,"pid":2}]},
        {"id":"nexus","name":"Nexus Desktop","installed":true,"launch":"shortcut","target":"C:\\Desktop\\Nexus Studio.lnk","version":"2026.11","cli":null,"state":"background","processes":0,"service":true,"windows":[]}]}"""

    /** Un AXELPC simulado: el guion solo responde si antes se ha instalado con su huella correcta. */
    private fun windows(answer: (List<String>) -> String = { report }) {
        coordinator.executor = { _, operation, args ->
            when (operation) {
                "files.mkdir" -> mapOf("path" to args.text("path"))
                "files.write_chunk" -> {
                    val data = Base64.getDecoder().decode(args.text("data_b64"))
                    assertEquals(args.text("sha256"), LinkRemote.sha256(data))
                    assertTrue(args.flag("final"))
                    installed[args.text("path").substringAfterLast('/')] = data
                    mapOf("path" to args.text("path"), "complete" to true)
                }
                "process.run" -> {
                    val argv = args.items("argv").map { it.toString() }
                    val file = argv[argv.indexOf("-File") + 1].substringAfterLast('\\')
                    // Lo que devuelve PowerShell de verdad cuando el archivo de -File no existe.
                    if (file !in installed) mapOf("exit_code" to 4294770688L, "stdout" to "", "stderr" to "El argumento para el parámetro -File no existe")
                    else mapOf("exit_code" to 0, "stdout" to answer(argv), "stderr" to "")
                }
                else -> throw IllegalStateException("inesperado")
            }
        }
    }

    @Test fun installsItsScriptOnceAndReadsTheRealState() {
        windows()
        val apps = RemoteApps(client)
        val first = apps.status()
        assertEquals(listOf("agy", "claude", "codex", "nexus"), first.map { it.id })
        assertEquals(listOf(RemoteAppState.OPEN, RemoteAppState.CLOSED, RemoteAppState.OPEN, RemoteAppState.BACKGROUND), first.map { it.state })
        assertTrue(first[0].foreground); assertTrue(first[2].minimized); assertFalse(first[0].minimized)
        assertEquals("la herramienta de consola se distingue de la aplicación", "C:\\bin\\claude.exe", first[1].cli)
        assertEquals(1, installed.size)
        assertTrue(installed.keys.single().matches(Regex("axel-apps-[0-9a-f]{12}\\.ps1")))
        val before = coordinator.submitted.size
        apps.status()
        assertEquals("ya instalado: una sola ejecución", before + 1, coordinator.submitted.size)
    }

    @Test fun openReportsWhatReallyHappened() {
        windows { argv ->
            assertEquals(listOf("-Action", "open", "-Id", "claude"), argv.takeLast(4))
            """{"apps":[{"id":"claude","name":"Claude","installed":true,"launch":"msix","target":"Claude_x!Claude","state":"open","windows":[{"title":"Claude","minimized":false,"foreground":true,"pid":9}],"result":{"did":"launched","confirmed":true,"foreground":true,"error":null}}]}"""
        }
        val opened = RemoteApps(client).open("claude")
        assertEquals("launched", opened.did)
        assertTrue(opened.confirmed); assertTrue(opened.foreground)
        assertEquals(RemoteAppState.OPEN, opened.app.state)
        assertEquals("", opened.error)
        assertThrows(IllegalArgumentException::class.java) { RemoteApps(client).open("calc") }
    }

    @Test fun aFailedLaunchIsNotReportedAsOpen() {
        windows { """{"apps":[{"id":"claude","name":"Claude","installed":true,"state":"closed","windows":[],"result":{"did":"launched","confirmed":false,"foreground":false,"error":"no_window"}}]}""" }
        val opened = RemoteApps(client).open("claude")
        assertFalse(opened.confirmed)
        assertEquals("no_window", opened.error)
        assertEquals(RemoteAppState.CLOSED, opened.app.state)
    }

    @Test fun explainsWhyItCannotAskInsteadOfGuessing() {
        windows()
        coordinator.devices = listOf(FakeCoordinator.device("axelpc", "windows", false, listOf("process.run")))
        assertEquals("axelpc está sin conexión", assertThrows(LinkRejected::class.java) { RemoteApps(client).status() }.message)
        coordinator.devices = listOf(FakeCoordinator.device("axelpc", "windows", true, listOf("files.read")))
        assertEquals("axelpc no ofrece process.run ahora mismo", assertThrows(LinkRejected::class.java) { RemoteApps(client).status() }.message)
        coordinator.devices = listOf(FakeCoordinator.device("asusvidal", "linux", true, listOf("process.run")))
        assertEquals("asusvidal no es un equipo Windows", assertThrows(LinkRejected::class.java) { RemoteApps(client, "asusvidal").status() }.message)
        assertTrue("sin ejecutar nada en el equipo", coordinator.submitted.isEmpty())
    }

    @Test fun aBrokenScriptSurfacesItsOwnError() {
        coordinator.executor = { _, operation, _ ->
            if (operation == "process.run") mapOf("exit_code" to 1, "stdout" to "", "stderr" to "\nAcceso denegado\n") else mapOf("complete" to true)
        }
        assertEquals("Acceso denegado", assertThrows(LinkException::class.java) { RemoteApps(client).status() }.message)
        // Con el guion ya en el equipo, Commander rechaza volver a escribirlo: se sigue viendo el error del guion.
        coordinator.executor = { _, operation, _ ->
            when (operation) {
                "process.run" -> mapOf("exit_code" to 1, "stdout" to "", "stderr" to "Fallo propio del guion")
                "files.write_chunk" -> throw IllegalStateException("Destination exists")
                else -> mapOf("complete" to true)
            }
        }
        assertEquals("Fallo propio del guion", assertThrows(LinkException::class.java) { RemoteApps(client).status() }.message)
    }
}
