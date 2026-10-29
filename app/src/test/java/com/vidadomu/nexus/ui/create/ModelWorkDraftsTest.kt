package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ModelWorkDraftsTest {
    private fun model(id:String)=NexusModel(id,id,"test",organization="Estudio",
        modalities=listOf(Modality.IMAGE),capabilities=emptyList(),parameters=emptyList())
    private fun image(id:String)=AttachedMedia(id,"file:///fixture/$id.png","image/png",MediaSlotType.REFERENCE_IMAGE)
    private fun video(id:String)=AttachedMedia(id,"file:///fixture/$id.mp4","video/mp4",MediaSlotType.VIDEO_INPUT)
    private fun draft(id:String,prompt:String="p-$id",media:List<AttachedMedia> = emptyList())=
        ModelWorkDraft(id,prompt,"neg-$id",mapOf("size" to id),media)

    @Test fun rememberKeepsMostRecentFirstWithoutDuplicates() {
        val drafts=ModelWorkDrafts.remember(ModelWorkDrafts.remember(listOf(draft("a"),draft("b")),draft("c")),draft("a","nuevo"))
        assertEquals(listOf("a","c","b"),drafts.map {it.modelId})
        assertEquals("nuevo",ModelWorkDrafts.find(drafts,"a")!!.prompt)
    }
    @Test fun rememberCapsAtThirtyModelsDroppingTheOldest() {
        var drafts=emptyList<ModelWorkDraft>()
        repeat(35) {drafts=ModelWorkDrafts.remember(drafts,draft("m$it"))}
        assertEquals(ModelWorkDrafts.MAX_MODELS,drafts.size)
        assertEquals("m34",drafts.first().modelId); assertNull(ModelWorkDrafts.find(drafts,"m4"))
        assertNotNull(ModelWorkDrafts.find(drafts,"m5"))
    }
    @Test fun targetWithDraftRestoresParametersButKeepsCurrentTextAndAllResources() {
        val leaving=draft("a",media=listOf(image("one")))
        val target=draft("b",media=listOf(image("two")))
        val plan=ModelWorkDrafts.plan(leaving,target) {true}
        assertTrue(plan.restored)
        assertEquals("p-a",plan.prompt); assertEquals("neg-a",plan.negativePrompt)
        assertEquals(mapOf("size" to "b"),plan.parameters); assertEquals(listOf("one","two"),plan.media.map {it.id})
        assertEquals(0,plan.missingMedia)
    }
    @Test fun restoredDraftRetainsMissingReferencesAndReportsThem() {
        val plan=ModelWorkDrafts.plan(draft("a"),draft("b",media=listOf(image("kept"),image("gone")))) {it.id=="kept"}
        assertEquals(listOf("kept","gone"),plan.media.map {it.id}); assertEquals(1,plan.missingMedia)
    }
    @Test fun targetWithoutDraftCarriesPromptAndEveryReferenceWithDefaultParameters() {
        val refs=listOf(image("one"),image("two"),video("clip"))
        val plan=ModelWorkDrafts.plan(draft("a",media=refs),null) {false}
        assertFalse(plan.restored)
        assertEquals("p-a",plan.prompt); assertEquals("neg-a",plan.negativePrompt)
        assertTrue(plan.parameters.isEmpty()); assertEquals(refs,plan.media); assertEquals(0,plan.missingMedia)
    }
    @Test fun carriedReferencesStillPassTheExistingCompatibilityRouting() {
        val plan=ModelWorkDrafts.plan(draft("a",media=listOf(image("one"),image("two"),video("clip"))),null) {true}
        val kept=compatibleModelMedia(model("refs").copy(imagesArrayField="images",maxImages=4),plan.media)
        assertEquals(listOf("one","two"),kept.map {it.id})
        assertTrue(kept.all {it.slotType==MediaSlotType.REFERENCE_IMAGE})
    }
    @Test fun serializationRoundTripsAndToleratesCorruptData() {
        val drafts=listOf(draft("a",media=listOf(image("one").copy(fieldKey="reference_ids"))),draft("b"))
        assertEquals(drafts,ModelWorkDrafts.decode(ModelWorkDrafts.encode(drafts)))
        assertTrue(ModelWorkDrafts.decode("{no es json").isEmpty())
        assertTrue(ModelWorkDrafts.decode(null).isEmpty())
    }
    @Test fun encodeNeverStoresMoreThanThirtyModels() {
        val drafts=(0 until 40).map {draft("m$it")}
        assertEquals(ModelWorkDrafts.MAX_MODELS,ModelWorkDrafts.decode(ModelWorkDrafts.encode(drafts)).size)
    }

    @Test fun currentAndPerConfigurationDraftsRoundTripAdaptationsAndDecodeLegacyData() {
        val adaptation=ParameterTransferAdaptation("duration","Duración","15","5","Límite del contrato")
        val saved=ModelWorkDraft("b","prompt",parameters=mapOf("duration" to "5"),providerId="test",
            endpoint="/b",modality=Modality.VIDEO,parameterAdaptations=listOf(adaptation))
        assertEquals(listOf(saved),ModelWorkDrafts.decode(ModelWorkDrafts.encode(listOf(saved))))
        val active=CreationDraft("b",Modality.VIDEO,"prompt",saved.parameters,providerId="test",endpoint="/b",
            parameterAdaptations=saved.parameterAdaptations)
        assertEquals(active,Json.decodeFromString<CreationDraft>(Json.encodeToString(active)))
        val legacy="""{"modelId":"b","modality":"VIDEO","prompt":"prompt","parameters":{"duration":"5"}}"""
        assertTrue(Json.decodeFromString<CreationDraft>(legacy).parameterAdaptations.isEmpty())
        assertTrue(ModelWorkDrafts.decode("[$legacy]").single().parameterAdaptations.isEmpty())
    }

    @Test fun targetPlanRestoresOnlyItsOwnConfigurationAdaptations() {
        val target=model("same").copy(contractEndpoint="/target")
        val adaptation=ParameterTransferAdaptation("duration","Duración","15","5","Límite del contrato")
        val saved=draft("same").copy(providerId=target.providerId,endpoint=target.contractEndpoint,modality=Modality.IMAGE,
            parameterAdaptations=listOf(adaptation))
        val other=saved.copy(endpoint="/other",parameterAdaptations=emptyList())
        val decoded=ModelWorkDrafts.decode(ModelWorkDrafts.encode(listOf(other,saved)))
        val plan=ModelWorkDrafts.plan(draft("source"),ModelWorkDrafts.find(decoded,target,Modality.IMAGE),target) {true}
        assertEquals(listOf(adaptation),plan.parameterAdaptations)
        val otherPlan=ModelWorkDrafts.plan(draft("source"),ModelWorkDrafts.find(decoded,target.copy(contractEndpoint="/other"),Modality.IMAGE)) {true}
        assertTrue(otherPlan.parameterAdaptations.isEmpty())
    }
}
