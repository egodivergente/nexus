@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.vidadomu.nexus.assistant

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vidadomu.nexus.agent.core.orders.FailureKind
import com.vidadomu.nexus.agent.core.orders.Order
import com.vidadomu.nexus.agent.core.orders.OrderBook
import com.vidadomu.nexus.agent.core.orders.OrderStatus
import com.vidadomu.nexus.agent.core.tools.PlanEdit
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.TextSecondary

/** La ÚNICA acción principal que ofrece una tarjeta según el estado verificado de su encargo. */
internal enum class CardAction { GENERATE, SENDING, NONE, VIEW_RESULT, APPLY_FIX, RETRY, ASK_AGENT, CHECK_HISTORY, REVIEW }

internal data class CardPresentation(
    val status: String,
    val action: CardAction,
    val actionLabel: String?,
    val enabled: Boolean,
    /** Mensaje principal para errores o bloqueos (una línea clara). */
    val headline: String? = null,
    val detail: String? = null,
    val error: Boolean = false,
) {
    /** Lo que la tarjeta declara haber pintado; el agente lo recibe tal cual. */
    val rendered: String get() = status + (actionLabel?.let { " · botón «$it»" + if (!enabled) " (desactivado)" else "" } ?: " · sin botón")
}

private val TRANSIENT = Regex("(?i)timeout|tiempo de espera|network|red|conexi[oó]n|unavailable|429|rate limit|503|502")

internal fun presentOrder(order: Order, costLabel: String?, busy: Boolean, assistantBusy: Boolean, dirty: Boolean): CardPresentation {
    val failure = order.lastFailure
    return when (order.status) {
        OrderStatus.DRAFT -> CardPresentation("Borrador", CardAction.REVIEW, "Revisar datos", true,
            headline = "Faltan datos para poder enviarlo.", detail = order.issues.joinToString("\n"), error = true)
        OrderStatus.AWAITING_CONFIRMATION -> CardPresentation("Pendiente de confirmación", CardAction.GENERATE,
            if (costLabel != null) "Generar · $costLabel" else "Generar", !busy && !assistantBusy && !dirty,
            detail = when { dirty -> "Aplica o deshaz tu cambio antes de generar."; assistantBusy -> "Espera a que el asistente termine."; else -> null })
        OrderStatus.SUBMITTING -> CardPresentation("Enviando…", CardAction.SENDING, "Enviando…", false)
        OrderStatus.PROCESSING -> CardPresentation("Procesando" + (order.progress?.let { " · ${(it.coerceIn(0f, 1f) * 100).toInt()} %" } ?: ""), CardAction.NONE, null, false)
        OrderStatus.COMPLETED -> CardPresentation("Completado", CardAction.VIEW_RESULT, "Ver resultado", true)
        OrderStatus.FAILED -> {
            val message = failure?.message ?: "El proveedor devolvió un error sin detalle."
            val charged = if (failure?.kind == FailureKind.JOB_FAILED) " El proveedor decide si cobra un trabajo fallido." else " No se ha cobrado: no se creó ningún trabajo."
            val (action, label) = when {
                failure?.fix != null -> CardAction.APPLY_FIX to "Aplicar corrección"
                failure?.kind in OrderBook.RETRYABLE && TRANSIENT.containsMatchIn(message) -> CardAction.RETRY to "Reintentar"
                else -> CardAction.ASK_AGENT to "Pedir corrección al agente"
            }
            CardPresentation("Error", action, label, !busy && !assistantBusy,
                headline = (failure?.explanation ?: "No se generó.") + if (failure?.kind == FailureKind.CANCELLED) "" else charged,
                detail = message + (failure?.fix?.let { "\nCorrección propuesta: ${it.description}." } ?: ""), error = true)
        }
        OrderStatus.UNCERTAIN -> CardPresentation("Envío sin confirmar", CardAction.CHECK_HISTORY, "Abrir Historial", true,
            headline = "No se sabe si el proveedor lo recibió: podría haberse cobrado. Nexus no lo repetirá; compruébalo en Historial.", error = true)
        OrderStatus.DISCARDED -> CardPresentation("Descartado", CardAction.NONE, null, false)
    }
}

internal fun billingLabel(providerId: String): String = when (providerId) {
    "atlas_cloud" -> "Atlas Cloud"
    "crun" -> "Crun"
    "runway" -> "Runway"
    "google_flow" -> "Google Flow · tu cuenta"
    else -> providerId
}

/**
 * Tarjeta compacta de un encargo: modelo, estado, coste y un solo botón. Prompt, parámetros,
 * medios e identificadores quedan plegados en «Detalles».
 */
