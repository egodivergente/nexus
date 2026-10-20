package com.vidadomu.nexus.ui.create

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.rememberNexusMotionAllowed
import com.vidadomu.nexus.ui.theme.tabularMoney

/** Keep the Studio strip compact normally; enlarged text exposes all actions in wrapping rows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioToolRow(content: @Composable () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8),
        verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {content()}
}

@Composable
internal fun StudioPromptField(value: String, onValueChange: (String) -> Unit,
    placeholder: String, modifier: Modifier = Modifier, focusRequester: FocusRequester? = null) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val tone by animateColorAsState(
        if(focused) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        tween(NexusTokens.contentDuration), label = "Prompt focus")
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    Column(modifier.fillMaxWidth().clip(NexusTokens.prompt).background(tone)
        .animateContentSize(tween(NexusTokens.contentDuration))) {
        BasicTextField(value, onValueChange, modifier = Modifier.fillMaxWidth()
            .then(if(focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = "Prompt de creación" }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            textStyle = textStyle, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            // Crece con el texto: vacío ocupa 2 líneas, no un bloque fijo de 4.
            minLines = 2, maxLines = if(expanded) 18 else 8,
            decorationBox = { field ->
                Box {
                    if(value.isEmpty()) Text(placeholder, style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    field()
                }
            })
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); expanded = !expanded
            }) {
                Icon(if(expanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                    if(expanded) "Reducir prompt" else "Expandir prompt", Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            // Deliberately labelled approximate: model tokenizers differ; no network work while typing.
            Text("≈ ${(value.length + 3) / 4} tokens", style = MaterialTheme.typography.labelSmall.tabularMoney(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = "Recuento aproximado de tokens" })
        }
    }
}

@Composable
internal fun StudioGenerateButton(cost: String, enabled: Boolean, busy: Boolean,
    busyLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .985f else 1f, tween(NexusTokens.pressDuration), label = "Generate press")
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val motionAllowed = rememberNexusMotionAllowed(requested = busy)
    Box(modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }) {
        Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
            enabled = enabled && !busy, interactionSource = interactions,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = NexusTokens.action,
            colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary,
                disabledContainerColor = if(busy) scheme.primary else scheme.surfaceContainerHigh,
                disabledContentColor = if(busy) scheme.onPrimary else scheme.onSurfaceVariant)) {
            AnimatedContent(targetState = busy, label = "Generation state") { working ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // The indeterminate spinner loops forever; with reduced motion or hidden, a static glyph keeps the busy label honest.
                    if(working && motionAllowed) CircularProgressIndicator(Modifier.size(16.dp), color = scheme.onPrimary, strokeWidth = 2.dp)
                    else if(working) Icon(Icons.Filled.Timelapse, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
                    Text(if(working) busyLabel else "Generar · $cost", style = MaterialTheme.typography.titleSmall.tabularMoney(),
                        modifier=Modifier.weight(1f,fill=false),maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // Created only while submitting with motion allowed. State is read during draw, avoiding per-frame recomposition.
        if(busy && motionAllowed) {
            val transition = rememberInfiniteTransition(label = "Generation glow")
            val glow = transition.animateFloat(.02f, .12f,
                infiniteRepeatable(tween(950), RepeatMode.Reverse), label = "Generation pulse")
            Box(Modifier.matchParentSize().clip(NexusTokens.action)
                .drawBehind { drawRect(Color.White.copy(alpha = glow.value)) })
        }
    }
}
