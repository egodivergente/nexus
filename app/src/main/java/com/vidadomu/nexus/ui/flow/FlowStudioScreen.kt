package com.vidadomu.nexus.ui.flow

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.vidadomu.nexus.ui.create.StudioPromptField
import com.vidadomu.nexus.ui.theme.rememberNexusHaptics
import com.vidadomu.nexus.ui.theme.NexusHapticEvent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import com.vidadomu.nexus.ui.theme.NexusInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationOutput
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.MediaInput
import com.vidadomu.nexus.core.work.GenerationPollingWorker
import com.vidadomu.nexus.provider.flow.FlowBridgeStatus
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.components.NexusTopAppBar
import com.vidadomu.nexus.ui.components.noticeMessage
import com.vidadomu.nexus.ui.theme.AccentCoral
import com.vidadomu.nexus.ui.theme.AccentEmerald
import com.vidadomu.nexus.ui.theme.BackgroundDark
import com.vidadomu.nexus.ui.theme.BorderSubtle
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.ScreenBackground
import com.vidadomu.nexus.ui.theme.SurfaceDark
import com.vidadomu.nexus.ui.theme.TextMuted
import com.vidadomu.nexus.ui.theme.TextPrimary
import com.vidadomu.nexus.ui.theme.rememberNexusMotionAllowed
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vidadomu.nexus.ui.create.CanvasWorkspaceSwitch
import com.vidadomu.nexus.assistant.AgentMediaStore
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.assistant.rememberProjectMediaPicker
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private sealed interface FlowStudioConnection {
    data object Checking : FlowStudioConnection
    data object OfflineQa : FlowStudioConnection
    data class Ready(val status: FlowBridgeStatus) : FlowStudioConnection
    data class Unavailable(val message: String) : FlowStudioConnection
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FlowStudioScreen(
    onBack: () -> Unit,
    onNavigateToOutput: (String) -> Unit,
    onOpenModels: () -> Unit = onBack
) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val runtimeRules=com.vidadomu.nexus.settings.RuntimePreferences.get(context)
    val configuration by runtimeRules.state.collectAsState()
    val maximumDrafts=runtimeRules.int("flow.drafts")
    val maximumReferences=runtimeRules.int("flow.references").takeIf {it>0} ?: Int.MAX_VALUE
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val workspace = remember(app) { FlowWorkspaceStore(app, AssistantRuntime.get(app).settings.activeProjectId()) }
    val savedWorkspace = remember(workspace) { workspace.read() }

    val drafts = rememberSaveable(saver = listSaver(
        save = { values: androidx.compose.runtime.snapshots.SnapshotStateList<FlowStudioDraft> -> values.map { Json.encodeToString(it) } },
        restore = { values -> values.map { Json.decodeFromString<FlowStudioDraft>(it) }.toMutableStateList() }
    )) { savedWorkspace.drafts.toMutableStateList() }
    val references = rememberSaveable(saver = listSaver(
        save = { values: androidx.compose.runtime.snapshots.SnapshotStateList<FlowStudioReference> -> values.map { Json.encodeToString(it) } },
        restore = { values -> values.map { Json.decodeFromString<FlowStudioReference>(it) }.toMutableStateList() }
    )) { savedWorkspace.references.toMutableStateList() }
    androidx.compose.runtime.SideEffect { workspace.write(drafts.toList(), references.toList()) }
    var importingReferences by remember { mutableStateOf(false) }
    var connectionSheet by rememberSaveable { mutableStateOf(false) }
    var resultsFilter by rememberSaveable { mutableStateOf("Todos") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var composerExpanded by rememberSaveable { mutableStateOf(false) }
    var connection by remember { mutableStateOf<FlowStudioConnection>(FlowStudioConnection.Checking) }
    var isSubmitting by remember { mutableStateOf(false) }
    var progressLabel by remember { mutableStateOf<String?>(null) }
    var exportTarget by remember { mutableStateOf<GenerationOutput?>(null) }
    var pendingExport by remember { mutableStateOf<Pair<GenerationOutput, FlowExportPlan>?>(null) }
    val exportMime = remember { mutableStateOf("application/octet-stream") }
    var costDetails by rememberSaveable { mutableStateOf(false) }
    // Explicit settings (model, mode, aspect, seconds, frames) when AXELPC offers the verified direct route.
    var directCaps by remember { mutableStateOf<com.vidadomu.nexus.provider.flow.FlowDirectCapabilities?>(null) }
    val directPrefs = remember { context.getSharedPreferences("flow_direct_v1", Context.MODE_PRIVATE) }
    fun loadDirect(kind: String) = directPrefs.getString(kind, null)?.let { runCatching { Json.decodeFromString<FlowDirectSettings>(it) }.getOrNull() }
        ?: FlowDirectSettings.default(kind)
    var videoSettings by remember { mutableStateOf(loadDirect("video")) }
    var imageSettings by remember { mutableStateOf(loadDirect("image")) }
    fun saveDirect(kind: String, value: FlowDirectSettings) {
        if (kind == "video") videoSettings = value else imageSettings = value
        directPrefs.edit().putString(kind, Json.encodeToString(value)).apply()
    }
    val frameSaver = androidx.compose.runtime.saveable.Saver<FlowStudioReference?, String>(
        save = { it?.let { r -> Json.encodeToString(r) } ?: "" }, restore = { it.takeIf(String::isNotBlank)?.let { r -> Json.decodeFromString<FlowStudioReference>(r) } })
    var startFrame by rememberSaveable(stateSaver = frameSaver) { mutableStateOf<FlowStudioReference?>(null) }
    var endFrame by rememberSaveable(stateSaver = frameSaver) { mutableStateOf<FlowStudioReference?>(null) }
    var directConfirm by remember { mutableStateOf<FlowDirectConfirmation?>(null) }

    suspend fun refreshConnection(showChecking: Boolean = true) {
        if (BuildConfig.OFFLINE_QA) {
            connection = FlowStudioConnection.OfflineQa
            return
        }
        if (showChecking) connection = FlowStudioConnection.Checking
        connection = (if(showChecking) app.flowBridgeProvider.workspaceStatus() else app.flowBridgeProvider.bridgeStatus()).fold(
            onSuccess = { status ->
                if (status.isReady || status.recovering) FlowStudioConnection.Ready(status)
                else FlowStudioConnection.Unavailable(status.detail?.takeIf { it.isNotBlank() }
                    ?: "El proyecto Flow no está listo. Ábrelo desde Ajustes y vuelve a intentarlo.")
            },
            onFailure = { error ->
                FlowStudioConnection.Unavailable(error.message?.takeIf { it.isNotBlank() }
                    ?: "No se pudo comprobar el puente privado de Flow.")
            }
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var left = false
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_PAUSE) left = true
            if(event == Lifecycle.Event.ON_RESUME && left) {
                left = false
                scope.launch { refreshConnection(false) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { refreshConnection() }
    if(connectionSheet) ModalBottomSheet(onDismissRequest={connectionSheet=false}) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            com.vidadomu.nexus.ui.settings.FlowRecoverySettings(onCreateInFlow={connectionSheet=false}, inStudio=true)
            OutlinedButton(onClick={scope.launch {refreshConnection()}}) {Text("Comprobar conexión")}
            TextButton(onClick={connectionSheet=false}) {Text("Volver al lienzo de Flow")}
        }
    }
    LaunchedEffect((connection as? FlowStudioConnection.Ready)?.status?.recovering) {
        while ((connection as? FlowStudioConnection.Ready)?.status?.recovering == true) {
            kotlinx.coroutines.delay(3_000)
            refreshConnection(showChecking = false)
        }
    }

    val status = (connection as? FlowStudioConnection.Ready)?.status
    LaunchedEffect(status?.isReady) {
        if (status?.isReady == true && !BuildConfig.OFFLINE_QA)
            directCaps = app.flowBridgeProvider.direct.capabilities().getOrNull()?.takeIf { it.available && it.models.isNotEmpty() }
    }
    val promptLimit: Int? = null // No verified Google limit for the active model/workflow.
    val accepted = status?.expectedOutputs.orEmpty().map { it.lowercase() }.toSet()
    val acceptedModalities = buildSet {
        if (accepted.isEmpty() || "image" in accepted) add(Modality.IMAGE)
        if (accepted.isEmpty() || "video" in accepted) add(Modality.VIDEO)
    }
    val sendable = FlowStudioPlan.sendable(drafts, promptLimit, acceptedModalities)
    val canSend = sendable.isNotEmpty() && !isSubmitting && !importingReferences && status?.isReady == true && !status.recovering

    val allJobs by app.generationRepository.allJobs.collectAsState()
    val flowJobs = allJobs.filter { it.providerId == "google_flow" }.sortedByDescending { it.startedAt }
    val trackedJobs = flowJobs.filter { job -> when(resultsFilter) {
        "En curso" -> FlowStudioPlan.isActive(job.status)
        "Listos" -> job.outputs.isNotEmpty()
        "Errores" -> job.status == GenerationStatus.FAILED
        else -> true
    } }.take(30)
    // title, connection, summary and composer header, plus the drafts, «add» and «cost» rows while unfolded.
    val jobsListIndex = 3

    fun acceptReferences(uris: List<Uri>) {
        if(importingReferences || isSubmitting || uris.isEmpty()) return
        importingReferences = true
        scope.launch {
            try {
                for(uri in uris.distinct()) {
                    if(references.size >= maximumReferences) break
                    val mime = com.vidadomu.nexus.assistant.projectMediaMimeType(context,uri)?.lowercase()
                    if(mime !in FlowStudioPlan.REFERENCE_MIME_TYPES) {
                        snackbarHost.showSnackbar("Flow admite referencias PNG, JPEG o WebP.")
                        continue
                    }
                    val local = AgentMediaStore.persist(app,uri,mime,AssistantRuntime.get(app).settings.activeProjectId())
                    if(references.none {it.uri == local.uri}) references += FlowStudioReference(local.uri, requireNotNull(mime), local.name)
                }
            } catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) {snackbarHost.showSnackbar("No se pudo preparar la imagen. Vuelve a seleccionarla.")}
            finally {importingReferences=false}
        }
    }
    val remainingReferences = (maximumReferences - references.size).coerceAtLeast(1).coerceAtMost(if(android.os.Build.VERSION.SDK_INT>=33) android.provider.MediaStore.getPickImagesMaxLimit() else Int.MAX_VALUE)
    val pickReference = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri!=null) acceptReferences(listOf(uri))
    }
    val pickReferences = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(remainingReferences.coerceAtLeast(2))
    ) { uris: List<Uri> -> acceptReferences(uris) }
    val libraryReferences = rememberProjectMediaPicker { uris -> acceptReferences(uris) }

    val saveDocument = rememberLauncherForActivityResult(
        remember {
            object : ActivityResultContracts.CreateDocument("application/octet-stream") {
                override fun createIntent(context: Context, input: String): Intent =
                    super.createIntent(context, input).setType(exportMime.value)
            }
        }
    ) { target: Uri? ->
        val pending = pendingExport
        pendingExport = null
        if (target == null || pending == null) return@rememberLauncherForActivityResult
        val (output, plan) = pending
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { writeExport(context.contentResolver, exportSourceUri(requireNotNull(output.localUri)), plan, target) }
            }
            val error = result.exceptionOrNull()
            if (error is CancellationException) throw error
            snackbarHost.showSnackbar(if (error == null) "Guardado"
                else "No se pudo guardar: ${error.message ?: error::class.java.simpleName}")
        }
    }

    var qualityOptions by remember { mutableStateOf<List<com.vidadomu.nexus.provider.flow.FlowDirectAssetOption>?>(null) }
    var qualityLoading by remember { mutableStateOf(false) }
    var qualityConfirm by remember { mutableStateOf<Triple<GenerationOutput, com.vidadomu.nexus.provider.flow.FlowDirectAssetOption, com.vidadomu.nexus.provider.flow.FlowDirectQuote>?>(null) }
    LaunchedEffect(exportTarget?.id) {
        qualityOptions = null
        val source = exportTarget?.url?.let(com.vidadomu.nexus.provider.flow.FlowDirect::sourceRequest) ?: return@LaunchedEffect
        qualityLoading = true
        qualityOptions = app.flowBridgeProvider.direct.assetOptions(source).getOrNull()
        qualityLoading = false
    }
    qualityConfirm?.let { (output, option, quote) ->
        AlertDialog(onDismissRequest = { qualityConfirm = null }, title = { Text("Descargar en ${option.resolution.uppercase()} desde Flow") },
            text = { Text((if (quote.included) "Incluido en tu cuenta Flow." else quote.amount?.let { "$it créditos de Flow." }
                ?: "Flow no muestra el importe.") + "\nFlow procesa el original; Nexus guardará el archivo nuevo en la Biblioteca.") },
            confirmButton = { TextButton(onClick = {
                qualityConfirm = null
                val source = com.vidadomu.nexus.provider.flow.FlowDirect.sourceRequest(output.url) ?: return@TextButton
                val job = allJobs.firstOrNull { it.id == output.jobId }
                scope.launch {
                    val request = com.vidadomu.nexus.core.model.GenerationRequest(id = java.util.UUID.randomUUID().toString(),
                        providerId = com.vidadomu.nexus.provider.flow.FlowBridgeProvider.PROVIDER_ID,
                        modelId = "flow/" + com.vidadomu.nexus.provider.flow.FlowDirect.processModel(output.type == Modality.VIDEO),
                        modality = output.type, prompt = job?.prompt?.ifBlank { null } ?: "Descarga ${option.resolution} desde Flow",
                        parameters = mapOf(com.vidadomu.nexus.provider.flow.FlowDirect.ROUTE to com.vidadomu.nexus.provider.flow.FlowDirect.DIRECT,
                            com.vidadomu.nexus.provider.flow.FlowDirect.SOURCE to source,
                            com.vidadomu.nexus.provider.flow.FlowDirect.RESOLUTION to option.resolution,
                            com.vidadomu.nexus.provider.flow.FlowDirect.COST to (quote.amount?.toString() ?: "null")))
                    val result = app.generationRepository.submitGeneration(request)
                    val created = result.getOrNull() ?: app.generationRepository.getJob(request.id)
                    if (created == null || created.status == GenerationStatus.FAILED) snackbarHost.showSnackbar(created?.error ?: result.exceptionOrNull()?.message ?: "Flow no aceptó la descarga.")
                    else { GenerationPollingWorker.enqueue(context.applicationContext, created.id); snackbarHost.showSnackbar("Flow está preparando la versión ${option.resolution.uppercase()}.") }
                }
            }) { Text("Descargar") } },
            dismissButton = { TextButton(onClick = { qualityConfirm = null }) { Text("Cancelar") } })
    }
    exportTarget?.let { output ->
        FlowExportSheet(
            output = output,
            flowOptions = qualityOptions,
            flowOptionsLoading = qualityLoading,
            onFlowQuality = { option ->
                val source = com.vidadomu.nexus.provider.flow.FlowDirect.sourceRequest(output.url)
                exportTarget = null
                if (source != null) scope.launch {
                    val quote = app.flowBridgeProvider.direct.quoteProcess(output.type == Modality.VIDEO, source, option.resolution)
                    quote.onSuccess { qualityConfirm = Triple(output, option, it) }
                        .onFailure { snackbarHost.showSnackbar(it.message ?: "Flow no confirmó esa resolución. No se ha procesado nada.") }
                }
            },
            onDismiss = { exportTarget = null },
            onSave = { format, size ->
                exportTarget = null
                scope.launch {
                    val bounds = withContext(Dispatchers.IO) {
                        runCatching { readBounds(context.contentResolver, exportSourceUri(requireNotNull(output.localUri)), output) }
                    }
                    val plan = runCatching {
                        FlowExport.plan(output.id, output.mimeType, output.type == Modality.VIDEO, bounds.getOrNull(), format, size)
                    }.getOrElse {
                        snackbarHost.showSnackbar("No se pudo preparar la descarga: ${it.message}")
                        return@launch
                    }
                    pendingExport = output to plan
                    exportMime.value = plan.mimeType
                    runCatching { saveDocument.launch(plan.fileName) }.onFailure {
                        pendingExport = null
                        snackbarHost.showSnackbar("No se pudo abrir el selector de archivos: ${it.message}")
                    }
                }
            }
        )
    }

    suspend fun uploadReferences(): Boolean {
        for (index in references.indices) {
            val ref = references[index]
            if (ref.remoteId != null) continue
            progressLabel = "Subiendo referencia ${index + 1} de ${references.size}…"
            val uploaded = try {
                val file = withContext(Dispatchers.IO) {
                    val extension = when (ref.mimeType) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
                    File(context.cacheDir, "flow_studio_ref_$index.$extension").also { target ->
                        context.contentResolver.openInputStream(Uri.parse(ref.uri))?.use { input ->
                            target.outputStream().use { input.copyTo(it) }
                        } ?: error("No se pudo leer la referencia.")
                    }
                }
                app.flowBridgeProvider.uploadFile(file, file.name, ref.mimeType) { _, _ -> }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
            val remoteId = uploaded.getOrNull()
            if (remoteId == null) {
                snackbarHost.showSnackbar(uploaded.exceptionOrNull()?.message ?: "No se pudo subir la referencia ${index + 1}.")
                return false
            }
            references[index] = ref.copy(remoteId = remoteId)
        }
        return true
    }

    fun sendAll() {
        focusManager.clearFocus(force = true)
        val batch = sendable.toList()
        if (BuildConfig.OFFLINE_QA) {
            scope.launch {
                val total = batch.sumOf { FlowStudioPlan.clampCount(it.count) }
                snackbarHost.showSnackbar("QA sin conexión: se habrían enviado ${batch.size} prompts ($total resultados). No se ha enviado nada.")
            }
            return
        }
        scope.launch {
            isSubmitting = true
            try {
                // Los prompts van a la cola local, que ya espera a que Flow esté listo (p. ej. mientras termina
                // el encargo anterior). Solo se detiene el envío si no se puede hablar con el puente.
                val bridgeNow = app.flowBridgeProvider.bridgeStatus()
                val reachable = bridgeNow.getOrNull()
                if (reachable == null) {
                    refreshConnection()
                    snackbarHost.showSnackbar(bridgeNow.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                        ?: "No se pudo contactar con el puente de Flow. Los prompts se conservan.")
                    return@launch
                }
                val waitsForFlow = !reachable.isReady
                if (references.isNotEmpty() && !uploadReferences()) return@launch
                val inputs = FlowStudioPlan.mediaInputs(references)
                var sent = 0
                batch.forEachIndexed { index, draft ->
                    progressLabel = "Enviando prompt ${index + 1} de ${batch.size}…"
                    val request = FlowStudioPlan.request(draft, inputs)
                    val result = app.generationRepository.submitGeneration(request)
                    val job = result.getOrNull() ?: app.generationRepository.getJob(request.id)
                    if (job == null || job.status == GenerationStatus.FAILED) {
                        snackbarHost.showSnackbar(job?.error ?: result.exceptionOrNull()?.message
                            ?: "Flow no aceptó el prompt ${index + 1}. Los demás siguen su curso.")
                    } else {
                        sent++
                        drafts.removeAll { it.id == draft.id }
                        if (job.status != GenerationStatus.COMPLETED) GenerationPollingWorker.enqueue(context.applicationContext, job.id)
                    }
                }
                if (drafts.isEmpty()) drafts += FlowStudioDraft()
                if (sent > 0) {
                    // Sent: fold the composer so the new job's progress is the first thing on screen.
                    composerExpanded = false
                    listState.scrollToItem(0)
                }
                if (sent > 0) snackbarHost.showSnackbar("$sent de ${batch.size} prompts en la cola de Flow." +
                    if (waitsForFlow) " Se enviarán en cuanto Flow esté listo." else "")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                snackbarHost.showSnackbar(error.message ?: "No se pudo completar el envío. Los prompts pendientes se conservan.")
            } finally {
                isSubmitting = false
                progressLabel = null
            }
        }
    }

    fun persistFrame(uri: Uri, assign: (FlowStudioReference) -> Unit) {
        scope.launch {
            try {
                val mime = com.vidadomu.nexus.assistant.projectMediaMimeType(context, uri)?.lowercase()
                if (mime !in FlowStudioPlan.REFERENCE_MIME_TYPES) { snackbarHost.showSnackbar("El fotograma debe ser PNG, JPEG o WebP."); return@launch }
                val local = AgentMediaStore.persist(app, uri, mime, AssistantRuntime.get(app).settings.activeProjectId())
                assign(FlowStudioReference(local.uri, requireNotNull(mime), local.name))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { snackbarHost.showSnackbar("No se pudo preparar el fotograma. Vuelve a elegirlo.") }
        }
    }
    val pickStart = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) persistFrame(uri) { startFrame = it } }
    val pickEnd = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) persistFrame(uri) { endFrame = it } }

    suspend fun uploadFrame(frame: FlowStudioReference?, label: String): MediaInput? {
        if (frame == null) return null
        val remote = frame.remoteId ?: run {
            progressLabel = "Subiendo fotograma $label…"
            val file = withContext(Dispatchers.IO) {
                val extension = when (frame.mimeType) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
                File(context.cacheDir, "flow_frame_${label.lowercase()}.$extension").also { target ->
                    context.contentResolver.openInputStream(Uri.parse(frame.uri))?.use { input -> target.outputStream().use { input.copyTo(it) } }
                        ?: error("No se pudo leer el fotograma.")
                }
            }
            app.flowBridgeProvider.uploadFile(file, file.name, frame.mimeType) { _, _ -> }.getOrElse { throw it }
        }
        if (label == "inicial") startFrame = frame.copy(remoteId = remote) else endFrame = frame.copy(remoteId = remote)
        return MediaInput(type = Modality.IMAGE, uriString = frame.uri, mimeType = frame.mimeType, fileName = frame.fileName, remoteUrl = remote)
    }

    /** Validate, upload, quote, then ask: nothing is generated before the user accepts the amount Flow reported. */
    fun directSend(batch: List<FlowStudioDraft>, capabilities: com.vidadomu.nexus.provider.flow.FlowDirectCapabilities) {
        scope.launch {
            isSubmitting = true
            try {
                for (draft in batch) {
                    val kind = if (draft.modality == Modality.VIDEO) "video" else "image"
                    val settings = (if (kind == "video") videoSettings else imageSettings).normalized(capabilities, kind)
                        ?: run { snackbarHost.showSnackbar("Flow no ofrece ${if (kind == "video") "vídeo" else "imagen"} con ajustes en este momento."); return@launch }
                    val problem = com.vidadomu.nexus.provider.flow.FlowDirect.validate(settings.selection(FlowStudioPlan.clampCount(draft.count),
                        startFrame?.let { "ref_" + "0".repeat(64) }, endFrame?.let { "ref_" + "1".repeat(64) },
                        references.mapIndexed { i, _ -> "ref_" + i.toString().padStart(64, '0') }), capabilities)
                    if (problem != null) { snackbarHost.showSnackbar(problem); return@launch }
                }
                if (references.isNotEmpty() && !uploadReferences()) return@launch
                val refs = FlowStudioPlan.mediaInputs(references)
                val start = uploadFrame(startFrame, "inicial")
                val end = uploadFrame(endFrame, "final")
                val requests = mutableListOf<FlowDirectConfirmation.Item>()
                val quotes = linkedMapOf<String, com.vidadomu.nexus.provider.flow.FlowDirectQuote>()
                for (draft in batch) {
                    val kind = if (draft.modality == Modality.VIDEO) "video" else "image"
                    val settings = requireNotNull((if (kind == "video") videoSettings else imageSettings).normalized(capabilities, kind))
                    val selection = settings.selection(FlowStudioPlan.clampCount(draft.count), start?.remoteUrl, end?.remoteUrl, refs.mapNotNull { it.remoteUrl })
                    val key = com.vidadomu.nexus.provider.flow.FlowDirect.arguments(selection).toString()
                    if (key !in quotes) {
                        progressLabel = "Consultando el importe en Flow…"
                        quotes[key] = app.flowBridgeProvider.direct.quote(selection).getOrElse {
                            snackbarHost.showSnackbar(it.message ?: "Flow no confirmó el importe. No se ha enviado nada."); return@launch
                        }
                    }
                    requests += FlowDirectConfirmation.Item(draft, settings, quotes.getValue(key))
                }
                directConfirm = FlowDirectConfirmation(requests, refs, start, end)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { snackbarHost.showSnackbar(error.message ?: "No se pudo preparar el envío. No se ha enviado nada.") }
            finally { isSubmitting = false; progressLabel = null }
        }
    }

    fun directSubmit(confirmation: FlowDirectConfirmation) {
        scope.launch {
            isSubmitting = true
            try {
                var sent = 0
                confirmation.requests.forEachIndexed { index, item ->
                    val draft = item.draft
                    progressLabel = "Enviando ${index + 1} de ${confirmation.requests.size} a Flow…"
                    val request = FlowDirectPlan.request(draft, item.settings, confirmation.references, confirmation.start, confirmation.end, item.quote.amount)
                    val result = app.generationRepository.submitGeneration(request)
                    val job = result.getOrNull() ?: app.generationRepository.getJob(request.id)
                    if (job == null || job.status == GenerationStatus.FAILED) snackbarHost.showSnackbar(job?.error ?: result.exceptionOrNull()?.message ?: "Flow no aceptó el prompt ${index + 1}.")
                    else {
                        sent++
                        drafts.removeAll { it.id == draft.id }
                        if (job.status != GenerationStatus.COMPLETED) GenerationPollingWorker.enqueue(context.applicationContext, job.id)
                    }
                }
                if (drafts.isEmpty()) drafts += FlowStudioDraft(modality = confirmation.requests.lastOrNull()?.draft?.modality ?: Modality.IMAGE)
                if (sent > 0) { composerExpanded = false; listState.scrollToItem(0); snackbarHost.showSnackbar("$sent ${if (sent == 1) "trabajo enviado" else "trabajos enviados"} a Flow.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { snackbarHost.showSnackbar(error.message ?: "No se pudo completar el envío. Comprueba los trabajos antes de repetir.") }
            finally { isSubmitting = false; progressLabel = null }
        }
    }

    directConfirm?.let { confirmation ->
        AlertDialog(onDismissRequest = { directConfirm = null },
            title = { Text("Generar en Flow") },
            text = { Text(FlowDirectConfirmation.summary(confirmation)) },
            confirmButton = { TextButton(onClick = { directConfirm = null; directSubmit(confirmation) }) { Text("Generar") } },
            dismissButton = { TextButton(onClick = { directConfirm = null }) { Text("Cancelar") } })
    }

    val draftEditor: @Composable (FlowStudioDraft) -> Unit = { draft ->
        DraftCard(
                    draft = draft,
                    number = drafts.indexOf(draft) + 1,
                    promptLimit = promptLimit,
                    accepted = acceptedModalities,
                    canRemove = drafts.size > 1,
                    enabled = !isSubmitting,
                    onChange = { updated ->
                        val index = drafts.indexOfFirst { it.id == draft.id }
                        if (index >= 0) drafts[index] = updated
                    },
                    onRemove = { drafts.removeAll { it.id == draft.id } },
                    attachments = {
                        if(draft.id == drafts.firstOrNull()?.id) {
                            ReferencesSection(references,!isSubmitting && !importingReferences,onAdd={
                                if(remainingReferences == 1) pickReference.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                else pickReferences.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },onLibrary={libraryReferences.launch(arrayOf("image/*"),remainingReferences)},onRemove={references.remove(it)})
                            if(importingReferences) Text("Preparando imágenes…",Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                )
    }
    if(composerExpanded) ModalBottomSheet(onDismissRequest={composerExpanded=false},sheetState=androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Prompts de Flow",style=MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.weight(1f,false),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                items(drafts,key={it.id}) {draft -> draftEditor(draft)}
                item {OutlinedButton(onClick={drafts+=FlowStudioDraft(modality=drafts.lastOrNull()?.modality ?: Modality.IMAGE)},enabled=drafts.size<maximumDrafts && !isSubmitting,modifier=Modifier.fillMaxWidth()) {Text("Añadir prompt")}}
            }
            Button(onClick={composerExpanded=false},modifier=Modifier.fillMaxWidth()) {Text("Listo · ${drafts.size} ${if(drafts.size==1) "prompt" else "prompts"}")}
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ScreenBackground,
        snackbarHost = {
            SnackbarHost(snackbarHost) { data ->
                InlineNotice(noticeMessage(data.visuals.message, data.visuals.message.ifBlank { "No se pudo completar la acción en Flow." }),
                    technicalDetails = data.visuals.message, actionLabel = "Cerrar", onAction = { data.dismiss() })
            }
        },
        topBar = {},
        bottomBar = {
            com.vidadomu.nexus.ui.components.FloatingActionDock {
                Column(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.fillMaxWidth().heightIn(max=if(WindowInsets.isImeVisible) 220.dp else if (directCaps != null) 460.dp else 340.dp)
                        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val capabilities = directCaps
                        val kind = if (drafts.firstOrNull()?.modality == Modality.VIDEO) "video" else "image"
                        val settings = (if (kind == "video") videoSettings else imageSettings).let { s -> capabilities?.let { s.normalized(it, kind) } }
                        if (capabilities != null && settings != null && !WindowInsets.isImeVisible) FlowDirectSettingsCard(capabilities, kind, settings,
                            onChange = { saveDirect(kind, it) }, start = startFrame, end = endFrame,
                            onPickStart = { pickStart.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onPickEnd = { pickEnd.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onClearStart = { startFrame = null; endFrame = null }, onClearEnd = { endFrame = null }, enabled = !isSubmitting)
                        drafts.firstOrNull()?.let {draftEditor(it)}
                    }
                    if(!WindowInsets.isImeVisible) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onClick={composerExpanded=true}) {Text(if(drafts.size==1) "Más prompts" else "${drafts.size} prompts preparados")}
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick={connectionSheet=true}) {Text("Proyecto y ajustes")}
                    }
                }
                Button(
                    onClick = { val capabilities = directCaps; if (capabilities != null) { focusManager.clearFocus(force = true); directSend(sendable.toList(), capabilities) } else sendAll() },
                    enabled = canSend,
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(NexusInsets.bottomEdge)
                        .padding(horizontal = NexusTokens.space16, vertical = NexusTokens.space12)
                        .heightIn(min = NexusTokens.minimumTouch),
                    shape = NexusTokens.field
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(NexusTokens.space8))
                        Text(progressLabel ?: "Enviando a Flow…")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(NexusTokens.space8))
                        val total = sendable.sumOf { FlowStudioPlan.clampCount(it.count) }
                        Text(if (sendable.size <= 1) "Generar en Flow" else "Enviar ${sendable.size} prompts · $total resultados")
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            // El contenido pasa por debajo del botón flotante y se desvanece; no termina en un corte recto.
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()).consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(start = NexusTokens.space16, end = NexusTokens.space16, top = NexusTokens.space16,
                bottom = innerPadding.calculateBottomPadding() + NexusTokens.space8),
            verticalArrangement = Arrangement.spacedBy(NexusTokens.space16)
        ) {
            item(key="flow-title") {
                Row(Modifier.fillMaxWidth().statusBarsPadding(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Flow",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f))
                    IconButton(onClick={connectionSheet=true}) {Icon(Icons.Default.Tune,"Conexión y mantenimiento de Flow")}
                    com.vidadomu.nexus.ui.components.NexusSettingsButton()
                }
            }
            item(key = "connection") {
                StudioConnectionCard(connection) { scope.launch { refreshConnection() } }
            }
            // Progress lives at the top, where it is found without scrolling past the composer.
            if (flowJobs.isNotEmpty()) item(key = "jobs-summary") {
                val counts = FlowStudioPlan.counts(flowJobs)
                val latest = flowJobs.firstOrNull { FlowStudioPlan.isActive(it.status) }
                FlowJobsSummary(counts, latest, resultsFilter) { filter ->
                    resultsFilter = filter
                    scope.launch { listState.animateScrollToItem(jobsListIndex) }
                }
            }
            if (flowJobs.isNotEmpty()) {
                item(key = "grid_title") {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Trabajos de Flow", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

                    }
                }
                if (trackedJobs.isEmpty()) item(key = "jobs-empty") {
                    Text("No hay trabajos en «$resultsFilter».", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
                items(trackedJobs, key = { "job_${it.id}" }) { job ->
                    JobResultGroup(
                        job = job,
                        onOpen = { com.vidadomu.nexus.ui.output.OutputViewerScope.prepare(job.id,trackedJobs.flatMap {j -> j.outputs.indices.map {j.id to it}});onNavigateToOutput(job.id) },
                        onDownload = { output -> exportTarget = output },
                        onReuse = {
                            val blank = drafts.indexOfFirst { it.prompt.isBlank() }
                            if (blank >= 0 || drafts.size < maximumDrafts) {
                                // An empty prompt box is filled in place instead of leaving it empty above the copy.
                                if (blank >= 0) drafts[blank] = FlowStudioPlan.reuseDraft(job).copy(id = drafts[blank].id)
                                else drafts += FlowStudioPlan.reuseDraft(job)
                                composerExpanded = true
                                scope.launch {
                                    listState.animateScrollToItem(3)
                                    snackbarHost.showSnackbar("Prompt preparado abajo. No se ha enviado nada.")
                                }
                            } else scope.launch { snackbarHost.showSnackbar("Ya tienes $maximumDrafts prompts preparados.") }
                        },
                        onOpenFlow = { connectionSheet = true }
                    )
                }
            }
        }
    }
}

private fun exportSourceUri(localUri: String): Uri {
    val parsed = Uri.parse(localUri)
    return if (parsed.scheme.isNullOrBlank()) Uri.fromFile(File(localUri)) else parsed
}

private fun readBounds(resolver: ContentResolver, source: Uri, output: GenerationOutput): FlowExportDimensions? {
    if (output.type == Modality.VIDEO) {
        val w = output.width
        val h = output.height
        return if (w != null && h != null && w > 0 && h > 0) FlowExportDimensions(w, h) else null
    }
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val stream = resolver.openInputStream(source) ?: error("No se puede leer el original")
    stream.use { BitmapFactory.decodeStream(it, null, options) }
    return if (options.outWidth > 0 && options.outHeight > 0) FlowExportDimensions(options.outWidth, options.outHeight) else null
}

private fun writeExport(resolver: ContentResolver, source: Uri, plan: FlowExportPlan, target: Uri) {
    val encodeAs = plan.encodeAs
    if (encodeAs == null) {
        resolver.openOutputStream(target)?.use { out ->
            resolver.openInputStream(source)?.use { it.copyTo(out) } ?: error("No se puede leer el original")
        } ?: error("No se puede escribir en el destino")
        return
    }
    val size = requireNotNull(plan.target)
    val decoded = decodeScaled(resolver, source, size)
    val bitmap = if (encodeAs == FlowExportFormat.JPG && decoded.hasAlpha()) {
        Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888).also { flat ->
            Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(decoded, 0f, 0f, null) }
            decoded.recycle()
        }
    } else decoded
    try {
        val (format, quality) = when (encodeAs) {
            FlowExportFormat.PNG -> Bitmap.CompressFormat.PNG to 100
            FlowExportFormat.JPG -> Bitmap.CompressFormat.JPEG to FlowExport.JPG_QUALITY
            FlowExportFormat.WEBP -> webpLossy() to FlowExport.WEBP_QUALITY
            FlowExportFormat.ORIGINAL -> error("Formato de exportación no resuelto")
        }
        resolver.openOutputStream(target)?.use { out ->
            check(bitmap.compress(format, quality, out)) { "El codificador ${encodeAs.label} falló" }
        } ?: error("No se puede escribir en el destino")
    } finally {
        bitmap.recycle()
    }
}

@Suppress("DEPRECATION")
private fun webpLossy(): Bitmap.CompressFormat =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

private fun decodeScaled(resolver: ContentResolver, source: Uri, size: FlowExportDimensions): Bitmap {
    val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, source)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(size.width, size.height)
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = FlowExport.sampleSize(bounds.outWidth, bounds.outHeight, size)
        }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("No se pudo decodificar la imagen")
    }
    if (decoded.width == size.width && decoded.height == size.height) return decoded
    return Bitmap.createScaledBitmap(decoded, size.width, size.height, true).also { if (it !== decoded) decoded.recycle() }
}

