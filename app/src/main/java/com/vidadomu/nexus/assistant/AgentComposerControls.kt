package com.vidadomu.nexus.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.theme.NexusTokens

internal data class AgentReasoningMode(val name: String, val effort: String?, val thinking: String?)

internal fun agentReasoningModes(antigravity: Boolean): List<AgentReasoningMode> =
    if (antigravity) listOf(AgentReasoningMode("Bajo", "low", null), AgentReasoningMode("Medio", "medium", null), AgentReasoningMode("Alto", "high", null))
    else listOf(
        AgentReasoningMode("Auto", null, null), AgentReasoningMode("Rápido", null, "disabled"), AgentReasoningMode("Mínimo", "minimal", null),
        AgentReasoningMode("Bajo", "low", null), AgentReasoningMode("Medio", "medium", null), AgentReasoningMode("Alto", "high", null))

internal fun agentReasoningCurrent(modes: List<AgentReasoningMode>, effort: String?, thinking: String?): String =
    modes.firstOrNull { it.effort == effort && it.thinking == thinking }?.name ?: "Auto"

/** Primero los integrados y después los propios, como en el menú. */
internal fun agentBrainLabel(current: String, options: List<Pair<String, String>>): String =
    options.firstOrNull { it.first == current }?.second ?: "Cerebro"

internal const val AGENT_CONTROL_BUSY = "No se puede cambiar mientras el agente responde"

/** Selector compacto del compositor: etiqueta fija, valor actual completo y 48 dp táctiles. */
@Composable
internal fun AgentControlButton(
    label: String,
    value: String,
    icon: ImageVector,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, enabled = enabled, shape = NexusTokens.filter, color = colors.surfaceContainerHigh,
        contentColor = if (enabled) colors.onSurface else colors.onSurfaceVariant,
        modifier = modifier.heightIn(min = NexusTokens.minimumTouch).semantics(mergeDescendants = true) {
            contentDescription = description
            stateDescription = state ?: value
        }) {
        Row(Modifier.padding(start = NexusTokens.space12, end = NexusTokens.space8, top = NexusTokens.space4, bottom = NexusTokens.space4),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            Icon(icon, null, Modifier.size(NexusTokens.iconSmall), tint = if (enabled) colors.primary else colors.onSurfaceVariant)
            Column(Modifier.weight(1f, fill = false)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.labelLarge)
            }
            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(NexusTokens.iconMedium))
        }
    }
}
