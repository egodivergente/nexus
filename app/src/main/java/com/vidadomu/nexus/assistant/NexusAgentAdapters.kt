package com.vidadomu.nexus.assistant

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.cost.CostConfidence
import com.vidadomu.nexus.agent.core.cost.CostEstimate
import com.vidadomu.nexus.agent.core.cost.CostEstimator
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.registry.MediaSpec
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.registry.ModelRegistry
import com.vidadomu.nexus.agent.core.registry.ModelSchema
import com.vidadomu.nexus.agent.core.registry.ModelSummary
import com.vidadomu.nexus.agent.core.registry.ParamSpec
import com.vidadomu.nexus.agent.core.skills.SkillSource
import com.vidadomu.nexus.agent.core.spend.GenerationBackend
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.JobStatus
import com.vidadomu.nexus.agent.core.spend.SubmissionUncertain
import com.vidadomu.nexus.agent.core.spend.SubmitResult
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.agent.core.tools.GenerationHistorySource
import com.vidadomu.nexus.agent.core.tools.HistoryEntry
import com.vidadomu.nexus.core.model.AttachedMedia
import com.vidadomu.nexus.core.model.FormContract
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.MediaSlotType
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.core.model.ParameterType
import com.vidadomu.nexus.provider.atlas.AtlasCloudProvider
import com.vidadomu.nexus.provider.atlas.AtlasPriceQuote
import com.vidadomu.nexus.provider.atlas.NativeModelSchema
import com.vidadomu.nexus.ui.create.CreateUiState
import com.vidadomu.nexus.ui.create.GenerationDispatcher
import com.vidadomu.nexus.ui.create.creationReadiness
import com.vidadomu.nexus.ui.models.modelGuide
import com.vidadomu.nexus.ui.models.modelRelease
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Registro del agente = catálogo visible de Nexus + schema real (NativeModelSchema / contratos). */
class NexusModelRegistry(private val app: NexusApplication) : ModelRegistry {
    private val configured = ConcurrentHashMap<String, NexusModel>()

    private suspend fun models(): List<NexusModel> =
        withTimeoutOrNull(15_000) { app.modelCatalogRepository.models.first { it.isNotEmpty() } } ?: app.modelCatalogRepository.models.value

    override suspend fun list(): List<ModelSummary> = (models() + app.flowBridgeProvider.getModels()).distinctBy { "${it.providerId}:${it.id}" }.map(::summary)

    private suspend fun find(ref: ModelRef): NexusModel =
        (models() + app.flowBridgeProvider.getModels()).firstOrNull { it.id == ref.modelId && it.providerId == ref.providerId }
            ?: throw IllegalArgumentException("${ref.modelId} no está visible en el catálogo de Nexus.")

    override suspend fun schema(ref: ModelRef): ModelSchema {
        val model = find(ref)
        val (cfg, unsupported, source) = if (model.providerId == AtlasCloudProvider.PROVIDER_ID) {
            val c = NativeModelSchema.load(model)
            Triple(c.model, c.unsupportedRequired, model.schemaUrl)
        } else Triple(agentMediaContract(model).copy(parameters = FormContract.normalize(model.parameters)), emptyList(), "contrato de ${model.providerId} en Nexus")
        configured["${ref.providerId}:${ref.modelId}"] = cfg
        return toSchema(ref, cfg, unsupported, source).copy(textLimits =
            ProviderPromptLimits.forModel(model)?.let { mapOf("prompt" to it) }.orEmpty())
    }

    /** Modelo ya configurado con su schema (el mismo objeto que usaría el Lienzo). */
    suspend fun configuredModel(ref: ModelRef): NexusModel =
        configured["${ref.providerId}:${ref.modelId}"] ?: run { schema(ref); configured.getValue("${ref.providerId}:${ref.modelId}") }

