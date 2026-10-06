package com.vidadomu.nexus

import com.vidadomu.nexus.data.repository.recoverCatalog
import com.vidadomu.nexus.provider.atlas.AtlasModelMapper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CatalogRecoveryTest {
    private val bundled=AtlasModelMapper.parseModelsResponse(javaClass.getResource("/native/catalog-public.json")!!.readText())
    @Test fun missingTruncatedAndEmptyCacheRecoverBundledCatalog() {
        listOf(null,"","[]","[{\"id\":").forEach { content ->
            assertEquals(bundled,recoverCatalog(content) {bundled})
        }
    }
    @Test fun validCacheWinsWithoutLoadingBundledSnapshot() {
        val saved=bundled.take(2)
        assertEquals(saved,recoverCatalog(Json.encodeToString(saved)) {error("Must not replace current cache")})
    }
}
