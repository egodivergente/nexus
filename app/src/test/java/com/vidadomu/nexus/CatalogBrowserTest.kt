package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.AtlasModelCatalog
import com.vidadomu.nexus.ui.models.*
import com.vidadomu.nexus.ui.components.catalogPrice
import org.junit.Assert.*
import org.junit.Test

class CatalogBrowserTest {
    private val base = AtlasModelCatalog.curatedModels.first().copy(
        id = "sample/dual", displayName = "Modelo de prueba", providerId = "atlas", organization = "Prueba",
        modalities = listOf(Modality.VIDEO), inputMode = ModelInputMode.SINGLE_IMAGE,
        capabilities = listOf(ModelCapability.TEXT_TO_VIDEO, ModelCapability.IMAGE_TO_VIDEO),
        basePriceUsd = .1, priceUnit = "second", priceUnitVerified = true
    )
    @Test fun dualModelAppearsInBothSupportedWorkflows() {
        assertTrue(matchesUse(base, CreationUse.VIDEO_IMAGE))
        assertTrue(matchesUse(base, CreationUse.VIDEO_TEXT))
        assertFalse(matchesUse(base, CreationUse.IMAGE_TEXT))
    }
    @Test fun allModelsAreVisibleWithoutFourItemLimit() {
        val models = (1..12).map { base.copy(id = "sample/$it") }
        assertEquals(12, browse(models).size)
    }
    @Test fun searchAcceptsAccentsWordsAndProvider() {
        val model = base.copy(displayName = "Vídeo especial")
        assertEquals(1, browse(listOf(model), query = "video atlas").size)
        assertTrue(browse(listOf(model), query = "inexistente").isEmpty())
    }
    @Test fun favoriteIsSpecificToModelAndProvider() {
        val other = base.copy(providerId = "crun")
        val result = browseCatalog(listOf(base, other), Modality.VIDEO, null, "", null, true,
            setOf(modelBookmark(other)), CatalogOrder.PRIORITY)
        assertEquals(listOf(other), result)
    }
    @Test fun filteringByManufacturerAndUseCombinesWithSearch() {
        assertEquals(1, browseCatalog(listOf(base), Modality.VIDEO, CreationUse.VIDEO_TEXT,
            "prueba", "Prueba", false, emptySet(), CatalogOrder.NAME).size)
        assertTrue(browseCatalog(listOf(base), Modality.VIDEO, CreationUse.VIDEO_TEXT,
            "prueba", "Otra", false, emptySet(), CatalogOrder.NAME).isEmpty())
    }
    @Test fun unknownPriceCannotSortBeforeKnownPrice() {
        val unknown = base.copy(id = "unknown", priceUnitVerified = false, basePriceUsd = .001)
        assertEquals(base, browseCatalog(listOf(unknown, base), Modality.VIDEO, null, "", null,
            false, emptySet(), CatalogOrder.PRICE).first())
    }
    @Test fun unknownUnitNeverBecomesClipEstimate() {
        assertNull(catalogPrice(base.copy(priceUnit = "tokens"), 5.0))
        assertNull(catalogPrice(base.copy(priceUnitVerified = false), 5.0))
        assertNull(catalogPrice(base.copy(basePriceUsd = Double.NaN), 5.0))
        assertNull(catalogPrice(base, Double.NaN))
    }
    @Test fun imageAndGenerationAreNotMultipliedByVideoDuration() {
        assertEquals(.1, catalogPrice(base.copy(priceUnit = "image"), 10.0)!!.usd, .00001)
        assertEquals(.1, catalogPrice(base.copy(priceUnit = "generation"), 10.0)!!.usd, .00001)
        assertEquals(1.0, catalogPrice(base, 10.0)!!.usd, .00001)
    }
    @Test fun unknownImageBillingUnitIsNotComparable() {
        assertNull(fiveSecondPriceUsd(base.copy(modalities = listOf(Modality.IMAGE), priceUnit = "tokens")))
    }
    private fun browse(models: List<NexusModel>, query: String = "") = browseCatalog(models,
        Modality.VIDEO, null, query, null, false, emptySet(), CatalogOrder.PRIORITY)
}
