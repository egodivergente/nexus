package com.axel.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Contra el AXEL Commander real. Solo se ejecuta si AXEL_LINK_LIVE apunta a un archivo de emparejamiento;
 * sin él se salta (así ocurre en CI). No abre ni cierra nada: pregunta y comprueba que la respuesta es real.
 */
class LiveCoordinatorTest {
    private val pairing = System.getenv("AXEL_LINK_LIVE").orEmpty()

    @Test fun readsTheDevicesAndTheRealStateOfTheApplications() {
        assumeTrue("AXEL_LINK_LIVE sin definir", pairing.isNotBlank())
        val client = LinkClient(FileLinkStore(File(pairing)))
        val devices = client.refresh()
        println("LIVE estado " + client.state)
        devices.forEach { println("LIVE dispositivo ${it.id} en_linea=${it.online} ${it.capabilities.sorted()}") }
        assertTrue("el cliente ve AXELPC", devices.any { it.id == "axelpc" })
        val started = System.currentTimeMillis()
        val apps = RemoteApps(client).status()
        println("LIVE consulta " + (System.currentTimeMillis() - started) + " ms")
        apps.forEach { println("LIVE app ${it.id} ${it.state} lanzamiento=${it.launch} ventanas=${it.windows} minimizada=${it.minimized} cli=${it.cli.isNotBlank()}") }
        assertEquals(RemoteApps.KNOWN.map { it.first }, apps.map { it.id })
        assertTrue("ninguna aplicación queda sin estado", apps.none { it.state == RemoteAppState.UNKNOWN })
    }
}
