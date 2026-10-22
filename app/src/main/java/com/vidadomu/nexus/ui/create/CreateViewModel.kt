package com.vidadomu.nexus.ui.create

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.ContractMediaField
import com.vidadomu.nexus.core.model.AttachedMedia
import com.vidadomu.nexus.core.model.GenerationRequest
import com.vidadomu.nexus.core.model.FormContract
import com.vidadomu.nexus.core.model.MediaInput
import com.vidadomu.nexus.core.model.MediaSlotType
import com.vidadomu.nexus.core.model.ModelCapability
import com.vidadomu.nexus.core.model.ModelInputMode
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.core.work.GenerationPollingWorker
import kotlinx.coroutines.CancellationException
import com.vidadomu.nexus.ui.components.MediaSniffer
import com.vidadomu.nexus.ui.components.SubmitFailure
import com.vidadomu.nexus.ui.components.describeSubmitFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
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
    val schemaUnsupported: Boolean = false,
    val uploadProgress: Float? = null,      // 0..1 upload progress (null = indeterminate)
    val errorMessage: String? = null,
    val submitFailure: SubmitFailure? = null
) {
    val missingRequiredFields: List<String>
        get() = selectedModel?.let { model ->
            if(model.contractEndpoint!=null) return@let buildList {
                if(model.promptRequired && prompt.isBlank()) add("Instrucciones")
                if(model.providerModelId=="suno/music-generate") {
                    if(parameters["mode"]=="custom") {
                        if(parameters["title"].isNullOrBlank()) add("Título")
                        if(parameters["tags"].isNullOrBlank()) add("Estilo musical")
                        if(parameters["instrumental"]!="true" && parameters["lyrics"].isNullOrBlank()) add("Letra")
                    } else if(prompt.isBlank()) add("Descripción de la canción")
                }
                model.contractMedia.filter { it.required && attachedMedia.none { media -> media.fieldKey==it.key } }.forEach { add(it.label) }
                if(model.providerId=="google_flow" && model.modalities.contains(Modality.VIDEO) && parameters["mode"]=="Fotogramas" &&
                    attachedMedia.none { it.fieldKey=="initial_frame" }) add("Fotograma Inicio")
                if(model.providerId=="google_flow" && model.modalities.contains(Modality.VIDEO) && parameters["mode"]=="Referencias" &&
                    attachedMedia.none { it.fieldKey=="reference_ids" }) add("Imágenes de referencia")
                model.parameters.filter { it.isRequired && parameters[it.key].isNullOrBlank() }.forEach { add(it.displayName) }
            }
            else buildList<String> {
                addAll(model.nativeRequiredFields.filter { field ->
                    when (field) {
                        "model" -> false
                        in model.contractMedia.map { it.key } -> attachedMedia.none { media -> media.fieldKey==field }
                        "prompt" -> prompt.isBlank()
                        model.firstFrameField -> attachedMedia.none { it.fieldKey==null && it.slotType == MediaSlotType.PRIMARY_IMAGE }
                        model.lastFrameField -> attachedMedia.none { it.fieldKey==null && it.slotType == MediaSlotType.LAST_FRAME }
                        model.imagesArrayField -> attachedMedia.none { it.fieldKey==null && it.slotType == MediaSlotType.REFERENCE_IMAGE }
                        model.videoField -> attachedMedia.none { it.fieldKey==null && it.slotType == MediaSlotType.VIDEO_INPUT }
                        model.videosArrayField -> attachedMedia.none { it.fieldKey==null && it.slotType == MediaSlotType.VIDEO_REFERENCE }
                        else -> parameters[field].isNullOrBlank()
                    }
                }.map { field -> when (field) {
                    "prompt" -> "Prompt"
                    model.firstFrameField -> "Imagen de entrada"
                    model.lastFrameField -> "Último fotograma"
                    model.imagesArrayField -> "Imágenes de referencia"
                    model.videoField, model.videosArrayField -> "Vídeo de entrada"
                    else -> model.parameters.find { it.key == field }?.displayName ?: field
                } })
                if(model.providerId=="google_flow" && model.modalities.contains(Modality.VIDEO) && parameters["mode"]=="Fotogramas" &&
                    attachedMedia.none { it.fieldKey=="initial_frame" }) add("Fotograma Inicio")
                if(model.providerId=="google_flow" && model.modalities.contains(Modality.VIDEO) && parameters["mode"]=="Referencias" &&
                    attachedMedia.none { it.fieldKey=="reference_ids" }) add("Imágenes de referencia")
            }
        }.orEmpty()
    val inputMediaUri: Uri?
        get() = attachedMedia.firstOrNull()?.uriString?.let { Uri.parse(it) }
    val inputMediaMime: String?
        get() = attachedMedia.firstOrNull()?.mimeType
}

class CreateViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NexusApplication
    private val modelCatalogRepo = app.modelCatalogRepository
    private val generationRepo = app.generationRepository
    val creationPreferences = CreationPreferences(app)
    val comparison = ComparisonController(app, viewModelScope)
    private val restoredDraft = creationPreferences.draft()
    private val workDraftStore = ModelWorkDraftStore(app)
    @Volatile private var workDrafts = workDraftStore.load()
    private var pendingSwitchParameters: Pair<String, Map<String, String>>? = null
    @Volatile private var switchUndo: ModelSwitchSnapshot? = null
    private var switchNoticeId = 0L
    private val _modelSwitchNotice = MutableStateFlow<ModelSwitchNotice?>(null)
    val modelSwitchNotice: StateFlow<ModelSwitchNotice?> = _modelSwitchNotice.asStateFlow()
    private var draftRestored = false
    private var localPreview = false
    private var pendingMediaCopies = 0
    private val mediaSession = MediaSelectionSession()
    private var schemaJob: kotlinx.coroutines.Job? = null
    private var schemaRequest = 0
    private fun loadNativeConfiguration(model: NexusModel) {
        val request = ++schemaRequest
        schemaJob?.cancel()
        if (model.providerId != com.vidadomu.nexus.provider.atlas.AtlasCloudProvider.PROVIDER_ID) {
            val parameters = takeSwitchParameters(model) ?: creationPreferences.values(model)
            _uiState.update {
                it.copy(
                    selectedModel = model.copy(parameters=com.vidadomu.nexus.core.model.FormContract.normalize(model.parameters)),
                    parameters = parameters,
                    attachedMedia = compatibleModelMedia(model, it.attachedMedia),
                    isLoadingSchema = false,
                    schemaReady = true,
                    schemaUnsupported = false,
                    schemaMessage = null
                )
            }
            restoreDraftIfMatching(model)
            return
        }
        _uiState.update { it.copy(isLoadingSchema = true, schemaReady = false, schemaMessage = null, schemaUnsupported=false) }
        schemaJob = viewModelScope.launch {
            try {
                val config = com.vidadomu.nexus.provider.atlas.NativeModelSchema.load(model)
                if (schemaRequest == request && _uiState.value.selectedModel?.id == model.id) {
                    val parameters = takeSwitchParameters(config.model) ?: creationPreferences.values(config.model)
                    _uiState.update { it.copy(selectedModel = config.model,
                        parameters = parameters,
                        attachedMedia = compatibleModelMedia(config.model, it.attachedMedia),
                        schemaReady = config.unsupportedRequired.isEmpty(),
                        schemaUnsupported = config.unsupportedRequired.isNotEmpty(),
                        schemaMessage = if(config.unsupportedRequired.isEmpty()) config.notices.takeIf { notes -> notes.isNotEmpty() }?.joinToString("\n") else
                            "Este modelo requiere entradas aún no compatibles: " + config.unsupportedRequired.joinToString()) }
                    restoreDraftIfMatching(config.model)
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if(schemaRequest == request) _uiState.update { it.copy(schemaReady = false, schemaUnsupported=false, schemaMessage = e.message ?: "No se pudo consultar el esquema.") }
            } finally { if(schemaRequest == request) _uiState.update { it.copy(isLoadingSchema = false) } }
        }
    }

    private val _uiState = MutableStateFlow(CreateUiState(selectedModality=restoredDraft?.modality ?: Modality.IMAGE))
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    init {
        observeDraft()
        viewModelScope.launch {
            modelCatalogRepo.models.collect { models ->
                if(localPreview) return@collect
                val previousId = _uiState.value.selectedModel?.id
                _uiState.update { current ->
                    val filtered = models.filter { it.modalities.contains(current.selectedModality) }
                        .sortedWith(compareBy<NexusModel> { it.inputMode == ModelInputMode.TEXT_ONLY }.then(NexusModel.DEFAULT_COMPARATOR))
                    val lastModelId = if(!draftRestored && restoredDraft!=null) restoredDraft.modelId else app.secureStorage.getLastModelId(current.selectedModality)
                    val currentSelectedStillValid = filtered.any { it.id == current.selectedModel?.id }
                    val newSelected = when {
                        currentSelectedStillValid -> current.selectedModel
                        lastModelId != null && filtered.any { it.id == lastModelId } -> filtered.find { it.id == lastModelId }
                        else -> filtered.firstOrNull()
                    }
                    val defaultParams = newSelected?.parameters?.associate { it.key to it.defaultValue } ?: emptyMap()
                    current.copy(
                        availableModels = filtered,
                        selectedModel = newSelected,
                        parameters = if(currentSelectedStillValid) current.parameters else defaultParams,
                        attachedMedia = current.attachedMedia
                    )
                }
                if(previousId != _uiState.value.selectedModel?.id) mediaSession.reset()
                if(previousId != _uiState.value.selectedModel?.id || (!_uiState.value.schemaReady && !_uiState.value.isLoadingSchema))
                    _uiState.value.selectedModel?.let { loadNativeConfiguration(it) }
            }
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeDraft() = viewModelScope.launch {
        _uiState.map {state -> state.selectedModel?.takeIf {state.schemaReady && !state.isLoadingSchema}?.let {model ->
            model.providerId to CreationDraft(model.id,state.selectedModality,state.prompt,state.parameters,state.attachedMedia,state.negativePrompt)
        }}.distinctUntilChanged().debounce(400).collect {entry ->
            if(entry!=null) withContext(Dispatchers.IO) {creationPreferences.saveDraft(entry.second,entry.first)}
        }
    }
    private fun isMediaAvailable(item:AttachedMedia):Boolean {
        val uri=Uri.parse(item.uriString)
        return uri.scheme in setOf("https","http") || (uri.scheme=="file" && java.io.File(uri.path.orEmpty()).isFile)
    }
    private fun takeSwitchParameters(model:NexusModel):Map<String,String>? {
        val pending=pendingSwitchParameters?.takeIf {it.first==model.id} ?: return null
        pendingSwitchParameters=null
        return FormContract.restoredValues(model,pending.second)
    }
    /** Saves the work of the model being left and plans what the target model starts with. */
    private fun switchWorkDraft(current:CreateUiState,target:NexusModel):ModelSwitchPlan? {
        val leaving=current.selectedModel?.takeIf {it.id!=target.id} ?: return null
        switchUndo=ModelSwitchUndo.snapshot(current)
        val configured=current.schemaReady && !current.isLoadingSchema
        val saved=ModelWorkDraft(leaving.id,current.prompt,current.negativePrompt,
            if(configured) current.parameters else ModelWorkDrafts.find(workDrafts,leaving.id)?.parameters ?: current.parameters,
            current.attachedMedia)
        workDrafts=ModelWorkDrafts.remember(workDrafts,saved)
        workDraftStore.save(workDrafts)
        val plan=ModelWorkDrafts.plan(saved,ModelWorkDrafts.find(workDrafts,target.id),::isMediaAvailable)
        pendingSwitchParameters=target.id to plan.parameters
        return plan
    }
    private fun forgetModelSwitch() {
        switchUndo=null
        _modelSwitchNotice.value=null
    }
    private fun announceSwitch(target:NexusModel,plan:ModelSwitchPlan?) {
        if(plan==null || switchUndo==null) return
        val parked=ModelSwitchUndo.parked(plan.media,compatibleModelMedia(target,plan.media)).size
        _modelSwitchNotice.value=ModelSwitchNotice(++switchNoticeId,target.displayName,parked)
    }
    /** Restores exactly the state before the last model switch; the new model's work stays saved as its draft. */
    fun undoModelSwitch():Boolean {
        val snapshot=switchUndo ?: return false
        mediaSession.reset()
        switchWorkDraft(_uiState.value,snapshot.model)
        forgetModelSwitch()
        pendingSwitchParameters=snapshot.model.id to snapshot.parameters
        app.secureStorage.setLastModelId(snapshot.modality,snapshot.model.id)
        _uiState.update {ModelSwitchUndo.restore(it,snapshot)}
        loadNativeConfiguration(snapshot.model)
        return true
    }
    private fun ModelSwitchPlan.notice():String? =
        if(missingMedia>0) "Borrador del modelo recuperado. Vuelve a adjuntar los archivos que ya no están disponibles." else null
    private fun restoreDraftIfMatching(model:NexusModel) {
        val draft=restoredDraft?.takeIf {!draftRestored && it.modelId==model.id} ?: return
        draftRestored=true
        val available=draft.media.filter(::isMediaAvailable)
        _uiState.update {it.copy(prompt=draft.prompt,negativePrompt=draft.negativePrompt,parameters=com.vidadomu.nexus.core.model.FormContract.restoredValues(model,draft.parameters),
            attachedMedia=compatibleModelMedia(model,available),errorMessage=if(available.size<draft.media.size) "Borrador recuperado. Vuelve a adjuntar los archivos que ya no están disponibles." else null)}
    }
    fun applyPreset(preset:CreationPreset) { forgetModelSwitch(); val model=_uiState.value.selectedModel ?: return
        _uiState.update {it.copy(parameters=com.vidadomu.nexus.core.model.FormContract.restoredValues(model,preset.values),errorMessage=null)}
    }
    fun restoreModelDefaults() { forgetModelSwitch();val model=_uiState.value.selectedModel ?: return
        _uiState.update {it.copy(parameters=model.parameters.associate {p->p.key to p.defaultValue},errorMessage=null)}
    }
    fun preparePrevious(job:com.vidadomu.nexus.core.model.GenerationJob) { forgetModelSwitch();
        val model=modelCatalogRepo.getModelById(job.modelId)
        if(model==null) {_uiState.update {it.copy(errorMessage="El modelo de esta creación ya no está disponible.")};return}
        selectModality(job.modality); selectModel(model); updatePrompt(job.prompt)
        viewModelScope.launch {
            schemaJob?.join()
            if(_uiState.value.selectedModel?.id==model.id) _uiState.update {state ->state.copy(negativePrompt=job.negativePrompt,
                parameters=com.vidadomu.nexus.core.model.FormContract.restoredValues(state.selectedModel!!,job.parameters),
                attachedMedia=emptyList(),errorMessage=if(job.inputMediaUri!=null) "Parámetros recuperados. Adjunta los archivos de entrada para continuar." else null)}
        }
    }
    /**
     * Abre en el Lienzo un plan del asistente (modelo ya configurado con su schema, valores y medios
     * en sus ranuras). Sustituye el borrador actual. No envía nada: el usuario revisa y pulsa Generar.
     */
    fun openAssistantPlan(state:CreateUiState) { forgetModelSwitch();
        schemaJob?.cancel(); schemaRequest++
        draftRestored=true; mediaSession.reset(); pendingSwitchParameters=null
        val model=state.selectedModel ?: return
        _uiState.value=state.copy(availableModels=modelCatalogRepo.models.value.filter {it.modalities.contains(state.selectedModality)}
            .ifEmpty {listOf(model)},isLoadingSchema=false,schemaReady=true,errorMessage=null)
    }
    fun prepareAgentDraft(draft:com.vidadomu.nexus.agent.AgentDraft) { forgetModelSwitch();
        check(com.vidadomu.nexus.BuildConfig.DEBUG)
        viewModelScope.launch {
            try {
                val models=kotlinx.coroutines.withTimeout(12000) {modelCatalogRepo.models.first {it.isNotEmpty()}}
                val model=models.singleOrNull {it.id==draft.modelId && it.providerId==draft.providerId}
                    ?: error("El modelo del borrador ya no está disponible.")
                draftRestored=true;mediaSession.reset();pendingSwitchParameters=null
                _uiState.value=CreateUiState(selectedModality=model.modalities.first(),selectedModel=model,
                    availableModels=models.filter {it.modalities.any(model.modalities::contains)},prompt=draft.prompt)
                loadNativeConfiguration(model);schemaJob?.join()
                val configured=_uiState.value.selectedModel ?: error("No se pudo cargar el modelo.")
                if(configured.id!=draft.modelId || configured.providerId!=draft.providerId) return@launch
                check(_uiState.value.schemaReady) {"No se pudo verificar el esquema del borrador. Vuelve a intentarlo."}
                com.vidadomu.nexus.agent.AgentDraftContract.validate(configured,draft.prompt,draft.parameters)
                _uiState.update {it.copy(prompt=draft.prompt,parameters=FormContract.restoredValues(configured,draft.parameters))}
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {_uiState.update {it.copy(errorMessage=e.message ?: "No se pudo abrir el borrador preparado.")}}
        }
    }
    fun loadLocalPreview(model:NexusModel, media:List<AttachedMedia>) {
        check(com.vidadomu.nexus.BuildConfig.OFFLINE_QA)
        if(localPreview) return
        localPreview=true; schemaJob?.cancel(); schemaRequest++
        _uiState.value=CreateUiState(selectedModality=model.modalities.first(),selectedModel=model,availableModels=listOf(model),
            parameters=model.parameters.associate {it.key to it.defaultValue},attachedMedia=media,schemaReady=true)
        restoreDraftIfMatching(model)
        _uiState.update {it.copy(attachedMedia=(it.attachedMedia+media).distinctBy {item->Triple(item.slotType,item.fieldKey,item.uriString)})}
    }

    fun swapFlowFrames() { forgetModelSwitch();
        _uiState.update { state ->
            val start = state.attachedMedia.firstOrNull { it.fieldKey == "initial_frame" } ?: return@update state
            val end = state.attachedMedia.firstOrNull { it.fieldKey == "end_frame" } ?: return@update state
            val (swappedStart, swappedEnd) = com.vidadomu.nexus.provider.flow.FlowReferences.swapFrames(start, end)
            state.copy(attachedMedia = state.attachedMedia.map { media -> when (media.id) {
                start.id -> swappedStart
                end.id -> swappedEnd
                else -> media
            }})
        }
    }

    fun selectModality(modality: Modality) {
        forgetModelSwitch()
        mediaSession.reset()
        val allModels = modelCatalogRepo.models.value
        val filtered = allModels.filter { it.modalities.contains(modality) }
            .sortedWith(compareBy<NexusModel> { it.inputMode == ModelInputMode.TEXT_ONLY }.then(NexusModel.DEFAULT_COMPARATOR))
        val lastModelId = app.secureStorage.getLastModelId(modality)
        val newSelected = filtered.find { it.id == lastModelId } ?: filtered.firstOrNull()
        val defaultParams = newSelected?.parameters?.associate { it.key to it.defaultValue } ?: emptyMap()
        val plan = newSelected?.let { switchWorkDraft(_uiState.value, it) }

        _uiState.update {
            it.copy(
                selectedModality = modality,
                availableModels = filtered,
                selectedModel = newSelected,
                prompt = plan?.prompt ?: it.prompt,
                negativePrompt = plan?.negativePrompt ?: it.negativePrompt,
                parameters = defaultParams,
                attachedMedia = plan?.media ?: it.attachedMedia,
                errorMessage = plan?.notice()
            )
        }
        newSelected?.let { announceSwitch(it, plan); loadNativeConfiguration(it) }
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
        forgetModelSwitch()
        mediaSession.reset()
        val modality = _uiState.value.selectedModality.takeIf { it in model.modalities } ?: model.modalities.first()
        app.secureStorage.setLastModelId(modality, model.id)
        val defaultParams = model.parameters.associate { it.key to it.defaultValue }
        val plan = switchWorkDraft(_uiState.value, model)
        _uiState.update { current ->
            current.copy(
                selectedModel = model,
                selectedModality = modality,
                availableModels = modelCatalogRepo.models.value.filter { modality in it.modalities },
                prompt = plan?.prompt ?: current.prompt,
                negativePrompt = plan?.negativePrompt ?: current.negativePrompt,
                parameters = defaultParams,
                showAdvanced = false,
                attachedMedia = plan?.media ?: current.attachedMedia,
                errorMessage = plan?.notice()
            )
        }
        announceSwitch(model, plan)
        loadNativeConfiguration(model)
    }

    fun retryConfiguration() {
        if (!_uiState.value.isLoadingSchema) _uiState.value.selectedModel?.let { loadNativeConfiguration(it) }
    }



    /**
     * Persists an incoming URI into a private app cache file so that Android transient
     * URI permissions are never lost, and uploads can read the bytes reliably.
     */
    private suspend fun persistMediaUri(uri: Uri, mimeType: String): Result<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        val scheme = uri.scheme?.lowercase()
        val providerMime = if (scheme == "http" || scheme == "https") null else runCatching { app.contentResolver.getType(uri) }.getOrNull()
        val resolvedMime = mimeType.takeUnless(MediaSniffer::isGeneric) ?: providerMime?.takeUnless(MediaSniffer::isGeneric)
            ?: MediaSniffer.resolve(if (scheme == "http" || scheme == "https") null else sniffHeader(uri), mimeType, providerMime)
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
        var incompleteCopy: java.io.File? = null
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
            val dir = java.io.File(app.filesDir, "draft_media")
            check(dir.exists() || dir.mkdirs()) { "No se pudo preparar la caché privada de medios." }
            val protectedUris = (_uiState.value.attachedMedia + creationPreferences.draft()?.media.orEmpty() + workDrafts.flatMap { it.media } + switchUndo?.media.orEmpty()).map {it.uriString}.toSet() + generationRepo.allJobs.value.mapNotNull {it.inputMediaUri}
            val protectedPaths = protectedUris.mapNotNull { runCatching {Uri.parse(it).path}.getOrNull() }.toSet()
            val occupied = PrivateMediaStorage.prepare(dir, protectedPaths)
            val targetFile = java.io.File(dir, "nexus_media_${UUID.randomUUID()}.$ext")
            incompleteCopy = targetFile
            val input = app.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Android no permitió leer el archivo seleccionado.")
            input.use {
                targetFile.outputStream().use { output ->
                    val buffer=ByteArray(65536); var total=0L
                    while(true) { val count=it.read(buffer); if(count<0) break
                        total+=count
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        require(total<=512L*1024*1024) {"El archivo supera el límite local de 512 MB."}
                        require(occupied+total<=PrivateMediaStorage.MAX_BYTES) {"Los medios de borrador ocupan 2 GB. Quita los archivos que ya no necesites."}
                        output.write(buffer,0,count)
                    }
                }
            }
            check(targetFile.isFile && targetFile.length() > 0) { "El archivo seleccionado está vacío." }
            incompleteCopy = null
            Result.success(Uri.fromFile(targetFile) to resolvedMime)
        } catch(e: CancellationException) { throw e
        } catch (e: Exception) {
            android.util.Log.e("CreateViewModel", "Media preparation failed: ${e.javaClass.simpleName}")
            Result.failure(e)
        } finally { incompleteCopy?.delete() }
    }

    private fun sniffHeader(uri: Uri): ByteArray? =
        runCatching { app.contentResolver.openInputStream(uri)?.use(MediaSniffer::readHeader) }.getOrNull()

    private fun beginMediaCopy() {
        pendingMediaCopies++
        _uiState.update { it.copy(isPreparingMedia = true, errorMessage = null) }
    }

    private fun endMediaCopy() {
        pendingMediaCopies = (pendingMediaCopies - 1).coerceAtLeast(0)
        _uiState.update { it.copy(isPreparingMedia = pendingMediaCopies > 0) }
    }

    private fun cacheMedia(rawUri: Uri, rawMime: String, slot: MediaSlotType, fieldKey: String? = null, append: Boolean = slot == MediaSlotType.VIDEO_REFERENCE, onReady: (Uri, String) -> Unit) {
        val expected=if(slot in setOf(MediaSlotType.VIDEO_INPUT,MediaSlotType.VIDEO_REFERENCE)) "video/" else null
        if(expected!=null && !rawMime.startsWith(expected) && !MediaSniffer.isGeneric(rawMime)) {_uiState.update {it.copy(errorMessage="Elige un archivo de vídeo.")};return}
        val token = mediaSession.begin(slot, append = append, fieldKey = fieldKey)
        beginMediaCopy()
        viewModelScope.launch {
            try {
                val (uri, mimeType) = persistMediaUri(rawUri, rawMime).getOrThrow()
                require(expected==null || mimeType.startsWith(expected)) { "Elige un archivo de vídeo." }
                if(uri.scheme=="file") withContext(Dispatchers.IO) { LocalMediaInspector.inspect(app,uri,mimeType) }
                if (mediaSession.accepts(token)) onReady(uri, mimeType)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (mediaSession.accepts(token)) _uiState.update {
                    it.copy(errorMessage = (e as? IllegalArgumentException)?.message ?: "No se pudo preparar el archivo. Comprueba que sigue disponible y vuelve a seleccionarlo.")
                }
            } finally {
                endMediaCopy()
            }
        }
    }

    /** Batch import uses the very same schema slot as the visible form. No implicit edit-model switch. */
    internal fun addCanvasMedia(fieldKey: String, expectedModelKey: String, selection: List<Pair<Uri, String>>) {
        forgetModelSwitch()
        val snapshot = _uiState.value
        val model = snapshot.selectedModel ?: return
        if (model.providerId + "/" + model.id != expectedModelKey) return
        if (!snapshot.schemaReady) return
        val field = CanvasInputPlan.fields(model, snapshot.parameters).find { it.key == fieldKey } ?: return
        if (selection.isEmpty()) return
        val token = mediaSession.begin(field.slot, append = field.maximum > 1, fieldKey = field.contract?.key)
        beginMediaCopy()
        viewModelScope.launch {
            try {
                val saved = selection.distinctBy { it.first.toString() }.take(minOf(field.remaining(snapshot.attachedMedia), CanvasInputPlan.PICKER_BATCH))
                    .mapNotNull { (uri, mime) ->
                        val local = persistMediaUri(uri, mime).getOrNull() ?: return@mapNotNull null
                        if (!field.acceptsMime(local.second)) return@mapNotNull null
                        if (local.first.scheme == "file" && withContext(Dispatchers.IO) {
                            runCatching { LocalMediaInspector.inspect(app, local.first, local.second) }.isFailure
                        }) return@mapNotNull null
                        local
                    }
                if (!mediaSession.accepts(token)) return@launch
                _uiState.update { current ->
                    val active = current.selectedModel
                    if (!mediaSession.accepts(token) || active?.id != model.id || active.providerId != model.providerId ||
                        CanvasInputPlan.fields(active, current.parameters).none { it == field }) return@update current
                    val retained = if (field.maximum == 1 && saved.isNotEmpty()) current.attachedMedia.filterNot(field::contains) else current.attachedMedia
                    val known = retained.filter(field::contains).map { it.uriString }.toSet()
                    val added = saved.filter { it.first.toString() !in known }.take(field.remaining(retained)).map { (uri, mime) ->
                        AttachedMedia(uriString = uri.toString(), mimeType = mime, slotType = field.slot,
                            fieldKey = field.contract?.key, fileName = extractFileName(uri, mime))
                    }
                    current.copy(attachedMedia = retained + added, errorMessage = when {
                        saved.isEmpty() -> "No se pudo preparar la selección para ${field.label}."
                        saved.size < selection.distinctBy { it.first.toString() }.size -> "Algunos archivos no se añadieron. Revisa el formato y el límite de ${field.label}."
                        else -> null
                    })
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (mediaSession.accepts(token)) _uiState.update { it.copy(errorMessage = "No se pudieron preparar los archivos. Vuelve a seleccionarlos.") }
            } finally { endMediaCopy() }
        }
    }

    fun setContractMedia(field:ContractMediaField, uri:Uri, mime:String) { forgetModelSwitch();
        val selectedId=_uiState.value.selectedModel?.id
        if(!mime.startsWith(field.mime.substringBefore('/'))) {
            _uiState.update { it.copy(errorMessage="Elige un archivo compatible con ${field.label}.") };return
        }
        cacheMedia(uri,mime,MediaSlotType.REFERENCE_IMAGE,fieldKey=field.key,append=field.maximum>1) { local,resolved ->
            if(_uiState.value.selectedModel?.id==selectedId) _uiState.update { state ->
                val existing=state.attachedMedia.filter { it.fieldKey==field.key }
                val retained=if(field.maximum==1) state.attachedMedia.filterNot { it.fieldKey==field.key } else state.attachedMedia
                if(field.maximum>1 && existing.size>=field.maximum) state.copy(errorMessage="Has alcanzado el máximo de archivos para ${field.label}.")
                else state.copy(attachedMedia=retained+AttachedMedia(uriString=local.toString(),mimeType=resolved,slotType=MediaSlotType.REFERENCE_IMAGE,fieldKey=field.key),errorMessage=null)
            }
        }
    }

    fun updatePrompt(text: String) { forgetModelSwitch();
        _uiState.update { it.copy(prompt = text, errorMessage = null) }
    }

    fun updateNegativePrompt(text: String) { forgetModelSwitch();
        _uiState.update { it.copy(negativePrompt = text) }
    }

    fun updateParameter(key: String, value: String) { forgetModelSwitch();
        val old = _uiState.value
        if (old.selectedModel?.providerId == "google_flow" && key == "mode" && old.parameters[key] != value) {
            mediaSession.reset()
            val fields = CanvasInputPlan.fields(old.selectedModel, old.parameters + (key to value))
            _uiState.update { state -> state.copy(attachedMedia = state.attachedMedia.filter { media -> fields.any { it.contains(media) } }) }
        }
        _uiState.update {
            val updated = it.parameters.toMutableMap()
            updated[key] = value
            it.copy(parameters = updated)
        }
    }

    fun setPrimaryImage(rawUri: Uri, rawMime: String) { forgetModelSwitch();
        cacheMedia(rawUri, rawMime, MediaSlotType.PRIMARY_IMAGE) { uri, mimeType ->
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

    fun setLastFrameImage(rawUri: Uri, rawMime: String) { forgetModelSwitch();
        cacheMedia(rawUri, rawMime, MediaSlotType.LAST_FRAME) { uri, mimeType ->
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

    fun addReferenceImages(urisWithMime: List<Pair<Uri, String>>) { forgetModelSwitch();
        if (urisWithMime.isEmpty()) return
        val token = mediaSession.begin(MediaSlotType.REFERENCE_IMAGE, append = true)
        beginMediaCopy()
        viewModelScope.launch {
            try {
                val persisted = urisWithMime.mapNotNull { (uri, mime) ->
                    val saved = persistMediaUri(uri, mime).getOrNull()
                    saved?.takeIf { (local, type) ->
                        type.startsWith("image/") && (local.scheme != "file" || withContext(Dispatchers.IO) { runCatching { LocalMediaInspector.inspect(app, local, type) }.isSuccess })
                    }
                }
                if (!mediaSession.accepts(token)) return@launch
                if (persisted.isEmpty()) {
                    _uiState.update { it.copy(errorMessage = "No se pudo conservar ninguna de las imágenes seleccionadas.") }
                    return@launch
                }
                _uiState.update { current ->
                    if(!mediaSession.accepts(token)) return@update current
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
                        errorMessage = when {
                            remainingSlots < persisted.size -> "Este modelo admite $maxAllowed imágenes. Se han añadido ${toAdd.size}."
                            persisted.size < urisWithMime.size -> "Alguna imagen no pudo conservarse."
                            else -> null
                        }
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if(mediaSession.accepts(token)) _uiState.update { it.copy(errorMessage = "No se pudieron conservar las imágenes: ${e.message ?: "error de lectura"}") }
            } finally {
                endMediaCopy()
            }
        }
    }

    fun setVideoInput(rawUri: Uri, rawMime: String) { forgetModelSwitch();
        val model = _uiState.value.selectedModel
        val targetSlot = if(model?.videoField == null && model?.videosArrayField != null)
            MediaSlotType.VIDEO_REFERENCE else MediaSlotType.VIDEO_INPUT
        cacheMedia(rawUri, rawMime, targetSlot) { uri, mimeType ->
            val fileName = extractFileName(uri, mimeType)
            _uiState.update { current ->
                val isArray = current.selectedModel?.videoField == null && current.selectedModel?.videosArrayField != null
                val slot = if(isArray) MediaSlotType.VIDEO_REFERENCE else MediaSlotType.VIDEO_INPUT
                if(isArray && current.attachedMedia.count { it.slotType == slot } >= (current.selectedModel?.maxVideos ?: 0)) return@update current
                val filtered = if(isArray) current.attachedMedia else current.attachedMedia.filter { it.slotType != slot }
                current.copy(
                    attachedMedia = listOf(
                        AttachedMedia(
                            uriString = uri.toString(),
                            mimeType = mimeType,
                            slotType = slot,
                            fileName = fileName
                        )
                    ) + filtered,
                    errorMessage = null
                )
            }
        }
    }

    fun removeMedia(id: String) { forgetModelSwitch();
        _uiState.value.attachedMedia.find { it.id == id }?.let { mediaSession.invalidate(it.slotType,it.fieldKey) }
        _uiState.update { current ->
            current.copy(attachedMedia = current.attachedMedia.filter { it.id != id })
        }
    }

    fun clearSlot(slotType: MediaSlotType) { forgetModelSwitch();
        mediaSession.invalidate(slotType)
        _uiState.update { current ->
            current.copy(attachedMedia = current.attachedMedia.filter { it.slotType != slotType })
        }
    }

    fun clearAllMedia() { forgetModelSwitch();
        mediaSession.reset()
        _uiState.update { it.copy(attachedMedia = emptyList()) }
    }

    /** Wait for the real media contract before attaching a frame or animating an image. */
    private var reuseImageJob: kotlinx.coroutines.Job? = null
    fun reuseImageInput(uri: Uri, mimeType: String?) { forgetModelSwitch();
        reuseImageJob?.cancel()
        reuseImageJob = viewModelScope.launch {
            selectModality(Modality.VIDEO)
            var expectedId = _uiState.value.selectedModel?.id
            schemaJob?.join()
            if (_uiState.value.selectedModel?.id != expectedId) return@launch
            fun acceptsImage(m: NexusModel) = m.inputMode != ModelInputMode.VIDEO_INPUT && (
                m.inputMode in setOf(ModelInputMode.SINGLE_IMAGE, ModelInputMode.DUAL_FRAME, ModelInputMode.MULTI_IMAGE) ||
                m.firstFrameField != null || m.inputImageField != null || m.imagesArrayField != null ||
                m.contractMedia.any { it.mime.startsWith("image/") })
            if (_uiState.value.selectedModel?.let(::acceptsImage) != true) {
                val target = _uiState.value.availableModels.firstOrNull(::acceptsImage)
                if (target != null) { selectModel(target); expectedId=target.id; schemaJob?.join() }
                if (_uiState.value.selectedModel?.id != expectedId) return@launch
            }
            if (_uiState.value.selectedModel?.let(::acceptsImage) != true || !_uiState.value.schemaReady) {
                _uiState.update { it.copy(errorMessage = "Selecciona un modelo de vídeo que admita imágenes para usar este archivo.") }
                return@launch
            }
            val remote = uri.scheme?.lowercase() in setOf("http", "https")
            val resolved = mimeType?.takeIf { it.startsWith("image/") }
                ?: app.contentResolver.getType(uri)?.takeUnless(MediaSniffer::isGeneric)
                ?: (if (remote) android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) else null)
                ?: MediaSniffer.resolve(if (remote) null else withContext(Dispatchers.IO) { sniffHeader(uri) })
            val selected = _uiState.value.selectedModel!!
            if (selected.providerId == "google_flow") {
                updateParameter("mode", "Fotogramas")
                selected.contractMedia.firstOrNull { it.key == "initial_frame" }?.let { setContractMedia(it, uri, resolved) }
            } else if (selected.contractEndpoint != null) {
                selected.contractMedia.firstOrNull { it.mime.startsWith("image/") }?.let { setContractMedia(it, uri, resolved) }
            } else setMediaInput(uri, resolved)
        }
    }

    fun setMediaInput(uri: Uri?, mimeType: String?) { forgetModelSwitch();
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

    fun clearMediaInput() { forgetModelSwitch();
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
        val attached = _uiState.value.attachedMedia.filter { it.fieldKey==null }
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

    private val _pendingCostAcceptance = MutableStateFlow<GenerationCostQuote?>(null)
    val pendingCostAcceptance: StateFlow<GenerationCostQuote?> = _pendingCostAcceptance.asStateFlow()

    fun requestGeneration(quote: GenerationCostQuote, onSuccess: (String) -> Unit) { forgetModelSwitch();
        _pendingCostAcceptance.value = null
        if(comparison.sending) return
        val state = _uiState.value
        generationBlocker(state)?.let { message -> _uiState.update { it.copy(errorMessage = message) }; return }
        when(val gate = GenerationCostGuard.onGenerate(quote, generationFingerprint(state), com.vidadomu.nexus.BuildConfig.OFFLINE_QA)) {
            CostGate.Proceed -> launchGeneration(state, onSuccess)
            is CostGate.AskAcceptance -> _pendingCostAcceptance.value = gate.quote
            is CostGate.Reject -> _uiState.update { it.copy(errorMessage = gate.message) }
        }
    }

    fun acceptUnknownCost(onSuccess: (String) -> Unit) { forgetModelSwitch();
        val pending = _pendingCostAcceptance.value ?: return
        _pendingCostAcceptance.value = null
        if(comparison.sending) return
        val state = _uiState.value
        generationBlocker(state)?.let { message -> _uiState.update { it.copy(errorMessage = message) }; return }
        when(val gate = GenerationCostGuard.onAccept(pending, generationFingerprint(state), com.vidadomu.nexus.BuildConfig.OFFLINE_QA)) {
            CostGate.Proceed -> launchGeneration(state, onSuccess)
            is CostGate.Reject -> _uiState.update { it.copy(errorMessage = gate.message) }
            is CostGate.AskAcceptance -> Unit
        }
    }

    fun dismissCostAcceptance() { _pendingCostAcceptance.value = null }

    private fun generationBlocker(state: CreateUiState): String? {
        val readiness=creationReadiness(state)
        if(!readiness.canSubmit) return readiness.message ?: "Revisa la configuración."
        if(com.vidadomu.nexus.BuildConfig.OFFLINE_QA) return GenerationCostGuard.OFFLINE_MESSAGE
        val model=state.selectedModel ?: return "Elige un modelo."
        if(model.contractEndpoint!=null) {
            try {
                val validationRequest=GenerationRequest("validate",model.providerId,model.id,state.selectedModality,state.prompt,parameters=state.parameters)
                com.vidadomu.nexus.provider.runway.ContractPayload.validate(model,validationRequest)
                com.vidadomu.nexus.provider.crun.CrunTextCatalog.validateSuno(model,validationRequest)
            } catch(e:IllegalArgumentException) {
                return e.message ?: "Revisa los campos del modelo."
            }
        }
        return null
    }

    private fun launchGeneration(state: CreateUiState, onSuccess: (String) -> Unit) {
        _uiState.update { it.copy(isSubmitting = true, isUploading = false, errorMessage = null, submitFailure = null) }

        viewModelScope.launch {
            try {
                val jobId = GenerationDispatcher(app).submit(state) { uploading, progress ->
                    _uiState.update { it.copy(isUploading = uploading, uploadProgress = progress) }
                }
                onSuccess(jobId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                android.util.Log.e("CreateViewModel", "Generation pipeline failed: ${e.javaClass.simpleName}")
                val failure = describeSubmitFailure(e)
                _uiState.update {
                    it.copy(errorMessage = failure.message, submitFailure = failure)
                }
            } finally {
                _uiState.update {
                    it.copy(isSubmitting = false, isUploading = false, uploadProgress = null)
                }
            }
        }
    }

    /**
     * Lanza un trabajo por prompt con el modelo, parámetros y adjuntos actuales del Lienzo.
     * Se envían de uno en uno; un fallo no detiene los demás y se informa al final.
     */
    fun submitBatch(prompts: List<String>, onProgress: (Int, Int) -> Unit, onDone: (sent: Int, firstError: String?) -> Unit) { forgetModelSwitch();
        if (comparison.sending || _uiState.value.isSubmitting || prompts.isEmpty()) return
        if (com.vidadomu.nexus.BuildConfig.OFFLINE_QA) { onDone(0, "NEXUS Lab: envío bloqueado; no se ha consumido saldo."); return }
        val batchSettings = com.vidadomu.nexus.assistant.AssistantRuntime.get(app).settings
        val batchLimit = batchSettings.effectiveLimits(batchSettings.load()).maxGenerationsPerBatch
        if (batchLimit > 0 && prompts.size > batchLimit) { onDone(0, "El lote supera tu límite de $batchLimit trabajos."); return }
        val base = _uiState.value
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            var sent = 0
            var firstError: String? = null
            try {
                for ((index, prompt) in prompts.withIndex()) {
                    onProgress(index, prompts.size)
                    try {
                        GenerationDispatcher(app).submit(base.copy(prompt = prompt))
                        sent++
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        if (firstError == null) firstError = "«${prompt.take(40)}»: ${e.message ?: "no se pudo enviar"}"
                    }
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false, isUploading = false, uploadProgress = null) }
                onDone(sent, firstError)
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
