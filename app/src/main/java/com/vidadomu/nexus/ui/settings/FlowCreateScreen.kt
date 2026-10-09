package com.vidadomu.nexus.ui.settings

import com.vidadomu.nexus.ui.components.NexusTopAppBar as TopAppBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.GenerationRequest
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.work.GenerationPollingWorker
import com.vidadomu.nexus.provider.flow.FlowBridgeProvider
import com.vidadomu.nexus.provider.flow.FlowBridgeStatus
import com.vidadomu.nexus.ui.theme.AccentCoral
import com.vidadomu.nexus.ui.theme.AccentEmerald
import com.vidadomu.nexus.ui.theme.BackgroundDark
import com.vidadomu.nexus.ui.theme.BorderSubtle
import com.vidadomu.nexus.ui.theme.ScreenBackground
import com.vidadomu.nexus.ui.theme.SurfaceDark
import com.vidadomu.nexus.ui.theme.TextMuted
import com.vidadomu.nexus.ui.theme.TextPrimary
import java.util.UUID
import kotlinx.coroutines.launch

private sealed interface FlowConnectionState {
    data object Checking : FlowConnectionState
    data class Ready(val status: FlowBridgeStatus) : FlowConnectionState
    data class Unavailable(val message: String) : FlowConnectionState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowCreateScreen(
    onBack: () -> Unit,
    onNavigateToOutput: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var prompt by rememberSaveable { mutableStateOf("") }
    var expectedOutput by rememberSaveable { mutableStateOf(Modality.IMAGE) }
    var connection by remember { mutableStateOf<FlowConnectionState>(FlowConnectionState.Checking) }
    var showPromptValidation by rememberSaveable { mutableStateOf(false) }
    var pendingRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    suspend fun refreshConnection() {
        connection = FlowConnectionState.Checking
        val result = app.flowBridgeProvider.bridgeStatus()
        connection = result.fold(
            onSuccess = { status ->
                if (status.isReady) FlowConnectionState.Ready(status)
                else FlowConnectionState.Unavailable(
                    status.detail?.takeIf { it.isNotBlank() }
                        ?: "El proyecto Flow no está listo. Ábrelo desde Ajustes y vuelve a intentarlo."
                )
            },
            onFailure = { error ->
                FlowConnectionState.Unavailable(
                    error.message?.takeIf { it.isNotBlank() }
                        ?: "No se pudo comprobar el puente privado de Flow."
                )
            }
        )
    }

    LaunchedEffect(Unit) { refreshConnection() }

    val bridgeStatus = (connection as? FlowConnectionState.Ready)?.status
    val promptLimit = (bridgeStatus?.promptLimit ?: 4000).coerceIn(1, 4000)
    val acceptedOutputs = bridgeStatus?.expectedOutputs.orEmpty().map { it.lowercase() }.toSet()
    val imageAvailable = acceptedOutputs.isEmpty() || "image" in acceptedOutputs
    val videoAvailable = acceptedOutputs.isEmpty() || "video" in acceptedOutputs

    LaunchedEffect(acceptedOutputs) {
        if (expectedOutput == Modality.IMAGE && !imageAvailable && videoAvailable) {
            pendingRequestId = null
            expectedOutput = Modality.VIDEO
        } else if (expectedOutput == Modality.VIDEO && !videoAvailable && imageAvailable) {
            pendingRequestId = null
            expectedOutput = Modality.IMAGE
        }
    }

    fun submitFlow() {
                    scope.launch {
                        isSubmitting = true
                        val currentStatus = app.flowBridgeProvider.bridgeStatus()
                        val ready = currentStatus.getOrNull()?.isReady == true
                        if (!ready) {
                            isSubmitting = false
                            refreshConnection()
                            snackbarHost.showSnackbar(
                                currentStatus.exceptionOrNull()?.message
                                    ?: "Flow ya no está listo. Ábrelo desde Ajustes y vuelve a intentarlo."
                            )
                            return@launch
                        }

                        val requestId = pendingRequestId ?: UUID.randomUUID().toString().also {
                            pendingRequestId = it
                        }
                        try {
                        val request = GenerationRequest(
                            id = requestId,
                            providerId = FlowBridgeProvider.PROVIDER_ID,
                            modelId = FlowBridgeProvider.CURRENT_PROJECT_MODEL,
                            modality = expectedOutput,
                            prompt = com.vidadomu.nexus.agent.core.llm.PromptAdapter(
                                { com.vidadomu.nexus.assistant.AssistantRuntime.get(app).llm(com.vidadomu.nexus.assistant.AssistantRuntime.get(app).settings.load()) },
                                { com.vidadomu.nexus.assistant.AssistantRuntime.get(app).settings.load().modelId })
                                .adapt(prompt.trim(), com.vidadomu.nexus.agent.core.registry.TextLimit("utf16_code_units", maximum=4000), "Google Flow"),
                            parameters = mapOf(
                                FlowBridgeProvider.EXPECTED_OUTPUT_KEY to if (expectedOutput == Modality.IMAGE) "image" else "video"
                            )
                        )
                        val result = app.generationRepository.submitGeneration(request, mapOf("__assistant_original_prompt" to prompt))
                        isSubmitting = false
                        if (result.isSuccess) {
                            val job = result.getOrThrow()
                            if (job.status == GenerationStatus.FAILED) {
                                pendingRequestId = null
                                snackbarHost.showSnackbar(
                                    job.error?.takeIf { it.isNotBlank() }
                                        ?: "Flow no pudo iniciar la generación."
                                )
                            } else {
                                pendingRequestId = null
                                prompt = ""
                                if (job.status != GenerationStatus.COMPLETED) {
                                    GenerationPollingWorker.enqueue(context.applicationContext, job.id)
                                }
                                onNavigateToOutput(job.id)
                            }
                        } else {
                            val error = result.exceptionOrNull()
                            snackbarHost.showSnackbar(
                                error?.message?.takeIf { it.isNotBlank() }
                                    ?: "No se pudo confirmar el envío a Flow."
                            )
                        }
                        } catch(error: kotlinx.coroutines.CancellationException) {
                            isSubmitting = false
                            throw error
                        } catch(error: Exception) {
                            isSubmitting = false
                            snackbarHost.showSnackbar(error.message ?: "No se pudo preparar el envío. El original se conserva.")
                        }
                    }
    }

    val isReady = connection is FlowConnectionState.Ready
    val outputSupported = when (expectedOutput) {
        Modality.IMAGE -> imageAvailable
        Modality.VIDEO -> videoAvailable
        else -> false
    }
    val canSubmit = prompt.isNotBlank() && isReady && outputSupported && !isSubmitting
    fun requestConfirmation() {
        if (prompt.isBlank()) {
            showPromptValidation = true
        } else if (canSubmit) {
            focusManager.clearFocus(force = true)
            submitFlow()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ScreenBackground,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("Flow", style=MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { scope.launch { refreshConnection() } },
                        enabled = connection !is FlowConnectionState.Checking && !isSubmitting
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar estado de Flow")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        bottomBar = {
            Surface(
                color = BackgroundDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Button(
                    onClick = ::requestConfirmation,
                    enabled = canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Enviando a Flow…")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Generar en Flow")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 16.dp, vertical = 20.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FlowConnectionCard(connection = connection, onRefresh = {
                scope.launch { refreshConnection() }
            })

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Prompt", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                OutlinedTextField(
                    value = prompt,
                    onValueChange = {
                        val nextPrompt = it
                        if (nextPrompt != prompt) pendingRequestId = null
                        prompt = nextPrompt
                        if (nextPrompt.isNotBlank()) showPromptValidation = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.Enter &&
                                !event.isShiftPressed
                            ) {
                                requestConfirmation()
                                true
                            } else {
                                false
                            }
                        },
                    label = { Text("Describe lo que quieres crear") },
                    placeholder = { Text("Una escena, estilo, luz, movimiento…") },
                    minLines = 5,
                    maxLines = 10,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { requestConfirmation() }),
                    supportingText = {
                        Text("${prompt.length}/$promptLimit")
                    },
                    isError = showPromptValidation && prompt.isBlank()
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Salida esperada", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = expectedOutput == Modality.IMAGE,
                        onClick = {
                            if (expectedOutput != Modality.IMAGE) pendingRequestId = null
                            expectedOutput = Modality.IMAGE
                        },
                        enabled = imageAvailable,
                        label = { Text("Imagen") },
                        leadingIcon = {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                    FilterChip(
                        selected = expectedOutput == Modality.VIDEO,
                        onClick = {
                            if (expectedOutput != Modality.VIDEO) pendingRequestId = null
                            expectedOutput = Modality.VIDEO
                        },
                        enabled = videoAvailable,
                        label = { Text("Vídeo") },
                        leadingIcon = {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                }
                Text(
                    "El puente puede recuperar imagen o vídeo. Elige el tipo que coincida con los ajustes actuales del proyecto Flow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Ajustes y coste", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(
                        "NEXUS enviará solo este prompt. Flow usa el modelo, los ajustes y los créditos de su proyecto activo. " +
                            "Aquí no hay una estimación de coste verificable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    HorizontalDivider(color = BorderSubtle)
                    Text(
                        "Las referencias multimedia se añaden directamente en Flow por ahora.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }
    }


}

@Composable
private fun FlowConnectionCard(
    connection: FlowConnectionState,
    onRefresh: () -> Unit
) {
    val (title, detail, available) = when (connection) {
        FlowConnectionState.Checking -> Triple("Comprobando Flow", "Conectando con el puente privado…", null)
        is FlowConnectionState.Ready -> Triple(
            "Flow listo",
            connection.status.projectName?.takeIf { it.isNotBlank() }
                ?: connection.status.detail?.takeIf { it.isNotBlank() }
                ?: "Proyecto activo disponible.",
            true
        )
        is FlowConnectionState.Unavailable -> Triple("Flow no está listo", connection.message, false)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (available) {
                true -> AccentEmerald.copy(alpha = 0.7f)
                false -> AccentCoral.copy(alpha = 0.7f)
                null -> BorderSubtle
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (available) {
                true -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentEmerald)
                false -> Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentCoral)
                null -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (connection !is FlowConnectionState.Checking) {
                OutlinedButton(onClick = onRefresh, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reintentar", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
