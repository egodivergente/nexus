package com.vidadomu.nexus.ui.create.capability

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.NativeModelSchema
import com.vidadomu.nexus.provider.flow.FlowReferences
import com.vidadomu.nexus.ui.create.CreateUiState
import com.vidadomu.nexus.ui.create.creationReadiness
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Uses the repository's reviewed provider snapshots; never calls a provider or generates media. */
class CapabilityDrivenUiTest {
    @Test fun malformedAdapterStepsAreUnknownRatherThanSilentlyIgnored() {
        for(step in listOf(0f,-1f,Float.NaN,Float.POSITIVE_INFINITY)) {
            val model=FlowReferences.model(Modality.IMAGE).copy(parameters=listOf(
                ModelParameter("duration","Duración",ParameterType.FLOAT,step=step)))
            val cap=capabilities(model)
            assertEquals(CapabilitySupport.UNKNOWN,cap.support)
            assertFalse(CapabilityConstraintEngine.evaluate(cap,"Original",emptyMap(),emptyList()).canSubmit)
        }
    }
    private fun fixture(name: String, modality: Modality = Modality.VIDEO): NexusModel {
        val payload = javaClass.getResource("/native/$name")!!.readText(Charsets.UTF_8)
        val input = Json.parseToJsonElement(payload).jsonObject["components"]!!.jsonObject["schemas"]!!.jsonObject["Input"]!!.jsonObject
        val id = input["properties"]!!.jsonObject["model"]!!.jsonObject["default"]!!.jsonPrimitive.content
        val base = NexusModel(id, id, "atlas_cloud", organization = "Fixture", modalities = listOf(modality),
            capabilities = emptyList(), parameters = emptyList(), schemaUrl = "fixture:native/$name")
        return NativeModelSchema.decode(base, payload).model
    }
    private fun capabilities(model: NexusModel, values: Map<String,String> = emptyMap(), media: List<AttachedMedia> = emptyList()) =
        ModelCapabilityRegistry.resolve(model, model.modalities.first(), values, schemaReady = true, media = media)
    private fun image(slot: MediaSlotType = MediaSlotType.PRIMARY_IMAGE, key: String? = null) =
        AttachedMedia(uriString = "content://fixture/${java.util.UUID.randomUUID()}", mimeType = "image/png", slotType = slot, fieldKey = key)
    private fun video() = AttachedMedia(uriString = "content://fixture/video", mimeType = "video/mp4", slotType = MediaSlotType.VIDEO_INPUT)
    private fun evaluate(model: NexusModel, media: List<AttachedMedia> = emptyList(), values: Map<String,String> = emptyMap(), prompt: String = "A calm scene") =
        CapabilityConstraintEngine.evaluate(capabilities(model, values, media), prompt, values, media)

    @Test fun textOnlyUsesPublishedDefaultsAndDoesNotInventInputs() {
        val model = fixture("alibaba_wan-3.0-prime_text-to-video.json")
        val cap = capabilities(model)
        assertEquals(CapabilitySupport.CONFIRMED, cap.support)
        assertTrue(cap.inputFields.isEmpty())
        assertTrue(cap.promptVisible)
        assertEquals("prompt", cap.prompt.fieldKey)
        assertEquals("1080p", cap.defaults["resolution"])
        assertTrue(cap.primaryParameters.size <= 4)
        assertEquals(cap.declaredParameters.map {it.key}.toSet(), (cap.primaryParameters + cap.advancedParameters).map {it.key}.toSet())
        assertTrue(evaluate(model).canSubmit)
    }

    @Test fun imageToVideoRequiresTheActualImageSlot() {
        val model = fixture("kling-i2v.json")
        assertTrue(evaluate(model).issues.any {it.kind == CapabilityIssueKind.MEDIA && it.fieldKey == "image"})
        assertTrue(evaluate(model, listOf(image())).canSubmit)
    }

