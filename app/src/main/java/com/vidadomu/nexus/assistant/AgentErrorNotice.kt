package com.vidadomu.nexus.assistant

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.components.StudioTactileCard
import com.vidadomu.nexus.ui.components.noticeMessage
import com.vidadomu.nexus.ui.components.studioSpecularBorder
import com.vidadomu.nexus.ui.theme.NexusHapticEvent
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.rememberNexusHaptics
import com.vidadomu.nexus.ui.theme.rememberNexusMotionAllowed

internal data class AgentErrorNoticeContent(val cause: String, val status: String?, val technical: String)

/**
 * Causa legible sin abrir el detalle y, solo si hay algo que añadir, una línea de estado. El estado describe lo que
 * la app sabe (conserva el mensaje y puede repetirlo); nunca afirma si el mensaje llegó o no al proveedor. Sin
 * reintento no hay línea: anunciar lo que no se puede hacer solo añade ruido al aviso.
 */
internal fun agentErrorNoticeContent(raw: String, canRetry: Boolean): AgentErrorNoticeContent {
    val retryStatus = "Reintentar usa el mismo texto y los mismos adjuntos."
    // Un turno que Android cortó no trae error que traducir: se dice tal cual; los adjuntos que falten, en el estado.
    if (raw.startsWith(InterruptedTurns.NOTICE_TEXT)) return AgentErrorNoticeContent(
        cause = InterruptedTurns.NOTICE_TEXT,
        status = if (canRetry) raw.removePrefix(InterruptedTurns.NOTICE_TEXT).trim().ifEmpty { retryStatus } else null,
        technical = InterruptedTurns.TECHNICAL,
    )
    return AgentErrorNoticeContent(
        cause = noticeMessage(raw, "El agente no ha podido completar esta respuesta."),
        status = if (canRetry) retryStatus else null,
        technical = raw,
    )
}

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
    compact: Boolean = false,
    onDismiss: (() -> Unit)? = null,
) {
    val content = remember(raw, onRetry != null) { agentErrorNoticeContent(raw, onRetry != null) }
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    var cardExpanded by rememberSaveable("$id:card") { mutableStateOf(!compact) }
    // When the conversation moves on (another message, a newer failure) the notice steps back to one line by itself;
    // a tap brings the whole card back. A notice that was already old keeps whatever the reader chose.
    var wasCompact by rememberSaveable("$id:compact") { mutableStateOf(compact) }
    if (wasCompact != compact) {
        wasCompact = compact
        cardExpanded = !compact
        if (compact) expanded = false
    }
    val colors = MaterialTheme.colorScheme
    val haptic = rememberNexusHaptics()
    val motion = rememberNexusMotionAllowed()
    val touch = Modifier.heightIn(min = NexusTokens.minimumTouch)
    val errorBorder = BorderStroke(1.dp, colors.error.copy(alpha = 0.28f))
    val container = colors.surface

    // One box for both shapes: the change from card to line (and back) is a single change of height.
    Box(modifier.fillMaxWidth().animateContentSize(if (motion) spring(dampingRatio = 0.84f, stiffness = 460f) else snap())) {
    if (compact && !cardExpanded) {
        StudioTactileCard(
            modifier = Modifier.fillMaxWidth(),
            shape = NexusTokens.pill,
            containerColor = colors.surfaceContainerLow,
            border = studioSpecularBorder(accentTint = colors.error.copy(alpha = 0.35f)),
            shadowElevation = 2.dp,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(NexusTokens.pill)
                    .clickable(role = Role.Button) {
                        haptic.perform(NexusHapticEvent.SELECTION)
                        cardExpanded = true
                    }
                    .padding(horizontal = NexusTokens.space12, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = colors.errorContainer.copy(alpha = 0.7f), modifier = Modifier.size(26.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = "Aviso anterior", tint = colors.error, modifier = Modifier.size(15.dp))
                    }
                }
                Text(
                    content.cause,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ExpandMore, contentDescription = "Expandir aviso", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    } else StudioTactileCard(
        Modifier.fillMaxWidth(),
        shape = NexusTokens.card,
        containerColor = container,
        border = errorBorder,
    ) {
        Column(Modifier.padding(NexusTokens.space12), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12), verticalAlignment = Alignment.Top) {
                Surface(shape = CircleShape, color = colors.errorContainer.copy(alpha = 0.65f), modifier = Modifier.size(34.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = "Error del agente", tint = colors.error, modifier = Modifier.size(NexusTokens.iconSmall))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                    Text(
                        content.cause,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        // The card is the only message of the failure: the current one is announced as it appears.
                        modifier = Modifier.semantics { heading(); if (!compact) liveRegion = LiveRegionMode.Polite },
                    )
                    content.status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                }
                if (compact) {
                    IconButton(
                        onClick = {
                            haptic.perform(NexusHapticEvent.SELECTION)
                            cardExpanded = false
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.ExpandLess, "Contraer aviso", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                if (onRetry != null) FilledTonalButton(
                    onClick = { haptic.perform(NexusHapticEvent.SELECTION); onRetry() },
                    shape = NexusTokens.pill,
                    modifier = touch,
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(NexusTokens.iconSmall)); Spacer(Modifier.width(NexusTokens.space4)); Text("Reintentar")
                }
                if (onDismiss != null) TextButton(onClick = { haptic.perform(NexusHapticEvent.SELECTION); onDismiss() }, modifier = touch) {
                    Text("Descartar")
                }
                if (onNewChat != null) FilledTonalButton(
                    onClick = { haptic.perform(NexusHapticEvent.SELECTION); onNewChat() },
                    shape = NexusTokens.pill,
                    modifier = touch,
                ) {
                    Icon(Icons.Default.AddComment, null, Modifier.size(NexusTokens.iconSmall)); Spacer(Modifier.width(NexusTokens.space4)); Text("Continuar en chat nuevo")
                }
                if (onChangeModel != null) OutlinedButton(
                    onClick = { haptic.perform(NexusHapticEvent.SELECTION); onChangeModel() },
                    shape = NexusTokens.pill,
                    border = studioSpecularBorder(),
                    modifier = touch,
                ) {
                    Icon(Icons.Default.Tune, null, Modifier.size(NexusTokens.iconSmall)); Spacer(Modifier.width(NexusTokens.space4)); Text("Cambiar modelo")
                }
                if (onOpenHistory != null) TextButton(onClick = { haptic.perform(NexusHapticEvent.SELECTION); onOpenHistory() }, modifier = touch) {
                    Icon(Icons.Default.Timelapse, null, Modifier.size(NexusTokens.iconSmall)); Spacer(Modifier.width(NexusTokens.space4)); Text("Abrir Historial")
                }
                TextButton(
                    onClick = {
                        haptic.perform(NexusHapticEvent.SELECTION)
                        expanded = !expanded
                    },
                    modifier = touch.semantics { stateDescription = if (expanded) "Detalle técnico visible" else "Detalle técnico oculto" },
                ) {
                    Text(if (expanded) "Ocultar detalle técnico" else "Detalle técnico", color = colors.onSurfaceVariant)
                }
            }
            if (expanded) SelectionContainer {
                Text(content.technical, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
    }
}
