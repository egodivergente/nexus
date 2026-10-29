package com.vidadomu.nexus.assistant

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.components.noticeMessage
import com.vidadomu.nexus.ui.theme.NexusTokens

internal data class AgentErrorNoticeContent(val cause: String, val status: String, val technical: String)

/**
 * Causa y estado legibles sin abrir el detalle. El estado solo describe lo que la app sabe
 * (si conserva el mensaje para reintentar); nunca afirma si el mensaje llegó o no al proveedor.
 */
internal fun agentErrorNoticeContent(raw: String, canRetry: Boolean): AgentErrorNoticeContent =
    AgentErrorNoticeContent(
        cause = noticeMessage(raw, "El agente no ha podido completar esta respuesta."),
        status = if (canRetry) "Reintentar usa el mismo texto y los mismos adjuntos."
            else "Reintento no disponible para este aviso.",
        technical = raw,
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AgentErrorNotice(
    id: String,
    raw: String,
    onRetry: (() -> Unit)?,
    onChangeModel: (() -> Unit)?,
    onNewChat: (() -> Unit)?,
    onOpenHistory: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val content = remember(raw, onRetry != null) { agentErrorNoticeContent(raw, onRetry != null) }
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val touch = Modifier.heightIn(min = NexusTokens.minimumTouch)
    Surface(modifier.fillMaxWidth().animateContentSize(), shape = NexusTokens.card,
        color = colors.errorContainer.copy(alpha = .55f), contentColor = colors.onSurface) {
        Column(Modifier.padding(NexusTokens.space12), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = "Error del agente", tint = colors.error, modifier = Modifier.size(NexusTokens.iconMedium))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                    Text(content.cause, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() })
                    Text(content.status, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                if (onRetry != null) FilledTonalButton(onClick = onRetry, shape = NexusTokens.filter, modifier = touch) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(NexusTokens.iconSmall)); Spacer(Modifier.width(NexusTokens.space4)); Text("Reintentar")
                }
                if (onNewChat != null) FilledTonalButton(onClick = onNewChat, shape = NexusTokens.filter, modifier = touch) {
                    Icon(Icons.Default.AddComment, null, Modifier.size(NexusTokens.iconSmall)); Spacer(Modifier.width(NexusTokens.space4)); Text("Continuar en chat nuevo")
                }
                if (onChangeModel != null) TextButton(onClick = onChangeModel, modifier = touch) {
                    Icon(Icons.Default.Tune, null, Modifier.size(NexusTokens.iconSmall)); Spacer(Modifier.width(NexusTokens.space4)); Text("Cambiar modelo")
                }
                if (onOpenHistory != null) TextButton(onClick = onOpenHistory, modifier = touch) {
                    Icon(Icons.Default.Timelapse, null, Modifier.size(NexusTokens.iconSmall)); Spacer(Modifier.width(NexusTokens.space4)); Text("Abrir Historial")
                }
                TextButton(onClick = { expanded = !expanded },
                    modifier = touch.semantics { stateDescription = if (expanded) "Detalle técnico visible" else "Detalle técnico oculto" }) {
                    Text(if (expanded) "Ocultar detalle técnico" else "Detalle técnico", color = colors.onSurfaceVariant)
                }
            }
            if (expanded) SelectionContainer {
                Text(content.technical, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
