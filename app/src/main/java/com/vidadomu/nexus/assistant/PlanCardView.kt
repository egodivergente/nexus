@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.vidadomu.nexus.assistant

import androidx.compose.ui.res.imageResource
import androidx.compose.foundation.background
import com.vidadomu.nexus.ui.components.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.vidadomu.nexus.ui.components.NexusTopAppBar
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.components.noticeMessage
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import android.net.Uri
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.vidadomu.nexus.agent.core.llm.AntigravityReasoning
import com.vidadomu.nexus.agent.core.llm.CodexReasoning
import com.vidadomu.nexus.agent.core.llm.BrainAuthMode
import com.vidadomu.nexus.agent.core.llm.BrainProfile
import com.vidadomu.nexus.agent.core.llm.BrainProtocol
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.cost.CostConfidence
import com.vidadomu.nexus.agent.core.cost.CostEstimate
import com.vidadomu.nexus.agent.core.schema.ParameterValidator
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.PlanState
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.agent.core.tools.PlanEdit
import com.vidadomu.nexus.ui.create.CreateUiState
import com.vidadomu.nexus.ui.theme.*
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged

// ---- tarjeta de plan ------------------------------------------------------------------------

internal fun visibleParams(plan: GenerationPlan): List<Pair<String, String>> =
    plan.effectiveParams.filter { (k, v) -> k != "prompt" && !ParameterValidator.isBlankLike(v) }.toList()

internal fun costLine(e:CostEstimate?,currency:com.vidadomu.nexus.core.currency.CurrencyManager?=null):String = when {
    e==null -> "Coste no disponible"
    e.amountUsd!=null -> (e.amountUsd?.let {currency?.formatCost(it)} ?: "Coste estimado") + when(e.confidence) {
        CostConfidence.NEXUS_QUOTE -> " · cotización de la calculadora"
        CostConfidence.PUBLISHED_RATE -> " · tarifa publicada × unidades"
        CostConfidence.BASE_ONLY -> " · orientativo"
        CostConfidence.UNKNOWN -> ""
    }
    else -> "Coste exacto no disponible · ${e.basis}"
}

internal fun stateLabel(p: PlanUi): String = when (p.state) {
    PlanState.PREPARED -> "Preparado"
    PlanState.AWAITING_CONFIRMATION -> "Esperando tu confirmación"
    PlanState.CONFIRMED -> "Confirmado"
    PlanState.SUBMITTED -> when (p.jobStatus) {
        "COMPLETED" -> "Terminado"; "FAILED" -> "Falló"; "CANCELLED" -> "Cancelado"; "PROCESSING" -> "Generando…"; else -> "En cola…"
    }
    PlanState.DISCARDED -> "Descartado"
    PlanState.UNCERTAIN -> "Envío sin confirmar"
}

