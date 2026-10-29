package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ComposerMediaStateTest {
    private fun model(id:String)=NexusModel(id,id,"fixture",organization="Fixture",modalities=listOf(Modality.VIDEO),
        capabilities=emptyList(),parameters=emptyList())
    private fun image(id:String)=AttachedMedia(id,"file:///$id.png","image/png",MediaSlotType.REFERENCE_IMAGE)

    @Test fun incompatibleSwitchParksEveryFileAndKeepsTheFullPrompt() {
        val prompt="🎨 cámara ".repeat(1000)
        val refs=(1..13).map {image("$it")}
        val original=CreateUiState(prompt=prompt,attachedMedia=refs)
        val text=original.withModelMedia(model("text"))
        assertEquals(prompt,text.prompt);assertTrue(text.attachedMedia.isEmpty());assertEquals(refs,text.retainedMedia)
        val references=text.withModelMedia(model("refs").copy(imagesArrayField="images",maxImages=20))
        assertEquals(refs,references.attachedMedia);assertTrue(references.retainedMedia.isEmpty())
    }
    @Test fun smallerCapacityPreservesOverflowWithoutSendingIt() {
        val refs=(1..4).map {image("$it")}
        val state=CreateUiState(attachedMedia=refs).withModelMedia(model("two").copy(imagesArrayField="images",maxImages=2))
        assertEquals(listOf("1","2"),state.attachedMedia.map {it.id})
        assertEquals(listOf("3","4"),state.retainedMedia.map {it.id})
        assertEquals(refs, (state.attachedMedia+state.retainedMedia))
    }
    @Test fun modeChangePreservesNamedResourcesAndReactivatesThem() {
        val model=model("modes").copy(providerId="google_flow",contractEndpoint="video",
            contractMedia=listOf(ContractMediaField("initial_frame","Inicio","image/*",false,1),
                ContractMediaField("reference_ids","Referencias","image/*",false,3)))
        val frame=image("start").copy(fieldKey="initial_frame")
        val reference=image("ref").copy(fieldKey="reference_ids")
        val state=CreateUiState(parameters=mapOf("mode" to "Referencias"),attachedMedia=listOf(frame,reference)).withModelMedia(model)
        assertEquals(listOf(reference),state.attachedMedia);assertEquals(listOf(frame),state.retainedMedia)
        val frames=state.copy(parameters=mapOf("mode" to "Fotogramas")).withModelMedia(model)
        assertEquals(listOf(frame),frames.attachedMedia);assertEquals(listOf(reference),frames.retainedMedia)
    }
    @Test fun preferencesAreSeparatedByProviderEndpointAndModality() {
        val a=ModelWorkDraft("same","prompt",providerId="a",endpoint="image",modality=Modality.IMAGE,parameters=mapOf("quality" to "high"))
        val b=a.copy(providerId="b",parameters=mapOf("quality" to "low"))
        val c=a.copy(endpoint="edit",parameters=mapOf("strength" to ".5"))
        val drafts=listOf(a,b,c).fold(emptyList<ModelWorkDraft>()) {saved,draft ->ModelWorkDrafts.remember(saved,draft)}
        assertEquals(3,drafts.size)
        assertEquals(c,ModelWorkDrafts.find(drafts,model("same").copy(providerId="a",contractEndpoint="edit"),Modality.IMAGE))
    }
    @Test fun undoPreservesBothPartitionsExactly() {
        val state=CreateUiState(selectedModel=model("before"),attachedMedia=listOf(image("active")),retainedMedia=listOf(image("parked")))
        val restored=ModelSwitchUndo.restore(CreateUiState(),ModelSwitchUndo.snapshot(state)!!)
        assertEquals(state.attachedMedia,restored.attachedMedia);assertEquals(state.retainedMedia,restored.retainedMedia)
    }
    @Test fun missingFileIsRetainedButDoesNotSatisfyARequiredInput() {
        val missing=image("missing")
        val state=CreateUiState(attachedMedia=listOf(missing)).withModelMedia(
            model("required").copy(firstFrameField="image",nativeRequiredFields=listOf("image"))) {false}
        assertTrue(state.attachedMedia.isEmpty());assertEquals(listOf(missing),state.retainedMedia)
    }
    @Test fun returningToAModelRestoresItsResourceRolesWhileKeepingNewWork() {
        val final=image("final").copy(slotType=MediaSlotType.LAST_FRAME)
        val leaving=ModelWorkDraft("text","new prompt",media=listOf(final.copy(slotType=MediaSlotType.REFERENCE_IMAGE),image("new")))
        val target=ModelWorkDraft("frames","old prompt",media=listOf(final))
        val plan=ModelWorkDrafts.plan(leaving,target) {true}
        assertEquals("new prompt",plan.prompt)
        assertEquals(MediaSlotType.LAST_FRAME,plan.media.first().slotType)
        assertEquals(listOf("final","new"),plan.media.map {it.id})
    }

    @Test fun sourceAwareContractSwitchRetainsOverflowAndReactivatesItOnTheNextModel() {
        fun contract(id:String,key:String,capacity:Int)=model(id).copy(contractEndpoint="/$id",imagesArrayField=key,maxImages=capacity,
            contractMedia=listOf(ContractMediaField(key,"References","image/*",maximum=capacity,encoding="array")))
        val source=contract("source","source_refs",4)
        val native=model("native").copy(imagesArrayField="images",maxImages=2)
        val target=contract("target","target_refs",4)
        val refs=(1..4).map {image("$it").copy(fieldKey="source_refs")}
        val prompt="Original prompt with all details"
        val narrowed=CreateUiState(selectedModel=native,prompt=prompt,attachedMedia=canonicalModelMedia(source,refs)).withModelMedia(native)
        assertEquals(listOf("1","2"),narrowed.attachedMedia.map {it.id})
        assertEquals(listOf("3","4"),narrowed.retainedMedia.map {it.id})
        val carried=canonicalModelMedia(native,narrowed.attachedMedia)+narrowed.retainedMedia
        val expanded=narrowed.copy(selectedModel=target,attachedMedia=carried,retainedMedia=emptyList()).withModelMedia(target)
        assertEquals(refs.map {it.id},expanded.attachedMedia.map {it.id})
        assertEquals(refs.map {it.uriString},expanded.attachedMedia.map {it.uriString})
        assertTrue(expanded.attachedMedia.all {it.fieldKey=="target_refs"})
        assertTrue(expanded.retainedMedia.isEmpty())
        assertEquals(prompt,expanded.prompt)
        assertTrue(refs.all {it.fieldKey=="source_refs"})
    }

    @Test fun returningToTheSourceRestoresItsSavedContractBindingsExactly() {
        val source=model("source").copy(contractEndpoint="/source",firstFrameField="source_head",lastFrameField="source_tail",
            contractMedia=listOf(ContractMediaField("source_head","Initial","image/*"),ContractMediaField("source_tail","Final","image/*")))
        val original=listOf(image("head").copy(fieldKey="source_head"),image("tail").copy(fieldKey="source_tail"))
        val native=model("native").copy(firstFrameField="image",lastFrameField="last")
        val nativeMedia=compatibleModelMedia(native,canonicalModelMedia(source,original))
        val leaving=ModelWorkDraft(native.id,"Current prompt",media=nativeMedia)
        val saved=ModelWorkDraft(source.id,"Old prompt",media=original)
        val plan=ModelWorkDrafts.plan(leaving,saved) {true}
        val restored=CreateUiState(selectedModel=source,prompt=plan.prompt,attachedMedia=plan.media).withModelMedia(source)
        assertEquals(original,restored.attachedMedia)
        assertTrue(restored.retainedMedia.isEmpty())
        assertEquals("Current prompt",restored.prompt)
    }

    @Test fun bindingOwnershipRoundTripsInDraftsAndOldMediaStillDecodes() {
        val owner=MediaBindingOwner("fixture","source","/source")
        val saved=ModelWorkDraft("source","Original prompt",media=listOf(image("mask").copy(fieldKey="input",bindingOwner=owner)))
        assertEquals(listOf(saved),ModelWorkDrafts.decode(ModelWorkDrafts.encode(listOf(saved))))
        val old="""[{"modelId":"source","prompt":"Original prompt","media":[{"id":"old","uriString":"file:///old.png","mimeType":"image/png","slotType":"REFERENCE_IMAGE","fieldKey":"input"}]}]"""
        val decoded=ModelWorkDrafts.decode(old).single().media.single()
        assertEquals("input",decoded.fieldKey)
        assertNull(decoded.bindingOwner)
    }
}
