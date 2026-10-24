package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.*
import com.vidadomu.nexus.ui.models.*
import org.junit.Assert.*
import org.junit.Test

class CatalogHierarchyTest {
    private val models = AtlasModelMapper.parseModelsResponse(javaClass.getResource("/native/catalog-public.json")!!.readText())
    private val media = models.filter { Modality.IMAGE in it.modalities || Modality.VIDEO in it.modalities }
    @Test fun klingTiersAreSeparateCardsAndKeepEveryRoute() {
        val kling = media.filter { it.id.startsWith("kwaivgi/kling-v3.0-") }
        assertTrue(kling.size > 4)
        val crun = kling.first().copy(id="crun/kling-v3-std",providerModelId="kling-v3-std",providerId="crun")
        val groups = catalogModelGroups(kling + crun)
        val titles = groups.map { it.title }
        assertTrue(titles.toString(), "Kling 3 Pro" in titles && "Kling 3 Std" in titles)
        // The same tier on another provider joins its card instead of making a duplicate.
        assertTrue(groups.first { it.title == "Kling 3 Std" }.routes.any { it.providerId == "crun" })
        assertEquals((kling + crun).map(::modelBookmark).toSet(), groups.flatMap { it.routes }.map(::modelBookmark).toSet())
    }
    @Test fun hierarchyKeepsO3SeparateFromKling3AndKeepsAllRoutes() {
        val groups = catalogModelGroups(media)
        assertTrue(groups.any {it.title.startsWith("Kling O3")})
        assertTrue(groups.any {it.title == "Kling 3"})
        assertEquals(media.map(::modelBookmark).toSet(), groups.flatMap {it.routes}.map(::modelBookmark).toSet())
        assertEquals(groups.size, groups.map {it.key}.distinct().size)
    }
    @Test fun qwenGenerationAndEditingHaveOneModelCard() {
        val qwen = media.filter {it.id.startsWith("qwen-image-3.0")}
        assertEquals(4, qwen.size)
        // Base and Pro are two products; each keeps its own generation and editing routes together.
        assertEquals(setOf("Qwen Image 3","Qwen Image 3 Pro"), catalogModelGroups(qwen).map { it.title }.toSet())
        assertTrue(catalogModelGroups(qwen).all { it.routes.size == 2 })
    }
    @Test fun fullLiveCatalogRetainsEveryMediaRouteInHierarchy() {
        assertEquals(384, media.size)
        val flattened = catalogSections(media, emptySet()).flatMap { it.models }
        assertEquals(media.map(::modelBookmark).toSet(), flattened.map(::modelBookmark).toSet())
        assertEquals(media.size, flattened.size)
    }
    @Test fun siblingVersionsStayInTheSameFamily() {
        val sample = media.first()
        assertEquals("Kling", catalogFamily(sample.copy(id="kwaivgi/kling-v3.0-pro/text-to-video")))
        assertEquals("Kling", catalogFamily(sample.copy(id="crun/kling-v2-std")))
        assertEquals("Wan", catalogFamily(sample.copy(id="alibaba/wan-3.0-prime/reference-to-video")))
        assertEquals("Qwen Image", catalogFamily(sample.copy(id="qwen-image-3.0/edit")))
    }
    @Test fun everyModelGetsSpanishPurposeStrengthAndSource() {
        media.forEach {
            val guide = modelGuide(it)
            assertTrue(it.id, guide.purpose.isNotBlank())
            assertTrue(it.id, guide.strength.isNotBlank())
            assertEquals("https://www.atlascloud.ai/models/${it.id}", guide.source)
        }
    }
    @Test fun identityClaimRequiresEvidenceInProviderProfile() {
        val sample = media.first().copy(id="example/pro/edit", description="", supportsLastFrame=false)
        assertFalse(modelGuide(sample).strength.contains("identidad"))
        val qwen = media.first { it.id == "qwen-image-3.0-pro/edit" }
        assertTrue(modelGuide(qwen).strength.contains("identidad"))
        assertTrue(modelGuide(qwen).note.contains("falta evaluar"))
    }
    @Test fun layersAndVideoToImageAreNotDescribedAsGenericGeneration() {
        assertTrue(modelGuide(media.first { "layer-decomposition" in it.id }).purpose.contains("capas"))
        assertTrue(modelGuide(media.first { "reference-to-image" in it.id }).purpose.contains("vídeo"))
    }
    @Test fun officialPriceSnapshotCovers383RoutesWithoutInventingUnits() {
        AtlasPublishedPrice.seedSnapshot(javaClass.getResource("/native/atlas-published-prices.json")!!.readText())
        assertEquals(383, media.count { AtlasPublishedPrice.cached(it) != null })
        assertTrue(media.filter { AtlasPublishedPrice.cached(it) != null }.all { AtlasPublishedPrice.cached(it)!!.baseTariff })
    }
}
