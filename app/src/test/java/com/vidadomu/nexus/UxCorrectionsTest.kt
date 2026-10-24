package com.vidadomu.nexus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.AtlasModelCatalog
import com.vidadomu.nexus.provider.atlas.NativeModelSchema
import com.vidadomu.nexus.ui.create.ParameterPresentation
import com.vidadomu.nexus.ui.create.ParameterSections
import com.vidadomu.nexus.ui.flow.FlowStudioPlan
import com.vidadomu.nexus.ui.models.catalogPriceNote
import com.vidadomu.nexus.ui.models.toolCategoryIcon
import org.junit.Assert.*
import org.junit.Test

/** Regression tests for the UX correction pack of 2026-10-24 (Lanzadera upscaler form, Flow jobs, ArtBox labels). */
class UxCorrectionsTest {
    private val base = AtlasModelCatalog.curatedModels.first()

    /** Fields exactly as published in the official Atlas contract for tencent/image/upscaler. */
    private val upscaler = NativeModelSchema.decode(base.copy(id = "tencent/image/upscaler"),
        """{"components":{"schemas":{"Input":{"properties":{"prompt":{"type":"string"},
        "type":{"type":"string","enum":["standard","super","ultra","fidelity"],"default":"ultra"},
        "mode":{"type":"string","enum":["percent","aspect","fixed"],"default":"percent"},
        "percent":{"type":"number","minimum":1.0,"maximum":10.0,"default":2.0},
        "width":{"type":"integer","minimum":1},"height":{"type":"integer","minimum":1},
        "long_side":{"type":"integer","minimum":1},"short_side":{"type":"integer","minimum":1},
        "encode_format":{"type":"string","enum":["JPEG","PNG","WEBP","BMP"],"default":"JPEG"},
        "encode_quality":{"type":"integer","minimum":1,"maximum":100,"default":80},
        "output_dir":{"type":"string"}},"required":["prompt"]}}}}""").model

    private fun keys(values: Map<String, String>) =
        ParameterDependencies.visible(upscaler, upscaler.parameters, values).map { it.key }.toSet()

    @Test fun percentModeShowsOnlyTheScaleFactor() {
        val shown = keys(emptyMap())
        assertTrue("percent" in shown)
        assertTrue("type" in shown && "mode" in shown && "encode_format" in shown && "encode_quality" in shown)
        assertTrue(shown.none { it in setOf("width", "height", "long_side", "short_side") })
        assertFalse("output_dir" in shown)
    }

    @Test fun fixedAndAspectModesShowDimensionsInsteadOfPercent() {
        listOf("fixed", "aspect").forEach { mode ->
            val shown = keys(mapOf("mode" to mode))
            assertTrue(shown.containsAll(setOf("width", "height", "long_side", "short_side")))
            assertFalse("percent" in shown)
        }
    }

    @Test fun qualityOnlyAppliesToLossyFormats() {
        assertTrue("encode_quality" in keys(mapOf("encode_format" to "WEBP")))
        assertFalse("encode_quality" in keys(mapOf("encode_format" to "PNG")))
        assertFalse("encode_quality" in keys(mapOf("encode_format" to "BMP")))
    }

    @Test fun inapplicableValuesNeverReachTheProviderButStayInTheDraft() {
        val draft = mapOf("mode" to "percent", "percent" to "3", "width" to "2048", "output_dir" to "x", "encode_format" to "PNG", "encode_quality" to "90")
        val sent = ParameterDependencies.effective(upscaler, draft)
        assertEquals(mapOf("mode" to "percent", "percent" to "3", "encode_format" to "PNG"), sent)
        assertEquals("2048", draft["width"])
        // Switching back restores the value the user had typed.
        assertEquals("2048", ParameterDependencies.effective(upscaler, draft + ("mode" to "fixed"))["width"])
    }

    @Test fun modelsWithoutPublishedRulesAreUntouched() {
        val values = mapOf("width" to "10", "mode" to "x")
        assertSame(values, ParameterDependencies.effective(base, values))
    }

    @Test fun rangeNeverShowsABrokenOpenEnd() {
        val width = upscaler.parameters.first { it.key == "width" }
        assertEquals("Mínimo 1", ParameterPresentation.rangeText(width))
        assertEquals("Rango: 1 – 10", ParameterPresentation.rangeText(upscaler.parameters.first { it.key == "percent" }))
        val unbounded = width.copy(minValue = null, maxValue = null, minExact = null, maxExact = null)
        assertEquals("Sin rango publicado", ParameterPresentation.rangeText(unbounded))
        assertFalse(listOfNotNull(ParameterPresentation.rangeText(width), ParameterPresentation.rangeText(unbounded)).any { it.contains("—") })
    }

