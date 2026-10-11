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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.theme.NexusTokens

/** Blur is confined to a static decorative layer. Foreground controls are never blurred.
 * This is a frosted treatment, not a costly capture of the scrolling screen beneath it.
 * Compose uses the native render effect on Android 12+ and a tonal fallback below it. */
@Composable
fun StudioChrome(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.clip(RectangleShape).background(scheme.background.copy(alpha = .90f))) {
        Box(Modifier.matchParentSize()
            .blur(18.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .background(Brush.horizontalGradient(listOf(
                scheme.primary.copy(alpha = .065f), scheme.surface.copy(alpha = .24f),
                scheme.secondary.copy(alpha = .035f)))))
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
) {
    TextField(value, onValueChange, modifier, enabled = enabled, readOnly = readOnly,
        singleLine = singleLine, label = label, placeholder = placeholder,
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
                .semantics { contentDescription = description }) {
            Text(label, Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
