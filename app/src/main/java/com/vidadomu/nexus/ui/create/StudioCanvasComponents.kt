package com.vidadomu.nexus.ui.create

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Undo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import com.vidadomu.nexus.ui.theme.rememberNexusHaptics
import com.vidadomu.nexus.ui.theme.NexusHapticEvent
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

/** The same two workspaces stay visible in both Canvas and Flow. */
@Composable
internal fun CanvasWorkspaceSwitch(flow: Boolean, onModels: () -> Unit, onFlow: () -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(selected = !flow, onClick = onModels, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
            Text("Modelos")
        }
        SegmentedButton(selected = flow, onClick = onFlow, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
            Text("Google Flow")
        }
    }
}

@Composable
internal fun GenerationQuantityControl(count: Int, enabled: Boolean, onCount: (Int) -> Unit, onModels: () -> Unit, allowed: (Int) -> Boolean = {true}, choices: List<Int> = BATCH_COUNTS) {
    var menu by remember {mutableStateOf(false)}
    val feedback=rememberNexusHaptics()
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Box {
            TextButton(onClick={menu=true},enabled=enabled) {Text("×$count · Resultados ▾")}
            DropdownMenu(menu,{menu=false}) {choices.forEach {value ->
                DropdownMenuItem(text={Text("×$value")},enabled=allowed(value),onClick={menu=false;feedback.perform(NexusHapticEvent.SELECTION,changed=value!=count);onCount(value)})
            }}
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick=onModels,enabled=enabled) {Text("Varios modelos")}
    }
}

