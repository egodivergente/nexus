package com.vidadomu.nexus.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.rememberNexusMotionAllowed
import kotlin.math.abs

/** Observe unclaimed drags only: horizontal scrolling and text-selection gestures keep priority. */
internal fun Modifier.recentLibraryGesture(opening: Boolean, onChange: () -> Unit): Modifier =
    composed {
    val currentChange by rememberUpdatedState(onChange)
    pointerInput(opening) {
        val threshold = 40.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed || change.isConsumed || event.changes.size != 1) break
                val delta = change.position - down.position
                if (abs(delta.x) > threshold && abs(delta.x) > abs(delta.y)) break
                val directedDistance = if (opening) -delta.y else delta.y
                if (directedDistance >= threshold && abs(delta.y) > abs(delta.x) * 1.5f) {
                    change.consume()
                    currentChange()
                    break
                }
            }
        }
    }
    }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun LibraryAttachmentButton(
    enabled: Boolean, onAttach: () -> Unit, onShowLibrary: (() -> Unit)?, libraryExpanded: Boolean,
) {
    Box(Modifier.size(NexusTokens.minimumTouch)
        .semantics {
            contentDescription = "Adjuntar"
            if (onShowLibrary != null) {
                stateDescription = if (libraryExpanded) "Recientes abiertos" else "Mantén pulsado para ver recientes"
            }
        }
        .combinedClickable(enabled = enabled, role = Role.Button,
            onLongClickLabel = "Mostrar archivos recientes", onLongClick = onShowLibrary, onClick = onAttach),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Default.AttachFile, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f))
    }
}

@Composable
internal fun ReasoningIcon(current: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(NexusTokens.minimumTouch)
        .semantics {
            contentDescription = "Modo de razonamiento: $current"
            stateDescription = if (enabled) current else "$current. No se puede cambiar ahora"
        }) {
        Icon(Icons.Default.Speed, null, Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f))
    }
}

@Composable
internal fun RecentLibraryPanel(expanded: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(enabled = expanded, onBack = onClose)
    val motion = rememberNexusMotionAllowed()
    AnimatedVisibility(expanded,
        enter = if (motion) expandVertically(tween(180), expandFrom = Alignment.Bottom) + fadeIn() else EnterTransition.None,
        exit = if (motion) shrinkVertically(tween(180), shrinkTowards = Alignment.Bottom) + fadeOut() else ExitTransition.None) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().recentLibraryGesture(opening = false, onChange = onClose)
                .padding(bottom = NexusTokens.space8)) {
                Row(Modifier.fillMaxWidth().padding(start = NexusTokens.space12), verticalAlignment = Alignment.CenterVertically) {
                    Text("Archivos recientes", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Ocultar archivos recientes", Modifier.size(18.dp)) }
                }
                content()
            }
        }
    }
}
