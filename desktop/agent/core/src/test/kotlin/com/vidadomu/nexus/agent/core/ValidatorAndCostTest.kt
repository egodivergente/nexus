package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.cost.CostConfidence
import com.vidadomu.nexus.agent.core.schema.ParameterValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ValidatorAndCostTest {
    private val kling = SCHEMAS.getValue(KLING_PRO)
    private val veo = SCHEMAS.getValue(VEO)

    @Test fun `parametro inventado es error`() {
        val r = ParameterValidator.validate(kling, mapOf("duration" to "8", "camera_speed" to "slow"))
        assertFalse(r.ok)
        assertTrue(r.errors.single().message.contains("camera_speed"))
    }

    @Test fun `vacios y guion no se envian`() {
        val r = ParameterValidator.validate(kling, mapOf("duration" to "8", "sound" to "–", "multi_shot" to "", "x" to null))
        assertTrue(r.ok)
        assertEquals(mapOf("duration" to "8"), r.normalized)
        assertEquals(setOf("sound", "multi_shot", "x"), r.dropped.toSet())
    }

    @Test fun `rango del schema manda sobre el catalogo`() {
        assertTrue(ParameterValidator.validate(kling, mapOf("duration" to "8")).ok) // el catálogo decía 5/10
        assertFalse(ParameterValidator.validate(kling, mapOf("duration" to "16")).ok)
        assertFalse(ParameterValidator.validate(kling, mapOf("duration" to "7.5")).ok)
        assertEquals("8", ParameterValidator.validate(kling, mapOf("duration" to "8.0")).normalized["duration"])
    }

    @Test fun `opciones y booleanos`() {
        assertFalse(ParameterValidator.validate(veo, mapOf("duration" to "10")).ok)
        assertEquals("1080p", ParameterValidator.validate(veo, mapOf("resolution" to "1080P")).normalized["resolution"])
        assertEquals("true", ParameterValidator.validate(veo, mapOf("generate_audio" to "TRUE")).normalized["generate_audio"])
        assertFalse(ParameterValidator.validate(veo, mapOf("generate_audio" to "si")).ok)
    }

    @Test fun `prompt y medios no van en parametros`() {
        val r = ParameterValidator.validate(kling, mapOf("prompt" to "hola", "image" to "content://x", "end_image" to "y"))
        assertEquals(3, r.errors.size)
    }

    @Test fun `campo obligatorio no soportado bloquea`() {
        assertFalse(ParameterValidator.validate(SCHEMAS.getValue(BROKEN), emptyMap()).ok)
    }

    @Test fun `efectivos incluyen defaults sin vacios`() {
        val eff = ParameterValidator.effective(kling, mapOf("duration" to "8"))
        assertEquals(mapOf("duration" to "8", "sound" to "false", "multi_shot" to "false"), eff)
    }

    @Test fun `coste por segundo con tabla publicada`() = runTest {
        val e = prices().estimate(MODELS.first { it.ref == KLING_PRO }, mapOf("duration" to "8"))
        assertEquals(CostConfidence.PUBLISHED_RATE, e.confidence)
        assertEquals(0.76, e.amountUsd!!, 1e-9)
        assertEquals("second", e.unit)
    }

    @Test fun `sin precio no se inventa`() = runTest {
        val e = prices().estimate(MODELS.first { it.ref == BROKEN }, mapOf("duration" to "5"))
        assertNull(e.amountUsd)
        assertEquals(CostConfidence.UNKNOWN, e.confidence)
    }

    @Test fun `imagenes cuentan unidades`() = runTest {
        val e = prices().estimate(MODELS.first { it.ref == SEEDREAM }, mapOf("num_images" to "3"))
        assertEquals(0.108, e.amountUsd!!, 1e-9)
    }
}
