package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.ui.theme.NexusTokens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidadomu.nexus.agent.core.chat.CalloutKind
import com.vidadomu.nexus.agent.core.chat.ChatMarkdown
import com.vidadomu.nexus.agent.core.chat.ListItem
import com.vidadomu.nexus.agent.core.chat.MdBlock
import com.vidadomu.nexus.agent.core.chat.MdSpan
import kotlinx.coroutines.delay

/** Estilos del bocadillo: cuerpo en Manrope, títulos en la fuente de marca, acento del tema. */
private data class MdStyles(
    val body: TextStyle,
    val ink: Color,
    val soft: Color,
    val muted: Color,
    val accent: Color,
    val mark: Color,
    val codeBg: Color,
    val line: Color,
    val heading: TextStyle,
    val label: TextStyle,
    val onLink: (String) -> Unit = {},
)

@Composable
private fun mdStyles(): MdStyles {
    val cs = MaterialTheme.colorScheme
    val ty = MaterialTheme.typography
    return MdStyles(
        body = ty.bodyLarge,
        ink = cs.onSurface,
        soft = cs.onSurface,
        muted = cs.onSurfaceVariant,
        accent = cs.onSurfaceVariant,
        mark = cs.primary.copy(alpha = 0.22f),
        codeBg = cs.background.copy(alpha = 0.72f),
        line = cs.outlineVariant,
        heading = ty.titleMedium,
        label = ty.labelMedium,
    )
}

private val TECHNICAL_ID = Regex("^att_[0-9a-zA-Z]{6,16}$")

/**
 * Internal attachment ids (`att_da34bd2a`) mean nothing to the reader: they are dropped together with the
 * parentheses around them, so «padre (`att_…`)» reads «padre». The agent still has the ids in its own context.
 */
internal fun hideTechnicalIds(spans: List<MdSpan>): List<MdSpan> {
    if (spans.none { it.code && TECHNICAL_ID.matches(it.text.trim()) }) return spans
    val out = spans.toMutableList()
    var i = 0
    while (i < out.size) {
        val sp = out[i]
        if (sp.code && TECHNICAL_ID.matches(sp.text.trim())) {
            val prev = out.getOrNull(i - 1); val next = out.getOrNull(i + 1)
            val wrapped = prev != null && !prev.code && prev.text.trimEnd().endsWith("(") && next != null && !next.code && next.text.startsWith(")")
            if (wrapped) {
                out[i - 1] = prev!!.copy(text = prev.text.trimEnd().removeSuffix("(").trimEnd())
                out[i + 1] = next!!.copy(text = next.text.removePrefix(")"))
            }
            out.removeAt(i)
        } else i++
    }
    return out
}

private val TOOL_NAME = Regex("^[a-z][a-z0-9]*(?:_[a-z0-9]+)+(?:\\(.*\\))?$")
private val STATE_WORDS = mapOf("QUEUED" to "en cola", "PENDING" to "pendiente", "PROCESSING" to "en curso",
    "SUBMITTING" to "enviando", "GENERATING" to "generando", "COMPLETED" to "completado", "FAILED" to "fallido",
    "CANCELLED" to "cancelado", "PAUSED" to "en pausa")

/**
 * Safety net for replies that still leak internals: a tool name in code (`update_plan`) reads as plain words
 * («update plan») and an English state (`QUEUED`) as its Spanish label. Real code the user may copy is untouched.
 */
internal fun readableSpans(spans: List<MdSpan>): List<MdSpan> = hideTechnicalIds(spans).map { sp ->
    val t = sp.text.trim()
    when {
        !sp.code -> sp
        t in STATE_WORDS -> sp.copy(text = sp.text.replace(t, STATE_WORDS.getValue(t)), code = false)
        TOOL_NAME.matches(t) -> sp.copy(text = sp.text.replace(t, t.substringBefore('(').replace('_', ' ')), code = false)
        else -> sp
    }
}

private fun annotated(spans: List<MdSpan>, st: MdStyles, leadAccent: Boolean = false): AnnotatedString = buildAnnotatedString {
    readableSpans(spans).forEachIndexed { idx, sp ->
        val accentLead = leadAccent && idx == 0 && sp.bold
        val style = SpanStyle(
            color = when {
                sp.link != null || accentLead -> st.accent
                sp.code || sp.bold || sp.mark -> st.ink
                else -> Color.Unspecified
            },
            fontWeight = if (sp.bold || sp.mark) FontWeight.Medium else null,
            fontStyle = if (sp.italic) FontStyle.Italic else null,
            fontFamily = if (sp.code) FontFamily.Monospace else null,
            // Inline code a step smaller and on a faint tint, so it does not break the line spacing into «patches».
            fontSize = if (sp.code) st.body.fontSize * 0.88f else androidx.compose.ui.unit.TextUnit.Unspecified,
            background = when {
                sp.code -> st.ink.copy(alpha = 0.045f)
                sp.mark -> st.mark
                else -> Color.Unspecified
            },
            textDecoration = when {
                sp.strike -> TextDecoration.LineThrough
                sp.link != null -> TextDecoration.Underline
                else -> null
            },
        )
        if (sp.link != null) {
            withLink(LinkAnnotation.Clickable(sp.link!!, TextLinkStyles(style)) { st.onLink(sp.link!!) }) { append(sp.text) }
        } else withStyle(style) { append(sp.text) }
    }
}