    companion object {
        fun summary(original: NexusModel): ModelSummary {
            val m = agentMediaContract(original)
            val guide = runCatching { modelGuide(m) }.getOrNull()
            return ModelSummary(
                ref = ModelRef(m.providerId, m.id), name = m.displayName, organization = m.organization,
                family = m.modelFamily.ifBlank { m.family ?: "" }.ifBlank { null },
                modalities = m.modalities.map { it.name }.toSet(), capabilities = m.capabilities.map { it.name }.toSet(),
                inputMode = m.inputMode.name, purpose = guide?.purpose, strength = guide?.strength,
                releaseMonth = runCatching { modelRelease(m)?.month }.getOrNull(), isFlagship = m.isFlagship,
                supportsImageInput = m.supportsImageInput, supportsFirstFrame = m.supportsFirstFrame, supportsLastFrame = m.supportsLastFrame,
                supportsReferenceImages = m.supportsReferenceImages, supportsVideoInput = m.supportsVideoInput, supportsAudio = m.supportsAudio,
                maxImages = m.maxImages, mediaRequired = m.isMediaRequired,
                catalogDurations = m.supportedDurations, catalogResolutions = m.supportedResolutions,
                basePriceUsd = m.basePriceUsd, priceUnit = m.priceUnit, priceUnitVerified = m.priceUnitVerified,
            )
        }

        /** Map unambiguous image/video inputs from the provider contract, without guessing field names. */
        internal fun agentMediaContract(m: NexusModel): NexusModel {
            val images = m.contractMedia.filter { it.mime.startsWith("image/") }
            val videos = m.contractMedia.filter { it.mime.startsWith("video/") }
            if (images.size == 1 && videos.isEmpty() && m.contractMedia.size == 1) {
                val field = images.single()
                val array = field.encoding in setOf("array", "object-array")
                return m.copy(inputMode = if(array) com.vidadomu.nexus.core.model.ModelInputMode.MULTI_IMAGE else com.vidadomu.nexus.core.model.ModelInputMode.SINGLE_IMAGE,
                    inputImageField = if(array) null else field.key, firstFrameField = if(array) null else field.key,
                    imagesArrayField = if(array) field.key else null, maxImages = field.maximum,
                    supportsImageInput = true, supportsFirstFrame = !array, supportsReferenceImages = array, isMediaRequired = field.required)
            }
            if(videos.size == 1 && images.isEmpty() && m.contractMedia.size == 1) {
                val field = videos.single()
                return m.copy(inputMode = com.vidadomu.nexus.core.model.ModelInputMode.VIDEO_INPUT,
                    videoField = if(field.encoding !in setOf("array", "object-array")) field.key else null,
                    videosArrayField = if(field.encoding !in setOf("array", "object-array")) null else field.key,
                    maxVideos = field.maximum, supportsVideoInput = true, isMediaRequired = field.required)
            }
            return m
        }

        private val MEDIA_TYPES = setOf(ParameterType.IMAGE, ParameterType.VIDEO, ParameterType.AUDIO, ParameterType.FILE,
            ParameterType.FIRST_FRAME, ParameterType.LAST_FRAME, ParameterType.REFERENCE_IMAGES, ParameterType.MASK)

        fun toSchema(ref: ModelRef, m: NexusModel, unsupported: List<String>, source: String?): ModelSchema = ModelSchema(
            ref = ref,
            parameters = FormContract.normalize(m.parameters).filter { it.type !in MEDIA_TYPES }.map { p ->
                ParamSpec(
                    key = p.key, displayName = p.displayName, uiType = p.type.name,
                    nativeType = p.nativeType ?: when (p.type) {
                        ParameterType.INTEGER, ParameterType.SEED -> "integer"
                        ParameterType.FLOAT, ParameterType.SLIDER -> "number"
                        ParameterType.BOOLEAN -> "boolean"
                        else -> null
                    },
                    options = p.options, min = p.minValue?.toDouble(), max = p.maxValue?.toDouble(), step = p.step?.toDouble(),
                    default = p.defaultValue.takeIf { FormContract.token(it) }, required = p.isRequired, advanced = p.isAdvanced,
                    description = p.description.takeIf { it.isNotBlank() },
                )
            },
            media = MediaSpec(
                inputMode = m.inputMode.name, firstFrameField = m.firstFrameField, lastFrameField = m.lastFrameField,
                inputImageField = m.inputImageField, imagesArrayField = m.imagesArrayField, videoField = m.videoField ?: m.videosArrayField,
                maxImages = m.maxImages, maxVideos = m.maxVideos, mediaRequired = m.isMediaRequired,
            ),
            promptRequired = m.promptRequired,
            // Medios extra (máscaras, audio) obligatorios: el asistente aún no los adjunta.
            unsupportedRequired = unsupported + m.contractMedia.filter { it.required && it.key !in setOf(m.inputImageField, m.firstFrameField, m.lastFrameField, m.imagesArrayField, m.videoField, m.videosArrayField) }.map { it.key },
            source = source,
        )
    }
}