    @Test fun upscalerFieldsHaveHumanSpanishNames() {
        fun title(key: String) = ParameterPresentation.title(upscaler.parameters.first { it.key == key })
        assertEquals("Motor de ampliación", title("type"))
        assertEquals("Cómo ajustar el tamaño", title("mode"))
        assertEquals("Factor de escala", title("percent"))
        assertEquals("Ancho (px)", title("width"))
        assertEquals("Lado largo (px)", title("long_side"))
        assertEquals("Formato de salida", title("encode_format"))
        assertEquals("Calidad de salida", title("encode_quality"))
        val type = upscaler.parameters.first { it.key == "type" }
        assertEquals("Ultra", ParameterPresentation.label(type, "ultra").primary)
        assertEquals("Automático", ParameterPresentation.label(type, "").primary)
        assertEquals("×2", ParameterPresentation.label(upscaler.parameters.first { it.key == "percent" }, "2.0").primary)
    }

    @Test fun sectionsGroupScaleQualityAndFormatWithPairedDimensions() {
        val optional = ParameterDependencies.visible(upscaler, upscaler.parameters, mapOf("mode" to "fixed")).filter { it.key != "prompt" }
        val sections = ParameterSections.split(optional)
        assertEquals(listOf("Motor y calidad", "Escala y tamaño", "Formato de salida"), sections.map { it.title })
        val rows = ParameterSections.rows(sections[1].parameters).map { row -> row.map { it.key } }
        assertTrue(listOf("width", "height") in rows)
        assertTrue(listOf("long_side", "short_side") in rows)
    }

    private fun job(status: GenerationStatus, error: String? = null, progress: Float? = null, prompt: String = "p") = GenerationJob(
        id = "j1", providerId = "google_flow", modelId = "m", modality = Modality.VIDEO, prompt = prompt,
        parameters = mapOf("count" to "3"), status = status, progress = progress, error = error)

    @Test fun flowNeverInventsAPercentage() {
        val active = job(GenerationStatus.GENERATING)
        val line = FlowStudioPlan.progressLine(active, "2 min 3 s")!!
        assertTrue(line, line.contains("progreso porcentual no disponible"))
        assertFalse(line.contains("%"))
        assertTrue(FlowStudioPlan.progressLine(active.copy(progress = .4f), "5 s")!!.contains("40 %"))
        assertNull(FlowStudioPlan.progressLine(job(GenerationStatus.COMPLETED), "1 s"))
    }

    @Test fun flowCountsAndDraftConflictAreDetectedFromRealJobs() {
        val conflict = job(GenerationStatus.FAILED, "Hay un borrador escrito en Flow. Revísalo o bórralo antes de enviar desde Nexus.")
        assertTrue(FlowStudioPlan.isDraftConflict(conflict))
        assertFalse(FlowStudioPlan.isDraftConflict(job(GenerationStatus.FAILED, "otro error")))
        val counts = FlowStudioPlan.counts(listOf(conflict, job(GenerationStatus.QUEUED), job(GenerationStatus.COMPLETED)))
        assertEquals(FlowStudioPlan.JobCounts(active = 1, ready = 1, failed = 1), counts)
    }

    @Test fun reusingAFailedFlowPromptKeepsTheTextWhole() {
        val long = "x".repeat(6_000)
        val draft = FlowStudioPlan.reuseDraft(job(GenerationStatus.FAILED, prompt = long))
        assertEquals(long, draft.prompt)
        assertEquals(3, draft.count)
        assertEquals(Modality.VIDEO, draft.modality)
    }

    @Test fun priceNoteNamesAUnitOnlyWhenEveryModelSharesIt() {
        val video = base.copy(modalities = listOf(Modality.VIDEO))
        val image = base.copy(modalities = listOf(Modality.IMAGE))
        assertEquals("Precios desde · por 5 s", catalogPriceNote(listOf(video, video)))
        assertEquals("Precios desde · por imagen", catalogPriceNote(listOf(image)))
        assertNull(catalogPriceNote(listOf(video, image)))
        assertNull(catalogPriceNote(emptyList()))
    }

    @Test fun everyToolCategoryHasItsOwnFunctionalIcon() {
        val categories = listOf("Edición de vídeo", "Movimiento y pose", "Mejorar imagen", "Extender vídeo",
            "Audio, música y voz", "Edición de imagen", "Mejorar vídeo", "Sincronización labial")
        val icons = categories.map { toolCategoryIcon(it) }
        assertEquals(categories.size, icons.distinct().size)
        assertFalse(icons.any { it == Icons.Default.Build })
    }

    @Test fun toolsNeverAppearAsCreationModels() {
        val upscaler = base.copy(id = "x/video/upscaler", providerModelId = "x/video/upscaler", modalities = listOf(Modality.VIDEO), capabilities = listOf(ModelCapability.UPSCALE))
        val lipsync = base.copy(id = "x/lip-sync", providerModelId = "x/lip-sync", modalities = listOf(Modality.VIDEO), capabilities = emptyList())
        val creator = base.copy(id = "x/text-to-video", providerModelId = "x/text-to-video", modalities = listOf(Modality.VIDEO), capabilities = listOf(ModelCapability.TEXT_TO_VIDEO))
        assertNotNull(com.vidadomu.nexus.ui.models.toolCategory(upscaler))
        assertNotNull(com.vidadomu.nexus.ui.models.toolCategory(lipsync))
        assertNull(com.vidadomu.nexus.ui.models.toolCategory(creator))
    }
}