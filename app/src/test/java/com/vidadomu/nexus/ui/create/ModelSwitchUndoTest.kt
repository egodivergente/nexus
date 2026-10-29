package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ModelSwitchUndoTest {
    private fun model(id:String)=NexusModel(id,"Modelo $id","test",organization="Estudio",
        modalities=listOf(Modality.IMAGE),capabilities=emptyList(),parameters=emptyList())
    private fun image(id:String)=AttachedMedia(id,"file:///fixture/$id.png","image/png",MediaSlotType.REFERENCE_IMAGE)
    private val longPrompt="Retrato cinematográfico ".repeat(40)

    private fun working()=CreateUiState(selectedModality=Modality.IMAGE,availableModels=listOf(model("a"),model("b")),
        selectedModel=model("a"),prompt=longPrompt,negativePrompt="borroso",parameters=mapOf("size" to "1024","seed" to "7"),
        attachedMedia=listOf(image("r1"),image("r2"),image("r3")))

    @Test fun undoRestoresExactlyThePreviousDraft() {
        val before=working()
        val snapshot=ModelSwitchUndo.snapshot(before)!!
        val afterSwitch=before.copy(selectedModel=model("b"),parameters=mapOf("size" to "512"),
            attachedMedia=listOf(image("r1").copy(slotType=MediaSlotType.PRIMARY_IMAGE)),showAdvanced=true,errorMessage="aviso")
        val restored=ModelSwitchUndo.restore(afterSwitch,snapshot)
        assertEquals(before.selectedModel,restored.selectedModel)
        assertEquals(before.selectedModality,restored.selectedModality)
        assertEquals(before.availableModels,restored.availableModels)
        assertEquals(before.prompt,restored.prompt)
        assertEquals(before.negativePrompt,restored.negativePrompt)
        assertEquals(before.parameters,restored.parameters)
        assertEquals(before.attachedMedia,restored.attachedMedia)
        assertFalse(restored.showAdvanced); assertNull(restored.errorMessage)
    }
    @Test fun snapshotIsNullWithoutModel() {
        assertNull(ModelSwitchUndo.snapshot(CreateUiState()))
    }
    @Test fun undoRestoresTheSourceAdaptationsAndClearsUnrelatedDestinationAnnotations() {
        val previous=ParameterTransferAdaptation("duration","Duración","15","5","Contrato anterior")
        val destination=ParameterTransferAdaptation("resolution","Resolución","1080p","720p","Otro contrato")
        val before=working().copy(parameterAdaptations=listOf(previous))
        val changed=before.copy(selectedModel=model("b"),parameterAdaptations=listOf(destination))
        val restored=ModelSwitchUndo.restore(changed,ModelSwitchUndo.snapshot(before)!!)
        assertEquals(listOf(previous),restored.parameterAdaptations)
        val clean=ModelSwitchUndo.restore(changed,ModelSwitchUndo.snapshot(working())!!)
        assertTrue(clean.parameterAdaptations.isEmpty())
    }
    @Test fun pendingUndoCarriesExactAnnotationsOnlyToTheRequestedConfiguration() {
        val adaptation=ParameterTransferAdaptation("duration","Duración","15","5","Contrato")
        val key=com.vidadomu.nexus.ui.create.capability.CapabilityKey("fixture","same",null,Modality.VIDEO)
        val pending=PendingWorkParameters(key,mapOf("duration" to "5"),listOf(adaptation))
        assertTrue(pending.matches(key.copy(endpoint="/resolved")))
        assertEquals(listOf(adaptation),pending.adaptations)
        assertFalse(pending.matches(key.copy(providerId="other")))
    }
    @Test fun parkedCountsOnlyIncompatibleMediaEvenWhenReSlotted() {
        val carried=listOf(image("r1"),image("r2"),image("r3"))
        val compatible=listOf(image("r1").copy(slotType=MediaSlotType.PRIMARY_IMAGE),image("r2"))
        assertEquals(listOf("r3"),ModelSwitchUndo.parked(carried,compatible).map {it.id})
        assertTrue(ModelSwitchUndo.parked(carried,carried).isEmpty())
    }
    @Test fun noticeMentionsSavedReferences() {
        assertEquals("Modelo cambiado",ModelSwitchNotice(1,"Modelo",0).message)
        assertEquals("Modelo cambiado · 1 archivo guardado",ModelSwitchNotice(1,"Modelo",1).message)
        assertEquals("Modelo cambiado · 2 archivos guardados",ModelSwitchNotice(1,"Modelo",2).message)
        assertEquals("Modelo cambiado · 2 ajustes adaptados",ModelSwitchNotice(1,"Modelo",0,2).message)
    }
}