@Composable
private fun FlowExportSheet(
    output: GenerationOutput,
    onDismiss: () -> Unit,
    onSave: (FlowExportFormat, FlowExportSize) -> Unit,
    /** Resolutions Flow itself offers for this result (direct route only), with their credits; null = not available. */
    flowOptions: List<com.vidadomu.nexus.provider.flow.FlowDirectAssetOption>? = null,
    flowOptionsLoading: Boolean = false,
    onFlowQuality: (com.vidadomu.nexus.provider.flow.FlowDirectAssetOption) -> Unit = {},
) {
    val isVideo = output.type == Modality.VIDEO
    var format by rememberSaveable(output.id) { mutableStateOf(FlowExportFormat.ORIGINAL) }
    var size by rememberSaveable(output.id) { mutableStateOf(FlowExportSize.ORIGINAL) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceDark) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = NexusTokens.space16).padding(bottom = NexusTokens.space24),
            verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)
        ) {
            Text("Descargar", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            // Real 2K/4K comes only from Flow's own processing of this result; the phone never invents resolution.
            if (flowOptionsLoading) Text("Consultando en Flow las resoluciones disponibles…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            flowOptions?.takeIf { it.isNotEmpty() }?.let { options ->
                Text("Calidad desde Flow", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        AssistChip(onClick = { onFlowQuality(option) }, label = { Text(option.label.ifBlank { option.resolution.uppercase() } +
                            (option.credits?.let { if (it == 0) " · incluido" else " · $it créditos" } ?: ""), maxLines = 1) })
                    }
                }
                Text("Flow procesa el archivo original y Nexus lo guarda como un resultado nuevo.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            if (isVideo) {
                Text(FlowExport.VIDEO_ORIGINAL_ONLY, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            } else {
                ExportChoiceRow("Formato", FlowExportFormat.entries, format, { it.label }) { format = it }
                // Only reductions: 2K/4K labels here suggested a resolution the local copy never gains.
                ExportChoiceRow("Reducir en el móvil", listOf(FlowExportSize.ORIGINAL, FlowExportSize.FHD_1080, FlowExportSize.HD_720), size, { it.label }) { size = it }
            }
            Button(onClick = { if (isVideo) onSave(FlowExportFormat.ORIGINAL, FlowExportSize.ORIGINAL) else onSave(format, size) },
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(NexusTokens.space8))
                Text("Guardar")
            }
        }
    }
}

