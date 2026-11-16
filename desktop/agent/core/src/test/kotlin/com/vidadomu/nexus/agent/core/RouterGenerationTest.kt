package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.registry.MediaSpec
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.registry.ModelSchema
import com.vidadomu.nexus.agent.core.registry.ModelSummary
import com.vidadomu.nexus.agent.core.registry.ParamSpec
import com.vidadomu.nexus.agent.core.router.ModelRouter
import com.vidadomu.nexus.agent.core.router.TaskSpec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regresión 02-10: el router recomendaba Seedance v1.5 Pro por llevar «pro» en el id, por encima de Seedance 2.5. */
class RouterGenerationTest {
    private val sd15 = ModelRef(ATLAS, "bytedance/seedance-v1.5-pro/text-to-video")
    private val sd20f = ModelRef(ATLAS, "bytedance/seedance-2.0-fast/text-to-video")
    private val sd25 = ModelRef(ATLAS, "bytedance/seedance-2.5/text-to-video")
    private val veo = ModelRef(ATLAS, "google/veo3.1/text-to-video")

    private fun t2v(ref: ModelRef, name: String, flagship: Boolean = false) = ModelSummary(
        ref = ref, name = name, modalities = setOf("VIDEO"), capabilities = setOf("TEXT_TO_VIDEO"), inputMode = "TEXT_ONLY",
        releaseMonth = null, isFlagship = flagship, supportsAudio = true, catalogDurations = listOf("5", "8", "10"),
    )
    private fun schema(ref: ModelRef) = ModelSchema(ref, listOf(
        ParamSpec("duration", "Duración", "DURATION", "integer", min = 4.0, max = 12.0, default = "5"),
        ParamSpec("resolution", "Resolución", "RESOLUTION", "string", options = listOf("720p", "1080p"), default = "720p"),
    ), MediaSpec(inputMode = "TEXT_ONLY"))

    private val models = listOf(t2v(sd15, "Seedance v1.5 Pro", flagship = true), t2v(sd20f, "Seedance 2.0 Fast"), t2v(sd25, "Seedance 2.5"), t2v(veo, "Veo 3.1"))
    private val registry = FakeRegistry(models, models.associate { it.ref to schema(it.ref) })
    private val task = TaskSpec("VIDEO", "text_to_video", durationSec = 8, priorities = listOf("cinematic_look"), budget = "medium")

    @Test fun `version del slug`() {
        assertEquals(1.5, ModelRouter.generationOf("bytedance/seedance-v1.5-pro/text-to-video"))
        assertEquals(2.5, ModelRouter.generationOf("bytedance/seedance-2.5/text-to-video"))
        assertEquals(2.0, ModelRouter.generationOf("bytedance/seedance-2.0-fast/image-to-video"))
        assertEquals(1.0, ModelRouter.generationOf("bytedance/seedance-v1-pro-i2v-720p"))
        assertEquals(3.1, ModelRouter.generationOf("google/veo3.1/text-to-video"))
        assertEquals(3.0, ModelRouter.generationOf("kwaivgi/kling-video-o3-pro/image-to-video"))
        assertNull(ModelRouter.generationOf("google/nano-banana-pro/edit"))
    }

    @Test fun `la generacion actual gana al pro antiguo`() = runTest {
        val r = ModelRouter(registry, realSkills(), prices()).route(task)
        val all = listOfNotNull(r.recommended) + r.alternatives
        val seedance = all.filter { it.ref.modelId.contains("seedance") }
        assertEquals(sd25, seedance.first().ref, "dentro de Seedance manda 2.5: ${all.map { it.ref.modelId to it.score }}")
        val old = all.firstOrNull { it.ref == sd15 }
        if (old != null) assertTrue(old.warnings.any { it.contains("Generación anterior") })
    }

    /** 14-11: con 9:16 el router ignoraba los modelos que publican 9:16 como tamaño en píxeles («1584*2816»). */
    @Test fun `el formato publicado como tamano en pixeles se traduce`() = runTest {
        val pixels = ModelRef(ATLAS, "bytedance/seedream-v5.5/text-to-image")
        val ratio = ModelRef(ATLAS, "bytedance/seedream-v4/text-to-image")
        fun image(ref: ModelRef, name: String) = ModelSummary(ref = ref, name = name, modalities = setOf("IMAGE"),
            capabilities = setOf("TEXT_TO_IMAGE"), inputMode = "TEXT_ONLY", releaseMonth = null, isFlagship = false,
            supportsAudio = false, catalogDurations = emptyList())
        val schemas = mapOf(
            pixels to ModelSchema(pixels, listOf(ParamSpec("size", "Tamaño", "ENUM", "string",
                options = listOf("2048*2048", "2816*1584", "1584*2816", "1152*2048"), default = "2048*2048")), MediaSpec(inputMode = "TEXT_ONLY")),
            ratio to ModelSchema(ratio, listOf(ParamSpec("aspect_ratio", "Formato", "ASPECT_RATIO", "string",
                options = listOf("1:1", "9:16", "16:9"), default = "1:1")), MediaSpec(inputMode = "TEXT_ONLY")))
        val router = ModelRouter(FakeRegistry(listOf(image(pixels, "Seedream v5.5"), image(ratio, "Seedream v4")), schemas), realSkills(), prices())
        val r = router.route(TaskSpec("IMAGE", "text_to_image", aspectRatio = "9:16", limit = 3))
        val all = listOfNotNull(r.recommended) + r.alternatives
        assertEquals("1584*2816", all.first { it.ref == pixels }.suggestedParams["size"], "el mayor tamaño 9:16")
        assertEquals("9:16", all.first { it.ref == ratio }.suggestedParams["aspect_ratio"])
    }

    @Test fun `veo excluido no se recomienda`() = runTest {
        val r = ModelRouter(registry, realSkills(), prices(), excludedFamilies = { setOf("veo") }).route(task)
        val all = listOfNotNull(r.recommended) + r.alternatives
        assertNotNull(r.recommended)
        assertTrue(all.none { it.ref == veo }, all.map { it.ref.modelId }.toString())
        assertEquals(1, r.rejected["familia excluida en tus ajustes"])
    }

    @Test fun `sin exclusion veo puede aparecer`() = runTest {
        val r = ModelRouter(registry, realSkills(), prices()).route(task)
        assertTrue((listOfNotNull(r.recommended) + r.alternatives).any { it.ref == veo })
    }
}
