package com.vidadomu.nexus.provider.atlas

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadReuseCacheTest {
    @Test fun `un lote con la misma referencia la sube una sola vez`() = runTest {
        val cache = UploadReuseCache()
        val uploads = AtomicInteger()
        val results = (1..20).map { cache.getOrUpload("foto") { uploads.incrementAndGet(); Result.success("https://cdn/foto.png") } }
        assertEquals(1, uploads.get())
        assertTrue(results.all { it.first.getOrNull() == "https://cdn/foto.png" })
        assertFalse("la primera sí se sube", results.first().second)
        assertTrue("las demás se reutilizan", results.drop(1).all { it.second })
    }

    @Test fun `dos envios simultaneos del mismo archivo comparten la subida`() = runTest {
        val cache = UploadReuseCache()
        val uploads = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val jobs = (1..3).map { async { cache.getOrUpload("foto") { uploads.incrementAndGet(); gate.await(); Result.success("u") } } }
        gate.complete(Unit)
        assertTrue(jobs.awaitAll().all { it.first.getOrNull() == "u" })
        assertEquals(1, uploads.get())
    }

    @Test fun `un fallo no se guarda y el siguiente intento vuelve a subir`() = runTest {
        val cache = UploadReuseCache()
        val uploads = AtomicInteger()
        val first = cache.getOrUpload("foto") { uploads.incrementAndGet(); Result.failure(IllegalStateException("red")) }
        assertTrue(first.first.isFailure)
        val second = cache.getOrUpload("foto") { uploads.incrementAndGet(); Result.success("u") }
        assertEquals("u", second.first.getOrNull())
        assertEquals(2, uploads.get())
    }

    @Test fun `la url caduca y entonces se vuelve a subir`() = runTest {
        var now = 0L
        val cache = UploadReuseCache(ttlMs = 1_000, clock = { now })
        val uploads = AtomicInteger()
        cache.getOrUpload("foto") { uploads.incrementAndGet(); Result.success("u1") }
        now = 900
        assertEquals("u1", cache.getOrUpload("foto") { uploads.incrementAndGet(); Result.success("u2") }.first.getOrNull())
        now = 2_000
        assertEquals("u2", cache.getOrUpload("foto") { uploads.incrementAndGet(); Result.success("u2") }.first.getOrNull())
        assertEquals(2, uploads.get())
    }

    @Test fun `la clave depende del contenido, el tipo y la cuenta, no del nombre`() {
        val dir = createTempDir()
        try {
            val a = File(dir, "a.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val b = File(dir, "copia con otro nombre.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val c = File(dir, "c.png").apply { writeBytes(byteArrayOf(1, 2, 4)) }
            val key = UploadReuseCache.contentKey(a, "image/png", "cuenta1")
            assertEquals(key, UploadReuseCache.contentKey(b, "image/png", "cuenta1"))
            assertNotEquals(key, UploadReuseCache.contentKey(c, "image/png", "cuenta1"))
            assertNotEquals(key, UploadReuseCache.contentKey(a, "image/jpeg", "cuenta1"))
            assertNotEquals(key, UploadReuseCache.contentKey(a, "image/png", "cuenta2"))
            assertFalse("no guarda la clave en claro", key.contains("cuenta1"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