    @Test fun startAndEndFramesKeepTheirSeparateRoles() {
        val model = fixture("kling-i2v.json")
        val cap = capabilities(model)
        assertEquals(setOf("image", "end_image"), cap.inputFields.map {it.key}.toSet())
        val start = image()
        val end = image(MediaSlotType.LAST_FRAME)
        assertTrue(evaluate(model, listOf(start, end)).canSubmit)
        assertFalse(evaluate(model, listOf(end)).canSubmit)
    }

    @Test fun multipleReferencesEnforcePublishedMinimumAndMaximumWithoutDroppingFiles() {
        val model = fixture("youchuan_v8.2_blend.json", Modality.IMAGE)
        val refs = List(6) {image(MediaSlotType.REFERENCE_IMAGE)}
        val input = capabilities(model).inputs.single()
        assertEquals(2, input.minimum)
        assertEquals(5, input.maximum)
        assertFalse(evaluate(model, refs.take(1)).canSubmit)
        assertTrue(evaluate(model, refs.take(2)).canSubmit)
        val overflow = evaluate(model, refs)
        assertFalse(overflow.canSubmit)
        assertEquals(refs, overflow.configuration.media)
    }

    @Test fun videoInputAndImageInputAreValidatedIndependently() {
        val model = fixture("kling-motion.json")
        val values = mapOf("character_orientation" to "video")
        assertTrue(evaluate(model, listOf(image(), video()), values).canSubmit)
        assertFalse(evaluate(model, listOf(image()), values).canSubmit)
        assertFalse(evaluate(model, listOf(image(), video().copy(mimeType = "image/png")), values).canSubmit)
    }

    // Synthetic regression schema decoded by the production parser, not a claim about a provider's limits.
    private fun optionalMediaContract(minimum: Int = 1, requiredAudio: Boolean = false,
        conditionalAudio: Boolean = false): NexusModel {
        val id = "optional-media-regression-$minimum-$requiredAudio-$conditionalAudio"
        val base = NexusModel(id, "Optional media engine fixture", "atlas_cloud", organization = "Test fixture",
            modalities = listOf(Modality.VIDEO), capabilities = emptyList(), parameters = emptyList(), schemaUrl = "fixture:$id")
        val required = if (requiredAudio) "[\"prompt\",\"images\",\"reference_audios\"]" else "[\"prompt\",\"images\"]"
        val conditional = if (conditionalAudio) """,
          "allOf":[{"if":{"properties":{"use_audio":{"const":true}},"required":["use_audio"]},
            "then":{"required":["reference_audios"]}}]
        """ else ""
        val payload = """{"components":{"schemas":{"Input":{
          "properties":{
            "model":{"type":"string","default":"$id"},
            "prompt":{"type":"string"},
            "images":{"type":"array","items":{"type":"string"},"minItems":1,"maxItems":3},
            "reference_videos":{"type":"array","items":{"type":"string"},"minItems":$minimum,"maxItems":3},
            "reference_audios":{"type":"array","items":{"type":"string"},"minItems":$minimum,"maxItems":3},
            "use_audio":{"type":"boolean","default":false}
          },"required":$required $conditional
        }}}}"""
        return NativeModelSchema.decode(base, payload).model
    }

    private fun referenceAudio() = AttachedMedia(uriString = "content://fixture/${java.util.UUID.randomUUID()}",
        mimeType = "audio/mpeg", slotType = MediaSlotType.REFERENCE_IMAGE, fieldKey = "reference_audios")
    private fun referenceVideo() = video().copy(slotType = MediaSlotType.VIDEO_REFERENCE)

    @Test fun absentOptionalAudioAndVideoMinimaDoNotBlockRealReadinessWithRequiredImage() {
        val model = optionalMediaContract()
        val media = listOf(image(MediaSlotType.REFERENCE_IMAGE))
        val cap = capabilities(model, media = media)
        for (key in listOf("reference_audios", "reference_videos")) {
            val input = cap.inputs.single {it.field.key == key}
            assertFalse(input.field.required)
            assertEquals(1, input.minimum)
        }
        val validation = evaluate(model, media)
        assertTrue(validation.issues.toString(), validation.canSubmit)
        val readiness = creationReadiness(CreateUiState(selectedModality = Modality.VIDEO, selectedModel = model,
            prompt = "A calm scene", attachedMedia = media, schemaReady = true))
        assertTrue(readiness.message, readiness.canSubmit)
        assertNull(NativeModelSchema.validate(model, validation.configuration.parameters + ("prompt" to "A calm scene")))
        val missingImage = evaluate(model)
        assertTrue(missingImage.issues.any {it.kind == CapabilityIssueKind.MEDIA && it.fieldKey == "images"})
        assertFalse(missingImage.issues.any {it.fieldKey in setOf("reference_audios", "reference_videos")})
    }

