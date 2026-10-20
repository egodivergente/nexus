package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.PriceQuote
import com.vidadomu.nexus.ui.models.*
import org.junit.Assert.*
import org.junit.Test

class ModelFactSheetTest {
    private val catalogOnly = NexusModel(id = "kwaivgi/kling-v3.0-pro/image-to-video", displayName = "Kling 3.0 Pro", providerId = "atlas_cloud",
        organization = "Kling", modalities = listOf(Modality.VIDEO), capabilities = listOf(ModelCapability.IMAGE_TO_VIDEO), parameters = emptyList(),
        supportedDurations = listOf("5", "10"), supportsAudio = true, maxImages = 4)
    private fun contract(durations: List<String>, audio: Boolean = false, resolution: List<String> = listOf("720p", "1080p")) = catalogOnly.copy(
        contractEndpoint = "image_to_video", nativeRequiredFields = listOf("prompt", "image"), promptRequired = true,
        firstFrameField = "image", inputImageField = "image",
        parameters = listOfNotNull(
            ModelParameter("duration", "Duración", ParameterType.ENUM, durations.first(), options = durations),
            ModelParameter("resolution", "Resolución", ParameterType.ENUM, resolution.first(), options = resolution),
            ModelParameter("generate_audio", "Sonido", ParameterType.BOOLEAN, "true").takeIf { audio }))
    private fun perSecond(usd: Double) = PriceQuote(usd, "base", 0L, unit = "s", unitUsd = usd, baseTariff = true)

    @Test fun catalogDefaultsNeverFillUnknownFacts() {
        val facts = modelFactSheet(catalogOnly, verified = null, publishedUnit = null).associate { it.label to it.value }
        assertNull(facts["Entrada"]); assertNull(facts["Audio"]); assertNull(facts["Límites"]); assertNull(facts["Coste por"])
        assertEquals("Vídeo", facts["Salida"])
    }

    @Test fun verifiedContractShowsInputsAudioAndLimits() {
        val verified = contract(listOf("5", "10"), audio = true)
        val facts = modelFactSheet(catalogOnly, verified, "s").associate { it.label to it.value }
        assertEquals("Texto (obligatorio), fotograma o imagen inicial (obligatorio)", facts["Entrada"])
        assertEquals("Sonido generado, activado por defecto", facts["Audio"])
        assertEquals("Duración 5, 10 s · Resolución 720p, 1080p", facts["Límites"])
        assertEquals("segundo", facts["Coste por"])
        assertEquals("No declarado en el esquema", modelFactSheet(catalogOnly, contract(listOf("5")), null).first { it.label == "Audio" }.value)
    }

    @Test fun exampleIsArithmeticAndOnlyForComparableUnits() {
        val example = exampleCost(catalogOnly, perSecond(0.14), contract(listOf("5", "10")))
        assertEquals(0.7, example.usd!!, 1e-9)
        assertTrue(example.note.contains("Ejemplo aritmético") && example.note.contains("No es una cotización"))
        assertTrue(exampleCost(catalogOnly, perSecond(0.14), null).note.contains("sin verificar"))
        val rejected = exampleCost(catalogOnly, perSecond(0.14), contract(listOf("4", "6", "8")))
        assertNull(rejected.usd)
        val perGeneration = exampleCost(catalogOnly, PriceQuote(1.0, "base", 0L, unit = "generación", unitUsd = 1.0), null)
        assertNull(perGeneration.usd)
        assertTrue(perGeneration.note.contains("generación"))
        assertNull(exampleCost(catalogOnly, null, null).usd)
    }

    @Test fun decimalDurationLimitsAreExactNotFloatNoise() {
        val exact = ModelParameter("duration", "Duración", ParameterType.FLOAT, minValue = ParameterBounds.legacyFloat("0.1", upper = false),
            maxValue = ParameterBounds.legacyFloat("2.5", upper = true), nativeType = "number", minExact = "0.1", maxExact = "2.5")
        val verified = catalogOnly.copy(contractEndpoint = "image_to_video", parameters = listOf(exact))
        val limits = modelFactSheet(catalogOnly, verified, null).first { it.label == "Límites" }.value
        assertEquals("Duración de 0.1 a 2.5 s", limits)
        assertEquals(true, supportsDuration(verified, 0.1))
        assertEquals(true, supportsDuration(verified, 2.5))
        assertEquals(false, supportsDuration(verified, 0.09))
        assertEquals(false, supportsDuration(verified, EXAMPLE_SECONDS))
        val legacyOnly = verified.copy(parameters = listOf(ModelParameter("duration", "Duración", ParameterType.FLOAT, minValue = 0.1f, maxValue = 30f)))
        assertEquals("Duración de 0.1 a 30 s", modelFactSheet(catalogOnly, legacyOnly, null).first { it.label == "Límites" }.value)
        assertEquals(true, supportsDuration(legacyOnly, EXAMPLE_SECONDS))
    }

    @Test fun differencesNeedEvidenceForEveryVariant() {
        val pro = catalogOnly.copy(id = "kling-pro")
        val turbo = catalogOnly.copy(id = "kling-turbo")
        val noEvidence = variantDifferences(listOf(VariantEvidence(pro, null, null), VariantEvidence(turbo, null, null)))
        assertEquals(setOf(UNDOCUMENTED_DIFFERENCE), noEvidence.values.toSet())
        val partial = variantDifferences(listOf(VariantEvidence(pro, contract(listOf("5"), resolution = listOf("1080p")), null), VariantEvidence(turbo, null, null)))
        assertEquals(setOf(UNDOCUMENTED_DIFFERENCE), partial.values.toSet())
        val backed = variantDifferences(listOf(
            VariantEvidence(pro, contract(listOf("5", "10"), resolution = listOf("1080p")), null),
            VariantEvidence(turbo, contract(listOf("5", "10"), resolution = listOf("720p")), null)))
        assertEquals("Resolución 1080p", backed[modelBookmark(pro)])
        assertEquals("Resolución 720p", backed[modelBookmark(turbo)])
    }
}
