package com.axel.link

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class LinkRemoteTest {
    private val coordinator = FakeCoordinator()
    private val remote = LinkRemote(LinkClient(MemoryStore(config()), coordinator, sleep = {}))

    @Test fun runsAProcessWithoutAShell() {
        coordinator.executor = { device, operation, args ->
            assertEquals("asusvidal", device); assertEquals("process.run", operation)
            assertEquals(listOf("uname", "-a"), args.items("argv")); assertEquals("/tmp", args.text("cwd"))
            mapOf("exit_code" to 0, "stdout" to "Linux", "stderr" to "", "stdout_truncated" to false)
        }
        assertEquals(LinkProcess(0, "Linux", "", false), remote.run("asusvidal", listOf("uname", "-a"), "/tmp"))
    }

    @Test fun aProgramThatExitsWithAnErrorIsAResultNotALostJob() {
        coordinator.executor = { _, _, _ -> mapOf("exit_code" to 4294770688L, "stdout" to "", "stderr" to "no existe", "stderr_truncated" to false) }
        assertEquals(LinkProcess(-196608, "", "no existe", false), remote.run("axelpc", listOf("powershell.exe", "-File", "falta.ps1")))
        coordinator.executor = { _, _, _ -> mapOf("exit_code" to 3, "stdout" to "a medias", "stderr" to "") }
        assertEquals(3, remote.run("asusvidal", listOf("false")).exitCode)
    }

    @Test fun aProgramThatCouldNotStartIsAFailedJob() {
        coordinator.executor = { _, _, _ -> throw IllegalStateException("Executable not allowed") }
        assertEquals("Executable not allowed", assertThrows(LinkJobFailed::class.java) { remote.run("axelpc", listOf("nada.exe")) }.message)
    }

    @Test fun writesLargeFilesInVerifiedChunks() {
        val bytes = ByteArray(1_200_000) { (it % 251).toByte() }
        val received = java.io.ByteArrayOutputStream()
        val finals = ArrayList<Boolean>()
        coordinator.executor = { _, _, args ->
            assertEquals(received.size().toLong(), args.number("offset")!!.toLong())
            assertEquals(LinkRemote.sha256(bytes), args.text("sha256"))
            received.write(Base64.getDecoder().decode(args.text("data_b64")))
            finals.add(args.flag("final"))
            mapOf("path" to "C:\\destino.bin", "complete" to args.flag("final"))
        }
        assertEquals("C:\\destino.bin", remote.write("axelpc", "~/destino.bin", bytes))
        assertArrayEquals(bytes, received.toByteArray())
        assertEquals(listOf(false, false, true), finals)
    }

    @Test fun decodesAScreenCapture() {
        coordinator.executor = { _, _, args ->
            assertEquals(800L, args["max_width"])
            mapOf("mime_type" to "image/png", "data_b64" to Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)), "width" to 800, "height" to 450)
        }
        val image = remote.capture("axelpc", 800)
        assertArrayEquals(byteArrayOf(1, 2, 3), image.bytes)
        assertEquals(800, image.width)
    }

    @Test fun refusesWhatTheDeviceDoesNotAdvertise() {
        assertEquals("asusvidal no ofrece screen.capture ahora mismo", assertThrows(LinkRejected::class.java) { remote.capture("asusvidal") }.message)
        assertEquals("s25 está sin conexión", assertThrows(LinkRejected::class.java) { remote.readText("s25", "x") }.message)
        assertEquals("Este cliente no tiene acceso al dispositivo pixel", assertThrows(LinkRejected::class.java) { remote.list("pixel", "x") }.message)
    }
}
