package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.create.capability.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CompatibleParameterTransferTest {
    private val provenance = CapabilityProvenance("fixture", 1, CapabilityVerification.DECLARED_ADAPTER)
    private fun duration(max: Float = 20f, key: String = "duration", default: String = "5") =
        ModelParameter(key, "Duración", ParameterType.DURATION, default, minValue = 1f, maxValue = max, nativeType = "integer")
    private fun resolution(default: String = "720p", key: String = "resolution") =
        ModelParameter(key, "Resolución", ParameterType.RESOLUTION, default, options = listOf("720p", "1080p"))
    private fun seed() = ModelParameter("seed", "Semilla", ParameterType.SEED, "1", minValue = 0f, nativeType = "integer")
    private fun caps(id: String, parameters: List<ModelParameter>, rules: List<CapabilityConstraint> = emptyList(),
        provider: String = "fixture", endpoint: String = "/$id"): ModelUiCapabilities {
        val model = NexusModel(id, id, provider, organization = "Fixture", modalities = listOf(Modality.VIDEO),
            capabilities = emptyList(), parameters = parameters, contractEndpoint = endpoint, promptRequired = false)
        return ModelUiCapabilities(CapabilityKey(provider, id, endpoint, Modality.VIDEO), model, CapabilitySupport.CONFIRMED,
            provenance, emptyList(), parameters, emptyList(),
            PromptCapability(CapabilitySupport.UNSUPPORTED, "prompt", false, null, null, null, provenance),
            parameters.associate { it.key to it.defaultValue }, rules, emptyList(), parameters)
    }
    private fun source(capabilities: ModelUiCapabilities, vararg values: Pair<String, String>) =
        ParameterTransferSource(capabilities, mapOf(*values))

    @Test fun compatibleCurrentValuesWinSavedTargetFallbackWithoutMutatingEitherSide() {
        val origin = caps("a", listOf(duration(), resolution(), seed()))
        val target = caps("b", origin.declaredParameters)
        val current = mapOf("duration" to "10", "resolution" to "1080p", "seed" to "42")
        val fallback = target.defaults.toMap()
        val result = transferCompatibleParameters(ParameterTransferSource(origin, current), target, fallback)
        assertEquals(current, result.parameters)
        assertEquals(current.keys, result.transferredKeys)
        assertTrue(result.adaptations.isEmpty())
        assertEquals("5", fallback["duration"])
        assertEquals("10", current["duration"])
    }

    @Test fun incompatibleValueUsesTargetFallbackAndPreservesOriginalIntent() {
        val origin = caps("a", listOf(duration()))
        val target = caps("b", listOf(duration(6f)))
        val source = source(origin, "duration" to "15")
        val result = transferCompatibleParameters(source, target, target.defaults)
        assertEquals("5", result.parameters["duration"])
        assertEquals("15", source.values["duration"])
        assertEquals("15", result.adaptations.single().requestedValue)
        assertEquals("5", result.adaptations.single().actualValue)
    }

    @Test fun jointConditionalRestrictionsNeverProduceAnInvalidCombination() {
        val origin = caps("a", listOf(duration(), resolution(), seed()))
        val rule = CapabilityConstraint("resolution", mapOf("duration" to setOf("10")), allowedValues = listOf("720p"))
        val target = caps("b", origin.declaredParameters, listOf(rule))
        val result = transferCompatibleParameters(source(origin, "duration" to "10", "resolution" to "1080p", "seed" to "42"),
            target, target.defaults)
        assertEquals(mapOf("duration" to "10", "resolution" to "720p", "seed" to "42"), result.parameters)
        assertEquals("resolution", result.adaptations.single().key)
        assertTrue(CapabilityConstraintEngine.evaluate(target, "", result.parameters, emptyList()).issues.isEmpty())
    }

    @Test fun validJointIntentIsNotRejectedBecauseAnIntermediateStateWouldBeInvalid() {
        val parameters = listOf(duration(), resolution(default = "1080p"))
        val origin = caps("a", parameters)
        val target = caps("b", parameters, listOf(CapabilityConstraint("resolution",
            mapOf("duration" to setOf("10")), allowedValues = listOf("720p"))))
        val result = transferCompatibleParameters(source(origin, "duration" to "10", "resolution" to "720p"), target, target.defaults)
        assertEquals("10", result.parameters["duration"])
        assertEquals("720p", result.parameters["resolution"])
        assertTrue(result.adaptations.isEmpty())
    }

    @Test fun knownSemanticAliasCopiesOnlyTheSameFormallyAcceptedValue() {
        val origin = caps("a", listOf(duration(key = "seconds"), resolution(key = "output_resolution")))
        val target = caps("b", listOf(duration(), resolution()))
        val result = transferCompatibleParameters(source(origin, "seconds" to "10", "output_resolution" to "1080p"), target, target.defaults)
        assertEquals("10", result.parameters["duration"])
        assertEquals("1080p", result.parameters["resolution"])
    }

    @Test fun ambiguousAliasesAndDifferentAudioSemanticsNeverGuessAMapping() {
        val origin = caps("a", listOf(duration(key = "seconds"), duration(key = "duration_seconds"),
            ModelParameter("generate_audio", "Generar audio", ParameterType.BOOLEAN, "false")))
        val target = caps("b", listOf(duration(), ModelParameter("keep_original_sound", "Conservar sonido", ParameterType.BOOLEAN, "false")))
        val result = transferCompatibleParameters(source(origin, "seconds" to "10", "duration_seconds" to "12", "generate_audio" to "true"), target, target.defaults)
        assertEquals(target.defaults, result.parameters)
        assertTrue(result.transferredKeys.isEmpty())
    }

    @Test fun deferredSchemaUsesResolvedLimitsRatherThanCatalogPlaceholderLimits() {
        val origin = caps("a", listOf(duration()))
        val placeholder = caps("b", listOf(duration()))
        val actual = caps("b", listOf(duration(6f)))
        val result = transferCompatibleParameters(source(origin, "duration" to "15"), placeholder, actual.defaults, resolve = { actual })
        assertEquals("5", result.parameters["duration"])
        assertEquals("duration", result.adaptations.single().key)
    }

    @Test fun anEditMadeAtTheDestinationWhileSchemaLoadsWinsTheCarriedValue() {
        val origin = caps("a", listOf(duration(), seed()))
        val target = caps("b", origin.declaredParameters)
        val result = transferCompatibleParameters(source(origin, "duration" to "10", "seed" to "42"), target, target.defaults,
            editedValues = mapOf("duration" to "6"))
        assertEquals("6", result.parameters["duration"])
        assertEquals("42", result.parameters["seed"])
        assertFalse("duration" in result.transferredKeys)
    }

    @Test fun pendingTransferCannotLeakToAnotherProviderModalityOrKnownEndpoint() {
        val origin = caps("a", listOf(duration()))
        val target = CapabilityKey("provider", "same", null, Modality.VIDEO)
        val pending = PendingParameterTransfer(target, source(origin, "duration" to "10"))
        assertTrue(pending.matches(target.copy(endpoint = "/resolved")))
        assertFalse(pending.matches(target.copy(providerId = "other")))
        assertFalse(pending.matches(target.copy(modality = Modality.IMAGE)))
        assertFalse(pending.matches(target.copy(modelId = "different")))
        assertFalse(pending.copy(target = target.copy(endpoint = "/first")).matches(target.copy(endpoint = "/second")))
    }

    @Test fun adaptedFallbackDoesNotOverwriteTheOriginalWhenReturningButUserEditDoes() {
        val a = caps("a", listOf(duration()))
        val b = caps("b", listOf(duration(6f)))
        val original = mapOf("duration" to "15")
        val atB = transferCompatibleParameters(ParameterTransferSource(a, original), b, b.defaults)
        val unchangedB = parameterTransferSource(b, atB.parameters, atB.adaptations)
        val back = transferCompatibleParameters(unchangedB, a, original)
        assertEquals("15", back.parameters["duration"])
        val editedB = parameterTransferSource(b, atB.parameters + ("duration" to "6"), emptyList())
        assertEquals("6", transferCompatibleParameters(editedB, a, original).parameters["duration"])
        assertEquals("15", original["duration"])
    }

    @Test fun threeModelRoundTripPreservesOriginalResolutionAcrossTwoAdaptedFallbacks() {
        val a = caps("a", listOf(resolution(default = "1080p")))
        val b = caps("b", listOf(resolution().copy(options = listOf("720p"))))
        val c = caps("c", b.declaredParameters)
        val original = mapOf("resolution" to "1080p")
        val atB = transferCompatibleParameters(ParameterTransferSource(a, original), b, b.defaults)
        val atC = transferCompatibleParameters(parameterTransferSource(b, atB.parameters, atB.adaptations), c, c.defaults)
        assertEquals("720p", atC.parameters["resolution"])
        assertFalse("resolution" in atC.transferredKeys)
        assertEquals("1080p", atC.adaptations.single().requestedValue)
        assertEquals("720p", atC.adaptations.single().actualValue)
        val back = transferCompatibleParameters(parameterTransferSource(c, atC.parameters, atC.adaptations), a, original)
        assertEquals(original, back.parameters)
        assertTrue(back.adaptations.isEmpty())
        assertEquals("1080p", original["resolution"])
    }

    @Test fun reopeningTheThirdModelPreservesAdaptationProvenanceAndTheOriginalDraft() {
        val a = caps("a", listOf(resolution(default = "1080p")))
        val b = caps("b", listOf(resolution().copy(options = listOf("720p"))))
        val c = caps("c", b.declaredParameters)
        val savedA = ModelWorkDraft(a.model.id, "Full original prompt", parameters = a.defaults,
            providerId = a.key.providerId, endpoint = a.key.endpoint, modality = a.key.modality)
        val atB = transferCompatibleParameters(ParameterTransferSource(a, savedA.parameters), b, b.defaults)
        val atC = transferCompatibleParameters(parameterTransferSource(b, atB.parameters, atB.adaptations), c, c.defaults)
        val current = CreationDraft(c.model.id, Modality.VIDEO, savedA.prompt, atC.parameters,
            providerId = c.key.providerId, endpoint = c.key.endpoint, parameterAdaptations = atC.adaptations)
        val reopened = Json.decodeFromString<CreationDraft>(Json.encodeToString(current))
        val restoredA = ModelWorkDrafts.decode(ModelWorkDrafts.encode(listOf(savedA))).single()
        val values = FormContract.restoredValues(c.model, reopened.parameters)
        val back = transferCompatibleParameters(parameterTransferSource(c, values, reopened.parameterAdaptations), a, restoredA.parameters)
        assertEquals("1080p", back.parameters["resolution"])
        assertTrue(back.adaptations.isEmpty())
        assertEquals(savedA, restoredA)
        assertEquals(savedA.prompt, reopened.prompt)
    }

    @Test fun explicitSameFallbackEditAtTheThirdModelBecomesNewIntentEvenDuringLoading() {
        val a = caps("a", listOf(resolution(default = "1080p"), seed()))
        val b = caps("b", listOf(resolution().copy(options = listOf("720p")), seed()))
        val c = caps("c", b.declaredParameters)
        val atB = transferCompatibleParameters(ParameterTransferSource(a, a.defaults), b, b.defaults)
        val atC = transferCompatibleParameters(parameterTransferSource(b, atB.parameters, atB.adaptations), c, c.defaults)
        val sourceC = parameterTransferSource(c, atC.parameters, atC.adaptations)
        val unrelated = sourceC.withEdits(mapOf("seed" to "42"))
        assertEquals(1, unrelated.adaptations.size)
        val edited = unrelated.withEdits(mapOf("resolution" to "720p"))
        assertTrue(edited.adaptations.isEmpty())
        val back = transferCompatibleParameters(edited, a, a.defaults)
        assertEquals("720p", back.parameters["resolution"])
        assertEquals("42", back.parameters["seed"])
        assertEquals(1, sourceC.adaptations.size)
        assertFalse("resolution" in sourceC.values)
    }

    @Test fun adaptationMarkersFollowOnlyUnambiguousSemanticAliasesAcrossModels() {
        val a = caps("a", listOf(resolution(default = "1080p")))
        val b = caps("b", listOf(resolution(key = "output_resolution").copy(options = listOf("720p"))))
        val c = caps("c", listOf(resolution(key = "target_resolution").copy(options = listOf("720p"))))
        val atB = transferCompatibleParameters(ParameterTransferSource(a, a.defaults), b, b.defaults)
        val atC = transferCompatibleParameters(parameterTransferSource(b, atB.parameters, atB.adaptations), c, c.defaults)
        assertEquals("target_resolution", atC.adaptations.single().key)
        assertEquals("1080p", atC.adaptations.single().requestedValue)
        assertEquals("720p", atC.parameters["target_resolution"])
        val back = transferCompatibleParameters(parameterTransferSource(c, atC.parameters, atC.adaptations), a, a.defaults)
        assertEquals("1080p", back.parameters["resolution"])
        assertTrue(back.adaptations.isEmpty())
    }

    @Test fun adaptationProvenanceDoesNotBypassTheIntermediateModelsJointConstraints() {
        val a = caps("a", listOf(duration(), resolution(default = "1080p")))
        val b = caps("b", listOf(duration(), resolution().copy(options = listOf("720p"))))
        val c = caps("c", listOf(duration(), resolution()), listOf(
            CapabilityConstraint("resolution", mapOf("duration" to setOf("10")), allowedValues = listOf("720p"))))
        val original = mapOf("duration" to "10", "resolution" to "1080p")
        val atB = transferCompatibleParameters(ParameterTransferSource(a, original), b, b.defaults)
        val atC = transferCompatibleParameters(parameterTransferSource(b, atB.parameters, atB.adaptations), c, c.defaults)
        assertEquals(mapOf("duration" to "10", "resolution" to "720p"), atC.parameters)
        assertEquals("1080p", atC.adaptations.single().requestedValue)
        assertTrue(CapabilityConstraintEngine.evaluate(c, "", atC.parameters, emptyList()).canSubmit)
        val back = transferCompatibleParameters(parameterTransferSource(c, atC.parameters, atC.adaptations), a, original)
        assertEquals(original, back.parameters)
    }

    @Test fun ambiguousAliasesNeverRebindAnAdaptationMarker() {
        val b = caps("b", listOf(resolution().copy(options = listOf("720p")),
            resolution(key = "target_resolution").copy(options = listOf("720p"))))
        val c = caps("c", listOf(resolution(key = "output_resolution")))
        val marker = ParameterTransferAdaptation("resolution", "Resolución", "1080p", "720p", "Contract limit")
        val carried = parameterTransferSource(b, b.defaults, listOf(marker))
        val atC = transferCompatibleParameters(carried, c, c.defaults)
        assertEquals(c.defaults, atC.parameters)
        assertTrue(atC.transferredKeys.isEmpty())
        assertTrue(atC.adaptations.isEmpty())
    }

    @Test fun reopeningAnAdaptedCurrentDraftStillRestoresTheOriginalSourceValue() {
        val a=caps("a",listOf(duration()))
        val b=caps("b",listOf(duration(6f)))
        val original=ModelWorkDraft(a.model.id,"prompt",parameters=mapOf("duration" to "15"),
            providerId=a.key.providerId,endpoint=a.key.endpoint,modality=a.key.modality)
        val atB=transferCompatibleParameters(ParameterTransferSource(a,original.parameters),b,b.defaults)
        val active=CreationDraft(b.model.id,Modality.VIDEO,"prompt",atB.parameters,
            providerId=b.key.providerId,endpoint=b.key.endpoint,parameterAdaptations=atB.adaptations)
        val reopened=Json.decodeFromString<CreationDraft>(Json.encodeToString(active))
        val stored=ModelWorkDrafts.decode(ModelWorkDrafts.encode(listOf(original)))
        val savedA=ModelWorkDrafts.find(stored,a.model,Modality.VIDEO)!!
        val values=FormContract.restoredValues(b.model,reopened.parameters)
        val adaptations=restoredParameterAdaptations(values,reopened.parameterAdaptations)
        val back=transferCompatibleParameters(parameterTransferSource(b,values,adaptations),a,savedA.parameters)
        assertEquals("15",back.parameters["duration"])
        assertTrue(back.adaptations.isEmpty())
        assertEquals("15",savedA.parameters["duration"])
    }

    @Test fun explicitlyEditingAnAdaptedValueAfterReopeningTransfersThatNewIntent() {
        val a=caps("a",listOf(duration(),seed()))
        val b=caps("b",listOf(duration(6f),seed()))
        val original=mapOf("duration" to "15","seed" to "1")
        val atB=transferCompatibleParameters(ParameterTransferSource(a,original),b,b.defaults)
        val draft=ModelWorkDraft(b.model.id,"prompt",parameters=atB.parameters,providerId=b.key.providerId,
            endpoint=b.key.endpoint,modality=b.key.modality,parameterAdaptations=atB.adaptations)
        val reopened=ModelWorkDrafts.decode(ModelWorkDrafts.encode(listOf(draft))).single()
        val edited=reopened.parameters+("duration" to "6")
        val adaptations=restoredParameterAdaptations(edited,reopened.parameterAdaptations,setOf("duration"))
        val savedAgain=reopened.copy(parameters=edited,parameterAdaptations=adaptations)
        val restoredAgain=ModelWorkDrafts.decode(ModelWorkDrafts.encode(listOf(savedAgain))).single()
        val back=transferCompatibleParameters(parameterTransferSource(b,restoredAgain.parameters,restoredAgain.parameterAdaptations),a,original)
        assertEquals("6",back.parameters["duration"])
        assertTrue(restoredAgain.parameterAdaptations.isEmpty())
        assertEquals("15",original["duration"])
    }

    @Test fun editingAReopenedDraftBeforeSchemaResolutionKeepsTheEditAndClearsItsAdaptation() {
        val a=caps("a",listOf(duration()))
        val b=caps("b",listOf(duration(6f)))
        val atB=transferCompatibleParameters(source(a,"duration" to "15"),b,b.defaults)
        val active=CreationDraft(b.model.id,Modality.VIDEO,"prompt",atB.parameters,
            providerId=b.key.providerId,endpoint=b.key.endpoint,parameterAdaptations=atB.adaptations)
        val reopened=Json.decodeFromString<CreationDraft>(Json.encodeToString(active))
        val pending=PendingWorkParameters(b.key,reopened.parameters,reopened.parameterAdaptations)
            .withEdit("duration","6")
        assertTrue(pending.matches(b.key))
        assertFalse(pending.matches(b.key.copy(endpoint="/different")))
        val resolved=FormContract.restoredValues(b.model,pending.parameters)
        val annotations=restoredParameterAdaptations(resolved,pending.adaptations)
        assertEquals("6",resolved["duration"])
        assertTrue(annotations.isEmpty())
        val back=transferCompatibleParameters(parameterTransferSource(b,resolved,annotations),a,mapOf("duration" to "15"))
        assertEquals("6",back.parameters["duration"])
    }

    @Test fun aRapidSecondSwitchCarriesAnExplicitExactKeyEditFromTheLoadingDestination() {
        val a=caps("a",listOf(duration()))
        val c=caps("c",listOf(duration()))
        val loadingB=PendingParameterTransfer(CapabilityKey("fixture","b",null,Modality.VIDEO),
            source(a,"duration" to "15"),editedValues=mapOf("duration" to "6","unconfirmed_alias" to "12"))
        val carried=loadingB.source.withEdits(loadingB.editedValues)
        assertFalse("unconfirmed_alias" in carried.values)
        assertEquals("15",loadingB.source.values["duration"])
        val atC=transferCompatibleParameters(carried,c,c.defaults)
        assertEquals("6",atC.parameters["duration"])
        assertTrue(atC.adaptations.isEmpty())
    }

    @Test fun targetAdaptationsSurviveUnrelatedTransfersButNotAnExplicitSameValueEdit() {
        val a=caps("a",listOf(seed()))
        val b=caps("b",listOf(duration(6f),seed()))
        val adaptation=ParameterTransferAdaptation("duration","Duración","15","5","Límite del contrato")
        val unchanged=transferCompatibleParameters(source(a,"seed" to "42"),b,b.defaults,
            fallbackAdaptations=listOf(adaptation))
        assertEquals("42",unchanged.parameters["seed"])
        assertEquals(listOf(adaptation),unchanged.adaptations)
        val edited=transferCompatibleParameters(source(a,"seed" to "42"),b,b.defaults,
            editedValues=mapOf("duration" to "5"),fallbackAdaptations=listOf(adaptation))
        assertTrue(edited.adaptations.isEmpty())
        val explicit=parameterTransferSource(b,edited.parameters,edited.adaptations)
        assertEquals("5",explicit.values["duration"])
    }

    @Test fun publishedStepRejectsAnOtherwiseValidNumberWithoutRoundingIt() {
        val origin = caps("a", listOf(seed()))
        val target = caps("b", listOf(seed()), listOf(CapabilityConstraint("seed", emptyMap(), multipleOf = "2")))
        val result = transferCompatibleParameters(source(origin, "seed" to "3"), target, mapOf("seed" to "2"))
        assertEquals("2", result.parameters["seed"])
        assertEquals("3", result.adaptations.single().requestedValue)
    }

    @Test fun editingAnotherSettingDoesNotTurnAnAdaptedFallbackIntoNewIntent() {
        val a=caps("a",listOf(duration(),seed()))
        val b=caps("b",listOf(duration(6f),seed()))
        val original=mapOf("duration" to "15","seed" to "1")
        val atB=transferCompatibleParameters(ParameterTransferSource(a,original),b,b.defaults)
        val edited=parameterTransferSource(b,atB.parameters+("seed" to "42"),atB.adaptations)
        val back=transferCompatibleParameters(edited,a,original)
        assertEquals("15",back.parameters["duration"])
        assertEquals("42",back.parameters["seed"])
    }

    @Test fun unknownContractsCannotAuthorizeAutomaticTransfers() {
        val origin = caps("a", listOf(duration()))
        val target = caps("b", listOf(duration())).copy(support = CapabilitySupport.UNKNOWN)
        val result = transferCompatibleParameters(source(origin, "duration" to "10"), target, target.defaults)
        assertEquals(target.defaults, result.parameters)
        assertTrue(result.transferredKeys.isEmpty())
    }
}