@Composable
internal fun OrderCard(
    order: Order,
    p: PlanUi,
    outputs: List<String>,
    assistantBusy: Boolean,
    onGenerate: () -> Unit,
    onApplyFix: () -> Unit,
    onAskAgent: () -> Unit,
    onEdit: (PlanEdit) -> Unit,
    onDiscard: () -> Unit,
    onOpenCanvas: () -> Unit,
    onOpenOutput: () -> Unit,
    onRendered: (String) -> Unit,
    onOpenHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as com.vidadomu.nexus.NexusApplication
    val currency = app.currencyManager
    val currencyState by currency.state.collectAsState()
    val plan = p.plan
    var expanded by rememberSaveable(order.id) { mutableStateOf(false) }
    var prompt by rememberSaveable(order.id, plan.id) { mutableStateOf(plan.prompt) }
    LaunchedEffect(plan.prompt) { prompt = plan.prompt }
    val dirty = prompt != plan.prompt
    val cost = remember(plan.estimate, currencyState) { plan.estimate?.amountUsd?.let(currency::formatCost) }
    val shown = presentOrder(order, cost, p.busy, assistantBusy, dirty)
    // Lo que de verdad se pinta: con resultados, miniaturas en lugar del botón.
    val rendered = if (order.status == OrderStatus.COMPLETED && outputs.isNotEmpty()) "${shown.status} · ${outputs.size} resultado(s) visibles como miniatura" else shown.rendered
    LaunchedEffect(order.id, rendered) { onRendered(rendered) }
    var editParam by remember { mutableStateOf<Pair<String, String>?>(null) }
    editParam?.let { (k, v) -> ParamEditDialogCompact(k, v, onSave = { nv -> editParam = null; onEdit(PlanEdit(params = mapOf(k to nv))) }, onDismiss = { editParam = null }) }

    var menu by remember { mutableStateOf(false) }
    val running = order.status in setOf(OrderStatus.SUBMITTING, OrderStatus.PROCESSING)
    Card(modifier.fillMaxWidth().animateContentSize().semantics { contentDescription = "Encargo ${plan.modelName}"; stateDescription = shown.status }, shape = NexusTokens.card) {
        Column(Modifier.padding(start = NexusTokens.space12, end = NexusTokens.space4, top = NexusTokens.space8, bottom = NexusTokens.space8),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Cabecera en una línea: qué, quién factura, cuánto, estado y controles secundarios plegados.
            val compact = order.status == OrderStatus.COMPLETED && outputs.isNotEmpty()
            val titleTexts: @Composable () -> Unit = {
                Text(plan.modelName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(billingLabel(plan.ref.providerId), cost ?: "coste por confirmar".takeIf { order.status == OrderStatus.AWAITING_CONFIRMATION },
                    "intento ${order.attempt}".takeIf { order.attempt > 1 }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val expandButton: @Composable () -> Unit = {
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(40.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Ocultar detalles" else "Detalles", tint = TextSecondary)
                }
            }
            val menuBox: @Composable () -> Unit = {
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "Más opciones", tint = TextSecondary) }
                    DropdownMenu(menu, { menu = false }) {
                        if (order.status.editable) DropdownMenuItem({ Text("Abrir en el Lienzo") }, { menu = false; onOpenCanvas() }, enabled = !p.busy)
                        if (order.status.editable) DropdownMenuItem({ Text("Descartar") }, { menu = false; onDiscard() }, enabled = !p.busy && !assistantBusy)
                        DropdownMenuItem({ Text("Abrir Historial") }, { menu = false; onOpenHistory() })
                    }
                }
            }
            if (compact) {
                // Terminado: miniatura a la izquierda y los datos a su derecha, sin el hueco vacío de antes.
                Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), verticalAlignment = Alignment.CenterVertically) {
                    ResultThumb(outputs.first(), plan.modality, onOpenOutput, small = true)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProviderMark(plan.ref.providerId, plan.ref.modelId, plan.modelName)
                            Spacer(Modifier.width(NexusTokens.space8))
                            Column(Modifier.weight(1f)) { titleTexts() }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(shown.status, shown.error)
                            Spacer(Modifier.weight(1f))
                            expandButton()
                            menuBox()
                        }
                    }
                }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderMark(plan.ref.providerId, plan.ref.modelId, plan.modelName)
                Spacer(Modifier.width(NexusTokens.space8))
                Column(Modifier.weight(1f)) { titleTexts() }
                StatusPill(shown.status, shown.error)
                expandButton()
                menuBox()
            }
            Column(Modifier.padding(end = NexusTokens.space8), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (running) {
                    val progress = order.progress
                    if (progress != null) LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                shown.headline?.takeIf { !compact || expanded }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (shown.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis) }
                shown.detail?.takeIf { it.isNotBlank() && (!compact || expanded) }?.let {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                // El resultado es el contenido: miniatura que abre el visor, sin botón aparte.
                if (outputs.isNotEmpty() && (!compact || outputs.size > 1)) LazyRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    items(if (compact) outputs.drop(1) else outputs) { uri -> ResultThumb(uri, plan.modality, onOpenOutput, small = compact) }
                }
                if (shown.actionLabel != null && (order.status != OrderStatus.COMPLETED || outputs.isEmpty())) {
                    val click = when (shown.action) {
                        CardAction.GENERATE, CardAction.RETRY -> onGenerate
                        CardAction.APPLY_FIX -> onApplyFix
                        CardAction.ASK_AGENT -> onAskAgent
                        CardAction.VIEW_RESULT -> onOpenOutput
                        CardAction.CHECK_HISTORY -> onOpenHistory
                        CardAction.REVIEW -> ({ expanded = true })
                        CardAction.SENDING, CardAction.NONE -> ({})
                    }
                    Button(onClick = click, enabled = shown.enabled, shape = NexusTokens.field, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = NexusTokens.space16, vertical = 8.dp)) {
                        Text(shown.actionLabel, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (expanded) OrderDetails(order, p, prompt, { prompt = it }, dirty, onEdit, onOpenCanvas, currency) { k, v -> editParam = k to v }
            }
        }
    }
}

