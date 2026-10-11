package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.ui.components.NexusTopAppBar as TopAppBar
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
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.components.noticeMessage
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.foundation.horizontalScroll
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
    Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
        Row(Modifier.fillMaxWidth().combinedClickable(onClick={showDefaults=true},onLongClick={showDefaults=true}).padding(vertical=NexusTokens.space8),
            verticalAlignment=Alignment.CenterVertically) {
            Text(ParameterPresentation.title(param) + if(param.isRequired) " *" else "",Modifier.weight(1f),color=TextPrimary)
            IconButton(onClick={onChange(param.defaultValue)}) { Icon(Icons.Default.Restore,"Restaurar ${ParameterPresentation.title(param)}",tint=TextSecondary) }
            IconButton(onClick={showDefaults=true}) { Icon(Icons.Default.MoreVert,"Ajustes de ${param.displayName}",tint=TextSecondary) }
        }
        NativeParameterValue(param,value,onChange)
    }
    if(showDefaults) AlertDialog(onDismissRequest={showDefaults=false},title={Text(ParameterPresentation.title(param))},text={Column {
        Text("Valor actual: ${ParameterPresentation.describe(param,value)}")
        Text("Predeterminado del modelo: ${ParameterPresentation.describe(param,param.defaultValue)}")
        if(param.minValue!=null || param.maxValue!=null) Text("Rango: ${param.minValue ?: "—"} – ${param.maxValue ?: "—"}")
    }},confirmButton={TextButton(onClick={onChange(param.defaultValue);showDefaults=false}, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {Text("Restaurar predeterminado")}},
        dismissButton={TextButton(onClick={showDefaults=false}, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {Text("Cerrar")}})
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
                modifier = Modifier.menuAnchor().fillMaxWidth(), shape = NexusTokens.field)
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if(!param.isRequired) DropdownMenuItem(text = { Text("Usar valor del proveedor") }, onClick = { onChange(""); expanded = false })
                choices.forEach { option ->
                    val optionLabel = ParameterPresentation.label(param,option)
                    DropdownMenuItem(text = { Column {
                        Text(optionLabel.primary)
                        optionLabel.secondary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
            if (!param.isRequired) TextButton(onClick = { onChange("") }, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {
                Text(if(value.isBlank()) "Automático (sin enviar)" else "Usar valor del proveedor")
            }
        }
    } else {
        OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(),
            supportingText = { if(param.minValue != null || param.maxValue != null) Text("Rango: " + (param.minValue ?: "—") + " – " + (param.maxValue ?: "—")) },
            shape = NexusTokens.field)
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
    var showModelPickerSheet by remember { mutableStateOf(false) }
    var showRequestInspector by remember { mutableStateOf(false) }
    var showPersonalPresets by remember { mutableStateOf(false) }
    var showParameters by remember { mutableStateOf(false) }
    var showMedia by remember { mutableStateOf(false) }
    var showInspiration by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    if(showPersonalPresets) CreationPresetsSheet(state,viewModel) { showPersonalPresets=false }
    val readiness=creationReadiness(if(comparisonState.phase == ComparisonPhase.SENDING) state.copy(isSubmitting = true) else state)
    val screenMargin = if(horizontalActions) NexusTokens.tabletMargin else NexusTokens.screenMargin

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
        bottomBar = {
            Surface(color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(horizontal=screenMargin, vertical=NexusTokens.space8),
                    verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                        TextButton(onClick={showPriceDetails=true}, modifier=Modifier.weight(1f).heightIn(min=NexusTokens.minimumTouch), shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(estimate.amount, style=MaterialTheme.typography.bodyMedium.tabularMoney(), color=TextPrimary)
                                Text("Coste estimado · Detalles", style=MaterialTheme.typography.labelMedium, color=TextSecondary)
                            }
                        }
                        GenerateActionButton(readiness.canSubmit,generateAction,Modifier.widthIn(min=128.dp))
                    }
                    readiness.message?.let { Text(it, color=TextSecondary, style=MaterialTheme.typography.labelMedium) }
                }
            }
        },
        containerColor=ScreenBackground,
        topBar={
            if(!compactTyping) TopAppBar(title={Text("Lienzo", style=com.vidadomu.nexus.ui.components.nexusScreenTitleStyle())},
                actions={
                    Box {
                        IconButton(onClick={showMenu=true}) { Icon(Icons.Default.MoreVert, "Más opciones del Lienzo", tint=TextSecondary) }
                        DropdownMenu(expanded=showMenu,onDismissRequest={showMenu=false}) {
                            DropdownMenuItem(text={Text("Todos los modelos")}, onClick={showMenu=false;onBrowseModels()})
                            if(com.vidadomu.nexus.BuildConfig.DEBUG && state.selectedModality != Modality.CHAT) {
                                DropdownMenuItem(text={Text("Diagnóstico local")}, onClick={showMenu=false;showRequestInspector=true})
                                DropdownMenuItem(text={Text("Copiar diagnóstico")}, onClick={
                                    showMenu=false
                                    clipboard.setText(AnnotatedString(maskedRequestPreview(state)))
                                    Toast.makeText(context,"Diagnóstico copiado",Toast.LENGTH_SHORT).show()
                                })
                            }
                        }
                    }
                }, colors=TopAppBarDefaults.topAppBarColors(containerColor=BackgroundDark))
        }
    ) { innerPadding ->
        LazyColumn(state=canvasScroll, contentPadding=PaddingValues(bottom=NexusTokens.space24),
            modifier=Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding).padding(horizontal=screenMargin),
            verticalArrangement=Arrangement.spacedBy(NexusTokens.space16)) {
            item { CompactModelSelectionCard(state.selectedModel,currencyManager) { showModelPickerSheet=true } }
            item {
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = { viewModel.updatePrompt(it) },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Prompt de creación" },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = {
                        Text(
                            text = if(state.selectedModality == Modality.CHAT) "Escribe tu consulta..." else "Describe lo que quieres cambiar o crear...",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                    shape = NexusTokens.field,
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

            // 3. Preparación local y subida: nunca se muestra un adjunto hasta tener copia privada estable.
            if (state.isPreparingMedia || state.isUploading) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = NexusTokens.thumbnail,
                        color = PrimaryNeon.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryNeon.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space8),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = TextSecondary
                            )
                            Text(
                                text = if (state.isPreparingMedia) {
                                    "Guardando copia privada del archivo..."
                                } else {
                                    "Subiendo multimedia a ${state.selectedModel?.let { app.providerRegistry.getProviderById(it.providerId)?.displayName } ?: "proveedor"}..."
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                            )
                        }
                    }
                }
            }


            state.selectedModel?.takeIf { state.schemaReady }?.let { model ->
                if(model.contractMedia.isNotEmpty() || model.supportsImageInput || model.supportsVideoInput ||
                    model.inputMode != ModelInputMode.TEXT_ONLY || model.pairedEditModelId != null || state.attachedMedia.isNotEmpty()) {
                    item {
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8), verticalAlignment=Alignment.CenterVertically) {
                            items(state.attachedMedia, key={it.id}) { media ->
                                CanvasMediaThumbnail(media) { viewModel.removeMedia(media.id) }
                            }
                            item {
                                OutlinedButton(onClick={showMedia=true}, modifier=Modifier.heightIn(min=NexusTokens.minimumTouch), shape=NexusTokens.field,
                                    colors=ButtonDefaults.outlinedButtonColors(contentColor=TextSecondary)) {
                                    Icon(Icons.Default.Add,null,Modifier.size(NexusTokens.space24))
                                    Text(if(state.attachedMedia.isEmpty()) "Medios" else "Añadir")
                                }
                            }
                        }
                    }
                }
            }
            item {
                val actions: @Composable () -> Unit = {
                    CanvasChip("Inspirar") {showInspiration=true}
                    CanvasChip(parameterSummary(state), "Parámetros del modelo") {showParameters=true}
                    if(state.selectedModality == Modality.IMAGE) {
                        CanvasChip(if(comparisonState.phase == ComparisonPhase.SENDING) "Ver lote" else "Comparar",
                            enabled=!state.isSubmitting && !state.isPreparingMedia) {
                            viewModel.comparison.open(state);showComparison=true
                        }
                    }
                    CanvasChip("Mis ajustes",enabled=state.schemaReady && !state.isSubmitting) {showPersonalPresets=true}
                }
                if(androidx.compose.ui.platform.LocalDensity.current.fontScale>=1.3f) {
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8),
                        verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {actions()}
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {actions()}
                }
            }
            if(state.isLoadingSchema) item {
                Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Cargando opciones del modelo…",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
                }
            }
            if(!state.schemaReady) state.schemaMessage?.let { raw -> item {
                InlineNotice(noticeMessage(raw,"No se han podido cargar las opciones del modelo."),technicalDetails=raw,
                    actionLabel="Reintentar",onAction=if(state.isLoadingSchema) null else viewModel::retryConfiguration)
            } }
            state.errorMessage?.let { raw -> item {
                InlineNotice(noticeMessage(raw,"No se ha podido completar la solicitud. Revisa sus detalles antes de volver a generar."),technicalDetails=raw)
            } }
        }
    }
    if(showMedia) CanvasSheet("Medios de entrada",{showMedia=false}) {
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
    if(showParameters) CanvasSheet("Parámetros",{showParameters=false}) {
        CreativePresetsRow(state.prompt) {viewModel.updatePrompt(it)}
        if(state.isLoadingSchema) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.schemaMessage?.let { raw ->
            InlineNotice(if(state.schemaReady) "Revisa la compatibilidad de estas opciones." else noticeMessage(raw,"Las opciones aún no están disponibles."),
                technicalDetails=raw,isError=!state.schemaReady,
                actionLabel=if(state.schemaReady) null else "Reintentar",onAction=if(!state.schemaReady && !state.isLoadingSchema) viewModel::retryConfiguration else null)
        }
        if(state.schemaReady) {
            val params=state.selectedModel?.parameters?.let(com.vidadomu.nexus.core.model.FormContract::normalize).orEmpty()
            params.filter { !it.isAdvanced }.forEach { param ->
                key(state.selectedModel?.id,param.key) { NativeParameterControl(param,state.parameters[param.key] ?: param.defaultValue) {viewModel.updateParameter(param.key,it)} }
            }
            if(params.any {it.isAdvanced}) Text("Opciones adicionales",style=MaterialTheme.typography.titleMedium)
            params.filter {it.isAdvanced}.forEach { param ->
                key(state.selectedModel?.id,param.key) { NativeParameterControl(param,state.parameters[param.key] ?: param.defaultValue) {viewModel.updateParameter(param.key,it)} }
            }
        }
    }
    if(showInspiration) CanvasSheet("Inspiración",{showInspiration=false}) {
        CreativePresetsRow(state.prompt) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);viewModel.updatePrompt(it) }
    }
    if(showPriceDetails) CanvasSheet("Coste estimado",{showPriceDetails=false}) {
        Text(estimate.amount,style=MaterialTheme.typography.titleMedium.tabularMoney())
        Text(estimate.details.ifBlank {"El coste depende de la configuración elegida."},style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
        estimate.retry?.let { retry -> TextButton(onClick=retry, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {Text("Reintentar precio",color=TextSecondary)} }
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

private fun maskedRequestPreview(state: CreateUiState): String {
    val model=state.selectedModel
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

    return com.vidadomu.nexus.core.security.SecureStorage.sanitizeForLogging(rawPreview)
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
    val simulatedPayload=maskedRequestPreview(state)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderSubtle) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max=560.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = NexusTokens.screenMargin)
                .padding(bottom = NexusTokens.space32),
            verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Diagnóstico local",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(NexusTokens.minimumTouch)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            // Endpoint & Routing Info
            Surface(
                color = SurfaceVariantDark,
                shape = NexusTokens.thumbnail,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(NexusTokens.space12), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Endpoint", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        Text(endpoint, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontFamily = FontFamily.Monospace)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Header Auth", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        Text(
                            if (model?.providerId == "crun") "x-api-key: ••••••••••••" else "Bearer ••••••••••••",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Modo de entrada", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        Text(model?.inputMode?.name ?: "TEXT_ONLY", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontFamily = FontFamily.Monospace)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Media adjunta", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        Text(
                            if (state.attachedMedia.isNotEmpty()) "${state.attachedMedia.size} adjunto(s) (${model?.inputMode?.name})" else "0 adjuntos (Texto puro)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.attachedMedia.isNotEmpty()) AccentEmerald else TextSecondary
                        )
                    }
                }
            }

            // Payload Preview
            Text(
                text = "Payload sin prompt ni rutas privadas",
                style = MaterialTheme.typography.labelMedium.copy(color = TextMuted, fontSize = MaterialTheme.typography.labelMedium.fontSize, letterSpacing = 1.sp)
            )
            Surface(
                color = MaterialTheme.colorScheme.onPrimary,
                shape = NexusTokens.thumbnail,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = simulatedPayload,
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.labelMedium.fontSize),
                    modifier = Modifier.padding(NexusTokens.space12)
                )
            }

            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(simulatedPayload))
                    Toast.makeText(context, "Payload copiado al portapapeles", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min=NexusTokens.minimumTouch),
                shape = NexusTokens.thumbnail,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark, contentColor = PrimaryNeon)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(NexusTokens.space8))
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
            .padding(vertical = NexusTokens.space4),
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
                    .clip(NexusTokens.thumbnail)
                    .clickable { onModalitySelected(modality) }
                    .padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space4),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = textColor
                    )
                )
                Spacer(modifier = Modifier.height(NexusTokens.space4))
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(2.dp)
                        .clip(NexusTokens.thumbnail)
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
    Surface(Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch).semantics {contentDescription="Elegir modelo"}.clickable(onClick=onClick),
        color=MaterialTheme.colorScheme.surface, shape=NexusTokens.field) {
        Row(Modifier.padding(vertical=NexusTokens.space8),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            BrandLogo(organization=selectedModel?.organization,family=selectedModel?.family,modelId=selectedModel?.id,size=NexusTokens.space32)
            Column(Modifier.weight(1f)) {
                Text(selectedModel?.displayName ?: "Seleccionar modelo",style=MaterialTheme.typography.titleMedium,color=TextPrimary)
                Text(selectedModel?.let {com.vidadomu.nexus.ui.models.modelUse(it).label} ?: "Elige qué quieres crear",
                    style=MaterialTheme.typography.labelMedium,color=TextSecondary)
            }
            Icon(Icons.Default.ExpandMore,null,tint=TextSecondary)
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
        Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
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
                        .clip(NexusTokens.field)
                        .clickable { onSwitchToEdit() },
                    color = SurfaceDark.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space8),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.8f),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Este modelo crea desde texto",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextMuted,
                                    fontSize = MaterialTheme.typography.labelMedium.fontSize
                                )
                            )
                        }
                        Text(
                            text = "Usar una imagen",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = MaterialTheme.typography.labelMedium.fontSize
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

            Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Imágenes del clip",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextMuted,
                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "Final opcional",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextSecondary,
                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)
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

            Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Imágenes de referencia",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextMuted,
                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Surface(
                        shape = NexusTokens.thumbnail,
                        color = if (refImages.isNotEmpty()) AccentEmerald.copy(alpha = 0.15f) else SurfaceVariantDark,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (refImages.isNotEmpty()) AccentEmerald.copy(alpha = 0.5f) else BorderSubtle
                        )
                    ) {
                        Text(
                            text = if(maxAllowed==Int.MAX_VALUE) "${refImages.size} imágenes" else "${refImages.size} / $maxAllowed imágenes",
                            modifier = Modifier.padding(horizontal = NexusTokens.space8, vertical = NexusTokens.space4),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (refImages.isNotEmpty()) AccentEmerald else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.labelMedium.fontSize
                            )
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8),
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

            Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Archivos de referencia",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextMuted,
                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = if(model.maxImages==Int.MAX_VALUE) "${refImages.size} imágenes" else "${refImages.size}/${model.maxImages} imágenes",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextSecondary,
                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8),
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
            .clip(NexusTokens.field)
            .clickable { onClick() },
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        shape = NexusTokens.field
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NexusTokens.space8),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (media != null) {
                if(media.mimeType.startsWith("image/")) AsyncImage(
                    model = Uri.parse(media.uriString), contentDescription = title,
                    modifier = Modifier.size(54.dp).clip(NexusTokens.thumbnail),contentScale = ContentScale.Crop)
                else Box(Modifier.size(54.dp).clip(NexusTokens.thumbnail).background(SurfaceVariantDark),contentAlignment=Alignment.Center) {
                    Icon(if(media.mimeType.startsWith("audio/")) Icons.Default.GraphicEq else Icons.Default.Movie,title,tint=TextSecondary)
                }
                Spacer(modifier = Modifier.width(NexusTokens.space12))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NexusTokens.space4)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                letterSpacing = 0.8.sp
                            )
                        )
                    }
                    Text(
                        text = media.fileName ?: "Archivo adjunto cargado",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    MediaFactsLabel(media)
                    Text(
                        text = "Toca para reemplazar",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextMuted,
                            fontSize = MaterialTheme.typography.labelMedium.fontSize
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
                        .clip(NexusTokens.thumbnail)
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
                Spacer(modifier = Modifier.width(NexusTokens.space12))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NexusTokens.space4)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (isRequired) TextSecondary else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                letterSpacing = 0.8.sp
                            )
                        )
                        if (badge != null) {
                            Surface(
                                shape = NexusTokens.thumbnail,
                                color = SurfaceVariantDark
                            ) {
                                Text(
                                    text = badge,
                                    modifier = Modifier.padding(horizontal = NexusTokens.space4, vertical = NexusTokens.space4),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = TextSecondary,
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                    Text(
                        text = if(isRequired) placeholder + "\nNecesaria para este modelo" else placeholder,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isRequired) TextSecondary else TextMuted
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
            .clip(NexusTokens.field)
            .clickable { onClick() },
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        shape = NexusTokens.field
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
                    shape = NexusTokens.thumbnail,
                    color = SurfaceDark.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = NexusTokens.space4, vertical = NexusTokens.space4),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = MaterialTheme.typography.labelMedium.fontSize
                        )
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(NexusTokens.minimumTouch)
                        .align(Alignment.TopEnd)
                        .padding(NexusTokens.space4)
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
                            modifier = Modifier.padding(NexusTokens.space4)
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(NexusTokens.space8),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(NexusTokens.space4))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = MaterialTheme.typography.labelMedium.fontSize
                    )
                )
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (isRequired) TextSecondary else TextMuted,
                        fontSize = MaterialTheme.typography.labelMedium.fontSize
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
            .clip(NexusTokens.field)
            .border(1.dp, BorderSubtle, NexusTokens.field)
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
                .size(NexusTokens.minimumTouch)
                .align(Alignment.TopEnd)
                .padding(NexusTokens.space4)
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
                    modifier = Modifier.padding(NexusTokens.space4)
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
            .clip(NexusTokens.field)
            .clickable { onClick() },
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            TextSecondary.copy(alpha = 0.4f)
        ),
        shape = NexusTokens.field
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
            Spacer(modifier = Modifier.height(NexusTokens.space4))
            Text(
                text = "Añadir",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    fontSize = MaterialTheme.typography.labelMedium.fontSize
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
            style = MaterialTheme.typography.labelMedium.copy(
                color = TextMuted,
                fontSize = MaterialTheme.typography.labelMedium.fontSize
            ),
            modifier = Modifier.padding(bottom = NexusTokens.space4)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
            items(options) { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .clip(NexusTokens.thumbnail)
                        .background(if (isSelected) PrimaryNeon.copy(alpha = 0.12f) else SurfaceDark)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) PrimaryNeon else BorderSubtle,
                            shape = NexusTokens.thumbnail
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = NexusTokens.space8, vertical = NexusTokens.space4)
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) PrimaryNeon else TextSecondary,
                            fontSize = MaterialTheme.typography.labelMedium.fontSize
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
            style = MaterialTheme.typography.labelMedium.copy(
                color = TextMuted,
                fontSize = MaterialTheme.typography.labelMedium.fontSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(bottom = NexusTokens.space4)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
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
                    shape = NexusTokens.thumbnail,
                    color = if (isSelected) PrimaryNeon.copy(alpha = 0.15f) else SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) PrimaryNeon else BorderSubtle
                    ),
                    modifier = Modifier.clickable { onSelect(option) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = NexusTokens.space8, vertical = NexusTokens.space8),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(boxW)
                                .height(boxH)
                                .border(
                                    1.2.dp,
                                    if (isSelected) PrimaryNeon else TextMuted,
                                    NexusTokens.thumbnail
                                )
                                .background(
                                    if (isSelected) PrimaryNeon.copy(alpha = 0.35f) else Color.Transparent
                                )
                        )
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) PrimaryNeon else TextSecondary,
                                fontSize = MaterialTheme.typography.labelMedium.fontSize
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
            text = "Estilos creativos",
            style = MaterialTheme.typography.labelMedium.copy(
                color = TextMuted,
                fontSize = MaterialTheme.typography.labelMedium.fontSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(bottom = NexusTokens.space4)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
            items(presets) { preset ->
                val isApplied = currentPrompt.contains(preset.promptSuffix.take(15), ignoreCase = true)
                Surface(
                    shape = NexusTokens.card,
                    color = if (isApplied) SecondaryVioletLight.copy(alpha = 0.2f) else SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isApplied) SecondaryVioletLight else BorderSubtle
                    ),
                    modifier = Modifier.heightIn(min=NexusTokens.minimumTouch).clickable {
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
                        modifier = Modifier.padding(horizontal = NexusTokens.space8, vertical = NexusTokens.space4),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NexusTokens.space4)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(NexusTokens.space24), tint=if(isApplied) PrimaryNeon else TextSecondary)
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isApplied) FontWeight.Bold else FontWeight.Normal,
                                color = if (isApplied) SecondaryVioletLight else TextSecondary,
                                fontSize = MaterialTheme.typography.labelMedium.fontSize
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
        shape = NexusTokens.sheet
    ) {
        NexusSheetSystemBars()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NexusTokens.screenMargin)
                .padding(bottom = NexusTokens.space24)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = NexusTokens.space8),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Seleccionar modelo",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "${filteredModels.size} disponibles",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            // Search Bar (Slim 46dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = NexusTokens.space8),
                shape = NexusTokens.thumbnail,
                color = MaterialTheme.colorScheme.onPrimary,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NexusTokens.space8, vertical = NexusTokens.space4),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(NexusTokens.space8))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar modelo...", color = TextMuted, style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = PrimaryNeon
                        )
                    , shape=NexusTokens.field)
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
                        .padding(bottom = NexusTokens.space8),
                    horizontalArrangement = Arrangement.spacedBy(NexusTokens.space4)
                ) {
                    item {
                        val isAll = selectedOrg == null
                        Box(
                            modifier = Modifier
                                .clip(NexusTokens.thumbnail)
                                .background(if (isAll) PrimaryNeon.copy(alpha = 0.15f) else SurfaceVariantDark)
                                .border(1.dp, if (isAll) PrimaryNeon else BorderSubtle, NexusTokens.thumbnail)
                                .clickable { selectedOrg = null }
                                .padding(horizontal = NexusTokens.space8, vertical = NexusTokens.space4)
                        ) {
                            Text(
                                text = "Todos",
                                style = MaterialTheme.typography.labelMedium.copy(
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
                                .clip(NexusTokens.thumbnail)
                                .background(if (isSelected) PrimaryNeon.copy(alpha = 0.15f) else SurfaceVariantDark)
                                .border(1.dp, if (isSelected) PrimaryNeon else BorderSubtle, NexusTokens.thumbnail)
                                .clickable { selectedOrg = if (isSelected) null else org }
                                .padding(horizontal = NexusTokens.space8, vertical = NexusTokens.space4)
                        ) {
                            Text(
                                text = org,
                                style = MaterialTheme.typography.labelMedium.copy(
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
                verticalArrangement = Arrangement.spacedBy(NexusTokens.space4),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                items(filteredModels, key = { it.id }) { model ->
                    val isSelected = model.id == selectedModel?.id
                    val formattedPrice = rememberPriceEstimate(model).amount

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(NexusTokens.field)
                            .clickable { onSelectModel(model) },
                        color = if (isSelected) SurfaceVariantDark else SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) PrimaryNeon.copy(alpha = 0.6f) else BorderSubtle
                        ),
                        shape = NexusTokens.field
                    ) {
                        Row(
                            modifier = Modifier.padding(NexusTokens.space8),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BrandLogo(
                                organization = model.organization,
                                family = model.family,
                                modelId = model.id,
                                size = 18.dp,
                                containerSize = 34.dp
                            )
                            Spacer(modifier = Modifier.width(NexusTokens.space8))
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
                                        Spacer(modifier = Modifier.width(NexusTokens.space4))
                                        Surface(
                                            shape = NexusTokens.thumbnail,
                                            color = PrimaryNeon.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = badge,
                                                modifier = Modifier.padding(horizontal = NexusTokens.space4, vertical = NexusTokens.space4),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    color = TextSecondary,
                                                    fontSize = MaterialTheme.typography.labelMedium.fontSize
                                                )
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${model.organization} • $formattedPrice",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = TextSecondary,
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize
                                    )
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = TextSecondary,
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
    Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
        model.contractMedia.forEach { field ->
            val selected=media.filter { it.fieldKey==field.key }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(NexusTokens.space16),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    Text(field.label+(if(field.required) " · obligatorio" else " · opcional"),style=MaterialTheme.typography.titleMedium)
                    selected.forEachIndexed { index,item ->
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text("Archivo ${index+1} preparado",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                            IconButton(onClick={onRemove(item.id)}) {Icon(Icons.Default.Close,"Quitar ${field.label} ${index+1}")}
                        }
                    }
                    if(selected.size<field.maximum || field.maximum==1) OutlinedButton(onClick={onPick(field)},modifier=Modifier.heightIn(min=48.dp), shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {
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
    facts?.let {Text(it,color=TextSecondary,style = MaterialTheme.typography.labelMedium)}
}

@Composable
private fun GenerateActionButton(enabled:Boolean,onClick:()->Unit,modifier:Modifier=Modifier) {
    Button(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=NexusTokens.minimumTouch),shape=NexusTokens.field,
        colors=ButtonDefaults.buttonColors(containerColor=PrimaryNeon,contentColor=MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor=SurfaceVariantDark,disabledContentColor=TextSecondary)) {Text("Generar",fontWeight=FontWeight.Bold)}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CanvasSheet(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        shape=NexusTokens.sheet,containerColor=MaterialTheme.colorScheme.surface) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().heightIn(max=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * .88f).verticalScroll(rememberScrollState())
            .padding(horizontal=NexusTokens.screenMargin).padding(bottom=NexusTokens.space24),
            verticalArrangement=Arrangement.spacedBy(NexusTokens.space16)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                IconButton(onClick=onDismiss) {Icon(Icons.Default.Close,"Cerrar $title")}
            }
            content()
        }
    }
}

@Composable
private fun CanvasChip(label:String,description:String=label,enabled:Boolean=true,onClick:()->Unit) {
    OutlinedButton(onClick=onClick,enabled=enabled,shape=NexusTokens.field,
        modifier=Modifier.heightIn(min=NexusTokens.minimumTouch).semantics {contentDescription=description},
        contentPadding=PaddingValues(horizontal=NexusTokens.space12,vertical=NexusTokens.space8),
        colors=ButtonDefaults.outlinedButtonColors(contentColor=TextSecondary)) {
        Text(label,style=MaterialTheme.typography.labelMedium)
    }
}

private fun parameterSummary(state:CreateUiState):String {
    val values=listOf("duration","resolution","aspect_ratio").mapNotNull { key ->
        state.parameters[key]?.takeIf {it.isNotBlank()}?.let {if(key=="duration") "$it s" else it}
    }
    return values.joinToString(" · ").ifBlank {"Parámetros"}
}

@Composable
private fun CanvasMediaThumbnail(media:AttachedMedia,onRemove:()->Unit) {
    Box(Modifier.size(80.dp).clip(NexusTokens.thumbnail).background(MaterialTheme.colorScheme.surfaceContainer)) {
        if(media.mimeType.startsWith("image/")) AsyncImage(Uri.parse(media.uriString),"Medio adjunto",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Icon(if(media.mimeType.startsWith("audio/")) Icons.Default.GraphicEq else Icons.Default.Movie,
            "Medio adjunto",Modifier.align(Alignment.Center),tint=TextSecondary)
        IconButton(onClick=onRemove,modifier=Modifier.align(Alignment.TopEnd)) {
            Surface(color=MediaScrim.copy(alpha=.75f),shape=NexusTokens.thumbnail) {
                Icon(Icons.Default.Close,"Quitar medio adjunto",Modifier.padding(NexusTokens.space4),tint=MediaForeground)
            }
        }
    }
}