/**
 * Mensaje del asistente con jerarquía tipográfica; nunca muestra marcas de markdown crudas.
 * [streaming]: el texto sigue llegando (los paneles a medias no se enseñan). [answered]: lo que el usuario contestó
 * después de este mensaje; bloquea los paneles interactivos y marca lo elegido. [onReply] envía una respuesta y
 * devuelve si se envió; null deja los paneles en solo lectura.
 */
@Composable
fun ChatMarkdownView(
    text: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    answered: String? = null,
    onReply: ((String) -> Boolean)? = null,
    selectable: Boolean = true,
) {
    var selectedLink by remember {mutableStateOf<String?>(null)}
    val st = mdStyles().copy(onLink={selectedLink=it})
    val blocks = remember(text, streaming) { com.vidadomu.nexus.agent.core.chat.ChatPresentation.blocks(text, streaming) }
    val ctx = remember(answered, onReply) { WidgetCtx(answered, onReply) }
    if (selectable) SelectionContainer(modifier) { MdBlockList(blocks, st, ctx) }
    else Box(modifier) { MdBlockList(blocks, st, ctx) }
    selectedLink?.let {ChatLinkSheet(it,onDismiss={selectedLink=null})}
}

@Composable
private fun MdBlockList(blocks: List<MdBlock>, st: MdStyles, ctx: WidgetCtx) {
    Column {
        blocks.forEachIndexed { i, b ->
            val prev = blocks.getOrNull(i - 1)
            val gap = when {
                i == 0 -> 0.dp
                b is MdBlock.Heading && b.level <= 2 -> NexusTokens.space16
                b is MdBlock.Heading -> NexusTokens.space16
                prev is MdBlock.Heading -> NexusTokens.space8
                else -> NexusTokens.space12
            }
            Spacer(Modifier.height(gap))
            MdBlockView(b, st, ctx)
        }
    }
}

@Composable
private fun MdBlockView(b: MdBlock, st: MdStyles, ctx: WidgetCtx) {
    when (b) {
        is MdBlock.Heading -> if (b.level <= 2) {
            Text(annotated(b.spans, st), style = st.heading, color = st.ink)
        } else {
            Text(annotated(b.spans, st), style = st.label, color = st.accent)
        }
        is MdBlock.Paragraph -> Text(annotated(b.spans, st), style = st.body, color = st.soft)
        is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) { b.items.forEach { MdListRow(it, st) } }
        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(NexusTokens.thumbnail).background(st.accent.copy(alpha = 0.7f)))
            Spacer(Modifier.width(NexusTokens.space12))
            Text(annotated(b.spans, st), style = st.body.copy(fontStyle = FontStyle.Italic), color = st.muted)
        }
        is MdBlock.Code -> MdCodeBlock(b, st)
        is MdBlock.Visual -> ChatVisualArtifact(b.language,b.code)
        is MdBlock.Table -> MdTable(b, st)
        is MdBlock.Callout -> if (b.blocks.isNotEmpty()) MdCallout(b, st, ctx)
        is MdBlock.Widget -> MdWidget(b.spec, ctx)
        MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = NexusTokens.space4), thickness = 1.dp, color = st.line)
    }
}

private class CalloutLook(val tone: Color, val icon: ImageVector, val label: String)

/** Recuadro semántico: el color sale del tema (claro/oscuro) y siempre va con icono y etiqueta, nunca solo color. */
@Composable
private fun MdCallout(b: MdBlock.Callout, st: MdStyles, ctx: WidgetCtx) {
    val cs = MaterialTheme.colorScheme
    val look = when (b.kind) {
        CalloutKind.TLDR -> CalloutLook(cs.primary, Icons.Default.AutoAwesome, "En breve")
        CalloutKind.TIP -> CalloutLook(cs.tertiary, Icons.Default.Info, "Consejo")
        CalloutKind.WARN -> CalloutLook(cs.error, Icons.Outlined.WarningAmber, "Ojo")
        CalloutKind.DECISION -> CalloutLook(cs.primary, Icons.Default.Tune, "Decisión")
        CalloutKind.RESULT -> CalloutLook(cs.tertiary, Icons.Default.Check, "Resultado")
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(NexusTokens.field).background(look.tone.copy(alpha = 0.09f))) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(look.tone))
        Column(Modifier.padding(NexusTokens.space12), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(look.icon, null, Modifier.size(16.dp), tint = look.tone)
                Spacer(Modifier.width(NexusTokens.space4))
                Text(look.label, style = st.label.copy(fontWeight = FontWeight.SemiBold), color = look.tone)
            }
            MdBlockList(b.blocks, st, ctx)
        }
    }
}