    @Test fun presentOptionalMediaStillEnforcesItsPublishedMinimumWithoutDroppingFiles() {
        val model = optionalMediaContract(minimum = 2)
        val requiredImage = image(MediaSlotType.REFERENCE_IMAGE)
        for (optional in listOf(listOf(referenceAudio(), referenceAudio()), listOf(referenceVideo(), referenceVideo()))) {
            val incompleteMedia = listOf(requiredImage, optional.first())
            val incomplete = evaluate(model, incompleteMedia)
            val key = if (optional.first().mimeType.startsWith("audio/")) "reference_audios" else "reference_videos"
            assertFalse(incomplete.canSubmit)
            assertTrue(incomplete.issues.any {it.kind == CapabilityIssueKind.MEDIA && it.fieldKey == key})
            assertEquals(incompleteMedia, incomplete.configuration.media)
            val complete = evaluate(model, listOf(requiredImage) + optional)
            assertTrue(complete.issues.toString(), complete.canSubmit)
        }
    }

    @Test fun explicitlyRequiredMediaRemainsRequiredWithTheSameMinimum() {
        val model = optionalMediaContract(requiredAudio = true)
        val media = listOf(image(MediaSlotType.REFERENCE_IMAGE))
        assertTrue(capabilities(model).inputs.single {it.field.key == "reference_audios"}.field.required)
        val absent = evaluate(model, media)
        assertFalse(absent.canSubmit)
        assertTrue(absent.issues.any {it.kind == CapabilityIssueKind.MEDIA && it.fieldKey == "reference_audios"})
        val supplied = evaluate(model, media + referenceAudio())
        assertTrue(supplied.issues.toString(), supplied.canSubmit)
    }

    @Test fun conditionalRequiredMediaStillBlocksOnlyWhenItsBranchApplies() {
        val model = optionalMediaContract(conditionalAudio = true)
        val media = listOf(image(MediaSlotType.REFERENCE_IMAGE))
        assertTrue(evaluate(model, media, mapOf("use_audio" to "false")).canSubmit)
        val values = mapOf("use_audio" to "true")
        assertTrue(capabilities(model, values, media).inputs.single {it.field.key == "reference_audios"}.field.required)
        val absent = evaluate(model, media, values)
        assertFalse(absent.canSubmit)
        assertTrue(absent.issues.any {it.kind == CapabilityIssueKind.MEDIA && it.fieldKey == "reference_audios"})
        val supplied = evaluate(model, media + referenceAudio(), values)
        assertTrue(supplied.issues.toString(), supplied.canSubmit)
    }

    @Test fun optionalMediaMaximumAndMimeRemainValidatedWhenPresent() {
        val model = optionalMediaContract()
        val requiredImage = image(MediaSlotType.REFERENCE_IMAGE)
        for (optional in listOf(List(4) {referenceAudio()}, List(4) {referenceVideo()})) {
            val media = listOf(requiredImage) + optional
            val overflow = evaluate(model, media)
            assertFalse(overflow.canSubmit)
            assertEquals(media, overflow.configuration.media)
            val wrongMime = evaluate(model, listOf(requiredImage, optional.first().copy(mimeType = "image/png")))
            assertFalse(wrongMime.canSubmit)
            assertTrue(wrongMime.issues.any {it.kind == CapabilityIssueKind.MEDIA && "tipo de archivo" in it.message})
        }
    }