/** Cotización del motor de precios de Atlas (la misma calculadora pública que usa el Lienzo, no genera). */
class NexusQuoteEstimator(private val registry: NexusModelRegistry) : CostEstimator {
    override suspend fun estimate(model: ModelSummary, params: Map<String, String>): CostEstimate {
        if (model.ref.providerId != AtlasCloudProvider.PROVIDER_ID) return CostEstimate(null, CostConfidence.UNKNOWN, "Sin calculadora para ${model.ref.providerId}.")
        val native = registry.configuredModel(model.ref)
        val images = if (native.supportsImageInput) 1 else 0
        val q = AtlasPriceQuote.fetch(native, params, images) ?: return CostEstimate(null, CostConfidence.UNKNOWN, "Atlas no devolvió cotización.")
        return if (q.unit == null && !q.baseTariff) {
            CostEstimate(q.usd, CostConfidence.NEXUS_QUOTE, q.configuration.ifBlank { "Calculadora de Atlas" }, source = "https://api.atlascloud.ai/api/v1/model/calculate",
                checkedAt = java.time.Instant.ofEpochMilli(q.checkedAt).toString().take(10), notes = listOf("Cotización para esta configuración; el cargo real lo confirma el proveedor."))
        } else CostEstimate(null, CostConfidence.BASE_ONLY, "${"%.3f".format(java.util.Locale.ROOT, q.unitUsd ?: q.usd)} $ por ${q.unit ?: "unidad"}", q.unitUsd, q.unit)
    }
}

/**
 * Envío real: reconstruye el MISMO estado del Lienzo (modelo configurado, valores del formulario,
 * medios en sus ranuras) y lo pasa por creationReadiness + GenerationDispatcher. Si el formulario
 * cambiaría algún valor del plan, se aborta en lugar de enviar otra cosa.
 */
class NexusGenerationBackend(private val app: NexusApplication, private val registry: NexusModelRegistry) : GenerationBackend {

    /**
     * El estado del Lienzo que corresponde a [plan]: el mismo que se enviaría. Lo usan el envío y
     * «Abrir en el Lienzo». Copia a almacenamiento privado los medios que no lo estén.
     */
    suspend fun toCreateState(plan: GenerationPlan): CreateUiState {
        val model = registry.configuredModel(plan.ref)
        val values = FormContract.restoredValues(model, plan.params)
        plan.params.forEach { (k, v) ->
            check(values[k] == v) { "El formulario de Nexus no acepta $k=$v; no se ha enviado nada." }
        }
        val media = plan.media.map { m ->
            // Assets del índice que no sean copia privada (content://, galería): se copian ahora.
            val local = if (Uri.parse(m.uri).scheme == "file") m.uri else AgentMediaStore.persist(app, Uri.parse(m.uri), m.mimeType).uri
            assistantMediaInput(model, m.copy(uri = local))
        }
        return CreateUiState(
            selectedModality = Modality.valueOf(plan.modality), selectedModel = model, prompt = plan.prompt,
            parameters = values, attachedMedia = media, schemaReady = true,
        )
    }

