package com.vidadomu.nexus.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

// Contrato: la barra superior gestiona statusBars; el borde inferior aplica UNA vez max(IME, navigationBars).
// Lo ya consumido por un padre (imePadding de MainActivity, barra inferior) no se vuelve a sumar.
object NexusInsets {
    val chromeOnly: WindowInsets
        @Composable get() = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

    val bottomEdge: WindowInsets
        @Composable get() = WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)
}