    @Test fun publishedDependenciesKeepLegacyDefaultReconciliationButRejectChosenInvalidValues() {
        val model = fixture("tencent_video_upscaler.json")
        val valid = evaluate(model, listOf(video()), mapOf("fps" to "24"))
        assertTrue(valid.issues.toString(), valid.canSubmit)
        assertFalse("short" in valid.configuration.parameters)
        assertEquals("0", valid.configuration.inactiveParameters["short"])
        val invalid = evaluate(model, listOf(video()), mapOf("fps" to "24", "short" to "1000"))
        assertTrue(invalid.issues.any {it.kind == CapabilityIssueKind.DEPENDENCY && it.fieldKey == "short"})
    }

    @Test fun durationAndResolutionUseEachModelsOwnPublishedOptions() {
        val model = fixture("alibaba_wan-3.0-prime_text-to-video.json")
        val values = mapOf("resolution" to "4k-esr", "duration" to "30")
        assertTrue(evaluate(model, values = values).canSubmit)
        assertFalse(evaluate(model, values = values + ("duration" to "31")).canSubmit)
        assertFalse(evaluate(model, values = values + ("resolution" to "invented")).canSubmit)
    }

    @Test fun unknownContractBlocksExecutionWithoutInventedCapabilitiesOrDate() {
        val model = NexusModel("unknown", "Unknown", "unconfigured", organization = "Unknown",
            modalities = listOf(Modality.VIDEO), capabilities = emptyList(), parameters = emptyList())
        val cap = capabilities(model)
        assertEquals(CapabilitySupport.UNKNOWN, cap.support)
        assertEquals(CapabilitySupport.UNKNOWN, cap.fieldSupport("duration"))
        assertNull(cap.provenance.recordedAt)
        assertFalse(CapabilityConstraintEngine.evaluate(cap, "original", emptyMap(), emptyList()).canSubmit)
    }

    @Test fun switchingToAnIncompatibleModelPreservesPromptResourcesAndPerModelValues() {
        val model = fixture("alibaba_wan-3.0-prime_text-to-video.json")
        val cap = capabilities(model)
        val media = listOf(video())
        val composer = ComposerState("Original full prompt", media).withValues(cap.key, mapOf("duration" to "30"))
        val result = composer.evaluate(cap)
        assertFalse(result.canSubmit)
        assertEquals("Original full prompt", composer.prompt)
        assertEquals(media, composer.resources)
        assertEquals("30", composer.preferences[cap.key]!!.values["duration"])
        assertEquals(media, result.configuration.media)
    }

    @Test fun multipleModelsKeepIndependentDurationsWithoutACommonMinimum() {
        val wan = capabilities(fixture("alibaba_wan-3.0-prime_text-to-video.json"))
        val kling = capabilities(fixture("kling-t2v.json"))
        val composer = ComposerState("One original prompt")
            .withValues(wan.key, mapOf("duration" to "30"))
            .withValues(kling.key, mapOf("duration" to "15"))
        val results = composer.evaluateAll(listOf(wan, kling))
        assertTrue(results.values.all {it.canSubmit})
        assertEquals("30", results.getValue(wan.key).configuration.parameters["duration"])
        assertEquals("15", results.getValue(kling.key).configuration.parameters["duration"])
    }

    @Test fun actualPromptUnicodeLimitIsEnforcedWithoutTruncatingTheOriginal() {
        val model = fixture("kling-t2v.json")
        val emoji = "\uD83C\uDF0C"
        assertTrue(evaluate(model, prompt = emoji.repeat(2500)).canSubmit)
        val original = emoji.repeat(2501)
        val rejected = evaluate(model, prompt = original)
        assertFalse(rejected.canSubmit)
        assertEquals(original, rejected.configuration.originalPrompt)
    }

    @Test fun conditionalThenElseRequiredAndVisibilityComeFromTheFixture() {
        val model = fixture("kling-t2v.json")
        val simple = capabilities(model, mapOf("multi_shot" to "false"))
        assertFalse((simple.primaryParameters + simple.advancedParameters).any {it.key == "shot_type"})
        assertFalse(evaluate(model, prompt = "").canSubmit)
        val missingType = evaluate(model, values = mapOf("multi_shot" to "true"))
        assertTrue(missingType.issues.any {it.fieldKey == "shot_type"})
        val custom = evaluate(model, values = mapOf("multi_shot" to "true", "shot_type" to "customize"))
        assertTrue(custom.issues.any {it.kind == CapabilityIssueKind.UNKNOWN && it.fieldKey == "multi_prompt"})
    }

