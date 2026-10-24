package com.vidadomu.nexus.ui.models

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.theme.NexusTokens

internal data class ArtBoxTab(val label: String, val selected: Boolean, val icon: ImageVector? = null, val onClick: () -> Unit)

/** Text tabs with a thin indicator: the active one is clear without turning into a large button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArtBoxTabs(tabs: List<ArtBoxTab>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // With large system text the tabs wrap onto a second line instead of running off the screen edge.
    val wrap = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.25f
    val container: Modifier = if (wrap) modifier.fillMaxWidth().selectableGroup()
        else modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()
    FlowRow(container, horizontalArrangement = Arrangement.spacedBy(2.dp), maxLines = if (wrap) 3 else 1) {
        tabs.forEach { tab ->
            val indicator by animateDpAsState(if (tab.selected) 24.dp else 0.dp, label = "ArtBox tab indicator")
            val tint = if (tab.selected) colors.primary else colors.onSurface.copy(alpha = .74f)
            Column(Modifier.clip(NexusTokens.filter).selectable(tab.selected, role = Role.Tab, onClick = tab.onClick)
                .heightIn(min = 44.dp).padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    tab.icon?.let {
                        Icon(it, null, Modifier.size(16.dp), tint = tint)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(tab.label, style = MaterialTheme.typography.titleSmall, color = tint, maxLines = 1,
                        fontWeight = if (tab.selected) FontWeight.SemiBold else FontWeight.Normal)
                }
                Box(Modifier.padding(top = 4.dp).height(2.dp).width(indicator)
                    .background(colors.primary, RoundedCornerShape(1.dp)))
            }
        }
    }
}

/** One compact row (48 dp): search plus the single entry point to filters and sorting. */
@Composable
internal fun ArtBoxSearchBar(query: String, onQuery: (String) -> Unit, filtersActive: Boolean,
    filterDescription: String, onFilters: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = NexusTokens.filter, color = colors.surfaceContainerLow,
        border = BorderStroke(.5.dp, colors.outlineVariant.copy(alpha = .7f)),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            Box(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp)) {
                if (query.isEmpty()) Text("Buscar modelo o función", style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant, maxLines = 1)
                BasicTextField(query, onQuery, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Buscar modelo o función" })
            }
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                Icon(Icons.Default.Close, "Borrar búsqueda", Modifier.size(18.dp), tint = colors.onSurfaceVariant)
            }
            IconButton(onClick = onFilters, modifier = Modifier.semantics { contentDescription = filterDescription }) {
                Icon(Icons.Default.Tune, null, tint = if (filtersActive) colors.primary else colors.onSurfaceVariant)
            }
        }
    }
}

internal data class ActiveFilter(val label: String, val onRemove: () -> Unit)

/** Only real, removable filters; nothing is drawn when none is active. */
@Composable
internal fun ActiveFilterChips(filters: List<ActiveFilter>, onClear: () -> Unit) {
    if (filters.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        items(filters, key = { it.label }) { filter ->
            InputChip(selected = true, onClick = filter.onRemove, label = { Text(filter.label, maxLines = 1) },
                trailingIcon = { Icon(Icons.Default.Close, "Quitar filtro ${filter.label}", Modifier.size(16.dp)) },
                shape = NexusTokens.filter,
                colors = InputChipDefaults.inputChipColors(selectedContainerColor = colors.primary.copy(alpha = .14f),
                    selectedLabelColor = colors.primary, selectedTrailingIconColor = colors.primary))
        }
        if (filters.size > 1) item { TextButton(onClick = onClear) { Text("Limpiar") } }
    }
}

/** «Precios desde» only names a unit when every visible model shares one real modality. */
internal fun catalogPriceNote(models: List<com.vidadomu.nexus.core.model.NexusModel>): String? {
    val modalities = models.flatMap { it.modalities }.toSet()
    return when (modalities) {
        setOf(com.vidadomu.nexus.core.model.Modality.VIDEO) -> "Precios desde · por 5 s"
        setOf(com.vidadomu.nexus.core.model.Modality.IMAGE) -> "Precios desde · por imagen"
        else -> null
    }
}
