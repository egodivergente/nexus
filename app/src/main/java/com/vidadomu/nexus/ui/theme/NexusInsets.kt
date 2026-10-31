package com.vidadomu.nexus.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

// Contrato: la barra superior gestiona el borde superior; el borde inferior aplica UNA vez max(IME, navigationBars).
// Lo ya consumido por un padre (safeDrawing horizontal e imePadding de MainActivity, barra inferior) no se vuelve a sumar.
// Nexus oculta las barras (applyNexusFullscreen): systemBars vale 0 y solo displayCutout aparta la cámara. No se usa
// statusBarsIgnoringVisibility: reservaría la barra oculta en apaisado y en móviles sin recorte, donde nada tapa.
object NexusInsets {
    /** Cabeceras dentro del árbol principal, cuyo borde horizontal ya consume MainActivity. */
    val top: WindowInsets
        @Composable get() = nexusChromeInsets(WindowInsets.systemBars, WindowInsets.displayCutout, WindowInsetsSides.Top)

    /** Barras y botones superpuestos, también en diálogos propios donde nadie ha consumido el recorte lateral. */
    val chromeOnly: WindowInsets
        @Composable get() = nexusChromeInsets(WindowInsets.systemBars, WindowInsets.displayCutout,
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

    /** Diálogos a pantalla completa que antes usaban systemBarsPadding(). */
    val fullScreen: WindowInsets
        @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    val bottomEdge: WindowInsets
        @Composable get() = WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)
}

// Separado de los getters para probarlo con insets falsos: max(barras, recorte) por lado, nunca la suma.
internal fun nexusChromeInsets(systemBars: WindowInsets, displayCutout: WindowInsets, sides: WindowInsetsSides): WindowInsets =
    systemBars.union(displayCutout).only(sides)
