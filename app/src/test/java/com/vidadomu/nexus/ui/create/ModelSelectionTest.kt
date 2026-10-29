package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ModelSelectionTest {
    private fun model(id:String="still",modality:Modality=Modality.IMAGE)=NexusModel(id,id,"test",organization="Estudio",
        modalities=listOf(modality),capabilities=emptyList(),parameters=emptyList())
    private fun image(id:String="reference")=AttachedMedia(id,"file:///fixture/$id.png","image/png",MediaSlotType.PRIMARY_IMAGE)

    @Test fun startsWithCurrentModalityNotTheEntireCatalog() {
        val still=model(); val movie=model("movie",Modality.VIDEO); val chat=model("chat",Modality.CHAT)
        assertEquals(listOf(still),filterCanvasModels(listOf(still,movie,chat),Modality.IMAGE,emptyList(),false,null,""))
    }
    @Test fun photoExcludesTextOnlyAndAllCatalogIsRecoverable() {
        val text=model(); val edit=model("edit").copy(firstFrameField="image",supportsImageInput=true)
        assertEquals(listOf(edit),filterCanvasModels(listOf(text,edit),Modality.IMAGE,listOf(image()),true,null,""))
        assertEquals(listOf(text,edit),filterCanvasModels(listOf(text,edit),null,listOf(image()),false,null,""))
    }
    @Test fun mustRetainEveryReferenceNotOnlyOne() {
        val single=model().copy(firstFrameField="image")
        assertFalse(retainsAllReferences(single,listOf(image("one"),image("two"))))
        assertTrue(retainsAllReferences(single.copy(lastFrameField="last"),listOf(image("one"),image("two"))))
    }
    @Test fun routesDoNotMutateReferenceIdentityOrFile() {
        val original=image();val mapped=compatibleModelMedia(model().copy(imagesArrayField="images",maxImages=2),listOf(original)).single()
        assertEquals(original.id,mapped.id); assertEquals(original.uriString,mapped.uriString)
        assertEquals(MediaSlotType.REFERENCE_IMAGE,mapped.slotType); assertEquals(MediaSlotType.PRIMARY_IMAGE,original.slotType)
    }
    @Test fun unknownMimeIsNotACompatibleImage() {
        assertFalse(retainsAllReferences(model().copy(firstFrameField="image"),listOf(image().copy(mimeType="application/octet-stream"))))
    }
    @Test fun contractRequiresMatchingFieldMimeAndCapacity() {
        val m=model().copy(contractEndpoint="/fixture",contractMedia=listOf(ContractMediaField("input","Entrada","image/*",maximum=1)))
        assertTrue(retainsAllReferences(m,listOf(image().copy(fieldKey="input"))))
        assertFalse(retainsAllReferences(m,listOf(image().copy(fieldKey="other"))))
        assertFalse(retainsAllReferences(m,listOf(image().copy(fieldKey="input",mimeType="video/mp4"))))
        assertFalse(retainsAllReferences(m,listOf(image("a").copy(fieldKey="input"),image("b").copy(fieldKey="input"))))
    }
    @Test fun multiwordSearchCombinesNameAndOrganization() {
        val m=model("Kling prueba"); assertEquals(listOf(m),filterCanvasModels(listOf(m),null,emptyList(),false,"ESTUDIO","kling estudio"))
        assertTrue(filterCanvasModels(listOf(m),null,emptyList(),false,null,"imposible").isEmpty())
    }
    @Test fun videoRouteAcceptsVideoButNotAnUnrelatedImage() {
        val m=model("extend",Modality.VIDEO).copy(videoField="video")
        val clip=image().copy(mimeType="video/mp4",slotType=MediaSlotType.VIDEO_INPUT)
        assertTrue(retainsAllReferences(m,listOf(clip)))
        assertFalse(retainsAllReferences(m,listOf(clip,image("still"))))
    }
    @Test fun loneFinalFrameDoesNotSilentlyBecomeTheInitialFrame() {
        val final=image().copy(slotType=MediaSlotType.LAST_FRAME)
        val mapped=compatibleModelMedia(model().copy(firstFrameField="first",lastFrameField="last"),listOf(final))
        assertEquals(listOf(final),mapped)
    }
}