    override suspend fun submit(plan: GenerationPlan): SubmitResult {
        if (plan.ref.providerId == "google_flow") {
            val media = plan.media.map { m ->
                require(m.role == "reference") { "Flow recibe ingredientes con rol reference; no fotogramas de inicio/final." }
                val mime = requireNotNull(m.mimeType) { "Falta el tipo de la referencia." }
                val local = if (Uri.parse(m.uri).scheme == "file") m.uri else AgentMediaStore.persist(app, Uri.parse(m.uri), mime).uri
                val file = File(requireNotNull(Uri.parse(local).path))
                val remote = app.flowBridgeProvider.uploadFile(file, file.name, mime).getOrThrow()
                com.vidadomu.nexus.core.model.MediaInput(type = Modality.IMAGE, uriString = local,
                    mimeType = mime, fileName = file.name, remoteUrl = remote, slotType = MediaSlotType.REFERENCE_IMAGE)
            }
            val jobId = "job_${UUID.randomUUID()}"
            val request = com.vidadomu.nexus.core.model.GenerationRequest(id = jobId,
                providerId = "google_flow", modelId = plan.ref.modelId, modality = Modality.valueOf(plan.modality), prompt = plan.prompt,
                parameters = plan.params + mapOf(com.vidadomu.nexus.provider.flow.FlowBridgeProvider.EXPECTED_OUTPUT_KEY to plan.modality.lowercase()),
                mediaInputs = media)
            val job = app.generationRepository.submitGeneration(request, mapOf(ORIGIN_KEY to "assistant", PLAN_KEY to plan.id, "__assistant_original_prompt" to (plan.originalPrompt ?: plan.prompt))).getOrElse { err ->
                val stored = withContext(NonCancellable) { runCatching { app.generationRepository.getJob(jobId) }.getOrNull() }
                if (stored != null && (stored.parameters["__submission_uncertain"] == "true" || stored.status != GenerationStatus.FAILED)) {
                    throw SubmissionUncertain("Envío Flow pendiente de comprobar.", stored.id)
                }
                throw err
            }
            if (job.parameters["__submission_uncertain"] == "true") throw SubmissionUncertain("Envío Flow pendiente de comprobar.", job.id)
            if (job.status == GenerationStatus.FAILED) error(job.error ?: "Flow no aceptó el envío.")
            if (job.status != GenerationStatus.COMPLETED) com.vidadomu.nexus.core.work.GenerationPollingWorker.enqueue(app, job.id)
            return SubmitResult(job.id)
        }
        val state = toCreateState(plan)
        val ready = creationReadiness(state)
        check(ready.canSubmit) { ready.message ?: "La configuración no está lista." }
        // Id conocido de antemano: si el envío falla, se mira en nexus.db si pudo llegar al proveedor.
        val jobId = "job_${UUID.randomUUID()}"
        try {
            GenerationDispatcher(app).submit(state, mapOf(ORIGIN_KEY to "assistant", PLAN_KEY to plan.id, "__assistant_original_prompt" to (plan.originalPrompt ?: plan.prompt)), jobId = jobId)
        } catch (e: Exception) {
            val job = withContext(NonCancellable) { runCatching { app.generationRepository.getJob(jobId) }.getOrNull() }
            // Sin registro = falló antes de enviar (subida de medios, validación). Con «uncertain» = pudo cobrarse.
            if (job != null && (job.parameters["__submission_uncertain"] == "true" || job.status != GenerationStatus.FAILED)) {
                throw SubmissionUncertain("No se pudo confirmar el envío; podría haberse iniciado. Compruébalo en Historial antes de repetir.", jobId)
            }
            throw e
        }
        return SubmitResult(jobId, note = "Enviado por el asistente tras confirmación del usuario.")
    }

    override suspend fun status(jobId: String): JobStatus {
        val current = app.generationRepository.getJob(jobId) ?: return JobStatus(jobId, "FAILED", error = "Nexus no conoce ese trabajo.")
        // A status inquiry queries existing remote work. Flow queue placeholders must never submit here.
        val job = if (current.status in NexusNativeOperations.ACTIVE && NexusNativeOperations.canQueryExisting(current))
            app.generationRepository.pollJob(jobId).getOrNull() ?: app.generationRepository.getJob(jobId) ?: current
        else current
        val status = when (job.status) {
            GenerationStatus.COMPLETED -> "COMPLETED"
            GenerationStatus.FAILED -> "FAILED"
            GenerationStatus.CANCELLED -> "CANCELLED"
            GenerationStatus.UPLOADING, GenerationStatus.QUEUED -> "PENDING"
            else -> "PROCESSING"
        }
        return JobStatus(jobId, status, job.progress, job.outputs.map { it.localUri ?: it.url }, job.error, job.actualCostUsd)
    }

    companion object {
        const val ORIGIN_KEY = "__assistant_origin"
        const val PLAN_KEY = "__assistant_plan"
    }

    override suspend fun cancel(jobId: String): Boolean =
        app.generationRepository.cancelRemoteGeneration(jobId).getOrThrow().status == GenerationStatus.CANCELLED
}

class NexusHistorySource(private val app: NexusApplication) : GenerationHistorySource {
    override suspend fun recent(limit: Int): List<HistoryEntry> = app.generationRepository.allJobs.value
        .sortedByDescending { it.startedAt }.take(limit).map { j ->
            HistoryEntry(j.id, j.providerId, j.modelId, j.modality.name, j.prompt.take(400), j.status.name, j.startedAt,
                j.actualCostUsd ?: j.estimatedCostUsd, j.outputs.map { it.localUri ?: it.url })
        }
}

/** Skills empaquetadas en assets/skills (se actualizan además desde files/skills sin recompilar). */
class AssetSkillSource(private val assets: AssetManager, private val root: String = "skills") : SkillSource {
    override fun load(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        fun walk(path: String) {
            val children = assets.list(path).orEmpty()
            if (children.isEmpty()) {
                if (path.endsWith("/SKILL.md")) out += "assets/$path" to assets.open(path).bufferedReader().use { it.readText() }
            } else children.forEach { walk("$path/$it") }
        }
        walk(root)
        return out
    }
}

