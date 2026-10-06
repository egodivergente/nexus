package com.vidadomu.nexus.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.currency.CurrencyManager
import com.vidadomu.nexus.core.model.AccountBalance
import com.vidadomu.nexus.core.model.AttachedMedia
import com.vidadomu.nexus.core.model.MediaSlotType
import com.vidadomu.nexus.core.model.ModelCapability
import com.vidadomu.nexus.core.model.ModelInputMode
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.core.model.ParameterType
import com.vidadomu.nexus.ui.components.BrandLogo
import com.vidadomu.nexus.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeParameterControl(param: com.vidadomu.nexus.core.model.ModelParameter, value: String, onChange: (String) -> Unit) {
    if(param.options.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(value = value.ifBlank { "Seleccionar" }, onValueChange = {}, readOnly = true,
                label = { Text(param.displayName + if(param.isRequired) " *" else "") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if(!param.isRequired) DropdownMenuItem(text = { Text("Automático") }, onClick = { onChange(""); expanded = false })
                param.options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false })
                }
            }
        }
    } else if(param.type == ParameterType.BOOLEAN) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(param.displayName, color = TextPrimary, modifier = Modifier.weight(1f))
            Switch(value == "true", { onChange(it.toString()) })
        }
    } else {
        OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(),
            label = { Text(param.displayName + if(param.isRequired) " *" else "") },
            supportingText = { if(param.minValue != null || param.maxValue != null) Text("Rango: " + (param.minValue ?: "—") + " – " + (param.maxValue ?: "—")) },
            shape = RoundedCornerShape(14.dp))
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    onNavigateToOutput: (String) -> Unit,
    onBrowseModels: () -> Unit,
    viewModel: CreateViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val currencyManager = app.currencyManager
    val haptic = LocalHapticFeedback.current

    var showModelPickerSheet by remember { mutableStateOf(false) }
    var showRequestInspector by remember { mutableStateOf(false) }
    var accountBalance by remember { mutableStateOf<AccountBalance?>(app.secureStorage.getCachedBalance()) }

    LaunchedEffect(Unit) {
        val res = app.atlasProvider.fetchBalance()
        if (res.isSuccess) {
            accountBalance = res.getOrNull()
        }
    }

    val primaryImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            viewModel.setPrimaryImage(uri, mime)
        }
    }

    val lastFrameLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            viewModel.setLastFrameImage(uri, mime)
        }
    }

    val multiImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = (state.selectedModel?.maxImages ?: 10).coerceAtLeast(2)
        )
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val items = uris.map { uri ->
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                uri to mime
            }
            viewModel.addReferenceImages(items)
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "video/mp4"
            viewModel.setVideoInput(uri, mime)
        }
    }

    var activeFileSlotType by remember { mutableStateOf(MediaSlotType.PRIMARY_IMAGE) }
    val universalFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            when (activeFileSlotType) {
                MediaSlotType.PRIMARY_IMAGE -> viewModel.setPrimaryImage(uri, mime)
                MediaSlotType.LAST_FRAME -> viewModel.setLastFrameImage(uri, mime)
                MediaSlotType.REFERENCE_IMAGE -> viewModel.addReferenceImages(listOf(uri to mime))
                MediaSlotType.VIDEO_INPUT -> viewModel.setVideoInput(uri, mime)
                MediaSlotType.VIDEO_REFERENCE -> viewModel.setVideoInput(uri, mime)
            }
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Lienzo",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = TextPrimary
                            )
                        )

                    }
                },
                actions = {
                    IconButton(onClick = { showRequestInspector = true }) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Inspector de Solicitud (DEBUG)",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    val balanceStr = currencyManager.formatBalance(accountBalance?.availableUsd ?: 0.0)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(AccentEmerald)
                            )
                            Text(
                                text = balanceStr,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 2. Tarjeta Compacta del Modelo Seleccionado (72-80dp)
            item {
                CompactModelSelectionCard(
                    selectedModel = state.selectedModel,
                    currencyManager = currencyManager,
                    onClick = onBrowseModels
                )
            }

            // 3. Preparación local y subida: nunca se muestra un adjunto hasta tener copia privada estable.
            if (state.isPreparingMedia || state.isUploading) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = PrimaryNeon.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryNeon.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryNeon
                            )
                            Text(
                                text = if (state.isPreparingMedia) {
                                    "Guardando copia privada del archivo..."
                                } else {
                                    "Subiendo multimedia a Atlas Cloud..."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(color = PrimaryNeon)
                            )
                        }
                    }
                }
            }

            // 4. Entrada de Medios Nativos Adaptativa al Modelo (1:1 según OpenAPI Schema)
            item {
                state.selectedModel?.takeIf { state.schemaReady }?.let { model ->
                    AdaptiveModelMediaInput(
                        model = model,
                        attachedMedia = state.attachedMedia,
                        isMediaRequired = viewModel.isMediaInputRequired(),
                        onPickPrimary = {
                            primaryImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onPickLastFrame = {
                            lastFrameLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onPickMultipleImages = {
                            multiImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onPickVideo = {
                            videoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onPickFile = { slot, mime ->
                            activeFileSlotType = slot
                            universalFileLauncher.launch(mime)
                        },
                        onSwitchToEdit = {
                            viewModel.switchToEditModel()
                        },
                        onRemoveMedia = { id -> viewModel.removeMedia(id) },
                        onClearSlot = { slot -> viewModel.clearSlot(slot) }
                    )
                }
            }

            // 5. Campo de Instrucción / Prompt (Dominante y limpio)
            item {
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = { viewModel.updatePrompt(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = if(state.selectedModality == Modality.CHAT) "Escribe tu consulta..." else "Describe lo que quieres cambiar o crear...",
                            color = TextMuted,
                            fontSize = 15.sp
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedBorderColor = PrimaryNeon.copy(alpha = 0.7f),
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = PrimaryNeon
                    )
                )
            }

            // 6. Carrusel de Presets Creativos (1 toque con haptic feedback)
            item {
                var showPresets by remember { mutableStateOf(false) }
                TextButton(onClick = { showPresets = !showPresets }) { Text("Inspiración creativa") }
                if(showPresets) CreativePresetsRow(
                    currentPrompt = state.prompt,
                    onApplyPreset = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.updatePrompt(it)
                    }
                )
            }

            item {
                if(state.isLoadingSchema) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = PrimaryNeon)
                    Text("Cargando opciones del modelo…", color = TextSecondary)
                }
                state.schemaMessage?.let { message ->
                    if(!state.schemaReady) Text(message, color = AccentAmber)
                    else {
                        var showCompatibility by remember(state.selectedModel?.id) { mutableStateOf(false) }
                        TextButton(onClick = { showCompatibility = !showCompatibility }) { Text("Ver avisos de compatibilidad", color = AccentAmber) }
                        if(showCompatibility) Text(message, color = TextSecondary, fontSize = 12.sp)
                    }
                }
                if(state.schemaReady) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.selectedModel?.parameters?.filter { !it.isAdvanced }?.forEach { param ->
                            NativeParameterControl(param, state.parameters[param.key] ?: param.defaultValue) { viewModel.updateParameter(param.key, it) }
                        }
                        val advanced = state.selectedModel?.parameters?.filter { it.isAdvanced }.orEmpty()
                        if(advanced.isNotEmpty()) {
                            TextButton(onClick = { viewModel.toggleAdvanced() }) {
                                Text(if(state.showAdvanced) "Ocultar opciones adicionales" else "Más opciones · " + advanced.size)
                            }
                            AnimatedVisibility(state.showAdvanced) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    advanced.forEach { param ->
                                        NativeParameterControl(param, state.parameters[param.key] ?: param.defaultValue) { viewModel.updateParameter(param.key, it) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Mensaje de Error si aplica
            if (state.errorMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentCoral.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentCoral)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentCoral, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = state.errorMessage ?: "", color = AccentCoral, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // 7. Botón de Acción Principal (Single-line CTA button)
            item {
                val dynamicPrice = "Coste final pendiente de confirmación por Atlas"
                val mediaRequired = viewModel.isMediaInputRequired()
                val isEnabled = state.schemaReady && !state.isLoadingSchema && !state.isSubmitting && !state.isUploading && !state.isPreparingMedia && !mediaRequired
                val ctaText = when {
                    state.isPreparingMedia -> "Preparando archivo..."
                    state.isUploading -> "Subiendo multimedia..."
                    state.isSubmitting -> "Iniciando..."
                    state.selectedModality == Modality.CHAT -> "Enviar consulta"
                    mediaRequired -> "Añade un archivo para continuar"
                    dynamicPrice.isNotBlank() -> "✦ Generar"
                    else -> "✦ Generar"
                }

                if(dynamicPrice.isNotBlank()) {
                    Text(dynamicPrice, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("No se calcula una estimación: el catálogo no siempre incluye duración, resolución, cantidad ni extras.", color = TextMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.submitGeneration { jobId ->
                            onNavigateToOutput(jobId)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = isEnabled,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryNeon,
                        contentColor = BackgroundDark,
                        disabledContainerColor = SurfaceVariantDark,
                        disabledContentColor = TextSecondary
                    )
                ) {
                    if (state.isSubmitting || state.isUploading || state.isPreparingMedia) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = BackgroundDark
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = ctaText,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Modal Bottom Sheet para Seleccionar Modelo
    if (showModelPickerSheet) {
        ModelPickerSheet(
            availableModels = state.availableModels,
            selectedModel = state.selectedModel,
            currencyManager = currencyManager,
            onSelectModel = { model ->
                viewModel.selectModel(model)
                showModelPickerSheet = false
            },
            onDismiss = { showModelPickerSheet = false }
        )
    }

    // Modal Bottom Sheet para Inspector de Solicitud (DEBUG)
    if (showRequestInspector) {
        RequestInspectorSheet(
            state = state,
            onDismiss = { showRequestInspector = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestInspectorSheet(
    state: CreateUiState,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val endpoint = when (state.selectedModality) {
        Modality.IMAGE -> "POST /api/v1/model/generateImage"
        Modality.VIDEO -> "POST /api/v1/model/generateVideo"
        Modality.AUDIO -> "POST /api/v1/model/generateAudio"
        Modality.CHAT -> "POST /v1/chat/completions"
    }

    val model = state.selectedModel
    val primary = state.attachedMedia.find { it.slotType == MediaSlotType.PRIMARY_IMAGE }
    val last = state.attachedMedia.find { it.slotType == MediaSlotType.LAST_FRAME }
    val refs = state.attachedMedia.filter { it.slotType == MediaSlotType.REFERENCE_IMAGE }
    val vid = state.attachedMedia.find { it.slotType == MediaSlotType.VIDEO_INPUT }

    val simulatedPayload = buildString {
        append("{\n")
        append("  \"model\": \"${model?.id ?: ""}\",\n")
        append("  \"prompt\": \"${state.prompt.replace("\"", "\\\"")}\"")
        if (state.negativePrompt.isNotBlank()) {
            append(",\n  \"negative_prompt\": \"${state.negativePrompt.replace("\"", "\\\"")}\"")
        }
        for ((k, v) in state.parameters) {
            append(",\n  \"$k\": \"$v\"")
        }
        if (model?.firstFrameField != null && primary != null) {
            append(",\n  \"${model.firstFrameField}\": \"https://storage.atlascloud.ai/uploads/first_${System.currentTimeMillis()}.jpg\"")
        } else if (primary != null && model?.imagesArrayField == null) {
            append(",\n  \"${model?.inputImageField ?: "image"}\": \"https://storage.atlascloud.ai/uploads/preview_${System.currentTimeMillis()}.jpg\"")
        }
        if (model?.lastFrameField != null && last != null) {
            append(",\n  \"${model.lastFrameField}\": \"https://storage.atlascloud.ai/uploads/last_${System.currentTimeMillis()}.jpg\"")
        }
        if (model?.imagesArrayField != null && (refs.isNotEmpty() || primary != null)) {
            val count = if (refs.isNotEmpty()) refs.size else 1
            val itemsStr = (1..count).joinToString(", ") { "\"https://storage.atlascloud.ai/uploads/ref_${it}.jpg\"" }
            append(",\n  \"${model.imagesArrayField}\": [ $itemsStr ]")
        }
        if (model?.videoField != null && vid != null) {
            append(",\n  \"${model.videoField}\": \"https://storage.atlascloud.ai/uploads/video_${System.currentTimeMillis()}.mp4\"")
        }
        append("\n}")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderSubtle) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = PrimaryNeon, modifier = Modifier.size(20.dp))
                    Text(
                        text = "INSPECTOR DE SOLICITUD (DEBUG)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            // Endpoint & Routing Info
            Surface(
                color = SurfaceVariantDark,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Endpoint", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(endpoint, style = MaterialTheme.typography.bodySmall, color = PrimaryNeon, fontFamily = FontFamily.Monospace)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Header Auth", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("Bearer •••••••••••• (Protegido)", style = MaterialTheme.typography.bodySmall, color = AccentEmerald, fontFamily = FontFamily.Monospace)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Modo de entrada", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(model?.inputMode?.name ?: "TEXT_ONLY", style = MaterialTheme.typography.bodySmall, color = AccentAmber, fontFamily = FontFamily.Monospace)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Media adjunta", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(
                            if (state.attachedMedia.isNotEmpty()) "${state.attachedMedia.size} adjunto(s) (${model?.inputMode?.name})" else "0 adjuntos (Texto puro)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.attachedMedia.isNotEmpty()) AccentEmerald else TextSecondary
                        )
                    }
                }
            }

            // Payload Preview
            Text(
                text = "PAYLOAD JSON COMPLETO",
                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            )
            Surface(
                color = BackgroundDark,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = simulatedPayload,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier.padding(12.dp)
                )
            }

            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(simulatedPayload))
                    Toast.makeText(context, "Payload copiado al portapapeles", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark, contentColor = PrimaryNeon)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copiar Payload JSON", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun ModalityTabsRow(
    selectedModality: Modality,
    onModalitySelected: (Modality) -> Unit
) {
    val modalities = listOf(
        Modality.IMAGE to "Imagen",
        Modality.VIDEO to "Vídeo",
        Modality.AUDIO to "Audio",
        Modality.CHAT to "Chat"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        modalities.forEach { (modality, label) ->
            val isSelected = modality == selectedModality
            val textColor by animateColorAsState(
                targetValue = if (isSelected) TextPrimary else TextMuted,
                label = "textColor"
            )

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onModalitySelected(modality) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = textColor
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (isSelected) PrimaryNeon else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun CompactModelSelectionCard(
    selectedModel: NexusModel?,
    currencyManager: CurrencyManager,
    onClick: () -> Unit
) {
    val formattedPrice = "Coste confirmado tras generar"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp, max = 80.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandLogo(
                organization = selectedModel?.organization,
                family = selectedModel?.family,
                modelId = selectedModel?.id,
                size = 54.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Modelo seleccionado",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp
                    )
                )
                Text(
                    text = selectedModel?.displayName ?: "Seleccionar Modelo",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = selectedModel?.let { com.vidadomu.nexus.ui.models.modelUse(it).label } ?: "Selecciona un modelo",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AccentEmerald,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AdaptiveModelMediaInput(
    model: NexusModel,
    attachedMedia: List<AttachedMedia>,
    isMediaRequired: Boolean,
    onPickPrimary: () -> Unit,
    onPickLastFrame: () -> Unit,
    onPickMultipleImages: () -> Unit,
    onPickVideo: () -> Unit,
    onPickFile: (MediaSlotType, String) -> Unit,
    onSwitchToEdit: () -> Unit,
    onRemoveMedia: (String) -> Unit,
    onClearSlot: (MediaSlotType) -> Unit
) {
    when (model.inputMode) {
        ModelInputMode.TEXT_ONLY -> {
            // Strictly respect model's native configuration: TEXT_ONLY accepts no attachments.
            // If paired edit variant exists, provide an explicit option to switch models.
            if (model.pairedEditModelId != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSwitchToEdit() },
                    color = SurfaceDark.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = PrimaryNeon.copy(alpha = 0.8f),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Este modelo crea desde texto",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Text(
                            text = "Usar una imagen",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = PrimaryNeon,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }

        ModelInputMode.SINGLE_IMAGE -> {
            val primary = attachedMedia.find { it.slotType == MediaSlotType.PRIMARY_IMAGE } ?: attachedMedia.firstOrNull()
            val isI2V = model.capabilities.contains(ModelCapability.IMAGE_TO_VIDEO)
            val title = if (isI2V) "Imagen inicial" else "Imagen de entrada"
            val placeholder = if (isI2V) "Añadir imagen para animar" else "Añadir imagen"

            SingleMediaSlotCard(
                title = title,
                badge = null,
                media = primary,
                placeholder = placeholder,
                isRequired = isMediaRequired && primary == null,
                icon = Icons.Default.AddPhotoAlternate,
                onClick = onPickPrimary,
                onPickFile = { onPickFile(MediaSlotType.PRIMARY_IMAGE, "image/*") },
                onRemove = { primary?.let { onRemoveMedia(it.id) } }
            )
        }

        ModelInputMode.DUAL_FRAME -> {
            val firstFrame = attachedMedia.find { it.slotType == MediaSlotType.PRIMARY_IMAGE } ?: attachedMedia.firstOrNull()
            val lastFrame = attachedMedia.find { it.slotType == MediaSlotType.LAST_FRAME } ?: attachedMedia.drop(1).firstOrNull()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Imágenes del clip",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "Final opcional",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = PrimaryNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        DualFrameSlotCard(
                            label = "Imagen inicial",
                            subLabel = if (firstFrame != null) "Listo" else "Necesaria",
                            badge = if (firstFrame == null) "Obligatorio" else null,
                            media = firstFrame,
                            isRequired = firstFrame == null,
                            onClick = onPickPrimary,
                            onRemove = { firstFrame?.let { onRemoveMedia(it.id) } }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        DualFrameSlotCard(
                            label = "Imagen final",
                            subLabel = if (lastFrame != null) "Listo" else "Opcional",
                            badge = if (lastFrame != null) "Interpolado" else "Opcional",
                            media = lastFrame,
                            isRequired = false,
                            onClick = onPickLastFrame,
                            onRemove = { lastFrame?.let { onRemoveMedia(it.id) } }
                        )
                    }
                }
            }
        }

        ModelInputMode.MULTI_IMAGE -> {
            val refImages = attachedMedia.filter { it.slotType in listOf(MediaSlotType.REFERENCE_IMAGE, MediaSlotType.PRIMARY_IMAGE) }
            val maxAllowed = model.maxImages

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Imágenes de referencia",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (refImages.isNotEmpty()) AccentEmerald.copy(alpha = 0.15f) else SurfaceVariantDark,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (refImages.isNotEmpty()) AccentEmerald.copy(alpha = 0.5f) else BorderSubtle
                        )
                    ) {
                        Text(
                            text = "${refImages.size} / $maxAllowed refs",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (refImages.isNotEmpty()) AccentEmerald else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(refImages) { img ->
                        ReferenceImageThumbnail(
                            media = img,
                            onRemove = { onRemoveMedia(img.id) }
                        )
                    }

                    if (refImages.size < maxAllowed) {
                        item {
                            AddReferenceButton(
                                remaining = maxAllowed - refImages.size,
                                isRequired = isMediaRequired && refImages.isEmpty(),
                                onClick = onPickMultipleImages
                            )
                        }
                    }
                }
            }
        }

        ModelInputMode.VIDEO_INPUT -> {
            val video = attachedMedia.find { it.slotType == MediaSlotType.VIDEO_INPUT } ?: attachedMedia.firstOrNull()
            SingleMediaSlotCard(
                title = "Vídeo de entrada",
                badge = null,
                media = video,
                placeholder = "Añadir vídeo",
                isRequired = video == null,
                icon = Icons.Default.Videocam,
                onClick = onPickVideo,
                onPickFile = { onPickFile(MediaSlotType.VIDEO_INPUT, "video/*") },
                onRemove = { video?.let { onRemoveMedia(it.id) } }
            )
        }

        ModelInputMode.MULTI_MODAL -> {
            val refImages = attachedMedia.filter { it.slotType in listOf(MediaSlotType.REFERENCE_IMAGE, MediaSlotType.PRIMARY_IMAGE) }
            val refVideos = attachedMedia.filter { it.slotType in listOf(MediaSlotType.VIDEO_REFERENCE, MediaSlotType.VIDEO_INPUT) }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Archivos de referencia",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "${refImages.size}/${model.maxImages} imgs • ${refVideos.size}/${model.maxVideos} vids",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = PrimaryNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(refImages) { img ->
                        ReferenceImageThumbnail(
                            media = img,
                            onRemove = { onRemoveMedia(img.id) }
                        )
                    }

                    if (refImages.size < model.maxImages) {
                        item {
                            AddReferenceButton(
                                remaining = model.maxImages - refImages.size,
                                isRequired = false,
                                onClick = onPickMultipleImages
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleMediaSlotCard(
    title: String,
    badge: String?,
    media: AttachedMedia?,
    placeholder: String,
    isRequired: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    onPickFile: (() -> Unit)? = null,
    onRemove: () -> Unit
) {
    val borderColor = when {
        media != null -> AccentEmerald.copy(alpha = 0.7f)
        isRequired -> BorderSubtle
        else -> BorderSubtle
    }
    val bgColor = when {
        media != null -> SurfaceDark
        isRequired -> SurfaceDark
        else -> SurfaceDark
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (media != null) {
                AsyncImage(
                    model = Uri.parse(media.uriString),
                    contentDescription = title,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentEmerald,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp
                            )
                        )
                    }
                    Text(
                        text = media.fileName ?: "Archivo adjunto cargado",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Toca para reemplazar",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Quitar",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVariantDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isRequired) TextSecondary else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp
                            )
                        )
                        if (badge != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = SurfaceVariantDark
                            ) {
                                Text(
                                    text = badge,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                    Text(
                        text = if(isRequired) placeholder + "\nNecesaria para este modelo" else placeholder,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isRequired) TextSecondary.copy(alpha = 0.85f) else TextMuted
                        )
                    )
                }
                if (onPickFile != null) {
                    IconButton(
                        onClick = onPickFile,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = "Elegir archivo",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = if (isRequired) TextSecondary else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DualFrameSlotCard(
    label: String,
    subLabel: String,
    badge: String?,
    media: AttachedMedia?,
    isRequired: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val borderColor = when {
        media != null -> AccentEmerald.copy(alpha = 0.7f)
        isRequired -> BorderSubtle
        else -> BorderSubtle
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (media != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = Uri.parse(media.uriString),
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    color = SurfaceDark.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentEmerald,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Quitar",
                            tint = Color.White,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 11.sp
                    )
                )
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isRequired) TextSecondary.copy(alpha = 0.7f) else TextMuted,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun ReferenceImageThumbnail(
    media: AttachedMedia,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
    ) {
        AsyncImage(
            model = Uri.parse(media.uriString),
            contentDescription = "Referencia",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.TopEnd)
                .padding(2.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Quitar",
                    tint = Color.White,
                    modifier = Modifier.padding(3.dp)
                )
            }
        }
    }
}

@Composable
private fun AddReferenceButton(
    remaining: Int,
    isRequired: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            TextSecondary.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Añadir",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Añadir",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            )
        }
    }
}

@Composable
private fun CompactChipSelector(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextMuted,
                fontSize = 11.sp
            ),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options) { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) PrimaryNeon.copy(alpha = 0.12f) else SurfaceDark)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) PrimaryNeon else BorderSubtle,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) PrimaryNeon else TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun VisualAspectRatioSelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column {
        Text(
            text = "RELACIÓN DE ASPECTO",
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                val isSelected = option == selected
                val (boxW, boxH) = when (option.trim()) {
                    "1:1" -> Pair(16.dp, 16.dp)
                    "9:16" -> Pair(11.dp, 20.dp)
                    "16:9" -> Pair(20.dp, 11.dp)
                    "4:3" -> Pair(18.dp, 13.5.dp)
                    "3:4" -> Pair(13.5.dp, 18.dp)
                    "21:9" -> Pair(22.dp, 9.5.dp)
                    else -> Pair(16.dp, 16.dp)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) PrimaryNeon.copy(alpha = 0.15f) else SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) PrimaryNeon else BorderSubtle
                    ),
                    modifier = Modifier.clickable { onSelect(option) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(boxW)
                                .height(boxH)
                                .border(
                                    1.2.dp,
                                    if (isSelected) PrimaryNeon else TextMuted,
                                    RoundedCornerShape(2.dp)
                                )
                                .background(
                                    if (isSelected) PrimaryNeon.copy(alpha = 0.35f) else Color.Transparent
                                )
                        )
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) PrimaryNeon else TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

