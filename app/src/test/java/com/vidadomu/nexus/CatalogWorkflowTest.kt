package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.AtlasModelMapper
import com.vidadomu.nexus.ui.models.*
import org.junit.Assert.*
import org.junit.Test

class CatalogWorkflowTest {
    private val seedream=AtlasModelMapper.parseModelsResponse(javaClass.getResource("/native/catalog-public.json")!!.readText())
        .filter {it.id.startsWith("bytedance/seedream-v5.0")}
    @Test fun seedreamHasThreeDistinctTasksAndRetainsEveryNativeRoute() {
        val group=CatalogModelGroup("seedream-5","Seedream 5",seedream)
        val workflows=workflowRoutes(group)
        assertEquals(setOf(CatalogWorkflow.CREATE_IMAGE,CatalogWorkflow.EDIT_IMAGE,CatalogWorkflow.LAYERS),workflows.keys)
        assertEquals(seedream.map(::modelBookmark).toSet(),workflows.values.flatten().map(::modelBookmark).toSet())
        assertEquals(1,workflows.getValue(CatalogWorkflow.LAYERS).size)
        assertTrue(workflows.getValue(CatalogWorkflow.CREATE_IMAGE).any {it.id.endsWith("/sequential")})
    }
    @Test fun providerNamesAndOldInputFlagsCannotDuplicateTextToImageTask() {
        val text=seedream.first {it.id.endsWith("/text-to-image")}
        val alternate=text.copy(id="other/t2i",providerId="other",inputMode=ModelInputMode.MULTI_IMAGE,
            supportsLastFrame=true,displayName="Imagen desde texto")
        assertEquals(listOf(CatalogWorkflow.CREATE_IMAGE),catalogWorkflows(alternate))
        val group=CatalogModelGroup("test","Seedream 5",listOf(text,alternate))
        assertEquals(1,workflowRoutes(group).size)
    }
    @Test fun oneOptionalReferenceEndpointCanServeBothIntentsWithoutInventingIds() {
        val shared=seedream.first().copy(id="provider/shared",capabilities=listOf(ModelCapability.TEXT_TO_IMAGE,ModelCapability.IMAGE_TO_IMAGE),
            isMediaRequired=false,inputMode=ModelInputMode.MULTI_IMAGE)
        val workflows=workflowRoutes(CatalogModelGroup("shared","Shared",listOf(shared)))
        assertEquals(setOf(CatalogWorkflow.CREATE_IMAGE,CatalogWorkflow.EDIT_IMAGE),workflows.keys)
        assertTrue(workflows.values.all {it.single().id==shared.id})
        assertEquals(listOf(CatalogWorkflow.EDIT_IMAGE),catalogWorkflows(shared.copy(isMediaRequired=true)))
    }
    @Test fun motionRemainsSeparateFromVideoGenerationAndImageEditing() {
        val motion=seedream.first().copy(id="opaque/native-model",modalities=listOf(Modality.VIDEO),
            capabilities=listOf(ModelCapability.MOTION_CONTROL),inputMode=ModelInputMode.MULTI_MODAL)
        assertEquals(listOf(CatalogWorkflow.MOTION),catalogWorkflows(motion))
        val image=seedream.first {it.id.endsWith("/edit")}.copy(supportsLastFrame=true,lastFrameField="end_image")
        assertEquals(listOf(CatalogWorkflow.EDIT_IMAGE),catalogWorkflows(image))
    }
    @Test fun variantLabelsRemoveRepeatedFamilyAndKeepBatchDifference() {
        val pro=seedream.first {it.id.endsWith("pro/edit")}
        assertEquals("Pro",optionVariantLabel(pro,"Seedream 5"))
        val batch=seedream.first {it.id.endsWith("lite/edit-sequential")}
        assertEquals("Lite · Serie de imágenes",optionVariantLabel(batch,"Seedream 5"))
    }
    @Test fun oldEditingChoiceMigratesAndNewStableChoiceWins() {
        val group=CatalogModelGroup("seedream-5","Seedream 5",seedream)
        assertEquals(CatalogWorkflow.EDIT_IMAGE,restoredWorkflow(group,null,"Con referencias"))
        assertEquals(CatalogWorkflow.CREATE_IMAGE,restoredWorkflow(group,null,"Imagen desde texto"))
        assertEquals(CatalogWorkflow.LAYERS,restoredWorkflow(group,"LAYERS","Con referencias"))
        assertEquals(CatalogWorkflow.EDIT_IMAGE,restoredWorkflow(group,"missing","removed task"))
    }
    @Test fun localizedRouteDescriptionIsRemovedAsAWholeWithoutCuttingWords() {
        val route=seedream.first().copy(id="runway/seedream5-pro",displayName="Seedream5 Pro · Crear y editar imagen")
        assertEquals("Pro",optionVariantLabel(route,"Seedream 5"))
        assertEquals("Credit Plus",optionVariantLabel(route.copy(displayName="Seedream5 Credit Plus"),"Seedream 5"))
    }
}
