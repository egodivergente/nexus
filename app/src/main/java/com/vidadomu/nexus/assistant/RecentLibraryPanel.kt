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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
    additionalOptions: (@Composable () -> Unit)? = null,
) {
    // Un solo «+»: abre el menú con archivos, cámara, Biblioteca y conversación por voz.
    // Vidal 01-11 (vídeo, 02:26): cada opción hace una sola cosa. «Archivo o galería» abre directamente el selector
    // del móvil (antes pasaba por otra hoja con un segundo «Importar») y la Biblioteca aparece una sola vez.
    var menu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        Box(Modifier.size(NexusTokens.minimumTouch)
            .semantics { contentDescription = "Añadir"; stateDescription = if (libraryExpanded) "Recientes abiertos" else "Menú de adjuntar" }
            .combinedClickable(enabled = enabled, role = Role.Button,
                onLongClickLabel = "Mostrar archivos recientes", onLongClick = onShowLibrary, onClick = { menu = true }),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, null, Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Archivo o galería") }, leadingIcon = { Icon(Icons.Default.AttachFile, null) }, onClick = { menu = false; onAttach() })
            onCamera?.let { DropdownMenuItem(text = { Text("Hacer una foto") }, leadingIcon = { Icon(Icons.Default.PhotoCamera, null) }, onClick = { menu = false; it() }) }
            onShowLibrary?.let { DropdownMenuItem(text = { Text("Biblioteca") }, leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) }, onClick = { menu = false; it() }) }
            onLive?.let { DropdownMenuItem(text = { Text("Conversación por voz") }, leadingIcon = { Icon(Icons.Default.GraphicEq, null) }, onClick = { menu = false; it() }) }
            additionalOptions?.let { more -> HorizontalDivider(); more() }
        }
    }
}

/**
 * The composer's mode pill. Nexus works in a single mode, Agente, so what it sets is how hard the agent thinks.
 * It carries the accent while it can be changed and stays visible, greyed, when it cannot.
 */
@Composable
internal fun ReasoningIcon(current: String, enabled: Boolean, description: String = "Modo de razonamiento: $current", onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, enabled = enabled, shape = androidx.compose.foundation.shape.CircleShape,
        color = if (enabled) colors.primaryContainer else colors.surfaceContainerHighest,
        contentColor = if (enabled) colors.onPrimaryContainer else colors.onSurfaceVariant.copy(alpha = .7f),
        modifier = Modifier.semantics {
            contentDescription = description
            stateDescription = if (enabled) current else "$current. No se puede cambiar ahora"
        }) {
        Row(Modifier.padding(start = 11.dp, end = 7.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bolt, null, Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text(current, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp))
        }
    }
}

/** The composer's model selector: the short name of who answers, flat, with the same arrow as the mode pill. */
@Composable
internal fun ModelMenuItem(current: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, enabled = enabled, shape = androidx.compose.foundation.shape.CircleShape,
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = .6f),
        modifier = Modifier.semantics {
            contentDescription = "Modelo: $current"
            stateDescription = if (enabled) current else "$current. No se puede cambiar ahora"
        }) {
        Row(Modifier.padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(current, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp))
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
        // A matte card with the composer's margins and tone, not a full-width dark slab over the chat.
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = NexusTokens.space4)) {
            Column(Modifier.fillMaxWidth().recentLibraryGesture(opening = false, onChange = onClose)
                .padding(bottom = NexusTokens.space8)) {
                Row(Modifier.fillMaxWidth().padding(start = NexusTokens.space16), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recientes de la Biblioteca", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Ocultar archivos recientes", Modifier.size(NexusTokens.iconSmall)) }
                }
                content()
            }
        }
    }
}