private data class CreativePreset(val label: String, val icon: String, val promptSuffix: String)

@Composable
private fun CreativePresetsRow(
    currentPrompt: String,
    onApplyPreset: (String) -> Unit
) {
    val presets = remember {
        listOf(
            CreativePreset("Cine 8K", "🎬", "cinematic film still, 35mm lens, photorealistic raw, 8k resolution, volumetric dramatic lighting"),
            CreativePreset("Moda Editorial", "✨", "vogue editorial photoshoot, elegant studio lighting, soft shadows, high fashion aesthetics"),
            CreativePreset("Cyberpunk", "🌆", "cyberpunk neon aesthetic, rainy reflections, chromatic aberration, octane render, moody atmosphere"),
            CreativePreset("Anime Studio", "🎨", "makoto shinkai studio ghibli anime style, vibrant lush colors, exquisite hand-drawn details"),
            CreativePreset("Render 3D", "💎", "unreal engine 5, octane render, 3D raytracing, subsurface scattering, hyperdetailed"),
            CreativePreset("Minimal", "⚪", "minimalist composition, clean geometric background, balanced contrast, elegant studio simplicity")
        )
    }

    Column {
        Text(
            text = "ESTILOS CREATIVOS",
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(presets) { preset ->
                val isApplied = currentPrompt.contains(preset.promptSuffix.take(15), ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isApplied) SecondaryVioletLight.copy(alpha = 0.2f) else SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isApplied) SecondaryVioletLight else BorderSubtle
                    ),
                    modifier = Modifier.clickable {
                        if (isApplied) {
                            val newPrompt = currentPrompt.replace(preset.promptSuffix, "").replace(" , ", " ").trim(' ', ',')
                            onApplyPreset(newPrompt)
                        } else {
                            val separator = if (currentPrompt.isBlank()) "" else if (currentPrompt.trimEnd().endsWith(",")) " " else ", "
                            val newPrompt = currentPrompt.trimEnd() + separator + preset.promptSuffix
                            onApplyPreset(newPrompt)
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = preset.icon, fontSize = 11.sp)
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isApplied) FontWeight.Bold else FontWeight.Normal,
                                color = if (isApplied) SecondaryVioletLight else TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    availableModels: List<NexusModel>,
    selectedModel: NexusModel?,
    currencyManager: CurrencyManager,
    onSelectModel: (NexusModel) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedOrg by remember { mutableStateOf<String?>(null) }

    val filteredModels = remember(availableModels, searchQuery, selectedOrg) {
        var list = availableModels
        if (!selectedOrg.isNullOrBlank()) {
            list = list.filter { it.organization.equals(selectedOrg, ignoreCase = true) }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.displayName.lowercase().contains(q) ||
                it.id.lowercase().contains(q) ||
                it.organization.lowercase().contains(q) ||
                it.family?.lowercase()?.contains(q) == true
            }
        }
        list
    }

    val orgs = remember(availableModels) {
        availableModels.map { it.organization }.distinct().sorted()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Seleccionar Modelo",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "${filteredModels.size} disponibles",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = PrimaryNeon,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            // Search Bar (Slim 46dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                shape = RoundedCornerShape(8.dp),
                color = BackgroundDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar modelo...", color = TextMuted, fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = PrimaryNeon
                        )
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Provider Filter Chips
            if (orgs.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        val isAll = selectedOrg == null
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isAll) PrimaryNeon.copy(alpha = 0.15f) else SurfaceVariantDark)
                                .border(1.dp, if (isAll) PrimaryNeon else BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable { selectedOrg = null }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Todos",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isAll) PrimaryNeon else TextSecondary,
                                    fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                    items(orgs) { org ->
                        val isSelected = selectedOrg == org
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) PrimaryNeon.copy(alpha = 0.15f) else SurfaceVariantDark)
                                .border(1.dp, if (isSelected) PrimaryNeon else BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable { selectedOrg = if (isSelected) null else org }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = org,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSelected) PrimaryNeon else TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }

            // Models List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                items(filteredModels, key = { it.id }) { model ->
                    val isSelected = model.id == selectedModel?.id
                    val formattedPrice = currencyManager.formatModelPricing(
                        model.basePriceUsd,
                        model.priceUnit,
                        model.pricingInfo
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectModel(model) },
                        color = if (isSelected) SurfaceVariantDark else SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) PrimaryNeon.copy(alpha = 0.6f) else BorderSubtle
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BrandLogo(
                                organization = model.organization,
                                family = model.family,
                                modelId = model.id,
                                size = 18.dp,
                                containerSize = 34.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    model.badge?.let { badge ->
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = PrimaryNeon.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = badge,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = PrimaryNeon,
                                                    fontSize = 9.sp
                                                )
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${model.organization} • $formattedPrice",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = AccentEmerald,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = PrimaryNeon,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
