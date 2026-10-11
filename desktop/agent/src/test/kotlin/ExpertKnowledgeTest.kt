import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.*
import com.vidadomu.nexus.agent.core.registry.*
import com.vidadomu.nexus.agent.core.tools.AgentSession
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class ExpertKnowledgeTest {
    private fun knowledge(date: String = "2026-10-06") = ExpertKnowledge(File("assets/expert-guides.json")) { LocalDate.parse(date) }
    private val ref = ModelRef("atlas_cloud", "black-forest-labs/flux-2-pro/text-to-image")
    private fun request(text: String, system: List<String> = listOf("Modo\nSkills disponibles (carga...)\n" + "irrelevant".repeat(100))) =
        LlmRequest("test", system, listOf(LlmMessage(Role.USER, listOf(ContentBlock.Text(text)))),
            listOf(ToolSpec("get_model_brief","brief",buildJsonObject {}), ToolSpec("recommend_models","legacy",buildJsonObject {})),4096)
    @Test fun greetingHasNoToolsOrCatalog() {
        val result=ExpertPolicy.compact(request("hey!"))
        assertTrue(result.tools.isEmpty());assertEquals(128,result.maxTokens)
        assertFalse(result.system.joinToString().contains("irrelevant"))
    }
    @Test fun substantiveQuestionKeepsBriefAndCapsOutput() {
        val result=ExpertPolicy.compact(request("Quiero un prompt FLUX"))
        assertEquals(listOf("get_model_brief"),result.tools.map{it.name})
        assertEquals(1800,result.maxTokens)
    }
    @Test fun followUpToolProtocolIsPreserved() {
        val request=request("hey").let { it.copy(messages=it.messages+
            LlmMessage(Role.ASSISTANT,listOf(ContentBlock.ToolUse("x","get_model_brief",buildJsonObject{})))+
            LlmMessage(Role.USER,listOf(ContentBlock.ToolResult("x",buildJsonObject{})))) }
        assertFalse(ExpertPolicy.greeting(request));assertTrue(ExpertPolicy.compact(request).tools.isNotEmpty())
    }
    @Test fun guidesDoNotLeakAcrossVersions() {
        assertTrue(knowledge().guides(ref.modelId).isNotEmpty())
        assertTrue(knowledge().guides("black-forest-labs/flux-3-video").isEmpty())
        assertTrue(knowledge().guides("minimax/h3-developer/reference-to-video").any { it["id"]?.jsonPrimitive?.content=="h3-reference-prompt" })
        assertTrue(knowledge().guides("bytedance/seedream-v5.0-pro/edit").any { it["id"]?.jsonPrimitive?.content=="seedream5pro" })
    }
    @Test fun staleGuidesAreLabelled() {
        assertEquals("stale",knowledge("2027-05-01").guides(ref.modelId).first()["freshness"]?.jsonPrimitive?.content)
    }
    private class Registry(val models: List<ModelSummary>, val broken: Boolean=false):ModelRegistry {
        var queries=0
        override suspend fun list()=models
        override suspend fun schema(ref:ModelRef):ModelSchema{
            queries++;if(broken)error("private error must not leak")
            return ModelSchema(ref,listOf(ParamSpec("seed","Seed","INTEGER")),MediaSpec(),source="https://example.test/schema")
        }
    }
    private fun run(registry: Registry, input: String, excluded: Set<String> = emptySet())=runBlocking {
        ExpertBriefTool(registry,knowledge()){excluded}.run(Json.parseToJsonElement(input).jsonObject,AgentSession("t",AgentMode.ASSISTANT)).result.jsonObject
    }
    @Test fun unknownPriceNeverBecomesFree() {
        val r=Registry(listOf(ModelSummary(ref,"Flux 2 Pro",modalities=setOf("IMAGE"),basePriceUsd=0.0)))
        val item=run(r,"""{"query":"flux 2 pro"}""")["items"]!!.jsonArray.first().jsonObject
        assertEquals("pending_quote",item["cost_status"]!!.jsonPrimitive.content)
        assertEquals(JsonNull,item["catalog_metadata"]!!.jsonObject["basePriceUsd"])
        assertEquals("available",item["schema_status"]!!.jsonPrimitive.content)
    }
    @Test fun missingModelIsNotInvented() {
        assertTrue(run(Registry(emptyList()),"""{"query":"fictional 999"}""")["items"]!!.jsonArray.isEmpty())
    }
    @Test fun flowUpscaleBriefUsesConnectedProviderAndCapability() {
        val flow=ModelRef("google_flow","google-flow/image_upscale")
        val r=Registry(listOf(
            ModelSummary(flow,"Escalar imagen Flow",modalities=setOf("IMAGE"),capabilities=setOf("UPSCALE")),
            ModelSummary(ModelRef("google_flow","google-flow/nano2"),"Nano Banana 2",modalities=setOf("IMAGE"),capabilities=setOf("TEXT_TO_IMAGE"))))
        val result=run(r,"""{"provider_id":"google_flow","modality":"IMAGE","capability":"UPSCALE","focus":"schema"}""")
        val item=result["items"]!!.jsonArray.single().jsonObject
        assertEquals(flow.modelId,item["model_id"]!!.jsonPrimitive.content)
        assertEquals("available",item["schema_status"]!!.jsonPrimitive.content)
        assertEquals(1,r.queries)
        assertTrue(ExpertPolicy.PROMPT.contains("no confirma saldo ni deduccion efectiva"))
    }
    @Test fun brokenSchemaIsExplicitAndSanitized() {
        val r=Registry(listOf(ModelSummary(ref,"Flux",modalities=setOf("IMAGE"))),true)
        val result=run(r,"{}").toString()
        assertTrue(result.contains("unavailable"));assertFalse(result.contains("private error"))
    }
    @Test fun atMostThreeSchemasPerBrief() {
        val r=Registry((1..8).map{ModelSummary(ModelRef("atlas_cloud","model-$it"),"Model $it",modalities=setOf("IMAGE"))})
        assertEquals(3,run(r,"""{"limit":100}""")["items"]!!.jsonArray.size)
        assertEquals(3,r.queries)
    }
    @Test fun excludedFamiliesRequireExplicitRequest() {
        val m=ModelSummary(ModelRef("atlas_cloud","google/veo3.1"),"Veo 3.1",modalities=setOf("VIDEO"))
        assertTrue(run(Registry(listOf(m)),"{}",setOf("veo"))["items"]!!.jsonArray.isEmpty())
        assertEquals(1,run(Registry(listOf(m)),"""{"query":"veo"}""",setOf("veo"))["items"]!!.jsonArray.size)
    }
    @Test fun promptPreservesCostAndMemoryRules() {
        assertTrue(ExpertPolicy.PROMPT.contains("coste pendiente"))
        assertTrue(ExpertPolicy.PROMPT.contains("update_project_memory"))
        assertTrue(ExpertPolicy.PROMPT.contains("sin adjunto visual"))
    }
    @Test fun textLimitsSurviveSchemaTransport() {
        val schema=Json.decodeFromString<ModelSchema>("""{"ref":{"providerId":"test","modelId":"one"},"parameters":[],"media":{},"textLimits":{"prompt":{"unit":"unicode_code_points","maximum":2000,"source":"official"}}}""")
        assertEquals(2000,schema.textLimits["prompt"]?.maximum)
        assertEquals("unicode_code_points",schema.textLimits["prompt"]?.unit)
        assertTrue(ExpertPolicy.PROMPT.contains("nunca ilimitado"))
    }
    @Test fun limitsBriefOmitsGuidesAndCatalog() {
        val r=Registry(listOf(ModelSummary(ref,"Flux",modalities=setOf("IMAGE"))))
        val item=run(r,"""{"focus":"limits"}""")["items"]!!.jsonArray.first().jsonObject
        assertTrue("textLimits" in item);assertFalse("guides" in item);assertFalse("catalog_metadata" in item)
    }
    @Test fun compactionPreservesCurrentToolResultAndUserText() {
        val large=buildJsonObject { put("payload","x".repeat(7000)) }
        val messages=listOf(LlmMessage(Role.USER,listOf(ContentBlock.Text("Mi regla"))),
            LlmMessage(Role.ASSISTANT,listOf(ContentBlock.ToolUse("old","get_model_brief",buildJsonObject{}))),
            LlmMessage(Role.USER,listOf(ContentBlock.ToolResult("old",large))),
            LlmMessage(Role.USER,listOf(ContentBlock.Text("Nueva consulta"))),
            LlmMessage(Role.ASSISTANT,listOf(ContentBlock.ToolUse("new","get_model_brief",buildJsonObject{}))),
            LlmMessage(Role.USER,listOf(ContentBlock.ToolResult("new",large))))
        val result=ExpertPolicy.compact(request("x").copy(messages=messages))
        assertEquals(messages.first(),result.messages.first())
        assertTrue((result.messages[2].content.first() as ContentBlock.ToolResult).content.toString().length<1000)
        assertEquals(messages.last(),result.messages.last())
    }
}

