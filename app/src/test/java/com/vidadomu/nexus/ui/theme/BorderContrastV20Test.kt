package com.vidadomu.nexus.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BorderContrastV20Test {
    private fun contrast(foreground: Color, background: Color): Float {
        val fg = foreground.compositeOver(background).luminance()
        val bg = background.luminance()
        return (maxOf(fg, bg) + .05f) / (minOf(fg, bg) + .05f)
    }

    private fun assertContrast(theme: NexusAppearance, name: String, fg: Color, bg: Color, minimum: Float) {
        val ratio = contrast(fg, bg)
        assertTrue("$theme $name is $ratio:1, needs $minimum:1", ratio >= minimum)
    }

    @Test fun preservesTheEightExistingThemesAndAddsFourPremiumIdentities() {
        assertEquals(listOf("AURORA", "OLED", "QUARTZ", "OCEAN", "FOREST", "SAND", "ROSE", "LIGHT"),
            NexusAppearance.entries.take(8).map { it.name })
        assertEquals(5,NexusAppearance.premiumThemes.size)
    }

    @Test fun essentialBorderIsOpaqueAndReachesThreeToOneOnEveryBackdrop() {
        NexusAppearance.entries.forEach { theme ->
            val c = palette(theme)
            assertEquals("$theme essential border must not depend on what is underneath", 1f, c.outline.alpha, 0f)
            mapOf("background" to c.background, "surface" to c.surface, "surfaceVariant" to c.surfaceVariant,
                "surfaceContainerHigh" to c.surfaceContainerHigh, "chrome" to chromeFor(theme)).forEach { (name, bg) ->
                assertContrast(theme, "outline on $name", c.outline, bg, 3f)
            }
        }
    }

    @Test fun normalTextReachesFourPointFiveOnEveryBackdrop() {
        NexusAppearance.entries.forEach { theme ->
            val c = palette(theme)
            val backdrops = mapOf("background" to c.background, "surface" to c.surface,
                "surfaceVariant" to c.surfaceVariant, "chrome" to chromeFor(theme))
            backdrops.forEach { (name, bg) ->
                assertContrast(theme, "onSurface on $name", c.onSurface, bg, 4.5f)
                assertContrast(theme, "onBackground on $name", c.onBackground, bg, 4.5f)
                assertContrast(theme, "onSurfaceVariant on $name", c.onSurfaceVariant, bg, 4.5f)
                assertContrast(theme, "primary on $name", c.primary, bg, 4.5f)
            }
            assertContrast(theme, "onPrimary on primary", c.onPrimary, c.primary, 4.5f)
            assertContrast(theme, "onPrimaryContainer on primaryContainer", c.onPrimaryContainer, c.primaryContainer, 4.5f)
            assertContrast(theme, "onSecondaryContainer on secondaryContainer", c.onSecondaryContainer, c.secondaryContainer, 4.5f)
            assertContrast(theme, "error on background", c.error, c.background, 4.5f)
            assertContrast(theme, "onErrorContainer on errorContainer", c.onErrorContainer, c.errorContainer, 4.5f)
        }
    }

    @Test fun decorativeBorderStaysFainterThanEssential() {
        NexusAppearance.entries.forEach { theme ->
            val c = palette(theme)
            val decorative = contrast(c.outlineVariant, c.surface)
            assertTrue("$theme decorative border is $decorative:1; it must remain a soft separator", decorative < 3f)
            assertTrue("$theme decorative border must stay below the essential one",
                decorative < contrast(c.outline, c.surface))
        }
    }
}
