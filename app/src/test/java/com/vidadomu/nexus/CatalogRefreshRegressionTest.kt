package com.vidadomu.nexus

import com.vidadomu.nexus.data.repository.*
import com.vidadomu.nexus.provider.atlas.AtlasModelMapper
import com.vidadomu.nexus.ui.models.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class CatalogRefreshRegressionTest {
    private val models = AtlasModelMapper.parseModelsResponse(javaClass.getResource("/native/catalog-public.json")!!.readText())

    @Test fun refreshKeepsHiddenRoutesWhenProviderRenamesUnknownFamily() {
        val original = models.first().copy(id="vendor/engine", providerModelId="vendor/engine", modelFamily="Original", displayName="Original")
        val hidden = setOf(familyVisibilityKey("Original"))
        val captured = captureHiddenRoutes(listOf(original), hidden, emptyMap())
        val refreshed = original.copy(modelFamily="Renamed", displayName="Renamed", basePriceUsd=0.25)
        val restored = captured.mapValues { it.value.toSet() }
        assertTrue(visibleCatalog(listOf(refreshed), hidden, restored).isEmpty())
        assertEquals(listOf(refreshed), visibleCatalog(listOf(refreshed), emptySet(), restored))
    }

    @Test fun refreshingSeedanceKeepsOneFamilyAndVersionGroups() {
        val original = models.first { "seedance" in it.id }
        val refreshed = listOf("text-to-video", "image-to-video").map { workflow ->
            original.copy(id="bytedance/seedance-2.5/$workflow", providerModelId="bytedance/seedance-2.5/$workflow",
                modelFamily="Provider renamed family", displayName="Changed $workflow", basePriceUsd=0.12)
        }
        assertEquals(listOf("Seedance"), catalogSections(refreshed, emptySet()).map { it.family })
        assertEquals(listOf("Seedance 2.5"), catalogModelGroups(refreshed).map { it.title })
        assertEquals(2, catalogModelGroups(refreshed).single().routes.size)
        assertTrue(visibleCatalog(refreshed, setOf(familyVisibilityKey("Seedance")), emptyMap()).isEmpty())
    }

    @Test fun hiddenToolsSurviveRefreshAndLegacyCategoryKeys() {
        val tool = models.first { "upscal" in it.id }
        val category = toolCategory(tool)!!
        for (key in listOf(toolVisibilityKey(category), familyVisibilityKey(category))) {
            val fresh = tool.copy(basePriceUsd=0.3)
            assertFalse(isCatalogModelVisible(fresh, setOf(key)))
            assertEquals(category, toolCategory(fresh))
        }
        assertTrue(catalogAtRoot(null, "", false))
        assertFalse(catalogAtRoot(category, "", false))
    }

    @Test fun sortingNeverFlattensFamiliesOrToolCategories() {
        val seedance = models.filter { "seedance" in it.id }
        val tools = models.filter { toolCategory(it) != null }.groupBy { toolCategory(it)!! }
            .map { (category, routes) -> CatalogSection(category, routes) }
        for (sort in CatalogSort.entries) {
            assertEquals(listOf("Seedance"), sortSections(catalogSections(seedance, emptySet()), sort, emptyList(), false).map { it.family })
            assertEquals(tools.map { it.family }.toSet(), sortSections(tools, sort, emptyList(), false).map { it.family }.toSet())
        }
    }

    @Test fun restoringParentRetainsExplicitHiddenChildAcrossRefresh() {
        val model = models.first { "seedance" in it.id }
        val parent = familyVisibilityKey("Seedance")
        val child = routeVisibilityKey(model)
        val captured = captureHiddenRoutes(listOf(model), setOf(parent, child), emptyMap())
        assertTrue(visibleCatalog(listOf(model), setOf(child), captured).isEmpty())
        assertEquals(listOf(model), visibleCatalog(listOf(model), emptySet(), captured))
    }

    @Test fun providerIdentityAndGroupKeysDoNotDependOnDeviceLocale() {
        val originalLocale = Locale.getDefault()
        try {
            val model = models.first { "seedance" in it.id }.copy(id="opaque-id", providerModelId="bytedance/seedance-2.5/text-to-video")
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("Seedance", catalogFamily(model))
            assertEquals("Seedance:seedance 2.5", catalogModelGroups(listOf(model)).single().key)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
