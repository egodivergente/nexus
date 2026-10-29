package com.vidadomu.nexus.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.TextSecondary

/**
 * Lo que estrecha la vista de Biblioteca en esta sesión. Columnas, detalles, favoritos y ocultos guardados
 * viven en preferencias aparte y no forman parte de este estado: restablecerlo nunca los modifica.
 */
internal data class LibraryViewFilters(
    val query: String = "",
    val modality: Modality? = null,
    val origin: LibraryOriginFilter = LibraryOriginFilter.ALL,
    val favoriteOnly: Boolean = false,
    val showHidden: Boolean = false,
    val projects: Set<String> = emptySet(),
) {
    val hasQuery: Boolean get() = query.isNotBlank()
    val hasFilters: Boolean get() = modality != null || origin != LibraryOriginFilter.ALL || favoriteOnly || showHidden || projects.isNotEmpty()
    val narrowed: Boolean get() = hasQuery || hasFilters
    fun withoutFilters() = copy(modality = null, origin = LibraryOriginFilter.ALL, favoriteOnly = false, showHidden = false,projects=emptySet())
    fun cleared() = LibraryViewFilters()
}

internal fun libraryNoResultsMessage(filters: LibraryViewFilters): String = when {
    filters.hasQuery && filters.hasFilters -> "No hay resultados para «${filters.query.trim()}» con estos filtros."
    filters.hasQuery -> "No hay resultados para «${filters.query.trim()}»."
    filters.showHidden -> "No hay archivos ocultos con estos filtros."
    filters.hasFilters -> "No hay archivos con estos filtros."
    else -> "Tus creaciones aparecerán aquí."
}

/** Estado vacío con salida: un toque devuelve la biblioteca completa sin borrar archivos ni preferencias. */
@Composable
internal fun LibraryNoResults(filters: LibraryViewFilters, onApply: (LibraryViewFilters) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(NexusTokens.space32), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        com.vidadomu.nexus.ui.theme.AxelMark(Modifier.size(NexusTokens.minimumTouch))
        Text(libraryNoResultsMessage(filters), style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center)
        if (filters.narrowed) {
            FilledTonalButton(onClick = { onApply(filters.cleared()) }, Modifier.heightIn(min = NexusTokens.minimumTouch)) {
                Text("Ver toda la biblioteca")
            }
            if (filters.hasQuery && filters.hasFilters) TextButton(onClick = { onApply(filters.withoutFilters()) },
                Modifier.heightIn(min = NexusTokens.minimumTouch)) { Text("Quitar filtros y mantener la búsqueda") }
        }
    }
}
