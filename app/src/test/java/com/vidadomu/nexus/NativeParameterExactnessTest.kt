package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.AtlasModelCatalog
import com.vidadomu.nexus.provider.atlas.NativeModelSchema
import com.vidadomu.nexus.ui.create.ParameterPresentation
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class NativeParameterExactnessTest {
    private val base = AtlasModelCatalog.curatedModels.first()
    private fun schema(properties: String, required: String = "") =
        """{"components":{"schemas":{"Input":{"properties":{"prompt":{"type":"string"},$properties},"required":["prompt"$required]}}}}"""
    private fun decode(properties: String, required: String = "", id: String = "exactness/test") =
        NativeModelSchema.decode(base.copy(id = id), schema(properties, required)).model
    private fun param(model: NexusModel, key: String) = model.parameters.first { it.key == key }

    @Test fun int32MaximumSurvivesExactlyAndIsFormattedInSpanish() {
        val model = decode(""""seed":{"type":"integer","minimum":0,"maximum":2147483647}""", id = "exactness/seed")
        val seed = param(model, "seed")
        assertEquals("2147483647", seed.maxExact)
        assertEquals("0", seed.minExact)
        val range = ParameterPresentation.range(seed)!!
        assertEquals("0 – 2.147.483.647", range)
        assertFalse(range.contains("E"))
        assertFalse(range.contains("2.147.483.648"))
        assertEquals("0 – 2,147,483,647", ParameterPresentation.range(seed, Locale.US))
    }

    @Test fun exactBoundsDriveValidationAtTheEdge() {
        val model = decode(""""seed":{"type":"integer","minimum":0,"maximum":2147483647}""", id = "exactness/validate")
        assertNull(NativeModelSchema.validate(model, mapOf("seed" to "2147483647")))
        val problem = NativeModelSchema.validate(model, mapOf("seed" to "2147483648"))
        assertNotNull(problem)
        assertTrue(problem!!, problem.contains("2.147.483.647"))
        assertNotNull(NativeModelSchema.validate(model, mapOf("seed" to "-1")))
        assertNull(FormContract.invalidValue(model.parameters, mapOf("seed" to "2147483647")))
    }

    @Test fun legacyFloatLimitsRoundOutwardSoExistingConsumersNeverRejectValidDecimals() {
        val model = decode(""""guidance_scale":{"type":"number","minimum":0.1,"maximum":4.0}""", id = "exactness/decimal")
        val guidance = param(model, "guidance_scale")
        assertEquals("0,1 – 4", ParameterPresentation.range(guidance))
        assertEquals("0.1 – 4", ParameterPresentation.range(guidance, Locale.US))
        assertTrue(guidance.minValue!! <= 0.1f)
        assertNull(FormContract.invalidValue(model.parameters, mapOf("guidance_scale" to "0.1")))
        assertNotNull(FormContract.invalidValue(model.parameters, mapOf("guidance_scale" to "0.09")))
        assertNull(NativeModelSchema.validate(model, mapOf("guidance_scale" to "4")))
        assertNotNull(NativeModelSchema.validate(model, mapOf("guidance_scale" to "4.0000001")))
        assertNotNull(NativeModelSchema.validate(model, mapOf("guidance_scale" to "0.0999")))
    }

    @Test fun integralDecimalsAreNotShownWithTrailingZero() {
        val model = decode(""""cfg_scale":{"type":"number","minimum":1.0,"maximum":4.0}""", id = "exactness/integral")
        assertEquals("1 – 4", ParameterPresentation.range(param(model, "cfg_scale")))
    }

    @Test fun extremeJsonLiteralsStayExactAndFloatsStayFinite() {
        val model = decode(""""steps":{"type":"integer","minimum":-50,"maximum":1e20},"huge":{"type":"number","maximum":1e60}""",
            id = "exactness/extreme")
        val steps = param(model, "steps")
        assertEquals("-50", steps.minExact)
        assertEquals("100000000000000000000", steps.maxExact)
        assertTrue(steps.maxValue!!.isFinite() && steps.maxValue!! >= 1e20f)
        assertEquals("-50 – 100.000.000.000.000.000.000", ParameterPresentation.range(steps))
        val huge = param(model, "huge")
        assertEquals("1" + "0".repeat(60), huge.maxExact)
        assertNull("Float no representa 1e60: sin límite legacy, nunca por debajo del exacto", huge.maxValue)
        assertNull(FormContract.invalidValue(model.parameters, mapOf("huge" to "1e50")))
        assertNull(NativeModelSchema.validate(model, mapOf("huge" to "1e50")))
        assertNotNull(NativeModelSchema.validate(model, mapOf("huge" to "1e61")))
        assertNull(NativeModelSchema.validate(model, mapOf("steps" to "99999999999999999999")))
        assertNotNull(NativeModelSchema.validate(model, mapOf("steps" to "100000000000000000001")))
    }

    @Test fun negativeOutOfFloatMinimumIsNullInLegacyButEnforcedExactly() {
        val model = decode(""""offset":{"type":"number","minimum":-1e60,"maximum":0}""", id = "exactness/negative")
        val offset = param(model, "offset")
        assertNull(offset.minValue)
        assertEquals(0f, offset.maxValue)
        assertNull(FormContract.invalidValue(model.parameters, mapOf("offset" to "-1e50")))
        assertNotNull(NativeModelSchema.validate(model, mapOf("offset" to "-1e61")))
    }

    @Test fun limitsJustBeyondFloatMaxAreNullInLegacyButEnforcedExactly() {
        assertNull(ParameterBounds.legacyFloat("3.4028235e38", upper = true))
        assertNull(ParameterBounds.legacyFloat("-3.4028235e38", upper = false))
        val floatMax = java.math.BigDecimal(Float.MAX_VALUE.toDouble()).toPlainString()
        assertEquals(Float.MAX_VALUE, ParameterBounds.legacyFloat(floatMax, upper = true))
        assertEquals(-Float.MAX_VALUE, ParameterBounds.legacyFloat("-$floatMax", upper = false))
        val model = decode(""""edge":{"type":"number","minimum":-3.4028235e38,"maximum":3.4028235e38}""",
            id = "exactness/floatmax")
        val edge = param(model, "edge")
        assertNull(edge.minValue)
        assertNull(edge.maxValue)
        assertEquals("340282350000000000000000000000000000000", edge.maxExact)
        assertEquals("-340282350000000000000000000000000000000", edge.minExact)
        assertNull(FormContract.invalidValue(model.parameters, mapOf("edge" to "3.4028235e38")))
        assertNull(NativeModelSchema.validate(model, mapOf("edge" to "3.4028235e38")))
        assertNull(NativeModelSchema.validate(model, mapOf("edge" to "-3.4028235e38")))
        assertNotNull(NativeModelSchema.validate(model, mapOf("edge" to "3.40282351e38")))
        assertNotNull(NativeModelSchema.validate(model, mapOf("edge" to "-3.40282351e38")))
    }

    @Test fun maliciousExponentsAndOversizedLiteralsAreNotExpanded() {
        assertNull(ParameterBounds.exact("1e999999999"))
        assertNull(ParameterBounds.exact("1e-999999999"))
        assertNull(ParameterBounds.exact("9".repeat(200)))
        assertNull(ParameterBounds.legacyFloat("1e999999999", upper = true))
        val model = decode(""""steps":{"type":"integer","minimum":0,"maximum":1e999999999}""", id = "exactness/malicious")
        val steps = param(model, "steps")
        assertNull(steps.maxExact)
        assertNull(steps.maxValue)
        assertEquals("0 – —", ParameterPresentation.range(steps))
        assertNull(NativeModelSchema.validate(model, mapOf("steps" to "1e999999999")))
        val bounded = decode(""""seed":{"type":"integer","minimum":0,"maximum":10}""", id = "exactness/oversized")
        assertNotNull(NativeModelSchema.validate(bounded, mapOf("seed" to "9".repeat(200))))
    }

    @Test fun parametersWithoutExactFieldsKeepTheirLegacyFloatRange() {
        val legacy = ModelParameter("outputCount", "Resultados", ParameterType.INTEGER, "1", minValue = 1f, maxValue = 4f, nativeType = "integer")
        assertNull(legacy.minExact)
        assertEquals("1 – 4", ParameterPresentation.range(legacy))
        assertNull(ParameterPresentation.range(legacy.copy(minValue = null, maxValue = null)))
    }

    @Test fun storedParametersWithoutExactFieldsStillDecode() {
        val restored = Json { ignoreUnknownKeys = true }.decodeFromString(ModelParameter.serializer(),
            """{"key":"seed","displayName":"Semilla","type":"SEED","minValue":0.0,"maxValue":2.14748365E9}""")
        assertNull(restored.maxExact)
        assertEquals(2.14748365E9f, restored.maxValue)
    }

    @Test fun optionalBooleanWithoutDefaultStaysAutomaticNotNo() {
        val model = decode(""""camera_fixed":{"type":"boolean"},"loop":{"type":"boolean"}""", ""","loop"""", id = "exactness/bool")
        val optional = param(model, "camera_fixed")
        assertEquals("", optional.defaultValue)
        assertEquals("Automático", ParameterPresentation.label(optional, optional.defaultValue).primary)
        assertEquals(listOf("", "true", "false"), ParameterPresentation.booleanChoices(optional).map { it.first })
        assertEquals(listOf("Automático", "Sí", "No"), ParameterPresentation.booleanChoices(optional).map { it.second })
        val required = param(model, "loop")
        assertEquals("false", required.defaultValue)
        assertEquals(listOf("true", "false"), ParameterPresentation.booleanChoices(required).map { it.first })
        val spoken = ParameterPresentation.booleanChoices(optional).map { (option, label) ->
            ParameterPresentation.chipDescription(optional, option, label)
        }
        assertEquals(3, spoken.distinct().size)
        assertEquals("Cámara fija: Automático", spoken.first())
    }

    @Test fun promptExtendHasSpanishLabelAndHelp() {
        val model = decode(""""prompt_extend":{"type":"boolean","default":true,"description":"Whether to expand the prompt"}""", id = "exactness/extend")
        val extend = param(model, "prompt_extend")
        assertEquals("Ampliar instrucciones", extend.displayName)
        assertEquals("Ampliar instrucciones", ParameterPresentation.title(extend))
        assertNotEquals(ParameterLabels.UNKNOWN_HELP, ParameterPresentation.help(extend))
        assertNull(ParameterPresentation.providerNote(extend))
    }

    @Test fun rawProviderNamesArePromotedToCuratedLabels() {
        val crunStyle = ModelParameter("prompt_extend", "Prompt extend", ParameterType.BOOLEAN, description = "Whether to expand")
        assertEquals("Ampliar instrucciones", ParameterPresentation.title(crunStyle))
        val curated = ModelParameter("temperature", "Creatividad", ParameterType.FLOAT)
        assertEquals("Creatividad", ParameterPresentation.title(curated))
        val runwayRaw = ModelParameter("speechRate", "speechRate", ParameterType.INTEGER)
        assertEquals("Speech rate", ParameterPresentation.title(runwayRaw))
    }

    @Test fun unknownKeysAreReadableButNotTranslatedAndKeepKeyForDiagnosticsOnly() {
        val model = decode(""""motion_bucket_id":{"type":"integer","description":"Controls motion amount"}""", id = "exactness/unknown")
        val unknown = param(model, "motion_bucket_id")
        assertEquals("Motion bucket id", unknown.displayName)
        assertNotEquals("motion_bucket_id", ParameterPresentation.title(unknown))
        assertEquals(ParameterLabels.UNKNOWN_HELP, ParameterPresentation.help(unknown))
        assertEquals("Controls motion amount", ParameterPresentation.providerNote(unknown))
        assertEquals("Clave técnica: motion_bucket_id", ParameterPresentation.diagnosticKey(unknown))
        assertNull(ParameterLabels.entry("motion_bucket_id"))
    }

    @Test fun readableFallbackHandlesCommonKeyShapes() {
        assertEquals("Voice audio uri", ParameterLabels.readable("voice.audioUri"))
        assertEquals("Target framerate", ParameterLabels.readable("targetFramerate"))
        assertEquals("Shot type", ParameterLabels.readable("shot-type"))
        assertEquals("___", ParameterLabels.readable("___"))
    }
}
