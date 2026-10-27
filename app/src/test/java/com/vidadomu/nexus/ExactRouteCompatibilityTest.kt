package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.routing.*
import com.vidadomu.nexus.ui.create.CreateUiState
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ExactRouteCompatibilityTest {
    private val params=listOf(ModelParameter("duration","Duration",ParameterType.DURATION,"5",nativeType="integer"),
        ModelParameter("sound","Audio",ParameterType.BOOLEAN,"true",nativeType="boolean"))
    private val atlas=NexusModel("kwaivgi/kling-v3.0-pro/text-to-video","Kling","atlas_cloud",organization="Kling",
        modalities=listOf(Modality.VIDEO),capabilities=listOf(ModelCapability.TEXT_TO_VIDEO),parameters=params)
    private val crun=atlas.copy(id="crun/kling-v3-pro",providerId="crun",parameters=params.map {if(it.key=="sound") it.copy(key="audio") else it})
    private val state=CreateUiState(selectedModel=atlas,selectedModality=Modality.VIDEO,prompt="A scene",parameters=mapOf("duration" to "5","sound" to "true"),schemaReady=true)
    @Test fun explicitModelAndCompleteParametersCanMapAcrossProviders() {
        val mapped=ExactRouteCompatibility.map(state,crun)!!
        assertEquals("true",mapped.parameters["audio"])
        assertEquals("5",mapped.parameters["duration"])
        assertEquals(state.prompt,mapped.prompt)
    }
    @Test fun proNeverMapsToStandardOrTurboEvenIfTheyAreCheaper() {
        assertNull(ExactRouteCompatibility.map(state,crun.copy(id="crun/kling-v3-std")))
        assertNull(ExactRouteCompatibility.map(state,crun.copy(id="crun/kling-v3-turbo")))
    }
    @Test fun sameNameAndFamilyNeverCreateAnEquivalence() {
        assertNull(ExactRouteCompatibility.map(state,crun.copy(id="unverified",providerModelId="unverified")))
        val fal=atlas.copy(id="minimax/h3-max/image-to-video",providerId="usr_fal-curated",providerModelId="minimax/h3-max/image-to-video")
        assertNull(ExactRouteCompatibility.identity(fal,true))
    }
    @Test fun unknownOrMissingFieldsAndChangedDefaultsBlockTheRoute() {
        assertNull(ExactRouteCompatibility.map(state.copy(parameters=state.parameters+("quality" to "high")),crun))
        assertNull(ExactRouteCompatibility.map(state,crun.copy(parameters=crun.parameters.filterNot {it.key=="audio"})))
        assertNull(ExactRouteCompatibility.map(state,crun.copy(parameters=crun.parameters+ModelParameter("steps","Steps",ParameterType.INTEGER,"20"))))
    }
    @Test fun outputCountCannotBeDroppedOrChanged() {
        val source=atlas.copy(parameters=params+ModelParameter("num_images","Count",ParameterType.INTEGER,"2"))
        assertNull(ExactRouteCompatibility.map(state.copy(selectedModel=source),crun))
    }
    @Test fun pricesExpiredOrAboveAcceptedCeilingNeverWin() {
        fun route(provider:String,cost:String,until:Long)=GenerationRoute(provider,"model",BigDecimal(cost),until) {error("must not execute")}
        val routes=listOf(route("cheap","0.1",100),route("funded","0.5",1000),route("expensive","2",1000))
        assertEquals(listOf("funded"),RoutingSubmissionPolicy.ordered(routes,BigDecimal.ONE,100).map {it.providerId})
    }
    @Test fun uncertainResponsesNeverPermitFallback() {
        for(error in listOf(java.net.SocketTimeoutException(),ProviderError("server",httpStatusCode=500),
            ProviderError("conflict",httpStatusCode=409),IllegalArgumentException("malformed response after POST")))
            assertFalse(RoutingSubmissionPolicy.recoverableBeforeAcceptance(error))
        for(status in listOf(401,402,403,404,429)) assertTrue(RoutingSubmissionPolicy.recoverableBeforeAcceptance(ProviderError("rejected",httpStatusCode=status)))
    }
    @Test fun quotePreservesSubCentDecimals() {
        val q=com.vidadomu.nexus.provider.atlas.AtlasPriceQuote.decode("""{"code":200,"data":{"price":0.000000123456789123456789}}""", "test")
        assertEquals("0.000000123456789123456789",q.exactUsd)
    }
    @Test fun logicalVariantsPreservePreferredProviderWithoutMergingTiersOrUnknownModels() {
        val standard=crun.copy(id="crun/kling-v3-std")
        val unknown=crun.copy(id="private-experimental")
        val all=listOf(atlas,crun,standard,unknown)
        val logical=logicalVariantRepresentatives(all,crun,false)
        assertEquals(listOf(crun,standard,unknown),logical)
        assertEquals(4,all.size)
    }
    @Test fun referenceRolesOrderAndLocalFilesSurviveButProviderUploadUrlsNeverTransfer() {
        val original=atlas.copy(id="kwaivgi/kling-v3.0-pro/image-to-video",firstFrameField="image",lastFrameField="end_image")
        val target=crun.copy(firstFrameField="first_frame",lastFrameField="last_frame")
        val first=AttachedMedia(uriString="file:///first.png",mimeType="image/png",slotType=MediaSlotType.PRIMARY_IMAGE,fieldKey="image",remoteUrl="https://old-provider/input")
        val last=first.copy(uriString="file:///last.png",slotType=MediaSlotType.LAST_FRAME,fieldKey="end_image")
        val mapped=ExactRouteCompatibility.map(state.copy(selectedModel=original,attachedMedia=listOf(first,last)),target)!!
        assertEquals(listOf("file:///first.png","file:///last.png"),mapped.attachedMedia.map {it.uriString})
        assertEquals(listOf("first_frame","last_frame"),mapped.attachedMedia.map {it.fieldKey})
        assertTrue(mapped.attachedMedia.all {it.remoteUrl==null})
        assertNull(ExactRouteCompatibility.map(state.copy(selectedModel=original,attachedMedia=listOf(first.copy(fieldKey="mask"))),target))
    }

    @Test fun fallbackRequiresDurableExplicitRejectionAndNoRemoteReceipt() {
        val rejected=GenerationJob("one",null,"atlas_cloud",atlas.id,Modality.VIDEO,"scene",status=GenerationStatus.FAILED,
            parameters=mapOf("__submission_uncertain" to "false"))
        val quota=ProviderError("quota",httpStatusCode=429)
        assertTrue(RoutingSubmissionPolicy.canFallback(rejected,quota))
        assertFalse(RoutingSubmissionPolicy.canFallback(null,quota))
        assertFalse(RoutingSubmissionPolicy.canFallback(rejected.copy(providerJobId="accepted-remote"),quota))
        assertFalse(RoutingSubmissionPolicy.canFallback(rejected.copy(parameters=emptyMap()),quota))
        assertFalse(RoutingSubmissionPolicy.canFallback(rejected.copy(parameters=mapOf("__submission_uncertain" to "true")),quota))
        assertFalse(RoutingSubmissionPolicy.canFallback(rejected.copy(status=GenerationStatus.PROCESSING),quota))
        assertFalse(RoutingSubmissionPolicy.canFallback(rejected,java.net.SocketTimeoutException()))
    }

}