/** Logo oficial del modelo (biblioteca de marcas de la app); si no hay uno conocido, monograma neutro con el proveedor. */
@Composable
internal fun ProviderMark(providerId: String, modelId: String, modelName: String) {
    com.vidadomu.nexus.ui.components.BrandLogo(organization = providerId.replace('_', ' '), family = modelName, modelId = modelId, size = 28.dp, containerSize = 32.dp)
}

/** Miniatura 16:9 del resultado (fotograma para vídeo) con indicador de reproducción. */
@Composable
private fun ResultThumb(uri: String, modality: String, onOpen: () -> Unit, small: Boolean = false) {
    val context = LocalContext.current
    val video = modality == "VIDEO"
    Box(Modifier.width(if (video) (if (small) 132.dp else 200.dp) else (if (small) 96.dp else 148.dp)).aspectRatio(if (video) 16f / 9f else 1f).clip(NexusTokens.field).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = coil.request.ImageRequest.Builder(context).data(uri).crossfade(160)
                .apply { if (video) decoderFactory { result, options, _ -> coil.decode.VideoFrameDecoder(result.source, options) } }.build(),
            contentDescription = if (video) "Abrir vídeo generado" else "Abrir imagen generada",
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
        )
        if (video) Surface(shape = NexusTokens.filter, color = MaterialTheme.colorScheme.scrim.copy(alpha = .55f), contentColor = com.vidadomu.nexus.ui.theme.MediaForeground) {
            Icon(Icons.Default.PlayArrow, null, Modifier.padding(8.dp).size(28.dp))
        }
    }
}

@Composable
private fun StatusPill(text: String, error: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = NexusTokens.filter, color = if (error) colors.errorContainer else colors.secondaryContainer,
        contentColor = if (error) colors.onErrorContainer else colors.onSecondaryContainer) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun OrderDetails(order: Order, p: PlanUi, prompt: String, onPrompt: (String) -> Unit, dirty: Boolean, onEdit: (PlanEdit) -> Unit,
    onOpenCanvas: () -> Unit, currency: com.vidadomu.nexus.core.currency.CurrencyManager, onEditParam: (String, String) -> Unit) {
    val plan = p.plan
    val editable = order.status.editable && !p.busy
    Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        HorizontalDivider()
        if (plan.prompt.isNotBlank() || dirty) {
            com.vidadomu.nexus.ui.components.StudioTextField(prompt, onPrompt, enabled = editable, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6,
                shape = NexusTokens.field, label = { Text("Prompt") })
            if (dirty) FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                TextButton(onClick = { onEdit(PlanEdit(prompt = prompt)) }) { Text("Aplicar cambio") }
                TextButton(onClick = { onPrompt(plan.prompt) }) { Text("Deshacer", color = TextSecondary) }
            }
        }
        val params = visibleParams(plan)
        if (params.isNotEmpty()) {
            Text("Configuración", style = MaterialTheme.typography.labelLarge)
            params.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(NexusTokens.thumbnail).clickable(enabled = editable) { onEditParam(k, v) },
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(k, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                    Text(v, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (plan.media.isNotEmpty()) Text("Archivos: " + plan.media.joinToString { m -> when (m.role) { "video" -> "vídeo original"; "first_frame" -> "primer fotograma"; "last_frame" -> "último fotograma"; else -> "referencia" } },
            style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(costLine(plan.estimate, currency), style = MaterialTheme.typography.bodySmall)
        plan.estimate?.notes?.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = TextSecondary) }
        if (plan.warnings.isNotEmpty()) Text(plan.warnings.joinToString("\n"), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        plan.rationale?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
        if (order.failures.size > 1) {
            Text("Historial de errores", style = MaterialTheme.typography.labelLarge)
            order.failures.dropLast(1).takeLast(4).forEach { f -> Text("Intento ${f.attempt}: ${f.message}", style = MaterialTheme.typography.labelSmall, color = TextSecondary) }
        }
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text("${plan.ref.providerId}:${plan.ref.modelId}\nEncargo ${order.id} · intento ${order.attempt} · plan ${plan.id}" + (order.jobId?.let { " · job $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun ParamEditDialogCompact(key: String, value: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var v by remember(key, value) { mutableStateOf(value) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(key) },
        text = { Column { com.vidadomu.nexus.ui.components.StudioTextField(v, { v = it }, shape = NexusTokens.field, singleLine = false, maxLines = 6)
            Text("Se valida contra el schema del modelo. Vacío o «–» = quitar.", style = MaterialTheme.typography.labelSmall, color = TextSecondary) } },
        confirmButton = { TextButton(onClick = { onSave(v.trim()) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