    @Test fun conditionalOutputFormatUsesExistingPublishedSchema() {
        val model = fixture("openai_gpt-image-2.5-sunburst_text-to-image.json", Modality.IMAGE)
        val cap = capabilities(model, mapOf("background" to "transparent"))
        val format = (cap.primaryParameters + cap.advancedParameters).single {it.key == "output_format"}
        assertEquals(listOf("png", "webp"), format.options)
        assertFalse(evaluate(model, values = mapOf("background" to "transparent", "output_format" to "jpeg")).canSubmit)
    }

    @Test fun declaredNonAtlasAdapterRemainsUsableWithoutInventedRemoteVerification() {
        val model = FlowReferences.model(Modality.IMAGE)
        val cap = capabilities(model)
        assertEquals(CapabilitySupport.CONFIRMED, cap.support)
        assertEquals(CapabilityVerification.DECLARED_ADAPTER, cap.provenance.verification)
        assertNull(cap.provenance.recordedAt)
        assertNull(cap.inputs.single().maximum)
        assertTrue(evaluate(model).canSubmit)
    }

    @Test fun registryKeysSeparateProviderEndpointAndModality() {
        val base = fixture("kling-t2v.json")
        val first = ModelCapabilityRegistry.key(base, Modality.VIDEO)
        assertNotEquals(first, ModelCapabilityRegistry.key(base.copy(providerId = "other"), Modality.VIDEO))
        assertNotEquals(first, ModelCapabilityRegistry.key(base.copy(contractEndpoint = "/other"), Modality.VIDEO))
        assertNotEquals(first, ModelCapabilityRegistry.key(base, Modality.IMAGE))
    }

    @Test fun hiddenValuesStayInTheDraftAndCannotActivateAnotherRequestBranch() {
        val model = fixture("kling-t2v.json")
        val values = mapOf("multi_shot" to "false", "shot_type" to "customize")
        val cap = capabilities(model, values)
        val result = CapabilityConstraintEngine.evaluate(cap, "Original prompt", values, emptyList())
        assertTrue(result.issues.toString(), result.canSubmit)
        assertFalse("shot_type" in result.configuration.parameters)
        assertEquals("customize", result.configuration.inactiveParameters["shot_type"])
        assertEquals("Original prompt", result.configuration.requestPrompt)
        assertTrue(cap.promptVisible)
    }

    // Synthetic engine fixture only. These limits are not claims about a production provider/model.
    private fun syntheticDurationContract(): Pair<NexusModel, JsonObject> {
        val schema = Json.parseToJsonElement("""{
          "properties":{"prompt":{"type":"string"},"duration":{"type":"integer","enum":[5,10]},"resolution":{"type":"string","enum":["720p","1080p"]}},
          "required":["prompt","duration","resolution"],
          "allOf":[{"if":{"properties":{"duration":{"const":10}},"required":["duration"]},
            "then":{"properties":{"resolution":{"enum":["720p"]}}},
            "else":{"properties":{"resolution":{"enum":["720p","1080p"]}}}}]
        }""").jsonObject
        val model = NexusModel("synthetic-duration-test", "Engine test only", "fixture", organization = "Test fixture",
            modalities = listOf(Modality.VIDEO), capabilities = emptyList(), parameters = listOf(
                ModelParameter("duration", "Duration", ParameterType.DURATION, "5", options = listOf("5","10"), isRequired = true, nativeType = "integer"),
                ModelParameter("resolution", "Resolution", ParameterType.RESOLUTION, "1080p", options = listOf("720p","1080p"), isRequired = true, nativeType = "string")),
            contractEndpoint = "/test-only", contractSource = "synthetic-engine-fixture", contractPromptField = "prompt")
        return model to schema
    }

