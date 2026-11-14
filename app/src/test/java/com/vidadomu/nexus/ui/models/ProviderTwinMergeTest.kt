package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.provider.atlas.AtlasModelMapper
import org.junit.Assert.*
import org.junit.Test

/** Vidal 01-11: «MiniMax H3» and «Video» were the same model on Atlas Cloud and Crun; one card with a provider choice. */
class ProviderTwinMergeTest {
    private val media = AtlasModelMapper.parseModelsResponse(javaClass.getResource("/native/catalog-public.json")!!.readText())
        .filter { Modality.IMAGE in it.modalities || Modality.VIDEO in it.modalities }
    private val atlasH3 = media.filter { it.id.startsWith("minimax/h3") }
    private fun atlas(id: String) = atlasH3.first { it.id == id }
    private fun crun(nativeId: String) = atlas("minimax/h3/image-to-video")
        .copy(id = "crun/$nativeId", providerModelId = nativeId, displayName = "MiniMax H3 Video", providerId = "crun")

    @Test fun atlasAndCrunH3ShareOneProviderNeutralStem() {
        assertEquals(setOf("minimaxh3"), providerNeutralStems(atlas("minimax/h3/image-to-video")))
        assertEquals(setOf("minimaxh3"), providerNeutralStems(crun("minimax/h3-i2v")))
        assertFalse("minimaxh3" in providerNeutralStems(atlas("minimax/h3-developer/image-to-video")))
    }

    @Test fun crunH3JoinsTheAtlasCardWithoutLosingRoutes() {
        val routes = atlasH3 + crun("minimax/h3-i2v") + crun("minimax/h3-r2v")
        val groups = catalogModelGroups(routes)
        val h3 = groups.single { g -> g.routes.any { it.id == "minimax/h3/image-to-video" } }
        assertTrue(h3.routes.map { it.id }.toString(), h3.routes.count { it.providerId == "crun" } == 2)
        // The merge adds the Crun routes and nothing else: the Atlas side is exactly the card Atlas had on its own.
        val atlasOnly = catalogModelGroups(atlasH3).single { g -> g.routes.any { it.id == "minimax/h3/image-to-video" } }
        assertEquals(atlasOnly.routes.map { it.id }.toSet(), h3.routes.filter { it.providerId != "crun" }.map { it.id }.toSet())
        // Developer and Max keep their own cards. Fast is not listed: computeGroupTitle drops "Fast" from a title without a
        // version, so on this unfiltered catalog H3 Fast lands in the base card. The app never shows that card:
        // CuratedVideoSelection leaves H3 Fast out of what is offered (Vidal's video list of 01-11; confirmed 14-11).
        assertFalse(h3.routes.any { listOf("h3-developer", "h3-max").any { tier -> tier in it.id } })
        assertEquals(routes.map(::modelBookmark).toSet(), groups.flatMap { it.routes }.map(::modelBookmark).toSet())
        assertEquals(groups.size, groups.map { it.key }.distinct().size)
    }

    @Test fun twinClustersPairProvidersButNeverTwoRoutesOfOneProvider() {
        val i2v = atlas("minimax/h3/image-to-video")
        assertEquals(1, providerTwinClusters(listOf(i2v, crun("minimax/h3-i2v"))).size)
        assertEquals(2, providerTwinClusters(listOf(i2v, atlas("minimax/h3/reference-to-video"))).size)
    }

    @Test fun aLoneVideoWordIsTheStandardVariant() {
        assertEquals("Estándar", optionVariantLabel(crun("minimax/h3-i2v"), "MiniMax H3"))
    }
}
