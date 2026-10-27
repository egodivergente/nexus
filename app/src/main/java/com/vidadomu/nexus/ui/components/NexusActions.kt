package com.vidadomu.nexus.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.theme.NexusTokens

/**
 * Secondary action that must read as a button, not as loose text: tonal fill, optional icon and a 48 dp target.
 * Use it for «Galería», «Biblioteca», «Exportar», «Ampliar imagen»… Primary actions keep their own filled button.
 */
@Composable
fun NexusTonalAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, enabled: Boolean = true, description: String = label) {
    FilledTonalButton(onClick = onClick, enabled = enabled, shape = NexusTokens.filter,
        contentPadding = PaddingValues(horizontal = NexusTokens.space12, vertical = 6.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            // Accent tint instead of a grey tone: stays visible on cards, fields and the composer dock alike.
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .14f),
            contentColor = MaterialTheme.colorScheme.primary),
        modifier = modifier.heightIn(min = 36.dp).semantics { contentDescription = description }) {
        icon?.let { Icon(it, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Selector that looks like what it is: outlined field with a caption, the current value and a drop-down arrow.
 * [caption] names what is being chosen («Ámbito», «Sección») so the value is never mistaken for a title.
 */
@Composable
fun NexusDropdownSelector(caption: String, value: String, choices: List<String>,
    modifier: Modifier = Modifier, icon: ImageVector? = null, onValue: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, shape = NexusTokens.filter,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = PaddingValues(start = NexusTokens.space12, end = NexusTokens.space8, top = 6.dp, bottom = 6.dp),
            modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = caption; stateDescription = value }) {
            icon?.let { Icon(it, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(6.dp)) }
            Text("$caption: ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(open, { open = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(choice) }, onClick = { open = false; onValue(choice) },
                    trailingIcon = if (choice == value) ({ Icon(androidx.compose.material.icons.Icons.Default.Check, "Seleccionado", Modifier.size(18.dp)) }) else null)
            }
        }
    }
}

/** Small status label (e.g. «Personalizado», «Heredado») that reads as metadata, not as a second title. */
@Composable
fun NexusStatusTag(label: String, emphasized: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = NexusTokens.filter,
        color = if (emphasized) colors.primary.copy(alpha = .16f) else colors.surfaceContainerHigh,
        contentColor = if (emphasized) colors.primary else colors.onSurfaceVariant) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** − value + with immediate saving: the right control for small integer limits (0–50). */
@Composable
fun NexusStepper(value: Int, onValue: (Int) -> Unit, minimum: Int, maximum: Int?, label: String,
    zeroLabel: String? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onValue(value - 1) }, enabled = value > minimum) {
            Icon(androidx.compose.material.icons.Icons.Default.Remove, "Reducir $label")
        }
        Text(if (value == 0 && zeroLabel != null) zeroLabel else value.toString(),
            style = MaterialTheme.typography.titleMedium, maxLines = 1,
            modifier = Modifier.widthIn(min = 56.dp).padding(horizontal = NexusTokens.space8)
                .semantics { contentDescription = "$label: " + (if (value == 0 && zeroLabel != null) zeroLabel else value.toString()) },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        FilledTonalIconButton(onClick = { onValue(value + 1) }, enabled = maximum == null || value < maximum) {
            Icon(androidx.compose.material.icons.Icons.Default.Add, "Aumentar $label")
        }
    }
}

/** Icon-only button with a visible hint on long press, so its meaning never depends on guessing the glyph. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusHintedIconButton(hint: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, content: @Composable () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(hint) } }, state = rememberTooltipState()) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier, content = content)
    }
}