@Composable
internal fun PlanCardView(
    p: PlanUi,
    onEdit: (PlanEdit) -> Unit,
    onDiscard: () -> Unit,
    onGenerate: () -> Unit,
    onOpenCanvas: () -> Unit,
    onOpenOutput: () -> Unit,
    assistantBusy: Boolean = false,
    primaryAction:Boolean=true,
    showDiscard:Boolean=true,
    onDirtyChanged:(Boolean)->Unit={},
    onDiagnoseFailure:()->Unit={},
    onOpenHistory:()->Unit={},
) {
    val planHaptics=rememberNexusHaptics()
    val app=LocalContext.current.applicationContext as com.vidadomu.nexus.NexusApplication
    val currency=app.currencyManager
    val currencyState by currency.state.collectAsState()
    var expanded by rememberSaveable(p.plan.id) {mutableStateOf(false)}
    val plan = p.plan
    val retired = com.vidadomu.nexus.provider.registry.RetiredProviders.isRetired(plan.ref.providerId)
    val editable = p.state != PlanState.SUBMITTED && p.state != PlanState.DISCARDED && p.state != PlanState.UNCERTAIN
    // El texto que edita el usuario no se pisa si el asistente cambia el plan a la vez.
    var prompt by rememberSaveable(plan.id) { mutableStateOf(plan.prompt) }
    var basePrompt by rememberSaveable(plan.id) { mutableStateOf(plan.prompt) }
    var changedUnderEdit by rememberSaveable(plan.id) { mutableStateOf(false) }
    LaunchedEffect(plan.prompt) {
        if (plan.prompt != basePrompt) {
            if (prompt == basePrompt) prompt = plan.prompt else changedUnderEdit = true
            basePrompt = plan.prompt
        }
        if (prompt == plan.prompt) changedUnderEdit = false
    }
    var editParam by remember { mutableStateOf<Pair<String, String>?>(null) }
    val dirty = prompt != plan.prompt
    SideEffect {onDirtyChanged(dirty)}
    editParam?.let { (k, v) -> ParamEditDialog(k, v, onSave = { nv -> editParam = null; onEdit(PlanEdit(params = mapOf(k to nv))) }, onDismiss = { editParam = null }) }

    Card(Modifier.fillMaxWidth(),shape=NexusTokens.card,border=null) {
        Column(Modifier.padding(NexusTokens.space16),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                Column(Modifier.weight(1f)) {
                    Text(plan.modelName,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(remember(plan.estimate,currencyState) {plan.estimate?.amountUsd?.let(currency::formatCost) ?: "Por confirmar"},style=MaterialTheme.typography.bodyMedium.tabularMoney(),color=TextSecondary)
                    Text(stateLabel(p),style=MaterialTheme.typography.labelMedium,color=TextSecondary)
                }
                if(editable && !retired) {
                    val enabled=p.executable && !p.busy && !dirty && !assistantBusy
                    if(primaryAction) Button(onClick={planHaptics.perform(NexusHapticEvent.CONFIRMATION);onGenerate()},enabled=enabled,shape=NexusTokens.field) {Text("Generar")}
                    else FilledTonalButton(onClick={planHaptics.perform(NexusHapticEvent.CONFIRMATION);onGenerate()},enabled=enabled,shape=NexusTokens.field) {Text("Generar")}
                }
            }
            if(p.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            StudioTextField(prompt,{prompt=it},enabled=editable && !p.busy,modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=8,
                textStyle=MaterialTheme.typography.bodyLarge,shape=NexusTokens.field,label={Text("Prompt")})
            if(changedUnderEdit && dirty) InlineNotice("El asistente cambió el prompt mientras editabas.",technicalDetails="Deshacer carga su versión; Aplicar guarda la tuya.",isError=false)
            if(dirty && editable) FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                TextButton(onClick={onEdit(PlanEdit(prompt=prompt))},enabled=!p.busy) {Text("Aplicar cambios",color=TextSecondary)}
                TextButton(onClick={prompt=plan.prompt;changedUnderEdit=false}) {Text("Deshacer",color=TextSecondary)}
            }
            if(dirty) Text("Aplica o deshaz el cambio antes de generar.",style=MaterialTheme.typography.labelMedium,color=TextSecondary)
            else if(assistantBusy) Text("Espera a que el asistente termine de ajustar el plan.",style=MaterialTheme.typography.labelMedium,color=TextSecondary)
            if(plan.warnings.isNotEmpty()) InlineNotice("Revisa los avisos del plan.",technicalDetails=plan.warnings.joinToString("\n"),isError=false)
            if(p.issues.isNotEmpty()) InlineNotice("Faltan datos para preparar este encargo.",technicalDetails=p.issues.joinToString("\n"))
            p.error?.takeIf { p.state!=PlanState.UNCERTAIN }?.let {
                val history=com.vidadomu.nexus.ui.navigation.HistoryLinks.mentionsHistoryScreen(it)
                InlineNotice(noticeMessage(it,"No se pudo completar el encargo."),technicalDetails=it,
                    actionLabel=if(history) "Abrir Historial" else null,onAction=if(history) onOpenHistory else null)
            }
            if(p.state==PlanState.UNCERTAIN) {
                InlineNotice("Envío sin confirmar. Revisa Historial antes de generar otra vez.",
                    technicalDetails=listOfNotNull(p.error,"Este plan queda bloqueado para evitar un doble pago.").joinToString("\n"),isError=false,
                    actionLabel="Abrir Historial",onAction=onOpenHistory)
                if(p.jobId!=null) FilledTonalButton(onClick=onOpenOutput) {Text("Ver este trabajo",color=TextSecondary)}
            } else if(p.state==PlanState.SUBMITTED) {
                if(p.jobStatus=="PENDING" || p.jobStatus=="PROCESSING") {
                    if(p.jobProgress!=null) LinearProgressIndicator(progress={p.jobProgress.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if(p.jobStatus=="COMPLETED") FilledTonalButton(onClick=onOpenOutput) {Text("Ver resultado")}
                if(p.jobStatus in setOf("FAILED","CANCELLED")) FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    if(p.jobStatus=="FAILED") TextButton(onClick=onDiagnoseFailure,enabled=!assistantBusy) {Text("Analizar fallo",color=TextSecondary)}
                    TextButton(onClick={planHaptics.perform(NexusHapticEvent.CANCEL_CONFIRMED);onDiscard()}) {Text("Quitar tarjeta",color=TextSecondary)}
                    TextButton(onClick=onOpenOutput) {Text("Ver detalle",color=TextSecondary)}
                }
            } else if(p.state!=PlanState.DISCARDED) FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                if(!retired) TextButton(onClick=onOpenCanvas,enabled=!p.busy) {Text("Abrir en el Lienzo",color=TextSecondary)}
                if(showDiscard) TextButton(onClick={planHaptics.perform(NexusHapticEvent.CANCEL_CONFIRMED);onDiscard()},enabled=!p.busy) {Text("Descartar",color=TextSecondary)}
            }
            TextButton(onClick={expanded=!expanded},colors=ButtonDefaults.textButtonColors(contentColor=TextSecondary)) {
                Text(if(expanded) "Ocultar detalles" else "Detalles");Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)
            }
            if(expanded) {
                Text("${plan.modelName} · ${modalityLabel(plan.modality)}",style=MaterialTheme.typography.bodyMedium)
                Text(plan.ref.modelId,style=MaterialTheme.typography.labelMedium,color=TextSecondary)
                plan.rationale?.takeIf {it.isNotBlank()}?.let {Text(it,style=MaterialTheme.typography.bodyMedium,color=TextSecondary)}
                listOfNotNull(plan.sceneCode?.let {"Escena $it"},plan.shotCode?.let {"Plano $it"},plan.characters.takeIf {it.isNotEmpty()}?.joinToString()).takeIf {it.isNotEmpty()}
                    ?.let {Text(it.joinToString(" · "),style=MaterialTheme.typography.labelMedium,color=TextSecondary)}
                if(plan.alternatives.isNotEmpty()) Text("Alternativas: "+plan.alternatives.joinToString {it.modelId.substringAfter('/')},style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
                if(plan.media.isNotEmpty()) {
                    Section("Medios")
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                        items(plan.media) {m->Column(Modifier.width(96.dp)) {
                            AsyncImage(m.uri,roleLabel(m.role),Modifier.size(96.dp).clip(NexusTokens.thumbnail),contentScale=ContentScale.Crop)
                            Text(roleLabel(m.role),style=MaterialTheme.typography.labelMedium,color=TextSecondary)
                        }}
                    }
                }
            plan.originalPrompt?.takeIf { it != plan.prompt }?.let { original ->
                var showOriginal by remember(plan.id) { mutableStateOf(false) }
                TextButton(onClick = { showOriginal = !showOriginal }) { Text(if(showOriginal) "Ocultar original" else "Ver prompt original") }
                if(showOriginal) androidx.compose.foundation.text.selection.SelectionContainer { Text(original, style = MaterialTheme.typography.bodySmall) }
            }
            val params = visibleParams(plan)
            if (params.isNotEmpty()) {
                Section("Configuración")
                params.forEach { (k, v) ->
                    Column(Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch).clip(NexusTokens.thumbnail).clickable(enabled=editable && !p.busy) {editParam=k to v}.padding(vertical=NexusTokens.space8)) {
                        Text(k, style = MaterialTheme.typography.bodySmall, color=TextSecondary)
                        Text(v, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Section("Coste")
            Text(costLine(plan.estimate,currency), fontWeight = FontWeight.Medium)
            plan.estimate?.notes?.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = TextMuted) }
                plan.estimate?.amountUsd?.let {Text(currency.formatUsdDetails(it),style=MaterialTheme.typography.labelMedium.tabularMoney(),color=TextSecondary)}
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title,style=MaterialTheme.typography.titleMedium,color=TextPrimary,modifier=Modifier.padding(top=NexusTokens.space4))
}

private fun modalityLabel(m: String) = when (m.uppercase()) { "VIDEO" -> "vídeo"; "IMAGE" -> "imagen"; "AUDIO" -> "audio"; else -> m.lowercase() }
private fun roleLabel(r: String) = when (r) { "first_frame" -> "Primer fotograma"; "last_frame" -> "Último fotograma"; "reference" -> "Referencia"; "video" -> "Vídeo"; else -> r }

@Composable
private fun ParamEditDialog(key: String, value: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var v by remember(key, value) { mutableStateOf(value) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(key) },
        text = { Column { StudioTextField(v, { v = it }, shape=NexusTokens.field, singleLine = false, maxLines = 6); Text("Se valida contra el schema del modelo. Vacío o «–» = quitar.", style = MaterialTheme.typography.labelSmall, color = TextMuted) } },
        confirmButton = { TextButton(onClick = { onSave(v.trim()) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