    @Test fun syntheticDurationChangeImmediatelyRestrictsResolutionAndBlocksIncompatibleRequest() {
        val (model, schema) = syntheticDurationContract()
        fun resolve(values: Map<String,String>) = ModelCapabilityRegistry.resolve(model, Modality.VIDEO, values, inputSchema = schema)
        val short = resolve(mapOf("duration" to "5"))
        assertEquals(listOf("720p","1080p"), (short.primaryParameters + short.advancedParameters).single {it.key == "resolution"}.options)
        val values = mapOf("duration" to "10", "resolution" to "1080p")
        val long = resolve(values)
        assertEquals(listOf("720p"), (long.primaryParameters + long.advancedParameters).single {it.key == "resolution"}.options)
        val blocked = CapabilityConstraintEngine.evaluate(long, "Original prompt", values, emptyList())
        assertFalse(blocked.canSubmit)
        assertEquals("1080p", blocked.configuration.parameters["resolution"])
        assertEquals("Original prompt", blocked.configuration.originalPrompt)
        val compatible = values + ("resolution" to "720p")
        assertTrue(CapabilityConstraintEngine.evaluate(resolve(compatible), "Original prompt", compatible, emptyList()).canSubmit)
    }

    @Test fun syntheticNestedUnsupportedOperatorsRemainExplicitlyUnknown() {
        val (model, original) = syntheticDurationContract()
        val rule = original["allOf"]!!.jsonArray.single().jsonObject
        val nested = JsonObject(original + ("allOf" to JsonArray(listOf(JsonObject(rule + ("then" to Json.parseToJsonElement(
            """{"properties":{"resolution":{"enum":["720p"],"oneOf":[{"const":"720p"},{"const":"1080p"}]}}}""")))))))
        val cap = ModelCapabilityRegistry.resolve(model, Modality.VIDEO, inputSchema = nested)
        assertEquals(CapabilitySupport.UNKNOWN, cap.support)
        assertTrue(cap.unknownReasons.any {"oneOf" in it})
        assertFalse(cap.unknownReasons.any {it.startsWith("No hay un contrato")})
        val extraPredicate = JsonObject(original + ("allOf" to JsonArray(listOf(JsonObject(rule + ("if" to Json.parseToJsonElement(
            """{"properties":{"duration":{"const":10,"not":{"const":5}}},"required":["duration"]}""")))))))
        assertEquals(CapabilitySupport.UNKNOWN, ModelCapabilityRegistry.resolve(model, Modality.VIDEO, inputSchema = extraPredicate).support)
    }

    @Test fun simpleNullableMetadataDoesNotDisableAnAlreadyRepresentedScalar() {
        val (model, schema) = syntheticDurationContract()
        val nullable = JsonObject(schema + ("properties" to JsonObject(schema["properties"]!!.jsonObject +
            ("label" to Json.parseToJsonElement("""{"type":"string","anyOf":[{"type":"string"},{"type":"null"}]}""")))))
        val declared = model.copy(parameters = model.parameters + ModelParameter("label", "Label", ParameterType.TEXT))
        val cap = ModelCapabilityRegistry.resolve(declared, Modality.VIDEO, inputSchema = nullable)
        assertEquals(CapabilitySupport.CONFIRMED, cap.support)
    }

    @Test fun syntheticPublishedDecimalStepsAreExactAndNeverRoundTheUsersValue() {
        val (original, schema) = syntheticDurationContract()
        val model = original.copy(parameters = original.parameters.map {if(it.key == "duration")
            it.copy(type = ParameterType.FLOAT, nativeType = "number", options = emptyList(), defaultValue = "0.3") else it})
        val stepped = JsonObject(schema + ("properties" to JsonObject(schema["properties"]!!.jsonObject +
            ("duration" to Json.parseToJsonElement("""{"type":"number","multipleOf":0.1}""")))))
        fun evaluateStep(value: String): CapabilityValidation {
            val values = mapOf("duration" to value)
            val cap = ModelCapabilityRegistry.resolve(model, Modality.VIDEO, values, inputSchema = stepped)
            assertEquals(java.math.BigDecimal("0.1"), cap.declaredSteps["duration"])
            return CapabilityConstraintEngine.evaluate(cap, "Original prompt", values, emptyList())
        }
        assertTrue(evaluateStep("0.3").canSubmit)
        val rejected = evaluateStep("0.35")
        assertFalse(rejected.canSubmit)
        assertEquals("0.35", rejected.configuration.parameters["duration"])
        assertEquals("Original prompt", rejected.configuration.originalPrompt)
    }

