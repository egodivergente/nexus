package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*
import kotlinx.coroutines.launch

/** Only declared contracts are adapted. Never infer an endpoint or capability from a brand. */
object ComparisonPlan {
    const val MAX_MODELS = 6
    const val MAX_GENERATIONS_PER_MODEL = 20
    const val MAX_REQUESTS = MAX_MODELS * MAX_GENERATIONS_PER_MODEL
    const val NUMBER = "__comparison_number"
    const val MODEL_SIZE = "__comparison_model_size"

    /** Round-robin gives each model a first result before starting its next generation. */
    fun requests(quantities: List<Int>, maxModels: Int = MAX_MODELS, maxGenerations: Int = MAX_GENERATIONS_PER_MODEL): List<ComparisonTask> {
        require(quantities.size in 1..maxModels)
        require(quantities.all { it in 1..maxGenerations })
        return (1..quantities.max()).flatMap { number ->
            quantities.indices.filter { quantities[it] >= number }.map { ComparisonTask(it, number) }
        }
    }
    const val ID = "__comparison_id"
    const val SIZE = "__comparison_size"
    const val NAME = "__comparison_model"

    fun key(model: NexusModel) = model.providerId + ":" + model.id
    fun eligible(model: NexusModel): Boolean = eligible(model,Modality.IMAGE)
    fun eligible(model: NexusModel, modality: Modality): Boolean = modality in model.modalities && model.promptAccepted &&
        when(modality) {
            Modality.IMAGE -> model.contractEndpoint != null && model.promptRequired ||
                model.capabilities.any {it in setOf(ModelCapability.TEXT_TO_IMAGE,ModelCapability.IMAGE_TO_IMAGE,ModelCapability.IMAGE_EDITING)}
            Modality.VIDEO -> model.contractEndpoint != null && model.promptRequired ||
                model.capabilities.any {it in setOf(ModelCapability.TEXT_TO_VIDEO,ModelCapability.IMAGE_TO_VIDEO,ModelCapability.REFERENCE_TO_VIDEO)}
            else -> false
        }

    private val counts = setOf("num_images", "num_outputs", "output_count", "outputCount", "n", "number_of_images", "batch_size", "count", "num_videos")
    private val ratios = setOf("aspect_ratio", "aspectRatio", "ratio", "size", "image_size")
    private fun sameRatio(a: String, b: String): Boolean {
        fun ratio(s: String): Double? {
            val parts = s.split(Regex("[:xX*×]"))
            if (parts.size != 2) return null
            val width = parts[0].toDoubleOrNull() ?: return null
            val height = parts[1].toDoubleOrNull() ?: return null
            return (width / height).takeIf { width > 0 && height > 0 && it.isFinite() }
        }
        val left = ratio(a) ?: return false
        val right = ratio(b) ?: return false
        return kotlin.math.abs(left - right) < 0.0001
    }

    fun adapt(source: CreateUiState, target: NexusModel): PreparedComparison {
        require(eligible(target,source.selectedModality)) { "Este modelo no admite encargos de esta modalidad con prompt." }
        require(source.prompt.isNotBlank()) { "Escribe un prompt en el lienzo." }
        val model = target.copy(parameters = FormContract.normalize(target.parameters))
        val notes = mutableListOf<String>()
        val used = mutableSetOf<String>()
        val values = model.parameters.associate { p ->
            val sourceKey = if(p.key in source.parameters) p.key else
                if(p.key in ratios) source.parameters.keys.firstOrNull { it in ratios } else null
            val original = sourceKey?.let(source.parameters::get)?.takeIf { it.isNotBlank() }
            val formatOptions = ParameterPresentation.options(p)
            val candidate = if(p.key in ratios && original != null && formatOptions.isNotEmpty())
                formatOptions.firstOrNull { it == original }
                    ?: formatOptions.firstOrNull { it.split(Regex("[:xX*×]")) == original.split(Regex("[:xX*×]")) }
                    ?: formatOptions.firstOrNull { sameRatio(it, original) } else original
            if(p.key in ratios && original != null && candidate != null && candidate != original)
                notes += "${ParameterPresentation.title(p)}: ${ParameterPresentation.describe(p,candidate)}."
            val value = when {
                p.key in counts -> {
                    require(FormContract.invalidValue(listOf(p), mapOf(p.key to "1")) == null) {
                        "${p.displayName}: este modelo no admite una sola salida. Utilízalo individualmente."
                    }
                    if(original != null && original != "1") notes += "${p.displayName}: 1 por solicitud; la cantidad del lote se elige aparte."
                    "1"
                }
                candidate != null && FormContract.invalidValue(listOf(p), mapOf(p.key to candidate)) == null -> candidate
                else -> {
                    if(original != null) notes += "${p.displayName}: ${ParameterPresentation.describe(p,p.defaultValue)} (no admite «${ParameterPresentation.describe(p,original)}»)."
                    p.defaultValue
                }
            }
            sourceKey?.let(used::add)
            p.key to value
        }
        source.parameters.filter { (key,value) -> value.isNotBlank() && !key.startsWith("__") && key !in used && key !in counts }
            .keys.forEach { key -> notes += "Sin ${source.selectedModel?.parameters?.find { it.key == key }?.displayName ?: key}." }

        require(source.attachedMedia.all { it.mimeType.startsWith("image/") }) { "Para comparar modelos, utiliza un prompt con imágenes; las otras entradas requieren su formulario específico." }
        val media = mapImages(model, source.attachedMedia)
        val state = CreateUiState(selectedModel = model, selectedModality = source.selectedModality,
            prompt = source.prompt, parameters = values, attachedMedia = media, schemaReady = true)
        val readiness = creationReadiness(state)
        require(readiness.canSubmit) { readiness.message ?: "Revisa los requisitos del modelo." }
        if(model.contractEndpoint != null) com.vidadomu.nexus.provider.runway.ContractPayload.validate(model,
            GenerationRequest("comparison-validation", model.providerId, model.id, source.selectedModality, source.prompt, parameters = values))
        return PreparedComparison(state, notes.distinct())
    }

