package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.create.capability.*
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
