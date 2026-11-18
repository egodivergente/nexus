package com.vidadomu.nexus.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidadomu.nexus.agent.core.orders.Order
import com.vidadomu.nexus.agent.core.orders.OrderStatus
import com.vidadomu.nexus.agent.core.tools.AgentStep
import com.vidadomu.nexus.agent.core.tools.AgentStepKind
import com.vidadomu.nexus.agent.core.tools.AgentStepStatus
import com.vidadomu.nexus.agent.core.tools.AgentSteps
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.rememberNexusMotionAllowed

/** The words of the activity timeline. Pure, so the same step always reads the same and is tested without a device. */
internal object AgentActivityText {
    /** «0,2 s», «8 s», «1 min 4 s»: tenths below one second, whole seconds after. */
    fun duration(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        return when {
            safe < 1_000 -> "0,${safe / 100} s"
            safe < 60_000 -> "${safe / 1_000} s"
            else -> "${safe / 60_000} min ${(safe % 60_000) / 1_000} s"
        }
    }

    fun state(status: AgentStepStatus): String = when (status) {
        AgentStepStatus.RUNNING -> "En curso"
        AgentStepStatus.DONE -> "Completado"
        AgentStepStatus.ISSUE -> "Con incidencia"
        AgentStepStatus.CANCELLED -> "Cancelado"
    }

    fun count(steps: Int): String = if (steps == 1) "1 paso" else "$steps pasos"

    /** Folded header of a finished turn: «5 pasos · 34 s». Old messages have no times and show the count alone. */
    fun summary(steps: List<AgentStep>): String {
        val total = AgentSteps.totalMs(steps)
        return count(steps.size) + if (total > 0L) " · " + duration(total) else ""
    }

    fun issues(steps: List<AgentStep>): String? = when (val n = AgentSteps.issues(steps)) {
        0 -> null
        1 -> "1 incidencia"
        else -> "$n incidencias"
    }

    /** What the detail block calls the outcome of a step. */
    fun outcomeTitle(status: AgentStepStatus): String = when (status) {
        AgentStepStatus.ISSUE -> "Incidencia"
        AgentStepStatus.CANCELLED -> "Cancelado"
        else -> "Resultado"
    }

    /** Everything the detail shows, as plain text for «Copiar». */
    fun copyText(step: AgentStep): String = listOfNotNull(
        step.command?.takeIf { it.isNotBlank() },
        step.input?.takeIf { it.isNotBlank() }?.let { "Entrada\n$it" },
        step.result?.takeIf { it.isNotBlank() }?.let { outcomeTitle(step.status) + "\n" + it },
    ).joinToString("\n\n")

    /** Messages saved before the timeline existed only kept the tool names: one finished row each, without a time. */
    fun legacy(messageId: String, toolCalls: List<String>): List<AgentStep> = toolCalls.mapIndexed { index, tool ->
        AgentStep("$messageId:tool:$index", AgentStepKind.TOOL, com.vidadomu.nexus.agent.core.agent.toolActivity(tool),
            AgentStepStatus.DONE, tool = tool, command = tool)
    }
}

/** The line under the conversation title: what the agent is really doing now, and whether the dot is lit. */
internal data class AgentHeaderStatus(val text: String, val active: Boolean)

internal fun agentHeaderStatus(
    thinking: Boolean,
    steps: List<AgentStep>,
    queuedSend: Boolean,
    workingElsewhere: Boolean,
    orders: Collection<Order>,
    attaching: Boolean,
    carrying: Boolean,
    offline: Boolean,
    project: String,
    model: String,
): AgentHeaderStatus {
    val running = orders.count { it.status == OrderStatus.SUBMITTING || it.status == OrderStatus.PROCESSING }
    return when {
        thinking -> AgentHeaderStatus((steps.lastOrNull { it.running }?.label ?: "Trabajando") +
            (if (queuedSend) " · 1 mensaje en espera" else ""), true)
        attaching -> AgentHeaderStatus("Preparando adjuntos", true)
        carrying -> AgentHeaderStatus("Resumiendo el contexto", true)
        workingElsewhere -> AgentHeaderStatus("Trabajando en otra conversación", true)
        running == 1 -> AgentHeaderStatus("1 encargo en curso", true)
        running > 1 -> AgentHeaderStatus("$running encargos en curso", true)
        offline -> AgentHeaderStatus(listOf(project, "Planificador local").filter { it.isNotBlank() }.joinToString(" · "), false)
        else -> AgentHeaderStatus(listOf(project, model).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Listo" }, false)
    }
}

/**
 * Compact header: the conversation's title leads, the agent's real state sits under it with a small dot that only
 * takes the accent while something is running. The menu and the settings stay one tap away.
 */
@Composable
internal fun AgentChatHeader(
    title: String,
    status: AgentHeaderStatus,
    onMenu: () -> Unit,
    onTitle: () -> Unit,
    onSettings: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    moreMenu: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = NexusTokens.space4), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Conversaciones", tint = colors.onSurface) }
        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Cambiar de proyecto", role = Role.Button, onClick = onTitle)
            .padding(horizontal = NexusTokens.space8, vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)
                .semantics(mergeDescendants = true) { stateDescription = if (status.active) "Activo" else "En reposo" }) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (status.active) colors.primary else colors.outline.copy(alpha = .7f)))
                Spacer(Modifier.width(7.dp))
                Text(status.text, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Ajustes", Modifier.size(22.dp), tint = colors.onSurfaceVariant) }
        Box {
            IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "Opciones de la conversación", tint = colors.onSurfaceVariant) }
            moreMenu()
        }
    }
}

