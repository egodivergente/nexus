package com.vidadomu.nexus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.theme.NexusTokens

/** Blur is confined to a static decorative layer. Foreground controls are never blurred.
 * This is a frosted treatment, not a costly capture of the scrolling screen beneath it.
 * Compose uses the native render effect on Android 12+ and a tonal fallback below it. */
@Composable
fun StudioChrome(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Barras con tono propio (jerarquía: barras > fondo > tarjetas), sin mezclarse con el contenido.
    Box(modifier.clip(RectangleShape).background(com.vidadomu.nexus.ui.theme.LocalChromeColor.current)) {
        content()
    }
}

/** Tonal field shared by parameters and saved presets, with no outline or underline. */
@Composable
fun StudioTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, readOnly: Boolean = false, singleLine: Boolean = false,
    label: (@Composable () -> Unit)? = null, placeholder: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null, supportingText: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    shape: Shape = NexusTokens.prompt,
    leadingIcon: (@Composable () -> Unit)? = null,
    minLines: Int = 1, maxLines: Int = Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    TextField(value, onValueChange, modifier, enabled = enabled, readOnly = readOnly,
        singleLine = singleLine, label = label, placeholder = placeholder, leadingIcon = leadingIcon,
        minLines = minLines, maxLines = maxLines, keyboardOptions = keyboardOptions, visualTransformation = visualTransformation,
        trailingIcon = trailingIcon, supportingText = supportingText, textStyle = textStyle,
        shape = shape, colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent))
}

@Composable
fun StudioToolChip(label: String, description: String = label, enabled: Boolean = true,
    selected: Boolean = false, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, tween(NexusTokens.pressDuration), label = "Chip press")
    val color by animateColorAsState(
        if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        tween(NexusTokens.pressDuration), label = "Chip tone")
    Box(Modifier.heightIn(min = NexusTokens.minimumTouch), contentAlignment = Alignment.Center) {
        Surface(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
            enabled = enabled, interactionSource = interactions, color = color,
            contentColor = if(enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = NexusTokens.filter, modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .height(36.dp)
                .semantics { contentDescription = description; if(description!=label) stateDescription=label }) {
            // 36 dp visibles dentro de 48 dp táctiles, con el texto centrado: todas las pastillas igual de altas.
            Box(contentAlignment = Alignment.Center) {
                Text(label, Modifier.padding(horizontal = NexusTokens.space12),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A 20 dp visual inside a 48 dp hit target, anchored inward from the media corner. */
@Composable
fun StudioRemoveMediaButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    description: String = "Quitar referencia") {
    val haptic = LocalHapticFeedback.current
    Box(modifier.size(NexusTokens.minimumTouch)
        .semantics { contentDescription = description }
        .clickable(role = Role.Button) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
        }) {
        Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
            .clip(CircleShape).background(Color.Black.copy(alpha = .50f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = Color.White)
        }
    }
}

@Composable
fun StudioCredentialField(value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier, label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null, singleLine: Boolean = true,
    shape: Shape = NexusTokens.field) {
    var visible by remember { mutableStateOf(false) }
    StudioTextField(value, onValueChange, modifier.fillMaxWidth(), label=label,
        placeholder=placeholder, singleLine=singleLine, shape=shape,
        visualTransformation=if(visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password, autoCorrectEnabled=false),
        trailingIcon={IconButton(onClick={visible=!visible}) {
            Icon(if(visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                if(visible) "Ocultar credencial" else "Mostrar credencial")
        }})
}

@Composable
fun StudioFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, leadingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = NexusTokens.filter) {
    val haptic=LocalHapticFeedback.current
    FilterChip(selected=selected, onClick={haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);onClick()},
        label=label, modifier=modifier, leadingIcon=leadingIcon, shape=shape,
        // Filtros bien visibles: seleccionado = relleno de acento sólido; sin seleccionar = tarjeta con borde propio.
        border=FilterChipDefaults.filterChipBorder(enabled=true, selected=selected,
            borderColor=MaterialTheme.colorScheme.outline.copy(alpha=.55f), selectedBorderColor=Color.Transparent),
        colors=FilterChipDefaults.filterChipColors(containerColor=MaterialTheme.colorScheme.surface,
            labelColor=MaterialTheme.colorScheme.onSurface,
            selectedContainerColor=MaterialTheme.colorScheme.primary,
            selectedLabelColor=MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor=MaterialTheme.colorScheme.onPrimary))
}
