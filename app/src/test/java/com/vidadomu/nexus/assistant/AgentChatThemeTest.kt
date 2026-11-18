package com.vidadomu.nexus.assistant

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.vidadomu.nexus.ui.theme.NexusAppearance
import com.vidadomu.nexus.ui.theme.NexusThemeSpecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Agent's charcoal finish, measured: neutral surfaces, the theme's own accent and readable text on all of them. */
class AgentChatThemeTest {
    private fun ratio(a: Color, b: Color): Float {
        val x = a.compositeOver(b).luminance(); val y = b.luminance()
        return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
    }
    private fun spread(c: Color) = maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue)
    private val themes = NexusAppearance.values().toList()
    private val darkThemes = themes.filter { NexusThemeSpecs.forAppearance(it).isDark }

    @Test fun everyDarkThemeWearsTheSameNeutralCharcoalAndKeepsItsOwnAccent() {
        assertTrue("hay temas oscuros que medir", darkThemes.size >= 4)
        val schemes = darkThemes.map { AgentChatColors.matte(NexusThemeSpecs.forAppearance(it).colors) }
        assertEquals("un solo fondo para todos", setOf(AgentChatColors.Ground), schemes.map { it.background }.toSet())
        darkThemes.zip(schemes).forEach { (theme, c) ->
            assertEquals("$theme conserva su acento", NexusThemeSpecs.forAppearance(theme).colors.primary, c.primary)
            for (surface in listOf(c.background, c.surface, c.surfaceContainerLowest, c.surfaceContainerLow, c.surfaceContainer,
                c.surfaceContainerHigh, c.surfaceContainerHighest, c.surfaceVariant, c.outlineVariant))
                assertTrue("$theme: las superficies son grises, sin tinte (${spread(surface)})", spread(surface) <= .04f)
            assertTrue("$theme: fondo carbón, no negro ni azul noche", c.background.luminance() in .008f..0.02f)
            assertTrue("$theme: el mensaje del usuario se distingue del fondo sin contorno", ratio(c.surfaceContainerHigh, c.background) in 1.1f..1.5f)
            assertTrue("$theme: la línea de actividad es discreta", ratio(c.outlineVariant, c.background) in 1.3f..2.2f)
        }
    }

    @Test fun textStatesAndActionsAreReadableOnEveryMatteSurface() {
        darkThemes.forEach { theme ->
            val c = AgentChatColors.matte(NexusThemeSpecs.forAppearance(theme).colors)
            val surfaces = listOf(c.background, c.surfaceContainerLowest, c.surfaceContainerLow, c.surfaceContainer, c.surfaceContainerHigh)
            for (surface in surfaces + c.surfaceContainerHighest) for (text in listOf(c.onSurface, c.onSurfaceVariant, c.error))
                assertTrue("$theme texto ${ratio(text, surface)}", ratio(text, surface) >= 4.5f)
            // Durations, commands and detail titles are written in the outline tone.
            for (surface in surfaces) assertTrue("$theme texto tenue ${ratio(c.outline, surface)}", ratio(c.outline, surface) >= 4.5f)
            assertTrue("$theme icono tenue", ratio(c.outline, c.surfaceContainerHighest) >= 3f)
            assertTrue("$theme estado activo sobre el fondo", ratio(c.primary, c.background) >= 4.5f)
            assertTrue("$theme botón de enviar", ratio(c.onPrimary, c.primary) >= 4.5f)
            assertTrue("$theme píldora de modo ${ratio(c.onPrimaryContainer, c.primaryContainer)}", ratio(c.onPrimaryContainer, c.primaryContainer) >= 4.5f)
            assertTrue("$theme botón de detener", ratio(c.surfaceContainerHigh, c.onSurface) >= 4.5f)
        }
    }

    @Test fun lightThemesKeepTheirOwnPaperAndInk() {
        val light = themes.filterNot { NexusThemeSpecs.forAppearance(it).isDark }
        assertTrue(light.isNotEmpty())
        light.forEach { theme ->
            val brand = NexusThemeSpecs.forAppearance(theme).colors
            val c = AgentChatColors.matte(brand)
            assertEquals("$theme fondo", brand.background, c.background)
            assertEquals("$theme texto", brand.onSurface, c.onSurface)
            assertEquals("$theme acento", brand.primary, c.primary)
            assertEquals("$theme superficies", brand.surfaceContainerHigh, c.surfaceContainerHigh)
        }
    }

    @Test fun theSameThemeAlwaysGivesTheSameScheme() {
        val brand = NexusThemeSpecs.forAppearance(NexusAppearance.NOCTURNE).colors
        assertEquals(AgentChatColors.dark(brand).primaryContainer, AgentChatColors.dark(brand).primaryContainer)
        assertEquals(AgentChatColors.matte(brand).background, AgentChatColors.dark(brand).background)
    }
}
