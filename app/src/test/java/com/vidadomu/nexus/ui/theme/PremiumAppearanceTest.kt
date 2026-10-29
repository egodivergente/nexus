package com.vidadomu.nexus.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class PremiumAppearanceTest {
    private fun ratio(a:Color,b:Color):Float {val x=a.compositeOver(b).luminance();val y=b.luminance();return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)}
    @Test fun fiveIdentitiesHaveDifferentCompositionLightingAndSurfaceRoles() {
        val specs=NexusAppearance.premiumThemes.map(NexusThemeSpecs::forAppearance)
        assertEquals(5,specs.map {it.ambientStyle}.toSet().size)
        assertEquals(5,specs.map {it.colors.background}.toSet().size)
        assertEquals(1,specs.count {!it.isDark})
        assertEquals(Color.Black,NexusThemeSpecs.forAppearance(NexusAppearance.OBSIDIAN).colors.background)
    }
    @Test fun semanticTextAndStateColorsAreReadableOnStableFunctionalSurfaces() {
        NexusAppearance.premiumThemes.forEach {theme ->
            val c=NexusThemeSpecs.forAppearance(theme).colors;val v=NexusThemeSpecs.visuals(theme)
            for(surface in listOf(v.promptSurface,v.chromeSurface,v.dialogSurface,v.sheetSurface,v.mediaSurface,c.surfaceContainerHigh)) {
                for(text in listOf(c.onSurface,c.onSurfaceVariant,v.disabledText,v.placeholder,v.success,v.warning,v.processing))
                    assertTrue("$theme semantic contrast ${ratio(text,surface)}",ratio(text,surface)>=4.5f)
                assertTrue("$theme essential border",ratio(v.promptBorder,surface)>=3f)
            }
            for(pair in listOf(v.onSuccess to v.success,v.onWarning to v.warning,v.onProcessing to v.processing))
                assertTrue("$theme state label",ratio(pair.first,pair.second)>=4.5f)
        }
    }
    @Test fun invalidAndOutOfRangeIntensityCannotReachRendererOrPreferences() {
        val base=AppearanceSelection(NexusAppearance.AURORA)
        assertEquals(AmbientMode.SUBTLE,base.ambientMode)
        assertEquals(.55f,sanitizedAppearance(base.copy(intensity=Float.NaN)).intensity,0f)
        assertEquals(.55f,sanitizedAppearance(base.copy(intensity=Float.POSITIVE_INFINITY)).intensity,0f)
        assertEquals(0f,sanitizedAppearance(base.copy(intensity=-1f)).intensity,0f)
        assertEquals(1f,sanitizedAppearance(base.copy(intensity=2f)).intensity,0f)
    }
}
