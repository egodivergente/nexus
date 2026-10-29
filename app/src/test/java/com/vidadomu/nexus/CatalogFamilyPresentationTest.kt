package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.models.*
import org.junit.Assert.*
import org.junit.Test

class CatalogFamilyPresentationTest {
    private fun model(id:String="bytedance/seedance-2.0/text-to-video",provider:String="atlas",
        modalities:List<Modality> = listOf(Modality.VIDEO),
        capabilities:List<ModelCapability> = listOf(ModelCapability.TEXT_TO_VIDEO))=NexusModel(
        id=id,displayName=id,providerId=provider,organization="ByteDance",modalities=modalities,
        capabilities=capabilities,parameters=emptyList())
    private fun group(title:String,vararg routes:NexusModel)=CatalogModelGroup("group:$title",title,routes.toList())

    @Test fun shortTitlePreservesVersionsTiersAndDoesNotInventStandard() {
        val route=model()
        assertEquals("2.5",catalogVariantTitle(group("Seedance 2.5",route)))
        assertEquals("2 Mini",catalogVariantTitle(group("Seedance 2 Mini",route)))
        assertEquals("2 Fast",catalogVariantTitle(group("Seedance 2 Fast",route)))
        assertEquals("2",catalogVariantTitle(group("Seedance 2",route)))
        assertEquals("Seedance",catalogVariantTitle(group("Seedance",route)))
    }

    @Test fun titleOnlyRemovesExactFamilyPrefixAndKeepsUnknownProducts() {
        val route=model()
        assertEquals("seedance 2 Mini",catalogVariantTitle(group("seedance 2 Mini",route)))
        assertEquals("My Seedance 2",catalogVariantTitle(group("My Seedance 2",route)))
        assertEquals("SeedanceCustom",catalogVariantTitle(group("SeedanceCustom",route)))
        assertEquals("Original",catalogVariantTitle(group("Original")))
    }

    @Test fun currentVariantUsesExactProviderAndModelEvenAfterSchemaEnrichment() {
        val atlas=model()
        val crun=atlas.copy(providerId="crun")
        val atlasGroup=group("Seedance 2",atlas)
        val crunGroup=group("Seedance 2 Mini",crun)
        assertSame(crunGroup,catalogFamilyVariantForModel(listOf(atlasGroup,crunGroup),crun.copy(description="schema loaded")))
        assertNull(catalogFamilyVariantForModel(listOf(atlasGroup),crun))
        assertNull(catalogFamilyVariantForModel(listOf(atlasGroup),null))
        assertNull(catalogFamilyVariantForModel(listOf(atlasGroup),atlas.copy(id="missing")))
    }

    @Test fun duplicatedMembershipDoesNotChooseAnArbitraryVariant() {
        val route=model()
        assertNull(catalogFamilyVariantForModel(listOf(group("A",route),group("B",route)),route))
    }

    @Test fun uniqueContinuationKeepsNativeProviderModalitiesAndWorkflow() {
        val current=model()
        val next=model("bytedance/seedance-2.0-mini/text-to-video")
        val foreign=next.copy(providerId="crun")
        val animate=model("bytedance/seedance-2.0-mini/image-to-video",capabilities=listOf(ModelCapability.IMAGE_TO_VIDEO))
        val otherModality=next.copy(id="other/modality",modalities=listOf(Modality.VIDEO,Modality.IMAGE))
        assertSame(next,catalogVariantContinuation(group("Seedance 2 Mini",foreign,animate,otherModality,next),current))
    }

    @Test fun continuationRequiresWholeWorkflowSetRatherThanOneSharedTask() {
        val current=model("qwen/qwen-image-3.0",modalities=listOf(Modality.IMAGE),
            capabilities=listOf(ModelCapability.TEXT_TO_IMAGE,ModelCapability.IMAGE_TO_IMAGE))
        val textOnly=current.copy(id="qwen/qwen-image-3.0-pro/text-to-image",capabilities=listOf(ModelCapability.TEXT_TO_IMAGE))
        assertNull(catalogVariantContinuation(group("Qwen Image 3 Pro",textOnly),current))
    }

