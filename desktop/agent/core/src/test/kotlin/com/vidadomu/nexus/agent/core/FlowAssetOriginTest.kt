package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.registry.*
import com.vidadomu.nexus.agent.core.tools.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class FlowAssetOriginTest {
    @Test fun `buscar Flow devuelve origen y job de Nexus sin confundir ids`() = runTest {
        val h=Harness()
        h.assets.upsertGeneration(GenerationRecord(id="generation-one",nexusJobId="flow-job-one",
            providerId="google_flow",modelId="google-flow/nano2",modality="IMAGE",prompt="Blue sphere",status="COMPLETED",createdAt=1L))
        h.assets.addAsset(Asset(id="asset-one",uri="/media/test.jpg",type=AssetType.IMAGE,
            displayName="Nano Banana 2",generationId="generation-one",createdAt=1L))
        val result=SearchAssetsTool(h.deps).run(buildJsonObject { put("text","Google Flow");put("type","IMAGE") },
            AgentSession("test",AgentMode.AGENT)).result.jsonObject
        val item=result["assets"]!!.jsonArray.single().jsonObject
        assertEquals("google_flow",item["provider_id"]!!.jsonPrimitive.content)
        assertEquals("google-flow/nano2",item["model_id"]!!.jsonPrimitive.content)
        assertEquals("flow-job-one",item["nexus_job_id"]!!.jsonPrimitive.content)
        assertEquals("generation-one",item["generation_id"]!!.jsonPrimitive.content)
        assertEquals("asset-one",item["asset_id"]!!.jsonPrimitive.content)
    }
    @Test fun `un archivo importado no inventa proveedor o job`() = runTest {
        val h=Harness()
        h.assets.addAsset(Asset(id="import-one",uri="/media/import.jpg",type=AssetType.IMAGE,displayName="Reference",createdAt=1L))
        val result=SearchAssetsTool(h.deps).run(buildJsonObject {},AgentSession("test",AgentMode.AGENT)).result.jsonObject
        val item=result["assets"]!!.jsonArray.single().jsonObject
        assertFalse("provider_id" in item);assertFalse("nexus_job_id" in item)
    }
    @Test fun `preparar postproceso sin prompt respeta schema y no envia`() = runTest {
        val ref=ModelRef("google_flow","google-flow/image_upscale")
        val model=ModelSummary(ref,"Escalar imagen Flow",modalities=setOf("IMAGE"),capabilities=setOf("UPSCALE"))
        val schema=ModelSchema(ref,listOf(
            ParamSpec("source_request_id",uiType="TEXT",nativeType="string",required=true),
            ParamSpec("resolution",uiType="ENUM",nativeType="string",options=listOf("2k","4k"),required=true)
        ),MediaSpec(),promptRequired=false)
        val h=Harness(registry=FakeRegistry(listOf(model),mapOf(ref to schema)))
        val result=PrepareGenerationTool(h.deps).run(buildJsonObject {
            put("provider_id",ref.providerId);put("model_id",ref.modelId)
            put("params",buildJsonObject { put("source_request_id","flow-job-one");put("resolution","2k") })
        },AgentSession("test",AgentMode.AGENT))
        assertFalse(result.isError,result.result.toString())
        assertEquals("",h.guard.plans().single().prompt)
        assertTrue(h.backend.submitted.isEmpty())
    }
    @Test fun `preparar generacion que exige prompt sigue rechazando vacio`() = runTest {
        val h=Harness()
        val result=PrepareGenerationTool(h.deps).run(buildJsonObject {
            put("provider_id",SEEDREAM.providerId);put("model_id",SEEDREAM.modelId)
        },AgentSession("test",AgentMode.AGENT))
        assertTrue(result.isError)
        assertTrue(result.result.toString().contains("El modelo exige prompt"))
        assertTrue(h.guard.plans().isEmpty());assertTrue(h.backend.submitted.isEmpty())
    }
}