@Composable
private fun MdListRow(item: ListItem, st: MdStyles) {
    // A bullet that only introduces the next ones («- Cómo quitarlo ahora:») is a label, not a peer of its steps.
    val introText = item.spans.joinToString("") { it.text }.trim()
    if (item.number == null && item.depth == 0 && introText.endsWith(":") && introText.length <= 60) {
        Text(annotated(item.spans, st), style = st.body, color = st.ink, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = NexusTokens.space4))
        return
    }
    Row(Modifier.padding(start = NexusTokens.space16 * item.depth)) {
        if (item.number != null) {
            Text(item.number!!, style = st.body.copy(fontFamily = MaterialTheme.typography.titleMedium.fontFamily, fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                color = st.accent, modifier = Modifier.widthIn(min = 20.dp))
            Spacer(Modifier.width(NexusTokens.space4))
        } else {
            // punto alineado con la primera línea (lineHeight 23sp)
            Box(Modifier.padding(top = NexusTokens.space8).size(if (item.depth == 0) 6.dp else 5.dp).clip(CircleShape)
                .let { m -> if (item.depth == 0) m.background(st.accent) else m.background(st.accent.copy(alpha = 0.45f)) })
            Spacer(Modifier.width(NexusTokens.space12))
        }
        Text(annotated(item.spans, st, leadAccent = true), style = st.body, color = st.soft)
    }
}

@Composable
private fun MdCodeBlock(b: MdBlock.Code, st: MdStyles) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1600); copied = false } }
    val label = when (b.language?.lowercase()) {
        null, "", "text", "txt", "prompt" -> "Prompt"
        else -> b.language!!
    }
    Surface(color = st.codeBg, shape = NexusTokens.card, border = BorderStroke(1.dp, st.accent.copy(alpha = 0.22f)), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = NexusTokens.space12, end = NexusTokens.space4, top = NexusTokens.space4), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = st.label.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize), color = st.accent.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                TextButton(onClick = { clipboard.setText(AnnotatedString(b.code)); copied = true }, contentPadding = PaddingValues(horizontal = NexusTokens.space8, vertical = 0.dp)) {
                    Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null, Modifier.size(15.dp), tint = st.accent)
                    Spacer(Modifier.width(NexusTokens.space4))
                    Text(if (copied) "Copiado" else "Copiar", style = MaterialTheme.typography.labelMedium, color = st.accent)
                }
            }
            HorizontalDivider(thickness = 1.dp, color = st.accent.copy(alpha = 0.12f))
            Text(b.code, style = st.body.copy(fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.bodyMedium.fontSize), color = st.ink,
                modifier = Modifier.padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space12))
        }
    }
}

@Composable
private fun MdTable(t: MdBlock.Table, st: MdStyles) {
    val cols = maxOf(t.header.size, t.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    // La tabla ocupa el ancho de la pantalla (hasta 3 columnas sin desplazarse); con más columnas, mínimo 120 dp cada una.
    Surface(color = Color.Transparent, shape = NexusTokens.field, border = BorderStroke(1.dp, st.line), modifier = Modifier.fillMaxWidth()) {
      BoxWithConstraints {
        val tableWidth = maxOf(maxWidth, (cols * 120).dp)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.width(tableWidth).background(st.accent.copy(alpha = 0.08f)).padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space8)) {
                for (c in 0 until cols) Text(annotated(t.header.getOrElse(c) { emptyList() }, st),
                    style = st.label.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize), color = st.accent, modifier = Modifier.weight(1f))
            }
            t.rows.forEachIndexed { r, row ->
                if (r > 0) HorizontalDivider(thickness = 1.dp, color = st.line)
                Row(Modifier.width(tableWidth).padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space8)) {
                    for (c in 0 until cols) Text(annotated(row.getOrElse(c) { emptyList() }, st), style = st.body.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                        color = if (c == 0) st.ink else st.soft, modifier = Modifier.weight(1f).padding(end = NexusTokens.space8))
                }
            }
        }
      }
    }
}
