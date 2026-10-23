package com.vidadomu.nexus

import com.vidadomu.nexus.ui.models.*
import com.vidadomu.nexus.core.model.*
import kotlinx.serialization.json.*
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EndpointEvaluationCoverageTest {
    private fun asset(name: String) = File("src/main/assets/$name").readText()
    @Test fun everyBundledAtlasAndCrunDestinationHasItsOwnEvidenceRecord() {
        val bundle=Json.parseToJsonElement(asset("model_endpoint_evidence.json")).jsonObject.getValue("records").jsonArray
        val keys=bundle.map {it.jsonObject.let {r->r.getValue("provider").jsonPrimitive.content+":"+r.getValue("model").jsonPrimitive.content}}.toSet()
        val atlas=Json.parseToJsonElement(asset("atlas_catalog_snapshot.json")).jsonObject.getValue("data").jsonArray
        atlas.forEach {assertTrue(it.jsonObject.getValue("model").toString(),"atlas_cloud:"+it.jsonObject.getValue("model").jsonPrimitive.content in keys)}
        val crun=Json.parseToJsonElement(asset("crun_models_snapshot.json")).jsonArray
        crun.forEach {assertTrue("crun:"+it.jsonObject.getValue("providerModelId").jsonPrimitive.content in keys)}
        assertEquals(atlas.size+crun.size,keys.size)
    }
    @Test fun everyRecordProducesBoundedExplainableAxesWithoutMakingAVisualBenchmarkClaim() {
        val json=Json {ignoreUnknownKeys=true}
        val records=Json.parseToJsonElement(asset("model_endpoint_evidence.json")).jsonObject.getValue("records").jsonArray
        for(raw in records) {
            val evidence=json.decodeFromJsonElement<EndpointEvidence>(raw)
            val model=NexusModel(evidence.model,evidence.model,evidence.provider,organization="",modalities=listOf(Modality.IMAGE),capabilities=emptyList(),parameters=emptyList())
            val evaluation=EndpointEvaluations.evaluate(model,evidence)
            assertTrue(evaluation.axes.isNotEmpty());assertTrue(evaluation.score in 0.0..10.0)
            evaluation.axes.forEach {assertTrue(it.reason.isNotBlank())}
            if(evidence.status=="official_contract") assertTrue(evaluation.source.startsWith("https://static.atlascloud.ai/"))
        }
    }
    @Test fun minimaxMaxAndFastAndGrokVersionsStayDistinctInPresentation() {
        fun m(id:String)=NexusModel(id,id,"atlas_cloud",organization="",modalities=listOf(Modality.VIDEO),capabilities=emptyList(),parameters=emptyList())
        assertNotEquals(com.vidadomu.nexus.ui.create.canvasModelIdentity(m("minimax/h3-max/image-to-video")).title,com.vidadomu.nexus.ui.create.canvasModelIdentity(m("minimax/h3-fast/image-to-video")).title)
        assertNotEquals(com.vidadomu.nexus.ui.create.canvasModelIdentity(m("xai/grok-imagine-video-v1.5/text-to-video")).title,com.vidadomu.nexus.ui.create.canvasModelIdentity(m("xai/grok-imagine-video/text-to-video")).title)
    }
}
