package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.*
import com.vidadomu.nexus.ui.create.*
import com.vidadomu.nexus.ui.models.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ReferenceBatchTest {
    private val catalog = AtlasModelMapper.parseModelsResponse(javaClass.getResource("/native/catalog-public.json")!!.readText())
    private fun native(id: String): NexusModel {
        val model = catalog.first { it.id == id }
        return NativeModelSchema.decode(model, javaClass.getResource("/native/"+id.replace('/', '_')+".json")!!.readText()).model
    }
    private fun images(n: Int) = (1..n).map { AttachedMedia(uriString="file:///local/ref$it.png", mimeType="image/png", slotType=MediaSlotType.REFERENCE_IMAGE) }

    @Test fun screenshotRegressionThreeReferencesReachAllCorrectNativePayloads() {
        val routes = listOf("bytedance/seedream-v5.0-pro/edit", "openai/gpt-image-2.5-sunburst/edit", "black-forest-labs/flux-2-pro/edit", "youchuan/v8.2/blend")
        routes.forEach { id ->
            val model = native(id)
            assertNull(ComparisonPlan.referenceProblem(model, images(3)))
            val plan = ComparisonPlan.adapt(CreateUiState(prompt="Mantén la persona y cambia el fondo", attachedMedia=images(3)), model).state
            assertEquals(3, plan.attachedMedia.size)
            val request = NativeGenerationRequest.build(model, Modality.IMAGE, plan.prompt, plan.parameters,
                plan.attachedMedia.mapIndexed { i,m -> MediaInput(Modality.IMAGE,m.uriString,m.mimeType,
                    remoteUrl="https://example.invalid/ref$i.png",slotType=m.slotType,fieldKey=m.fieldKey) }, "offline-test")
            val payload = AtlasGenerationPayload.build(request)
            assertEquals(3, payload[model.imagesArrayField]!!.jsonArray.size)
            assertEquals(plan.prompt, payload["prompt"]!!.jsonPrimitive.content)
            assertEquals(id, payload["model"]!!.jsonPrimitive.content)
        }
    }
    @Test fun equivalentFormatPreservesExactPixelSizeWhenAvailable() {
        val target=native("openai/gpt-image-2.5-sunburst/edit")
        val state=ComparisonPlan.adapt(CreateUiState(prompt="Conservar encuadre",attachedMedia=images(3),parameters=mapOf("size" to "2048*2048")),target).state
        assertEquals("2048x2048",state.parameters["size"])
        val flux=native("black-forest-labs/flux-2-pro/edit")
        val wide=ComparisonPlan.adapt(CreateUiState(prompt="Conservar encuadre",attachedMedia=images(3),parameters=mapOf("aspect_ratio" to "16:9")),flux).state
        assertEquals("1280*720",wide.parameters["size"])
    }
    @Test fun textRoutesAndSingleImageLimitAreDetectedBeforeReview() {
        val text = native("bytedance/seedream-v5.0-pro/text-to-image")
        assertFalse(ImageModelChoices.acceptsReferences(text))
        assertNotNull(ComparisonPlan.referenceProblem(text,images(3)))
        val single = native("youchuan/v8.2/image-to-image")
        val blend = native("youchuan/v8.2/blend")
        assertNotNull(ComparisonPlan.referenceProblem(blend,images(1)))
        assertNull(ComparisonPlan.referenceProblem(blend,images(3)))
        assertEquals(1,ComparisonPlan.referenceLimit(single))
        assertNull(ComparisonPlan.referenceProblem(single,images(1)))
        assertNotNull(ComparisonPlan.referenceProblem(single,images(3)))
        assertEquals(10,ComparisonPlan.referenceLimit(native("bytedance/seedream-v5.0-pro/edit")))
        assertEquals(8,ComparisonPlan.referenceLimit(native("black-forest-labs/flux-2-pro/edit")))
    }
    @Test fun shortlistPrioritizesFamiliesLatestVersionsAndEditingWithoutHidingAlternatives() {
        val references = catalog.filter(ComparisonPlan::eligible).filter(ImageModelChoices::acceptsReferences)
        val selected = ImageModelChoices.shortlist(references)
        assertEquals(listOf("Seedream","GPT Image","FLUX","Midjourney","Nano Banana"), selected.map(::catalogFamily))
        assertEquals("bytedance/seedream-v5.0-pro/edit",selected[0].id)
        assertTrue(selected[1].id.contains("2.5"))
        assertFalse(selected[1].id.contains("developer"))
        assertEquals(selected.size,selected.distinctBy(::catalogFamily).size)
        assertTrue(references.size > selected.size)
        assertTrue(selected.all { CatalogWorkflow.CREATE_IMAGE !in catalogWorkflows(it) })
    }
    @Test fun restoredTextSelectionCannotPassReferenceReview() {
        val m=native("bytedance/seedream-v5.0-pro/edit");val key=ComparisonPlan.key(m)
        val s=ComparisonState(source=CreateUiState(prompt="prompt",attachedMedia=images(3)),catalog=listOf(m),selected=setOf(key))
        assertFalse(s.canReview)
        assertTrue(s.copy(checked=setOf(key)).canReview)
        assertFalse(s.copy(checked=setOf(key),selectionProblems=mapOf(key to "incompatible")).canReview)
        assertFalse(s.copy(checked=setOf(key),checking=setOf(key)).canReview)
        assertFalse(s.copy(checked=setOf(key),source=s.source.copy(attachedMedia=emptyList())).canReview)
    }
}
