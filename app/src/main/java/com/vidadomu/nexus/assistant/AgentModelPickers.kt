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

/**
 * La píldora de modo del cuadro de entrada. Nexus trabaja en un único modo (Agente), así que lo que se elige aquí es
 * cuánto razona, solo si el cerebro activo lo admite: Auto · Rápido · Mínimo · Bajo · Medio · Alto.
 */
@Composable
internal fun ReasoningPicker(ui: AssistantUiState, vm: AssistantViewModel) {
    val brain = ui.settings.brain
    val argon = if(ui.settings.backend == AssistantSettings.BACKEND_ANTIGRAVITY) AntigravityReasoning.effort(ui.settings.modelId) else null
    val codex = ui.settings.backend == AssistantSettings.BACKEND_CODEX
    // Sin soporte se muestra desactivado (no oculto) para que el control siga siendo reconocible.
    if (!reasoningAvailable(ui)) {
        ReasoningIcon("Agente", enabled = false, description = "Modo Agente. Este modelo no permite ajustar el razonamiento", onClick = {})
        return
    }
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val modes = if (codex) codexReasoningModes(ui.settings.modelId) else if (argon != null) antigravityReasoningModes(ui.settings.modelId) else agentReasoningModes(antigravity = false)
    val current = if (codex) agentReasoningCurrent(modes, CodexReasoning.effective(ui.settings.modelId, ui.settings.codexEffort), null)
        else agentReasoningCurrent(modes, argon ?: brain?.reasoningEffort, brain?.thinking)
    Box {
        ReasoningIcon(current, enabled = !ui.thinking, onClick = { open = true })
        DropdownMenu(open, { open = false }) {
            modes.forEach { (name, effort, thinking) ->
                DropdownMenuItem(text = { Column { Text(name)
                        (if (codex && effort == null) codexAutoHint(ui.settings.modelId) else reasoningHint(name))?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } },
                    trailingIcon = { if (name == current) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { open = false; vm.setReasoning(effort, thinking)?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() } })
            }
        }
    }
}

private fun reasoningHint(name: String): String? = when (name.lowercase()) {
    "auto", "automático" -> "El agente decide cuánto pensar"
    "rápido" -> "Contesta enseguida"
    "mínimo" -> "Casi sin razonar"
    "bajo" -> "Razonamiento ligero"
    "medio" -> "Equilibrio entre velocidad y calidad"
    "alto" -> "Más profundo, tarda más"
    "muy alto" -> "Aún más profundo; bastante más lento"
    "máximo" -> "Lo máximo que razona; muy lento"
    "ultra" -> "Máximo y delega partes de la tarea por su cuenta; lo más lento"
    else -> null
}

private fun codexAutoHint(model: String): String =
    CodexReasoning.defaultFor(model)?.let { "El que trae el modelo (${CODEX_EFFORT_NAMES[it]})" } ?: "El que trae el modelo"

/** Como en Ajustes: los modelos de generaciones anteriores no se ofrecen salvo que sea el elegido. */
private val LEGACY_BRIDGE_MODEL = Regex("4-[56]|claude-.*4-6|3\\.[67]-flash|gpt-5\\.")

/**
 * El selector de modelo del cuadro de entrada. AGY y Codex cambian de modelo aquí mismo (Vidal 06-11); los demás
 * cerebros abren con un toque los ajustes del asistente, que es donde se elige su modelo.
 */
@Composable
internal fun BridgeModelPicker(ui: AssistantUiState, vm: AssistantViewModel, onOpenSettings: () -> Unit) {
    val s = ui.settings
    val all = when (s.backend) {
        AssistantSettings.BACKEND_ANTIGRAVITY -> AssistantSettings.ANTIGRAVITY_MODELS
        AssistantSettings.BACKEND_CODEX -> AssistantSettings.CODEX_MODELS
        else -> {
            ModelMenuItem(shortModelName(agentLabel(s)).ifBlank { agentLabel(s) }.ifBlank { "Modelo" }, enabled = !ui.thinking, onClick = onOpenSettings)
            return
        }
    }
    // Las variantes de nivel de Argon e Hydrogen son un solo modelo cada uno; el nivel se elige en «Razonamiento».
    val selected = all.firstOrNull { it.first == s.modelId ||
        (s.backend == AssistantSettings.BACKEND_ANTIGRAVITY && AntigravityReasoning.family(it.first) != null && AntigravityReasoning.family(it.first) == AntigravityReasoning.family(s.modelId)) }
    val options = all.filter { !LEGACY_BRIDGE_MODEL.containsMatchIn(it.first) || it == selected }
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box {
        ModelMenuItem(shortModelName(selected?.second ?: s.modelId).ifBlank { selected?.second ?: s.modelId }, enabled = !ui.thinking, onClick = { open = true })
        DropdownMenu(open, { open = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) },
                    trailingIcon = { if (selected?.first == id) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { open = false; vm.setBridgeModel(id)?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() } })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Otro cerebro o modelo…") }, leadingIcon = { Icon(Icons.Default.Tune, null) },
                onClick = { open = false; onOpenSettings() })
        }
    }
}

private fun reasoningAvailable(ui: AssistantUiState): Boolean =
    (ui.settings.backend == AssistantSettings.BACKEND_ANTIGRAVITY && AntigravityReasoning.effort(ui.settings.modelId) != null) ||
    ui.settings.backend == AssistantSettings.BACKEND_CODEX ||
    ui.settings.brain?.let { it.capabilities.reasoning && it.protocol != BrainProtocol.CUSTOM_REST } == true
