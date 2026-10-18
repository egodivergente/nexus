package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.ui.theme.NexusTokens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val codeBg: Color,
    val line: Color,
    val heading: TextStyle,
    val label: TextStyle,
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
        codeBg = cs.background.copy(alpha = 0.72f),
        line = cs.outlineVariant,
        heading = ty.titleMedium,
        label = ty.labelMedium,
    )
}

private fun annotated(spans: List<MdSpan>, st: MdStyles, leadAccent: Boolean = false): AnnotatedString = buildAnnotatedString {
    spans.forEachIndexed { idx, sp ->
        val accentLead = leadAccent && idx == 0 && sp.bold
        val style = SpanStyle(
            color = when {
                sp.code || sp.link != null || accentLead -> st.accent
                sp.bold -> st.ink
                else -> Color.Unspecified
            },
            // Negrita moderada: el modelo marca mucho en negrita y en un párrafo pesaba demasiado; el color ya la destaca.
            fontWeight = if (sp.bold) FontWeight.Medium else null,
            fontStyle = if (sp.italic) FontStyle.Italic else null,
            fontFamily = if (sp.code) FontFamily.Monospace else null,
            fontSize = if (sp.code) st.body.fontSize else androidx.compose.ui.unit.TextUnit.Unspecified,
            background = if (sp.code) st.accent.copy(alpha = 0.12f) else Color.Unspecified,
            textDecoration = when {
                sp.strike -> TextDecoration.LineThrough
                sp.link != null -> TextDecoration.Underline
                else -> null
            },
        )
        if (sp.link != null && sp.link!!.startsWith("http")) {
            withLink(LinkAnnotation.Url(sp.link!!, TextLinkStyles(style))) { append(sp.text) }
        } else withStyle(style) { append(sp.text) }
    }
}

/** Mensaje del asistente con jerarquía tipográfica; nunca muestra marcas de markdown crudas. */
@Composable
fun ChatMarkdownView(text: String, modifier: Modifier = Modifier) {
    val st = mdStyles()
    val blocks = remember(text) { ChatMarkdown.parse(text) }
    SelectionContainer(modifier) {
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
                MdBlockView(b, st)
            }
        }
    }
}

@Composable
private fun MdBlockView(b: MdBlock, st: MdStyles) {
    when (b) {
        is MdBlock.Heading -> if (b.level <= 2) {
            Text(annotated(b.spans, st), style = st.heading, color = st.ink)
        } else {
            Text(annotated(b.spans, st), style = st.label, color = st.accent)
        }
        is MdBlock.Paragraph -> Text(annotated(b.spans, st), style = st.body, color = st.soft)
        is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) { b.items.forEach { MdListRow(it, st) } }
        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(NexusTokens.thumbnail).background(st.accent.copy(alpha = 0.7f)))
            Spacer(Modifier.width(NexusTokens.space12))
            Text(annotated(b.spans, st), style = st.body.copy(fontStyle = FontStyle.Italic), color = st.muted)
        }
        is MdBlock.Code -> MdCodeBlock(b, st)
        is MdBlock.Table -> MdTable(b, st)
        MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = NexusTokens.space4), thickness = 1.dp, color = st.line)
    }
}

@Composable
private fun MdListRow(item: ListItem, st: MdStyles) {
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
    Surface(color = Color.Transparent, shape = NexusTokens.field, border = BorderStroke(1.dp, st.line), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().background(st.accent.copy(alpha = 0.08f)).padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space8)) {
                for (c in 0 until cols) Text(annotated(t.header.getOrElse(c) { emptyList() }, st),
                    style = st.label.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize), color = st.accent, modifier = Modifier.weight(1f))
            }
            t.rows.forEachIndexed { r, row ->
                if (r > 0) HorizontalDivider(thickness = 1.dp, color = st.line)
                Row(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space8)) {
                    for (c in 0 until cols) Text(annotated(row.getOrElse(c) { emptyList() }, st), style = st.body.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                        color = if (c == 0) st.ink else st.soft, modifier = Modifier.weight(1f).padding(end = NexusTokens.space8))
                }
            }
        }
    }
}
