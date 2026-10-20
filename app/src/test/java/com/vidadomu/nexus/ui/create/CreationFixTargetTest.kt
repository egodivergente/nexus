package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*
import org.junit.Assert.*
import org.junit.Test

class CreationFixTargetTest {
    private val model=NexusModel("fixture","Modelo","test",organization="Fixture",modalities=listOf(Modality.IMAGE),
        capabilities=emptyList(),parameters=emptyList(),promptRequired=false)
    private fun state(m:NexusModel=model)=CreateUiState(selectedModel=m,schemaReady=true)
    @Test fun noModelLeadsToModelPicker() {assertEquals(CreationFixTarget.MODEL,creationReadiness(CreateUiState()).fixTarget)}
    @Test fun loadingHasNoMisleadingActionAndFailedConfigurationCanRetry() {
        assertNull(creationReadiness(state().copy(isLoadingSchema=true)).fixTarget)
        assertEquals(CreationFixTarget.RETRY_CONFIGURATION,creationReadiness(state().copy(schemaReady=false)).fixTarget)
    }
    @Test fun unsupportedSchemaAsksForAnotherModelNotAnEndlessRetry() {
        assertEquals(CreationFixTarget.MODEL,creationReadiness(state().copy(schemaReady=false,schemaUnsupported=true)).fixTarget)
    }
    @Test fun nativeImageAndVideoFieldsLeadToMedia() {
        listOf(model.copy(firstFrameField="start",nativeRequiredFields=listOf("start")),
            model.copy(videoField="video",nativeRequiredFields=listOf("video"))).forEach {
            assertEquals(CreationFixTarget.MEDIA,creationReadiness(state(it)).fixTarget)
        }
    }
    @Test fun sunoSimpleDescriptionLeadsToPromptNotMedia() {
        assertEquals(CreationFixTarget.PROMPT,creationReadiness(state(model.copy(contractEndpoint="/music",providerModelId="suno/music-generate"))).fixTarget)
    }
    @Test fun sunoCustomTitleLeadsToParametersNotMedia() {
        assertEquals(CreationFixTarget.PARAMETERS,creationReadiness(state(model.copy(contractEndpoint="/music",providerModelId="suno/music-generate"))
            .copy(parameters=mapOf("mode" to "custom"))).fixTarget)
    }
    @Test fun missingNamedContractMediaLeadsToMedia() {
        assertEquals(CreationFixTarget.MEDIA,creationReadiness(state(model.copy(contractEndpoint="/video",
            contractMedia=listOf(ContractMediaField("mask","Máscara precisa","image/*",required=true))))).fixTarget)
    }
    @Test fun invalidTypeAndExcessReferencesLeadToMedia() {
        val media=AttachedMedia("a","file:///test","video/mp4",MediaSlotType.PRIMARY_IMAGE)
        assertEquals(CreationFixTarget.MEDIA,creationReadiness(state().copy(attachedMedia=listOf(media))).fixTarget)
        assertEquals(CreationFixTarget.MEDIA,creationReadiness(state().copy(attachedMedia=listOf(media.copy(slotType=MediaSlotType.REFERENCE_IMAGE,mimeType="image/png")))).fixTarget)
    }
    @Test fun requiredScalarLeadsToParameters() {
        val m=model.copy(contractEndpoint="/fixture",parameters=listOf(ModelParameter("quality","Calidad",ParameterType.TEXT,isRequired=true)))
        assertEquals(CreationFixTarget.PARAMETERS,creationReadiness(state(m)).fixTarget)
    }
}
