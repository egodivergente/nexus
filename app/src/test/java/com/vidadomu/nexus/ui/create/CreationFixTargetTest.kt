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
    /** El modo se declara como parámetro con «simple» por defecto, igual que en el catálogo real. */
    private val suno=model.copy(contractEndpoint="/music",providerModelId="suno/music-generate",
        parameters=listOf(ModelParameter("mode","Cómo componer",ParameterType.ENUM,"simple",options=listOf("simple","custom"),isRequired=true,nativeType="string"),
            ModelParameter("title","Título (modo custom)",ParameterType.TEXT,"",nativeType="string")))
    @Test fun sunoSimpleDescriptionLeadsToPromptNotMedia() {
        assertEquals(CreationFixTarget.PROMPT,creationReadiness(state(suno)
            .copy(parameters=FormContract.restoredValues(suno,emptyMap()))).fixTarget)
    }
    @Test fun sunoCustomTitleLeadsToParametersNotMedia() {
        assertEquals(CreationFixTarget.PARAMETERS,creationReadiness(state(suno)
            .copy(parameters=mapOf("mode" to "custom"))).fixTarget)
    }
    @Test fun missingNamedContractMediaLeadsToMedia() {
        assertEquals(CreationFixTarget.MEDIA,creationReadiness(state(model.copy(contractEndpoint="/video",
            contractMedia=listOf(ContractMediaField("mask","Máscara precisa","image/*",required=true))))).fixTarget)
    }
    /**
     * Con un modelo que no admite ningún archivo, lo que procede es cambiar de modelo; para que el aviso
     * apunte a los archivos, el modelo tiene que aceptarlos y el problema estar en ellos.
     */
    @Test fun invalidTypeAndExcessReferencesLeadToMedia() {
        val frames=model.copy(firstFrameField="start",nativeRequiredFields=listOf("start"))
        val video=AttachedMedia("a","file:///test","video/mp4",MediaSlotType.PRIMARY_IMAGE)
        assertEquals(CreationFixTarget.MEDIA,creationReadiness(state(frames).copy(attachedMedia=listOf(video))).fixTarget)
        // El modelo tiene que declarar algo; si no, el aviso correcto es que no hay contrato cargado.
        val one=model.copy(imagesArrayField="images",maxImages=1,nativeRequiredFields=listOf("images"))
        val refs=(1..2).map {AttachedMedia("r$it","file:///r$it.png","image/png",MediaSlotType.REFERENCE_IMAGE)}
        assertEquals(CreationFixTarget.MEDIA,creationReadiness(state(one).copy(attachedMedia=refs)).fixTarget)
    }
    @Test fun requiredScalarLeadsToParameters() {
        val m=model.copy(contractEndpoint="/fixture",parameters=listOf(ModelParameter("quality","Calidad",ParameterType.TEXT,isRequired=true)))
        assertEquals(CreationFixTarget.PARAMETERS,creationReadiness(state(m)).fixTarget)
    }
}
