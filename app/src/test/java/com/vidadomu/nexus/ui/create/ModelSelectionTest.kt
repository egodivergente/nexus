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

    @Test fun contractReferencesCrossNativeAndAnotherContractWithoutChangingFiles() {
        val source=model("source").copy(contractEndpoint="/source",imagesArrayField="source_refs",maxImages=3,
            contractMedia=listOf(ContractMediaField("source_refs","References","image/*",maximum=3,encoding="array")))
        val native=model("native").copy(imagesArrayField="images",maxImages=3)
        val target=model("target").copy(contractEndpoint="/target",imagesArrayField="target_refs",maxImages=3,
            contractMedia=listOf(ContractMediaField("target_refs","References","image/*",maximum=3,encoding="array")))
        val originals=(1..3).map {image("$it").copy(fieldKey="source_refs",slotType=MediaSlotType.REFERENCE_IMAGE,
            remoteUrl="https://fixture.invalid/$it.png")}
        val nativeMedia=compatibleModelMedia(native,canonicalModelMedia(source,originals))
        assertEquals(originals.map {it.id},nativeMedia.map {it.id})
        assertTrue(nativeMedia.all {it.fieldKey==null && it.slotType==MediaSlotType.REFERENCE_IMAGE})
        val targetMedia=compatibleModelMedia(target,canonicalModelMedia(native,nativeMedia))
        assertEquals(originals.map {it.id},targetMedia.map {it.id})
        assertEquals(originals.map {it.uriString},targetMedia.map {it.uriString})
        assertEquals(originals.map {it.remoteUrl},targetMedia.map {it.remoteUrl})
        assertTrue(targetMedia.all {it.fieldKey=="target_refs"})
        assertTrue(originals.all {it.fieldKey=="source_refs"})
    }

    @Test fun foreignNamedFieldsStayStrictWithoutTheSourceContext() {
        val native=model().copy(firstFrameField="image")
        val foreign=image().copy(fieldKey="image")
        assertTrue(compatibleModelMedia(native,listOf(foreign)).isEmpty())
        val target=model("target").copy(contractEndpoint="/target",
            contractMedia=listOf(ContractMediaField("destination","Image","image/*")))
        assertTrue(compatibleModelMedia(target,listOf(foreign)).isEmpty())
    }

    @Test fun finalFrameKeepsItsRoleAcrossDifferentContractKeys() {
        val source=model("source").copy(contractEndpoint="/source",lastFrameField="source_tail",
            contractMedia=listOf(ContractMediaField("source_tail","Final","image/*")))
        val final=image("final").copy(fieldKey="source_tail")
        val canonical=canonicalModelMedia(source,listOf(final)).single()
        assertEquals(MediaSlotType.LAST_FRAME,canonical.slotType)
        assertTrue(compatibleModelMedia(model().copy(firstFrameField="initial"),listOf(canonical)).isEmpty())
        val target=model("target").copy(contractEndpoint="/target",firstFrameField="target_head",lastFrameField="target_tail",
            contractMedia=listOf(ContractMediaField("target_head","Initial","image/*"),ContractMediaField("target_tail","Final","image/*")))
        val routed=compatibleModelMedia(target,listOf(canonical)).single()
        assertEquals("target_tail",routed.fieldKey)
        assertEquals(MediaSlotType.LAST_FRAME,routed.slotType)
        assertEquals(final.id,routed.id)
    }

    @Test fun masksAndAmbiguousImagesAreNotReinterpretedAsOrdinaryPhotos() {
        val masked=model("mask-source").copy(contractEndpoint="/mask-source",
            parameters=listOf(ModelParameter("matte","Mask",ParameterType.MASK)),
            contractMedia=listOf(ContractMediaField("matte","Mask","image/*"),ContractMediaField("maskImage","Mask","image/*")))
        val masks=listOf(image("typed-mask").copy(fieldKey="matte"),image("named-mask").copy(fieldKey="maskImage"))
        assertEquals(masks.map {it.fieldKey},canonicalModelMedia(masked,masks).map {it.fieldKey})
        assertTrue(canonicalModelMedia(masked,masks).all {it.bindingOwner?.modelId==masked.id})
        assertTrue(compatibleModelMedia(model().copy(firstFrameField="image"),canonicalModelMedia(masked,masks)).isEmpty())
        val ambiguous=model("ambiguous").copy(contractEndpoint="/ambiguous",
            contractMedia=listOf(ContractMediaField("subject","Subject","image/*"),ContractMediaField("control","Control","image/*")))
        val inputs=listOf(image("subject").copy(fieldKey="subject"),image("control").copy(fieldKey="control"))
        assertEquals(inputs.map {it.fieldKey},canonicalModelMedia(ambiguous,inputs).map {it.fieldKey})
        assertTrue(canonicalModelMedia(ambiguous,inputs).all {it.bindingOwner?.modelId==ambiguous.id})
        val targetMask=model("mask-target").copy(contractEndpoint="/mask-target",
            contractMedia=listOf(ContractMediaField("mask_image","Mask","image/*")))
        assertTrue(compatibleModelMedia(targetMask,listOf(image())).isEmpty())
    }

    @Test fun contractRoutingEnforcesMimeAndCapacityBeforeReturningCompatibleMedia() {
        val target=model().copy(contractEndpoint="/target",imagesArrayField="references",maxImages=1,
            contractMedia=listOf(ContractMediaField("references","References","image/*",maximum=1,encoding="array")))
        val valid=image("valid").copy(fieldKey="references")
        val wrong=image("wrong").copy(fieldKey="references",mimeType="video/mp4")
        val extra=image("extra").copy(fieldKey="references")
        assertEquals(listOf(valid),compatibleModelMedia(target,listOf(wrong,valid,extra)))
        assertFalse(retainsAllReferences(target,listOf(valid,extra)))
    }

    @Test fun videoRoleAndMimeTransferBetweenDeclaredContractFields() {
        val source=model("source",Modality.VIDEO).copy(contractEndpoint="/source",videoField="source_clip",
            contractMedia=listOf(ContractMediaField("source_clip","Video","video/*")))
        val clip=image("clip").copy(fieldKey="source_clip",mimeType="video/mp4",slotType=MediaSlotType.REFERENCE_IMAGE)
        val target=model("target",Modality.VIDEO).copy(contractEndpoint="/target",videoField="target_clip",
            contractMedia=listOf(ContractMediaField("target_clip","Video","video/*")))
        val routed=compatibleModelMedia(target,canonicalModelMedia(source,listOf(clip))).single()
        assertEquals("target_clip",routed.fieldKey)
        assertEquals(MediaSlotType.VIDEO_INPUT,routed.slotType)
        assertEquals(clip.uriString,routed.uriString)
    }

    @Test fun pluralMaskArraysCannotBecomeReferenceImages() {
        val source=model("source").copy(contractEndpoint="/source",
            contractMedia=listOf(ContractMediaField("masks","Masks","image/*",maximum=3,encoding="array")))
        val masks=listOf(image("mask").copy(fieldKey="masks"))
        assertEquals(masks.map {it.fieldKey},canonicalModelMedia(source,masks).map {it.fieldKey})
        assertTrue(canonicalModelMedia(source,masks).all {it.bindingOwner?.modelId==source.id})
        assertTrue(compatibleModelMedia(model().copy(imagesArrayField="images",maxImages=3),canonicalModelMedia(source,masks)).isEmpty())
        assertTrue(compatibleModelMedia(source,listOf(image())).isEmpty())
    }

    @Test fun legacyBoundInitialFrameSatisfiesTheRoleBeforeAssigningTheFinalFrame() {
        val target=model("target").copy(contractEndpoint="/target",firstFrameField="head",lastFrameField="tail",
            contractMedia=listOf(ContractMediaField("head","Initial","image/*"),ContractMediaField("tail","Final","image/*")))
        val legacy=image("head").copy(fieldKey="head",slotType=MediaSlotType.REFERENCE_IMAGE)
        val canonical=image("tail").copy(slotType=MediaSlotType.REFERENCE_IMAGE)
        val routed=compatibleModelMedia(target,listOf(legacy,canonical))
        assertEquals(legacy,routed.first())
        assertEquals("tail",routed.last().fieldKey)
        assertEquals(MediaSlotType.LAST_FRAME,routed.last().slotType)
    }

    @Test fun sameKeyMaskCollisionIsRetainedUntilReturningToItsBindingOwner() {
        val source=model("source").copy(contractEndpoint="/source",parameters=listOf(ModelParameter("input","Mask",ParameterType.MASK)),
            contractMedia=listOf(ContractMediaField("input","Mask","image/*")))
        val target=model("target").copy(contractEndpoint="/target",
            contractMedia=listOf(ContractMediaField("input","Photo","image/*")))
        val original=image("mask").copy(fieldKey="input")
        val protected=canonicalModelMedia(source,listOf(original)).single()
        assertEquals(original.fieldKey,protected.fieldKey)
        assertEquals(original.id,protected.id)
        assertEquals(original.uriString,protected.uriString)
        assertEquals(MediaBindingOwner(source.providerId,source.id,"/source"),protected.bindingOwner)
        assertTrue(compatibleModelMedia(target,listOf(protected)).isEmpty())
        assertEquals(listOf(protected),compatibleModelMedia(source,listOf(protected)))
        assertEquals(protected,canonicalModelMedia(target,listOf(protected)).single())
    }

    @Test fun ambiguousSameKeyCollisionAndEndpointChangeDoNotReleaseOwnership() {
        val source=model("same").copy(contractEndpoint="/source",
            contractMedia=listOf(ContractMediaField("input","Subject","image/*"),ContractMediaField("control","Control","image/*")))
        val target=source.copy(contractEndpoint="/target",contractMedia=listOf(ContractMediaField("input","Photo","image/*")))
        val protected=canonicalModelMedia(source,listOf(image().copy(fieldKey="input"))).single()
        assertTrue(compatibleModelMedia(target,listOf(protected)).isEmpty())
        assertTrue(compatibleModelMedia(source.copy(providerId="other"),listOf(protected)).isEmpty())
        assertEquals(listOf(protected),compatibleModelMedia(source,listOf(protected)))
    }
}
