package com.vidadomu.nexus.ui.create

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.AttachedMedia
import com.vidadomu.nexus.core.model.GenerationRequest
import com.vidadomu.nexus.core.model.MediaInput
import com.vidadomu.nexus.core.model.MediaSlotType
import com.vidadomu.nexus.core.model.ModelCapability
import com.vidadomu.nexus.core.model.ModelInputMode
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.core.work.GenerationPollingWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class CreateUiState(
    val selectedModality: Modality = Modality.IMAGE,
    val availableModels: List<NexusModel> = emptyList(),
    val selectedModel: NexusModel? = null,
    val prompt: String = "",
    val negativePrompt: String = "",
    val parameters: Map<String, String> = emptyMap(),
    val attachedMedia: List<AttachedMedia> = emptyList(),
    val showAdvanced: Boolean = false,
    val isSubmitting: Boolean = false,
    val isUploading: Boolean = false,       // True during pre-generation media upload
    val isPreparingMedia: Boolean = false,  // True while transient picker URIs are cloned into app cache
    val isLoadingSchema: Boolean = false,
    val schemaReady: Boolean = false,
    val schemaMessage: String? = null,
    val uploadProgress: Float? = null,      // 0..1 upload progress (null = indeterminate)
    val errorMessage: String? = null
) {
    val inputMediaUri: Uri?
        get() = attachedMedia.firstOrNull()?.uriString?.let { Uri.parse(it) }
    val inputMediaMime: String?
        get() = attachedMedia.firstOrNull()?.mimeType
}

class CreateViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NexusApplication
    private val modelCatalogRepo = app.modelCatalogRepository
    private val generationRepo = app.generationRepository
    private var pendingMediaCopies = 0
    private var schemaJob: kotlinx.coroutines.Job? = null
    private var schemaRequest = 0
    private fun loadNativeConfiguration(model: NexusModel) {
        val request = ++schemaRequest
        schemaJob?.cancel()
        _uiState.update { it.copy(isLoadingSchema = true, schemaReady = false, schemaMessage = null) }
        schemaJob = viewModelScope.launch {
            try {
                val config = com.vidadomu.nexus.provider.atlas.NativeModelSchema.load(model)
                if (schemaRequest == request && _uiState.value.selectedModel?.id == model.id) {
                    _uiState.update { it.copy(selectedModel = config.model,
                        parameters = config.model.parameters.filter { p -> p.defaultValue.isNotBlank() }.associate { p -> p.key to p.defaultValue },
                        attachedMedia = compatibleMedia(config.model, it.attachedMedia),
                        schemaReady = config.unsupportedRequired.isEmpty(),
                        schemaMessage = if(config.unsupportedRequired.isEmpty()) config.notices.takeIf { notes -> notes.isNotEmpty() }?.joinToString("\n") else
                            "Este modelo requiere entradas aún no compatibles: " + config.unsupportedRequired.joinToString()) }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if(schemaRequest == request) _uiState.update { it.copy(schemaReady = false, schemaMessage = e.message ?: "No se pudo consultar el esquema.") }
            } finally { if(schemaRequest == request) _uiState.update { it.copy(isLoadingSchema = false) } }
        }
    }

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            modelCatalogRepo.models.collect { models ->
                _uiState.update { current ->
                    val filtered = models.filter { it.modalities.contains(current.selectedModality) }
                        .sortedWith(compareBy<NexusModel> { it.inputMode == ModelInputMode.TEXT_ONLY }.then(NexusModel.DEFAULT_COMPARATOR))
                    val lastModelId = app.secureStorage.getLastModelId(current.selectedModality)
                    val currentSelectedStillValid = filtered.any { it.id == current.selectedModel?.id }
                    val newSelected = when {
                        currentSelectedStillValid -> filtered.first { it.id == current.selectedModel?.id }
                        lastModelId != null && filtered.any { it.id == lastModelId } -> filtered.find { it.id == lastModelId }
                        else -> filtered.firstOrNull()
                    }
                    val defaultParams = newSelected?.parameters?.associate { it.key to it.defaultValue } ?: emptyMap()
                    current.copy(
                        availableModels = filtered,
                        selectedModel = newSelected,
                        parameters = defaultParams,
                        attachedMedia = newSelected?.let { compatibleMedia(it, current.attachedMedia) } ?: emptyList()
                    )
                }
                _uiState.value.selectedModel?.let { loadNativeConfiguration(it) }
            }
        }
    }

    fun selectModality(modality: Modality) {
        val allModels = modelCatalogRepo.models.value
        val filtered = allModels.filter { it.modalities.contains(modality) }
            .sortedWith(compareBy<NexusModel> { it.inputMode == ModelInputMode.TEXT_ONLY }.then(NexusModel.DEFAULT_COMPARATOR))
        val lastModelId = app.secureStorage.getLastModelId(modality)
        val newSelected = filtered.find { it.id == lastModelId } ?: filtered.firstOrNull()
        val defaultParams = newSelected?.parameters?.associate { it.key to it.defaultValue } ?: emptyMap()

        _uiState.update {
            it.copy(
                selectedModality = modality,
                availableModels = filtered,
                selectedModel = newSelected,
                parameters = defaultParams,
                attachedMedia = newSelected?.let { model -> compatibleMedia(model, it.attachedMedia) } ?: emptyList(),
                errorMessage = null
            )
        }
        newSelected?.let { loadNativeConfiguration(it) }
    }

    fun switchToEditModel(): NexusModel? {
        val currentModel = _uiState.value.selectedModel ?: return null
        val allModels = modelCatalogRepo.models.value

        // 1. Try explicit paired model ID
        val pairedId = currentModel.pairedEditModelId
        var target = if (pairedId != null) allModels.find { it.id == pairedId } else null

        // 2. Try same organization with image/video input
        if (target == null) {
            target = allModels.find {
                it.organization.equals(currentModel.organization, ignoreCase = true) &&
                it.modalities.contains(currentModel.modalities.firstOrNull()) &&
                it.inputMode != ModelInputMode.TEXT_ONLY
            }
        }

        // 3. Fallback to top flagship edit/I2I model in same modality
        if (target == null) {
            target = allModels.filter {
                it.modalities.contains(currentModel.modalities.firstOrNull()) &&
                it.inputMode != ModelInputMode.TEXT_ONLY
            }.maxByOrNull { it.popularityScore }
        }

        if (target != null) {
            selectModel(target)
        }
        return target
    }

    fun selectModel(model: NexusModel) {
        app.secureStorage.setLastModelId(_uiState.value.selectedModality, model.id)
        val defaultParams = model.parameters.associate { it.key to it.defaultValue }
        _uiState.update { current ->
            current.copy(
                selectedModel = model,
                parameters = defaultParams,
                attachedMedia = compatibleMedia(model, current.attachedMedia),
                errorMessage = null
            )
        }
        loadNativeConfiguration(model)
    }

    private fun compatibleMedia(model: NexusModel, attached: List<AttachedMedia>): List<AttachedMedia> {
        val images = attached.filter { it.mimeType.startsWith("image/") }
        val videos = attached.filter { it.mimeType.startsWith("video/") }
        return when (model.inputMode) {
            ModelInputMode.TEXT_ONLY -> emptyList()
            ModelInputMode.SINGLE_IMAGE -> images
                .firstOrNull()
                ?.let { listOf(it.copy(slotType = MediaSlotType.PRIMARY_IMAGE)) }
                ?: emptyList()
            ModelInputMode.DUAL_FRAME -> images.take(2).mapIndexed { index, media ->
                media.copy(slotType = if (index == 0) MediaSlotType.PRIMARY_IMAGE else MediaSlotType.LAST_FRAME)
            }
            ModelInputMode.MULTI_IMAGE -> images.take(model.maxImages).map {
                it.copy(slotType = MediaSlotType.REFERENCE_IMAGE)
            }
            ModelInputMode.VIDEO_INPUT -> videos.firstOrNull()
                ?.let { listOf(it.copy(slotType = MediaSlotType.VIDEO_INPUT)) }
                ?: emptyList()
            ModelInputMode.MULTI_MODAL -> attached
        }
    }

    /**
     * Persists an incoming URI into a private app cache file so that Android transient
     * URI permissions are never lost, and uploads can read the bytes reliably.
     */
    private suspend fun persistMediaUri(uri: Uri, mimeType: String): Result<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        val scheme = uri.scheme?.lowercase()
        val resolvedMime = mimeType.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: app.contentResolver.getType(uri)
            ?: "application/octet-stream"
        if (scheme == "http" || scheme == "https") {
            return@withContext Result.success(uri to resolvedMime)
        }
        if (scheme == "file") {
            val file = java.io.File(uri.path ?: "")
            return@withContext if (file.isFile && file.length() > 0) {
                Result.success(uri to resolvedMime)
            } else {
                Result.failure(IllegalStateException("El archivo seleccionado ya no está disponible."))
            }
        }
        try {
            val ext = when {
                resolvedMime.contains("jpeg") || resolvedMime.contains("jpg") -> "jpg"
                resolvedMime.contains("png") -> "png"
                resolvedMime.contains("webp") -> "webp"
                resolvedMime.contains("mp4") -> "mp4"
                resolvedMime.contains("quicktime") || resolvedMime.contains("mov") -> "mov"
                resolvedMime.contains("mpeg") || resolvedMime.contains("mp3") -> "mp3"
                resolvedMime.contains("wav") -> "wav"
                else -> "bin"
            }
            val dir = java.io.File(app.cacheDir, "attached_media")
            check(dir.exists() || dir.mkdirs()) { "No se pudo preparar la caché privada de medios." }
            val targetFile = java.io.File(dir, "nexus_media_${UUID.randomUUID()}.$ext")
            val input = app.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Android no permitió leer el archivo seleccionado.")
            input.use {
                targetFile.outputStream().use { output ->
                    it.copyTo(output)
                }
            }
            check(targetFile.isFile && targetFile.length() > 0) { "El archivo seleccionado está vacío." }
            Result.success(Uri.fromFile(targetFile) to resolvedMime)
        } catch (e: Exception) {
            android.util.Log.e("CreateViewModel", "Failed to cache media locally: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun beginMediaCopy() {
        pendingMediaCopies++
        _uiState.update { it.copy(isPreparingMedia = true, errorMessage = null) }
    }

    private fun endMediaCopy() {
        pendingMediaCopies = (pendingMediaCopies - 1).coerceAtLeast(0)
        _uiState.update { it.copy(isPreparingMedia = pendingMediaCopies > 0) }
    }

    private fun cacheMedia(rawUri: Uri, rawMime: String, onReady: (Uri, String) -> Unit) {
        beginMediaCopy()
        viewModelScope.launch {
            try {
                val (uri, mimeType) = persistMediaUri(rawUri, rawMime).getOrThrow()
                onReady(uri, mimeType)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "No se pudo conservar el archivo seleccionado: ${e.message ?: "error de lectura"}")
                }
            } finally {
                endMediaCopy()
            }
        }
    }

    fun updatePrompt(text: String) {
        _uiState.update { it.copy(prompt = text, errorMessage = null) }
    }

    fun updateNegativePrompt(text: String) {
        _uiState.update { it.copy(negativePrompt = text) }
    }

    fun updateParameter(key: String, value: String) {
        _uiState.update {
            val updated = it.parameters.toMutableMap()
            updated[key] = value
            it.copy(parameters = updated)
        }
    }

    fun setPrimaryImage(rawUri: Uri, rawMime: String) {
        cacheMedia(rawUri, rawMime) { uri, mimeType ->
            val fileName = extractFileName(uri, mimeType)
            _uiState.update { current ->
                val filtered = current.attachedMedia.filter { it.slotType != MediaSlotType.PRIMARY_IMAGE }
                current.copy(
                    attachedMedia = listOf(
                        AttachedMedia(
                            uriString = uri.toString(),
                            mimeType = mimeType,
                            slotType = MediaSlotType.PRIMARY_IMAGE,
                            fileName = fileName
                        )
                    ) + filtered,
                    errorMessage = null
                )
            }
        }
    }

    fun setLastFrameImage(rawUri: Uri, rawMime: String) {
        cacheMedia(rawUri, rawMime) { uri, mimeType ->
            val fileName = extractFileName(uri, mimeType)
            _uiState.update { current ->
                val filtered = current.attachedMedia.filter { it.slotType != MediaSlotType.LAST_FRAME }
                current.copy(
                    attachedMedia = filtered + AttachedMedia(
                        uriString = uri.toString(),
                        mimeType = mimeType,
                        slotType = MediaSlotType.LAST_FRAME,
                        fileName = fileName
                    ),
                    errorMessage = null
                )
            }
        }
    }

    fun addReferenceImages(urisWithMime: List<Pair<Uri, String>>) {
        if (urisWithMime.isEmpty()) return
        beginMediaCopy()
        viewModelScope.launch {
            try {
                val persisted = urisWithMime.mapNotNull { (uri, mime) ->
                    persistMediaUri(uri, mime).getOrNull()
                }
                if (persisted.isEmpty()) {
                    _uiState.update { it.copy(errorMessage = "No se pudo conservar ninguna de las imágenes seleccionadas.") }
                    return@launch
                }
                _uiState.update { current ->
                    val maxAllowed = current.selectedModel?.maxImages ?: 10
                    val existingRefs = current.attachedMedia.filter { it.slotType == MediaSlotType.REFERENCE_IMAGE }
                    val remainingSlots = (maxAllowed - existingRefs.size).coerceAtLeast(0)
                    val toAdd = persisted.take(remainingSlots).map { (uri, mime) ->
                        AttachedMedia(
                            uriString = uri.toString(),
                            mimeType = mime,
                            slotType = MediaSlotType.REFERENCE_IMAGE,
                            fileName = extractFileName(uri, mime)
                        )
                    }
                    current.copy(
                        attachedMedia = current.attachedMedia + toAdd,
                        errorMessage = if (persisted.size == urisWithMime.size) null else "Alguna imagen no pudo conservarse."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "No se pudieron conservar las imágenes: ${e.message ?: "error de lectura"}") }
            } finally {
                endMediaCopy()
            }
        }
    }

    fun setVideoInput(rawUri: Uri, rawMime: String) {
        cacheMedia(rawUri, rawMime) { uri, mimeType ->
            val fileName = extractFileName(uri, mimeType)
            _uiState.update { current ->
                val filtered = current.attachedMedia.filter { it.slotType != MediaSlotType.VIDEO_INPUT }
                current.copy(
                    attachedMedia = listOf(
                        AttachedMedia(
                            uriString = uri.toString(),
                            mimeType = mimeType,
                            slotType = MediaSlotType.VIDEO_INPUT,
                            fileName = fileName
                        )
                    ) + filtered,
                    errorMessage = null
                )
            }
        }
    }

    fun removeMedia(id: String) {
        _uiState.update { current ->
            current.copy(attachedMedia = current.attachedMedia.filter { it.id != id })
        }
    }

    fun clearSlot(slotType: MediaSlotType) {
        _uiState.update { current ->
            current.copy(attachedMedia = current.attachedMedia.filter { it.slotType != slotType })
        }
    }

    fun clearAllMedia() {
        _uiState.update { it.copy(attachedMedia = emptyList()) }
    }

    fun setMediaInput(uri: Uri?, mimeType: String?) {
        if (uri == null) {
            clearAllMedia()
        } else {
            val model = _uiState.value.selectedModel
            val mime = mimeType ?: "image/jpeg"
            when (model?.inputMode) {
                ModelInputMode.TEXT_ONLY -> _uiState.update {
                    it.copy(errorMessage = "Este modelo solo admite texto. Cambia a su variante de edición para adjuntar archivos.")
                }
                ModelInputMode.VIDEO_INPUT -> setVideoInput(uri, mime)
                ModelInputMode.MULTI_IMAGE -> addReferenceImages(listOf(uri to mime))
                else -> setPrimaryImage(uri, mime)
            }
        }
    }

    fun clearMediaInput() {
        clearAllMedia()
    }

    fun toggleAdvanced() {
        _uiState.update { it.copy(showAdvanced = !it.showAdvanced) }
    }

    /**
     * Returns true if the currently selected model requires a media input
     * (image, video, etc.) that hasn't been provided yet.
     */
    fun isMediaInputRequired(): Boolean {
        val model = _uiState.value.selectedModel ?: return false
        if (!model.isMediaRequired) return false
        val attached = _uiState.value.attachedMedia
        if(model.nativeRequiredFields.isNotEmpty()) {
            return model.nativeRequiredFields.any { field ->
                when(field) {
                    model.firstFrameField -> attached.none { it.slotType == MediaSlotType.PRIMARY_IMAGE }
                    model.lastFrameField -> attached.none { it.slotType == MediaSlotType.LAST_FRAME }
                    model.imagesArrayField -> attached.none { it.slotType in listOf(MediaSlotType.REFERENCE_IMAGE, MediaSlotType.PRIMARY_IMAGE) }
                    model.videoField -> attached.none { it.slotType == MediaSlotType.VIDEO_INPUT }
                    model.videosArrayField -> attached.none { it.slotType == MediaSlotType.VIDEO_REFERENCE }
                    else -> false
                }
            }
        }

        return when (model.inputMode) {
            ModelInputMode.TEXT_ONLY -> false
            ModelInputMode.SINGLE_IMAGE -> !attached.any { it.slotType == MediaSlotType.PRIMARY_IMAGE }
            ModelInputMode.DUAL_FRAME -> !attached.any { it.slotType == MediaSlotType.PRIMARY_IMAGE }
            ModelInputMode.MULTI_IMAGE -> !attached.any { it.slotType in listOf(MediaSlotType.REFERENCE_IMAGE, MediaSlotType.PRIMARY_IMAGE) }
            ModelInputMode.VIDEO_INPUT -> !attached.any { it.slotType == MediaSlotType.VIDEO_INPUT }
            ModelInputMode.MULTI_MODAL -> false
        }
    }

    fun submitGeneration(onSuccess: (String) -> Unit) {
        val state = _uiState.value
        if (!state.schemaReady || state.isLoadingSchema) {
            _uiState.update { it.copy(errorMessage = it.schemaMessage ?: "Espera a que se cargue la configuración del modelo.") }
            return
        }
        if (state.isSubmitting || state.isUploading) return // Double-tap guard

        if (state.isPreparingMedia) {
            _uiState.update { it.copy(errorMessage = "Espera un instante: NEXUS está preparando el archivo seleccionado.") }
            return
        }

        val model = state.selectedModel
        if (model == null) {
            _uiState.update { it.copy(errorMessage = "Selecciona un modelo de IA antes de generar.") }
            return
        }

        if (state.prompt.isBlank() && ("prompt" in model.nativeRequiredFields || state.attachedMedia.isEmpty())) {
            _uiState.update { it.copy(errorMessage = "Escribe un prompt o adjunta los medios requeridos.") }
            return
        }

        // Validate: model requires media but none provided
        if (isMediaInputRequired()) {
            val modelName = model.displayName
            val reqMsg = when (model.inputMode) {
                ModelInputMode.VIDEO_INPUT -> "«$modelName» requiere un vídeo de entrada."
                ModelInputMode.MULTI_IMAGE -> "«$modelName» requiere al menos una imagen de referencia."
                ModelInputMode.DUAL_FRAME -> "«$modelName» requiere el primer fotograma."
                else -> "«$modelName» requiere una imagen de entrada."
            }
            _uiState.update { it.copy(errorMessage = reqMsg) }
            return
        }

        val schemaError = com.vidadomu.nexus.provider.atlas.NativeModelSchema.validate(model,
            state.parameters.filterValues { it.isNotBlank() } + ("prompt" to state.prompt.trim()))
        if(schemaError != null) {
            _uiState.update { it.copy(errorMessage = schemaError) }
            return
        }
        val invalid = model.parameters.firstOrNull { p ->
            val value = state.parameters[p.key].orEmpty()
            (p.isRequired && value.isBlank()) ||
                (value.isNotBlank() && (
                    (p.options.isNotEmpty() && value !in p.options) ||
                    (p.nativeType == "integer" && value.toLongOrNull() == null) ||
                    (p.nativeType == "number" && value.toDoubleOrNull()?.isFinite() != true) ||
                    (p.nativeType == "boolean" && value !in listOf("true", "false")) ||
                    (p.minValue != null && (value.toDoubleOrNull() ?: Double.NEGATIVE_INFINITY) < p.minValue) ||
                    (p.maxValue != null && (value.toDoubleOrNull() ?: Double.POSITIVE_INFINITY) > p.maxValue)
                ))
        }
        if(invalid != null) {
            _uiState.update { it.copy(errorMessage = "Revisa el campo «" + invalid.displayName + "» en las opciones del modelo.") }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, isUploading = false, errorMessage = null) }

        viewModelScope.launch {
            val jobId = "job_${UUID.randomUUID()}"
            val uploadedMediaList = mutableListOf<MediaInput>()
            try {
                // Step 1: upload the private cached copies before building the request.
                if (state.attachedMedia.isNotEmpty()) {
                    _uiState.update { it.copy(isUploading = true, uploadProgress = 0f) }
                    state.attachedMedia.forEachIndexed { index, media ->
                        val uri = Uri.parse(media.uriString)
                        val scheme = uri.scheme?.lowercase()
                        val remoteUrl = when {
                            media.remoteUrl != null -> media.remoteUrl
                            scheme == "http" || scheme == "https" -> media.uriString
                            else -> {
                                val bytes = withContext(Dispatchers.IO) {
                                    if (scheme == "file") {
                                        val file = java.io.File(uri.path ?: "")
                                        file.takeIf { it.isFile }?.readBytes()
                                    } else {
                                        app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                    }
                                } ?: throw IllegalStateException("No se pudo leer el archivo multimedia adjunto.")
                                val fileName = media.fileName ?: extractFileName(uri, media.mimeType)
                                app.atlasProvider.uploadMedia(bytes, fileName, media.mimeType).getOrThrow()
                            }
                        }
                        uploadedMediaList += MediaInput(
                            type = if (media.slotType in listOf(MediaSlotType.VIDEO_INPUT, MediaSlotType.VIDEO_REFERENCE)) Modality.VIDEO else Modality.IMAGE,
                            uriString = media.uriString,
                            mimeType = media.mimeType,
                            fileName = media.fileName ?: extractFileName(uri, media.mimeType),
                            remoteUrl = remoteUrl,
                            slotType = media.slotType
                        )
                        _uiState.update {
                            it.copy(uploadProgress = (index + 1f) / state.attachedMedia.size.toFloat())
                        }
                    }
                }

                // Step 2: build a payload using only the selected model's native fields.
                val parametersWithMeta = state.parameters.filter { (key, value) ->
                    value.isNotBlank() && model.parameters.any { it.key == key }
                }.toMutableMap()
                parametersWithMeta["__native_schema"] = "true"
                model.parameters.forEach { p -> p.nativeType?.let { parametersWithMeta["__type_" + p.key] = it } }
                model.firstFrameField?.let { parametersWithMeta["__first_frame_field"] = it }
                model.lastFrameField?.let { parametersWithMeta["__last_frame_field"] = it }
                model.imagesArrayField?.let { parametersWithMeta["__images_array_field"] = it }
                model.videoField?.let { parametersWithMeta["__video_field"] = it }
                model.videosArrayField?.let { parametersWithMeta["__videos_array_field"] = it }
                model.inputImageField?.let { parametersWithMeta["__input_field_name"] = it }

                val request = GenerationRequest(
                    id = jobId,
                    providerId = model.providerId,
                    modelId = model.id,
                    modality = state.selectedModality,
                    prompt = state.prompt.trim(),
                    negativePrompt = "",
                    parameters = parametersWithMeta,
                    mediaInputs = uploadedMediaList
                )

                val submitted = generationRepo.submitGeneration(request).getOrThrow()
                if(submitted.status != com.vidadomu.nexus.core.model.GenerationStatus.COMPLETED) GenerationPollingWorker.enqueue(app, jobId)
                onSuccess(jobId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                android.util.Log.e("CreateViewModel", "Generation pipeline failed: ${e.message}", e)
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Error al iniciar la generación.")
                }
            } finally {
                _uiState.update {
                    it.copy(isSubmitting = false, isUploading = false, uploadProgress = null)
                }
            }
        }
    }

    /**
     * Extracts a reasonable file name from a URI and MIME type.
     */
    private fun extractFileName(uri: Uri, mimeType: String): String {
        // Try to get the display name from ContentResolver
        val name = if (uri.scheme.equals("content", ignoreCase = true)) {
            try {
                app.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            } catch (_: Exception) {
                null
            }
        } else {
            uri.lastPathSegment
        }
        if (!name.isNullOrBlank()) return name

        // Fallback: generate from MIME type
        val ext = when {
            mimeType.startsWith("image/jpeg") -> "jpg"
            mimeType.startsWith("image/png") -> "png"
            mimeType.startsWith("image/webp") -> "webp"
            mimeType.startsWith("video/mp4") -> "mp4"
            mimeType.startsWith("video/") -> "mp4"
            mimeType.startsWith("audio/mpeg") -> "mp3"
            mimeType.startsWith("audio/") -> "aac"
            else -> "bin"
        }
        return "nexus_input_${System.currentTimeMillis()}.$ext"
    }
}
