package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.*
import com.vidadomu.nexus.provider.runway.*
import com.vidadomu.nexus.ui.create.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ComparisonPlanTest {
    private val ratio = ModelParameter("aspect_ratio", "Formato", ParameterType.ENUM, "1:1", options = listOf("1:1", "16:9"))
    private fun model(id: String = "image", parameters: List<ModelParameter> = listOf(ratio)) = NexusModel(
        id, "Modelo $id", "fixture", organization = "Fixture", modalities = listOf(Modality.IMAGE),
        capabilities = listOf(ModelCapability.TEXT_TO_IMAGE), parameters = parameters)
    private fun source() = CreateUiState(selectedModel = model(), prompt = "Una escultura de cerámica azul", schemaReady = true,
        parameters = mapOf("aspect_ratio" to "16:9"))
    private fun image(index: Int = 0) = AttachedMedia(id = "$index", uriString = "file:///fixture/$index.png",
        mimeType = "image/png", slotType = MediaSlotType.REFERENCE_IMAGE, fieldKey = "oldImages", remoteUrl = "https://example.invalid/old-provider")

    @Test fun retainsPromptAndMapsEquivalentAspectRatioToExactNativeKey() {
        val target = model(parameters = listOf(ratio.copy(key = "ratio", options = listOf("1024:1024", "1920:1080"), defaultValue = "1024:1024")))
        val prepared = ComparisonPlan.adapt(source(), target)
        assertEquals(source().prompt, prepared.state.prompt)
        assertEquals(mapOf("ratio" to "1920:1080"), prepared.state.parameters)
        assertTrue(prepared.changes.single().contains("16:9"))
        assertTrue(prepared.changes.single().contains("1920 × 1080 px"))
    }
    @Test fun incompatibleOptionsAreDisclosedAndNeverSentBlindly() {
        val target = model(parameters = listOf(ratio.copy(options = listOf("1:1"))))
        val prepared = ComparisonPlan.adapt(source().copy(parameters = source().parameters + ("negative_prompt" to "blur")), target)
        assertEquals("1:1", prepared.state.parameters["aspect_ratio"])
        assertTrue(prepared.changes.any { "16:9" in it })
        assertTrue(prepared.changes.any { "negative_prompt" in it })
        assertFalse(prepared.state.parameters.containsKey("negative_prompt"))
    }
    @Test fun neverSilentlyDropsReferencesOrReusesAnotherProvidersUploadedUrl() {
        val target = model().copy(contractEndpoint = "image", contractMedia = listOf(ContractMediaField("referenceImages", "Referencias", "image/*", maximum = 2, encoding = "object-array")))
        val prepared = ComparisonPlan.adapt(source().copy(attachedMedia = listOf(image(), image(1))), target)
        assertEquals(2, prepared.state.attachedMedia.size)
        assertTrue(prepared.state.attachedMedia.all { it.fieldKey == "referenceImages" && it.remoteUrl == null })
        assertThrows(IllegalArgumentException::class.java) { ComparisonPlan.adapt(source().copy(attachedMedia = listOf(image(), image(1), image(2))), target) }
        assertThrows(IllegalArgumentException::class.java) { ComparisonPlan.adapt(source().copy(attachedMedia = listOf(image())), model()) }
    }
    @Test fun rejectsMaskVideoAndMissingRequiredReferences() {
        val s = source()
        assertThrows(IllegalArgumentException::class.java) { ComparisonPlan.adapt(s.copy(attachedMedia = listOf(image().copy(fieldKey = "mask"))), model()) }
        assertThrows(IllegalArgumentException::class.java) { ComparisonPlan.adapt(s.copy(attachedMedia = listOf(image().copy(mimeType = "video/mp4"))), model()) }
        val target = model().copy(contractEndpoint = "edit", contractMedia = listOf(ContractMediaField("image", "Imagen", "image/*", required = true)))
        assertThrows(IllegalArgumentException::class.java) { ComparisonPlan.adapt(s, target) }
    }
    @Test fun exactAtlasImageArrayAndRunwayPayloadsRemainIndependent() {
        val atlas = NativeModelSchema.decode(model("atlas-image").copy(providerId = "atlas_cloud"),
            """{"components":{"schemas":{"Input":{"required":["prompt","images"],"properties":{"prompt":{"type":"string"},"images":{"type":"array","items":{"type":"string"},"maxItems":4},"aspect_ratio":{"type":"string","enum":["1:1","16:9"],"default":"1:1"}}}}}}""").model
        val runway = model("runway-image").copy(providerId = "runway", providerModelId = "model-native", contractEndpoint = "image",
            contractMedia = listOf(ContractMediaField("referenceImages", "Referencias", "image/*", maximum = 4, encoding = "object-array")))
        val input = source().copy(attachedMedia = listOf(image(), image(1)))
        fun request(target: NexusModel): GenerationRequest {
            val p = ComparisonPlan.adapt(input, target).state
            return NativeGenerationRequest.build(target, Modality.IMAGE, p.prompt, p.parameters,
                p.attachedMedia.map { MediaInput(Modality.IMAGE, it.uriString, it.mimeType, remoteUrl = "https://example.invalid/fixture.png", slotType = it.slotType, fieldKey = it.fieldKey) }, "offline-test")
        }
        val atlasPayload = AtlasGenerationPayload.build(request(atlas))
        val runwayPayload = ContractPayload.encode(runway, request(runway))
        assertEquals(2, atlasPayload["images"]!!.jsonArray.size)
        assertEquals(2, runwayPayload["referenceImages"]!!.jsonArray.size)
        assertEquals(input.prompt, atlasPayload["prompt"]!!.jsonPrimitive.content)
        assertEquals(input.prompt, runwayPayload["promptText"]!!.jsonPrimitive.content)
        assertFalse(atlasPayload.containsKey("referenceImages"))
        assertFalse(runwayPayload.containsKey("images"))
        assertTrue((atlasPayload.keys+runwayPayload.keys).none { it.startsWith("__comparison_") })
    }
    @Test fun oneOutputWhenCountIsExposedAndNoInvalidFixedCounts() {
        val count = ModelParameter("num_images", "Imágenes", ParameterType.INTEGER, "4", minValue = 1f, maxValue = 4f)
        val p = ComparisonPlan.adapt(source().copy(parameters = mapOf("num_images" to "4")), model(parameters = listOf(count)))
        assertEquals("1", p.state.parameters["num_images"])
        assertTrue(p.changes.any { "1 por solicitud" in it })
        assertThrows(IllegalArgumentException::class.java) { ComparisonPlan.adapt(source(), model(parameters = listOf(count.copy(minValue = 4f)))) }
    }
    @Test fun totalsKeepCreditsSeparateAndUnknownIsNeverZero() {
        val usd = ComparisonCost(.05, ComparisonCurrency.USD, "fixture")
        val credits = ComparisonCost(10.0, ComparisonCurrency.CRUN_CREDITS, "fixture")
        assertEquals(mapOf(ComparisonCurrency.USD to .1, ComparisonCurrency.CRUN_CREDITS to 10.0), comparisonTotal(listOf(usd,usd,credits)))
        assertNull(comparisonTotal(listOf(usd,null)))
        assertNull(comparisonTotal(emptyList()))
        assertThrows(IllegalArgumentException::class.java) { ComparisonCost(Double.NaN, ComparisonCurrency.USD, "invalid") }
    }
    @Test fun unknownCostAllowsSendingButDuplicateModelsDoNot() {
        val plan = ComparisonPlan.adapt(source(), model())
        val priced = ComparisonEntry(model(), plan, ComparisonCost(.1, ComparisonCurrency.USD, "fixture"))
        val second = priced.copy(model = model("second"))
        assertTrue(ComparisonState(phase = ComparisonPhase.REVIEW, entries = listOf(priced)).canSend)
        assertTrue(ComparisonState(phase = ComparisonPhase.REVIEW, entries = listOf(priced, second.copy(cost = null))).canSend)
        assertFalse(ComparisonState(phase = ComparisonPhase.REVIEW, entries = listOf(priced, priced)).canSend)
        assertTrue(ComparisonState(phase = ComparisonPhase.REVIEW, entries = listOf(priced, second)).canSend)
    }
    @Test fun eligibilityUsesContractsNotNames() {
        assertFalse(ComparisonPlan.eligible(model("Seedream GPT Flux").copy(capabilities = emptyList())))
        assertTrue(ComparisonPlan.eligible(model("brand-independent").copy(capabilities = emptyList(), contractEndpoint = "image", promptRequired = true)))
        assertFalse(ComparisonPlan.eligible(model().copy(modalities = listOf(Modality.VIDEO))))
    }
    @Test fun queueDispatchesEachRequestExactlyOnceWithoutNetwork() = runBlocking {
        val queue = ComparisonQueue()
        val sent = mutableListOf<Int>()
        queue.run(listOf(1,2,3,4)) { _, item -> sent += item }
        assertEquals(listOf(1,2,3,4), sent)
        try { queue.run(listOf(1,2)) { _,item -> sent += item }; fail("Must reject repeated submission") }
        catch(_: IllegalStateException) { }
        assertEquals(4, sent.size)
    }
    @Test fun stopOnlyPreventsUnsentRequestsAndFailureNeverRetries() = runBlocking {
        val queue = ComparisonQueue()
        val sent = mutableListOf<Int>()
        queue.run(listOf(1,2,3,4)) { _,item -> sent += item; queue.stop() }
        assertEquals(listOf(1), sent)
        val other = ComparisonQueue()
        sent.clear()
        try { other.run(listOf(1,2,3)) { _,item -> sent += item; error("uncertain submission") } }
        catch(_: IllegalStateException) { }
        assertEquals(listOf(1), sent)
    }
    @Test fun productionCatalogContainsTheFourOfflineComparisonRoutes() {
        val ids = setOf("seedream5_pro", "gpt_image_2", "gemini_image3_pro", "seedream5_lite")
        assertEquals(4, RunwayContracts.models.count { it.providerModelId in ids && ComparisonPlan.eligible(it) })
    }
    @Test fun interruptedDispatchRequiresHumanReviewAndNeverBecomesQueuedAgain() {
        val pending = GenerationJob("interrupted", providerId = "fixture", modelId = "image", modality = Modality.IMAGE,
            prompt = "fixture", parameters = mapOf(ComparisonPlan.ID to "local-batch"))
        val recovered = pending.recoverInterruptedComparison()
        assertEquals(GenerationStatus.FAILED, recovered.status)
        assertEquals("true", recovered.parameters["__submission_uncertain"])
        assertEquals("local-batch", recovered.parameters[ComparisonPlan.ID])
        assertEquals(recovered, recovered.recoverInterruptedComparison())
        val accepted = pending.copy(providerJobId = "accepted")
        assertSame(accepted, accepted.recoverInterruptedComparison())
        val olderTask = pending.copy(parameters = emptyMap())
        assertSame(olderTask, olderTask.recoverInterruptedComparison())
    }
    @Test fun mixedQuantitiesHaveExactTotalsAndRemainIndependentFromNativeOutputCount() {
        val count = ModelParameter("num_images", "Imágenes", ParameterType.INTEGER, "4", minValue = 1f, maxValue = 4f)
        val target = model(parameters = listOf(count))
        val plan = ComparisonPlan.adapt(source(), target)
        val entries = listOf(
            ComparisonEntry(target, plan, ComparisonCost(.1, ComparisonCurrency.USD, "unit"), quantity = 4),
            ComparisonEntry(model("second"), plan, ComparisonCost(.2, ComparisonCurrency.USD, "unit"), quantity = 3))
        val state = ComparisonState(phase = ComparisonPhase.REVIEW, entries = entries)
        assertTrue(state.canSend)
        assertEquals(7, state.requestCount)
        assertEquals(1.0, state.total!![ComparisonCurrency.USD]!!, 0.00001)
        assertEquals("1", entries.first().prepared!!.state.parameters["num_images"])
        assertNull(state.copy(entries = entries.map { it.copy(cost = null) }).total)
        assertFalse(state.copy(entries = listOf(entries.first().copy(quantity = 0))).canSend)
        assertFalse(state.copy(entries = listOf(entries.first().copy(quantity = 21))).canSend)
        assertTrue(state.copy(maximumPerModel=30,entries = listOf(entries.first().copy(quantity = 21))).canSend)
    }
    @Test fun expandedBatchIsFairAndSupportsSingleModelAndMaximumSize() {
        assertEquals(listOf(ComparisonTask(0,1),ComparisonTask(1,1),ComparisonTask(0,2),ComparisonTask(0,3)), ComparisonPlan.requests(listOf(3,1)))
        assertEquals(5, ComparisonPlan.requests(listOf(5)).size)
        val maximum = ComparisonPlan.requests(List(6) { 20 })
        assertEquals(120, maximum.size)
        assertEquals(120, maximum.distinct().size)
        for(counts in listOf(emptyList(),listOf(0),listOf(-1),listOf(21),List(7) { 1 }))
            assertThrows(IllegalArgumentException::class.java) { ComparisonPlan.requests(counts) }
    }
    @Test fun mixedBatchStopsInsideModelWithoutDuplicatingOrRestarting() = runBlocking {
        val queue = ComparisonQueue()
        val sent = mutableListOf<ComparisonTask>()
        queue.run(ComparisonPlan.requests(listOf(4,3))) { _,task ->
            sent += task
            if(sent.size == 3) queue.stop()
        }
        assertEquals(listOf(ComparisonTask(0,1),ComparisonTask(1,1),ComparisonTask(0,2)), sent)
        val single = mutableListOf<ComparisonTask>()
        ComparisonQueue().run(ComparisonPlan.requests(listOf(1))) { _,task -> single += task }
        assertEquals(listOf(ComparisonTask(0,1)), single)
    }

}