@Composable
private fun <T> ExportChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) },
                    label = { Text(label(option), maxLines = 1) })
            }
        }
    }
}

@Composable
private fun StudioConnectionCard(connection: FlowStudioConnection, onRefresh: () -> Unit) {
    when (connection) {
        FlowStudioConnection.Checking -> Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
            CircularProgressIndicator(Modifier.size(NexusTokens.space24), strokeWidth = 2.dp)
            Text("Comprobando Flow…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        FlowStudioConnection.OfflineQa -> InlineNotice("QA sin conexión: no se envía nada a Flow.",
            technicalDetails = "BuildConfig.OFFLINE_QA", actionLabel = "Actualizar", onAction = onRefresh, isError = false)
        is FlowStudioConnection.Ready -> Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(connection.status.recovering) "Recuperando acceso…" else "Flow listo",style=MaterialTheme.typography.labelLarge,
                    color=if(connection.status.recovering) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                Text(connection.status.projectName ?: "Proyecto de Flow abierto",style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(listOfNotNull(connection.status.modelName,connection.status.aspectRatio).joinToString(" · ").ifBlank {"Modelo y formato: consultar en Flow"},style=MaterialTheme.typography.labelSmall,color=TextMuted)
            }
            IconButton(onClick=onRefresh) {Icon(Icons.Default.Refresh,"Actualizar conexión Flow",Modifier.size(18.dp))}
        }
        is FlowStudioConnection.Unavailable -> InlineNotice(
            noticeMessage(connection.message, "Flow no está listo. Abre el proyecto desde Ajustes y comprueba la conexión."),
            technicalDetails = connection.message, actionLabel = "Actualizar", onAction = onRefresh)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DraftCard(
    draft: FlowStudioDraft, number: Int, promptLimit: Int?, accepted: Set<Modality>,
    canRemove: Boolean, enabled: Boolean, onChange: (FlowStudioDraft) -> Unit, onRemove: () -> Unit,
    attachments: @Composable ColumnScope.() -> Unit = {}
) {
    var countMenu by remember {mutableStateOf(false)}
    val feedback=rememberNexusHaptics()
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        if(canRemove) Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Prompt $number",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
            IconButton(onClick=onRemove,enabled=enabled) {Icon(Icons.Default.Close,"Quitar prompt $number",Modifier.size(16.dp))}
        }
        StudioPromptField(draft.prompt,{onChange(draft.copy(prompt=it))},"Una escena, estilo, luz, movimiento…",
            enabled=enabled,maximum=promptLimit,attachments=attachments)
        if(!WindowInsets.isImeVisible) Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            FilterChip(draft.modality==Modality.IMAGE,onClick={
                feedback.perform(NexusHapticEvent.SELECTION,changed=draft.modality!=Modality.IMAGE)
                onChange(draft.copy(modality=Modality.IMAGE))
            },enabled=enabled && Modality.IMAGE in accepted,label={Text("Imagen")})
            FilterChip(draft.modality==Modality.VIDEO,onClick={
                feedback.perform(NexusHapticEvent.SELECTION,changed=draft.modality!=Modality.VIDEO)
                onChange(draft.copy(modality=Modality.VIDEO))
            },enabled=enabled && Modality.VIDEO in accepted,label={Text("Vídeo")})
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick={countMenu=true},enabled=enabled) {Text("×${draft.count} ▾")}
                DropdownMenu(countMenu,{countMenu=false}) {
                    (FlowStudioPlan.MIN_COUNT..FlowStudioPlan.MAX_COUNT).forEach {value ->
                        DropdownMenuItem(text={Text("×$value")},onClick={
                            countMenu=false;feedback.perform(NexusHapticEvent.SELECTION,changed=value!=draft.count)
                            onChange(draft.copy(count=value))
                        })
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReferencesSection(
    references: List<FlowStudioReference>,
    enabled: Boolean,
    onAdd: () -> Unit,
    onLibrary: () -> Unit,
    onRemove: (FlowStudioReference) -> Unit
) {
    val config by com.vidadomu.nexus.settings.RuntimePreferences.get(LocalContext.current).state.collectAsState()
    val maximumReferences=config.value("flow.references").toIntOrNull()?.takeIf {it>0} ?: Int.MAX_VALUE
    Column(Modifier.padding(horizontal=12.dp),verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Adjuntos · ${references.size}" + if(maximumReferences<Int.MAX_VALUE) "/$maximumReferences" else "", style = MaterialTheme.typography.labelSmall,
                color = TextPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = onAdd, enabled = enabled && references.size < maximumReferences) {
                Text("Galería")
            }
            TextButton(onClick=onLibrary,enabled=enabled && references.size<maximumReferences) {Text("Biblioteca")}
        }
        if(references.isNotEmpty() && !WindowInsets.isImeVisible) Text("Para todos los prompts",style=MaterialTheme.typography.labelSmall,color=TextMuted)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(references, key={it.uri}) { ref ->
                com.vidadomu.nexus.ui.create.CanvasAttachmentTile(
                    com.vidadomu.nexus.core.model.AttachedMedia(id=ref.uri,uriString=ref.uri,mimeType=ref.mimeType,fileName=ref.fileName,
                        slotType=com.vidadomu.nexus.core.model.MediaSlotType.REFERENCE_IMAGE),
                    "Referencia ${references.indexOf(ref)+1}",enabled,thumbnailSize=if(WindowInsets.isImeVisible) 48.dp else 72.dp) {onRemove(ref)}
            }
        }
    }
}

