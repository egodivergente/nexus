package com.vidadomu.nexus.ui.flow

import android.net.Uri
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

private sealed interface FlowStudioConnection {
    data object Checking : FlowStudioConnection
    data object OfflineQa : FlowStudioConnection
    data class Ready(val status: FlowBridgeStatus) : FlowStudioConnection
    data class Unavailable(val message: String) : FlowStudioConnection
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowStudioScreen(
    onBack: () -> Unit,
    onNavigateToOutput: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    val drafts = remember { mutableStateListOf(FlowStudioDraft()) }
    val references = remember { mutableStateListOf<FlowStudioReference>() }
    var submittedIds by rememberSaveable { mutableStateOf("") }
    var connection by remember { mutableStateOf<FlowStudioConnection>(FlowStudioConnection.Checking) }
    var isSubmitting by remember { mutableStateOf(false) }
    var progressLabel by remember { mutableStateOf<String?>(null) }
    var pendingDownload by remember { mutableStateOf<GenerationOutput?>(null) }
    var costDetails by rememberSaveable { mutableStateOf(false) }

    suspend fun refreshConnection() {
        if (BuildConfig.OFFLINE_QA) {
            connection = FlowStudioConnection.OfflineQa
            return
        }
        connection = FlowStudioConnection.Checking
        connection = app.flowBridgeProvider.bridgeStatus().fold(
            onSuccess = { status ->
                if (status.isReady) FlowStudioConnection.Ready(status)
                else FlowStudioConnection.Unavailable(status.detail?.takeIf { it.isNotBlank() }
                    ?: "El proyecto Flow no está listo. Ábrelo desde Ajustes y vuelve a intentarlo.")
            },
            onFailure = { error ->
                FlowStudioConnection.Unavailable(error.message?.takeIf { it.isNotBlank() }
                    ?: "No se pudo comprobar el puente privado de Flow.")
            }
        )
    }
    LaunchedEffect(Unit) { refreshConnection() }

    val status = (connection as? FlowStudioConnection.Ready)?.status
    val promptLimit = (status?.promptLimit ?: 4000).coerceIn(1, 4000)
    val accepted = status?.expectedOutputs.orEmpty().map { it.lowercase() }.toSet()
    val acceptedModalities = buildSet {
        if (accepted.isEmpty() || "image" in accepted) add(Modality.IMAGE)
        if (accepted.isEmpty() || "video" in accepted) add(Modality.VIDEO)
    }
    val sendable = FlowStudioPlan.sendable(drafts, promptLimit, acceptedModalities)
    val canSend = sendable.isNotEmpty() && !isSubmitting && connection is FlowStudioConnection.Ready

    val allJobs by app.generationRepository.allJobs.collectAsState()
    val trackedIds = FlowStudioPlan.decodeIds(submittedIds)
    val trackedJobs = trackedIds.mapNotNull { id -> allJobs.firstOrNull { it.id == id } }

    val pickReferences = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(FlowStudioPlan.MAX_REFERENCES)
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            if (references.size >= FlowStudioPlan.MAX_REFERENCES || references.any { it.uri == uri.toString() }) return@forEach
            val mime = context.contentResolver.getType(uri)?.lowercase()
            if (mime != null && mime in FlowStudioPlan.REFERENCE_MIME_TYPES) {
                references += FlowStudioReference(uri.toString(), mime, uri.lastPathSegment)
            } else {
                scope.launch { snackbarHost.showSnackbar("Flow solo admite referencias PNG, JPEG o WebP.") }
            }
        }
    }

    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { target: Uri? ->
        val output = pendingDownload
        pendingDownload = null
        if (target == null || output == null) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    val source = File(requireNotNull(Uri.parse(requireNotNull(output.localUri)).path))
                    context.contentResolver.openOutputStream(target)?.use { out ->
                        source.inputStream().use { it.copyTo(out) }
                    } ?: error("destino")
                }.isSuccess
            }
            snackbarHost.showSnackbar(if (copied) "Original guardado." else "No se pudo guardar el original. El archivo de Nexus se conserva.")
        }
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
                if (app.flowBridgeProvider.bridgeStatus().getOrNull()?.isReady != true) {
                    refreshConnection()
                    snackbarHost.showSnackbar("Flow ya no está listo. Revisa el puente y vuelve a intentarlo.")
                    return@launch
                }
                if (references.isNotEmpty() && !uploadReferences()) return@launch
                val inputs = FlowStudioPlan.mediaInputs(references)
                var sent = 0
                batch.forEachIndexed { index, draft ->
                    progressLabel = "Enviando prompt ${index + 1} de ${batch.size}…"
                    val result = app.generationRepository.submitGeneration(FlowStudioPlan.request(draft, inputs))
                    val job = result.getOrNull()
                    if (job == null || job.status == GenerationStatus.FAILED) {
                        snackbarHost.showSnackbar(job?.error ?: result.exceptionOrNull()?.message
                            ?: "Flow no aceptó el prompt ${index + 1}. Los demás siguen su curso.")
                    } else {
                        sent++
                        submittedIds = FlowStudioPlan.encodeIds(FlowStudioPlan.decodeIds(submittedIds) + job.id)
                        drafts.removeAll { it.id == draft.id }
                        if (job.status != GenerationStatus.COMPLETED) GenerationPollingWorker.enqueue(context.applicationContext, job.id)
                    }
                }
                if (drafts.isEmpty()) drafts += FlowStudioDraft()
                if (sent > 0) snackbarHost.showSnackbar("$sent de ${batch.size} prompts en la cola de Flow.")
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ScreenBackground,
        snackbarHost = {
            SnackbarHost(snackbarHost) { data ->
                InlineNotice(noticeMessage(data.visuals.message, "No se pudo completar la acción en Flow."),
                    technicalDetails = data.visuals.message, actionLabel = "Cerrar", onAction = { data.dismiss() })
            }
        },
        topBar = {
            NexusTopAppBar(
                title = { Text("Flow Studio", style = com.vidadomu.nexus.ui.components.nexusScreenTitleStyle()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        bottomBar = {
            Surface(color = BackgroundDark, border = BorderStroke(1.dp, BorderSubtle)) {
                Button(
                    onClick = ::sendAll,
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
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(NexusTokens.space16),
            verticalArrangement = Arrangement.spacedBy(NexusTokens.space16)
        ) {
            item(key = "connection") {
                StudioConnectionCard(connection) { scope.launch { refreshConnection() } }
            }
            items(drafts, key = { it.id }) { draft ->
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
                    onRemove = { drafts.removeAll { it.id == draft.id } }
                )
            }
            item(key = "add") {
                OutlinedButton(
                    onClick = { drafts += FlowStudioDraft(modality = drafts.lastOrNull()?.modality ?: Modality.IMAGE) },
                    enabled = drafts.size < FlowStudioPlan.MAX_DRAFTS && !isSubmitting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = NexusTokens.minimumTouch)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(NexusTokens.space8))
                    Text("Añadir prompt (${drafts.size}/${FlowStudioPlan.MAX_DRAFTS})")
                }
            }
            item(key = "references") {
                ReferencesSection(
                    references = references,
                    enabled = !isSubmitting,
                    onAdd = { pickReferences.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRemove = { ref -> references.remove(ref) }
                )
            }
            item(key = "cost") {
                Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    TextButton(onClick = { costDetails = !costDetails }) {
                        Text(if (costDetails) "Ocultar detalles" else "Ajustes y coste · Detalles")
                    }
                    if (costDetails) Text(
                        "NEXUS envía cada prompt con su cantidad. Flow usa el modelo, los ajustes y los créditos de su proyecto activo. " +
                            "Aquí no hay una estimación de coste verificable.",
                        style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            if (trackedJobs.isNotEmpty()) {
                item(key = "grid_title") {
                    Text("Resultados en vivo", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
                items(trackedJobs.asReversed(), key = { "job_${it.id}" }) { job ->
                    JobResultGroup(
                        job = job,
                        onOpen = { onNavigateToOutput(job.id) },
                        onDownload = { output ->
                            pendingDownload = output
                            saveDocument.launch(suggestedName(output))
                        }
                    )
                }
            }
        }
    }
}

private fun suggestedName(output: GenerationOutput): String {
    val local = output.localUri?.let { Uri.parse(it).lastPathSegment }
    return local?.takeIf { it.contains('.') } ?: "flow_${output.id}" + when {
        output.mimeType.contains("png") -> ".png"
        output.mimeType.contains("webp") -> ".webp"
        output.mimeType.startsWith("video") -> ".mp4"
        else -> ".jpg"
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
        is FlowStudioConnection.Ready -> InlineNotice(
            "Flow listo" + (connection.status.projectName?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
            technicalDetails = connection.status.detail, actionLabel = "Actualizar", onAction = onRefresh, isError = false)
        is FlowStudioConnection.Unavailable -> InlineNotice(
            noticeMessage(connection.message, "Flow no está listo. Abre el proyecto desde Ajustes y comprueba la conexión."),
            technicalDetails = connection.message, actionLabel = "Actualizar", onAction = onRefresh)
    }
}

@Composable
private fun DraftCard(
    draft: FlowStudioDraft,
    number: Int,
    promptLimit: Int,
    accepted: Set<Modality>,
    canRemove: Boolean,
    enabled: Boolean,
    onChange: (FlowStudioDraft) -> Unit,
    onRemove: () -> Unit
) {
    Surface(color = SurfaceDark, border = BorderStroke(1.dp, BorderSubtle), shape = NexusTokens.field) {
        Column(Modifier.padding(NexusTokens.space12), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Prompt $number", style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
                if (canRemove) IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(Icons.Default.Delete, contentDescription = "Quitar prompt $number")
                }
            }
            OutlinedTextField(
                value = draft.prompt,
                onValueChange = { onChange(draft.copy(prompt = it)) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Describe lo que quieres crear") },
                placeholder = { Text("Una escena, estilo, luz, movimiento…") },
                minLines = 3,
                maxLines = 8,
                isError = draft.prompt.trim().length > promptLimit,
                supportingText = { Text("${draft.prompt.length}/$promptLimit") },
                shape = NexusTokens.field
            )
            Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = draft.modality == Modality.IMAGE, enabled = enabled && Modality.IMAGE in accepted,
                    onClick = { onChange(draft.copy(modality = Modality.IMAGE)) }, label = { Text("Imagen") },
                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp)) })
                FilterChip(selected = draft.modality == Modality.VIDEO, enabled = enabled && Modality.VIDEO in accepted,
                    onClick = { onChange(draft.copy(modality = Modality.VIDEO)) }, label = { Text("Vídeo") },
                    leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space4), verticalAlignment = Alignment.CenterVertically) {
                Text("Cantidad", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                (FlowStudioPlan.MIN_COUNT..FlowStudioPlan.MAX_COUNT).forEach { value ->
                    FilterChip(selected = draft.count == value, enabled = enabled,
                        onClick = { onChange(draft.copy(count = value)) }, label = { Text("×$value") })
                }
            }
        }
    }
}

@Composable
private fun ReferencesSection(
    references: List<FlowStudioReference>,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: (FlowStudioReference) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Referencias (${references.size}/${FlowStudioPlan.MAX_REFERENCES})", style = MaterialTheme.typography.titleMedium,
                color = TextPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = onAdd, enabled = enabled && references.size < FlowStudioPlan.MAX_REFERENCES) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(NexusTokens.space4))
                Text("Añadir")
            }
        }
        Text("Se aplican a todos los prompts del envío. En vídeo activan el modo Referencias.",
            style = MaterialTheme.typography.bodySmall, color = TextMuted)
        references.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                row.forEach { ref ->
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(NexusTokens.field)) {
                        AsyncImage(model = ref.uri, contentDescription = "Referencia", contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize())
                        IconButton(onClick = { onRemove(ref) }, enabled = enabled, modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(Icons.Default.Close, contentDescription = "Quitar referencia", tint = TextPrimary)
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun JobResultGroup(job: GenerationJob, onOpen: () -> Unit, onDownload: (GenerationOutput) -> Unit) {
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
            job.error?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = if (job.status == GenerationStatus.FAILED) AccentCoral else TextMuted)
            }
            val cells = FlowStudioPlan.expectedCells(job)
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
            Text(FlowStudioPlan.DOWNLOAD_FORMAT_REASON, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            if (job.modality == Modality.VIDEO) {
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Extender vídeo") }
                Text(FlowStudioPlan.EXTEND_VIDEO_REASON, style = MaterialTheme.typography.bodySmall, color = TextMuted)
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
            Text("Original")
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
