package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.ui.components.NexusTopAppBar as TopAppBar
import androidx.compose.runtime.saveable.rememberSaveable

import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.components.noticeMessage
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.ParameterDependencies
import com.vidadomu.nexus.core.model.NexusModel
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.vidadomu.nexus.ui.components.StudioChrome
import com.vidadomu.nexus.ui.components.StudioToolChip
import com.vidadomu.nexus.ui.theme.*
import com.vidadomu.nexus.ui.create.capability.ModelCapabilityRegistry

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateScreen(
    onNavigateToOutput: (String) -> Unit,
    onBrowseModels: () -> Unit,
    viewModel: CreateViewModel = viewModel(),
    onOpenFlow: () -> Unit = {},
    onBytePlusSettings: (String) -> Unit = {}
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
    val compactTyping = WindowInsets.isImeVisible
    val canvasScroll = rememberLazyListState()
    val promptFocus = remember { FocusRequester() }
    var promptFocused by remember {mutableStateOf(false)}
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
    val effectiveConfiguration=remember(state.selectedModel,state.selectedModality,state.parameters,state.prompt,state.schemaReady,state.attachedMedia) {
        state.capabilityValidation()?.configuration
    }
    val effectivePrompt=effectiveConfiguration?.requestPrompt.orEmpty()
    val estimate = rememberPriceEstimate(state.selectedModel,effectiveConfiguration?.parameters ?: state.parameters,
        state.attachedMedia.count { it.mimeType.startsWith("image/") }
            .takeIf { it > 0 } ?: state.selectedModel?.takeIf { it.supportsImageInput }?.let { 1 },
        !state.isLoadingSchema, videoSeconds, effectivePrompt.length, effectivePrompt)
    // Captured with this composition's estimate so the ViewModel can detect a quote shown for an older configuration.
    val costQuote = generationCostQuote(state, estimate)
    val countParam = if(state.selectedModality in setOf(Modality.IMAGE,Modality.VIDEO) && state.selectedModel?.promptAccepted == true)
        nativeCountParameter(state.selectedModel) else null
    var batchCount by rememberSaveable(state.selectedModel?.providerId,state.selectedModel?.id) { mutableIntStateOf(1) }
    var pendingBatch by remember { mutableStateOf<Pair<Int, GenerationFingerprint>?>(null) }
    val quantity=canvasQuantity(countParam,state.parameters,batchCount)
    val repeatedBatch = quantity.repeated && state.selectedModality in setOf(Modality.IMAGE,Modality.VIDEO)
    val generateAction: () -> Unit = {
        if(repeatedBatch) pendingBatch = batchCount to generationFingerprint(state)
        else viewModel.requestGeneration(costQuote, onNavigateToOutput)
    }
    val pendingCost by viewModel.pendingCostAcceptance.collectAsState()


    var showPriceDetails by remember { mutableStateOf(false) }
    var showModelPickerSheet by remember { mutableStateOf(false) }
    var showBytePlus by remember {mutableStateOf(false)}
    var bytePlusOnly by remember {mutableStateOf(false)}
    var pickerModality by remember {mutableStateOf<Modality?>(null)}
    var showRequestInspector by remember { mutableStateOf(false) }
    var showPersonalPresets by remember { mutableStateOf(false) }
    var showParameters by remember { mutableStateOf(false) }
    var showToolMenu by remember { mutableStateOf(false) }
    val catalogModels by app.modelCatalogRepository.models.collectAsState()
    // A model change preserves the prompt and all files; incompatible media stays in Conservados.
    val chooseModel: (NexusModel) -> Unit = { model ->
        if(model.id != state.selectedModel?.id || model.providerId != state.selectedModel?.providerId) {
            viewModel.selectModel(model)
        }
    }
    var showInspiration by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showBatch by remember { mutableStateOf(false) }
    val batchSnackbar = remember { SnackbarHostState() }
    val batchScope = rememberCoroutineScope()
    val switchNotice by viewModel.modelSwitchNotice.collectAsState()
    LaunchedEffect(switchNotice?.id) {
        val notice = switchNotice ?: return@LaunchedEffect
        batchSnackbar.currentSnackbarData?.dismiss()
        if (batchSnackbar.showSnackbar(notice.message, actionLabel = "Volver", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed)
            viewModel.undoModelSwitch()
    }
    if(showBatch) PromptBatchSheet(state, estimate.amount, estimate.usd, viewModel, { showBatch=false }) { summary ->
        showBatch=false
        batchScope.launch { batchSnackbar.showSnackbar(summary) }
    }
    val clipboard = LocalClipboardManager.current
    if(showPersonalPresets) CreationPresetsSheet(state,viewModel) { showPersonalPresets=false }
    val readiness=creationReadiness(if(comparisonState.phase == ComparisonPhase.SENDING) state.copy(isSubmitting = true) else state)
    val screenMargin = if(horizontalActions) NexusTokens.tabletMargin else NexusTokens.screenMargin

    val capabilities=remember(state.selectedModel,state.selectedModality,state.parameters,state.schemaReady,state.attachedMedia) {
        state.selectedModel?.let {ModelCapabilityRegistry.resolve(it,state.selectedModality,state.parameters,state.schemaReady,state.attachedMedia)}
    }
    val inputFields=capabilities?.inputFields.orEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
    // The parent already reserves the IME. Use the remaining height, including large text, rather than a device/orientation name.
    val compactEditor=compactTyping && maxHeight < 480.dp*LocalDensity.current.fontScale.coerceAtLeast(1f)
    LaunchedEffect(compactEditor,promptFocused) {
        if(compactEditor && promptFocused) {
            withFrameNanos { }
            canvasScroll.scrollToItem(2)
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(batchSnackbar) },
        bottomBar = {
            com.vidadomu.nexus.ui.components.FloatingActionDock {
                Column(Modifier.fillMaxWidth().windowInsetsPadding(com.vidadomu.nexus.ui.theme.NexusInsets.bottomEdge)
                    .padding(horizontal=screenMargin, vertical=NexusTokens.space4),
                    verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    val busy = state.isSubmitting || state.isUploading || state.isPreparingMedia || comparisonState.phase == ComparisonPhase.SENDING
                    val inlineReason=readiness.fixTarget==CreationFixTarget.MEDIA || readiness.fixTarget==CreationFixTarget.PROMPT
                    if(!compactEditor && !busy && !inlineReason) readiness.message?.let { reason ->
                        if(readiness.fixTarget==null) Text(reason, color=MaterialTheme.colorScheme.onSurface,
                            style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(horizontal=NexusTokens.space12))
                        else TextButton(onClick={
                            when(readiness.fixTarget) {
                                CreationFixTarget.MODEL -> showModelPickerSheet=true
                                CreationFixTarget.PROMPT -> batchScope.launch {canvasScroll.animateScrollToItem(2);promptFocus.requestFocus()}
                                CreationFixTarget.MEDIA -> batchScope.launch { canvasScroll.animateScrollToItem(3) }
                                CreationFixTarget.PARAMETERS -> showParameters=true
                                CreationFixTarget.RETRY_CONFIGURATION -> viewModel.retryConfiguration()
                                null -> Unit
                            }
                        }, modifier=Modifier.fillMaxWidth()) {
                            Text(reason, color=MaterialTheme.colorScheme.onSurface,
                                style=MaterialTheme.typography.bodyMedium, modifier=Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                    val totalCost=canvasQuantityCost(estimate,quantity,currencyManager::formatUsd)
                    GenerateActionBar(if(isKnownCost(estimate)) totalCost else "Por confirmar", readiness.canSubmit, busy,
                        if(comparisonState.phase == ComparisonPhase.SENDING) "Enviando lote…" else readiness.message ?: "Generando…",
                        generateAction, actionLabel=when(state.selectedModality) {
                            Modality.VIDEO -> if(state.selectedModel?.promptAccepted==false) "Procesar vídeo" else if(quantity.count>1) "Generar ${quantity.count} vídeos" else "Generar vídeo"
                            Modality.IMAGE -> if(state.selectedModel?.promptAccepted==false) "Procesar imagen" else if(quantity.count>1) "Generar ${quantity.count} imágenes" else "Generar imagen"
                            Modality.AUDIO -> "Generar audio"
                            Modality.CHAT -> "Enviar consulta"
                            else -> "Procesar"
                        },disabledLabel=if(inlineReason && !compactEditor) readiness.message else null,onCostDetails={showPriceDetails=true})
                }
            }
        },
        containerColor=ScreenBackground,
        topBar={}
    ) { innerPadding ->
        // El contenido pasa por debajo del botón flotante y se desvanece; no termina en un corte recto.
        LazyColumn(state=canvasScroll, contentPadding=PaddingValues(bottom=innerPadding.calculateBottomPadding()+NexusTokens.space8),
            modifier=Modifier.fillMaxSize().padding(top=innerPadding.calculateTopPadding()).consumeWindowInsets(innerPadding).padding(horizontal=screenMargin),
            verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
            item(key="launcher-title") {
                if(!compactEditor) Row(Modifier.fillMaxWidth().statusBarsPadding(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Lanzadera",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f))
                    com.vidadomu.nexus.ui.components.NexusSettingsButton()

                    Box {
                        IconButton(onClick={showMenu=true}) { Icon(Icons.Default.MoreVert, "Más opciones del Lienzo", tint=TextSecondary) }
                        DropdownMenu(expanded=showMenu,onDismissRequest={showMenu=false}) {
                            DropdownMenuItem(text={Text("Todos los modelos")}, onClick={showMenu=false;onBrowseModels()})
                            if(state.selectedModality != Modality.CHAT)
                                DropdownMenuItem(text={Text("Lote desde archivo…")}, onClick={showMenu=false;showBatch=true})
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

                }
            }
            item(key="studio-header") {
                if(!compactEditor) FlagshipStudioHeader(state.selectedModality, catalogModels, state.selectedModel,
                    viewModel::selectModality, chooseModel, { showModelPickerSheet=true }, onOpenFlow)
            }
            item(key="composer") {
                Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    val selected=state.selectedModel
                    val textRoute=selected?.let {current ->
                        if(current.pairedEditModelId!=null) current else catalogModels.firstOrNull {
                            it.providerId==current.providerId && it.pairedEditModelId==current.id
                        }
                    }
                    val editRoute=textRoute?.pairedEditModelId?.let {id -> catalogModels.firstOrNull {it.id==id && it.providerId==textRoute.providerId}}
                    if(!compactEditor && state.selectedModality==Modality.IMAGE && textRoute!=null && editRoute!=null) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(selected=selected?.id==textRoute.id,onClick={chooseModel(textRoute)},shape=SegmentedButtonDefaults.itemShape(0,2)) {Text("Desde texto")}
                        SegmentedButton(selected=selected?.id==editRoute.id,onClick={chooseModel(editRoute)},shape=SegmentedButtonDefaults.itemShape(1,2)) {Text("Con referencias")}
                    }
                    if(capabilities?.promptVisible==true) PromptEditor(state.prompt,viewModel::updatePrompt,
                        when(state.selectedModality) {Modality.VIDEO -> "Describe el movimiento, la acción y la cámara…";Modality.CHAT -> "Escribe tu consulta…";else -> "Describe lo que quieres crear o transformar…"},
                        focusRequester=promptFocus,enabled=!state.isSubmitting,
                        capability=state.selectedModel?.let(com.vidadomu.nexus.settings.ModelCapabilities::prompt),
                        ruleScope=com.vidadomu.nexus.agent.core.config.RuleScope(provider=state.selectedModel?.providerId,
                            model=state.selectedModel?.providerModelId ?: state.selectedModel?.id,workflow=state.selectedModel?.inputMode?.name),
                        onFocusChanged={promptFocused=it})
                }
            }
            item(key="capability-inputs") {
                capabilities?.let {resolved -> DynamicFormRenderer(resolved,state.parameters,state.attachedMedia,
                    enabled=!state.isPreparingMedia && !state.isSubmitting && !state.isUploading,countKey=countParam?.key,
                    onAdd={field,items ->
                        val modelKey=state.selectedModel?.let {it.providerId+"/"+it.id}.orEmpty()
                        viewModel.addCanvasMedia(field.key,modelKey,items)
                    },onRemove=viewModel::removeMedia,onReorder=viewModel::reorderMedia,onChange=viewModel::updateParameter) }
            }
            if(state.retainedMedia.isNotEmpty()) item(key="retained-media") {
                ConservedMediaSection(state.retainedMedia,!state.isSubmitting,viewModel::removeRetainedMedia)
            }

            // 3. Preparación local y subida: nunca se muestra un adjunto hasta tener copia privada estable.
            if (state.isPreparingMedia || state.isUploading) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = NexusTokens.thumbnail,
                        color = PrimaryNeon.copy(alpha = 0.08f),
                        border = null
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space8),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12)
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


            if(state.schemaReady && state.selectedModality in setOf(Modality.IMAGE,Modality.VIDEO) && capabilities?.promptVisible==true) item(key="generation-quantity") {
                GenerationQuantityControl(quantity.count,!state.isSubmitting,onCount={count ->
                    batchCount=count
                    if(countParam!=null) viewModel.updateParameter(countParam.key,if(nativeCountAllowed(countParam,count)) count.toString() else "1")
                },onModels={viewModel.comparison.open(state,withReferences=state.attachedMedia.isNotEmpty());showComparison=true},
                    allowed={count -> countParam==null || nativeCountAllowed(countParam,count) || nativeCountAllowed(countParam,1)},
                    choices=(BATCH_COUNTS+listOf(quantity.count)+countParam?.options.orEmpty().mapNotNull {it.toIntOrNull()}).filter {it>0}.distinct().sorted())
            }
            item(key="creation-tools") {
                StudioToolRow {
                    if(comparisonState.phase==ComparisonPhase.SENDING) StudioToolChip("Ver lote") {showComparison=true}
                    Box {
                        StudioToolChip("Más", "Más herramientas de Lanzadera") {showToolMenu=true}
                        DropdownMenu(expanded=showToolMenu,onDismissRequest={showToolMenu=false}) {
                            DropdownMenuItem(text={Text("Todos los parámetros")},enabled=state.schemaReady,
                                onClick={showToolMenu=false;showParameters=true})
                            DropdownMenuItem(text={Text("Comparar modelos")},enabled=state.schemaReady && !state.isSubmitting,
                                onClick={showToolMenu=false;viewModel.comparison.open(state,withReferences=state.attachedMedia.isNotEmpty());showComparison=true})
                            DropdownMenuItem(text={Text("Mis ajustes")},enabled=state.schemaReady && !state.isSubmitting,
                                onClick={showToolMenu=false;showPersonalPresets=true})
                            DropdownMenuItem(text={Text("Usar mi cuenta BytePlus")},onClick={showToolMenu=false;showBytePlus=true})
                        }
                    }
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
                val failure=state.submitFailure?.takeIf { it.message==raw }
                if(failure!=null) {
                    val viaFlow=state.selectedModel?.providerId?.contains("flow",ignoreCase=true)==true
                    InlineNotice(failure.message,technicalDetails=failure.technicalDetails,
                        actionLabel="Revisar conexión",onAction=if(viaFlow) onOpenFlow else onBrowseModels)
                } else InlineNotice(noticeMessage(raw,"No se ha podido completar la solicitud. Revisa sus detalles antes de volver a generar."),technicalDetails=raw)
            } }
        }
    }
    }
    if(showParameters) CanvasSheet("Parámetros",{showParameters=false}) {
        if(state.isLoadingSchema) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.schemaMessage?.let { raw ->
            InlineNotice(if(state.schemaReady) "Revisa la compatibilidad de estas opciones." else noticeMessage(raw,"Las opciones aún no están disponibles."),
                technicalDetails=raw,isError=!state.schemaReady,
                actionLabel=if(state.schemaReady) null else "Reintentar",onAction=if(!state.schemaReady && !state.isLoadingSchema) viewModel::retryConfiguration else null)
        }
        if(state.schemaReady) {
            val params=(capabilities?.primaryParameters.orEmpty()+capabilities?.advancedParameters.orEmpty()).filter {it.key!=countParam?.key}.distinctBy {it.key}
            state.selectedModel?.let {model -> ParameterSheetContent(model,params,state.parameters) {key,value -> viewModel.updateParameter(key,value)}}
        }
    }
    if(showInspiration) CanvasSheet("Inspiración",{showInspiration=false}) {
        CreativePresetsRow(state.prompt) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);viewModel.updatePrompt(it) }
    }
    if(showPriceDetails) CanvasSheet("Coste estimado",{showPriceDetails=false}) {
        Text(estimate.amount,style=MaterialTheme.typography.titleMedium.tabularMoney())
        Text(estimate.details.ifBlank {"El coste depende de la configuración elegida."},style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
        estimate.retry?.let { retry -> TextButton(onClick=retry) {Text("Reintentar precio",color=TextSecondary)} }
    }

    if(showBytePlus) BytePlusCanvasSheet(app,catalogModels,onDismiss={showBytePlus=false},onChoose={modality ->
        showBytePlus=false;bytePlusOnly=true;pickerModality=modality;showModelPickerSheet=true
    },onSettings={page -> showBytePlus=false;onBytePlusSettings(page)})

    // Modal Bottom Sheet para Seleccionar Modelo
    if (showModelPickerSheet) {
        ModelPickerSheet(
            availableModels = if(bytePlusOnly) catalogModels.filter {it.providerId==BYTEPLUS_MEDIA_PROVIDER} else catalogModels,
            selectedModel = state.selectedModel,
            initialModality = pickerModality ?: state.selectedModality,
            media = state.attachedMedia,
            currencyManager = currencyManager,
            onSelectModel = { model ->
                chooseModel(model)
                showModelPickerSheet = false;bytePlusOnly=false;pickerModality=null
            },
            onDismiss = { showModelPickerSheet = false;bytePlusOnly=false;pickerModality=null }
        )
    }

    pendingCost?.let { quote ->
        AlertDialog(onDismissRequest=viewModel::dismissCostAcceptance, title={Text(if(quote.known) "Confirmar gasto" else "Importe sin confirmar")},
            text={Text("Importe mostrado: ${quote.amount}.\n"+quote.details.ifBlank {"No hay una tarifa confirmada para esta configuración. No equivale a coste cero."}+
                "\nSi generas, el proveedor puede cobrar según su tarifa real. Cualquier cambio en el modelo, el prompt, las opciones o los archivos anula esta aceptación.")},
            confirmButton={TextButton(onClick={viewModel.acceptUnknownCost(onNavigateToOutput)}) {Text("Aceptar y generar")}},
            dismissButton={TextButton(onClick=viewModel::dismissCostAcceptance) {Text("Cancelar")}})
    }

    pendingBatch?.let { (count, fingerprint) ->
        val offline = com.vidadomu.nexus.BuildConfig.OFFLINE_QA
        val unitKnown = isKnownCost(estimate)
        AlertDialog(onDismissRequest={pendingBatch=null}, title={Text("Generar $count ${if(state.selectedModality==Modality.VIDEO) "vídeos" else "imágenes"}")},
            text={Text(buildString {
                append("Se enviarán $count generaciones independientes con el mismo prompt, modelo, ajustes y archivos.\n")
                append("Importe por unidad: ${estimate.amount}")
                if(!unitKnown) append(" (sin tarifa confirmada; no equivale a coste cero)")
                append(".\nEl proveedor cobra cada unidad por separado: el total es $count veces ese importe.")
                if(offline) append("\nNEXUS Lab: el envío está bloqueado.")
            })},
            confirmButton={TextButton(enabled=!offline, onClick={
                pendingBatch=null
                when {
                    com.vidadomu.nexus.BuildConfig.OFFLINE_QA -> batchScope.launch { batchSnackbar.showSnackbar(GenerationCostGuard.OFFLINE_MESSAGE) }
                    fingerprint != generationFingerprint(state) -> batchScope.launch { batchSnackbar.showSnackbar(GenerationCostGuard.CHANGED_AFTER_ACCEPTANCE_MESSAGE) }
                    else -> viewModel.submitBatch(List(count) { state.prompt }, { _, _ -> }) { sent, firstError ->
                        batchScope.launch { batchSnackbar.showSnackbar("Enviadas $sent de $count." + (firstError?.let { " $it" } ?: "")) }
                    }
                }
            }) {Text("Generar $count")}},
            dismissButton={TextButton(onClick={pendingBatch=null}) {Text("Cancelar")}})
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
                .padding(horizontal = NexusTokens.space16)
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
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = PrimaryNeon, modifier = Modifier.size(NexusTokens.iconMedium))
                    Text(
                        text = "Diagnóstico local",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(NexusTokens.minimumTouch)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary, modifier = Modifier.size(NexusTokens.iconSmall))
                }
            }

            // Endpoint & Routing Info
            Surface(
                color = SurfaceVariantDark,
                shape = NexusTokens.thumbnail,
                border = null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(NexusTokens.space12), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
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
                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = MaterialTheme.typography.labelMedium.fontSize, letterSpacing = 1.sp)
            )
            Surface(
                color = MaterialTheme.colorScheme.onPrimary,
                shape = NexusTokens.thumbnail,
                border = null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = simulatedPayload,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.labelMedium.fontSize),
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

                        .clickable { onSelect(option) }
                        .padding(horizontal = NexusTokens.space8, vertical = NexusTokens.space4)
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelSmall.copy(
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
            style = MaterialTheme.typography.labelSmall.copy(
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
                    border = null,
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

                                .background(
                                    if (isSelected) PrimaryNeon.copy(alpha = 0.35f) else Color.Transparent
                                )
                        )
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelSmall.copy(
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
            style = MaterialTheme.typography.labelSmall.copy(
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
                    border = null,
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
                        modifier = Modifier.padding(horizontal = NexusTokens.space8, vertical = NexusTokens.space4),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NexusTokens.space4)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(NexusTokens.space24), tint=if(isApplied) PrimaryNeon else TextSecondary)
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelSmall.copy(
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
private fun CanvasSheet(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        shape=NexusTokens.sheet,containerColor=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).verticalScroll(rememberScrollState())
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
