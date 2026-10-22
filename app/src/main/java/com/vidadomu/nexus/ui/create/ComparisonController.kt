package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.*
import com.vidadomu.nexus.provider.runway.RunwayPricing
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import com.vidadomu.nexus.ui.models.ImageModelChoices
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class ComparisonPhase { CHOOSE, PREPARING, REVIEW, SENDING, RESULTS }
data class ComparisonEntry(val model: NexusModel, val prepared: PreparedComparison? = null,
    val cost: ComparisonCost? = null, val error: String? = null, val jobId: String? = null,
    val status: String = "Pendiente", val progress: Float? = null,
    val quantity: Int = 1, val sentCount: Int = 0) {
    val subtotal get() = cost?.takeIf { quantity in 1..ComparisonPlan.MAX_GENERATIONS_PER_MODEL }
        ?.let { it.copy(amount = it.amount * quantity) }
}
data class ComparisonState(val source: CreateUiState = CreateUiState(), val catalog: List<NexusModel> = emptyList(),
    val selected: Set<String> = emptySet(), val entries: List<ComparisonEntry> = emptyList(),
    val phase: ComparisonPhase = ComparisonPhase.CHOOSE, val message: String? = null,
    val batchId: String? = null, val quotedAt: Long = 0, val stopped: Boolean = false,
    val quantities: Map<String,Int> = emptyMap(), val withReferences: Boolean = true,
    val checking: Set<String> = emptySet(), val checked: Set<String> = emptySet(),
    val selectionProblems: Map<String,String> = emptyMap()) {
    val canReview get() = phase == ComparisonPhase.CHOOSE && selected.isNotEmpty() &&
        selected.all { it in checked && it !in checking && it !in selectionProblems } &&
        source.prompt.isNotBlank() && (!withReferences || source.attachedMedia.isNotEmpty())
    fun quantity(model: NexusModel) = quantities[ComparisonPlan.key(model)] ?: 1
    val selectedRequests get() = selected.sumOf { quantities[it] ?: 1 }
    val requestCount get() = entries.sumOf { it.quantity }
    val total get() = comparisonTotal(entries.map { it.subtotal })
    val canSend get() = phase == ComparisonPhase.REVIEW && entries.size in 1..ComparisonPlan.MAX_MODELS &&
        entries.map { ComparisonPlan.key(it.model) }.distinct().size == entries.size &&
        entries.any { it.prepared != null && it.error == null && it.quantity in 1..ComparisonPlan.MAX_GENERATIONS_PER_MODEL }
}

