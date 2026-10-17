package com.vidadomu.nexus.ui.components

import com.vidadomu.nexus.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlexiaBrandAssetsNewLogosTest {
    private fun id(text: String) = AlexiaBrandAssets.resolve(text)?.resId

    @Test fun `la marca concreta gana a su casa matriz`() {
        assertEquals(R.drawable.alexia_seedream, id("seedream 5 pro"))
        assertEquals(R.drawable.alexia_seedance, id("seedance 2.0"))
        assertEquals(R.drawable.alexia_veo, id("veo 3.1"))
        assertEquals(R.drawable.alexia_gemini, id("gemini 3 pro"))
        assertEquals(R.drawable.alexia_flow, id("google flow"))
        assertEquals(R.drawable.alexia_bytedance, id("byteplus modelark"))
    }

    @Test fun `proveedores y marcas nuevas se resuelven y las palabras parecidas no se confunden`() {
        assertEquals(R.drawable.alexia_crun, id("crun"))
        assertEquals(R.drawable.alexia_antigravity, id("antigravity"))
        assertEquals(R.drawable.alexia_dola, id("dola-seed-2-1-turbo"))
        assertEquals(R.drawable.alexia_udio, id("udio"))
        assertNull(id("studio"))
        assertNull(id("async"))
    }
}