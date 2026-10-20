package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.ModelCapability
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.provider.atlas.AtlasCloudProvider
import com.vidadomu.nexus.provider.atlas.AtlasPublishedPrice
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PublishedPriceReactivityTest {
    private fun model(id: String, provider: String = AtlasCloudProvider.PROVIDER_ID) = NexusModel(id, id, provider,
        organization = "QA", modalities = listOf(Modality.VIDEO), capabilities = listOf(ModelCapability.IMAGE_TO_VIDEO), parameters = emptyList())
    private fun snapshot(id: String, usd: String = "0.05", unit: String = "second") =
        """[{"id":"$id","usd":$usd,"unit":"$unit","checkedAt":1792400000000}]"""
    private fun uniqueId(tag: String) = "qa-reactivity/$tag-${System.nanoTime()}"

    @Test fun collectorWakesWhenTariffEntersCacheAfterSubscribing() = runTest {
        val id = uniqueId("signal")
        assertNull(AtlasPublishedPrice.cached(model(id)))
        val before = AtlasPublishedPrice.revision.value
        val woke = async(start = CoroutineStart.UNDISPATCHED) { AtlasPublishedPrice.revision.first { it > before } }
        assertFalse(woke.isCompleted)
        AtlasPublishedPrice.seedSnapshot(snapshot(id))
        assertTrue(woke.await() > before)
        assertEquals("s", AtlasPublishedPrice.cached(model(id))?.unit)
    }

    @Test fun signalForOneModelNeverExposesItsTariffToAnother() {
        val priced = uniqueId("priced")
        val other = uniqueId("other")
        AtlasPublishedPrice.seedSnapshot(snapshot(priced, "0.07", "image"))
        val quote = AtlasPublishedPrice.cached(model(priced))!!
        assertEquals(.07, quote.usd, 1e-9)
        assertEquals("imagen", quote.unit)
        assertTrue(quote.baseTariff)
        assertNull(AtlasPublishedPrice.cached(model(other)))
        assertNull(AtlasPublishedPrice.cached(model(priced, provider = "runway")))
        assertNull(AtlasPublishedPrice.cached(model("$other-alias").copy(providerModelId = other)))
        assertNotNull(AtlasPublishedPrice.cached(model("$other-alias").copy(providerModelId = priced)))
    }

    @Test fun reseedingOrRejectedRowsDoNotSignalNorReplaceTariff() {
        val id = uniqueId("stable")
        AtlasPublishedPrice.seedSnapshot(snapshot(id, "0.05"))
        val revision = AtlasPublishedPrice.revision.value
        AtlasPublishedPrice.seedSnapshot(snapshot(id, "0.09"))
        AtlasPublishedPrice.seedSnapshot(snapshot(uniqueId("negative"), "-1"))
        AtlasPublishedPrice.seedSnapshot(snapshot(uniqueId("tokens"), unit = "token"))
        assertEquals(revision, AtlasPublishedPrice.revision.value)
        assertEquals(.05, AtlasPublishedPrice.cached(model(id))!!.usd, 1e-9)
    }
}