class ComparisonController(private val app: NexusApplication, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(ComparisonState())
    val state: StateFlow<ComparisonState> = mutable.asStateFlow()
    private val preferences = app.getSharedPreferences("comparison_v1", 0)
    private var preparation: Job? = null
    private var queue: ComparisonQueue? = null
    val sending get() = state.value.phase == ComparisonPhase.SENDING

    private val checks = mutableMapOf<String, Job>()
    private val checkSlots = Semaphore(3)
    private var selectionEpoch = 0

    fun open(source: CreateUiState, withReferences: Boolean = true) {
        if(sending) return
        preparation?.cancel()
        checks.values.toList().forEach { it.cancel() }; checks.clear(); selectionEpoch++
        val candidates = if(BuildConfig.OFFLINE_QA) localModels() + listOfNotNull(source.selectedModel) else app.modelCatalogRepository.models.value
        val references = withReferences || source.attachedMedia.isNotEmpty()
        val catalog = candidates.filter {ComparisonPlan.eligible(it,source.selectedModality)}.filter {
            if(source.selectedModality==Modality.IMAGE) {
                if(references) ImageModelChoices.acceptsReferences(it) else ImageModelChoices.acceptsText(it)
            } else if(references) ComparisonPlan.referenceLimit(it)>0 else !it.isMediaRequired && it.contractMedia.none {field->field.required}
        }.distinctBy(ComparisonPlan::key).sortedWith(ImageModelChoices.comparator)
        val keys = catalog.map(ComparisonPlan::key).toSet()
        val previous = preferences.getStringSet("models", emptySet()).orEmpty()
        val saved = previous.intersect(keys).take(ComparisonPlan.MAX_MODELS).toSet()
        val wanted = saved.ifEmpty { source.selectedModel?.let(ComparisonPlan::key)?.takeIf { it in keys }?.let(::setOf).orEmpty() }
        val quantities = catalog.associate { model -> val key = ComparisonPlan.key(model)
            key to preferences.getInt("quantity:$key", 1).coerceIn(1, ComparisonPlan.MAX_GENERATIONS_PER_MODEL) }
        mutable.value = ComparisonState(source = source, catalog = catalog, quantities = quantities, withReferences = references,
            message = if(previous.any { it !in keys }) "Se han apartado de este lote las variantes que no corresponden a estas entradas." else null)
        catalog.filter { ComparisonPlan.key(it) in wanted }.forEach { checkModel(it, select = true) }
        ImageModelChoices.shortlist(catalog).filter { ComparisonPlan.key(it) !in wanted }.forEach { checkModel(it) }
    }

    fun setReferenceMode(references: Boolean) {
        if(state.value.phase != ComparisonPhase.CHOOSE || state.value.source.attachedMedia.isNotEmpty()) return
        open(state.value.source, references)
    }

    private fun checkModel(target: NexusModel, select: Boolean = false) {
        val key = ComparisonPlan.key(target)
        if(key in checks) return
        val epoch = selectionEpoch
        mutable.update { it.copy(checking = it.checking + key) }
        checks[key] = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val model = checkSlots.withPermit {
                    if(target.providerId == "atlas_cloud" && !BuildConfig.OFFLINE_QA) {
                        val native = NativeModelSchema.load(target)
                        require(native.unsupportedRequired.isEmpty()) { "Este modelo requiere entradas que Nexus todavía no admite." }
                        native.model
                    } else target
                }
                val current = state.value
                if(epoch != selectionEpoch) return@launch
                val problem = if(current.withReferences && ComparisonPlan.referenceLimit(model) == 0)
                    "Esta variante no admite referencias de imagen."
                else ComparisonPlan.referenceProblem(model, current.source.attachedMedia)
                mutable.update { s -> s.copy(catalog = s.catalog.map { if(ComparisonPlan.key(it) == key) model else it },
                    checking = s.checking - key, checked = s.checked + key,
                    selectionProblems = if(problem == null) s.selectionProblems - key else s.selectionProblems + (key to problem),
                    selected = if(select && problem == null && s.phase == ComparisonPhase.CHOOSE && s.selected.size < ComparisonPlan.MAX_MODELS) s.selected + key else if(problem != null) s.selected - key else s.selected) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) {
                if(epoch == selectionEpoch) mutable.update { it.copy(checking = it.checking - key,
                    selectionProblems = it.selectionProblems + (key to "No se pudo comprobar la compatibilidad. Toca para reintentar."), selected = it.selected - key) }
            } finally { if(epoch == selectionEpoch) checks.remove(key) }
        }
        checks[key]?.start()
    }

    fun toggle(model: NexusModel) {
        val s = state.value
        if(s.phase != ComparisonPhase.CHOOSE) return
        val key = ComparisonPlan.key(model)
        if(s.catalog.none { ComparisonPlan.key(it) == key } || key in s.checking) return
        if(key in s.selected) { mutable.update { it.copy(selected = it.selected-key, message = null) }; return }
        if(s.selected.size >= ComparisonPlan.MAX_MODELS) {
            mutable.update { it.copy(message = "Hasta ${ComparisonPlan.MAX_MODELS} modelos por lote.") }; return
        }
        if(key !in s.checked) { checkModel(model, select = true); return }
        s.selectionProblems[key]?.let { problem -> mutable.update { it.copy(message = problem) }; return }
        mutable.update { it.copy(selected = it.selected+key, message = null) }
    }

    fun setQuantity(model: NexusModel, quantity: Int) {
        val s = state.value
        val key = ComparisonPlan.key(model)
        if(s.phase != ComparisonPhase.CHOOSE || key !in s.selected || quantity !in 1..ComparisonPlan.MAX_GENERATIONS_PER_MODEL) return
        mutable.update { it.copy(quantities = it.quantities + (key to quantity), message = null) }
    }

    fun edit() {
        if(sending) return
        preparation?.cancel()
        mutable.update { it.copy(phase = ComparisonPhase.CHOOSE, entries = emptyList(), message = null) }
    }

    fun prepare() {
        val current = state.value
        if(current.phase !in setOf(ComparisonPhase.CHOOSE, ComparisonPhase.REVIEW)) return
        if(current.phase == ComparisonPhase.CHOOSE && !current.canReview) return
        if(current.source.prompt.isBlank()) { mutable.update { it.copy(message = "Escribe primero un prompt en el lienzo.") }; return }
        if(current.source.selectedModel != null && !current.source.schemaReady) {
            mutable.update { it.copy(message = "Espera a que se carguen los ajustes del lienzo.") }; return
        }
        if(current.source.isPreparingMedia || current.source.isSubmitting) {
            mutable.update { it.copy(message = "Espera a que el lienzo termine de preparar los archivos.") }; return
        }
        val chosen = current.catalog.filter { ComparisonPlan.key(it) in current.selected }
        if(chosen.size !in 1..ComparisonPlan.MAX_MODELS) return
        preferences.edit().putStringSet("models", current.selected).also { editor ->
            chosen.forEach { editor.putInt("quantity:${ComparisonPlan.key(it)}", current.quantity(it)) }
        }.apply()
        mutable.update { it.copy(phase = ComparisonPhase.PREPARING, entries = chosen.map { ComparisonEntry(it, quantity = current.quantity(it)) }, message = null) }
        preparation = scope.launch {
            val quoteStartedAt = System.currentTimeMillis()
            chosen.forEachIndexed { index, target ->
                update(index) { it.copy(status = "Comprobando opciones…") }
                try {
                    val model = if(target.providerId == "atlas_cloud" && !BuildConfig.OFFLINE_QA) {
                        val native = NativeModelSchema.load(target)
                        require(native.unsupportedRequired.isEmpty()) { "El esquema requiere entradas que todavía no admite Nexus." }
                        native.model
                    } else target
                    val runtime = com.vidadomu.nexus.assistant.AssistantRuntime.get(app)
                    val settings = runtime.settings.load()
                    val fitted = com.vidadomu.nexus.agent.core.llm.PromptAdapter({ runtime.llm(settings) }, { settings.modelId })
                        .adapt(current.source.prompt, com.vidadomu.nexus.assistant.ProviderPromptLimits.forModel(model), model.displayName)
                    val plan = ComparisonPlan.adapt(current.source.copy(prompt = fitted), model).copy(originalPrompt = current.source.prompt)
                    update(index) { it.copy(prepared = plan, status = "Calculando coste…") }
                    val cost = runCatching { quote(plan) }.getOrNull()
                    update(index) { it.copy(model = model, cost = cost, status = if(cost == null) "Listo · coste sin estimación" else "Listo") }
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(error: Exception) {
                    update(index) { it.copy(error = friendlyError(error), status = "Revisar") }
                }
            }
            mutable.update { it.copy(phase = ComparisonPhase.REVIEW, quotedAt = quoteStartedAt) }
        }
    }

    private suspend fun quote(plan: PreparedComparison): ComparisonCost {
        val s = plan.state
        val model = requireNotNull(s.selectedModel)
        if(BuildConfig.OFFLINE_QA) return ComparisonCost(0.05, ComparisonCurrency.USD, "Tarifa simulada · laboratorio sin conexión")
        return when(model.providerId) {
            "atlas_cloud" -> {
                val q = AtlasPriceQuote.fetch(model, s.parameters, s.attachedMedia.size)
                require(q != null && q.unit == null && !q.baseTariff) {
                    "Falta el coste para esta configuración. Una tarifa base no permite calcular el total."
                }
                ComparisonCost(q.usd, ComparisonCurrency.USD, "Estimación Atlas · ${q.configuration}")
            }
            "runway" -> {
                val q = RunwayPricing.quote(model, s.parameters, s.attachedMedia.size, null, s.prompt.length)
                ComparisonCost(requireNotNull(q.usd) { "No hay tarifa disponible para estas opciones." }, ComparisonCurrency.USD, q.detail)
            }
            "crun" -> ComparisonCost(app.crunProvider.estimateCredits(model, s.parameters, s.prompt).getOrThrow(),
                ComparisonCurrency.CRUN_CREDITS, "Cotización Crun · créditos de tu cuenta")
            else -> error("Este proveedor no ofrece un coste que pueda compararse.")
        }
    }

    fun send() {
        val s = state.value
        if(!s.canSend) return
        if(BuildConfig.OFFLINE_QA) {
            mutable.update { it.copy(message = "Validación correcta. NEXUS Lab bloquea todos los envíos: 0 generaciones, 0 saldo consumido.") }; return
        }
        val batchId = UUID.randomUUID().toString()
        val run = ComparisonQueue().also { queue = it }
        mutable.update { it.copy(phase = ComparisonPhase.SENDING, batchId = batchId, message = null) }
        scope.launch {
            try {
                run.run(ComparisonPlan.requests(s.entries.map { it.quantity })) { _, task ->
                    val index = task.entryIndex
                    val entry = s.entries[index]
                    if(entry.prepared == null || state.value.entries[index].error != null) return@run
                    update(index) { it.copy(status = "Enviando ${task.number} de ${entry.quantity}…") }
                    try {
                        val jobId = GenerationDispatcher(app).submit(requireNotNull(entry.prepared).state,
                            mapOf(ComparisonPlan.ID to batchId, ComparisonPlan.SIZE to s.requestCount.toString(),
                                ComparisonPlan.NAME to entry.model.displayName,
                                ComparisonPlan.NUMBER to task.number.toString(),
                                ComparisonPlan.MODEL_SIZE to entry.quantity.toString(), "__comparison_original_prompt" to requireNotNull(entry.prepared).originalPrompt)) { uploading,progress ->
                            update(index) { it.copy(status = if(uploading) "Subiendo referencias · ${task.number}/${entry.quantity}…" else "Enviando ${task.number} de ${entry.quantity}…", progress = progress) }
                        }
                        update(index) { it.copy(jobId = jobId, sentCount = it.sentCount + 1, status = "${it.sentCount + 1} de ${it.quantity} enviadas", progress = null) }
                    } catch(cancelled: CancellationException) { throw cancelled }
                    catch(error: Exception) {
                        update(index) { it.copy(error = friendlyError(error), status = "Envío no confirmado", progress = null) }
                        // Keep the failed model pending; independent models continue.
                    }
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) {
                mutable.update { it.copy(message = "Se ha detenido el resto de envíos. Revisa el historial antes de repetir una tarea.") }
            } finally {
                mutable.update { it.copy(phase = ComparisonPhase.RESULTS, entries = it.entries.map { entry ->
                    if(entry.sentCount < entry.quantity && entry.error == null)
                        entry.copy(status = "${entry.sentCount} de ${entry.quantity} enviadas · ${entry.quantity-entry.sentCount} pendientes sin enviar") else entry
                }) }
            }
        }
    }

    fun stop() {
        queue?.stop()
        mutable.update { it.copy(stopped = true, message = "Se detendrá tras el envío actual. Las tareas aceptadas siguen en curso.") }
    }
    fun dismiss() { if(state.value.phase == ComparisonPhase.PREPARING) edit() }
    private fun update(index: Int, transform: (ComparisonEntry) -> ComparisonEntry) {
        mutable.update { s -> s.copy(entries = s.entries.mapIndexed { i,e -> if(i == index) transform(e) else e }) }
    }
    private fun friendlyError(error: Exception) = when(error) {
        is ProviderError -> error.humanReadableMessage
        is IllegalArgumentException -> error.message ?: "Revisa las opciones del modelo."
        else -> "No se pudo completar la comprobación. Revisa la conexión y la configuración del proveedor."
    }

    private fun localModels(): List<NexusModel> {
        check(BuildConfig.OFFLINE_QA)
        val ids = setOf("bytedance/seedream-v5.0-pro/edit", "openai/gpt-image-2.5-sunburst/edit",
            "black-forest-labs/flux-2-pro/edit", "youchuan/v8.2/image-to-image", "bytedance/seedream-v5.0-pro/text-to-image")
        return AtlasModelMapper.parseModelsResponse(app.assets.open("atlas_catalog_snapshot.json").bufferedReader().use { it.readText() })
            .filter { it.id in ids }.map { model -> NativeModelSchema.decode(model,
                app.assets.open("qa-" + model.id.replace('/', '_') + ".json").bufferedReader().use { it.readText() }).model }
    }
}
