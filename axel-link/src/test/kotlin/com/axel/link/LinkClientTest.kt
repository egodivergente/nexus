package com.axel.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkClientTest {
    private val coordinator = FakeCoordinator()
    private var clock = 1_000_000L
    private fun client(store: LinkStore = MemoryStore(config())) = LinkClient(store, coordinator, { clock }, { clock += it })

    @Test fun withoutACredentialItSaysSoAndNeverTouchesTheNetwork() {
        val client = client(MemoryStore(null))
        assertTrue(client.state is LinkState.Unconfigured)
        assertThrows(LinkUnconfigured::class.java) { client.refresh() }
        assertTrue(coordinator.seen.isEmpty())
    }

    @Test fun usesThePublicRouteAndReportsRealCapabilities() {
        val client = client()
        val devices = client.refresh()
        assertEquals(LinkState.Online::class.java, client.state.javaClass)
        assertEquals("cloudflare", (client.state as LinkState.Online).route)
        assertEquals(listOf("axelpc", "asusvidal", "s25"), devices.map { it.id })
        assertTrue(client.device("axelpc")!!.can("process.run"))
        assertFalse("un dispositivo sin conexión no puede nada", client.device("s25")!!.can("files.read"))
        assertFalse(client.device("asusvidal")!!.can("screen.capture"))
    }

    @Test fun fallsOverToTheNextRouteAndReturnsToThePreferredOneLater() {
        val client = client()
        coordinator.down += PUBLIC
        client.refresh()
        assertEquals("asus", (client.state as LinkState.Online).route)
        coordinator.seen.clear()
        client.refresh()
        assertEquals("no insiste en la ruta caída en cada llamada", listOf(STABLE to "devices.list"), coordinator.seen)
        coordinator.down.clear()
        clock += 5 * 60_000
        client.refresh()
        assertEquals("cloudflare", (client.state as LinkState.Online).route)
    }

    @Test fun aGatewayErrorPageCountsAsADeadRoute() {
        val client = client()
        coordinator.broken += PUBLIC
        client.refresh()
        assertEquals("asus", (client.state as LinkState.Online).route)
    }

    @Test fun reportsReconnectingWithGrowingAttemptsAndRecovers() {
        val client = client()
        val states = ArrayList<LinkState>()
        client.addListener { states.add(it) }
        coordinator.down += listOf(PUBLIC, STABLE)
        assertThrows(LinkOffline::class.java) { client.refresh() }
        assertThrows(LinkOffline::class.java) { client.refresh() }
        assertEquals(listOf(1, 2), states.filterIsInstance<LinkState.Reconnecting>().map { it.attempt })
        coordinator.down.clear()
        client.refresh()
        assertTrue(states.last() is LinkState.Online)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (1..7).map { LinkClient.backoff(it) })
    }

    @Test fun aRejectedCredentialIsNotConfusedWithANetworkProblem() {
        val client = client(MemoryStore(config(token = "otra")))
        assertThrows(LinkUnauthorized::class.java) { client.refresh() }
        assertTrue(client.state is LinkState.Unauthorized)
        assertEquals("no prueba otra ruta con una credencial rechazada", 1, coordinator.seen.size)
    }

    @Test fun aDeniedOperationKeepsTheConnectionOnline() {
        val client = client()
        client.refresh()
        val error = assertThrows(LinkRejected::class.java) { client.submit("otro", "process.run") }
        assertEquals("Device not granted", error.message)
        assertTrue(client.state is LinkState.Online)
    }

    @Test fun executeWaitsForTheResultAndSurfacesDeviceFailures() {
        val client = client()
        coordinator.executor = { _, operation, args -> if (operation == "process.run") mapOf("exit_code" to 0, "stdout" to args.items("argv").joinToString(" ")) else throw IllegalStateException("No existe") }
        assertEquals("hostname", client.execute("axelpc", "process.run", mapOf("argv" to listOf("hostname"))).text("stdout"))
        val failed = assertThrows(LinkJobFailed::class.java) { client.execute("axelpc", "files.read", mapOf("path" to "x")) }
        assertEquals("No existe", failed.message)
    }

    @Test fun aNetworkCutWhileWaitingDoesNotLoseTheJobOrRunItTwice() {
        val client = client()
        coordinator.executor = { _, _, _ -> mapOf("exit_code" to 0) }
        val job = client.submit("axelpc", "process.run", mapOf("argv" to listOf("x")), key = "k1")
        coordinator.down += listOf(PUBLIC, STABLE)
        var polls = 0
        val waiting = LinkClient(MemoryStore(config()), LinkTransport { url, token, body, timeout ->
            if (++polls == 3) coordinator.down.clear()
            coordinator.post(url, token, body, timeout)
        }, { clock }, { clock += it })
        assertTrue(waiting.await(job, 60_000).succeeded)
        // Reenviar con la misma clave devuelve el mismo trabajo: nada se ejecuta dos veces.
        assertEquals(job.id, client.submit("axelpc", "process.run", mapOf("argv" to listOf("x")), key = "k1").id)
        assertEquals(1, coordinator.submitted.size)
    }

    @Test fun awaitGivesUpAtTheDeadlineWithoutCancelling() {
        val client = client()
        val stuck = LinkJob("j", "axelpc", "process.run", "running", emptyMap(), "")
        val never = LinkClient(MemoryStore(config()), LinkTransport { _, _, _, _ -> LinkResponse(200, "{\"id\":\"j\",\"status\":\"running\"}") }, { clock }, { clock += it })
        val timeout = assertThrows(LinkTimeout::class.java) { never.await(stuck, 5_000) }
        assertEquals("j", timeout.job.id)
        assertTrue(client.state is LinkState.Connecting)
    }

    @Test fun discoversTheNewPublicAddressThroughCommanderAndPersistsIt() {
        val store = MemoryStore(config(discovery = true))
        val client = client(store)
        coordinator.broken += PUBLIC
        coordinator.executor = { device, operation, args ->
            assertEquals("asusvidal", device); assertEquals("files.read", operation); assertEquals("~/endpoint.json", args.text("path"))
            mapOf("text" to "{\"url\": \"https://nuevo.example/\", \"transport\": \"cloudflare_quick_tunnel\"}")
        }
        client.hold().use {
            waitFor { LinkConfig.parse(store.text!!).routes[0].url == "https://nuevo.example" }
        }
        assertEquals(STABLE, LinkConfig.parse(store.text!!).routes[1].url)
        assertEquals(1, store.writes)
    }

    @Test fun discoveryNeverAcceptsAnInsecureAddress() {
        val store = MemoryStore(config(discovery = true))
        val client = client(store)
        coordinator.executor = { _, _, _ -> mapOf("text" to "{\"url\": \"http://malo.example\"}") }
        client.hold().use { waitFor { coordinator.submitted.isNotEmpty() }; Thread.sleep(150) }
        assertEquals(0, store.writes)
    }

    @Test fun whileHeldItReconnectsOnItsOwn() {
        coordinator.down += listOf(PUBLIC, STABLE)
        val client = LinkClient(MemoryStore(config()), coordinator)
        client.hold().use {
            waitFor { client.state is LinkState.Reconnecting }
            assertTrue("anuncia cuándo reintenta", (client.state as LinkState.Reconnecting).retryAt > 0)
            coordinator.down.clear()
            client.kick()
            waitFor { client.state is LinkState.Online }
        }
    }

    @Test fun aNewCredentialIsPickedUpAfterBeingRejected() {
        val store = MemoryStore(config(token = "otra"))
        val client = LinkClient(store, coordinator)
        client.hold().use {
            waitFor { client.state is LinkState.Unauthorized }
            store.text = config()
            client.kick()
            waitFor { client.state is LinkState.Online }
        }
    }

    private fun waitFor(condition: () -> Boolean) {
        val limit = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > limit) throw AssertionError("La condición no se cumplió a tiempo")
            Thread.sleep(20)
        }
    }
}
