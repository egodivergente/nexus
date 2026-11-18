package com.axel.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class LinkConfigTest {
    @Test fun survivesARoundTripAndTrimsTheAddress() {
        val parsed = LinkConfig.parse("""{"client_id":"c","token":"t","routes":[{"id":"a","url":"https://uno.example/"},{"id":"b","url":"http://127.0.0.1:18980"}],
            "discovery":{"device":"asusvidal","path":"~/x.json","route":"a"}}""")
        assertEquals("https://uno.example", parsed.routes[0].url)
        assertEquals(parsed, LinkConfig.parse(parsed.toJson()))
        assertEquals("https://dos.example", parsed.withRoute("a", "https://dos.example/").routes[0].url)
    }

    @Test fun refusesAnythingThatCouldLeakTheCredential() {
        fun with(url: String) = """{"client_id":"c","token":"t","routes":[{"id":"a","url":"$url"}]}"""
        for (bad in listOf("http://uno.example", "https://usuario:clave@uno.example", "https://uno.example/?a=1", "https://uno.example/#x", "ftp://uno.example", "uno.example")) {
            assertThrows(bad, IllegalArgumentException::class.java) { LinkConfig.parse(with(bad)) }
        }
        assertThrows(IllegalArgumentException::class.java) { LinkConfig.parse("""{"client_id":"c","token":"","routes":[{"id":"a","url":"https://uno.example"}]}""") }
        assertThrows(IllegalArgumentException::class.java) { LinkConfig.parse("""{"client_id":"c","token":"t","routes":[]}""") }
    }

    @Test fun fileStoreWritesAtomicallyAndReadsBack() {
        val folder = File(System.getProperty("java.io.tmpdir"), "axel-link-test-" + System.nanoTime())
        try {
            val store = FileLinkStore(File(folder, "axel-link.json"))
            assertNull(store.read())
            store.write("uno")
            store.write("dos")
            assertEquals("dos", store.read())
            assertEquals(listOf("axel-link.json"), folder.list()!!.toList())
        } finally {
            folder.deleteRecursively()
        }
    }
}
