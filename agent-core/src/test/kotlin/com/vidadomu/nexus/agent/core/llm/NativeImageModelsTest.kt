package com.vidadomu.nexus.agent.core.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeImageModelsTest {
    @Test fun `names the Nano Banana family and GPT Image`() {
        assertEquals("Nano Banana 2", NativeImageModels.label("gemini-3.1-flash-image"))
        assertEquals("Nano Banana 2", NativeImageModels.label("gemini-3.1-flash-image-preview"))
        assertEquals("Nano Banana Pro", NativeImageModels.label("gemini-3-pro-image-preview"))
        assertEquals("Nano Banana", NativeImageModels.label("gemini-2.5-flash-image-preview"))
        assertEquals("Nano Banana 2 Lite", NativeImageModels.label("gemini-3.1-flash-image-lite"))
        assertEquals("GPT Image 2", NativeImageModels.label("gpt-image-2"))
        assertEquals("modelo-nuevo", NativeImageModels.label("modelo-nuevo"))
        assertNull(NativeImageModels.label(null))
        assertNull(NativeImageModels.label(" "))
    }

    @Test fun `only Lite models are vetoed`() {
        assertTrue(NativeImageModels.isRejected("gemini-3.1-flash-image-lite"))
        assertTrue(NativeImageModels.isRejected("gemini-3.1-flash-lite-image"))
        assertFalse(NativeImageModels.isRejected("gemini-3.1-flash-image"))
        assertFalse(NativeImageModels.isRejected("gemini-3-pro-image"))
        assertFalse(NativeImageModels.isRejected("satellite-image"))
        assertFalse(NativeImageModels.isRejected(null))
    }
}
