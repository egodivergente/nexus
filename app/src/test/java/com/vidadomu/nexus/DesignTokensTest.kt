package com.vidadomu.nexus

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import com.vidadomu.nexus.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class DesignTokensTest {
    private fun contrast(a: Color,b: Color): Float {
        val x=a.luminance(); val y=b.luminance()
        return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
    }
    @Test fun readableTextAcrossEveryTheme() {
        NexusAppearance.entries.forEach { theme ->
            val c=palette(theme)
            listOf(c.onSurface to c.surface, c.onSurfaceVariant to c.surface,
                c.onSurfaceVariant to c.surfaceVariant,c.onPrimary to c.primary,
                c.onPrimaryContainer to c.primaryContainer,c.onSecondaryContainer to c.secondaryContainer,
                c.error to c.surface,c.onErrorContainer to c.errorContainer).forEach { (text,bg) ->
                assertTrue("$theme text contrast is ${contrast(text,bg)}",contrast(text,bg)>=4.5f)
            }
        }
    }
    @Test fun typographyHasNoStyleBelowTwelve() {
        val t=nexusTypography(FontFamily.Default,FontFamily.Default)
        listOf(t.displayLarge,t.displayMedium,t.displaySmall,t.headlineLarge,t.headlineMedium,t.headlineSmall,
            t.titleLarge,t.titleMedium,t.titleSmall,t.bodyLarge,t.bodyMedium,t.bodySmall,
            t.labelLarge,t.labelMedium,t.labelSmall).forEach { assertTrue(it.fontSize.value>=12f) }
        assertEquals(24f,t.titleLarge.fontSize.value,0f)
        assertEquals(17f,t.titleMedium.fontSize.value,0f)
        assertEquals("tnum",t.labelMedium.tabularMoney().fontFeatureSettings)
    }
}
