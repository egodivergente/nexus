package com.vidadomu.nexus.ui.create

import androidx.compose.runtime.saveable.rememberSaveable

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
import androidx.compose.foundation.lazy.rememberLazyListState
import com.vidadomu.nexus.ui.components.rememberPriceEstimate
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.vidadomu.nexus.core.model.ContractMediaField
import com.vidadomu.nexus.core.model.AttachedMedia
import com.vidadomu.nexus.core.model.MediaSlotType
import com.vidadomu.nexus.core.model.ModelCapability
import com.vidadomu.nexus.core.model.ModelInputMode
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.NexusModel
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import com.vidadomu.nexus.core.model.ParameterType
import com.vidadomu.nexus.ui.components.BrandLogo
import com.vidadomu.nexus.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NativeParameterControl(param: com.vidadomu.nexus.core.model.ModelParameter, value: String, onChange: (String) -> Unit) {
    var showDefaults by remember(param.key) { mutableStateOf(false) }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().combinedClickable(onClick={showDefaults=true},onLongClick={showDefaults=true}).padding(vertical=8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Text(ParameterPresentation.title(param) + if(param.isRequired) " *" else "",Modifier.weight(1f),color=TextPrimary)
            Icon(Icons.Default.MoreVert,"Ajustes de ${param.displayName}",Modifier.size(20.dp),tint=TextSecondary)
        }
        NativeParameterValue(param,value,onChange)
    }
    if(showDefaults) AlertDialog(onDismissRequest={showDefaults=false},title={Text(ParameterPresentation.title(param))},text={Column {
        Text("Valor actual: ${ParameterPresentation.describe(param,value)}")
        Text("Predeterminado del modelo: ${ParameterPresentation.describe(param,param.defaultValue)}")
        if(param.minValue!=null || param.maxValue!=null) Text("Rango: ${param.minValue ?: "—"} – ${param.maxValue ?: "—"}")
    }},confirmButton={TextButton(onClick={onChange(param.defaultValue);showDefaults=false}) {Text("Restaurar predeterminado")}},
        dismissButton={TextButton(onClick={showDefaults=false}) {Text("Cerrar")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeParameterValue(param: com.vidadomu.nexus.core.model.ModelParameter, value: String, onChange: (String) -> Unit) {
    val choices = ParameterPresentation.options(param)
    if(choices.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            val label = ParameterPresentation.label(param,value)
            OutlinedTextField(value = if(value.isBlank()) "Seleccionar" else label.primary, onValueChange = {}, readOnly = true,
                supportingText = if(label.secondary == null) null else { { Text(label.secondary.orEmpty()) } },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if(!param.isRequired) DropdownMenuItem(text = { Text("Usar valor del proveedor") }, onClick = { onChange(""); expanded = false })
                choices.forEach { option ->
                    val optionLabel = ParameterPresentation.label(param,option)
                    DropdownMenuItem(text = { Column {
                        Text(optionLabel.primary)
                        optionLabel.secondary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } }, onClick = { onChange(option); expanded = false })
                }
            }
        }
    } else if(param.type == ParameterType.BOOLEAN) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if(value=="true") "Activado" else "Desactivado", color = TextPrimary, modifier = Modifier.weight(1f))
                Switch(value == "true", { onChange(it.toString()) })
            }
            if (!param.isRequired) TextButton(onClick = { onChange("") }) {
                Text(if(value.isBlank()) "Automático (sin enviar)" else "Usar valor del proveedor")
            }
        }
    } else {
        OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(),
            supportingText = { if(param.minValue != null || param.maxValue != null) Text("Rango: " + (param.minValue ?: "—") + " – " + (param.maxValue ?: "—")) },
            shape = RoundedCornerShape(14.dp))
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateScreen(
    onNavigateToOutput: (String) -> Unit,
    onBrowseModels: () -> Unit,
    viewModel: CreateViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val comparisonState by viewModel.comparison.state.collectAsState()
    var showComparison by rememberSaveable { mutableStateOf(false) }
    if(showComparison) ComparisonScreen(viewModel.comparison, { showComparison = false }) {
        showComparison = false
        onNavigateToOutput(it)
    }
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val currencyManager = app.currencyManager
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val horizontalActions = configuration.screenWidthDp >= 600
    val compactTyping = configuration.screenHeightDp < 500 && WindowInsets.isImeVisible
    val generateAction = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.submitGeneration(onNavigateToOutput) }
    val canvasScroll = rememberLazyListState()
    LaunchedEffect(state.selectedModel?.id) { canvasScroll.scrollToItem(0) }
    val inputVideo = state.attachedMedia.singleOrNull { it.mimeType.startsWith("video/") }
    var videoSeconds by remember(inputVideo?.uriString) { mutableStateOf<Double?>(null) }
    LaunchedEffect(inputVideo?.uriString) {
        videoSeconds = null
        val uri = inputVideo?.uriString ?: return@LaunchedEffect
        videoSeconds = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val parsed = Uri.parse(uri)
            // Metadata only from a local copy: never upload or probe a remote private URL for a price.
            if(parsed.scheme !in setOf("file", "content")) return@withContext null
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, parsed)
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toDoubleOrNull()?.div(1000.0)?.takeIf { it.isFinite() && it > 0 }
            } catch (_: Exception) { null }
            finally { retriever.release() }
        }
    }
    val estimate = rememberPriceEstimate(state.selectedModel,state.parameters,
        state.attachedMedia.count { it.mimeType.startsWith("image/") }
            .takeIf { it > 0 } ?: state.selectedModel?.takeIf { it.supportsImageInput }?.let { 1 },
        !state.isLoadingSchema, videoSeconds, state.prompt.length, state.prompt)


    var showPriceDetails by remember { mutableStateOf(false) }
    if(showPriceDetails) AlertDialog(onDismissRequest={showPriceDetails=false},title={Text(estimate.amount)},text={Text(estimate.details.ifBlank {"El coste depende de la configuración elegida."})},confirmButton={TextButton(onClick={showPriceDetails=false}) {Text("Cerrar")}})
    var showModelPickerSheet by remember { mutableStateOf(false) }
    var showRequestInspector by remember { mutableStateOf(false) }
    var showPersonalPresets by remember {mutableStateOf(false)}
    if(showPersonalPresets) CreationPresetsSheet(state,viewModel) {showPersonalPresets=false}
    val readiness=creationReadiness(if(comparisonState.phase == ComparisonPhase.SENDING) state.copy(isSubmitting = true) else state)
    val snackbar=remember {SnackbarHostState()}
    LaunchedEffect(state.errorMessage) {state.errorMessage?.let {snackbar.showSnackbar(it,withDismissAction=true,duration=SnackbarDuration.Long)}}
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
        contract = ActivityResultContracts.GetMultipleContents()
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

    var contractField by remember { mutableStateOf<ContractMediaField?>(null) }
    val contractPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val field=contractField
        if(uri!=null && field!=null) viewModel.setContractMedia(field,uri,context.contentResolver.getType(uri) ?: field.mime)
        contractField=null
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {SnackbarHost(snackbar)},
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BackgroundDark,
                shadowElevation = 8.dp
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    readiness.message?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(estimate.amount, Modifier.weight(1f), color = TextPrimary, fontSize = 14.sp)
                        if(estimate.details.isNotBlank()) IconButton(onClick={showPriceDetails=true}) {Icon(Icons.Default.Info,"Detalle del precio",Modifier.size(20.dp),tint=TextSecondary)}
                        estimate.retry?.let { retry -> IconButton(onClick=retry) {Icon(Icons.Default.Refresh,"Reintentar precio")} }
                        if(horizontalActions) GenerateActionButton(readiness.canSubmit,generateAction,Modifier.width(160.dp))
                    }
                    if(!horizontalActions) GenerateActionButton(readiness.canSubmit,generateAction,Modifier.fillMaxWidth())
                }
            }
        },
        containerColor = ScreenBackground,
        topBar = {
            if(!compactTyping) TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if(com.vidadomu.nexus.BuildConfig.OFFLINE_QA) "Lienzo · Lab" else "Lienzo",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.sp,
                                color = TextPrimary
                            )
                        )

                    }
                },
                actions = {
                    IconButton(onClick={showPersonalPresets=true},enabled=state.schemaReady && !state.isSubmitting) {Icon(Icons.Default.Tune,"Mis ajustes",tint=TextSecondary)}
                    if(com.vidadomu.nexus.BuildConfig.DEBUG && state.selectedModality != Modality.CHAT) IconButton(onClick = { showRequestInspector = true }) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Inspector de Solicitud (DEBUG)",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    val balanceStr = accountBalance?.availableUsd?.let {currencyManager.formatBalance(it)}
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
                                text = creationProviderLabel(state.selectedModel?.providerId, balanceStr),
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
            state = canvasScroll,
            contentPadding = PaddingValues(bottom = 20.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
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

            if(state.selectedModality == Modality.IMAGE) item {
                TextButton(onClick = {
                    viewModel.comparison.open(state)
                    showComparison = true
                }, enabled = !state.isSubmitting && !state.isPreparingMedia,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Compare, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if(comparisonState.phase == ComparisonPhase.SENDING) "Ver lote en curso" else "Encargar a varios modelos")
                }
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
                                    "Subiendo multimedia a ${state.selectedModel?.let { app.providerRegistry.getProviderById(it.providerId)?.displayName } ?: "proveedor"}..."
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
                    if(model.contractEndpoint==null && model.contractMedia.isNotEmpty()) {
                        ContractMediaEditor(model,state.attachedMedia,{field -> contractField=field;contractPicker.launch(field.mime)},viewModel::removeMedia)
                    }
                    if(model.contractEndpoint!=null) {
                        ContractMediaEditor(model,state.attachedMedia,{field -> contractField=field;contractPicker.launch(field.mime)},viewModel::removeMedia)
                    } else AdaptiveModelMediaInput(
                        model = model,
                        attachedMedia = state.attachedMedia.filter { it.fieldKey==null },
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
                            multiImageLauncher.launch("image/*")
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
                    if(!state.schemaReady) {
                        Text(message, color = AccentAmber)
                        TextButton(onClick = viewModel::retryConfiguration, enabled = !state.isLoadingSchema) {
                            Text("Reintentar carga de opciones")
                        }
                    }
                    else {
                        var showCompatibility by remember(state.selectedModel?.id) { mutableStateOf(false) }
                        TextButton(onClick = { showCompatibility = !showCompatibility }) { Text("Ver avisos de compatibilidad", color = AccentAmber) }
                        if(showCompatibility) Text(message, color = TextSecondary, fontSize = 12.sp)
                    }
                }
                if(state.schemaReady) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.selectedModel?.parameters?.let(com.vidadomu.nexus.core.model.FormContract::normalize)?.filter { !it.isAdvanced }?.forEach { param ->
                            key(state.selectedModel?.id,param.key) {
                                NativeParameterControl(param, state.parameters[param.key] ?: param.defaultValue) { viewModel.updateParameter(param.key, it) }
                            }
                        }
                        val advanced = state.selectedModel?.parameters?.let(com.vidadomu.nexus.core.model.FormContract::normalize)?.filter { it.isAdvanced }.orEmpty()
                        if(advanced.isNotEmpty()) {
                            TextButton(onClick = { viewModel.toggleAdvanced() }) {
                                Text(if(state.showAdvanced) "Ocultar opciones adicionales" else "Más opciones · " + advanced.size)
                            }
                            AnimatedVisibility(state.showAdvanced) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    advanced.forEach { param ->
                                        key(state.selectedModel?.id,param.key) {
                                        NativeParameterControl(param, state.parameters[param.key] ?: param.defaultValue) { viewModel.updateParameter(param.key, it) }
                                        }
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

    val model = state.selectedModel
    val endpoint = when (model?.providerId) {
        "crun" -> "POST https://api.crun.ai/api/v1/client/job/CreateTask"
        "runway" -> "POST " + (model.contractEndpoint ?: "/v1/image_to_video")
        else -> when (state.selectedModality) {
            Modality.IMAGE -> "POST /api/v1/model/generateImage"
            Modality.VIDEO -> "POST /api/v1/model/generateVideo"
            Modality.AUDIO -> "POST /api/v1/model/generateAudio"
            Modality.CHAT -> "POST /v1/chat/completions"
        }
    }
    val rawPreview = model?.let { selected ->
        val media = state.attachedMedia.map { item ->
            com.vidadomu.nexus.core.model.MediaInput(
                type = when {item.mimeType.startsWith("video/")->Modality.VIDEO;item.mimeType.startsWith("audio/")->Modality.AUDIO;else->Modality.IMAGE},
                uriString = "local://omitted", mimeType = item.mimeType, slotType = item.slotType,fieldKey=item.fieldKey,
                remoteUrl = "https://example.invalid/pending-upload/${item.slotType.name.lowercase()}")
        }
        runCatching {
            val previewRequest = com.vidadomu.nexus.provider.atlas.NativeGenerationRequest.build(
                selected, state.selectedModality, "[Prompt omitido: ${state.prompt.length} caracteres]", com.vidadomu.nexus.core.model.FormContract.previewValues(selected,state.parameters), media, "preview")
            when (selected.providerId) {
                "crun" -> com.vidadomu.nexus.provider.crun.CrunPayloadCodec.encode(selected, previewRequest).toString()
                "runway" -> com.vidadomu.nexus.provider.runway.RunwayPayloadCodec.encode(previewRequest).toString()
                else -> com.vidadomu.nexus.provider.atlas.AtlasGenerationPayload.build(previewRequest).toString()
            }
        }.getOrElse { "Revisa los valores: ${it.message}" }
    } ?: "Selecciona un modelo."

    val simulatedPayload=com.vidadomu.nexus.core.security.SecureStorage.sanitizeForLogging(rawPreview)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderSubtle) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max=560.dp).verticalScroll(rememberScrollState())
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
                        text = "Diagnóstico local",
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
                        Text(
                            if (model?.providerId == "crun") "x-api-key: ••••••••••••" else "Bearer ••••••••••••",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentEmerald,
                            fontFamily = FontFamily.Monospace
                        )
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
                text = "Payload sin prompt ni rutas privadas",
                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            )
            Surface(
                color = MaterialTheme.colorScheme.onPrimary,
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
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
    if (model.supportsImageInput || model.supportsVideoInput) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            fun required(field: String?) = field in model.nativeRequiredFields
            if (model.firstFrameField != null) {
                val media = attachedMedia.find { it.slotType == MediaSlotType.PRIMARY_IMAGE }
                SingleMediaSlotCard("Imagen de entrada", null, media, "Añadir imagen",
                    required(model.firstFrameField), Icons.Default.Image, onPickPrimary,
                    { onPickFile(MediaSlotType.PRIMARY_IMAGE, "image/*") }, { media?.let { onRemoveMedia(it.id) } })
            }
            if (model.lastFrameField != null) {
                val media = attachedMedia.find { it.slotType == MediaSlotType.LAST_FRAME }
                SingleMediaSlotCard("Último fotograma", null, media, "Añadir imagen",
                    required(model.lastFrameField), Icons.Default.Image, onPickLastFrame,
                    { onPickFile(MediaSlotType.LAST_FRAME, "image/*") }, { media?.let { onRemoveMedia(it.id) } })
            }
            if (model.imagesArrayField != null) {
                val refs = attachedMedia.filter { it.slotType == MediaSlotType.REFERENCE_IMAGE }
                Text("Imágenes de referencia", color = TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(refs, key = { it.id }) { media -> ReferenceImageThumbnail(media) { onRemoveMedia(media.id) } }
                    if (refs.size < model.maxImages) item {
                        AddReferenceButton(model.maxImages - refs.size, required(model.imagesArrayField), onPickMultipleImages)
                    }
                }
            }
            if (model.videoField != null || model.videosArrayField != null) {
                val videos = attachedMedia.filter { it.slotType in listOf(MediaSlotType.VIDEO_INPUT, MediaSlotType.VIDEO_REFERENCE) }
                videos.forEach { media ->
                    SingleMediaSlotCard("Vídeo de entrada", null, media, "Añadir vídeo", false,
                        Icons.Default.Videocam, onPickVideo, null, { onRemoveMedia(media.id) })
                }
                if (videos.isEmpty() || model.videosArrayField != null && videos.size < model.maxVideos) {
                    SingleMediaSlotCard("Vídeo de entrada", null, null, "Añadir vídeo",
                        required(model.videoField) || required(model.videosArrayField), Icons.Default.Videocam,
                        onPickVideo, { onPickFile(MediaSlotType.VIDEO_INPUT, "video/*") }, {})
                }
            }
        }
        return
    }
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
                            text = if(maxAllowed==Int.MAX_VALUE) "${refImages.size} imágenes" else "${refImages.size} / $maxAllowed imágenes",
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
                    items(refImages,key={it.id}) { img ->
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
                        text = if(model.maxImages==Int.MAX_VALUE) "${refImages.size} imágenes" else "${refImages.size}/${model.maxImages} imágenes",
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
                    items(refImages,key={it.id}) { img ->
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
                if(media.mimeType.startsWith("image/")) AsyncImage(
                    model = Uri.parse(media.uriString), contentDescription = title,
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)),contentScale = ContentScale.Crop)
                else Box(Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceVariantDark),contentAlignment=Alignment.Center) {
                    Icon(if(media.mimeType.startsWith("audio/")) Icons.Default.GraphicEq else Icons.Default.Movie,title,tint=TextSecondary)
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
                    MediaFactsLabel(media)
                    Text(
                        text = "Toca para reemplazar",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
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
                        .background(MediaScrim.copy(alpha = 0.35f))
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
                        color = MediaScrim.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Quitar",
                            tint = MediaForeground,
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
                color = MediaScrim.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Quitar",
                    tint = MediaForeground,
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
                        val nextPrompt = if (isApplied) {
                            currentPrompt.replace(preset.promptSuffix, "").replace(" , ", " ").trim(' ', ',')
                        } else {
                            val separator = when {
                                currentPrompt.isBlank() -> ""
                                currentPrompt.trimEnd().endsWith(",") -> " "
                                else -> ", "
                            }
                            currentPrompt.trimEnd() + separator + preset.promptSuffix
                        }
                        onApplyPreset(nextPrompt)
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
                color = MaterialTheme.colorScheme.onPrimary,
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
                    val formattedPrice = rememberPriceEstimate(model).amount

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

@Composable
private fun ContractMediaEditor(model:NexusModel,media:List<AttachedMedia>,onPick:(ContractMediaField)->Unit,onRemove:(String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        model.contractMedia.forEach { field ->
            val selected=media.filter { it.fieldKey==field.key }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(field.label+(if(field.required) " · obligatorio" else " · opcional"),style=MaterialTheme.typography.titleSmall)
                    selected.forEachIndexed { index,item ->
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text("Archivo ${index+1} preparado",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                            IconButton(onClick={onRemove(item.id)}) {Icon(Icons.Default.Close,"Quitar ${field.label} ${index+1}")}
                        }
                    }
                    if(selected.size<field.maximum || field.maximum==1) OutlinedButton(onClick={onPick(field)},modifier=Modifier.heightIn(min=48.dp)) {
                        Text(if(selected.isNotEmpty() && field.maximum==1) "Cambiar archivo" else "Añadir archivo")
                    }
                    if(field.maximum>1) Text("${selected.size} de ${field.maximum} archivos",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun MediaFactsLabel(media:AttachedMedia) {
    val context=LocalContext.current
    var facts by remember(media.uriString) {mutableStateOf<String?>(null)}
    LaunchedEffect(media.uriString) {facts=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {LocalMediaInspector.inspect(context,Uri.parse(media.uriString),media.mimeType).summary}.getOrNull()
    }}
    facts?.let {Text(it,color=TextSecondary,fontSize=11.sp)}
}

@Composable
private fun GenerateActionButton(enabled:Boolean,onClick:()->Unit,modifier:Modifier=Modifier) {
    Button(onClick=onClick,enabled=enabled,modifier=modifier.height(52.dp),shape=RoundedCornerShape(10.dp),
        colors=ButtonDefaults.buttonColors(containerColor=PrimaryNeon,contentColor=MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor=SurfaceVariantDark,disabledContentColor=TextSecondary)) {Text("Generar",fontWeight=FontWeight.Bold)}
}
