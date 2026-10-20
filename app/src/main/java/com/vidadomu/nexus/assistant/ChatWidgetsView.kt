package com.vidadomu.nexus.assistant

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.agent.core.chat.WidgetOption
import com.vidadomu.nexus.agent.core.chat.WidgetSpec
import com.vidadomu.nexus.ui.theme.NexusTokens

/**
 * Interacción de los paneles `nexus-ui`. [onReply] devuelve si el mensaje se envió (false: el asistente está
 * ocupado y el panel conserva su estado anterior); null = solo lectura. [answered] es lo que el usuario contestó
 * después de este mensaje: bloquea los paneles y marca lo que eligió.
 */
internal class WidgetCtx(val answered: String?, val onReply: ((String) -> Boolean)?) {
    val locked: Boolean get() = answered != null || onReply == null
    fun chose(label: String): Boolean = answered?.contains(label, ignoreCase = true) == true
}

@Composable
internal fun MdWidget(spec: WidgetSpec, ctx: WidgetCtx) {
    when (spec) {
        is WidgetSpec.Poll -> PollPanel(spec, ctx)
        is WidgetSpec.Compare -> ComparePanel(spec, ctx)
        is WidgetSpec.Steps -> StepsPanel(spec)
    }
}

@Composable
private fun PanelSurface(content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surface, shape = NexusTokens.card, border = BorderStroke(1.dp, cs.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(NexusTokens.space12), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8), content = content)
    }
}

/** Los índices marcados se guardan como texto «0,2» para sobrevivir a rotaciones y a la lista recortada del chat. */
private fun indicesOf(key: String): Set<Int> = key.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

@Composable
private fun CheckMark(selected: Boolean, round: Boolean) {
    val cs = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (selected) cs.primary else Color.Transparent, tween(NexusTokens.contentDuration), label = "Marca")
    val edge by animateColorAsState(if (selected) cs.primary else cs.outline, tween(NexusTokens.contentDuration), label = "Borde marca")
    val shape = if (round) CircleShape else RoundedCornerShape(6.dp)
    Box(Modifier.size(22.dp).clip(shape).background(fill).border(1.5.dp, edge, shape), contentAlignment = Alignment.Center) {
        if (selected) Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = cs.onPrimary)
    }
}

@Composable
private fun ChoiceRow(option: WidgetOption, selected: Boolean, dimmed: Boolean, enabled: Boolean, round: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.primary.copy(alpha = 0.12f) else Color.Transparent, tween(NexusTokens.contentDuration), label = "Fondo opción")
    val edge by animateColorAsState(if (selected) cs.primary else cs.outlineVariant, tween(NexusTokens.contentDuration), label = "Borde opción")
    Surface(onClick = onClick, enabled = enabled, color = bg, shape = NexusTokens.field, border = BorderStroke(1.dp, edge),
        modifier = Modifier.fillMaxWidth().heightIn(min = NexusTokens.minimumTouch).alpha(if (dimmed) 0.5f else 1f)
            .semantics { stateDescription = if (selected) "Elegida" else "No elegida" }) {
        Row(Modifier.padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space8), verticalAlignment = Alignment.CenterVertically) {
            CheckMark(selected, round)
            Spacer(Modifier.width(NexusTokens.space12))
            Column(Modifier.weight(1f)) {
                Text(option.label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = cs.onSurface)
                option.hint?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun PollPanel(p: WidgetSpec.Poll, ctx: WidgetCtx) {
    var pickedKey by rememberSaveable(p.id) { mutableStateOf("") }
    val picked = indicesOf(pickedKey)
    PanelSurface {
        Text(p.question, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        p.options.forEachIndexed { i, option ->
            val selected = if (ctx.answered != null) ctx.chose(option.label) else i in picked
            ChoiceRow(option, selected = selected, dimmed = ctx.answered != null && !selected, enabled = !ctx.locked, round = !p.multi) {
                if (p.multi) {
                    pickedKey = (if (i in picked) picked - i else picked + i).sorted().joinToString(",")
                } else {
                    pickedKey = i.toString()
                    if (ctx.onReply?.invoke(option.label) != true) pickedKey = ""
                }
            }
        }
        if (p.multi && !ctx.locked) {
            Button(onClick = {
                val labels = p.options.filterIndexed { i, _ -> i in picked }.joinToString(", ") { it.label }
                ctx.onReply?.invoke(labels)
            }, enabled = picked.isNotEmpty(), modifier = Modifier.align(Alignment.End)) {
                Text(if (picked.isEmpty()) "Enviar" else "Enviar (${picked.size})")
            }
        }
    }
}

@Composable
private fun ComparePanel(c: WidgetSpec.Compare, ctx: WidgetCtx) {
    PanelSurface {
        c.title?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface) }
        if (c.options.size == 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), modifier = Modifier.height(IntrinsicSize.Max)) {
                c.options.forEach { CompareCard(it, ctx, Modifier.weight(1f).fillMaxHeight()) }
            }
        } else c.options.forEach { CompareCard(it, ctx, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun CompareCard(option: WidgetOption, ctx: WidgetCtx, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val chosen = ctx.chose(option.label)
    val edge by animateColorAsState(if (chosen) cs.primary else cs.outlineVariant, tween(NexusTokens.contentDuration), label = "Borde tarjeta")
    Surface(color = if (chosen) cs.primary.copy(alpha = 0.10f) else Color.Transparent, shape = NexusTokens.field, border = BorderStroke(1.dp, edge),
        modifier = modifier.alpha(if (ctx.answered != null && !chosen) 0.5f else 1f)) {
        Column(Modifier.padding(NexusTokens.space12), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
            Text(option.label, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
            option.hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
            Spacer(Modifier.height(NexusTokens.space4))
            if (chosen) Text("Elegida", style = MaterialTheme.typography.labelMedium, color = cs.primary)
            else OutlinedButton(onClick = { ctx.onReply?.invoke("Elijo: ${option.label}") }, enabled = !ctx.locked,
                contentPadding = PaddingValues(horizontal = NexusTokens.space12, vertical = 0.dp)) { Text("Elegir") }
        }
    }
}

@Composable
private fun StepsPanel(s: WidgetSpec.Steps) {
    val cs = MaterialTheme.colorScheme
    var doneKey by rememberSaveable(s.id) { mutableStateOf("") }
    val done = indicesOf(doneKey)
    val progress by animateFloatAsState(done.size.toFloat() / s.items.size, tween(NexusTokens.contentDuration), label = "Avance")
    PanelSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.title ?: "Pasos", style = MaterialTheme.typography.titleSmall, color = cs.onSurface, modifier = Modifier.weight(1f))
            Text("${done.size}/${s.items.size}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        s.items.forEachIndexed { i, item ->
            val checked = i in done
            Surface(onClick = { doneKey = (if (checked) done - i else done + i).sorted().joinToString(",") }, color = Color.Transparent, shape = NexusTokens.thumbnail,
                modifier = Modifier.fillMaxWidth().heightIn(min = NexusTokens.minimumTouch).semantics { stateDescription = if (checked) "Hecho" else "Pendiente" }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = NexusTokens.space4)) {
                    CheckMark(checked, round = false)
                    Spacer(Modifier.width(NexusTokens.space12))
                    Text(item, style = MaterialTheme.typography.bodyLarge, color = if (checked) cs.onSurfaceVariant else cs.onSurface,
                        textDecoration = if (checked) TextDecoration.LineThrough else null)
                }
            }
        }
    }
}
