package com.vidadomu.nexus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.stylusHover

/** Borde especular 3D cálido/frío compartido: luz blanca suave arriba y contorno sutil abajo. */
@Composable
fun studioSpecularBorder(width: Dp = 1.dp, accentTint: Color? = null): BorderStroke {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val topRim = (accentTint ?: Color.White).copy(alpha = if (isDark) 0.20f else 0.88f)
    val bottomEdge = (accentTint ?: MaterialTheme.colorScheme.outlineVariant).copy(alpha = if (isDark) 0.42f else 0.65f)
    return BorderStroke(width, Brush.verticalGradient(listOf(topRim, bottomEdge)))
}

/** Tarjeta táctil de estudio 10/10: elevación suave, reflejo especular superior y borde biselado. */
@Composable
fun StudioTactileCard(
    modifier: Modifier = Modifier,
    shape: Shape = NexusTokens.card,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    shadowElevation: Dp? = null,
    border: BorderStroke? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val elevation = shadowElevation ?: if (isDark) 3.dp else 6.dp
    val sheenTop = Color.White.copy(alpha = if (isDark) 0.06f else 0.52f)
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        shadowElevation = elevation,
        border = border ?: studioSpecularBorder(),
    ) {
        Box(
            Modifier.background(Brush.verticalGradient(0f to sheenTop, 0.55f to Color.Transparent)),
            content = content,
        )
    }
}

/** Botón circular táctil elevado (hit target de 48 dp con medalla visual 3D de 40 dp). */
@Composable
fun StudioCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: @Composable () -> Unit,
) {
    val haptic = com.vidadomu.nexus.ui.theme.rememberNexusHaptics()
    val motionAllowed = com.vidadomu.nexus.ui.theme.rememberNexusMotionAllowed()
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        if (motionAllowed) spring(dampingRatio = 0.78f, stiffness = 560f) else snap(),
        label = "CircleBtnPress",
    )
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Box(
        modifier
            .size(NexusTokens.minimumTouch)
            .stylusHover(enabled)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = interactions,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.SELECTION)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale },
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = if (isDark) 3.dp else 5.dp,
            border = studioSpecularBorder(),
        ) {
            Box(
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (isDark) 0.08f else 0.55f), Color.Transparent)
                    )
                ),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        }
    }
}

/** Selector segmentado en píldora 3D (sustituye TabRow plano para vistas de estudio). */
@Composable
fun StudioSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = com.vidadomu.nexus.ui.theme.rememberNexusHaptics()
    val motionAllowed = com.vidadomu.nexus.ui.theme.rememberNexusMotionAllowed()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = NexusTokens.pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val bg by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    if (motionAllowed) spring(dampingRatio = 0.82f, stiffness = 500f) else snap(),
                    label = "SegTabBg",
                )
                Surface(
                    onClick = {
                        if (!selected) {
                            haptic.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.SELECTION)
                            onSelect(index)
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                    shape = NexusTokens.pill,
                    color = bg,
                    shadowElevation = if (selected) (if (isDark) 2.dp else 4.dp) else 0.dp,
                    border = if (selected) studioSpecularBorder() else null,
                ) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Blur is confined to a static decorative layer. Foreground controls are never blurred.
 * This is a frosted treatment, not a costly capture of the scrolling screen beneath it.
 * Compose uses the native render effect on Android 12+ and a tonal fallback below it. */
@Composable
fun StudioChrome(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val chrome = com.vidadomu.nexus.ui.theme.LocalChromeColor.current
    val isDark = chrome.luminance() < 0.5f
    val topGlow = Color.White.copy(alpha = if (isDark) 0.06f else 0.36f)
    val rim = Color.White.copy(alpha = if (isDark) 0.14f else 0.72f)
    Box(
        modifier
            .shadow(elevation = if (isDark) 4.dp else 8.dp, shape = RectangleShape, clip = false)
            .clip(RectangleShape)
            .background(chrome.copy(alpha = 0.95f))
            .background(Brush.verticalGradient(listOf(topGlow, Color.Transparent)))
    ) {
        content()
        HorizontalDivider(
            modifier = Modifier.align(Alignment.TopCenter),
            thickness = 1.dp,
            color = rim,
        )
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter),
            thickness = NexusTokens.divider,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

/**
 * Bottom action area that floats: no strip or edge behind the button. The screen's content scrolls underneath
 * (give it this dock's height as bottom content padding) and fades out softly just above the button.
 */
@Composable
fun FloatingActionDock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Surface(
        modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = NexusTokens.sheet,
        border = studioSpecularBorder(),
        shadowElevation = if (isDark) 6.dp else 10.dp,
    ) {
        Column(Modifier.padding(top = NexusTokens.space8), content = content)
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
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    TextField(value, onValueChange, modifier, enabled = enabled, readOnly = readOnly,
        singleLine = singleLine, label = label, placeholder = placeholder, leadingIcon = leadingIcon,
        minLines = minLines, maxLines = maxLines, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
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
    val haptic = com.vidadomu.nexus.ui.theme.rememberNexusHaptics()
    val motionAllowed = com.vidadomu.nexus.ui.theme.rememberNexusMotionAllowed()
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) .97f else 1f,
        if (motionAllowed) spring(dampingRatio = 0.82f, stiffness = 520f) else snap(),
        label = "Chip press"
    )
    val color by animateColorAsState(
        if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        if (motionAllowed) spring(dampingRatio = 0.85f, stiffness = 480f) else snap(),
        label = "Chip tone"
    )
    Box(Modifier.heightIn(min = NexusTokens.minimumTouch).stylusHover(enabled), contentAlignment = Alignment.Center) {
        Surface(onClick = { haptic.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.SELECTION); onClick() },
            enabled = enabled, interactionSource = interactions, color = color,
            contentColor = if(enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = NexusTokens.filter, modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .heightIn(min=36.dp)
                .semantics { contentDescription = description; if(description!=label) stateDescription=label }) {
            // Minimum height only: large fonts may grow without clipping the label.
            Box(contentAlignment = Alignment.Center) {
                Text(label, Modifier.padding(horizontal = NexusTokens.space12, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A 20 dp visual inside a 48 dp hit target, anchored inward from the media corner. */
@Composable
fun StudioRemoveMediaButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    description: String = "Quitar referencia") {
    val haptic = com.vidadomu.nexus.ui.theme.rememberNexusHaptics()
    Box(modifier.size(NexusTokens.minimumTouch)
        .semantics { contentDescription = description }
        .clickable(role = Role.Button) {
            haptic.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.SELECTION); onClick()
        }) {
        Box(Modifier.align(Alignment.TopEnd).padding(NexusTokens.space4).size(20.dp)
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
    val haptic=com.vidadomu.nexus.ui.theme.rememberNexusHaptics()
    FilterChip(selected=selected, onClick={haptic.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.SELECTION,changed=!selected);onClick()},
        label=label, modifier=modifier, leadingIcon=leadingIcon, shape=shape,
        // Filtros bien visibles: seleccionado = relleno de acento sólido; sin seleccionar = tarjeta con borde propio.
        border=FilterChipDefaults.filterChipBorder(enabled=true, selected=selected,
            borderColor=MaterialTheme.colorScheme.outline, selectedBorderColor=Color.Transparent),
        colors=FilterChipDefaults.filterChipColors(containerColor=MaterialTheme.colorScheme.surface,
            labelColor=MaterialTheme.colorScheme.onSurface,
            selectedContainerColor=MaterialTheme.colorScheme.primary,
            selectedLabelColor=MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor=MaterialTheme.colorScheme.onPrimary))
}
