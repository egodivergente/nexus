package com.vidadomu.nexus
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.create.ParameterPresentation
import org.junit.Assert.*
import org.junit.Test

class ParameterPresentationTest {
    private val p = ModelParameter("ratio","Formato y resolución",ParameterType.ENUM, options=listOf("1280:720","720:1280"))
    @Test fun familiarRatiosLeadAndPixelsRemainSecondary() {
        assertEquals("16:9 · Horizontal", ParameterPresentation.label(p,"1280:720").primary)
        assertEquals("9:16 · Vertical", ParameterPresentation.label(p,"720:1280").primary)
        assertEquals("1280 × 720 px", ParameterPresentation.label(p,"1280:720").secondary)
        assertEquals("1:1 · Cuadrado", ParameterPresentation.label(p,"1024x1024").primary)
        assertEquals("4:3 · Horizontal", ParameterPresentation.label(p,"1024:768").primary)
        assertEquals("21:9 · Horizontal", ParameterPresentation.label(p,"1792:768").primary)
        assertEquals("3:4 · Vertical", ParameterPresentation.label(p,"3:4").primary)
        assertNull(ParameterPresentation.label(p,"3:4").secondary)
    }
    @Test fun approximationsAndUnknownFormatsStayHonest() {
        assertEquals("≈ 4:3 · Horizontal", ParameterPresentation.label(p,"1104:832").primary)
        assertTrue(ParameterPresentation.label(p,"1104:832").secondary!!.contains("aproximada"))
        assertEquals("1k",ParameterPresentation.label(p,"1k").primary)
        assertEquals("0:0",ParameterPresentation.label(p,"0:0").primary)
        assertEquals("1280:720",ParameterPresentation.label(p.copy(key="seed"),"1280:720").primary)
    }
    @Test fun nativeStarDimensionsRemainValidAndPresetsKeepTheApiSyntax() {
        val size = ModelParameter("size","Tamaño",ParameterType.TEXT,"1024*1024", minValue=256f,maxValue=2048f,nativeType="string")
        assertNull(FormContract.invalidValue(listOf(size),mapOf("size" to "1024*1024")))
        assertNotNull(FormContract.invalidValue(listOf(size),mapOf("size" to "4096*1024")))
        assertEquals("16:9 · Horizontal",ParameterPresentation.label(size,"1280*720").primary)
        assertTrue(ParameterPresentation.options(size).contains("720*1280"))
        assertTrue(ParameterPresentation.options(size).all { '*' in it })
    }
    @Test fun labelsNeverModifyNativeOptionsOrSelections() {
        val before=p.copy()
        p.options.forEach { ParameterPresentation.describe(p,it) }
        assertEquals(before,p)
        assertEquals(listOf("1280:720","720:1280"),p.options)
        assertEquals("Formato",ParameterPresentation.title(p))
    }
}
