package com.vidadomu.nexus

import com.vidadomu.nexus.data.repository.*
import com.vidadomu.nexus.provider.atlas.AtlasModelMapper
import com.vidadomu.nexus.ui.models.*
import org.junit.Assert.*
import org.junit.Test

class CatalogPersonalizationTest {
    private val models = AtlasModelMapper.parseModelsResponse(javaClass.getResource("/native/catalog-public.json")!!.readText())
    private val kling = models.first {it.id=="kwaivgi/kling-v3.0-pro/image-to-video"}
    @Test fun familyRuleHidesFutureModelsWithoutRemovingOtherFamilies() {
        val hidden=setOf(familyVisibilityKey("Kling"))
        assertFalse(isCatalogModelVisible(kling.copy(id="kwaivgi/kling-v9.0-pro/image-to-video"),hidden))
        assertTrue(isCatalogModelVisible(models.first {"seedance" in it.id},hidden))
    }
    @Test fun modelRuleHidesAllItsVariantsAcrossProvidersButNotAnotherVersion() {
        val hidden=setOf(groupVisibilityKey(kling))
        assertFalse(isCatalogModelVisible(kling.copy(id="crun/kling-v3-std",providerModelId="kling-v3-std",providerId="crun"),hidden))
        assertTrue(isCatalogModelVisible(models.first {it.id.startsWith("kwaivgi/kling-v2.6")},hidden))
    }
    @Test fun hidingSingleRouteKeepsOtherProviderAndRestoringParentRespectsChildRule() {
        val child=routeVisibilityKey(kling)
        val parent=familyVisibilityKey("Kling")
        assertFalse(isCatalogModelVisible(kling,setOf(parent,child)-parent))
        assertTrue(isCatalogModelVisible(kling.copy(providerId="crun"),setOf(child)))
        assertTrue(isCatalogModelVisible(kling,emptySet()))
    }
    @Test fun movingUsesKeysAndKeepsHiddenPositions() {
        assertEquals(listOf("C","hidden","A","B"),moveCatalogItem(listOf("A","hidden","B","C"),listOf("A","B","C"),"C","A"))
        assertEquals(listOf("B","C","A"),moveCatalogItem(emptyList(),listOf("A","B","C"),"A","C"))
        assertEquals(emptyList<String>(),moveCatalogItem(emptyList(),listOf("A","B"),"header","B"))
    }
}