/** Counts and the live line of the newest active job; a tap filters the list below. */
@Composable
private fun FlowJobsSummary(counts: FlowStudioPlan.JobCounts, latest: GenerationJob?, selected: String, onShow: (String) -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(latest?.id) {
        while (latest != null) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
    }
    Surface(color = SurfaceDark, border = BorderStroke(1.dp, BorderSubtle), shape = NexusTokens.field, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(NexusTokens.space12), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            Text("Progreso", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            // Wraps instead of squeezing a count onto two lines when the system text is large.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8),
                verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                FilterChip(selected = selected == "Todos",onClick={onShow("Todos")},label={Text("Todos")})
                FilterChip(selected = selected == "En curso", onClick = { onShow("En curso") }, label = { Text("En curso · ${counts.active}", maxLines = 1) })
                FilterChip(selected = selected == "Listos", onClick = { onShow("Listos") }, label = { Text("Listos · ${counts.ready}", maxLines = 1) })
                FilterChip(selected = selected == "Errores", onClick = { onShow("Errores") }, label = { Text("Errores · ${counts.failed}", maxLines = 1) })
            }
            latest?.let { job ->
                Text(FlowStudioPlan.progressLine(job, com.vidadomu.nexus.provider.flow.FlowProgress.elapsed((now - job.startedAt) / 1_000)).orEmpty(),
                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}

@Composable
private fun JobResultGroup(job: GenerationJob, onOpen: () -> Unit, onDownload: (GenerationOutput) -> Unit,
    onReuse: () -> Unit = {}, onOpenFlow: () -> Unit = {}) {
    var now by remember(job.id) { mutableStateOf(System.currentTimeMillis()) }
    val active = FlowStudioPlan.isActive(job.status)
    LaunchedEffect(job.id, active) {
        while (active) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
    }
    val time = remember(job.startedAt) { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(job.startedAt)) }
    Surface(color = SurfaceDark, border = BorderStroke(1.dp, BorderSubtle), shape = NexusTokens.field) {
        Column(Modifier.padding(NexusTokens.space12), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.prompt, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(NexusTokens.space8))
                Text("${FlowStudioPlan.statusLabel(job.status)} · ${job.outputs.size}/${FlowStudioPlan.expectedCells(job)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = when (job.status) {
                        GenerationStatus.COMPLETED -> AccentEmerald
                        GenerationStatus.FAILED -> AccentCoral
                        else -> TextMuted
                    })
            }
            Text("$time · ${if (job.modality == Modality.VIDEO) "Vídeo" else "Imagen"} · ID ${job.providerJobId?.takeLast(8) ?: job.id.takeLast(8)}",
                style = MaterialTheme.typography.labelSmall, color = TextMuted)
            if (active) {
                Text(FlowStudioPlan.progressLine(job, com.vidadomu.nexus.provider.flow.FlowProgress.elapsed((now - job.startedAt) / 1_000)).orEmpty(),
                    style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                TextButton(onClick = onOpen) { Text("Ver estado y opciones") }
            }
            job.error?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = if (job.status == GenerationStatus.FAILED) AccentCoral else TextMuted)
            }
            if (job.status == GenerationStatus.FAILED) {
                if (FlowStudioPlan.isDraftConflict(job)) {
                    Text("No se ha enviado nada. Borra el texto de Flow (o envíalo desde allí) y vuelve a preparar este prompt.",
                        style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                        OutlinedButton(onClick = onOpenFlow) { Text("Abrir Flow") }
                        OutlinedButton(onClick = onReuse) { Text("Preparar de nuevo") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                        TextButton(onClick = onOpen) { Text("Revisar trabajo") }
                        TextButton(onClick = onReuse) { Text("Preparar de nuevo") }
                    }
                }
            }
            val cells = if(active) FlowStudioPlan.expectedCells(job) else job.outputs.size
            (0 until cells).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    row.forEach { index ->
                        val output = job.outputs.getOrNull(index)
                        Box(Modifier.weight(1f)) {
                            if (output != null) ResultCell(output, onOpen, onDownload)
                            else PendingCell(job)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

        }
    }
}

@Composable
private fun ResultCell(output: GenerationOutput, onOpen: () -> Unit, onDownload: (GenerationOutput) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(NexusTokens.field).background(BackgroundDark).clickable(onClick = onOpen),
            contentAlignment = Alignment.Center) {
            if (output.type == Modality.IMAGE && output.localUri != null) {
                AsyncImage(model = output.localUri, contentDescription = "Resultado de Flow", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.Videocam, contentDescription = "Vídeo de Flow", tint = TextPrimary, modifier = Modifier.size(40.dp))
            }
        }
        TextButton(onClick = { onDownload(output) }, enabled = output.localUri != null, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(NexusTokens.space4))
            Text("Descargar")
        }
    }
}

@Composable
private fun PendingCell(job: GenerationJob) {
    val active = FlowStudioPlan.isActive(job.status)
    val motion = rememberNexusMotionAllowed(active)
    val pulse = if (motion) {
        val transition = rememberInfiniteTransition(label = "flow_render")
        val animated by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "flow_render_pulse"
        )
        animated
    } else 1f
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(NexusTokens.field).background(BackgroundDark),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            when {
                active -> Box(Modifier.size(28.dp).alpha(pulse).clip(NexusTokens.field).background(AccentEmerald))
                job.status == GenerationStatus.FAILED -> Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentCoral)
                else -> Icon(Icons.Default.Image, contentDescription = null, tint = TextMuted)
            }
            Text(FlowStudioPlan.statusLabel(job.status), style = MaterialTheme.typography.labelMedium, color = TextMuted)
        }
    }
}