/** The turn in progress: every step the orchestrator reports, on one quiet vertical line. */
@Composable
internal fun AgentActivityLive(steps: List<AgentStep>, startedAtElapsedMs: Long, modifier: Modifier = Modifier) {
    val seconds by produceState(thinkingElapsedSeconds(android.os.SystemClock.elapsedRealtime(), startedAtElapsedMs), startedAtElapsedMs) {
        while (true) {
            value = thinkingElapsedSeconds(android.os.SystemClock.elapsedRealtime(), startedAtElapsedMs)
            kotlinx.coroutines.delay(1_000L)
        }
    }
    // Before the first report there is still something true to show: the turn started and nothing has come back yet.
    val preparing = remember(startedAtElapsedMs) {
        val waited = if (startedAtElapsedMs > 0L) (android.os.SystemClock.elapsedRealtime() - startedAtElapsedMs).coerceAtLeast(0L) else 0L
        AgentStep("preparing", AgentStepKind.THINK, "Preparando la respuesta", AgentStepStatus.RUNNING, startedAt = System.currentTimeMillis() - waited)
    }
    Column(modifier.fillMaxWidth().semantics { contentDescription = "Actividad del agente" }) {
        AgentTimeline {
            if (steps.isEmpty()) AgentStepRow(preparing)
            else steps.forEach { step -> key(step.id) { AgentStepRow(step) } }
        }
        if (seconds >= 30) Text("Tarda más de lo normal. Puedes detenerlo y volver a intentarlo.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 47.dp, top = 2.dp))
    }
}

/**
 * The activity kept with a finished message: folded to one line («5 pasos · 34 s», plus the incidents if any) and
 * opened on demand, so a reopened conversation still shows how the answer was reached.
 */
@Composable
internal fun AgentActivitySaved(id: String, steps: List<AgentStep>, modifier: Modifier = Modifier, initiallyOpen: Boolean = false) {
    if (steps.isEmpty()) return
    var open by rememberSaveable(id) { mutableStateOf(initiallyOpen) }
    val colors = MaterialTheme.colorScheme
    val issues = AgentActivityText.issues(steps)
    val cancelled = steps.any { it.status == AgentStepStatus.CANCELLED }
    val summary = AgentActivityText.summary(steps)
    val motion = rememberNexusMotionAllowed()
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = if (open) "Ocultar la actividad" else "Ver la actividad", role = Role.Button) { open = !open }
            .semantics(mergeDescendants = true) {
                contentDescription = "Actividad del agente: " + listOfNotNull(summary, issues).joinToString(", ")
                stateDescription = if (open) "Desplegada" else "Plegada"
            }
            // Dense to the eye, but a full touch target: it is the way into how the answer was reached.
            .heightIn(min = NexusTokens.minimumTouch).padding(start = 1.dp, end = NexusTokens.space8),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                when {
                    issues != null -> Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp), tint = colors.error)
                    cancelled -> Icon(Icons.Outlined.Cancel, null, Modifier.size(18.dp), tint = colors.outline)
                    else -> Icon(Icons.Outlined.Terminal, null, Modifier.size(18.dp), tint = colors.outline)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
            issues?.let { Text("  ·  $it", style = MaterialTheme.typography.bodyMedium, color = colors.error, maxLines = 1) }
            Spacer(Modifier.width(NexusTokens.space4))
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp), tint = colors.outline)
        }
        AnimatedVisibility(open,
            enter = if (motion) expandVertically() + fadeIn() else EnterTransition.None,
            exit = if (motion) shrinkVertically() + fadeOut() else ExitTransition.None) {
            AgentTimeline { steps.forEach { step -> key(step.id) { AgentStepRow(step) } } }
        }
    }
}

@Composable
private fun AgentTimeline(content: @Composable ColumnScope.() -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().padding(start = 10.dp, top = 2.dp, bottom = 4.dp)
        .drawBehind { drawLine(line, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.5.dp.toPx()) }
        .padding(start = 13.dp), content = content)
}