/** Copia privada de los adjuntos del asistente (los URIs del selector caducan). */
object AgentMediaStore {
    private const val MAX_BYTES = 512L * 1024 * 1024

    suspend fun persist(context: Context, uri: Uri, mime: String?, projectId: String? = null): Attachment = withContext(Dispatchers.IO) {
        val runtime = AssistantRuntime.get(context.applicationContext as com.vidadomu.nexus.NexusApplication)
        val existing = runtime.assets.assetByUri(uri.toString())
        if (existing != null && (projectId == null || existing.projectId == projectId) && uri.scheme == "file" && File(uri.path.orEmpty()).isFile) {
            return@withContext Attachment(existing.id, existing.uri, existing.mimeType ?: mime ?: "image/*",
                existing.displayName, existing.width, existing.height, assetId = existing.id)
        }
        var resolved = mime?.takeIf { it.isNotBlank() && it != "application/octet-stream" } ?: context.contentResolver.getType(uri) ?: "application/octet-stream"
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment ?: "adjunto"
        val documentFormat=AgentDocuments.format(resolved,name)
        if(documentFormat!=null && resolved !in AgentDocuments.types) resolved=AgentDocuments.mimeType(documentFormat)
        require(resolved.startsWith("image/") || resolved.startsWith("video/") || resolved.startsWith("audio/") || documentFormat!=null) { "Formato no compatible. Usa PDF, Word, TXT o Markdown." }
        val byteLimit=if(documentFormat!=null) AgentDocuments.MAX_BYTES else MAX_BYTES
        val ext = when {
            documentFormat!=null -> documentFormat
            "png" in resolved -> "png"; "webp" in resolved -> "webp"; "jpeg" in resolved || "jpg" in resolved -> "jpg"
            "quicktime" in resolved -> "mov"; resolved.startsWith("video/") -> "mp4"; resolved.startsWith("audio/") -> android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(resolved) ?: "audio"; else -> "bin"
        }
        val dir = File(context.filesDir, "agent_media").apply { mkdirs() }
        val target = File(dir, "agent_${UUID.randomUUID()}.$ext")
        try {
            (context.contentResolver.openInputStream(uri) ?: error("Android no permitió leer el archivo.")).use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(65536); var total = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        total += n; require(total <= byteLimit) { if(documentFormat!=null) "Bedrock admite documentos de hasta 4,5 MB; divide el archivo en capítulos." else "El archivo supera 512 MB." }
                        out.write(buf, 0, n)
                    }
                }
            }
        } catch (e: Exception) { target.delete(); throw e }
        var w: Int? = null; var h: Int? = null
        if (resolved.startsWith("image/")) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.path, o); w = o.outWidth.takeIf { it > 0 }; h = o.outHeight.takeIf { it > 0 }
        }
        val attachment = Attachment("att_" + UUID.randomUUID().toString().take(8), Uri.fromFile(target).toString(), resolved, name, w, h)
        val saved = runtime.assets.addAsset(com.vidadomu.nexus.agent.core.assets.Asset(
            id = attachment.id, uri = attachment.uri,
            type = when {
                resolved.startsWith("image/") -> com.vidadomu.nexus.agent.core.assets.AssetType.IMAGE
                resolved.startsWith("video/") -> com.vidadomu.nexus.agent.core.assets.AssetType.VIDEO
                resolved.startsWith("audio/") -> com.vidadomu.nexus.agent.core.assets.AssetType.AUDIO
                else -> com.vidadomu.nexus.agent.core.assets.AssetType.OTHER
            },
            role = com.vidadomu.nexus.agent.core.assets.AssetRole.REFERENCE,
            displayName = name, mimeType = resolved, width = w, height = h,
            projectId = projectId, createdAt = System.currentTimeMillis(),
        ))
        attachment.copy(id = saved.id, assetId = saved.id)
    }

    /** Miniatura JPEG ≤1024 px para que el LLM vea el keyframe (solo si el usuario lo permite). */
    suspend fun thumbnail(att: Attachment): ContentBlock.Image? = withContext(Dispatchers.IO) {
        if (!att.mimeType.startsWith("image/")) return@withContext null
        val path = Uri.parse(att.uri).path ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext null
        val scale = 1024f / maxOf(bmp.width, bmp.height)
        val sized = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val bytes = ByteArrayOutputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, 85, it); it.toByteArray() }
        ContentBlock.Image("jpeg", bytes)
    }
}
