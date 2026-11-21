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

// ---- ajustes ---------------------------------------------------------------------------------

@Composable
internal fun AssistantSettingsDialog(ui: AssistantUiState, vm: AssistantViewModel, onDismiss: () -> Unit) {
    val s = ui.settings
    var backend by remember { mutableStateOf(s.backend) }
    var accessKey by remember { mutableStateOf(s.awsAccessKeyId) }
    var secret by remember { mutableStateOf("") }
    var clearSecret by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf(s.endpoint) }
    var modelId by remember { mutableStateOf(s.modelId) }
    var region by remember { mutableStateOf(s.region) }
    var token by remember { mutableStateOf("") }
    var clearToken by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf(s.temperature?.toString().orEmpty()) }
    var maxTokens by remember { mutableStateOf(s.maxTokens.toString()) }
    var sendImages by remember { mutableStateOf(s.sendImages) }
    var excluded by remember { mutableStateOf(s.excludedFamilies) }
    var baseUrl by remember { mutableStateOf(s.llmBaseUrl) }
    var apiKey by remember { mutableStateOf("") }
    var clearApiKey by remember { mutableStateOf(false) }
    var deleteCredential by remember { mutableStateOf<String?>(null) }
    var editingSkills by remember { mutableStateOf(false) }
    var inputRate by remember { mutableStateOf(s.inputUsdPerM?.toString().orEmpty()) }
    var outputRate by remember { mutableStateOf(s.outputUsdPerM?.toString().orEmpty()) }
    var agyEndpoint by remember { mutableStateOf(s.antigravityEndpoint) }
    var nativeImages by remember { mutableStateOf(s.nativeImages) }
    var codexEffort by remember { mutableStateOf(s.codexEffort) }
    // Cerebros personalizados y BytePlus: el selector los muestra junto a los de siempre.
    val brainStore = vm.runtime.settings.brains
    var brainTick by remember { mutableStateOf(0) }
    val brains = remember(brainTick) { brainStore.all() }
    var editingBrain by remember { mutableStateOf<BrainProfile?>(null) }
    var addingBrain by remember { mutableStateOf(false) }
    val isCustom = backend.startsWith(BrainStore.PREFIX)
    val maskedKey = remember(backend) { vm.maskedApiKey(backend) }
    var error by remember { mutableStateOf<String?>(null) }
    fun save() {
        val t = temperature.replace(',', '.').trim()
        val temp = if (t.isEmpty()) null else t.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
        if (t.isNotEmpty() && temp == null) { error = "Temperatura no válida (número ≥ 0; el máximo lo decide el modelo)."; return }
        val newToken = when { clearToken -> ""; token.isNotBlank() -> token; else -> null }
        val newSecret = when { clearSecret -> ""; secret.isNotBlank() -> secret; else -> null }
        val newApiKey = when { clearApiKey -> ""; apiKey.isNotBlank() -> apiKey; else -> null }
        val inRate = inputRate.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: run { error = "Tarifa de entrada no válida."; return } }
        val outRate = outputRate.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: run { error = "Tarifa de salida no válida."; return } }
        if (!isCustom && modelId.isBlank()) { error = "Escribe el id del modelo."; return }
        error = vm.saveSettings(s.copy(backend = backend, awsAccessKeyId = accessKey, endpoint = endpoint, modelId = modelId, region = region, temperature = temp,
            maxTokens = maxTokens.toIntOrNull() ?: s.maxTokens, sendImages = sendImages, excludedFamilies = excluded,
            llmBaseUrl = baseUrl, inputUsdPerM = inRate, outputUsdPerM = outRate,
            antigravityEndpoint = agyEndpoint.ifBlank { AssistantSettings.DEFAULT_ANTIGRAVITY_ENDPOINT }, nativeImages = nativeImages,
            codexEffort = codexEffort), newToken, newSecret, newApiKey)
        if (error == null) onDismiss()
    }
    NexusSheet("Ajustes del asistente", onDismiss, icon = Icons.Default.AutoAwesome,
        subtitle = "${ui.catalog.size} modelos · ${ui.skillsCount} skills · " + if (ui.thinking) "Trabajando" else "Listo",
        primaryAction = { Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") } }) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = NexusTokens.screenMargin, vertical = NexusTokens.space8),
                verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                // 1 · Cerebro
                AgentSection("Cerebro", Icons.Default.AutoAwesome) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                        listOf(
                            AssistantSettings.BACKEND_DIRECT to "Bedrock",
                            AssistantSettings.BACKEND_ANTHROPIC to "Claude API",
                            AssistantSettings.BACKEND_OPENAI to "Compatible OpenAI",
                            AssistantSettings.BACKEND_PROXY to "Proxy",
                            AssistantSettings.BACKEND_ANTIGRAVITY to "Antigravity",
                            AssistantSettings.BACKEND_CODEX to "Codex",
                        ).forEach { (value, label) ->
                            StudioFilterChip(backend == value, {
                                if (backend != value) { backend = value; apiKey = ""; clearApiKey = false
                                    // Se recupera el modelo guardado para el nuevo proveedor o un valor por defecto válido.
                                    modelId = vm.savedModelFor(value)
                                }
                            }, label = { Text(label) })
                        }
                        brains.forEach { b ->
                            StudioFilterChip(backend == BrainStore.backendFor(b.id), {
                                backend = BrainStore.backendFor(b.id); apiKey = ""; clearApiKey = false; modelId = b.modelId
                            }, label = { Text(b.name) })
                        }
                        AssistChip(onClick = { addingBrain = true }, label = { Text("+ Añadir") })
                    }
                    if (isCustom) {
                        val brain = brains.firstOrNull { BrainStore.backendFor(it.id) == backend }
                        if (brain == null) Text("Este proveedor ya no existe. Elige otro y guarda.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        else {
                            val hasKey = brainStore.hasKey(brain.id)
                            val ready = hasKey || brain.auth.mode == BrainAuthMode.NONE
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                                Column(Modifier.weight(1f)) {
                                    Text(brain.modelId.ifBlank { "sin modelo" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(when (brain.protocol) { BrainProtocol.OPENAI_RESPONSES -> "Responses API"; BrainProtocol.CUSTOM_REST -> "REST"; else -> "Chat Completions" },
                                        style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                }
                                Surface(shape = NexusTokens.filter, color = if (ready) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer) {
                                    Text(if (ready) "Clave lista" else "Falta la clave", Modifier.padding(horizontal = 10.dp, vertical = NexusTokens.space4), style = MaterialTheme.typography.labelMedium,
                                        color = if (ready) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                            if (!brain.capabilities.tools) Text("Sin herramientas: solo conversa, no prepara encargos.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            OutlinedButton(onClick = { editingBrain = brain }, shape = NexusTokens.field) { Text("Editar proveedor, clave y límites") }
                        }
                    } else if (backend == AssistantSettings.BACKEND_ANTHROPIC || backend == AssistantSettings.BACKEND_OPENAI) {
                        Text(if (backend == AssistantSettings.BACKEND_ANTHROPIC) "Claude con tu clave de la API de Anthropic (se guarda cifrada en el teléfono)."
                            else "Cualquier API compatible con OpenAI: elige un acceso rápido o escribe la URL. La clave se guarda cifrada y es distinta para cada proveedor.",
                            style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        if (backend == AssistantSettings.BACKEND_OPENAI) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                                AssistantSettings.OPENAI_PRESETS.forEach { (name, url) -> StudioFilterChip(baseUrl.trimEnd('/') == url, { baseUrl = url }, label = { Text(name) }) }
                            }
                            StudioTextField(baseUrl, { baseUrl = it.trim() }, shape = NexusTokens.field, label = { Text("URL de la API (https://…/v1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        StudioCredentialField(apiKey, { apiKey = it; clearApiKey = false }, shape = NexusTokens.field,
                            label = { Text(if (maskedKey.isNotBlank()) "Clave de API ($maskedKey)" else "Clave de API") }, singleLine = true)
                        if (maskedKey.isNotBlank()) OutlinedButton(onClick = { deleteCredential = "api" }) { Text(if (clearApiKey) "Se borrará al guardar" else "Borrar clave") }
                        if (backend == AssistantSettings.BACKEND_OPENAI) {
                            Text("Tarifa para el límite de consumo (opcional, USD por millón de tokens). Sin ella solo se limita el número de llamadas por mensaje.",
                                style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                                StudioTextField(inputRate, { inputRate = it }, shape = NexusTokens.field, label = { Text("Entrada") }, singleLine = true, modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                                StudioTextField(outputRate, { outputRate = it }, shape = NexusTokens.field, label = { Text("Salida") }, singleLine = true, modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            }
                        }
                    } else if (backend == AssistantSettings.BACKEND_ANTIGRAVITY || backend == AssistantSettings.BACKEND_CODEX) {
                        val codex = backend == AssistantSettings.BACKEND_CODEX
                        Text(if (codex) "Codex CLI por el mismo puente local de Termux (127.0.0.1:8788), con tu cuenta de Codex."
                            else "Conecta con el puente local de Antigravity CLI (AGY) en el dispositivo o por túnel (127.0.0.1:8788).",
                            style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        StudioTextField(agyEndpoint, { agyEndpoint = it.trim() }, shape = NexusTokens.field,
                            label = { Text(if (codex) "Endpoint del puente (http://127.0.0.1:8788)" else "Endpoint del puente AGY (http://127.0.0.1:8788)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                            Column(Modifier.weight(1f)) {
                                Text("Crear imágenes con su motor", style = MaterialTheme.typography.bodyLarge)
                                Text(if (codex) "GPT Image de Codex. Se guardan en la Biblioteca." else "Nano Banana de AGY: se le pide el de más calidad; si el servidor asigna Lite, la imagen llega igual. Se guardan en la Biblioteca.",
                                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                            Switch(nativeImages, { nativeImages = it })
                        }
                    } else if (backend != AssistantSettings.BACKEND_PROXY) {
                        Text("La app firma y llama a Bedrock con tus claves de AWS (guardadas cifradas en el teléfono).", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        StudioCredentialField(accessKey, { accessKey = it.trim() }, shape = NexusTokens.field, label = { Text("AWS Access Key ID") }, singleLine = true)
                        StudioCredentialField(secret, { secret = it; clearSecret = false }, shape = NexusTokens.field, label = { Text(if (s.maskedAwsSecret.isNotBlank()) "AWS Secret (${s.maskedAwsSecret})" else "AWS Secret Access Key") },
                            singleLine = true)
                        if (s.maskedAwsSecret.isNotBlank()) OutlinedButton(onClick = { deleteCredential = "secret" }) { Text(if (clearSecret) "Se borrará al guardar" else "Borrar clave") }
                        StudioTextField(region, { region = it }, shape = NexusTokens.field, label = { Text("Región (us-east-1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    } else {
                        Text("La app habla con tu proxy (APK → proxy → Bedrock); las claves de AWS se quedan en el proxy.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        StudioTextField(endpoint, { endpoint = it }, shape = NexusTokens.field, label = { Text("URL del proxy (https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        StudioCredentialField(token, { token = it; clearToken = false }, shape = NexusTokens.field, label = { Text(if (s.maskedToken.isNotBlank()) "Token del dispositivo (${s.maskedToken})" else "Token del dispositivo") },
                            singleLine = true)
                        if (s.maskedToken.isNotBlank()) OutlinedButton(onClick = { deleteCredential = "token" }) { Text(if (clearToken) "Se borrará al guardar" else "Borrar clave") }
                    }
                    if (!isCustom) StudioTextField(modelId, { modelId = it.trim() }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(when (backend) {
                            AssistantSettings.BACKEND_OPENAI -> "Modelo (id exacto en tu proveedor)"
                            AssistantSettings.BACKEND_ANTHROPIC -> "Modelo de Claude"
                            AssistantSettings.BACKEND_ANTIGRAVITY -> "Modelo de Antigravity (AGY)"
                            AssistantSettings.BACKEND_CODEX -> "Modelo de Codex"
                            else -> "Model ID / inference profile"
                        }) })
                    val modelChips = when (backend) {
                        AssistantSettings.BACKEND_ANTHROPIC -> AssistantSettings.ANTHROPIC_MODELS
                        AssistantSettings.BACKEND_ANTIGRAVITY -> AssistantSettings.ANTIGRAVITY_MODELS
                        AssistantSettings.BACKEND_CODEX -> AssistantSettings.CODEX_MODELS
                        AssistantSettings.BACKEND_OPENAI -> emptyList()
                        else -> AssistantSettings.KNOWN_MODELS
                    }
                    if (!isCustom && modelChips.isNotEmpty()) {
                        // Los modelos de generaciones anteriores no se borran: quedan plegados salvo que uno esté elegido.
                        val legacyModel = Regex("4-[56]|claude-.*4-6|3\\.[67]-flash|gpt-5\\.")
                        val (older, current) = modelChips.partition { legacyModel.containsMatchIn(it.first) }
                        var showOlder by remember(backend) { mutableStateOf(false) }
                        val selectedIsOlder = older.any { it.first == modelId }
                        @Composable fun chip(id: String, name: String) = StudioFilterChip((modelId == id || (backend == AssistantSettings.BACKEND_ANTIGRAVITY && AntigravityReasoning.family(id) != null && AntigravityReasoning.family(id) == AntigravityReasoning.family(modelId))), { modelId = AntigravityReasoning.switchTo(id, modelId).takeIf { backend == AssistantSettings.BACKEND_ANTIGRAVITY } ?: id }, label = { Text(name) })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                            current.forEach { (id, name) -> chip(id, name) }
                        }
                        if (older.isNotEmpty()) {
                            TextButton(onClick = { showOlder = !showOlder }, contentPadding = PaddingValues(0.dp)) {
                                Text(if (showOlder || selectedIsOlder) "Ocultar modelos anteriores" else "Modelos anteriores (${older.size})")
                            }
                            if (showOlder || selectedIsOlder) FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                                older.forEach { (id, name) -> chip(id, name) }
                            }
                        }
                    }
                    if(backend == AssistantSettings.BACKEND_ANTIGRAVITY && AntigravityReasoning.effort(modelId) != null) {
                        Text("Razonar",style=MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                            AntigravityReasoning.efforts(modelId).map { it to CODEX_EFFORT_NAMES.getValue(it) }.forEach { (effort,label) ->
                                StudioFilterChip(AntigravityReasoning.effort(modelId)==effort,{modelId=AntigravityReasoning.model(modelId,effort)},label={Text(label)})
                            }
                        }
                    }
                    if (backend == AssistantSettings.BACKEND_CODEX) {
                        Text("Razonar", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                            codexReasoningModes(modelId).forEach { mode ->
                                StudioFilterChip(CodexReasoning.effective(modelId, codexEffort) == mode.effort, { codexEffort = mode.effort }, label = { Text(mode.name) })
                            }
                        }
                    }
                    if (!isCustom && modelId.contains("-5-5")) Text("Requiere acceso a este modelo en tu cuenta AWS. Usa los parámetros de muestreo del modelo; conserva tus límites de consumo.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                // «Respuesta» y «Tu espacio de creación» se ocultan: conservan sus valores guardados, sin ensuciar los ajustes.
                // 3 · Consumo y límites
                AgentSection("Consumo y límites", Icons.Default.Speed) {
                    AgentBudgetSettings()
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AgentLimitsSettings()
                }
                // 4 · Herramientas
                // «Edición y Whisper» vive ahora en Ajustes → Sistema y diagnóstico.
                AgentSection("Herramientas", Icons.Default.Build) {
                    var autoReview by remember { mutableStateOf(vm.autoReviewEnabled()) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                        Column(Modifier.weight(1f)) {
                            Text("Revisar los resultados al terminar", style = MaterialTheme.typography.bodyLarge)
                            Text("Cuando acaban tus imágenes, el agente las mira y te dice si cumplen lo que pediste. Cada revisión es un turno más del modelo; nunca genera por su cuenta.",
                                style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        Switch(autoReview, { autoReview = it; vm.setAutoReview(it) })
                    }
                    AgentInternetSettings()
                }
                // 5 · Skills
                AgentSection("Skills", Icons.Default.Extension) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${ui.skillsCount} cargadas", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (ui.skillErrors.isNotEmpty()) Text("${ui.skillErrors.size} con errores", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                    if (ui.skillErrors.isNotEmpty()) InlineNotice("Algunas skills no se pudieron cargar.", technicalDetails = ui.skillErrors.joinToString("\n"))
                    Text("Se cargan de la app y, sin recompilar, de la carpeta de skills del teléfono (las de igual id sustituyen a las de la app).", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                        FilledTonalButton(onClick = { editingSkills = true }) { Text("Ver y editar") }
                        FilledTonalButton(onClick = vm::reloadSkills) { Text("Recargar skills") }
                        FilledTonalButton(onClick = { vm.reindexHistory() }) { Text("Indexar historial") }
                    }
                }
                error?.let { InlineNotice(noticeMessage(it, "Revisa los ajustes antes de guardar."), technicalDetails = it) }
            }
    }
    if (editingSkills) SkillsEditorDialog(vm, androidx.compose.ui.platform.LocalContext.current.applicationContext as com.vidadomu.nexus.NexusApplication) { editingSkills = false }
    deleteCredential?.let { target ->
        AlertDialog(onDismissRequest = { deleteCredential = null }, title = { Text("¿Borrar la clave guardada?") },
            text = { Text("Se eliminará al guardar los ajustes. Puedes cerrar sin guardar para conservarla.") },
            confirmButton = { Button(onClick = {
                when (target) { "api" -> { clearApiKey = true; apiKey = "" }; "secret" -> { clearSecret = true; secret = "" }; "token" -> { clearToken = true; token = "" } }
                deleteCredential = null
            }) { Text("Borrar clave") } },
            dismissButton = { TextButton(onClick = { deleteCredential = null }) { Text("Cancelar") } })
    }
    if (addingBrain || editingBrain != null) BrainEditorDialog(editingBrain, brainStore) { saved ->
        addingBrain = false; editingBrain = null; brainTick++
        // Un proveedor recién guardado queda elegido; hay que guardar los ajustes para activarlo.
        if (saved != null) brainStore.get(saved)?.let { backend = BrainStore.backendFor(it.id); modelId = it.modelId; apiKey = ""; clearApiKey = false }
    }
}

/** Los ajustes del asistente, abiertos desde Ajustes → Agente y modelos, con el mismo modelo de vista que el chat. */
@Composable
internal fun AssistantSettingsSheetHost(vm: AssistantViewModel, onDismiss: () -> Unit) {
    val ui by vm.ui.collectAsState()
    AssistantSettingsDialog(ui, vm, onDismiss)
}