/** Icon on the left, what it did in the middle, how long on the right. A row with detail opens it on tap. */
@Composable
private fun AgentStepRow(step: AgentStep) {
    var open by rememberSaveable(step.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val motion = rememberNexusMotionAllowed()
    val state = AgentActivityText.state(step.status)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .then(if (step.hasDetail) Modifier.clickable(onClickLabel = if (open) "Ocultar el detalle" else "Ver el detalle", role = Role.Button) { open = !open }
                else Modifier)
            .semantics(mergeDescendants = true) { stateDescription = if (step.hasDetail) "$state. Detalle " + (if (open) "desplegado" else "plegado") else state }
            // Only the rows that open something need a full touch target; the rest stay as tight as the reference.
            .then(if (step.hasDetail) Modifier.heightIn(min = NexusTokens.minimumTouch) else Modifier)
            .padding(vertical = 7.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                when (step.status) {
                    AgentStepStatus.RUNNING -> CircularProgressIndicator(Modifier.size(15.dp), color = colors.primary, strokeWidth = 2.dp,
                        trackColor = colors.outlineVariant)
                    AgentStepStatus.ISSUE -> Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(19.dp), tint = colors.error)
                    AgentStepStatus.CANCELLED -> Icon(Icons.Outlined.Cancel, null, Modifier.size(18.dp), tint = colors.outline)
                    AgentStepStatus.DONE -> Icon(if (step.kind == AgentStepKind.THINK) Icons.Default.Psychology else Icons.Outlined.Terminal, null,
                        Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(step.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (step.status == AgentStepStatus.CANCELLED) colors.onSurfaceVariant else colors.onSurface)
                step.command?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = colors.outline,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            StepDuration(step, Modifier.padding(start = NexusTokens.space8))
        }
        AnimatedVisibility(open && step.hasDetail,
            enter = if (motion) expandVertically() + fadeIn() else EnterTransition.None,
            exit = if (motion) shrinkVertically() + fadeOut() else ExitTransition.None) {
            StepDetail(step)
        }
    }
}

/** A finished step shows what it took; a running one counts from its real start. */
@Composable
private fun StepDuration(step: AgentStep, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.outline
    if (!step.running) {
        step.durationMs?.let { Text(AgentActivityText.duration(it), modifier, style = style, color = color, maxLines = 1) }
        return
    }
    if (step.startedAt <= 0L) return
    val elapsed by produceState((System.currentTimeMillis() - step.startedAt).coerceAtLeast(0L), step.id, step.startedAt) {
        while (true) {
            value = (System.currentTimeMillis() - step.startedAt).coerceAtLeast(0L)
            kotlinx.coroutines.delay(if (value < 1_000L) 100L else 500L)
        }
    }
    Text(AgentActivityText.duration(elapsed), modifier, style = style, color = color, maxLines = 1)
}

/** Command, input and outcome of one step: monospaced, selectable, wrapped to the width, and one tap to copy it all. */
@Composable
private fun StepDetail(step: AgentStep) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
    Surface(color = colors.surfaceContainerLowest, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 34.dp, top = 2.dp, bottom = NexusTokens.space8)) {
        Column(Modifier.padding(start = NexusTokens.space12, end = NexusTokens.space4, top = NexusTokens.space4, bottom = NexusTokens.space12)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step.tool ?: "Modelo", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(AgentActivityText.copyText(step)))
                    android.widget.Toast.makeText(context, "Detalle copiado", android.widget.Toast.LENGTH_SHORT).show()
                }, contentPadding = PaddingValues(horizontal = NexusTokens.space8), modifier = Modifier.heightIn(min = 36.dp)) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(15.dp)); Spacer(Modifier.width(6.dp))
                    Text("Copiar", style = MaterialTheme.typography.labelMedium)
                }
            }
            @Composable fun block(title: String?, text: String, tint: androidx.compose.ui.graphics.Color) {
                title?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.outline, modifier = Modifier.padding(top = NexusTokens.space8, bottom = 2.dp)) }
                SelectionContainer {
                    // Wrapped: an incident is a sentence to read whole, and a phone has no room to scroll it sideways.
                    Text(text, style = mono, color = tint,
                        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(end = NexusTokens.space8))
                }
            }
            step.command?.takeIf { it.isNotBlank() }?.let { block(null, it, colors.onSurface) }
            step.input?.takeIf { it.isNotBlank() }?.let { block("Entrada", it, colors.onSurfaceVariant) }
            step.result?.takeIf { it.isNotBlank() }?.let {
                block(AgentActivityText.outcomeTitle(step.status), it, if (step.status == AgentStepStatus.ISSUE) colors.error else colors.onSurfaceVariant)
            }
        }
    }
}
