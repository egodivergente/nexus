package com.vidadomu.nexus.ui.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.vidadomu.nexus.ui.theme.NexusTokens

/** One titled group of the Avanzado sheet's ways out: a quiet card with a row per destination. */
@Composable
internal fun AdvancedLinkGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Surface(shape = NexusTokens.field, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** A full-width row of [AdvancedLinkGroup]; [opensSomething] adds the chevron of a row that leads elsewhere. */
@Composable
internal fun AdvancedLink(label: String, icon: ImageVector, enabled: Boolean = true, opensSomething: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val alpha = if (enabled) 1f else .38f
    Row(Modifier.fillMaxWidth().heightIn(min = NexusTokens.minimumTouch)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = NexusTokens.space16, vertical = NexusTokens.space8),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
        Icon(icon, null, Modifier.size(NexusTokens.iconSmall), tint = colors.primary.copy(alpha = alpha))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface.copy(alpha = alpha))
        if (opensSomething) Icon(Icons.Default.ChevronRight, null, Modifier.size(NexusTokens.iconSmall), tint = colors.onSurfaceVariant.copy(alpha = alpha))
    }
}

/** The adapted settings, each once, and their reason once when every adaptation shares it. */
internal fun adaptationLines(adaptations: List<Triple<String, String, String>>, reasons: List<String>): Pair<List<String>, String?> {
    val distinct = reasons.filter { it.isNotBlank() }.distinct()
    val shared = distinct.singleOrNull()?.takeIf { reasons.all { it == distinct.single() } }
    val lines = adaptations.mapIndexed { i, (label, from, to) ->
        val reason = reasons.getOrNull(i).orEmpty()
        "$label: $from → $to" + if (shared == null && reason.isNotBlank()) ". $reason" else ""
    }
    return lines to shared
}
