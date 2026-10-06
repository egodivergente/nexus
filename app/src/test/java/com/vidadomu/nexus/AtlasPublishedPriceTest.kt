package com.vidadomu.nexus

import com.vidadomu.nexus.provider.atlas.AtlasPublishedPrice
import org.junit.Assert.*
import org.junit.Test

class AtlasPublishedPriceTest {
    private val page = javaClass.getResource("/native/kling-motion-public-page.md")!!.readText()
    private val id = "kwaivgi/kling-v3.0-std/motion-control"

    @Test fun actualPublicPageSuppliesRateWhenCatalogHasNoUnit() {
        val quote = AtlasPublishedPrice.decode(page, id, 123)!!
        assertEquals(.107, quote.usd, .000001)
        assertEquals("s", quote.unit)
        assertEquals(123L, quote.checkedAt)
        assertTrue(quote.baseTariff)
        assertNull(quote.clipSeconds)
    }
    @Test fun vendorRateDoesNotOverrideAtlasRate() {
        assertTrue(page.contains("0.126"))
        assertEquals(.107, AtlasPublishedPrice.decode(page, id)!!.usd, .000001)
    }
    @Test fun wrongModelOrMissingAuthoritativeSectionIsRejected() {
        assertNull(AtlasPublishedPrice.decode(page, "wrong/model"))
        assertNull(AtlasPublishedPrice.decode(page.replace("## Pricing on Atlas Cloud", "## Vendor pricing"), id))
    }
    @Test fun imageTariffRemainsAUnitRateNotAConfigurationQuote() {
        val imagePage = "**Model ID**: `test/image`\n\n## Pricing on Atlas Cloud\n\n- $0.03 per image\n\n## Other\n- $9 per second"
        val quote = AtlasPublishedPrice.decode(imagePage, "test/image")!!
        assertEquals(.03, quote.usd, .000001)
        assertEquals("imagen", quote.unit)
        assertTrue(quote.baseTariff)
    }
    @Test fun unrecognizedPricingDoesNotBecomeAnImageTariff() {
        assertNull(AtlasPublishedPrice.decode(page.replace("per second", "per million tokens"), id))
    }
}