    @Test fun modalityOrderDoesNotCreateFalseAmbiguityOrDropAModality() {
        val current=model(modalities=listOf(Modality.VIDEO,Modality.IMAGE))
        val target=current.copy(id="bytedance/seedance-2.5/text-to-video",modalities=listOf(Modality.IMAGE,Modality.VIDEO))
        assertSame(target,catalogVariantContinuation(group("Seedance 2.5",target),current))
        assertNull(catalogVariantContinuation(group("Seedance 2.5",target.copy(modalities=listOf(Modality.VIDEO))),current))
    }

    @Test fun twoCompatibleNativeRoutesRemainAnExplicitChoice() {
        val first=model("bytedance/seedance-2.0-mini/text-to-video")
        val second=first.copy(id="bytedance/seedance-2.0-mini/text-to-video-4k",contractEndpoint="different/native/route")
        assertNull(catalogVariantContinuation(group("Seedance 2 Mini",first,second),model()))
    }

    @Test fun noCurrentModelOnlyAllowsOneRealRouteAndNeverAProviderFallback() {
        val route=model()
        assertSame(route,catalogVariantContinuation(group("Seedance 2",route),null))
        assertNull(catalogVariantContinuation(group("Seedance 2",route,route.copy(providerId="crun")),null))
        assertNull(catalogVariantContinuation(group("Empty"),null))
        assertNull(catalogVariantContinuation(group("Seedance 2",route.copy(providerId="crun")),route))
    }

    @Test fun sharedUpstreamAliasDoesNotMergeTextAndVideoEditingLabels() {
        val text=model("runway/seedance2_5/t2v","runway").copy(providerModelId="seedance2_5",
            displayName="Seedance2 5 · Crear vídeo",inputMode=ModelInputMode.TEXT_ONLY)
        val video=text.copy(id="runway/seedance2_5/v2v",displayName="Seedance2 5 · Editar vídeo")
        val product=group("Seedance 2.5",text,video)
        assertEquals("Desde texto",catalogRouteTitle(text,product))
        assertEquals("Editar vídeo",catalogRouteTitle(video,product))
        assertNotEquals(catalogRouteTitle(text,product),catalogRouteTitle(video,product))
    }

    @Test fun repeatedFamilyAndWorkflowDoNotAddFragmentsToTheRouteTitle() {
        val image=model("runway/seedance2_5/i2v","runway",capabilities=listOf(ModelCapability.IMAGE_TO_VIDEO))
            .copy(providerModelId="seedance2_5",displayName="Seedance2 5 · Animar imagen")
        assertEquals("Animar imagen",catalogRouteTitle(image,group("Seedance 2.5",image)))
        val reference=model("bytedance/seedance-2.5/reference-to-video",capabilities=listOf(ModelCapability.REFERENCE_TO_VIDEO))
            .copy(displayName="Seedance 2.5 Seedance")
        assertEquals("Con referencias",catalogRouteTitle(reference,group("Seedance 2.5",reference)))
    }

    @Test fun exactResolutionVariantsRemainNamed() {
        val small=model("bytedance/seedance-v1-pro/image-to-video-720p",capabilities=listOf(ModelCapability.IMAGE_TO_VIDEO))
            .copy(displayName="Seedance 1 Pro 720p")
        val large=small.copy(id="bytedance/seedance-v1-pro/image-to-video-1080p",displayName="Seedance 1 Pro 1080p")
        val product=group("Seedance 1 Pro",small,large)
        assertTrue(catalogRouteTitle(small,product).contains("720p"))
        assertTrue(catalogRouteTitle(large,product).contains("1080p"))
        assertNotEquals(catalogRouteTitle(small,product),catalogRouteTitle(large,product))
    }
}
