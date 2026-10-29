package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.core.model.*
import org.junit.Assert.*
import org.junit.Test

class CatalogFamilyNavigationTest {
    private fun model(id:String,family:String)=NexusModel(id,"$family ${id.substringAfter("-v").substringBefore('/')}","fixture",
        organization="Fixture",modelFamily=family,modalities=listOf(Modality.VIDEO),
        capabilities=listOf(ModelCapability.TEXT_TO_VIDEO),parameters=emptyList())
    private val catalog=listOf(model("orbit-v1/text-to-video","Orbit"),model("orbit-v2/text-to-video","Orbit"),
        model("vela-v1/text-to-video","Vela"))

    @Test fun searchShowsOneFamilyWithoutRemovingItsMatchingVersionRoutes() {
        val visible=browseCatalog(catalog,Modality.VIDEO,null,"Orbit",null,false,emptySet(),CatalogOrder.PRIORITY)
        assertTrue(catalogAtSectionLevel(null,"Orbit",false,false,false))
        val sections=catalogSections(visible,emptySet())
        assertEquals(listOf("Orbit"),sections.map {it.family})
        assertEquals(catalog.take(2).map(::modelBookmark).toSet(),sections.single().models.map(::modelBookmark).toSet())
        assertEquals(2,catalogModelGroups(sections.single().models).size)
    }

    @Test fun favoritesShowOneFamilyAndKeepOnlyTheActualFavoriteRoutes() {
        val favorites=catalog.take(2).map(::modelBookmark).toSet()
        val visible=browseCatalog(catalog,Modality.VIDEO,null,"",null,true,favorites,CatalogOrder.PRIORITY)
        assertTrue(catalogAtSectionLevel(null,"",true,false,false))
        val sections=catalogSections(visible,favorites)
        assertEquals(1,sections.size)
        assertEquals("Orbit",sections.single().family)
        assertEquals(favorites,sections.single().models.map(::modelBookmark).toSet())
        assertFalse(sections.flatMap {it.models}.any {it.id.startsWith("vela")})
    }

    @Test fun toolsAndPersonalizationKeepTheLegacyHierarchyForEveryFilterCombination() {
        for(query in listOf("","Orbit")) for(favoritesOnly in listOf(false,true)) {
            val legacy=catalogAtRoot(null,query,favoritesOnly)
            assertEquals(legacy,catalogAtSectionLevel(null,query,favoritesOnly,toolsView=true,personalizing=false))
            assertEquals(legacy,catalogAtSectionLevel(null,query,favoritesOnly,toolsView=false,personalizing=true))
            assertTrue(catalogAtSectionLevel(null,query,favoritesOnly,toolsView=false,personalizing=false))
        }
    }

    @Test fun anOpenedFamilyAndAnEmptyFilteredResultDoNotReintroduceOtherModels() {
        assertFalse(catalogAtSectionLevel("Orbit","Orbit",true,false,false))
        val favorites=setOf(modelBookmark(catalog.last()))
        val visible=browseCatalog(catalog,Modality.VIDEO,null,"Orbit",null,true,favorites,CatalogOrder.PRIORITY)
        assertTrue(visible.isEmpty())
        assertTrue(catalogSections(visible,favorites).isEmpty())
        assertTrue(catalogAtSectionLevel(null,"Orbit",true,false,false))
    }
}
