package com.vidadomu.nexus.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    onCamera: (() -> Unit)? = null, onLive: (() -> Unit)? = null,
) {
    // Un solo «+»: abre el menú con archivos, cámara, recientes y conversación por voz.
    var menu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        Box(Modifier.size(NexusTokens.minimumTouch)
            .semantics { contentDescription = "Añadir"; stateDescription = if (libraryExpanded) "Recientes abiertos" else "Menú de adjuntar" }
            .combinedClickable(enabled = enabled, role = Role.Button,
                onLongClickLabel = "Mostrar archivos recientes", onLongClick = onShowLibrary, onClick = { menu = true }),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Archivo o galería") }, leadingIcon = { Icon(Icons.Default.AttachFile, null) }, onClick = { menu = false; onAttach() })
            onCamera?.let { DropdownMenuItem(text = { Text("Hacer una foto") }, leadingIcon = { Icon(Icons.Default.PhotoCamera, null) }, onClick = { menu = false; it() }) }
            onShowLibrary?.let { DropdownMenuItem(text = { Text("Recientes de la Biblioteca") }, leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) }, onClick = { menu = false; it() }) }
            onLive?.let { DropdownMenuItem(text = { Text("Conversación por voz") }, leadingIcon = { Icon(Icons.Default.GraphicEq, null) }, onClick = { menu = false; it() }) }
        }
    }
}

@Composable
internal fun ReasoningIcon(current: String, enabled: Boolean, onClick: () -> Unit) {
    // Una píldora con el modo actual en vez de un icono mudo: se ve de un vistazo qué esfuerzo está activo.
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, enabled = enabled, shape = NexusTokens.filter,
        color = colors.surfaceContainerHighest.copy(alpha = if (enabled) 1f else .5f),
        modifier = Modifier.heightIn(min = NexusTokens.minimumTouch)
            .semantics {
                contentDescription = "Modo de razonamiento: $current"
                stateDescription = if (enabled) current else "$current. No se puede cambiar ahora"
            }) {
        Row(Modifier.padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space8), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
            Icon(Icons.Default.Speed, null, Modifier.size(14.dp), tint = colors.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f))
            Text(current, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                color = colors.onSurface.copy(alpha = if (enabled) 1f else .38f))
        }
    }
}

@Composable
internal fun RecentLibraryPanel(expanded: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(enabled = expanded, onBack = onClose)
    val motion = rememberNexusMotionAllowed()
    AnimatedVisibility(expanded,
        enter = if (motion) expandVertically(tween(180), expandFrom = Alignment.Bottom) + fadeIn() else EnterTransition.None,
        exit = if (motion) shrinkVertically(tween(180), shrinkTowards = Alignment.Bottom) + fadeOut() else ExitTransition.None) {
        // A floating card with the composer's margins, corners and shadow, not a full-width dark slab over the chat.
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = NexusTokens.card, shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().padding(horizontal = NexusTokens.space12).padding(top = NexusTokens.space4)) {
            Column(Modifier.fillMaxWidth().recentLibraryGesture(opening = false, onChange = onClose)
                .padding(bottom = NexusTokens.space8)) {
                Row(Modifier.fillMaxWidth().padding(start = NexusTokens.space16), verticalAlignment = Alignment.CenterVertically) {
                    Text("Archivos recientes", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Ocultar archivos recientes", Modifier.size(NexusTokens.iconSmall)) }
                }
                content()
            }
        }
    }
}