/** Keep the Studio strip compact normally; enlarged text exposes all actions in wrapping rows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioToolRow(content: @Composable () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8),
        verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {content()}
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,ExperimentalLayoutApi::class)
@Composable
internal fun StudioPromptField(value: String, onValueChange: (String) -> Unit,
    placeholder: String, modifier: Modifier = Modifier, focusRequester: FocusRequester? = null,
    enabled: Boolean = true, maximum: Int? = null, capability: com.vidadomu.nexus.agent.core.registry.PromptCapability? = null, ruleScope: com.vidadomu.nexus.agent.core.config.RuleScope = com.vidadomu.nexus.agent.core.config.RuleScope(), onEditorFocusChanged:(Boolean)->Unit = {}, attachments: @Composable ColumnScope.() -> Unit = {}) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val bringPromptIntoView=remember {BringIntoViewRequester()}
    val imeVisible=WindowInsets.isImeVisible
    LaunchedEffect(focused,imeVisible) {
        if(focused && imeVisible) {
            withFrameNanos { }
            bringPromptIntoView.bringIntoView()
        }
    }
    val haptic = LocalHapticFeedback.current
    val feedback = rememberNexusHaptics()
    val clipboard = LocalClipboardManager.current
    var cleared by rememberSaveable { mutableStateOf<String?>(null) }
    val motion=rememberNexusMotionAllowed()
    val tone by animateColorAsState(
        if(focused) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        tween(if(motion) NexusTokens.contentDuration else 0), label = "Prompt focus")
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    Column(modifier.fillMaxWidth().clip(NexusTokens.prompt).background(tone)
        .animateContentSize(tween(if(motion) NexusTokens.contentDuration else 0))) {
        BasicTextField(value, onValueChange, modifier = Modifier.fillMaxWidth()
            .then(if(focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .bringIntoViewRequester(bringPromptIntoView)
            .onFocusChanged { focused = it.isFocused;onEditorFocusChanged(it.isFocused) }
            .semantics { contentDescription = "Prompt de creación" }
            .heightIn(min = if(expanded) 176.dp else 72.dp)
            .padding(start = NexusTokens.space16, end = NexusTokens.space16, top = NexusTokens.space8),
            textStyle = textStyle, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            // Collapsed editor stays around 120–128 dp including its toolbar. Text scrolls; no text is discarded.
            enabled = enabled, minLines = 2, maxLines = if(expanded) 18 else 3,
            decorationBox = { field ->
                Box {
                    if(value.isEmpty()) Text(placeholder, style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    field()
                }
            })
        val rules by com.vidadomu.nexus.settings.RuntimePreferences.get(LocalContext.current).state.collectAsState()
        val warning=rules.value("prompt.softWarning",ruleScope).toLongOrNull() ?: 0
        if(warning>0 && value.codePointCount(0,value.length)>warning) Text("Has superado tu aviso de longitud. El texto se conserva y puedes enviarlo.",Modifier.padding(horizontal=NexusTokens.space16),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.tertiary)
        rules.value("prompt.observation",ruleScope).takeIf {it.isNotBlank()}?.let {observation->Text("Tu observación · $observation",Modifier.padding(horizontal=NexusTokens.space16),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Row(Modifier.fillMaxWidth().padding(start = NexusTokens.space4, end = NexusTokens.space8), verticalAlignment = Alignment.CenterVertically) {
            com.vidadomu.nexus.ui.components.NexusHintedIconButton("Pegar prompt",modifier=Modifier.size(NexusTokens.minimumTouch),enabled=enabled,onClick={
                val pasted=clipboard.getText()?.text
                if(!pasted.isNullOrEmpty() && pasted!=value) {onValueChange(pasted);feedback.perform(NexusHapticEvent.SELECTION)}
            }) {Icon(Icons.Default.ContentPaste,"Pegar prompt",Modifier.size(16.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}
            com.vidadomu.nexus.ui.components.NexusHintedIconButton("Borrar prompt",modifier=Modifier.size(NexusTokens.minimumTouch),enabled=enabled && value.isNotEmpty(),onClick={
                cleared=value;onValueChange("");feedback.perform(NexusHapticEvent.SELECTION)
            }) {Icon(Icons.Default.Close,"Borrar prompt",Modifier.size(16.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(value.isEmpty() && cleared!=null) com.vidadomu.nexus.ui.components.NexusHintedIconButton("Restaurar prompt",modifier=Modifier.size(NexusTokens.minimumTouch),enabled=enabled,onClick={
                onValueChange(cleared.orEmpty());cleared=null;feedback.perform(NexusHapticEvent.SELECTION)
            }) {Icon(Icons.Default.Undo,"Restaurar prompt",Modifier.size(16.dp))}
            Spacer(Modifier.weight(1f))
            val measured=capability?.measured(value)
            Text(if(capability!=null) "${measured ?: "≈ ?"} / ${capability.maximum} ${capability.unitLabel}"
                else value.codePointCount(0,value.length).let {n -> if(maximum!=null) "$n / $maximum caracteres" else "$n ${if(n==1) "carácter" else "caracteres"}"},
                style=MaterialTheme.typography.labelSmall,
                modifier=Modifier.weight(1f,fill=false),
                color=if(capability?.exceeds(value)==true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            com.vidadomu.nexus.ui.components.NexusHintedIconButton(if(expanded) "Reducir prompt" else "Expandir prompt",onClick = {
                feedback.perform(NexusHapticEvent.SELECTION); expanded = !expanded
            },enabled=enabled) {
                Icon(if(expanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                    if(expanded) "Reducir prompt" else "Expandir prompt", Modifier.size(NexusTokens.iconSmall),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        attachments()
    }
}

@Composable
internal fun StudioGenerateButton(cost: String, enabled: Boolean, busy: Boolean,
    busyLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier, actionLabel: String = "Generar",
    /** What is still missing, shown on the disabled button itself so no separate strip competes with it. */
    disabledLabel: String? = null, showCost: Boolean = true) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .985f else 1f, tween(NexusTokens.pressDuration), label = "Generate press")
    val haptic = rememberNexusHaptics()
    val scheme = MaterialTheme.colorScheme
    val motionAllowed = rememberNexusMotionAllowed(requested = busy)
    Box(modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }) {
        Button(onClick = { haptic.perform(NexusHapticEvent.SELECTION); onClick() },
            enabled = enabled && !busy, interactionSource = interactions,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = NexusTokens.action,
            colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary,
                disabledContainerColor = if(busy) scheme.primary else scheme.surfaceContainerHigh,
                disabledContentColor = if(busy) scheme.onPrimary else scheme.onSurfaceVariant)) {
            AnimatedContent(targetState = busy, label = "Generation state") { working ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // The indeterminate spinner loops forever; with reduced motion or hidden, a static glyph keeps the busy label honest.
                    if(working && motionAllowed) CircularProgressIndicator(Modifier.size(16.dp), color = scheme.onPrimary, strokeWidth = 2.dp)
                    else if(working) Icon(Icons.Filled.Timelapse, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
                    Text(if(working) busyLabel else if(!enabled && disabledLabel != null) disabledLabel else if(showCost) "$actionLabel · $cost" else actionLabel, style = MaterialTheme.typography.titleSmall.tabularMoney(),
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
