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
import androidx.compose.foundation.rememberScrollState
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
    var exportTarget by remember { mutableStateOf<GenerationOutput?>(null) }
    var pendingExport by remember { mutableStateOf<Pair<GenerationOutput, FlowExportPlan>?>(null) }
    val exportMime = remember { mutableStateOf("application/octet-stream") }
    var costDetails by rememberSaveable { mutableStateOf(false) }

    suspend fun refreshConnection(showChecking: Boolean = true) {
        if (BuildConfig.OFFLINE_QA) {
            connection = FlowStudioConnection.OfflineQa
            return
        }
        if (showChecking) connection = FlowStudioConnection.Checking
        connection = app.flowBridgeProvider.bridgeStatus().fold(
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
    LaunchedEffect(Unit) { refreshConnection() }
    LaunchedEffect((connection as? FlowStudioConnection.Ready)?.status?.recovering) {
        while ((connection as? FlowStudioConnection.Ready)?.status?.recovering == true) {
            kotlinx.coroutines.delay(3_000)
            refreshConnection(showChecking = false)
        }
    }

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

    exportTarget?.let { output ->
        FlowExportSheet(
            output = output,
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
                    // Failed submissions are also saved by the repository. Keep
                    // their error visible in Studio after the snackbar disappears.
                    if (job != null) {
                        submittedIds = FlowStudioPlan.encodeIds(FlowStudioPlan.decodeIds(submittedIds) + job.id)
                    }
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ScreenBackground,
        snackbarHost = {
            SnackbarHost(snackbarHost) { data ->
                InlineNotice(noticeMessage(data.visuals.message, data.visuals.message.ifBlank { "No se pudo completar la acción en Flow." }),
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
                        onDownload = { output -> exportTarget = output }
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
    onSave: (FlowExportFormat, FlowExportSize) -> Unit
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
            if (isVideo) {
                Text(FlowExport.VIDEO_ORIGINAL_ONLY, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            } else {
                ExportChoiceRow("Formato", FlowExportFormat.entries, format, { it.label }) { format = it }
                ExportChoiceRow("Tamaño", FlowExportSize.entries, size, { it.label }) { size = it }
                Text("Nunca se amplía: si el original es menor, se mantiene su tamaño.",
                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
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
        is FlowStudioConnection.Ready -> InlineNotice(
            (if (connection.status.recovering) "Recuperando acceso a Flow…" else "Flow listo") + (connection.status.projectName?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
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
    var now by remember(job.id) { mutableStateOf(System.currentTimeMillis()) }
    val active = FlowStudioPlan.isActive(job.status)
    LaunchedEffect(job.id, active) {
        while (active) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
    }
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
            if (active) {
                Text("Tiempo transcurrido: ${com.vidadomu.nexus.provider.flow.FlowProgress.elapsed((now - job.startedAt) / 1_000)}",
                    style = MaterialTheme.typography.labelSmall, color = TextMuted)
                TextButton(onClick = onOpen) { Text("Ver estado y opciones") }
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
