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
}