    fun referenceProblem(model: NexusModel, source: List<AttachedMedia>): String? = try {
        require(source.all { it.mimeType.startsWith("image/") }) { "El lote admite imágenes de referencia; hay otro tipo de archivo." }
        mapImages(model, source)
        null
    } catch (error: IllegalArgumentException) { error.message ?: "Referencias incompatibles." }

    fun referenceLimit(model: NexusModel): Int = if (model.contractEndpoint != null) {
        model.contractMedia.singleOrNull { it.mime.startsWith("image/") && !it.key.contains("mask", true) }?.maximum ?: 0
    } else if(model.lastFrameField != null) 0 else {
        val array = if(model.imagesArrayField != null) model.maxImages else 0
        (array.toLong() + if(model.firstFrameField != null) 1 else 0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun mapImages(model: NexusModel, source: List<AttachedMedia>): List<AttachedMedia> {
        if(source.isEmpty()) return emptyList()
        require(source.all { mediaBindingMatches(model, it) }) {
            "Hay archivos vinculados a entradas específicas de otro modelo. Se conservan y no se pueden reinterpretar como referencias."
        }
        if(model.providerId == "atlas_cloud") {
            val minimumProblem = com.vidadomu.nexus.provider.atlas.NativeModelSchema.referenceCountProblem(model, source.size)
            require(minimumProblem == null) { minimumProblem ?: "Revisa las referencias." }
        }
        // Multiple named inputs can be a mask or other distinct role. Do not guess their meaning.
        require(source.mapNotNull { it.fieldKey }.distinct().size <= 1 && source.none { it.fieldKey?.contains("mask", true) == true }) {
            "Hay entradas con funciones distintas. Compara usando solo imágenes de referencia."
        }
        if(model.contractEndpoint != null) {
            val fields = model.contractMedia.filter { it.mime.startsWith("image/") }
            require(fields.size == 1 && !fields.single().key.contains("mask", true)) {
                "Este modelo no admite estas referencias como una entrada común."
            }
            val field = fields.single()
            require(source.size <= field.maximum) { "Admite ${field.maximum} referencias; has añadido ${source.size}." }
            return source.map { it.copy(fieldKey = field.key, slotType = MediaSlotType.REFERENCE_IMAGE, remoteUrl = null) }
        }
        require(model.contractMedia.none { it.required }) { "Necesita entradas específicas además de las referencias." }
        require(model.lastFrameField == null) { "Necesita referencias con papeles distintos." }
        val first = model.firstFrameField != null
        val capacity = referenceLimit(model)
        require(source.size <= capacity) { "Admite $capacity referencias; has añadido ${source.size}." }
        return source.mapIndexed { index, media -> media.copy(fieldKey = null, remoteUrl = null,
            slotType = if(first && index == 0) MediaSlotType.PRIMARY_IMAGE else MediaSlotType.REFERENCE_IMAGE) }
    }
}

data class ComparisonTask(val entryIndex: Int, val number: Int)

data class PreparedComparison(val state: CreateUiState, val changes: List<String>, val originalPrompt: String = state.prompt)
enum class ComparisonCurrency { USD, CRUN_CREDITS }
data class ComparisonCost(val amount: Double, val currency: ComparisonCurrency, val detail: String) {
    init { require(amount.isFinite() && amount >= 0) }
}
fun comparisonTotal(costs: List<ComparisonCost?>): Map<ComparisonCurrency, Double>? {
    if(costs.isEmpty() || costs.any { it == null }) return null
    return costs.filterNotNull().groupBy { it.currency }.mapValues { (_,list) -> list.sumOf { it.amount } }
}

/** Dispatch once, then poll using the existing per-job workers. A stop never cancels an accepted job. */
class ComparisonQueue(private val parallelism: Int = 1) {
    var stopRequested = false
        private set
    private var started = false
    fun stop() { stopRequested = true }
    suspend fun <T> run(items: List<T>, dispatch: suspend (Int,T) -> Unit) {
        check(!started) { "Este lote ya se ha enviado." }
        require(items.isNotEmpty())
        started = true
        val slots=kotlinx.coroutines.sync.Semaphore(parallelism.coerceAtLeast(1))
        kotlinx.coroutines.coroutineScope {
            items.forEachIndexed {index,item->
                slots.acquire()
                if(stopRequested) {slots.release();return@forEachIndexed}
                launch {try {dispatch(index,item)} finally {slots.release()}}
            }
        }
    }
}