    @Test fun declaredAdapterStepsUseTheirPublishedMinimumAsOrigin() {
        val (original, schema) = syntheticDurationContract()
        val model = original.copy(parameters = original.parameters.map {if(it.key == "duration")
            it.copy(type = ParameterType.FLOAT, nativeType = "number", options = emptyList(), defaultValue = "1.25", minExact = "1", step = .25f) else it})
        val declaredSchema = JsonObject(schema + ("properties" to JsonObject(schema["properties"]!!.jsonObject +
            ("duration" to Json.parseToJsonElement("""{"type":"number","minimum":1}""")))))
        fun evaluateStep(value: String): CapabilityValidation {
            val values = mapOf("duration" to value)
            val cap = ModelCapabilityRegistry.resolve(model, Modality.VIDEO, values, inputSchema = declaredSchema)
            assertEquals(java.math.BigDecimal("0.25"), cap.declaredSteps["duration"])
            return CapabilityConstraintEngine.evaluate(cap, "Original prompt", values, emptyList())
        }
        assertTrue(evaluateStep("1.25").canSubmit)
        assertTrue(evaluateStep("1.5").canSubmit)
        val rejected = evaluateStep("1.6")
        assertFalse(rejected.canSubmit)
        assertEquals("1.6", rejected.configuration.parameters["duration"])
    }

    @Test fun foreignBindingOwnerBlocksDirectStatesEvenWhenFieldAndMimeMatch() {
        val model=NexusModel("target","Target","fixture",organization="Fixture",modalities=listOf(Modality.IMAGE),
            capabilities=emptyList(),parameters=emptyList(),contractEndpoint="/target",contractPromptField="prompt",
            contractMedia=listOf(ContractMediaField("input","Photo","image/*",required=true)))
        val media=image(key="input").copy(bindingOwner=MediaBindingOwner("fixture","mask-source","/source"))
        val owners=listOf(media.bindingOwner!!,MediaBindingOwner("other",model.id,"/target"),
            MediaBindingOwner(model.providerId,model.id,"/another-endpoint"))
        for(owner in owners) {
            val input=media.copy(bindingOwner=owner)
            val result=CapabilityConstraintEngine.evaluate(capabilities(model,media=listOf(input)),"Original prompt",emptyMap(),listOf(input))
            assertFalse(result.canSubmit)
            assertTrue(result.issues.any {it.kind==CapabilityIssueKind.MEDIA && it.fieldKey=="input"})
            assertEquals(listOf(input),result.configuration.media)
            assertEquals(owner,result.configuration.media.single().bindingOwner)
        }
    }

    @Test fun owningModelAcceptsItsDeclaredMaskAndLegacyUnownedMedia() {
        val model=NexusModel("mask-owner","Mask owner","fixture",organization="Fixture",modalities=listOf(Modality.IMAGE),
            capabilities=emptyList(),parameters=listOf(ModelParameter("input","Mask",ParameterType.MASK)),
            contractEndpoint="/mask",contractPromptField="prompt",
            contractMedia=listOf(ContractMediaField("input","Mask","image/*",required=true)))
        val owner=MediaBindingOwner(model.providerId,model.id,model.contractEndpoint)
        val owned=image(key="input").copy(bindingOwner=owner)
        for(input in listOf(owned,owned.copy(bindingOwner=null))) {
            val result=CapabilityConstraintEngine.evaluate(capabilities(model,media=listOf(input)),"Original prompt",emptyMap(),listOf(input))
            assertTrue(result.issues.toString(),result.canSubmit)
            assertEquals(listOf(input),result.configuration.media)
        }
    }
}
